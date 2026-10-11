package vn.ticketscenter.ticketing.dto;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActiveHoldProjectionTest {
    private static final UUID ID = UUID.randomUUID();

    @Test
    void itemsAreImmutableAndVndIsExactWithScaleZero() {
        var item = new ActiveHoldProjection.Item(ID, null, 2, new BigDecimal("1234567890123456789.00"));
        var mutable = new ArrayList<>(List.of(item));
        var projection = projection(mutable);
        mutable.clear();
        assertEquals(List.of(item), projection.items());
        assertEquals(new BigDecimal("1234567890123456789"), projection.items().getFirst().unitPrice());
        assertThrows(UnsupportedOperationException.class, () -> projection.items().clear());
    }

    @Test
    void rejectsInvalidIdsMoneyAndTicketCounts() {
        assertThrows(IllegalArgumentException.class, () -> new ActiveHoldProjection.Item(new UUID(0, 0), null, 1, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ActiveHoldProjection.Item(ID, ID, 2, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ActiveHoldProjection.Item(ID, null, 0, BigDecimal.ZERO));
        assertThrows(ArithmeticException.class, () -> new ActiveHoldProjection.Item(ID, null, 1, new BigDecimal("1.5")));
        assertEquals(new BigDecimal("9999999999999999999"), new ActiveHoldProjection.Item(ID, null, 1,
                new BigDecimal("9999999999999999999")).unitPrice());
        assertThrows(IllegalArgumentException.class, () -> new ActiveHoldProjection.Item(ID, null, 1, new BigDecimal("10000000000000000000")));
        assertThrows(IllegalArgumentException.class, () -> new ActiveHoldProjection.Item(ID, null, 1, BigDecimal.valueOf(-1)));
        assertThrows(IllegalArgumentException.class, () -> projection(List.of()));
        assertThrows(IllegalArgumentException.class, () -> projection(List.of(new ActiveHoldProjection.Item(ID, null, 8, BigDecimal.ONE),
                new ActiveHoldProjection.Item(ID, null, 1, BigDecimal.ONE))));
        var seated = new ActiveHoldProjection.Item(ID, ID, 1, BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> projection(List.of(seated, seated)));
    }

    private static ActiveHoldProjection projection(List<ActiveHoldProjection.Item> items) {
        return new ActiveHoldProjection(ID, ID, ID, Instant.parse("2026-10-09T00:10:00.123456789Z"), items);
    }
}
