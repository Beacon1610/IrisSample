package com.iritech.irissample.export;

import com.iritech.irissample.model.export.AttendanceCell;
import com.iritech.irissample.model.export.AttendanceExportData;
import com.iritech.irissample.model.export.AttendanceMatrixRow;
import com.iritech.irissample.model.export.SessionInfo;
import com.iritech.irissample.model.AttendanceRecord;

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

    public String buildCsv(AttendanceExportData data, String exportDate) {
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF");
        csv.append("Thông tin môn học\n");
        csv.append("Mã môn,").append(escapeCsvField(data.getSubjectId())).append("\n");
        csv.append("Tên môn,").append(escapeCsvField(data.getSubjectName())).append("\n");
        csv.append("Giảng viên,").append(escapeCsvField(data.getInstructorName())).append("\n");
        csv.append("Ngày xuất,").append(escapeCsvField(exportDate)).append("\n");
        csv.append("\n");
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

    public String buildCsvFileName(AttendanceExportData data) {
        return "DiemDanh_" + sanitizeFileName(data.getSubjectName()) + "_" +
                new Date().getTime() + ".csv";
    }

    private static String sanitizeFileName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "MonHoc";
        }

        return value.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }

        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }

        return field;
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
}
