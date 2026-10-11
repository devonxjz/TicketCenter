package vn.ticketscenter.identity.repository;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.identity.model.Organization;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class OrganizationRepository {

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
                        "SELECT COUNT(o) FROM Organization o WHERE o.requester.id = :requesterId AND o.status = vn.ticketscenter.identity.model.OrganizationStatus.PENDING_APPROVAL",
                        Long.class)
                .setParameter("requesterId", requesterId)
                .getSingleResult();
        return count != null && count > 0;
    }

    public List<Organization> findPending(EntityManager em) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        return em.createQuery(
                        "SELECT o FROM Organization o WHERE o.status = vn.ticketscenter.identity.model.OrganizationStatus.PENDING_APPROVAL ORDER BY o.submittedAt ASC, o.createdAt ASC",
                        Organization.class)
                .getResultList();
    }

    public long countPending(EntityManager em) {
        Objects.requireNonNull(em, "EntityManager must not be null.");
        Long count = em.createQuery(
                        "SELECT COUNT(o) FROM Organization o WHERE o.status = vn.ticketscenter.identity.model.OrganizationStatus.PENDING_APPROVAL",
                        Long.class)
                .getSingleResult();
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
