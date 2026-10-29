package vn.ticketscenter.settlement.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.infrastructure.FinanceException;

/** Reloads current authority; a role cached in the session is not authority. */
public final class FinanceAuthorization {
    private FinanceAuthorization() {}

    public static void requireAdmin(Connection db, UserPrincipal actor) throws SQLException {
        if (!currentRole(db, actor).equals("ADMIN")) throw new FinanceException("FORBIDDEN", 403);
    }

    public static void requireManager(Connection db, UserPrincipal actor, UUID organizationId)
            throws SQLException {
        String role = currentRole(db, actor);
        if (organizationId == null) throw new FinanceException("INVALID_ORGANIZATION", 400);
        if (role.equals("ADMIN")) return;
        try (var query =
                db.prepareStatement(
                        "SELECT 1 FROM dbo.vw_ActiveManagerMemberships WHERE user_id=? AND organization_id=?")) {
            query.setString(1, actor.actorId().toString());
            query.setString(2, organizationId.toString());
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new FinanceException("FORBIDDEN", 403);
            }
        }
    }

    /** The first organisation the actor manages, or null when they manage none. */
    public static UUID firstManagedOrganization(Connection db, UserPrincipal actor)
            throws SQLException {
        try (var query =
                db.prepareStatement(
                        "SELECT TOP (1) organization_id FROM dbo.vw_ActiveManagerMemberships WHERE user_id=? ORDER BY organization_id")) {
            query.setString(1, actor.actorId().toString());
            try (var row = query.executeQuery()) {
                return row.next() ? UUID.fromString(row.getString(1)) : null;
            }
        }
    }

    private static String currentRole(Connection db, UserPrincipal actor) throws SQLException {
        if (actor == null) throw new FinanceException("UNAUTHENTICATED", 401);
        try (var query =
                db.prepareStatement(
                        "SELECT status,auth_version,platform_role FROM dbo.vw_ActorAuthority WHERE id=?")) {
            query.setString(1, actor.actorId().toString());
            try (var row = query.executeQuery()) {
                if (!row.next()
                        || !"ACTIVE".equals(row.getString("status"))
                        || row.getInt("auth_version") != actor.authVersion())
                    throw new FinanceException("UNAUTHENTICATED", 401);
                return row.getString("platform_role");
            }
        }
    }
}
