package vn.ticketscenter.event.cache;

import java.util.UUID;

@FunctionalInterface
public interface EventCacheInvalidator {
    void invalidateCommitted(UUID eventId, UUID organizationId);
}
