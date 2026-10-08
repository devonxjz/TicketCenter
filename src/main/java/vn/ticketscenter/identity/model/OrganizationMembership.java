package vn.ticketscenter.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.util.UUID;

@Entity
@Table(name = "tc_organization_memberships", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_membership_user_org", columnNames = {"user_id", "organization_id"}))
public class OrganizationMembership extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrganizationRole role;

    @Column(nullable = false)
    private boolean active = true;

    protected OrganizationMembership() { }

    public OrganizationMembership(User user, Organization organization, OrganizationRole role) {
        this.user = user;
        this.organization = organization;
        this.role = role;
    }

    public User getUser() { return user; }
    public Organization getOrganization() { return organization; }
    public OrganizationRole getRole() { return role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public UUID getUserId() { return user == null ? null : user.getId(); }
}
