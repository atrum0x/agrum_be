package com.atrum.agrum.weight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyWeightSummaryJobTest {

    // 24/10/2026 22:00 in Colombo
    private static final Instant NOW = Instant.parse("2026-10-24T16:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
    private static final Instant LAST_SUCCESS = Instant.parse("2026-10-23T16:30:00Z");

    @Mock
    private DailyWeightSummaryService dailyWeightSummaryService;
    @Mock
    private DailyWeightSummaryRepository dailyWeightSummaryRepository;
    @Mock
    private JobRunStateRepository jobRunStateRepository;

    private DailyWeightSummaryJob job;
    private JobRunState state;

    @BeforeEach
    void setUp() {
        job = new DailyWeightSummaryJob(dailyWeightSummaryService, dailyWeightSummaryRepository,
                jobRunStateRepository, Clock.fixed(NOW, ZoneId.of("Asia/Colombo")));
        state = new JobRunState();
        state.setJobName(DailyWeightSummaryService.JOB_NAME);
        state.setLastSuccessAt(LAST_SUCCESS);
        when(jobRunStateRepository.findById(DailyWeightSummaryService.JOB_NAME)).thenReturn(Optional.of(state));
    }

    @Test
    void TC66_TC67_recalculatesTodayAndEveryDateChangedSinceTheLastRun() {
        // A correction approved today for the 20th, a weighing sent late for yesterday
        when(dailyWeightSummaryRepository.findDatesChangedSince(LAST_SUCCESS))
                .thenReturn(List.of(TODAY.minusDays(4), TODAY.minusDays(1), TODAY));

        List<LocalDate> dates = job.run();

        assertThat(dates).containsExactly(TODAY.minusDays(4), TODAY.minusDays(1), TODAY);
        verify(dailyWeightSummaryService).rebuild(TODAY.minusDays(4), NOW);
        verify(dailyWeightSummaryService).rebuild(TODAY, NOW);
        assertThat(state.getLastSuccessAt()).isEqualTo(NOW);
        assertThat(state.getLastError()).isNull();
        verify(jobRunStateRepository).save(state);
    }

    @Test
    void firstRun_includesEveryDateWithWeighings() {
        state.setLastSuccessAt(null);
        when(dailyWeightSummaryRepository.findDatesChangedSince(Instant.EPOCH)).thenReturn(List.of(TODAY.minusDays(10)));

        assertThat(job.run()).containsExactly(TODAY.minusDays(10), TODAY);
    }

    @Test
    void failure_keepsTheLastSuccessAndIsRetriedHourly() {
        when(dailyWeightSummaryRepository.findDatesChangedSince(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("db down")).when(dailyWeightSummaryService).rebuild(eq(TODAY), any());

        job.run();

        assertThat(state.getLastSuccessAt()).isEqualTo(LAST_SUCCESS);
        assertThat(state.getLastAttemptAt()).isEqualTo(NOW);
        assertThat(state.getLastError()).contains("db down");

        // The hourly check runs again while the error is set, and clears it on success
        doReturn(0).when(dailyWeightSummaryService).rebuild(eq(TODAY), any());
        job.retryIfFailed();
        assertThat(state.getLastError()).isNull();
        assertThat(state.getLastSuccessAt()).isEqualTo(NOW);
    }

    @Test
    void hourlyCheck_doesNothingAfterASuccessfulRun() {
        job.retryIfFailed();

        verify(dailyWeightSummaryService, never()).rebuild(any(), any());
        verify(jobRunStateRepository, never()).save(any());
    }
}
