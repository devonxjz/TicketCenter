package vn.ticketscenter.ticketing.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class HoldExpiryPolicy {
    public static final Duration LIFETIME = Duration.ofMinutes(10);

    private HoldExpiryPolicy() { }

    public static Instant expiresAt(Instant serverCreatedAt) {
        return Objects.requireNonNull(serverCreatedAt).plus(LIFETIME);
    }

    public static boolean isExpired(Instant now, Instant expiresAt) {
        return !Objects.requireNonNull(now).isBefore(Objects.requireNonNull(expiresAt));
    }
}
