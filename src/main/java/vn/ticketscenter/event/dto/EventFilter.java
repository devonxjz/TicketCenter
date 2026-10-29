package vn.ticketscenter.event.dto;

import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Normalized public scope; request text never becomes SQL identifiers or Redis keys. */
public record EventFilter(
        String keyword,
        UUID categoryId,
        Instant from,
        Instant to,
        String sort,
        int page,
        int pageSize) {
    private static final Map<String, String> SORTS =
            Map.of(
                    "startTime_asc",
                    "start_time ASC",
                    "startTime_desc",
                    "start_time DESC",
                    "price_asc",
                    "minimum_price ASC",
                    "price_desc",
                    "minimum_price DESC");

    public EventFilter {
        keyword = keyword == null ? "" : keyword.strip();
        if (keyword.length() > 100
                || sort == null
                || !SORTS.containsKey(sort)
                || page < 1
                || pageSize < 1
                || pageSize > 50
                || (long) (page - 1) * pageSize > 1_000_000
                || (from != null && to != null && !from.isBefore(to))) {
            throw new IllegalArgumentException("Invalid event filters");
        }
    }

    public static EventFilter parse(Map<String, String> parameters) {
        String category = text(parameters, "categoryId", "");
        return new EventFilter(
                text(parameters, "keyword", ""),
                category.isEmpty() ? null : id(category),
                instant(parameters.get("from")),
                instant(parameters.get("to")),
                text(parameters, "sort", "startTime_asc"),
                integer(parameters, "page", 1),
                catalogSize(parameters));
    }

    private static int catalogSize(Map<String, String> parameters) {
        int requested = integer(parameters, "pageSize", 6);
        if (requested < 1 || requested > 50) {
            throw new IllegalArgumentException("Invalid page size");
        }
        return Math.min(6, requested);
    }

    public static UUID id(String value) {
        if (value == null
                || !value.matches(
                        "(?i)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
            throw new IllegalArgumentException("Invalid UUID");
        }
        return UUID.fromString(value);
    }

    public int offset() {
        return (page - 1) * pageSize;
    }

    public String orderBy() {
        return SORTS.get(sort) + ", event_id ASC";
    }

    public String keywordPattern() {
        return "%"
                + keyword.replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                        .replace("[", "\\[")
                + "%";
    }

    public String cacheKey() {
        // Length-prefixing prevents delimiter collisions in free-form search text.
        String canonical =
                "scope=PUBLIC|keyword="
                        + keyword.length()
                        + ":"
                        + keyword
                        + "|category="
                        + categoryId
                        + "|from="
                        + from
                        + "|to="
                        + to
                        + "|sort="
                        + sort
                        + "|page="
                        + page
                        + "|pageSize="
                        + pageSize;
        try {
            return "tc:event:list:v1:"
                    + HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String text(Map<String, String> parameters, String key, String fallback) {
        String value = parameters.get(key);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static int integer(Map<String, String> parameters, String key, int fallback) {
        String value = parameters.get(key);
        if (value == null) {
            return fallback;
        }
        if (!value.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        return Integer.parseInt(value);
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Instant result = Instant.parse(value.strip());
            int year = result.atOffset(ZoneOffset.UTC).getYear();
            if (year < 1 || year > 9999) {
                throw new IllegalArgumentException("Date outside SQL Server range");
            }
            return result;
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid UTC date");
        }
    }
}
