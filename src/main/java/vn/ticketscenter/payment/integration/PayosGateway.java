package vn.ticketscenter.payment.integration;

import vn.ticketscenter.config.error.AppException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** The payOS operations the payment feature needs; {@link PayosGatewayImpl} talks to the real API. */
public interface PayosGateway extends AutoCloseable {
    /** Past this age an unpaid link has expired (links expire with the seat hold), so "never created" is final. */
    Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

    enum Outcome {
        CAPTURED,
        FAILED,
        UNKNOWN
    }

    record Result(Outcome outcome, String providerReference) {
        public static Result unknown() {
            return new Result(Outcome.UNKNOWN, null);
        }
    }

    record CheckoutLink(long orderCode, String paymentLinkId, String checkoutUrl) {}

    /** payOS statuses: PENDING, PROCESSING, UNDERPAID, PAID, CANCELLED, EXPIRED, FAILED. */
    record LinkStatus(
            long orderCode, String paymentLinkId, String status, long amount, long amountPaid) {}

    /** Signature-verified webhook data. */
    record WebhookEvent(
            long orderCode, long amount, String code, String reference, String paymentLinkId) {}

    class InvalidWebhook extends AppException {
        public InvalidWebhook(String message, Throwable cause) {
            super(400, "INVALID_WEBHOOK", message, cause);
        }
    }

    /** payOS only accepts each orderCode once; a second create for the same code returns the existing link. */
    CheckoutLink createLink(long orderCode, long amount, String description, Instant expiresAt);

    /** Empty when payOS does not know the order code (the link was never created). */
    Optional<LinkStatus> findLink(long orderCode);

    WebhookEvent verifyWebhook(String body);

    /** Releases HTTP resources; never throws. */
    @Override
    default void close() {}

    /** Ask payOS what really happened; never throws (transport problems are {@link Outcome#UNKNOWN}). */
    default Result query(String txnRef, BigDecimal amount, boolean windowClosed) {
        try {
            return interpret(findLink(PayosOrderCodes.fromTxnRef(txnRef)), amount, windowClosed);
        } catch (RuntimeException failure) {
            return Result.unknown();
        }
    }

    /**
     * @param windowClosed the payment link has expired, so a link payOS never saw is a final failure
     *                     rather than "maybe still being created"
     */
    static Result interpret(
            Optional<LinkStatus> maybeLink, BigDecimal amount, boolean windowClosed) {
        if (maybeLink.isEmpty()) {
            return windowClosed ? new Result(Outcome.FAILED, null) : Result.unknown();
        }
        LinkStatus link = maybeLink.get();
        String status = link.status() == null ? "" : link.status();
        return switch (status) {
            case "PAID" -> {
                long expected;
                try {
                    expected = amount.longValueExact();
                } catch (ArithmeticException notWholeVnd) {
                    yield Result.unknown();
                }
                boolean exact = link.amount() == expected && link.amountPaid() == expected;
                yield exact && link.paymentLinkId() != null && !link.paymentLinkId().isBlank()
                        ? new Result(Outcome.CAPTURED, link.paymentLinkId())
                        : Result.unknown();
            }
            case "CANCELLED", "EXPIRED", "FAILED" -> new Result(Outcome.FAILED, null);
            default -> Result.unknown();
        };
    }
}
