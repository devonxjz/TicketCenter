package vn.ticketscenter.audit.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Parse bounded JSON, then expose only deliberately typed, non-secret fields. */
public final class AuditMetadata {
    private AuditMetadata() {}

    private static final Set<String> IDS =
            Set.of(
                    "eventId",
                    "organizationId",
                    "orderId",
                    "settlementId",
                    "refundId",
                    "commissionRuleId",
                    "payoutId");
    private static final Set<String> MONEY =
            Set.of(
                    "amount",
                    "grossRevenue",
                    "totalRefund",
                    "totalCommission",
                    "netPayable",
                    "paidAmount",
                    "pendingAmount",
                    "fixedFee");
    private static final Set<String> STATES =
            Set.of(
                    "DRAFT",
                    "PENDING_APPROVAL",
                    "APPROVED",
                    "REJECTED",
                    "PUBLISHED",
                    "CANCELLED",
                    "PAID",
                    "CONFIRMED",
                    "REQUESTED",
                    "PROCESSING",
                    "COMPLETED",
                    "RETRYABLE",
                    "NEEDS_RECONCILIATION",
                    "PENDING",
                    "UNKNOWN",
                    "FAILED",
                    "SUCCEEDED",
                    "ACTIVE",
                    "DISABLED");

    public static Map<String, String> project(String json) {
        if (json == null || json.length() > 8000) return Map.of();
        try {
            Object parsed = new Parser(json).parse();
            if (!(parsed instanceof Map<?, ?> source)) return Map.of();
            var result = new LinkedHashMap<String, String>();
            for (var entry : source.entrySet()) {
                String key = (String) entry.getKey();
                Object raw = entry.getValue();
                if (!(raw instanceof String || raw instanceof BigDecimal)) continue;
                String text = raw.toString();
                if (IDS.contains(key)
                        && raw instanceof String
                        && text.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
                    result.put(key, UUID.fromString(text).toString());
                else if (MONEY.contains(key) && text.matches("[0-9]{1,19}"))
                    result.put(key, new BigDecimal(text).toPlainString());
                else if (key.equals("ratePercent") && text.matches("[0-9]{1,3}(\\.[0-9]{1,4})?")) {
                    BigDecimal rate = new BigDecimal(text);
                    if (rate.compareTo(new BigDecimal("100")) <= 0)
                        result.put(key, rate.setScale(4).toPlainString());
                } else if (Set.of("previousStatus", "newStatus", "status", "result").contains(key)
                        && STATES.contains(text)) result.put(key, text);
                else if (Set.of("processedAt", "confirmedAt", "effectiveFrom", "effectiveTo")
                                .contains(key)
                        && raw instanceof String) {
                    try {
                        result.put(key, Instant.parse(text).toString());
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            return Collections.unmodifiableMap(result);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private static final class Parser {
        final String text;
        int pos;

        Parser(String text) {
            this.text = text;
        }

        Object parse() {
            Object value = value(0);
            space();
            if (pos != text.length()) throw bad();
            return value;
        }

        Object value(int depth) {
            if (depth > 16) throw bad();
            space();
            if (pos >= text.length()) throw bad();
            char c = text.charAt(pos);
            if (c == '"') return string();
            if (c == '{') {
                pos++;
                var result = new LinkedHashMap<String, Object>();
                if (take('}')) return result;
                do {
                    String key = string();
                    expect(':');
                    Object value = value(depth + 1);
                    if (result.containsKey(key)) throw bad();
                    result.put(key, value);
                } while (take(','));
                expect('}');
                return result;
            }
            if (c == '[') {
                pos++;
                var result = new ArrayList<Object>();
                if (take(']')) return result;
                do {
                    result.add(value(depth + 1));
                } while (take(','));
                expect(']');
                return result;
            }
            for (String literal : List.of("true", "false", "null"))
                if (text.startsWith(literal, pos)) {
                    pos += literal.length();
                    return literal.equals("null") ? null : Boolean.valueOf(literal);
                }
            int start = pos;
            while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
            String number = text.substring(start, pos);
            if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw bad();
            return new BigDecimal(number);
        }

        String string() {
            expect('"');
            var out = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') return out.toString();
                if (c < 32) throw bad();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) throw bad();
                char escape = text.charAt(pos++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw bad();
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw bad();
                }
            }
            throw bad();
        }

        boolean take(char c) {
            space();
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        void expect(char c) {
            if (!take(c)) throw bad();
        }

        void space() {
            while (pos < text.length() && " \r\n\t".indexOf(text.charAt(pos)) >= 0) pos++;
        }

        IllegalArgumentException bad() {
            return new IllegalArgumentException("Invalid audit metadata");
        }
    }
}
