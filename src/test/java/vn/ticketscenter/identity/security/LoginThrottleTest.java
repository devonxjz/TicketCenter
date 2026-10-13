package vn.ticketscenter.identity.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-13T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private final LoginThrottle throttle = new LoginThrottle(3, Duration.ofMinutes(15), clock);

    @Test
    void blocksAfterTheFailureLimitAndReleasesWhenTheWindowPasses() {
        for (int attempt = 0; attempt < 2; attempt++) throttle.recordFailure("User@Example.com", "10.0.0.1");
        assertEquals(0, throttle.retryAfterSeconds("user@example.com", "10.0.0.1"));
        throttle.recordFailure("user@example.com ", "10.0.0.1");
        assertTrue(throttle.retryAfterSeconds("user@example.com", "10.0.0.1") > 0);
        assertEquals(0, throttle.retryAfterSeconds("user@example.com", "10.0.0.2"), "another client is not locked out");
        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertEquals(0, throttle.retryAfterSeconds("user@example.com", "10.0.0.1"));
    }

    @Test
    void successClearsEarlierFailures() {
        for (int attempt = 0; attempt < 3; attempt++) throttle.recordFailure("a", "c");
        throttle.recordSuccess("a", "c");
        assertEquals(0, throttle.retryAfterSeconds("a", "c"));
    }
}
