package vn.ticketscenter.ticketing.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import vn.ticketscenter.config.error.AppException;
import vn.ticketscenter.config.redis.RedisScripts;

/**
 * Coordinates duplicate requests, without authorizing inventory changes.
 * Before mutation, callers must recover durable DB state even after Acquired.
 * Redis data loss or completed-entry expiry can make an old key appear new.
 * Callers must check current authorization before replay and complete only after DB commit.
 * SQL retains durable receipts when a bounded Redis entry expires.
 */
public final class RedisHoldIdempotency {
    public static final Duration MAX_RESULT_RETENTION = Duration.ofMinutes(10);
    private final Supplier<Jedis> connections;

    public RedisHoldIdempotency(Supplier<Jedis> connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    public sealed interface Result permits Acquired, InProgress, Replay, Conflict {}

    public record Acquired(Claim claim) implements Result {}

    public record InProgress() implements Result {}

    public record Replay(CompletedResponse response) implements Result {}

    public record Conflict() implements Result {}

    public record CompletedResponse(int status, String contentType, String body) {
        public CompletedResponse {
            if (status < 200
                    || status > 599
                    || contentType == null
                    || contentType.isBlank()
                    || body == null
                    || contentType
                            .chars()
                            .anyMatch(character -> character < 32 || character == 127)) {
                throw new IllegalArgumentException(
                        "a completed response needs status, content type, and body");
            }
            if (!new String(contentType.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                            .equals(contentType)
                    || !new String(body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                            .equals(body)) {
                throw new IllegalArgumentException("a completed response must be valid UTF-8 text");
            }
        }
    }

    public static final class Claim {
        private final String redisKey;
        private final String requestHash;
        private final String pendingValue;

        private Claim(String redisKey, String requestHash) {
            this.redisKey = redisKey;
            this.requestHash = requestHash;
            this.pendingValue = "1|P|" + requestHash + "|" + UUID.randomUUID();
        }
    }

    public Result acquire(UUID actorId, String key, String requestHash) {
        String redisKey = redisKey(actorId, key);
        if (requestHash == null || !requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestHash must be a lowercase SHA-256 hash");
        }
        Claim claim = new Claim(redisKey, requestHash);
        try (Jedis jedis = connections.get()) {
            if ("OK"
                    .equals(
                            jedis.set(
                                    redisKey,
                                    claim.pendingValue,
                                    SetParams.setParams()
                                            .nx()
                                            .px(MAX_RESULT_RETENTION.toMillis())))) {
                return new Acquired(claim);
            }
            return readResult(jedis.get(redisKey), requestHash);
        } catch (RuntimeException unavailable) {
            throw unavailable();
        }
    }

    /** False means this claim no longer owns the entry. It is not permission to retry a DB mutation. */
    public boolean complete(Claim claim, CompletedResponse response, Duration retention) {
        Objects.requireNonNull(claim);
        Objects.requireNonNull(response);
        long retentionMillis = retentionMillis(retention);
        String completed = completedValue(claim.requestHash, response);
        try (Jedis jedis = connections.get()) {
            Object result =
                    jedis.eval(
                            RedisScripts.REPLACE_IF_EQUALS_PX,
                            List.of(claim.redisKey),
                            List.of(claim.pendingValue, completed, Long.toString(retentionMillis)));
            if (Long.valueOf(1).equals(result)) {
                return true;
            }
            if (Long.valueOf(0).equals(result)) {
                String current = jedis.get(claim.redisKey);
                if (current != null) {
                    readResult(current, claim.requestHash);
                }
                return false;
            }
            throw unavailable();
        } catch (RuntimeException unavailable) {
            throw unavailable();
        }
    }

    private static AppException unavailable() {
        return new AppException(503, "HOLD_UNAVAILABLE", "Hold idempotency is unavailable");
    }

    public static String redisKey(UUID actorId, String key) {
        if (actorId == null || actorId.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("actorId must be a nonzero UUID");
        }
        if (key == null
                || key.isEmpty()
                || key.length() > 128
                || key.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 1..128 visible ASCII characters");
        }
        return "tc:idem:hold:" + actorId + ":" + key;
    }

    /**
     * Caller must first confirm this exact actor, key, fingerprint and result in durable DB state.
     * This reconciles Redis only. False never authorizes retry, and absent entries remain absent.
     */
    public boolean recoverConfirmedResult(
            UUID actorId,
            String key,
            String requestHash,
            CompletedResponse response,
            Duration retention) {
        String redisKey = redisKey(actorId, key);
        if (requestHash == null || !requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestHash must be a lowercase SHA-256 hash");
        }
        Objects.requireNonNull(response);
        long millis = retentionMillis(retention);
        String completed = completedValue(requestHash, response);
        try (Jedis jedis = connections.get()) {
            String observed = jedis.get(redisKey);
            if (observed == null) return false;
            Result state = readResult(observed, requestHash);
            if (state instanceof Replay replay) return replay.response().equals(response);
            if (!(state instanceof InProgress)) return false;
            Object result =
                    jedis.eval(
                            RedisScripts.REPLACE_IF_EQUALS_PX,
                            List.of(redisKey),
                            List.of(observed, completed, Long.toString(millis)));
            if (Long.valueOf(1).equals(result)) return true;
            if (Long.valueOf(0).equals(result)) return false;
            throw unavailable();
        } catch (RuntimeException unavailable) {
            throw unavailable();
        }
    }

    private static Result readResult(String value, String requestHash) {
        if (value == null) {
            throw unavailable();
        }
        String[] fields = value.split("\\|", -1);
        if (fields.length < 4 || !fields[0].equals("1") || !fields[2].matches("[0-9a-f]{64}")) {
            throw unavailable();
        }
        CompletedResponse response = null;
        if (fields[1].equals("P") && fields.length == 4) {
            UUID token = UUID.fromString(fields[3]);
            if (!token.toString().equals(fields[3]) || token.equals(new UUID(0, 0))) {
                throw unavailable();
            }
        } else if (fields[1].equals("C") && fields.length == 6) {
            int status = Integer.parseInt(fields[3]);
            if (!Integer.toString(status).equals(fields[3])) {
                throw unavailable();
            }
            response = new CompletedResponse(status, decode(fields[4]), decode(fields[5]));
        } else {
            throw unavailable();
        }
        if (!fields[2].equals(requestHash)) {
            return new Conflict();
        }
        return response == null ? new InProgress() : new Replay(response);
    }

    private static long retentionMillis(Duration retention) {
        if (retention == null
                || retention.isNegative()
                || retention.isZero()
                || retention.compareTo(MAX_RESULT_RETENTION) > 0
                || retention.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    "result retention must be 1 millisecond..600 seconds in whole milliseconds");
        }
        return retention.toMillis();
    }

    private static String completedValue(String requestHash, CompletedResponse response) {
        return "1|C|"
                + requestHash
                + "|"
                + response.status()
                + "|"
                + encode(response.contentType())
                + "|"
                + encode(response.body());
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        if (!encode(decoded).equals(value)) {
            throw unavailable();
        }
        return decoded;
    }
}
