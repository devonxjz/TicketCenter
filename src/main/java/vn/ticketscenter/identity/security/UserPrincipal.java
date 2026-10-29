package vn.ticketscenter.identity.security;

import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.PlatformRole;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The current user.
 * Kept in the HttpSession and set as an HttpServletRequest attribute
 * so other features (order, event, refund, ticketing, ...) check access
 * without each reading the session its own way.
 */
public record UserPrincipal(
        UUID actorId,
        PlatformRole platformRole,
        int authVersion,
        OrganizationRole organizationRole,
        String email)
        implements Serializable {

    public UserPrincipal {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(platformRole, "platformRole must not be null");
    }

    public UserPrincipal(UUID actorId, PlatformRole platformRole, int authVersion) {
        this(actorId, platformRole, authVersion, null, null);
    }

    public UserPrincipal(
            UUID actorId,
            PlatformRole platformRole,
            int authVersion,
            OrganizationRole organizationRole) {
        this(actorId, platformRole, authVersion, organizationRole, null);
    }

    public boolean isAdmin() {
        return platformRole == PlatformRole.ADMIN;
    }

    public boolean isManager() {
        return isAdmin() || organizationRole == OrganizationRole.MANAGER;
    }

    public boolean isStaff() {
        return isAdmin()
                || organizationRole == OrganizationRole.CHECK_IN_STAFF
                || organizationRole == OrganizationRole.MANAGER;
    }

    public boolean isCustomerOnly() {
        return !isAdmin() && organizationRole == null;
    }

    public String fullName() {
        return email != null ? email : "Thành viên";
    }

    public String getFullName() {
        return fullName();
    }

    public UUID getActorId() {
        return actorId;
    }

    public PlatformRole getPlatformRole() {
        return platformRole;
    }

    public int getAuthVersion() {
        return authVersion;
    }

    public OrganizationRole getOrganizationRole() {
        return organizationRole;
    }

    public String getEmail() {
        return email;
    }
}
