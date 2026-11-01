package vn.ticketscenter.config.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTest {

    @Test
    void objectSkipsAbsentOptionalFieldsAndKeepsOrder() {
        String json = Json.object(
                Json.field("a", Json.text(null)),
                Json.optional("b", null),
                Json.optional("c", "x\ty"),
                Json.field("d", Json.nullableText(null)),
                Json.field("e", Json.number(null)),
                Json.field("f", Json.money(null)),
                Json.field("g", Json.array(List.of(1, 2), Json::number)));

        assertEquals("{\"a\":\"\",\"c\":\"x\\ty\",\"d\":null,\"e\":null,\"f\":0,\"g\":[1,2]}", json);
    }

    @Test
    void controlCharactersAreEscaped() {
        assertEquals("\"a\\u0001b\\tc\"", Json.text("a\u0001b\tc"));
        assertEquals("1E+3", Json.money(new BigDecimal("1E+3")));
    }
}
