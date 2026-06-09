package com.iritech.irissample;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "attendance.db";
    private static final int DATABASE_VERSION = 20; // Version 19: Xóa eye_photo_path, đơn giản hóa schema

    // Bảng ADMIN
    public static final String TABLE_ADMIN = "admin";
    public static final String COL_ADMIN_ID = "admin_id";
    public static final String COL_ADMIN_EMAIL = "email";
    public static final String COL_ADMIN_PASSWORD = "password";
    public static final String COL_ADMIN_FULL_NAME = "full_name";
    public static final String COL_ADMIN_DOB = "date_of_birth";
    public static final String COL_ADMIN_PHONE = "phone";
    public static final String COL_ADMIN_GENDER = "gender";
    public static final String COL_ADMIN_PHOTO = "photo_path";
    public static final String COL_ADMIN_DESC = "description";
    public static final String COL_ADMIN_ROLE = "role";
    public static final String COL_ADMIN_CODE = "admin_code"; // Mã số giảng viên (chỉ Admin thường)
    public static final String COL_ADMIN_RESET_TOKEN = "reset_token"; // Token reset password
    public static final String COL_ADMIN_EXPIRE_AT = "expire_at"; // Thời gian hết hạn token (milliseconds)
    public static final String COL_ADMIN_IS_FIRST_LOGIN = "is_first_login"; // Cờ đánh dấu lần đăng nhập đầu (cần cập nhật avatar/password)
    public static final String COL_ADMIN_HAS_IRIS = "has_iris"; // Đánh dấu đã ghi danh mống mắt chưa (0 = chưa, 1 = rồi)

    // Bảng SUBJECTS
    public static final String TABLE_SUBJECTS = "subjects";
    public static final String COL_SUBJECT_ID = "subject_id";
    public static final String COL_SUBJECT_NAME = "subject_name";
    public static final String COL_TIME_SLOT = "time_slot";
    public static final String COL_SUBJECT_CREATED_BY = "created_by";
    public static final String COL_SUBJECT_INSTRUCTOR_ID = "instructor_id";
    public static final String COL_SUBJECT_STATUS = "subject_status";
    public static final String COL_SUBJECT_CREATED_AT = "created_at";

    public static final String STATUS_UNASSIGNED = "UNASSIGNED";
    public static final String STATUS_ASSIGNED = "ASSIGNED";

    // Bảng STUDENTS
    public static final String TABLE_STUDENTS = "students";
    public static final String COL_STUDENT_ID = "student_id";
    public static final String COL_FULL_NAME = "full_name";
    public static final String COL_PHONE = "phone";
    public static final String COL_EMAIL = "email";

    public  static  final String COL_PASSWORD = "password" ;

    public static final String COL_FACE_VECTOR = "face_vector";
    public static final String COL_PHOTO_PATH = "photo_path";



    // Bảng ENROLLMENTS
    public static final String TABLE_ENROLLMENTS = "enrollments";
    public static final String COL_ENROLLMENT_STATUS = "enrollment_status";
    
    // Bảng CHECKIN_HISTORY (Đơn giản hóa - chỉ lưu thông tin cơ bản)
    public static final String TABLE_CHECKIN_HISTORY = "checkin_history";
    public static final String COL_CHECKIN_ID = "checkin_id";
    public static final String COL_CHECKIN_TIME = "checkin_time";
    public static final String COL_CHECKIN_DATE = "checkin_date"; // Ngày điểm danh để biết điểm danh ngày nào
    
    // Bảng EMAIL_RECIPIENTS - Lưu danh sách email nhận báo cáo điểm danh (per-subject)
    public static final String TABLE_EMAIL_RECIPIENTS = "email_recipients";
    public static final String COL_EMAIL_ID = "email_id";
    public static final String COL_EMAIL_ADDRESS = "email_address";
    public static final String COL_RECIPIENT_NAME = "recipient_name";
    public static final String COL_ADDED_DATE = "added_date";
    // COL_SUBJECT_ID đã định nghĩa ở trên

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // TẠO BẢNG ADMIN - Có phân quyền và thông tin đầy đủ
        String CREATE_ADMIN_TABLE = "CREATE TABLE " + TABLE_ADMIN + " (" +
                COL_ADMIN_ID + " TEXT PRIMARY KEY, " +
                COL_ADMIN_EMAIL + " TEXT UNIQUE NOT NULL, " +
                COL_ADMIN_PASSWORD + " TEXT NOT NULL, " +
                COL_ADMIN_FULL_NAME + " TEXT NOT NULL, " +
                COL_ADMIN_DOB + " TEXT, " +
                COL_ADMIN_PHONE + " TEXT, " +
                COL_ADMIN_GENDER + " TEXT, " +
                COL_ADMIN_PHOTO + " TEXT, " +
                COL_ADMIN_DESC + " TEXT, " +
                COL_ADMIN_ROLE + " TEXT NOT NULL DEFAULT 'ADMIN', " +
                COL_ADMIN_CODE + " TEXT, " +
                COL_ADMIN_RESET_TOKEN + " TEXT, " +
                COL_ADMIN_EXPIRE_AT + " INTEGER, " +
                COL_ADMIN_IS_FIRST_LOGIN + " INTEGER DEFAULT 1, " + // 1 = first login, 0 = đã cập nhật
                COL_ADMIN_HAS_IRIS + " INTEGER DEFAULT 0)"; // 0 = chưa ghi danh, 1 = đã ghi danh

        // TẠO BẢNG SUBJECTS - Có instructor_id, subject_status, created_at
        String CREATE_SUBJECTS_TABLE = "CREATE TABLE " + TABLE_SUBJECTS + " (" +
                COL_SUBJECT_ID + " TEXT PRIMARY KEY, " +
                COL_SUBJECT_NAME + " TEXT NOT NULL, " +
                COL_TIME_SLOT + " TEXT, " +
                COL_SUBJECT_CREATED_BY + " TEXT, " +
                COL_SUBJECT_INSTRUCTOR_ID + " TEXT, " +
                COL_SUBJECT_STATUS + " TEXT NOT NULL DEFAULT 'UNASSIGNED', " +
                COL_SUBJECT_CREATED_AT + " TEXT, " +
                "FOREIGN KEY(" + COL_SUBJECT_CREATED_BY + ") REFERENCES " +
                        TABLE_ADMIN + "(" + COL_ADMIN_ID + ") ON DELETE SET NULL, " +
                "FOREIGN KEY(" + COL_SUBJECT_INSTRUCTOR_ID + ") REFERENCES " +
                        TABLE_ADMIN + "(" + COL_ADMIN_ID + ") ON DELETE SET NULL);";  

        // TẠO BẢNG STUDENTS 
        String CREATE_STUDENTS_TABLE = "CREATE TABLE " + TABLE_STUDENTS + " (" +
                COL_STUDENT_ID + " TEXT PRIMARY KEY, " +
                COL_FULL_NAME + " TEXT, " +
                COL_EMAIL + " TEXT, " +
                COL_PHONE + " TEXT, " +
                COL_PASSWORD + " TEXT," +
                COL_FACE_VECTOR + " TEXT," +
                COL_PHOTO_PATH + " TEXT);"; // Giản lược, chỉ giữ thông tin cơ bản



        // TẠO BẢNG ENROLLMENTS
        String CREATE_ENROLLMENTS_TABLE = "CREATE TABLE " + TABLE_ENROLLMENTS + " (" +
                COL_STUDENT_ID + " TEXT, " +
                COL_SUBJECT_ID + " TEXT, " +
                COL_ENROLLMENT_STATUS + " TEXT, " +
                "PRIMARY KEY (" + COL_STUDENT_ID + ", " + COL_SUBJECT_ID + "), " +
                "FOREIGN KEY(" + COL_STUDENT_ID + ") REFERENCES " + TABLE_STUDENTS + "(" + COL_STUDENT_ID + "), " +
                "FOREIGN KEY(" + COL_SUBJECT_ID + ") REFERENCES " + TABLE_SUBJECTS + "(" + COL_SUBJECT_ID + "));";
        
        // TẠO BẢNG CHECKIN_HISTORY - Đơn giản hóa, chỉ lưu thông tin cơ bản
        String CREATE_CHECKIN_HISTORY_TABLE = "CREATE TABLE " + TABLE_CHECKIN_HISTORY + " (" +
                COL_CHECKIN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_STUDENT_ID + " TEXT, " +
                COL_SUBJECT_ID + " TEXT, " +
                COL_CHECKIN_TIME + " TEXT, " +
                COL_CHECKIN_DATE + " TEXT, " +
                "FOREIGN KEY(" + COL_STUDENT_ID + ") REFERENCES " + TABLE_STUDENTS + "(" + COL_STUDENT_ID + "), " +
                "FOREIGN KEY(" + COL_SUBJECT_ID + ") REFERENCES " + TABLE_SUBJECTS + "(" + COL_SUBJECT_ID + "));";
        
        // TẠO BẢNG EMAIL_RECIPIENTS - Danh sách email nhận báo cáo điểm danh (per-subject)
        String CREATE_EMAIL_RECIPIENTS_TABLE = "CREATE TABLE " + TABLE_EMAIL_RECIPIENTS + " (" +
                COL_EMAIL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SUBJECT_ID + " TEXT NOT NULL, " +
                COL_EMAIL_ADDRESS + " TEXT NOT NULL, " +
                COL_RECIPIENT_NAME + " TEXT, " +
                COL_ADDED_DATE + " TEXT, " +
                "UNIQUE(" + COL_SUBJECT_ID + ", " + COL_EMAIL_ADDRESS + "), " +
                "FOREIGN KEY(" + COL_SUBJECT_ID + ") REFERENCES " + TABLE_SUBJECTS + "(" + COL_SUBJECT_ID + ") ON DELETE CASCADE);";

        db.execSQL(CREATE_ADMIN_TABLE);
        db.execSQL(CREATE_SUBJECTS_TABLE);
        db.execSQL(CREATE_STUDENTS_TABLE);
        db.execSQL(CREATE_ENROLLMENTS_TABLE);
        db.execSQL(CREATE_CHECKIN_HISTORY_TABLE);
        db.execSQL(CREATE_EMAIL_RECIPIENTS_TABLE);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys = ON;");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Giai đoạn dev: drop tất cả bảng rồi tạo lại
        // Drop bảng con trước, bảng cha sau (tránh lỗi foreign key)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EMAIL_RECIPIENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHECKIN_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ENROLLMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STUDENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SUBJECTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ADMIN);
        onCreate(db);
    }

    /**
     * Hash password using SHA-256
     * @param password Plain text password
     * @return Hashed password in hexadecimal format
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(password.getBytes());
            
            // Convert byte array to hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tạo mã số giảng viên tự động
     * Format: GV + năm hiện tại + 5 số ngẫu nhiên
     * Ví dụ: GV202512345
     */
    private String generateAdminCode() {
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        int randomNum = (int) (Math.random() * 90000) + 10000; // 5 số ngẫu nhiên từ 10000-99999
        return "GV" + currentYear + randomNum;
    }

    //Kiểm tra đã có Super Admin trong hệ thống chưa
    public boolean isSuperAdminExists() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + TABLE_ADMIN + 
                      " WHERE " + COL_ADMIN_ROLE + " = 'SUPER_ADMIN'";
        
        Cursor cursor = db.rawQuery(query, null);
        boolean exists = false;
        
        if (cursor.moveToFirst()) {
            exists = cursor.getInt(0) > 0;
        }
        
        cursor.close();
        return exists;
    }

    //Kiểm tra email đã tồn tại trong hệ thống chưa
     
    public boolean isEmailExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        
        // Kiểm tra trong bảng admin
        String queryAdmin = "SELECT COUNT(*) FROM " + TABLE_ADMIN + 
                           " WHERE " + COL_ADMIN_EMAIL + " = ?";
        Cursor cursorAdmin = db.rawQuery(queryAdmin, new String[]{email});
        
        boolean existsInAdmin = false;
        if (cursorAdmin.moveToFirst()) {
            existsInAdmin = cursorAdmin.getInt(0) > 0;
        }
        cursorAdmin.close();
        
        if (existsInAdmin) return true;
        
        // Kiểm tra trong bảng students
        String queryStudent = "SELECT COUNT(*) FROM " + TABLE_STUDENTS + 
                             " WHERE " + COL_EMAIL + " = ?";
        Cursor cursorStudent = db.rawQuery(queryStudent, new String[]{email});
        
        boolean existsInStudent = false;
        if (cursorStudent.moveToFirst()) {
            existsInStudent = cursorStudent.getInt(0) > 0;
        }
        cursorStudent.close();
        
        return existsInStudent;
    }

   //Xác thực đăng nhập (email + password)
    public Cursor authenticateUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        
        // Hash password before comparison
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) {
            return null; // Hashing failed
        }
        
        // Tìm trong bảng admin
        String queryAdmin = "SELECT * FROM " + TABLE_ADMIN + 
                           " WHERE " + COL_ADMIN_EMAIL + " = ? AND " +
                           COL_ADMIN_PASSWORD + " = ?";
        
        Cursor cursorAdmin = db.rawQuery(queryAdmin, new String[]{email, hashedPassword});
        
        if (cursorAdmin.getCount() > 0) {
            return cursorAdmin;
        }
        cursorAdmin.close();
        
        return null;
    }

    // Xác thực Password của Admin (dùng khi cần xác thực device biometric)
    public boolean verifyAdminPassword(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();

        // Debug log
        android.util.Log.d("DatabaseHelper", "verifyAdminPassword called with email: " + email);

        // Hash password before comparison
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) {
            android.util.Log.e("DatabaseHelper", "Password hashing failed");
            return false; // Hashing failed
        }

        android.util.Log.d("DatabaseHelper", "Hashed password (first 10 chars): " + hashedPassword.substring(0, Math.min(10, hashedPassword.length())));

        // Kiểm tra password
        String queryPassword = "SELECT COUNT(*) FROM " + TABLE_ADMIN +
                              " WHERE " + COL_ADMIN_EMAIL + " = ? AND " +
                              COL_ADMIN_PASSWORD + " = ?";
        Cursor cursor = db.rawQuery(queryPassword, new String[]{email, hashedPassword});

        boolean verified = false;
        if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
            verified = true;
        }
        cursor.close();

        android.util.Log.d("DatabaseHelper", "Password verification result: " + verified);
        return verified;
    }

    // Thêm Super Admin (chỉ dùng lần đầu khởi tạo hệ thống)
    public boolean insertSuperAdmin(String email, String password, 
                                    String fullName, String dob, String phone,
                                    String gender, String photoPath, String description) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        // Hash password before storing
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) {
            return false; // Hashing failed
        }
        
        String adminId = "SADM_" + System.currentTimeMillis();
        
        values.put(COL_ADMIN_ID, adminId);
        values.put(COL_ADMIN_EMAIL, email);
        values.put(COL_ADMIN_PASSWORD, hashedPassword);
        values.put(COL_ADMIN_FULL_NAME, fullName);
        values.put(COL_ADMIN_DOB, dob);
        values.put(COL_ADMIN_PHONE, phone);
        values.put(COL_ADMIN_GENDER, gender);
        values.put(COL_ADMIN_PHOTO, photoPath);
        values.put(COL_ADMIN_DESC, description);
        values.put(COL_ADMIN_ROLE, "SUPER_ADMIN");
        // Super Admin không có mã số (admin_code = null)
        
        long result = db.insert(TABLE_ADMIN, null, values);
        return result != -1;
    }

    // Thêm Admin (Super Admin tạo cho giảng viên)
    public boolean insertAdmin(String email, String password,
                              String fullName, String dob, String phone,
                              String gender, String photoPath, String description,
                              String adminCode) { // Nhận mã số từ Super Admin nhập
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        // Hash password before storing
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) {
            return false; // Hashing failed
        }
        
        String adminId = "ADM_" + System.currentTimeMillis();
        // String adminCode = generateAdminCode(); Không tự động tạo nữa, nhận từ Super Admin
        
        values.put(COL_ADMIN_ID, adminId);
        values.put(COL_ADMIN_EMAIL, email);
        values.put(COL_ADMIN_PASSWORD, hashedPassword);
        values.put(COL_ADMIN_FULL_NAME, fullName);
        values.put(COL_ADMIN_DOB, dob);
        values.put(COL_ADMIN_PHONE, phone);
        values.put(COL_ADMIN_GENDER, gender);
        values.put(COL_ADMIN_PHOTO, photoPath);
        values.put(COL_ADMIN_DESC, description);
        values.put(COL_ADMIN_ROLE, "ADMIN");
        values.put(COL_ADMIN_CODE, adminCode); // Lưu mã số do Super Admin nhập
        
        long result = db.insert(TABLE_ADMIN, null, values);
        return result != -1;
    }

    // Xóa Admin (chỉ Super Admin có quyền) – có xử lý môn liên quan
    public boolean deleteAdmin(String adminId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            // Bước 1: Cập nhật tất cả môn của admin này về UNASSIGNED
            ContentValues subjectValues = new ContentValues();
            subjectValues.putNull(COL_SUBJECT_INSTRUCTOR_ID);
            subjectValues.put(COL_SUBJECT_STATUS, STATUS_UNASSIGNED);
            db.update(TABLE_SUBJECTS, subjectValues,
                    COL_SUBJECT_INSTRUCTOR_ID + " = ?", new String[]{adminId});

            // Bước 2: Xóa admin
            int rowsDeleted = db.delete(TABLE_ADMIN, COL_ADMIN_ID + " = ?", new String[]{adminId});

            db.setTransactionSuccessful();
            return rowsDeleted > 0;
        } finally {
            db.endTransaction();
        }
    }

    // Lấy danh sách Admin (không bao gồm Super Admin)
    public Cursor getAllAdmins() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_ADMIN + 
                      " WHERE " + COL_ADMIN_ROLE + " = 'ADMIN'";
        return db.rawQuery(query, null);
    }

    // Lấy tất cả môn học với tên giảng viên (cho Super Admin)
    public Cursor getAllSubjectsWithInstructor() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query =
            "SELECT s.*, a." + COL_ADMIN_FULL_NAME + " AS instructor_name " +
            "FROM " + TABLE_SUBJECTS + " s " +
            "LEFT JOIN " + TABLE_ADMIN + " a " +
            "ON s." + COL_SUBJECT_INSTRUCTOR_ID + " = a." + COL_ADMIN_ID + " " +
            "ORDER BY s." + COL_SUBJECT_STATUS + " DESC, s." + COL_SUBJECT_NAME + " ASC";
        return db.rawQuery(query, null);
    }

    // Lấy môn học theo giảng viên (cho Admin)
    public Cursor getSubjectsByInstructor(String adminId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query =
            "SELECT s.*, a." + COL_ADMIN_FULL_NAME + " AS instructor_name " +
            "FROM " + TABLE_SUBJECTS + " s " +
            "LEFT JOIN " + TABLE_ADMIN + " a " +
            "ON s." + COL_SUBJECT_INSTRUCTOR_ID + " = a." + COL_ADMIN_ID + " " +
            "WHERE s." + COL_SUBJECT_INSTRUCTOR_ID + " = ? " +
            "ORDER BY s." + COL_SUBJECT_NAME + " ASC";
        return db.rawQuery(query, new String[]{adminId});
    }    
    // Lay thong tin Admin theo email
    public Cursor getAdminByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_ADMIN + 
                      " WHERE " + COL_ADMIN_EMAIL + " = ?";
        return db.rawQuery(query, new String[]{email});
    }
    
    // Cap nhat thong tin Admin
    public boolean updateAdmin(String email, String password,
                              String fullName, String dob, String phone,
                              String gender, String photoPath, String description) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        if (password != null && !password.isEmpty()) {
            // Hash password before storing
            String hashedPassword = hashPassword(password);
            if (hashedPassword == null) {
                return false; // Hashing failed
            }
            values.put(COL_ADMIN_PASSWORD, hashedPassword);
        }
        values.put(COL_ADMIN_FULL_NAME, fullName);
        values.put(COL_ADMIN_DOB, dob);
        values.put(COL_ADMIN_PHONE, phone);
        values.put(COL_ADMIN_GENDER, gender);
        if (photoPath != null) {
            values.put(COL_ADMIN_PHOTO, photoPath);
        }
        values.put(COL_ADMIN_DESC, description);
        
        int rowsAffected = db.update(TABLE_ADMIN, values,
                                    COL_ADMIN_EMAIL + " = ?",
                                    new String[]{email});
        
        return rowsAffected > 0;
    }
    
    // Cập nhật email của Admin (chỉ Admin thường được phép đổi)
    public boolean updateAdminEmail(String oldEmail, String newEmail) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ADMIN_EMAIL, newEmail);
        
        int rowsAffected = db.update(TABLE_ADMIN, values,
                                    COL_ADMIN_EMAIL + " = ?",
                                    new String[]{oldEmail});
        
        return rowsAffected > 0;
    }
    
    // ============ FIRST LOGIN METHODS ============
    
    /**
     * Kiểm tra xem Admin có phải lần đăng nhập đầu tiên không
     * @param email Email của admin
     * @return true nếu là lần đầu, false nếu không
     */
    public boolean isFirstLogin(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT " + COL_ADMIN_IS_FIRST_LOGIN +
            " FROM " + TABLE_ADMIN +
            " WHERE " + COL_ADMIN_EMAIL + " = ?",
            new String[]{email}
        );
        
        boolean isFirst = true; // Mặc định là lần đầu
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COL_ADMIN_IS_FIRST_LOGIN);
            if (columnIndex != -1) {
                isFirst = cursor.getInt(columnIndex) == 1;
            }
            cursor.close();
        }
        
        return isFirst;
    }
    
    /**
     * Đánh dấu Admin đã hoàn thành cập nhật thông tin lần đầu
     * @param email Email của admin
     * @return true nếu cập nhật thành công
     */
    public boolean markFirstLoginComplete(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ADMIN_IS_FIRST_LOGIN, 0); // 0 = đã hoàn thành
        
        int rowsAffected = db.update(TABLE_ADMIN, values,
                                    COL_ADMIN_EMAIL + " = ?",
                                    new String[]{email});
        
        return rowsAffected > 0;
    }

    // ============ IRIS ENROLLMENT METHODS ============
    
    /**
     * Lấy admin_id từ email (dùng làm USER_ID cho iris enrollment)
     * @param email Email của admin
     * @return admin_id hoặc null nếu không tìm thấy
     */
    public String getAdminIdByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT " + COL_ADMIN_ID +
            " FROM " + TABLE_ADMIN +
            " WHERE " + COL_ADMIN_EMAIL + " = ?",
            new String[]{email}
        );
        
        String adminId = null;
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COL_ADMIN_ID);
            if (columnIndex != -1) {
                adminId = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        
        return adminId;
    }
    
    /**
     * Lấy email từ admin_id (reverse lookup cho identify)
     * @param adminId ID của admin
     * @return email hoặc null nếu không tìm thấy
     */
    public String getEmailByAdminId(String adminId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT " + COL_ADMIN_EMAIL +
            " FROM " + TABLE_ADMIN +
            " WHERE " + COL_ADMIN_ID + " = ?",
            new String[]{adminId}
        );
        
        String email = null;
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COL_ADMIN_EMAIL);
            if (columnIndex != -1) {
                email = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        
        return email;
    }
    
    /**
     * Cập nhật trạng thái đã ghi danh mống mắt cho Admin
     * @param email Email của admin
     * @return true nếu cập nhật thành công
     */
    public boolean updateAdminIrisEnrollment(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ADMIN_HAS_IRIS, 1); // 1 = đã ghi danh
        
        int rowsAffected = db.update(TABLE_ADMIN, values,
                                    COL_ADMIN_EMAIL + " = ?",
                                    new String[]{email});
        
        return rowsAffected > 0;
    }
    
    /**
     * Kiểm tra Admin đã ghi danh mống mắt chưa
     * @param email Email của admin
     * @return true nếu đã ghi danh
     */
    public boolean hasAdminEnrolledIris(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT " + COL_ADMIN_HAS_IRIS +
            " FROM " + TABLE_ADMIN +
            " WHERE " + COL_ADMIN_EMAIL + " = ?",
            new String[]{email}
        );
        
        boolean hasIris = false;
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COL_ADMIN_HAS_IRIS);
            if (columnIndex != -1) {
                hasIris = cursor.getInt(columnIndex) == 1;
            }
            cursor.close();
        }
        
        return hasIris;
    }
    
    /**
     * Kiểm tra có ít nhất 1 admin đã ghi danh mống mắt chưa
     * Dùng để enable/disable tính năng reset password bằng mống mắt
     * @return true nếu có ít nhất 1 admin đã ghi danh
     */
    public boolean hasAnyAdminWithIris() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT COUNT(*) FROM " + TABLE_ADMIN +
            " WHERE " + COL_ADMIN_HAS_IRIS + " = 1",
            null
        );
        
        boolean hasAny = false;
        if (cursor != null && cursor.moveToFirst()) {
            hasAny = cursor.getInt(0) > 0;
            cursor.close();
        }
        
        return hasAny;
    }

    // ============ SUBJECT MANAGEMENT METHODS (Version 18) ============

    /**
     * Lấy role của Admin theo email
     * @return "SUPER_ADMIN", "ADMIN", hoặc null nếu không tìm thấy
     */
    public String getAdminRole(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT " + COL_ADMIN_ROLE + " FROM " + TABLE_ADMIN +
            " WHERE " + COL_ADMIN_EMAIL + " = ?",
            new String[]{email}
        );
        String role = null;
        if (cursor != null && cursor.moveToFirst()) {
            int idx = cursor.getColumnIndex(COL_ADMIN_ROLE);
            if (idx != -1) role = cursor.getString(idx);
            cursor.close();
        }
        return role;
    }

    /**
     * Tạo môn học mới (dùng chung cho cả Super Admin và Admin)
     * @param instructorId ID giảng viên phụ trách (nullable cho Super Admin)
     * @return row ID nếu thành công, -1 nếu thất bại
     */
    public long createSubject(String subjectId, String subjectName, String timeSlot,
                              String createdById, String instructorId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SUBJECT_ID, subjectId);
        values.put(COL_SUBJECT_NAME, subjectName);
        values.put(COL_TIME_SLOT, timeSlot);
        values.put(COL_SUBJECT_CREATED_BY, createdById);
        if (instructorId != null) {
            values.put(COL_SUBJECT_INSTRUCTOR_ID, instructorId);
            values.put(COL_SUBJECT_STATUS, STATUS_ASSIGNED);
        } else {
            values.putNull(COL_SUBJECT_INSTRUCTOR_ID);
            values.put(COL_SUBJECT_STATUS, STATUS_UNASSIGNED);
        }
        values.put(COL_SUBJECT_CREATED_AT,
            new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date()));
        return db.insert(TABLE_SUBJECTS, null, values);
    }

    /**
     * Gán hoặc thay đổi giảng viên cho môn học
     * @param instructorId null để thu hồi (UNASSIGNED)
     */
    public boolean assignInstructor(String subjectId, String instructorId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        if (instructorId != null) {
            values.put(COL_SUBJECT_INSTRUCTOR_ID, instructorId);
            values.put(COL_SUBJECT_STATUS, STATUS_ASSIGNED);
        } else {
            values.putNull(COL_SUBJECT_INSTRUCTOR_ID);
            values.put(COL_SUBJECT_STATUS, STATUS_UNASSIGNED);
        }
        int rows = db.update(TABLE_SUBJECTS, values,
                COL_SUBJECT_ID + " = ?", new String[]{subjectId});
        return rows > 0;
    }

    /**
     * Kiểm tra môn học có thuộc quyền Admin không (instructor_id = adminId)
     */
    public boolean isSubjectOwnedByAdmin(String subjectId, String adminId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT 1 FROM " + TABLE_SUBJECTS +
            " WHERE " + COL_SUBJECT_ID + " = ? AND " + COL_SUBJECT_INSTRUCTOR_ID + " = ?",
            new String[]{subjectId, adminId});
        boolean owned = cursor.moveToFirst();
        cursor.close();
        return owned;
    }

    /**
     * Lấy thông tin môn học theo ID (cho edit)
     */
    public Cursor getSubjectById(String subjectId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query =
            "SELECT s.*, a." + COL_ADMIN_FULL_NAME + " AS instructor_name " +
            "FROM " + TABLE_SUBJECTS + " s " +
            "LEFT JOIN " + TABLE_ADMIN + " a " +
            "ON s." + COL_SUBJECT_INSTRUCTOR_ID + " = a." + COL_ADMIN_ID + " " +
            "WHERE s." + COL_SUBJECT_ID + " = ?";
        return db.rawQuery(query, new String[]{subjectId});
    }

    /**
     * Cập nhật môn học (Super Admin – có thể đổi instructor)
     */
    public boolean updateSubjectBySuperAdmin(String subjectId, String subjectName,
                                              String timeSlot, String instructorId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SUBJECT_NAME, subjectName);
        values.put(COL_TIME_SLOT, timeSlot);
        if (instructorId != null) {
            values.put(COL_SUBJECT_INSTRUCTOR_ID, instructorId);
            values.put(COL_SUBJECT_STATUS, STATUS_ASSIGNED);
        } else {
            values.putNull(COL_SUBJECT_INSTRUCTOR_ID);
            values.put(COL_SUBJECT_STATUS, STATUS_UNASSIGNED);
        }
        int rows = db.update(TABLE_SUBJECTS, values,
                COL_SUBJECT_ID + " = ?", new String[]{subjectId});
        return rows > 0;
    }

    /**
     * Cập nhật môn học (Admin – chỉ sửa tên và ca học, không đổi instructor)
     */
    public boolean updateSubjectByAdmin(String subjectId, String adminId,
                                         String subjectName, String timeSlot) {
        // Kiểm tra quyền sở hữu trước
        if (!isSubjectOwnedByAdmin(subjectId, adminId)) {
            return false;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SUBJECT_NAME, subjectName);
        values.put(COL_TIME_SLOT, timeSlot);
        int rows = db.update(TABLE_SUBJECTS, values,
                COL_SUBJECT_ID + " = ? AND " + COL_SUBJECT_INSTRUCTOR_ID + " = ?",
                new String[]{subjectId, adminId});
        return rows > 0;
    }

    /**
     * Kiểm tra admin_id có tồn tại không (dùng cho CSV import)
     */
    public boolean isAdminIdExists(String adminId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
            "SELECT 1 FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_ID + " = ?",
            new String[]{adminId});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    public String getStudentPassword(String studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String password = null;
        Cursor cursor = db.query(TABLE_STUDENTS,
                new String[]{COL_PASSWORD},
                COL_STUDENT_ID + "=?",
                new String[]{studentId}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            password = cursor.getString(0);
            cursor.close();
        }
        return password;
    }

    // ============ CÁC HÀM HỖ TRỢ FACE ID & ĐIỂM DANH ============

    /**
     * Hàm 1: Lưu mảng vector khuôn mặt (đã chuyển thành chuỗi JSON) vào database
     * Gọi hàm này sau khi AI quét xong mặt lúc Thêm sinh viên mới.
     */
    public Cursor getStudentFaceVectorById(
            String studentId,
            String subjectId
    ) {
        SQLiteDatabase db = getReadableDatabase();

        String query =
                "SELECT s." + COL_STUDENT_ID + ", " +
                        "s." + COL_FULL_NAME + ", " +
                        "s." + COL_FACE_VECTOR +
                        " FROM " + TABLE_STUDENTS + " s" +
                        " INNER JOIN " + TABLE_ENROLLMENTS + " e" +
                        " ON s." + COL_STUDENT_ID +
                        " = e." + COL_STUDENT_ID +
                        " WHERE s." + COL_STUDENT_ID + " = ?" +
                        " AND e." + COL_SUBJECT_ID + " = ?" +
                        " AND s." + COL_FACE_VECTOR + " IS NOT NULL" +
                        " AND s." + COL_FACE_VECTOR + " != ''" +
                        " LIMIT 1";

        return db.rawQuery(
                query,
                new String[]{studentId, subjectId}
        );
    }
    public Cursor getStudentFaceVectorsBySubject(String subjectId) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query =
                "SELECT s." + COL_STUDENT_ID + ", s." + COL_FULL_NAME + ", s." + COL_FACE_VECTOR +
                        " FROM " + TABLE_STUDENTS + " s" +
                        " INNER JOIN " + TABLE_ENROLLMENTS + " e" +
                        " ON s." + COL_STUDENT_ID + " = e." + COL_STUDENT_ID +
                        " WHERE e." + COL_SUBJECT_ID + " = ?" +
                        " AND s." + COL_FACE_VECTOR + " IS NOT NULL" +
                        " AND s." + COL_FACE_VECTOR + " != ''";

        return db.rawQuery(query, new String[]{subjectId});
    }
    public String getStudentNameById(String studentId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT " + COL_FULL_NAME +
                        " FROM " + TABLE_STUDENTS +
                        " WHERE " + COL_STUDENT_ID + " = ?",
                new String[]{studentId}
        );

        String name = studentId;

        if (cursor.moveToFirst()) {
            name = cursor.getString(0);
        }

        cursor.close();
        return name;
    }
    public boolean isStudentInSubject(String studentId, String subjectId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT 1 FROM " + TABLE_ENROLLMENTS +
                        " WHERE " + COL_STUDENT_ID + " = ?" +
                        " AND " + COL_SUBJECT_ID + " = ?",
                new String[]{studentId, subjectId}
        );

        boolean exists = cursor.moveToFirst();
        cursor.close();

        return exists;
    }
    public String getTodayForAttendance() {
        java.util.Calendar calendar = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        );

        java.text.SimpleDateFormat dateFormat =
                new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());

        return dateFormat.format(calendar.getTime());
    }
    /**
     * Hàm 2: Lấy danh sách khuôn mặt của TẤT CẢ sinh viên
     * Gọi hàm này khi bật Camera điểm danh để AI tải dữ liệu vào bộ nhớ và so sánh.
     */
    public Cursor getAllStudentFaceVectors() {
        SQLiteDatabase db = this.getReadableDatabase();
        // Chỉ lấy những sinh viên đã có đăng ký FaceID (cột face_vector không bị rỗng)
        String query = "SELECT " + COL_STUDENT_ID + ", " + COL_FACE_VECTOR +
                " FROM " + TABLE_STUDENTS +
                " WHERE " + COL_FACE_VECTOR + " IS NOT NULL";
        return db.rawQuery(query, null);
    }

}
