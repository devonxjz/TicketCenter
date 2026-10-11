package vn.ticketscenter.database;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.repository.AdminRefundRepository;
import vn.ticketscenter.fulfillment.refund.service.*;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TC_ADMIN_REFUND_TEST",matches="true")
public class AdminRefundDecisionSqlServerTest {
    public record Case(UUID admin,UUID event,UUID order,UUID ticket,UUID refund,UUID zone) {
        public UserPrincipal actor(){return new UserPrincipal(admin,PlatformRole.ADMIN,0);}
        public AdminRefundRepository repository(){return new AdminRefundRepository(source("admin",admin));}
        public AdminRefundDecisionService service(){return new AdminRefundDecisionService(repository(),(a,k,r)->{});}
    }
    public static Case fixture(boolean free)throws Exception {
        var f=SqlReportSafetyIT.fixture();
        if(free)try(var c=ScanFixture.owner()) {
            ScanFixture.execute(c,"UPDATE dbo.tc_order_items SET unit_price=0 WHERE id=?",f.item());
            ScanFixture.execute(c,"UPDATE dbo.tc_orders SET subtotal_amount=0,total_amount=0 WHERE id=?",f.order());
            ScanFixture.execute(c,"UPDATE dbo.tc_payments SET status='FAILED' WHERE id=?",f.payment());
            ScanFixture.execute(c,"EXEC sys.sp_set_session_context @key=N'actor_id',@value=?",f.user().toString());
            ScanFixture.execute(c,"EXECUTE AS USER = 'sql_report_buyer'");
            ScanFixture.execute(c,"EXEC dbo.usp_CompleteFreeOrder ?,?,?",f.order(),f.user(),"[{\"ticketCode\":\""+UUID.randomUUID()+"\",\"qrSecretHashHex\":\"01020304\"}]");
        }else SqlReportSafetyIT.capture(f,f.payment(),f.payment().toString());
        UUID ticket=UUID.fromString(scalar("SELECT CONVERT(varchar(36),id) FROM dbo.tc_tickets WHERE order_item_id=?",f.item()));
        try(var c=source("buyer",f.user()).getConnection()) {
            ScanFixture.execute(c,"DECLARE @id uniqueidentifier; EXEC dbo.sp_refund_request_for_tickets ?,?,?,?,@id OUTPUT,?,0",f.order(),f.user(),ticket.toString(),"Khách yêu cầu hoàn vé",UUID.randomUUID());
        }
        UUID refund=UUID.fromString(scalar("SELECT CONVERT(varchar(36),id) FROM dbo.tc_refund_obligations WHERE order_id=? AND purpose='CUSTOMER_REFUND'",f.order()));
        return new Case(f.user(),f.event(),f.order(),ticket,refund,f.zone());
    }
    public static DataSource source(String role,UUID actor){return PaymentCrossDomainSqlTest.source(role,actor);}
    private static String scalar(String sql,Object...args)throws Exception{return ScanFixture.scalar(sql,args);}
    private static AdminRefundMutationDto approve(Case f,UUID key){return f.service().decide(f.actor(),f.refund(),new AdminRefundDecisionRequestDto("APPROVE","Đã xác minh",null),key);}
    private static AdminRefundMutationDto reject(Case f,UUID key){return f.service().decide(f.actor(),f.refund(),new AdminRefundDecisionRequestDto("REJECT",null,"Không đủ điều kiện"),key);}
    public static void outcome(UUID attempt,String result)throws Exception{
        try(var c=source("worker",null).getConnection()){ScanFixture.execute(c,"EXEC dbo.usp_ApplyRefundResult ?,?",attempt,"{\"status\":\""+result+"\",\"providerReference\":\"REF-"+attempt+"\"}");}
    }
    @Test void dec01ApproveQueuesOneAttemptAndKeepsTicketsPending()throws Exception{
        var f=fixture(false);var key=UUID.randomUUID();var first=approve(f,key);var replay=approve(f,key);
        assertEquals(first,replay);assertEquals("APPROVED",first.status());assertEquals("PROCESSING",first.canonicalStatus());assertNotNull(first.attemptId());
        assertEquals("REFUND_PENDING",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.ticket()));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_refunds WHERE refund_request_id=(SELECT legacy_request_id FROM dbo.tc_refund_obligations WHERE id=?)",f.refund()));
        var detail=f.service().detail(f.actor(),f.refund());assertEquals("Đã xác minh",detail.adminNote());assertEquals(1,detail.attempts().size());assertFalse(detail.refund().canRetry());
        outcome(first.attemptId(),"SUCCEEDED");assertEquals("REFUNDED",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.ticket()));
        assertEquals("0",scalar("SELECT sold_quantity FROM dbo.tc_zones WHERE id=?",f.zone()));
    }
    @Test void dec02RejectRestoresActiveTicketAndRecordsRequiredReason()throws Exception{
        var f=fixture(false);var key=UUID.randomUUID();assertEquals(reject(f,key),reject(f,key));
        assertEquals("ACTIVE",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.ticket()));
        var detail=f.service().detail(f.actor(),f.refund());assertEquals("REJECTED",detail.refund().status());assertEquals("Không đủ điều kiện",detail.refund().rejectionReason());assertTrue(detail.attempts().isEmpty());
    }
    @Test void cancelledEventNeverRestoresValidTicket()throws Exception{
        var f=fixture(false);try(var c=ScanFixture.owner()){ScanFixture.execute(c,"UPDATE dbo.tc_events SET status='CANCELLED' WHERE id=?",f.event());}
        reject(f,UUID.randomUUID());assertEquals("INVALIDATED",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.ticket()));
    }
    @Test void dec03DatabaseRejectsBlankReasonAndRollsBack()throws Exception{
        var f=fixture(false);try(var c=source("admin",f.admin()).getConnection()){
            assertThrows(SQLException.class,()->ScanFixture.execute(c,"EXEC dbo.sp_refund_reject ?,?,N' ',?,0",f.refund(),f.admin(),UUID.randomUUID()));
        }
        assertEquals("REQUESTED",f.repository().detail(f.refund()).refund().status());assertEquals("REFUND_PENDING",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.ticket()));
    }
    @Test void dec04BuyerPrincipalCannotInvokeAdminFacadeOrReadAdminView()throws Exception{
        var f=fixture(false);try(var c=source("buyer",f.admin()).getConnection()){
            assertThrows(SQLException.class,()->ScanFixture.execute(c,"SELECT * FROM dbo.v_admin_refund_requests"));
            assertThrows(SQLException.class,()->ScanFixture.execute(c,"EXEC dbo.sp_refund_approve ?,?,NULL,?,0",f.refund(),f.admin(),UUID.randomUUID()));
        }
        assertEquals("REQUESTED",f.repository().detail(f.refund()).refund().status());
    }
    @Test void dec05RetryOnlyAfterVerifiedFailureKeepsObligationAndCreatesNewAttempt()throws Exception{
        var f=fixture(false);assertEquals(409,assertThrows(RefundException.class,()->f.service().retry(f.actor(),f.refund(),new AdminRefundRetryRequestDto(UUID.randomUUID()))).status());
        var first=approve(f,UUID.randomUUID());assertEquals(409,assertThrows(RefundException.class,()->f.service().retry(f.actor(),f.refund(),new AdminRefundRetryRequestDto(UUID.randomUUID()))).status());
        outcome(first.attemptId(),"FAILED");assertTrue(f.repository().detail(f.refund()).refund().canRetry());
        var key=UUID.randomUUID();var next=f.service().retry(f.actor(),f.refund(),new AdminRefundRetryRequestDto(key));
        assertEquals(next,f.service().retry(f.actor(),f.refund(),new AdminRefundRetryRequestDto(key)));assertEquals(f.refund(),next.refundId());assertNotEquals(first.attemptId(),next.attemptId());
        assertEquals(2,f.repository().detail(f.refund()).attempts().size());assertEquals("REFUND_PENDING",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.ticket()));
    }
    @Test void unknownOrReconciliationNeverAllowsRetry()throws Exception{
        var f=fixture(false);var first=approve(f,UUID.randomUUID());outcome(first.attemptId(),"UNKNOWN");
        assertFalse(f.repository().detail(f.refund()).refund().canRetry());
        assertEquals(409,assertThrows(RefundException.class,()->f.service().retry(f.actor(),f.refund(),new AdminRefundRetryRequestDto(UUID.randomUUID()))).status());
        assertEquals(1,f.repository().detail(f.refund()).attempts().size());
    }
    @Test void changedPayloadWithSameKeyConflictsWithoutSecondDecision()throws Exception{
        var f=fixture(false);var key=UUID.randomUUID();approve(f,key);
        assertEquals(409,assertThrows(RefundException.class,()->reject(f,key)).status());assertEquals(1,f.repository().detail(f.refund()).attempts().size());
    }
    @Test void outerTransactionRollbackUndoesDecisionAttemptAuditAndReceipt()throws Exception{
        var f=fixture(false);var key=UUID.randomUUID();try(var c=source("admin",f.admin()).getConnection()){
            c.setAutoCommit(false);ScanFixture.execute(c,"EXEC dbo.sp_refund_approve ?,?,N'test',?,0",f.refund(),f.admin(),key);c.rollback();
        }
        assertEquals("REQUESTED",f.repository().detail(f.refund()).refund().status());
        assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_request_idempotency WHERE actor_id=? AND operation='ADMIN_REFUND_DECISION'",f.admin()));
        assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_audit_logs WHERE aggregate_id=? AND action='ADMIN_REFUND_APPROVE'",f.refund()));
    }
    @Test void currentRoleAndAuthVersionRecheckedEvenWithCachedPrincipal()throws Exception{
        var f=fixture(false);try(var c=ScanFixture.owner()){ScanFixture.execute(c,"DELETE dbo.tc_user_platform_roles WHERE user_id=? AND role='ADMIN'",f.admin());}
        assertEquals(403,assertThrows(RefundException.class,()->approve(f,UUID.randomUUID())).status());
        try(var c=ScanFixture.owner()){ScanFixture.execute(c,"INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?,'ADMIN')",f.admin());ScanFixture.execute(c,"UPDATE dbo.tc_users SET auth_version=auth_version+1 WHERE id=?",f.admin());}
        assertEquals(401,assertThrows(RefundException.class,()->approve(f,UUID.randomUUID())).status());assertEquals("REQUESTED",f.repository().detail(f.refund()).refund().status());
    }
    @Test void zeroAmountCompletesWithoutGatewayAttemptAndReturnsInventory()throws Exception{
        var f=fixture(true);var result=approve(f,UUID.randomUUID());assertEquals("COMPLETED",result.canonicalStatus());assertNull(result.attemptId());
        assertTrue(f.repository().detail(f.refund()).attempts().isEmpty());assertEquals("REFUNDED",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.ticket()));
        assertEquals("0",scalar("SELECT sold_quantity FROM dbo.tc_zones WHERE id=?",f.zone()));
    }
    @Test void filteredPaginationAndDetailUseRealProjection()throws Exception{
        var f=fixture(false);reject(f,UUID.randomUUID());var page=f.service().list(f.actor(),"REJECTED",1,1);
        assertTrue(page.total()>0);assertEquals(1,page.items().size());assertEquals("REJECTED",page.items().getFirst().status());
        var detail=f.repository().detail(f.refund());assertEquals("1000",detail.refund().totalAmount());assertEquals(1,detail.refund().ticketCount());assertEquals(f.ticket(),detail.tickets().getFirst().ticketId());
    }
    @Test void competingApproveRejectAndRetryCreateAtMostOneNewAttempt()throws Exception{
        var f=fixture(false);var second=fixture(false);
        race(()->approve(f,UUID.randomUUID()),()->second.service().decide(second.actor(),f.refund(),new AdminRefundDecisionRequestDto("REJECT",null,"Không đủ điều kiện"),UUID.randomUUID()));
        var detail=f.repository().detail(f.refund());assertTrue(detail.attempts().size()<=1);
        var retry=fixture(false);var first=approve(retry,UUID.randomUUID());
        outcome(first.attemptId(),"FAILED");race(()->retry.service().retry(retry.actor(),retry.refund(),new AdminRefundRetryRequestDto(UUID.randomUUID())),()->retry.service().retry(retry.actor(),retry.refund(),new AdminRefundRetryRequestDto(UUID.randomUUID())));
        assertEquals(2,retry.repository().detail(retry.refund()).attempts().size());
    }
    private static void race(Callable<?> a,Callable<?> b)throws Exception{
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try{var x=pool.submit(()->run(a,ready,start));var y=pool.submit(()->run(b,ready,start));assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            var statuses=List.of(x.get(25,TimeUnit.SECONDS),y.get(25,TimeUnit.SECONDS));assertEquals(1,statuses.stream().filter(s->s==200).count());assertEquals(1,statuses.stream().filter(s->s==409).count());
        }finally{pool.shutdownNow();}
    }
    private static int run(Callable<?> action,CountDownLatch ready,CountDownLatch start)throws Exception{ready.countDown();start.await();try{action.call();return 200;}catch(RefundException error){return error.status();}}
    @Test void eventWaitDoesNotHoldAdminUserLockAndRevocationIsObserved()throws Exception{
        var f=fixture(false);var pool=Executors.newSingleThreadExecutor();var ready=new CountDownLatch(1);
        try(var gate=ScanFixture.owner()){gate.setAutoCommit(false);ScanFixture.execute(gate,"UPDATE dbo.tc_events SET version=version WHERE id=?",f.event());
            var mutation=pool.submit(()->{ready.countDown();return assertThrows(RefundException.class,()->approve(f,UUID.randomUUID())).status();});
            assertTrue(ready.await(5,TimeUnit.SECONDS));Thread.sleep(600);
            ScanFixture.execute(gate,"UPDATE dbo.tc_users SET auth_version=auth_version+1 WHERE id=?",f.admin());gate.commit();assertEquals(401,mutation.get(20,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals("REQUESTED",f.repository().detail(f.refund()).refund().status());
    }
    @Test void failureAfterAttemptCreationRollsBackEntireDecision()throws Exception{
        var f=fixture(false);String trigger="trg_K04_Failure_"+f.refund().toString().replace("-","");
        try(var owner=ScanFixture.owner()){
            ScanFixture.execute(owner,"CREATE TRIGGER dbo."+trigger+" ON dbo.tc_audit_logs AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE aggregate_id='"+f.refund()+"' AND action='ADMIN_REFUND_APPROVE') THROW 54309,'Injected fixture failure',1; END;");
            try{assertThrows(RefundException.class,()->approve(f,UUID.randomUUID()));}
            finally{ScanFixture.execute(owner,"DROP TRIGGER dbo."+trigger);}
        }
        assertEquals("REQUESTED",f.repository().detail(f.refund()).refund().status());assertTrue(f.repository().detail(f.refund()).attempts().isEmpty());
        assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_request_idempotency WHERE actor_id=? AND operation='ADMIN_REFUND_DECISION'",f.admin()));
    }
}
