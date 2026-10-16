package vn.ticketscenter.event.cache;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.dao.EventManagementRepository.Result;
import vn.ticketscenter.event.service.EventProblem;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryEventIdempotencyStoreTest {

    @Test
    void acquireLeaseSucceedsAndGatesConcurrentRequests() {
        var store = new InMemoryEventIdempotencyStore();
        String key = "test-key-1";
        String hash = "hash123";

        var lease = store.acquire(key, hash);
        assertNotNull(lease);
        assertEquals(key, lease.key());
        assertTrue(lease.value().contains("PROCESSING"));
        assertTrue(lease.value().contains(hash));

        // Same hash conflicts
        var conflictSame = assertThrows(EventProblem.class, () -> store.acquire(key, hash));
        assertEquals(409, conflictSame.status());
        assertTrue(conflictSame.getMessage().contains("Yêu cầu đang được xử lý"));

        // Different hash conflicts
        var conflictDiff = assertThrows(EventProblem.class, () -> store.acquire(key, "differentHash"));
        assertEquals(409, conflictDiff.status());
        assertTrue(conflictDiff.getMessage().contains("Idempotency key đã dùng"));
    }

    @Test
    void releaseAllowsReacquisition() {
        var store = new InMemoryEventIdempotencyStore();
        String key = "test-key-2";
        String hash = "hash456";

        var lease = store.acquire(key, hash);
        assertNotNull(lease);

        store.release(lease);

        var secondLease = store.acquire(key, hash);
        assertNotNull(secondLease);
    }

    @Test
    void completedMarksLeaseWithoutThrowing() {
        var store = new InMemoryEventIdempotencyStore();
        String key = "test-key-3";
        String hash = "hash789";

        var lease = store.acquire(key, hash);
        assertNotNull(lease);

        var result = new Result(200, "{\"ok\":true}", UUID.randomUUID(), UUID.randomUUID(), null);
        assertDoesNotThrow(() -> store.completed(lease, hash, result));
    }
}
