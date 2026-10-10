package vn.ticketscenter.event.cache;

import java.util.Optional;

/** JSON DTO response payloads only; a failure is always a miss. */
public interface EventCatalogCache {
    Optional<String> get(String key);
    void put(String key, String json, int ttlSeconds);
}
