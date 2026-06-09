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
        Cursor cursor =
                dbHelper.getStudentFaceVectorById(
                        studentId,
                        subjectId
                );

        try {
            if (!cursor.moveToFirst()) {
                return null;
            }

            String matchedStudentId = cursor.getString(0);
            String studentName = cursor.getString(1);
            String embeddingJson = cursor.getString(2);

            if (embeddingJson == null ||
                    embeddingJson.trim().isEmpty()) {
                return null;
            }

            float[] savedEmbedding =
                    gson.fromJson(
                            embeddingJson,
                            float[].class
                    );

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
            cursor.close();
        }
    }
    public FaceMatchResult findBestMatch(float[] currentEmbedding, String subjectId) {
        Cursor cursor = dbHelper.getStudentFaceVectorsBySubject(subjectId);

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

                float[] savedEmbedding = gson.fromJson(embeddingJson, float[].class);

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

    public static float euclideanDistance(float[] emb1, float[] emb2) {
        float distance = 0f;

        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            distance += diff * diff;
        }

        return (float) Math.sqrt(distance);
    }
}
