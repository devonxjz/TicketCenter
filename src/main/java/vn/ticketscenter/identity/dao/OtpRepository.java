package vn.ticketscenter.identity.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.OtpPurpose;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class OtpRepository {

    private static final String USABLE =
            "AND o.consumedAt IS NULL AND o.invalidatedAt IS NULL AND o.expiresAt > :now AND o.failedAttempts < 5 ";

    public void save(EntityManager em, Otp otp) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        Objects.requireNonNull(otp, "Otp must not be null");
        if (otp.getId() == null) {
            em.persist(otp);
        } else {
            em.merge(otp);
        }
    }

    public Optional<Otp> findById(EntityManager em, UUID id) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        if (id == null) return Optional.empty();
        return Optional.ofNullable(em.find(Otp.class, id));
    }

    public Optional<Otp> findLatestOtpByEmail(EntityManager em, String emailNormalized, OtpPurpose purpose) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        if (emailNormalized == null || emailNormalized.isBlank() || purpose == null) {
            return Optional.empty();
        }
        try {
            Otp otp = em.createQuery(
                            "SELECT o FROM Otp o WHERE LOWER(o.emailNormalized) = :email "
                                    + "AND o.purpose = :purpose ORDER BY o.createdAt DESC", Otp.class)
                    .setParameter("email", emailNormalized.trim().toLowerCase(Locale.ROOT))
                    .setParameter("purpose", purpose)
                    .setMaxResults(1)
                    .getSingleResult();
            return Optional.of(otp);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<Otp> findLatestValidOtp(EntityManager em, UUID userId, OtpPurpose purpose, Instant now) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        if (userId == null || purpose == null || now == null) {
            return Optional.empty();
        }
        try {
            Otp otp = em.createQuery(
                            "SELECT o FROM Otp o WHERE o.user.id = :userId AND o.purpose = :purpose " +
                            USABLE + "ORDER BY o.createdAt DESC", Otp.class)
                    .setParameter("userId", userId)
                    .setParameter("purpose", purpose)
                    .setParameter("now", now)
                    .setMaxResults(1)
                    .getSingleResult();
            return Optional.of(otp);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<Otp> findLatestValidOtpByEmail(EntityManager em, String emailNormalized,
                                                    OtpPurpose purpose, Instant now) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        if (emailNormalized == null || emailNormalized.isBlank() || purpose == null || now == null) {
            return Optional.empty();
        }
        try {
            Otp otp = em.createQuery(
                            "SELECT o FROM Otp o WHERE LOWER(o.emailNormalized) = :email AND o.purpose = :purpose " +
                            USABLE + "ORDER BY o.createdAt DESC", Otp.class)
                    .setParameter("email", emailNormalized.trim().toLowerCase(Locale.ROOT))
                    .setParameter("purpose", purpose)
                    .setParameter("now", now)
                    .setMaxResults(1)
                    .getSingleResult();
            return Optional.of(otp);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public int invalidateExistingOtpsByEmail(EntityManager em, String emailNormalized, OtpPurpose purpose,
                                             Instant now) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        Objects.requireNonNull(now, "Instant now must not be null");
        if (emailNormalized == null || emailNormalized.isBlank() || purpose == null) return 0;
        return em.createQuery(
                        "UPDATE Otp o SET o.invalidatedAt = :now " +
                        "WHERE LOWER(o.emailNormalized) = :email AND o.purpose = :purpose "
                                + "AND o.consumedAt IS NULL AND o.invalidatedAt IS NULL")
                .setParameter("now", now)
                .setParameter("email", emailNormalized.trim().toLowerCase(Locale.ROOT))
                .setParameter("purpose", purpose)
                .executeUpdate();
    }

    /**
     * Tiêu thụ OTP một cách nguyên tử (atomic conditional update).
     * Trả về true nếu và chỉ nếu cập nhật thành công
     * (chống race condition 2 request verify đồng thời).
     */
    public boolean consumeOtpAtomically(EntityManager em, UUID otpId, Instant now) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        Objects.requireNonNull(otpId, "otpId must not be null");
        Objects.requireNonNull(now, "Instant now must not be null");
        int updated = em.createQuery(
                        "UPDATE Otp o SET o.consumedAt = :now " +
                        "WHERE o.id = :otpId AND o.consumedAt IS NULL AND o.invalidatedAt IS NULL "
                                + "AND o.expiresAt > :now AND o.failedAttempts < 5")
                .setParameter("now", now)
                .setParameter("otpId", otpId)
                .executeUpdate();
        return updated == 1;
    }

    /**
     * Tăng số lần thử sai. Tự động vô hiệu hóa (gán invalidatedAt) nếu chạm ngưỡng 5 lần.
     */
    public int incrementFailedAttempts(EntityManager em, UUID otpId, Instant now) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        Objects.requireNonNull(otpId, "otpId must not be null");
        Objects.requireNonNull(now, "Instant now must not be null");
        return em.createQuery(
                        "UPDATE Otp o SET o.failedAttempts = o.failedAttempts + 1, " +
                        "o.invalidatedAt = CASE WHEN (o.failedAttempts + 1) >= 5 THEN :now ELSE o.invalidatedAt END " +
                        "WHERE o.id = :otpId AND o.consumedAt IS NULL AND o.invalidatedAt IS NULL")
                .setParameter("now", now)
                .setParameter("otpId", otpId)
                .executeUpdate();
    }
}
