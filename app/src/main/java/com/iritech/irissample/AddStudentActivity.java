package com.iritech.irissample;

import android.app.AlertDialog;
import android.graphics.Color;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.Manifest;
import android.content.pm.PackageManager;
import java.io.InputStream;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.iritech.irissample.face.FaceEmbeddingExtractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.content.ContentValues;
import android.content.Intent; // Đã thêm
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView; // Đã thêm
import android.widget.Toast;
import com.google.gson.Gson;
import com.iritech.irissample.face.FaceEmbeddingExtractor;

import androidx.annotation.Nullable; // Đã thêm
import androidx.appcompat.app.AppCompatActivity;

public class AddStudentActivity extends AppCompatActivity {
    private String currentPhotoPath;

    private static final int REQUEST_CAMERA_CAPTURE = 102;
    private static final int REQUEST_GALLERY_PICK = 103;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private ImageView imgStudentAvatar;

    private FaceEmbeddingExtractor faceEmbeddingExtractor;
    private final Gson gson = new Gson();

    private String savedAvatarPath = "";
    private String savedFaceEmbeddingJson = "";
    private EditText edtStudentId, edtStudentName, edtStudentEmail, edtStudentPhone, edtStudentPassword;
    private Button btnSaveStudent;

    private DatabaseHelper dbHelper;
    private String subjectId;
    private String editStudentId;
    private boolean isEditMode = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_student);
        imgStudentAvatar = findViewById(R.id.imgStudentAvatar);

        edtStudentId = findViewById(R.id.edtStudentId);
        edtStudentName = findViewById(R.id.edtStudentName);
        edtStudentEmail = findViewById(R.id.edtStudentEmail);
        edtStudentPhone = findViewById(R.id.edtStudentPhone);
        edtStudentPassword = findViewById(R.id.edtStudentPassword);
        btnSaveStudent = findViewById(R.id.btnSaveStudent);

        Button btnAddStudentPhoto = findViewById(R.id.btnScanFace);
        btnAddStudentPhoto.setText("Thêm ảnh sinh viên");

        dbHelper = new DatabaseHelper(this);
        subjectId = getIntent().getStringExtra("subject_id"); // Nhận subject_id từ Intent
        editStudentId = getIntent().getStringExtra("student_id"); // Nhận student_id nếu đang edit

        try {
            faceEmbeddingExtractor = new FaceEmbeddingExtractor(this);
        } catch (Exception e) {
            Toast.makeText(this, "Không tải được model Face ID: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (editStudentId != null && !editStudentId.isEmpty()) {
            isEditMode = true;
            loadStudentInfo(editStudentId);
            edtStudentId.setEnabled(false); // Không cho sửa mã sinh viên
            btnSaveStudent.setText("Cập nhật thông tin");
        }

        btnSaveStudent.setOnClickListener(v -> {
            if (isEditMode) {
                updateStudent();
            } else {
                saveStudent();
            }
        });

        btnAddStudentPhoto.setOnClickListener(v -> showImageSourceDialog());
    }
    private void showImageSourceDialog() {
            String[] options = {"Chụp ảnh bằng camera", "Chọn ảnh từ thư viện"};

            new AlertDialog.Builder(this)
                    .setTitle("Thêm ảnh sinh viên")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            if (checkCameraPermission()) {
                                openCamera();
                            }
                        } else {
                            if (checkStoragePermission()) {
                                openGallery();
                            }
                        }
                    })
                    .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_CAMERA_CAPTURE) {
            if (currentPhotoPath == null || currentPhotoPath.isEmpty()) {
                Toast.makeText(this, "Không tìm thấy ảnh vừa chụp", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
            processStudentFaceBitmap(bitmap);

        } else if (requestCode == REQUEST_GALLERY_PICK) {
            if (data == null || data.getData() == null) {
                Toast.makeText(this, "Không lấy được ảnh từ thư viện", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                Uri selectedImage = data.getData();

                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(),
                        selectedImage
                );

                processStudentFaceBitmap(bitmap);

            } catch (Exception e) {
                Toast.makeText(this, "Không thể tải ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void processStudentFaceBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, "Ảnh không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hiện ảnh gốc trước để biết chọn ảnh đã thành công
        imgStudentAvatar.setImageBitmap(bitmap);

        if (faceEmbeddingExtractor == null) {
            Toast.makeText(this, "Face model chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }

        faceEmbeddingExtractor.extractEmbeddingFromBitmap(
                bitmap,
                new FaceEmbeddingExtractor.OnEmbeddingExtractedCallback() {
                    @Override
                    public void onSuccess(float[] embedding, Bitmap faceBitmap) {
                        savedFaceEmbeddingJson = gson.toJson(embedding);
                        savedAvatarPath = saveBitmapToFile(faceBitmap);

                        imgStudentAvatar.setImageBitmap(faceBitmap);

                        Toast.makeText(
                                AddStudentActivity.this,
                                "Đã thêm ảnh sinh viên thành công",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onError(String error) {
                        savedFaceEmbeddingJson = "";
                        savedAvatarPath = "";

                        Toast.makeText(
                                AddStudentActivity.this,
                                error,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }
    private boolean checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return true;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION
            );
            return false;
        }

        return true;
    }
    private boolean checkCameraPermission() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION
            );
            return false;
        }

        return true;
    }
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Cần quyền truy cập camera", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Cần quyền truy cập bộ nhớ", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private String saveBitmapToFile(Bitmap bitmap) {
        try {
            String timeStamp = new java.text.SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    java.util.Locale.getDefault()
            ).format(new java.util.Date());

            String imageFileName = "STUDENT_" + timeStamp + ".jpg";

            File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);

            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs();
            }

            File file = new File(storageDir, imageFileName);

            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();

            return file.getAbsolutePath();

        } catch (Exception e) {
            Toast.makeText(this, "Không lưu được ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return "";
        }
    }
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "Không tìm thấy ứng dụng camera", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File photoFile = createImageFile();

            Uri photoURI = FileProvider.getUriForFile(
                    this,
                    "com.iritech.irissample.fileprovider",
                    photoFile
            );

            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, REQUEST_CAMERA_CAPTURE);

        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void openGallery() {
        Intent pickPhoto = new Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        );

        startActivityForResult(pickPhoto, REQUEST_GALLERY_PICK);
    }
    private File  createImageFile() throws Exception {
        String timeStamp = new java.text.SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                java.util.Locale.getDefault()
        ).format(new java.util.Date());

        String imageFileName = "STUDENT_" + timeStamp + "_";

        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(imageFileName, ".jpg", storageDir);

        currentPhotoPath = image.getAbsolutePath();

        return image;
    }

    private void loadStudentInfo(String studentId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + DatabaseHelper.TABLE_STUDENTS + " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{studentId});

        if (cursor.moveToFirst()) {
            edtStudentId.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STUDENT_ID)));
            edtStudentName.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_FULL_NAME)));
            edtStudentEmail.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EMAIL)));
            edtStudentPhone.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PHONE)));
            edtStudentPassword.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PASSWORD)));

            savedAvatarPath = cursor.getString(
                    cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PHOTO_PATH)
            );

            savedFaceEmbeddingJson = cursor.getString(
                    cursor.getColumnIndexOrThrow(DatabaseHelper.COL_FACE_VECTOR)
            );
            if (savedAvatarPath == null) {
                savedAvatarPath = "";
        }
            if (savedFaceEmbeddingJson == null) {
                savedFaceEmbeddingJson = "";
            }
            // Hiển thị ảnh cũ trên màn hình cập nhật.
            if (!savedAvatarPath.isEmpty()) {
                Bitmap bitmap = BitmapFactory.decodeFile(savedAvatarPath);

                if (bitmap != null) {
                    imgStudentAvatar.setImageBitmap(bitmap);
                }
            }
        }
        cursor.close();
    }

    private void updateStudent() {
        String studentId = edtStudentId.getText().toString().trim();
        String studentName = edtStudentName.getText().toString().trim();
        String studentEmail = edtStudentEmail.getText().toString().trim();
        String studentPhone = edtStudentPhone.getText().toString().trim();
        String studentPassword = edtStudentPassword.getText().toString().trim();

        if (studentId.isEmpty() || studentName.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues studentValues = new ContentValues();
        studentValues.put(DatabaseHelper.COL_FULL_NAME, studentName);
        studentValues.put(DatabaseHelper.COL_EMAIL, studentEmail);
        studentValues.put(DatabaseHelper.COL_PHONE, studentPhone);
        studentValues.put(DatabaseHelper.COL_PASSWORD, studentPassword);
        if (savedAvatarPath != null && !savedAvatarPath.isEmpty()) {
            studentValues.put(
                    DatabaseHelper.COL_PHOTO_PATH,
                    savedAvatarPath
            );
        }

// Chỉ cập nhật vector nếu có dữ liệu hợp lệ.
// Tránh làm mất Face ID cũ.
        if (savedFaceEmbeddingJson != null &&
                !savedFaceEmbeddingJson.isEmpty()) {
            studentValues.put(
                    DatabaseHelper.COL_FACE_VECTOR,
                    savedFaceEmbeddingJson
            );
        }
        int rowsAffected = db.update(DatabaseHelper.TABLE_STUDENTS, studentValues,
                DatabaseHelper.COL_STUDENT_ID + " = ?", new String[]{studentId});

        if (rowsAffected > 0) {
            Toast.makeText(this, "Cập nhật thông tin thành công", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "Cập nhật thất bại", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveStudent() {
        ContentValues studentValues = new ContentValues();
        String studentId = edtStudentId.getText().toString().trim();
        String studentName = edtStudentName.getText().toString().trim();
        String studentEmail = edtStudentEmail.getText().toString().trim();
        String studentPhone = edtStudentPhone.getText().toString().trim();
        String studentPassword = edtStudentPassword.getText().toString().trim();
        studentValues.put(DatabaseHelper.COL_PHOTO_PATH, savedAvatarPath);
        studentValues.put(DatabaseHelper.COL_FACE_VECTOR, savedFaceEmbeddingJson);

        if (savedFaceEmbeddingJson == null || savedFaceEmbeddingJson.isEmpty()) {
            Toast.makeText(this, "Vui lòng thêm ảnh khuôn mặt sinh viên trước khi lưu", Toast.LENGTH_SHORT).show();
            return;
        }

        if (studentId.isEmpty() || studentName.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }


        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Kiểm tra xem sinh viên đã tồn tại trong bảng students chưa
        String query = "SELECT * FROM " + DatabaseHelper.TABLE_STUDENTS + " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{studentId});
        if (cursor.getCount() == 0) {
            // Nếu sinh viên chưa tồn tại, thêm vào bảng student
            studentValues.put(DatabaseHelper.COL_STUDENT_ID, studentId);
            studentValues.put(DatabaseHelper.COL_FULL_NAME, studentName);
            studentValues.put(DatabaseHelper.COL_EMAIL, studentEmail);
            studentValues.put(DatabaseHelper.COL_PHONE, studentPhone);
            studentValues.put(DatabaseHelper.COL_PASSWORD, studentPassword);
            studentValues.put(DatabaseHelper.COL_FACE_VECTOR, savedFaceEmbeddingJson);
            if (studentId.isEmpty() || studentName.isEmpty() || studentPassword.isEmpty()) { // Kiểm tra trống
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            long studentResult = db.insert(DatabaseHelper.TABLE_STUDENTS, null, studentValues);

            if (studentResult == -1) {
                Toast.makeText(this, "Thêm sinh viên thất bại", Toast.LENGTH_SHORT).show();
                cursor.close();
                return;
            }
        }
        cursor.close();

        ContentValues enrollmentValues = new ContentValues();
        enrollmentValues.put(DatabaseHelper.COL_STUDENT_ID, studentId);
        enrollmentValues.put(DatabaseHelper.COL_SUBJECT_ID, subjectId);
        enrollmentValues.put(DatabaseHelper.COL_ENROLLMENT_STATUS, "Not Enrolled");

        long enrollmentResult = db.insert(DatabaseHelper.TABLE_ENROLLMENTS, null, enrollmentValues);

        if (enrollmentResult != -1) {
            Toast.makeText(this, "Thêm sinh viên thành công", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK); // Trả kết quả thành công
            finish(); // Đóng Activity
        } else {
            Toast.makeText(this, "Sinh viên đã được thêm vào môn học này", Toast.LENGTH_SHORT).show();
        }
    }
}