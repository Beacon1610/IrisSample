package com.iritech.irissample.model.export;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dữ liệu sheet chi tiết: mỗi dòng là một sinh viên, mỗi cột ngày là một cell.
 */
public final class AttendanceMatrixRow {
    private final String studentId;
    private final String fullName;
    private final String email;
    private final String phone;
    private final Map<String, AttendanceCell> cellsByDate = new LinkedHashMap<>();

    public AttendanceMatrixRow(String studentId, String fullName, String email) {
        this(studentId, fullName, email, "");
    }

    public AttendanceMatrixRow(String studentId, String fullName, String email, String phone) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
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

    public String getPhone() {
        return phone;
    }

    public void putCell(AttendanceCell cell) {
        if (cell != null && cell.getSessionDate() != null) {
            cellsByDate.put(cell.getSessionDate(), cell);
        }
    }

    public AttendanceCell getCell(String sessionDate) {
        return cellsByDate.get(sessionDate);
    }

    public Map<String, AttendanceCell> getCellsByDate() {
        return Collections.unmodifiableMap(cellsByDate);
    }
}
