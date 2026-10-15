package vn.ticketscenter.fulfillment.refund.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.refund.service.RefundException;

class RefundJsonContractTest {
    private final UUID order = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();

    @Test
    void requestKeepsDecodedStringLimitAndEscapes() {
        String escaped = "\\u0061".repeat(500);
        assertEquals("a".repeat(500), RefundRequestJson.parse(request(escaped)).reason());
        assertInvalid(() -> RefundRequestJson.parse(request(escaped + "a")));

        String reason = "Lý do: \\\"x\\\" \\/ \\\\ \\b \\f \\n \\r \\t end";
        assertEquals("Lý do: \"x\" / \\ \b \f \n \r \t end", RefundRequestJson.parse(request(reason)).reason());
    }

    @Test
    void adminKeepsNullValuesAliasesAndSeparateLengthLimit() {
        assertNull(AdminRefundJson.decision("{\"decision\":\"APPROVE\",\"note\":null}").note());
        String reason = "\\u0061".repeat(1000);
        assertEquals("a".repeat(1000), AdminRefundJson.decision(
                "{\"decision\":\"REJECT\",\"rejectionReason\":\"" + reason + "\"}").rejectReason());
        assertInvalid(() -> AdminRefundJson.decision(
                "{\"decision\":\"REJECT\",\"rejectReason\":\"" + reason + "a\"}"));
        assertInvalid(() -> AdminRefundJson.decision(
                "{\"decision\":\"APPROVE\",\"note\":null,\"note\":null}"));
    }

    @Test
    void bothEndpointsRejectInvalidEscapesControlCharactersAndNonJsonWhitespace() {
        for (String value : List.of("\\x", "\\u123", "\\u00GG", "\\u１２３４", "raw\nline", "raw\ttext")) {
            assertInvalid(() -> RefundRequestJson.parse(request(value)));
            assertInvalid(() -> AdminRefundJson.decision("{\"decision\":\"APPROVE\",\"note\":\"" + value + "\"}"));
        }
        assertInvalid(() -> RefundRequestJson.parse("\u00a0" + request("Valid reason")));
        assertInvalid(() -> AdminRefundJson.retry("{\u00a0}"));
        assertDoesNotThrow(() -> AdminRefundJson.retry(" \r\n\t{ } \r\n\t"));
    }

    private String request(String reason) {
        return "{\"orderId\":\"" + order + "\",\"ticketIds\":[\"" + ticket + "\"],\"reason\":\"" + reason + "\"}";
    }

    private static void assertInvalid(Runnable action) {
        assertEquals(400, assertThrows(RefundException.class, action::run).status());
    }
}
