package vn.ticketscenter.ticketing.dto;

import java.util.Objects;
import java.util.UUID;

public sealed interface HoldCommand permits HoldCommand.Create, HoldCommand.Cancel {
    UUID key();

    String fingerprint();

    int httpStatus();

    record Create(UUID key, CreateHoldRequest request) implements HoldCommand {
        public Create {
            requireKey(key);
            Objects.requireNonNull(request);
        }

        public String fingerprint() {
            return HoldMutationFingerprint.create(request);
        }

        public int httpStatus() {
            return 201;
        }
    }

    record Cancel(UUID key, UUID holdId) implements HoldCommand {
        public Cancel {
            requireKey(key);
            requireKey(holdId);
        }

        public String fingerprint() {
            return HoldMutationFingerprint.cancel(holdId);
        }

        public int httpStatus() {
            return 200;
        }
    }

    private static void requireKey(UUID id) {
        if (id == null || id.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("Nonzero UUID required");
        }
    }
}
