package vn.ticketscenter.payment.dto;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.payment.service.PaymentException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InitiatePaymentRequestTest {
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void acceptsValidBankCode() {
        var req = InitiatePaymentRequest.of("NCB");
        assertTrue(req.bankCode().isPresent());
        assertEquals("NCB", req.bankCode().get());

        var empty = InitiatePaymentRequest.of(null);
        assertTrue(empty.bankCode().isEmpty());

        var blank = InitiatePaymentRequest.of("   ");
        assertTrue(blank.bankCode().isEmpty());
    }

    @Test
    void rejectsInvalidBankCode() {
        assertThrows(IllegalArgumentException.class, () -> InitiatePaymentRequest.of("NCB!"));
        assertThrows(IllegalArgumentException.class, () -> InitiatePaymentRequest.of("NCB 123"));
        assertThrows(IllegalArgumentException.class, () -> InitiatePaymentRequest.of("a".repeat(21)));
    }

    @Test
    void fingerprintIsDeterministic() {
        var req1 = InitiatePaymentRequest.of("NCB");
        var req2 = InitiatePaymentRequest.of("NCB");
        var reqDiff = InitiatePaymentRequest.of("VNPAYQR");

        assertEquals(req1.fingerprint(ACTOR, ORDER), req2.fingerprint(ACTOR, ORDER));
        assertNotEquals(req1.fingerprint(ACTOR, ORDER), reqDiff.fingerprint(ACTOR, ORDER));
        assertNotEquals(req1.fingerprint(ACTOR, ORDER), req1.fingerprint(UUID.randomUUID(), ORDER));
    }

    @Test
    void parsesJsonPayload() throws IOException {
        String json = "{\"bankCode\":\"NCB\"}";
        var req = PaymentRequests.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.of("NCB"), req.bankCode());

        var emptyJson = PaymentRequests.parse(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(emptyJson.bankCode().isEmpty());

        var nullStream = PaymentRequests.parse(null);
        assertTrue(nullStream.bankCode().isEmpty());

        assertThrows(PaymentException.class, () ->
                PaymentRequests.parse(new ByteArrayInputStream("{not json".getBytes(StandardCharsets.UTF_8))));
    }
}
