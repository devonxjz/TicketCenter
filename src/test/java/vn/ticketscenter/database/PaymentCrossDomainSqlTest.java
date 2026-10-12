package vn.ticketscenter.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
import vn.ticketscenter.payment.repository.PaymentRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TC_SQL_REPORT_TEST",matches="true")
class PaymentCrossDomainSqlTest {
    private static java.util.TimeZone originalTimeZone;
    @org.junit.jupiter.api.BeforeAll static void nonUtcRuntime() {
        originalTimeZone=java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Bangkok"));
    }
    @org.junit.jupiter.api.AfterAll static void restoreRuntimeTimeZone() { java.util.TimeZone.setDefault(originalTimeZone); }
    @Test void expiredHoldReplacementThroughRealFacadesCanCheckoutAndPay() throws Exception {
        var f=SqlReportSafetyIT.fixture();
        try(var c=ScanFixture.owner()) {
            ScanFixture.execute(c,"UPDATE dbo.tc_organizations SET status='APPROVED' WHERE id=?",f.org());
            ScanFixture.execute(c,"DELETE dbo.tc_payments WHERE order_id=?",f.order());
            ScanFixture.execute(c,"DELETE dbo.tc_order_items WHERE order_id=?",f.order());
            ScanFixture.execute(c,"DELETE dbo.tc_orders WHERE id=?",f.order());
            ScanFixture.execute(c,"UPDATE dbo.tc_ticket_holds SET expires_at=DATEADD(minute,-1,SYSUTCDATETIME()),created_at=DATEADD(minute,-11,SYSUTCDATETIME()) WHERE id=?",f.hold());
        }
        int version=Integer.parseInt(ScanFixture.scalar("SELECT auth_version FROM dbo.tc_users WHERE id=?",f.user()));
        var buyer=source("buyer",f.user());var worker=source("worker",null);
        var holds=new vn.ticketscenter.ticketing.repository.HoldRepository(buyer,worker);
        var command=new vn.ticketscenter.ticketing.dto.HoldCommand.Create(UUID.randomUUID(),
                new vn.ticketscenter.ticketing.dto.CreateHoldRequest(f.event(),java.util.List.of(
                        new vn.ticketscenter.ticketing.dto.HoldSelection(f.zone(),null,1))));
        var actor=new vn.ticketscenter.ticketing.dto.HoldActor(f.user(),version);
        var receipt=holds.mutate(actor,command);
        assertEquals(receipt.body(),holds.mutate(actor,command).body());
        var hold=holds.current(actor).projection().orElseThrow();
        var order=new vn.ticketscenter.order.repository.OrderRepository(buyer).mutate(
                new vn.ticketscenter.order.dto.OrderActor(f.user(),version),
                new vn.ticketscenter.order.dto.OrderCommand.Create(UUID.randomUUID(),new vn.ticketscenter.order.dto.CreateOrderRequest(hold.id())));
        var payments=new PaymentRepository(buyer,worker);
        var intent=payments.beginOrderPayment(order.orderId(),f.user());
        payments.applyPaymentResult(order.orderId(),intent.paymentId(),"CAPTURED",intent.txnRef());
        assertEquals("PAID",ScanFixture.scalar("SELECT status FROM dbo.tc_orders WHERE id=?",order.orderId()));
        assertEquals(1,new vn.ticketscenter.fulfillment.ticket.repository.TicketRepository(buyer).findTicketsByOrderId(order.orderId()).size());
    }
    static javax.sql.DataSource source(String role,UUID actor) {
        return (javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(
                javax.sql.DataSource.class.getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(proxy,method,args)->{
                if(!method.getName().equals("getConnection"))throw new UnsupportedOperationException(method.getName());
                Connection c=ScanFixture.owner();
                try {
                    ScanFixture.execute(c,"EXEC sys.sp_set_session_context @key=N'actor_id',@value=?",actor==null?null:actor.toString());
                    ScanFixture.execute(c,"EXECUTE AS USER = 'sql_report_"+role+"'");
                    return c;
                } catch(SQLException failure){c.close();throw failure;}
        });
    }

    @Test void anonymousCallbackUsesWorkerAndPersistsAcceptedCapture() throws Exception {
        var f=SqlReportSafetyIT.fixture();
        var repository=new PaymentRepository(source("buyer",null),source("worker",null));
        assertTrue(repository.findPaymentByTxnRef(f.payment().toString()).isPresent(),"Verified anonymous callback must use technical reads");
        repository.applyPaymentResult(f.order(),f.payment(),"CAPTURED",f.payment().toString());
        repository.applyPaymentResult(f.order(),f.payment(),"CAPTURED",f.payment().toString());
        assertEquals("PAID",ScanFixture.scalar("SELECT status FROM dbo.tc_orders WHERE id=?",f.order()));
        assertEquals(f.payment().toString(),ScanFixture.scalar("SELECT CONVERT(varchar(36),accepted_payment_id) FROM dbo.tc_orders WHERE id=?",f.order()).toLowerCase());
        assertEquals("1",ScanFixture.scalar("SELECT COUNT(*) FROM dbo.tc_tickets WHERE order_item_id=?",f.item()));
        assertEquals("0",ScanFixture.scalar("SELECT COUNT(*) FROM dbo.tc_refunds WHERE payment_id=?",f.payment()));
    }

    @Test void freeCheckoutUsesBuyerFacadeAndIssuesTicketsWithoutPaymentCapture() throws Exception {
        var f=SqlReportSafetyIT.fixture();
        try(var c=ScanFixture.owner()) {
            ScanFixture.execute(c,"UPDATE dbo.tc_order_items SET unit_price=0 WHERE id=?",f.item());
            ScanFixture.execute(c,"UPDATE dbo.tc_orders SET subtotal_amount=0,total_amount=0 WHERE id=?",f.order());
            ScanFixture.execute(c,"UPDATE dbo.tc_payments SET status='FAILED' WHERE id=?",f.payment());
        }
        new PaymentRepository(source("buyer",f.user()),source("worker",null)).completeZeroOrder(f.order(),f.user());
        assertEquals("PAID",ScanFixture.scalar("SELECT status FROM dbo.tc_orders WHERE id=?",f.order()));
        assertEquals("1",ScanFixture.scalar("SELECT COUNT(*) FROM dbo.tc_tickets WHERE order_item_id=?",f.item()));
        assertEquals("0",ScanFixture.scalar("SELECT COUNT(*) FROM dbo.tc_payments WHERE order_id=? AND status='CAPTURED'",f.order()));
    }

    @Test void capturedPaymentCanRequestRefundWithoutManuallyLinkingPayment() throws Exception {
        var f=SqlReportSafetyIT.fixture();
        new PaymentRepository(source("buyer",f.user()),source("worker",null))
                .applyPaymentResult(f.order(),f.payment(),"CAPTURED",f.payment().toString());
        var tickets=new vn.ticketscenter.fulfillment.ticket.repository.TicketRepository(source("buyer",f.user()))
                .findTicketsByOrderId(f.order());
        assertEquals(1,tickets.size());
        try(var c=source("buyer",f.user()).getConnection()) {
            ScanFixture.execute(c,"DECLARE @id uniqueidentifier; EXEC dbo.sp_refund_request_for_tickets ?,?,?,?,@id OUTPUT,?,?",
                    f.order(),f.user(),tickets.getFirst().id().toString(),"Customer requests ticket refund",UUID.randomUUID(),
                    Integer.parseInt(ScanFixture.scalar("SELECT auth_version FROM dbo.tc_users WHERE id=?",f.user())));
        }
        assertEquals("REFUND_PENDING",ScanFixture.scalar("SELECT status FROM dbo.tc_tickets WHERE order_item_id=?",f.item()));
        assertEquals("1",ScanFixture.scalar("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=? AND payment_id=?",f.order(),f.payment()));
    }

    @Test void refundWaitingForEventDoesNotLockUserBeforeEvent() throws Exception {
        var f=SqlReportSafetyIT.fixture();
        new PaymentRepository(source("buyer",f.user()),source("worker",null)).applyPaymentResult(f.order(),f.payment(),"CAPTURED",f.payment().toString());
        String ticket=ScanFixture.scalar("SELECT CONVERT(varchar(36),id) FROM dbo.tc_tickets WHERE order_item_id=?",f.item());
        int version=Integer.parseInt(ScanFixture.scalar("SELECT auth_version FROM dbo.tc_users WHERE id=?",f.user()));
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        var ready=new java.util.concurrent.CountDownLatch(1);
        try(var gate=ScanFixture.owner()) {
            gate.setAutoCommit(false);
            ScanFixture.execute(gate,"UPDATE dbo.tc_events SET version=version WHERE id=?",f.event());
            var refund=pool.submit(()->{try(var c=source("buyer",f.user()).getConnection()){
                ready.countDown();
                ScanFixture.execute(c,"DECLARE @id uniqueidentifier; EXEC dbo.sp_refund_request_for_tickets ?,?,?,?,@id OUTPUT,?,?",
                        f.order(),f.user(),ticket,"Customer requests ticket refund",UUID.randomUUID(),version);
            }return true;});
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));Thread.sleep(750);
            try(var q=gate.prepareStatement("UPDATE dbo.tc_users SET auth_version=auth_version WHERE id=?")) {
                q.setQueryTimeout(8);q.setString(1,f.user().toString());assertEquals(1,q.executeUpdate());
            }
            gate.commit();assertTrue(refund.get(12,java.util.concurrent.TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals("REFUND_PENDING",ScanFixture.scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",ticket));
    }

    @Test void issuedTicketIsReadableOnlyByOwnerAndAcceptedByActualScanner() throws Exception {
        var f=SqlReportSafetyIT.fixture();
        new PaymentRepository(source("buyer",f.user()),source("worker",null))
                .applyPaymentResult(f.order(),f.payment(),"CAPTURED",f.payment().toString());
        var own=new vn.ticketscenter.fulfillment.ticket.repository.TicketRepository(source("buyer",f.user()));
        var ticket=own.findTicketsByOrderId(f.order()).getFirst();
        assertTrue(own.findById(ticket.id()).isPresent());
        var other=new vn.ticketscenter.fulfillment.ticket.repository.TicketRepository(source("buyer",UUID.randomUUID()));
        assertTrue(other.findById(ticket.id()).isEmpty());
        assertTrue(other.findTicketsByOrderId(f.order()).isEmpty());
        try(var c=source("buyer",f.user()).getConnection()) {
            assertThrows(SQLException.class,()->ScanFixture.execute(c,"SELECT * FROM dbo.tc_payments"));
        }
        try(var c=ScanFixture.owner()) {
            ScanFixture.execute(c,"UPDATE dbo.tc_events SET sale_end=DATEADD(minute,-2,SYSUTCDATETIME()),start_time=DATEADD(minute,-1,SYSUTCDATETIME()),end_time=DATEADD(hour,1,SYSUTCDATETIME()) WHERE id=?",f.event());
        }
        String qr=vn.ticketscenter.fulfillment.ticket.service.SignedTicketQrSigner.configured()
                .buildEnvelope(ticket.id(),ticket.eventTitle()+" - "+ticket.zoneName());
        try(var factory=vn.ticketscenter.fulfillment.checkin.integration.TicketCheckInSqlServerTest.factory()) {
            var result=vn.ticketscenter.fulfillment.checkin.integration.TicketCheckInSqlServerTest.service(factory)
                    .scan(f.user(),UUID.randomUUID(),new vn.ticketscenter.fulfillment.checkin.dto.CheckInScanRequestDto(f.event(),qr,null));
            assertTrue(result.success());
        }
        assertEquals("USED",ScanFixture.scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",ticket.id()));
    }

    @Test void staffWhoIsAlsoBuyerWaitsForEventBeforeLockingUser() throws Exception {
        var f=SqlReportSafetyIT.fixture();
        new PaymentRepository(source("buyer",f.user()),source("worker",null)).applyPaymentResult(f.order(),f.payment(),"CAPTURED",f.payment().toString());
        String ticket=ScanFixture.scalar("SELECT CONVERT(varchar(36),id) FROM dbo.tc_tickets WHERE order_item_id=?",f.item());
        try(var c=ScanFixture.owner()) {
            ScanFixture.execute(c,"UPDATE dbo.tc_events SET sale_end=DATEADD(minute,15,SYSUTCDATETIME()),start_time=DATEADD(minute,30,SYSUTCDATETIME()),end_time=DATEADD(hour,2,SYSUTCDATETIME()) WHERE id=?",f.event());
        }
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();var ready=new java.util.concurrent.CountDownLatch(1);
        try(var gate=ScanFixture.owner()) {
            gate.setAutoCommit(false);ScanFixture.execute(gate,"UPDATE dbo.tc_events SET version=version WHERE id=?",f.event());
            var scan=pool.submit(()->{try(var c=ScanFixture.connect("CHECKIN")) {
                ready.countDown();ScanFixture.execute(c,"EXEC dbo.sp_ticket_check_in @event_id=?,@staff_user_id=?,@ticket_uuid=?,@request_key=?,@request_fingerprint=?",
                        f.event(),f.user(),ticket,UUID.randomUUID(),"a".repeat(64));
            }return true;});
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));Thread.sleep(750);
            try(var q=gate.prepareStatement("UPDATE dbo.tc_users SET auth_version=auth_version WHERE id=?")) {
                q.setQueryTimeout(8);q.setString(1,f.user().toString());assertEquals(1,q.executeUpdate());
            }
            gate.commit();assertTrue(scan.get(12,java.util.concurrent.TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals("USED",ScanFixture.scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",ticket));
    }
}
