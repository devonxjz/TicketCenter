package vn.ticketscenter.payment.integration;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.refund.worker.RefundTransferRequest;
import vn.ticketscenter.payment.service.VnpaySigner;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class VnpaySandboxRefundGatewayTest {
    private final UUID refund=UUID.randomUUID(), attempt=UUID.randomUUID();
    private final String secret="sandbox-secret";
    private VnpaySandboxRefundGateway.Context context(String environment) {
        return new VnpaySandboxRefundGateway.Context(refund,attempt,new BigDecimal("100"),new BigDecimal("100"),
                "VND","CAPTURED","T123","123456","TMN12345",environment,"20261013120000",Instant.parse("2026-10-13T06:00:00Z"));
    }
    private JSONObject response(JSONObject request, String status, String type, String merchant, String amount, String info) {
        JSONObject r=new JSONObject().put("vnp_ResponseId","R123").put("vnp_Command","refund")
                .put("vnp_ResponseCode","00").put("vnp_Message","OK").put("vnp_TmnCode",merchant)
                .put("vnp_TxnRef","T123").put("vnp_Amount",amount).put("vnp_BankCode","NCB")
                .put("vnp_PayDate","20261013130000").put("vnp_TransactionNo","7890")
                .put("vnp_TransactionType",type).put("vnp_TransactionStatus",status).put("vnp_OrderInfo",info);
        String data=String.join("|","R123","refund","00","OK",merchant,"T123",amount,"NCB","20261013130000","7890",type,status,info);
        return r.put("vnp_SecureHash",VnpaySigner.hmacSha512(secret,data));
    }
    private VnpaySandboxRefundGateway gateway(VnpaySandboxRefundGateway.Transport transport) {
        return new VnpaySandboxRefundGateway(id->Optional.of(context("SANDBOX")),"TMN12345",secret,"127.0.0.1",transport);
    }
    @Test void signedMatchedCompletedRefundSucceeds() {
        var gateway=gateway(q->response(q,"00","02","TMN12345","10000",q.getString("vnp_OrderInfo")).toString());
        var result=gateway.executeRefund(new RefundTransferRequest(refund,attempt,new BigDecimal("100")));
        assertEquals(RefundGateway.Status.SUCCEEDED,result.status()); assertEquals("7890",result.providerReference());
    }
    @Test void pendingRefundAndOriginalPaymentAreNeverSuccess() {
        for(String status:new String[]{"05","06","01"}) {
            var gateway=gateway(q->response(q,status,"02","TMN12345","10000",q.getString("vnp_OrderInfo")).toString());
            assertEquals(RefundGateway.Status.UNKNOWN,gateway.executeRefund(new RefundTransferRequest(refund,attempt,new BigDecimal("100"))).status());
        }
        var g=gateway(q->response(q,"00","01","TMN12345","10000",q.getString("vnp_OrderInfo")).toString());
        assertEquals(RefundGateway.Status.UNKNOWN,g.executeRefund(new RefundTransferRequest(refund,attempt,new BigDecimal("100"))).status());
        assertEquals(RefundGateway.Status.UNKNOWN,g.queryRefundStatus("123456",attempt).status());
    }
    @Test void mismatchedSignedResponsesAndInvalidSignatureFailClosed() {
        var g=gateway(q->"{}"); var c=context("SANDBOX"); var q=g.body(c); String info=q.getString("vnp_OrderInfo");
        for(JSONObject r:new JSONObject[]{response(q,"00","02","OTHER123","10000",info),
                response(q,"00","02","TMN12345","9999",info),response(q,"00","02","TMN12345","10000","other attempt"),
                response(q,"00","02","TMN12345","10000",info).put("vnp_SecureHash","invalid")})
            assertEquals(RefundGateway.Status.UNKNOWN,g.interpret(r,c).status());
    }
    @Test void missingOrLegacyProvenanceNeverCallsProvider() {
        AtomicInteger calls=new AtomicInteger();
        for(String environment:new String[]{"DEMO","PRODUCTION"}) {
            var g=new VnpaySandboxRefundGateway(id->Optional.of(context(environment)),"TMN12345",secret,"127.0.0.1",q->{calls.incrementAndGet();return "{}";});
            assertEquals(RefundGateway.Status.UNKNOWN,g.executeRefund(new RefundTransferRequest(refund,attempt,new BigDecimal("100"))).status());
        }
        var g=new VnpaySandboxRefundGateway(id->Optional.empty(),"TMN12345",secret,"127.0.0.1",q->{calls.incrementAndGet();return "{}";});
        assertEquals(RefundGateway.Status.UNKNOWN,g.executeRefund(new RefundTransferRequest(refund,attempt,new BigDecimal("100"))).status());
        assertEquals(0,calls.get());
    }
    @Test void immutableRequestIdAndCreationDateAndChecksum() {
        var g=gateway(q->"{}"); JSONObject first=g.body(context("SANDBOX")),second=g.body(context("SANDBOX"));
        assertEquals(first.toMap(),second.toMap()); assertEquals(attempt.toString().replace("-",""),first.getString("vnp_RequestId"));
        assertEquals("20261013130000",first.getString("vnp_CreateDate"));
        String data=String.join("|",first.getString("vnp_RequestId"),"2.1.0","refund","TMN12345","02","T123","10000","123456","20261013120000","TicketCenter","20261013130000","127.0.0.1",first.getString("vnp_OrderInfo"));
        assertEquals(VnpaySigner.hmacSha512(secret,data),first.getString("vnp_SecureHash"));
    }
    @Test void lostResponseStaysUnknownAndNeverQueriesOriginalPayment() {
        var g=gateway(q->{throw new java.io.IOException("response lost");});
        assertEquals(RefundGateway.Status.UNKNOWN,g.executeRefund(new RefundTransferRequest(refund,attempt,new BigDecimal("100"))).status());
        assertEquals(RefundGateway.Status.UNKNOWN,g.queryRefundStatus(null,attempt).status());
    }
}
