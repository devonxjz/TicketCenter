package vn.ticketscenter.identity.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows password guessing: after {@code maxFailures} failed logins for the same account and client within
 * the window, further attempts are refused until the window has passed. State is per application node.
 */
public final class LoginThrottle {
    private record Entry(int failures, Instant windowStart) { }

    private static final int MAX_TRACKED = 100_000;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    public LoginThrottle() {
        this(5, Duration.ofMinutes(15), Clock.systemUTC());
    }

    public LoginThrottle(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** @return seconds the caller must wait, or 0 when a login attempt is allowed */
    public long retryAfterSeconds(String identifier, String client) {
        Entry entry = entries.get(key(identifier, client));
        if (entry == null) return 0;
        Instant unlock = entry.windowStart().plus(window);
        if (!clock.instant().isBefore(unlock)) {
            entries.remove(key(identifier, client), entry);
            return 0;
        }
        if (entry.failures() < maxFailures) {
            return 0;
        }
        return Math.max(1, Duration.between(clock.instant(), unlock).toSeconds());
    }

    public void recordFailure(String identifier, String client) {
        Instant now = clock.instant();
        if (entries.size() >= MAX_TRACKED) {
            entries.values().removeIf(entry -> !now.isBefore(entry.windowStart().plus(window)));
        }
        if (entries.size() >= MAX_TRACKED) {
            return;
        }
        entries.merge(key(identifier, client), new Entry(1, now), (old, fresh) ->
                now.isBefore(old.windowStart().plus(window))
                        ? new Entry(old.failures() + 1, old.windowStart())
                        : fresh);
    }

    public void recordSuccess(String identifier, String client) {
        entries.remove(key(identifier, client));
    }

    private static String key(String identifier, String client) {
        String account = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        return account + '|' + (client == null ? "" : client);
    }
}
