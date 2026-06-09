package com.iritech.irissample.model;

/**
 * Model đại diện cho một bản ghi điểm danh của sinh viên.
 * Dùng trong AttendanceManagementActivity để hiển thị danh sách điểm danh.
 */
public class AttendanceRecord {
    private String studentId;
    private String fullName;
    private boolean attended;
    private String checkinDate;  // Format: dd/MM/yyyy
    private String checkinTime;  // Format: HH:mm:ss

    // Constructor đầy đủ
    public AttendanceRecord(String studentId, String fullName, boolean attended,
                           String checkinDate, String checkinTime) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.attended = attended;
        this.checkinDate = checkinDate;
        this.checkinTime = checkinTime;
    }

    // Constructor cho sinh viên chưa điểm danh
    public AttendanceRecord(String studentId, String fullName) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.attended = false;
        this.checkinDate = null;
        this.checkinTime = null;
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
