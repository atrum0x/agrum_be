package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.user.UserService;
import com.atrum.agrum.weight.dto.DailyWeightSummarySearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyWeightSummaryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T16:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    @Mock
    private DailyWeightSummaryRepository dailyWeightSummaryRepository;
    @Mock
    private JobRunStateRepository jobRunStateRepository;
    @Mock
    private UserService userService;

    private DailyWeightSummaryService service;

    @BeforeEach
    void setUp() {
        service = new DailyWeightSummaryService(dailyWeightSummaryRepository, jobRunStateRepository,
                userService, Clock.fixed(NOW, ZoneId.of("Asia/Colombo")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void TC62_TC63_rebuild_replacesTheDateWithClassifiedRows() {
        when(dailyWeightSummaryRepository.calculateTotals(TODAY)).thenReturn(List.of(
                total("E001", 2, "25.50"),
                total("E002", 2, "16.66")));

        assertThat(service.rebuild(TODAY, NOW)).isEqualTo(2);

        InOrder order = inOrder(dailyWeightSummaryRepository);
        order.verify(dailyWeightSummaryRepository).deleteForDate(TODAY);
        ArgumentCaptor<List<DailyWeightSummary>> saved = ArgumentCaptor.forClass(List.class);
        order.verify(dailyWeightSummaryRepository).saveAll(saved.capture());

        DailyWeightSummary e001 = saved.getValue().get(0);
        assertThat(e001.getSummaryDate()).isEqualTo(TODAY);
        assertThat(e001.getEstateId()).isEqualTo("S01");
        assertThat(e001.getAttendanceCategory()).isEqualTo(AttendanceCategory.REGULAR);
        assertThat(e001.getTransactionCount()).isEqualTo(2);
        assertThat(e001.getDailyTotalKg()).isEqualTo("25.50");
        assertThat(e001.getDayClassification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(e001.getOverweightKg()).isEqualTo("2.50");
        assertThat(e001.getCalculatedAt()).isEqualTo(NOW);

        DailyWeightSummary e002 = saved.getValue().get(1);
        assertThat(e002.getDayClassification()).isEqualTo(DayClassification.HALF_DAY);
        assertThat(e002.getOverweightKg()).isEqualTo("4.66");
    }

    @Test
    void search_defaultsToYesterday() {
        service.search(new DailyWeightSummarySearchRequest(null, null, null, null, null, null));

        verify(dailyWeightSummaryRepository).search(eq(TODAY.minusDays(1)), eq(TODAY.minusDays(1)),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void DS01_dateFromAfterDateTo_isRejected() {
        assertThatThrownBy(() -> service.search(
                new DailyWeightSummarySearchRequest(TODAY, TODAY.minusDays(1), null, null, null, null)))
                .hasMessage(WeightMessages.DS_01_DATES);
    }

    private static DailyWeightSummaryRepository.DailyTotal total(String employeeCode, long count, String kg) {
        return new DailyWeightSummaryRepository.DailyTotal() {
            public String getEmployeeCode() { return employeeCode; }
            public String getEstateId() { return "S01"; }
            public String getCollectionPointCode() { return "CP1"; }
            public String getWorkType() { return "PLUCKING"; }
            public String getAttendanceCategory() { return "REGULAR"; }
            public Long getTransactionCount() { return count; }
            public BigDecimal getDailyTotalKg() { return new BigDecimal(kg); }
            public BigDecimal getFullDayQtyKg() { return new BigDecimal("23.00"); }
            public BigDecimal getHalfDayQtyKg() { return new BigDecimal("12.00"); }
        };
    }
}
