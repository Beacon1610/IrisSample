package com.iritech.irissample;

import android.app.ProgressDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Activity quản lý Backup và Restore Database
 * CHỈ Super Admin mới có quyền truy cập activity này
 * 
 * Chức năng:
 * - Backup database ra file mã hóa
 * - Restore database từ file backup
 * - Xem thông tin backup
 * - Xóa backup cũ
 */
public class BackupRestoreActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> createBackupLauncher;
    private ActivityResultLauncher<String[]> openBackupLauncher;
    
    private Button btnBackup;
    private Button btnRestore;
    private Button btnViewBackups;
    private Button btnDeleteBackups;
    private TextView textViewBackupInfo;
    private TextView textViewStatus;
    
    private BackupRestoreHelper backupHelper;
    private BackupImportManager backupImportManager;
    private DatabaseHelper dbHelper;
    private String currentUserEmail;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_restore);
        registerActivityResultLaunchers();
        
        // Khởi tạo helpers
        backupHelper = new BackupRestoreHelper(this);
        backupImportManager = new BackupImportManager(this);
        dbHelper = new DatabaseHelper(this);
        
        // Lấy email user hiện tại
        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        
        // Kiểm tra quyền Super Admin
        if (!isSuperAdmin()) {
            Toast.makeText(this, "Chỉ Super Admin mới có quyền truy cập!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        initializeViews();
        setupListeners();
        updateBackupInfo();
    }
    
    private void initializeViews() {
        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
        btnViewBackups = findViewById(R.id.btnViewBackups);
        btnDeleteBackups = findViewById(R.id.btnDeleteBackups);
        textViewBackupInfo = findViewById(R.id.textViewBackupInfo);
        textViewStatus = findViewById(R.id.textViewStatus);
    }
    
    private void setupListeners() {
        btnBackup.setOnClickListener(v -> showExportAuthorizationDialog());
        btnRestore.setOnClickListener(v -> launchOpenBackupDocument());
        btnViewBackups.setOnClickListener(v -> showBackupsList());
        btnDeleteBackups.setOnClickListener(v -> confirmDeleteAllBackups());
    }

    
    /**
     * Kiểm tra user có phải Super Admin không
     */
    private void showExportAuthorizationDialog() {
        EditText passwordInput = new EditText(this);

        passwordInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        passwordInput.setHint(
                "Mật khẩu tài khoản Super Admin"
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Xác thực Super Admin")
                .setMessage(
                        "Xác thực tài khoản trước khi xuất dữ liệu."
                )
                .setView(passwordInput)
                .setPositiveButton("Tiếp tục", null)
                .setNegativeButton("Hủy", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(view -> {
                    String accountPassword =
                            passwordInput.getText().toString();

                    if (accountPassword.isEmpty()) {
                        passwordInput.setError(
                                "Vui lòng nhập mật khẩu"
                        );

                        return;
                    }

                    if (!dbHelper.verifyAdminPassword(
                            currentUserEmail,
                            accountPassword
                    )) {
                        passwordInput.setError(
                                "Mật khẩu tài khoản không đúng"
                        );

                        return;
                    }

                    passwordInput.setText("");
                    dialog.dismiss();

                    launchCreateBackupDocument();
                })
        );

        dialog.show();
    }
    private void launchCreateBackupDocument() {
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
        ).format(new Date());

        String fileName =
                "IrisSample_" +
                        timestamp +
                        ".iribackup";

        createBackupLauncher.launch(fileName);
    }
    private void showBackupPassphraseDialog(Uri destinationUri) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        int padding = (int) (
                24 * getResources()
                        .getDisplayMetrics()
                        .density
        );

        container.setPadding(
                padding,
                0,
                padding,
                0
        );

        EditText passphraseInput = new EditText(this);
        passphraseInput.setHint(
                "Mật khẩu backup (tối thiểu 8 ký tự)"
        );
        passphraseInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        EditText confirmInput = new EditText(this);
        confirmInput.setHint("Nhập lại mật khẩu backup");
        confirmInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        container.addView(passphraseInput);
        container.addView(confirmInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Đặt mật khẩu backup")
                .setMessage(
                        "Đây là mật khẩu riêng của file backup, " +
                                "không phải mật khẩu đăng nhập.\n\n" +
                                "Nếu quên mật khẩu này, file không thể phục hồi."
                )
                .setView(container)
                .setPositiveButton("Export", null)
                .setNegativeButton(
                        "Hủy",
                        (ignored, which) ->
                                deleteUnusedDocument(destinationUri)
                )
                .create();

        dialog.setOnCancelListener(
                ignored -> deleteUnusedDocument(destinationUri)
        );

        dialog.setOnShowListener(ignored ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(view -> {
                    char[] passphrase = passphraseInput
                            .getText()
                            .toString()
                            .toCharArray();

                    char[] confirmation = confirmInput
                            .getText()
                            .toString()
                            .toCharArray();

                    if (passphrase.length < 8) {
                        Arrays.fill(passphrase, '\0');
                        Arrays.fill(confirmation, '\0');

                        passphraseInput.setError(
                                "Cần ít nhất 8 ký tự"
                        );

                        return;
                    }

                    if (!Arrays.equals(
                            passphrase,
                            confirmation
                    )) {
                        Arrays.fill(passphrase, '\0');
                        Arrays.fill(confirmation, '\0');

                        confirmInput.setError(
                                "Hai mật khẩu không giống nhau"
                        );

                        return;
                    }

                    Arrays.fill(confirmation, '\0');

                    passphraseInput.setText("");
                    confirmInput.setText("");

                    dialog.dismiss();

                    performSafExport(
                            destinationUri,
                            passphrase
                    );
                })
        );

        dialog.show();
    }
    private void performSafExport(
            Uri destinationUri,
            char[] passphrase
    ) {
        ProgressDialog progressDialog =
                new ProgressDialog(this);

        progressDialog.setMessage(
                "Đang tạo file backup..."
        );

        progressDialog.setCancelable(false);
        progressDialog.show();

        btnBackup.setEnabled(false);
        textViewStatus.setText("Đang export dữ liệu...");

        new Thread(() -> {
            BackupRestoreHelper.ExportResult result;

            try {
                result = backupHelper.exportBackup(
                        destinationUri,
                        passphrase
                );
            } finally {
                Arrays.fill(passphrase, '\0');
            }

            runOnUiThread(() -> {
                progressDialog.dismiss();
                btnBackup.setEnabled(true);

                if (result.isSuccess()) {
                    textViewStatus.setText(
                            "Export backup thành công"
                    );

                    StringBuilder message = new StringBuilder();

                    message.append("File backup đã được lưu.\n\n");
                    message.append("Kích thước: ")
                            .append(formatFileSize(
                                    result.getFileSize()
                            ))
                            .append("\n");

                    message.append("Avatar Admin: ")
                            .append(result.getAdminAvatarCount())
                            .append("\n");

                    message.append("Avatar sinh viên: ")
                            .append(result.getStudentAvatarCount())
                            .append("\n");

                    message.append("Iris repository: ")
                            .append(
                                    result.isIrisIncluded()
                                            ? "Có"
                                            : "Không"
                            );

                    if (!result.getWarnings().isEmpty()) {
                        message.append("\n\nCảnh báo:");

                        for (String warning :
                                result.getWarnings()) {
                            message.append("\n- ")
                                    .append(warning);
                        }
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Export thành công")
                            .setMessage(message.toString())
                            .setPositiveButton("OK", null)
                            .show();

                } else {
                    textViewStatus.setText(
                            "Export backup thất bại"
                    );

                    new AlertDialog.Builder(this)
                            .setTitle("Export thất bại")
                            .setMessage(result.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        if (bytes < 1024L * 1024L) {
            return String.format(
                    Locale.US,
                    "%.2f KB",
                    bytes / 1024.0
            );
        }

        return String.format(
                Locale.US,
                "%.2f MB",
                bytes / (1024.0 * 1024.0)
        );
    }

    private void deleteUnusedDocument(Uri uri) {
        try {
            DocumentsContract.deleteDocument(
                    getContentResolver(),
                    uri
            );
        } catch (Exception exception) {
            android.util.Log.w(
                    "BackupRestoreActivity",
                    "Could not delete unused document",
                    exception
            );
        }
    }
    private boolean isSuperAdmin() {
        if (currentUserEmail == null) {
            return false;
        }
        Cursor cursor = dbHelper.getAdminByEmail(currentUserEmail);
        if (cursor != null && cursor.moveToFirst()) {
            String role = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_ROLE));
            cursor.close();
            return "SUPER_ADMIN".equals(role);
        }
        return false;
    }
    private void registerActivityResultLaunchers() {
        createBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(
                        "application/vnd.iritech.iribackup"
                ),
                uri -> {
                    if (uri == null) {
                        textViewStatus.setText(
                                "Đã hủy chọn nơi lưu backup"
                        );

                        return;
                    }

                    showBackupPassphraseDialog(uri);
                }
        );

        openBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        textViewStatus.setText("Đã hủy chọn file backup");
                        return;
                    }
                    showImportPassphraseDialog(uri);
                }
        );
    }

    private void launchOpenBackupDocument() {
        openBackupLauncher.launch(new String[]{
                "application/vnd.iritech.iribackup",
                "application/octet-stream",
                "*/*"
        });
    }

    private void showImportPassphraseDialog(Uri sourceUri) {
        EditText input = new EditText(this);
        input.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        input.setHint("Mật khẩu file backup");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Phục hồi dữ liệu")
                .setMessage(
                        "Thao tác này sẽ thay thế toàn bộ dữ liệu hiện tại. " +
                                "Database hiện tại sẽ được tạo safety backup trước khi thay thế.\n\n" +
                                "Nhập mật khẩu đã dùng khi tạo file backup:"
                )
                .setView(input)
                .setPositiveButton("Phục hồi", null)
                .setNegativeButton("Hủy", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            char[] passphrase = input.getText()
                                    .toString()
                                    .toCharArray();
                            if (passphrase.length == 0) {
                                input.setError("Vui lòng nhập mật khẩu backup");
                                return;
                            }

                            input.setText("");
                            dialog.dismiss();
                            performSafImport(sourceUri, passphrase);
                        })
        );
        dialog.show();
    }

    private void performSafImport(Uri sourceUri, char[] passphrase) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Đang kiểm tra và phục hồi dữ liệu...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        btnBackup.setEnabled(false);
        btnRestore.setEnabled(false);
        textViewStatus.setText("Đang kiểm tra file backup...");

        new Thread(() -> {
            BackupImportManager.ImportResult result;
            try {
                result = backupImportManager.importBackup(sourceUri, passphrase);
            } finally {
                Arrays.fill(passphrase, '\0');
            }

            runOnUiThread(() -> {
                progressDialog.dismiss();
                btnBackup.setEnabled(true);
                btnRestore.setEnabled(true);

                if (!result.isSuccess()) {
                    textViewStatus.setText("Phục hồi dữ liệu thất bại");
                    new AlertDialog.Builder(this)
                            .setTitle("Phục hồi thất bại")
                            .setMessage(result.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }

                textViewStatus.setText("Phục hồi dữ liệu thành công");
                StringBuilder message = new StringBuilder();
                message.append("Dữ liệu đã được phục hồi an toàn.\n\n")
                        .append("Format: ")
                        .append(result.isVersionedFormat() ? ".iribackup" : ".enc legacy")
                        .append("\nAvatar Admin: ")
                        .append(result.getAdminAvatarCount())
                        .append("\nAvatar sinh viên: ")
                        .append(result.getStudentAvatarCount())
                        .append("\nIris repository: ")
                        .append(result.isIrisRestored() ? "Đã lưu" : "Không có");

                if (!result.getWarnings().isEmpty()) {
                    message.append("\n\nCảnh báo:");
                    for (String warning : result.getWarnings()) {
                        message.append("\n- ").append(warning);
                    }
                }
                message.append("\n\nỨng dụng sẽ khởi động lại.");

                new AlertDialog.Builder(this)
                        .setTitle("Phục hồi thành công")
                        .setMessage(message.toString())
                        .setCancelable(false)
                        .setPositiveButton("Khởi động lại", (ignored, which) -> restartApp())
                        .show();
            });
        }).start();
    }

    
    /**
     * Hiển thị dialog để restore database
     */
    private void showRestoreDialog() {
        // Tìm file backup
        File latestBackup = backupHelper.findLatestBackup();
        
        if (latestBackup == null) {
            new AlertDialog.Builder(this)
                .setTitle("Không tìm thấy backup")
                .setMessage("Không có file backup nào trong hệ thống.\n\n" +
                           "Vui lòng tạo backup trước hoặc đảm bảo file backup có trong:\n" +
                           "Documents/IrisSample/")
                .setPositiveButton("OK", null)
                .show();
            return;
        }
        
        BackupRestoreHelper.BackupInfo info = backupHelper.getBackupInfo(latestBackup);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Restore Database");
        builder.setMessage("Tìm thấy file backup:\n\n" +
                          "Tên: " + info.fileName + "\n" +
                          "Kích thước: " + info.getFormattedSize() + "\n" +
                          "Thời gian: " + info.getFormattedDate() + "\n\n" +
                          "CẢNH BÁO: Restore sẽ XÓA TOÀN BỘ dữ liệu hiện tại!\n\n" +
                          "Nhập mật khẩu Super Admin đã dùng để mã hóa file này:");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Mật khẩu");
        builder.setView(input);
        
        builder.setPositiveButton("Restore", (dialog, which) -> {
            String password = input.getText().toString().trim();
            
            if (password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }
            
            performRestore(latestBackup, password);
        });
        
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }
    
    /**
     * Thực hiện restore database
     */
    private void performRestore(File backupFile, String password) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Đang restore database...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Chạy restore trong background thread
        new Thread(() -> {
            boolean success = backupHelper.restoreDatabase(backupFile, password);
            
            runOnUiThread(() -> {
                progressDialog.dismiss();
                
                if (success) {
                    new AlertDialog.Builder(this)
                        .setTitle("Restore thành công!")
                        .setMessage("Database đã được khôi phục.\n\n" +
                                   "App sẽ khởi động lại để áp dụng thay đổi.")
                        .setCancelable(false)
                        .setPositiveButton("Khởi động lại", (dialog, which) -> {
                            // Restart app
                            restartApp();
                        })
                        .show();
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("Restore thất bại")
                        .setMessage("Không thể restore database.\n\n" +
                                   "Nguyên nhân có thể:\n" +
                                   "- Sai mật khẩu\n" +
                                   "- File backup bị hỏng\n" +
                                   "- File không phải do app này tạo ra")
                        .setPositiveButton("Thử lại", (dialog, which) -> showRestoreDialog())
                        .setNegativeButton("Hủy", null)
                        .show();
                }
            });
        }).start();
    }
    
    /**
     * Hiển thị danh sách các file backup
     */
    private void showBackupsList() {
        // TODO: Implement list view for all backups
        File latestBackup = backupHelper.findLatestBackup();
        
        if (latestBackup == null) {
            Toast.makeText(this, "Không có file backup nào", Toast.LENGTH_SHORT).show();
            return;
        }
        
        BackupRestoreHelper.BackupInfo info = backupHelper.getBackupInfo(latestBackup);
        
        new AlertDialog.Builder(this)
            .setTitle("File Backup gần nhất")
            .setMessage("Tên: " + info.fileName + "\n" +
                       "Đường dẫn: " + info.filePath + "\n" +
                       "Kích thước: " + info.getFormattedSize() + "\n" +
                       "Thời gian: " + info.getFormattedDate())
            .setPositiveButton("OK", null)
            .show();
    }
    
    /**
     * Xác nhận xóa tất cả backup
     */
    private void confirmDeleteAllBackups() {
        new AlertDialog.Builder(this)
            .setTitle("Xóa tất cả backup")
            .setMessage("Bạn có chắc muốn xóa TẤT CẢ file backup?\n\n" +
                       "Hành động này KHÔNG THỂ HOÀN TÁC!")
            .setPositiveButton("Xóa", (dialog, which) -> deleteAllBackups())
            .setNegativeButton("Hủy", null)
            .show();
    }
    
    /**
     * Xóa tất cả file backup
     */
    private void deleteAllBackups() {
        int deletedCount = backupHelper.deleteAllBackups();
        
        if (deletedCount > 0) {
            Toast.makeText(this, "Đã xóa " + deletedCount + " file backup", Toast.LENGTH_SHORT).show();
            updateBackupInfo();
        } else {
            Toast.makeText(this, "Không có file backup nào để xóa", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Cập nhật thông tin backup hiện tại
     */
    private void updateBackupInfo() {
        File latestBackup = backupHelper.findLatestBackup();
        
        if (latestBackup != null) {
            BackupRestoreHelper.BackupInfo info = backupHelper.getBackupInfo(latestBackup);
            textViewBackupInfo.setText("File backup gần nhất:\n\n" +
                                      "Tên: " + info.fileName + "\n" +
                                      "Kích thước: " + info.getFormattedSize() + "\n" +
                                      "Thời gian: " + info.getFormattedDate());
            textViewBackupInfo.setVisibility(View.VISIBLE);
            
            btnRestore.setEnabled(true);
            btnDeleteBackups.setEnabled(true);
        } else {
            textViewBackupInfo.setText(
                    "Chưa phát hiện backup cũ (.enc).\n" +
                            "Bạn vẫn có thể chọn file .iribackup hoặc .enc để phục hồi."
            );
            textViewBackupInfo.setVisibility(View.VISIBLE);
            
            btnRestore.setEnabled(true);
            btnDeleteBackups.setEnabled(false);
        }
    }
    
    /**
     * Restart app
     */
    private void restartApp() {
        android.content.Intent intent = getPackageManager()
            .getLaunchIntentForPackage(getPackageName());
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        System.exit(0);
    }
}
