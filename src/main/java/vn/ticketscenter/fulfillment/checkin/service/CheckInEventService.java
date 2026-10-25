package vn.ticketscenter.fulfillment.checkin.service;

import vn.ticketscenter.fulfillment.checkin.dto.CheckInEventSummaryDto;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInWindowStatusDto;
import java.util.*;

public interface CheckInEventService {
    List<CheckInEventSummaryDto> events(UUID actorId, UUID organizationId);

    CheckInWindowStatusDto window(UUID actorId, UUID eventId);

    void invalidateEvent(UUID eventId, UUID organizationId);
}
