package vn.ticketscenter.identity.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class OrganizationTest {

    @Test
    @DisplayName("Verify overloaded constructor with 5 arguments initializes fields correctly")
    void testOverloadedConstructor() {
        User requester = new User("req@example.com", "requester", "Requester User", "hash");
        Organization org = new Organization(
                "Acme Corp",
                "contact@acme.com",
                "0901234567",
                "Company description",
                requester
        );

        assertEquals("Acme Corp", org.getName());
        assertEquals("contact@acme.com", org.getContactEmail());
        assertEquals("0901234567", org.getContactPhone());
        assertEquals("Company description", org.getDescription());
        assertEquals(requester, org.getRequester());
        assertEquals(OrganizationStatus.DRAFT, org.getStatus());
        assertNull(org.getRejectionReason());
        assertNull(org.getSubmittedAt());
    }

    @Test
    @DisplayName("Verify submitForApproval sets status to PENDING_APPROVAL and records submittedAt")
    void testSubmitForApproval() {
        Organization org = new Organization("Acme Corp", "contact@acme.com", null);
        assertNull(org.getSubmittedAt());
        assertEquals(OrganizationStatus.DRAFT, org.getStatus());

        Instant before = Instant.now().minusMillis(100);
        org.submitForApproval();
        Instant after = Instant.now().plusMillis(100);

        assertEquals(OrganizationStatus.PENDING_APPROVAL, org.getStatus());
        assertNotNull(org.getSubmittedAt());
        assertTrue(org.getSubmittedAt().isAfter(before));
        assertTrue(org.getSubmittedAt().isBefore(after));
    }

    @Test
    @DisplayName("Verify approve sets status to APPROVED and clears rejection reason")
    void testApprove() {
        Organization org = new Organization("Acme Corp", "contact@acme.com", null);
        org.reject("Needs more detail");
        assertEquals(OrganizationStatus.REJECTED, org.getStatus());
        assertEquals("Needs more detail", org.getRejectionReason());

        org.approve();
        assertEquals(OrganizationStatus.APPROVED, org.getStatus());
        assertNull(org.getRejectionReason());
    }

    @Test
    @DisplayName("Verify reject sets status to REJECTED and records reason")
    void testReject() {
        Organization org = new Organization("Acme Corp", "contact@acme.com", null);
        org.reject("Invalid tax code");
        assertEquals(OrganizationStatus.REJECTED, org.getStatus());
        assertEquals("Invalid tax code", org.getRejectionReason());
    }
}
