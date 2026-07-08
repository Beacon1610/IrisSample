package com.iritech.irissample.model.export;

/**
 * Thông tin một buổi học dùng cho thống kê/export điểm danh.
 */
public final class SessionInfo {
    private final String sessionDate;
    private final String lateCutoffTime;

    public SessionInfo(String sessionDate, String lateCutoffTime) {
        this.sessionDate = sessionDate;
        this.lateCutoffTime = lateCutoffTime;
    }

    public String getSessionDate() {
        return sessionDate;
    }

    public String getLateCutoffTime() {
        return lateCutoffTime;
    }
}
