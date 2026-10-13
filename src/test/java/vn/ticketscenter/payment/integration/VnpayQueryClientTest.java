package vn.ticketscenter.payment.integration;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.payment.service.VnpaySigner;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VnpayQueryClientTest {
    private static final String SECRET = "secret";
    private final VnpayQueryClient client = new VnpayQueryClient(HttpClient.newHttpClient(), "http://localhost:1/x", "TMN", SECRET, Clock.systemUTC());

    private JSONObject response(String code, String status, String amount, boolean sign) {
        JSONObject r = new JSONObject().put("vnp_ResponseId", "r1").put("vnp_Command", "querydr").put("vnp_ResponseCode", code)
                .put("vnp_Message", "ok").put("vnp_TmnCode", "TMN").put("vnp_TxnRef", "T1").put("vnp_Amount", amount)
                .put("vnp_BankCode", "NCB").put("vnp_PayDate", "20260101000000").put("vnp_TransactionNo", "999")
                .put("vnp_TransactionType", "01").put("vnp_TransactionStatus", status).put("vnp_OrderInfo", "x")
                .put("vnp_PromotionCode", "").put("vnp_PromotionAmount", "");
        String data = String.join("|", "r1", "querydr", code, "ok", "TMN", "T1", amount, "NCB", "20260101000000", "999", "01", status, "x", "", "");
        return r.put("vnp_SecureHash", sign ? VnpaySigner.hmacSha512(SECRET, data) : "bad");
    }

    @Test void successMapsToCaptured() {
        var r = client.interpret(response("00", "00", "10000000", true), "T1", new BigDecimal("100000"));
        assertEquals(VnpayQueryClient.Outcome.CAPTURED, r.outcome());
        assertEquals("999", r.providerReference());
    }
    @Test void errorStatusMapsToFailed() {
        assertEquals(VnpayQueryClient.Outcome.FAILED, client.interpret(response("00", "02", "10000000", true), "T1", new BigDecimal("100000")).outcome());
    }
    @Test void amountMismatchIsUnknown() {
        assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.interpret(response("00", "00", "10000000", true), "T1", new BigDecimal("90000")).outcome());
    }
    @Test void badSignatureOrUnknownCodeIsUnknown() {
        assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.interpret(response("00", "00", "10000000", false), "T1", new BigDecimal("100000")).outcome());
        assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.interpret(response("91", "00", "10000000", true), "T1", new BigDecimal("100000")).outcome());
        assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.interpret(response("00", "01", "10000000", true), "T1", new BigDecimal("100000")).outcome());
    }
    @Test void abandonedPaymentFailsOnlyAfterThePaymentWindowCloses() {
        BigDecimal amount = new BigDecimal("100000");
        for (String status : new String[]{"01", "11"}) {
            assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.interpret(response("00", status, "10000000", true), "T1", amount, false).outcome());
            assertEquals(VnpayQueryClient.Outcome.FAILED, client.interpret(response("00", status, "10000000", true), "T1", amount, true).outcome());
        }
        assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.interpret(response("91", "", "", true), "T1", amount, false).outcome());
        assertEquals(VnpayQueryClient.Outcome.FAILED, client.interpret(response("91", "", "", true), "T1", amount, true).outcome());
    }
    @Test void closedWindowNeverFailsACapturedOrUnverifiedAnswer() {
        BigDecimal amount = new BigDecimal("100000");
        assertEquals(VnpayQueryClient.Outcome.CAPTURED, client.interpret(response("00", "00", "10000000", true), "T1", amount, true).outcome());
        assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.interpret(response("91", "", "", false), "T1", amount, true).outcome());
        assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.interpret(response("94", "", "", true), "T1", amount, true).outcome());
        assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.interpret(response("00", "05", "10000000", true), "T1", amount, true).outcome());
    }
    @Test void networkFailureIsUnknown() {
        assertEquals(VnpayQueryClient.Outcome.UNKNOWN, client.query("T1", BigDecimal.TEN, java.time.Instant.now()).outcome());
    }
}
