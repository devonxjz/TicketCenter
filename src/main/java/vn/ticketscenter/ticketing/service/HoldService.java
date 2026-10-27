package vn.ticketscenter.ticketing.service;

import vn.ticketscenter.ticketing.dto.*;
import java.util.UUID;

public interface HoldService {
    HoldMutationReceipt mutate(HoldActor actor, HoldCommand command);

    CurrentHold current(HoldActor actor);

    String options(HoldActor actor, UUID eventId);

    int expire(int batchSize);
}
