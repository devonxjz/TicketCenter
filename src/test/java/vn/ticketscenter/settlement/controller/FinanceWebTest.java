package vn.ticketscenter.settlement.controller;

import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.util.Map;
import vn.ticketscenter.settlement.service.FinanceException;
import static org.junit.jupiter.api.Assertions.*;

class FinanceWebTest {
    @Test void rejectsShortUuidAndMalformedDatesWithoutEchoingInput() {
        assertThrows(FinanceException.class,()->FinanceWeb.id("1-1-1-1-1"));
        assertThrows(FinanceException.class,()->FinanceWeb.instant("2026-10-10T00:00:00+07:00"));
        assertThrows(FinanceException.class,()->FinanceWeb.instant("2026-10-10T00:00:00.123456Z"));
    }
    @Test void commissionMoneyCannotBeFractionalOrOverflow() {
        var input=Map.of("ratePercent","5.1250","fixedFee","2","effectiveFrom","2020-01-01T00:00:00Z","effectiveTo","2099-01-01T00:00:00Z");
        assertEquals(2,FinanceWeb.commission(input).fixedFee());
        var bad=new java.util.HashMap<>(input); bad.put("fixedFee","2.1"); assertThrows(FinanceException.class,()->FinanceWeb.commission(bad));
        bad.put("fixedFee","9223372036854775808"); assertThrows(FinanceException.class,()->FinanceWeb.commission(bad));
    }
    @Test void bodyLimitAppliesEvenWhenContentLengthIsUnknown() {
        assertThrows(FinanceException.class,()->FinanceWeb.body(new StringReader(" ".repeat(65537))));
    }
    @Test void duplicateAndUnknownFieldsAreRejected() throws Exception {
        assertThrows(FinanceException.class,()->FinanceWeb.body(new StringReader("{\"amount\":\"1\",\"amount\":\"2\"}")));
        var request=Map.of("amount","100","bankAccountNumber","123","bankName","Bank","accountHolderName","Holder","sql","secret");
        assertThrows(FinanceException.class,()->FinanceWeb.payout(request));
    }
}
