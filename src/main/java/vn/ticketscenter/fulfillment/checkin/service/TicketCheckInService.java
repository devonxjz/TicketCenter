package vn.ticketscenter.fulfillment.checkin.service;

import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanRequestDto;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;
import vn.ticketscenter.fulfillment.checkin.dto.RecentCheckInLogDto;
import java.util.List;
import java.util.UUID;

public interface TicketCheckInService {
    CheckInScanResponseDto scan(UUID actor, UUID key, CheckInScanRequestDto request);

    List<RecentCheckInLogDto> recent(UUID actor, UUID event, int limit);
}
