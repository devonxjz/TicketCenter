package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.OrganizationStatus;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Redis-backed replay protection for submitting an organisation request: the first result for an
 * {@code Idempotency-Key} is cached for a day, and reusing the key with a different payload is a conflict.
 * Every Redis failure degrades to "no cache" rather than failing the request.
 */
public class OrganizationRequestIdempotency {

    private static final Logger LOGGER =
            Logger.getLogger(OrganizationRequestIdempotency.class.getName());
    private static final int TTL_SECONDS = 86400;

    /** The slice of Redis this cache needs. */
    interface Store {
        String get(String key);

        void setex(String key, int seconds, String value);
    }

    private final Store store;

    private OrganizationRequestIdempotency(Store store) {
        this.store = store;
    }

    /** Never remembers anything. */
    public static OrganizationRequestIdempotency none() {
        return new OrganizationRequestIdempotency(null);
    }

    public static OrganizationRequestIdempotency redis(RedisClientProvider provider) {
        if (provider == null) {
            return none();
        }
        return new OrganizationRequestIdempotency(
                new Store() {
                    @Override
                    public String get(String key) {
                        try (var jedis = provider.getResource()) {
                            return jedis.get(key);
                        }
                    }

                    @Override
                    public void setex(String key, int seconds, String value) {
                        try (var jedis = provider.getResource()) {
                            jedis.setex(key, seconds, value);
                        }
                    }
                });
    }

    static OrganizationRequestIdempotency withStore(Store store) {
        return new OrganizationRequestIdempotency(store);
    }

    /** The Redis key for this submission, or {@code null} when no replay protection applies. */
    public String keyFor(UUID actorId, String idempotencyKey) {
        if (store == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return "idempotency:org-request:" + actorId + ":" + idempotencyKey.trim();
    }

    /**
     * The result remembered for {@code key}, if any.
     *
     * @throws IdentityException {@code IDEMPOTENCY_CONFLICT} when the key was used with a different payload
     */
    public Optional<OrganizationRequestDetailDto> find(String key, String payloadHash) {
        try {
            String cached = store.get(key);
            if (cached == null || !cached.startsWith("{")) {
                return Optional.empty();
            }
            Map<String, String> fields = JsonObjectParser.parse(new StringReader(cached));
            String cachedHash = fields.get("payloadHash");
            if (cachedHash != null && !cachedHash.equals(payloadHash)) {
                throw new IdentityException(
                        IdentityError.IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key đã được sử dụng với nội dung khác");
            }
            return Optional.ofNullable(deserialize(fields));
        } catch (IdentityException conflict) {
            throw conflict;
        } catch (Exception failure) {
            LOGGER.log(Level.WARNING, "Failed to read idempotency cache: " + failure.getMessage());
            return Optional.empty();
        }
    }

    public void remember(String key, String payloadHash, OrganizationRequestDetailDto result) {
        try {
            String entry =
                    "{\"payloadHash\":"
                            + HttpResponses.jsonString(payloadHash)
                            + ","
                            + result.toJson().substring(1);
            store.setex(key, TTL_SECONDS, entry);
        } catch (Exception failure) {
            LOGGER.log(
                    Level.WARNING, "Failed to cache idempotency result: " + failure.getMessage());
        }
    }

    /** Identifies a submission by its trimmed content, so only an identical retry replays the result. */
    public static String payloadHash(OrganizationRequestCreateDto dto) {
        try {
            String normalized =
                    String.join(
                            "\n",
                            Stream.of(
                                            dto.name(),
                                            dto.contactEmail(),
                                            dto.contactPhone(),
                                            dto.description(),
                                            dto.eventTitle(),
                                            dto.eventDate(),
                                            dto.eventStartTime(),
                                            dto.eventEndTime(),
                                            dto.eventVenue())
                                    .map(value -> value == null ? "" : value.trim())
                                    .toList());
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static OrganizationRequestDetailDto deserialize(Map<String, String> fields) {
        try {
            return new OrganizationRequestDetailDto(
                    uuid(fields.get("organizationId")),
                    fields.get("name"),
                    fields.get("contactEmail"),
                    nullIfBlank(fields.get("contactPhone")),
                    nullIfBlank(fields.get("description")),
                    isBlank(fields.get("status"))
                            ? null
                            : OrganizationStatus.valueOf(fields.get("status")),
                    uuid(fields.get("requesterId")),
                    isBlank(fields.get("submittedAt"))
                            ? null
                            : Instant.parse(fields.get("submittedAt")),
                    nullIfBlank(fields.get("rejectionReason")));
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to deserialize cached detail: " + e.getMessage());
            return null;
        }
    }

    private static UUID uuid(String value) {
        return isBlank(value) ? null : UUID.fromString(value);
    }

    private static String nullIfBlank(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
