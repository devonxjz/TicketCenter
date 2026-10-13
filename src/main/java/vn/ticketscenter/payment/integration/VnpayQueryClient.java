package vn.ticketscenter.payment.integration;

import org.json.JSONObject;
import vn.ticketscenter.payment.service.VnpaySigner;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** VNPAY {@code querydr} client: asks the gateway what really happened to a payment. */
public final class VnpayQueryClient {
    public enum Outcome { CAPTURED, FAILED, UNKNOWN }

    public record Result(Outcome outcome, String providerReference) {
        public static Result unknown() { return new Result(Outcome.UNKNOWN, null); }
    }

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VIETNAM_ZONE);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** VNPAY keeps a payment link open for 15 minutes; past this margin an unpaid transaction can no longer succeed. */
    static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

    private final HttpClient http;
    private final String apiUrl;
    private final String tmnCode;
    private final String hashSecret;
    private final Clock clock;

    public VnpayQueryClient(HttpClient http, String apiUrl, String tmnCode, String hashSecret, Clock clock) {
        this.http = Objects.requireNonNull(http);
        this.apiUrl = Objects.requireNonNull(apiUrl);
        this.tmnCode = Objects.requireNonNull(tmnCode);
        this.hashSecret = Objects.requireNonNull(hashSecret);
        this.clock = Objects.requireNonNull(clock);
    }

    public static VnpayQueryClient create(String apiUrl, String tmnCode, String hashSecret) {
        return new VnpayQueryClient(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
                apiUrl, tmnCode, hashSecret, Clock.systemUTC());
    }

    /** Never throws: any transport, signature or amount problem maps to {@link Outcome#UNKNOWN}. */
    public Result query(String txnRef, BigDecimal amount, Instant paymentCreatedAt) {
        try {
            String requestId = UUID.randomUUID().toString().replace("-", "");
            String transactionDate = DATE_FORMATTER.format(paymentCreatedAt);
            String createDate = DATE_FORMATTER.format(clock.instant());
            String orderInfo = "Query " + txnRef;
            String ip = "127.0.0.1";
            String hashData = String.join("|", requestId, "2.1.0", "querydr", tmnCode, txnRef,
                    transactionDate, createDate, ip, orderInfo);
            JSONObject body = new JSONObject()
                    .put("vnp_RequestId", requestId).put("vnp_Version", "2.1.0").put("vnp_Command", "querydr")
                    .put("vnp_TmnCode", tmnCode).put("vnp_TxnRef", txnRef).put("vnp_OrderInfo", orderInfo)
                    .put("vnp_TransactionDate", transactionDate).put("vnp_CreateDate", createDate)
                    .put("vnp_IpAddr", ip).put("vnp_SecureHash", VnpaySigner.hmacSha512(hashSecret, hashData));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl)).timeout(TIMEOUT)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return Result.unknown();
            return interpret(new JSONObject(response.body()), txnRef, amount,
                    clock.instant().isAfter(paymentCreatedAt.plus(PAYMENT_WINDOW)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.unknown();
        } catch (IOException | RuntimeException failure) {
            return Result.unknown();
        }
    }

    Result interpret(JSONObject r, String txnRef, BigDecimal amount) {
        return interpret(r, txnRef, amount, false);
    }

    /**
     * @param windowClosed the payment link has expired, so "never reached the gateway" (91), "not completed" (01)
     *                     and "abandoned by the customer" (11) are final failures rather than still-pending
     */
    Result interpret(JSONObject r, String txnRef, BigDecimal amount, boolean windowClosed) {
        String data = String.join("|", s(r, "vnp_ResponseId"), s(r, "vnp_Command"), s(r, "vnp_ResponseCode"),
                s(r, "vnp_Message"), s(r, "vnp_TmnCode"), s(r, "vnp_TxnRef"), s(r, "vnp_Amount"), s(r, "vnp_BankCode"),
                s(r, "vnp_PayDate"), s(r, "vnp_TransactionNo"), s(r, "vnp_TransactionType"), s(r, "vnp_TransactionStatus"),
                s(r, "vnp_OrderInfo"), s(r, "vnp_PromotionCode"), s(r, "vnp_PromotionAmount"));
        String expected = VnpaySigner.hmacSha512(hashSecret, data);
        if (!MessageDigest.isEqual(expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                s(r, "vnp_SecureHash").toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) return Result.unknown();
        if (!txnRef.equals(s(r, "vnp_TxnRef"))) return Result.unknown();
        if (windowClosed && "91".equals(s(r, "vnp_ResponseCode"))) return new Result(Outcome.FAILED, null);
        if (!"00".equals(s(r, "vnp_ResponseCode"))) return Result.unknown();
        String status = s(r, "vnp_TransactionStatus");
        if ("00".equals(status)) {
            try {
                BigDecimal paid = new BigDecimal(s(r, "vnp_Amount")).movePointLeft(2);
                if (paid.compareTo(amount) != 0) return Result.unknown();
            } catch (NumberFormatException bad) { return Result.unknown(); }
            String reference = s(r, "vnp_TransactionNo");
            return reference.isEmpty() ? Result.unknown() : new Result(Outcome.CAPTURED, reference);
        }
        if ("02".equals(status) || (windowClosed && ("01".equals(status) || "11".equals(status))))
            return new Result(Outcome.FAILED, s(r, "vnp_TransactionNo"));
        return Result.unknown();
    }

    private static String s(JSONObject o, String key) {
        Object v = o.opt(key);
        return v == null || JSONObject.NULL.equals(v) ? "" : String.valueOf(v);
    }
}
