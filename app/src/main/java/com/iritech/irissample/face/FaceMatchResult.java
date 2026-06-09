package com.iritech.irissample.face;

public class FaceMatchResult {
    public final String studentId;
    public final String studentName;
    public final float distance;

    public FaceMatchResult(String studentId, String studentName, float distance) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.distance = distance;
    }
}