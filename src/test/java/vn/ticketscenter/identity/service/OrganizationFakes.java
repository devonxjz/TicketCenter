package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository;
import vn.ticketscenter.identity.dao.OrganizationRepository;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** In-memory stand-ins shared by the organisation service tests. */
final class OrganizationFakes {

    private OrganizationFakes() {
    }

    static TransactionManager createFakeTransactionManager() {
        return createFakeTransactionManager(null);
    }

    static TransactionManager createFakeTransactionManager(List<Object> persistedEntities) {
        EntityTransaction tx = (EntityTransaction) Proxy.newProxyInstance(
                EntityTransaction.class.getClassLoader(),
                new Class<?>[]{EntityTransaction.class},
                (proxy, method, args) -> {
                    if ("isActive".equals(method.getName())) return true;
                    return null;
                }
        );
        EntityManager em = (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> {
                    if ("getTransaction".equals(method.getName())) return tx;
                    if ("persist".equals(method.getName()) && persistedEntities != null && args != null && args.length > 0) {
                        persistedEntities.add(args[0]);
                        return null;
                    }
                    return null;
                }
        );
        EntityManagerFactory emf = (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(),
                new Class<?>[]{EntityManagerFactory.class},
                (proxy, method, args) -> {
                    if ("createEntityManager".equals(method.getName())) return em;
                    return null;
                }
        );
        return new TransactionManager(Map.of(DatabasePrincipal.AUTH, emf, DatabasePrincipal.ADMIN, emf, DatabasePrincipal.BUYER, emf));
    }

    static void setEntityId(Object entity, UUID id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void setUserStatus(User user, UserStatus status) {
        try {
            Field statusField = User.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(user, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Stands in for the approval/rejection procedures: records the call and applies the status change. */
    static class FakeDecisionRepository extends OrganizationDecisionRepository {
        record ApprovalCall(UUID organizationId, UUID adminId, CommissionPolicy policy, InitialEvent event) { }

        final List<ApprovalCall> approvals = new ArrayList<>();
        final List<String> rejections = new ArrayList<>();
        private final FakeOrganizationRepository organizations;
        private final FakeUserRepository users;

        FakeDecisionRepository(FakeOrganizationRepository organizations, FakeUserRepository users) {
            this.organizations = organizations;
            this.users = users;
        }

        @Override
        public UUID submit(TransactionManager transactions, UUID requesterId, String name, String contactEmail,
                           String contactPhone, String description) {
            Organization org = new Organization(name, contactEmail, contactPhone, description,
                    users.findById(null, requesterId).orElseThrow());
            org.submitForApproval();
            return organizations.save(null, org).getId();
        }

        @Override
        public Approval approve(TransactionManager transactions, UUID organizationId, UUID adminId,
                                CommissionPolicy policy, InitialEvent event) {
            approvals.add(new ApprovalCall(organizationId, adminId, policy, event));
            Organization org = organizations.findById(null, organizationId).orElseThrow();
            org.approve();
            return new Approval(org.getRequester() == null ? null : org.getRequester().getId(), null, UUID.randomUUID());
        }

        @Override
        public void reject(TransactionManager transactions, UUID organizationId, UUID adminId, String reason) {
            rejections.add(reason);
            organizations.findById(null, organizationId).orElseThrow().reject(reason);
        }
    }

    static class FakeUserRepository extends UserRepository {
        private final Map<UUID, User> users = new HashMap<>();

        void putUser(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public Optional<User> findById(EntityManager entityManager, UUID id) {
            return Optional.ofNullable(users.get(id));
        }
    }

    static class FakeOrganizationRepository extends OrganizationRepository {
        private final Map<UUID, Organization> orgs = new java.util.LinkedHashMap<>();
        private final Set<UUID> pendingRequesters = new HashSet<>();
        private int savedCount = 0;

        void putOrg(Organization org) {
            orgs.put(org.getId(), org);
        }

        void setHasPending(UUID requesterId, boolean hasPending) {
            if (hasPending) {
                pendingRequesters.add(requesterId);
            } else {
                pendingRequesters.remove(requesterId);
            }
        }

        int getSavedCount() {
            return savedCount;
        }

        @Override
        public Optional<Organization> findById(EntityManager em, UUID id) {
            return Optional.ofNullable(orgs.get(id));
        }

        @Override
        public List<Organization> findByRequesterId(EntityManager em, UUID requesterId) {
            return orgs.values().stream()
                    .filter(o -> o.getRequester() != null && requesterId.equals(o.getRequester().getId()))
                    .toList();
        }

        @Override
        public boolean hasPendingRequest(EntityManager em, UUID requesterId) {
            return pendingRequesters.contains(requesterId);
        }

        @Override
        public List<Organization> findByStatus(EntityManager em, OrganizationStatus status, int offset, int limit) {
            return orgs.values().stream()
                    .filter(o -> status == null || o.getStatus() == status)
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countByStatus(EntityManager em, OrganizationStatus status) {
            return orgs.values().stream().filter(o -> status == null || o.getStatus() == status).count();
        }

        @Override
        public Organization save(EntityManager em, Organization organization) {
            savedCount++;
            if (organization.getId() == null) {
                setEntityId(organization, UUID.randomUUID());
            }
            orgs.put(organization.getId(), organization);
            return organization;
        }
    }

    static class InMemoryRedisStore implements OrganizationRequestIdempotency.Store {
        private final Map<String, String> store = new HashMap<>();

        @Override
        public String get(String key) {
            return store.get(key);
        }

        @Override
        public void setex(String key, int seconds, String value) {
            store.put(key, value);
        }
    }
}
