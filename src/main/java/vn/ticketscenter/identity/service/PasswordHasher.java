package vn.ticketscenter.identity.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256. New hashes are stored as {@code v2:<iterations>:<salt>:<hash>}; the original
 * {@code <salt>:<hash>} form (10,000 iterations) still verifies and is reported by {@link #needsRehash}.
 */
public class PasswordHasher {
    private static final int LEGACY_ITERATIONS = 10000;
    private static final int ITERATIONS = 600_000;
    private static final int KEY_LENGTH = 256;
    private static final String PREFIX = "v2";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS);
        return PREFIX
                + ":"
                + ITERATIONS
                + ":"
                + Base64.getEncoder().encodeToString(salt)
                + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    public boolean verify(String password, String storedHash) {
        if (password == null || storedHash == null) return false;
        String[] parts = storedHash.split(":");
        try {
            int iterations;
            byte[] salt;
            byte[] hash;
            if (parts.length == 4 && PREFIX.equals(parts[0])) {
                iterations = Integer.parseInt(parts[1]);
                if (iterations < LEGACY_ITERATIONS || iterations > 10_000_000) return false;
                salt = Base64.getDecoder().decode(parts[2]);
                hash = Base64.getDecoder().decode(parts[3]);
            } else if (parts.length == 2) {
                iterations = LEGACY_ITERATIONS;
                salt = Base64.getDecoder().decode(parts[0]);
                hash = Base64.getDecoder().decode(parts[1]);
            } else {
                return false;
            }
            return java.security.MessageDigest.isEqual(
                    hash, pbkdf2(password.toCharArray(), salt, iterations));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    /** True when the stored hash was produced with fewer iterations than the current setting. */
    public boolean needsRehash(String storedHash) {
        if (storedHash == null) return false;
        String[] parts = storedHash.split(":");
        if (parts.length == 2) return true;
        try {
            return parts.length == 4
                    && PREFIX.equals(parts[0])
                    && Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException malformed) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
}
