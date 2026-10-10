package vn.ticketscenter.event.dto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record EventFields(
        UUID categoryId,
        String title,
        String description,
        String venueName,
        String venueAddress,
        Instant saleStart,
        Instant saleEnd,
        Instant startTime,
        Instant endTime) {
    public EventFields {
        Objects.requireNonNull(categoryId, "categoryId");
        title = text(title, 250, "title");
        description = text(description, 5000, "description");
        venueName = text(venueName, 250, "venueName");
        venueAddress = text(venueAddress, 500, "venueAddress");
        saleStart = time(saleStart);
        saleEnd = time(saleEnd);
        startTime = time(startTime);
        endTime = time(endTime);
        if (!saleStart.isBefore(saleEnd)
                || saleEnd.isAfter(startTime)
                || !startTime.isBefore(endTime))
            throw new IllegalArgumentException("Invalid Event schedule");
    }

    private static String text(String value, int max, String name) {
        if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid " + name);
        return value.strip();
    }

    private static Instant time(Instant value) {
        Instant result = Objects.requireNonNull(value, "Event time").truncatedTo(ChronoUnit.MILLIS);
        if (result.isBefore(Instant.parse("1900-01-01T00:00:00Z"))
                || result.isAfter(Instant.parse("9999-12-31T23:59:59Z")))
            throw new IllegalArgumentException("Event time outside supported range");
        return result;
    }
}
