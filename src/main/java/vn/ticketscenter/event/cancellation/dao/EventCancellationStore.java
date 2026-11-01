package vn.ticketscenter.event.cancellation.dao;

import java.util.UUID;
import vn.ticketscenter.event.cancellation.dto.CancellationProgressDto;
import vn.ticketscenter.event.cancellation.dto.EventCancelRequestDto;
import vn.ticketscenter.identity.security.UserPrincipal;

public interface EventCancellationStore {
    void requireAdmin(UserPrincipal actor);

    CancellationProgressDto cancel(
            UserPrincipal actor, UUID eventId, EventCancelRequestDto request, UUID key);

    CancellationProgressDto progress(UserPrincipal actor, UUID eventId);
}
