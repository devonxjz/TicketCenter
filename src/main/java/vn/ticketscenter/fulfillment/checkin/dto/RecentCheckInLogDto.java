package vn.ticketscenter.fulfillment.checkin.dto;

import java.time.Instant;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.object;

public record RecentCheckInLogDto(
        UUID id,
        UUID ticketId,
        String result,
        Instant scannedAt,
        String staffName,
        String zoneName,
        String seatLabel) {
    public String toJson() {
        return object(
                field("id", nullableText(id)),
                field("ticketId", nullableText(ticketId)),
                field("result", nullableText(result)),
                field("scannedAt", nullableText(scannedAt)),
                field("staffName", nullableText(staffName)),
                field("zoneName", nullableText(zoneName)),
                field("seatLabel", nullableText(seatLabel)));
    }
}
