package vn.ticketscenter.payment.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class VnpaySigner {
    private static final String VERSION = "2.1.0";
    private static final String COMMAND = "pay";
    private static final String ORDER_TYPE = "other";
    private static final String LOCALE = "vn";
    private static final String CURRENCY = "VND";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VIETNAM_ZONE);

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final Clock clock;

    public VnpaySigner(String tmnCode, String hashSecret, String payUrl, String returnUrl, Clock clock) {
        this.tmnCode = Objects.requireNonNull(tmnCode, "vnpay.tmn.code is required");
        this.hashSecret = Objects.requireNonNull(hashSecret, "vnpay.hash.secret is required");
        this.payUrl = Objects.requireNonNull(payUrl, "vnpay.pay.url is required");
        this.returnUrl = Objects.requireNonNull(returnUrl, "vnpay.return.url is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public String buildPaymentUrl(
            String txnRef,
            BigDecimal amount,
            String orderInfo,
            String clientIp,
            Optional<String> bankCode
    ) {
        Objects.requireNonNull(txnRef, "txnRef is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(orderInfo, "orderInfo is required");
        String ip = (clientIp == null || clientIp.isBlank()) ? "127.0.0.1" : clientIp.trim();

        // VNPAY yêu cầu nhân 100
        BigDecimal vnpAmount = amount.multiply(BigDecimal.valueOf(100));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", COMMAND);
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", vnpAmount.toBigInteger().toString());
        params.put("vnp_CurrCode", CURRENCY);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", ORDER_TYPE);
        params.put("vnp_Locale", LOCALE);
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ip);
        params.put("vnp_CreateDate", DATE_FORMATTER.format(clock.instant()));

        bankCode.ifPresent(code -> {
            if (!code.isBlank()) {
                params.put("vnp_BankCode", code.trim());
            }
        });

        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            String fieldValue = params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII);
                String encodedVal = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);

                hashData.append(encodedKey).append('=').append(encodedVal);
                query.append(encodedKey).append('=').append(encodedVal);

                if (i < fieldNames.size() - 1) {
                    hashData.append('&');
                    query.append('&');
                }
            }
        }

        String secureHash = hmacSha512(hashSecret, hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);

        return payUrl + "?" + query;
    }

    public boolean verifySignature(Map<String, String> queryParams, String secureHash) {
        if (secureHash == null || secureHash.isBlank() || queryParams == null || queryParams.isEmpty()) {
            return false;
        }
        List<String> fieldNames = new ArrayList<>();
        for (String key : queryParams.keySet()) {
            if (key != null && key.startsWith("vnp_")
                    && !key.equals("vnp_SecureHash")
                    && !key.equals("vnp_SecureHashType")) {
                String val = queryParams.get(key);
                if (val != null && !val.isEmpty()) {
                    fieldNames.add(key);
                }
            }
        }
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            String fieldValue = queryParams.get(fieldName);
            String encodedKey = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII);
            String encodedVal = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);
            hashData.append(encodedKey).append('=').append(encodedVal);
            if (i < fieldNames.size() - 1) {
                hashData.append('&');
            }
        }

        String calculated = hmacSha512(hashSecret, hashData.toString());
        return java.security.MessageDigest.isEqual(
                calculated.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                secureHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String hmacSha512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException failure) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA512: " + failure.getMessage(), failure);
        }
    }
}
