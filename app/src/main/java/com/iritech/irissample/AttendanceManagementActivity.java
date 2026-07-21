package com.iritech.irissample;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.iritech.irissample.adapter.AttendanceRecordAdapter;
import com.iritech.irissample.adapter.EmailRecipientAdapter;
import com.iritech.irissample.adapter.StudentAttendanceStatsAdapter;
import com.iritech.irissample.export.AttendanceExportRepository;
import com.iritech.irissample.export.AttendanceExportService;
import com.iritech.irissample.model.AttendanceRecord;
import com.iritech.irissample.model.EmailRecipient;
import com.iritech.irissample.model.StudentAttendanceStats;
import com.iritech.irissample.adapter.StudentAttendanceDetailAdapter;
import com.iritech.irissample.model.export.AttendanceExportData;
import com.iritech.irissample.model.export.ExportedReport;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
 * - Tab 3: Xem điểm danh
 */
public class AttendanceManagementActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_IMPORT_EMAIL_CSV = 300;
    private static final int REQUEST_CODE_IMPORT_ATTENDANCE_CSV = 400;
    private static final int REQUEST_CODE_CREATE_CSV_DOCUMENT = 500;
    private static final int REQUEST_CODE_CREATE_EXCEL_DOCUMENT = 501;
    private static final int FILTER_ALL = 0;
    private static final int FILTER_PRESENT = 1;
    private static final int FILTER_ABSENT = 2;
    private static final int STATS_MODE_DAILY = 0;
    private static final int STATS_MODE_STUDENT = 1;

    // UI Components - Tabs
    private Button btnTabExport, btnTabEmail, btnTabView;
    private ScrollView layoutExportTab, layoutEmailTab;
    private LinearLayout layoutViewTab;

    // Export Tab
    private TextView textSubjectInfo, textRecordCount;
    private Button btnExportCsv, btnExportSummaryCsv, btnExportMatrixCsv, btnExportExcel;

    // Email Tab
    private EditText editEmailAddress, editRecipientName;
    private Button btnAddEmail, btnImportEmailCsv, btnSendEmail;
    private CheckBox checkBoxSelectAll;
    private TextView textSelectedCount, textEmptyEmailList;
    private TextView textSelectedReportType, textSelectedReportFileName, textSelectedReportMeta;
    private Button btnChooseReport;
    private RecyclerView recyclerEmailList;

    // View Tab
    private EditText editSearchStudent;
    private Button btnSelectDate, btnFilterAll, btnFilterAttended, btnFilterNotAttended, btnImportCsvView;
    private Button btnStatsByDate, btnStatsByStudent;
    private TextView textViewStats, textEmptyAttendanceList;
    private TextView textTotalStudents, textPresentStudents, textAbsentStudents;
    private TextView textTotalAttendanceSessions, textEmptyStudentAttendanceStats;
    private ProgressBar progressAttendanceRate;
    private AttendancePieChartView attendancePieChart;
    private View layoutDailyStatistics;
    private LinearLayout layoutStudentStatistics;
    private RecyclerView recyclerAttendanceList;
    private RecyclerView recyclerStudentAttendanceStats;
    private AttendanceRecordAdapter attendanceAdapter;
    private StudentAttendanceStatsAdapter studentAttendanceStatsAdapter;
    private List<AttendanceRecord> attendanceList;
    private List<AttendanceRecord> filteredAttendanceList;
    private List<StudentAttendanceStats> studentAttendanceStatsList;
    private String selectedDate; // Format: dd/MM/yyyy
    private int currentFilter = FILTER_ALL;
    private int currentStatsMode = STATS_MODE_DAILY;

    // Data
    private DatabaseHelper dbHelper;
    private AttendanceExportService attendanceExportService;
    private String currentSubjectId;
    private String currentSubjectName;
    private EmailRecipientAdapter emailAdapter;
    private List<EmailRecipient> emailList;
    private final List<ExportedReport> exportedReports = new ArrayList<>();
    private ExportedReport selectedEmailReport;
    private byte[] pendingExportData;
    private ExportedReport.ReportType pendingExportReportType;
    private String pendingExportDisplayName;
    private String pendingExportCacheFileName;
    private String pendingExportSubjectId;

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
        attendanceExportService = new AttendanceExportService(
                new AttendanceExportRepository(dbHelper)
        );
        attendanceList = new ArrayList<>();
        filteredAttendanceList = new ArrayList<>();
        studentAttendanceStatsList = new ArrayList<>();
        selectedDate = getTodayDate();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Quản lý điểm danh");
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());

        initViews();
        loadSubjectInfo();
        setupTabs();
        loadEmailRecipients();

        showTab(0);
        reloadCurrentStatistics();
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
        btnExportSummaryCsv = findViewById(R.id.btnExportSummaryCsv);
        btnExportMatrixCsv = findViewById(R.id.btnExportMatrixCsv);
        btnExportExcel = findViewById(R.id.btnExportExcel);

        // Email tab
        editEmailAddress = findViewById(R.id.editEmailAddress);
        editRecipientName = findViewById(R.id.editRecipientName);
        btnAddEmail = findViewById(R.id.btnAddEmail);
        btnImportEmailCsv = findViewById(R.id.btnImportEmailCsv);
        btnSendEmail = findViewById(R.id.btnSendEmail);
        checkBoxSelectAll = findViewById(R.id.checkBoxSelectAll);
        textSelectedCount = findViewById(R.id.textSelectedCount);
        textEmptyEmailList = findViewById(R.id.textEmptyEmailList);
        textSelectedReportType = findViewById(R.id.textSelectedReportType);
        textSelectedReportFileName = findViewById(R.id.textSelectedReportFileName);
        textSelectedReportMeta = findViewById(R.id.textSelectedReportMeta);
        btnChooseReport = findViewById(R.id.btnChooseReport);
        recyclerEmailList = findViewById(R.id.recyclerEmailList);

        // View tab
        editSearchStudent = findViewById(R.id.editSearchStudent);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterAttended = findViewById(R.id.btnFilterAttended);
        btnFilterNotAttended = findViewById(R.id.btnFilterNotAttended);
        btnImportCsvView = findViewById(R.id.btnImportCsvView);
        btnStatsByDate = findViewById(R.id.btnStatsByDate);
        btnStatsByStudent = findViewById(R.id.btnStatsByStudent);
        textViewStats = findViewById(R.id.textViewStats);
        textTotalStudents = findViewById(R.id.textTotalStudents);
        textPresentStudents = findViewById(R.id.textPresentStudents);
        textAbsentStudents = findViewById(R.id.textAbsentStudents);
        progressAttendanceRate = findViewById(R.id.progressAttendanceRate);
        attendancePieChart = findViewById(R.id.attendancePieChart);
        layoutDailyStatistics = findViewById(R.id.layoutDailyStatistics);
        layoutStudentStatistics = findViewById(R.id.layoutStudentStatistics);
        textTotalAttendanceSessions = findViewById(R.id.textTotalAttendanceSessions);
        textEmptyStudentAttendanceStats = findViewById(R.id.textEmptyStudentAttendanceStats);
        textEmptyAttendanceList = findViewById(R.id.textEmptyAttendanceList);
        recyclerAttendanceList = findViewById(R.id.recyclerAttendanceList);
        recyclerStudentAttendanceStats = findViewById(R.id.recyclerStudentAttendanceStats);
    }

    private void setupTabs() {
        // Tab clicks
        btnTabView.setOnClickListener(v -> {
            showTab(0);
            reloadCurrentStatistics();
        });
        btnTabExport.setOnClickListener(v -> showTab(1));
        btnTabEmail.setOnClickListener(v -> {
            updateSelectedReportUi();
            showTab(2);
        });

        // Export button
        btnExportCsv.setOnClickListener(v -> exportAttendanceToCSV());
        btnExportSummaryCsv.setOnClickListener(v -> exportAttendanceSummaryCsv());
        btnExportMatrixCsv.setOnClickListener(v -> exportAttendanceMatrixCsv());
        btnExportExcel.setOnClickListener(v -> startExportExcel());

        // Email tab buttons
        btnAddEmail.setOnClickListener(v -> addEmailManually());
        btnImportEmailCsv.setOnClickListener(v -> openEmailCsvPicker());
        btnSendEmail.setOnClickListener(v -> sendEmailWithCsv());
        btnChooseReport.setOnClickListener(v -> showReportSelectionDialog());

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

        // View Tab Setup
        setupViewTab();
    }

    private void setupViewTab() {
        // Attendance RecyclerView
        attendanceAdapter = new AttendanceRecordAdapter(this, filteredAttendanceList);
        recyclerAttendanceList.setLayoutManager(new LinearLayoutManager(this));
        recyclerAttendanceList.setAdapter(attendanceAdapter);

        studentAttendanceStatsAdapter = new StudentAttendanceStatsAdapter(
                this,
                studentAttendanceStatsList,
                this::showStudentAttendanceDetails
        );
        recyclerStudentAttendanceStats.setLayoutManager(new LinearLayoutManager(this));
        recyclerStudentAttendanceStats.setAdapter(studentAttendanceStatsAdapter);

        btnStatsByDate.setOnClickListener(v -> showStatisticsMode(STATS_MODE_DAILY));
        btnStatsByStudent.setOnClickListener(v -> showStatisticsMode(STATS_MODE_STUDENT));

        // Date picker
        btnSelectDate.setText(selectedDate);
        btnSelectDate.setOnClickListener(v -> showDatePicker());

        // Filter buttons
        btnFilterAll.setOnClickListener(v -> {
            currentFilter = FILTER_ALL;
            updateFilterButtons();
            applyFilters();
        });

        btnFilterAttended.setOnClickListener(v -> {
            currentFilter = FILTER_PRESENT;
            updateFilterButtons();
            applyFilters();
        });

        btnFilterNotAttended.setOnClickListener(v -> {
            currentFilter = FILTER_ABSENT;
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
        updateStatisticsModeUi();
    }

    private void showStatisticsMode(int mode) {
        currentStatsMode = mode;
        updateStatisticsModeUi();
        reloadCurrentStatistics();
    }

    private void updateStatisticsModeUi() {
        boolean dailyMode = currentStatsMode == STATS_MODE_DAILY;
        layoutDailyStatistics.setVisibility(dailyMode ? View.VISIBLE : View.GONE);
        layoutStudentStatistics.setVisibility(dailyMode ? View.GONE : View.VISIBLE);
        updateFilterButtonStyle(btnStatsByDate, dailyMode);
        updateFilterButtonStyle(btnStatsByStudent, !dailyMode);
    }

    private void reloadCurrentStatistics() {
        if (currentStatsMode == STATS_MODE_STUDENT) {
            loadStudentAttendanceStats();
        } else {
            loadAttendanceData();
        }
    }

    private void showTab(int tabIndex) {
        layoutViewTab.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutExportTab.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        layoutEmailTab.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        btnTabView.setAlpha(tabIndex == 0 ? 1.0f : 0.6f);
        btnTabExport.setAlpha(tabIndex == 1 ? 1.0f : 0.6f);
        btnTabEmail.setAlpha(tabIndex == 2 ? 1.0f : 0.6f);
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

    // ==================== VIEW TAB FUNCTIONS ====================

    private String getTodayDate() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        if (selectedDate != null) {
            String[] dateParts = selectedDate.split("/");
            if (dateParts.length == 3) {
                try {
                    calendar.set(
                            Integer.parseInt(dateParts[2]),
                            Integer.parseInt(dateParts[1]) - 1,
                            Integer.parseInt(dateParts[0])
                    );
                } catch (NumberFormatException ignored) {
                    // Giữ ngày hiện tại nếu dữ liệu ngày cũ không hợp lệ.
                }
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year);
            btnSelectDate.setText(selectedDate);
            loadAttendanceData();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        dialog.show();
    }

    private void loadAttendanceData() {
        attendanceList.clear();
        attendanceList.addAll(
                dbHelper.getAttendanceRecordsBySubjectAndDate(
                        currentSubjectId,
                        selectedDate
                )
        );

        applyFilters();
    }
    private void showStudentAttendanceDetails(StudentAttendanceStats stats) {
        if (stats == null) {
            return;
        }

        List<AttendanceRecord> details = dbHelper.getStudentAttendanceDetailsBySubject(
                currentSubjectId,
                stats.getStudentId(),
                stats.getStudentName()
        );

        if (details.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(stats.getStudentName())
                    .setMessage("Chưa có buổi học nào cho môn này")
                    .setPositiveButton("Đóng", null)
                    .show();
            return;
        }
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new StudentAttendanceDetailAdapter(this, details));
        recyclerView.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), 0);
        recyclerView.setClipToPadding(false);

        int estimatedItemHeight = dpToPx(92);
        int maxListHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
        int listHeight = Math.min(maxListHeight, estimatedItemHeight * details.size());
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(dpToPx(120), listHeight)
        ));

        new AlertDialog.Builder(this)
                .setTitle(stats.getStudentName())
                .setView(recyclerView)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void loadStudentAttendanceStats() {
        studentAttendanceStatsList.clear();
        studentAttendanceStatsList.addAll(
                dbHelper.getStudentAttendanceStatsBySubject(currentSubjectId)
        );
        studentAttendanceStatsAdapter.updateList(studentAttendanceStatsList);

        boolean noStudents = studentAttendanceStatsList.isEmpty();
        int totalSessions = noStudents
                ? 0
                : studentAttendanceStatsList.get(0).getTotalSessions();

        textTotalAttendanceSessions.setText(
                "Tổng số buổi học: " + totalSessions
        );

        boolean noRecordedSessions = !noStudents && totalSessions == 0;
        boolean showEmpty = noStudents || noRecordedSessions;

        recyclerStudentAttendanceStats.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
        textEmptyStudentAttendanceStats.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        textEmptyStudentAttendanceStats.setText(
                noStudents
                        ? "Môn học chưa có sinh viên"
                        : "Chưa có dữ liệu điểm danh cho môn học này"
        );
    }

    private void applyFilters() {
        filteredAttendanceList.clear();
        String searchText = editSearchStudent.getText().toString()
                .toLowerCase(Locale.getDefault())
                .trim();

        for (AttendanceRecord record : attendanceList) {
            // Search filter
            String studentId = record.getStudentId() == null ? "" : record.getStudentId();
            String fullName = record.getFullName() == null ? "" : record.getFullName();
            boolean matchesSearch = searchText.isEmpty() ||
                    studentId.toLowerCase(Locale.getDefault()).contains(searchText) ||
                    fullName.toLowerCase(Locale.getDefault()).contains(searchText);

            if (!matchesSearch) continue;

            // Status filter
            boolean matchesFilter = false;
            switch (currentFilter) {
                case FILTER_ALL:
                    matchesFilter = true;
                    break;
                case FILTER_PRESENT:
                    matchesFilter = record.isAttended();
                    break;
                case FILTER_ABSENT:
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
        updateFilterButtonStyle(btnFilterAll, currentFilter == FILTER_ALL);
        updateFilterButtonStyle(btnFilterAttended, currentFilter == FILTER_PRESENT);
        updateFilterButtonStyle(btnFilterNotAttended, currentFilter == FILTER_ABSENT);
    }

    private void updateFilterButtonStyle(Button button, boolean selected) {
        button.setAlpha(1f);
        button.setBackgroundResource(
                selected ? R.drawable.button_primary : R.drawable.button_secondary
        );
        button.setTextColor(ContextCompat.getColor(
                this,
                selected ? android.R.color.white : R.color.colorPrimaryDark
        ));
    }

    private void updateStats() {
        int total = dbHelper.getTotalStudentsBySubject(currentSubjectId);
        int attended = dbHelper.getPresentCountBySubjectAndDate(currentSubjectId, selectedDate);
        int absent = Math.max(0, total - attended);
        int percent = total > 0 ? Math.round(attended * 100f / total) : 0;

        textTotalStudents.setText(String.valueOf(total));
        textPresentStudents.setText(String.valueOf(attended));
        textAbsentStudents.setText(String.valueOf(absent));
        textViewStats.setText(total == 0
                ? "Chưa có sinh viên trong môn học"
                : String.format(Locale.getDefault(),
                "Tỷ lệ cả lớp: %d%% (%d có mặt, %d vắng)",
                percent, attended, absent));
        progressAttendanceRate.setProgress(percent, true);
        attendancePieChart.setData(attended, absent);
    }

    private void updateEmptyAttendanceState() {
        if (filteredAttendanceList.isEmpty()) {
            recyclerAttendanceList.setVisibility(View.GONE);
            textEmptyAttendanceList.setVisibility(View.VISIBLE);
            int totalStudents = dbHelper.getTotalStudentsBySubject(currentSubjectId);
            int presentStudents = dbHelper.getPresentCountBySubjectAndDate(currentSubjectId, selectedDate);

            String emptyMessage;
            if (totalStudents == 0) {
                emptyMessage = "Môn học chưa có sinh viên";
            } else if (presentStudents == 0 && currentFilter == FILTER_PRESENT) {
                emptyMessage = "Chưa có sinh viên nào điểm danh ngày này";
            } else if (filteredAttendanceList.isEmpty()) {
                emptyMessage = "Không có sinh viên phù hợp với bộ lọc";
            } else {
                emptyMessage = "Chưa có dữ liệu điểm danh";
            }

            textEmptyAttendanceList.setText(emptyMessage);
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
        int insertedCount = 0;
        int sessionOnlyCount = 0;
        int duplicateOrSkippedCount = 0;
        int invalidCount = 0;
        boolean foundHeader = false;
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

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> tokens = parseCsvLine(line);
                String normalizedLine = line.toLowerCase(Locale.getDefault());

                if (!foundHeader) {
                    if (normalizedLine.contains("mã sinh viên")
                            && (normalizedLine.contains("ngày điểm danh")
                            || normalizedLine.contains("ngày học"))) {
                        foundHeader = true;
                    }
                    continue;
                }

                if (tokens.size() < 4) {
                    invalidCount++;
                    continue;
                }

                String studentId = tokens.get(0).replace("\"", "").trim();
                String fullName = tokens.get(1).replace("\"", "").trim();
                String date = tokens.get(2).replace("\"", "").trim();
                String time = tokens.get(3).replace("\"", "").trim();
                String lateCutoffTime = tokens.size() >= 5
                        ? tokens.get(4).replace("\"", "").trim()
                        : "";
                String status = tokens.size() >= 6
                        ? tokens.get(5).replace("\"", "").trim()
                        : "";

                if (studentId.isEmpty() || date.isEmpty() || date.equals("--")) {
                    duplicateOrSkippedCount++;
                    continue;
                }

                if (!dbHelper.isStudentInSubject(studentId, currentSubjectId)) {
                    invalidCount++;
                    continue;
                }

                if (isAbsentStatus(status) || !isNonEmptyCsvValue(time)) {
                    boolean sessionSaved = isNonEmptyCsvValue(lateCutoffTime)
                            ? dbHelper.updateSessionLateCutoffTime(
                                    currentSubjectId,
                                    date,
                                    lateCutoffTime
                            )
                            : dbHelper.ensureClassSession(currentSubjectId, date);
                    if (sessionSaved) {
                        sessionOnlyCount++;
                    } else {
                        invalidCount++;
                    }
                    continue;
                }

                DatabaseHelper.AttendanceWriteResult writeResult =
                        dbHelper.recordDailyAttendance(
                                studentId,
                                currentSubjectId,
                                date,
                                time,
                                isNonEmptyCsvValue(lateCutoffTime)
                                        ? lateCutoffTime
                                        : null
                        );

                if (writeResult == DatabaseHelper.AttendanceWriteResult.SUCCESS) {
                    insertedCount++;
                } else if (writeResult == DatabaseHelper.AttendanceWriteResult.DUPLICATE) {
                    duplicateOrSkippedCount++;
                } else {
                    invalidCount++;
                }
            }
            reader.close();
            loadSubjectInfo();
            reloadCurrentStatistics();

            Toast.makeText(
                    this,
                    "Import xong: thêm " + insertedCount +
                            ", buổi/vắng " + sessionOnlyCount +
                            ", bỏ qua " + duplicateOrSkippedCount +
                            ", lỗi " + invalidCount,
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi import CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    // ==================== EXPORT CSV FUNCTIONS ====================

    private void exportAttendanceToCSV() {
        try {
            AttendanceExportData exportData =
                    attendanceExportService.loadExportData(currentSubjectId);
            String exportDate = buildExportDate();
            String fileName = attendanceExportService.buildCsvFileName(exportData);
            byte[] csvBytes = attendanceExportService.buildCsv(exportData, exportDate)
                    .getBytes(StandardCharsets.UTF_8);

            preparePendingExport(
                    ExportedReport.ReportType.CSV_DETAIL,
                    fileName,
                    buildReportCacheFileName(ExportedReport.ReportType.CSV_DETAIL),
                    csvBytes
            );

            openCsvCreateDocument(fileName);

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi xuất CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void exportAttendanceSummaryCsv() {
        try {
            AttendanceExportData exportData =
                    attendanceExportService.loadExportData(currentSubjectId);
            String fileName = attendanceExportService.buildSummaryCsvFileName(exportData);
            byte[] csvBytes = attendanceExportService.buildSummaryCsv(
                    exportData,
                    buildExportDate()
            ).getBytes(StandardCharsets.UTF_8);

            preparePendingExport(
                    ExportedReport.ReportType.CSV_SUMMARY,
                    fileName,
                    buildReportCacheFileName(ExportedReport.ReportType.CSV_SUMMARY),
                    csvBytes
            );

            openCsvCreateDocument(fileName);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Lỗi khi xuất CSV tổng hợp: " + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();
            e.printStackTrace();
        }
    }

    private void exportAttendanceMatrixCsv() {
        try {
            AttendanceExportData exportData =
                    attendanceExportService.loadExportData(currentSubjectId);
            String fileName = attendanceExportService.buildMatrixCsvFileName(exportData);
            byte[] csvBytes = attendanceExportService.buildMatrixCsv(
                    exportData,
                    buildExportDate()
            ).getBytes(StandardCharsets.UTF_8);

            preparePendingExport(
                    ExportedReport.ReportType.CSV_MATRIX,
                    fileName,
                    buildReportCacheFileName(ExportedReport.ReportType.CSV_MATRIX),
                    csvBytes
            );

            openCsvCreateDocument(fileName);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Lỗi khi xuất CSV chi tiết dạng bảng: " + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();
            e.printStackTrace();
        }
    }

    private void openCsvCreateDocument(String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_CODE_CREATE_CSV_DOCUMENT);
    }

    private void startExportExcel() {
        if (currentSubjectId == null || currentSubjectId.trim().isEmpty()) {
            Toast.makeText(this, "Không tìm thấy thông tin môn học", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = buildDefaultExcelFileName();
        preparePendingExport(
                ExportedReport.ReportType.EXCEL,
                fileName,
                buildReportCacheFileName(ExportedReport.ReportType.EXCEL),
                null
        );

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_CODE_CREATE_EXCEL_DOCUMENT);
    }

    private void handleExportExcelUri(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, "Không chọn được nơi lưu file Excel", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean userFileSaved = false;
        try {
            if (pendingExportReportType != ExportedReport.ReportType.EXCEL
                    || pendingExportDisplayName == null
                    || pendingExportCacheFileName == null
                    || !String.valueOf(currentSubjectId).equals(pendingExportSubjectId)) {
                preparePendingExport(
                        ExportedReport.ReportType.EXCEL,
                        buildDefaultExcelFileName(),
                        buildReportCacheFileName(ExportedReport.ReportType.EXCEL),
                        null
                );
            }

            AttendanceExportData exportData =
                    attendanceExportService.loadExportData(currentSubjectId);
            byte[] excelBytes;
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                new AttendanceExcelExporter().export(
                        outputStream,
                        exportData,
                        buildExportDate()
                );
                excelBytes = outputStream.toByteArray();
            }

            writeBytesToUri(uri, excelBytes);
            userFileSaved = true;

            File cacheFile = writeBytesToCache(excelBytes, pendingExportCacheFileName);
            registerExportedReport(
                    ExportedReport.ReportType.EXCEL,
                    pendingExportDisplayName,
                    cacheFile
            );

            Toast.makeText(this, "Đã xuất file Excel thành công!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String message = userFileSaved
                    ? "File Excel đã được lưu, nhưng không tạo được cache để gửi email: "
                    : "Lỗi khi xuất Excel: ";
            Toast.makeText(this, message + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } finally {
            clearPendingExport();
        }
    }

    private String buildDefaultExcelFileName() {
        String subjectPart = currentSubjectName;

        if (subjectPart == null || subjectPart.trim().isEmpty()) {
            subjectPart = currentSubjectId;
        }

        if (subjectPart == null || subjectPart.trim().isEmpty()) {
            subjectPart = "MonHoc";
        }

        String safeSubject = subjectPart.replaceAll("[^a-zA-Z0-9_-]", "_");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        return "DiemDanh_" + safeSubject + "_" + timestamp + ".xlsx";
    }

    private String buildExportDate() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
    }

    private void preparePendingExport(
            ExportedReport.ReportType reportType,
            String displayName,
            String cacheFileName,
            byte[] data
    ) {
        pendingExportReportType = reportType;
        pendingExportDisplayName = displayName;
        pendingExportCacheFileName = cacheFileName;
        pendingExportSubjectId = currentSubjectId == null ? null : String.valueOf(currentSubjectId);
        pendingExportData = data;
    }

    private void clearPendingExport() {
        pendingExportData = null;
        pendingExportReportType = null;
        pendingExportDisplayName = null;
        pendingExportCacheFileName = null;
        pendingExportSubjectId = null;
    }

    private File writeBytesToCache(byte[] data, String fileName) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("Dữ liệu báo cáo rỗng");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IOException("Tên file cache không hợp lệ");
        }

        File cacheFile = new File(getCacheDir(), fileName);
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            fos.write(data);
            fos.flush();
        } catch (IOException e) {
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            throw e;
        }

        if (!cacheFile.exists() || !cacheFile.isFile() || cacheFile.length() <= 0) {
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            throw new IOException("File cache báo cáo rỗng");
        }

        return cacheFile;
    }

    private void writeBytesToUri(Uri uri, byte[] data) throws IOException {
        if (uri == null) {
            throw new IOException("Không chọn được nơi lưu file");
        }
        if (data == null || data.length == 0) {
            throw new IOException("Dữ liệu báo cáo rỗng");
        }

        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                throw new IOException("Không mở được file để ghi");
            }
            os.write(data);
            os.flush();
        }
    }

    private String buildReportCacheFileName(ExportedReport.ReportType reportType) {
        String subjectPart = currentSubjectId;
        if (subjectPart == null || subjectPart.trim().isEmpty()) {
            subjectPart = "unknown";
        }
        String safeSubjectId = subjectPart.replaceAll("[^a-zA-Z0-9_-]", "_");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new Date());

        switch (reportType) {
            case CSV_DETAIL:
                return "attendance_detail_" + safeSubjectId + "_" + timestamp + ".csv";
            case CSV_SUMMARY:
                return "attendance_summary_" + safeSubjectId + "_" + timestamp + ".csv";
            case CSV_MATRIX:
                return "attendance_matrix_" + safeSubjectId + "_" + timestamp + ".csv";
            case EXCEL:
                return "attendance_" + safeSubjectId + "_" + timestamp + ".xlsx";
            default:
                return "attendance_" + safeSubjectId + "_" + timestamp;
        }
    }

    private void registerExportedReport(
            ExportedReport.ReportType reportType,
            String displayName,
            File cacheFile
    ) {
        if (reportType == null
                || displayName == null
                || displayName.trim().isEmpty()
                || currentSubjectId == null
                || currentSubjectId.trim().isEmpty()
                || cacheFile == null
                || !cacheFile.exists()
                || !cacheFile.isFile()
                || cacheFile.length() <= 0) {
            return;
        }

        String subjectId = String.valueOf(currentSubjectId);
        List<File> oldFiles = new ArrayList<>();
        for (int i = exportedReports.size() - 1; i >= 0; i--) {
            ExportedReport report = exportedReports.get(i);
            if (report != null
                    && report.getReportType() == reportType
                    && subjectId.equals(report.getSubjectId())) {
                File oldFile = report.getCacheFile();
                exportedReports.remove(i);
                if (oldFile != null && !isSameFile(oldFile, cacheFile)) {
                    oldFiles.add(oldFile);
                }
            }
        }

        ExportedReport report = new ExportedReport(
                reportType,
                subjectId,
                displayName.trim(),
                cacheFile,
                cacheFile.length(),
                System.currentTimeMillis()
        );
        exportedReports.add(report);
        selectedEmailReport = report;

        for (File oldFile : oldFiles) {
            deleteCacheFileIfUnused(oldFile);
        }

        updateSelectedReportUi();
    }

    private List<ExportedReport> getReportsForCurrentSubject() {
        List<ExportedReport> reportsForSubject = new ArrayList<>();

        for (int i = exportedReports.size() - 1; i >= 0; i--) {
            ExportedReport report = exportedReports.get(i);
            boolean sameSubject = report != null
                    && String.valueOf(currentSubjectId).equals(report.getSubjectId());
            if (!sameSubject) {
                continue;
            }

            if (isValidReportFile(report)) {
                reportsForSubject.add(report);
            } else {
                exportedReports.remove(i);
                if (report == selectedEmailReport) {
                    selectedEmailReport = null;
                }
            }
        }

        reportsForSubject.sort((left, right) ->
                Long.compare(right.getExportedAt(), left.getExportedAt()));
        return reportsForSubject;
    }

    private boolean isValidReportFile(ExportedReport report) {
        if (report == null
                || report.getReportType() == null
                || currentSubjectId == null
                || !String.valueOf(currentSubjectId).equals(report.getSubjectId())
                || report.getCacheFile() == null
                || !report.getCacheFile().exists()
                || !report.getCacheFile().isFile()
                || report.getCacheFile().length() <= 0) {
            return false;
        }

        String fileName = report.getCacheFile().getName().toLowerCase(Locale.US);
        switch (report.getReportType()) {
            case CSV_DETAIL:
            case CSV_SUMMARY:
            case CSV_MATRIX:
                return fileName.endsWith(".csv");
            case EXCEL:
                return fileName.endsWith(".xlsx");
            default:
                return false;
        }
    }

    private File getSelectedReportFile() {
        if (isValidReportFile(selectedEmailReport)) {
            return selectedEmailReport.getCacheFile();
        }

        List<ExportedReport> reportsForSubject = getReportsForCurrentSubject();
        if (!reportsForSubject.isEmpty()) {
            selectedEmailReport = reportsForSubject.get(0);
            return selectedEmailReport.getCacheFile();
        }

        selectedEmailReport = null;
        return null;
    }

    private void updateSelectedReportUi() {
        if (textSelectedReportType == null
                || textSelectedReportFileName == null
                || textSelectedReportMeta == null
                || btnChooseReport == null) {
            return;
        }

        if (!isValidReportFile(selectedEmailReport)) {
            selectedEmailReport = null;
        }

        if (selectedEmailReport == null) {
            textSelectedReportType.setText("Chưa chọn báo cáo");
            textSelectedReportFileName.setText("Bạn chưa xuất báo cáo nào cho môn học này");
            textSelectedReportMeta.setText("");
            btnChooseReport.setText("Chọn báo cáo");
            return;
        }

        File cacheFile = selectedEmailReport.getCacheFile();
        textSelectedReportType.setText(selectedEmailReport.getReportTypeLabel());
        textSelectedReportFileName.setText(selectedEmailReport.getDisplayName());
        textSelectedReportMeta.setText(formatFileSize(cacheFile.length()) +
                " · " + formatExportedAt(selectedEmailReport.getExportedAt()));
        btnChooseReport.setText("Thay đổi");
    }

    private void showReportSelectionDialog() {
        List<ExportedReport> reportsForSubject = getReportsForCurrentSubject();
        if (reportsForSubject.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Chưa có báo cáo")
                    .setMessage("Bạn chưa xuất báo cáo nào cho môn học này.\nVui lòng xuất ít nhất một báo cáo trước khi gửi email.")
                    .setPositiveButton("Đến phần xuất", (dialog, which) -> showTab(1))
                    .setNegativeButton("Đóng", null)
                    .show();
            updateSelectedReportUi();
            return;
        }

        String[] items = new String[reportsForSubject.size()];
        int selectedIndex = -1;
        for (int i = 0; i < reportsForSubject.size(); i++) {
            ExportedReport report = reportsForSubject.get(i);
            File cacheFile = report.getCacheFile();
            items[i] = report.getReportTypeLabel() + " — " +
                    report.getDisplayName() + "\n" +
                    formatFileSize(cacheFile.length()) + " · " +
                    formatExportedAt(report.getExportedAt());

            if (selectedEmailReport != null
                    && report.getReportType() == selectedEmailReport.getReportType()
                    && report.getSubjectId().equals(selectedEmailReport.getSubjectId())
                    && isSameFile(report.getCacheFile(), selectedEmailReport.getCacheFile())) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Chọn báo cáo")
                .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                    selectedEmailReport = reportsForSubject.get(which);
                    updateSelectedReportUi();
                    dialog.dismiss();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double kiloBytes = bytes / 1024d;
        if (kiloBytes < 1024) {
            return String.format(Locale.US, "%.1f KB", kiloBytes);
        }

        double megaBytes = kiloBytes / 1024d;
        return String.format(Locale.US, "%.1f MB", megaBytes);
    }

    private String formatExportedAt(long timestamp) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private void deleteCacheFileIfUnused(File file) {
        if (file == null || isCacheFileUsed(file)) {
            return;
        }

        try {
            File cacheDir = getCacheDir().getCanonicalFile();
            File targetFile = file.getCanonicalFile();
            if (targetFile.getParentFile() != null
                    && cacheDir.equals(targetFile.getParentFile())
                    && targetFile.exists()
                    && targetFile.isFile()) {
                targetFile.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isCacheFileUsed(File file) {
        for (ExportedReport report : exportedReports) {
            if (report != null && isSameFile(report.getCacheFile(), file)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameFile(File left, File right) {
        if (left == null || right == null) {
            return false;
        }

        try {
            return left.getCanonicalFile().equals(right.getCanonicalFile());
        } catch (IOException e) {
            return left.getAbsolutePath().equals(right.getAbsolutePath());
        }
    }

    private boolean isNonEmptyCsvValue(String value) {
        return value != null
                && !value.trim().isEmpty()
                && !"--".equals(value.trim());
    }

    private boolean isAbsentStatus(String status) {
        if (status == null) {
            return false;
        }

        String normalized = status.trim().toLowerCase(Locale.getDefault());
        return normalized.equals("vắng")
                || normalized.equals("vang")
                || normalized.equals("absent");
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString().trim());
        return values;
    }

    private void writeCsvToUri(Uri uri) {
        if (pendingExportData == null
                || pendingExportReportType == null
                || pendingExportDisplayName == null
                || pendingExportCacheFileName == null
                || !String.valueOf(currentSubjectId).equals(pendingExportSubjectId)) {
            Toast.makeText(this, "Không có dữ liệu CSV để lưu", Toast.LENGTH_SHORT).show();
            clearPendingExport();
            return;
        }

        boolean userFileSaved = false;
        try {
            writeBytesToUri(uri, pendingExportData);
            userFileSaved = true;

            File cacheFile = writeBytesToCache(pendingExportData, pendingExportCacheFileName);
            registerExportedReport(
                    pendingExportReportType,
                    pendingExportDisplayName,
                    cacheFile
            );

            Toast.makeText(this, "Đã xuất file CSV thành công!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String message = userFileSaved
                    ? "File CSV đã được lưu, nhưng không tạo được cache để gửi email: "
                    : "Lỗi khi lưu file CSV: ";
            Toast.makeText(this, message + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } finally {
            clearPendingExport();
        }
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

        File attachmentFile = getSelectedReportFile();
        if (attachmentFile == null) {
            new AlertDialog.Builder(this)
                .setTitle("Chưa có báo cáo")
                .setMessage("Bạn chưa xuất báo cáo nào. Vui lòng xuất ít nhất một báo cáo trước khi gửi email.")
                .setPositiveButton("Xuất CSV", (dialog, which) -> exportAttendanceToCSV())
                .setNegativeButton("Hủy", null)
                .show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Gửi Email")
            .setMessage("Gửi báo cáo điểm danh đến " + selectedEmails.length + " email?\n\n" +
                       "File đính kèm: " + attachmentFile.getName())
            .setPositiveButton("Gửi", (dialog, which) -> performSendEmail(selectedEmails))
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void performSendEmail(String[] recipients) {
        File attachmentFile = getSelectedReportFile();
        if (attachmentFile == null) {
            Toast.makeText(this,
                    "File báo cáo không còn tồn tại. Vui lòng xuất lại báo cáo.",
                    Toast.LENGTH_LONG).show();
            return;
        }

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

        EmailService.sendAttendanceReportEmail(recipients, subject, htmlBody, attachmentFile,
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
            } else if (requestCode == REQUEST_CODE_CREATE_CSV_DOCUMENT) {
                if (uri != null) {
                    writeCsvToUri(uri);
                } else {
                    clearPendingExport();
                }
            } else if (requestCode == REQUEST_CODE_CREATE_EXCEL_DOCUMENT) {
                if (uri != null) {
                    handleExportExcelUri(uri);
                } else {
                    clearPendingExport();
                }
            }
        } else if (requestCode == REQUEST_CODE_CREATE_CSV_DOCUMENT
                || requestCode == REQUEST_CODE_CREATE_EXCEL_DOCUMENT) {
            clearPendingExport();
        }
    }

}
