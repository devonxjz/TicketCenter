package vn.ticketscenter.event.service;

import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.dao.EventRepository;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.storage.*;
import java.util.*;

public interface EventService {
    static EventService maintenance(EventRepository repository, StorageService storage) {
        return new EventServiceImpl(repository, storage, true);
    }

    EventDto save(UserPrincipal actor, EventSave command);

    EventDto migrateCover(UUID eventId, long version, UUID requestId, byte[] image);

    List<EventDto> publicEvents();

    EventDto publicEvent(UUID id);

    List<EventDto> managedEvents(UserPrincipal actor, UUID org);

    EventDto managedEvent(UserPrincipal actor, UUID id);

    String options(UserPrincipal actor);

    List<EventRepository.MigrationImage> migrationInventory();

    int cleanup(int limit);
}
