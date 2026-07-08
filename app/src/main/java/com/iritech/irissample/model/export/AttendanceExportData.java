package com.iritech.irissample.model.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container dữ liệu export cho một môn học.
 */
public final class AttendanceExportData {
    private final String subjectId;
    private final String subjectName;
    private final String instructorName;
    private final List<SessionInfo> sessions;
    private final List<StudentAttendanceSummary> summaries;
    private final List<AttendanceMatrixRow> matrixRows;

    public AttendanceExportData(
            String subjectId,
            String subjectName,
            String instructorName,
            List<SessionInfo> sessions,
            List<StudentAttendanceSummary> summaries,
            List<AttendanceMatrixRow> matrixRows
    ) {
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.instructorName = instructorName;
        this.sessions = copyList(sessions);
        this.summaries = copyList(summaries);
        this.matrixRows = copyList(matrixRows);
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getInstructorName() {
        return instructorName;
    }

    public List<SessionInfo> getSessions() {
        return sessions;
    }

    public List<StudentAttendanceSummary> getSummaries() {
        return summaries;
    }

    public List<AttendanceMatrixRow> getMatrixRows() {
        return matrixRows;
    }

    private static <T> List<T> copyList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
