package com.iritech.irissample;

import com.iritech.irissample.model.AttendanceRecord;
import com.iritech.irissample.model.export.AttendanceCell;
import com.iritech.irissample.model.export.AttendanceExportData;
import com.iritech.irissample.model.export.AttendanceMatrixRow;
import com.iritech.irissample.model.export.SessionInfo;
import com.iritech.irissample.model.export.StudentAttendanceSummary;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class AttendanceExcelExporter {
    private static final int DATA_HEADER_ROW = 7;
    private static final int DATA_FIRST_ROW = DATA_HEADER_ROW + 1;

    public void export(
            OutputStream outputStream,
            AttendanceExportData data,
            String exportDate
    ) throws IOException {
        if (outputStream == null) {
            throw new IOException("OutputStream is null");
        }
        if (data == null) {
            throw new IOException("Attendance export data is null");
        }

        List<StudentAttendanceSummary> safeSummaries =
                data.getSummaries() == null ? Collections.emptyList() : data.getSummaries();
        List<AttendanceMatrixRow> safeMatrixRows =
                data.getMatrixRows() == null ? Collections.emptyList() : data.getMatrixRows();
        List<SessionInfo> safeSessions =
                data.getSessions() == null ? Collections.emptyList() : data.getSessions();

        ZipOutputStream zip = new ZipOutputStream(outputStream);
        writeEntry(zip, "[Content_Types].xml", buildContentTypesXml());
        writeEntry(zip, "_rels/.rels", buildRootRelationshipsXml());
        writeEntry(zip, "xl/workbook.xml", buildWorkbookXml());
        writeEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelationshipsXml());
        writeEntry(zip, "xl/styles.xml", buildStylesXml());
        writeEntry(zip, "xl/worksheets/sheet1.xml",
                buildSummarySheetXml(data, safe(exportDate), safeSummaries));
        writeEntry(zip, "xl/worksheets/sheet2.xml",
                buildDetailSheetXml(data, safe(exportDate), safeMatrixRows, safeSessions));
        zip.finish();
        zip.flush();
    }

    private static String buildSummarySheetXml(
            AttendanceExportData data,
            String exportDate,
            List<StudentAttendanceSummary> summaries
    ) {
        StringBuilder xml = new StringBuilder();
        appendWorksheetStart(xml);
        xml.append("<sheetData>");

        appendSubjectInfoRows(xml, data, exportDate);

        appendRowStart(xml, DATA_HEADER_ROW);
        appendStringCell(xml, 1, DATA_HEADER_ROW, "STT", 1);
        appendStringCell(xml, 2, DATA_HEADER_ROW, "Mã sinh viên", 1);
        appendStringCell(xml, 3, DATA_HEADER_ROW, "Họ tên sinh viên", 1);
        appendStringCell(xml, 4, DATA_HEADER_ROW, "Email", 1);
        appendStringCell(xml, 5, DATA_HEADER_ROW, "Số điện thoại", 1);
        appendStringCell(xml, 6, DATA_HEADER_ROW, "Mã môn học", 1);
        appendStringCell(xml, 7, DATA_HEADER_ROW, "Tên môn học", 1);
        appendStringCell(xml, 8, DATA_HEADER_ROW, "Tổng số buổi", 1);
        appendStringCell(xml, 9, DATA_HEADER_ROW, "Số buổi có mặt", 1);
        appendStringCell(xml, 10, DATA_HEADER_ROW, "Số buổi đi muộn", 1);
        appendStringCell(xml, 11, DATA_HEADER_ROW, "Số buổi vắng", 1);
        appendStringCell(xml, 12, DATA_HEADER_ROW, "Tỷ lệ đi học %", 1);
        appendStringCell(xml, 13, DATA_HEADER_ROW, "Tỷ lệ đi muộn %", 1);
        appendRowEnd(xml);

        int rowIndex = DATA_FIRST_ROW;
        int order = 1;
        for (StudentAttendanceSummary summary : summaries) {
            appendRowStart(xml, rowIndex);
            appendNumberCell(xml, 1, rowIndex, order, 0);
            appendStringCell(xml, 2, rowIndex, safe(summary.getStudentId()), 0);
            appendStringCell(xml, 3, rowIndex, safe(summary.getFullName()), 0);
            appendStringCell(xml, 4, rowIndex, safe(summary.getEmail()), 0);
            appendStringCell(xml, 5, rowIndex, safe(summary.getPhone()), 0);
            appendStringCell(xml, 6, rowIndex, safe(summary.getSubjectId()), 0);
            appendStringCell(xml, 7, rowIndex, safe(summary.getSubjectName()), 0);
            appendNumberCell(xml, 8, rowIndex, summary.getTotalSessions(), 0);
            appendNumberCell(xml, 9, rowIndex, summary.getPresentCount(), 0);
            appendNumberCell(xml, 10, rowIndex, summary.getLateCount(), 0);
            appendNumberCell(xml, 11, rowIndex, summary.getAbsentCount(), 0);
            appendDecimalCell(xml, 12, rowIndex, summary.getAttendanceRate());
            appendDecimalCell(xml, 13, rowIndex, summary.getLateRate());
            appendRowEnd(xml);

            rowIndex++;
            order++;
        }

        xml.append("</sheetData>");
        appendWorksheetEnd(xml);
        return xml.toString();
    }

    private static String buildDetailSheetXml(
            AttendanceExportData data,
            String exportDate,
            List<AttendanceMatrixRow> matrixRows,
            List<SessionInfo> sessions
    ) {
        StringBuilder xml = new StringBuilder();
        appendWorksheetStart(xml);
        xml.append("<sheetData>");

        appendSubjectInfoRows(xml, data, exportDate);

        appendRowStart(xml, DATA_HEADER_ROW);
        appendStringCell(xml, 1, DATA_HEADER_ROW, "STT", 1);
        appendStringCell(xml, 2, DATA_HEADER_ROW, "Mã sinh viên", 1);
        appendStringCell(xml, 3, DATA_HEADER_ROW, "Họ tên sinh viên", 1);

        int headerColumn = 4;
        for (SessionInfo session : sessions) {
            appendStringCell(xml, headerColumn, DATA_HEADER_ROW, safe(session.getSessionDate()), 1);
            headerColumn++;
        }
        appendRowEnd(xml);

        int rowIndex = DATA_FIRST_ROW;
        int order = 1;
        for (AttendanceMatrixRow row : matrixRows) {
            appendRowStart(xml, rowIndex);
            appendNumberCell(xml, 1, rowIndex, order, 0);
            appendStringCell(xml, 2, rowIndex, safe(row.getStudentId()), 0);
            appendStringCell(xml, 3, rowIndex, safe(row.getFullName()), 0);

            int columnIndex = 4;
            for (SessionInfo session : sessions) {
                AttendanceCell cell = row.getCell(session.getSessionDate());
                appendStringCell(xml, columnIndex, rowIndex, buildAttendanceText(cell), 0);
                columnIndex++;
            }

            appendRowEnd(xml);
            rowIndex++;
            order++;
        }

        xml.append("</sheetData>");
        appendWorksheetEnd(xml);
        return xml.toString();
    }

    private static String buildAttendanceText(AttendanceCell cell) {
        if (cell == null) {
            return "Chưa có dữ liệu";
        }

        String status = cell.getStatus();
        String checkinTime = shortTime(cell.getCheckinTime());

        if (AttendanceRecord.STATUS_PRESENT.equals(status)) {
            return checkinTime.isEmpty() ? "Có mặt" : "Có mặt - " + checkinTime;
        }

        if (AttendanceRecord.STATUS_LATE.equals(status)) {
            return checkinTime.isEmpty() ? "Đi muộn" : "Đi muộn - " + checkinTime;
        }

        if (AttendanceRecord.STATUS_ABSENT.equals(status)) {
            return "Vắng";
        }

        return "Chưa có dữ liệu";
    }

    private static void appendWorksheetStart(StringBuilder xml) {
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<sheetViews><sheetView workbookViewId=\"0\">");
        xml.append("<pane ySplit=\"")
                .append(DATA_HEADER_ROW)
                .append("\" topLeftCell=\"A")
                .append(DATA_FIRST_ROW)
                .append("\" activePane=\"bottomLeft\" state=\"frozen\"/>");
        xml.append("</sheetView></sheetViews>");
    }

    private static void appendWorksheetEnd(StringBuilder xml) {
        xml.append("</worksheet>");
    }

    private static void appendRowStart(StringBuilder xml, int rowIndex) {
        xml.append("<row r=\"").append(rowIndex).append("\">");
    }

    private static void appendRowEnd(StringBuilder xml) {
        xml.append("</row>");
    }

    private static void appendSubjectInfoRows(
            StringBuilder xml,
            AttendanceExportData data,
            String exportDate
    ) {
        appendRowStart(xml, 1);
        appendStringCell(xml, 1, 1, "Thông tin môn học", 1);
        appendRowEnd(xml);

        appendMetadataRow(xml, 2, "Mã môn", data.getSubjectId());
        appendMetadataRow(xml, 3, "Tên môn", data.getSubjectName());
        appendMetadataRow(xml, 4, "Giảng viên", data.getInstructorName());
        appendMetadataRow(xml, 5, "Ngày xuất", exportDate);
    }

    private static void appendMetadataRow(
            StringBuilder xml,
            int rowIndex,
            String label,
            String value
    ) {
        appendRowStart(xml, rowIndex);
        appendStringCell(xml, 1, rowIndex, label, 1);
        appendStringCell(xml, 2, rowIndex, safe(value), 0);
        appendRowEnd(xml);
    }

    private static void appendStringCell(
            StringBuilder xml,
            int columnIndex,
            int rowIndex,
            String value,
            int styleIndex
    ) {
        xml.append("<c r=\"").append(cellRef(columnIndex, rowIndex)).append("\" t=\"inlineStr\"");
        if (styleIndex > 0) {
            xml.append(" s=\"").append(styleIndex).append("\"");
        }
        xml.append("><is><t>").append(escapeXml(value)).append("</t></is></c>");
    }

    private static void appendNumberCell(
            StringBuilder xml,
            int columnIndex,
            int rowIndex,
            int value,
            int styleIndex
    ) {
        xml.append("<c r=\"").append(cellRef(columnIndex, rowIndex)).append("\"");
        if (styleIndex > 0) {
            xml.append(" s=\"").append(styleIndex).append("\"");
        }
        xml.append("><v>").append(value).append("</v></c>");
    }

    private static void appendDecimalCell(
            StringBuilder xml,
            int columnIndex,
            int rowIndex,
            double value
    ) {
        xml.append("<c r=\"").append(cellRef(columnIndex, rowIndex)).append("\" s=\"2\"><v>");
        xml.append(String.format(Locale.US, "%.6f", value));
        xml.append("</v></c>");
    }

    private static String cellRef(int columnIndex, int rowIndex) {
        return columnName(columnIndex) + rowIndex;
    }

    private static String columnName(int columnIndex) {
        StringBuilder name = new StringBuilder();
        int current = columnIndex;
        while (current > 0) {
            current--;
            name.insert(0, (char) ('A' + current % 26));
            current /= 26;
        }
        return name.toString();
    }

    private static String shortTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String trimmed = value.trim();
        return trimmed.length() >= 5 ? trimmed.substring(0, 5) : trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String escapeXml(String value) {
        String safeValue = safe(value);
        StringBuilder escaped = new StringBuilder();

        for (int i = 0; i < safeValue.length(); i++) {
            char c = safeValue.charAt(i);
            switch (c) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&apos;");
                    break;
                default:
                    if (c == '\n' || c == '\r' || c == '\t' || c >= 0x20) {
                        escaped.append(c);
                    }
                    break;
            }
        }

        return escaped.toString();
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String buildContentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private static String buildRootRelationshipsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String buildWorkbookXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>"
                + "<sheet name=\"Thông tin sinh viên\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "<sheet name=\"Chi tiết điểm danh\" sheetId=\"2\" r:id=\"rId2\"/>"
                + "</sheets>"
                + "</workbook>";
    }

    private static String buildWorkbookRelationshipsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String buildStylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.##\"/></numFmts>"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9EAF7\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"3\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "<dxfs count=\"0\"/>"
                + "<tableStyles count=\"0\" defaultTableStyle=\"TableStyleMedium9\" defaultPivotStyle=\"PivotStyleLight16\"/>"
                + "</styleSheet>";
    }
}
