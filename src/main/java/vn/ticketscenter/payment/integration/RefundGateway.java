package vn.ticketscenter.payment.integration;

import java.math.BigDecimal;
import java.util.UUID;

public interface RefundGateway {
    enum Status { SUCCEEDED, FAILED, PENDING }
    record Result(Status status, String providerReference) {}
    class Timeout extends Exception {}

    Result submit(UUID refundId, BigDecimal amount) throws Timeout;
    Result query(String providerReference) throws Timeout;
}
