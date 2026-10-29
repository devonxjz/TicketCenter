package vn.ticketscenter.identity.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.OtpPurpose;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads OTPs through JPA; every write is one procedure call (migration 072). */
public class OtpRepository {

    private static final String USABLE =
            "AND o.consumedAt IS NULL AND o.invalidatedAt IS NULL AND o.expiresAt > :now AND o.failedAttempts < 5 ";

    /** Closes the open OTPs of the email and purpose, then stores a new one; returns its id. */
    public UUID issue(
            EntityManager em,
            UUID userId,
            String emailNormalized,
            OtpPurpose purpose,
            byte[] secretHash,
            Instant expiresAt,
            Instant now) {
        return IdentityProcedures.query(
                em,
                "{call dbo.usp_IssueOtp(?,?,?,?,?,?)}",
                call -> {
                    call.setString(1, userId.toString());
                    call.setString(2, emailNormalized);
                    call.setString(3, purpose.name());
                    call.setBytes(4, secretHash);
                    call.setObject(5, utc(expiresAt));
                    call.setObject(6, utc(now));
                },
                row -> UUID.fromString(row.getString("otp_id")));
    }

    public Optional<Otp> findById(EntityManager em, UUID id) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(Otp.class, id));
    }

    public Optional<Otp> findLatestOtpByEmail(
            EntityManager em, String emailNormalized, OtpPurpose purpose) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        if (emailNormalized == null || emailNormalized.isBlank() || purpose == null) {
            return Optional.empty();
        }
        try {
            Otp otp =
                    em.createQuery(
                                    "SELECT o FROM Otp o WHERE LOWER(o.emailNormalized) = :email "
                                            + "AND o.purpose = :purpose ORDER BY o.createdAt DESC",
                                    Otp.class)
                            .setParameter("email", emailNormalized.trim().toLowerCase(Locale.ROOT))
                            .setParameter("purpose", purpose)
                            .setMaxResults(1)
                            .getSingleResult();
            return Optional.of(otp);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<Otp> findLatestValidOtp(
            EntityManager em, UUID userId, OtpPurpose purpose, Instant now) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        if (userId == null || purpose == null || now == null) {
            return Optional.empty();
        }
        try {
            Otp otp =
                    em.createQuery(
                                    "SELECT o FROM Otp o WHERE o.user.id = :userId AND o.purpose = :purpose "
                                            + USABLE
                                            + "ORDER BY o.createdAt DESC",
                                    Otp.class)
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

    public Optional<Otp> findLatestValidOtpByEmail(
            EntityManager em, String emailNormalized, OtpPurpose purpose, Instant now) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        if (emailNormalized == null
                || emailNormalized.isBlank()
                || purpose == null
                || now == null) {
            return Optional.empty();
        }
        try {
            Otp otp =
                    em.createQuery(
                                    "SELECT o FROM Otp o WHERE LOWER(o.emailNormalized) = :email AND o.purpose = :purpose "
                                            + USABLE
                                            + "ORDER BY o.createdAt DESC",
                                    Otp.class)
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

    /** Closes the open OTPs of the email and purpose. */
    public void invalidateOpen(EntityManager em, String emailNormalized, OtpPurpose purpose, Instant now) {
        if (emailNormalized == null || emailNormalized.isBlank() || purpose == null) {
            return;
        }
        IdentityProcedures.execute(
                em,
                "{call dbo.usp_InvalidateOtps(?,?,?)}",
                call -> {
                    call.setString(1, emailNormalized.trim().toLowerCase(Locale.ROOT));
                    call.setString(2, purpose.name());
                    call.setObject(3, utc(now));
                });
    }

    /** Uses the OTP once; false when it was already used, closed, expired or locked (two verifies cannot both win). */
    public boolean consume(EntityManager em, UUID otpId, Instant now) {
        Boolean consumed =
                IdentityProcedures.query(
                        em,
                        "{call dbo.usp_ConsumeOtp(?,?)}",
                        call -> {
                            call.setString(1, otpId.toString());
                            call.setObject(2, utc(now));
                        },
                        row -> row.getBoolean("consumed"));
        return Boolean.TRUE.equals(consumed);
    }

    /** Counts a wrong code (the fifth closes the OTP) and returns the failed attempts so far. */
    public int recordFailure(EntityManager em, UUID otpId, Instant now) {
        Integer attempts =
                IdentityProcedures.query(
                        em,
                        "{call dbo.usp_RecordOtpFailure(?,?)}",
                        call -> {
                            call.setString(1, otpId.toString());
                            call.setObject(2, utc(now));
                        },
                        row -> row.getInt("failed_attempts"));
        return attempts == null ? 0 : attempts;
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
