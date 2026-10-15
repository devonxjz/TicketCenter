package vn.ticketscenter.payment.integration;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.payment.integration.PayosGateway.LinkStatus;
import vn.ticketscenter.payment.integration.PayosGateway.Outcome;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PayosGatewayTest {
    private static final BigDecimal AMOUNT = new BigDecimal("100000");

    private static PayosGateway.Result interpret(String status, long amount, long paid, boolean windowClosed) {
        return PayosGateway.interpret(Optional.of(new LinkStatus(1, "LINK-1", status, amount, paid)), AMOUNT, windowClosed);
    }

    @Test void paidWithExactAmountIsCapturedUnderThePaymentLinkId() {
        assertEquals(new PayosGateway.Result(Outcome.CAPTURED, "LINK-1"), interpret("PAID", 100000, 100000, false));
    }

    @Test void paidWithAnotherAmountIsNotTrusted() {
        assertEquals(Outcome.UNKNOWN, interpret("PAID", 100000, 90000, false).outcome());
        assertEquals(Outcome.UNKNOWN, interpret("PAID", 200000, 200000, false).outcome());
    }

    @Test void closedLinksAreFinalFailures() {
        for (String status : new String[]{"CANCELLED", "EXPIRED", "FAILED"})
            assertEquals(new PayosGateway.Result(Outcome.FAILED, null), interpret(status, 100000, 0, false), status);
    }

    @Test void openLinksStayUnknown() {
        for (String status : new String[]{"PENDING", "PROCESSING", "UNDERPAID"})
            assertEquals(Outcome.UNKNOWN, interpret(status, 100000, 0, true).outcome(), status);
    }

    @Test void linkPayosNeverSawFailsOnlyAfterThePaymentWindow() {
        assertEquals(Outcome.UNKNOWN, PayosGateway.interpret(Optional.empty(), AMOUNT, false).outcome());
        assertEquals(Outcome.FAILED, PayosGateway.interpret(Optional.empty(), AMOUNT, true).outcome());
    }
}
