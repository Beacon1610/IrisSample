package com.iritech.irissample;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImportCsvActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_CSV = 1;
    private static final int MAX_IMPORT_ROWS = 10_000;

    private String subjectId;
    private DatabaseHelper dbHelper;
    private Button btnChooseCsv;
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_csv);

        btnChooseCsv = findViewById(R.id.btnChooseCsv);
        dbHelper = new DatabaseHelper(this);
        subjectId = getIntent().getStringExtra("subject_id");

        if (subjectId == null || subjectId.trim().isEmpty()) {
            Toast.makeText(this, "Không xác định được môn học", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnChooseCsv.setOnClickListener(v -> openFilePicker());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(
                Intent.createChooser(intent, "Chọn file CSV"),
                REQUEST_CODE_PICK_CSV
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_CSV
                && resultCode == Activity.RESULT_OK
                && data != null
                && data.getData() != null) {
            importCsv(data.getData());
        }
    }

    private void importCsv(Uri uri) {
        btnChooseCsv.setEnabled(false);
        btnChooseCsv.setText("Đang nhập dữ liệu...");

        importExecutor.execute(() -> {
            ImportResult result = performImport(uri);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showImportResult(result);
                }
            });
        });
    }

    private ImportResult performImport(Uri uri) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int imported = 0;
        int skipped = 0;
        int rowNumber = 1;

        db.beginTransaction();
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                return ImportResult.failure("Không thể mở file CSV");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            )) {
                String header = reader.readLine();
                if (header == null) {
                    return ImportResult.failure("File CSV rỗng");
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    rowNumber++;
                    if (rowNumber > MAX_IMPORT_ROWS + 1) {
                        return ImportResult.failure(
                                "File vượt quá giới hạn " + MAX_IMPORT_ROWS + " sinh viên"
                        );
                    }

                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    List<String> columns = parseCsvLine(line);
                    if (columns.size() < 4) {
                        skipped++;
                        continue;
                    }

                    String studentId = clean(columns.get(0));
                    String fullName = clean(columns.get(1));
                    String phone = getColumn(columns, 2);
                    String email = getColumn(columns, 3);
                    String password = getColumn(columns, 4);
                    String photoPath = getColumn(columns, 5);

                    if (studentId.isEmpty() || fullName.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    boolean studentExists = studentExists(db, studentId);
                    if (!studentExists && password.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    if (!studentExists) {
                        ContentValues studentValues = new ContentValues();
                        studentValues.put(DatabaseHelper.COL_STUDENT_ID, studentId);
                        studentValues.put(DatabaseHelper.COL_FULL_NAME, fullName);
                        studentValues.put(DatabaseHelper.COL_PHONE, phone);
                        studentValues.put(DatabaseHelper.COL_EMAIL, email);
                        studentValues.put(
                                DatabaseHelper.COL_PASSWORD,
                                dbHelper.hashStudentPasswordForStorage(password)
                        );
                        if (!photoPath.isEmpty() && !"NULL".equalsIgnoreCase(photoPath)) {
                            studentValues.put(DatabaseHelper.COL_PHOTO_PATH, photoPath);
                        }

                        long studentRow = db.insert(
                                DatabaseHelper.TABLE_STUDENTS,
                                null,
                                studentValues
                        );
                        if (studentRow == -1) {
                            throw new IllegalStateException(
                                    "Không thể thêm sinh viên ở dòng " + rowNumber
                            );
                        }
                    }

                    ContentValues enrollmentValues = new ContentValues();
                    enrollmentValues.put(DatabaseHelper.COL_STUDENT_ID, studentId);
                    enrollmentValues.put(DatabaseHelper.COL_SUBJECT_ID, subjectId);
                    enrollmentValues.put(
                            DatabaseHelper.COL_ENROLLMENT_STATUS,
                            DatabaseHelper.ENROLLMENT_STATUS_ACTIVE
                    );

                    if (isActiveEnrollment(db, studentId, subjectId)) {
                        skipped++;
                        continue;
                    }

                    int reactivatedRows = db.update(
                            DatabaseHelper.TABLE_ENROLLMENTS,
                            enrollmentValues,
                            DatabaseHelper.COL_STUDENT_ID + " = ? AND " +
                                    DatabaseHelper.COL_SUBJECT_ID + " = ?",
                            new String[]{studentId, subjectId}
                    );
                    if (reactivatedRows == 0) {
                        long enrollmentRow = db.insert(
                                DatabaseHelper.TABLE_ENROLLMENTS,
                                null,
                                enrollmentValues
                        );
                        if (enrollmentRow == -1) {
                            throw new IllegalStateException(
                                    "Không thể thêm enrollment ở dòng " + rowNumber
                            );
                        }
                    }
                    imported++;
                }
            }

            db.setTransactionSuccessful();
            return ImportResult.success(imported, skipped);
        } catch (Exception exception) {
            return ImportResult.failure(
                    "Lỗi tại dòng " + rowNumber + ": " + safeMessage(exception)
            );
        } finally {
            db.endTransaction();
        }
    }

    private static boolean isActiveEnrollment(
            SQLiteDatabase db,
            String studentId,
            String subjectId
    ) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM " + DatabaseHelper.TABLE_ENROLLMENTS +
                        " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ? AND " +
                        DatabaseHelper.COL_SUBJECT_ID + " = ? AND " +
                        DatabaseHelper.COL_ENROLLMENT_STATUS + " = ? LIMIT 1",
                new String[]{
                        studentId,
                        subjectId,
                        DatabaseHelper.ENROLLMENT_STATUS_ACTIVE
                }
        )) {
            return cursor.moveToFirst();
        }
    }

    private static boolean studentExists(SQLiteDatabase db, String studentId) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM " + DatabaseHelper.TABLE_STUDENTS +
                        " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ? LIMIT 1",
                new String[]{studentId}
        )) {
            return cursor.moveToFirst();
        }
    }

    /**
     * Parser một dòng CSV, hỗ trợ dấu phẩy trong chuỗi được đặt trong dấu
     * ngoặc kép và escape dấu ngoặc kép bằng hai ký tự "".
     */
    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Dấu ngoặc kép CSV không đóng");
        }
        values.add(current.toString());
        return values;
    }

    private static String getColumn(List<String> columns, int index) {
        return index < columns.size() ? clean(columns.get(index)) : "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (!cleaned.isEmpty() && cleaned.charAt(0) == '\uFEFF') {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private void showImportResult(ImportResult result) {
        btnChooseCsv.setEnabled(true);
        btnChooseCsv.setText("Chọn tệp CSV");

        if (!result.success) {
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
            return;
        }

        String message = "Đã thêm " + result.imported + " sinh viên";
        if (result.skipped > 0) {
            message += "; bỏ qua " + result.skipped + " dòng trùng hoặc không hợp lệ";
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        if (result.imported > 0) {
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        importExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class ImportResult {
        final boolean success;
        final int imported;
        final int skipped;
        final String message;

        private ImportResult(
                boolean success,
                int imported,
                int skipped,
                String message
        ) {
            this.success = success;
            this.imported = imported;
            this.skipped = skipped;
            this.message = message;
        }

        static ImportResult success(int imported, int skipped) {
            return new ImportResult(true, imported, skipped, "");
        }

        static ImportResult failure(String message) {
            return new ImportResult(false, 0, 0, message);
        }
    }
}
