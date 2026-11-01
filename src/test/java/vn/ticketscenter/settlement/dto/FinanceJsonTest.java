package vn.ticketscenter.settlement.dto;

import vn.ticketscenter.settlement.dto.FinanceJson;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FinanceJsonTest {
    @Test void serializesExplicitDtoScalarsWithoutRoundingOrGraphs() {
        var dto = new LinkedHashMap<String, Object>();
        dto.put("id", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        dto.put("amount", new BigDecimal("9223372036854775807"));
        dto.put("at", Instant.parse("2026-10-10T00:00:00Z"));
        dto.put("count", 2); dto.put("ready", false); dto.put("missing", null);
        assertEquals("{\"id\":\"00000000-0000-0000-0000-000000000001\",\"amount\":\"9223372036854775807\",\"at\":\"2026-10-10T00:00:00Z\",\"count\":2,\"ready\":false,\"missing\":null}", FinanceJson.write(dto));
    }
    @Test void escapesUserTextAndKeepsUnicode() {
        assertEquals("[\"Hà Nội 🎟️\",\"a\\\"b\\\\c\\r\\n\\t\\u0001\"]", FinanceJson.write(List.of("Hà Nội 🎟️", "a\"b\\c\r\n\t\u0001")));
    }
    @Test void decimalRateStaysExact() {
        assertEquals("\"5.1250\"", FinanceJson.write(new BigDecimal("5.1250")));
    }
    @Test void rejectsEntityAndFloatingMoneyInsteadOfGuessing() {
        assertThrows(IllegalArgumentException.class, () -> FinanceJson.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> FinanceJson.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> FinanceJson.write(1.2));
        assertThrows(IllegalArgumentException.class, () -> FinanceJson.write(Map.of(1,"value")));
    }
    @Test void rejectsInvalidUnicodeAndNulWithNoPayloadInError() {
        for (String raw : List.of("secret\uD800", "secret\uDC00", "secret\u0000")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> FinanceJson.write(raw));
            assertFalse(failure.getMessage().contains("secret"));
        }
    }
}
