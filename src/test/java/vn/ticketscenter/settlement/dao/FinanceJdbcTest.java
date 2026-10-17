package vn.ticketscenter.settlement.dao;

import org.junit.jupiter.api.Test;

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
    @Test void sqlServerUuidTextIsCanonicalWithoutChangingProviderReferences() throws Exception {
        String upper="A6948A7A-5C46-4307-BD17-635EBC1F8D68";
        var metadata=(java.sql.ResultSetMetaData)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.ResultSetMetaData.class},(p,m,args)->switch(m.getName()) {
            case "getColumnCount"->2;
            case "getColumnType"->java.sql.Types.CHAR;
            case "getColumnLabel"->((int)args[0])==1?"payout_id":"provider_reference";
            default->throw new UnsupportedOperationException(m.getName());
        });
        var rows=(java.sql.ResultSet)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.ResultSet.class},(p,m,args)->switch(m.getName()) {
            case "getMetaData"->metadata;
            case "getObject"->upper;
            default->throw new UnsupportedOperationException(m.getName());
        });
        var projected=FinanceJdbc.row(rows);
        assertEquals(upper.toLowerCase(java.util.Locale.ROOT),projected.get("payoutId").toString());
        assertEquals(upper,projected.get("providerReference"));
    }
}
