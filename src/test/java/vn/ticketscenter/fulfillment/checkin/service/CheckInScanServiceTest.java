package vn.ticketscenter.fulfillment.checkin.service;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.repository.CheckInScanRepository;
import vn.ticketscenter.fulfillment.ticket.service.SignedTicketQrSigner;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CheckInScanServiceTest {
    private static final UUID EVENT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private final SignedTicketQrSigner signer = new SignedTicketQrSigner("shared-signing-key");
    private final RecordingRepository repository = new RecordingRepository();
    private final CheckInScanService service = new CheckInScanService(repository, signer);

    @Test void signedQrUsesVerifiedTicketIdForCanonicalCheckIn() {
        var result = service.scanQr(EVENT, ACTOR, signer.buildEnvelope(TICKET, "Concert A"));
        assertEquals("SUCCESS", result.result());
        assertEquals(EVENT, repository.eventId);
        assertEquals(ACTOR, repository.actorId);
        assertEquals(TICKET, repository.ticketId);
        assertNull(repository.ticketCode);
    }

    @Test void alteredQrNeverCallsDatabase() {
        String valid = signer.buildEnvelope(TICKET, "Concert A");
        String altered = (valid.startsWith("A") ? "B" : "A") + valid.substring(1);
        CheckInException failure = assertThrows(CheckInException.class, () -> service.scanQr(EVENT, ACTOR, altered));
        assertEquals("INVALID_QR", failure.code());
        assertNull(repository.ticketId);
    }

    @Test void manualCodeGoesToExistingCheckInProcedure() {
        service.scanCode(EVENT, ACTOR, "TC-ABC123");
        assertEquals("TC-ABC123", repository.ticketCode);
        assertNull(repository.ticketId);
    }

    @Test void malformedManualCodeNeverCallsDatabase() {
        CheckInException failure = assertThrows(CheckInException.class, () -> service.scanCode(EVENT, ACTOR, "' OR 1=1"));
        assertEquals("INVALID_TICKET_CODE", failure.code());
        assertNull(repository.ticketCode);
    }

    private static final class RecordingRepository extends CheckInScanRepository {
        private UUID eventId, actorId, ticketId;
        private String ticketCode;
        @Override public ScanResult scanSignedTicket(UUID eventId, UUID actorId, UUID ticketId) {
            this.eventId = eventId; this.actorId = actorId; this.ticketId = ticketId;
            return new ScanResult("SUCCESS", Instant.EPOCH, ticketId);
        }
        @Override public ScanResult scanTicketCode(UUID eventId, UUID actorId, String ticketCode) {
            this.eventId = eventId; this.actorId = actorId; this.ticketCode = ticketCode;
            return new ScanResult("SUCCESS", Instant.EPOCH, TICKET);
        }
    }
}
