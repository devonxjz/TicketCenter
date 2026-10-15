package vn.ticketscenter.identity.repository;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class OrganizationMembershipRepository {

    public Optional<OrganizationMembership> findByOrgAndUser(EntityManager em, UUID orgId, UUID userId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (orgId == null || userId == null) {
            return Optional.empty();
        }
        List<OrganizationMembership> list = em.createQuery(
                        "SELECT m FROM OrganizationMembership m "
                                + "WHERE m.organization.id = :orgId AND m.user.id = :userId",
                        OrganizationMembership.class)
                .setParameter("orgId", orgId)
                .setParameter("userId", userId)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<OrganizationMembership> findById(EntityManager em, UUID membershipId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (membershipId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(OrganizationMembership.class, membershipId));
    }

    public List<OrganizationMemberDto> findMembersByOrgId(EntityManager em, UUID orgId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (orgId == null) {
            return List.of();
        }
        return em.createQuery(
                        "SELECT new vn.ticketscenter.identity.dto.OrganizationMemberDto("
                                + "m.id, u.id, u.userName, u.fullName, u.email, m.role, m.active, m.joinedAt) "
                                + "FROM OrganizationMembership m JOIN m.user u "
                                + "WHERE m.organization.id = :orgId ORDER BY m.joinedAt ASC",
                        OrganizationMemberDto.class)
                .setParameter("orgId", orgId)
                .getResultList();
    }

    public List<MembershipSummaryDto> findMembershipsByUserId(EntityManager em, UUID userId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (userId == null) {
            return List.of();
        }
        return em.createQuery(
                        "SELECT new vn.ticketscenter.identity.dto.MembershipSummaryDto("
                                + "o.id, o.name, m.role, m.active) "
                                + "FROM OrganizationMembership m JOIN m.organization o "
                                + "WHERE m.user.id = :userId ORDER BY o.name ASC",
                        MembershipSummaryDto.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public long countActiveManagers(EntityManager em, UUID orgId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (orgId == null) {
            return 0L;
        }
        Long count = em.createQuery(
                        "SELECT COUNT(m) FROM OrganizationMembership m WHERE m.organization.id = :orgId "
                                + "AND m.role = vn.ticketscenter.identity.model.OrganizationRole.MANAGER "
                                + "AND m.active = true",
                        Long.class)
                .setParameter("orgId", orgId)
                .getSingleResult();
        return count != null ? count : 0L;
    }

    public OrganizationMembership save(EntityManager em, OrganizationMembership membership) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        Objects.requireNonNull(membership, "OrganizationMembership must not be null.");
        if (membership.getId() == null) {
            em.persist(membership);
            return membership;
        } else {
            return em.merge(membership);
        }
    }

    /** The user's strongest active role: MANAGER before any other, empty when the user has no membership. */
    public Optional<OrganizationRole> findPrimaryRole(EntityManager em, UUID userId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (userId == null) {
            return Optional.empty();
        }
        return em.createQuery(
                        "SELECT m.role FROM OrganizationMembership m WHERE m.user.id = :uid AND m.active = true "
                                + "ORDER BY CASE WHEN m.role = :mgr THEN 1 ELSE 2 END",
                        OrganizationRole.class)
                .setParameter("uid", userId)
                .setParameter("mgr", OrganizationRole.MANAGER)
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }
}
