package vn.ticketscenter.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tc_otps", schema = "dbo")
public class Otp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "email_normalized", length = 320)
    private String emailNormalized;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "secret_hash", nullable = false)
    private byte[] secretHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Otp() { }

    public Otp(User user, String emailNormalized, OtpPurpose purpose, byte[] secretHash, Instant expiresAt) {
        this.user = user;
        this.emailNormalized = emailNormalized;
        this.purpose = purpose;
        this.secretHash = secretHash;
        this.expiresAt = expiresAt;
        this.failedAttempts = 0;
        this.createdAt = Instant.now();
    }

    public Otp(User user, OtpPurpose purpose, byte[] secretHash, Instant expiresAt) {
        this(user, user != null ? user.getEmail() : null, purpose, secretHash, expiresAt);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getEmailNormalized() { return emailNormalized; }
    public OtpPurpose getPurpose() { return purpose; }
    public byte[] getSecretHash() { return secretHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isConsumed() { return consumedAt != null; }
    public boolean isInvalidated() { return invalidatedAt != null; }
    public boolean isExpired(Instant now) { return now.isAfter(expiresAt); }
    public boolean isValid(Instant now) {
        return !isConsumed() && !isInvalidated() && !isExpired(now) && failedAttempts < 5;
    }

    public void consume(Instant now) { this.consumedAt = now; }
    public void invalidate(Instant now) { this.invalidatedAt = now; }
    public void incrementFailedAttempts(Instant now) {
        this.failedAttempts++;
        if (this.failedAttempts >= 5) {
            this.invalidatedAt = now;
        }
    }
}
