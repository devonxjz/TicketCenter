package vn.ticketscenter.ticketing.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HoldExpiryPolicyTest {
    @Test
    void expiresTenMinutesAfterServerCreationAtTheExactBoundary() {
        Instant createdAt = Instant.parse("2026-10-09T00:00:00Z");
        Instant expiresAt = HoldExpiryPolicy.expiresAt(createdAt);
        assertEquals(Instant.parse("2026-10-09T00:10:00Z"), expiresAt);
        assertFalse(HoldExpiryPolicy.isExpired(expiresAt.minusNanos(1), expiresAt));
        assertTrue(HoldExpiryPolicy.isExpired(expiresAt, expiresAt));
        assertTrue(HoldExpiryPolicy.isExpired(expiresAt.plusNanos(1), expiresAt));
    }
}
