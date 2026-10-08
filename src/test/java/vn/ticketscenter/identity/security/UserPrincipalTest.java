package vn.ticketscenter.identity.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class UserPrincipalTest {

    @Test
    @DisplayName("UserPrincipal must preserve actorId, platformRole, and authVersion")
    void testUserPrincipalCreation() {
        UUID actorId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1);

        assertEquals(actorId, principal.actorId());
        assertEquals(PlatformRole.CUSTOMER, principal.platformRole());
        assertEquals(1, principal.authVersion());
        assertFalse(principal.isAdmin());
    }

    @Test
    @DisplayName("UserPrincipal with ADMIN platformRole returns isAdmin true")
    void testUserPrincipalAdmin() {
        UUID actorId = UUID.randomUUID();
        UserPrincipal adminPrincipal = new UserPrincipal(actorId, PlatformRole.ADMIN, 0);

        assertTrue(adminPrincipal.isAdmin());
    }

    @Test
    @DisplayName("UserPrincipal throws NullPointerException if actorId or platformRole is null")
    void testUserPrincipalNullGuards() {
        assertThrows(NullPointerException.class, () ->
                new UserPrincipal(null, PlatformRole.CUSTOMER, 0));

        assertThrows(NullPointerException.class, () ->
                new UserPrincipal(UUID.randomUUID(), null, 0));
    }
}
