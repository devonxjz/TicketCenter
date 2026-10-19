package vn.ticketscenter.ticketing.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.function.Executable;
import vn.ticketscenter.config.error.AppException;

final class HoldIdempotencyAssertions {
    private HoldIdempotencyAssertions() {}

    static AppException assertUnavailable(Executable action) {
        AppException failure = assertThrows(AppException.class, action);
        assertEquals(503, failure.status());
        assertEquals("HOLD_UNAVAILABLE", failure.code());
        return failure;
    }
}
