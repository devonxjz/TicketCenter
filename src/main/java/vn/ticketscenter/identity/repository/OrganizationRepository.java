package vn.ticketscenter.identity.repository;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class OrganizationRepository {

    /** JPQL enum literal; kept as a constant so queries stay within the line limit. */
    private static final String PENDING = "vn.ticketscenter.identity.model.OrganizationStatus.PENDING_APPROVAL";

    public Optional<Organization> findById(EntityManager em, UUID id) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(Organization.class, id));
    }

    public List<Organization> findByRequesterId(EntityManager em, UUID requesterId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (requesterId == null) {
            return List.of();
        }
        return em.createQuery(
                        "SELECT o FROM Organization o WHERE o.requester.id = :requesterId ORDER BY o.createdAt DESC",
                        Organization.class)
                .setParameter("requesterId", requesterId)
                .getResultList();
    }

    public boolean hasPendingRequest(EntityManager em, UUID requesterId) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        if (requesterId == null) {
            return false;
        }
        Long count = em.createQuery(
                        "SELECT COUNT(o) FROM Organization o WHERE o.requester.id = :requesterId AND o.status = "
                                + PENDING,
                        Long.class)
                .setParameter("requesterId", requesterId)
                .getSingleResult();
        return count != null && count > 0;
    }

    public List<Organization> findByStatus(EntityManager em, OrganizationStatus status, int offset, int limit) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        String q = status != null
                ? "SELECT o FROM Organization o WHERE o.status = :status ORDER BY o.submittedAt DESC, o.createdAt DESC"
                : "SELECT o FROM Organization o ORDER BY o.submittedAt DESC, o.createdAt DESC";
        var query = em.createQuery(q, Organization.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (offset > 0) {
            query.setFirstResult(offset);
        }
        if (limit > 0) {
            query.setMaxResults(limit);
        }
        return query.getResultList();
    }

    public long countByStatus(EntityManager em, OrganizationStatus status) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        String q = status != null
                ? "SELECT COUNT(o) FROM Organization o WHERE o.status = :status"
                : "SELECT COUNT(o) FROM Organization o";
        var query = em.createQuery(q, Long.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        Long count = query.getSingleResult();
        return count != null ? count : 0L;
    }

    public Organization save(EntityManager em, Organization organization) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        Objects.requireNonNull(organization, "Organization must not be null.");
        if (organization.getId() == null) {
            em.persist(organization);
            return organization;
        } else {
            return em.merge(organization);
        }
    }
}
