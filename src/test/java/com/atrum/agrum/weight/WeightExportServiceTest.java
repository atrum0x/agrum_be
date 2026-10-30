package com.atrum.agrum.weight;

import com.atrum.agrum.attendance.AttendanceCategory;
import com.atrum.agrum.weight.dto.DailyWeightSummaryDto;
import com.atrum.agrum.weight.dto.WeightHistoryRowDto;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightExportServiceTest {

    // 24/10/2026 15:05 in Colombo
    private static final Instant NOW = Instant.parse("2026-10-24T09:35:00Z");
    private final WeightExportService exports = new WeightExportService(Clock.fixed(NOW, ZoneId.of("Asia/Colombo")));

    @Test
    void TC83_historyExport_hasTwentyColumnsAndRealValues() throws IOException {
        WeightHistoryRowDto changed = historyRow(LineState.RECORDED, ApprovalStatus.APPROVED, true);
        WeightHistoryRowDto cancelled = historyRow(LineState.CANCELLED, ApprovalStatus.APPROVED, false);

        WeightExportService.ExcelFile file = exports.weightHistory(List.of(changed, cancelled));

        assertThat(file.fileName()).isEqualTo("AgriWeight_WeightHistory_20261024_1505.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            XSSFSheet sheet = workbook.getSheet("Weight History");
            Row header = sheet.getRow(0);
            assertThat(header.getLastCellNum()).isEqualTo((short) 20);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Date");
            assertThat(header.getCell(19).getStringCellValue()).isEqualTo("Weight Record ID");
            assertThat(workbook.getFontAt(header.getCell(0).getCellStyle().getFontIndex()).getBold()).isTrue();
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isTrue();

            Row row = sheet.getRow(1);
            // Date and time are real Excel values in Colombo time
            assertThat(DateUtil.isCellDateFormatted(row.getCell(0))).isTrue();
            assertThat(row.getCell(0).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 10, 24, 0, 0));
            assertThat(row.getCell(0).getCellStyle().getDataFormatString()).isEqualTo("dd/mm/yyyy");
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo((10 * 3600 + 15 * 60) / 86_400.0);
            assertThat(row.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("hh:mm");
            assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(2);
            assertThat(row.getCell(11).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(11).getNumericCellValue()).isEqualTo(14.00);
            assertThat(row.getCell(11).getCellStyle().getDataFormatString()).isEqualTo("0.00");
            assertThat(row.getCell(12).getCellStyle().getDataFormatString()).isEqualTo("+0.00;-0.00;0.00");
            assertThat(row.getCell(13).getNumericCellValue()).isEqualTo(14.28);
            assertThat(row.getCell(14).getStringCellValue()).isEqualTo("Recorded");
            assertThat(row.getCell(15).getStringCellValue()).isEqualTo("Approved");
            assertThat(row.getCell(16).getStringCellValue()).isEqualTo("sup1 24/10/2026 12:00");
            assertThat(row.getCell(18).getStringCellValue()).isEqualTo("approver1 24/10/2026 14:00");
            assertThat(row.getCell(0).getCellStyle().getFillPattern()).isEqualTo(FillPatternType.NO_FILL);

            // TC-70 / TC-71: cancelled rows are included and greyed; empty change cells show "—"
            Row cancelledRow = sheet.getRow(2);
            assertThat(cancelledRow.getCell(14).getStringCellValue()).isEqualTo("Cancelled");
            assertThat(cancelledRow.getCell(0).getCellStyle().getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(cancelledRow.getCell(16).getStringCellValue()).isEqualTo("—");
            assertThat(cancelledRow.getCell(17).getStringCellValue()).isEqualTo("—");
            assertThat(cancelledRow.getCell(18).getStringCellValue()).isEqualTo("—");
        }
    }

    @Test
    void TC84_summaryExport_hasTwelveColumnsAndNoTotalsRow() throws IOException {
        DailyWeightSummaryDto row = new DailyWeightSummaryDto(LocalDate.of(2026, 10, 23), "E001", "Kamala Perera",
                AttendanceCategory.REGULAR, "S01", "Estate One", "CP1", "PLUCKING", 2,
                new BigDecimal("25.50"), DayClassification.FULL_DAY, new BigDecimal("2.50"),
                Instant.parse("2026-10-23T16:30:00Z"));

        WeightExportService.ExcelFile file = exports.dailySummary(List.of(row));

        assertThat(file.fileName()).isEqualTo("AgriWeight_DailySummary_20261024_1505.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            Sheet sheet = workbook.getSheet("Daily Summary");
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 12);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            Row data = sheet.getRow(1);
            assertThat(data.getCell(0).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 10, 23, 0, 0));
            assertThat(data.getCell(7).getNumericCellValue()).isEqualTo(2);
            assertThat(data.getCell(8).getNumericCellValue()).isEqualTo(25.50);
            assertThat(data.getCell(9).getStringCellValue()).isEqualTo("Full Day");
            assertThat(data.getCell(10).getNumericCellValue()).isEqualTo(2.50);
            // 22:00 Colombo
            assertThat(data.getCell(11).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 10, 23, 22, 0));
            assertThat(data.getCell(11).getCellStyle().getDataFormatString()).isEqualTo("dd/mm/yyyy hh:mm");
        }
    }

    @Test
    void TC86_noMatches_isRefusedWithoutAFile() {
        assertThatThrownBy(() -> exports.weightHistory(List.of())).hasMessage("No records to export.");
        assertThatThrownBy(() -> exports.dailySummary(List.of())).hasMessage("No records to export.");
    }

    private static WeightHistoryRowDto historyRow(LineState lineState, ApprovalStatus status, boolean withChange) {
        return new WeightHistoryRowDto(UUID.randomUUID(), LocalDate.of(2026, 10, 24),
                Instant.parse("2026-10-24T04:45:00Z"), 2, "E001", "Kamala Perera", AttendanceCategory.REGULAR,
                "sup1", "Sunil Silva", "S01", "Estate One", "CP1", "Main CP", "PLUCKING", EntryType.AUTOMATIC, null, null,
                new BigDecimal("13.00"), new BigDecimal("14.00"), new BigDecimal("2.00"), new BigDecimal("14.28"),
                lineState, status,
                withChange ? 7L : null,
                withChange ? ChangeType.CORRECTION : null,
                withChange ? ApprovalStatus.APPROVED : null,
                withChange ? new BigDecimal("14.00") : null,
                withChange ? "Scale misread" : null,
                withChange ? "sup1" : null,
                withChange ? Instant.parse("2026-10-24T06:30:00Z") : null,
                withChange ? "approver1" : null,
                withChange ? Instant.parse("2026-10-24T08:30:00Z") : null,
                1);
    }
}
