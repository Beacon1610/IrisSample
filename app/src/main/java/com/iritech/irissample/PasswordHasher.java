package com.iritech.irissample;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing utility with backward-compatible verification.
 *
 * <p>New values use a salted PBKDF2 format. Existing admin SHA-256 hashes and
 * existing student plain-text passwords are only accepted for migration and
 * should be replaced immediately after a successful verification.</p>
 */
public final class PasswordHasher {
    private static final String PREFIX_SHA256 = "pbkdf2_sha256";
    private static final String PREFIX_SHA1 = "pbkdf2_sha1";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    private PasswordHasher() {
    }

    public static String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }

        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);

        String algorithm = "PBKDF2WithHmacSHA256";
        String prefix = PREFIX_SHA256;
        byte[] derived;
        try {
            derived = derive(password, salt, ITERATIONS, algorithm);
        } catch (NoSuchAlgorithmException e) {
            // PBKDF2WithHmacSHA256 is unavailable on a small number of older
            // Android providers. PBKDF2WithHmacSHA1 remains a salted, iterated
            // fallback and its identifier is stored for future verification.
            algorithm = "PBKDF2WithHmacSHA1";
            prefix = PREFIX_SHA1;
            try {
                derived = derive(password, salt, ITERATIONS, algorithm);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException fallbackError) {
                throw new IllegalStateException("No supported PBKDF2 provider", fallbackError);
            }
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive password hash", e);
        }

        return prefix + "$" + ITERATIONS + "$" + encode(salt) + "$" + encode(derived);
    }

    public static boolean verify(String password, String storedValue) {
        if (password == null || storedValue == null) {
            return false;
        }

        String[] parts = storedValue.split("\\$", -1);
        if (parts.length != 4) {
            return false;
        }

        String algorithm;
        if (PREFIX_SHA256.equals(parts[0])) {
            algorithm = "PBKDF2WithHmacSHA256";
        } else if (PREFIX_SHA1.equals(parts[0])) {
            algorithm = "PBKDF2WithHmacSHA1";
        } else {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 10_000 || iterations > 2_000_000) {
                return false;
            }

            byte[] salt = decode(parts[2]);
            byte[] expected = decode(parts[3]);
            if (salt.length < 8 || expected.length < 16) {
                return false;
            }

            byte[] actual = derive(password, salt, iterations, algorithm);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            return false;
        }
    }

    public static boolean isModernHash(String storedValue) {
        return storedValue != null
                && (storedValue.startsWith(PREFIX_SHA256 + "$")
                || storedValue.startsWith(PREFIX_SHA1 + "$"));
    }

    public static boolean verifyLegacyAdminSha256(String password, String storedValue) {
        if (password == null || storedValue == null || !storedValue.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return MessageDigest.isEqual(
                    storedValue.toLowerCase(Locale.US).getBytes(StandardCharsets.US_ASCII),
                    hex.toString().getBytes(StandardCharsets.US_ASCII)
            );
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    public static boolean verifyLegacyPlainText(String password, String storedValue) {
        if (password == null || storedValue == null || isModernHash(storedValue)) {
            return false;
        }
        return MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8),
                storedValue.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static byte[] derive(
            String password,
            byte[] salt,
            int iterations,
            String algorithm
    ) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(byte[] value) {
        return Base64.encodeToString(value, Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static byte[] decode(String value) {
        return Base64.decode(value, Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
