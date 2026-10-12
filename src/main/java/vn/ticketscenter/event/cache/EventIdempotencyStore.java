package vn.ticketscenter.event.cache;

import vn.ticketscenter.event.repository.EventManagementRepository.Result;

public interface EventIdempotencyStore {
    record Lease(String key,String value) { }
    Lease acquire(String key,String requestHash);
    void completed(Lease lease,String requestHash,Result response);
    void release(Lease lease);
}
