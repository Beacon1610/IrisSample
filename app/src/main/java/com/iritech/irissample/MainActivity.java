package com.iritech.irissample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.iritech.android.widget.alertdialog.BestImageDialog;
import com.iritech.android.widget.alertdialog.SettingDialog;
import com.iritech.android.widget.alertdialog.RegisterLicenseDialog;
import com.iritech.iris.CaptureActivity;
import com.iritech.iris.Constants;
import com.iritech.iris.LicenseInfo;
import com.iritech.iris.Utilities;
import com.iritech.iris.DeveloperSettings;
import com.iritech.mqel704.GemResult;
import com.iritech.mqel704.ImageData;
import com.iritech.mqel704.ImageFormat;
import com.iritech.mqel704.ImageKind;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static final int PERMISSIONS_REQUEST_CAMERA = 2;
    private static final int PERMISSIONS_READ_PHONE_STATE = 3;
    private static final int PERMISSIONS_ACCESS_NETWORK_STATE = 4;
    private static final int REQUEST_IRIS_PERMISSIONS = 5;

    private Intent pendingIrisIntent;
    private int pendingRequestCode;

    String enrollImgPath = (Environment.getExternalStorageDirectory().toString() + File.separator
            + "iritech" + File.separator + "enroll");
    String verifyImgPath = (Environment.getExternalStorageDirectory().toString() + File.separator
            + "iritech" + File.separator + "verify");
    String mUserId;
    private int REQUEST_CODE_IDENTIFY = 1111;
    private int REQUEST_CODE_CAPTURE = 1112;
    private int REQUEST_CODE_ENROLL = 1113;
    private int REQUEST_CODE_VERIFY = 1114;
    private int REQUEST_CODE_UNENROLL = 1115;
    private EditText editUserId;
    PowerManager.WakeLock wl;

    private int mResultCode;

    private String mAction;

    private int mRequestCode;

    Button btnMonHoc;
    // THEM MOI: Nut Quan ly Admin
    Button btnQuanLyAdmin;
    // Cards for new UI
    androidx.cardview.widget.CardView cardMonHoc;
    androidx.cardview.widget.CardView cardQuanLyAdmin;
    androidx.cardview.widget.CardView cardMockCheckin; // THEM MOI: Mock Checkin for DEMO


    private DatabaseHelper dbHelper;
    
    // THEM MOI: Luu thong tin user dang dang nhap
    private String currentUserEmail;
    private String currentUserRole;
    private String currentUserName;

    // Lớp Subject để lưu cả ID và tên môn học
    private static class Subject {
        String id;
        String name;

        Subject(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // Class để lưu thông tin sinh viên và trạng thái điểm danh
    private static class StudentAttendance {
        String studentId;
        String fullName;
        String attendanceStatus;

        StudentAttendance(String studentId, String fullName, String attendanceStatus) {
            this.studentId = studentId;
            this.fullName = fullName;
            this.attendanceStatus = attendanceStatus;
        }

        @Override
        public String toString() {
            return studentId + " - " + fullName + " - " + 
                   (attendanceStatus != null ? attendanceStatus : "Chưa điểm danh");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CaptureActivity.setUSBActivity(this);
        dbHelper = new DatabaseHelper(this);
        
        // THEM MOI: Lay thong tin user tu LoginActivity
        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        currentUserRole = getIntent().getStringExtra("USER_ROLE");
        currentUserName = getIntent().getStringExtra("USER_NAME");
        
        // Kiểm tra nếu là lần đăng nhập đầu tiên
        checkFirstLoginAndShowDialog();

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            String version = pInfo.versionName;
            TextView tvVersion = findViewById(R.id.tv_version);
            tvVersion.setText("Phiên bản: " + version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // Setup cards
        cardMonHoc = findViewById(R.id.cardMonHoc);
        cardQuanLyAdmin = findViewById(R.id.cardQuanLyAdmin);
        cardMockCheckin = findViewById(R.id.cardMockCheckin); // THEM MOI: Mock Checkin card
        
        // Keep old buttons for compatibility (hidden in new layout)
        btnMonHoc = findViewById(R.id.btnMonHoc);
        btnQuanLyAdmin = findViewById(R.id.btnQuanLyAdmin);
        
        // Mon Hoc Card Click
        cardMonHoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SubjectListActivity.class);
                intent.putExtra("USER_EMAIL", currentUserEmail);
                startActivity(intent);
            }
        });
        
        // Mock Checkin Card Click (DEMO MODE)
        cardMockCheckin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MockCheckinActivity.class);
                startActivity(intent);
            }
        });
        
        // Fallback button click (if old layout is used)
        btnMonHoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SubjectListActivity.class);
                intent.putExtra("USER_EMAIL", currentUserEmail);
                startActivity(intent);
            }
        });
        
        // Quan Ly Admin Card (chi Super Admin)
        if ("SUPER_ADMIN".equals(currentUserRole)) {
            cardQuanLyAdmin.setVisibility(View.VISIBLE);
            cardQuanLyAdmin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Yêu cầu xác thực Device Lock trước khi mở danh sách Admin
                    authenticateDeviceLockForAdminManagement();
                }
            });
        }
        
        // Mock Checkin Card - Chi hien thi khi Mock Mode duoc BAT
        DeveloperSettings devSettings = new DeveloperSettings(this);
        if (devSettings.isMockModeEnabled()) {
            cardMockCheckin.setVisibility(View.VISIBLE);
        } else {
            cardMockCheckin.setVisibility(View.GONE);
        }
        
//        // WakeLock to keep screen on
//        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
//        wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "UbioXiris:MainActivity");
//        wl.acquire();
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Tải và yêu cầu quyền từ /sdcard/iritech/irissample.json
//        loadConfigs();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//        checkPermissions(this);
    } // Đóng onCreate
    
    /**
     * Kiểm tra và hiển thị dialog yêu cầu cập nhật thông tin lần đầu
     */
    private void checkFirstLoginAndShowDialog() {
        if (dbHelper.isFirstLogin(currentUserEmail)) {
            // Kiểm tra xem user đã có avatar chưa
            boolean hasAvatar = false;
            Cursor cursor = dbHelper.getAdminByEmail(currentUserEmail);
            if (cursor != null && cursor.moveToFirst()) {
                int photoIndex = cursor.getColumnIndex(DatabaseHelper.COL_ADMIN_PHOTO);
                if (photoIndex != -1) {
                    String photoPath = cursor.getString(photoIndex);
                    hasAvatar = photoPath != null && !photoPath.isEmpty();
                }
                cursor.close();
            }
            
            // Nếu Super Admin đã có avatar → tự động mark hoàn thành, không hiện dialog
            boolean hasFace = dbHelper.hasAdminFace(currentUserEmail);
            if ("SUPER_ADMIN".equals(currentUserRole) && hasAvatar && hasFace) {
                dbHelper.markFirstLoginComplete(currentUserEmail);
                return;
            }
            
            // Nếu Admin đã có avatar (do Super Admin thêm) → vẫn hiện dialog nhắc đổi mật khẩu
            // Nhưng nếu Admin quay lại mà không đổi mật khẩu, dialog vẫn hiện (đúng ý đồ)
            
            String title, message;
            
            if ("SUPER_ADMIN".equals(currentUserRole)) {
                // Super Admin lần đầu login
                title = "Chào mừng Super Admin!";
                message = "Bạn đang đăng nhập lần đầu tiên.\n\n" +
                         "Vui lòng cập nhật Avatar của bạn ngay bây giờ để sử dụng đầy đủ tính năng của hệ thống.";
            } else {
                // Admin thường lần đầu login
                title = "Chào mừng Admin!";
                message = "Bạn đang đăng nhập lần đầu tiên.\n\n" +
                         "Vui lòng thực hiện các bước sau:\n" +
                         "1. Cập nhật Avatar\n" +
                         "2. Đổi mật khẩu (password do Super Admin tạo)\n\n" +
                         "Để đảm bảo bảo mật và sử dụng đầy đủ tính năng.";
            }
            
            new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false) // Không cho dismiss bằng back button
                .setPositiveButton("Cập nhật ngay", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Chuyển đến ProfileActivity để cập nhật
                        Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                        intent.putExtra("USER_EMAIL", currentUserEmail);
                        intent.putExtra("IS_FIRST_LOGIN", true);
                        intent.putExtra("FORCE_FACE_ENROLLMENT", true);
                        intent.putExtra("RETURN_TO_MAIN_AFTER_SAVE", true);// Đánh dấu là lần đầu
                        startActivity(intent);
                    }
                })
//                .setNegativeButton("\u2715", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                    }
//                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
        }
    }
    
    /**
     * Kiểm tra và yêu cầu nhập License nếu chưa có
     * Chỉ hiện dialog nếu:
     * 1. Chưa nhập license (Settings.getLicenseEntered = false)
     * 2. Không ở Mock Mode (DeveloperSettings.isMockModeEnabled = false)
     */
    private void checkAndRequestLicense() {
        // Kiểm tra Mock Mode - nếu đang bật thì không cần license
        DeveloperSettings devSettings = new DeveloperSettings(this);
        if (devSettings.isMockModeEnabled()) {
            // Mock Mode đang bật → Không yêu cầu license
            return;
        }
        
        // Kiểm tra license đã nhập chưa
        LicenseInfo licInfo = LicenseInfo.getInstance();
        if (!licInfo.isInitialized()) {
            licInfo.initialize(this);
        }
        
        if (!licInfo.isLicenseExisted()) {
            // Chưa nhập license → Hiển thị dialog hướng dẫn
            new AlertDialog.Builder(this)
                .setTitle("Cấu hình License")
                .setMessage("Để sử dụng tính năng nhận diện mống mắt, bạn cần nhập Customer ID và License ID.\n\n" +
                           "Bạn có thể:\n" +
                           "• Nhập ngay bây giờ\n" +
                           "• Hoặc bật Mock Mode (Dev) trong Settings để test không cần license")
                .setCancelable(false) // Không cho dismiss
                .setPositiveButton("Nhập License", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Hiển thị RegisterLicenseDialog
                        LicenseCheckHelper.showLicenseDialog(MainActivity.this);
                    }
                })
                .setNegativeButton("Bật Mock Mode", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Bật Mock Mode và refresh
                        DeveloperSettings devSettings = new DeveloperSettings(MainActivity.this);
                        devSettings.setMockModeEnabled(true);
                        Toast.makeText(MainActivity.this, 
                                      "Đã bật Mock Mode - Không cần license để test", 
                                      Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    }
                })
                .setNeutralButton("Để sau", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Đóng dialog, để user tự nhập sau qua Settings
                        Toast.makeText(MainActivity.this, 
                                      "Bạn có thể nhập License sau trong Settings", 
                                      Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mResultCode == GemResult.IDDK_UVC_DEVICE_ACCESS_DENIED) {
            startCaptureActivity(mAction, mRequestCode);
        }


    }

    private void loadConfigs() {
        // JSON parser object to parse read file
        JSONParser jsonParser = new JSONParser();

        try (FileReader reader = new FileReader("/sdcard/iritech/irissample.json")) {
            // Read JSON file
            Object obj = jsonParser.parse(reader);

            JSONObject jsonObject = new JSONObject(obj.toString());
            JSONArray permissionList = jsonObject.getJSONArray("permissions");
            for (int i = 0; i < permissionList.length(); i++) {
                JSONObject permission = permissionList.getJSONObject(i);
                String permissionName = permission.getString("name");
                int permissionCode = permission.getInt("code");
                if (ContextCompat.checkSelfPermission(this, permissionName) !=
                        PackageManager.PERMISSION_GRANTED) {
                    // Permission is not granted
                    ActivityCompat.requestPermissions(this, new String[]{permissionName},
                            permissionCode);
                }
            }
        } catch (Exception var) {
            var.printStackTrace();
        }
    }

    private void startCaptureActivity(String actionType, int requestCode) {
        mUserId = editUserId.getText().toString().trim();
        if (mUserId.isEmpty() && (actionType.equals(Constants.ACTION_VERIFY)
                || actionType.equals(Constants.ACTION_ENROLL))) {
            BestImageDialog dialog = new BestImageDialog(this);
            dialog.setTitle("Thông tin!");
            dialog.show();
            dialog.setBestImages(null, null, null);
            dialog.setMessage("  Vui lòng nhập User ID  ");
            return;
        }
        Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
        intent.setAction(actionType);
        intent.putExtra(Constants.EXTRA_USER_ID, mUserId);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null) {
            int resultCodeExt = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
            mResultCode = resultCodeExt;
        }

        if (mResultCode != GemResult.IDDK_UVC_DEVICE_ACCESS_DENIED) {
            if (resultCode == RESULT_OK) {
                processResult(requestCode, data);
            } else {
                Toast.makeText(getApplicationContext(), "Hoạt động chụp ảnh thất bại", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void processResult(int requestCode, Intent data) {
        if (requestCode == REQUEST_CODE_CAPTURE) {
            processCaptureResult(data, verifyImgPath, mUserId, "best");
        } else if (requestCode == REQUEST_CODE_ENROLL) {
            processCaptureResult(data, enrollImgPath, mUserId, "best");
        } else {
            Bitmap leftBm = null;
            Bitmap rightBm = null;
            Bitmap unknownBm = null;
            int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
            String msg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);
            msg += (resultCode != GemResult.IDDK_OK ? " (" + resultCode + ")" : "");
            if (requestCode == REQUEST_CODE_VERIFY) {
                if (resultCode == 0) {
                    boolean matchingResult = data.getBooleanExtra(Constants.EXTRA_MATCHING_RESULT, false);
                    if (matchingResult) {
                        msg = "Xác minh thành công";
                    } else {
                        msg = "Xác minh thất bại! Không khớp";
                    }
                }
            } else if (requestCode == REQUEST_CODE_IDENTIFY) {
                if (resultCode == 0) {
                    int resultCount = data.getIntExtra(Constants.EXTRA_MATCHING_COUNT, 0);
                    String resultItems = data.getStringExtra(Constants.EXTRA_MATCHING_ITEMS);
                    msg = "Khớp với " + resultCount + " người dùng: " + resultItems;
                }
            }

            BestImageDialog dialog = new BestImageDialog(this);
            dialog.setTitle("Thông tin!");
            dialog.show();
            dialog.setBestImages(leftBm, rightBm, unknownBm);
            dialog.setMessage(msg);
        }
    }

    private void processCaptureResult(Intent data, String folderPath, String prefix, String filePurpose) {
        String imageExt = ".bmp";
        String templateExt = ".tpl";
        Bitmap leftBm = null;
        Bitmap rightBm = null;
        Bitmap unknownBm = null;

        int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, 0);
        String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);

        if (resultCode == 0) {
            // Capture success
            resultMsg = "Thành công! Dữ liệu đã chụp được lưu tại: \n" + folderPath;
            byte[] leftTemplateBuffer = data.getByteArrayExtra(Constants.EXTRA_LEFT_TEMPLATE);
            byte[] rightTemplateBuffer = data.getByteArrayExtra(Constants.EXTRA_RIGHT_TEMPLATE);

            Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);
            int second = c.get(Calendar.SECOND);
            prefix = prefix + String.format(
                    Locale.US, "_%02d%02d%02d_%02d%02d%02d_",
                    year, month, day, hour, minute, second
            );

            saveFile(folderPath, prefix, filePurpose + "L" + templateExt, leftTemplateBuffer);
            saveFile(folderPath, prefix, filePurpose + "R" + templateExt, rightTemplateBuffer);

            ImageData leftImage = new ImageData();
            ImageData rightImage = new ImageData();
            ImageData unknownImage = new ImageData();

            byte[] bmpLeft = null;
            byte[] bmpRight = null;
            byte[] bmpUnknown = null;

            CaptureActivity.getResultImages(leftImage, rightImage, unknownImage);
            if (leftImage.getData() != null) {
                bmpLeft = Utilities.convertRawImageToBitmap(leftImage.getData(), leftImage.getWidth(), leftImage.getHeight());
                saveFile(folderPath, prefix, filePurpose + "L" + imageExt, bmpLeft);
            }

            if (rightImage.getData() != null) {
                bmpRight = Utilities.convertRawImageToBitmap(rightImage.getData(), rightImage.getWidth(), rightImage.getHeight());
                saveFile(folderPath, prefix, filePurpose + "R" + imageExt, bmpRight);
            }

            if (unknownImage.getData() != null) {
                bmpUnknown = Utilities.convertRawImageToBitmap(unknownImage.getData(), unknownImage.getWidth(), unknownImage.getHeight());
                saveFile(folderPath, prefix, filePurpose + "U" + imageExt, bmpUnknown);
            }

            ImageData leftImageJp2 = new ImageData(ImageKind.IDDK_IKIND_K7_3_5, ImageFormat.IDDK_IFORMAT_MONO_JPEG2000, 0, 0, null);
            leftImageJp2.setCompressParams(1, 100);
            ImageData rightImageJp2 = new ImageData(ImageKind.IDDK_IKIND_K7_3_5, ImageFormat.IDDK_IFORMAT_MONO_JPEG2000, 0, 0, null);
            rightImageJp2.setCompressParams(1, 100);
            ImageData unknownImageJp2 = new ImageData(ImageKind.IDDK_IKIND_K7_3_5, ImageFormat.IDDK_IFORMAT_MONO_JPEG2000, 0, 0, null);
            unknownImageJp2.setCompressParams(1, 100);
            CaptureActivity.getResultImages(leftImageJp2, rightImageJp2, unknownImageJp2);
            imageExt = ".jp2";
            String imagesKindString = "MONO_JPEG2000";
            String imageFormatString = "IDDK_IKIND_K7_35";
            if (leftImageJp2.getData() != null) {
                saveFile(folderPath, prefix, filePurpose + "L_" + imagesKindString + imageFormatString + imageExt, leftImageJp2.getData());
            }

            if (rightImageJp2.getData() != null) {
                saveFile(folderPath, prefix, filePurpose + "R_" + imagesKindString + imageFormatString + imageExt, rightImageJp2.getData());
            }

            if (unknownImageJp2.getData() != null) {
                saveFile(folderPath, prefix, filePurpose + "U_" + imagesKindString + imageFormatString + imageExt, unknownImageJp2.getData());
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            if (bmpLeft != null) {
                leftBm = BitmapFactory.decodeByteArray(bmpLeft, 0, bmpLeft.length, options);
            }

            if (bmpRight != null) {
                rightBm = BitmapFactory.decodeByteArray(bmpRight, 0, bmpRight.length, options);
            }

            if (bmpUnknown != null) {
                unknownBm = BitmapFactory.decodeByteArray(bmpUnknown, 0, bmpUnknown.length, options);
            }

        } else {
            resultMsg += " (" + resultCode + ")";
        }

        BestImageDialog dialog = new BestImageDialog(this);
        dialog.setTitle("Thông tin!");
        dialog.show();
        dialog.setBestImages(leftBm, rightBm, unknownBm);
        dialog.setMessage(resultMsg);
    }

    private void saveFile(String filePath, String prefix, String fileName,
                          byte[] byteArray) {
        if (byteArray == null) {
            return;
        }
        String fileFullname = prefix + fileName;
        String filePathName = filePath + File.separator + fileFullname;
        File folder = new File(filePath);
        File file = new File(filePathName);

        try {
            if (!folder.exists()) {
                folder.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream bos = new FileOutputStream(file);

            bos.write(byteArray);
            bos.flush();
            bos.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Thông tin!")
                        .setMessage("Vui lòng cho phép quyền ghi vào bộ nhớ ngoài.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this,
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
        } else {
            // Permission has already been granted
            initFolder();
        }
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Thông tin!")
                        .setMessage("Vui lòng cho phép quyền sử dụng camera.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.CAMERA},
                                        PERMISSIONS_REQUEST_CAMERA);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        PERMISSIONS_REQUEST_CAMERA);
            }
            return false;
        }
        return true;
    }

    private void initFolder() {
        File enrollFolder = new File(enrollImgPath);
        if (!enrollFolder.exists()) {
            enrollFolder.mkdirs();
        }

        File verifyFolder = new File(verifyImgPath);
        if (!verifyFolder.exists()) {
            verifyFolder.mkdirs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    checkCameraPermission();
                }
                break;
            case PERMISSIONS_READ_PHONE_STATE:
                checkStorage(this);
                break;
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initFolder();
                    checkNetWork(this);
                } else {
                    checkStoragePermission();
                }
                break;
        }
    }

    public static void writeWakeLock() {
        String path = "/sys/power/wake_lock";
        BufferedWriter bw = null;    // default buffer size = 8192
        try {
            bw = new BufferedWriter(new FileWriter(path));
            bw.write("dont_sleep");
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            Log.d("writeWakeLock", "IOException : " + e);
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e) {
                    Log.d("writeWakeLock", "IOException : " + e);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        
        // Chỉ hiện menu Backup & Restore cho Super Admin
        MenuItem backupMenuItem = menu.findItem(R.id.action_backup_restore);
        if (backupMenuItem != null) {
            backupMenuItem.setVisible("SUPER_ADMIN".equals(currentUserRole));
        }
        
        return true;
    }


    @Override //ĐĂNG XUẤT
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            // Mở ProfileActivity
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("USER_EMAIL", currentUserEmail);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_backup_restore) {
            // Mở BackupRestoreActivity (chỉ Super Admin)
            Intent intent = new Intent(this, BackupRestoreActivity.class);
            intent.putExtra("USER_EMAIL", currentUserEmail);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_settings) {
            SettingDialog dialog = new SettingDialog(this);
            dialog.show();
            return true;
        } else if (id == R.id.action_logout) {
            // Xử lý đăng xuất
            Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();

            // Xoá thông tin đăng nhập (nếu dùng SharedPreferences)
            SharedPreferences preferences = getSharedPreferences("user_prefs", MODE_PRIVATE);
            preferences.edit().clear().apply();

            // Chuyển về màn hình đăng nhập
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static void checkPermissions(final Context context) {
        // Read phone state permission
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity)context,
                    Manifest.permission.READ_PHONE_STATE)) {
                new AlertDialog.Builder(context)
                        .setTitle("Thông tin!")
                        .setMessage("Cần cho phép quyền đọc trạng thái điện thoại.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions((Activity)context,
                                        new String[]{Manifest.permission.READ_PHONE_STATE},
                                        PERMISSIONS_READ_PHONE_STATE);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions((Activity)context,
                        new String[]{Manifest.permission.READ_PHONE_STATE},
                        PERMISSIONS_READ_PHONE_STATE);
            }
        } else {
            checkStorage(context);
        }
    }

    private static void checkStorage(final Context context) {
        String TAG = "";
        // Write external storage permission
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity)context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(context)
                        .setTitle("Thông tin!")
                        .setMessage("Cần cho phép quyền truy cập bộ nhớ ngoài.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions((Activity)context,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions((Activity)context,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        } else {
            checkNetWork(context);
        }
    }

    private static void checkNetWork(final Context context) {
        String TAG = "";
        // Access network state permission
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_NETWORK_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity)context,
                    Manifest.permission.ACCESS_NETWORK_STATE)) {
                new AlertDialog.Builder(context)
                        .setTitle("Thông tin!")
                        .setMessage("Cần cho phép quyền truy cập trạng thái mạng.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions((Activity)context,
                                        new String[]{Manifest.permission.ACCESS_NETWORK_STATE},
                                        PERMISSIONS_ACCESS_NETWORK_STATE);
                            }
                        })
                        .show();
            } else {
                ActivityCompat.requestPermissions((Activity)context,
                        new String[]{Manifest.permission.ACCESS_NETWORK_STATE},
                        PERMISSIONS_ACCESS_NETWORK_STATE);
            }
        }
    }

    /**
     * Xác thực Device Lock trước khi mở màn hình Quản lý Admin
     * Sử dụng BiometricAuthHelper với logic 3 tầng:
     *   Tầng 1 – Biometric (vân tay/khuôn mặt)
     *   Tầng 2 – Device PIN/Pattern/Password
     *   Tầng 3 – Fallback về mật khẩu app nếu thiết bị chưa setup Device Lock
     */
    private void authenticateDeviceLockForAdminManagement() {
        BiometricAuthHelper.showBiometricPrompt(
                this,
                "Xác thực để quản lý Admin",
                "Sử dụng vân tay, khuôn mặt hoặc mã PIN thiết bị",
                currentUserEmail,
                dbHelper,
                new BiometricAuthHelper.AuthCallback() {
                    @Override
                    public void onAuthSuccess() {
                        Toast.makeText(MainActivity.this, "Xác thực thành công!", Toast.LENGTH_SHORT).show();
                        // Xác thực thành công → Mở AdminListActivity
                        Intent intent = new Intent(MainActivity.this, AdminListActivity.class);
                        intent.putExtra("USER_EMAIL", currentUserEmail);
                        startActivity(intent);
                    }

                    @Override
                    public void onAuthFailed() {
                        Toast.makeText(MainActivity.this, "Xác thực bị hủy", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthError(String errorMessage) {
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }


    @Override
    protected void onDestroy() {
//        if (wl != null && wl.isHeld()) {
//            wl.release();
//        }
        dbHelper.close(); // Đóng DatabaseHelper
        super.onDestroy();
    }
}