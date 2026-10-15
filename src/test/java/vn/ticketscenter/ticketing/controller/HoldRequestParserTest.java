package vn.ticketscenter.ticketing.controller;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.ticketing.dto.CreateHoldRequest;
import vn.ticketscenter.ticketing.dto.HoldSelection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HoldRequestParserTest {
    private static final String EVENT = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String ZONE = "00000000-0000-0000-0000-000000000002";
    private static final String SEAT = "00000000-0000-0000-0000-000000000003";
    private static final String BODY = "{\"eventId\":\"" + EVENT + "\",\"selections\":[{\"zoneId\":\"" + ZONE + "\",\"quantity\":3}]}";

    @Test
    void acceptsDecodedKeysCanonicalUuidAndNumericQuantity() {
        assertEquals(new CreateHoldRequest(UUID.fromString(EVENT), List.of(new HoldSelection(UUID.fromString(ZONE), null, 3))),
                parse(BODY.replace("eventId", "event\\u0049d").replace(EVENT, EVENT.toUpperCase()).replace("quantity", "\\u0071uantity")));
        assertEquals(parse(BODY), parse(BODY.replace("\"quantity\":3", "\"seatId\":null,\"quantity\":3")));
        assertEquals(1, parse(BODY.replace("\"quantity\":3", "\"seatId\":\"" + SEAT + "\",\"quantity\":1")).selections().getFirst().quantity());
    }

    @Test
    void acceptsOnlyJsonWhitespaceAndAnEmptyCancelObject() {
        assertEquals(parse(BODY), parse("\n\t " + BODY + "\r\n"));
        HoldRequestParser.parseCancel(stream(" \r\n{\t} "));
        for (String body : List.of("", "null", "[]", "{\"actorId\":\"" + EVENT + "\"}", "{},", "{}{}", "{,}", "{}\u000b")) {
            assertInvalid(() -> HoldRequestParser.parseCancel(stream(body)));
        }
        for (String whitespace : List.of("\u000b", "\f", "\u2003", "\u00a0", "\ufeff")) {
            assertInvalid(() -> parse(whitespace + BODY));
        }
    }

    @Test
    void rejectsDuplicateKeysAfterEscapeDecodingAtBothObjectLevels() {
        assertInvalid(() -> parse(BODY.replace("\"selections\"", "\"event\\u0049d\":\"" + EVENT + "\",\"selections\"")));
        assertInvalid(() -> parse(BODY.replace("\"quantity\":3", "\"quantity\":3,\"\\u0071uantity\":3")));
        assertInvalid(() -> parse(BODY.replace("\"quantity\":3", "\"seatId\":null,\"seatId\":null,\"quantity\":3")));
    }

    @Test
    void rejectsUnknownOrClientControlledFieldsAndWrongShapes() {
        for (String field : List.of("actorId", "buyerId", "price", "unitPrice", "status", "expiresAt", "unknown")) {
            assertInvalid(() -> parse(BODY.replace("\"selections\"", "\"" + field + "\":null,\"selections\"")));
            assertInvalid(() -> parse(BODY.replace("\"quantity\":3", "\"quantity\":3,\"" + field + "\":null")));
        }
        for (String body : List.of("{}", "[]", "null", BODY.replace("\"eventId\"", "\"event\""), BODY.replace("\"quantity\":3", "\"quantity\":null"),
                BODY.replace("\"quantity\":3", "\"quantity\":true"), BODY.replace("\"quantity\":3", "\"quantity\":{}"),
                BODY.replace("{\"zoneId\":\"" + ZONE + "\",\"quantity\":3}", "null"))) {
            assertInvalid(() -> parse(body));
        }
    }

    @Test
    void rejectsMalformedNumbersSeparatorsAndTrailingContent() {
        for (String number : List.of("\"3\"", "3.0", "3e0", "3E+0", "03", "-1", "+1", "0", "9", "33", "NaN", "Infinity")) {
            assertInvalid(() -> parse(BODY.replace("\"quantity\":3", "\"quantity\":" + number)));
        }
        for (String body : List.of(BODY + "{}", BODY + "x", BODY.replace("3}", "3,}"), BODY.replace("}]", "},]"),
                BODY.substring(0, BODY.length() - 1) + ",}", BODY.replace(",\"selections\"", "\"selections\""), BODY.replace(":3", " 3"))) {
            assertInvalid(() -> parse(body));
        }
    }

    @Test
    void rejectsPartialZeroAndMalformedUuidAndSurrogateEscapes() {
        for (String uuid : List.of("1-2-3-4-5", "00000000-0000-0000-0000-000000000000", EVENT + " ", EVENT.replace("-", ""),
                "\\uD800", "\\uDC00", "\\uD800x", "\\uD800\\u0041", "\\u+123", "\\u00xz", "\\q", "a\nb")) {
            assertInvalid(() -> parse(BODY.replace(EVENT, uuid)));
        }
        assertInvalid(() -> parse(BODY.replace("eventId", "event\\uD800Id")));
        assertInvalid(() -> parse(BODY.replace("eventId", "event\\uD83D\\uDE00Id")));
    }

    @Test
    void enforcesDtoTicketBoundsAndDuplicateSeatRules() {
        assertInvalid(() -> parse(BODY.replace("\"quantity\":3", "\"seatId\":\"" + SEAT + "\",\"quantity\":3")));
        String selection = "{\"zoneId\":\"" + ZONE + "\",\"seatId\":\"" + SEAT + "\",\"quantity\":1}";
        assertInvalid(() -> parse("{\"eventId\":\"" + EVENT + "\",\"selections\":[" + selection + "," + selection + "]}"));
        assertInvalid(() -> parse("{\"eventId\":\"" + EVENT + "\",\"selections\":[]}"));
        assertInvalid(() -> parse(BODY.replace("3}]", "8},{\"zoneId\":\"" + ZONE + "\",\"quantity\":1}]")));
    }

    @Test
    void capsActualBytesAt64KiBEvenWithUnknownLengthAndChunkedInput() {
        String exact = BODY + " ".repeat(HoldRequestParser.MAX_BODY_BYTES - BODY.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(parse(BODY), HoldRequestParser.parseCreate(new ByteArrayInputStream(exact.getBytes(StandardCharsets.UTF_8)) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) { return super.read(bytes, offset, Math.min(7, length)); }
        }));
        assertEquals(HoldRequestParser.Failure.BODY_TOO_LARGE,
                assertThrows(HoldRequestParser.ParseException.class, () -> parse(exact + " ")).failure());
        String multibyte = "{\"" + "é".repeat(40_000) + "\":0}";
        assertTrue(multibyte.length() < HoldRequestParser.MAX_BODY_BYTES);
        assertEquals(HoldRequestParser.Failure.BODY_TOO_LARGE,
                assertThrows(HoldRequestParser.ParseException.class, () -> parse(multibyte)).failure());
        assertEquals(HoldRequestParser.Failure.BODY_TOO_LARGE,
                assertThrows(HoldRequestParser.ParseException.class, () -> HoldRequestParser.parseCancel(stream(" ".repeat(65537)))).failure());
    }

    @Test
    void rejectsInvalidUtf8AndDoesNotExposeInputOrProviderFailures() {
        assertInvalid(() -> HoldRequestParser.parseCreate(new ByteArrayInputStream(new byte[]{(byte) 0xc3, 0x28})));
        var failure = assertThrows(HoldRequestParser.ParseException.class, () -> HoldRequestParser.parseCreate(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("private provider information"); }
        }));
        assertEquals("Invalid hold request body", failure.getMessage());
        assertNull(failure.getCause());
    }

    private static CreateHoldRequest parse(String body) { return HoldRequestParser.parseCreate(stream(body)); }
    private static InputStream stream(String body) { return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
    private static void assertInvalid(org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(HoldRequestParser.ParseException.class, action);
        assertEquals(HoldRequestParser.Failure.INVALID_BODY, failure.failure());
        assertEquals("Invalid hold request body", failure.getMessage());
        assertNull(failure.getCause());
    }
}
