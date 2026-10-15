package vn.ticketscenter.identity.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRedirectTest {
    private final vn.ticketscenter.identity.security.UserPrincipal customer = new vn.ticketscenter.identity.security.UserPrincipal(
            java.util.UUID.randomUUID(), vn.ticketscenter.identity.model.PlatformRole.CUSTOMER, 1);

    @Test void customerReturnsToOrganizationApplicationAndNormalSearch() {
        assertTrue(LoginServlet.isRoleRedirectAllowed(customer, "/ticketscenter/organizer/request?step=1", "/ticketscenter"));
        assertTrue(LoginServlet.isRoleRedirectAllowed(customer, "/ticketscenter/?keyword=/admin", "/ticketscenter"));
        assertFalse(LoginServlet.isRoleRedirectAllowed(customer, "/ticketscenter/admin/events", "/ticketscenter"));
        assertFalse(LoginServlet.isRoleRedirectAllowed(customer, "/ticketscenter/organizer/coupons", "/ticketscenter"));
        assertFalse(LoginServlet.isRoleRedirectAllowed(customer, "/ticketscenter/%61dmin/events", "/ticketscenter"));
    }

    @Test void staffReturnsToCheckinButCannotEnterOtherManagementViews() {
        var staff = new vn.ticketscenter.identity.security.UserPrincipal(java.util.UUID.randomUUID(),
                vn.ticketscenter.identity.model.PlatformRole.CUSTOMER, 1, vn.ticketscenter.identity.model.OrganizationRole.CHECK_IN_STAFF);
        assertTrue(LoginServlet.isRoleRedirectAllowed(staff, "/ticketscenter/organizer/checkin?eventId=123", "/ticketscenter"));
        assertFalse(LoginServlet.isRoleRedirectAllowed(staff, "/ticketscenter/organizer/coupons", "/ticketscenter"));
        assertFalse(LoginServlet.isRoleRedirectAllowed(staff, "/ticketscenter/admin", "/ticketscenter"));
    }
    @Test
    void acceptsPathsInsideTheApplication() {
        assertTrue(LoginServlet.isLocalRedirect("/ticketscenter/me/orders?page=2", "/ticketscenter"));
        assertTrue(LoginServlet.isLocalRedirect("/me/orders", ""));
    }

    @Test
    void rejectsTargetsThatLeaveTheApplication() {
        for (String target : new String[]{null, "", "https://evil.example", "//evil.example", "/\\evil.example",
                "javascript:alert(1)", "/other-app/page", "/ticketscenter-evil", "/ticketscenter/a\r\nSet-Cookie: x=1"}) {
            assertFalse(LoginServlet.isLocalRedirect(target, "/ticketscenter"), String.valueOf(target));
        }
    }
}
