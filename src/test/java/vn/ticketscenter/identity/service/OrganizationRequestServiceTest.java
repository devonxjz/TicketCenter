package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.event.model.EventCategory;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.repository.OrganizationRepository;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.persistence.model.BaseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class OrganizationRequestServiceTest {

    private FakeUserRepository userRepository;
    private FakeOrganizationRepository organizationRepository;
    private InMemoryRedisStore redisStore;
    private TransactionManager transactionManager;
    private List<Object> persistedEntities;
    private OrganizationRequestService service;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        organizationRepository = new FakeOrganizationRepository();
        redisStore = new InMemoryRedisStore();
        persistedEntities = new ArrayList<>();
        transactionManager = createFakeTransactionManager(persistedEntities);
        service = OrganizationRequestService.withRedisStore(transactionManager, organizationRepository, userRepository, redisStore);
    }

    @Test
    @DisplayName("submitRequest throws IllegalArgumentException when actorId is null")
    void testSubmitRequestNullActorThrows() {
        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Acme Corp", "contact@acme.vn", "0901234567", "Description"
        );
        assertThrows(IllegalArgumentException.class, () -> service.submitRequest(null, dto, null));
    }

    @Test
    @DisplayName("submitRequest throws IllegalArgumentException when dto is null")
    void testSubmitRequestNullDtoThrows() {
        UUID actorId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.submitRequest(actorId, null, null));
    }

    @Test
    @DisplayName("submitRequest fails when dto validation fails")
    void testSubmitRequestInvalidDtoFields() {
        UUID actorId = UUID.randomUUID();

        OrganizationRequestCreateDto blankName = new OrganizationRequestCreateDto(
                "  ", "test@test.vn", null, null
        );
        assertThrows(IllegalArgumentException.class, () -> service.submitRequest(actorId, blankName, null));

        OrganizationRequestCreateDto shortName = new OrganizationRequestCreateDto(
                "AB", "test@test.vn", null, null
        );
        assertThrows(IllegalArgumentException.class, () -> service.submitRequest(actorId, shortName, null));

        OrganizationRequestCreateDto badEmail = new OrganizationRequestCreateDto(
                "Valid Name", "invalid-email", null, null
        );
        assertThrows(IllegalArgumentException.class, () -> service.submitRequest(actorId, badEmail, null));

        OrganizationRequestCreateDto badPhone = new OrganizationRequestCreateDto(
                "Valid Name", "test@test.vn", "123456", null
        );
        assertThrows(IllegalArgumentException.class, () -> service.submitRequest(actorId, badPhone, null));

        OrganizationRequestCreateDto longDesc = new OrganizationRequestCreateDto(
                "Valid Name", "test@test.vn", null, "A".repeat(2001)
        );
        assertThrows(IllegalArgumentException.class, () -> service.submitRequest(actorId, longDesc, null));
    }

    @Test
    @DisplayName("submitRequest throws IllegalArgumentException when user does not exist")
    void testSubmitRequestUserNotFoundThrows() {
        UUID actorId = UUID.randomUUID();
        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Acme Corp", "contact@acme.vn", "0901234567", "Desc"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.submitRequest(actorId, dto, null));
        assertTrue(ex.getMessage().contains("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("submitRequest throws IllegalStateException when user is not active")
    void testSubmitRequestUserSuspendedThrows() {
        UUID actorId = UUID.randomUUID();
        User suspendedUser = new User("user@test.vn", "username", "Full Name", "hash");
        setEntityId(suspendedUser, actorId);
        setUserStatus(suspendedUser, UserStatus.DISABLED);
        userRepository.putUser(suspendedUser);

        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Acme Corp", "contact@acme.vn", "0901234567", "Desc"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.submitRequest(actorId, dto, null));
        assertTrue(ex.getMessage().contains("USER_SUSPENDED"));
    }

    @Test
    @DisplayName("submitRequest throws IllegalStateException when pending request already exists")
    void testSubmitRequestPendingExistsThrows() {
        UUID actorId = UUID.randomUUID();
        User activeUser = new User("user@test.vn", "username", "Full Name", "hash");
        setEntityId(activeUser, actorId);
        userRepository.putUser(activeUser);
        organizationRepository.setHasPending(actorId, true);

        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Acme Corp", "contact@acme.vn", "0901234567", "Desc"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.submitRequest(actorId, dto, null));
        assertTrue(ex.getMessage().contains("ORGANIZATION_REQUEST_EXISTS"));
    }

    @Test
    @DisplayName("submitRequest creates organization successfully for active user")
    void testSubmitRequestSuccess() {
        UUID actorId = UUID.randomUUID();
        User activeUser = new User("user@test.vn", "username", "Full Name", "hash");
        setEntityId(activeUser, actorId);
        userRepository.putUser(activeUser);

        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Standard Event Co", "events@ticketscenter.vn", "0912345678", "Official event organizer"
        );

        OrganizationRequestDetailDto detail = service.submitRequest(actorId, dto, null);

        assertNotNull(detail);
        assertNotNull(detail.organizationId());
        assertEquals("Standard Event Co", detail.name());
        assertEquals("events@ticketscenter.vn", detail.contactEmail());
        assertEquals("0912345678", detail.contactPhone());
        assertEquals("Official event organizer", detail.description());
        assertEquals(OrganizationStatus.PENDING_APPROVAL, detail.status());
        assertEquals(actorId, detail.requesterId());
        assertNotNull(detail.submittedAt());
        assertNull(detail.rejectionReason());
    }

    @Test
    @DisplayName("submitRequest handles idempotency replay and conflict correctly")
    void testSubmitRequestIdempotencySuccessAndConflict() {
        UUID actorId = UUID.randomUUID();
        User activeUser = new User("user@test.vn", "username", "Full Name", "hash");
        setEntityId(activeUser, actorId);
        userRepository.putUser(activeUser);

        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Idem Event Co", "idem@ticketscenter.vn", "0912345678", "Idempotency description"
        );

        String idemKey = "test-key-001";

        OrganizationRequestDetailDto initial = service.submitRequest(actorId, dto, idemKey);
        assertNotNull(initial);
        assertEquals(1, organizationRepository.getSavedCount());

        OrganizationRequestDetailDto replayed = service.submitRequest(actorId, dto, idemKey);
        assertNotNull(replayed);
        assertEquals(initial.organizationId(), replayed.organizationId());
        assertEquals(initial.name(), replayed.name());
        assertEquals(initial.contactEmail(), replayed.contactEmail());
        assertEquals(1, organizationRepository.getSavedCount());

        OrganizationRequestCreateDto alteredDto = new OrganizationRequestCreateDto(
                "Different Event Co", "different@ticketscenter.vn", "0912345678", "Different description"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.submitRequest(actorId, alteredDto, idemKey));
        assertTrue(ex.getMessage().contains("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("getMyRequests returns empty list or mapped summary dtos")
    void testGetMyRequests() {
        UUID actorId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.getMyRequests(null));

        List<OrganizationRequestSummaryDto> emptyList = service.getMyRequests(actorId);
        assertNotNull(emptyList);
        assertTrue(emptyList.isEmpty());

        User user = new User("user@test.vn", "username", "Full Name", "hash");
        setEntityId(user, actorId);

        Organization org1 = new Organization("Org One", "org1@test.vn", "0900000001", "Desc 1", user);
        setEntityId(org1, UUID.randomUUID());
        org1.submitForApproval();
        organizationRepository.putOrg(org1);

        Organization org2 = new Organization("Org Two", "org2@test.vn", "0900000002", "Desc 2", user);
        setEntityId(org2, UUID.randomUUID());
        org2.reject("Invalid document");
        organizationRepository.putOrg(org2);

        List<OrganizationRequestSummaryDto> list = service.getMyRequests(actorId);
        assertEquals(2, list.size());
        OrganizationRequestSummaryDto summary1 = list.stream()
                .filter(s -> "Org One".equals(s.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(summary1);
        assertEquals(OrganizationStatus.PENDING_APPROVAL, summary1.status());

        OrganizationRequestSummaryDto summary2 = list.stream()
                .filter(s -> "Org Two".equals(s.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(summary2);
        assertEquals(OrganizationStatus.REJECTED, summary2.status());
        assertEquals("Invalid document", summary2.rejectionReason());
    }

    @Test
    @DisplayName("getRequestDetail validates inputs and handles not found")
    void testGetRequestDetailValidationAndNotFound() {
        UUID actorId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.getRequestDetail(null, false, orgId));
        assertThrows(IllegalArgumentException.class, () -> service.getRequestDetail(actorId, false, null));

        NoSuchElementException ex = assertThrows(NoSuchElementException.class, () ->
                service.getRequestDetail(actorId, false, orgId));
        assertTrue(ex.getMessage().contains("ORGANIZATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("getRequestDetail allows access for owner and admin")
    void testGetRequestDetailOwnerAndAdminAccess() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        User owner = new User("owner@test.vn", "owner", "Owner User", "hash");
        setEntityId(owner, ownerId);

        UUID orgId = UUID.randomUUID();
        Organization org = new Organization("Detail Org", "detail@test.vn", "0900000000", "Details", owner);
        setEntityId(org, orgId);
        org.submitForApproval();
        organizationRepository.putOrg(org);

        OrganizationRequestDetailDto ownerView = service.getRequestDetail(ownerId, false, orgId);
        assertNotNull(ownerView);
        assertEquals(orgId, ownerView.organizationId());
        assertEquals("Detail Org", ownerView.name());
        assertEquals(ownerId, ownerView.requesterId());

        OrganizationRequestDetailDto adminView = service.getRequestDetail(otherId, true, orgId);
        assertNotNull(adminView);
        assertEquals(orgId, adminView.organizationId());
        assertEquals("Detail Org", adminView.name());
    }

    @Test
    @DisplayName("getRequestDetail throws SecurityException for unauthorized non-admin actor")
    void testGetRequestDetailIdorGuard() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        User owner = new User("owner@test.vn", "owner", "Owner User", "hash");
        setEntityId(owner, ownerId);

        UUID orgId = UUID.randomUUID();
        Organization org = new Organization("Private Org", "private@test.vn", "0900000000", "Details", owner);
        setEntityId(org, orgId);
        organizationRepository.putOrg(org);

        SecurityException ex = assertThrows(SecurityException.class, () ->
                service.getRequestDetail(attackerId, false, orgId));
        assertTrue(ex.getMessage().contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("Admin lists pending organization requests successfully")
    void testGetPendingRequestsForAdminSuccess() {
        UUID adminId = UUID.randomUUID();
        User admin = new User("admin@test.vn", "adminuser", "Admin User", "hash");
        setEntityId(admin, adminId);
        admin.grantPlatformRole("ADMIN");
        userRepository.putUser(admin);

        UUID requesterId = UUID.randomUUID();
        User requester = new User("req@test.vn", "requester", "Requester User", "hash");
        setEntityId(requester, requesterId);
        userRepository.putUser(requester);

        Organization org1 = new Organization("Org One", "org1@test.vn", requester);
        setEntityId(org1, UUID.randomUUID());
        org1.submitForApproval();
        organizationRepository.putOrg(org1);

        Organization org2 = new Organization("Org Two", "org2@test.vn", requester);
        setEntityId(org2, UUID.randomUUID());
        org2.submitForApproval();
        organizationRepository.putOrg(org2);

        List<OrganizationRequestSummaryDto> pending = service.getPendingRequestsForAdmin(adminId);
        assertEquals(2, pending.size());

        long count = service.countPendingRequestsForAdmin(adminId);
        assertEquals(2, count);
    }

    @Test
    @DisplayName("Non admin cannot list pending requests")
    void testGetPendingRequestsForAdminRejectsNonAdmin() {
        UUID customerId = UUID.randomUUID();
        User customer = new User("customer@test.vn", "customeruser", "Customer User", "hash");
        setEntityId(customer, customerId);
        userRepository.putUser(customer);

        SecurityException ex1 = assertThrows(SecurityException.class, () ->
                service.getPendingRequestsForAdmin(customerId));
        assertTrue(ex1.getMessage().contains("FORBIDDEN"));

        SecurityException ex2 = assertThrows(SecurityException.class, () ->
                service.countPendingRequestsForAdmin(customerId));
        assertTrue(ex2.getMessage().contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("Admin approves request elevates requester to manager and creates initial event")
    void testApproveRequestSuccess() {
        UUID adminId = UUID.randomUUID();
        User admin = new User("admin@test.vn", "adminuser", "Admin User", "hash");
        setEntityId(admin, adminId);
        admin.grantPlatformRole("ADMIN");
        userRepository.putUser(admin);

        UUID requesterId = UUID.randomUUID();
        User requester = new User("req@test.vn", "requester", "Requester User", "hash");
        setEntityId(requester, requesterId);
        userRepository.putUser(requester);

        UUID orgId = UUID.randomUUID();
        Organization org = new Organization("Concert Corp", "concert@test.vn", "0901234567",
                "Concert organizer\n[Event: Rock Night 2026 | Venue: My Dinh Stadium]", requester);
        setEntityId(org, orgId);
        org.submitForApproval();
        organizationRepository.putOrg(org);

        OrganizationRequestDetailDto approved = service.approveRequest(adminId, orgId);

        assertNotNull(approved);
        assertEquals(OrganizationStatus.APPROVED, approved.status());
        assertEquals(OrganizationStatus.APPROVED, org.getStatus());
        assertNull(org.getRejectionReason());

        Optional<OrganizationMembership> membershipOpt = persistedEntities.stream()
                .filter(e -> e instanceof OrganizationMembership)
                .map(e -> (OrganizationMembership) e)
                .findFirst();
        assertTrue(membershipOpt.isPresent());
        OrganizationMembership membership = membershipOpt.get();
        assertEquals(requester, membership.getUser());
        assertEquals(org, membership.getOrganization());
        assertEquals(OrganizationRole.MANAGER, membership.getRole());

        Optional<Event> eventOpt = persistedEntities.stream()
                .filter(e -> e instanceof Event)
                .map(e -> (Event) e)
                .findFirst();
        assertTrue(eventOpt.isPresent());
        Event event = eventOpt.get();
        assertEquals(org, event.getOrganization());
        assertEquals("Rock Night 2026", event.getTitle());
        assertEquals("My Dinh Stadium", event.getVenueName());
        assertNotNull(event.getStartTime());
        assertNotNull(event.getEndTime());
    }

    @Test
    @DisplayName("Approve request fails when caller is not admin")
    void testApproveRequestFailsForNonAdmin() {
        UUID customerId = UUID.randomUUID();
        User customer = new User("customer@test.vn", "customeruser", "Customer User", "hash");
        setEntityId(customer, customerId);
        userRepository.putUser(customer);

        UUID orgId = UUID.randomUUID();
        Organization org = new Organization("Org Test", "test@test.vn", customer);
        setEntityId(org, orgId);
        org.submitForApproval();
        organizationRepository.putOrg(org);

        SecurityException ex = assertThrows(SecurityException.class, () ->
                service.approveRequest(customerId, orgId));
        assertTrue(ex.getMessage().contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("Approve request fails when organization is not found")
    void testApproveRequestNotFound() {
        UUID adminId = UUID.randomUUID();
        User admin = new User("admin@test.vn", "adminuser", "Admin User", "hash");
        setEntityId(admin, adminId);
        admin.grantPlatformRole("ADMIN");
        userRepository.putUser(admin);

        UUID nonExistentOrgId = UUID.randomUUID();
        assertThrows(NoSuchElementException.class, () ->
                service.approveRequest(adminId, nonExistentOrgId));
    }

    @Test
    @DisplayName("Approve request fails when organization is not in pending approval status")
    void testApproveRequestNotPending() {
        UUID adminId = UUID.randomUUID();
        User admin = new User("admin@test.vn", "adminuser", "Admin User", "hash");
        setEntityId(admin, adminId);
        admin.grantPlatformRole("ADMIN");
        userRepository.putUser(admin);

        UUID requesterId = UUID.randomUUID();
        User requester = new User("req@test.vn", "requester", "Requester User", "hash");
        setEntityId(requester, requesterId);
        userRepository.putUser(requester);

        UUID orgId = UUID.randomUUID();
        Organization org = new Organization("Org Draft", "draft@test.vn", requester);
        setEntityId(org, orgId);
        organizationRepository.putOrg(org);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.approveRequest(adminId, orgId));
        assertTrue(ex.getMessage().contains("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Approve request validates null arguments")
    void testApproveRequestNullArguments() {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.approveRequest(null, orgId));
        assertThrows(IllegalArgumentException.class, () -> service.approveRequest(adminId, null));
        assertThrows(IllegalArgumentException.class, () -> service.getPendingRequestsForAdmin(null));
        assertThrows(IllegalArgumentException.class, () -> service.countPendingRequestsForAdmin(null));
    }

    @Test
    @DisplayName("Constructors initialize service dependencies properly")
    void testConstructorOverloads() {
        assertThrows(NullPointerException.class, () ->
                new OrganizationRequestService(null, organizationRepository, userRepository));
        assertThrows(NullPointerException.class, () ->
                new OrganizationRequestService(transactionManager, null, userRepository));
        assertThrows(NullPointerException.class, () ->
                new OrganizationRequestService(transactionManager, organizationRepository, null));

        OrganizationRequestService directService = new OrganizationRequestService(
                transactionManager, organizationRepository, userRepository
        );
        assertNotNull(directService);

        OrganizationRequestService nullRedisService = new OrganizationRequestService(
                transactionManager, organizationRepository, userRepository, (vn.ticketscenter.config.redis.RedisClientProvider) null
        );
        assertNotNull(nullRedisService);
    }

    private static TransactionManager createFakeTransactionManager() {
        return createFakeTransactionManager(null);
    }

    private static TransactionManager createFakeTransactionManager(List<Object> persistedEntities) {
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
        return new TransactionManager(Map.of(DatabasePrincipal.AUTH, emf));
    }

    private static void setEntityId(Object entity, UUID id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setUserStatus(User user, UserStatus status) {
        try {
            Field statusField = User.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(user, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class FakeUserRepository extends UserRepository {
        private final Map<UUID, User> users = new HashMap<>();

        void putUser(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public Optional<User> findById(EntityManager entityManager, UUID id) {
            return Optional.ofNullable(users.get(id));
        }
    }

    private static class FakeOrganizationRepository extends OrganizationRepository {
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
        public List<Organization> findPending(EntityManager em) {
            return orgs.values().stream()
                    .filter(o -> o.getStatus() == OrganizationStatus.PENDING_APPROVAL)
                    .toList();
        }

        @Override
        public long countPending(EntityManager em) {
            return orgs.values().stream()
                    .filter(o -> o.getStatus() == OrganizationStatus.PENDING_APPROVAL)
                    .count();
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

    private static class InMemoryRedisStore implements OrganizationRequestService.RedisStore {
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
