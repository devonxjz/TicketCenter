package vn.ticketscenter.database;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.event.repository.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundDecisionRequestDto;
import vn.ticketscenter.fulfillment.refund.repository.AdminRefundRepository;
import vn.ticketscenter.fulfillment.refund.service.AdminRefundDecisionService;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.payment.repository.PaymentRepository;
import static org.junit.jupiter.api.Assertions.*;

/** Actual combined contracts in the dedicated loopback fixture; no gateway/provider transfer. */
@EnabledIfEnvironmentVariable(named="TC_SQL_REPORT_TEST",matches="true")
class RefundEventSqlIntegrationTest {
    @Test void publishedEventPurchaseAndAdminRefundUseSharedContractsAndBlockSettlement() throws Exception {
        var f=SqlReportSafetyIT.fixture();
        UUID administrator=UUID.randomUUID();
        try(var c=ScanFixture.owner()) {
            c.setAutoCommit(false);
            ScanFixture.execute(c,"UPDATE dbo.tc_organizations SET status='APPROVED' WHERE id=?",f.org());
            ScanFixture.execute(c,"DELETE dbo.tc_user_platform_roles WHERE user_id=?",f.user());
            ScanFixture.execute(c,"UPDATE dbo.tc_users SET platform_role='CUSTOMER' WHERE id=?",f.user());
            ScanFixture.execute(c,"INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,email_verified_at,platform_role) VALUES(?,?,?,N'test-only',N'Combined admin',SYSUTCDATETIME(),'ADMIN')",administrator,administrator+"@fixture.invalid",administrator.toString());
            ScanFixture.execute(c,"INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?,'ADMIN')",administrator);
            ScanFixture.execute(c,"DELETE dbo.tc_payments WHERE order_id=?",f.order());
            ScanFixture.execute(c,"DELETE dbo.tc_order_items WHERE order_id=?",f.order());
            ScanFixture.execute(c,"DELETE dbo.tc_orders WHERE id=?",f.order());
            ScanFixture.execute(c,"UPDATE dbo.tc_ticket_holds SET created_at=DATEADD(minute,-11,SYSUTCDATETIME()),expires_at=DATEADD(minute,-1,SYSUTCDATETIME()) WHERE id=?",f.hold());
            c.commit();
        }
        int version=Integer.parseInt(ScanFixture.scalar("SELECT auth_version FROM dbo.tc_users WHERE id=?",f.user()));
        var eventTransactions=new EventTransactions(null) {
            @Override public <T> T run(DatabasePrincipal principal,Work<T> work) {
                try(var c=PaymentCrossDomainSqlTest.source("manager",f.user()).getConnection()) {
                    c.setAutoCommit(false);
                    try { T result=work.execute(c);c.commit();return result; }
                    catch(SQLException|RuntimeException failure) {c.rollback();throw failure;}
                }catch(SQLException failure){throw EventTransactions.translate(failure);}
            }
        };
        var eventRepository=new EventManagementRepository(eventTransactions);
        String event=eventRepository.read(new EventManagementRepository.Actor(f.user(),version),false,"DETAIL",f.org(),f.event(),null,null,1,20);
        assertTrue(event.contains(f.event().toString()));
        assertTrue(event.contains("PUBLISHED"));

        var buyer=PaymentCrossDomainSqlTest.source("buyer",f.user());
        var worker=PaymentCrossDomainSqlTest.source("worker",null);
        var holds=new vn.ticketscenter.ticketing.repository.HoldRepository(buyer,worker);
        var holdActor=new vn.ticketscenter.ticketing.dto.HoldActor(f.user(),version);
        var holdCommand=new vn.ticketscenter.ticketing.dto.HoldCommand.Create(UUID.randomUUID(),
                new vn.ticketscenter.ticketing.dto.CreateHoldRequest(f.event(),List.of(new vn.ticketscenter.ticketing.dto.HoldSelection(f.zone(),null,1))));
        assertEquals(holds.mutate(holdActor,holdCommand).body(),holds.mutate(holdActor,holdCommand).body());
        var hold=holds.current(holdActor).projection().orElseThrow();
        var order=new vn.ticketscenter.order.repository.OrderRepository(buyer).mutate(
                new vn.ticketscenter.order.dto.OrderActor(f.user(),version),
                new vn.ticketscenter.order.dto.OrderCommand.Create(UUID.randomUUID(),new vn.ticketscenter.order.dto.CreateOrderRequest(hold.id())));
        var payments=new PaymentRepository(buyer,worker);
        var intent=payments.beginOrderPayment(order.orderId(),f.user());
        payments.applyPaymentResult(order.orderId(),intent.paymentId(),"CAPTURED",intent.txnRef());
        var tickets=new vn.ticketscenter.fulfillment.ticket.repository.TicketRepository(buyer).findTicketsByOrderId(order.orderId());
        assertEquals(1,tickets.size());
        try(var c=buyer.getConnection()) {
            ScanFixture.execute(c,"DECLARE @id uniqueidentifier; EXEC dbo.sp_refund_request_for_tickets ?,?,?,?,@id OUTPUT,?,?",order.orderId(),f.user(),tickets.getFirst().id().toString(),"Combined owner refund request",UUID.randomUUID(),version);
        }
        UUID refund=UUID.fromString(ScanFixture.scalar("SELECT CONVERT(varchar(36),id) FROM dbo.tc_refund_obligations WHERE order_id=? AND purpose='CUSTOMER_REFUND'",order.orderId()));
        int adminVersion=Integer.parseInt(ScanFixture.scalar("SELECT auth_version FROM dbo.tc_users WHERE id=?",administrator));
        var admin=new UserPrincipal(administrator,PlatformRole.ADMIN,adminVersion);
        var decisions=new AdminRefundDecisionService(new AdminRefundRepository(PaymentCrossDomainSqlTest.source("admin",administrator)),(a,k,r)->{});
        var request=new AdminRefundDecisionRequestDto("APPROVE","Verified combined purchase",null);
        UUID key=UUID.randomUUID();
        var approved=decisions.decide(admin,refund,request,key);
        assertEquals(approved,decisions.decide(admin,refund,request,key));
        assertEquals("PROCESSING",approved.canonicalStatus());
        assertNotNull(approved.attemptId());
        assertEquals("REFUND_PENDING",ScanFixture.scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",tickets.getFirst().id()));
        assertEquals("1",ScanFixture.scalar("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE id=? AND status='PENDING'",approved.attemptId()));
        try(var c=PaymentCrossDomainSqlTest.source("admin",administrator).getConnection();var q=c.prepareStatement("SELECT COUNT(*) FROM dbo.fn_GetSettlementBlockers(?,SYSUTCDATETIME()) WHERE blocker_type IN('REFUND','REFUND_REQUEST_OPEN','REFUND_UNRESOLVED')")) {
            q.setString(1,f.event().toString());try(var rows=q.executeQuery()){assertTrue(rows.next());assertTrue(rows.getInt(1)>0,"Queued refund must block settlement");}
        }
        assertEquals(f.user(),decisions.detail(admin,refund).refund().buyerId());
        assertNotEquals(administrator,f.user());
    }
}
