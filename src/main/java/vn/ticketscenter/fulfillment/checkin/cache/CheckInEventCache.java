package vn.ticketscenter.fulfillment.checkin.cache;

import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository.EventInfo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckInEventCache {
    Optional<List<EventInfo>> events(UUID actorId, UUID organizationId);
    void putEvents(UUID actorId, UUID organizationId, List<EventInfo> events);
    Optional<EventInfo> window(UUID eventId);
    void putWindow(EventInfo event);
    /** Call after a committed cancellation/schedule change; deletes every actor's list for this organization. */
    void invalidate(UUID eventId, UUID organizationId);
}
