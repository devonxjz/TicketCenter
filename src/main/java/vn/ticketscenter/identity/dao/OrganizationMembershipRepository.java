package vn.ticketscenter.identity.dao;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads of memberships go through JPA; every write is one call of usp_AddMember / usp_ChangeMemberRole, which own
 * the authorization (active manager or platform admin), the audit row and the organization lock. The
 * last-manager rule is kept by trg_Membership_ProtectLastManager.
 */
public class OrganizationMembershipRepository {

    /** The membership as the procedures return it after a write. */
    public record MembershipRow(UUID membershipId, UUID userId, OrganizationRole role, boolean active,
                                Instant joinedAt) { }

    public Optional<OrganizationMembership> findByOrgAndUser(EntityManager em, UUID orgId, UUID userId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (orgId == null || userId == null) {
            return Optional.empty();
        }
        List<OrganizationMembership> list = em.createQuery(
                        "SELECT m FROM OrganizationMembership m "
                                + "WHERE m.organization.id = :orgId AND m.user.id = :userId",
                        OrganizationMembership.class)
                .setParameter("orgId", orgId)
                .setParameter("userId", userId)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OrganizationMembership> findById(EntityManager em, UUID membershipId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (membershipId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(OrganizationMembership.class, membershipId));
    }

    public List<OrganizationMemberDto> findMembersByOrgId(EntityManager em, UUID orgId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (orgId == null) {
            return List.of();
        }
        return em.createQuery(
                        "SELECT new vn.ticketscenter.identity.dto.OrganizationMemberDto("
                                + "m.id, u.id, u.userName, u.fullName, u.email, m.role, m.active, m.joinedAt) "
                                + "FROM OrganizationMembership m JOIN m.user u "
                                + "WHERE m.organization.id = :orgId ORDER BY m.joinedAt ASC",
                        OrganizationMemberDto.class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    public List<MembershipSummaryDto> findMembershipsByUserId(EntityManager em, UUID userId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (userId == null) {
            return List.of();
        }
        return em.createQuery(
                        "SELECT new vn.ticketscenter.identity.dto.MembershipSummaryDto("
                                + "o.id, o.name, m.role, m.active) "
                                + "FROM OrganizationMembership m JOIN m.organization o "
                                + "WHERE m.user.id = :userId ORDER BY o.name ASC",
                        MembershipSummaryDto.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    /** Adds the user, or reactivates a deactivated member, as {@code role}. */
    public MembershipRow addMember(EntityManager em, UUID orgId, UUID actorId, UUID userId, OrganizationRole role) {
        Objects.requireNonNull(role, "role must not be null");
        return callMembershipProcedure(em, "{call dbo.usp_AddMember(?,?,?,?)}", call -> {
            call.setString(1, orgId.toString());
            call.setString(2, actorId.toString());
            call.setString(3, userId.toString());
            call.setString(4, role.name());
        });
    }

    /** Changes the role and/or the active flag; a null argument keeps the current value. */
    public MembershipRow changeMember(EntityManager em, UUID orgId, UUID actorId, UUID userId,
                                      OrganizationRole role, Boolean active) {
        return callMembershipProcedure(em, "{call dbo.usp_ChangeMemberRole(?,?,?,?,?)}", call -> {
            call.setString(1, orgId.toString());
            call.setString(2, actorId.toString());
            call.setString(3, userId.toString());
            if (role == null) {
                call.setNull(4, Types.VARCHAR);
            } else {
                call.setString(4, role.name());
            }
            if (active == null) {
                call.setNull(5, Types.BIT);
            } else {
                call.setBoolean(5, active);
            }
        });
    }

    @FunctionalInterface
    private interface Binder {
        void bind(CallableStatement call) throws SQLException;
    }

    private MembershipRow callMembershipProcedure(EntityManager em, String sql, Binder binder) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        try {
            return em.unwrap(Session.class).doReturningWork(connection -> {
                try (CallableStatement call = connection.prepareCall(sql)) {
                    binder.bind(call);
                    try (ResultSet row = call.executeQuery()) {
                        if (!row.next()) {
                            throw new IllegalStateException("Membership procedure returned no row");
                        }
                        return new MembershipRow(
                                UUID.fromString(row.getString("membership_id")),
                                UUID.fromString(row.getString("user_id")),
                                OrganizationRole.valueOf(row.getString("role")),
                                row.getBoolean("active"),
                                row.getObject("joined_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
                    }
                } catch (SQLException failure) {
                    throw translate(failure);
                }
            });
        } finally {
            em.clear();
        }
    }

    /** Maps the procedures' and the trigger's THROW numbers onto the exceptions the service has always raised. */
    static RuntimeException translate(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            switch (error.getErrorCode()) {
                case 51810: return new SecurityException(
                        "FORBIDDEN: Chỉ Quản lý (MANAGER) của tổ chức mới có quyền thực hiện thao tác này");
                case 51816: return new IllegalArgumentException(
                        "Vai trò không hợp lệ: chỉ chấp nhận MANAGER hoặc CHECK_IN_STAFF");
                case 51817: return new IllegalStateException("Tài khoản người dùng không ở trạng thái hoạt động");
                case 52650: return new IllegalStateException(
                        "MEMBER_ALREADY_EXISTS: Thành viên đã đang hoạt động trong tổ chức");
                case 52651: return new IllegalArgumentException("Thành viên không tồn tại trong tổ chức");
                case 52652: return new IllegalArgumentException("Không có thay đổi nào được yêu cầu");
                case 51106: return new IllegalStateException("CANNOT_DEACTIVATE_LAST_MANAGER: "
                        + "Không thể hạ quyền hoặc vô hiệu hóa Quản lý duy nhất còn lại của tổ chức");
                default: break;
            }
        }
        return new IllegalStateException("Membership command failed", failure);
    }

    /** The user's strongest active role: MANAGER before any other, empty when the user has no membership. */
    public Optional<OrganizationRole> findPrimaryRole(EntityManager em, UUID userId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (userId == null) {
            return Optional.empty();
        }
        return em.createQuery(
                        "SELECT m.role FROM OrganizationMembership m WHERE m.user.id = :uid AND m.active = true "
                                + "ORDER BY CASE WHEN m.role = :mgr THEN 1 ELSE 2 END",
                        OrganizationRole.class)
                .setParameter("uid", userId)
                .setParameter("mgr", OrganizationRole.MANAGER)
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }
}
