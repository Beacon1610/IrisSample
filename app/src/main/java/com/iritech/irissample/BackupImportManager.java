package com.iritech.irissample;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Phase 3 SAF importer. All untrusted input is staged and validated before the
 * live database is replaced. This class intentionally does not implement the
 * fresh-install flow in LoginActivity.
 */
public final class BackupImportManager {

    private static final String TAG = "BackupImportManager";
    private static final String DATABASE_NAME = DatabaseHelper.DATABASE_NAME;
    private static final long MAX_BACKUP_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final String WRONG_PASSWORD_OR_CORRUPT =
            "Sai mật khẩu hoặc file backup đã bị hỏng/bị thay đổi.";
    private static final String[] SQLITE_SUFFIXES = {"", "-wal", "-shm", "-journal"};

    private final Context context;
    private final DatabaseHelper dbHelper;

    public BackupImportManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new DatabaseHelper(this.context);
    }

    public ImportResult importBackup(Uri sourceUri, char[] passphrase) {
        if (sourceUri == null) {
            return ImportResult.failure("Chưa chọn file backup.");
        }
        if (passphrase == null || passphrase.length == 0) {
            return ImportResult.failure("Vui lòng nhập mật khẩu backup.");
        }

        File workDirectory = new File(context.getNoBackupFilesDir(), "import_work");
        File sourceFile = new File(workDirectory, "selected.backup");
        File decryptedFile = new File(workDirectory, "decrypted.payload");
        File extractDirectory = new File(workDirectory, "extracted");
        File stagedDatabase = new File(workDirectory, DATABASE_NAME);
        File liveDatabase = context.getDatabasePath(DATABASE_NAME);

        File safetyDirectory = null;
        PreparedAssets preparedAssets = null;
        boolean replacementStarted = false;
        boolean success = false;

        try {
            deleteRecursively(workDirectory);
            ensureDirectory(workDirectory);
            copyUriToFile(sourceUri, sourceFile);

            ArchiveContents archive;
            if (EncryptionHelper.isVersionedBackup(sourceFile)) {
                decryptVersionedBackup(sourceFile, decryptedFile, passphrase);
                if (!isZipFile(decryptedFile)) {
                    throw new CorruptBackupException();
                }
                archive = extractVersionedArchive(decryptedFile, extractDirectory);
                copyFile(archive.databaseFile, stagedDatabase);
            } else {
                decryptLegacyBackup(sourceFile, decryptedFile, passphrase);
                archive = prepareLegacyArchive(decryptedFile, extractDirectory, stagedDatabase);
            }

            migrateStagedDatabase(stagedDatabase);
            validateDatabaseOrThrow(stagedDatabase);

            preparedAssets = new PreparedAssets();
            prepareAssetsAndRewritePaths(
                    archive,
                    stagedDatabase,
                    new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                            .format(new Date()),
                    preparedAssets
            );
            validateDatabaseOrThrow(stagedDatabase);

            checkpointAndCloseDatabase();
            safetyDirectory = createSafetyBackup(liveDatabase);
            replacementStarted = true;

            deleteSQLiteFiles(liveDatabase);
            deleteSQLiteSidecars(stagedDatabase);
            copyFile(stagedDatabase, liveDatabase);
            deleteSQLiteSidecars(liveDatabase);

            validateDatabaseOrThrow(liveDatabase);
            clearSafetyBackup(safetyDirectory);
            success = true;

            List<String> warnings = new ArrayList<>(archive.warnings);
            warnings.addAll(preparedAssets.warnings);
            return ImportResult.success(
                    archive.versioned,
                    preparedAssets.adminAvatarCount,
                    preparedAssets.studentAvatarCount,
                    preparedAssets.irisRestored,
                    warnings
            );

        } catch (WrongPasswordOrCorruptException | CorruptBackupException exception) {
            Log.e(TAG, "Backup authentication or archive validation failed", exception);
            rollbackIfNecessary(replacementStarted, liveDatabase, safetyDirectory);
            return ImportResult.failure(WRONG_PASSWORD_OR_CORRUPT);

        } catch (IncompatibleBackupException exception) {
            Log.e(TAG, "Backup version is not supported", exception);
            rollbackIfNecessary(replacementStarted, liveDatabase, safetyDirectory);
            return ImportResult.failure(exception.getMessage());

        } catch (Exception exception) {
            Log.e(TAG, "Import failed", exception);
            boolean rollbackSucceeded = rollbackIfNecessary(
                    replacementStarted,
                    liveDatabase,
                    safetyDirectory
            );
            if (replacementStarted && !rollbackSucceeded) {
                return ImportResult.failure(
                        "Khôi phục thất bại và không thể tự động rollback database cũ."
                );
            }
            String message = exception.getMessage();
            return ImportResult.failure(
                    message == null || message.trim().isEmpty()
                            ? "Không thể phục hồi file backup."
                            : message
            );

        } finally {
            if (!success && preparedAssets != null) {
                preparedAssets.cleanup();
            }
            try {
                deleteRecursively(workDirectory);
            } catch (IOException cleanupError) {
                Log.w(TAG, "Could not clean import staging", cleanupError);
            }
        }
    }

    private void copyUriToFile(Uri uri, File destination) throws IOException {
        InputStream rawInput = context.getContentResolver().openInputStream(uri);
        if (rawInput == null) {
            throw new IOException("Không thể đọc file backup đã chọn.");
        }

        long total = 0;
        try (InputStream input = new BufferedInputStream(rawInput);
             FileOutputStream fileOutput = new FileOutputStream(destination);
             OutputStream output = new BufferedOutputStream(fileOutput)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BACKUP_BYTES) {
                    throw new IOException("File backup vượt quá giới hạn dung lượng.");
                }
                output.write(buffer, 0, read);
            }
            output.flush();
            fileOutput.getFD().sync();
        }
        if (total == 0) {
            throw new CorruptBackupIOException();
        }
    }

    private void decryptVersionedBackup(
            File source,
            File destination,
            char[] passphrase
    ) throws WrongPasswordOrCorruptException {
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            EncryptionHelper.decryptBackupStream(input, output, passphrase);
        } catch (GeneralSecurityException | IOException exception) {
            throw new WrongPasswordOrCorruptException(exception);
        }
    }

    private void decryptLegacyBackup(
            File source,
            File destination,
            char[] passphrase
    ) throws WrongPasswordOrCorruptException {
        boolean decrypted = EncryptionHelper.decryptFile(
                source,
                destination,
                new String(passphrase)
        );
        if (!decrypted) {
            throw new WrongPasswordOrCorruptException(null);
        }
    }

    private ArchiveContents prepareLegacyArchive(
            File decryptedFile,
            File extractDirectory,
            File stagedDatabase
    ) throws IOException, CorruptBackupException {
        ArchiveContents contents = new ArchiveContents(false);
        if (!isZipFile(decryptedFile)) {
            copyFile(decryptedFile, stagedDatabase);
            contents.databaseFile = stagedDatabase;
            contents.warnings.add("Backup legacy chỉ chứa database; asset ngoài DB có thể không đầy đủ.");
            return contents;
        }

        ensureDirectory(extractDirectory);
        Map<String, File> extracted = extractRecognizedEntries(
                decryptedFile,
                extractDirectory,
                false
        );
        File database = extracted.get(DATABASE_NAME);
        if (database == null) {
            database = extracted.get("database/" + DATABASE_NAME);
        }
        if (database == null) {
            throw new CorruptBackupException();
        }
        copyFile(database, stagedDatabase);
        contents.databaseFile = stagedDatabase;

        for (Map.Entry<String, File> entry : extracted.entrySet()) {
            if (entry.getKey().startsWith("avatars/")) {
                contents.assets.add(new AssetItem(
                        entry.getValue(),
                        "legacy_admin_avatar",
                        null,
                        entry.getValue().getName()
                ));
            }
        }
        contents.warnings.add("Đã nhập backup .enc legacy; một số asset có thể không tồn tại trong format cũ.");
        return contents;
    }

    private ArchiveContents extractVersionedArchive(
            File zipFile,
            File extractDirectory
    ) throws IOException, CorruptBackupException, IncompatibleBackupException {
        ensureDirectory(extractDirectory);
        Map<String, File> extracted = extractRecognizedEntries(
                zipFile,
                extractDirectory,
                true
        );

        File metadataFile = extracted.get("metadata.json");
        File databaseFile = extracted.get("database/" + DATABASE_NAME);
        if (metadataFile == null || databaseFile == null) {
            throw new CorruptBackupException();
        }

        BackupMetadata metadata;
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(metadataFile),
                StandardCharsets.UTF_8
        )) {
            metadata = new Gson().fromJson(reader, BackupMetadata.class);
        } catch (Exception exception) {
            throw new CorruptBackupException(exception);
        }

        validateMetadata(metadata, extracted);
        ArchiveContents contents = new ArchiveContents(true);
        contents.metadata = metadata;
        contents.databaseFile = databaseFile;
        if (metadata.warnings != null) {
            contents.warnings.addAll(metadata.warnings);
        }

        for (BackupMetadata.Entry metadataEntry : metadata.entries) {
            File extractedFile = extracted.get(normalizeEntryName(metadataEntry.path));
            if ("admin_avatar".equals(metadataEntry.type)) {
                contents.assets.add(new AssetItem(
                        extractedFile,
                        "admin_avatar",
                        metadataEntry.ownerId,
                        extractedFile.getName()
                ));
            } else if ("student_avatar".equals(metadataEntry.type)) {
                contents.assets.add(new AssetItem(
                        extractedFile,
                        "student_avatar",
                        metadataEntry.ownerId,
                        extractedFile.getName()
                ));
            } else if ("iris_repository".equals(metadataEntry.type)) {
                contents.irisRepository = extractedFile;
            }
        }
        return contents;
    }

    private Map<String, File> extractRecognizedEntries(
            File zipFile,
            File extractDirectory,
            boolean versioned
    ) throws IOException, CorruptBackupException {
        Map<String, File> extracted = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int entryCount = 0;
        long totalExtracted = 0;
        String rootPath = extractDirectory.getCanonicalPath() + File.separator;

        try (ZipInputStream zipInput = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new CorruptBackupException();
                }

                String name = normalizeEntryName(entry.getName());
                if (name.isEmpty() || name.startsWith("/") || !seen.add(name)) {
                    throw new CorruptBackupException();
                }
                if (entry.isDirectory()) {
                    zipInput.closeEntry();
                    continue;
                }

                boolean recognized = versioned
                        ? isRecognizedVersionedEntry(name)
                        : isRecognizedLegacyEntry(name);
                if (!recognized) {
                    throw new CorruptBackupException();
                }

                File destination = new File(extractDirectory, name);
                String canonicalDestination = destination.getCanonicalPath();
                if (!canonicalDestination.startsWith(rootPath)) {
                    throw new CorruptBackupException();
                }
                File parent = destination.getParentFile();
                if (parent != null) {
                    ensureDirectory(parent);
                }

                long written = writeZipEntry(zipInput, destination);
                totalExtracted += written;
                if (totalExtracted > MAX_EXTRACTED_BYTES) {
                    throw new CorruptBackupException();
                }
                extracted.put(name, destination);
                zipInput.closeEntry();
            }
        }
        return extracted;
    }

    private long writeZipEntry(ZipInputStream input, File destination)
            throws IOException, CorruptBackupException {
        long total = 0;
        try (FileOutputStream fileOutput = new FileOutputStream(destination);
             OutputStream output = new BufferedOutputStream(fileOutput)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ENTRY_BYTES) {
                    throw new CorruptBackupException();
                }
                output.write(buffer, 0, read);
            }
            output.flush();
            fileOutput.getFD().sync();
        }
        return total;
    }

    private boolean isRecognizedVersionedEntry(String name) {
        return "metadata.json".equals(name)
                || ("database/" + DATABASE_NAME).equals(name)
                || name.startsWith("assets/admins/")
                || name.startsWith("assets/students/")
                || "biometrics/iris/iritechdb.repo".equals(name);
    }

    private boolean isRecognizedLegacyEntry(String name) {
        return DATABASE_NAME.equals(name)
                || ("database/" + DATABASE_NAME).equals(name)
                || (name.startsWith("avatars/") && name.indexOf('/', "avatars/".length()) == -1);
    }

    private String normalizeEntryName(String name) throws CorruptBackupException {
        if (name == null) {
            throw new CorruptBackupException();
        }
        String normalized = name.replace('\\', '/');
        if (normalized.contains("../") || normalized.equals("..")) {
            throw new CorruptBackupException();
        }
        return normalized;
    }

    private void validateMetadata(
            BackupMetadata metadata,
            Map<String, File> extracted
    ) throws CorruptBackupException, IncompatibleBackupException {
        if (metadata == null
                || metadata.backupFormatVersion != BackupMetadata.CURRENT_FORMAT_VERSION
                || metadata.entries == null
                || !context.getPackageName().equals(metadata.applicationId)
                || !DATABASE_NAME.equals(metadata.databaseName)) {
            throw new CorruptBackupException();
        }
        if (metadata.databaseVersion > DatabaseHelper.getCurrentDatabaseVersion()) {
            throw new IncompatibleBackupException(
                    "Backup được tạo bởi database version mới hơn. Hãy cập nhật ứng dụng."
            );
        }

        boolean databaseDeclared = false;
        boolean irisDeclared = false;
        Set<String> declaredPaths = new HashSet<>();
        for (BackupMetadata.Entry entry : metadata.entries) {
            if (entry == null || entry.path == null || entry.type == null) {
                throw new CorruptBackupException();
            }
            String path = normalizeEntryName(entry.path);
            if (!declaredPaths.add(path)) {
                throw new CorruptBackupException();
            }
            File file = extracted.get(path);
            if (file == null || !file.isFile() || file.length() != entry.size) {
                throw new CorruptBackupException();
            }
            String checksum = EncryptionHelper.calculateFileChecksum(file);
            if (checksum == null || !checksum.equals(entry.sha256)) {
                throw new CorruptBackupException();
            }
            if (("database/" + DATABASE_NAME).equals(path)
                    && "database".equals(entry.type)) {
                databaseDeclared = true;
            }
            if ("iris_repository".equals(entry.type)) {
                irisDeclared = true;
            }
        }
        if (!databaseDeclared || metadata.irisRepositoryIncluded != irisDeclared) {
            throw new CorruptBackupException();
        }
    }

    private void prepareAssetsAndRewritePaths(
            ArchiveContents archive,
            File stagedDatabase,
            String importId,
            PreparedAssets prepared
    ) throws IOException, CorruptBackupException {
        File picturesBase = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesBase == null) {
            picturesBase = new File(context.getFilesDir(), "Pictures");
        }

        if (!archive.assets.isEmpty()) {
            prepared.avatarRoot = new File(picturesBase, "restored_" + importId);
            ensureDirectory(prepared.avatarRoot);
        }

        Map<String, String> adminPaths = new HashMap<>();
        Map<String, String> studentPaths = new HashMap<>();
        Map<String, String> legacyPathsByName = new HashMap<>();
        Map<String, String> legacyAdminPaths = new HashMap<>();

        for (AssetItem asset : archive.assets) {
            String folder = "student_avatar".equals(asset.type) ? "students" : "admins";
            String ownerFolder = asset.ownerId == null
                    ? "legacy"
                    : sanitizeSegment(asset.ownerId);
            File targetDirectory = new File(prepared.avatarRoot, folder + File.separator + ownerFolder);
            ensureDirectory(targetDirectory);
            File destination = uniqueFile(targetDirectory, sanitizeSegment(asset.originalName));
            copyFile(asset.source, destination);

            if ("admin_avatar".equals(asset.type)) {
                adminPaths.put(asset.ownerId, destination.getAbsolutePath());
                prepared.adminAvatarCount++;
            } else if ("student_avatar".equals(asset.type)) {
                studentPaths.put(asset.ownerId, destination.getAbsolutePath());
                prepared.studentAvatarCount++;
            } else {
                legacyPathsByName.put(asset.originalName, destination.getAbsolutePath());
                prepared.adminAvatarCount++;
            }
        }

        if (archive.irisRepository != null && archive.irisRepository.isFile()) {
            File irisDirectory = new File(context.getFilesDir(), "iris/imported");
            ensureDirectory(irisDirectory);
            prepared.irisFile = new File(irisDirectory, "iritechdb_" + importId + ".repo");
            copyFile(archive.irisRepository, prepared.irisFile);
            prepared.irisRestored = true;
            prepared.warnings.add(
                    "Iris repository đã được lưu an toàn trong vùng nội bộ; SDK chưa được tự động reload ở Phase 3."
            );
        }

        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    stagedDatabase.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE
            );
            database.beginTransaction();

            // Capture legacy owner-to-file mapping before clearing device-specific paths.
            if (!legacyPathsByName.isEmpty()) {
                try (Cursor cursor = database.rawQuery(
                        "SELECT " + DatabaseHelper.COL_ADMIN_ID + ", "
                                + DatabaseHelper.COL_ADMIN_PHOTO
                                + " FROM " + DatabaseHelper.TABLE_ADMIN,
                        null
                )) {
                    while (cursor.moveToNext()) {
                        String oldPath = cursor.getString(1);
                        if (oldPath == null) {
                            continue;
                        }
                        String replacement = legacyPathsByName.get(new File(oldPath).getName());
                        if (replacement != null) {
                            legacyAdminPaths.put(cursor.getString(0), replacement);
                        }
                    }
                }
            }

            database.execSQL(
                    "UPDATE " + DatabaseHelper.TABLE_ADMIN
                            + " SET " + DatabaseHelper.COL_ADMIN_PHOTO + " = NULL"
            );
            database.execSQL(
                    "UPDATE " + DatabaseHelper.TABLE_STUDENTS
                            + " SET " + DatabaseHelper.COL_PHOTO_PATH + " = NULL"
            );

            for (Map.Entry<String, String> entry : adminPaths.entrySet()) {
                ContentValues values = new ContentValues();
                values.put(DatabaseHelper.COL_ADMIN_PHOTO, entry.getValue());
                int updated = database.update(
                        DatabaseHelper.TABLE_ADMIN,
                        values,
                        DatabaseHelper.COL_ADMIN_ID + " = ?",
                        new String[]{entry.getKey()}
                );
                if (updated != 1) {
                    throw new CorruptBackupException();
                }
            }
            for (Map.Entry<String, String> entry : studentPaths.entrySet()) {
                ContentValues values = new ContentValues();
                values.put(DatabaseHelper.COL_PHOTO_PATH, entry.getValue());
                int updated = database.update(
                        DatabaseHelper.TABLE_STUDENTS,
                        values,
                        DatabaseHelper.COL_STUDENT_ID + " = ?",
                        new String[]{entry.getKey()}
                );
                if (updated != 1) {
                    throw new CorruptBackupException();
                }
            }

            for (Map.Entry<String, String> entry : legacyAdminPaths.entrySet()) {
                ContentValues values = new ContentValues();
                values.put(DatabaseHelper.COL_ADMIN_PHOTO, entry.getValue());
                database.update(
                        DatabaseHelper.TABLE_ADMIN,
                        values,
                        DatabaseHelper.COL_ADMIN_ID + " = ?",
                        new String[]{entry.getKey()}
                );
            }

            database.setTransactionSuccessful();
        } finally {
            if (database != null) {
                if (database.inTransaction()) {
                    database.endTransaction();
                }
                database.close();
            }
        }
    }

    private void migrateStagedDatabase(File databaseFile) throws IOException {
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    databaseFile.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE
            );
            DatabaseHelper.migrateToCurrentSchema(database);
        } catch (Exception exception) {
            throw new IOException("Không thể migrate database backup.", exception);
        } finally {
            if (database != null && database.isOpen()) {
                database.close();
            }
        }
    }

    private void validateDatabaseOrThrow(File databaseFile)
            throws CorruptBackupException {
        DatabaseValidator.ValidationResult result =
                DatabaseValidator.validateRestoreDatabase(databaseFile);
        if (!result.isValid()) {
            Log.e(TAG, "Database validation failed: " + result.getErrorCode()
                    + " / " + result.getMessage());
            throw new CorruptBackupException();
        }
    }

    private void checkpointAndCloseDatabase() {
        try {
            SQLiteDatabase database = dbHelper.getWritableDatabase();
            try (Cursor cursor = database.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                cursor.moveToFirst();
            }
        } catch (Exception exception) {
            Log.w(TAG, "Could not checkpoint live database", exception);
        } finally {
            dbHelper.close();
        }
    }

    private File createSafetyBackup(File liveDatabase) throws IOException {
        File safetyDirectory = new File(context.getNoBackupFilesDir(), "import_safety");
        deleteRecursively(safetyDirectory);
        ensureDirectory(safetyDirectory);
        for (String suffix : SQLITE_SUFFIXES) {
            File source = new File(liveDatabase.getAbsolutePath() + suffix);
            if (source.isFile()) {
                copyFile(source, new File(safetyDirectory, DATABASE_NAME + suffix));
            }
        }
        return safetyDirectory;
    }

    private boolean rollbackIfNecessary(
            boolean replacementStarted,
            File liveDatabase,
            File safetyDirectory
    ) {
        if (!replacementStarted) {
            return true;
        }
        try {
            dbHelper.close();
            deleteSQLiteFiles(liveDatabase);
            if (safetyDirectory == null || !safetyDirectory.isDirectory()) {
                throw new IOException("Safety backup is missing");
            }
            File safetyDatabase = new File(safetyDirectory, DATABASE_NAME);
            if (!safetyDatabase.isFile()) {
                throw new IOException("Safety database is missing");
            }
            for (String suffix : SQLITE_SUFFIXES) {
                File source = new File(safetyDirectory, DATABASE_NAME + suffix);
                if (source.isFile()) {
                    copyFile(source, new File(liveDatabase.getAbsolutePath() + suffix));
                }
            }
            return true;
        } catch (Exception rollbackError) {
            Log.e(TAG, "CRITICAL: rollback failed", rollbackError);
            return false;
        }
    }

    private void clearSafetyBackup(File safetyDirectory) {
        try {
            deleteRecursively(safetyDirectory);
        } catch (IOException exception) {
            Log.w(TAG, "Could not clear safety backup", exception);
        }
    }

    private void deleteSQLiteFiles(File databaseFile) throws IOException {
        dbHelper.close();
        for (String suffix : SQLITE_SUFFIXES) {
            File file = new File(databaseFile.getAbsolutePath() + suffix);
            if (file.exists() && !file.delete()) {
                throw new IOException("Không thể thay thế SQLite file: " + file.getName());
            }
        }
    }

    private void deleteSQLiteSidecars(File databaseFile) throws IOException {
        for (String suffix : new String[]{"-wal", "-shm", "-journal"}) {
            File file = new File(databaseFile.getAbsolutePath() + suffix);
            if (file.exists() && !file.delete()) {
                throw new IOException("Không thể xóa SQLite sidecar: " + file.getName());
            }
        }
    }

    private boolean isZipFile(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            return input.read() == 0x50 && input.read() == 0x4b;
        } catch (IOException exception) {
            return false;
        }
    }

    private void copyFile(File source, File destination) throws IOException {
        if (source == null || !source.isFile()) {
            throw new IOException("Source file is missing");
        }
        File parent = destination.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             FileOutputStream fileOutput = new FileOutputStream(destination);
             OutputStream output = new BufferedOutputStream(fileOutput)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            fileOutput.getFD().sync();
        }
    }

    private void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Không thể tạo thư mục: " + directory.getAbsolutePath());
        }
        if (!directory.isDirectory()) {
            throw new IOException("Đường dẫn không phải thư mục: " + directory.getAbsolutePath());
        }
    }

    private void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Không thể đọc thư mục: " + file.getAbsolutePath());
            }
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            throw new IOException("Không thể xóa: " + file.getAbsolutePath());
        }
    }

    private File uniqueFile(File directory, String requestedName) {
        File candidate = new File(directory, requestedName);
        int index = 1;
        while (candidate.exists()) {
            candidate = new File(directory, index + "_" + requestedName);
            index++;
        }
        return candidate;
    }

    private String sanitizeSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isEmpty() ? "unknown" : sanitized;
    }

    private static final class ArchiveContents {
        final boolean versioned;
        final List<AssetItem> assets = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        BackupMetadata metadata;
        File databaseFile;
        File irisRepository;

        ArchiveContents(boolean versioned) {
            this.versioned = versioned;
        }
    }

    private static final class AssetItem {
        final File source;
        final String type;
        final String ownerId;
        final String originalName;

        AssetItem(File source, String type, String ownerId, String originalName) {
            this.source = source;
            this.type = type;
            this.ownerId = ownerId;
            this.originalName = originalName;
        }
    }

    private static final class PreparedAssets {
        File avatarRoot;
        File irisFile;
        int adminAvatarCount;
        int studentAvatarCount;
        boolean irisRestored;
        final List<String> warnings = new ArrayList<>();

        void cleanup() {
            try {
                deleteTree(avatarRoot);
                if (irisFile != null && irisFile.exists() && !irisFile.delete()) {
                    Log.w(TAG, "Could not remove imported iris repository after failed restore");
                }
            } catch (Exception exception) {
                Log.w(TAG, "Could not clean prepared assets", exception);
            }
        }

        private static void deleteTree(File file) throws IOException {
            if (file == null || !file.exists()) {
                return;
            }
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteTree(child);
                    }
                }
            }
            if (!file.delete()) {
                throw new IOException("Cannot delete prepared asset: " + file.getAbsolutePath());
            }
        }
    }

    public static final class ImportResult {
        private final boolean success;
        private final String message;
        private final boolean versionedFormat;
        private final int adminAvatarCount;
        private final int studentAvatarCount;
        private final boolean irisRestored;
        private final List<String> warnings;

        private ImportResult(
                boolean success,
                String message,
                boolean versionedFormat,
                int adminAvatarCount,
                int studentAvatarCount,
                boolean irisRestored,
                List<String> warnings
        ) {
            this.success = success;
            this.message = message;
            this.versionedFormat = versionedFormat;
            this.adminAvatarCount = adminAvatarCount;
            this.studentAvatarCount = studentAvatarCount;
            this.irisRestored = irisRestored;
            this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        }

        static ImportResult success(
                boolean versionedFormat,
                int adminAvatarCount,
                int studentAvatarCount,
                boolean irisRestored,
                List<String> warnings
        ) {
            return new ImportResult(
                    true,
                    "Phục hồi dữ liệu thành công.",
                    versionedFormat,
                    adminAvatarCount,
                    studentAvatarCount,
                    irisRestored,
                    warnings
            );
        }

        static ImportResult failure(String message) {
            return new ImportResult(false, message, false, 0, 0, false, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public boolean isVersionedFormat() {
            return versionedFormat;
        }

        public int getAdminAvatarCount() {
            return adminAvatarCount;
        }

        public int getStudentAvatarCount() {
            return studentAvatarCount;
        }

        public boolean isIrisRestored() {
            return irisRestored;
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
    }

    private static final class WrongPasswordOrCorruptException extends Exception {
        WrongPasswordOrCorruptException(Throwable cause) {
            super(cause);
        }
    }

    private static final class CorruptBackupException extends Exception {
        CorruptBackupException() {
            super();
        }

        CorruptBackupException(Throwable cause) {
            super(cause);
        }
    }

    private static final class IncompatibleBackupException extends Exception {
        IncompatibleBackupException(String message) {
            super(message);
        }
    }

    /** Marker IOException used while copying an empty SAF document. */
    private static final class CorruptBackupIOException extends IOException {
        CorruptBackupIOException() {
            super(WRONG_PASSWORD_OR_CORRUPT);
        }
    }
}
