package vn.ticketscenter.settlement.dto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An ended event ready for settlement, as the workbench table shows it (end time in Vietnam time). */
public record ReadySettlementEventDto(UUID id, String title, String endTime) {

    private static final DateTimeFormatter END_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public static ReadySettlementEventDto from(Map<String, Object> row) {
        UUID id = UUID.fromString(row.get("id").toString());
        Object title = row.get("title");
        String endTime = "—";
        if (row.get("endTime") instanceof Instant at) {
            endTime = END_TIME.format(at);
        }
        return new ReadySettlementEventDto(id, title == null ? "" : title.toString(), endTime);
    }

    public static List<ReadySettlementEventDto> fromRows(List<Map<String, Object>> rows) {
        return rows.stream().map(ReadySettlementEventDto::from).toList();
    }
}
