package vn.ticketscenter.event.cache;

import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.event.dao.EventManagementRepository.Result;

public interface EventIdempotencyStore {
    record Lease(String key, String value) {}

    Lease acquire(String key, String requestHash);

    void completed(Lease lease, String requestHash, Result response);

    void release(Lease lease);

    /** Redis when event.redis.enabled=true, otherwise an in-process store. */
    static EventIdempotencyStore configured() {
        return AppConfig.getBoolean("event.redis.enabled", false)
                ? new RedisEventIdempotencyStore()
                : new InMemoryEventIdempotencyStore();
    }
}
