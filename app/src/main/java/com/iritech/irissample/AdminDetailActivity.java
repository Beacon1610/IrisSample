package com.iritech.irissample;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog; // Hiển thị dialog xác nhận PIN, xóa Admin
import android.content.DialogInterface; 
import android.content.Intent; 
import android.content.pm.PackageManager;
import android.database.Cursor; // Đọc dữ liệu Admin từ database
import android.graphics.Bitmap; // Xử lý ảnh đại diện
import android.graphics.BitmapFactory; // Chuyển file ảnh thành Bitmap để hiển thị
import android.net.Uri;
import android.os.Bundle; // Lưu trạng thái activity
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType; 
import android.text.TextUtils; // Kiểm tra chuỗi rỗng
import android.view.View; // Xử lý sự kiện click
import android.widget.ArrayAdapter; // Adapter cho Spinner giới tính
import android.widget.Button; 
import android.widget.CheckBox; // Checkbox hiển thị/ẩn password
import android.widget.CompoundButton; // Xử lý sự kiện checkbox
import android.widget.EditText; 
import android.widget.ImageView; // Hiển thị ảnh đại diện
import android.widget.LinearLayout; 
import android.widget.Spinner; // Dropdown chọn giới tính
import android.widget.TextView; 
import android.widget.Toast; // Hiển thị thông báo ngắn

import androidx.appcompat.app.AppCompatActivity; // Base class cho activity
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File; // Kiểm tra file ảnh tồn tại
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AdminDetailActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private String currentSuperAdminEmail; // Email của Super Admin đang đăng nhập
    private String targetAdminEmail; // Email của Admin đang được xem/sửa
    private boolean isEditMode = false; // Chế độ hiện tại: xem hay sửa
    
    // Các view components
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
    private Button buttonDelete;
    private LinearLayout layoutPasswordSection;
    private LinearLayout layoutActionButtons;
    private LinearLayout layoutViewButtons; // Layout chứa nút Sửa/Xóa ở chế độ xem
    private LinearLayout layoutAvatarButtons; // Layout chứa nút Chụp ảnh/Thư viện
    private Button buttonCamera;
    private Button buttonGallery;
    
    private String photoPath; // Đường dẫn ảnh hiện tại
    private String currentPhotoPath; // Đường dẫn ảnh vừa chụp
    
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_CAMERA_CAPTURE = 102;
    private static final int REQUEST_GALLERY_PICK = 103;
    
    // Lưu thông tin cũ để so sánh khi cập nhật
    private String oldFullName;
    private String oldDob;
    private String oldPhone;
    private String oldGender;
    private String oldDescription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_detail);

        dbHelper = new DatabaseHelper(this);
        
        // Lấy email của Super Admin hiện tại và Admin được xem
        currentSuperAdminEmail = getIntent().getStringExtra("SUPER_ADMIN_EMAIL");
        targetAdminEmail = getIntent().getStringExtra("TARGET_ADMIN_EMAIL");

        if (TextUtils.isEmpty(currentSuperAdminEmail) || TextUtils.isEmpty(targetAdminEmail)) {
            Toast.makeText(this, "Lỗi: Không tìm thấy thông tin", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupGenderSpinner();
        loadAdminData();
        setViewMode(); // Mặc định: chế độ xem
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
        buttonDelete = findViewById(R.id.buttonDelete);
        layoutPasswordSection = findViewById(R.id.layoutPasswordSection);
        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        layoutViewButtons = findViewById(R.id.layoutViewButtons);
        layoutAvatarButtons = findViewById(R.id.layoutAvatarButtons);
        buttonCamera = findViewById(R.id.buttonCamera);
        buttonGallery = findViewById(R.id.buttonGallery);
    }

    private void setupGenderSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.gender_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);
    }

    private void loadAdminData() {
        Cursor cursor = dbHelper.getAdminByEmail(targetAdminEmail);
        if (cursor != null && cursor.moveToFirst()) {
            editTextEmail.setText(cursor.getString(cursor.getColumnIndexOrThrow("email")));
            editTextFullName.setText(cursor.getString(cursor.getColumnIndexOrThrow("full_name")));
            editTextDob.setText(cursor.getString(cursor.getColumnIndexOrThrow("date_of_birth")));
            editTextPhone.setText(cursor.getString(cursor.getColumnIndexOrThrow("phone")));
            
            String gender = cursor.getString(cursor.getColumnIndexOrThrow("gender"));
            setSpinnerValue(spinnerGender, gender);
            
            editTextDescription.setText(cursor.getString(cursor.getColumnIndexOrThrow("description")));
            
            // Lưu thông tin cũ để so sánh sau này
            oldFullName = cursor.getString(cursor.getColumnIndexOrThrow("full_name"));
            oldDob = cursor.getString(cursor.getColumnIndexOrThrow("date_of_birth"));
            oldPhone = cursor.getString(cursor.getColumnIndexOrThrow("phone"));
            oldGender = gender;
            oldDescription = cursor.getString(cursor.getColumnIndexOrThrow("description"));
            
            String role = cursor.getString(cursor.getColumnIndexOrThrow("role"));
            textViewRole.setText("Vai trò: " + (role.equals("SUPER_ADMIN") ? "Super Admin" : "Admin"));
            
            // Hiển thị mã số giảng viên (Admin thường mới có)
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
            
            photoPath = cursor.getString(cursor.getColumnIndexOrThrow("photo_path"));
            if (!TextUtils.isEmpty(photoPath)) {
                File file = new File(photoPath);
                if (file.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
                    imageViewAvatar.setImageBitmap(bitmap);
                } else {
                    // File doesn't exist, show initials
                    String name = cursor.getString(cursor.getColumnIndexOrThrow("full_name"));
                    imageViewAvatar.setImageBitmap(InitialsAvatarHelper.generateAvatar(name, 176));
                }
            } else {
                // No photo, show initials
                String name = cursor.getString(cursor.getColumnIndexOrThrow("full_name"));
                imageViewAvatar.setImageBitmap(InitialsAvatarHelper.generateAvatar(name, 176));
            }
            
            cursor.close();
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
                loadAdminData(); // Tải lại dữ liệu gốc
                setViewMode();
            }
        });

        // Nút "Xóa" - xóa Admin (xác thực bằng Device Lock Super Admin)
        buttonDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBiometricAuthForDelete();
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

        // Nút "Lưu" - xác thực bằng Device Lock của Super Admin
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBiometricAuthentication();
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
    }

    // Chuyển sang chế độ xem: tắt các field, ẩn phần password/PIN/avatar buttons
    private void setViewMode() {
        isEditMode = false;
        
        // Hiển thị nút "Chỉnh sửa" và "Xóa", ẩn nút "Lưu"/"Hủy"
        layoutViewButtons.setVisibility(View.VISIBLE);
        layoutActionButtons.setVisibility(View.GONE);
        layoutPasswordSection.setVisibility(View.GONE);
        layoutAvatarButtons.setVisibility(View.GONE);
        
        // Tắt tất cả các field (chỉ cho phép xem)
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
        
        // Ẩn nút "Chỉnh sửa"/"Xóa", hiển thị nút "Lưu"/"Hủy"
        layoutViewButtons.setVisibility(View.GONE);
        layoutActionButtons.setVisibility(View.VISIBLE);
        layoutPasswordSection.setVisibility(View.VISIBLE);
        layoutAvatarButtons.setVisibility(View.VISIBLE);
        
        // Bật tất cả các field (cho phép chỉnh sửa)
        editTextFullName.setEnabled(true);
        editTextDob.setEnabled(true);
        editTextPhone.setEnabled(true);
        spinnerGender.setEnabled(true);
        editTextDescription.setEnabled(true);
        
        // Xóa thông báo lỗi
        textViewError.setVisibility(View.GONE);
    }

    // Xác thực Super Admin bằng Device Lock trước khi xóa Admin
    private void showBiometricAuthForDelete() {
        BiometricAuthHelper.showBiometricPrompt(
            this,
            "Xác thực Super Admin",
            "Xác thực để xóa Admin này",
            currentSuperAdminEmail,
            dbHelper,
            new BiometricAuthHelper.AuthCallback() {
                @Override
                public void onAuthSuccess() {
                    performDelete();
                }

                @Override
                public void onAuthFailed() {
                    Toast.makeText(AdminDetailActivity.this, "Đã hủy xóa", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAuthError(String errorMessage) {
                    Toast.makeText(AdminDetailActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    // Thực hiện xóa Admin sau khi xác thực thành công
    private void performDelete() {
        // Lưu thông tin trước khi xóa để gửi email
        String adminFullName = editTextFullName.getText().toString().trim();
        String adminEmail = targetAdminEmail;
        
        // Xóa Admin khỏi database
        Cursor cursor = dbHelper.getAdminByEmail(targetAdminEmail);
        if (cursor != null && cursor.moveToFirst()) {
            String adminId = cursor.getString(cursor.getColumnIndexOrThrow("admin_id"));
            cursor.close();
            
            boolean success = dbHelper.deleteAdmin(adminId);
            if (success) {
                Toast.makeText(this, "Đã xóa Admin", Toast.LENGTH_SHORT).show();
                
                // Gửi email thông báo tài khoản đã bị xóa
                EmailService.sendAccountDeletedEmail(adminEmail, adminFullName);
                
                finish(); // Đóng activity và quay về danh sách
            } else {
                Toast.makeText(this, "Lỗi khi xóa Admin", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Xác thực Super Admin bằng Device Lock trước khi lưu
    private void showBiometricAuthentication() {
        BiometricAuthHelper.showBiometricPrompt(
            this,
            "Xác thực Super Admin",
            "Xác thực để lưu thay đổi",
            currentSuperAdminEmail,
            dbHelper,
            new BiometricAuthHelper.AuthCallback() {
                @Override
                public void onAuthSuccess() {
                    attemptSave();
                }

                @Override
                public void onAuthFailed() {
                    Toast.makeText(AdminDetailActivity.this, "Đã hủy xác thực", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAuthError(String errorMessage) {
                    Toast.makeText(AdminDetailActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    // Thực hiện lưu thông tin Admin sau khi xác thực thành công
    private void attemptSave() {
        textViewError.setVisibility(View.GONE);

        String password = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();
        // Bỏ PIN fields
        String fullName = editTextFullName.getText().toString().trim();
        String dob = editTextDob.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String gender = spinnerGender.getSelectedItem().toString();
        String description = editTextDescription.getText().toString().trim();

        // Kiểm tra password nếu có thay đổi
        if (!TextUtils.isEmpty(password) || !TextUtils.isEmpty(confirmPassword)) {
            if (password.length() < 6) {
                showError("Password phải có ít nhất 6 ký tự");
                return;
            }
            if (!password.equals(confirmPassword)) {
                showError("Password và xác nhận không khớp");
                return;
            }
        }

        // Bỏ validation PIN
        
        // Kiểm tra các trường bắt buộc
        if (TextUtils.isEmpty(fullName)) {
            showError("Vui lòng nhập họ và tên");
            return;
        }

        // Cập nhật thông tin Admin vào database (bỏ tham số PIN)
        boolean result = dbHelper.updateAdmin(
                targetAdminEmail,
                TextUtils.isEmpty(password) ? null : password,
                fullName,
                dob,
                phone,
                gender,
                photoPath,
                description
        );

        if (result) {
            Toast.makeText(this, "Cập nhật thành công", Toast.LENGTH_SHORT).show();
            
            // Tạo danh sách các thay đổi để gửi email
            StringBuilder changes = new StringBuilder();
            boolean hasChanges = false;
            
            if (!TextUtils.isEmpty(password)) {
                changes.append("- Mật khẩu đã được thay đổi\n");
                hasChanges = true;
            }
//            if (!TextUtils.isEmpty(pin)) {
//                changes.append("- Mã PIN đã được thay đổi\n");
//                hasChanges = true;
//            }
            if (!fullName.equals(oldFullName)) {
                changes.append("- Họ và tên: ").append(oldFullName).append(" → ").append(fullName).append("\n");
                hasChanges = true;
            }
            if (!dob.equals(oldDob)) {
                changes.append("- Ngày sinh: ").append(oldDob).append(" → ").append(dob).append("\n");
                hasChanges = true;
            }
            if (!phone.equals(oldPhone)) {
                changes.append("- Số điện thoại: ").append(oldPhone).append(" → ").append(phone).append("\n");
                hasChanges = true;
            }
            if (!gender.equals(oldGender)) {
                changes.append("- Giới tính: ").append(oldGender).append(" → ").append(gender).append("\n");
                hasChanges = true;
            }
            if (!description.equals(oldDescription)) {
                changes.append("- Mô tả đã được cập nhật\n");
                hasChanges = true;
            }
            
            // Gửi email thông báo nếu có thay đổi
            if (hasChanges) {
                EmailService.sendAccountUpdatedEmail(targetAdminEmail, fullName, changes.toString());
            }
            
            // Tải lại dữ liệu và chuyển về chế độ xem
            loadAdminData();
            setViewMode();
        } else {
            Toast.makeText(this, "Cập nhật thất bại", Toast.LENGTH_SHORT).show();
        }
    }

    // Hiển thị thông báo lỗi
    private void showError(String message) {
        textViewError.setText(message);
        textViewError.setVisibility(View.VISIBLE);
    }

    // Camera/Gallery methods
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
                Toast.makeText(this, "Khong the tao file anh", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Can quyen truy cap camera", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Can quyen truy cap bo nho", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CAMERA_CAPTURE) {
                if (currentPhotoPath != null) {
                    File file = new File(currentPhotoPath);
                    if (file.exists()) {
                        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                        imageViewAvatar.setImageBitmap(bitmap);
                        photoPath = currentPhotoPath;
                    }
                }
            } else if (requestCode == REQUEST_GALLERY_PICK) {
                if (data != null && data.getData() != null) {
                    Uri selectedImage = data.getData();
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage);
                        imageViewAvatar.setImageBitmap(bitmap);
                        photoPath = saveBitmapToFile(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Khong the tai anh", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }
}
