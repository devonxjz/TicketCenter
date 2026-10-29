package vn.ticketscenter.settlement.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadySettlementEventDtoTest {
    @Test
    void endTimeIsShownInVietnamTime() {
        UUID id = UUID.randomUUID();
        var event =
                ReadySettlementEventDto.from(
                        Map.of("id", id, "title", "A", "endTime", Instant.parse("2026-10-14T01:00:00Z")));
        assertEquals(new ReadySettlementEventDto(id, "A", "14/10/2026 08:00"), event);
    }

    @Test
    void missingEndTimeShowsADash() {
        UUID id = UUID.randomUUID();
        var events = ReadySettlementEventDto.fromRows(List.of(Map.of("id", id, "title", "A")));
        assertEquals("—", events.get(0).endTime());
    }

    @Test
    void rejectsIdsThatAreNotUuids() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReadySettlementEventDto.from(Map.of("id", "\" onmouseover=\"bad", "title", "x")));
    }
}
