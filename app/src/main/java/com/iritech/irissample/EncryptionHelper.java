package com.iritech.irissample;

import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Helper class để mã hóa và giải mã file sử dụng AES-256-GCM
 * Dùng cho tính năng Backup/Restore Database an toàn
 * 
 * Security Features:
 * - AES-256 encryption (mạnh nhất trong AES)
 * - GCM mode (Galois/Counter Mode) - chống giả mạo dữ liệu
 * - PBKDF2 để derive key từ password (chống brute-force)
 * - Random salt và IV cho mỗi lần mã hóa (chống rainbow table)
 * 
 * File format sau khi mã hóa:
 * [16 bytes Salt][12 bytes IV][Encrypted Data]
 */
public class EncryptionHelper {
    
    private static final String TAG = "EncryptionHelper";
    
    // Cấu hình mã hóa
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256; // AES-256
    private static final int GCM_TAG_LENGTH = 128; // 128 bits = 16 bytes
    private static final int GCM_IV_LENGTH = 12; // 12 bytes IV cho GCM
    private static final int SALT_LENGTH = 16; // 16 bytes salt
    private static final byte[] BACKUP_MAGIC = {
            'I', 'R', 'I', 'B', 'K', 'P', '0', '1'
    };

    private static final int BACKUP_FORMAT_VERSION = 1;

    private static final int BACKUP_PBKDF2_ITERATIONS = 210000;
    
    // Cấu hình PBKDF2 (Password-Based Key Derivation Function)
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 100000; // 100,000 iterations (khuyến nghị hiện tại)
    
    /**
     * Mã hóa file sử dụng password
     * 
     * @param inputFile File gốc cần mã hóa (database file)
     * @param outputFile File đầu ra sau khi mã hóa (.enc)
     * @param password Mật khẩu để mã hóa (Super Admin password)
     * @return true nếu mã hóa thành công, false nếu thất bại
     */

    /**
     * Mã hóa ZIP thành format .iribackup.
     *
     * Method không đóng input hoặc output stream.
     */
    public static void encryptBackupStream(
            InputStream input,
            OutputStream output,
            char[] passphrase
    ) throws IOException, GeneralSecurityException {
        if (input == null || output == null) {
            throw new IllegalArgumentException(
                    "Input and output streams are required"
            );
        }

        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException(
                    "Backup passphrase cannot be empty"
            );
        }

        byte[] salt = generateRandomBytes(SALT_LENGTH);
        byte[] iv = generateRandomBytes(GCM_IV_LENGTH);

        SecretKey key = deriveKeyFromPassword(
                passphrase,
                salt,
                BACKUP_PBKDF2_ITERATIONS
        );

        // Tạo header trước để vừa ghi ra file vừa dùng làm GCM AAD.
        ByteArrayOutputStream headerBuffer =
                new ByteArrayOutputStream();

        DataOutputStream headerOutput =
                new DataOutputStream(headerBuffer);

        headerOutput.write(BACKUP_MAGIC);
        headerOutput.writeInt(BACKUP_FORMAT_VERSION);
        headerOutput.writeInt(BACKUP_PBKDF2_ITERATIONS);

        headerOutput.writeInt(salt.length);
        headerOutput.write(salt);

        headerOutput.writeInt(iv.length);
        headerOutput.write(iv);

        headerOutput.flush();

        byte[] header = headerBuffer.toByteArray();

        output.write(header);

        Cipher cipher = Cipher.getInstance(ALGORITHM);

        GCMParameterSpec gcmSpec = new GCMParameterSpec(
                GCM_TAG_LENGTH,
                iv
        );

        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                gcmSpec
        );

        // Header không được mã hóa nhưng được xác thực bởi GCM.
        cipher.updateAAD(header);

        byte[] inputBuffer = new byte[64 * 1024];
        int bytesRead;

        while ((bytesRead = input.read(inputBuffer)) != -1) {
            byte[] encryptedChunk = cipher.update(
                    inputBuffer,
                    0,
                    bytesRead
            );

            if (encryptedChunk != null &&
                    encryptedChunk.length > 0) {
                output.write(encryptedChunk);
            }
        }

        byte[] finalChunk = cipher.doFinal();

        if (finalChunk != null && finalChunk.length > 0) {
            output.write(finalChunk);
        }

        output.flush();

        Arrays.fill(inputBuffer, (byte) 0);
        Arrays.fill(header, (byte) 0);
    }

    /**
     * Returns true when the file starts with the versioned .iribackup header.
     */
    public static boolean isVersionedBackup(File file) {
        if (file == null || !file.isFile() || file.length() < BACKUP_MAGIC.length) {
            return false;
        }

        byte[] actual = new byte[BACKUP_MAGIC.length];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < actual.length) {
                int read = input.read(actual, offset, actual.length - offset);
                if (read == -1) {
                    return false;
                }
                offset += read;
            }
            return Arrays.equals(actual, BACKUP_MAGIC);
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Decrypts the versioned .iribackup stream. The caller owns both streams.
     * Authentication failure is exposed as GeneralSecurityException so the UI
     * can report a wrong passphrase or a tampered backup without replacing data.
     */
    public static void decryptBackupStream(
            InputStream input,
            OutputStream output,
            char[] passphrase
    ) throws IOException, GeneralSecurityException {
        if (input == null || output == null) {
            throw new IllegalArgumentException("Input and output streams are required");
        }
        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("Backup passphrase cannot be empty");
        }

        DataInputStream dataInput = new DataInputStream(input);
        byte[] magic = new byte[BACKUP_MAGIC.length];
        dataInput.readFully(magic);
        if (!Arrays.equals(magic, BACKUP_MAGIC)) {
            throw new IOException("Unsupported backup header");
        }

        int formatVersion = dataInput.readInt();
        if (formatVersion != BACKUP_FORMAT_VERSION) {
            throw new IOException("Unsupported backup format version: " + formatVersion);
        }

        int iterations = dataInput.readInt();
        if (iterations < 10_000 || iterations > 2_000_000) {
            throw new IOException("Invalid PBKDF2 iteration count");
        }

        int saltLength = dataInput.readInt();
        if (saltLength < 8 || saltLength > 64) {
            throw new IOException("Invalid backup salt length");
        }
        byte[] salt = new byte[saltLength];
        dataInput.readFully(salt);

        int ivLength = dataInput.readInt();
        if (ivLength != GCM_IV_LENGTH) {
            throw new IOException("Invalid backup IV length");
        }
        byte[] iv = new byte[ivLength];
        dataInput.readFully(iv);

        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        DataOutputStream headerOutput = new DataOutputStream(headerBuffer);
        headerOutput.write(magic);
        headerOutput.writeInt(formatVersion);
        headerOutput.writeInt(iterations);
        headerOutput.writeInt(salt.length);
        headerOutput.write(salt);
        headerOutput.writeInt(iv.length);
        headerOutput.write(iv);
        headerOutput.flush();
        byte[] header = headerBuffer.toByteArray();

        SecretKey key = deriveKeyFromPassword(passphrase, salt, iterations);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(GCM_TAG_LENGTH, iv)
        );
        cipher.updateAAD(header);

        byte[] encryptedBuffer = new byte[64 * 1024];
        int bytesRead;
        try {
            while ((bytesRead = dataInput.read(encryptedBuffer)) != -1) {
                byte[] decryptedChunk = cipher.update(encryptedBuffer, 0, bytesRead);
                if (decryptedChunk != null && decryptedChunk.length > 0) {
                    output.write(decryptedChunk);
                }
            }

            byte[] finalChunk = cipher.doFinal();
            if (finalChunk != null && finalChunk.length > 0) {
                output.write(finalChunk);
            }
            output.flush();
        } finally {
            Arrays.fill(encryptedBuffer, (byte) 0);
            Arrays.fill(header, (byte) 0);
        }
    }

    public static boolean encryptFile(File inputFile, File outputFile, String password) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        
        try {
            // 1. Tạo Salt ngẫu nhiên (16 bytes)
            byte[] salt = generateRandomBytes(SALT_LENGTH);
            
            // 2. Tạo IV (Initialization Vector) ngẫu nhiên (12 bytes)
            byte[] iv = generateRandomBytes(GCM_IV_LENGTH);
            
            // 3. Derive key từ password + salt
            SecretKey key = deriveKeyFromPassword(password, salt);
            
            // 4. Khởi tạo Cipher với GCM mode
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            // 5. Đọc file input
            fis = new FileInputStream(inputFile);
            byte[] inputBytes = new byte[(int) inputFile.length()];
            fis.read(inputBytes);
            
            // 6. Mã hóa dữ liệu
            byte[] encryptedBytes = cipher.doFinal(inputBytes);
            
            // 7. Ghi file output: [Salt][IV][Encrypted Data]
            fos = new FileOutputStream(outputFile);
            fos.write(salt); // Ghi salt trước
            fos.write(iv);   // Ghi IV sau
            fos.write(encryptedBytes); // Ghi dữ liệu đã mã hóa
            
            Log.d(TAG, "File encrypted successfully: " + outputFile.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return false;
        } finally {
            closeQuietly(fis);
            closeQuietly(fos);
        }
    }
    
    /**
     * Giải mã file sử dụng password
     * 
     * @param inputFile File đã mã hóa (.enc)
     * @param outputFile File đầu ra sau khi giải mã (database file)
     * @param password Mật khẩu để giải mã
     * @return true nếu giải mã thành công, false nếu thất bại (sai password hoặc file hỏng)
     */
    public static boolean decryptFile(File inputFile, File outputFile, String password) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        
        try {
            // 1. Đọc file input
            fis = new FileInputStream(inputFile);
            
            // 2. Đọc Salt (16 bytes đầu)
            byte[] salt = new byte[SALT_LENGTH];
            if (fis.read(salt) != SALT_LENGTH) {
                Log.e(TAG, "Invalid file format: cannot read salt");
                return false;
            }
            
            // 3. Đọc IV (12 bytes tiếp theo)
            byte[] iv = new byte[GCM_IV_LENGTH];
            if (fis.read(iv) != GCM_IV_LENGTH) {
                Log.e(TAG, "Invalid file format: cannot read IV");
                return false;
            }
            
            // 4. Đọc phần dữ liệu đã mã hóa (phần còn lại)
            int encryptedLength = (int) inputFile.length() - SALT_LENGTH - GCM_IV_LENGTH;
            byte[] encryptedBytes = new byte[encryptedLength];
            if (fis.read(encryptedBytes) != encryptedLength) {
                Log.e(TAG, "Invalid file format: cannot read encrypted data");
                return false;
            }
            
            // 5. Derive key từ password + salt
            SecretKey key = deriveKeyFromPassword(password, salt);
            
            // 6. Khởi tạo Cipher để giải mã
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            // 7. Giải mã dữ liệu
            // Nếu password sai hoặc dữ liệu bị sửa đổi, dòng này sẽ throw Exception
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            // 8. Ghi file output
            fos = new FileOutputStream(outputFile);
            fos.write(decryptedBytes);
            
            Log.d(TAG, "File decrypted successfully: " + outputFile.getAbsolutePath());
            return true;
            
        } catch (javax.crypto.AEADBadTagException e) {
            // Exception đặc biệt của GCM - có nghĩa là password sai hoặc dữ liệu bị giả mạo
            Log.e(TAG, "Decryption failed: Wrong password or tampered data", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return false;
        } finally {
            closeQuietly(fis);
            closeQuietly(fos);
        }
    }
    
    /**
     * Tạo SecretKey từ password sử dụng PBKDF2
     * PBKDF2 giúp biến password yếu thành key mạnh và chống brute-force
     * 
     * @param password Password từ user
     * @param salt Salt ngẫu nhiên (16 bytes)
     * @return SecretKey để dùng cho AES
     */
    private static SecretKey deriveKeyFromPassword(
            String password,
            byte[] salt
    ) throws Exception {
        char[] passwordChars = password.toCharArray();

        try {
            return deriveKeyFromPassword(
                    passwordChars,
                    salt,
                    PBKDF2_ITERATIONS
            );
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private static SecretKey deriveKeyFromPassword(
            char[] passphrase,
            byte[] salt,
            int iterations
    ) throws GeneralSecurityException {
        PBEKeySpec keySpec = new PBEKeySpec(
                passphrase,
                salt,
                iterations,
                KEY_SIZE
        );

        byte[] keyBytes = null;

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(
                            PBKDF2_ALGORITHM
                    );

            keyBytes = factory
                    .generateSecret(keySpec)
                    .getEncoded();

            return new SecretKeySpec(
                    keyBytes,
                    "AES"
            );

        } finally {
            keySpec.clearPassword();

            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }
    
    /**
     * Tạo byte array ngẫu nhiên
     * Dùng SecureRandom để đảm bảo tính ngẫu nhiên cao (cryptographically secure)
     */
    private static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SecureRandom random = new SecureRandom();
        random.nextBytes(bytes);
        return bytes;
    }
    
    /**
     * Đóng stream một cách an toàn (không throw exception)
     */
    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    /**
     * Hash password sử dụng SHA-256
     * Dùng để verify password mà không cần lưu plaintext
     * (Tuy nhiên, để giải mã file backup vẫn cần password gốc)
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * Tạo checksum cho file để verify integrity
     * Dùng để kiểm tra file có bị hỏng không (trước khi thử giải mã)
     */
    public static String calculateFileChecksum(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(file);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            fis.close();
            
            byte[] hash = digest.digest();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate checksum", e);
            return null;
        }
    }
}
