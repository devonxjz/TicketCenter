package vn.ticketscenter.ticketing.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HoldMutationFingerprintTest {
    @Test
    void domainHashesMatchIndependentLiteralSha256Fixtures() {
        var request = new CreateHoldRequest(id(1), List.of(new HoldSelection(id(2), null, 3)));
        assertEquals("3136d3bbf0fd41ec54c7c61956863d55da3e92a70f50f2e28fb4f09436b924cd", request.canonicalHash());
        assertEquals("9f7736e4570a4c77a821726ecc6af68d72bae2368993372339d694b1b07cde23", HoldMutationFingerprint.create(request));
        assertEquals("4ed0b5e7e09ffee1b8b7346e37a191f4a28d7fe56a2b79991d36eb8b864ee535", HoldMutationFingerprint.cancel(id(1)));
    }

    @Test
    void normalizedEquivalentSelectionsProduceTheSameCreateFingerprint() {
        var first = new CreateHoldRequest(id(1), List.of(new HoldSelection(id(2), null, 1), new HoldSelection(id(2), null, 2)));
        var second = new CreateHoldRequest(id(1), List.of(new HoldSelection(id(2), null, 3)));
        assertEquals(HoldMutationFingerprint.create(first), HoldMutationFingerprint.create(second));
        assertNotEquals(HoldMutationFingerprint.create(first), HoldMutationFingerprint.cancel(id(1)));
        assertNotEquals(HoldMutationFingerprint.cancel(id(1)), HoldMutationFingerprint.cancel(id(2)));
        assertThrows(IllegalArgumentException.class, () -> HoldMutationFingerprint.cancel(new UUID(0, 0)));
    }

    private static UUID id(int number) { return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(number)); }
}
