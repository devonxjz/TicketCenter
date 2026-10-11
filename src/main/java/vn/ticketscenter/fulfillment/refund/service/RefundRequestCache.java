package vn.ticketscenter.fulfillment.refund.service;

import java.util.List;
import java.util.UUID;
import vn.ticketscenter.fulfillment.refund.dto.RefundSummaryDto;

public interface RefundRequestCache {
    interface Lease extends AutoCloseable { @Override void close(); }
    Lease lock(List<UUID> tickets);
    void committed(UUID actor,UUID key,List<UUID> tickets,RefundSummaryDto result);
    RefundRequestCache NONE=new RefundRequestCache() {
        public Lease lock(List<UUID> tickets) { return () -> {}; }
        public void committed(UUID actor,UUID key,List<UUID> tickets,RefundSummaryDto result) {}
    };
}
