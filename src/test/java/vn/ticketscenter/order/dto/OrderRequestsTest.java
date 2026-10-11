package vn.ticketscenter.order.dto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class OrderRequestsTest {
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test void parsesCreateAcrossSingleByteReads() throws IOException {
        byte[] content = (" { \"holdId\" : \"" + TARGET + "\" }\n").getBytes(StandardCharsets.UTF_8);
        InputStream chunked = new InputStream() {
            private int position;
            @Override public int read() { return position < content.length ? content[position++] & 255 : -1; }
            @Override public int read(byte[] buffer, int offset, int length) {
                if (length == 0) return 0;
                int next = read();
                if (next < 0) return -1;
                buffer[offset] = (byte) next;
                return 1;
            }
        };
        assertEquals(new CreateOrderRequest(TARGET), OrderRequests.create(chunked));
    }

    @Test void preservesDecodedCodeAndUsesExplicitNullRemoval() throws IOException {
        assertEquals(new CouponMutation.SetCoupon(TARGET, " Save/20 "),
                OrderRequests.coupon(TARGET, body("{\"couponCode\":\" Save\\/20 \"}")));
        assertEquals(new CouponMutation.RemoveCoupon(TARGET),
                OrderRequests.coupon(TARGET, body("{\"couponCode\":null}")));
        assertEquals(new CouponMutation.SetCoupon(TARGET, "null"),
                OrderRequests.coupon(TARGET, body("{\"couponCode\":\"null\"}")));
    }

    @Test void acceptsValidSurrogatePairsAndEscapedQuotes() throws IOException {
        assertEquals(new CouponMutation.SetCoupon(TARGET, "\uD83C\uDFAB\"\\"),
                OrderRequests.coupon(TARGET, body("{\"couponCode\":\"\\uD83C\\uDFAB\\\"\\\\\"}")));
    }

    @Test void acceptsBodyAtLimitAndRejectsTheNextByte() throws IOException {
        String json = "{\"holdId\":\"" + TARGET + "\"}";
        String atLimit = json + " ".repeat(16384 - json.length());
        assertEquals(TARGET, OrderRequests.create(body(atLimit)).holdId());
        assertThrows(IllegalArgumentException.class, () -> OrderRequests.create(body(atLimit + " ")));
    }

    @Test void countsUtf8BytesRatherThanCharacters() {
        String json = "{\"couponCode\":\"" + "\uD83C\uDFAB".repeat(4096) + "\"}";
        assertThrows(IllegalArgumentException.class, () -> OrderRequests.coupon(TARGET, body(json)));
    }

    @Test void propagatesInputFailureAndRejectsMalformedUtf8() {
        assertThrows(IOException.class, () -> OrderRequests.create(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("fixture read failure"); }
        }));
        for (byte[] invalid : new byte[][] { {(byte) 0xC3, 0x28}, {(byte) 0xED, (byte) 0xA0, (byte) 0x80},
                {(byte) 0xC0, (byte) 0xAF}, {(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80} }) {
            var json = new ByteArrayOutputStream();
            json.writeBytes("{\"couponCode\":\"".getBytes(StandardCharsets.UTF_8));
            json.writeBytes(invalid);
            json.writeBytes("\"}".getBytes(StandardCharsets.UTF_8));
            assertThrows(IllegalArgumentException.class,
                    () -> OrderRequests.coupon(TARGET, new ByteArrayInputStream(json.toByteArray())));
        }
    }

    @ParameterizedTest @MethodSource("invalidCreate")
    void rejectsClosedSchemaViolations(String json) {
        assertThrows(IllegalArgumentException.class, () -> OrderRequests.create(body(json)));
    }

    static Stream<String> invalidCreate() {
        String pair = "\"holdId\":\"" + TARGET + "\"";
        return Stream.of("", "{}", "null", "[]", "{\"holdId\":null}", "{\"holdId\":1}",
                "{\"holdId\":true}", "{\"holdId\":[]}", "{\"holdId\":{}}",
                "{" + pair + ",\"price\":\"1\"}", "{" + pair + "," + pair + "}",
                "{" + pair + ",\"hold\\u0049d\":null}", "{" + pair + ",}",
                "{" + pair + "} {}", "\uFEFF{" + pair + "}", "\u2003{" + pair + "}",
                "{\"holdId\":\"1-1-1-1-1\"}", "{\"holdId\":\"00000000-0000-0000-0000-000000000000\"}",
                "{\"holdId\":\"" + TARGET + " \"}", "{\"holdId\":\"\\uD800\"}",
                "{\"holdId\":\"\\uDC00\"}", "{\"holdId\":\"\\uZZZZ\"}",
                "{\"holdId\":\"\\x20\"}", "{\"holdId\":\"abc\nxyz\"}", "{\"holdId\":\"abc");
    }

    @ParameterizedTest @MethodSource("invalidCoupon")
    void rejectsInvalidCouponBodies(String json) {
        assertThrows(IllegalArgumentException.class, () -> OrderRequests.coupon(TARGET, body(json)));
    }

    static Stream<String> invalidCoupon() {
        return Stream.of("{}", "{\"couponCode\":\"\"}", "{\"couponCode\":\"  \"}",
                "{\"couponCode\":\"\\tSAVE\"}", "{\"couponCode\":\"\\u007FSAVE\"}",
                "{\"couponCode\":null,\"coupon\\u0043ode\":null}", "{\"couponCode\":false}",
                "{\"couponCode\":nullx}", "{\"couponCode\":\"\\uD800a\"}",
                "{\"couponCode\":\"" + "A".repeat(81) + "\"}");
    }

    @Test void couponLengthUsesUtf16StorageUnits() throws IOException {
        assertEquals("\uD83C\uDFAB".repeat(40),
                ((CouponMutation.SetCoupon) OrderRequests.coupon(TARGET,
                        body("{\"couponCode\":\"" + "\uD83C\uDFAB".repeat(40) + "\"}"))).couponCode());
        assertThrows(IllegalArgumentException.class,
                () -> new CouponMutation.SetCoupon(TARGET, "\uD83C\uDFAB".repeat(40) + "x"));
    }

    @Test void hashesKnownVersionedOperationFixtures() {
        assertEquals("9e535b5b482fd41af988a41e8678cfb16989d689f367247887bfa2cb7cda5d0e",
                OrderRequests.fingerprint(ACTOR, new CreateOrderRequest(TARGET)));
        assertEquals("add5406f762c8391a883002d897c2f6dc39468604586d0845bef43e6622fc484",
                OrderRequests.fingerprint(ACTOR, new CouponMutation.SetCoupon(TARGET, "SAVE 20")));
        assertEquals("2c7cbfdb6045f5e634be2355daba6f9de759c5b771088e07e9db6a65afdb3b37",
                OrderRequests.fingerprint(ACTOR, new CouponMutation.RemoveCoupon(TARGET)));
    }

    @Test void hashUsesDecodedSemanticsAndBindsActorTargetAndExactCode() throws IOException {
        String setHash = "add5406f762c8391a883002d897c2f6dc39468604586d0845bef43e6622fc484";
        assertEquals(setHash, OrderRequests.fingerprint(ACTOR,
                OrderRequests.coupon(TARGET, body(" { \"coupon\\u0043ode\" : \"SAVE\\u002020\" } "))));
        assertNotEquals(setHash, OrderRequests.fingerprint(TARGET, new CouponMutation.SetCoupon(TARGET, "SAVE 20")));
        assertNotEquals(setHash, OrderRequests.fingerprint(ACTOR, new CouponMutation.SetCoupon(ACTOR, "SAVE 20")));
        assertNotEquals(setHash, OrderRequests.fingerprint(ACTOR, new CouponMutation.SetCoupon(TARGET, "save 20")));
        assertNotEquals(setHash, OrderRequests.fingerprint(ACTOR, new CouponMutation.SetCoupon(TARGET, "SAVE 20 ")));
        assertNotEquals(setHash, OrderRequests.fingerprint(ACTOR, new CouponMutation.RemoveCoupon(TARGET)));
    }

    @Test void rejectsMissingAndZeroIdentitiesAtAllEntryPoints() {
        UUID zero = new UUID(0, 0);
        for (UUID invalid : new UUID[] { null, zero }) {
            assertThrows(IllegalArgumentException.class, () -> new CreateOrderRequest(invalid));
            assertThrows(IllegalArgumentException.class, () -> new CouponMutation.SetCoupon(invalid, "SAVE"));
            assertThrows(IllegalArgumentException.class, () -> new CouponMutation.RemoveCoupon(invalid));
            assertThrows(IllegalArgumentException.class,
                    () -> OrderRequests.fingerprint(invalid, new CreateOrderRequest(TARGET)));
        }
        assertThrows(IllegalArgumentException.class, () -> OrderRequests.create(null));
        assertThrows(IllegalArgumentException.class, () -> OrderRequests.coupon(TARGET, null));
        assertThrows(NullPointerException.class, () -> OrderRequests.fingerprint(ACTOR, (CreateOrderRequest) null));
        assertThrows(NullPointerException.class, () -> OrderRequests.fingerprint(ACTOR, (CouponMutation) null));
    }

    private static InputStream body(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
