package com.iritech.irissample;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.security.SecureRandom;
import android.util.Base64;

/**
 * Helper class cho chức năng reset password
 * Tách ra từ DatabaseHelper để giảm tải code và tổ chức tốt hơn
 */
public class PasswordResetHelper {
    
    private DatabaseHelper dbHelper;
    
    public PasswordResetHelper(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }
    
    /**
     * Tạo và lưu reset token cho email (120 giây hiệu lực)
     * @param email Email của admin cần reset password
     * @return Reset token được tạo, hoặc null nếu email không tồn tại
     */
    public String createPasswordResetToken(String email) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // Kiểm tra email có tồn tại không
        if (!dbHelper.isEmailExists(email)) {
            return null;
        }
        
        // Tạo token ngẫu nhiên
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.encodeToString(tokenBytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        
        // Thời gian hiệu lực: 120 giây (2 phút) từ bây giờ
        long currentTime = System.currentTimeMillis();
        long expireTime = currentTime + (120 * 1000); // 120 seconds
        
        // Lưu token vào database
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_ADMIN_RESET_TOKEN, token);
        values.put(DatabaseHelper.COL_ADMIN_EXPIRE_AT, expireTime);
        
        int rowsAffected = db.update(DatabaseHelper.TABLE_ADMIN, values,
                                    DatabaseHelper.COL_ADMIN_EMAIL + " = ?",
                                    new String[]{email});
        
        if (rowsAffected > 0) {
            return token;
        }
        
        return null;
    }
    
    /**
     * Kiểm tra token có hợp lệ và chưa hết hạn không
     * @param email Email của admin
     * @param token Token cần kiểm tra
     * @return true nếu token hợp lệ và chưa hết hạn
     */
    public boolean isResetTokenValid(String email, String token) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        String query = "SELECT " + DatabaseHelper.COL_ADMIN_EXPIRE_AT +
                      " FROM " + DatabaseHelper.TABLE_ADMIN +
                      " WHERE " + DatabaseHelper.COL_ADMIN_EMAIL + " = ? AND " +
                      DatabaseHelper.COL_ADMIN_RESET_TOKEN + " = ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{email, token});
        
        boolean isValid = false;
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(DatabaseHelper.COL_ADMIN_EXPIRE_AT);
            if (columnIndex != -1) {
                long expireAt = cursor.getLong(columnIndex);
                long currentTime = System.currentTimeMillis();
                
                // Token còn hiệu lực nếu chưa quá thời gian expire
                isValid = currentTime <= expireAt;
            }
            cursor.close();
        }
        
        return isValid;
    }
    
    /**
     * Reset password cho admin
     * @param email Email của admin
     * @param newPassword Password mới (sẽ được hash)
     * @param token Token để xác nhận (phải hợp lệ)
     * @return true nếu reset thành công, false nếu không
     */
    public boolean resetPassword(String email, String newPassword, String token) {
        // Kiểm tra token hợp lệ
        if (!isResetTokenValid(email, token)) {
            return false;
        }
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // Hash password mới
        String hashedPassword = PasswordHasher.hash(newPassword);
        
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_ADMIN_PASSWORD, hashedPassword);
        values.putNull(DatabaseHelper.COL_ADMIN_RESET_TOKEN); // Xóa token sau khi dùng
        values.putNull(DatabaseHelper.COL_ADMIN_EXPIRE_AT);   // Xóa thời gian hết hạn
        
        int rowsAffected = db.update(DatabaseHelper.TABLE_ADMIN, values,
                                    DatabaseHelper.COL_ADMIN_EMAIL + " = ?",
                                    new String[]{email});
        
        return rowsAffected > 0;
    }
    
    /**
     * Xóa token đã hết hạn (cleanup)
     * @param email Email của admin
     */
    public void clearExpiredToken(String email) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        ContentValues values = new ContentValues();
        values.putNull(DatabaseHelper.COL_ADMIN_RESET_TOKEN);
        values.putNull(DatabaseHelper.COL_ADMIN_EXPIRE_AT);
        
        db.update(DatabaseHelper.TABLE_ADMIN, values,
                 DatabaseHelper.COL_ADMIN_EMAIL + " = ?",
                 new String[]{email});
    }
    
    /**
     * THÊM MỚI: Tạo token đặc biệt cho iris reset (30 phút hiệu lực)
     * Token này được tạo sau khi verify mống mắt thành công
     * Có thời gian dài hơn email reset để user có thời gian nhập password mới
     * 
     * @param email Email của admin đã verify bằng mống mắt
     * @return Reset token được tạo
     */
    public String createPasswordResetTokenForIris(String email) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // Tạo token ngẫu nhiên
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.encodeToString(tokenBytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        
        // Thời gian hiệu lực: 30 phút (dài hơn email reset vì đã verify rồi)
        long currentTime = System.currentTimeMillis();
        long expireTime = currentTime + (30 * 60 * 1000); // 30 minutes
        
        // Lưu token vào database
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_ADMIN_RESET_TOKEN, token);
        values.put(DatabaseHelper.COL_ADMIN_EXPIRE_AT, expireTime);
        
        db.update(DatabaseHelper.TABLE_ADMIN, values,
                 DatabaseHelper.COL_ADMIN_EMAIL + " = ?",
                 new String[]{email});
        
        return token;
    }
}
