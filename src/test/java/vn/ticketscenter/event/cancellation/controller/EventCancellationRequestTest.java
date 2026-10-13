package vn.ticketscenter.event.cancellation.controller;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import static org.junit.jupiter.api.Assertions.*;

class EventCancellationRequestTest {
    @Test void reasonIsTrimmedAndEscapedVietnameseRemainsText() {
        assertEquals("Thời tiết nguy hiểm",EventCancellationWeb.parse("{\"reason\":\"  Thời tiết nguy hiểm  \"}").reason());
        assertEquals("Mưa lớn \"bão\" nguy hiểm",EventCancellationWeb.parse("{\"reason\":\"Mưa lớn \\\"bão\\\" nguy hiểm\"}").reason());
    }
    @Test void duplicateUnknownOrCallerAuthorityFieldsAreRejected() {
        for(String body:new String[]{"{}","{\"reason\":\"Thời tiết nguy hiểm\",\"reason\":\"Thời tiết nguy hiểm\"}","{\"reason\":\"Thời tiết nguy hiểm\",\"actorId\":\"admin\"}","{\"reason\":\"Thời tiết nguy hiểm\",\"now\":\"2030\"}"})
            assertThrows(RefundException.class,()->EventCancellationWeb.parse(body),body);
    }
    @Test void reasonMustBeBoundedStringAndJsonMustHaveNoTrailingContent() {
        for(String body:new String[]{"{\"reason\":null}","{\"reason\":1234567890}","{\"reason\":[]}","{\"reason\":{}}","{\"reason\":\"ngắn\"}","{\"reason\":\""+"x".repeat(501)+"\"}","{\"reason\":\"Thời tiết nguy hiểm\"}{}"})
            assertThrows(RefundException.class,()->EventCancellationWeb.parse(body),body);
    }
    @Test void uuidCannotUseJavaAbbreviatedForms() {
        assertThrows(RefundException.class,()->EventCancellationWeb.id("1-1-1-1-1"));
        assertThrows(RefundException.class,()->EventCancellationWeb.id(null));
    }
}
