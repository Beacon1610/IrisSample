package com.iritech.irissample.face;

import android.database.Cursor;

import com.google.gson.Gson;
import com.iritech.irissample.DatabaseHelper;

public class FaceMatcher {

    private final DatabaseHelper dbHelper;
    private final Gson gson = new Gson();

    public FaceMatcher(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public FaceMatchResult verifyStudent(
            float[] currentEmbedding,
            String studentId,
            String subjectId
    ) {
        if (currentEmbedding == null || currentEmbedding.length == 0) {
            return null;
        }

        Cursor cursor =
                dbHelper.getStudentFaceVectorById(
                        studentId,
                        subjectId
                );

        try {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            String matchedStudentId = cursor.getString(0);
            String studentName = cursor.getString(1);
            String embeddingJson = cursor.getString(2);

            if (embeddingJson == null ||
                    embeddingJson.trim().isEmpty()) {
                return null;
            }

            float[] savedEmbedding = parseEmbedding(embeddingJson);

            if (savedEmbedding == null ||
                    savedEmbedding.length != currentEmbedding.length) {
                return null;
            }

            float distance = euclideanDistance(
                    currentEmbedding,
                    savedEmbedding
            );

            return new FaceMatchResult(
                    matchedStudentId,
                    studentName,
                    distance
            );
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    public FaceMatchResult verifyAdmin(float[] currentEmbedding, String adminEmail) {
        if (currentEmbedding == null || currentEmbedding.length == 0) {
            return null;
        }
        Cursor cursor = dbHelper.getAdminFaceVectorByEmail(adminEmail);

        try {
            if (cursor == null || !cursor.moveToFirst()) return null;

            String adminId = cursor.getString(0);
            String adminName = cursor.getString(1);
            String embeddingJson = cursor.getString(2);

            if (embeddingJson == null || embeddingJson.trim().isEmpty()) return null;

            float[] savedEmbedding = parseEmbedding(embeddingJson);
            if (savedEmbedding == null || savedEmbedding.length != currentEmbedding.length) return null;

            float distance = euclideanDistance(currentEmbedding, savedEmbedding);
            return new FaceMatchResult(adminId, adminName, distance);
        } finally {
            if (cursor != null) cursor.close();
        }
    }
    public FaceMatchResult findBestMatch(float[] currentEmbedding, String subjectId) {
        if (currentEmbedding == null || currentEmbedding.length == 0) {
            return null;
        }
        Cursor cursor = dbHelper.getStudentFaceVectorsBySubject(subjectId);
        if (cursor == null) {
            return null;
        }

        FaceMatchResult bestResult = null;
        float bestDistance = Float.MAX_VALUE;

        try {
            while (cursor.moveToNext()) {
                String studentId = cursor.getString(0);
                String studentName = cursor.getString(1);
                String embeddingJson = cursor.getString(2);

                if (embeddingJson == null || embeddingJson.trim().isEmpty()) {
                    continue;
                }

                float[] savedEmbedding = parseEmbedding(embeddingJson);

                if (savedEmbedding == null || savedEmbedding.length != currentEmbedding.length) {
                    continue;
                }

                float distance = euclideanDistance(currentEmbedding, savedEmbedding);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestResult = new FaceMatchResult(studentId, studentName, distance);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return bestResult;
    }

    private float[] parseEmbedding(String embeddingJson) {
        try {
            return gson.fromJson(embeddingJson, float[].class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static float euclideanDistance(float[] emb1, float[] emb2) {
        if (emb1 == null || emb2 == null || emb1.length == 0
                || emb1.length != emb2.length) {
            return Float.MAX_VALUE;
        }

        float distance = 0f;

        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            distance += diff * diff;
        }

        return (float) Math.sqrt(distance);
    }
}
