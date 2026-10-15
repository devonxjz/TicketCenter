package vn.ticketscenter.payment.integration;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import vn.payos.PayOS;
import vn.payos.crypto.CryptoProviderImpl;
import vn.ticketscenter.config.PayosConfig;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the real payOS SDK signature code; no network call is made. */
class PayosClientTest {
    private static final String CHECKSUM_KEY = "test-checksum-key";
    private final PayosClient client = new PayosClient(new PayOS("client-id", "api-key", CHECKSUM_KEY),
            "http://localhost/return", "http://localhost/cancel");

    private static Map<String, Object> data() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 123L);
        data.put("amount", 3000L);
        data.put("description", "TC123");
        data.put("accountNumber", "12345678");
        data.put("reference", "FT123");
        data.put("transactionDateTime", "2026-10-14 10:00:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "LINK-1");
        data.put("code", "00");
        data.put("desc", "success");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }

    private static String webhook(Map<String, Object> data, String signature) {
        return new JSONObject().put("code", "00").put("desc", "success").put("success", true)
                .put("data", data).put("signature", signature).toString();
    }

    @Test
    void verifiesAWebhookSignedWithTheChecksumKey() {
        String signature = new CryptoProviderImpl().createSignatureFromObj(data(), CHECKSUM_KEY);

        PayosGateway.WebhookEvent event = client.verifyWebhook(webhook(data(), signature));
        assertEquals(new PayosGateway.WebhookEvent(123L, 3000L, "00", "FT123", "LINK-1"), event);
    }

    @Test
    void rejectsTamperedOrUnsignedWebhooks() {
        String signature = new CryptoProviderImpl().createSignatureFromObj(data(), CHECKSUM_KEY);
        Map<String, Object> tampered = data();
        tampered.put("amount", 1L);

        assertThrows(PayosGateway.InvalidWebhook.class, () -> client.verifyWebhook(webhook(tampered, signature)));
        assertThrows(PayosGateway.InvalidWebhook.class, () -> client.verifyWebhook(webhook(data(), "")));
        assertThrows(PayosGateway.InvalidWebhook.class, () -> client.verifyWebhook("not json"));
    }

    @Test
    void closeStopsTheOwnedHttpClientSoNoThreadOutlivesTheWebApp() {
        PayosConfig config = new PayosConfig("client-id", "api-key", CHECKSUM_KEY,
                "http://localhost/return", "http://localhost/cancel", "NONE");
        var http = config.httpClient();
        new PayosClient(config, http).close();
        assertTrue(http.dispatcher().executorService().isShutdown());

        var supplied = config.httpClient();
        new PayosClient(config.client(supplied), config.returnUrl(), config.cancelUrl()).close();
        assertFalse(supplied.dispatcher().executorService().isShutdown(), "a supplied PayOS instance is left alone");
    }
}
