package vn.ticketscenter.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "tc_organization_requests", schema = "dbo")
public class OrganizationRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(length = 2000)
    private String rejectionReason;

    protected OrganizationRequest() { }

    public OrganizationRequest(Organization organization, User requester) {
        this.organization = organization;
        this.requester = requester;
        this.submittedAt = Instant.now();
    }

    public Organization getOrganization() { return organization; }
    public User getRequester() { return requester; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getRejectionReason() { return rejectionReason; }
}
