package vn.ticketscenter.config.worker;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delivers outbox rows. Rows are leased in a short transaction ({@code usp_OutboxLease}), delivered outside
 * any transaction, then marked PUBLISHED or FAILED by {@code usp_OutboxComplete} (retried with backoff until
 * {@code MAX_ATTEMPTS}).
 */
public final class OutboxWorker {
    public interface EmailJob {
        void send(UUID outboxId, String eventType, String payload);
    }

    static final int MAX_ATTEMPTS = 5;
    private static final Logger LOGGER = Logger.getLogger(OutboxWorker.class.getName());

    private record Leased(UUID id, String eventType, String payload) {}

    private final TransactionManager tx;
    private final EmailJob email;
    private final String owner;

    public OutboxWorker(TransactionManager tx, EmailJob email, String owner) {
        this.tx = tx;
        this.email = email;
        this.owner = owner;
    }

    public int runOnce() {
        List<Leased> leased =
                tx.execute(
                        DatabasePrincipal.WORKER,
                        em -> {
                            @SuppressWarnings("unchecked")
                            List<Object[]> rows =
                                    em.createNativeQuery(
                                                    "EXEC dbo.usp_OutboxLease @owner=:o,@max_attempts=:max")
                                            .setParameter("o", owner)
                                            .setParameter("max", MAX_ATTEMPTS)
                                            .getResultList();
                            return rows.stream()
                                    .map(r -> new Leased(uuid(r[0]), (String) r[1], (String) r[2]))
                                    .toList();
                        });
        for (Leased row : leased) {
            boolean sent;
            try {
                email.send(row.id(), row.eventType(), row.payload());
                sent = true;
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Outbox delivery failed for " + row.id(), failure);
                sent = false;
            }
            finish(row.id(), sent);
        }
        return leased.size();
    }

    private void finish(UUID id, boolean sent) {
        tx.execute(
                DatabasePrincipal.WORKER,
                em ->
                        em.createNativeQuery(
                                        "EXEC dbo.usp_OutboxComplete @id=:id,@owner=:o,@sent=:s")
                                .setParameter("id", id)
                                .setParameter("o", owner)
                                .setParameter("s", sent)
                                .getSingleResult());
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }
}
