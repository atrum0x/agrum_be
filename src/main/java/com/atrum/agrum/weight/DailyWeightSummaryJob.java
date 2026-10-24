package com.atrum.agrum.weight;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The 10 pm Daily Weight Summary run (D-23). Recalculates today and every earlier date where,
 * since the last successful run, a weighing arrived, a change was approved / disapproved,
 * or an attendance was corrected. Data arriving after the run is picked up the next night.
 * On failure it logs, records the error and retries hourly (proposed). The hourly check and a
 * startup check also catch up a 22:00 run that was missed because the app was down or asleep.
 * Runs without a logged-in user, so the estate filter is off and every site is included.
 */
@Component
@RequiredArgsConstructor
public class DailyWeightSummaryJob {

    private static final Logger log = LoggerFactory.getLogger(DailyWeightSummaryJob.class);
    private static final Instant NEVER = Instant.EPOCH;
    private static final LocalTime RUN_TIME = LocalTime.of(22, 0);

    private final DailyWeightSummaryService dailyWeightSummaryService;
    private final DailyWeightSummaryRepository dailyWeightSummaryRepository;
    private final JobRunStateRepository jobRunStateRepository;
    private final Clock clock;

    // The nightly run and the hourly retry both fire at 22:00
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(cron = "0 0 22 * * *", zone = "${agrum.time-zone:Asia/Colombo}")
    public void nightly() {
        run();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "${agrum.time-zone:Asia/Colombo}")
    public void retryIfFailed() {
        if (isOverdue()) {
            run();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        if (isOverdue()) {
            log.info("Daily weight summary run is overdue; running now");
            run();
        }
    }

    // The last run failed, or no run has succeeded since the most recent 22:00
    private boolean isOverdue() {
        JobRunState state = jobRunStateRepository.findById(DailyWeightSummaryService.JOB_NAME).orElse(null);
        if (state != null && state.getLastError() != null) {
            return true;
        }
        Instant lastSuccess = state == null || state.getLastSuccessAt() == null ? NEVER : state.getLastSuccessAt();
        return lastSuccess.isBefore(lastScheduledRun());
    }

    private Instant lastScheduledRun() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime todayRun = now.with(RUN_TIME);
        return (now.isBefore(todayRun) ? todayRun.minusDays(1) : todayRun).toInstant();
    }

    /** Returns the dates recalculated, or an empty list when a run is already in progress. */
    public List<LocalDate> run() {
        if (!running.compareAndSet(false, true)) {
            return List.of();
        }
        try {
            JobRunState state = jobRunStateRepository.findById(DailyWeightSummaryService.JOB_NAME)
                    .orElseGet(JobRunState::new);
            state.setJobName(DailyWeightSummaryService.JOB_NAME);
            // Anything that changes while the run is going is picked up again next time
            Instant start = clock.instant();
            Instant since = state.getLastSuccessAt() != null ? state.getLastSuccessAt() : NEVER;
            LocalDate today = LocalDate.now(clock);

            TreeSet<LocalDate> dates = new TreeSet<>(dailyWeightSummaryRepository.findDatesChangedSince(since));
            dates.add(today);
            dates.removeIf(date -> date == null || date.isAfter(today));

            state.setLastAttemptAt(start);
            try {
                runFor(dates, start);
                state.setLastSuccessAt(start);
                state.setLastError(null);
                log.info("Daily weight summary recalculated for {} date(s)", dates.size());
            } catch (RuntimeException e) {
                log.error("Daily weight summary run failed; retrying hourly", e);
                state.setLastError(truncate(e.toString()));
            }
            jobRunStateRepository.save(state);
            return List.copyOf(dates);
        } finally {
            running.set(false);
        }
    }

    // One transaction per date, so a failure keeps the dates already done
    void runFor(Collection<LocalDate> dates, Instant calculatedAt) {
        for (LocalDate date : dates) {
            dailyWeightSummaryService.rebuild(date, calculatedAt);
        }
    }

    private static String truncate(String message) {
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
