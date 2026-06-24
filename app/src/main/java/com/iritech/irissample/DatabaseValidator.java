package com.iritech.irissample;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class DatabaseValidator {

    private static final byte[] SQLITE_HEADER =
            "SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII);

    private static final String[] REQUIRED_TABLES = {
            DatabaseHelper.TABLE_ADMIN,
            DatabaseHelper.TABLE_SUBJECTS,
            DatabaseHelper.TABLE_STUDENTS,
            DatabaseHelper.TABLE_ENROLLMENTS,
            DatabaseHelper.TABLE_CHECKIN_HISTORY,
            DatabaseHelper.TABLE_EMAIL_RECIPIENTS
    };

    private DatabaseValidator() {
    }

    public static ValidationResult validateRestoreDatabase(File databaseFile) {
        if (databaseFile == null) {
            return ValidationResult.failure(
                    "FILE_MISSING",
                    "Không nhận được file database."
            );
        }

        if (!databaseFile.isFile() || databaseFile.length() < SQLITE_HEADER.length) {
            return ValidationResult.failure(
                    "FILE_INVALID",
                    "File database không tồn tại hoặc không hợp lệ."
            );
        }

        if (!hasValidSqliteHeader(databaseFile)) {
            return ValidationResult.failure(
                    "INVALID_SQLITE_HEADER",
                    "File không phải SQLite database hợp lệ."
            );
        }

        SQLiteDatabase database = null;

        try {
            database = SQLiteDatabase.openDatabase(
                    databaseFile.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );

            String integrityError = checkIntegrity(database);
            if (integrityError != null) {
                return ValidationResult.failure(
                        "INTEGRITY_CHECK_FAILED",
                        "SQLite integrity_check thất bại: " + integrityError
                );
            }

            for (String table : REQUIRED_TABLES) {
                if (!tableExists(database, table)) {
                    return ValidationResult.failure(
                            "MISSING_TABLE",
                            "Database thiếu bảng bắt buộc: " + table
                    );
                }
            }

            int databaseVersion = readDatabaseVersion(database);

            if (databaseVersion > DatabaseHelper.getCurrentDatabaseVersion()) {
                return ValidationResult.failure(
                        "DATABASE_VERSION_TOO_NEW",
                        "Database version " + databaseVersion +
                                " mới hơn version ứng dụng hỗ trợ (" +
                                DatabaseHelper.getCurrentDatabaseVersion() + ")."
                );
            }

            if (!hasSuperAdmin(database)) {
                return ValidationResult.failure(
                        "SUPER_ADMIN_MISSING",
                        "Database không có tài khoản Super Admin."
                );
            }

            return ValidationResult.success(databaseVersion);

        } catch (SQLiteException exception) {
            return ValidationResult.failure(
                    "SQLITE_OPEN_FAILED",
                    "Không thể mở SQLite database: " + exception.getMessage()
            );
        } finally {
            if (database != null && database.isOpen()) {
                database.close();
            }
        }
    }

    private static boolean hasValidSqliteHeader(File file) {
        byte[] header = new byte[SQLITE_HEADER.length];

        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;

            while (offset < header.length) {
                int read = input.read(header, offset, header.length - offset);

                if (read == -1) {
                    return false;
                }

                offset += read;
            }

            for (int index = 0; index < SQLITE_HEADER.length; index++) {
                if (header[index] != SQLITE_HEADER[index]) {
                    return false;
                }
            }

            return true;

        } catch (IOException exception) {
            return false;
        }
    }

    private static String checkIntegrity(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery(
                "PRAGMA integrity_check", null)) {

            if (!cursor.moveToFirst()) {
                return "Không nhận được kết quả integrity_check.";
            }

            String result = cursor.getString(0);

            if (!"ok".equalsIgnoreCase(result)) {
                return result;
            }

            return null;
        }
    }

    private static boolean tableExists(
            SQLiteDatabase database,
            String tableName
    ) {
        try (Cursor cursor = database.rawQuery(
                "SELECT 1 FROM sqlite_master " +
                        "WHERE type = 'table' AND name = ? LIMIT 1",
                new String[]{tableName}
        )) {
            return cursor.moveToFirst();
        }
    }

    private static boolean hasSuperAdmin(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ADMIN +
                        " WHERE " + DatabaseHelper.COL_ADMIN_ROLE + " = ?",
                new String[]{"SUPER_ADMIN"}
        )) {
            return cursor.moveToFirst() && cursor.getLong(0) > 0;
        }
    }

    private static int readDatabaseVersion(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery(
                "PRAGMA user_version", null)) {

            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }

            return 0;
        }
    }

    public static final class ValidationResult {

        private final boolean valid;
        private final String errorCode;
        private final String message;
        private final int databaseVersion;

        private ValidationResult(
                boolean valid,
                String errorCode,
                String message,
                int databaseVersion
        ) {
            this.valid = valid;
            this.errorCode = errorCode;
            this.message = message;
            this.databaseVersion = databaseVersion;
        }

        public static ValidationResult success(int databaseVersion) {
            return new ValidationResult(
                    true,
                    null,
                    "Database hợp lệ.",
                    databaseVersion
            );
        }

        public static ValidationResult failure(
                String errorCode,
                String message
        ) {
            return new ValidationResult(
                    false,
                    errorCode,
                    message,
                    -1
            );
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }

        public int getDatabaseVersion() {
            return databaseVersion;
        }
    }
}