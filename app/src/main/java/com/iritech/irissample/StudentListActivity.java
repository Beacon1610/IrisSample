package com.iritech.irissample;

import android.content.Intent;
import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.iritech.android.widget.alertdialog.BestImageDialog;
import com.iritech.android.widget.alertdialog.RegisterLicenseDialog;
import com.iritech.iris.CaptureActivity;
import com.iritech.iris.Constants;
import com.iritech.iris.DeveloperSettings;
import com.iritech.iris.LicenseInfo;
import com.iritech.mqel704.GemResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class StudentListActivity extends AppCompatActivity {

    private int REQUEST_CODE_IDENTIFY = 1111;
    private int REQUEST_CODE_CAPTURE = 1112;
    private int REQUEST_CODE_ENROLL = 1113;
    private int REQUEST_CODE_UNENROLL = 1115;
    private int REQUEST_CODE_FACE_ID = 2001; // Mã dành riêng cho Face ID

    private String  selectedStudentId = null;
    private String pendingFaceStudentId = null;
    private String currentSubjectId = null;

    private int mResultCode;
    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 100;
    private static final int REQUEST_IRIS_PERMISSIONS = 101;

    private Intent pendingIrisIntent;
    private int pendingRequestCode;

    ListView listViewStudents;
    EditText edtSearch;
    SwitchCompat switchFilterAttendance;
    DatabaseHelper dbHelper;
    ArrayList<String> studentList = new ArrayList<>();
    ArrayList<String> studentIds = new ArrayList<>();
    ArrayList<String> filteredStudentList = new ArrayList<>();
    ArrayList<String> filteredStudentIds = new ArrayList<>();
    StudentAdapter adapter;
    ArrayList<String> studentAvatarPaths = new ArrayList<>();
    ArrayList<String> filteredStudentAvatarPaths = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_list);

        // Initialize the iris recognition SDK
        LicenseInfo licenseInfo = LicenseInfo.getInstance();
        if (!licenseInfo.isInitialized()) {
            licenseInfo.initialize(this);
        }

        listViewStudents = findViewById(R.id.listViewStudents);
        edtSearch = findViewById(R.id.edtSearch);
        switchFilterAttendance = findViewById(R.id.switchFilterAttendance);
        dbHelper = new DatabaseHelper(this);

        // Get subject_id from the Intent
        currentSubjectId = getIntent().getStringExtra("subject_id");

        // Load students for the selected subject
        loadStudentsForSubject(currentSubjectId);

        // Setup search functionality
        setupSearch();
        
        // Setup attendance filter switch
        setupAttendanceFilter();
        Button btnAddStudent = findViewById(R.id.btnAddStudent);
        btnAddStudent.setOnClickListener(v -> {
            Intent intent = new Intent(StudentListActivity.this, AddStudentActivity.class);
            intent.putExtra("subject_id", currentSubjectId);
            startActivityForResult(intent, 1001);
        });

        // Handle "Import CSV" button click
        Button btnImportCsv = findViewById(R.id.btnImportCsv);
        btnImportCsv.setOnClickListener(v -> {
            Intent intent = new Intent(StudentListActivity.this, ImportCsvActivity.class);
            intent.putExtra("subject_id", currentSubjectId);
            startActivityForResult(intent, 1001);
        });

        // Handle "Identify" button click
        Button btnIdentify = findViewById(R.id.btnIdentify);
        btnIdentify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check Mock Mode
                DeveloperSettings devSettings = new DeveloperSettings(StudentListActivity.this);
                if (devSettings.isMockModeEnabled()) {
                    // Mock Mode: Hiện dialog chọn sinh viên đã ghi danh
                    showMockCheckinDialog();
                } else {
                    showIdentifyOptionsDialog();
                    // Normal Mode: Dùng iris recognition
                    // startCaptureActivity(Constants.ACTION_IDENTIFY, REQUEST_CODE_IDENTIFY);

                }
            }
        });

        // Handle "Export" button click - Navigate to AttendanceManagementActivity
        Button btnExport = findViewById(R.id.btnExport);
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StudentListActivity.this, AttendanceManagementActivity.class);
                intent.putExtra("subject_id", currentSubjectId);
                startActivity(intent);
            }
        });
    }
    private void showIdentifyOptionsDialog() {

        String[] options = {"Điểm danh bằng mật khẩu", "Điểm danh bằng mống mắt", "Điểm danh bằng Face ID"};

        new AlertDialog.Builder(this)

        .setTitle("Chọn phương thức điểm danh")

                .setItems(options, (dialog, which) -> {

                    switch (which) {

                        case 0: // Mật khẩu

                            showMockCheckinDialog();

                            break;

                        case 1: // Mống mắt

                            startCaptureActivity(Constants.ACTION_IDENTIFY, REQUEST_CODE_IDENTIFY);

                            break;

                        case 2: // Face ID

                            startFaceIdAuth(); // Hàm chúng ta sẽ xây dựng

                            break;

                    }

                })

                .setNegativeButton("Hủy", null)

                .show();

    }

    // Hàm mở màn hình AI nhận diện khuôn mặt
    public void startFaceIdAuth() {
        Intent intent = new Intent(
                StudentListActivity.this,
                FaceRecognitionActivity.class
        );

        intent.putExtra("action", "attendance");
        intent.putExtra("subject_id", currentSubjectId);
        intent.putExtra("target_student_id", pendingFaceStudentId);

        startActivityForResult(intent, REQUEST_CODE_FACE_ID);
    }
    private void reloadStudents(String subjectId) {
        studentList.clear();
        studentIds.clear();
        studentAvatarPaths.clear();
        filteredStudentAvatarPaths.clear();
        filteredStudentList.clear();
        filteredStudentIds.clear();
        loadStudentsForSubject(subjectId);
        // Re-apply all filters
        applyFilters();
    }

    private void loadStudentsForSubject(String subjectId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
            // query cu~ ko chua avatar
//        String query = "SELECT s.student_id, s.full_name " +
//                "FROM " + DatabaseHelper.TABLE_STUDENTS + " s " +
//                "JOIN " + DatabaseHelper.TABLE_ENROLLMENTS + " e ON s.student_id = e.student_id " +
//                "WHERE e.subject_id = ?";
        // query co  avatar
        String query = "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", " +
                "s." + DatabaseHelper.COL_FULL_NAME + ", " +
                "s." + DatabaseHelper.COL_PHOTO_PATH + " " +
                "FROM " + DatabaseHelper.TABLE_STUDENTS + " s " +
                "JOIN " + DatabaseHelper.TABLE_ENROLLMENTS + " e " +
                "ON s." + DatabaseHelper.COL_STUDENT_ID + " = e." + DatabaseHelper.COL_STUDENT_ID + " " +
                "WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{subjectId});

        if (cursor.moveToFirst()) {
            do {
                String studentId = cursor.getString(0);
                String fullName = cursor.getString(1);
                String avatarPath = cursor.getString(2);

                studentIds.add(studentId);
                studentList.add(fullName);
                studentAvatarPaths.add(avatarPath);
            } while (cursor.moveToNext());
        }

        cursor.close();

        // Copy to filtered lists initially
        filteredStudentList.clear();
        filteredStudentIds.clear();
        filteredStudentAvatarPaths.clear();
        filteredStudentAvatarPaths.addAll(studentAvatarPaths);
        filteredStudentList.addAll(studentList);
        filteredStudentIds.addAll(studentIds);

        adapter = new StudentAdapter(this,
                filteredStudentList,
                filteredStudentIds,
                filteredStudentAvatarPaths,
                new StudentAdapter.OnStudentActionListener() {
            @Override
            public void onCheckInClick(String studentId) {
                selectedStudentId = studentId;
                showPasswordThenFaceDialog(studentId);
            }

            @Override
            public void onEnrollClick(String studentId) {
                selectedStudentId = studentId;
                startCaptureActivity(Constants.ACTION_ENROLL, REQUEST_CODE_ENROLL);
            }

            @Override
            public void onUnenrollClick(String studentId) {
                selectedStudentId = studentId;
                startCaptureActivity(Constants.ACTION_UNENROLL, REQUEST_CODE_UNENROLL);
            }

            @Override
           public void onEditClick(String studentId) {
                Intent intent = new Intent(StudentListActivity.this, AddStudentActivity.class);
                intent.putExtra("subject_id", currentSubjectId);
                intent.putExtra("student_id", studentId);
                startActivityForResult(intent, 1001);
            }
        });
        listViewStudents.setAdapter(adapter);
    }

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterStudents(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupAttendanceFilter() {
        switchFilterAttendance.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applyFilters();
            }
        });
    }

    private void filterStudents(String searchText) {
        applyFilters();
    }

    private void applyFilters() {
        filteredStudentAvatarPaths.clear();
        filteredStudentList.clear();
        filteredStudentIds.clear();

        String searchText = edtSearch.getText().toString();
        boolean filterNotAttended = switchFilterAttendance.isChecked();

        // Get today's date in Vietnam timezone (format must match database)
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String today = dateFormat.format(calendar.getTime());

        for (int i = 0; i < studentList.size(); i++) {
            String studentName = studentList.get(i);
            String studentId = studentIds.get(i);
            String avatarPath = studentAvatarPaths.get(i);

            // Apply search filter
            boolean matchesSearch = searchText.isEmpty() || 
                    studentName.toLowerCase().contains(searchText.toLowerCase()) ||
                    studentId.toLowerCase().contains(searchText.toLowerCase());

            if (!matchesSearch) {
                continue;
            }

            // Apply attendance filter
            if (filterNotAttended) {
                // Only show students who haven't checked in today
                if (!hasStudentCheckedInToday(studentId, today)) {
                    filteredStudentList.add(studentName);
                    filteredStudentIds.add(studentId);
                    filteredStudentAvatarPaths.add(avatarPath);
                }
            } else {
                // Show all students (no attendance filter)
                filteredStudentList.add(studentName);
                filteredStudentIds.add(studentId);
                filteredStudentAvatarPaths.add(avatarPath);
            }
        }

        // Update adapter
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private boolean hasStudentCheckedInToday(String studentId, String today) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ? AND " +
                DatabaseHelper.COL_SUBJECT_ID + " = ? AND " +
                DatabaseHelper.COL_CHECKIN_DATE + " = ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{studentId, currentSubjectId, today});
        
        boolean hasCheckedIn = false;
        if (cursor.moveToFirst()) {
            hasCheckedIn = cursor.getInt(0) > 0;
        }
        cursor.close();
        return hasCheckedIn;
    }
    private void showPasswordThenFaceDialog(final String studentId) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Xác nhận điểm danh");
        builder.setMessage("Vui lòng nhập mật khẩu xác nhận cho mã SV: " + studentId);

        // Tạo ô nhập liệu (EditText) dạng ẩn mật khẩu
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        // Xử lý sự kiện khi bấm nút Xác nhận
        builder.setPositiveButton("Xác nhận", (dialog, which) -> {
            String inputPassword = input.getText().toString().trim();

            // Khởi tạo DatabaseHelper
            DatabaseHelper dbHelper = new DatabaseHelper(StudentListActivity.this);

            // Lấy mật khẩu của sinh viên từ database
            String actualPassword = dbHelper.getStudentPassword(studentId);

            // Kiểm tra mật khẩu (nếu actualPassword khác null và khớp với mật khẩu nhập vào)
            if (actualPassword == null ||
                    !actualPassword.equals(inputPassword)) {
                Toast.makeText(
                        StudentListActivity.this,
                        "Mật khẩu không chính xác",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            pendingFaceStudentId = studentId;

            Toast.makeText(
                    StudentListActivity.this,
                    "Mật khẩu đúng. Vui lòng xác thực khuôn mặt.",
                    Toast.LENGTH_SHORT
            ).show();
            startFaceIdAuth();
        });

        // Xử lý sự kiện khi bấm nút Hủy
        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());

        // Hiển thị hộp thoại
        builder.show();
    }
    /**
     * Hiển thị dialog chọn sinh viên để điểm danh (Mock Mode)
     * Chỉ hiển thị sinh viên chưa điểm danh hôm nay
     */
    private void showMockCheckinDialog() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        // Lấy ngày hôm nay
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String today = dateFormat.format(new Date());
        
        // Query sinh viên chưa điểm danh hôm nay
        String query = "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", " +
                      "s." + DatabaseHelper.COL_FULL_NAME + " " +
                      "FROM " + DatabaseHelper.TABLE_STUDENTS + " s " +
                      "JOIN " + DatabaseHelper.TABLE_ENROLLMENTS + " e " +
                      "ON s." + DatabaseHelper.COL_STUDENT_ID + " = e." + DatabaseHelper.COL_STUDENT_ID + " " +
                      "WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ? " +
                      "AND s." + DatabaseHelper.COL_STUDENT_ID + " NOT IN (" +
                      "  SELECT " + DatabaseHelper.COL_STUDENT_ID + " " +
                      "  FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY + " " +
                      "  WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ? " +
                      "  AND " + DatabaseHelper.COL_CHECKIN_DATE + " = ?" +
                      ") " +
                      "ORDER BY s." + DatabaseHelper.COL_FULL_NAME;
        
        Cursor cursor = db.rawQuery(query, new String[]{currentSubjectId, currentSubjectId, today});
        
        final ArrayList<String> enrolledStudentIds = new ArrayList<>();
        final ArrayList<String> enrolledStudentNames = new ArrayList<>();
        
        if (cursor.moveToFirst()) {
            do {
                String studentId = cursor.getString(0);
                String fullName = cursor.getString(1);
                enrolledStudentIds.add(studentId);
                enrolledStudentNames.add(fullName + " (" + studentId + ")");
            } while (cursor.moveToNext());
        }
        cursor.close();
        
        // Check if có sinh viên chưa điểm danh
        if (enrolledStudentIds.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Thông báo")
                .setMessage("Tất cả sinh viên đã điểm danh hôm nay rồi!\n\nHoặc chưa có sinh viên nào trong môn học.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }
        
        // Hiện dialog chọn sinh viên
        new AlertDialog.Builder(this)
            .setTitle("Chọn sinh viên để điểm danh")
            .setItems(enrolledStudentNames.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final String selectedId = enrolledStudentIds.get(which);
                    final String selectedName = enrolledStudentNames.get(which);
                    
                    // --- BƯỚC MỚI: XÁC THỰC MẬT KHẨU SINH VIÊN ---
                    AlertDialog.Builder pwdBuilder = new AlertDialog.Builder(StudentListActivity.this);
                    pwdBuilder.setTitle("Xác thực: " + selectedName);
                    pwdBuilder.setMessage("Vui lòng nhập mật khẩu sinh viên");

                    final EditText inputPwd = new EditText(StudentListActivity.this);
                    inputPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    inputPwd.setHint("Mật khẩu");
                    pwdBuilder.setView(inputPwd);

                    pwdBuilder.setPositiveButton("Xác nhận", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            String enteredPwd = inputPwd.getText().toString();
                            String correctPwd = dbHelper.getStudentPassword(selectedId);

                            if (correctPwd != null && correctPwd.equals(enteredPwd)) {
                                // Nếu đúng mật khẩu -> Tiến hành logic điểm danh cũ
                                performCheckinLogic(selectedId);
                            } else {
                                Toast.makeText(StudentListActivity.this, "Mật khẩu không chính xác!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    pwdBuilder.setNegativeButton("Hủy", null);
                    pwdBuilder.show();
                }
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    /**
     * Tách logic kiểm tra và ghi điểm danh để code gọn hơn
     */
    private void performCheckinLogic(String studentId) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String today = dateFormat.format(calendar.getTime());

        if (hasStudentCheckedInToday(studentId, today)) {
            new AlertDialog.Builder(StudentListActivity.this)
                .setTitle("Cảnh báo")
                .setMessage("Sinh viên này đã điểm danh hôm nay rồi.\n\nBạn có muốn điểm danh lại không?")
                .setPositiveButton("Điểm danh lại", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInner, int whichInner) {
                        markAttendance(studentId);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
        } else {
            markAttendance(studentId);
        }
    }

    private void startCaptureActivity(String actionType, int requestCode) {
        // Kiểm tra chưa chọn sinh viên trước
        if ((selectedStudentId == null || selectedStudentId.isEmpty()) && (actionType.equals(Constants.ACTION_VERIFY)
                || actionType.equals(Constants.ACTION_ENROLL) || actionType.equals(Constants.ACTION_UNENROLL))) {
            BestImageDialog dialog = new BestImageDialog(this);
            dialog.setTitle("Information!");
            dialog.show();
            dialog.setBestImages(null, null, null);
            dialog.setMessage("  Please input User ID  ");
            return;
        }
        // Tạo Intent
        Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
        intent.setAction(actionType);
        intent.putExtra(Constants.EXTRA_USER_ID, selectedStudentId);
        // Kiểm tra quyền trước (WRITE trước, CAMERA sau)
        checkIrisPermissionsAndStart(intent, requestCode);
    }

    /**
     * Kiểm tra quyền WRITE_EXTERNAL_STORAGE và CAMERA trước khi khởi chạy CaptureActivity.
     * Thứ tự: Ghi (WRITE) trước → Chụp (CAMERA) sau.
     */
    private void checkIrisPermissionsAndStart(Intent intent, int requestCode) {
        boolean hasWrite = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasCamera = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (hasWrite && hasCamera) {
            LicenseCheckHelper.checkLicenseAndStartCapture(this, intent, requestCode);
            return;
        }

        pendingIrisIntent = intent;
        pendingRequestCode = requestCode;
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA
                },
                REQUEST_IRIS_PERMISSIONS);
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                new AlertDialog.Builder(StudentListActivity.this)
                        .setTitle("Information!")
                        .setMessage("Please allow camera permission.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(StudentListActivity.this,
                                        new String[]{Manifest.permission.CAMERA},
                                        2);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        2);
            }
            return false;
        }
        return true;
    }

    private void processCaptureResult(Intent data, String folderPath, String prefix, String filePurpose) {
        String templateExt = ".tpl";

        int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, 0);
        String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);

        if (resultCode == 0) {
            byte[] leftTemplateBuffer = data.getByteArrayExtra(Constants.EXTRA_LEFT_TEMPLATE);
            byte[] rightTemplateBuffer = data.getByteArrayExtra(Constants.EXTRA_RIGHT_TEMPLATE);

            if (leftTemplateBuffer != null) {
                saveFile(folderPath, prefix, filePurpose + "L" + templateExt, leftTemplateBuffer);
            }
            if (rightTemplateBuffer != null) {
                saveFile(folderPath, prefix, filePurpose + "R" + templateExt, rightTemplateBuffer);
            }
        } else {
            Toast.makeText(this, "Lỗi ghi danh: " + resultMsg, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveFile(String filePath, String prefix, String fileName, byte[] byteArray) {
        if (byteArray == null) return;

        String fileFullname = prefix + fileName;
        String filePathName = filePath + File.separator + fileFullname;
        File folder = new File(filePath);
        File file = new File(filePathName);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(byteArray);
            fos.flush();
            Toast.makeText(this, "Đã lưu tệp: " + filePathName, Toast.LENGTH_SHORT).show();
        } catch (IOException ex) {
            Toast.makeText(this, "Lỗi khi lưu tệp: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void markAttendance(String studentId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Get current time in Vietnam timezone (GMT+7)
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String currentTime = timeFormat.format(calendar.getTime());
        String currentDate = dateFormat.format(calendar.getTime());

        // Insert vào checkin_history với checkin_date
        String query = "INSERT INTO " + DatabaseHelper.TABLE_CHECKIN_HISTORY + " (" +
                DatabaseHelper.COL_STUDENT_ID + ", " +
                DatabaseHelper.COL_SUBJECT_ID + ", " +
                DatabaseHelper.COL_CHECKIN_TIME + ", " +
                DatabaseHelper.COL_CHECKIN_DATE + ") VALUES (?, ?, ?, ?)";

        db.execSQL(query, new Object[]{studentId, currentSubjectId, currentTime, currentDate});
        
        // Get student name from database
        String studentName = "";
        Cursor cursor = db.rawQuery(
            "SELECT " + DatabaseHelper.COL_FULL_NAME + 
            " FROM " + DatabaseHelper.TABLE_STUDENTS + 
            " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?",
            new String[]{studentId}
        );
        if (cursor.moveToFirst()) {
            studentName = cursor.getString(0);
        }
        cursor.close();

        // Show dialog with student name
        BestImageDialog dialog = new BestImageDialog(this);
        dialog.setTitle("Thông báo!");
        dialog.show();
        dialog.setBestImages(null, null, null);
        dialog.setMessage("Điểm danh thành công!\n" +
                         "Sinh viên: " + studentName + "\n" +
                         "Thời gian: " + currentTime + "\n" +
                         "Ngày: " + currentDate);
        applyFilters();
    }
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (data != null) {
//            int resultCodeExt = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
//            mResultCode = resultCodeExt;
//        }
//
//        if (mResultCode != GemResult.IDDK_UVC_DEVICE_ACCESS_DENIED){
//            if (resultCode == RESULT_OK) {
//                if (requestCode == REQUEST_CODE_IDENTIFY) {
//                    // Handle IDENTIFY result
//                    processIdentifyResult(data);
//                } else if (requestCode == REQUEST_CODE_ENROLL) {
//                    // Handle ENROLL result
//                    processEnrollResult(data);
//                } else if (requestCode == REQUEST_CODE_UNENROLL) {
//                    // Handle UNENROLL result
//                    processUnenrollResult(data);
//                } else if (requestCode == 1001) {
//                    // Handle edit/add student result
//                    if (currentSubjectId != null) {
//                        reloadStudents(currentSubjectId);
//                    }
//                }
//
//                else {
//                    Toast.makeText(StudentListActivity.this,"Success",Toast.LENGTH_SHORT).show();
//                }
//            } else {
//                Toast.makeText(getApplicationContext(), "Capture activity failed", Toast.LENGTH_LONG).show();
//            }
//        }
//    }
@Override
protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (data != null) {
        int resultCodeExt = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
        mResultCode = resultCodeExt;
    }

    if (mResultCode != GemResult.IDDK_UVC_DEVICE_ACCESS_DENIED){
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_IDENTIFY) {
                // Handle IDENTIFY result
                processIdentifyResult(data);
            } else if (requestCode == REQUEST_CODE_ENROLL) {
                // Handle ENROLL result
                processEnrollResult(data);
            } else if (requestCode == REQUEST_CODE_UNENROLL) {
                // Handle UNENROLL result
                processUnenrollResult(data);
            } else if (requestCode == 1001) {
                // Handle edit/add student result
                if (currentSubjectId != null) {
                    reloadStudents(currentSubjectId);
                }
            }else if (requestCode == REQUEST_CODE_FACE_ID) {
                if (data == null) {   pendingFaceStudentId = null;
                    Toast.makeText(
                            this,
                            "Đã hủy nhận diện Face ID",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                String recognizedStudentId = data.getStringExtra("student_id");
                String recognizedStudentName = data.getStringExtra("student_name");
                float distance = data.getFloatExtra("distance", -1f);
                boolean manualConfirmed =
                        data.getBooleanExtra("manual_confirmed", false);
                if (pendingFaceStudentId == null) {
                    Toast.makeText(
                            this,
                            "Không tìm thấy sinh viên đã xác thực mật khẩu",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }
                if (recognizedStudentId == null ||
                        !pendingFaceStudentId.equals(recognizedStudentId)) {
                    pendingFaceStudentId = null;

                    Toast.makeText(
                            this,
                            "Khuôn mặt không khớp với sinh viên đã xác thực mật khẩu",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                if (!isStudentInSubject(recognizedStudentId, currentSubjectId)) {
                    Toast.makeText(this, "Sinh viên không thuộc lớp này", Toast.LENGTH_SHORT).show();
                    return;
                }
                String verifiedStudentId = pendingFaceStudentId;
                pendingFaceStudentId = null;
                String note = manualConfirmed ? " - xác nhận thủ công" : "";
                Toast.makeText(
                        this,
                        "Face ID hợp lệ: " + recognizedStudentName +
                                note +
                                " | distance=" + String.format("%.2f", distance),
                        Toast.LENGTH_LONG
                ).show();

                performCheckinLogic(verifiedStudentId);}
            // =============================================================
            else {
                Toast.makeText(StudentListActivity.this,"Success",Toast.LENGTH_SHORT).show();
            }
        } else {
            if (requestCode == REQUEST_CODE_FACE_ID) {
                pendingFaceStudentId = null;
            }

            Toast.makeText(
                    getApplicationContext(),
                    "Capture activity failed",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}
    private void processIdentifyResult(Intent data) {
        int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
        if (resultCode == 0) {
            int resultCount = data.getIntExtra(Constants.EXTRA_MATCHING_COUNT, 0);
            String resultItems = data.getStringExtra(Constants.EXTRA_MATCHING_ITEMS);
            
            if (resultCount > 0 && resultItems != null) {
                // Parse student ID from result items
                // Format: "ID: studentId1, distance: xxx; ID: studentId2, distance: yyy"
                String studentId = extractStudentIdFromResult(resultItems);
                if (studentId != null && !studentId.isEmpty()) {
                    // Check if student belongs to this subject
                    if (isStudentInSubject(studentId, currentSubjectId)) {
                        markAttendance(studentId);
                    } else {
                        // Show dialog that student doesn't belong to this class
                        BestImageDialog dialog = new BestImageDialog(this);
                        dialog.setTitle("Thông báo!");
                        dialog.show();
                        dialog.setBestImages(null, null, null);
                        dialog.setMessage("Sinh viên không thuộc lớp này");
                    }
                } else {
                    Toast.makeText(this, "Không thể xác định sinh viên từ kết quả", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Không tìm thấy sinh viên khớp", Toast.LENGTH_SHORT).show();
            }
        } else {
            String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);
            Toast.makeText(this, "Lỗi điểm danh: " + resultMsg, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isStudentInSubject(String studentId, String subjectId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ENROLLMENTS + 
                " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ? AND " + 
                DatabaseHelper.COL_SUBJECT_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{studentId, subjectId});
        
        boolean exists = false;
        if (cursor.moveToFirst()) {
            exists = cursor.getInt(0) > 0;
        }
        cursor.close();
        return exists;
    }

    private String extractStudentIdFromResult(String resultItems) {
        // Parse format: "ID: studentId1, distance: xxx" or "ID: studentId1, distance: xxx; ID: studentId2, distance: yyy"
        try {
            // Get the first ID (best match)
            if (resultItems.contains("ID: ")) {
                int startIndex = resultItems.indexOf("ID: ") + 4;
                int endIndex = resultItems.indexOf(", distance:", startIndex);
                if (endIndex > startIndex) {
                    return resultItems.substring(startIndex, endIndex).trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void processEnrollResult(Intent data) {
        int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, 0);
        String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);

        if (resultCode == 0) {
            Toast.makeText(this, "Ghi danh thành công!", Toast.LENGTH_SHORT).show();
            // Reload students to show the newly enrolled student
            if (currentSubjectId != null) {
                reloadStudents(currentSubjectId);
            }
        } else {
            Toast.makeText(this, "Lỗi ghi danh: " + resultMsg, Toast.LENGTH_SHORT).show();
        }
    }

    private void processUnenrollResult(Intent data) {
        int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, 0);
        String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);

        if (resultCode == 0) {
            Toast.makeText(this, "Hủy ghi danh thành công!", Toast.LENGTH_SHORT).show();
            // Reload students
            if (currentSubjectId != null) {
                reloadStudents(currentSubjectId);
            }
        } else {
            Toast.makeText(this, "Lỗi hủy ghi danh: " + resultMsg, Toast.LENGTH_SHORT).show();
        }
    }

    private void exportAttendanceToCSV() {
        if (!checkStoragePermission()) {
            return;
        }

        if (currentSubjectId == null) {
            Toast.makeText(this, "Không tìm thấy thông tin môn học", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            
            // Get subject name
            String subjectName = "";
            Cursor subjectCursor = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_SUBJECT_NAME + 
                " FROM " + DatabaseHelper.TABLE_SUBJECTS + 
                " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{currentSubjectId}
            );
            if (subjectCursor.moveToFirst()) {
                subjectName = subjectCursor.getString(0);
            }
            subjectCursor.close();

            // Query attendance data with student names
            String query = "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", " +
                    "s." + DatabaseHelper.COL_FULL_NAME + ", " +
                    "ch." + DatabaseHelper.COL_CHECKIN_DATE + ", " +
                    "ch." + DatabaseHelper.COL_CHECKIN_TIME + " " +
                    "FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY + " ch " +
                    "JOIN " + DatabaseHelper.TABLE_STUDENTS + " s ON ch." + DatabaseHelper.COL_STUDENT_ID + " = s." + DatabaseHelper.COL_STUDENT_ID + " " +
                    "WHERE ch." + DatabaseHelper.COL_SUBJECT_ID + " = ? " +
                    "ORDER BY ch." + DatabaseHelper.COL_CHECKIN_DATE + " DESC, ch." + DatabaseHelper.COL_CHECKIN_TIME + " DESC";

            Cursor cursor = db.rawQuery(query, new String[]{currentSubjectId});

            // Create CSV content
            StringBuilder csv = new StringBuilder();
            csv.append("Mã sinh viên,Họ và tên,Ngày điểm danh,Giờ điểm danh\n");

            if (cursor.moveToFirst()) {
                do {
                    String studentId = cursor.getString(0);
                    String fullName = cursor.getString(1);
                    String checkinDate = cursor.getString(2);
                    String checkinTime = cursor.getString(3);
                    
                    // Escape commas and quotes in CSV
                    csv.append(escapeCsvField(studentId)).append(",");
                    csv.append(escapeCsvField(fullName)).append(",");
                    csv.append(escapeCsvField(checkinDate)).append(",");
                    csv.append(escapeCsvField(checkinTime)).append("\n");
                } while (cursor.moveToNext());
            } else {
                csv.append("Chưa có dữ liệu điểm danh\n");
            }
            cursor.close();

            // Save to Downloads folder
            String fileName = "DiemDanh_" + subjectName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + 
                    System.currentTimeMillis() + ".csv";
            
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File csvFile = new File(downloadsDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(csvFile)) {
                fos.write(csv.toString().getBytes("UTF-8"));
                fos.flush();
                Toast.makeText(this, "Đã xuất file CSV thành công!\n" + csvFile.getAbsolutePath(), 
                        Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this, "Lỗi khi lưu file CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi xuất CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private String escapeCsvField(String field) {
        if (field == null) return "";
        // If field contains comma, quote, or newline, wrap in quotes and escape quotes
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private boolean checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(StudentListActivity.this)
                        .setTitle("Cần quyền lưu trữ")
                        .setMessage("Ứng dụng cần quyền lưu trữ để xuất file CSV.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(StudentListActivity.this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_IRIS_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && pendingIrisIntent != null) {
                LicenseCheckHelper.checkLicenseAndStartCapture(this, pendingIrisIntent, pendingRequestCode);
                pendingIrisIntent = null;
            } else {
                Toast.makeText(this,
                        "Cần cấp quyền Camera và bộ nhớ để sử dụng tính năng mống mắt.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportAttendanceToCSV();
            } else {
                Toast.makeText(this, "Cần quyền lưu trữ để xuất file CSV", Toast.LENGTH_SHORT).show();
            }
        }
    }
}