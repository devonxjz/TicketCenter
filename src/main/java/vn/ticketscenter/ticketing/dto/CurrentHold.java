package vn.ticketscenter.ticketing.dto;

import java.util.Objects;
import java.util.Optional;

public record CurrentHold(String body, Optional<ActiveHoldProjection> projection) {
    public CurrentHold {
        Objects.requireNonNull(body);
        Objects.requireNonNull(projection);
    }
}
