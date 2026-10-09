package vn.ticketscenter.identity.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityContextTest {

    @Test
    @DisplayName("SecurityContext constants must conform to specifications")
    void testSecurityContextConstants() {
        assertEquals("TC_CURRENT_USER", SecurityContext.SESSION_USER_ATTRIBUTE);
        assertEquals("TC_REQUEST_USER", SecurityContext.REQUEST_USER_ATTRIBUTE);
        assertEquals("TC_CSRF_TOKEN", SecurityContext.CSRF_TOKEN_ATTRIBUTE);
        assertEquals("X-CSRF-Token", SecurityContext.CSRF_HEADER_NAME);
    }

    @Test
    @DisplayName("getCurrentUser returns empty when request is null")
    void testGetCurrentUserNullRequest() {
        assertTrue(SecurityContext.getCurrentUser(null).isEmpty());
    }

    @Test
    @DisplayName("requireCurrentUser throws IllegalArgumentException when unauthenticated")
    void testRequireCurrentUserThrowsWhenUnauthenticated() {
        assertThrows(IllegalArgumentException.class, () ->
                SecurityContext.requireCurrentUser(null));
    }
}
