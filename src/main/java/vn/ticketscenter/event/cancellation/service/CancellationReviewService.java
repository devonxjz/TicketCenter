package vn.ticketscenter.event.cancellation.service;

import java.util.UUID;
import vn.ticketscenter.identity.security.UserPrincipal;

/** SQL owns current authority, durable receipts, state and the atomic approval/cancellation. */
public interface CancellationReviewService {
    @FunctionalInterface
    interface Store {
        String execute(UserPrincipal actor, UUID eventId, String action, String reason, UUID key);
    }

    String execute(UserPrincipal actor, UUID eventId, String action, String reason, UUID key);
}
