package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityValidatorTest {

    @Test
    void normalizerTrimsAndLowerCases() {
        assertEquals("a@b.vn", EmailNormalizer.normalize("  A@B.vn "));
        assertEquals("", EmailNormalizer.normalize(null));
    }

    @Test
    void emailMustContainAtAndFitTheColumn() {
        assertDoesNotThrow(() -> IdentityValidator.requireEmail("user@test.vn"));
        assertThrows(IllegalArgumentException.class, () -> IdentityValidator.requireEmail(""));
        assertThrows(IllegalArgumentException.class, () -> IdentityValidator.requireEmail("no-at-sign"));
        assertThrows(IllegalArgumentException.class,
                () -> IdentityValidator.requireEmail("a".repeat(320) + "@x"));
    }

    @Test
    void userNameLengthIsThreeToOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> IdentityValidator.requireUserName("ab"));
        assertDoesNotThrow(() -> IdentityValidator.requireUserName("abc"));
        assertThrows(IllegalArgumentException.class, () -> IdentityValidator.requireUserName("a".repeat(101)));
    }

    @Test
    void fullNameAndPasswordLimits() {
        assertThrows(IllegalArgumentException.class, () -> IdentityValidator.requireFullName(" "));
        assertThrows(IllegalArgumentException.class, () -> IdentityValidator.requireFullName("a".repeat(201)));
        assertThrows(IllegalArgumentException.class, () -> IdentityValidator.requirePassword("short"));
        assertDoesNotThrow(() -> IdentityValidator.requirePassword("a".repeat(12)));
        assertThrows(IllegalArgumentException.class, () -> IdentityValidator.requirePassword("a".repeat(257)));
    }
}
