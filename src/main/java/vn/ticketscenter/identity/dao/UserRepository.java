package vn.ticketscenter.identity.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import vn.ticketscenter.identity.dto.ProfileDto;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class UserRepository {

    public Optional<User> findById(EntityManager entityManager, UUID id) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        if (id == null) return Optional.empty();
        return Optional.ofNullable(entityManager.find(User.class, id));
    }

    public Optional<User> findByNormalizedEmail(EntityManager entityManager, String email) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        if (email == null || email.isBlank()) return Optional.empty();
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
        if (userName == null || userName.isBlank()) return Optional.empty();
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
        if (email == null || email.isBlank()) return false;
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
        if (userName == null || userName.isBlank()) return false;
        Long count =
                entityManager
                        .createQuery(
                                "SELECT COUNT(u) FROM User u WHERE LOWER(u.userName) = :userName",
                                Long.class)
                        .setParameter("userName", userName.trim().toLowerCase(Locale.ROOT))
                        .getSingleResult();
        return count != null && count > 0;
    }

    public void save(EntityManager entityManager, User user) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(user, "user must not be null");
        if (user.getId() == null) {
            entityManager.persist(user);
        } else {
            entityManager.merge(user);
        }
    }

    public int updateAuthVersion(EntityManager entityManager, UUID userId, int newVersion) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        return entityManager
                .createQuery("UPDATE User u SET u.authVersion = :version WHERE u.id = :id")
                .setParameter("version", newVersion)
                .setParameter("id", userId)
                .executeUpdate();
    }

    public int updateStatus(EntityManager entityManager, UUID userId, UserStatus status) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return entityManager
                .createQuery("UPDATE User u SET u.status = :status WHERE u.id = :id")
                .setParameter("status", status)
                .setParameter("id", userId)
                .executeUpdate();
    }

    public int updatePasswordHashAndAuthVersion(
            EntityManager entityManager, UUID userId, String newPasswordHash) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(newPasswordHash, "newPasswordHash must not be null");
        return entityManager
                .createQuery(
                        "UPDATE User u SET u.passwordHash = :hash, u.authVersion = u.authVersion + 1 WHERE u.id = :id")
                .setParameter("hash", newPasswordHash)
                .setParameter("id", userId)
                .executeUpdate();
    }

    public int updateEmailVerifiedAt(EntityManager entityManager, UUID userId, Instant verifiedAt) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        return entityManager
                .createQuery("UPDATE User u SET u.emailVerifiedAt = :verifiedAt WHERE u.id = :id")
                .setParameter("verifiedAt", verifiedAt)
                .setParameter("id", userId)
                .executeUpdate();
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
