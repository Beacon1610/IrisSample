package com.iritech.irissample.model.export;

/**
 * Dữ liệu sheet tổng hợp: mỗi dòng là thống kê chuyên cần của một sinh viên.
 */
public final class StudentAttendanceSummary {
    private final String studentId;
    private final String fullName;
    private final String email;
    private final int totalSessions;
    private final int presentCount;
    private final int lateCount;

    public StudentAttendanceSummary(
            String studentId,
            String fullName,
            String email,
            int totalSessions,
            int presentCount,
            int lateCount
    ) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.email = email;
        this.totalSessions = Math.max(0, totalSessions);
        this.presentCount = Math.max(0, Math.min(presentCount, this.totalSessions));
        this.lateCount = Math.max(0, Math.min(lateCount, this.presentCount));
    }

    public String getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public int getPresentCount() {
        return presentCount;
    }

    public int getLateCount() {
        return lateCount;
    }

    public int getAbsentCount() {
        return Math.max(0, totalSessions - presentCount);
    }

    public double getAttendanceRate() {
        return totalSessions == 0 ? 0d : presentCount * 100d / totalSessions;
    }

    public double getLateRate() {
        return totalSessions == 0 ? 0d : lateCount * 100d / totalSessions;
    }
}
