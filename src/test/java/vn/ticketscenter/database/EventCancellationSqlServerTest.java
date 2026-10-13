package vn.ticketscenter.database;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
import vn.ticketscenter.event.cancellation.dto.*;
import vn.ticketscenter.event.repository.EventCancellationRepository;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.config.worker.EventCancellationJob;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TC_CANCELLATION_SQL_TEST",matches="true")
public class EventCancellationSqlServerTest {
    static final String REASON="Thời tiết nguy hiểm, không thể tổ chức sự kiện.";
    record Fixture(SqlReportSafetyIT.Fixture base,UUID admin,UserPrincipal actor,UUID ticket) { }
    static void sql(String s,Object...a)throws Exception {try(var c=ScanFixture.owner()){ScanFixture.execute(c,s,a);}}
    static String value(String s,Object...a)throws Exception {return ScanFixture.scalar(s,a);}
    static Fixture fixture(boolean paid,boolean zero)throws Exception {
        var f=SqlReportSafetyIT.fixture();UUID admin=UUID.randomUUID();
        sql("UPDATE dbo.tc_users SET platform_role='CUSTOMER' WHERE id=?",f.user());
        sql("INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,email_verified_at,platform_role) VALUES(?,?,?,N'test-only',N'KHANH06 Admin',SYSUTCDATETIME(),'ADMIN')",admin,admin+"@fixture.invalid",admin.toString());
        if(paid) {
            if(zero) {
                sql("UPDATE dbo.tc_order_items SET unit_price=0 WHERE id=?;UPDATE dbo.tc_orders SET subtotal_amount=0,total_amount=0 WHERE id=?;UPDATE dbo.tc_payments SET status='FAILED' WHERE id=?",f.item(),f.order(),f.payment());
                new vn.ticketscenter.payment.repository.PaymentRepository(PaymentCrossDomainSqlTest.source("buyer",f.user()),PaymentCrossDomainSqlTest.source("worker",null)).completeZeroOrder(f.order(),f.user());
            }else SqlReportSafetyIT.capture(f,f.payment(),f.payment().toString());
        }
        var actor=new UserPrincipal(admin,PlatformRole.ADMIN,Integer.parseInt(value("SELECT auth_version FROM dbo.tc_users WHERE id=?",admin)));
        String ticket=paid?value("SELECT CONVERT(varchar(36),id) FROM dbo.tc_tickets WHERE order_item_id=?",f.item()):null;
        return new Fixture(f,admin,actor,ticket==null?null:UUID.fromString(ticket));
    }
    static EventCancellationRepository repository(Fixture f){return new EventCancellationRepository(PaymentCrossDomainSqlTest.source("admin",f.admin()));}
    static SqlReportSafetyIT.Fixture anotherOrder(Fixture f)throws Exception {
        var b=f.base();var next=new SqlReportSafetyIT.Fixture(b.user(),b.org(),b.category(),b.event(),b.zone(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        try(var c=ScanFixture.owner()) {c.setAutoCommit(false);
            ScanFixture.execute(c,"UPDATE dbo.tc_zones SET held_quantity=held_quantity+1 WHERE id=?",b.zone());
            ScanFixture.execute(c,"INSERT dbo.tc_ticket_holds(id,user_id,event_id,expires_at) VALUES(?,?,?,DATEADD(minute,10,SYSUTCDATETIME()))",next.hold(),b.user(),b.event());
            ScanFixture.execute(c,"INSERT dbo.tc_ticket_hold_items(hold_id,zone_id,quantity,unit_price) VALUES(?,?,1,1000)",next.hold(),b.zone());
            ScanFixture.execute(c,"INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount) VALUES(?,?,?,?,?,1000,0,1000)",next.order(),b.user(),b.event(),next.hold(),next.order().toString());
            ScanFixture.execute(c,"INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES(?,?,?,1,1000,N'A')",next.item(),next.order(),b.zone());
            ScanFixture.execute(c,"INSERT dbo.tc_payments(id,order_id,txn_ref,amount) VALUES(?,?,?,1000)",next.payment(),next.order(),next.payment().toString());c.commit();
        }
        SqlReportSafetyIT.capture(next,next.payment(),next.payment().toString());return next;
    }
    static CancellationProgressDto cancel(Fixture f)throws Exception{return repository(f).cancel(f.actor(),f.base().event(),new EventCancelRequestDto(REASON),UUID.randomUUID());}
    static void process(Fixture f)throws Exception {
        try(var c=PaymentCrossDomainSqlTest.source("worker",null).getConnection()) {
            try(var q=c.prepareStatement("EXEC dbo.sp_refund_adopt_event_cancellation ?,?")) {
                q.setString(1,f.base().event().toString());q.setString(2,f.base().order().toString());q.setQueryTimeout(20);
                boolean result=q.execute();while(true){if(result){try(var rows=q.getResultSet()){while(rows.next()){ }}}else if(q.getUpdateCount()==-1)break;result=q.getMoreResults();}
            }
            ScanFixture.execute(c,"EXEC dbo.sp_event_cancellation_finish");
        }
    }
    static String requested(Fixture f)throws Exception {
        try(var c=PaymentCrossDomainSqlTest.source("buyer",f.base().user()).getConnection()) {
            ScanFixture.execute(c,"DECLARE @id uniqueidentifier;EXEC dbo.sp_refund_request_for_tickets ?,?,?,?,@id OUTPUT,?,?",f.base().order(),f.base().user(),f.ticket().toString(),"Lý do khách hàng ban đầu",UUID.randomUUID(),0);
        }
        return value("SELECT CONVERT(varchar(36),id) FROM dbo.tc_refund_obligations WHERE order_id=? AND purpose='CUSTOMER_REFUND'",f.base().order());
    }
    @Test void CAN01StopsSalesAndReleasesPendingHoldImmediately()throws Exception {
        var f=fixture(false,false);var p=cancel(f);
        assertEquals("CANCELLED",value("SELECT status FROM dbo.tc_events WHERE id=?",f.base().event()));
        assertEquals("RELEASED",value("SELECT status FROM dbo.tc_ticket_holds WHERE id=?",f.base().hold()));
        assertEquals("CANCELLED",value("SELECT status FROM dbo.tc_orders WHERE id=?",f.base().order()));
        assertEquals("0",value("SELECT held_quantity FROM dbo.tc_zones WHERE id=?",f.base().zone()));
        assertEquals(0,p.totalOrders());process(f);assertEquals("COMPLETED",repository(f).progress(f.actor(),f.base().event()).status());
    }
    @Test void CAN02StartBoundaryRejectsWithoutSideEffects()throws Exception {
        var f=fixture(false,false);sql("UPDATE dbo.tc_events SET sale_start=DATEADD(day,-3,SYSUTCDATETIME()),sale_end=DATEADD(day,-2,SYSUTCDATETIME()),start_time=DATEADD(second,-1,SYSUTCDATETIME()) WHERE id=?",f.base().event());
        assertEquals(400,assertThrows(RefundException.class,()->cancel(f)).status());
        assertEquals("PUBLISHED",value("SELECT status FROM dbo.tc_events WHERE id=?",f.base().event()));
        assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_cancellation_jobs WHERE event_id=?",f.base().event()));
    }
    @Test void CAN03DifferentKeyConflictsAndLostResponseReplaysSameIntent()throws Exception {
        var f=fixture(true,false);var repo=repository(f);var key=UUID.randomUUID();var request=new EventCancelRequestDto(REASON);
        var first=repo.cancel(f.actor(),f.base().event(),request,key);
        assertEquals(first.cancelledAt(),repo.cancel(f.actor(),f.base().event(),request,key).cancelledAt());
        assertEquals(409,assertThrows(RefundException.class,()->repo.cancel(f.actor(),f.base().event(),new EventCancelRequestDto("Một lý do hủy khác hoàn toàn"),key)).status());
        assertEquals(409,assertThrows(RefundException.class,()->cancel(f)).status());
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_outbox WHERE event_type='EVENT_CANCELLED' AND aggregate_id=?",f.base().event()));
    }
    @Test void CAN04AdoptsOriginalObligationAndPreservesBuyerReason()throws Exception {
        var f=fixture(true,false);String original=requested(f);cancel(f);process(f);process(f);
        assertEquals(original,value("SELECT CONVERT(varchar(36),id) FROM dbo.tc_refund_obligations WHERE order_id=? AND purpose='CUSTOMER_REFUND'",f.base().order()));
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=? AND purpose='CUSTOMER_REFUND'",f.base().order()));
        assertEquals("EVENT_CANCELLATION",value("SELECT reason_type FROM dbo.tc_refund_obligations WHERE id=?",original));
        assertEquals("Lý do khách hàng ban đầu",value("SELECT reason FROM dbo.tc_refund_obligations WHERE id=?",original));
        assertEquals("PROCESSING",value("SELECT status FROM dbo.tc_refund_obligations WHERE id=?",original));
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=?",original));
    }
    @Test void zeroOrderCompletesWithoutPaymentOrTransfer()throws Exception {
        var f=fixture(true,true);cancel(f);process(f);
        assertEquals("COMPLETED",value("SELECT status FROM dbo.tc_refund_obligations WHERE order_id=? AND purpose='CUSTOMER_REFUND'",f.base().order()));
        assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refund_obligations r ON r.id=l.refund_id WHERE r.order_id=?",f.base().order()));
        assertEquals("REFUNDED",value("SELECT status FROM dbo.tc_tickets WHERE id=?",f.ticket()));
    }
    @Test void usedTicketIsAnExceptionAndNeverAutomaticallyRefunded()throws Exception {
        var f=fixture(true,false);sql("UPDATE dbo.tc_tickets SET status='USED' WHERE id=?",f.ticket());cancel(f);process(f);process(f);
        assertEquals("USED",value("SELECT status FROM dbo.tc_tickets WHERE id=?",f.ticket()));
        assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.base().order()));
        assertEquals(1,repository(f).progress(f.actor(),f.base().event()).usedTicketExceptions());
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_audit_logs WHERE aggregate_id=? AND action='CANCELLED_EVENT_USED_TICKET'",f.ticket()));
    }
    @Test void currentAdminDemotionDeniesEvenSavedReplay()throws Exception {
        var f=fixture(true,false);var key=UUID.randomUUID();var repo=repository(f);var request=new EventCancelRequestDto(REASON);
        repo.cancel(f.actor(),f.base().event(),request,key);sql("UPDATE dbo.tc_users SET platform_role='CUSTOMER' WHERE id=?",f.admin());
        assertEquals(403,assertThrows(RefundException.class,()->repo.cancel(f.actor(),f.base().event(),request,key)).status());
        assertEquals(403,assertThrows(RefundException.class,()->repo.progress(f.actor(),f.base().event())).status());
    }
    @Test void authVersionRevocationIsCheckedAfterWaitingForEvent()throws Exception {
        var f=fixture(true,false);var pool=Executors.newSingleThreadExecutor();
        try(var gate=ScanFixture.owner()) {gate.setAutoCommit(false);ScanFixture.execute(gate,"UPDATE dbo.tc_events SET version=version WHERE id=?",f.base().event());
            var started=new CountDownLatch(1);var future=pool.submit(()->{started.countDown();return assertThrows(RefundException.class,()->cancel(f));});
            assertTrue(started.await(3,TimeUnit.SECONDS));Thread.sleep(200);
            sql("UPDATE dbo.tc_users SET auth_version=auth_version+1 WHERE id=?",f.admin());gate.commit();
            assertEquals(401,future.get(15,TimeUnit.SECONDS).status());
        }finally{pool.shutdownNow();}
        assertEquals("PUBLISHED",value("SELECT status FROM dbo.tc_events WHERE id=?",f.base().event()));
    }
    @Test void twoConcurrentAdministratorsProduceOneJobAndOneConflict()throws Exception {
        var f=fixture(true,false);var other=fixture(false,false);var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        Callable<Integer> first=()->{barrier.await(5,TimeUnit.SECONDS);try{cancel(f);return 200;}catch(RefundException e){return e.status();}};
        Callable<Integer> second=()->{barrier.await(5,TimeUnit.SECONDS);try{repository(other).cancel(other.actor(),f.base().event(),new EventCancelRequestDto(REASON),UUID.randomUUID());return 200;}catch(RefundException e){return e.status();}};
        try{var a=pool.submit(first);var b=pool.submit(second);assertEquals(Set.of(200,409),Set.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS)));}
        finally{pool.shutdownNow();}
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_cancellation_jobs WHERE event_id=?",f.base().event()));
    }
    @Test void outerTransactionRollsBackStateQueueOutboxAndHold()throws Exception {
        var f=fixture(false,false);
        try(var c=PaymentCrossDomainSqlTest.source("admin",f.admin()).getConnection()) {c.setAutoCommit(false);
            ScanFixture.execute(c,"EXEC dbo.sp_event_cancel ?,?,?,?,?",f.base().event(),f.admin(),REASON,UUID.randomUUID(),f.actor().authVersion());c.rollback();}
        assertEquals("PUBLISHED",value("SELECT status FROM dbo.tc_events WHERE id=?",f.base().event()));
        assertEquals("ACTIVE",value("SELECT status FROM dbo.tc_ticket_holds WHERE id=?",f.base().hold()));
        assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_cancellation_jobs WHERE event_id=?",f.base().event()));
    }
    @Test void adoptionFailureRollsBackObligationAndCursor()throws Exception {
        var f=fixture(true,false);cancel(f);String name="k06_fail_"+f.base().event().toString().replace("-","");
        sql("CREATE TRIGGER dbo."+name+" ON dbo.tc_cancellation_orders AFTER UPDATE AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE event_id='"+f.base().event()+"') THROW 53998,'Injected after adoption',1;END");
        try{assertThrows(SQLException.class,()->process(f));assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.base().order()));
            assertEquals("0",value("SELECT processed_orders FROM dbo.tc_cancellation_jobs WHERE event_id=?",f.base().event()));}
        finally{sql("DROP TRIGGER dbo."+name);}
        process(f);assertEquals("1",value("SELECT processed_orders FROM dbo.tc_cancellation_jobs WHERE event_id=?",f.base().event()));
    }
    @Test void workerRaceAndRepeatedBatchesDoNotDuplicateTransfer()throws Exception {
        var f=fixture(true,false);cancel(f);var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        Callable<Void> work=()->{barrier.await(5,TimeUnit.SECONDS);process(f);return null;};
        try{var a=pool.submit(work);var b=pool.submit(work);a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        assertEquals("1",value("SELECT processed_orders FROM dbo.tc_cancellation_jobs WHERE event_id=?",f.base().event()));
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refund_obligations r ON r.id=l.refund_id WHERE r.order_id=?",f.base().order()));
    }
    @Test void CAN05RealJpaWorkerRestartResumesDurableCursor()throws Exception {
        var f=fixture(true,false);var second=anotherOrder(f);cancel(f);
        var delegate=PaymentCrossDomainSqlTest.source("worker",null);
        var source=(javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(javax.sql.DataSource.class.getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(p,m,a)->{
            if(m.getName().equals("toString"))return "KHANH06 restricted fixture worker";
            if(m.getName().equals("getConnection"))return delegate.getConnection();
            if(m.getName().equals("isWrapperFor"))return false;
            throw new UnsupportedOperationException(m.getName());
        });
        try(var factory=jakarta.persistence.Persistence.createEntityManagerFactory("ticketscenter",Map.of("jakarta.persistence.nonJtaDataSource",source,"hibernate.hbm2ddl.auto","none"))) {
            var tx=new TransactionManager(Map.of(DatabasePrincipal.WORKER,factory));
            var repo=new EventCancellationRepository(){@Override public List<Object> findWork(jakarta.persistence.EntityManager em,int size) {
                return super.findWork(em,1000).stream().filter(item->((Work)item).eventId().equals(f.base().event())).limit(size).toList();
            }};
            assertEquals(1,new EventCancellationJob(tx,repo,1).runOnce());
            var partial=repository(f).progress(f.actor(),f.base().event());assertEquals(1,partial.processedOrders());assertEquals("IN_PROGRESS",partial.status());
            assertEquals(1,new EventCancellationJob(tx,repo,1).runOnce());
            assertEquals(0,new EventCancellationJob(tx,repo,1).runOnce());
        }
        var p=repository(f).progress(f.actor(),f.base().event());assertEquals(2,p.processedOrders());assertEquals("COMPLETED",p.status());assertTrue(Set.of(f.base().order(),second.order()).contains(p.lastProcessedOrderId()));
    }
    @Test void runtimePrincipalsCannotReadOrWriteQueueOrCallOtherPrincipalProcedure()throws Exception {
        var f=fixture(true,false);cancel(f);
        for(String role:List.of("buyer","worker","admin"))try(var c=PaymentCrossDomainSqlTest.source(role,role.equals("admin")?f.admin():null).getConnection()) {
            assertThrows(SQLException.class,()->ScanFixture.execute(c,"UPDATE dbo.tc_cancellation_jobs SET processed_orders=0 WHERE event_id=?",f.base().event()));
            assertThrows(SQLException.class,()->ScanFixture.execute(c,"SELECT * FROM dbo.tc_cancellation_receipts"));
        }
        try(var c=PaymentCrossDomainSqlTest.source("buyer",f.base().user()).getConnection()) {
            assertThrows(SQLException.class,()->ScanFixture.execute(c,"EXEC dbo.sp_event_cancel ?,?,?,?,?",f.base().event(),f.admin(),REASON,UUID.randomUUID(),0));
            assertThrows(SQLException.class,()->ScanFixture.execute(c,"EXEC dbo.sp_refund_adopt_event_cancellation ?,?",f.base().event(),f.base().order()));
        }
    }
    @Test void lateCaptureAfterCancellationCompensatesWithoutIssuingTickets()throws Exception {
        var f=fixture(false,false);cancel(f);SqlReportSafetyIT.capture(f.base(),f.base().payment(),f.base().payment().toString());
        assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_tickets WHERE order_item_id=?",f.base().item()));
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=? AND purpose='PAYMENT_COMPENSATION'",f.base().order()));
    }
    @Test void approvedUnknownObligationKeepsAttemptAndOriginalTicketSet()throws Exception {
        var f=fixture(true,false);String refund=requested(f);
        try(var c=PaymentCrossDomainSqlTest.source("admin",f.admin()).getConnection()) {ScanFixture.execute(c,"EXEC dbo.sp_refund_approve ?,?,?,?,?",refund,f.admin(),"Approved before event cancellation",UUID.randomUUID(),f.actor().authVersion());}
        String attempt=value("SELECT CONVERT(varchar(36),current_attempt_id) FROM dbo.tc_refund_obligations WHERE id=?",refund);
        try(var c=PaymentCrossDomainSqlTest.source("worker",null).getConnection()){ScanFixture.execute(c,"EXEC dbo.usp_ApplyRefundResult ?,?",attempt,"{\"status\":\"UNKNOWN\",\"providerReference\":\"K06-UNKNOWN\"}");}
        cancel(f);process(f);
        assertEquals(attempt,value("SELECT CONVERT(varchar(36),current_attempt_id) FROM dbo.tc_refund_obligations WHERE id=?",refund));
        assertEquals("NEEDS_RECONCILIATION",value("SELECT status FROM dbo.tc_refund_obligations WHERE id=?",refund));
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.base().order()));
        assertEquals(1,repository(f).progress(f.actor(),f.base().event()).unknownRefunds());
    }
    @Test void missingEventReturns404WithoutCreatingAJob()throws Exception {
        var f=fixture(false,false);var id=UUID.randomUUID();
        assertEquals(404,assertThrows(RefundException.class,()->repository(f).cancel(f.actor(),id,new EventCancelRequestDto(REASON),UUID.randomUUID())).status());
        assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_cancellation_jobs WHERE event_id=?",id));
    }
    @Test void oneKeyCannotCancelAnotherEvent()throws Exception {
        var f=fixture(false,false);var other=fixture(false,false);var key=UUID.randomUUID();var repo=repository(f);
        repo.cancel(f.actor(),f.base().event(),new EventCancelRequestDto(REASON),key);
        assertEquals(409,assertThrows(RefundException.class,()->repo.cancel(f.actor(),other.base().event(),new EventCancelRequestDto(REASON),key)).status());
        assertEquals("PUBLISHED",value("SELECT status FROM dbo.tc_events WHERE id=?",other.base().event()));
    }
    @Test void cancellationReleasesTheAllocatedSeatAndReservedCouponExactlyOnce()throws Exception {
        var f=fixture(false,false);var b=f.base();UUID seat=UUID.randomUUID(),coupon=UUID.randomUUID();
        sql("UPDATE dbo.tc_events SET status='DRAFT' WHERE id=?",b.event());
        sql("UPDATE dbo.tc_zones SET type='SEATED',capacity=NULL,held_quantity=0 WHERE id=?",b.zone());
        sql("INSERT dbo.tc_seats(id,zone_id,row_name,seat_number,label,status) VALUES(?,?,'A',1,'A1','HELD')",seat,b.zone());
        sql("UPDATE dbo.tc_ticket_hold_items SET seat_id=? WHERE hold_id=?;UPDATE dbo.tc_order_items SET seat_id=? WHERE id=?",seat,b.hold(),seat,b.item());
        sql("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?",b.event());
        sql("INSERT dbo.tc_coupons(id,organization_id,code,discount_type,percentage_value,max_uses,valid_from,valid_to) VALUES(?,?,?,'PERCENTAGE',10,1,DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()))",coupon,b.org(),coupon.toString());
        sql("UPDATE dbo.tc_payments SET status='FAILED' WHERE id=?",b.payment());
        sql("EXEC dbo.usp_ApplyOrderCoupon ?,?,?",b.order(),b.user(),coupon.toString());
        assertEquals("RESERVED",value("SELECT status FROM dbo.tc_coupon_redemptions WHERE order_id=?",b.order()));
        cancel(f);process(f);process(f);
        assertEquals("AVAILABLE",value("SELECT status FROM dbo.tc_seats WHERE id=?",seat));
        assertEquals("RELEASED",value("SELECT status FROM dbo.tc_coupon_redemptions WHERE order_id=?",b.order()));
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE order_id=?",b.order()));
    }
    static Fixture twoTicketOrder()throws Exception {
        var f=fixture(false,false);var b=f.base();
        sql("UPDATE dbo.tc_zones SET held_quantity=2 WHERE id=?;UPDATE dbo.tc_ticket_hold_items SET quantity=2 WHERE hold_id=?;UPDATE dbo.tc_order_items SET quantity=2 WHERE id=?",b.zone(),b.hold(),b.item());
        sql("UPDATE dbo.tc_orders SET subtotal_amount=2000,total_amount=2000 WHERE id=?;UPDATE dbo.tc_payments SET amount=2000 WHERE id=?",b.order(),b.payment());
        String codes="[{\"ticketCode\":\""+UUID.randomUUID()+"\",\"qrSecretHashHex\":\"01020304\"},{\"ticketCode\":\""+UUID.randomUUID()+"\",\"qrSecretHashHex\":\"01020304\"}]";
        try(var c=ScanFixture.owner();var q=c.prepareStatement("EXEC dbo.usp_ApplyPaymentResult @order_id=?,@payment_id=?,@verified_result=?,@ticket_codes=?")) {
            q.setString(1,b.order().toString());q.setString(2,b.payment().toString());q.setString(3,"{\"status\":\"CAPTURED\",\"providerReference\":\""+b.payment()+"\"}");q.setString(4,codes);q.setQueryTimeout(20);
            boolean result=q.execute();while(true){if(result){try(var r=q.getResultSet()){while(r.next()){ }}}else if(q.getUpdateCount()==-1)break;result=q.getMoreResults();}
        }
        return new Fixture(b,f.admin(),f.actor(),UUID.fromString(value("SELECT TOP(1) CONVERT(varchar(36),id) FROM dbo.tc_tickets WHERE order_item_id=? ORDER BY id",b.item())));
    }
    @Test void partialRequestedRefundIsAdoptedAndOnlyRemainingTicketGetsNewObligation()throws Exception {
        var f=twoTicketOrder();String original=requested(f);cancel(f);process(f);process(f);
        assertEquals("1000",value("SELECT amount FROM dbo.tc_refund_obligations WHERE id=?",original));
        assertEquals("EVENT_CANCELLATION",value("SELECT reason_type FROM dbo.tc_refund_obligations WHERE id=?",original));
        assertEquals("2",value("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=? AND purpose='CUSTOMER_REFUND'",f.base().order()));
        assertEquals("2000",value("SELECT SUM(amount) FROM dbo.tc_refund_obligations WHERE order_id=? AND purpose='CUSTOMER_REFUND'",f.base().order()));
        assertEquals("2",value("SELECT COUNT(*) FROM dbo.tc_refund_tickets rt JOIN dbo.tc_refund_obligations r ON r.id=rt.refund_id WHERE r.order_id=?",f.base().order()));
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_tickets WHERE ticket_id=? AND is_open=1",f.ticket()));
    }
    @Test void approvedPartialRefundRetainsItsAttemptAndRemainingTicketGetsCancellationRefund()throws Exception {
        var f=twoTicketOrder();String original=requested(f);
        try(var c=PaymentCrossDomainSqlTest.source("admin",f.admin()).getConnection()){ScanFixture.execute(c,"EXEC dbo.sp_refund_approve ?,?,?,?,?",original,f.admin(),"Approved partial refund",UUID.randomUUID(),f.actor().authVersion());}
        String attempt=value("SELECT CONVERT(varchar(36),current_attempt_id) FROM dbo.tc_refund_obligations WHERE id=?",original);
        cancel(f);process(f);process(f);
        assertEquals(attempt,value("SELECT CONVERT(varchar(36),current_attempt_id) FROM dbo.tc_refund_obligations WHERE id=?",original));
        assertEquals("CUSTOMER_REQUEST",value("SELECT reason_type FROM dbo.tc_refund_obligations WHERE id=?",original));
        assertEquals("2",value("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.base().order()));
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=?",original));
        assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=? AND reason_type='EVENT_CANCELLATION'",f.base().order()));
    }
}
