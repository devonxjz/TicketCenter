package vn.ticketscenter.ticketing.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record HoldMutationReceipt(
        int httpStatus, String body, boolean replayed, List<Change> changes) {
    public HoldMutationReceipt {
        Objects.requireNonNull(body);
        changes = List.copyOf(changes);
    }

    public record Change(UUID actorId, UUID eventId, UUID holdId, long version) {}
}
