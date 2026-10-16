package vn.ticketscenter.fulfillment.checkin.cache;

import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;

import java.util.UUID;

public interface CheckInScanCache {
    interface Lease extends AutoCloseable {
        @Override void close();
    }

    Lease lock(UUID ticketId);
    void completed(UUID actor, UUID key, CheckInScanResponseDto result);
}
