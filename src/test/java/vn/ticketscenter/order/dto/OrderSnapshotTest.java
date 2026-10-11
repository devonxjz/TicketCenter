package vn.ticketscenter.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.ticketscenter.order.model.OrderStatus;
import static org.junit.jupiter.api.Assertions.*;

class OrderSnapshotTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant EXPIRY = Instant.parse("2026-10-10T00:10:00Z");

    @Test void copiesItemsAndRetainsStoredMoneyAndLabels() {
        var items = new ArrayList<>(List.of(item(ID, 2, "100.00"),
                new OrderSnapshot.Item(SECOND, ID, SECOND, "VIP at booking", Optional.of("A1 at booking"), 1, new BigDecimal("101"))));
        var snapshot = snapshot(items, new OrderSnapshot.Totals(new BigDecimal("301.00"),
                new BigDecimal("60.00"), new BigDecimal("241.00")));
        items.clear();
        assertEquals(2, snapshot.items().size());
        assertEquals(new BigDecimal("200"), snapshot.items().getFirst().lineTotal());
        assertEquals("VIP at booking", snapshot.items().get(1).zoneNameSnapshot());
        assertEquals(Optional.of("A1 at booking"), snapshot.items().get(1).seatLabelSnapshot());
        assertEquals(new BigDecimal("301"), snapshot.totals().subtotal());
        assertEquals(new BigDecimal("60"), snapshot.totals().discountAmount());
        assertEquals(new BigDecimal("241"), snapshot.totals().totalAmount());
        assertEquals(Optional.of("Original Code"), snapshot.appliedCouponCode());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.items().clear());
    }

    @Test void checksArithmeticAndFloorCapWithoutRepricingExistingCoupon() {
        assertEquals(new OrderSnapshot.Totals(new BigDecimal("101"), new BigDecimal("30"), new BigDecimal("71")),
                new OrderSnapshot.Totals(new BigDecimal("101.00"), new BigDecimal("30.00"), new BigDecimal("71.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderSnapshot.Totals(new BigDecimal("101"), new BigDecimal("31"), new BigDecimal("70")));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderSnapshot.Totals(new BigDecimal("101"), new BigDecimal("30"), new BigDecimal("72")));
        var totals = new OrderSnapshot.Totals(new BigDecimal("100"), new BigDecimal("17"), new BigDecimal("83"));
        assertEquals(new BigDecimal("17"), snapshot(List.of(item(ID, 1, "100")), totals).totals().discountAmount());
        assertEquals(BigDecimal.ZERO, snapshot(List.of(item(ID, 1, "0")),
                new OrderSnapshot.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).totals().totalAmount());
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "1.01", "10000000000000000000"})
    void rejectsOutOfDomainMoney(String value) {
        BigDecimal invalid = new BigDecimal(value);
        assertThrows(IllegalArgumentException.class, () -> item(ID, 1, value));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderSnapshot.Totals(invalid, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderSnapshot.Totals(BigDecimal.ONE, invalid, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderSnapshot.Totals(BigDecimal.ONE, BigDecimal.ZERO, invalid));
    }

    @Test void preservesFullDecimalDomainAndRejectsLineAndAggregateOverflow() {
        var huge = new BigDecimal("9999999999999999999");
        var snapshot = snapshot(List.of(item(ID, 1, huge.toString())),
                new OrderSnapshot.Totals(huge, BigDecimal.ZERO, huge));
        assertEquals(huge, snapshot.totals().totalAmount());
        assertThrows(ArithmeticException.class, () -> snapshot.totals().totalAmount().longValueExact());
        assertThrows(IllegalArgumentException.class, () -> item(ID, 2, huge.toString()));
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(item(ID, 1, huge.toString()),
                item(SECOND, 1, "1")), new OrderSnapshot.Totals(huge, BigDecimal.ZERO, huge)));
    }

    @Test void rejectsDuplicatesInvalidQuantitiesAndSubtotalMismatch() {
        var hundred = new OrderSnapshot.Totals(new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"));
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(item(ID, 1, "50"), item(ID, 1, "50")), hundred));
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(), hundred));
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(item(ID, 1, "99")), hundred));
        assertThrows(IllegalArgumentException.class, () -> item(ID, 0, "100"));
        assertThrows(IllegalArgumentException.class, () -> item(ID, 9, "100"));
        assertThrows(IllegalArgumentException.class, () -> new OrderSnapshot.Item(ID, ID, ID, "VIP", Optional.of("A1"), 2, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(item(ID, 8, "0"), item(SECOND, 1, "0")),
                new OrderSnapshot.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        assertEquals(8, snapshot(List.of(item(ID, 8, "0")),
                new OrderSnapshot.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).items().getFirst().quantity());
    }

    @Test void rejectsMissingIdentityLabelsAndNullableProjectionParts() {
        var totals = new OrderSnapshot.Totals(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE);
        var items = List.of(item(ID, 1, "1"));
        UUID zero = new UUID(0, 0);
        for (UUID invalid : new UUID[] {null, zero}) {
            assertThrows(IllegalArgumentException.class, () -> item(invalid, 1, "1"));
            assertThrows(IllegalArgumentException.class, () -> new OrderSnapshot(
                    invalid, "ORDER", ID, ID, ID, OrderStatus.PENDING_PAYMENT, EXPIRY, items, totals, Optional.empty()));
            assertThrows(IllegalArgumentException.class, () -> new OrderSnapshot(
                    ID, "ORDER", invalid, ID, ID, OrderStatus.PENDING_PAYMENT, EXPIRY, items, totals, Optional.empty()));
            assertThrows(IllegalArgumentException.class, () -> new OrderSnapshot(
                    ID, "ORDER", ID, invalid, ID, OrderStatus.PENDING_PAYMENT, EXPIRY, items, totals, Optional.empty()));
            assertThrows(IllegalArgumentException.class, () -> new OrderSnapshot(
                    ID, "ORDER", ID, ID, invalid, OrderStatus.PENDING_PAYMENT, EXPIRY, items, totals, Optional.empty()));
        }
        assertThrows(NullPointerException.class, () -> new OrderSnapshot.Item(ID, ID, ID, "VIP", null, 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderSnapshot.Item(ID, ID, ID, "", Optional.empty(), 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderSnapshot.Item(ID, ID, ID, "VIP", Optional.of("\uD800"), 1, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> snapshot(null, totals));
        assertThrows(NullPointerException.class, () -> snapshot(items, null));
        assertThrows(NullPointerException.class, () -> new OrderSnapshot(
                ID, "ORDER", ID, ID, ID, null, EXPIRY, items, totals, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new OrderSnapshot(
                ID, "ORDER", ID, ID, ID, OrderStatus.PENDING_PAYMENT, null, items, totals, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new OrderSnapshot(
                ID, "ORDER", ID, ID, ID, OrderStatus.PENDING_PAYMENT, EXPIRY, items, totals, null));
    }

    @Test void enforcesLockedSnapshotTextLengths() {
        var item = new OrderSnapshot.Item(ID, ID, ID, "Z".repeat(120), Optional.of("S".repeat(50)), 1, BigDecimal.ONE);
        var totals = new OrderSnapshot.Totals(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE);
        var order = new OrderSnapshot(ID, "O".repeat(64), ID, ID, ID,
                OrderStatus.PENDING_PAYMENT, EXPIRY, List.of(item), totals, Optional.empty());
        assertEquals("O".repeat(64), order.orderCode());
        assertEquals("Z".repeat(120), order.items().getFirst().zoneNameSnapshot());
        assertEquals(Optional.of("S".repeat(50)), order.items().getFirst().seatLabelSnapshot());
        assertThrows(IllegalArgumentException.class, () -> new OrderSnapshot(ID, "O".repeat(65), ID, ID, ID,
                OrderStatus.PENDING_PAYMENT, EXPIRY, List.of(item), totals, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderSnapshot.Item(ID, ID, ID, "Z".repeat(121), Optional.empty(), 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderSnapshot.Item(ID, ID, ID, "Z", Optional.of("S".repeat(51)), 1, BigDecimal.ONE));
    }

    private static OrderSnapshot.Item item(UUID id, int quantity, String price) {
        return new OrderSnapshot.Item(id, ID, null, "Standing at booking", Optional.empty(), quantity, new BigDecimal(price));
    }

    private static OrderSnapshot snapshot(List<OrderSnapshot.Item> items, OrderSnapshot.Totals totals) {
        return new OrderSnapshot(ID, "ORDER", ID, ID, ID, OrderStatus.PENDING_PAYMENT,
                EXPIRY, items, totals, Optional.of("Original Code"));
    }
}
