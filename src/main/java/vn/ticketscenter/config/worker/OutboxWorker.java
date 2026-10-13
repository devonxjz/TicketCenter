package vn.ticketscenter.config.worker;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delivers outbox rows. Rows are leased in a short transaction, delivered outside any transaction,
 * then marked PUBLISHED or FAILED (retried with backoff until {@code MAX_ATTEMPTS}).
 */
public final class OutboxWorker {
    public interface EmailJob { void send(UUID outboxId, String eventType, String payload); }

    static final int MAX_ATTEMPTS = 5;
    private static final Logger LOGGER = Logger.getLogger(OutboxWorker.class.getName());

    private record Leased(UUID id, String eventType, String payload) { }

    private final TransactionManager tx;
    private final EmailJob email;
    private final String owner;

    public OutboxWorker(TransactionManager tx, EmailJob email, String owner) {
        this.tx = tx;
        this.email = email;
        this.owner = owner;
    }

    public int runOnce() {
        List<Leased> leased = tx.execute(DatabasePrincipal.WORKER, em -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT TOP 50 id,event_type,payload FROM dbo.tc_outbox WITH (UPDLOCK,READPAST)
                    WHERE (status IN ('PENDING','FAILED') OR (status='PROCESSING' AND lease_until<=SYSUTCDATETIME()))
                      AND attempts<:max AND event_type<>'EVENT_CANCELLED' AND available_at<=SYSUTCDATETIME()
                    ORDER BY created_at""").setParameter("max", MAX_ATTEMPTS).getResultList();
            return rows.stream().map(r -> {
                UUID id = (UUID) r[0];
                em.createNativeQuery("""
                        UPDATE dbo.tc_outbox SET status='PROCESSING',lease_owner=:o,
                          lease_until=DATEADD(minute,2,SYSUTCDATETIME()),attempts=attempts+1 WHERE id=:id""")
                        .setParameter("o", owner).setParameter("id", id).executeUpdate();
                return new Leased(id, (String) r[1], (String) r[2]);
            }).toList();
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
        String sql = sent
                ? "UPDATE dbo.tc_outbox SET status='PUBLISHED',published_at=SYSUTCDATETIME(),lease_owner=NULL,lease_until=NULL WHERE id=:id AND lease_owner=:o"
                : "UPDATE dbo.tc_outbox SET status='FAILED',available_at=DATEADD(minute,5,SYSUTCDATETIME()),lease_owner=NULL,lease_until=NULL WHERE id=:id AND lease_owner=:o";
        tx.execute(DatabasePrincipal.WORKER, em -> em.createNativeQuery(sql).setParameter("id", id).setParameter("o", owner).executeUpdate());
    }
}
