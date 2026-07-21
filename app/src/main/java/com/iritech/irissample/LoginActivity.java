package com.iritech.irissample;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity; // Cần thư viện này
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Build;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Environment;
import android.os.Bundle;
import android.text.InputType;
import android.view.View; // Thêm import này
import android.widget.Button; // Thêm import này
import android.widget.EditText; // Thêm import này
import android.widget.ImageButton;
import android.widget.TextView; // Thêm import này
import android.content.Intent; // Thêm import này cho việc chuyển Activity
import android.widget.Toast; // Thêm import này nếu dùng Toast

import com.google.common.util.concurrent.ListenableFuture;
import com.iritech.iris.CaptureActivity;
import com.iritech.iris.Constants;
import com.iritech.irissample.face.FaceEmbeddingExtractor;
import com.iritech.irissample.face.FaceMatchResult;
import com.iritech.irissample.face.FaceMatcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {
    private EditText editTextEmail;
    private EditText editTextPassword;
    private Button buttonLogin;
    private Button buttonLoginWithIris;
    private Button buttonRegister;
    private TextView textViewError;
    private TextView textViewForgotPassword;
    private ActivityResultLauncher<String[]> openFreshBackupLauncher;
    private BackupImportManager backupImportManager;
    private boolean firstRunDialogVisible;
    
    // THAY DOI: Them DatabaseHelper de kiem tra va xac thuc
    private DatabaseHelper dbHelper;
    
    private static final int REQUEST_CODE_IDENTIFY_LOGIN = 3001;
    private static final int REQUEST_IRIS_PERMISSIONS = 102;
    private static final int REQUEST_IRIS_SDK_PERMISSION = 103;
    private static final int REQUEST_ALL_PERMISSIONS = 104; // Request tất cả permissions quan trọng
    private static final String IRIS_SDK_PERMISSION = "com.id2mp.permissions.IRIS";

    private Intent pendingIrisIntent; // Intent chờ được cấp permission

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        android.util.Log.d("LoginActivity", "=== onCreate() started ===");
        
        // THAY DOI: Khoi tao DatabaseHelper
        dbHelper = new DatabaseHelper(this);
        backupImportManager = new BackupImportManager(this);
        registerFreshInstallRestoreLauncher();

        boolean superAdminExists = dbHelper.isSuperAdminExists();
        android.util.Log.d("LoginActivity", "Super Admin exists: " + superAdminExists);

        if (!superAdminExists) {
            showFirstRunDialog();
            return;
        }

        // QUAN TRONG: Thiet lap USB Activity de SDK co the mo sensor phan cung
        CaptureActivity.setUSBActivity(this);
        initIrisFolders();

        // Request tất cả permissions quan trọng ngay khi app khởi động
        // Đây là entry point đầu tiên, cần request đầy đủ như MainActivity
        requestCriticalPermissions();

        // Yêu cầu quyền SDK mống mắt ngay khi khởi động (fire-and-forget, giống MainActivity.loadConfigs)
        if (ContextCompat.checkSelfPermission(this, IRIS_SDK_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{IRIS_SDK_PERMISSION}, REQUEST_IRIS_SDK_PERMISSION);
        }
        
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonLoginWithIris = findViewById(R.id.buttonLoginWithIris);
        buttonRegister = findViewById(R.id.buttonRegister);
        textViewError = findViewById(R.id.textViewError);
        textViewForgotPassword = findViewById(R.id.textViewForgotPassword);
        
        // Handle deep link từ email (irissample://login)
        handleDeepLink(getIntent());
        
        // Ẩn nút Đăng ký - Admin thường không tự đăng ký được nữa
        buttonRegister.setVisibility(View.GONE);

        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });
        
        buttonLoginWithIris.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginWithIris();
            }
        });
        
        // Forgot Password click listener
        textViewForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
                startActivity(intent);
            }
        });
        
        // Bỏ listener cho buttonRegister - Admin thường không tự đăng ký
    }


    private void attemptLogin() {

        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        // THAY DOI: Xac thuc bang DatabaseHelper thay vi hard-code
        Cursor cursor = dbHelper.authenticateUser(email, password);
        if (cursor != null && cursor.moveToFirst()) {
            // Dang nhap thanh cong
            String role = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_ROLE));
            String fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_FULL_NAME));
            cursor.close();

            boolean isFirstLogin = dbHelper.isFirstLogin(email);
//
//            Toast.makeText(this, "Đăng nhập thành công! Xin chào " + fullName, Toast.LENGTH_SHORT).show();
//
//            // THAY DOI: Truyen role va email sang MainActivity
//            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
//            intent.putExtra("USER_EMAIL", email);
//            intent.putExtra("USER_ROLE", role);
//            intent.putExtra("USER_NAME", fullName);
//            startActivity(intent);
//            finish();
            if (isFirstLogin) {
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("USER_EMAIL", email);
                intent.putExtra("USER_ROLE", role);
                intent.putExtra("USER_NAME", fullName);
                startActivity(intent);
                finish();
                return;
            }
            boolean hasFace = dbHelper.hasAdminFace(email);
            if (!hasFace) {
                Intent intent = new Intent(LoginActivity.this, ProfileActivity.class);
                intent.putExtra("USER_EMAIL", email);
                intent.putExtra("USER_ROLE", role);
                intent.putExtra("USER_NAME", fullName);
                intent.putExtra("IS_FIRST_LOGIN", false);
                intent.putExtra("FORCE_FACE_ENROLLMENT", true);
                intent.putExtra("RETURN_TO_MAIN_AFTER_SAVE", true);
                startActivity(intent);
                finish();
                return;
            }
            Intent intent = new Intent(LoginActivity.this, AdminFaceVerifyActivity.class);
            intent.putExtra("USER_EMAIL", email);
            intent.putExtra("USER_ROLE", role);
            intent.putExtra("USER_NAME", fullName);
            startActivity(intent);
            finish();

        } else {
            // Dang nhap that bai
            if (textViewError != null) {
                textViewError.setText("Tên đăng nhập hoặc mật khẩu không đúng.");
                textViewError.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, "Tên đăng nhập hoặc mật khẩu không đúng.", Toast.LENGTH_SHORT).show();
            }
            editTextPassword.setText("");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Xử lý khi app đang chạy và nhận deep link
        handleDeepLink(intent);
    }

    // Xử lý deep link từ email: irissample://login
    private void handleDeepLink(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            android.net.Uri uri = intent.getData();
            if (uri != null) {
                String scheme = uri.getScheme(); // "irissample"
                String host = uri.getHost();     // "login"
                
                if ("irissample".equals(scheme) && "login".equals(host)) {
                    // Người dùng click vào link từ email - hiển thị thông báo
                    Toast.makeText(this, "Chào mừng bạn quay lại! Vui lòng đăng nhập để tiếp tục", Toast.LENGTH_LONG).show();
                    
                    // Nếu có email được truyền qua query parameter, tự động điền vào form
                    String emailParam = uri.getQueryParameter("email");
                    if (emailParam != null && editTextEmail != null) {
                        editTextEmail.setText(emailParam);
                        editTextPassword.requestFocus(); // Focus vào mật khẩu
                    }
                }
            }
        }
    }
    
    /**
     * Đăng nhập bằng nhận diện mống mắt
     */
    private void loginWithIris() {
        // Kiểm tra có Admin nào đã ghi danh mống mắt chưa
        if (!dbHelper.hasAnyAdminWithIris()) {
            Toast.makeText(this, 
                          "Chưa có Admin nào đăng ký mống mắt.\nVui lòng đăng nhập bằng email và mật khẩu.",
                          Toast.LENGTH_LONG).show();
            return;
        }
        
        // Tạo intent, kiểm tra quyền trước khi launch
        Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
        intent.setAction(Constants.ACTION_IDENTIFY);
        intent.putExtra(Constants.EXTRA_USER_ID, "");
        checkIrisPermissionsAndStart(intent);
    }

    /**
     * Kiểm tra quyền WRITE_EXTERNAL_STORAGE và CAMERA trước khi khởi chạy CaptureActivity.
     * Thứ tự: Ghi (WRITE) trước → Chụp (CAMERA) sau.
     */
    private void checkIrisPermissionsAndStart(Intent intent) {
        boolean hasWrite = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasCamera = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (hasWrite && hasCamera) {
            LicenseCheckHelper.checkLicenseAndStartCapture(this, intent, REQUEST_CODE_IDENTIFY_LOGIN);
            return;
        }

        pendingIrisIntent = intent;
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA
                },
                REQUEST_IRIS_PERMISSIONS);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_IDENTIFY_LOGIN && resultCode == RESULT_OK) {
            if (data != null) {
                int resultCodeExt = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
                String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);
                
                // Log chi tiết để debug
                android.util.Log.d("LoginActivity", "Iris identify result: code=" + resultCodeExt + ", msg=" + resultMsg);
                
                if (resultCodeExt == 0) {
                    // Identify thành công
                    int resultCount = data.getIntExtra(Constants.EXTRA_MATCHING_COUNT, 0);
                    String resultItems = data.getStringExtra(Constants.EXTRA_MATCHING_ITEMS);
                    
                    android.util.Log.d("LoginActivity", "Match count: " + resultCount + ", items: " + resultItems);
                    
                    if (resultCount > 0 && resultItems != null) {
                        // Parse email từ result (lấy match đầu tiên - best match)
                        String email = extractEmailFromResult(resultItems);
                        
                        if (email != null && !email.isEmpty()) {
                            // Tìm thông tin Admin từ email
                            Cursor cursor = dbHelper.getAdminByEmail(email);
                            
                            if (cursor != null && cursor.moveToFirst()) {
                                String role = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_ROLE));
                                String fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_FULL_NAME));
                                cursor.close();
                                
                                Toast.makeText(this, "Đăng nhập thành công! Xin chào " + fullName, Toast.LENGTH_SHORT).show();
                                
                                // Chuyển đến MainActivity
                                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                intent.putExtra("USER_EMAIL", email);
                                intent.putExtra("USER_ROLE", role);
                                intent.putExtra("USER_NAME", fullName);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(this, "Không tìm thấy thông tin Admin", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Không thể xác định email từ kết quả", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Không tìm thấy mống mắt khớp. Vui lòng thử lại hoặc đăng nhập bằng mật khẩu.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Identify thất bại - resultMsg đã khai báo ở đầu method
                    Toast.makeText(this, "Lỗi nhận diện: " + resultMsg, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    /**
     * Parse email từ result string và convert safe ID ngược lại thành email
     * Format: "ID: admin_at_example_com, distance: 0.123; ID: teacher_at_school_edu, distance: 0.456"
     */
    private String extractEmailFromResult(String resultItems) {
        try {
            // Lấy ID đầu tiên (best match)
            if (resultItems.contains("ID: ")) {
                int startIndex = resultItems.indexOf("ID: ") + 4;
                int endIndex = resultItems.indexOf(", distance:", startIndex);
                if (endIndex > startIndex) {
                    String safeId = resultItems.substring(startIndex, endIndex).trim();
                    // Convert safe ID ngược lại thành email
                    // VD: admin_at_example_com -> admin@example.com
                    String email = safeId.replace("_at_", "@");
                    // Thay _ cuối cùng thành . (chỉ sau @)
                    int atIndex = email.indexOf("@");
                    if (atIndex != -1) {
                        String domain = email.substring(atIndex + 1);
                        domain = domain.replace("_", ".");
                        email = email.substring(0, atIndex + 1) + domain;
                    }
                    return email;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private void registerFreshInstallRestoreLauncher() {
        openFreshBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                sourceUri -> {
                    if (sourceUri == null) {
                        showFirstRunDialog();
                        return;
                    }
                    showFreshRestorePassphraseDialog(sourceUri);
                }
        );
    }

    private void showFirstRunDialog() {
        if (isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())
                || firstRunDialogVisible
                || dbHelper.isSuperAdminExists()) {
            return;
        }

        firstRunDialogVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("No system data found")
                .setMessage("You can restore a previous backup or create a new Super Admin account.")
                .setPositiveButton("Restore Backup", (ignored, which) -> {
                    firstRunDialogVisible = false;
                    launchFreshBackupPicker();
                })
                .setNegativeButton("Create New Super Admin", (ignored, which) -> {
                    firstRunDialogVisible = false;
                    startActivity(new Intent(LoginActivity.this, RegisterAdminActivity.class));
                    finish();
                })
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(ignored -> firstRunDialogVisible = false);
        dialog.show();
    }

    private void launchFreshBackupPicker() {
        openFreshBackupLauncher.launch(new String[]{
                "application/vnd.iritech.iribackup",
                "application/octet-stream",
                "*/*"
        });
    }

    private void showFreshRestorePassphraseDialog(Uri sourceUri) {
        EditText input = new EditText(this);
        input.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        input.setHint("Mật khẩu file backup");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Restore from Backup")
                .setMessage("Nhập mật khẩu riêng đã dùng khi tạo file backup:")
                .setView(input)
                .setPositiveButton("Restore", null)
                .setNegativeButton("Back", (ignored, which) -> showFirstRunDialog())
                .create();

        dialog.setOnCancelListener(ignored -> showFirstRunDialog());
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    char[] passphrase = input.getText().toString().toCharArray();
                    if (passphrase.length == 0) {
                        input.setError("Vui lòng nhập mật khẩu backup");
                        return;
                    }

                    input.setText("");
                    dialog.setOnCancelListener(null);
                    dialog.dismiss();
                    performFreshInstallRestore(sourceUri, passphrase);
                })
        );
        dialog.show();
    }

    private void performFreshInstallRestore(Uri sourceUri, char[] passphrase) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Đang kiểm tra và phục hồi dữ liệu...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Release LoginActivity's SQLite connection before BackupImportManager
        // checkpoints, creates a safety backup and replaces the live database.
        dbHelper.close();

        new Thread(() -> {
            BackupImportManager.ImportResult result;
            try {
                result = backupImportManager.importBackup(sourceUri, passphrase);
            } finally {
                Arrays.fill(passphrase, '\0');
            }

            runOnUiThread(() -> {
                if (isFinishing()
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                    return;
                }
                progressDialog.dismiss();

                if (!result.isSuccess()) {
                    showFreshRestoreFailureDialog(sourceUri, result.getMessage());
                    return;
                }

                StringBuilder message = new StringBuilder("Dữ liệu đã được phục hồi an toàn.");
                if (!result.getWarnings().isEmpty()) {
                    message.append("\n\nCảnh báo:");
                    for (String warning : result.getWarnings()) {
                        message.append("\n- ").append(warning);
                    }
                }
                message.append("\n\nỨng dụng sẽ quay lại màn hình đăng nhập.");

                new AlertDialog.Builder(this)
                        .setTitle("Restore successful")
                        .setMessage(message.toString())
                        .setCancelable(false)
                        .setPositiveButton("Continue", (ignored, which) -> restartAfterFreshRestore())
                        .show();
            });
        }).start();
    }

    private void showFreshRestoreFailureDialog(Uri sourceUri, String errorMessage) {
        String message = errorMessage == null || errorMessage.trim().isEmpty()
                ? "Sai mật khẩu hoặc file backup đã bị hỏng/bị thay đổi."
                : errorMessage;

        new AlertDialog.Builder(this)
                .setTitle("Restore failed")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Try Again", (ignored, which) ->
                        showFreshRestorePassphraseDialog(sourceUri))
                .setNegativeButton("Back", (ignored, which) -> showFirstRunDialog())
                .show();
    }

    private void restartAfterFreshRestore() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_IRIS_SDK_PERMISSION) {
            // Kết quả fire-and-forget – không chặn luồng người dùng
            return;
        }

        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            // Log kết quả
            int granted = 0;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) granted++;
            }
            android.util.Log.d("LoginActivity", "Critical permissions: " + granted + "/" + grantResults.length + " granted");

            return;
        }

        if (requestCode == REQUEST_IRIS_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && pendingIrisIntent != null) {
                LicenseCheckHelper.checkLicenseAndStartCapture(this, pendingIrisIntent, REQUEST_CODE_IDENTIFY_LOGIN);
                pendingIrisIntent = null;
            } else {
                Toast.makeText(this,
                        "Cần cấp quyền Camera và bộ nhớ để sử dụng đăng nhập bằng mống mắt.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
    }

    /**
     * Tạo thư mục cần thiết cho SDK mống mắt (giống MainActivity.initFolder)
     */
    private void initIrisFolders() {
        String base = Environment.getExternalStorageDirectory().toString() + File.separator + "iritech";
        File enrollFolder = new File(base + File.separator + "enroll");
        if (!enrollFolder.exists()) enrollFolder.mkdirs();
        File verifyFolder = new File(base + File.separator + "verify");
        if (!verifyFolder.exists()) verifyFolder.mkdirs();
    }

    /**
     * Request tất cả permissions quan trọng ngay khi app khởi động.
     * Đây là entry point đầu tiên của app, cần request đầy đủ giống MainActivity.
     *
     * Permissions cần thiết:
     * - CAMERA: Chụp ảnh mống mắt
     * - WRITE_EXTERNAL_STORAGE: Lưu template và ảnh
     * - READ_EXTERNAL_STORAGE: Đọc backup, template
     * - READ_PHONE_STATE: SDK license verification
     * - ACCESS_NETWORK_STATE: Kết nối mạng
     */
    private void requestCriticalPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Camera - cho chụp mống mắt
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

//        // Storage - cho lưu template và ảnh
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED) {
//            permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
//        }
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED) {
//            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
//        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        // Phone state - SDK license check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
        }

        // Network state - kết nối mạng
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_NETWORK_STATE);
        }

        if (!permissionsNeeded.isEmpty()) {
            android.util.Log.d("LoginActivity", "Requesting " + permissionsNeeded.size() + " critical permissions");
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    REQUEST_ALL_PERMISSIONS);
        }
    }
}
