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

@Entity
@Table(name = "tc_organizations", schema = "dbo")
public class Organization extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id")
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrganizationStatus status = OrganizationStatus.DRAFT;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    protected Organization() { }

    public Organization(String name, String contactEmail, User requester) {
        this.name = name;
        this.contactEmail = contactEmail;
        this.requester = requester;
    }

    public String getName() { return name; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getDescription() { return description; }
    public User getRequester() { return requester; }
    public OrganizationStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }

    public void submitForApproval() { status = OrganizationStatus.PENDING_APPROVAL; }
    public void approve() { status = OrganizationStatus.APPROVED; rejectionReason = null; }
    public void reject(String reason) {
        status = OrganizationStatus.REJECTED;
        rejectionReason = reason;
    }
}
