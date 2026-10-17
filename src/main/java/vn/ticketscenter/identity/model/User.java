package vn.ticketscenter.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "tc_users", schema = "dbo")
public class User extends BaseEntity {
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "user_name", nullable = false, unique = true, length = 100)
    private String userName;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "auth_version", nullable = false)
    private int authVersion;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 20)
    private PlatformRole platformRole = PlatformRole.CUSTOMER;

    protected User() { }

    public User(String email, String userName, String fullName, String passwordHash) {
        this.email = email;
        this.userName = userName;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
    }

    public User(String email, String userName, String passwordHash, String fullName, Instant emailVerifiedAt) {
        this(email, userName, fullName, passwordHash);
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public String getEmail() { return email; }
    public String getUserName() { return userName; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public UserStatus getStatus() { return status; }
    public String getPasswordHash() { return passwordHash; }
    public int getAuthVersion() { return authVersion; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public PlatformRole getPlatformRole() { return platformRole; }

    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public void verifyEmail() { emailVerifiedAt = Instant.now(); }
    public void verifyEmail(Instant verifiedAt) { emailVerifiedAt = verifiedAt; }
    public boolean hasPlatformRole(String role) {
        return platformRole.name().equalsIgnoreCase(role);
    }
    public void grantPlatformRole(String role) {
        platformRole = PlatformRole.valueOf(role.toUpperCase());
    }
    public void updateProfile(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

    /** Stores the same password under a stronger hash; sessions stay valid, unlike {@link #changePassword}. */
    public void rehashPassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.authVersion++;
    }
}
