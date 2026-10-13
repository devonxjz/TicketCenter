package vn.ticketscenter.config.worker;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.integration.mail.EmailMessage;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEmailJobTest {
    private final List<EmailMessage> sent = new ArrayList<>();
    private final OutboxEmailJob job = new OutboxEmailJob(
            (id, type) -> new OutboxEmailJob.Context("a@b.c", "An", "OC1", "Show", new BigDecimal("100000")), sent::add);

    @Test void orderPaidSendsEmail() {
        job.send(UUID.randomUUID(), "ORDER_PAID", "{}");
        assertEquals("a@b.c", sent.get(0).recipient());
        assertTrue(sent.get(0).subject().contains("OC1"));
    }
    @Test void refundSucceededSendsEmail() {
        job.send(UUID.randomUUID(), "REFUND_SUCCEEDED", "{}");
        assertTrue(sent.get(0).subject().startsWith("Hoàn tiền"));
    }
    @Test void unknownTypeThrowsSoRowFails() {
        assertThrows(IllegalArgumentException.class, () -> job.send(UUID.randomUUID(), "SOMETHING", "{}"));
        assertTrue(sent.isEmpty());
    }
    @Test void missingContextThrows() {
        var empty = new OutboxEmailJob((i, t) -> null, sent::add);
        assertThrows(IllegalStateException.class, () -> empty.send(UUID.randomUUID(), "ORDER_PAID", "{}"));
    }
}
