package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.user.AppUser;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.WeightAuditDto;
import com.atrum.agrum.weight.dto.WeightChangeDto;
import com.atrum.agrum.weight.dto.WeightHistoryRowDto;
import com.atrum.agrum.weight.dto.WeightHistorySearchRequest;
import com.atrum.agrum.weight.dto.WeightRecordDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Weight History Management (FSD 2.8): search and View History.
 * Limited to the user's visible sites by the estate filter (WH-02).
 * Pickers see only their own weighings (WH-02, TC-68) by being granted searchMine's endpoint
 * instead of the full search.
 */
@Service
@RequiredArgsConstructor
public class WeightHistoryService {

    private static final int MAX_PAGE_SIZE = 200;
    // Keeps the IN list well under Postgres' bind parameter limit
    private static final int LOOKUP_CHUNK_SIZE = 1000;

    private final WeightRecordRepository weightRecordRepository;
    private final WeightChangeRequestRepository weightChangeRequestRepository;
    private final UserService userService;
    private final Clock clock;

    /** Sorted by date/time, EMP No, entry sequence. Cancelled rows are included (greyed on screen). */
    @Transactional(readOnly = true)
    public Page<WeightHistoryRowDto> search(WeightHistorySearchRequest request, Pageable pageable) {
        // The query has its own ORDER BY, so only the page number and size are used
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        Page<WeightRecord> rows = find(request, page);
        Map<UUID, WeightChangeRequest> latest = findLatestRequests(
                rows.getContent().stream().map(WeightRecord::getWeightId).toList());
        return rows.map(record -> WeightHistoryRowDto.from(record, latest.get(record.getWeightId())));
    }

    /** The same search, for the logged-in user's own weighings; any worker filter is replaced. */
    @Transactional(readOnly = true)
    public Page<WeightHistoryRowDto> searchMine(WeightHistorySearchRequest request, Pageable pageable) {
        return search(forCurrentEmployee(request), pageable);
    }

    /** Every row of the logged-in user's own weighings, for the picker's Excel export (TC-85). */
    @Transactional(readOnly = true)
    public List<WeightHistoryRowDto> searchAllMine(WeightHistorySearchRequest request) {
        return searchAll(forCurrentEmployee(request));
    }

    // The picker's login is linked to their employee record; the username is the employee code
    private WeightHistorySearchRequest forCurrentEmployee(WeightHistorySearchRequest request) {
        AppUser user = userService.getCurrentUser();
        if (user.getEmployee() == null) {
            throw new IllegalArgumentException("Your login is not linked to an employee.");
        }
        return new WeightHistorySearchRequest(request.dateFrom(), request.dateTo(),
                user.getEmployee().getEmployeeCode(), request.supervisor(), request.estateId(),
                request.cpCode(), request.workType(), request.attendanceCategory(),
                request.entryType(), request.lineState(), request.approvalStatus());
    }

    /** Every matching row, for the Excel export. */
    @Transactional(readOnly = true)
    public List<WeightHistoryRowDto> searchAll(WeightHistorySearchRequest request) {
        List<WeightRecord> rows = find(request, Pageable.unpaged()).getContent();
        Map<UUID, WeightChangeRequest> latest = findLatestRequests(rows.stream().map(WeightRecord::getWeightId).toList());
        return rows.stream()
                .map(record -> WeightHistoryRowDto.from(record, latest.get(record.getWeightId())))
                .toList();
    }

    /** View History: original weighing and every request, newest first. Refused for an invisible weighing. */
    @Transactional(readOnly = true)
    public WeightAuditDto getAudit(UUID weightId) {
        WeightRecord record = weightRecordRepository.findVisibleById(weightId)
                .orElseThrow(() -> new IllegalArgumentException("Weight record not found: " + weightId));
        List<WeightChangeDto> changes = weightChangeRequestRepository.findHistory(weightId).stream()
                .map(WeightChangeDto::from)
                .toList();
        return new WeightAuditDto(WeightRecordDto.from(record), changes);
    }

    private Page<WeightRecord> find(WeightHistorySearchRequest request, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        LocalDate dateFrom = request.dateFrom() != null ? request.dateFrom() : today;
        LocalDate dateTo = request.dateTo() != null ? request.dateTo() : today;
        // WH-01
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException(WeightMessages.WH_01_DATES);
        }
        return weightRecordRepository.search(dateFrom, dateTo,
                term(request.employeeCode()), term(request.supervisor()), term(request.estateId()),
                term(request.cpCode()), term(request.workType()),
                oneOrAll(request.attendanceCategory(), AttendanceCategory.class),
                oneOrAll(request.entryType(), EntryType.class),
                oneOrAll(request.lineState(), LineState.class),
                oneOrAll(request.approvalStatus(), ApprovalStatus.class),
                pageable);
    }

    private Map<UUID, WeightChangeRequest> findLatestRequests(List<UUID> weightIds) {
        Map<UUID, WeightChangeRequest> latest = new HashMap<>();
        for (int from = 0; from < weightIds.size(); from += LOOKUP_CHUNK_SIZE) {
            List<UUID> chunk = weightIds.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, weightIds.size()));
            for (WeightChangeRequest request : weightChangeRequestRepository.findLatestFor(chunk)) {
                latest.put(request.getWeight().getWeightId(), request);
            }
        }
        return latest;
    }

    // The query takes the allowed values, so no filter means every value
    private static <E extends Enum<E>> Set<E> oneOrAll(E value, Class<E> type) {
        return value == null ? EnumSet.allOf(type) : EnumSet.of(value);
    }

    private static String term(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
