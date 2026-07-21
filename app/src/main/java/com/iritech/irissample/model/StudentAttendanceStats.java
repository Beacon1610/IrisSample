package com.iritech.irissample.model;

/**
 * Thống kê chuyên cần của một sinh viên trong một môn học.
 */
public final class StudentAttendanceStats {

    private final String studentId;
    private final String studentName;
    private final int totalSessions;
    private final int presentCount;
    private final int lateCount;
    private final double lateRate;
    private final int absentCount;
    private final double attendanceRate;

    public StudentAttendanceStats(
            String studentId,
            String studentName,
            int totalSessions,
            int presentCount,
            int lateCount
    ) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.totalSessions = Math.max(0, totalSessions);
        this.presentCount = Math.max(0, Math.min(presentCount, this.totalSessions));
        this.lateCount = Math.max(0, Math.min(lateCount, this.presentCount));
        this.absentCount = Math.max(0, this.totalSessions - this.presentCount);
        this.attendanceRate = this.totalSessions == 0
                ? 0d
                : this.presentCount * 100d / this.totalSessions;
        this.lateRate = this.totalSessions == 0
                ? 0d
                : this.lateCount * 100d / this.totalSessions;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public int getPresentCount() {
        return presentCount;
    }

    public int getAbsentCount() {
        return absentCount;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }
    public int getLateCount() {
        return lateCount;
    }

    public double getLateRate() {
        return lateRate;
    }

    public int getRoundedLateRate() {
        return (int) Math.round(lateRate);
    }

    public int getRoundedAttendanceRate() {
        return (int) Math.round(attendanceRate);
    }

    public boolean needsAttention() {
        return totalSessions > 0 && attendanceRate < 70d;
    }
}
