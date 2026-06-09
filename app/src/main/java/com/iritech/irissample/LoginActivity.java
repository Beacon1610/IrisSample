package com.iritech.irissample;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity; // Cần thư viện này
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Build;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Environment;
import android.os.Build;
import android.os.Bundle;
import android.view.View; // Thêm import này
import android.widget.Button; // Thêm import này
import android.widget.EditText; // Thêm import này
import android.widget.TextView; // Thêm import này
import android.content.Intent; // Thêm import này cho việc chuyển Activity
import android.widget.Toast; // Thêm import này nếu dùng Toast
import com.iritech.irissample.R;
import com.iritech.iris.CaptureActivity;
import com.iritech.iris.Constants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {
    private EditText editTextEmail;
    private EditText editTextPassword;
    private Button buttonLogin;
    private Button buttonLoginWithIris;
    private Button buttonRegister;
    private TextView textViewError;
    private TextView textViewForgotPassword;
    
    // THAY DOI: Them DatabaseHelper de kiem tra va xac thuc
    private DatabaseHelper dbHelper;
    
    private static final int REQUEST_CODE_IDENTIFY_LOGIN = 3001;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 100;
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
        
        // Bỏ auto-redirect đến RegisterAdminActivity
        // Super Admin phải tự tạo nếu chưa có (thông qua màn hình đặc biệt)
        boolean superAdminExists = dbHelper.isSuperAdminExists();
        android.util.Log.d("LoginActivity", "Super Admin exists: " + superAdminExists);
        
        if (!superAdminExists) {
            // Kiểm tra quyền storage trước
            if (!checkStoragePermission()) {
                // requestCriticalPermissions() đã request READ_EXTERNAL_STORAGE rồi
                // Chờ onRequestPermissionsResult(REQUEST_ALL_PERMISSIONS) xử lý tiếp
                android.util.Log.d("LoginActivity", "Storage permission not granted, waiting for REQUEST_ALL_PERMISSIONS result...");
                return;
            }
            
            // Kiểm tra backup trước - nếu có backup thì ưu tiên restore
            if (checkForBackupOnStartup()) {
                // Tìm thấy backup và đã hiện dialog → Không redirect đến RegisterAdminActivity
                // User sẽ quyết định restore hay bỏ qua
                return;
            }
            
            // Không có backup → Chuyển đến màn hình tạo Super Admin đầu tiên
            android.util.Log.d("LoginActivity", "No backup found, redirecting to RegisterAdminActivity");
            Intent intent = new Intent(LoginActivity.this, RegisterAdminActivity.class);
            startActivity(intent);
            finish();
            return;
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
            
            Toast.makeText(this, "Đăng nhập thành công! Xin chào " + fullName, Toast.LENGTH_SHORT).show();

            // THAY DOI: Truyen role va email sang MainActivity
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
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
    
    /**
     * Kiểm tra backup khi app khởi động
     * Nếu database trống và có backup → Hiện dialog restore
     * 
     * @return true nếu tìm thấy backup và hiện dialog, false nếu không có backup
     */
    private boolean checkForBackupOnStartup() {
        android.util.Log.d("LoginActivity", "checkForBackupOnStartup() called");
        BackupRestoreHelper backupHelper = new BackupRestoreHelper(this);
        
        // Chỉ check khi database trống (chưa có Super Admin)
        if (!dbHelper.isSuperAdminExists()) {
            android.util.Log.d("LoginActivity", "Database is empty, checking for backup files...");
            java.io.File latestBackup = backupHelper.findLatestBackup();
            
            if (latestBackup != null) {
                android.util.Log.d("LoginActivity", "Found backup: " + latestBackup.getAbsolutePath());
                // Tìm thấy backup → Hiện dialog cho phép restore
                showRestoreBackupDialog(latestBackup, backupHelper);
                return true;
            } else {
                android.util.Log.d("LoginActivity", "No backup file found");
            }
        } else {
            android.util.Log.d("LoginActivity", "Database not empty, skipping backup check");
        }
        
        return false;
    }
    
    /**
     * Kiểm tra quyền READ_EXTERNAL_STORAGE
     */
    private boolean checkStoragePermission() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
//                    == PackageManager.PERMISSION_GRANTED;
//        }
//        return true; // Android < 6.0 không cần runtime permission
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            return true;
        }

        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Request quyền READ_EXTERNAL_STORAGE
     */
    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_CODE_STORAGE_PERMISSION);
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

            // Xử lý backup/register flow nếu chưa có Super Admin
            if (!dbHelper.isSuperAdminExists()) {
                if (checkStoragePermission()) {
                    android.util.Log.d("LoginActivity", "Storage permission granted via REQUEST_ALL_PERMISSIONS, checking backup...");
                    if (checkForBackupOnStartup()) {
                        return;
                    }
                    // Không có backup → Redirect đến RegisterAdminActivity
                    android.util.Log.d("LoginActivity", "No backup found, redirecting to RegisterAdminActivity");
                    Intent intent = new Intent(LoginActivity.this, RegisterAdminActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    android.util.Log.w("LoginActivity", "Storage permission denied via REQUEST_ALL_PERMISSIONS");
                    Toast.makeText(this, "Không có quyền truy cập storage. Không thể kiểm tra backup.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(LoginActivity.this, RegisterAdminActivity.class);
                    startActivity(intent);
                    finish();
                }
            }
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
        
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("LoginActivity", "Storage permission granted, checking backup...");
                // Permission granted, check backup again
                if (!dbHelper.isSuperAdminExists()) {
                    if (checkForBackupOnStartup()) {
                        return;
                    }
                    // Không có backup → Redirect đến RegisterAdminActivity
                    android.util.Log.d("LoginActivity", "No backup found, redirecting to RegisterAdminActivity");
                    Intent intent = new Intent(LoginActivity.this, RegisterAdminActivity.class);
                    startActivity(intent);
                    finish();
                }
            } else {
                android.util.Log.w("LoginActivity", "Storage permission denied");
                // Permission denied, proceed to RegisterAdminActivity
                Toast.makeText(this, "Không có quyền truy cập storage. Không thể kiểm tra backup.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(LoginActivity.this, RegisterAdminActivity.class);
                startActivity(intent);
                finish();
            }
        }
    }
    
    /**
     * Hiển thị dialog cho phép restore backup
     */
    private void showRestoreBackupDialog(final java.io.File backupFile, final BackupRestoreHelper backupHelper) {
        BackupRestoreHelper.BackupInfo info = backupHelper.getBackupInfo(backupFile);
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Phát hiện dữ liệu cũ");
        builder.setMessage("Tìm thấy file backup:\n\n" +
                          "Tên: " + info.fileName + "\n" +
                          "Kích thước: " + info.getFormattedSize() + "\n" +
                          "Thời gian: " + info.getFormattedDate() + "\n\n" +
                          "Bạn có muốn khôi phục dữ liệu này không?");
        builder.setCancelable(false);
        
        builder.setPositiveButton("Khôi phục", (dialog, which) -> {
            // Yêu cầu nhập password để restore
            showRestorePasswordDialog(backupFile, backupHelper);
        });
        
        builder.setNegativeButton("Bỏ qua", (dialog, which) -> {
            // Tiếp tục tạo Super Admin mới
            dialog.dismiss();
            // Chuyển đến RegisterAdminActivity
            Intent intent = new Intent(LoginActivity.this, RegisterAdminActivity.class);
            startActivity(intent);
            finish();
        });
        
        builder.show();
    }
    
    /**
     * Hiển thị dialog nhập password để restore
     */
    private void showRestorePasswordDialog(final java.io.File backupFile, final BackupRestoreHelper backupHelper) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Nhập mật khẩu");
        builder.setMessage("Nhập mật khẩu Super Admin đã dùng để mã hóa file backup:");
        
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Mật khẩu");
        builder.setView(input);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            String password = input.getText().toString().trim();
            
            if (password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show();
                showRestorePasswordDialog(backupFile, backupHelper);
                return;
            }
            
            // Thực hiện restore
            performRestoreOnStartup(backupFile, password, backupHelper);
        });
        
        builder.setNegativeButton("Hủy", (dialog, which) -> {
            // Bỏ qua restore, tiếp tục tạo Super Admin mới
            dialog.dismiss();
        });
        
        builder.show();
    }
    
    /**
     * Thực hiện restore backup khi startup
     */
    private void performRestoreOnStartup(final java.io.File backupFile, final String password,
                                        final BackupRestoreHelper backupHelper) {
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Đang khôi phục dữ liệu...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Chạy restore trong background thread
        new Thread(() -> {
            boolean success = backupHelper.restoreDatabase(backupFile, password);
            
            runOnUiThread(() -> {
                progressDialog.dismiss();
                
                if (success) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Khôi phục thành công!")
                        .setMessage("Dữ liệu đã được khôi phục.\n\n" +
                                   "App sẽ khởi động lại để áp dụng thay đổi.")
                        .setCancelable(false)
                        .setPositiveButton("Khởi động lại", (dialog, which) -> {
                            // Restart app
                            restartApp();
                        })
                        .show();
                } else {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Khôi phục thất bại")
                        .setMessage("Không thể khôi phục dữ liệu.\n\n" +
                                   "Nguyên nhân có thể:\n" +
                                   "- Sai mật khẩu\n" +
                                   "- File backup bị hỏng")
                        .setPositiveButton("Thử lại", (dialog, which) -> {
                            showRestorePasswordDialog(backupFile, backupHelper);
                        })
                        .setNegativeButton("Bỏ qua", null)
                        .show();
                }
            });
        }).start();
    }
    
    /**
     * Restart app
     */
    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        System.exit(0);
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
