package vn.ticketscenter.payment.integration;

import java.util.Locale;

/**
 * payOS identifies a payment by a positive numeric {@code orderCode} (at most 2^53-1), while the database
 * issues {@code txn_ref = 'PAY-' + 32 hex digits of NEWID()}. The order code is the first 13 hex digits
 * (52 bits) of the txn_ref, so it is deterministic (retries reuse the same payOS link) and reversible
 * to a txn_ref prefix without any extra column.
 */
public final class PayosOrderCodes {
    private static final String PREFIX = "PAY-";
    private static final int HEX_DIGITS = 13;

    private PayosOrderCodes() {}

    public static long fromTxnRef(String txnRef) {
        if (txnRef == null
                || !txnRef.startsWith(PREFIX)
                || txnRef.length() < PREFIX.length() + HEX_DIGITS) {
            throw new IllegalArgumentException(
                    "txnRef is not a payOS-compatible payment reference");
        }
        String hex = txnRef.substring(PREFIX.length(), PREFIX.length() + HEX_DIGITS);
        if (!hex.matches("[0-9A-Fa-f]{" + HEX_DIGITS + "}")) {
            throw new IllegalArgumentException(
                    "txnRef is not a payOS-compatible payment reference");
        }
        long code = Long.parseLong(hex, 16);
        if (code <= 0)
            throw new IllegalArgumentException("txnRef maps to an invalid payOS order code");
        return code;
    }

    /** The txn_ref prefix an order code came from; callers match it with {@code LIKE prefix + '%'}. */
    public static String txnRefPrefix(long orderCode) {
        if (orderCode <= 0 || orderCode >= (1L << (4 * HEX_DIGITS))) {
            throw new IllegalArgumentException("orderCode is outside the TicketsCenter range");
        }
        return PREFIX + String.format(Locale.ROOT, "%0" + HEX_DIGITS + "X", orderCode);
    }
}
