package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherUpgradeTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void newHashesVerifyAndDoNotNeedRehash() {
        String stored = hasher.hash("correct horse");
        assertTrue(stored.startsWith("v2:600000:"));
        assertTrue(hasher.verify("correct horse", stored));
        assertFalse(hasher.verify("wrong", stored));
        assertFalse(hasher.needsRehash(stored));
    }

    @Test
    void legacyHashesStillVerifyAndAreFlaggedForUpgrade() throws Exception {
        byte[] salt = "0123456789abcdef".getBytes();
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec("legacy".toCharArray(), salt, 10000, 256)).getEncoded();
        String stored = Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        assertTrue(hasher.verify("legacy", stored));
        assertTrue(hasher.needsRehash(stored));
    }

    @Test
    void malformedHashesAreRejected() {
        assertFalse(hasher.verify("x", "not-a-hash"));
        assertFalse(hasher.verify("x", "v2:abc:AAAA:AAAA"));
        assertFalse(hasher.verify("x", "v2:1:AAAA:AAAA"));
    }
}
