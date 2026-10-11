package vn.ticketscenter.ticketing.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateHoldRequestTest {
    private static final UUID EVENT = id(1);
    private static final UUID ZONE = id(2);
    private static final UUID SEAT = id(3);

    @Test
    void canonicalHashesHaveLiteralVersionedBinaryFixtures() {
        assertEquals("3136d3bbf0fd41ec54c7c61956863d55da3e92a70f50f2e28fb4f09436b924cd",
                request(new HoldSelection(ZONE, null, 3)).canonicalHash());
        assertEquals("68ffeabf414d80a05a4843394538751d8f2d82e85ad5cd361198c65408ef579a",
                request(new HoldSelection(ZONE, SEAT, 1)).canonicalHash());
    }

    @Test
    void permutationsAndStandingSplitsHaveTheSameCanonicalRequest() {
        var split = request(new HoldSelection(id(4), id(5), 1), new HoldSelection(ZONE, null, 1),
                new HoldSelection(ZONE, null, 2));
        var combined = request(new HoldSelection(ZONE, null, 3), new HoldSelection(id(4), id(5), 1));
        assertEquals(List.of(new HoldSelection(ZONE, null, 3), new HoldSelection(id(4), id(5), 1)),
                split.selections());
        assertEquals(combined, split);
        assertEquals(combined.canonicalHash(), split.canonicalHash());
    }

    @Test
    void eventZoneQuantityAndSeatChangesChangeTheHash() {
        String original = request(new HoldSelection(ZONE, SEAT, 1)).canonicalHash();
        assertNotEquals(original, new CreateHoldRequest(id(4), List.of(new HoldSelection(ZONE, SEAT, 1))).canonicalHash());
        assertNotEquals(original, request(new HoldSelection(id(4), SEAT, 1)).canonicalHash());
        assertNotEquals(original, request(new HoldSelection(ZONE, id(4), 1)).canonicalHash());
        assertNotEquals(original, request(new HoldSelection(ZONE, null, 1)).canonicalHash());
        assertNotEquals(request(new HoldSelection(ZONE, null, 1)).canonicalHash(),
                request(new HoldSelection(ZONE, null, 2)).canonicalHash());
    }

    @Test
    void duplicateSeatsAreRejectedEvenAcrossZones() {
        assertThrows(IllegalArgumentException.class, () -> request(new HoldSelection(ZONE, SEAT, 1),
                new HoldSelection(ZONE, SEAT, 1)));
        assertThrows(IllegalArgumentException.class, () -> request(new HoldSelection(ZONE, SEAT, 1),
                new HoldSelection(id(4), SEAT, 1)));
    }

    @Test
    void ticketBoundsAndSeatedQuantityAreEnforced() {
        assertEquals(8, request(new HoldSelection(ZONE, null, 8)).selections().getFirst().quantity());
        assertThrows(IllegalArgumentException.class, () -> new HoldSelection(ZONE, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new HoldSelection(ZONE, null, -1));
        assertThrows(IllegalArgumentException.class, () -> new HoldSelection(ZONE, null, 9));
        assertThrows(IllegalArgumentException.class, () -> new HoldSelection(ZONE, SEAT, 2));
        assertThrows(IllegalArgumentException.class, () -> request(new HoldSelection(ZONE, null, 8),
                new HoldSelection(id(4), null, 1)));
        assertThrows(IllegalArgumentException.class, () -> new CreateHoldRequest(EVENT, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CreateHoldRequest(EVENT,
                java.util.Collections.nCopies(9, new HoldSelection(ZONE, null, 1))));
    }

    @Test
    void nullAndZeroIdentifiersAreRejected() {
        UUID zero = new UUID(0, 0);
        assertThrows(IllegalArgumentException.class, () -> new HoldSelection(null, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new HoldSelection(zero, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new HoldSelection(ZONE, zero, 1));
        assertThrows(IllegalArgumentException.class, () -> new CreateHoldRequest(null, List.of(new HoldSelection(ZONE, null, 1))));
        assertThrows(IllegalArgumentException.class, () -> new CreateHoldRequest(zero, List.of(new HoldSelection(ZONE, null, 1))));
        assertThrows(IllegalArgumentException.class, () -> new CreateHoldRequest(EVENT, null));
        assertThrows(IllegalArgumentException.class, () -> new CreateHoldRequest(EVENT, Arrays.asList((HoldSelection) null)));
    }

    @Test
    void canonicalSelectionsAreAnImmutableSnapshot() {
        var mutable = new ArrayList<>(List.of(new HoldSelection(ZONE, null, 1)));
        var request = new CreateHoldRequest(EVENT, mutable);
        mutable.clear();
        assertEquals(List.of(new HoldSelection(ZONE, null, 1)), request.selections());
        assertThrows(UnsupportedOperationException.class, () -> request.selections().clear());
    }

    private static CreateHoldRequest request(HoldSelection... selections) {
        return new CreateHoldRequest(EVENT, List.of(selections));
    }

    private static UUID id(int number) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(number));
    }
}
