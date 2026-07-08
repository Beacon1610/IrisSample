package com.iritech.irissample.export;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.iritech.irissample.DatabaseHelper;
import com.iritech.irissample.model.AttendanceRecord;
import com.iritech.irissample.model.export.AttendanceCell;
import com.iritech.irissample.model.export.AttendanceExportData;
import com.iritech.irissample.model.export.AttendanceMatrixRow;
import com.iritech.irissample.model.export.SessionInfo;
import com.iritech.irissample.model.export.StudentAttendanceSummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access layer for attendance exports.
 */
public final class AttendanceExportRepository {
    private final DatabaseHelper dbHelper;

    public AttendanceExportRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public AttendanceExportData loadExportData(String subjectId) {
        SubjectInfo subjectInfo = loadSubjectInfo(subjectId);
        List<SessionInfo> sessions = loadSessions(subjectId);
        List<AttendanceMatrixRow> matrixRows = loadMatrixRows(subjectId, sessions);
        List<StudentAttendanceSummary> summaries = buildSummaries(sessions, matrixRows);

        return new AttendanceExportData(
                subjectId,
                subjectInfo.subjectName,
                subjectInfo.instructorName,
                sessions,
                summaries,
                matrixRows
        );
    }

    private SubjectInfo loadSubjectInfo(String subjectId) {
        String subjectName = "";
        String instructorName = "Chưa phân công";

        if (isBlank(subjectId)) {
            return new SubjectInfo(subjectName, instructorName);
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT sub." + DatabaseHelper.COL_SUBJECT_NAME + ", " +
                "adm." + DatabaseHelper.COL_ADMIN_FULL_NAME + " " +
                "FROM " + DatabaseHelper.TABLE_SUBJECTS + " sub " +
                "LEFT JOIN " + DatabaseHelper.TABLE_ADMIN + " adm ON sub." +
                DatabaseHelper.COL_SUBJECT_INSTRUCTOR_ID + " = adm." +
                DatabaseHelper.COL_ADMIN_ID + " " +
                "WHERE sub." + DatabaseHelper.COL_SUBJECT_ID + " = ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{subjectId})) {
            if (cursor.moveToFirst()) {
                subjectName = cursor.isNull(0) ? "" : cursor.getString(0);
                instructorName = cursor.isNull(1) ? "Chưa phân công" : cursor.getString(1);
            }
        }

        return new SubjectInfo(subjectName, instructorName);
    }

    public List<SessionInfo> loadSessions(String subjectId) {
        List<SessionInfo> sessions = new ArrayList<>();

        if (isBlank(subjectId)) {
            return sessions;
        }

        String query = "SELECT " + DatabaseHelper.COL_SESSION_DATE + ", " +
                DatabaseHelper.COL_LATE_CUTOFF_TIME + " " +
                "FROM " + DatabaseHelper.TABLE_CLASS_SESSIONS + " " +
                "WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ? " +
                "ORDER BY substr(" + DatabaseHelper.COL_SESSION_DATE + ", 7, 4) || '-' || " +
                "substr(" + DatabaseHelper.COL_SESSION_DATE + ", 4, 2) || '-' || " +
                "substr(" + DatabaseHelper.COL_SESSION_DATE + ", 1, 2)";

        try (Cursor cursor = dbHelper.getReadableDatabase().rawQuery(
                query,
                new String[]{subjectId}
        )) {
            while (cursor.moveToNext()) {
                sessions.add(new SessionInfo(
                        cursor.isNull(0) ? "" : cursor.getString(0),
                        cursor.isNull(1) ? "" : cursor.getString(1)
                ));
            }
        }

        return sessions;
    }

    public List<AttendanceMatrixRow> loadMatrixRows(
            String subjectId,
            List<SessionInfo> sessions
    ) {
        Map<String, AttendanceMatrixRow> rowsByStudentId = loadStudents(subjectId);

        if (rowsByStudentId.isEmpty() || sessions == null || sessions.isEmpty()) {
            return new ArrayList<>(rowsByStudentId.values());
        }

        String query = "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", " +
                "cs." + DatabaseHelper.COL_SESSION_DATE + ", " +
                "fc.first_checkin_time, " +
                "cs." + DatabaseHelper.COL_LATE_CUTOFF_TIME + " " +
                "FROM " + DatabaseHelper.TABLE_ENROLLMENTS + " e " +
                "JOIN " + DatabaseHelper.TABLE_STUDENTS + " s ON e." +
                DatabaseHelper.COL_STUDENT_ID + " = s." +
                DatabaseHelper.COL_STUDENT_ID + " " +
                "JOIN " + DatabaseHelper.TABLE_CLASS_SESSIONS + " cs ON " +
                "cs." + DatabaseHelper.COL_SUBJECT_ID + " = e." +
                DatabaseHelper.COL_SUBJECT_ID + " " +
                "LEFT JOIN (" +
                "SELECT " + DatabaseHelper.COL_STUDENT_ID + ", " +
                DatabaseHelper.COL_SUBJECT_ID + ", " +
                DatabaseHelper.COL_CHECKIN_DATE + ", " +
                "MIN(" + DatabaseHelper.COL_CHECKIN_TIME + ") AS first_checkin_time " +
                "FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY + " " +
                "GROUP BY " + DatabaseHelper.COL_STUDENT_ID + ", " +
                DatabaseHelper.COL_SUBJECT_ID + ", " +
                DatabaseHelper.COL_CHECKIN_DATE +
                ") fc ON fc." + DatabaseHelper.COL_STUDENT_ID + " = s." +
                DatabaseHelper.COL_STUDENT_ID + " AND " +
                "fc." + DatabaseHelper.COL_SUBJECT_ID + " = e." +
                DatabaseHelper.COL_SUBJECT_ID + " AND " +
                "fc." + DatabaseHelper.COL_CHECKIN_DATE + " = cs." +
                DatabaseHelper.COL_SESSION_DATE + " " +
                "WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ? " +
                "ORDER BY s." + DatabaseHelper.COL_FULL_NAME + " COLLATE NOCASE, " +
                "s." + DatabaseHelper.COL_STUDENT_ID + ", " +
                "substr(cs." + DatabaseHelper.COL_SESSION_DATE + ", 7, 4) || '-' || " +
                "substr(cs." + DatabaseHelper.COL_SESSION_DATE + ", 4, 2) || '-' || " +
                "substr(cs." + DatabaseHelper.COL_SESSION_DATE + ", 1, 2)";

        try (Cursor cursor = dbHelper.getReadableDatabase().rawQuery(
                query,
                new String[]{subjectId}
        )) {
            while (cursor.moveToNext()) {
                String studentId = cursor.getString(0);
                AttendanceMatrixRow row = rowsByStudentId.get(studentId);
                if (row == null) {
                    continue;
                }

                String sessionDate = cursor.isNull(1) ? "" : cursor.getString(1);
                String checkinTime = cursor.isNull(2) ? "" : cursor.getString(2);
                String lateCutoffTime = cursor.isNull(3) ? "" : cursor.getString(3);

                row.putCell(new AttendanceCell(
                        sessionDate,
                        resolveStatus(checkinTime, lateCutoffTime),
                        checkinTime,
                        lateCutoffTime
                ));
            }
        }

        return new ArrayList<>(rowsByStudentId.values());
    }

    private Map<String, AttendanceMatrixRow> loadStudents(String subjectId) {
        Map<String, AttendanceMatrixRow> rowsByStudentId = new LinkedHashMap<>();

        if (isBlank(subjectId)) {
            return rowsByStudentId;
        }

        String query = "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", " +
                "s." + DatabaseHelper.COL_FULL_NAME + ", " +
                "s." + DatabaseHelper.COL_EMAIL + " " +
                "FROM " + DatabaseHelper.TABLE_ENROLLMENTS + " e " +
                "JOIN " + DatabaseHelper.TABLE_STUDENTS + " s ON s." +
                DatabaseHelper.COL_STUDENT_ID + " = e." +
                DatabaseHelper.COL_STUDENT_ID + " " +
                "WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ? " +
                "ORDER BY s." + DatabaseHelper.COL_FULL_NAME + " COLLATE NOCASE, " +
                "s." + DatabaseHelper.COL_STUDENT_ID;

        try (Cursor cursor = dbHelper.getReadableDatabase().rawQuery(
                query,
                new String[]{subjectId}
        )) {
            while (cursor.moveToNext()) {
                String studentId = cursor.isNull(0) ? "" : cursor.getString(0);
                rowsByStudentId.put(
                        studentId,
                        new AttendanceMatrixRow(
                                studentId,
                                cursor.isNull(1) ? "" : cursor.getString(1),
                                cursor.isNull(2) ? "" : cursor.getString(2)
                        )
                );
            }
        }

        return rowsByStudentId;
    }

    private List<StudentAttendanceSummary> buildSummaries(
            List<SessionInfo> sessions,
            List<AttendanceMatrixRow> matrixRows
    ) {
        List<StudentAttendanceSummary> summaries = new ArrayList<>();
        int totalSessions = sessions == null ? 0 : sessions.size();

        for (AttendanceMatrixRow row : matrixRows) {
            int presentCount = 0;
            int lateCount = 0;

            for (AttendanceCell cell : row.getCellsByDate().values()) {
                if (cell.isPresentOrLate()) {
                    presentCount++;
                }
                if (cell.isLate()) {
                    lateCount++;
                }
            }

            summaries.add(new StudentAttendanceSummary(
                    row.getStudentId(),
                    row.getFullName(),
                    row.getEmail(),
                    totalSessions,
                    presentCount,
                    lateCount
            ));
        }

        return summaries;
    }

    private static String resolveStatus(String checkinTime, String lateCutoffTime) {
        if (isBlank(checkinTime)) {
            return AttendanceRecord.STATUS_ABSENT;
        }

        return DatabaseHelper.isLate(checkinTime, lateCutoffTime)
                ? AttendanceRecord.STATUS_LATE
                : AttendanceRecord.STATUS_PRESENT;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class SubjectInfo {
        final String subjectName;
        final String instructorName;

        SubjectInfo(String subjectName, String instructorName) {
            this.subjectName = subjectName == null ? "" : subjectName;
            this.instructorName = isBlank(instructorName)
                    ? "Chưa phân công"
                    : instructorName;
        }
    }
}
