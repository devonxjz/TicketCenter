package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.ticketscenter.identity.service.OrganizationFakes.*;

import vn.ticketscenter.identity.service.OrganizationFakes.FakeDecisionRepository;
import vn.ticketscenter.identity.service.OrganizationFakes.FakeOrganizationRepository;
import vn.ticketscenter.identity.service.OrganizationFakes.FakeUserRepository;

public class OrganizationRequestServiceTest {

    private FakeUserRepository userRepository;
    private FakeOrganizationRepository organizationRepository;
    private OrganizationFakes.InMemoryRedisStore redisStore;
    private TransactionManager transactionManager;
    private List<Object> persistedEntities;
    private FakeDecisionRepository decisions;
    private OrganizationRequestService service;
    private String previousDataMode, previousEnvironment;

    @BeforeEach
    void setUp() {
        previousDataMode = System.getProperty("app.data.mode"); previousEnvironment = System.getProperty("app.env");
        System.setProperty("app.data.mode", "real"); System.setProperty("app.env", "test");
        userRepository = new FakeUserRepository();
        organizationRepository = new FakeOrganizationRepository();
        redisStore = new OrganizationFakes.InMemoryRedisStore();
        persistedEntities = new ArrayList<>();
        transactionManager = createFakeTransactionManager(persistedEntities);
        decisions = new FakeDecisionRepository(organizationRepository, userRepository);
        service = new OrganizationRequestService(transactionManager, organizationRepository, userRepository, decisions,
                OrganizationRequestIdempotency.withStore(redisStore));
    }

    @org.junit.jupiter.api.AfterEach void restoreDataMode() {
        if (previousDataMode == null) System.clearProperty("app.data.mode"); else System.setProperty("app.data.mode", previousDataMode);
        if (previousEnvironment == null) System.clearProperty("app.env"); else System.setProperty("app.env", previousEnvironment);
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
    @DisplayName("submitRequest throws USER_SUSPENDED when user is not active")
    void testSubmitRequestUserSuspendedThrows() {
        UUID actorId = UUID.randomUUID();
        User suspendedUser = new User("user@test.vn", "username", "Full Name", "hash");
        setEntityId(suspendedUser, actorId);
        setUserStatus(suspendedUser, UserStatus.DISABLED);
        userRepository.putUser(suspendedUser);

        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Acme Corp", "contact@acme.vn", "0901234567", "Desc"
        );

        IdentityException ex = assertThrows(IdentityException.class, () ->
                service.submitRequest(actorId, dto, null));
        assertEquals(IdentityError.USER_SUSPENDED, ex.error());
    }

    @Test
    @DisplayName("submitRequest throws ORGANIZATION_REQUEST_EXISTS when a pending request already exists")
    void testSubmitRequestPendingExistsThrows() {
        UUID actorId = UUID.randomUUID();
        User activeUser = new User("user@test.vn", "username", "Full Name", "hash");
        setEntityId(activeUser, actorId);
        userRepository.putUser(activeUser);
        organizationRepository.setHasPending(actorId, true);

        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Acme Corp", "contact@acme.vn", "0901234567", "Desc"
        );

        IdentityException ex = assertThrows(IdentityException.class, () ->
                service.submitRequest(actorId, dto, null));
        assertEquals(IdentityError.ORGANIZATION_REQUEST_EXISTS, ex.error());
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

        IdentityException ex = assertThrows(IdentityException.class, () ->
                service.submitRequest(actorId, alteredDto, idemKey));
        assertEquals(IdentityError.IDEMPOTENCY_CONFLICT, ex.error());
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

        OrganizationRequestService noCacheService = new OrganizationRequestService(
                transactionManager, organizationRepository, userRepository, decisions,
                OrganizationRequestIdempotency.redis(null)
        );
        assertNotNull(noCacheService);
    }
}
