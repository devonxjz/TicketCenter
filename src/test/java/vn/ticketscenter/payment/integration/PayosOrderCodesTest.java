package vn.ticketscenter.payment.integration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PayosOrderCodesTest {
    @Test void orderCodeRoundTripsToTheTxnRefPrefix() {
        for (int i = 0; i < 1000; i++) {
            String txnRef = "PAY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
            long code = PayosOrderCodes.fromTxnRef(txnRef);
            assertTrue(code > 0 && code <= 9_007_199_254_740_991L, "payOS orderCode must be a safe JS integer");
            assertTrue(txnRef.startsWith(PayosOrderCodes.txnRefPrefix(code)), txnRef);
        }
    }

    @Test void leadingZerosArePreserved() {
        long code = PayosOrderCodes.fromTxnRef("PAY-0000000000ABC0000000000000000000");
        assertEquals("PAY-0000000000ABC", PayosOrderCodes.txnRefPrefix(code));
    }

    @Test void foreignReferencesAreRejected() {
        for (String bad : new String[]{null, "PAY-12", "TXN-0123456789ABCDEF", "PAY-XYZ3456789ABCDEF", "PAY-0000000000000FFFF"})
            assertThrows(IllegalArgumentException.class, () -> PayosOrderCodes.fromTxnRef(bad), String.valueOf(bad));
        assertThrows(IllegalArgumentException.class, () -> PayosOrderCodes.txnRefPrefix(0));
        assertThrows(IllegalArgumentException.class, () -> PayosOrderCodes.txnRefPrefix(1L << 52));
    }
}
