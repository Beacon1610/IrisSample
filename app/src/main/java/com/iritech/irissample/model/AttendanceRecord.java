package com.iritech.irissample.model;

/**
 * Model đại diện cho một bản ghi điểm danh của sinh viên.
 * Dùng trong AttendanceManagementActivity để hiển thị danh sách điểm danh.
 */
public class AttendanceRecord {
    public static final String STATUS_PRESENT = "PRESENT";
    public static final String STATUS_LATE = "LATE";
    public static final String STATUS_ABSENT = "ABSENT";

    private String studentId;
    private String fullName;
    private boolean attended;
    private String attendanceStatus;
    private String lateCutoffTime;
    private String checkinDate;  // Format: dd/MM/yyyy
    private String checkinTime;  // Format: HH:mm:ss

    // Constructor đầy đủ
    public AttendanceRecord(
            String studentId,
            String fullName,
            String attendanceStatus,
            String checkinDate,
            String checkinTime,
            String lateCutoffTime
    ) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.checkinDate = checkinDate;
        this.checkinTime = checkinTime;
        this.lateCutoffTime = lateCutoffTime;
        setAttendanceStatus(attendanceStatus);
    }

    // Constructor cho sinh viên chưa điểm danh
    public AttendanceRecord(String studentId, String fullName) {
        this(studentId, fullName, STATUS_ABSENT, null, null, null);
    }

    // Getters
    public String getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isAttended() {
        return attended;
    }

    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    public boolean isLate() {
        return STATUS_LATE.equals(attendanceStatus);
    }

    public boolean isAbsent() {
        return STATUS_ABSENT.equals(attendanceStatus);
    }

    public String getLateCutoffTime() {
        return lateCutoffTime;
    }

    public String getCheckinDate() {
        return checkinDate;
    }

    public String getCheckinTime() {
        return checkinTime;
    }

    // Setters
    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setAttended(boolean attended) {
        this.attended = attended;
        this.attendanceStatus = attended ? STATUS_PRESENT : STATUS_ABSENT;
    }

    public void setAttendanceStatus(String attendanceStatus) {
        if (STATUS_PRESENT.equals(attendanceStatus)
                || STATUS_LATE.equals(attendanceStatus)
                || STATUS_ABSENT.equals(attendanceStatus)) {
            this.attendanceStatus = attendanceStatus;
        } else {
            this.attendanceStatus = STATUS_ABSENT;
        }

        this.attended = !STATUS_ABSENT.equals(this.attendanceStatus);
    }

    public void setLateCutoffTime(String lateCutoffTime) {
        this.lateCutoffTime = lateCutoffTime;
    }

    public void setCheckinDate(String checkinDate) {
        this.checkinDate = checkinDate;
    }

    public void setCheckinTime(String checkinTime) {
        this.checkinTime = checkinTime;
    }

    // Helper method để hiển thị thời gian ngắn gọn (HH:mm)
    public String getShortTime() {
        if (checkinTime == null || checkinTime.isEmpty()) {
            return "--";
        }
        // Nếu format là HH:mm:ss, chỉ lấy HH:mm
        if (checkinTime.length() >= 5) {
            return checkinTime.substring(0, 5);
        }
        return checkinTime;
    }

    // Helper method để hiển thị ngày ngắn gọn (dd/MM)
    public String getShortDate() {
        if (checkinDate == null || checkinDate.isEmpty()) {
            return "--";
        }
        // Nếu format là dd/MM/yyyy, chỉ lấy dd/MM
        if (checkinDate.length() >= 5) {
            return checkinDate.substring(0, 5);
        }
        return checkinDate;
    }
}
