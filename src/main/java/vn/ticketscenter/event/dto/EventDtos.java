package vn.ticketscenter.event.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.array;
import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

/** Public projections only. VND amounts are integer strings, times are UTC. */
public final class EventDtos {
    private EventDtos() {}

    public record Event(
            UUID eventId,
            UUID categoryId,
            String title,
            String description,
            String coverImageUrl,
            String venueName,
            String venueAddress,
            Instant saleStart,
            Instant saleEnd,
            Instant startTime,
            Instant endTime,
            BigDecimal minimumPrice,
            String category,
            String organizationName,
            BigDecimal maximumPrice,
            boolean isSaleActive) {
        public String toJson() {
            return object(
                    field("eventId", nullableText(eventId)),
                    field("id", nullableText(eventId)),
                    field("categoryId", nullableText(categoryId)),
                    field("title", nullableText(title)),
                    field("description", nullableText(description)),
                    field("coverImageUrl", nullableText(coverImageUrl)),
                    field("venueName", nullableText(venueName)),
                    field("venueAddress", nullableText(venueAddress)),
                    field("venue", nullableText(venueName + ", " + venueAddress)),
                    field("saleStartTime", nullableText(saleStart)),
                    field("saleEndTime", nullableText(saleEnd)),
                    field("saleStart", nullableText(saleStart)),
                    field("saleEnd", nullableText(saleEnd)),
                    field("startTime", nullableText(startTime)),
                    field("endTime", nullableText(endTime)),
                    field("minPrice", money(minimumPrice)),
                    field("maxPrice", money(maximumPrice)),
                    field("category", nullableText(category)),
                    field("organizationName", nullableText(organizationName)),
                    field("isSaleActive", number(isSaleActive)),
                    field("status", text("PUBLISHED")));
        }
    }

    public record Zone(
            UUID zoneId,
            UUID eventId,
            String name,
            String type,
            BigDecimal price,
            long capacity,
            long heldQuantity,
            long soldQuantity,
            long availableQuantity) {
        public String toJson() {
            return object(
                    field("zoneId", nullableText(zoneId)),
                    field("eventId", nullableText(eventId)),
                    field("name", nullableText(name)),
                    field("zoneType", nullableText(type)),
                    field("basePrice", money(price)),
                    field("totalCapacity", number(capacity)),
                    field("heldQuantity", number(heldQuantity)),
                    field("soldQuantity", number(soldQuantity)),
                    field("availableSeats", number(availableQuantity)));
        }
    }

    public record Seat(
            UUID seatId, UUID zoneId, String rowName, int seatNumber, String label, String status) {
        public String toJson() {
            return object(
                    field("seatId", nullableText(seatId)),
                    field("zoneId", nullableText(zoneId)),
                    field("rowName", nullableText(rowName)),
                    field("seatNumber", number(seatNumber)),
                    field("seatLabel", nullableText(label)),
                    field("status", nullableText(status)));
        }
    }

    public record Category(UUID categoryId, String name, String slug) {
        public String toJson() {
            return object(
                    field("categoryId", nullableText(categoryId)),
                    field("name", nullableText(name)),
                    field("slug", nullableText(slug)));
        }
    }

    public record Page(List<Event> events, long total) {
        public Page {
            events = List.copyOf(events);
        }

        public String toJson(EventFilter filter) {
            return object(
                    field("data", array(events, Event::toJson)),
                    field("page", number(filter.page())),
                    field("pageSize", number(filter.pageSize())),
                    field("total", number(total)),
                    field(
                            "totalPages",
                            number(total / filter.pageSize() + (total % filter.pageSize() == 0 ? 0 : 1))));
        }
    }

    private static String money(BigDecimal value) {
        return nullableText(value == null ? null : value.toBigIntegerExact());
    }

    public static String data(String json) {
        return object(field("data", json));
    }
}
