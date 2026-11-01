package vn.ticketscenter.event.controller;

import java.nio.ByteBuffer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import vn.ticketscenter.event.EventException;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.storage.ImageContentValidator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class EventMultipartParser {
    private static final Set<String> FIELDS =
            Set.of(
                    "title",
                    "description",
                    "categoryId",
                    "venueName",
                    "venueAddress",
                    "saleStart",
                    "saleEnd",
                    "startTime",
                    "endTime",
                    "expectedVersion",
                    "_csrf");

    private EventMultipartParser() {}

    public static EventSave parse(HttpServletRequest request, EventSave.Kind kind, UUID target)
            throws IOException, ServletException {
        if (request.getContentType() == null
                || !request.getContentType()
                        .toLowerCase(Locale.ROOT)
                        .startsWith("multipart/form-data")) {
            throw new EventException(415, "CONTENT_TYPE_INVALID", "Multipart form is required");
        }
        UUID key = id(request.getHeader("Idempotency-Key"));
        Map<String, String> fields = new HashMap<>();
        byte[] image = null;
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (Part part : request.getParts()) {
            if (++count > 13 || !seen.add(part.getName())) {
                throw new IllegalArgumentException("Duplicate or excessive multipart fields");
            }
            if (part.getName().equals("file")) {
                if (part.getSize() > ImageContentValidator.DEFAULT_MAX_BYTES) {
                    throw new EventException(413, "IMAGE_TOO_LARGE", "Image exceeds 5 MiB");
                }
                if (part.getSize() == 0
                        && part.getSubmittedFileName() != null
                        && !part.getSubmittedFileName().isEmpty()) {
                    throw new IllegalArgumentException("Selected image is empty");
                }
                if (part.getSize() > 0) {
                    try (InputStream stream = part.getInputStream()) {
                        image = ImageContentValidator.readAndValidate(stream);
                    }
                }
            } else {
                if (!FIELDS.contains(part.getName())
                        || part.getSubmittedFileName() != null
                        || part.getSize() > 20_000) {
                    throw new IllegalArgumentException("Unknown or oversized multipart field");
                }
                try (InputStream stream = part.getInputStream()) {
                    byte[] bytes = stream.readNBytes(20_001);
                    if (bytes.length > 20_000) {
                        throw new IllegalArgumentException("Oversized field");
                    }
                    fields.put(
                            part.getName(),
                            StandardCharsets.UTF_8
                                    .newDecoder()
                                    .decode(ByteBuffer.wrap(bytes))
                                    .toString());
                }
            }
        }
        if (kind == EventSave.Kind.COVER) {
            if (fields.keySet().stream()
                    .anyMatch(n -> !Set.of("expectedVersion", "_csrf").contains(n))) {
                throw new IllegalArgumentException("Unexpected cover fields");
            }
            return new EventSave(
                    key, kind, target, version(fields.get("expectedVersion")), null, image);
        }
        if (kind == EventSave.Kind.CREATE && fields.containsKey("expectedVersion")) {
            throw new IllegalArgumentException("Create does not accept expectedVersion");
        }
        EventFields value =
                new EventFields(
                        id(fields.get("categoryId")),
                        fields.get("title"),
                        fields.get("description"),
                        fields.get("venueName"),
                        fields.get("venueAddress"),
                        instant(fields.get("saleStart")),
                        instant(fields.get("saleEnd")),
                        instant(fields.get("startTime")),
                        instant(fields.get("endTime")));
        return new EventSave(
                key,
                kind,
                target,
                kind == EventSave.Kind.CREATE ? 0 : version(fields.get("expectedVersion")),
                value,
                image);
    }

    public static UUID id(String value) {
        if (value == null
                || !value.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Invalid UUID");
        }
        return UUID.fromString(value);
    }

    private static long version(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("Invalid expectedVersion");
        }
        return Long.parseLong(value);
    }

    private static Instant instant(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Required Event time");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Invalid Event time", invalid);
        }
    }
}
