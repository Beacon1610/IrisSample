package com.iritech.irissample;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.iritech.irissample.face.FaceEmbeddingExtractor;
import com.iritech.irissample.face.FaceMatchResult;
import com.iritech.irissample.face.FaceMatcher;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@OptIn(markerClass = ExperimentalGetImage.class)
public  class AdminFaceVerifyActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 4001;
    private static final float AUTO_ACCEPT_THRESHOLD = 0.8f;
    private static final long ANALYZE_INTERVAL_MS = 1000L;

    private PreviewView previewView;
    private TextView txtFaceStatus;
    private ImageButton btnSwitchCamera;

    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;

    private volatile boolean isProcessingFrame = false;
    private long lastAnalyzeTime = 0L;
    private String currentStatus = "";

    private FaceEmbeddingExtractor faceEmbeddingExtractor;
    private FaceMatcher faceMatcher;

    private String adminEmail;
    private String adminRole;
    private String adminName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_face_verify);

        previewView = findViewById(R.id.previewView);
        txtFaceStatus = findViewById(R.id.txtFaceStatus);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);

        adminEmail = getIntent().getStringExtra("USER_EMAIL");
        adminRole = getIntent().getStringExtra("USER_ROLE");
        adminName = getIntent().getStringExtra("USER_NAME");

        if (adminEmail == null || adminEmail.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy Admin cần xác minh", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        DatabaseHelper dbHelper = new DatabaseHelper(this);
        faceMatcher = new FaceMatcher(dbHelper);
        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            faceEmbeddingExtractor = new FaceEmbeddingExtractor(this);
        } catch (Exception e) {
            Toast.makeText(this, "Không tải được model Face ID: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnSwitchCamera.setOnClickListener(v -> switchCamera());

        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION
            );
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        if (cameraProvider != null) {
            bindCameraUseCases();
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                updateStatus("Không mở được camera");
                Toast.makeText(this, "Không mở được camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

        isProcessingFrame = false;
        lastAnalyzeTime = 0L;
        updateStatus("Đưa khuôn mặt vào camera");
    }

    private void switchCamera() {
        lensFacing =
                lensFacing == CameraSelector.LENS_FACING_FRONT
                        ? CameraSelector.LENS_FACING_BACK
                        : CameraSelector.LENS_FACING_FRONT;

        isProcessingFrame = false;
        startCamera();
    }

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        long now = System.currentTimeMillis();

        if (isProcessingFrame || now - lastAnalyzeTime < ANALYZE_INTERVAL_MS) {
            imageProxy.close();
            return;
        }

        isProcessingFrame = true;
        lastAnalyzeTime = now;

        try {
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            Bitmap bitmap = imageProxyToBitmap(imageProxy);

            if (bitmap == null) {
                continueRecognition("Không đọc được khung hình camera");
                return;
            }

            Bitmap rotatedBitmap = rotateBitmap(bitmap, rotationDegrees);
            processBitmapForRecognition(rotatedBitmap);
        } catch (Exception e) {
            continueRecognition("Lỗi xử lý khung hình camera");
        } finally {
            imageProxy.close();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) {
            return null;
        }

        byte[] nv21 = yuv420ToNv21(image);
        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                image.getWidth(),
                image.getHeight(),
                null
        );

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean compressed = yuvImage.compressToJpeg(
                new Rect(0, 0, image.getWidth(), image.getHeight()),
                90,
                outputStream
        );

        if (!compressed) {
            return null;
        }

        byte[] jpegBytes = outputStream.toByteArray();
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    private byte[] yuv420ToNv21(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        if (rotationDegrees == 0) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);

        Bitmap rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true
        );

        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }

        return rotatedBitmap;
    }

    private void processBitmapForRecognition(Bitmap bitmap) {
        faceEmbeddingExtractor.extractEmbeddingFromBitmap(
                bitmap,
                new FaceEmbeddingExtractor.OnEmbeddingExtractedCallback() {
                    @Override
                    public void onSuccess(float[] embedding, Bitmap faceBitmap) {
                        FaceMatchResult match = faceMatcher.verifyAdmin(embedding, adminEmail);

                        if (match == null) {
                            continueRecognition("Admin chưa đăng ký Face ID");
                            return;
                        }

                        handleMatchResult(match);
                    }

                    @Override
                    public void onError(String error) {
                        if (error != null && error.startsWith("Không phát hiện khuôn mặt")) {
                            continueRecognition("Đưa khuôn mặt vào camera");
                        } else {
                            continueRecognition("Đang nhận diện khuôn mặt...");
                        }
                    }
                }
        );
    }

    private void handleMatchResult(FaceMatchResult match) {
        float distance = match.distance;

        if (distance < AUTO_ACCEPT_THRESHOLD) {
            loginSuccess();
        } else {
            continueRecognition("Face ID không khớp, vui lòng thử lại");
        }
    }

    private void loginSuccess() {
        runOnUiThread(() -> {
            Toast.makeText(
                    AdminFaceVerifyActivity.this,
                    "Xác thực Face ID thành công",
                    Toast.LENGTH_SHORT
            ).show();

            Intent intent = new Intent(AdminFaceVerifyActivity.this, MainActivity.class);
            intent.putExtra("USER_EMAIL", adminEmail);
            intent.putExtra("USER_ROLE", adminRole);
            intent.putExtra("USER_NAME", adminName);
            startActivity(intent);
            finish();
        });
    }

    private void continueRecognition(String status) {
        isProcessingFrame = false;
        updateStatus(status);
    }

    private void updateStatus(String status) {
        if (status == null || status.equals(currentStatus)) {
            return;
        }

        currentStatus = status;

        runOnUiThread(() -> {
            if (!status.equals(txtFaceStatus.getText().toString())) {
                txtFaceStatus.setText(status);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        if (faceEmbeddingExtractor != null) {
            faceEmbeddingExtractor.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Cần quyền camera để xác thực Face ID", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}