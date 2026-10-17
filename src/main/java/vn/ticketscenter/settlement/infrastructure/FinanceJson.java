package vn.ticketscenter.settlement.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Serializes explicit response maps, never persistent entities or floating money. */
public final class FinanceJson {
    private FinanceJson() {}

    public static String write(Object value) {
        var out = new StringBuilder();
        append(out, value, 0);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value, int depth) {
        if (depth > 32) throw invalid();
        if (value == null) out.append("null");
        else if (value instanceof String
                || value instanceof UUID
                || value instanceof Instant
                || value instanceof BigDecimal) quote(out, value.toString());
        else if (value instanceof Boolean
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte) out.append(value);
        else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw invalid();
                if (!first) out.append(',');
                first = false;
                quote(out, key);
                out.append(':');
                append(out, entry.getValue(), depth + 1);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            boolean first = true;
            for (var element : list) {
                if (!first) out.append(',');
                first = false;
                append(out, element, depth + 1);
            }
            out.append(']');
        } else throw invalid();
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || Character.isLowSurrogate(c)) throw invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
                    throw invalid();
                out.append(c).append(text.charAt(i));
                continue;
            }
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unsupported finance response value");
    }
}
