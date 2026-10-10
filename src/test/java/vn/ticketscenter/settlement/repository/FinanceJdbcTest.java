package vn.ticketscenter.settlement.repository;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class FinanceJdbcTest {
    @Test void stableSqlErrorsBecomeSafePublicCodes() {
        var error=FinanceJdbc.error(new SQLException("PAYOUT_EXCEEDS_BALANCE", "S0001",52709));
        assertEquals("PAYOUT_EXCEEDS_BALANCE",error.code()); assertEquals(409,error.status());
        assertEquals("DATABASE_UNAVAILABLE",FinanceJdbc.error(new SQLException("password=secret", "08001",0)).code());
        assertEquals("FINANCE_CONFLICT",FinanceJdbc.error(new SQLException("bank secret", "S0001",52709)).code());
        assertEquals(401,FinanceJdbc.error(new SQLException("UNAUTHENTICATED","S0001",52701)).status());
    }
    @Test void columnNamesUseExplicitCamelCase() {
        assertEquals("netPayable",FinanceJdbc.camel("net_payable"));
        assertEquals("isReplay",FinanceJdbc.camel("is_replay"));
    }
}
