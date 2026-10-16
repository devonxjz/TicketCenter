package vn.ticketscenter.fulfillment.checkin.integration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import jakarta.persistence.*;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.checkin.*;
import vn.ticketscenter.fulfillment.checkin.cache.*;
import vn.ticketscenter.fulfillment.checkin.dao.TicketCheckInRepository;
import vn.ticketscenter.fulfillment.checkin.dto.*;
import vn.ticketscenter.fulfillment.checkin.security.*;
import vn.ticketscenter.fulfillment.checkin.service.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static vn.ticketscenter.fulfillment.checkin.integration.ScanFixture.*;
import static org.junit.jupiter.api.Assertions.*;
/** Required real SQL/JPA tests: missing configuration fails instead of reporting skipped acceptance. */
@EnabledIfEnvironmentVariable(named="TC_SCAN_INTEGRATION",matches="true")
public class TicketCheckInSqlServerTest {
    static EntityManagerFactory factory;
    public static final CheckInScanCache NO_CACHE=new CheckInScanCache(){public Lease lock(UUID id){return ()->{};}public void completed(UUID a,UUID k,CheckInScanResponseDto r){}};
    @BeforeAll static void open(){factory=factory();}
    @AfterAll static void close(){if(factory!=null)factory.close();}
    public static EntityManagerFactory factory(){
        return Persistence.createEntityManagerFactory("ticketscenter",Map.of("jakarta.persistence.jdbc.url",required("TC_SCAN_TEST_URL"),
            "jakarta.persistence.jdbc.user",required("TC_SCAN_CHECKIN_USER"),"jakarta.persistence.jdbc.password",required("TC_SCAN_CHECKIN_PASSWORD"),
            "hibernate.hbm2ddl.auto","none"));
    }
    public static TicketCheckInService service(EntityManagerFactory f){return new TicketCheckInService(
        new TicketCheckInRepository(new TransactionManager(Map.of(DatabasePrincipal.CHECK_IN,f))),
        new SignedQrDecoder(required("TC_TICKET_SIGNING_SECRET").getBytes(StandardCharsets.UTF_8)),NO_CACHE);}
    private CheckInScanResponseDto scan(ScanFixture f,UUID ticket)throws Exception{return service(factory).scan(f.staff,UUID.randomUUID(),new CheckInScanRequestDto(f.event,f.qr(ticket),null));}
    @Test void validSignedQrUpdatesTicketLogAndAuditAtomically()throws Exception{
        var f=new ScanFixture();var r=scan(f,f.tickets.getFirst());assertTrue(r.success());assertEquals("Khu A",r.zoneName());assertEquals("Khách mời Khánh 02",r.customerName());
        assertEquals("USED",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",r.ticketId()));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_ticket_check_in_logs WHERE ticket_id=? AND result='SUCCESS'",r.ticketId()));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_audit_logs WHERE aggregate_id=? AND action='TICKET_CHECKED_IN'",r.ticketId()));
    }
    @Test void replayAfterServiceRestartReturnsOriginalResultWithoutAnotherLog()throws Exception{
        var f=new ScanFixture();var key=UUID.randomUUID();var input=new CheckInScanRequestDto(f.event,f.qr(f.tickets.getFirst()),null);
        var first=service(factory).scan(f.staff,key,input);var replay=service(factory).scan(f.staff,key,input);assertEquals(first,replay);
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_ticket_check_in_logs WHERE event_id=?",f.event));
    }
    @Test void differentPayloadCannotReuseKey()throws Exception{
        var f=new ScanFixture();var key=UUID.randomUUID();service(factory).scan(f.staff,key,new CheckInScanRequestDto(f.event,f.qr(f.tickets.getFirst()),null));
        var e=assertThrows(CheckInException.class,()->service(factory).scan(f.staff,key,new CheckInScanRequestDto(f.event,f.qr(f.tickets.getLast()),null)));assertEquals("IDEMPOTENCY_CONFLICT",e.code());
        assertEquals("ACTIVE",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.tickets.getLast()));
    }
    @Test void secondScanRejectsUsedTicketWithOriginalCheckInTime()throws Exception{
        var f=new ScanFixture();var first=scan(f,f.tickets.getFirst());var second=scan(f,f.tickets.getFirst());
        assertEquals("ALREADY_CHECKED_IN",second.status());assertEquals(first.checkedInAt(),second.checkedInAt());assertEquals(409,second.httpStatus());
    }
    @Test void manualTicketCodeUsesSameAtomicPath()throws Exception{
        var f=new ScanFixture();var r=service(factory).scan(f.manager,UUID.randomUUID(),new CheckInScanRequestDto(f.event,null,"K02-"+f.tickets.getFirst()));assertTrue(r.success());
    }
    @Test void fakeSignatureIsLoggedWithoutTicketIdentity()throws Exception{
        var f=new ScanFixture();var r=service(factory).scan(f.staff,UUID.randomUUID(),new CheckInScanRequestDto(f.event,"forged.signature",null));
        assertEquals("INVALID_SIGNATURE",r.status());assertNull(r.ticketId());assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_ticket_check_in_logs WHERE event_id=? AND ticket_id IS NULL AND result='INVALID_CODE'",f.event));
    }
    @Test void wrongEventDoesNotLeakForeignTicketOrCustomer()throws Exception{
        var a=new ScanFixture();var b=new ScanFixture();var r=service(factory).scan(a.staff,UUID.randomUUID(),new CheckInScanRequestDto(a.event,b.qr(b.tickets.getFirst()),null));
        assertEquals("WRONG_EVENT",r.status());assertNull(r.ticketId());assertNull(r.customerName());assertNull(r.zoneName());
    }
    @Test void refundPendingRefundedAndInvalidatedTicketsRemainUnusable()throws Exception{
        for(String status:List.of("REFUND_PENDING","REFUNDED","INVALIDATED")){
            var f=new ScanFixture();try(var c=owner()){execute(c,"UPDATE dbo.tc_tickets SET status=? WHERE id=?",status,f.tickets.getFirst());}
            var r=scan(f,f.tickets.getFirst());assertFalse(r.success());assertEquals(status,scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.tickets.getFirst()));
        }
    }
    @Test void membershipRevocationBlocksEvenADurableSuccessfulReplay()throws Exception{
        var f=new ScanFixture();var key=UUID.randomUUID();var input=new CheckInScanRequestDto(f.event,f.qr(f.tickets.getFirst()),null);service(factory).scan(f.staff,key,input);
        try(var c=owner()){execute(c,"UPDATE dbo.tc_organization_memberships SET active=0 WHERE user_id=?",f.staff);}
        var e=assertThrows(CheckInException.class,()->service(factory).scan(f.staff,key,input));assertEquals(403,e.httpStatus());
    }
    @Test void outsidersAndDisabledStaffCannotScanOrReadHistory()throws Exception{
        var f=new ScanFixture();var input=new CheckInScanRequestDto(f.event,f.qr(f.tickets.getFirst()),null);
        assertEquals(403,assertThrows(CheckInException.class,()->service(factory).scan(f.other,UUID.randomUUID(),input)).httpStatus());
        assertEquals(403,assertThrows(CheckInException.class,()->service(factory).recent(f.other,f.event,20)).httpStatus());
        try(var c=owner()){execute(c,"UPDATE dbo.tc_users SET status='DISABLED' WHERE id=?",f.staff);}
        assertEquals(403,assertThrows(CheckInException.class,()->service(factory).scan(f.staff,UUID.randomUUID(),input)).httpStatus());
        assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_ticket_check_in_logs WHERE event_id=?",f.event));
    }
    @Test void cancelledAndEarlyAndEndedEventsRejectWithoutChangingTicket()throws Exception{
        for(String scenario:List.of("CANCELLED","EARLY","ENDED")){
            var f=new ScanFixture();try(var c=owner()){
                if(scenario.equals("CANCELLED"))execute(c,"UPDATE dbo.tc_events SET status='CANCELLED' WHERE id=?",f.event);
                if(scenario.equals("EARLY"))execute(c,"UPDATE dbo.tc_events SET start_time=DATEADD(hour,2,SYSUTCDATETIME()),end_time=DATEADD(hour,3,SYSUTCDATETIME()) WHERE id=?",f.event);
                if(scenario.equals("ENDED"))execute(c,"UPDATE dbo.tc_events SET start_time=DATEADD(hour,-2,SYSUTCDATETIME()),end_time=SYSUTCDATETIME() WHERE id=?",f.event);
            }
            assertFalse(scan(f,f.tickets.getFirst()).success());assertEquals("ACTIVE",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.tickets.getFirst()));
        }
    }
    @Test void historyNeverReturnsRawQrTicketCodeOrFinancialFields()throws Exception{
        var f=new ScanFixture();scan(f,f.tickets.getFirst());String json=service(factory).recent(f.staff,f.event,20).getFirst().toJson();
        for(String secret:List.of("qrContent","ticketCode","paid_amount","email","password","K02-"))assertFalse(json.contains(secret));
    }
    @Test void checkInPrincipalCannotDirtyWriteTicketsOrReadFinancialTotals()throws Exception{
        try(var c=connect("CHECKIN")){
            assertThrows(SQLException.class,()->execute(c,"UPDATE dbo.tc_tickets SET status='USED' WHERE 1=0"));
            assertThrows(SQLException.class,()->execute(c,"SELECT total_amount FROM dbo.tc_orders"));
        }
    }
    @Test void injectedFailureAfterTicketUpdateRollsBackTicketAuditLogAndRequest()throws Exception{
        var f=new ScanFixture();try(var c=owner()){execute(c,"CREATE OR ALTER TRIGGER dbo.trg_Khanh02_Fault ON dbo.tc_ticket_check_in_logs AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE event_id='"+f.event+"') THROW 53999,'Fixture fault after ticket update',1; END");}
        try{
            assertEquals(503,assertThrows(CheckInException.class,()->scan(f,f.tickets.getFirst())).httpStatus());
            assertEquals("ACTIVE",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.tickets.getFirst()));
            assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_ticket_check_in_logs WHERE event_id=?",f.event));
            assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_checkin_scan_requests WHERE event_id=?",f.event));
            assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_audit_logs WHERE aggregate_id=?",f.tickets.getFirst()));
        }finally{try(var c=owner()){execute(c,"DROP TRIGGER dbo.trg_Khanh02_Fault");}}
    }
}
