package vn.ticketscenter.payment.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class VnpaySignerTest {
    private static final String TMN_CODE = "TESTTMN01";
    private static final String HASH_SECRET = "TESTSECRETKEY1234567890ABCDEF";
    private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String RETURN_URL = "http://localhost:8080/ticketscenter/api/payments/vnpay/return";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Test
    void buildsDeterministicPaymentUrlWithCanonicalOrdering() {
        VnpaySigner signer = new VnpaySigner(TMN_CODE, HASH_SECRET, PAY_URL, RETURN_URL, FIXED_CLOCK);
        String url = signer.buildPaymentUrl(
                "PAY-0001",
                new BigDecimal("250000"),
                "Thanh toan don hang ORD-0001",
                "192.168.1.100",
                Optional.of("NCB")
        );

        assertTrue(url.startsWith(PAY_URL + "?"));
        URI uri = URI.create(url);
        String query = uri.getQuery();

        Map<String, String> queryParams = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            queryParams.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }

        // Kiểm tra đúng giá trị các tham số
        assertEquals("2.1.0", queryParams.get("vnp_Version"));
        assertEquals("pay", queryParams.get("vnp_Command"));
        assertEquals(TMN_CODE, queryParams.get("vnp_TmnCode"));
        // 250000 * 100 = 25000000
        assertEquals("25000000", queryParams.get("vnp_Amount"));
        assertEquals("VND", queryParams.get("vnp_CurrCode"));
        assertEquals("PAY-0001", queryParams.get("vnp_TxnRef"));
        assertEquals("Thanh toan don hang ORD-0001", queryParams.get("vnp_OrderInfo"));
        assertEquals("other", queryParams.get("vnp_OrderType"));
        assertEquals("vn", queryParams.get("vnp_Locale"));
        assertEquals(RETURN_URL, queryParams.get("vnp_ReturnUrl"));
        assertEquals("192.168.1.100", queryParams.get("vnp_IpAddr"));
        assertEquals("20261011190000", queryParams.get("vnp_CreateDate"));
        assertEquals("NCB", queryParams.get("vnp_BankCode"));
        assertNotNull(queryParams.get("vnp_SecureHash"));

        // Kiểm tra thứ tự tham số trước vnp_SecureHash tuân thủ bảng chữ cái
        List<String> keys = new ArrayList<>(queryParams.keySet());
        keys.remove("vnp_SecureHash");
        List<String> sortedKeys = new ArrayList<>(keys);
        Collections.sort(sortedKeys);
        assertEquals(sortedKeys, keys, "Query parameters must be alphabetically ordered");

        // Tái tạo lại hash và đối chiếu
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < sortedKeys.size(); i++) {
            String k = sortedKeys.get(i);
            String v = queryParams.get(k);
            raw.append(java.net.URLEncoder.encode(k, StandardCharsets.US_ASCII))
               .append('=')
               .append(java.net.URLEncoder.encode(v, StandardCharsets.US_ASCII));
            if (i < sortedKeys.size() - 1) {
                raw.append('&');
            }
        }
        String expectedHash = VnpaySigner.hmacSha512(HASH_SECRET, raw.toString());
        assertEquals(expectedHash, queryParams.get("vnp_SecureHash"));
    }

    @Test
    void omitsBankCodeWhenEmpty() {
        VnpaySigner signer = new VnpaySigner(TMN_CODE, HASH_SECRET, PAY_URL, RETURN_URL, FIXED_CLOCK);
        String url = signer.buildPaymentUrl(
                "PAY-0002",
                new BigDecimal("100000"),
                "Don hang ORD-0002",
                null,
                Optional.empty()
        );

        assertFalse(url.contains("vnp_BankCode"));
        assertTrue(url.contains("vnp_IpAddr=127.0.0.1"));
    }

    @Test
    void rejectsNullArguments() {
        VnpaySigner signer = new VnpaySigner(TMN_CODE, HASH_SECRET, PAY_URL, RETURN_URL, FIXED_CLOCK);
        assertThrows(NullPointerException.class, () -> signer.buildPaymentUrl(null, BigDecimal.TEN, "Info", "127.0.0.1", Optional.empty()));
        assertThrows(NullPointerException.class, () -> signer.buildPaymentUrl("TXN", null, "Info", "127.0.0.1", Optional.empty()));
        assertThrows(NullPointerException.class, () -> signer.buildPaymentUrl("TXN", BigDecimal.TEN, null, "127.0.0.1", Optional.empty()));
    }
}
