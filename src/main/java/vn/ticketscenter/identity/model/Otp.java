package vn.ticketscenter.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "tc_otps", schema = "dbo")
public class Otp extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "used_at")
    private Instant usedAt;

    protected Otp() { }

    public Otp(User user, OtpPurpose purpose, String codeHash, Instant expiresAt) {
        this.user = user;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public User getUser() { return user; }
    public OtpPurpose getPurpose() { return purpose; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getUsedAt() { return usedAt; }
}
