package vn.ticketscenter.config.worker;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.repository.EventCancellationRepository;

import java.util.Objects;
import java.util.UUID;
import java.util.HashSet;
import java.util.function.Consumer;

public final class EventCancellationJob {
    private final TransactionManager transactions;
    private final EventCancellationRepository repository;
    private final int batchSize;
    private final Consumer<UUID> committed;

    public EventCancellationJob(TransactionManager transactions,
                                EventCancellationRepository repository,
                                int batchSize) {
        this(transactions,repository,batchSize,id -> { });
    }

    public EventCancellationJob(TransactionManager transactions, EventCancellationRepository repository,
                                int batchSize, Consumer<UUID> committed) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
        if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        this.batchSize = batchSize;
        this.committed=Objects.requireNonNull(committed);
    }

    public int runOnce() {
        var work = transactions.execute(DatabasePrincipal.WORKER,
                entityManager -> repository.findWork(entityManager, batchSize));
        var changed = new HashSet<UUID>();
        try {
            for (var item : work) {
                transactions.execute(DatabasePrincipal.WORKER, entityManager -> {
                    repository.process(entityManager, item);
                    return null;
                });
                changed.add(((EventCancellationRepository.Work) item).eventId());
            }
            transactions.execute(DatabasePrincipal.WORKER, entityManager -> {
                repository.completeFinishedEvents(entityManager);
                return null;
            });
        } finally {
            for (UUID id : changed) {
                try { committed.accept(id); } catch (RuntimeException ignored) { }
            }
        }
        return work.size();
    }
}
