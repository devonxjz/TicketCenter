package vn.ticketscenter.fulfillment.checkin.service;

import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.repository.CheckInScanRepository;
import vn.ticketscenter.fulfillment.ticket.service.SignedTicketQrSigner;

import java.util.Objects;
import java.util.UUID;

public final class CheckInScanService {
    private final CheckInScanRepository repository;
    private final SignedTicketQrSigner signer;

    public CheckInScanService(CheckInScanRepository repository, SignedTicketQrSigner signer) {
        this.repository = Objects.requireNonNull(repository);
        this.signer = Objects.requireNonNull(signer);
    }

    public CheckInScanRepository.ScanResult scanQr(UUID eventId, UUID actorId, String envelope) {
        if (envelope == null || envelope.isBlank() || envelope.length() > 2048)
            throw new CheckInException(400, "INVALID_QR", "Mã QR không hợp lệ.");
        UUID ticketId;
        try {
            ticketId = signer.verifyAndDecode(envelope).ticketId();
        } catch (SecurityException | IllegalArgumentException invalid) {
            throw new CheckInException(400, "INVALID_QR", "Mã QR không hợp lệ.");
        }
        return repository.scanSignedTicket(eventId, actorId, ticketId);
    }

    public CheckInScanRepository.ScanResult scanCode(UUID eventId, UUID actorId, String code) {
        if (code == null || !code.matches("[A-Za-z0-9-]{3,100}"))
            throw new CheckInException(400, "INVALID_TICKET_CODE", "Mã vé không hợp lệ.");
        return repository.scanTicketCode(eventId, actorId, code);
    }
}
