package vn.ticketscenter.event.cancellation.service;

import java.util.UUID;
import vn.ticketscenter.event.cancellation.dto.EventCancelRequestDto;
import vn.ticketscenter.event.cancellation.dto.EventCancelResponseDto;
import vn.ticketscenter.identity.security.UserPrincipal;

public interface EventCancellationService {
    EventCancelResponseDto cancel(
            UserPrincipal actor, UUID id, EventCancelRequestDto request, UUID key);

    String progress(UserPrincipal actor, UUID id);
}
