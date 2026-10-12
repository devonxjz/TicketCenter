package vn.ticketscenter.fulfillment.checkin.integration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ticketscenter.fulfillment.checkin.dto.*;
import java.util.*;
import java.util.concurrent.*;
import static vn.ticketscenter.fulfillment.checkin.integration.ScanFixture.*;
import static vn.ticketscenter.fulfillment.checkin.integration.TicketCheckInSqlServerTest.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="TC_SCAN_INTEGRATION",matches="true")
class CheckInConcurrencyTest {
    @Test void twoStaffAndTwoRealConnectionsProduceExactlyOneSuccess()throws Exception{
        var f=new ScanFixture();try(var em=factory();var pool=Executors.newFixedThreadPool(2)){
            var barrier=new CyclicBarrier(2);var input=new CheckInScanRequestDto(f.event,f.qr(f.tickets.getFirst()),null);
            var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return service(em).scan(f.staff,UUID.randomUUID(),input);});
            var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return service(em).scan(f.manager,UUID.randomUUID(),input);});
            var results=List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(CheckInScanResponseDto::success).count());assertEquals(1,results.stream().filter(r->r.status().equals("ALREADY_CHECKED_IN")).count());
            assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_ticket_check_in_logs WHERE ticket_id=? AND result='SUCCESS'",f.tickets.getFirst()));
        }
    }
    @Test void simultaneousIdenticalRequestReplaysOneMutation()throws Exception{
        var f=new ScanFixture();try(var em=factory();var pool=Executors.newFixedThreadPool(2)){
            var barrier=new CyclicBarrier(2);var key=UUID.randomUUID();var input=new CheckInScanRequestDto(f.event,f.qr(f.tickets.getFirst()),null);
            Callable<CheckInScanResponseDto> work=()->{barrier.await(10,TimeUnit.SECONDS);return service(em).scan(f.staff,key,input);};
            var a=pool.submit(work);var b=pool.submit(work);assertEquals(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS));
            assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_ticket_check_in_logs WHERE event_id=?",f.event));
        }
    }
}
