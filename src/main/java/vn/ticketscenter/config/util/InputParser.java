package vn.ticketscenter.config.util;

import java.math.BigDecimal;
import java.util.UUID;

public final class InputParser {

    private InputParser() {
    }

    public static UUID asUuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID u) return u;
        return UUID.fromString(value.toString());
    }

    public static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    public static BigDecimal vnd(String value, String field) {
        try {
            BigDecimal amount = new BigDecimal(value);
            if (amount.scale() > 0 || amount.precision() > 19 || amount.signum() < 0) {
                throw new NumberFormatException();
            }
            return amount;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a non-negative whole VND amount");
        }
    }
}
