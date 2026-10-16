package vn.ticketscenter.event.cache;

import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.dao.EventManagementRepository.Result;
import vn.ticketscenter.event.service.EventProblem;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory idempotency store used when Redis is not enabled or unavailable.
 * Maintains atomicity and lease lifecycle in local memory.
 */
public final class InMemoryEventIdempotencyStore implements EventIdempotencyStore {
    private record Entry(String hash, String value, String status, long expiresAt) { }
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Lease acquire(String key, String hash) {
        long now = System.currentTimeMillis();
        String token = UUID.randomUUID().toString();
        String value = "{\"status\":\"PROCESSING\",\"hash\":" + HttpResponses.jsonString(hash) + ",\"token\":" + HttpResponses.jsonString(token) + "}";
        Entry created = new Entry(hash, value, "PROCESSING", now + 120_000);

        Entry existing = store.compute(key, (k, current) -> {
            if (current == null || current.expiresAt() <= now) {
                return created;
            }
            return current;
        });

        if (existing == created) {
            return new Lease(key, value);
        }
        if (!Objects.equals(hash, existing.hash())) {
            throw EventProblem.conflict("Idempotency key đã dùng cho nội dung khác.");
        }
        throw EventProblem.conflict("Yêu cầu đang được xử lý hoặc cần đối chiếu kết quả DB.");
    }

    @Override
    public void completed(Lease lease, String hash, Result response) {
        String value = "{\"status\":\"COMPLETED\",\"hash\":" + HttpResponses.jsonString(hash)
            + ",\"statusCode\":" + HttpResponses.jsonString(Integer.toString(response.status())) + ",\"response\":" + HttpResponses.jsonString(response.json()) + "}";
        store.computeIfPresent(lease.key(), (k, current) -> {
            if (Objects.equals(current.value(), lease.value())) {
                return new Entry(hash, value, "COMPLETED", System.currentTimeMillis() + 86_400_000);
            }
            return current;
        });
    }

    @Override
    public void release(Lease lease) {
        store.computeIfPresent(lease.key(), (k, current) -> {
            if (Objects.equals(current.value(), lease.value())) {
                return null;
            }
            return current;
        });
    }
}
