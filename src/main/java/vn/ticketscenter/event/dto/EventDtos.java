package vn.ticketscenter.event.dto;

import vn.ticketscenter.config.web.HttpResponses;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Public projections only. VND amounts are integer strings, times are UTC. */
public final class EventDtos {
    private EventDtos() { }
    public record Event(UUID eventId, UUID categoryId, String title, String description, String coverImageUrl,
                        String venueName, String venueAddress, Instant saleStart, Instant saleEnd,
                        Instant startTime, Instant endTime, BigDecimal minimumPrice,
                        String category, String organizationName, BigDecimal maximumPrice, boolean isSaleActive) {
        public String toJson() {
            return "{\"eventId\":" + json(eventId) + ",\"id\":" + json(eventId) + ",\"categoryId\":" + json(categoryId)
                    + ",\"title\":" + json(title) + ",\"description\":" + json(description)
                    + ",\"coverImageUrl\":" + json(coverImageUrl) + ",\"venueName\":" + json(venueName)
                    + ",\"venueAddress\":" + json(venueAddress) + ",\"venue\":" + json(venueName + ", " + venueAddress)
                    + ",\"saleStartTime\":" + json(saleStart) + ",\"saleEndTime\":" + json(saleEnd)
                    + ",\"saleStart\":" + json(saleStart) + ",\"saleEnd\":" + json(saleEnd)
                    + ",\"startTime\":" + json(startTime) + ",\"endTime\":" + json(endTime)
                    + ",\"minPrice\":" + money(minimumPrice) + ",\"maxPrice\":" + money(maximumPrice)
                    + ",\"category\":" + json(category) + ",\"organizationName\":" + json(organizationName)
                    + ",\"isSaleActive\":" + isSaleActive + ",\"status\":\"PUBLISHED\"}";
        }
    }
    public record Zone(UUID zoneId, UUID eventId, String name, String type, BigDecimal price,
                       long capacity, long heldQuantity, long soldQuantity, long availableQuantity) {
        public String toJson() {
            return "{\"zoneId\":" + json(zoneId) + ",\"eventId\":" + json(eventId)
                    + ",\"name\":" + json(name) + ",\"zoneType\":" + json(type) + ",\"basePrice\":" + money(price)
                    + ",\"totalCapacity\":" + capacity + ",\"heldQuantity\":" + heldQuantity
                    + ",\"soldQuantity\":" + soldQuantity + ",\"availableSeats\":" + availableQuantity + "}";
        }
    }
    public record Seat(UUID seatId, UUID zoneId, String rowName, int seatNumber, String label, String status) {
        public String toJson() {
            return "{\"seatId\":" + json(seatId) + ",\"zoneId\":" + json(zoneId) + ",\"rowName\":" + json(rowName)
                    + ",\"seatNumber\":" + seatNumber + ",\"seatLabel\":" + json(label) + ",\"status\":" + json(status) + "}";
        }
    }
    public record Category(UUID categoryId, String name, String slug) {
        public String toJson() { return "{\"categoryId\":" + json(categoryId) + ",\"name\":" + json(name) + ",\"slug\":" + json(slug) + "}"; }
    }
    public record Page(List<Event> events, long total) {
        public Page { events = List.copyOf(events); }
        public String toJson(EventFilter filter) {
            return "{\"data\":" + array(events, Event::toJson) + ",\"page\":" + filter.page()
                    + ",\"pageSize\":" + filter.pageSize() + ",\"total\":" + total
                    + ",\"totalPages\":" + (total / filter.pageSize() + (total % filter.pageSize() == 0 ? 0 : 1)) + "}";
        }
    }
    public static String json(Object value) { return value == null ? "null" : HttpResponses.jsonString(value.toString()); }
    private static String money(BigDecimal value) { return value == null ? "null" : json(value.toBigIntegerExact()); }
    public static <T> String array(List<T> items, Function<T, String> encode) {
        return items.stream().map(encode).collect(Collectors.joining(",", "[", "]"));
    }
    public static String data(String json) { return "{\"data\":" + json + "}"; }
}
