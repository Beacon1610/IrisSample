package com.iritech.irissample;

// Các import cơ bản của Android
import android.Manifest; // Khai báo các permission (CAMERA, READ_EXTERNAL_STORAGE)
import android.app.Activity; // Xử lý result từ camera/gallery
import android.app.AlertDialog; // Hiển thị dialog xác nhận PIN
import android.content.DialogInterface; // Xử lý sự kiện click trên dialog
import android.content.Intent; // Nhận email từ MainActivity, mở camera/gallery
import android.content.pm.PackageManager; // Kiểm tra permission đã cấp chưa
import android.database.Cursor; // Đọc dữ liệu user từ database
import android.graphics.Bitmap; // Xử lý ảnh đại diện
import android.graphics.BitmapFactory; // Chuyển file ảnh thành Bitmap để hiển thị
import android.net.Uri; // Xử lý URI ảnh từ gallery và camera
import android.os.Bundle; // Lưu trạng thái activity
import android.os.Environment; // Lấy đường dẫn thư mục Pictures
import android.provider.MediaStore; // Mở camera và gallery
import android.text.InputType; // Thiết lập kiểu input cho EditText (password, number)
import android.text.TextUtils; // Kiểm tra chuỗi rỗng
import android.view.View; // Xử lý sự kiện click
import android.widget.ArrayAdapter; // Adapter cho Spinner giới tính
import android.widget.Button; // Các nút Sửa, Lưu, Hủy, Chụp ảnh, Chọn ảnh
import android.widget.CheckBox; // Checkbox hiển thị/ẩn password
import android.widget.CompoundButton; // Xử lý sự kiện checkbox
import android.widget.EditText; // Các trường nhập liệu
import android.widget.ImageView; // Hiển thị ảnh đại diện
import android.widget.LinearLayout; // Layout chứa các phần tử
import android.widget.Spinner; // Dropdown chọn giới tính
import android.widget.TextView; // Hiển thị text và thông báo lỗi
import android.widget.Toast; // Hiển thị thông báo ngắn

// Import từ AndroidX
import androidx.appcompat.app.AppCompatActivity; // Base class cho activity
import androidx.core.app.ActivityCompat; // Request permissions
import androidx.core.content.ContextCompat; // Kiểm tra permissions
import androidx.core.content.FileProvider; // Tạo URI an toàn cho file ảnh từ camera

// Import từ iris module
import com.iritech.iris.CaptureActivity;
import com.iritech.iris.Constants;

// Import Java utilities
import java.io.File; // Tạo và kiểm tra file ảnh
import java.io.FileOutputStream; // Ghi Bitmap vào file
import java.io.IOException; // Xử lý lỗi file I/O
import java.text.SimpleDateFormat; // Format timestamp cho tên file ảnh
import java.util.Date; // Lấy thời gian hiện tại cho tên file
import java.util.Locale; // Định dạng ngôn ngữ cho SimpleDateFormat

public class ProfileActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private PasswordValidationHelper validationHelper;
    private String currentUserEmail; // Email của user đang đăng nhập
    private String currentUserRole; // Vai trò của user (SUPER_ADMIN / ADMIN)
    private boolean isEditMode = false; // Chế độ hiện tại: false = xem, true = sửa
    private boolean isFirstLogin = false; // Đánh dấu nếu đang là lần đầu login
    
    private ImageView imageViewAvatar;
    private EditText editTextEmail;
    private EditText editTextPassword;
    private EditText editTextConfirmPassword;
    // PIN fields đã xóa - dùng Device Lock thay thế
    private CheckBox checkBoxShowPassword;
    private EditText editTextFullName;
    private EditText editTextDob;
    private EditText editTextPhone;
    private Spinner spinnerGender;
    private EditText editTextDescription;
    private TextView textViewRole;
    private TextView textViewEmployeeCode; // Hiển thị mã số giảng viên
    private TextView textViewError;
    private Button buttonSave;
    private Button buttonCancel;
    private Button buttonEdit;
    private Button buttonCamera;
    private Button buttonGallery;
    private Button buttonEnrollIris;
    private TextView textViewIrisStatus;
    private LinearLayout layoutPasswordSection;
    private LinearLayout layoutActionButtons;
    private LinearLayout layoutAvatarButtons;
    
    private String photoPath;
    private String currentPhotoPath;
    
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_CAMERA_CAPTURE = 102;
    private static final int REQUEST_GALLERY_PICK = 103;
    private static final int REQUEST_CODE_ENROLL_IRIS = 2002;
    private static final int REQUEST_IRIS_PERMISSIONS = 104;
    private static final int REQUEST_IRIS_SDK_PERMISSION = 105;
    private static final String IRIS_SDK_PERMISSION = "com.id2mp.permissions.IRIS";

    private Intent pendingIrisIntent; // Intent cho den duoc cap permission

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        dbHelper = new DatabaseHelper(this);

        // QUAN TRONG: Thiet lap USB Activity de SDK co the mo sensor phan cung
        CaptureActivity.setUSBActivity(this);

        validationHelper = new PasswordValidationHelper(dbHelper);
        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        isFirstLogin = getIntent().getBooleanExtra("IS_FIRST_LOGIN", false);

        if (TextUtils.isEmpty(currentUserEmail)) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Nếu là lần đầu login, tự động chuyển sang chế độ edit
        if (isFirstLogin) {
            isEditMode = true;
        }

        initializeViews();
        setupGenderSpinner();
        loadUserData();
        setViewMode();
        setupListeners();
    }

    private void initializeViews() {
        imageViewAvatar = findViewById(R.id.imageViewAvatar);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        // Bỏ findViewById cho PIN fields
        checkBoxShowPassword = findViewById(R.id.checkBoxShowPassword);
        editTextFullName = findViewById(R.id.editTextFullName);
        editTextDob = findViewById(R.id.editTextDob);
        editTextPhone = findViewById(R.id.editTextPhone);
        spinnerGender = findViewById(R.id.spinnerGender);
        editTextDescription = findViewById(R.id.editTextDescription);
        textViewRole = findViewById(R.id.textViewRole);
        textViewEmployeeCode = findViewById(R.id.textViewEmployeeCode);
        textViewError = findViewById(R.id.textViewError);
        buttonSave = findViewById(R.id.buttonSave);
        buttonCancel = findViewById(R.id.buttonCancel);
        buttonEdit = findViewById(R.id.buttonEdit);
        buttonCamera = findViewById(R.id.buttonCamera);
        buttonGallery = findViewById(R.id.buttonGallery);
        buttonEnrollIris = findViewById(R.id.buttonEnrollIris);
        textViewIrisStatus = findViewById(R.id.textViewIrisStatus);
        layoutPasswordSection = findViewById(R.id.layoutPasswordSection);
        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        layoutAvatarButtons = findViewById(R.id.layoutAvatarButtons);
    }

    private void setupGenderSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.gender_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);
    }

    private void loadUserData() {
        Cursor cursor = dbHelper.getAdminByEmail(currentUserEmail);
        if (cursor != null && cursor.moveToFirst()) {
            editTextEmail.setText(cursor.getString(cursor.getColumnIndexOrThrow("email")));
            editTextFullName.setText(cursor.getString(cursor.getColumnIndexOrThrow("full_name")));
            editTextDob.setText(cursor.getString(cursor.getColumnIndexOrThrow("date_of_birth")));
            editTextPhone.setText(cursor.getString(cursor.getColumnIndexOrThrow("phone")));
            
            String gender = cursor.getString(cursor.getColumnIndexOrThrow("gender"));
            setSpinnerValue(spinnerGender, gender);
            
            editTextDescription.setText(cursor.getString(cursor.getColumnIndexOrThrow("description")));
            
            String role = cursor.getString(cursor.getColumnIndexOrThrow("role"));
            currentUserRole = role; // Lưu role để kiểm tra sau này
            textViewRole.setText("Vai trò: " + (role.equals("SUPER_ADMIN") ? "Super Admin" : "Admin"));
            
            // Hiển thị mã số giảng viên (chỉ cho Admin thường, không hiển thị cho Super Admin)
            if (role.equals("SUPER_ADMIN")) {
                textViewEmployeeCode.setVisibility(View.GONE);
            } else {
                int codeIndex = cursor.getColumnIndex(DatabaseHelper.COL_ADMIN_CODE);
                if (codeIndex != -1) {
                    String adminCode = cursor.getString(codeIndex);
                    if (!TextUtils.isEmpty(adminCode)) {
                        textViewEmployeeCode.setText("Mã số giảng viên: " + adminCode);
                        textViewEmployeeCode.setVisibility(View.VISIBLE);
                    } else {
                        textViewEmployeeCode.setVisibility(View.GONE);
                    }
                } else {
                    textViewEmployeeCode.setVisibility(View.GONE);
                }
            }
            
            photoPath = cursor.getString(cursor.getColumnIndexOrThrow("photo_path"));
            if (!TextUtils.isEmpty(photoPath)) {
                File file = new File(photoPath);
                if (file.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
                    imageViewAvatar.setImageBitmap(bitmap);
                } else {
                    // File doesn't exist, show initials
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("full_name"));
                    imageViewAvatar.setImageBitmap(InitialsAvatarHelper.generateAvatar(name, 192));
                }
            } else {
                // No photo, show initials
                String name = cursor.getString(cursor.getColumnIndexOrThrow("full_name"));
                imageViewAvatar.setImageBitmap(InitialsAvatarHelper.generateAvatar(name, 192));
            }
            
            cursor.close();
        }
        
        // Load trạng thái iris enrollment
        boolean hasIris = dbHelper.hasAdminEnrolledIris(currentUserEmail);
        if (hasIris) {
            textViewIrisStatus.setText("Đã ghi danh mống mắt");
            textViewIrisStatus.setTextColor(0xFF4CAF50); // Green
            buttonEnrollIris.setText("Ghi danh lại");
        } else {
            textViewIrisStatus.setText("Chưa ghi danh");
            textViewIrisStatus.setTextColor(0xFF999999); // Gray
            buttonEnrollIris.setText("Ghi danh mống mắt");
        }
    }

    private void setSpinnerValue(Spinner spinner, String value) {
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).toString().equalsIgnoreCase(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void setupListeners() {
        // Nút "Chỉnh sửa" - chuyển sang chế độ sửa
        buttonEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setEditMode();
            }
        });

        // Nút "Hủy" - quay về chế độ xem, bỏ các thay đổi
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadUserData(); // Tải lại dữ liệu gốc
                setViewMode();
            }
        });

        // Checkbox hiển thị/ẩn password (bỏ PIN logic)
        checkBoxShowPassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Hiển thị password dạng text thường
                    editTextPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    editTextConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else {
                    // Ẩn password dạng mật khẩu
                    editTextPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    editTextConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
            }
        });

        // Nút chụp ảnh từ camera
        buttonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkCameraPermission()) {
                    openCamera();
                }
            }
        });

        // Nút chọn ảnh từ thư viện
        buttonGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    openGallery();

            }
        });

        // Nút ghi danh mống mắt
        buttonEnrollIris.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enrollIrisForCurrentUser();
            }
        });

        // Nút "Lưu" - xác thực bằng Device Lock (không dùng custom PIN nữa)
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBiometricAuthentication();
            }
        });
    }

    // Chuyển sang chế độ xem: tắt các field, ẩn phần password/PIN/avatar buttons
    private void setViewMode() {
        isEditMode = false;
        
        // Hiển thị nút "Chỉnh sửa", ẩn nút "Lưu"/"Hủy"
        buttonEdit.setVisibility(View.VISIBLE);
        layoutActionButtons.setVisibility(View.GONE);
        layoutPasswordSection.setVisibility(View.GONE);
        layoutAvatarButtons.setVisibility(View.GONE);
        
        // Tắt tất cả các field (chỉ cho phép xem)
        editTextEmail.setEnabled(false); // Email luôn disabled trong chế độ xem
        editTextFullName.setEnabled(false);
        editTextDob.setEnabled(false);
        editTextPhone.setEnabled(false);
        spinnerGender.setEnabled(false);
        editTextDescription.setEnabled(false);
        
        // Xóa thông báo lỗi
        textViewError.setVisibility(View.GONE);
        
        // Xóa các field password (bỏ PIN)
        editTextPassword.setText("");
        editTextConfirmPassword.setText("");
    }

    // Chuyển sang chế độ sửa: bật các field, hiển thị phần password/PIN/avatar buttons
    private void setEditMode() {
        isEditMode = true;
        
        // Ẩn nút "Chỉnh sửa", hiển thị nút "Lưu"/"Hủy"
        buttonEdit.setVisibility(View.GONE);
        layoutActionButtons.setVisibility(View.VISIBLE);
        layoutPasswordSection.setVisibility(View.VISIBLE);
        layoutAvatarButtons.setVisibility(View.VISIBLE);
        
        // Bật tất cả các field (cho phép chỉnh sửa)
        // Cả Super Admin và Admin đều có thể đổi email của chính mình
        editTextEmail.setEnabled(true);
        
        editTextFullName.setEnabled(true);
        editTextDob.setEnabled(true);
        editTextPhone.setEnabled(true);
        spinnerGender.setEnabled(true);
        editTextDescription.setEnabled(true);
        
        // Xóa thông báo lỗi
        textViewError.setVisibility(View.GONE);
    }

    // Kiểm tra quyền truy cập camera
    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, 
                REQUEST_CAMERA_PERMISSION);
            return false;
        }
        return true;
    }

    private boolean checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_STORAGE_PERMISSION);
            return false;
        }
        return true;
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Không thể tạo file ảnh", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.iritech.irissample.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_CAMERA_CAPTURE);
            }
        }
    }

    private void openGallery() {
//        Intent pickPhoto = new Intent(Intent.ACTION_PICK,
//                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
//        startActivityForResult(pickPhoto, REQUEST_GALLERY_PICK);
        Intent pickPhoto = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pickPhoto.addCategory(Intent.CATEGORY_OPENABLE);
        pickPhoto.setType("image/*");
        pickPhoto.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(pickPhoto, REQUEST_GALLERY_PICK);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private String saveBitmapToFile(Bitmap bitmap) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + ".jpg";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File file = new File(storageDir, imageFileName);
            
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
            
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Xác thực Device Lock (vân tay / PIN) hoặc fallback về mật khẩu app
    private void showBiometricAuthentication() {
        BiometricAuthHelper.showBiometricPrompt(
            this,
            "Xác thực để lưu",
            "Sử dụng vân tay, khuôn mặt hoặc Device PIN",
            currentUserEmail,
            dbHelper,
            new BiometricAuthHelper.AuthCallback() {
                @Override
                public void onAuthSuccess() {
                    attemptSave();
                }

                @Override
                public void onAuthFailed() {
                    Toast.makeText(ProfileActivity.this, "Đã hủy xác thực", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAuthError(String errorMessage) {
                    Toast.makeText(ProfileActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    private void attemptSave() {
        textViewError.setVisibility(View.GONE);

        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();
        // Bỏ PIN fields
        String fullName = editTextFullName.getText().toString().trim();
        String dob = editTextDob.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String gender = spinnerGender.getSelectedItem().toString();
        String description = editTextDescription.getText().toString().trim();
        
        // Kiểm tra email nếu có thay đổi (cả Super Admin và Admin đều có thể đổi email của chính mình)
        if (!email.equals(currentUserEmail)) {
            // Kiểm tra email hợp lệ
            if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showError("Email không hợp lệ");
                return;
            }
            
            // Kiểm tra email mới đã tồn tại chưa
            if (dbHelper.isEmailExists(email)) {
                showError("Email này đã được sử dụng");
                return;
            }
        }

        // Validate password if changed
        if (!TextUtils.isEmpty(password) || !TextUtils.isEmpty(confirmPassword)) {
            if (password.length() < 6) {
                showError("Password phải có ít nhất 6 ký tự");
                return;
            }
            if (!password.equals(confirmPassword)) {
                showError("Password và xác nhận không khớp");
                return;
            }
            
            // Kiểm tra password mới không được trùng với password cũ
            if (validationHelper.isSameAsOldPassword(currentUserEmail, password)) {
                showError("Mật khẩu mới không được giống với mật khẩu cũ");
                return;
            }
        }

        // Bỏ validation PIN
        
        // Validate required fields
        if (TextUtils.isEmpty(fullName)) {
            showError("Vui lòng nhập họ và tên");
            return;
        }

        // Update admin info (bỏ tham số PIN)
        boolean result = dbHelper.updateAdmin(
                currentUserEmail, // Sử dụng email cũ để tìm bản ghi
                TextUtils.isEmpty(password) ? null : password,
                fullName,
                dob,
                phone,
                gender,
                photoPath,
                description
        );
        
        // Nếu đổi email, cần cập nhật email trong database
        boolean emailChanged = !email.equals(currentUserEmail);
        if (emailChanged && result) {
            result = dbHelper.updateAdminEmail(currentUserEmail, email);
        }

        if (result) {
            // Nếu là lần đầu login, đánh dấu hoàn thành sau khi save thành công
            // Quan trọng: Phải dùng email MỚI nếu đã đổi email
            if (isFirstLogin) {
                String emailToMark = emailChanged ? email : currentUserEmail;
                dbHelper.markFirstLoginComplete(emailToMark);
                Toast.makeText(this, "Đã hoàn thành thiết lập tài khoản!", Toast.LENGTH_LONG).show();
                isFirstLogin = false; // Reset flag
            } else {
                Toast.makeText(this, "Cập nhật thành công", Toast.LENGTH_SHORT).show();
            }
            
            // Nếu đổi email, cập nhật session và gửi email thông báo
            if (emailChanged) {
                // Gửi email thông báo đến email cũ
                EmailService.sendAccountUpdatedEmail(currentUserEmail, fullName, 
                    "- Email đã được thay đổi từ: " + currentUserEmail + " \u2192 " + email + "\n");
                    
                // Gửi email xác nhận đến email mới
                EmailService.sendRegistrationSuccessEmail(email, fullName, "Admin");
                
                // Cập nhật email hiện tại
                currentUserEmail = email;
            }
            
            // Clear password fields and switch to view mode
            loadUserData();
            setViewMode();
        } else {
            Toast.makeText(this, "Cập nhật thất bại", Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String message) {
        textViewError.setText(message);
        textViewError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Cần quyền truy cập camera", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Cần quyền truy cập bộ nhớ", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_IRIS_SDK_PERMISSION) {
            // Kết quả fire-and-forget – không chặn luồng người dùng
        } else if (requestCode == REQUEST_IRIS_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && pendingIrisIntent != null) {
                LicenseCheckHelper.checkLicenseAndStartCapture(this, pendingIrisIntent, REQUEST_CODE_ENROLL_IRIS);
                pendingIrisIntent = null;
            } else {
                Toast.makeText(this,
                        "Cần cấp quyền Camera và bộ nhớ để ghi danh mống mắt.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CAMERA_CAPTURE) {
                // Camera capture result
                if (currentPhotoPath != null) {
                    File file = new File(currentPhotoPath);
                    if (file.exists()) {
                        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                        imageViewAvatar.setImageBitmap(bitmap);
                        photoPath = currentPhotoPath;
                    }
                }
            } else if (requestCode == REQUEST_GALLERY_PICK) {
                // Gallery pick result
                if (data != null && data.getData() != null) {
                    Uri selectedImage = data.getData();
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage);
                        imageViewAvatar.setImageBitmap(bitmap);
                        // Save to app storage
                        photoPath = saveBitmapToFile(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (requestCode == REQUEST_CODE_ENROLL_IRIS) {
                // Iris enrollment result
                if (data != null) {
                    int resultCodeExt = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
                    String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);
                    
                    // Log chi tiết để debug
                    android.util.Log.d("ProfileActivity", "Iris enroll result: code=" + resultCodeExt + ", msg=" + resultMsg);
                    
                    if (resultCodeExt == 0) {
                        // Ghi danh thành công - cập nhật database
                        boolean success = dbHelper.updateAdminIrisEnrollment(currentUserEmail);
                        
                        if (success) {
                            textViewIrisStatus.setText("Đã ghi danh mống mắt");
                            textViewIrisStatus.setTextColor(0xFF4CAF50); // Green
                            buttonEnrollIris.setText("Ghi danh lại");
                            Toast.makeText(this, "Ghi danh mống mắt thành công!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Lỗi khi cập nhật database", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Ghi danh thất bại - Hiển thị AlertDialog để xem đầy đủ lỗi
                        new android.app.AlertDialog.Builder(this)
                            .setTitle("Lỗi ghi danh mống mắt")
                            .setMessage("Mã lỗi: " + resultCodeExt + "\n\nChi tiết: " + (resultMsg != null ? resultMsg : "Không rõ"))
                            .setPositiveButton("OK", null)
                            .show();
                    }
                }
            }
        }
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
            LicenseCheckHelper.checkLicenseAndStartCapture(this, intent, REQUEST_CODE_ENROLL_IRIS);
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

    /**
     * Ghi danh mống mắt cho user hiện tại
     */
    private void enrollIrisForCurrentUser() {
        // Convert email thành format an toàn cho SDK (SDK không chấp nhận @, .)
        String safeUserId = currentUserEmail.replace("@", "_at_").replace(".", "_");
        
        // Kiểm tra đã ghi danh chưa - nếu rồi thì unenroll trước
        boolean hasIris = dbHelper.hasAdminEnrolledIris(currentUserEmail);
        
        if (hasIris) {
            // Đã ghi danh rồi - xóa template cũ trước (unenroll)
            Intent unenrollIntent = new Intent(getApplicationContext(), CaptureActivity.class);
            unenrollIntent.setAction(Constants.ACTION_UNENROLL);
            unenrollIntent.putExtra(Constants.EXTRA_USER_ID, safeUserId);
            
            // Hiển thị dialog xác nhận
            new android.app.AlertDialog.Builder(this)
                .setTitle("Ghi danh lại mống mắt")
                .setMessage("Bạn đã ghi danh trước đó. Template cũ sẽ bị xóa và thay thế bằng template mới. Tiếp tục?")
                .setPositiveButton("Tiếp tục", (dialog, which) -> {
                    // Thực hiện enroll luôn (SDK sẽ tự động overwrite)
                    Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
                    intent.setAction(Constants.ACTION_ENROLL);
                    intent.putExtra(Constants.EXTRA_USER_ID, safeUserId);
                    // Kiểm tra quyền trước (WRITE trước, CAMERA sau)
                    checkIrisPermissionsAndStart(intent);
                })
                .setNegativeButton("Hủy", null)
                .show();
        } else {
            // Chưa ghi danh - enroll bình thường
            Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
            intent.setAction(Constants.ACTION_ENROLL);
            intent.putExtra(Constants.EXTRA_USER_ID, safeUserId);
            // Kiểm tra quyền trước (WRITE trước, CAMERA sau)
            checkIrisPermissionsAndStart(intent);
        }
    }
}
