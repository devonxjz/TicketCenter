package vn.ticketscenter.fulfillment.refund.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import static org.junit.jupiter.api.Assertions.*;

class AdminRefundJsonTest {
    @Test void acceptsOptionalApproveNoteAndDocumentedRejectAlias() {
        assertNull(AdminRefundJson.decision("{\"decision\":\"APPROVE\"}").note());
        assertEquals("Đã kiểm tra",AdminRefundJson.decision("{\"decision\":\"APPROVE\",\"note\":\" Đã kiểm tra \"}").note());
        assertEquals("Không đủ điều kiện",AdminRefundJson.decision("{\"decision\":\"REJECT\",\"rejectionReason\":\"Không đủ điều kiện\"}").rejectReason());
    }
    @Test void dec03RejectNeedsNonBlankReason() {
        var error=assertThrows(RefundException.class,()->AdminRefundJson.decision("{\"decision\":\"REJECT\",\"rejectReason\":\"  \"}"));
        assertEquals(400,error.status());assertEquals("REJECTION_REASON_REQUIRED",error.code());
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","null","{\"decision\":42}","{\"decision\":\"APPROVE\",\"decision\":\"REJECT\"}","{\"decision\":\"APPROVE\",\"actorId\":\"forged\"}","{\"decision\":\"APPROVE\"}x","{\"decision\":\"APPROVE\",}","{\"decision\":\"REJECT\",\"rejectReason\":\"x\",\"rejectionReason\":\"x\"}","{\"decision\":\"APPROVE\",\"rejectReason\":\"x\"}","{\"decision\":\"REJECT\",\"rejectReason\":\"x\",\"note\":\"discarded\"}","{\"decision\":\"RETRY\"}"})
    void rejectsAmbiguousForgedOrMalformedPayload(String json){assertEquals(400,assertThrows(RefundException.class,()->AdminRefundJson.decision(json)).status());}
    @Test void lengthLimitsAndRetryCannotSelectAttemptOrActor(){
        assertThrows(RefundException.class,()->AdminRefundJson.decision("{\"decision\":\"APPROVE\",\"note\":\""+"x".repeat(501)+"\"}"));
        assertDoesNotThrow(()->AdminRefundJson.retry("{}"));
        assertThrows(RefundException.class,()->AdminRefundJson.retry("{\"attemptId\":\"x\"}"));
    }
    @Test void preservesUnicodeAndEscapedQuotes(){assertEquals("Khánh \"01\"\n02",AdminRefundJson.decision("{\"decision\":\"APPROVE\",\"note\":\"Kh\\u00e1nh \\\"01\\\"\\n02\"}").note());}
}
