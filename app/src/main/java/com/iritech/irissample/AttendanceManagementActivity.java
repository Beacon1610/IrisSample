package com.iritech.irissample;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.iritech.irissample.adapter.AttendanceRecordAdapter;
import com.iritech.irissample.adapter.EmailRecipientAdapter;
import com.iritech.irissample.model.AttendanceRecord;
import com.iritech.irissample.model.EmailRecipient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Activity quản lý xuất CSV và gửi email báo cáo điểm danh
 * - Tab 1: Xuất CSV
 * - Tab 2: Quản lý email recipients và gửi email
 * - Tab 3: Xem điểm danh (NEW)
 */
public class AttendanceManagementActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_STORAGE_PERMISSION = 200;
    private static final int REQUEST_CODE_IMPORT_EMAIL_CSV = 300;
    private static final int REQUEST_CODE_IMPORT_ATTENDANCE_CSV = 400;
    private static final int REQUEST_CODE_CREATE_CSV_DOCUMENT = 500;

    // UI Components - Tabs
    private Button btnTabExport, btnTabEmail, btnTabView;
    private ScrollView layoutExportTab, layoutEmailTab;
    private LinearLayout layoutViewTab;

    // Export Tab
    private TextView textSubjectInfo, textRecordCount;
    private Button btnExportCsv;

    // Email Tab
    private EditText editEmailAddress, editRecipientName;
    private Button btnAddEmail, btnImportEmailCsv, btnSendEmail;
    private CheckBox checkBoxSelectAll;
    private TextView textSelectedCount, textEmptyEmailList;
    private RecyclerView recyclerEmailList;

    // View Tab (NEW)
    private EditText editSearchStudent;
    private Button btnSelectDate, btnFilterAll, btnFilterAttended, btnFilterNotAttended, btnImportCsvView;
    private TextView textViewStats, textEmptyAttendanceList;
    private RecyclerView recyclerAttendanceList;
    private AttendanceRecordAdapter attendanceAdapter;
    private List<AttendanceRecord> attendanceList;
    private List<AttendanceRecord> filteredAttendanceList;
    private String selectedDate; // Format: dd/MM/yyyy, null = tất cả
    private int currentFilter = 0; // 0 = all, 1 = attended, 2 = not attended

    // Data
    private DatabaseHelper dbHelper;
    private String currentSubjectId;
    private String currentSubjectName;
    private EmailRecipientAdapter emailAdapter;
    private List<EmailRecipient> emailList;
    private File lastExportedCsvFile;
    private String pendingCsvContent; // CSV content chờ ghi vào vị trí user chọn

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_management);

        currentSubjectId = getIntent().getStringExtra("subject_id");
        if (currentSubjectId == null) {
            Toast.makeText(this, "Không tìm thấy thông tin môn học", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        dbHelper = new DatabaseHelper(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Quản lý điểm danh");
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());

        initViews();
        loadSubjectInfo();
        setupTabs();
        loadEmailRecipients();

        // Init View Tab data
        attendanceList = new ArrayList<>();
        filteredAttendanceList = new ArrayList<>();
        selectedDate = getTodayDate();

        showTab(0); // Export tab by default
    }

    private void initViews() {
        // Tab buttons
        btnTabExport = findViewById(R.id.btnTabExport);
        btnTabEmail = findViewById(R.id.btnTabEmail);
        btnTabView = findViewById(R.id.btnTabView);

        // Tab content layouts
        layoutExportTab = findViewById(R.id.layoutExportTab);
        layoutEmailTab = findViewById(R.id.layoutEmailTab);
        layoutViewTab = findViewById(R.id.layoutViewTab);

        // Export tab
        textSubjectInfo = findViewById(R.id.textSubjectInfo);
        textRecordCount = findViewById(R.id.textRecordCount);
        btnExportCsv = findViewById(R.id.btnExportCsv);

        // Email tab
        editEmailAddress = findViewById(R.id.editEmailAddress);
        editRecipientName = findViewById(R.id.editRecipientName);
        btnAddEmail = findViewById(R.id.btnAddEmail);
        btnImportEmailCsv = findViewById(R.id.btnImportEmailCsv);
        btnSendEmail = findViewById(R.id.btnSendEmail);
        checkBoxSelectAll = findViewById(R.id.checkBoxSelectAll);
        textSelectedCount = findViewById(R.id.textSelectedCount);
        textEmptyEmailList = findViewById(R.id.textEmptyEmailList);
        recyclerEmailList = findViewById(R.id.recyclerEmailList);

        // View tab (NEW)
        editSearchStudent = findViewById(R.id.editSearchStudent);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterAttended = findViewById(R.id.btnFilterAttended);
        btnFilterNotAttended = findViewById(R.id.btnFilterNotAttended);
        btnImportCsvView = findViewById(R.id.btnImportCsvView);
        textViewStats = findViewById(R.id.textViewStats);
        textEmptyAttendanceList = findViewById(R.id.textEmptyAttendanceList);
        recyclerAttendanceList = findViewById(R.id.recyclerAttendanceList);
    }

    private void setupTabs() {
        // Tab clicks
        btnTabExport.setOnClickListener(v -> showTab(0));
        btnTabEmail.setOnClickListener(v -> showTab(1));
        btnTabView.setOnClickListener(v -> {
            showTab(2);
            loadAttendanceData();
        });

        // Export button
        btnExportCsv.setOnClickListener(v -> exportAttendanceToCSV());

        // Email tab buttons
        btnAddEmail.setOnClickListener(v -> addEmailManually());
        btnImportEmailCsv.setOnClickListener(v -> openEmailCsvPicker());
        btnSendEmail.setOnClickListener(v -> sendEmailWithCsv());

        checkBoxSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (emailAdapter != null) {
                emailAdapter.selectAll(isChecked);
            }
        });

        // Email RecyclerView
        emailList = new ArrayList<>();
        emailAdapter = new EmailRecipientAdapter(this, emailList);
        recyclerEmailList.setLayoutManager(new LinearLayoutManager(this));
        recyclerEmailList.setAdapter(emailAdapter);

        emailAdapter.setOnRecipientActionListener(new EmailRecipientAdapter.OnRecipientActionListener() {
            @Override
            public void onDeleteClick(EmailRecipient recipient) {
                confirmDeleteEmail(recipient);
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                updateSelectedCount(selectedCount);
            }
        });

        // View Tab Setup (NEW)
        setupViewTab();
    }

    private void setupViewTab() {
        // Attendance RecyclerView
        attendanceAdapter = new AttendanceRecordAdapter(this, filteredAttendanceList);
        recyclerAttendanceList.setLayoutManager(new LinearLayoutManager(this));
        recyclerAttendanceList.setAdapter(attendanceAdapter);

        // Date picker
        btnSelectDate.setText("Hôm nay");
        btnSelectDate.setOnClickListener(v -> showDatePicker());

        // Filter buttons
        btnFilterAll.setOnClickListener(v -> {
            currentFilter = 0;
            updateFilterButtons();
            applyFilters();
        });

        btnFilterAttended.setOnClickListener(v -> {
            currentFilter = 1;
            updateFilterButtons();
            applyFilters();
        });

        btnFilterNotAttended.setOnClickListener(v -> {
            currentFilter = 2;
            updateFilterButtons();
            applyFilters();
        });

        // Search
        editSearchStudent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Import CSV
        btnImportCsvView.setOnClickListener(v -> openAttendanceCsvPicker());

        updateFilterButtons();
    }

    private void showTab(int tabIndex) {
        layoutExportTab.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutEmailTab.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        layoutViewTab.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        btnTabExport.setAlpha(tabIndex == 0 ? 1.0f : 0.6f);
        btnTabEmail.setAlpha(tabIndex == 1 ? 1.0f : 0.6f);
        btnTabView.setAlpha(tabIndex == 2 ? 1.0f : 0.6f);
    }

    private void loadSubjectInfo() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor subjectCursor = db.rawQuery(
            "SELECT " + DatabaseHelper.COL_SUBJECT_NAME +
            " FROM " + DatabaseHelper.TABLE_SUBJECTS +
            " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
            new String[]{currentSubjectId}
        );

        if (subjectCursor.moveToFirst()) {
            currentSubjectName = subjectCursor.getString(0);
            textSubjectInfo.setText("Môn học: " + currentSubjectName);
        }
        subjectCursor.close();

        Cursor countCursor = db.rawQuery(
            "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
            " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
            new String[]{currentSubjectId}
        );

        if (countCursor.moveToFirst()) {
            int count = countCursor.getInt(0);
            textRecordCount.setText("Số bản ghi điểm danh: " + count);
        }
        countCursor.close();
    }

    // ==================== VIEW TAB FUNCTIONS (NEW) ====================

    private String getTodayDate() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year);
            btnSelectDate.setText(selectedDate.substring(0, 5)); // dd/MM
            loadAttendanceData();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        // Add "Tất cả" option
        dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Tất cả", (d, which) -> {
            selectedDate = null;
            btnSelectDate.setText("Tất cả");
            loadAttendanceData();
        });

        dialog.show();
    }

    private void loadAttendanceData() {
        attendanceList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Get all students in this subject
        String studentQuery = "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", s." + DatabaseHelper.COL_FULL_NAME +
                " FROM " + DatabaseHelper.TABLE_STUDENTS + " s " +
                " JOIN " + DatabaseHelper.TABLE_ENROLLMENTS + " e ON s." + DatabaseHelper.COL_STUDENT_ID +
                " = e." + DatabaseHelper.COL_STUDENT_ID +
                " WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ?" +
                " ORDER BY s." + DatabaseHelper.COL_FULL_NAME;

        Cursor studentCursor = db.rawQuery(studentQuery, new String[]{currentSubjectId});

        if (studentCursor.moveToFirst()) {
            do {
                String studentId = studentCursor.getString(0);
                String fullName = studentCursor.getString(1);

                // Check attendance for this student
                String attendanceQuery;
                String[] args;

                if (selectedDate != null) {
                    // Specific date
                    attendanceQuery = "SELECT " + DatabaseHelper.COL_CHECKIN_DATE + ", " + DatabaseHelper.COL_CHECKIN_TIME +
                            " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                            " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ? AND " +
                            DatabaseHelper.COL_SUBJECT_ID + " = ? AND " +
                            DatabaseHelper.COL_CHECKIN_DATE + " = ?" +
                            " ORDER BY " + DatabaseHelper.COL_CHECKIN_TIME + " DESC LIMIT 1";
                    args = new String[]{studentId, currentSubjectId, selectedDate};
                } else {
                    // All dates - get latest
                    attendanceQuery = "SELECT " + DatabaseHelper.COL_CHECKIN_DATE + ", " + DatabaseHelper.COL_CHECKIN_TIME +
                            " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                            " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ? AND " +
                            DatabaseHelper.COL_SUBJECT_ID + " = ?" +
                            " ORDER BY " + DatabaseHelper.COL_CHECKIN_DATE + " DESC, " +
                            DatabaseHelper.COL_CHECKIN_TIME + " DESC LIMIT 1";
                    args = new String[]{studentId, currentSubjectId};
                }

                Cursor attendanceCursor = db.rawQuery(attendanceQuery, args);

                if (attendanceCursor.moveToFirst()) {
                    String date = attendanceCursor.getString(0);
                    String time = attendanceCursor.getString(1);
                    attendanceList.add(new AttendanceRecord(studentId, fullName, true, date, time));
                } else {
                    attendanceList.add(new AttendanceRecord(studentId, fullName));
                }
                attendanceCursor.close();

            } while (studentCursor.moveToNext());
        }
        studentCursor.close();

        applyFilters();
    }

    private void applyFilters() {
        filteredAttendanceList.clear();
        String searchText = editSearchStudent.getText().toString().toLowerCase().trim();

        for (AttendanceRecord record : attendanceList) {
            // Search filter
            boolean matchesSearch = searchText.isEmpty() ||
                    record.getStudentId().toLowerCase().contains(searchText) ||
                    record.getFullName().toLowerCase().contains(searchText);

            if (!matchesSearch) continue;

            // Status filter
            boolean matchesFilter = false;
            switch (currentFilter) {
                case 0: // All
                    matchesFilter = true;
                    break;
                case 1: // Attended
                    matchesFilter = record.isAttended();
                    break;
                case 2: // Not attended
                    matchesFilter = !record.isAttended();
                    break;
            }

            if (matchesFilter) {
                filteredAttendanceList.add(record);
            }
        }

        attendanceAdapter.updateList(filteredAttendanceList);
        updateStats();
        updateEmptyAttendanceState();
    }

    private void updateFilterButtons() {
        btnFilterAll.setAlpha(currentFilter == 0 ? 1.0f : 0.5f);
        btnFilterAttended.setAlpha(currentFilter == 1 ? 1.0f : 0.5f);
        btnFilterNotAttended.setAlpha(currentFilter == 2 ? 1.0f : 0.5f);
    }

    private void updateStats() {
        int total = attendanceList.size();
        int attended = 0;
        for (AttendanceRecord record : attendanceList) {
            if (record.isAttended()) attended++;
        }

        int percent = total > 0 ? (attended * 100 / total) : 0;
        textViewStats.setText("Thống kê: " + attended + "/" + total + " sinh viên đã điểm danh (" + percent + "%)");
    }

    private void updateEmptyAttendanceState() {
        if (filteredAttendanceList.isEmpty()) {
            recyclerAttendanceList.setVisibility(View.GONE);
            textEmptyAttendanceList.setVisibility(View.VISIBLE);
        } else {
            recyclerAttendanceList.setVisibility(View.VISIBLE);
            textEmptyAttendanceList.setVisibility(View.GONE);
        }
    }

    private void openAttendanceCsvPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn file CSV điểm danh"), REQUEST_CODE_IMPORT_ATTENDANCE_CSV);
    }

    private void importAttendanceFromCsv(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            // Skip BOM if present
            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }

            attendanceList.clear();
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    // Skip header if contains column names
                    if (line.toLowerCase().contains("mã") || line.toLowerCase().contains("sinh viên")) {
                        continue;
                    }
                }

                String[] tokens = line.split(",");
                if (tokens.length >= 2) {
                    String studentId = tokens[0].trim().replace("\"", "");
                    String fullName = tokens[1].trim().replace("\"", "");
                    String date = tokens.length >= 3 ? tokens[2].trim().replace("\"", "") : "";
                    String time = tokens.length >= 4 ? tokens[3].trim().replace("\"", "") : "";

                    boolean attended = !date.isEmpty() && !date.equals("--");
                    attendanceList.add(new AttendanceRecord(studentId, fullName, attended,
                            attended ? date : null, attended ? time : null));
                }
            }

            reader.close();

            applyFilters();
            Toast.makeText(this, "Import thành công " + attendanceList.size() + " sinh viên!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi import CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    // ==================== EXPORT CSV FUNCTIONS ====================

    private void exportAttendanceToCSV() {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            // Lấy thông tin môn học + giảng viên cho header CSV
            String instructorName = "";
            Cursor subjectCursor = db.rawQuery(
                "SELECT sub." + DatabaseHelper.COL_SUBJECT_NAME + ", " +
                "adm." + DatabaseHelper.COL_ADMIN_FULL_NAME + " " +
                "FROM " + DatabaseHelper.TABLE_SUBJECTS + " sub " +
                "LEFT JOIN " + DatabaseHelper.TABLE_ADMIN + " adm ON sub." +
                DatabaseHelper.COL_SUBJECT_INSTRUCTOR_ID + " = adm." + DatabaseHelper.COL_ADMIN_ID + " " +
                "WHERE sub." + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{currentSubjectId}
            );
            if (subjectCursor.moveToFirst()) {
                instructorName = subjectCursor.getString(1) != null ? subjectCursor.getString(1) : "Chưa phân công";
            }
            subjectCursor.close();

            String exportDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());

            // Query: Tất cả sinh viên enrolled, LEFT JOIN checkin_history để lấy điểm danh
            String query = "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", " +
                    "s." + DatabaseHelper.COL_FULL_NAME + ", " +
                    "ch." + DatabaseHelper.COL_CHECKIN_DATE + ", " +
                    "ch." + DatabaseHelper.COL_CHECKIN_TIME + " " +
                    "FROM " + DatabaseHelper.TABLE_ENROLLMENTS + " e " +
                    "JOIN " + DatabaseHelper.TABLE_STUDENTS + " s ON e." + DatabaseHelper.COL_STUDENT_ID +
                    " = s." + DatabaseHelper.COL_STUDENT_ID + " " +
                    "LEFT JOIN " + DatabaseHelper.TABLE_CHECKIN_HISTORY + " ch ON " +
                    "ch." + DatabaseHelper.COL_STUDENT_ID + " = s." + DatabaseHelper.COL_STUDENT_ID +
                    " AND ch." + DatabaseHelper.COL_SUBJECT_ID + " = ? " +
                    "WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ? " +
                    "ORDER BY ch." + DatabaseHelper.COL_CHECKIN_DATE + " IS NULL, " +
                    "ch." + DatabaseHelper.COL_CHECKIN_DATE + " DESC, " +
                    "ch." + DatabaseHelper.COL_CHECKIN_TIME + " DESC, " +
                    "s." + DatabaseHelper.COL_STUDENT_ID + " ASC";

            Cursor cursor = db.rawQuery(query, new String[]{currentSubjectId, currentSubjectId});

            StringBuilder csv = new StringBuilder();
            csv.append("\uFEFF"); // UTF-8 BOM
            // Thông tin môn học ở đầu file
            csv.append("Thông tin môn học\n");
            csv.append("Mã môn,").append(escapeCsvField(currentSubjectId)).append("\n");
            csv.append("Tên môn,").append(escapeCsvField(currentSubjectName)).append("\n");
            csv.append("Giảng viên,").append(escapeCsvField(instructorName)).append("\n");
            csv.append("Ngày xuất,").append(escapeCsvField(exportDate)).append("\n");
            csv.append("\n"); // Dòng trống ngăn cách
            csv.append("Mã sinh viên,Họ và tên,Ngày điểm danh,Giờ điểm danh\n");

            if (cursor.moveToFirst()) {
                do {
                    String studentId = cursor.getString(0);
                    String fullName = cursor.getString(1);
                    String checkinDate = cursor.isNull(2) ? "" : cursor.getString(2);
                    String checkinTime = cursor.isNull(3) ? "" : cursor.getString(3);

                    csv.append(escapeCsvField(studentId)).append(",");
                    csv.append(escapeCsvField(fullName)).append(",");
                    csv.append(escapeCsvField(checkinDate)).append(",");
                    csv.append(escapeCsvField(checkinTime)).append("\n");
                } while (cursor.moveToNext());
            } else {
                csv.append("Chưa có sinh viên nào trong danh sách môn\n");
            }
            cursor.close();

            pendingCsvContent = csv.toString();

            // Lưu 1 bản vào cache dir cho tính năng gửi email
            String fileName = "DiemDanh_" + currentSubjectName.replaceAll("[^a-zA-Z0-9]", "_") + "_" +
                    System.currentTimeMillis() + ".csv";
            try {
                File cacheFile = new File(getCacheDir(), fileName);
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    fos.write(pendingCsvContent.getBytes("UTF-8"));
                    fos.flush();
                }
                lastExportedCsvFile = cacheFile;
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Mở SAF để người dùng chọn vị trí lưu file
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(intent, REQUEST_CODE_CREATE_CSV_DOCUMENT);

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi xuất CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private String escapeCsvField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private void writeCsvToUri(Uri uri) {
        if (pendingCsvContent == null) {
            Toast.makeText(this, "Không có dữ liệu CSV để lưu", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(pendingCsvContent.getBytes("UTF-8"));
                os.flush();
                os.close();
                Toast.makeText(this, "Đã xuất file CSV thành công!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi lưu file CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
        pendingCsvContent = null;
    }

    private boolean checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_STORAGE_PERMISSION);
            return false;
        }
        return true;
    }

    // ==================== EMAIL MANAGEMENT FUNCTIONS ====================

    private void loadEmailRecipients() {
        emailList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
            "SELECT * FROM " + DatabaseHelper.TABLE_EMAIL_RECIPIENTS +
            " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ? " +
            " ORDER BY " + DatabaseHelper.COL_ADDED_DATE + " DESC",
            new String[]{currentSubjectId}
        );

        if (cursor.moveToFirst()) {
            do {
                int emailId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EMAIL_ID));
                String email = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EMAIL_ADDRESS));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_RECIPIENT_NAME));
                String date = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADDED_DATE));

                EmailRecipient recipient = new EmailRecipient(emailId, currentSubjectId, email, name, date);
                emailList.add(recipient);
            } while (cursor.moveToNext());
        }
        cursor.close();

        emailAdapter.notifyDataSetChanged();
        updateEmptyState();
        updateSelectedCount(0);
    }

    private void addEmailManually() {
        String email = editEmailAddress.getText().toString().trim();
        String name = editRecipientName.getText().toString().trim();

        if (email.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập email", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        if (addEmailToDatabase(email, name)) {
            editEmailAddress.setText("");
            editRecipientName.setText("");
            loadEmailRecipients();
            Toast.makeText(this, "Đã thêm email", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean addEmailToDatabase(String email, String name) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_SUBJECT_ID, currentSubjectId);
        values.put(DatabaseHelper.COL_EMAIL_ADDRESS, email);
        values.put(DatabaseHelper.COL_RECIPIENT_NAME, name);
        values.put(DatabaseHelper.COL_ADDED_DATE,
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

        try {
            long result = db.insertWithOnConflict(DatabaseHelper.TABLE_EMAIL_RECIPIENTS, null,
                                                  values, SQLiteDatabase.CONFLICT_IGNORE);
            return result != -1;
        } catch (Exception e) {
            Toast.makeText(this, "Email đã tồn tại", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void confirmDeleteEmail(EmailRecipient recipient) {
        new AlertDialog.Builder(this)
            .setTitle("Xóa Email")
            .setMessage("Bạn có chắc muốn xóa email \"" + recipient.getEmailAddress() + "\"?")
            .setPositiveButton("Xóa", (dialog, which) -> deleteEmail(recipient))
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void deleteEmail(EmailRecipient recipient) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rows = db.delete(DatabaseHelper.TABLE_EMAIL_RECIPIENTS,
                DatabaseHelper.COL_EMAIL_ID + " = ?",
                new String[]{String.valueOf(recipient.getEmailId())});

        if (rows > 0) {
            loadEmailRecipients();
            Toast.makeText(this, "Đã xóa email", Toast.LENGTH_SHORT).show();
        }
    }

    private void openEmailCsvPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn file CSV email"), REQUEST_CODE_IMPORT_EMAIL_CSV);
    }

    private void importEmailsFromCsv(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }

            String line;
            int count = 0;

            String firstLine = reader.readLine();
            if (firstLine != null && !firstLine.toLowerCase().contains("email")) {
                String[] tokens = firstLine.split(",");
                if (tokens.length >= 1) {
                    String email = tokens[0].trim();
                    String name = tokens.length >= 2 ? tokens[1].trim() : "";
                    if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        if (addEmailToDatabase(email, name)) {
                            count++;
                        }
                    }
                }
            }

            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length >= 1) {
                    String email = tokens[0].trim();
                    String name = tokens.length >= 2 ? tokens[1].trim() : "";

                    if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        if (addEmailToDatabase(email, name)) {
                            count++;
                        }
                    }
                }
            }

            reader.close();

            loadEmailRecipients();
            Toast.makeText(this, "Import thành công " + count + " email!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi import CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void sendEmailWithCsv() {
        String[] selectedEmails = emailAdapter.getSelectedEmails();

        if (selectedEmails.length == 0) {
            Toast.makeText(this, "Vui lòng chọn ít nhất 1 email", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lastExportedCsvFile == null || !lastExportedCsvFile.exists()) {
            new AlertDialog.Builder(this)
                .setTitle("Chưa có file CSV")
                .setMessage("Bạn cần xuất CSV trước khi gửi email. Xuất ngay bây giờ?")
                .setPositiveButton("Xuất CSV", (dialog, which) -> exportAttendanceToCSV())
                .setNegativeButton("Hủy", null)
                .show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Gửi Email")
            .setMessage("Gửi báo cáo điểm danh đến " + selectedEmails.length + " email?\n\n" +
                       "File đính kèm: " + lastExportedCsvFile.getName())
            .setPositiveButton("Gửi", (dialog, which) -> performSendEmail(selectedEmails))
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void performSendEmail(String[] recipients) {
        String subject = "Báo cáo điểm danh - " + currentSubjectName;
        String htmlBody = "<html><body style='font-family: Arial, sans-serif;'>" +
                "<h2>Báo cáo điểm danh</h2>" +
                "<p><strong>Môn học:</strong> " + currentSubjectName + "</p>" +
                "<p><strong>Mã môn:</strong> " + currentSubjectId + "</p>" +
                "<p><strong>Ngày gửi:</strong> " +
                new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()) + "</p>" +
                "<p>File CSV dữ liệu điểm danh đính kèm.</p>" +
                "<p style='margin-top: 30px;'>Trân trọng,<br/>" +
                BuildConfig.BREVO_SENDER_NAME + "</p>" +
                "</body></html>";

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Đang gửi email...")
                .setMessage("Vui lòng đợi")
                .setCancelable(false)
                .create();
        progressDialog.show();

        EmailService.sendAttendanceReportEmail(recipients, subject, htmlBody, lastExportedCsvFile,
                new EmailService.EmailCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(AttendanceManagementActivity.this,
                                    "Đã gửi email thành công đến " + recipients.length + " người nhận",
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(AttendanceManagementActivity.this)
                                    .setTitle("Lỗi gửi email")
                                    .setMessage("Không thể gửi email: " + error)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                });
    }

    private void updateEmptyState() {
        if (emailList.isEmpty()) {
            recyclerEmailList.setVisibility(View.GONE);
            textEmptyEmailList.setVisibility(View.VISIBLE);
            btnSendEmail.setEnabled(false);
        } else {
            recyclerEmailList.setVisibility(View.VISIBLE);
            textEmptyEmailList.setVisibility(View.GONE);
            btnSendEmail.setEnabled(true);
        }
    }

    private void updateSelectedCount(int count) {
        textSelectedCount.setText("Đã chọn: " + count + " email");
        btnSendEmail.setEnabled(count > 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (requestCode == REQUEST_CODE_IMPORT_EMAIL_CSV) {
                importEmailsFromCsv(uri);
            } else if (requestCode == REQUEST_CODE_IMPORT_ATTENDANCE_CSV) {
                importAttendanceFromCsv(uri);
            } else if (requestCode == REQUEST_CODE_CREATE_CSV_DOCUMENT && uri != null) {
                writeCsvToUri(uri);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportAttendanceToCSV();
            } else {
                Toast.makeText(this, "Cần quyền truy cập lưu trữ để xuất CSV", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
