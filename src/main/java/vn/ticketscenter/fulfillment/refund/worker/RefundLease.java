package vn.ticketscenter.fulfillment.refund.worker;

import java.util.*;

public interface RefundLease {
    interface Handle extends AutoCloseable {
        @Override
        void close();
    }

    Optional<Handle> acquire(UUID refundId);
}
