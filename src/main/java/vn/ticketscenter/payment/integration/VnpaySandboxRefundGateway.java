package vn.ticketscenter.payment.integration;

import org.json.JSONObject;
import vn.ticketscenter.fulfillment.refund.worker.RefundTransferRequest;
import vn.ticketscenter.fulfillment.refund.worker.RefundTransferResult;
import vn.ticketscenter.payment.service.VnpaySigner;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Only refunds genuine sandbox payments; uncertain attempts require provider evidence/manual reconciliation. */
public final class VnpaySandboxRefundGateway implements RefundGateway {
    public static final String API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    public record Context(UUID refundId, UUID attemptId, BigDecimal refundAmount, BigDecimal paymentAmount,
                          String currency, String paymentStatus, String txnRef, String paymentReference,
                          String merchant, String environment, String transactionDate, Instant attemptCreatedAt) { }
    @FunctionalInterface public interface ContextLoader { Optional<Context> load(UUID attemptId); }
    @FunctionalInterface interface Transport { String post(JSONObject request) throws Exception; }
    private static final String[] RESPONSE_FIELDS = {"vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
            "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
            "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ContextLoader contexts;
    private final String merchant, secret, ip;
    private final Transport transport;

    public VnpaySandboxRefundGateway(ContextLoader contexts, String merchant, String secret, String ip) {
        this(contexts, merchant, secret, ip, request -> {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(request.toString(), StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IllegalStateException("VNPAY response unavailable");
            return response.body();
        });
    }
    VnpaySandboxRefundGateway(ContextLoader contexts, String merchant, String secret, String ip, Transport transport) {
        this.contexts = Objects.requireNonNull(contexts); this.merchant = Objects.requireNonNull(merchant);
        this.secret = Objects.requireNonNull(secret); this.ip = Objects.requireNonNull(ip);
        this.transport = Objects.requireNonNull(transport);
        if (!merchant.matches("[A-Za-z0-9]{8}") || secret.isBlank() || !ip.matches("[0-9a-fA-F:.]{7,45}"))
            throw new IllegalArgumentException("Sandbox refund merchant, secret and server IP are required");
    }
    @Override public RefundTransferResult executeRefund(RefundTransferRequest request) {
        try {
            Context c = contexts.load(request.attemptId()).orElse(null);
            if (!eligible(c, request)) return unknown();
            return interpret(new JSONObject(transport.post(body(c))), c);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return unknown();
        } catch (Exception uncertain) { return unknown(); }
    }
    private boolean eligible(Context c, RefundTransferRequest request) {
        return c != null && c.refundId().equals(request.refundId()) && c.attemptId().equals(request.attemptId())
                && c.refundAmount().compareTo(request.amount()) == 0 && c.refundAmount().signum() > 0
                && c.refundAmount().stripTrailingZeros().scale() <= 0 && c.refundAmount().compareTo(c.paymentAmount()) <= 0
                && c.refundAmount().movePointRight(2).toBigIntegerExact().toString().length() <= 12
                && "VND".equals(c.currency().trim()) && "CAPTURED".equals(c.paymentStatus())
                && merchant.equals(c.merchant()) && "SANDBOX".equals(c.environment())
                && c.txnRef() != null && c.txnRef().matches("[A-Za-z0-9-]{1,100}")
                && c.paymentReference() != null && c.paymentReference().matches("[0-9]{1,15}")
                && c.transactionDate() != null && c.transactionDate().matches("[0-9]{14}")
                && c.attemptCreatedAt() != null;
    }
    JSONObject body(Context c) {
        // Database-created attempt ID and timestamp remain identical on every invocation.
        String id = c.attemptId().toString().replace("-", ""), type = type(c), amount = amount(c);
        String date = DATE.format(c.attemptCreatedAt()), info = info(c);
        String data = String.join("|", id, "2.1.0", "refund", merchant, type, c.txnRef(), amount,
                c.paymentReference(), c.transactionDate(), "TicketCenter", date, ip, info);
        return new JSONObject().put("vnp_RequestId", id).put("vnp_Version", "2.1.0").put("vnp_Command", "refund")
                .put("vnp_TmnCode", merchant).put("vnp_TransactionType", type).put("vnp_TxnRef", c.txnRef())
                .put("vnp_Amount", amount).put("vnp_TransactionNo", c.paymentReference())
                .put("vnp_TransactionDate", c.transactionDate()).put("vnp_CreateBy", "TicketCenter")
                .put("vnp_CreateDate", date).put("vnp_IpAddr", ip).put("vnp_OrderInfo", info)
                .put("vnp_SecureHash", VnpaySigner.hmacSha512(secret, data));
    }
    RefundTransferResult interpret(JSONObject r, Context c) {
        return interpret(r,c,"refund");
    }
    private RefundTransferResult interpret(JSONObject r, Context c, String command) {
        var values = new java.util.ArrayList<String>();
        for (String field:RESPONSE_FIELDS) values.add(r.optString(field,""));
        if (command.equals("querydr")) {
            values.add(r.optString("vnp_PromotionCode",""));
            values.add(r.optString("vnp_PromotionAmount",""));
        }
        String signature = VnpaySigner.hmacSha512(secret, String.join("|", values));
        if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII),
                r.optString("vnp_SecureHash", "").toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) return unknown();
        if (!command.equals(r.optString("vnp_Command")) || !merchant.equals(r.optString("vnp_TmnCode"))
                || !c.txnRef().equals(r.optString("vnp_TxnRef")) || !amount(c).equals(r.optString("vnp_Amount"))
                || !type(c).equals(r.optString("vnp_TransactionType")) || !info(c).equals(r.optString("vnp_OrderInfo"))) return unknown();
        String reference = r.optString("vnp_TransactionNo", ""), code = r.optString("vnp_ResponseCode", "");
        if (!reference.matches("[0-9]{1,15}") || !r.optString("vnp_ResponseId", "").matches("[A-Za-z0-9]{1,32}")) return unknown();
        if ("00".equals(code) && "00".equals(r.optString("vnp_TransactionStatus")))
            return new RefundTransferResult(Status.SUCCEEDED, reference);
        if ("00".equals(code) && "09".equals(r.optString("vnp_TransactionStatus")))
            return new RefundTransferResult(Status.FAILED, reference);
        return unknown();
    }
    private static String type(Context c) { return c.refundAmount().compareTo(c.paymentAmount()) == 0 ? "02" : "03"; }
    private static String amount(Context c) { return c.refundAmount().movePointRight(2).toBigIntegerExact().toString(); }
    private static String info(Context c) { return "Refund " + c.attemptId().toString().replace("-", ""); }
    private static RefundTransferResult unknown() { return new RefundTransferResult(Status.UNKNOWN, null); }
    @Override public Result submit(UUID id, BigDecimal amount) { return new Result(Status.UNKNOWN, null); }
    @Override public Result query(String reference) { return new Result(Status.UNKNOWN, reference); }
    @Override public RefundTransferResult queryRefundStatus(String reference, UUID attemptId) {
        try {
            Context c=contexts.load(attemptId).orElse(null);
            if(c==null || !c.attemptId().equals(attemptId)
                    || !eligible(c,new RefundTransferRequest(c.refundId(),attemptId,c.refundAmount()))) return unknown();
            String requestId=UUID.randomUUID().toString().replace("-",""),date=DATE.format(Instant.now()),info=info(c);
            String data=String.join("|",requestId,"2.1.0","querydr",merchant,c.txnRef(),c.transactionDate(),date,ip,info);
            JSONObject query=new JSONObject().put("vnp_RequestId",requestId).put("vnp_Version","2.1.0")
                    .put("vnp_Command","querydr").put("vnp_TmnCode",merchant).put("vnp_TxnRef",c.txnRef())
                    .put("vnp_TransactionNo",c.paymentReference()).put("vnp_TransactionDate",c.transactionDate())
                    .put("vnp_CreateDate",date).put("vnp_IpAddr",ip).put("vnp_OrderInfo",info)
                    .put("vnp_SecureHash",VnpaySigner.hmacSha512(secret,data));
            // Querydr may return the original payment or a different partial refund. The signature,
            // refund type, amount and attempt marker must all match before accepting a result.
            return interpret(new JSONObject(transport.post(query)),c,"querydr");
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();return unknown();
        } catch(Exception uncertain) {return unknown();}
    }
}
