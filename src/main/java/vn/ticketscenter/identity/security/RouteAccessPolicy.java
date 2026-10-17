package vn.ticketscenter.identity.security;

import vn.ticketscenter.identity.model.OrganizationRole;

/**
 * The single statement of which signed-in user may open which page. {@link RoleAuthorizationFilter} enforces it
 * on every request and the login redirect uses it so a user is never sent to a page the filter would refuse.
 *
 * <ul>
 *   <li>{@code /admin/*}: admins only.</li>
 *   <li>{@code /organizer/request}: every signed-in user (anyone may apply to become an organiser).</li>
 *   <li>{@code /organizer/*}: admins and organisation managers; check-in staff only {@code /organizer/checkin*};
 *       users without an organisation role never.</li>
 *   <li>everything else: allowed.</li>
 * </ul>
 */
public final class RouteAccessPolicy {

    /** What to do with a request for a protected route. */
    public enum Decision {
        ALLOW,
        /** The page belongs to the admin area and the user is not an admin. */
        DENY_ADMIN_AREA,
        /** The page belongs to the organiser area and the user has no organisation role. */
        DENY_ORGANIZER_AREA,
        /** Check-in staff asked for an organiser page that is not a check-in page. */
        REDIRECT_TO_CHECK_IN
    }

    public static final String CHECK_IN_HOME = "/organizer/checkin-select";

    private static final String ADMIN_AREA = "/admin";
    private static final String ORGANIZER_AREA = "/organizer";
    private static final String ORGANIZER_APPLICATION = "/organizer/request";
    private static final String CHECK_IN_PREFIX = "/organizer/checkin";

    private RouteAccessPolicy() {}

    /** @param path the request path relative to the application context, without query string */
    public static Decision decide(UserPrincipal user, String path) {
        if (path.equals(ORGANIZER_APPLICATION)) {
            return Decision.ALLOW;
        }
        if (isWithin(path, ADMIN_AREA)) {
            return user.isAdmin() ? Decision.ALLOW : Decision.DENY_ADMIN_AREA;
        }
        if (isWithin(path, ORGANIZER_AREA)) {
            return decideOrganizerArea(user, path);
        }
        return Decision.ALLOW;
    }

    public static boolean canOpen(UserPrincipal user, String path) {
        return decide(user, path) == Decision.ALLOW;
    }

    private static Decision decideOrganizerArea(UserPrincipal user, String path) {
        if (user.isAdmin()) {
            return Decision.ALLOW;
        }
        if (user.isCustomerOnly()) {
            return Decision.DENY_ORGANIZER_AREA;
        }
        if (user.organizationRole() == OrganizationRole.CHECK_IN_STAFF
                && !path.startsWith(CHECK_IN_PREFIX)) {
            return Decision.REDIRECT_TO_CHECK_IN;
        }
        return Decision.ALLOW;
    }

    private static boolean isWithin(String path, String area) {
        return path.equals(area) || path.startsWith(area + "/");
    }
}
