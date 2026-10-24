package com.atrum.agrum.weight;

import com.atrum.agrum.weight.dto.DailyWeightSummaryDto;
import com.atrum.agrum.weight.dto.WeightHistoryRowDto;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The two Excel exports (FSD section 9): every matching row, real dates and numbers,
 * bold frozen header with a filter. An empty result is refused (DS-04) and no file is made.
 */
@Service
@RequiredArgsConstructor
public class WeightExportService {

    public static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String EMPTY = "—";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
    private static final DateTimeFormatter BY_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int ROWS_IN_MEMORY = 200;

    private static final String[] HISTORY_COLUMNS = {
            "Date", "Time", "Entry Sequence", "Worker EMP No", "Worker Name", "Employment Category",
            "Supervisor", "Site", "Collection Point", "Work Type", "Entry Type", "Weight Picked (kg)",
            "Variance (%)", "Weight After Variance (kg)", "Line State", "Approval Status",
            "Requested By / At", "Change Comment", "Approved / Disapproved By / At", "Weight Record ID"
    };
    private static final String[] SUMMARY_COLUMNS = {
            "Date", "Worker EMP No", "Worker Name", "Employment Category", "Site", "Collection Point",
            "Work Type", "No. of Transactions", "Daily Total After Variance (kg)", "Day Classification",
            "Overweight (kg)", "Last Updated"
    };

    private final Clock clock;

    public record ExcelFile(String fileName, byte[] content) {

        public ResponseEntity<byte[]> toResponse() {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(fileName).build().toString())
                    .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                    .body(content);
        }
    }

    /** 9.1 – cancelled rows included, greyed. */
    public ExcelFile weightHistory(List<WeightHistoryRowDto> rows) {
        requireRows(rows);
        return write("AgriWeight_WeightHistory_", "Weight History", HISTORY_COLUMNS, sheet -> {
            int r = 1;
            for (WeightHistoryRowDto row : rows) {
                Writer w = sheet.row(r++, row.lineState() == LineState.CANCELLED);
                LocalDateTime weighedAt = LocalDateTime.ofInstant(row.weighedAt(), clock.getZone());
                w.date(weighedAt.toLocalDate().atStartOfDay());
                w.time(weighedAt);
                w.integer(row.entrySequence());
                w.text(row.employeeCode());
                w.text(row.employeeName());
                w.text(label(row.attendanceCategory()));
                w.text(row.supervisorName());
                w.text(row.estateName());
                w.text(row.cpName() != null ? row.cpName() : row.cpCode());
                w.text(row.workType());
                w.text(label(row.entryType()));
                w.decimal(row.weightKg(), Format.WEIGHT);
                w.decimal(row.variancePercent(), Format.VARIANCE);
                w.decimal(row.weightAfterVariance(), Format.WEIGHT);
                w.text(label(row.lineState()));
                w.text(label(row.approvalStatus()));
                w.text(byAt(row.requestedBy(), row.requestedAt()));
                w.text(row.changeComment() == null ? EMPTY : row.changeComment());
                w.text(byAt(row.decidedBy(), row.decidedAt()));
                w.text(row.weightId().toString());
            }
        });
    }

    /** 9.2 – no totals row. */
    public ExcelFile dailySummary(List<DailyWeightSummaryDto> rows) {
        requireRows(rows);
        return write("AgriWeight_DailySummary_", "Daily Summary", SUMMARY_COLUMNS, sheet -> {
            int r = 1;
            for (DailyWeightSummaryDto row : rows) {
                Writer w = sheet.row(r++, false);
                w.date(row.summaryDate().atStartOfDay());
                w.text(row.employeeCode());
                w.text(row.employeeName());
                w.text(label(row.attendanceCategory()));
                w.text(row.estateName());
                w.text(row.cpCode());
                w.text(row.workType());
                w.integer(row.transactionCount());
                w.decimal(row.dailyTotalKg(), Format.WEIGHT);
                w.text(label(row.dayClassification()));
                w.decimal(row.overweightKg(), Format.WEIGHT);
                w.dateTime(LocalDateTime.ofInstant(row.calculatedAt(), clock.getZone()));
            }
        });
    }

    // DS-04
    private static void requireRows(List<?> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(WeightMessages.DS_04_NO_RECORDS);
        }
    }

    private ExcelFile write(String filePrefix, String sheetName, String[] columns, Consumer<Sheet> body) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROWS_IN_MEMORY);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SXSSFSheet poiSheet = workbook.createSheet(sheetName);
            Sheet sheet = new Sheet(workbook, poiSheet);
            Row header = poiSheet.createRow(0);
            CellStyle headerStyle = sheet.style(Format.HEADER, false);
            for (int c = 0; c < columns.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(columns[c]);
                cell.setCellStyle(headerStyle);
                poiSheet.setColumnWidth(c, Math.max(12, columns[c].length() + 4) * 256);
            }
            body.accept(sheet);
            poiSheet.createFreezePane(0, 1);
            poiSheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.length - 1));
            workbook.write(out);
            String stamp = LocalDateTime.now(clock).format(FILE_STAMP);
            return new ExcelFile(filePrefix + stamp + ".xlsx", out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            workbook.dispose();
        }
    }

    // "PENDING_AUTHORIZATION" -> "Pending Authorization"
    private static String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        StringBuilder label = new StringBuilder();
        for (String word : value.name().split("_")) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return label.toString();
    }

    private String byAt(String user, Instant at) {
        if (user == null || at == null) {
            return EMPTY;
        }
        return user + " " + LocalDateTime.ofInstant(at, clock.getZone()).format(BY_AT);
    }

    private enum Format {
        HEADER(null), TEXT(null), DATE("dd/mm/yyyy"), TIME("hh:mm"), DATE_TIME("dd/mm/yyyy hh:mm"),
        INTEGER("0"), WEIGHT("0.00"), VARIANCE("+0.00;-0.00;0.00");

        private final String pattern;

        Format(String pattern) {
            this.pattern = pattern;
        }
    }

    /** Styles are made once per workbook; Excel limits how many a file can hold. */
    private static final class Sheet {
        private final SXSSFWorkbook workbook;
        private final SXSSFSheet sheet;
        private final Map<String, CellStyle> styles = new HashMap<>();

        Sheet(SXSSFWorkbook workbook, SXSSFSheet sheet) {
            this.workbook = workbook;
            this.sheet = sheet;
        }

        Writer row(int index, boolean greyed) {
            return new Writer(this, sheet.createRow(index), greyed);
        }

        CellStyle style(Format format, boolean greyed) {
            return styles.computeIfAbsent(format + ":" + greyed, key -> {
                CellStyle style = workbook.createCellStyle();
                if (format.pattern != null) {
                    style.setDataFormat(workbook.createDataFormat().getFormat(format.pattern));
                }
                if (format == Format.HEADER) {
                    Font bold = workbook.createFont();
                    bold.setBold(true);
                    style.setFont(bold);
                }
                if (greyed) {
                    style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                return style;
            });
        }
    }

    /** Writes one row's cells left to right. */
    private static final class Writer {
        private final Sheet sheet;
        private final Row row;
        private final boolean greyed;
        private int column;

        Writer(Sheet sheet, Row row, boolean greyed) {
            this.sheet = sheet;
            this.row = row;
            this.greyed = greyed;
        }

        void text(String value) {
            next(Format.TEXT).setCellValue(value == null ? "" : value);
        }

        void integer(int value) {
            next(Format.INTEGER).setCellValue(value);
        }

        void decimal(BigDecimal value, Format format) {
            Cell cell = next(format);
            if (value != null) {
                cell.setCellValue(value.doubleValue());
            }
        }

        void date(LocalDateTime value) {
            next(Format.DATE).setCellValue(value);
        }

        void dateTime(LocalDateTime value) {
            next(Format.DATE_TIME).setCellValue(value);
        }

        // Time of day as Excel's fraction of a day, so it sorts and filters as a time
        void time(LocalDateTime value) {
            next(Format.TIME).setCellValue(value.toLocalTime().toSecondOfDay() / 86_400.0);
        }

        private Cell next(Format format) {
            Cell cell = row.createCell(column++);
            cell.setCellStyle(sheet.style(format, greyed));
            return cell;
        }
    }
}
