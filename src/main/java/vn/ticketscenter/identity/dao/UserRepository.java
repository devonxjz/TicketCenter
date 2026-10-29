package vn.ticketscenter.identity.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import vn.ticketscenter.identity.dto.ProfileDto;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads accounts through JPA; every write is one procedure call (072, and usp_UpdateMyProfile from 059). */
public class UserRepository {

    public Optional<User> findById(EntityManager entityManager, UUID id) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entityManager.find(User.class, id));
    }

    public Optional<User> findByNormalizedEmail(EntityManager entityManager, String email) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        try {
            User user =
                    entityManager
                            .createQuery(
                                    "SELECT u FROM User u WHERE LOWER(u.email) = :email",
                                    User.class)
                            .setParameter("email", email.trim().toLowerCase(Locale.ROOT))
                            .getSingleResult();
            return Optional.of(user);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByNormalizedUserName(EntityManager entityManager, String userName) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        if (userName == null || userName.isBlank()) {
            return Optional.empty();
        }
        try {
            User user =
                    entityManager
                            .createQuery(
                                    "SELECT u FROM User u WHERE LOWER(u.userName) = :userName",
                                    User.class)
                            .setParameter("userName", userName.trim().toLowerCase(Locale.ROOT))
                            .getSingleResult();
            return Optional.of(user);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public boolean existsByNormalizedEmail(EntityManager entityManager, String email) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        if (email == null || email.isBlank()) {
            return false;
        }
        Long count =
                entityManager
                        .createQuery(
                                "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = :email",
                                Long.class)
                        .setParameter("email", email.trim().toLowerCase(Locale.ROOT))
                        .getSingleResult();
        return count != null && count > 0;
    }

    public boolean existsByNormalizedUserName(EntityManager entityManager, String userName) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        if (userName == null || userName.isBlank()) {
            return false;
        }
        Long count =
                entityManager
                        .createQuery(
                                "SELECT COUNT(u) FROM User u WHERE LOWER(u.userName) = :userName",
                                Long.class)
                        .setParameter("userName", userName.trim().toLowerCase(Locale.ROOT))
                        .getSingleResult();
        return count != null && count > 0;
    }

    /** New CUSTOMER account through usp_RegisterUser; email and user name must be unused, ignoring case. */
    public UUID register(
            EntityManager entityManager,
            String email,
            String userName,
            String fullName,
            String passwordHash) {
        return IdentityProcedures.query(
                entityManager,
                "{call dbo.usp_RegisterUser(?,?,?,?)}",
                call -> {
                    call.setString(1, email);
                    call.setString(2, userName);
                    call.setString(3, fullName);
                    call.setString(4, passwordHash);
                },
                row -> UUID.fromString(row.getString("user_id")));
    }

    /** Stores a stronger hash of the same password; sessions stay valid. */
    public void rehashPassword(EntityManager entityManager, UUID userId, String passwordHash) {
        IdentityProcedures.execute(
                entityManager,
                "{call dbo.usp_RehashPassword(?,?)}",
                call -> {
                    call.setString(1, userId.toString());
                    call.setString(2, passwordHash);
                });
    }

    /** Stores a new password and raises authVersion, which ends every session and reset token. */
    public void resetPassword(EntityManager entityManager, UUID userId, String passwordHash) {
        IdentityProcedures.execute(
                entityManager,
                "{call dbo.usp_ResetPassword(?,?)}",
                call -> {
                    call.setString(1, userId.toString());
                    call.setString(2, passwordHash);
                });
    }

    /** Raises authVersion, which ends every session of the user. */
    public void revokeSessions(EntityManager entityManager, UUID userId) {
        IdentityProcedures.execute(
                entityManager,
                "{call dbo.usp_RevokeUserSessions(?)}",
                call -> call.setString(1, userId.toString()));
    }

    public void markEmailVerified(EntityManager entityManager, UUID userId, Instant verifiedAt) {
        IdentityProcedures.execute(
                entityManager,
                "{call dbo.usp_MarkEmailVerified(?,?)}",
                call -> {
                    call.setString(1, userId.toString());
                    call.setObject(2, LocalDateTime.ofInstant(verifiedAt, ZoneOffset.UTC));
                });
    }

    /**
     * The account the {@code admin} alias stands for: the one registered under {@code preferredEmail}, else
     * any ADMIN account, preferring an email that contains "admin".
     */
    public Optional<User> findAdminByAlias(EntityManager entityManager, String preferredEmail) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Optional<User> preferred = findByNormalizedEmail(entityManager, preferredEmail);
        if (preferred.isPresent()) {
            return preferred;
        }
        return entityManager
                .createQuery(
                        "SELECT u FROM User u WHERE u.platformRole = :role "
                                + "ORDER BY CASE WHEN LOWER(u.email) LIKE '%admin%' THEN 0 ELSE 1 END",
                        User.class)
                .setParameter("role", PlatformRole.ADMIN)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Updates the user's own profile through {@code usp_UpdateMyProfile}.
     *
     * @return the stored profile, or empty when the procedure returns no row
     */
    public Optional<ProfileDto> updateProfile(
            EntityManager entityManager, UUID actorId, String fullName, String phone) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        @SuppressWarnings("unchecked")
        List<Object[]> rows =
                entityManager
                        .createNativeQuery(
                                "EXEC dbo.usp_UpdateMyProfile @actor_id=:actor,@full_name=:name,@phone=:phone")
                        .setParameter("actor", actorId)
                        .setParameter("name", fullName)
                        .setParameter("phone", phone == null ? "" : phone)
                        .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.get(0);
        return Optional.of(
                new ProfileDto(
                        UUID.fromString(row[0].toString()),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (String) row[4]));
    }
}
