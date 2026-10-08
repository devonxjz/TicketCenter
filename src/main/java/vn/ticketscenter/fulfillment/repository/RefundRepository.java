package vn.ticketscenter.fulfillment.repository;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.payment.integration.RefundGateway;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RefundRepository {
    public record WorkItem(UUID refundId, String providerReference, BigDecimal amount) {}

    public List<WorkItem> findWork(EntityManager entityManager, int batchSize) {
        return Collections.emptyList();
    }

    public void apply(EntityManager entityManager, UUID refundId, RefundGateway.Result result) {
    }
}
