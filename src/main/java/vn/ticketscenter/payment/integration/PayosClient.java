package vn.ticketscenter.payment.integration;

import vn.ticketscenter.config.error.UpstreamException;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import vn.payos.PayOS;
import vn.payos.exception.APIException;
import vn.payos.exception.NotFoundException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.WebhookData;
import vn.ticketscenter.config.PayosConfig;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** payOS SDK adapter (servlet port of the payos-demo-java-spring OrderController/PaymentController). */
public final class PayosClient implements PayosGateway {
    /** payOS: "Đơn thanh toán đã tồn tại" — the order code already has a payment link. */
    private static final String ORDER_CODE_EXISTS = "231";

    /** payOS: payment link not found for the requested order code. */
    private static final String LINK_NOT_FOUND = "101";

    private static final String CHECKOUT_BASE = "https://pay.payos.vn/web/";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PayOS payOS;
    private final String returnUrl;
    private final String cancelUrl;

    /** Owned HTTP client, closed with the web app; null when the PayOS instance was supplied. */
    private final OkHttpClient http;

    public PayosClient(PayosConfig config) {
        this(config, config.httpClient());
    }

    PayosClient(PayosConfig config, OkHttpClient http) {
        this(config.client(http), config.returnUrl(), config.cancelUrl(), http);
    }

    public PayosClient(PayOS payOS, String returnUrl, String cancelUrl) {
        this(payOS, returnUrl, cancelUrl, null);
    }

    private PayosClient(PayOS payOS, String returnUrl, String cancelUrl, OkHttpClient http) {
        this.payOS = Objects.requireNonNull(payOS, "payOS is required");
        this.returnUrl = Objects.requireNonNull(returnUrl, "returnUrl is required");
        this.cancelUrl = Objects.requireNonNull(cancelUrl, "cancelUrl is required");
        this.http = http;
    }

    /** Stops okhttp's worker threads and drops pooled connections. */
    @Override
    public void close() {
        if (http == null) return;
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    @Override
    public CheckoutLink createLink(
            long orderCode, long amount, String description, Instant expiresAt) {
        CreatePaymentLinkRequest request =
                CreatePaymentLinkRequest.builder()
                        .orderCode(orderCode)
                        .amount(amount)
                        .description(description)
                        .item(
                                PaymentLinkItem.builder()
                                        .name(description)
                                        .quantity(1)
                                        .price(amount)
                                        .build())
                        .returnUrl(returnUrl)
                        .cancelUrl(cancelUrl)
                        .expiredAt(expiresAt == null ? null : expiresAt.getEpochSecond())
                        .build();
        try {
            CreatePaymentLinkResponse created = payOS.paymentRequests().create(request);
            return new CheckoutLink(
                    created.getOrderCode(), created.getPaymentLinkId(), created.getCheckoutUrl());
        } catch (APIException failure) {
            if (!ORDER_CODE_EXISTS.equals(failure.getErrorCode().orElse(null)))
                throw unavailable(failure);
            // A retry after a lost response: reuse the link payOS already holds for this order
            // code.
            LinkStatus existing = findLink(orderCode).orElseThrow(() -> unavailable(failure));
            if (!"PENDING".equals(existing.status()) || existing.amount() != amount)
                throw unavailable(failure);
            return new CheckoutLink(
                    orderCode, existing.paymentLinkId(), CHECKOUT_BASE + existing.paymentLinkId());
        } catch (UpstreamException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public Optional<LinkStatus> findLink(long orderCode) {
        try {
            PaymentLink link = payOS.paymentRequests().get(orderCode);
            return Optional.of(
                    new LinkStatus(
                            link.getOrderCode(),
                            link.getId(),
                            link.getStatus() == null ? null : link.getStatus().getValue(),
                            link.getAmount(),
                            link.getAmountPaid()));
        } catch (NotFoundException missing) {
            return Optional.empty();
        } catch (APIException failure) {
            if (LINK_NOT_FOUND.equals(failure.getErrorCode().orElse(null))) return Optional.empty();
            throw unavailable(failure);
        } catch (UpstreamException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    private static UpstreamException unavailable(RuntimeException cause) {
        return new UpstreamException("PAYOS_UNAVAILABLE", "payOS request failed", cause);
    }

    @Override
    public WebhookEvent verifyWebhook(String body) {
        try {
            Map<?, ?> payload = JSON.readValue(body, Map.class);
            WebhookData data = payOS.webhooks().verify(payload);
            return new WebhookEvent(
                    data.getOrderCode(),
                    data.getAmount(),
                    data.getCode(),
                    data.getReference(),
                    data.getPaymentLinkId());
        } catch (Exception invalid) {
            throw new InvalidWebhook("payOS webhook could not be verified", invalid);
        }
    }
}
