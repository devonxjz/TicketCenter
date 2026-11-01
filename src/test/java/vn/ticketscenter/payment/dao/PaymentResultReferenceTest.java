package vn.ticketscenter.payment.dao;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PaymentResultReferenceTest {
    @Test
    void cancelledPaymentsDoNotShareAPlaceholderProviderReference() {
        for (String reference : new String[]{"0", null, "", "15703642"}) {
            var payload = new AtomicReference<String>();
            var statement = (CallableStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{CallableStatement.class}, (proxy, method, args) -> {
                        if (method.getName().matches("setString|setObject") && (Integer) args[0] == 4) payload.set((String) args[1]);
                        return switch (method.getName()) {
                            case "execute", "getMoreResults" -> false;
                            case "getUpdateCount" -> -1;
                            default -> null;
                        };
                    });
            var connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) ->
                            method.getName().equals("prepareCall") ? statement : null);
            var source = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{DataSource.class}, (proxy, method, args) ->
                            method.getName().equals("getConnection") ? connection : null);
            new PaymentRepository(source).applyPaymentResult(UUID.randomUUID(), UUID.randomUUID(), "FAILED", reference);
            var result = new JSONObject(payload.get());
            assertEquals("FAILED", result.getString("status"));
            if (reference == null || reference.isBlank() || reference.equals("0")) assertTrue(result.isNull("providerReference"));
            else assertEquals(reference, result.getString("providerReference"));
        }
    }
}
