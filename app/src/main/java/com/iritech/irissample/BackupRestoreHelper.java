package com.iritech.irissample;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;
/**
 * Helper class quản lý Backup và Restore database
 * Tích hợp với EncryptionHelper để mã hóa/giải mã an toàn
 * 
 * Flow Backup:
 * 1. Super Admin bấm "Backup Database"
 * 2. Nhập password để mã hóa
 * 3. File database được mã hóa và lưu ra bộ nhớ ngoài
 * 4. File backup: /Documents/IrisSample/backup_YYYYMMDD_HHMMSS.enc
 * 
 * Flow Restore:
 * 1. App khởi động → Quét thư mục backup
 * 2. Nếu tìm thấy file backup → Hiện dialog "Phát hiện backup cũ"
 * 3. Nhập password Super Admin để giải mã
 * 4. Nếu đúng password → Restore database → Restart app
 * 5. Nếu sai password → Báo lỗi, cho phép thử lại hoặc bỏ qua
 */
public class BackupRestoreHelper {
    private static final long MAX_STAGED_DATABASE_BYTES =
            1024L * 1024L * 1024L;

    private static final String TAG = "BackupRestoreHelper";
    
    // Tên thư mục lưu backup trong bộ nhớ ngoài
    private static final String BACKUP_FOLDER_NAME = "IrisSample";
    private static final String BACKUP_FILE_PREFIX = "backup_";
    private static final String BACKUP_FILE_EXTENSION = ".enc";
    
    // Tên database file (phải khớp với DatabaseHelper)
    private static final String DATABASE_NAME = "attendance.db";
    private static final String SAFETY_DIRECTORY_NAME = "restore_safety";

    private static final String[] SQLITE_FILE_SUFFIXES = {
            "",
            "-wal",
            "-shm",
            "-journal"
    };
    
    // Tên thư mục avatar trong file ZIP backup
    private static final String ZIP_AVATAR_FOLDER = "avatars";
    
    private Context context;
    private DatabaseHelper dbHelper;
    
    public BackupRestoreHelper(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new DatabaseHelper(context);
    }
    public ExportResult exportBackup(
            Uri destinationUri,
            char[] passphrase
    ) {
        if (destinationUri == null) {
            return ExportResult.failure(
                    "Chưa chọn nơi lưu backup."
            );
        }

        if (passphrase == null || passphrase.length < 8) {
            return ExportResult.failure(
                    "Mật khẩu backup phải có ít nhất 8 ký tự."
            );
        }

        File workDirectory = new File(
                context.getNoBackupFilesDir(),
                "export_work"
        );

        File databaseSnapshot = new File(
                workDirectory,
                DATABASE_NAME
        );

        File zipFile = new File(
                workDirectory,
                "backup_payload.zip"
        );

        File encryptedFile = new File(
                workDirectory,
                "backup.iribackup"
        );

        boolean shouldDeleteDestinationOnFailure = true;

        try {
            deleteRecursivelyIfExists(workDirectory);

            if (!workDirectory.mkdirs() &&
                    !workDirectory.isDirectory()) {
                throw new IOException(
                        "Không thể tạo thư mục export staging."
                );
            }

            List<String> warnings = new ArrayList<>();
            List<BackupSource> sources = new ArrayList<>();

            AvatarCounts avatarCounts =
                    collectAvatarSources(sources, warnings);

            checkpointAndCloseDatabase();

            File currentDatabase =
                    context.getDatabasePath(DATABASE_NAME);

            if (!currentDatabase.isFile()) {
                throw new IOException(
                        "Không tìm thấy database hiện tại."
                );
            }

            copyFile(currentDatabase, databaseSnapshot);

            DatabaseValidator.ValidationResult validation =
                    DatabaseValidator.validateRestoreDatabase(
                            databaseSnapshot
                    );

            if (!validation.isValid()) {
                throw new IOException(
                        "Database snapshot không hợp lệ: " +
                                validation.getMessage()
                );
            }

            sources.add(
                    0,
                    new BackupSource(
                            databaseSnapshot,
                            "database/" + DATABASE_NAME,
                            "database",
                            null
                    )
            );

            File irisRepository = findOptionalIrisRepository();
            boolean irisIncluded = false;

            if (irisRepository != null) {
                sources.add(new BackupSource(
                        irisRepository,
                        "biometrics/iris/iritechdb.repo",
                        "iris_repository",
                        null
                ));

                irisIncluded = true;
            } else {
                warnings.add(
                        "Không tìm thấy hoặc không thể đọc " +
                                "iritechdb.repo. Backup không chứa iris repository."
                );
            }

            BackupMetadata metadata = createMetadata(
                    sources,
                    warnings,
                    avatarCounts,
                    irisIncluded
            );

            createEncryptedPayloadZip(
                    zipFile,
                    metadata,
                    sources
            );

            try (InputStream input = new BufferedInputStream(
                    new FileInputStream(zipFile));
                 OutputStream output = new BufferedOutputStream(
                         new FileOutputStream(encryptedFile))) {

                EncryptionHelper.encryptBackupStream(
                        input,
                        output,
                        passphrase
                );
            }

            long bytesWritten = copyBackupToUri(
                    encryptedFile,
                    destinationUri
            );

            if (bytesWritten != encryptedFile.length()) {
                throw new IOException(
                        "Số byte ghi ra SAF không khớp file backup."
                );
            }

            shouldDeleteDestinationOnFailure = false;

            return ExportResult.success(
                    destinationUri,
                    bytesWritten,
                    irisIncluded,
                    avatarCounts.adminCount,
                    avatarCounts.studentCount,
                    warnings
            );

        } catch (Exception exception) {
            Log.e(TAG, "SAF backup export failed", exception);

            if (shouldDeleteDestinationOnFailure) {
                deleteFailedDestination(destinationUri);
            }

            return ExportResult.failure(
                    exception.getMessage() == null
                            ? "Không thể tạo file backup."
                            : exception.getMessage()
            );

        } finally {
            try {
                deleteRecursivelyIfExists(workDirectory);
            } catch (IOException exception) {
                Log.w(
                        TAG,
                        "Could not clean export work directory",
                        exception
                );
            }
        }
    }
    private AvatarCounts collectAvatarSources(
            List<BackupSource> sources,
            List<String> warnings
    ) {
        AvatarCounts counts = new AvatarCounts();
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        try (Cursor cursor = database.rawQuery(
                "SELECT " +
                        DatabaseHelper.COL_ADMIN_ID + ", " +
                        DatabaseHelper.COL_ADMIN_PHOTO +
                        " FROM " + DatabaseHelper.TABLE_ADMIN +
                        " WHERE " +
                        DatabaseHelper.COL_ADMIN_PHOTO +
                        " IS NOT NULL AND " +
                        DatabaseHelper.COL_ADMIN_PHOTO +
                        " != ''",
                null
        )) {
            while (cursor.moveToNext()) {
                String adminId = cursor.getString(0);
                String path = cursor.getString(1);

                if (addAvatarSource(
                        sources,
                        warnings,
                        path,
                        "assets/admins",
                        "admin_avatar",
                        adminId
                )) {
                    counts.adminCount++;
                }
            }
        }

        try (Cursor cursor = database.rawQuery(
                "SELECT " +
                        DatabaseHelper.COL_STUDENT_ID + ", " +
                        DatabaseHelper.COL_PHOTO_PATH +
                        " FROM " + DatabaseHelper.TABLE_STUDENTS +
                        " WHERE " +
                        DatabaseHelper.COL_PHOTO_PATH +
                        " IS NOT NULL AND " +
                        DatabaseHelper.COL_PHOTO_PATH +
                        " != ''",
                null
        )) {
            while (cursor.moveToNext()) {
                String studentId = cursor.getString(0);
                String path = cursor.getString(1);

                if (addAvatarSource(
                        sources,
                        warnings,
                        path,
                        "assets/students",
                        "student_avatar",
                        studentId
                )) {
                    counts.studentCount++;
                }
            }
        }

        return counts;
    }

    private boolean addAvatarSource(
            List<BackupSource> sources,
            List<String> warnings,
            String filePath,
            String zipDirectory,
            String type,
            String ownerId
    ) {
        File file = new File(filePath);

        if (!file.isFile() || !file.canRead()) {
            warnings.add(
                    "Không thể backup " + type +
                            " của " + ownerId +
                            ": file không tồn tại hoặc không đọc được."
            );

            return false;
        }

        String safeOwner = sanitizePathSegment(ownerId);
        String safeFileName = sanitizePathSegment(file.getName());

        String zipPath =
                zipDirectory + "/" +
                        safeOwner + "/" +
                        safeFileName;

        sources.add(new BackupSource(
                file,
                zipPath,
                type,
                ownerId
        ));

        return true;
    }

    private String sanitizePathSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }

        String sanitized = value.replaceAll(
                "[^A-Za-z0-9._-]",
                "_"
        );

        return sanitized.isEmpty() ? "unknown" : sanitized;
    }
    private File findOptionalIrisRepository() {
        List<File> candidates = new ArrayList<>();

        candidates.add(new File(
                context.getFilesDir(),
                "iris/iritechdb.repo"
        ));

        File externalFiles = context.getExternalFilesDir(null);

        if (externalFiles != null) {
            candidates.add(new File(
                    externalFiles,
                    "iris/iritechdb.repo"
            ));
        }

        // Legacy SDK path. Không request storage permission nếu không đọc được.
        String packageName = context.getPackageName();
        int lastDot = packageName.lastIndexOf('.');

        String packageSuffix = lastDot >= 0
                ? packageName.substring(lastDot + 1)
                : packageName;

        File legacyRoot =
                Environment.getExternalStorageDirectory();

        candidates.add(new File(
                legacyRoot,
                "iritech/" + packageSuffix +
                        "/iritechdb.repo"
        ));

        for (File candidate : candidates) {
            if (candidate.isFile() && candidate.canRead()) {
                return candidate;
            }
        }

        return null;
    }
    private BackupMetadata createMetadata(
            List<BackupSource> sources,
            List<String> warnings,
            AvatarCounts avatarCounts,
            boolean irisIncluded
    ) throws IOException {
        BackupMetadata metadata = new BackupMetadata();

        metadata.applicationId = context.getPackageName();
        metadata.appVersionName = BuildConfig.VERSION_NAME;
        metadata.appVersionCode = BuildConfig.VERSION_CODE;
        metadata.databaseName = DATABASE_NAME;
        metadata.databaseVersion =
                DatabaseHelper.getCurrentDatabaseVersion();

        SimpleDateFormat utcFormat = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.US
        );

        utcFormat.setTimeZone(
                TimeZone.getTimeZone("UTC")
        );

        metadata.createdAtUtc =
                utcFormat.format(new Date());

        metadata.irisRepositoryIncluded = irisIncluded;
        metadata.rawIrisCaptureIncluded = false;
        metadata.adminAvatarCount =
                avatarCounts.adminCount;
        metadata.studentAvatarCount =
                avatarCounts.studentCount;

        metadata.warnings.addAll(warnings);

        for (BackupSource source : sources) {
            String checksum =
                    EncryptionHelper.calculateFileChecksum(
                            source.file
                    );

            if (checksum == null) {
                throw new IOException(
                        "Không thể tính checksum cho " +
                                source.zipPath
                );
            }

            metadata.addEntry(
                    source.zipPath,
                    source.type,
                    source.ownerId,
                    source.file.length(),
                    checksum
            );
        }

        return metadata;
    }

    private void createEncryptedPayloadZip(
            File outputZip,
            BackupMetadata metadata,
            List<BackupSource> sources
    ) throws IOException {
        Gson gson = new Gson();

        byte[] metadataBytes = gson
                .toJson(metadata)
                .getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zipOutput =
                     new ZipOutputStream(
                             new BufferedOutputStream(
                                     new FileOutputStream(outputZip)
                             )
                     )) {

            ZipEntry metadataEntry =
                    new ZipEntry("metadata.json");

            zipOutput.putNextEntry(metadataEntry);
            zipOutput.write(metadataBytes);
            zipOutput.closeEntry();

            for (BackupSource source : sources) {
                addFileToZip(
                        zipOutput,
                        source.file,
                        source.zipPath
                );
            }
        }
    }
    private long copyBackupToUri(
            File source,
            Uri destinationUri
    ) throws IOException {
        OutputStream output = context
                .getContentResolver()
                .openOutputStream(destinationUri);

        if (output == null) {
            throw new IOException(
                    "Không thể mở file đích SAF."
            );
        }

        long totalBytes = 0;

        try (InputStream input = new BufferedInputStream(
                new FileInputStream(source));
             OutputStream destination =
                     new BufferedOutputStream(output)) {

            byte[] buffer = new byte[64 * 1024];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                destination.write(
                        buffer,
                        0,
                        bytesRead
                );

                totalBytes += bytesRead;
            }

            destination.flush();
        }

        return totalBytes;
    }

    private void deleteFailedDestination(Uri destinationUri) {
        try {
            DocumentsContract.deleteDocument(
                    context.getContentResolver(),
                    destinationUri
            );
        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Could not delete incomplete SAF document",
                    exception
            );
        }
    }
    /**
     * Backup database + avatar images ra file được mã hóa
     * Format mới: ZIP(DB + avatars) → Encrypt → .enc
     * Tương thích ngược: restore vẫn hỗ trợ format cũ (raw DB)
     * 
     * @param password Mật khẩu Super Admin để mã hóa
     * @return File backup đã tạo, hoặc null nếu thất bại
     */
    public File backupDatabase(String password) {
        try {
            // 1. Lấy danh sách avatar files TRƯỚC KHI đóng DB
            List<File> avatarFiles = getAvatarFiles();
            
            // 2. Đóng database trước khi backup (để tránh file bị lock)
            dbHelper.close();
            
            // 3. Lấy đường dẫn file database hiện tại
            File dbFile = context.getDatabasePath(DATABASE_NAME);
            
            if (!dbFile.exists()) {
                Log.e(TAG, "Database file not found: " + dbFile.getAbsolutePath());
                return null;
            }
            
            // 4. Tạo thư mục backup nếu chưa có
            File backupFolder = getBackupFolder();
            if (!backupFolder.exists()) {
                if (!backupFolder.mkdirs()) {
                    Log.e(TAG, "Failed to create backup folder");
                    return null;
                }
            }
            
            // 5. Tạo tên file backup với timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String backupFileName = BACKUP_FILE_PREFIX + timestamp + BACKUP_FILE_EXTENSION;
            File backupFile = new File(backupFolder, backupFileName);
            
            // 6. Tạo temp ZIP chứa DB + avatar images
            File tempZip = new File(context.getCacheDir(), "temp_backup.zip");
            createBackupZip(dbFile, avatarFiles, tempZip);
            
            // 7. Mã hóa ZIP file
            Log.d(TAG, "Starting backup encryption...");
            Log.d(TAG, "Source ZIP: " + tempZip.getAbsolutePath() + " (" + tempZip.length() + " bytes)");
            Log.d(TAG, "Destination: " + backupFile.getAbsolutePath());
            Log.d(TAG, "Avatar files included: " + avatarFiles.size());
            
            boolean success = EncryptionHelper.encryptFile(tempZip, backupFile, password);
            
            // 8. Xóa temp ZIP
            tempZip.delete();
            
            if (success) {
                Log.d(TAG, "Backup completed successfully");
                Log.d(TAG, "Backup file size: " + backupFile.length() + " bytes");
                return backupFile;
            } else {
                Log.e(TAG, "Backup encryption failed");
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Backup failed", e);
            return null;
        }
    }
    
    /**
     * Restore database + avatar images từ file backup được mã hóa
     * Hỗ trợ cả format mới (ZIP chứa DB + avatars) và format cũ (raw DB)
     * 
     * @param backupFile File backup cần restore
     * @param password Mật khẩu Super Admin để giải mã
     * @return true nếu restore thành công, false nếu thất bại (sai password)
     */
    public boolean restoreDatabase(File backupFile, String password) {
        File databaseFile = context.getDatabasePath(DATABASE_NAME);

        File workDirectory = new File(
                context.getNoBackupFilesDir(),
                "restore_work"
        );

        File decryptedFile = new File(
                workDirectory,
                "decrypted_backup.dat"
        );

        File stagedDatabase = new File(
                workDirectory,
                DATABASE_NAME
        );

        File safetyDirectory = null;
        boolean replacementStarted = false;

        try {
            if (backupFile == null || !backupFile.isFile()) {
                Log.e(TAG, "Backup file does not exist");
                return false;
            }

            deleteRecursivelyIfExists(workDirectory);

            if (!workDirectory.mkdirs() &&
                    !workDirectory.isDirectory()) {
                throw new IOException(
                        "Cannot create restore work directory"
                );
            }

            // 1. Giải mã hoàn toàn trong staging.
            boolean decrypted = EncryptionHelper.decryptFile(
                    backupFile,
                    decryptedFile,
                    password
            );

            if (!decrypted) {
                Log.e(
                        TAG,
                        "Wrong password or corrupted backup"
                );
                return false;
            }

            // 2. Lấy DB ra staging, chưa đụng tới DB đang dùng.
            prepareStagedDatabase(
                    decryptedFile,
                    stagedDatabase
            );

            // 3. Migrate bản staging lên schema hiện tại.
            migrateStagedDatabase(stagedDatabase);

            // 4. Validate đầy đủ trước khi thay DB.
            DatabaseValidator.ValidationResult stagedValidation =
                    DatabaseValidator.validateRestoreDatabase(
                            stagedDatabase
                    );

            if (!stagedValidation.isValid()) {
                throw new IOException(
                        "Staged database validation failed [" +
                                stagedValidation.getErrorCode() +
                                "]: " +
                                stagedValidation.getMessage()
                );
            }

            // 5. Checkpoint và đóng helper trước khi safety copy.
            checkpointAndCloseDatabase();

            // 6. Tạo safety backup trước khi xóa DB hiện tại.
            safetyDirectory = createSafetyBackup(databaseFile);

            // Từ đây mọi lỗi đều phải rollback.
            replacementStarted = true;

            // 7. Xóa DB hiện tại và toàn bộ sidecar.
            deleteSQLiteFiles(databaseFile);

            // 8. Copy staged database vào vị trí thật.
            copyFile(stagedDatabase, databaseFile);

            // Đảm bảo sidecar cũ không còn.
            deleteSQLiteSidecars(databaseFile);

            // 9. Validate lại chính file vừa được cài đặt.
            DatabaseValidator.ValidationResult installedValidation =
                    DatabaseValidator.validateRestoreDatabase(
                            databaseFile
                    );

            if (!installedValidation.isValid()) {
                throw new IOException(
                        "Installed database validation failed [" +
                                installedValidation.getErrorCode() +
                                "]: " +
                                installedValidation.getMessage()
                );
            }

            // Legacy ZIP vẫn được nhận diện và đọc DB.
            // Avatar legacy sẽ được xử lý riêng ở bước sau.
            clearSafetyBackup(safetyDirectory);

            Log.d(
                    TAG,
                    "Database restored successfully, version=" +
                            installedValidation.getDatabaseVersion()
            );

            return true;

        } catch (Exception restoreException) {
            Log.e(TAG, "Restore failed", restoreException);

            if (replacementStarted) {
                try {
                    rollbackFromSafetyBackup(
                            databaseFile,
                            safetyDirectory
                    );

                    Log.w(
                            TAG,
                            "Restore failed; original database was restored"
                    );

                } catch (Exception rollbackException) {
                    Log.e(
                            TAG,
                            "CRITICAL: database rollback failed",
                            rollbackException
                    );
                }
            }

            return false;

        } finally {
            try {
                deleteRecursivelyIfExists(workDirectory);
            } catch (IOException cleanupException) {
                Log.w(
                        TAG,
                        "Could not clean restore work directory",
                        cleanupException
                );
            }
        }
    }
    /**
     * Hỗ trợ:
     * - Legacy encrypted raw SQLite database.
     * - Legacy encrypted ZIP có attendance.db ở root.
     * - Format mới có database/attendance.db.
     */
    private void prepareStagedDatabase(
            File decryptedFile,
            File stagedDatabase
    ) throws IOException {
        if (!isZipFile(decryptedFile)) {
            // Legacy format: encrypted raw SQLite DB.
            copyFile(decryptedFile, stagedDatabase);
            return;
        }

        boolean databaseFound = false;

        try (ZipInputStream zipInput = new ZipInputStream(
                new FileInputStream(decryptedFile))) {

            ZipEntry entry;

            while ((entry = zipInput.getNextEntry()) != null) {
                String entryName = entry.getName()
                        .replace('\\', '/');

                boolean isDatabaseEntry =
                        DATABASE_NAME.equals(entryName) ||
                                ("database/" + DATABASE_NAME)
                                        .equals(entryName);

                if (isDatabaseEntry && !entry.isDirectory()) {
                    if (databaseFound) {
                        throw new IOException(
                                "Backup contains duplicate database entries"
                        );
                    }

                    if (entry.getSize() >
                            MAX_STAGED_DATABASE_BYTES) {
                        throw new IOException(
                                "Database entry is too large"
                        );
                    }

                    writeDatabaseEntryToFile(
                            zipInput,
                            stagedDatabase
                    );

                    databaseFound = true;
                }

                zipInput.closeEntry();
            }
        }

        if (!databaseFound || !stagedDatabase.isFile()) {
            throw new IOException(
                    "Backup does not contain " + DATABASE_NAME
            );
        }
    }

    private void writeDatabaseEntryToFile(
            ZipInputStream zipInput,
            File destination
    ) throws IOException {
        File parent = destination.getParentFile();

        if (parent != null &&
                !parent.exists() &&
                !parent.mkdirs()) {
            throw new IOException(
                    "Cannot create staging directory"
            );
        }

        long totalBytes = 0;

        try (FileOutputStream output =
                     new FileOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = zipInput.read(buffer)) != -1) {
                totalBytes += bytesRead;

                if (totalBytes > MAX_STAGED_DATABASE_BYTES) {
                    throw new IOException(
                            "Extracted database exceeds size limit"
                    );
                }

                output.write(buffer, 0, bytesRead);
            }

            output.flush();
            output.getFD().sync();
        }
    }

    private void migrateStagedDatabase(
            File stagedDatabase
    ) throws IOException {
        SQLiteDatabase database = null;

        try {
            database = SQLiteDatabase.openDatabase(
                    stagedDatabase.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE
            );

            DatabaseHelper.migrateToCurrentSchema(database);

        } catch (Exception exception) {
            throw new IOException(
                    "Cannot migrate staged database: " +
                            exception.getMessage(),
                    exception
            );

        } finally {
            if (database != null && database.isOpen()) {
                database.close();
            }
        }
    }
    private void deleteSQLiteSidecars(
            File databaseFile
    ) throws IOException {
        String[] sidecarSuffixes = {
                "-wal",
                "-shm",
                "-journal"
        };

        for (String suffix : sidecarSuffixes) {
            File sidecar = new File(
                    databaseFile.getAbsolutePath() + suffix
            );

            if (sidecar.exists() && !sidecar.delete()) {
                throw new IOException(
                        "Cannot delete SQLite sidecar: " +
                                sidecar.getAbsolutePath()
                );
            }
        }
    }
    
    /**
     * Quét và tìm file backup mới nhất trong thư mục
     * 
     * @return File backup mới nhất, hoặc null nếu không tìm thấy
     */
    public File findLatestBackup() {
        File backupFolder = getBackupFolder();
        
        Log.d(TAG, "findLatestBackup() - Scanning folder: " + backupFolder.getAbsolutePath());
        
        if (!backupFolder.exists() || !backupFolder.isDirectory()) {
            Log.w(TAG, "Backup folder does not exist or is not a directory");
            return null;
        }
        
        File[] backupFiles = backupFolder.listFiles((dir, name) -> 
            name.startsWith(BACKUP_FILE_PREFIX) && name.endsWith(BACKUP_FILE_EXTENSION)
        );
        
        if (backupFiles == null || backupFiles.length == 0) {
            Log.w(TAG, "No backup files found in folder");
            // List tất cả files trong thư mục để debug
            File[] allFiles = backupFolder.listFiles();
            if (allFiles != null && allFiles.length > 0) {
                Log.d(TAG, "Found " + allFiles.length + " files in folder:");
                for (File file : allFiles) {
                    Log.d(TAG, "  - " + file.getName());
                }
            } else {
                Log.d(TAG, "Folder is empty or cannot list files");
            }
            return null;
        }
        
        Log.d(TAG, "Found " + backupFiles.length + " backup file(s)");
        
        // Tìm file mới nhất (theo thời gian sửa đổi)
        File latestBackup = backupFiles[0];
        for (File file : backupFiles) {
            Log.d(TAG, "  - " + file.getName() + " (" + file.lastModified() + ")");
            if (file.lastModified() > latestBackup.lastModified()) {
                latestBackup = file;
            }
        }
        
        Log.d(TAG, "Latest backup: " + latestBackup.getName());
        return latestBackup;
    }
    
    /**
     * Xóa tất cả file backup cũ
     * Dùng sau khi restore thành công hoặc khi user muốn xóa backup
     * 
     * @return Số file đã xóa
     */
    public int deleteAllBackups() {
        File backupFolder = getBackupFolder();
        
        if (!backupFolder.exists() || !backupFolder.isDirectory()) {
            return 0;
        }
        
        File[] backupFiles = backupFolder.listFiles((dir, name) -> 
            name.startsWith(BACKUP_FILE_PREFIX) && name.endsWith(BACKUP_FILE_EXTENSION)
        );
        
        if (backupFiles == null || backupFiles.length == 0) {
            return 0;
        }
        
        int deletedCount = 0;
        for (File file : backupFiles) {
            if (file.delete()) {
                deletedCount++;
                Log.d(TAG, "Deleted backup: " + file.getName());
            }
        }
        
        return deletedCount;
    }
    
    /**
     * Xóa một file backup cụ thể
     */
    public boolean deleteBackup(File backupFile) {
        if (backupFile != null && backupFile.exists()) {
            return backupFile.delete();
        }
        return false;
    }
    
    /**
     * Lấy thông tin về file backup
     */
    public BackupInfo getBackupInfo(File backupFile) {
        if (backupFile == null || !backupFile.exists()) {
            return null;
        }
        
        BackupInfo info = new BackupInfo();
        info.fileName = backupFile.getName();
        info.filePath = backupFile.getAbsolutePath();
        info.fileSize = backupFile.length();
        info.lastModified = backupFile.lastModified();
        
        // Parse timestamp từ tên file
        // Format: backup_YYYYMMDD_HHMMSS.enc
        String fileName = backupFile.getName();
        if (fileName.startsWith(BACKUP_FILE_PREFIX)) {
            String timestamp = fileName.substring(BACKUP_FILE_PREFIX.length(), fileName.length() - BACKUP_FILE_EXTENSION.length());
            info.timestamp = timestamp;
        }
        
        return info;
    }
    
    /**
     * Lấy thư mục backup (Documents/IrisSample)
     * Thư mục này không bị xóa khi uninstall app
     */
    private File getBackupFolder() {
        // Android 10+ (API 29+): Dùng getExternalFilesDir() thay vì Environment.getExternalStorageDirectory()
        // Hoặc dùng Documents folder qua MediaStore
        
        // Cách 1: Lưu trong thư mục app-specific (bị xóa khi uninstall)
        // File backupFolder = new File(context.getExternalFilesDir(null), BACKUP_FOLDER_NAME);
        
        // Cách 2: Lưu trong Documents (không bị xóa khi uninstall) - KHUYẾN NGHỊ
        File documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File backupFolder = new File(documentsFolder, BACKUP_FOLDER_NAME);
        
        Log.d(TAG, "Backup folder path: " + backupFolder.getAbsolutePath());
        Log.d(TAG, "Backup folder exists: " + backupFolder.exists());
        
        return backupFolder;
    }
    
    // ============ AVATAR BACKUP HELPER METHODS ============
    
    /**
     * Lấy danh sách file avatar từ DB (phải gọi TRƯỚC KHI đóng DB)
     */
    private List<File> getAvatarFiles() {
        List<File> files = new ArrayList<>();
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_ADMIN_PHOTO +
                " FROM " + DatabaseHelper.TABLE_ADMIN +
                " WHERE " + DatabaseHelper.COL_ADMIN_PHOTO + " IS NOT NULL" +
                " AND " + DatabaseHelper.COL_ADMIN_PHOTO + " != ''", null);
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    if (path != null && !path.isEmpty()) {
                        File file = new File(path);
                        if (file.exists()) {
                            files.add(file);
                            Log.d(TAG, "Avatar found: " + file.getName());
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting avatar files", e);
        }
        return files;
    }
    
    /**
     * Tạo file ZIP chứa database + avatar images
     */
    private void createBackupZip(File dbFile, List<File> avatarFiles, File outputZip) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip));
        
        // Add database file
        addFileToZip(zos, dbFile, DATABASE_NAME);
        
        // Add avatar files
        for (File avatar : avatarFiles) {
            addFileToZip(zos, avatar, ZIP_AVATAR_FOLDER + "/" + avatar.getName());
        }
        
        zos.close();
        Log.d(TAG, "Backup ZIP created: " + outputZip.length() + " bytes, " +
              (1 + avatarFiles.size()) + " files");
    }
    
    /**
     * Thêm một file vào ZIP
     */
    private void addFileToZip(
            ZipOutputStream zipOutput,
            File file,
            String entryName
    ) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zipOutput.putNextEntry(entry);

        try (FileInputStream input =
                     new FileInputStream(file)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                zipOutput.write(
                        buffer,
                        0,
                        bytesRead
                );
            }
        }

        zipOutput.closeEntry();
    }
    
    /**
     * Kiểm tra file có phải ZIP không (dựa vào magic bytes PK)
     */
    private boolean isZipFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] magic = new byte[2];
            int read = fis.read(magic);
            fis.close();
            return read == 2 && magic[0] == 0x50 && magic[1] == 0x4B;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Restore từ file ZIP chứa DB + avatars
     */
    private void restoreFromZip(File zipFile, File dbFile) throws IOException {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry entry;
        
        File avatarDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (avatarDir != null && !avatarDir.exists()) {
            avatarDir.mkdirs();
        }
        
        while ((entry = zis.getNextEntry()) != null) {
            String entryName = entry.getName();
            
            // Bảo mật: chống path traversal attack
            if (entryName.contains("..")) {
                Log.w(TAG, "Skipping suspicious ZIP entry: " + entryName);
                zis.closeEntry();
                continue;
            }
            
            if (entryName.equals(DATABASE_NAME)) {
                // Restore database
                if (dbFile.exists()) {
                    dbFile.delete();
                }
                writeStreamToFile(zis, dbFile);
                Log.d(TAG, "Restored database file");
                
            } else if (entryName.startsWith(ZIP_AVATAR_FOLDER + "/") && !entry.isDirectory()) {
                // Restore avatar image
                String fileName = entryName.substring((ZIP_AVATAR_FOLDER + "/").length());
                if (!fileName.isEmpty() && !fileName.contains("/") && avatarDir != null) {
                    File avatarFile = new File(avatarDir, fileName);
                    writeStreamToFile(zis, avatarFile);
                    Log.d(TAG, "Restored avatar: " + fileName);
                }
            }
            zis.closeEntry();
        }
        zis.close();
        
        // Cập nhật photo_path trong DB cho đúng đường dẫn thiết bị hiện tại
        if (avatarDir != null) {
            updatePhotoPathsAfterRestore(dbFile, avatarDir);
        }
    }
    
    /**
     * Ghi dữ liệu từ ZipInputStream vào file
     */
    private void writeStreamToFile(ZipInputStream zis, File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[8192];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.close();
    }
    
    /**
     * Cập nhật photo_path sau khi restore để phù hợp với thiết bị hiện tại
     * (trường hợp restore sang thiết bị khác hoặc đường dẫn thay đổi)
     */
    private void updatePhotoPathsAfterRestore(File dbFile, File avatarDir) {
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            
            Cursor cursor = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_ADMIN_ID + ", " + DatabaseHelper.COL_ADMIN_PHOTO +
                " FROM " + DatabaseHelper.TABLE_ADMIN +
                " WHERE " + DatabaseHelper.COL_ADMIN_PHOTO + " IS NOT NULL" +
                " AND " + DatabaseHelper.COL_ADMIN_PHOTO + " != ''", null);
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String adminId = cursor.getString(0);
                    String oldPath = cursor.getString(1);
                    String fileName = new File(oldPath).getName();
                    File avatarFile = new File(avatarDir, fileName);
                    
                    if (avatarFile.exists()) {
                        String newPath = avatarFile.getAbsolutePath();
                        if (!newPath.equals(oldPath)) {
                            ContentValues values = new ContentValues();
                            values.put(DatabaseHelper.COL_ADMIN_PHOTO, newPath);
                            db.update(DatabaseHelper.TABLE_ADMIN, values,
                                DatabaseHelper.COL_ADMIN_ID + " = ?", new String[]{adminId});
                            Log.d(TAG, "Updated photo path: " + oldPath + " -> " + newPath);
                        }
                    }
                }
                cursor.close();
            }
            
            db.close();
        } catch (Exception e) {
            Log.e(TAG, "Error updating photo paths after restore", e);
        }
    }
    
    /**
     * Copy file thủ công (fallback nếu renameTo() thất bại)
     */

//    private void copyFile(File source, File dest) throws Exception {
//        FileInputStream fis = new FileInputStream(source);
//        FileOutputStream fos = new FileOutputStream(dest);
//
//        byte[] buffer = new byte[8192];
//        int bytesRead;
//        while ((bytesRead = fis.read(buffer)) != -1) {
//            fos.write(buffer, 0, bytesRead);
//        }
//
//        fis.close();
//        fos.close();
//    }
    private void copyFile(File source, File destination) throws IOException {
        if (source == null || !source.isFile()) {
            throw new IOException(
                    "Source file does not exist: " +
                            (source == null ? "null" : source.getAbsolutePath())
            );
        }

        File parent = destination.getParentFile();

        if (parent != null &&
                !parent.exists() &&
                !parent.mkdirs()) {
            throw new IOException(
                    "Cannot create destination directory: " +
                            parent.getAbsolutePath()
            );
        }

        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            output.flush();
            output.getFD().sync();
        }
    }
    /**
     * Yêu cầu SQLite ghi WAL về database chính trước khi copy.
     * Sau đó đóng DatabaseHelper do BackupRestoreHelper sở hữu.
     */
    private void checkpointAndCloseDatabase() {
        try {
            SQLiteDatabase database = dbHelper.getWritableDatabase();

            try (Cursor cursor = database.rawQuery(
                    "PRAGMA wal_checkpoint(FULL)", null)) {
                cursor.moveToFirst();
            }

        } catch (Exception exception) {
            // Database có thể không dùng WAL; vẫn tiếp tục đóng.
            Log.w(TAG, "Could not checkpoint database", exception);

        } finally {
            dbHelper.close();
        }
    }

    /**
     * Tạo safety copy của database hiện tại và các SQLite sidecar.
     */
    private File createSafetyBackup(File databaseFile) throws IOException {
        File safetyDirectory = new File(
                context.getNoBackupFilesDir(),
                SAFETY_DIRECTORY_NAME
        );

        deleteRecursivelyIfExists(safetyDirectory);

        if (!safetyDirectory.mkdirs() &&
                !safetyDirectory.isDirectory()) {
            throw new IOException(
                    "Cannot create safety directory: " +
                            safetyDirectory.getAbsolutePath()
            );
        }

        for (String suffix : SQLITE_FILE_SUFFIXES) {
            File source = new File(
                    databaseFile.getAbsolutePath() + suffix
            );

            if (source.isFile()) {
                File destination = new File(
                        safetyDirectory,
                        DATABASE_NAME + suffix
                );

                copyFile(source, destination);
            }
        }

        return safetyDirectory;
    }

    /**
     * Xóa database restore lỗi và đưa safety copy trở lại.
     */
    private void rollbackFromSafetyBackup(
            File databaseFile,
            File safetyDirectory
    ) throws IOException {
        dbHelper.close();
        deleteSQLiteFiles(databaseFile);

        if (safetyDirectory == null ||
                !safetyDirectory.isDirectory()) {
            throw new IOException(
                    "Safety backup directory is missing"
            );
        }

        File safetyDatabase = new File(
                safetyDirectory,
                DATABASE_NAME
        );

        // Nếu trước restore không có DB thì rollback chỉ cần xóa DB mới.
        if (!safetyDatabase.isFile()) {
            return;
        }

        for (String suffix : SQLITE_FILE_SUFFIXES) {
            File source = new File(
                    safetyDirectory,
                    DATABASE_NAME + suffix
            );

            if (source.isFile()) {
                File destination = new File(
                        databaseFile.getAbsolutePath() + suffix
                );

                copyFile(source, destination);
            }
        }
    }

    /**
     * Xóa attendance.db cùng WAL, SHM và journal.
     */
    private void deleteSQLiteFiles(File databaseFile) throws IOException {
        for (String suffix : SQLITE_FILE_SUFFIXES) {
            File file = new File(
                    databaseFile.getAbsolutePath() + suffix
            );

            if (file.exists() && !file.delete()) {
                throw new IOException(
                        "Cannot delete SQLite file: " +
                                file.getAbsolutePath()
                );
            }
        }
    }

    private void deleteRecursivelyIfExists(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();

            if (children == null) {
                throw new IOException(
                        "Cannot list directory: " +
                                file.getAbsolutePath()
                );
            }

            for (File child : children) {
                deleteRecursivelyIfExists(child);
            }
        }

        if (!file.delete()) {
            throw new IOException(
                    "Cannot delete: " + file.getAbsolutePath()
            );
        }
    }

    private void clearSafetyBackup(File safetyDirectory) {
        try {
            deleteRecursivelyIfExists(safetyDirectory);
        } catch (IOException exception) {
            // Không làm restore bị báo lỗi chỉ vì cleanup thất bại.
            Log.w(TAG, "Could not remove safety backup", exception);
        }
    }
    
    /**
     * Kiểm tra database hiện tại có dữ liệu không
     * Dùng để quyết định có nên hiện dialog restore không
     */
    public boolean isDatabaseEmpty() {
        return !dbHelper.isSuperAdminExists();
    }
    
    /**
     * Class chứa thông tin về file backup
     */
    public static class BackupInfo {
        public String fileName;
        public String filePath;
        public long fileSize;
        public long lastModified;
        public String timestamp;
        
        public String getFormattedSize() {
            if (fileSize < 1024) {
                return fileSize + " B";
            } else if (fileSize < 1024 * 1024) {
                return String.format(Locale.US, "%.2f KB", fileSize / 1024.0);
            } else {
                return String.format(Locale.US, "%.2f MB", fileSize / (1024.0 * 1024.0));
            }
        }
        
        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
            return sdf.format(new Date(lastModified));
        }
    }
    private static final class BackupSource {

        final File file;
        final String zipPath;
        final String type;
        final String ownerId;

        BackupSource(
                File file,
                String zipPath,
                String type,
                String ownerId
        ) {
            this.file = file;
            this.zipPath = zipPath;
            this.type = type;
            this.ownerId = ownerId;
        }
    }

    private static final class AvatarCounts {
        int adminCount;
        int studentCount;
    }

    public static final class ExportResult {

        private final boolean success;
        private final String message;
        private final Uri destinationUri;
        private final long fileSize;
        private final boolean irisIncluded;
        private final int adminAvatarCount;
        private final int studentAvatarCount;
        private final List<String> warnings;

        private ExportResult(
                boolean success,
                String message,
                Uri destinationUri,
                long fileSize,
                boolean irisIncluded,
                int adminAvatarCount,
                int studentAvatarCount,
                List<String> warnings
        ) {
            this.success = success;
            this.message = message;
            this.destinationUri = destinationUri;
            this.fileSize = fileSize;
            this.irisIncluded = irisIncluded;
            this.adminAvatarCount = adminAvatarCount;
            this.studentAvatarCount = studentAvatarCount;
            this.warnings = warnings == null
                    ? new ArrayList<>()
                    : new ArrayList<>(warnings);
        }

        static ExportResult success(
                Uri uri,
                long fileSize,
                boolean irisIncluded,
                int adminCount,
                int studentCount,
                List<String> warnings
        ) {
            return new ExportResult(
                    true,
                    "Export thành công.",
                    uri,
                    fileSize,
                    irisIncluded,
                    adminCount,
                    studentCount,
                    warnings
            );
        }

        static ExportResult failure(String message) {
            return new ExportResult(
                    false,
                    message,
                    null,
                    0,
                    false,
                    0,
                    0,
                    null
            );
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Uri getDestinationUri() {
            return destinationUri;
        }

        public long getFileSize() {
            return fileSize;
        }

        public boolean isIrisIncluded() {
            return irisIncluded;
        }

        public int getAdminAvatarCount() {
            return adminAvatarCount;
        }

        public int getStudentAvatarCount() {
            return studentAvatarCount;
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
    }
}
