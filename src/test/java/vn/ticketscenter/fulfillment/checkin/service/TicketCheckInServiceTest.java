package vn.ticketscenter.fulfillment.checkin.service;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInScanCache;
import vn.ticketscenter.fulfillment.checkin.dto.*;
import vn.ticketscenter.fulfillment.checkin.repository.TicketCheckInRepository;
import vn.ticketscenter.fulfillment.checkin.security.SignedQrDecoder;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TicketCheckInServiceTest {
    static final UUID ACTOR=UUID.randomUUID(),EVENT=UUID.randomUUID(),TICKET=UUID.randomUUID(),KEY=UUID.randomUUID(),ORG=UUID.randomUUID();
    static final byte[] SECRET="fixture-ticket-signing-secret-32bytes".getBytes(StandardCharsets.UTF_8);
    private String state="CHECKED_IN_SUCCESS";private boolean invalid;private UUID found;private String welcome;private int writes,cached,closed;private RuntimeException failure;
    private final TicketCheckInRepository repository=new TicketCheckInRepository(null){
        @Override public CheckInScanResponseDto scan(UUID actor,UUID key,UUID event,UUID ticket,String code,String name,String fingerprint,boolean bad){
            writes++;assertEquals(ACTOR,actor);assertEquals(EVENT,event);assertEquals(KEY,key);assertTrue(fingerprint.matches("[0-9a-f]{64}"));
            found=ticket;welcome=name;invalid=bad;if(failure!=null)throw failure;
            return new CheckInScanResponseDto(state,TICKET,"TEST-CODE",name,"Khu A","A1",Instant.EPOCH,Instant.EPOCH,ORG);
        }};
    private final CheckInScanCache cache=new CheckInScanCache(){public Lease lock(UUID id){return ()->closed++;}public void completed(UUID a,UUID k,CheckInScanResponseDto r){cached++;}};
    private final TicketCheckInService service=new TicketCheckInService(repository,new SignedQrDecoder(SECRET),cache);
    @Test void authenticQrUsesUuidAndNameOnlyForDisplay()throws Exception{
        assertTrue(service.scan(ACTOR,KEY,new CheckInScanRequestDto(EVENT,qr(),null)).success());assertEquals(TICKET,found);assertEquals("Tên QR",welcome);assertFalse(invalid);assertEquals(1,cached);assertEquals(1,closed);
    }
    @Test void forgedQrCannotSupplyTicketIdButAuthorizedFailureIsLogged(){service.scan(ACTOR,KEY,new CheckInScanRequestDto(EVENT,"invalid.signature",null));assertNull(found);assertNull(welcome);assertTrue(invalid);}
    @Test void manualCodeUsesTheSameRepository(){service.scan(ACTOR,KEY,new CheckInScanRequestDto(EVENT,null,"TEST-CODE"));assertNull(found);assertFalse(invalid);}
    @Test void usedTicketIsRejectedWithOriginalTime(){state="ALREADY_CHECKED_IN";var r=service.scan(ACTOR,KEY,new CheckInScanRequestDto(EVENT,null,"TEST-CODE"));assertFalse(r.success());assertEquals(409,r.httpStatus());assertEquals(Instant.EPOCH,r.checkedInAt());}
    @Test void wrongEventIsRejected(){state="WRONG_EVENT";assertEquals(400,service.scan(ACTOR,KEY,new CheckInScanRequestDto(EVENT,null,"TEST-CODE")).httpStatus());}
    @Test void missingActorOrKeyCannotReachDatabase(){assertThrows(CheckInException.class,()->service.scan(null,KEY,new CheckInScanRequestDto(EVENT,null,"CODE")));assertThrows(CheckInException.class,()->service.scan(ACTOR,null,new CheckInScanRequestDto(EVENT,null,"CODE")));assertEquals(0,writes);}
    @Test void transactionFailureDoesNotPublishCacheAndReleasesOwnLock(){failure=new CheckInException(503,"UNAVAILABLE","Fixture");assertThrows(CheckInException.class,()->service.scan(ACTOR,KEY,new CheckInScanRequestDto(EVENT,null,"CODE")));assertEquals(0,cached);assertEquals(1,closed);}
    @Test void ambiguousOrOversizeInputsRejectedAndToStringRedactsQr(){assertThrows(CheckInException.class,()->new CheckInScanRequestDto(EVENT,"QR","CODE"));assertThrows(CheckInException.class,()->new CheckInScanRequestDto(EVENT,null,"x".repeat(121)));assertFalse(new CheckInScanRequestDto(EVENT,"private-signature",null).toString().contains("private-signature"));}
    @Test void historyBoundsRejectedBeforeRepository(){assertThrows(CheckInException.class,()->service.recent(ACTOR,EVENT,101));}
    private static String qr()throws Exception{byte[] bytes=("{\"id\":\""+TICKET+"\",\"name\":\"Tên QR\"}").getBytes(StandardCharsets.UTF_8);Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(SECRET,"HmacSHA256"));var b64=Base64.getUrlEncoder().withoutPadding();return b64.encodeToString(bytes)+"."+b64.encodeToString(mac.doFinal(bytes));}
}
