package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository.CommissionPolicy;
import vn.ticketscenter.identity.service.OrganizationFakes.FakeDecisionRepository;
import vn.ticketscenter.identity.service.OrganizationFakes.FakeOrganizationRepository;
import vn.ticketscenter.identity.service.OrganizationFakes.FakeUserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.ticketscenter.identity.service.OrganizationFakes.createFakeTransactionManager;
import static vn.ticketscenter.identity.service.OrganizationFakes.setEntityId;

public class OrganizationApprovalServiceTest {

    private FakeUserRepository userRepository;
    private FakeOrganizationRepository organizationRepository;
    private FakeDecisionRepository decisions;
    private OrganizationApprovalService service;
    private String previousDataMode;
    private String previousEnvironment;

    @BeforeEach
    void setUp() {
        previousDataMode = System.getProperty("app.data.mode");
        previousEnvironment = System.getProperty("app.env");
        System.setProperty("app.data.mode", "real");
        System.setProperty("app.env", "test");
        userRepository = new FakeUserRepository();
        organizationRepository = new FakeOrganizationRepository();
        decisions = new FakeDecisionRepository(organizationRepository, userRepository);
        service = new OrganizationApprovalService(createFakeTransactionManager(new ArrayList<>()),
                organizationRepository, userRepository, decisions);
    }

    @AfterEach
    void restoreDataMode() {
        restore("app.data.mode", previousDataMode);
        restore("app.env", previousEnvironment);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private UUID newAdmin() {
        UUID adminId = UUID.randomUUID();
        User admin = new User("admin@test.vn", "adminuser", "Admin User", "hash");
        setEntityId(admin, adminId);
        admin.grantPlatformRole("ADMIN");
        userRepository.putUser(admin);
        return adminId;
    }

    private User newCustomer(String email, String userName) {
        User customer = new User(email, userName, "Customer User", "hash");
        setEntityId(customer, UUID.randomUUID());
        userRepository.putUser(customer);
        return customer;
    }

    private Organization newPendingOrganization(String name, String description, User requester) {
        Organization org = new Organization(name, "contact@test.vn", "0901234567", description, requester);
        setEntityId(org, UUID.randomUUID());
        org.submitForApproval();
        organizationRepository.putOrg(org);
        return org;
    }

    @Test
    @DisplayName("Admin lists and counts requests by status")
    void listsAndCountsRequestsByStatus() {
        UUID adminId = newAdmin();
        User requester = newCustomer("req@test.vn", "requester");
        newPendingOrganization("Org One", null, requester);
        newPendingOrganization("Org Two", null, requester);
        Organization draft = new Organization("Org Draft", "draft@test.vn", requester);
        setEntityId(draft, UUID.randomUUID());
        organizationRepository.putOrg(draft);

        List<OrganizationRequestSummaryDto> pending =
                service.listRequests(adminId, OrganizationStatus.PENDING_APPROVAL, 1, 20);
        assertEquals(2, pending.size());
        assertEquals(2, service.countRequests(adminId, OrganizationStatus.PENDING_APPROVAL));
        assertEquals(3, service.countRequests(adminId, null));
        assertEquals(1, service.listRequests(adminId, null, 2, 2).size());
    }

    @Test
    @DisplayName("Non admin cannot list or count requests")
    void listingRejectsNonAdmin() {
        UUID customerId = newCustomer("customer@test.vn", "customeruser").getId();

        SecurityException list = assertThrows(SecurityException.class, () ->
                service.listRequests(customerId, OrganizationStatus.PENDING_APPROVAL, 1, 20));
        assertTrue(list.getMessage().contains("FORBIDDEN"));
        SecurityException count = assertThrows(SecurityException.class, () ->
                service.countRequests(customerId, null));
        assertTrue(count.getMessage().contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("Real approval preserves organization approval without inventing an event")
    void realApprovalCreatesNoInitialEvent() {
        approveAndAssertInitialEvent(false);
    }

    @Test
    void demoApprovalRetainsFixtureInitialEvent() {
        System.setProperty("app.data.mode", "demo");
        approveAndAssertInitialEvent(true);
    }

    private void approveAndAssertInitialEvent(boolean demo) {
        UUID adminId = newAdmin();
        User requester = newCustomer("req@test.vn", "requester");
        Organization org = newPendingOrganization("Concert Corp",
                "Concert organizer\n[Event: Rock Night 2026 | Venue: My Dinh Stadium]", requester);

        Instant policyFrom = Instant.parse("2026-10-13T00:00:00Z");
        Optional<CommissionPolicy> policy = demo
                ? Optional.empty()
                : Optional.of(new CommissionPolicy(new BigDecimal("5"), 1000L, policyFrom,
                        policyFrom.plusSeconds(3600)));
        OrganizationRequestDetailDto approved = service.approve(adminId, org.getId(), policy);

        assertNotNull(approved);
        assertEquals(OrganizationStatus.APPROVED, approved.status());
        assertEquals(OrganizationStatus.APPROVED, org.getStatus());
        assertNull(org.getRejectionReason());
        assertEquals(requester.getId(), approved.requesterId());

        assertEquals(1, decisions.approvals.size());
        FakeDecisionRepository.ApprovalCall call = decisions.approvals.get(0);
        assertEquals(org.getId(), call.organizationId());
        assertEquals(adminId, call.adminId());
        if (demo) {
            assertNull(call.policy());
            assertEquals("Rock Night 2026", call.event().title());
            assertEquals("My Dinh Stadium", call.event().venue());
            assertNotNull(call.event().startTime());
            assertNotNull(call.event().endTime());
            assertTrue(call.event().startTime().isBefore(call.event().endTime()));
        } else {
            assertEquals(new BigDecimal("5"), call.policy().ratePercent());
            assertEquals(1000L, call.policy().fixedFee());
            assertEquals(policyFrom, call.policy().effectiveFrom());
            assertNull(call.event(), "Approval must not submit fabricated schedule or venue to SQL");
        }
    }

    @Test
    @DisplayName("Approve fails when the caller is not an admin")
    void approveFailsForNonAdmin() {
        User customer = newCustomer("customer@test.vn", "customeruser");
        Organization org = newPendingOrganization("Org Test", null, customer);

        SecurityException ex = assertThrows(SecurityException.class, () ->
                service.approve(customer.getId(), org.getId(), Optional.empty()));
        assertTrue(ex.getMessage().contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("Approve fails when the organization is not found")
    void approveFailsWhenNotFound() {
        UUID adminId = newAdmin();
        assertThrows(NoSuchElementException.class, () ->
                service.approve(adminId, UUID.randomUUID(), Optional.empty()));
    }

    @Test
    @DisplayName("Approve fails when the organization is not pending approval")
    void approveFailsWhenNotPending() {
        UUID adminId = newAdmin();
        User requester = newCustomer("req@test.vn", "requester");
        Organization draft = new Organization("Org Draft", "draft@test.vn", requester);
        setEntityId(draft, UUID.randomUUID());
        organizationRepository.putOrg(draft);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.approve(adminId, draft.getId(), Optional.empty()));
        assertTrue(ex.getMessage().contains("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Approve and reject validate their arguments")
    void validatesArguments() {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.approve(null, orgId, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> service.approve(adminId, null, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> service.reject(null, orgId, "A valid reason here"));
        assertThrows(IllegalArgumentException.class, () -> service.listRequests(null, null, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.countRequests(null, null));
    }

    @Test
    @DisplayName("Reject records the trimmed reason and returns the rejected request")
    void rejectRecordsReason() {
        UUID adminId = newAdmin();
        User requester = newCustomer("req@test.vn", "requester");
        Organization org = newPendingOrganization("Org To Reject", null, requester);

        OrganizationRequestDetailDto rejected = service.reject(adminId, org.getId(), "  Hồ sơ chưa đầy đủ thông tin  ");

        assertEquals(OrganizationStatus.REJECTED, rejected.status());
        assertEquals(List.of("Hồ sơ chưa đầy đủ thông tin"), decisions.rejections);
    }

    @Test
    @DisplayName("Reject requires a reason of 10 to 500 characters, an admin and a pending request")
    void rejectGuards() {
        UUID adminId = newAdmin();
        User requester = newCustomer("req@test.vn", "requester");
        Organization org = newPendingOrganization("Org To Reject", null, requester);

        assertThrows(IllegalArgumentException.class, () -> service.reject(adminId, org.getId(), "too short"));
        assertThrows(IllegalArgumentException.class, () -> service.reject(adminId, org.getId(), "x".repeat(501)));
        assertThrows(SecurityException.class, () ->
                service.reject(requester.getId(), org.getId(), "A perfectly valid reason"));
        assertThrows(NoSuchElementException.class, () ->
                service.reject(adminId, UUID.randomUUID(), "A perfectly valid reason"));
        assertTrue(decisions.rejections.isEmpty());
    }
}
