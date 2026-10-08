package vn.ticketscenter.config.worker;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.repository.EventCancellationRepository;

import java.util.Objects;

public final class EventCancellationJob {
    private final TransactionManager transactions;
    private final EventCancellationRepository repository;
    private final int batchSize;

    public EventCancellationJob(TransactionManager transactions,
                                EventCancellationRepository repository,
                                int batchSize) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
        this.batchSize = batchSize;
    }

    public int runOnce() {
        var work = transactions.execute(DatabasePrincipal.WORKER,
                entityManager -> repository.findWork(entityManager, batchSize));
        for (var item : work) {
            transactions.execute(DatabasePrincipal.WORKER, entityManager -> {
                repository.process(entityManager, item);
                return null;
            });
        }
        transactions.execute(DatabasePrincipal.WORKER, entityManager -> {
            repository.completeFinishedEvents(entityManager);
            return null;
        });
        return work.size();
    }
}
