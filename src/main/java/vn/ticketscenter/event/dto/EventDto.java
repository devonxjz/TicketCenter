package vn.ticketscenter.event.dto;

import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record EventDto(
        UUID id,
        UUID organizationId,
        UUID categoryId,
        String title,
        String description,
        String venueName,
        String venueAddress,
        Instant saleStart,
        Instant saleEnd,
        Instant startTime,
        Instant endTime,
        String status,
        long version,
        String coverImageUrl,
        String imagePublicId) {
    public EventFields fields() {
        return new EventFields(
                categoryId,
                title,
                description,
                venueName,
                venueAddress,
                saleStart,
                saleEnd,
                startTime,
                endTime);
    }

    private Map<String, String> values() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id.toString());
        m.put("organizationId", organizationId.toString());
        m.put("categoryId", categoryId.toString());
        m.put("title", title);
        m.put("description", description);
        m.put("venueName", venueName);
        m.put("venueAddress", venueAddress);
        m.put("saleStart", saleStart.toString());
        m.put("saleEnd", saleEnd.toString());
        m.put("startTime", startTime.toString());
        m.put("endTime", endTime.toString());
        m.put("status", status);
        m.put("version", Long.toString(version));
        m.put("coverImageUrl", coverImageUrl == null ? "" : coverImageUrl);
        m.put("imagePublicId", imagePublicId == null ? "" : imagePublicId);
        return m;
    }

    public String snapshot() {
        return encode(values(), true);
    }

    public String toJson() {
        return encode(values(), false);
    }

    private static String encode(Map<String, String> m, boolean snapshot) {
        return m.entrySet().stream()
                .map(
                        e ->
                                HttpResponses.jsonString(e.getKey())
                                        + ":"
                                        + (!snapshot && e.getKey().equals("version")
                                                ? e.getValue()
                                                : !snapshot && e.getValue().isEmpty()
                                                        ? "null"
                                                        : HttpResponses.jsonString(e.getValue())))
                .collect(Collectors.joining(",", "{", "}"));
    }

    public static EventDto fromSnapshot(String json) {
        try {
            Map<String, String> m = JsonObjectParser.parse(new StringReader(json));
            return new EventDto(
                    UUID.fromString(m.get("id")),
                    UUID.fromString(m.get("organizationId")),
                    UUID.fromString(m.get("categoryId")),
                    m.get("title"),
                    m.get("description"),
                    m.get("venueName"),
                    m.get("venueAddress"),
                    Instant.parse(m.get("saleStart")),
                    Instant.parse(m.get("saleEnd")),
                    Instant.parse(m.get("startTime")),
                    Instant.parse(m.get("endTime")),
                    m.get("status"),
                    Long.parseLong(m.get("version")),
                    nullable(m.get("coverImageUrl")),
                    nullable(m.get("imagePublicId")));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid stored Event result", e);
        }
    }

    private static String nullable(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
