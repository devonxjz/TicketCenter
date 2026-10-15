package vn.ticketscenter.identity.security;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.RouteAccessPolicy.Decision;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteAccessPolicyTest {

    private static UserPrincipal user(PlatformRole platform, OrganizationRole organization) {
        return new UserPrincipal(UUID.randomUUID(), platform, 1, organization);
    }

    private final UserPrincipal admin = user(PlatformRole.ADMIN, null);
    private final UserPrincipal manager = user(PlatformRole.CUSTOMER, OrganizationRole.MANAGER);
    private final UserPrincipal staff = user(PlatformRole.CUSTOMER, OrganizationRole.CHECK_IN_STAFF);
    private final UserPrincipal customer = user(PlatformRole.CUSTOMER, null);

    @Test
    void adminAreaIsForAdminsOnly() {
        assertEquals(Decision.ALLOW, RouteAccessPolicy.decide(admin, "/admin/dashboard"));
        for (UserPrincipal other : new UserPrincipal[]{manager, staff, customer}) {
            assertEquals(Decision.DENY_ADMIN_AREA, RouteAccessPolicy.decide(other, "/admin"));
            assertEquals(Decision.DENY_ADMIN_AREA, RouteAccessPolicy.decide(other, "/admin/events"));
        }
    }

    @Test
    void everyoneMayApplyToBecomeAnOrganizer() {
        for (UserPrincipal anyone : new UserPrincipal[]{admin, manager, staff, customer}) {
            assertEquals(Decision.ALLOW, RouteAccessPolicy.decide(anyone, "/organizer/request"));
        }
    }

    @Test
    void organizerAreaFollowsTheOrganizationRole() {
        assertEquals(Decision.ALLOW, RouteAccessPolicy.decide(admin, "/organizer/dashboard"));
        assertEquals(Decision.ALLOW, RouteAccessPolicy.decide(manager, "/organizer/dashboard"));
        assertEquals(Decision.ALLOW, RouteAccessPolicy.decide(manager, "/organizer/checkin-select"));
        assertEquals(Decision.DENY_ORGANIZER_AREA, RouteAccessPolicy.decide(customer, "/organizer/dashboard"));
        assertEquals(Decision.DENY_ORGANIZER_AREA, RouteAccessPolicy.decide(customer, "/organizer/checkin-select"));
    }

    @Test
    void checkInStaffAreKeptToCheckInPages() {
        assertEquals(Decision.ALLOW, RouteAccessPolicy.decide(staff, "/organizer/checkin"));
        assertEquals(Decision.ALLOW, RouteAccessPolicy.decide(staff, "/organizer/checkin-select"));
        assertEquals(Decision.ALLOW, RouteAccessPolicy.decide(staff, "/organizer/checkin-scanner"));
        assertEquals(Decision.REDIRECT_TO_CHECK_IN, RouteAccessPolicy.decide(staff, "/organizer/dashboard"));
        assertEquals(Decision.REDIRECT_TO_CHECK_IN, RouteAccessPolicy.decide(staff, "/organizer"));
    }

    @Test
    void otherRoutesAreOpen() {
        assertTrue(RouteAccessPolicy.canOpen(customer, "/"));
        assertTrue(RouteAccessPolicy.canOpen(customer, "/events/42"));
        assertTrue(RouteAccessPolicy.canOpen(customer, "/administration-notes"));
        assertFalse(RouteAccessPolicy.canOpen(customer, "/admin/x"));
    }
}
