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
    @DisplayName("UserPrincipal with organization roles and helper checks")
    void testUserPrincipalOrganizationRoles() {
        UUID actorId = UUID.randomUUID();
        UserPrincipal customer = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 0, null);
        assertTrue(customer.isCustomerOnly());
        assertFalse(customer.isAdmin());
        assertFalse(customer.isManager());
        assertFalse(customer.isStaff());

        UserPrincipal staff = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 0, vn.ticketscenter.identity.model.OrganizationRole.CHECK_IN_STAFF);
        assertFalse(staff.isCustomerOnly());
        assertFalse(staff.isAdmin());
        assertFalse(staff.isManager());
        assertTrue(staff.isStaff());

        UserPrincipal manager = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 0, vn.ticketscenter.identity.model.OrganizationRole.MANAGER);
        assertFalse(manager.isCustomerOnly());
        assertFalse(manager.isAdmin());
        assertTrue(manager.isManager());
        assertTrue(manager.isStaff());

        UserPrincipal admin = new UserPrincipal(actorId, PlatformRole.ADMIN, 0, null);
        assertFalse(admin.isCustomerOnly());
        assertTrue(admin.isAdmin());
        assertTrue(admin.isManager());
        assertTrue(admin.isStaff());
    }
}
