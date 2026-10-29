package vn.ticketscenter.ticketing.dto;

import java.util.Objects;
import java.util.UUID;

public record HoldActor(UUID id, int authVersion) {
    public HoldActor {
        Objects.requireNonNull(id);
        if (id.equals(new UUID(0, 0)) || authVersion < 0) {
            throw new IllegalArgumentException("Invalid Hold actor");
        }
    }
}
