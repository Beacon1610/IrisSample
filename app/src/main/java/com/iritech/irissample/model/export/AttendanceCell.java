package com.iritech.irissample.model.export;

import com.iritech.irissample.model.AttendanceRecord;

/**
 * Trạng thái điểm danh của một sinh viên trong một buổi học.
 */
public final class AttendanceCell {
    private final String sessionDate;
    private final String status;
    private final String checkinTime;
    private final String lateCutoffTime;

    public AttendanceCell(
            String sessionDate,
            String status,
            String checkinTime,
            String lateCutoffTime
    ) {
        this.sessionDate = sessionDate;
        this.status = normalizeStatus(status);
        this.checkinTime = checkinTime;
        this.lateCutoffTime = lateCutoffTime;
    }

    public String getSessionDate() {
        return sessionDate;
    }

    public String getStatus() {
        return status;
    }

    public String getCheckinTime() {
        return checkinTime;
    }

    public String getLateCutoffTime() {
        return lateCutoffTime;
    }

    public boolean isPresentOrLate() {
        return AttendanceRecord.STATUS_PRESENT.equals(status)
                || AttendanceRecord.STATUS_LATE.equals(status);
    }

    public boolean isLate() {
        return AttendanceRecord.STATUS_LATE.equals(status);
    }

    public String getDisplayText() {
        if (AttendanceRecord.STATUS_LATE.equals(status)) {
            return "Đi muộn" + formatTimeSuffix();
        }

        if (AttendanceRecord.STATUS_PRESENT.equals(status)) {
            return "Có mặt" + formatTimeSuffix();
        }

        return "Vắng";
    }

    private String formatTimeSuffix() {
        String shortTime = getShortCheckinTime();
        return shortTime.isEmpty() ? "" : " - " + shortTime;
    }

    public String getShortCheckinTime() {
        if (checkinTime == null || checkinTime.trim().isEmpty()) {
            return "";
        }

        String trimmed = checkinTime.trim();
        return trimmed.length() >= 5 ? trimmed.substring(0, 5) : trimmed;
    }

    private static String normalizeStatus(String rawStatus) {
        if (AttendanceRecord.STATUS_PRESENT.equals(rawStatus)
                || AttendanceRecord.STATUS_LATE.equals(rawStatus)
                || AttendanceRecord.STATUS_ABSENT.equals(rawStatus)) {
            return rawStatus;
        }

        return AttendanceRecord.STATUS_ABSENT;
    }
}
