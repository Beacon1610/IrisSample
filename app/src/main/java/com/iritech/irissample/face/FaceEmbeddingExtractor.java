package com.iritech.irissample.face;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class FaceEmbeddingExtractor {

    private static final int INPUT_SIZE = 112;
    private static final int EMBEDDING_SIZE = 192;

    private final Interpreter interpreter;
    private final FaceDetector faceDetector;

    public interface OnEmbeddingExtractedCallback {
        void onSuccess(float[] embedding, Bitmap faceBitmap);
        void onError(String error);
    }

    public FaceEmbeddingExtractor(Context context) throws IOException {
        interpreter = new Interpreter(loadModelFile(context, "mobile_face_net.tflite"));

        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .enableTracking()
                        .build();

        faceDetector = FaceDetection.getClient(options);
    }

    public void extractEmbeddingFromBitmap(
            Bitmap bitmap,
            OnEmbeddingExtractedCallback callback
    ) {
        if (bitmap == null) {
            callback.onError("Ảnh không hợp lệ");
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces == null || faces.isEmpty()) {
                        callback.onError("Không phát hiện khuôn mặt");
                        return;
                    }

                    Face largestFace = getLargestFace(faces);

                    if (largestFace == null) {
                        callback.onError("Không tìm thấy khuôn mặt phù hợp");
                        return;
                    }

                    Rect boundingBox = largestFace.getBoundingBox();

                    Bitmap faceBitmap = cropFace(bitmap, boundingBox);

                    if (faceBitmap == null) {
                        callback.onError("Không thể cắt khuôn mặt");
                        return;
                    }

                    Bitmap resizedFace = resizeBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE);

                    float[] embedding = runModel(resizedFace);

                    callback.onSuccess(embedding, resizedFace);
                })
                .addOnFailureListener(e -> callback.onError("Lỗi detect face: " + e.getMessage()));
    }

    private Face getLargestFace(List<Face> faces) {
        Face largestFace = null;
        int largestArea = 0;

        for (Face face : faces) {
            Rect rect = face.getBoundingBox();
            int area = rect.width() * rect.height();

            if (area > largestArea) {
                largestArea = area;
                largestFace = face;
            }
        }

        return largestFace;
    }

    private Bitmap cropFace(Bitmap source, Rect boundingBox) {
        int padding = 20;

        int left = Math.max(boundingBox.left - padding, 0);
        int top = Math.max(boundingBox.top - padding, 0);
        int right = Math.min(boundingBox.right + padding, source.getWidth());
        int bottom = Math.min(boundingBox.bottom + padding, source.getHeight());

        int width = right - left;
        int height = bottom - top;

        if (width <= 0 || height <= 0) {
            return null;
        }

        RectF cropRect = new RectF(left, top, right, bottom);
        return getCropBitmapByCPU(source, cropRect);
    }

    private Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap(
                (int) cropRectF.width(),
                (int) cropRectF.height(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(resultBitmap);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);

        canvas.drawRect(
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint
        );

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        canvas.drawBitmap(source, matrix, paint);

        return resultBitmap;
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int width, int height) {
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    private float[] runModel(Bitmap faceBitmap) {
        ByteBuffer inputBuffer = bitmapToByteBuffer(faceBitmap);

        float[][] output = new float[1][EMBEDDING_SIZE];

        interpreter.run(inputBuffer, output);

        return output[0];
    }

    private ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(
                1 * INPUT_SIZE * INPUT_SIZE * 3 * 4
        );

        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];

        bitmap.getPixels(
                intValues,
                0,
                bitmap.getWidth(),
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight()
        );

        int pixel = 0;

        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                int value = intValues[pixel++];

                byteBuffer.putFloat((((value >> 16) & 0xFF) - 128.0f) / 128.0f);
                byteBuffer.putFloat((((value >> 8) & 0xFF) - 128.0f) / 128.0f);
                byteBuffer.putFloat(((value & 0xFF) - 128.0f) / 128.0f);
            }
        }

        return byteBuffer;
    }

    private MappedByteBuffer loadModelFile(
            Context context,
            String modelFileName
    ) throws IOException {
        try (AssetFileDescriptor fileDescriptor =
                     context.getAssets().openFd(modelFileName);
             FileInputStream inputStream =
                     new FileInputStream(fileDescriptor.getFileDescriptor());
             FileChannel fileChannel = inputStream.getChannel()) {
            return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.getStartOffset(),
                    fileDescriptor.getDeclaredLength()
            );
        }
    }

    public void close() {
        faceDetector.close();
        interpreter.close();
    }
}