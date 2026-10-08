package vn.ticketscenter.event.repository;

import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;

public class EventCancellationRepository {
    public List<Object> findWork(EntityManager entityManager, int batchSize) {
        return Collections.emptyList();
    }

    public void process(EntityManager entityManager, Object item) {
    }

    public void completeFinishedEvents(EntityManager entityManager) {
    }
}
