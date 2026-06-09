package com.iritech.irissample;

import android.app.Activity;
import android.content.Intent;
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

public class ImportCsvActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_CSV = 1;
    private String subjectId;
    private DatabaseHelper dbHelper;
    private Button btnChooseCsv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_csv);

        btnChooseCsv = findViewById(R.id.btnChooseCsv);
        dbHelper = new DatabaseHelper(this);
        subjectId = getIntent().getStringExtra("subject_id");

        btnChooseCsv.setOnClickListener(v -> openFilePicker());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn file CSV"), REQUEST_CODE_PICK_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_CSV && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            importCsv(uri);
        }
    }

    private void importCsv(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            
            // Check for BOM and skip if present
            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }
            
            String line;
            int count = 0;
            int lineNumber = 0;
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            // Skip the header line
            String header = reader.readLine();
            lineNumber++;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }
                
                // Split by comma, but keep empty tokens
                String[] tokens = line.split(",", -1);

                // Cần ít nhất 4 cột: student_id, full_name, phone, email
                // photo_path có thể không có
                if (tokens.length >= 4) {
                    String studentId = tokens[0].trim();
                    String fullName = tokens[1].trim();
                    String phone = tokens.length > 2 ? tokens[2].trim() : "";
                    String email = tokens.length > 3 ? tokens[3].trim() : "";
                    String password = tokens.length > 4 ? tokens[4].trim() : "";
                    String photoPath = tokens.length > 5 ? tokens[5].trim() : "";

                    // Validate required fields
                    if (studentId.isEmpty() || fullName.isEmpty()) {
                        continue;
                    }

                    // Insert student (if not exists) into TABLE_STUDENTS
                    db.execSQL("INSERT OR IGNORE INTO " + DatabaseHelper.TABLE_STUDENTS + " (" +
                                    DatabaseHelper.COL_STUDENT_ID + ", " +
                                    DatabaseHelper.COL_FULL_NAME + ", " +
                                    DatabaseHelper.COL_PHONE + ", " +
                                    DatabaseHelper.COL_EMAIL + ", " +
                                    DatabaseHelper.COL_PASSWORD + ", " +
                                    DatabaseHelper.COL_PHOTO_PATH + ") VALUES (?, ?, ?, ?, ?, ?)",
                            new Object[]{studentId, fullName, phone, email, password, photoPath.isEmpty() || photoPath.equals("NULL") ? null : photoPath});

                    // Insert into TABLE_ENROLLMENTS with default values (pre-attendance state)
                    db.execSQL("INSERT OR REPLACE INTO " + DatabaseHelper.TABLE_ENROLLMENTS + " (" +
                                    DatabaseHelper.COL_STUDENT_ID + ", " +
                                    DatabaseHelper.COL_SUBJECT_ID + ", " +
                                    DatabaseHelper.COL_ENROLLMENT_STATUS + ") VALUES (?, ?, ?)",
                            new Object[]{studentId, subjectId, "Not Enrolled"});
                    count++;
                }
            }

            reader.close();
            
            if (count > 0) {
                Toast.makeText(this, "Đã thêm " + count + " sinh viên vào danh sách.", Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "Không có sinh viên nào được thêm. Kiểm tra lại file CSV.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi import CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}