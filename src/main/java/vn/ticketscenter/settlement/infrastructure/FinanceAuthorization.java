package vn.ticketscenter.settlement.infrastructure;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import vn.ticketscenter.identity.security.UserPrincipal;

/** Reloads current authority; a role cached in the session is not authority. */
public final class FinanceAuthorization {
    private FinanceAuthorization() {}
    public static void requireAdmin(Connection db, UserPrincipal actor) throws SQLException {
        if (!currentRole(db, actor).equals("ADMIN")) throw new FinanceException("FORBIDDEN",403);
    }
    public static void requireManager(Connection db, UserPrincipal actor, UUID organizationId) throws SQLException {
        String role = currentRole(db,actor);
        if (organizationId == null) throw new FinanceException("INVALID_ORGANIZATION",400);
        if (role.equals("ADMIN")) return;
        try (var query = db.prepareStatement("SELECT role FROM dbo.tc_organization_memberships WHERE user_id=? AND organization_id=? AND active=1 AND role='MANAGER'")) {
            query.setString(1, actor.actorId().toString()); query.setString(2,organizationId.toString());
            try (var row = query.executeQuery()) { if (!row.next()) throw new FinanceException("FORBIDDEN",403); }
        }
    }
    private static String currentRole(Connection db, UserPrincipal actor) throws SQLException {
        if (actor == null) throw new FinanceException("UNAUTHENTICATED",401);
        try (var query = db.prepareStatement("SELECT status,auth_version,platform_role FROM dbo.tc_users WHERE id=?")) {
            query.setString(1, actor.actorId().toString());
            try (var row = query.executeQuery()) {
                if (!row.next() || !"ACTIVE".equals(row.getString("status")) || row.getInt("auth_version") != actor.authVersion())
                    throw new FinanceException("UNAUTHENTICATED",401);
                return row.getString("platform_role");
            }
        }
    }
}
