package com.iritech.irissample.export;

import com.iritech.irissample.model.AttendanceRecord;
import com.iritech.irissample.model.export.AttendanceCell;
import com.iritech.irissample.model.export.AttendanceExportData;
import com.iritech.irissample.model.export.AttendanceMatrixRow;
import com.iritech.irissample.model.export.SessionInfo;
import com.iritech.irissample.model.export.StudentAttendanceSummary;

import java.util.Date;
import java.util.List;

/**
 * Export business logic shared by CSV now and Excel later.
 */
public final class AttendanceExportService {
    private final AttendanceExportRepository repository;

    public AttendanceExportService(AttendanceExportRepository repository) {
        this.repository = repository;
    }

    public AttendanceExportData loadExportData(String subjectId) {
        return repository.loadExportData(subjectId);
    }

    // Legacy detailed CSV format kept for the existing email/report attachment flow.
    public String buildCsv(AttendanceExportData data, String exportDate) {
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF");
        appendSubjectInfoSection(csv, data, exportDate);
        csv.append("Mã sinh viên,Họ và tên,Ngày học,Giờ điểm danh,Mốc muộn,Trạng thái\n");

        List<SessionInfo> sessions = data.getSessions();
        List<AttendanceMatrixRow> rows = data.getMatrixRows();

        if (sessions.isEmpty() || rows.isEmpty()) {
            csv.append("Chưa có dữ liệu sinh viên/buổi học để xuất\n");
            return csv.toString();
        }

        for (AttendanceMatrixRow row : rows) {
            for (SessionInfo session : sessions) {
                AttendanceCell cell = row.getCell(session.getSessionDate());
                if (cell == null) {
                    cell = new AttendanceCell(
                            session.getSessionDate(),
                            com.iritech.irissample.model.AttendanceRecord.STATUS_ABSENT,
                            "",
                            session.getLateCutoffTime()
                    );
                }

                csv.append(escapeCsvField(row.getStudentId())).append(",");
                csv.append(escapeCsvField(row.getFullName())).append(",");
                csv.append(escapeCsvField(session.getSessionDate())).append(",");
                csv.append(escapeCsvField(cell.getCheckinTime())).append(",");
                csv.append(escapeCsvField(cell.getLateCutoffTime())).append(",");
                csv.append(escapeCsvField(getStatusText(cell))).append("\n");
            }
        }

        return csv.toString();
    }

    public String buildSummaryCsv(AttendanceExportData data, String exportDate) {
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF");
        appendSubjectInfoSection(csv, data, exportDate);
        csv.append("STT,");
        csv.append("Mã sinh viên,");
        csv.append("Họ tên sinh viên,");
        csv.append("Email,");
        csv.append("Số điện thoại,");
        csv.append("Mã môn học,");
        csv.append("Tên môn học,");
        csv.append("Tổng số buổi,");
        csv.append("Số buổi có mặt,");
        csv.append("Số buổi đi muộn,");
        csv.append("Số buổi vắng,");
        csv.append("Tỷ lệ đi học %,");
        csv.append("Tỷ lệ đi muộn %\n");

        List<StudentAttendanceSummary> summaries = data.getSummaries();
        int order = 1;
        for (StudentAttendanceSummary summary : summaries) {
            csv.append(order++).append(",");
            csv.append(escapeCsvField(summary.getStudentId())).append(",");
            csv.append(escapeCsvField(summary.getFullName())).append(",");
            csv.append(escapeCsvField(summary.getEmail())).append(",");
            csv.append(escapeCsvField(summary.getPhone())).append(",");
            csv.append(escapeCsvField(summary.getSubjectId())).append(",");
            csv.append(escapeCsvField(summary.getSubjectName())).append(",");
            csv.append(summary.getTotalSessions()).append(",");
            csv.append(summary.getPresentCount()).append(",");
            csv.append(summary.getLateCount()).append(",");
            csv.append(summary.getAbsentCount()).append(",");
            csv.append(formatRate(summary.getAttendanceRate())).append(",");
            csv.append(formatRate(summary.getLateRate())).append("\n");
        }

        return csv.toString();
    }

    public String buildMatrixCsv(AttendanceExportData data, String exportDate) {
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF");
        appendSubjectInfoSection(csv, data, exportDate);
        csv.append("STT,Mã sinh viên,Họ tên sinh viên");

        List<SessionInfo> sessions = data.getSessions();
        for (SessionInfo session : sessions) {
            csv.append(",").append(escapeCsvField(session.getSessionDate()));
        }
        csv.append("\n");

        int order = 1;
        for (AttendanceMatrixRow row : data.getMatrixRows()) {
            csv.append(order++).append(",");
            csv.append(escapeCsvField(row.getStudentId())).append(",");
            csv.append(escapeCsvField(row.getFullName()));

            for (SessionInfo session : sessions) {
                AttendanceCell cell = row.getCell(session.getSessionDate());
                csv.append(",").append(escapeCsvField(getMatrixStatusText(cell)));
            }
            csv.append("\n");
        }

        return csv.toString();
    }

    public String buildCsvFileName(AttendanceExportData data) {
        return "DiemDanh_" + sanitizeFileName(data.getSubjectName()) + "_" +
                new Date().getTime() + ".csv";
    }

    public String buildSummaryCsvFileName(AttendanceExportData data) {
        return "DiemDanh_TongHop_" + sanitizeFileName(data.getSubjectName()) + "_" +
                new Date().getTime() + ".csv";
    }

    public String buildMatrixCsvFileName(AttendanceExportData data) {
        return "DiemDanh_ChiTietBang_" + sanitizeFileName(data.getSubjectName()) + "_" +
                new Date().getTime() + ".csv";
    }

    private static String sanitizeFileName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "MonHoc";
        }

        return value.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private static void appendSubjectInfoSection(
            StringBuilder csv,
            AttendanceExportData data,
            String exportDate
    ) {
        csv.append("Thông tin môn học\n");
        csv.append("Mã môn,").append(escapeCsvField(data.getSubjectId())).append("\n");
        csv.append("Tên môn,").append(escapeCsvField(data.getSubjectName())).append("\n");
        csv.append("Giảng viên,").append(escapeCsvField(data.getInstructorName())).append("\n");
        csv.append("Ngày xuất,").append(escapeCsvField(exportDate)).append("\n");
        csv.append("\n");
    }

    private static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }

        if (field.contains(",")
                || field.contains("\"")
                || field.contains("\n")
                || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }

        return field;
    }

    private static String formatRate(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private static String getStatusText(AttendanceCell cell) {
        if (AttendanceRecord.STATUS_LATE.equals(cell.getStatus())) {
            return "Đi muộn";
        }

        if (AttendanceRecord.STATUS_PRESENT.equals(cell.getStatus())) {
            return "Có mặt";
        }

        return "Vắng";
    }

    private static String getMatrixStatusText(AttendanceCell cell) {
        if (cell == null) {
            return "Chưa có dữ liệu";
        }

        String shortCheckinTime = cell.getShortCheckinTime();
        if (AttendanceRecord.STATUS_LATE.equals(cell.getStatus())) {
            return shortCheckinTime.isEmpty()
                    ? "Đi muộn"
                    : "Đi muộn - " + shortCheckinTime;
        }

        if (AttendanceRecord.STATUS_PRESENT.equals(cell.getStatus())) {
            return shortCheckinTime.isEmpty()
                    ? "Có mặt"
                    : "Có mặt - " + shortCheckinTime;
        }

        return "Vắng";
    }
}
