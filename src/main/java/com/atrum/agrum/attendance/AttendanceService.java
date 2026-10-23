package com.atrum.agrum.attendance;

import com.atrum.agrum.attendance.AttendanceSiteValidator.AttendanceSite;
import com.atrum.agrum.attendance.dto.AttendanceDefaultsDto;
import com.atrum.agrum.attendance.dto.AttendanceDto;
import com.atrum.agrum.attendance.dto.AttendanceSearchRequest;
import com.atrum.agrum.attendance.dto.AttendanceWorkerDto;
import com.atrum.agrum.attendance.dto.AttendanceWorkerRequest;
import com.atrum.agrum.attendance.dto.RecordAttendanceRequest;
import com.atrum.agrum.attendance.dto.SyncAttendanceRecord;
import com.atrum.agrum.attendance.dto.SyncAttendanceResult;
import com.atrum.agrum.employee.Employee;
import com.atrum.agrum.employee.EmployeeRepository;
import com.atrum.agrum.employee.EmploymentStatus;
import com.atrum.agrum.estate.Estate;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    // A phone clock slightly ahead of the server is not a future record
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    // Keeps the last-correction IN list well under Postgres' bind parameter limit
    private static final int LOOKUP_CHUNK_SIZE = 1000;

    private final AttendanceRepository attendanceRepository;
    private final AttendanceCorrectionRepository attendanceCorrectionRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceSiteValidator siteValidator;
    private final UserService userService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AttendanceDefaultsDto getDefaults() {
        AppUser user = userService.getCurrentUser();
        Employee employee = user.getEmployee();
        return new AttendanceDefaultsDto(
                LocalDate.now(clock),
                clock.instant(),
                employee == null ? null : idOf(employee.getDefaultEstate()));
    }

    /** Workers for the picker: Active, employed today, from all sites, not yet marked today. */
    @Transactional(readOnly = true)
    public List<AttendanceWorkerDto> getAvailableWorkers(String search) {
        LocalDate today = LocalDate.now(clock);
        // Postgres cannot type a null parameter in ":search IS NULL", so pass "" for no search
        String term = StringUtils.hasText(search) ? search.trim() : "";
        Set<String> recorded = new HashSet<>(attendanceRepository.findAllEmployeeCodesRecordedOn(today));
        return employeeRepository.findEmployedOn(EmploymentStatus.ACTIVE, today, term).stream()
                .filter(employee -> !recorded.contains(employee.getEmployeeCode()))
                .map(employee -> new AttendanceWorkerDto(
                        employee.getEmployeeCode(),
                        employee.getFullName(),
                        idOf(employee.getDefaultEstate()),
                        idOf(employee.getHomeEstate()),
                        employee.getDefaultCollectionPoint() == null
                                ? null : employee.getDefaultCollectionPoint().getCpCode(),
                        defaultCategory(employee)))
                .toList();
    }

    /** Web batch: all workers are saved, or none. */
    @Transactional
    public List<AttendanceDto> recordAttendance(RecordAttendanceRequest request) {
        AppUser user = userService.getCurrentUser();
        // AT-01
        if (request.workers() == null || request.workers().isEmpty()) {
            throw new IllegalArgumentException(AttendanceMessages.AT_01_NO_WORKERS);
        }
        // AT-11: the server date on web
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        AttendanceSite site = siteValidator.resolve(request.estateId(), request.workType(), request.cpCode());

        List<String> employeeCodes = request.workers().stream()
                .map(worker -> worker.employeeCode() == null ? "" : worker.employeeCode().trim())
                .toList();
        Map<String, Employee> employees = employeeRepository.findAllById(employeeCodes).stream()
                .collect(Collectors.toMap(Employee::getEmployeeCode, Function.identity()));
        checkNotRecorded(today, employeeCodes, employees);

        List<Attendance> rows = new ArrayList<>();
        for (int i = 0; i < request.workers().size(); i++) {
            AttendanceWorkerRequest worker = request.workers().get(i);
            Employee employee = employees.get(employeeCodes.get(i));
            if (employee == null) {
                throw new IllegalArgumentException("Worker not found: " + employeeCodes.get(i));
            }
            rows.add(newAttendance(user, site, employee, worker.attendanceCategory(),
                    UUID.randomUUID(), today, now));
        }
        return save(rows).stream()
                .map(attendance -> AttendanceDto.from(attendance, null))
                .toList();
    }

    /**
     * One phone record in its own transaction. Called by AttendanceSyncService,
     * which turns a thrown validation error into a Rejected result.
     */
    @Transactional
    public SyncAttendanceResult recordSynced(SyncAttendanceRecord record) {
        if (record.attendanceId() == null) {
            throw new IllegalArgumentException("Attendance ID is required");
        }
        // AT-12: already received, e.g. the reply to an earlier send was lost
        if (attendanceRepository.existsById(record.attendanceId())) {
            return SyncAttendanceResult.sent(record.attendanceId());
        }
        AppUser user = userService.getCurrentUser();
        checkRecordedOnItsDate(record);
        AttendanceSite site = siteValidator.resolve(record.estateId(), record.workType(), record.cpCode());

        String employeeCode = record.employeeCode() == null ? "" : record.employeeCode().trim();
        Employee employee = employeeRepository.findById(employeeCode)
                .orElseThrow(() -> new IllegalArgumentException("Worker not found: " + employeeCode));
        checkNotRecorded(record.attendanceDate(), List.of(employeeCode), Map.of(employeeCode, employee));

        save(List.of(newAttendance(user, site, employee, record.attendanceCategory(),
                record.attendanceId(), record.attendanceDate(), record.recordedAt())));
        return SyncAttendanceResult.sent(record.attendanceId());
    }

    /** List / search, limited to the user's visible sites by the estate filter. */
    @Transactional(readOnly = true)
    public List<AttendanceDto> searchAttendance(AttendanceSearchRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate dateFrom = request.dateFrom() != null ? request.dateFrom() : today;
        LocalDate dateTo = request.dateTo() != null ? request.dateTo() : today;
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Date From must be on or before Date To.");
        }

        List<Attendance> rows = attendanceRepository.search(dateFrom, dateTo,
                term(request.employee()), term(request.estateId()), term(request.cpCode()),
                term(request.workType()), term(request.recordedBy()));
        Map<UUID, AttendanceCorrection> lastCorrections = findLastCorrections(
                rows.stream().map(Attendance::getAttendanceId).toList());
        return rows.stream()
                .map(attendance -> AttendanceDto.from(attendance, lastCorrections.get(attendance.getAttendanceId())))
                .toList();
    }

    // AT-11: phone records are accepted later, but only for the date they were recorded on
    private void checkRecordedOnItsDate(SyncAttendanceRecord record) {
        LocalDate today = LocalDate.now(clock);
        if (record.attendanceDate() == null
                || record.recordedAt() == null
                || record.attendanceDate().isAfter(today)
                || record.recordedAt().isAfter(clock.instant().plus(MAX_CLOCK_SKEW))
                || !LocalDate.ofInstant(record.recordedAt(), clock.getZone()).equals(record.attendanceDate())) {
            throw new IllegalArgumentException(AttendanceMessages.AT_11_TODAY_ONLY);
        }
    }

    // AT-02, including the same worker ticked twice in one batch
    private void checkNotRecorded(LocalDate date, List<String> employeeCodes, Map<String, Employee> employees) {
        Set<String> seen = new HashSet<>();
        for (String employeeCode : employeeCodes) {
            if (!seen.add(employeeCode)) {
                throw new IllegalArgumentException(AttendanceMessages.alreadyRecorded(
                        workerLabel(employeeCode, employees.get(employeeCode))));
            }
        }
        List<String> recorded = attendanceRepository.findEmployeeCodesRecordedOn(date, employeeCodes);
        if (!recorded.isEmpty()) {
            String employeeCode = recorded.getFirst();
            throw new IllegalArgumentException(AttendanceMessages.alreadyRecorded(
                    workerLabel(employeeCode, employees.get(employeeCode))));
        }
    }

    private Attendance newAttendance(AppUser user, AttendanceSite site, Employee employee,
                                     AttendanceCategory category, UUID attendanceId,
                                     LocalDate date, Instant recordedAt) {
        String worker = workerLabel(employee.getEmployeeCode(), employee);
        // AT-03
        if (employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new IllegalArgumentException(worker + ": " + AttendanceMessages.AT_03_INACTIVE_WORKER);
        }
        // AT-04
        if ((employee.getStartDate() != null && employee.getStartDate().isAfter(date))
                || (employee.getEndDate() != null && employee.getEndDate().isBefore(date))) {
            throw new IllegalArgumentException(worker + ": " + AttendanceMessages.AT_04_NOT_EMPLOYED);
        }
        // AT-08
        if (category == null) {
            throw new IllegalArgumentException(worker + ": " + AttendanceMessages.AT_08_CATEGORY);
        }
        // TODO(decision CL-03) AT-06: the spec has not decided who may record a worker at a site other
        //  than their default site (AttendanceMessages.otherSite). Until then anyone who can record may.
        String estateId = site.estate().getId();

        Attendance attendance = new Attendance();
        attendance.setAttendanceId(attendanceId);
        attendance.setAttendanceDate(date);
        attendance.setEmployee(employee);
        attendance.setEstateId(estateId);
        // Read-only mapping; set so the returned DTO has the estate without a reload
        attendance.setEstate(site.estate());
        attendance.setHomeEstate(employee.getHomeEstate());
        attendance.setWorkType(site.workType());
        attendance.setCollectionPoint(site.collectionPoint());
        attendance.setAttendanceCategory(category);
        attendance.setRecordedBy(user);
        attendance.setRecordedAt(recordedAt);
        return attendance;
    }

    private List<Attendance> save(List<Attendance> rows) {
        try {
            // The ids are assigned, but the null version makes save() insert rather than merge
            return attendanceRepository.saveAllAndFlush(rows);
        } catch (DataIntegrityViolationException e) {
            // Another phone or user recorded one of these workers after the AT-02 check
            String worker = rows.size() == 1
                    ? workerLabel(rows.getFirst().getEmployee().getEmployeeCode(), rows.getFirst().getEmployee())
                    : "one of the selected workers";
            throw new IllegalArgumentException(AttendanceMessages.alreadyRecorded(worker), e);
        }
    }

    private Map<UUID, AttendanceCorrection> findLastCorrections(List<UUID> attendanceIds) {
        Map<UUID, AttendanceCorrection> lastCorrections = new HashMap<>();
        for (int from = 0; from < attendanceIds.size(); from += LOOKUP_CHUNK_SIZE) {
            List<UUID> chunk = attendanceIds.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, attendanceIds.size()));
            for (AttendanceCorrection correction : attendanceCorrectionRepository.findLatestFor(chunk)) {
                lastCorrections.putIfAbsent(correction.getAttendanceId(), correction);
            }
        }
        return lastCorrections;
    }

    // TODO(decision): Employee.employeeCategory is free text; switch to AttendanceCategory if it becomes an enum
    private static AttendanceCategory defaultCategory(Employee employee) {
        if (!StringUtils.hasText(employee.getEmployeeCategory())) {
            return null;
        }
        try {
            return AttendanceCategory.valueOf(employee.getEmployeeCategory().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String workerLabel(String employeeCode, Employee employee) {
        return employee == null ? employeeCode : employee.getFullName() + " (" + employeeCode + ")";
    }

    private static String idOf(Estate estate) {
        return estate == null ? null : estate.getId();
    }

    private static String term(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
