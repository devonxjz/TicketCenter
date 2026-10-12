package vn.ticketscenter.fulfillment.checkin.cache;
import java.util.UUID;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;
public interface CheckInScanCache {
    interface Lease extends AutoCloseable { @Override void close(); }
    Lease lock(UUID ticketId);
    void completed(UUID actor,UUID key,CheckInScanResponseDto result);
}
