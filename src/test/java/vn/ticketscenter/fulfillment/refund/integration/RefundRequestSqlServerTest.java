package vn.ticketscenter.fulfillment.refund.integration;

import jakarta.persistence.*;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.dao.*;
import vn.ticketscenter.fulfillment.refund.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.ticketscenter.fulfillment.refund.integration.RefundFixture.*;

@EnabledIfEnvironmentVariable(named="TC_REFUND_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefundRequestSqlServerTest {
    EntityManagerFactory factory;RefundRequestService service;
    @BeforeAll void connect()throws Exception{try(var preflight=RefundFixture.connect("BUYER")){assertEquals("TicketCenter_Khanh03_Test",preflight.getCatalog());}factory=Persistence.createEntityManagerFactory("ticketscenter",Map.of("jakarta.persistence.jdbc.url",required("TC_REFUND_TEST_URL"),"jakarta.persistence.jdbc.user",required("TC_REFUND_BUYER_USER"),"jakarta.persistence.jdbc.password",required("TC_REFUND_BUYER_PASSWORD")));service=new RefundRequestServiceImpl(new RefundRequestRepository(new TransactionManager(Map.of(DatabasePrincipal.BUYER,factory))),RefundRequestCache.NONE,Clock.systemUTC());}
    @AfterAll void close(){if(factory!=null)factory.close();}
    private CreateRefundRequestDto request(RefundFixture f){return new CreateRefundRequestDto(f.order,f.tickets,"Không thể tham dự sự kiện này.");}
    private static void error(int status,Runnable work){assertEquals(status,assertThrows(RefundException.class,work::run).status());}
    @Test void validRequestUsesPaidAmountAndFreezesEveryTicket()throws Exception{
        var f=new RefundFixture(160001,false);var r=service.create(f.buyer(),request(f),UUID.randomUUID());assertEquals("320002",r.totalRefundAmount());assertEquals("REQUESTED",r.status());assertEquals(2,r.ticketCount());assertEquals("2",scalar("SELECT COUNT(*) FROM dbo.tc_tickets WHERE order_item_id=? AND status='REFUND_PENDING'",f.item));assertEquals(f.payment.toString().toUpperCase(),scalar("SELECT payment_id FROM dbo.tc_refund_obligations WHERE id=?",r.refundId()));assertEquals(2,service.detail(f.buyer(),r.refundId()).tickets().size());assertEquals(1,service.list(f.buyer(),1,20).total());
    }
    @Test void usedTicketRejectsWholeSelectionWithoutPartialMutation()throws Exception{var f=new RefundFixture(100,false);try(var c=owner()){execute(c,"UPDATE dbo.tc_tickets SET status='USED' WHERE id=?",f.tickets.get(0));}error(400,()->service.create(f.buyer(),request(f),UUID.randomUUID()));assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.order));assertEquals("ACTIVE",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.tickets.get(1)));assertFalse(service.tickets(f.buyer(),f.order).getFirst().isRefundable()&&service.tickets(f.buyer(),f.order).getLast().isRefundable());}
    @Test void otherOwnerCannotReadOrCreateOrReadDetail()throws Exception{var f=new RefundFixture(100,false);error(403,()->service.tickets(f.other(),f.order));error(403,()->service.create(f.other(),request(f),UUID.randomUUID()));var r=service.create(f.buyer(),request(f),UUID.randomUUID());error(403,()->service.detail(f.other(),r.refundId()));assertEquals(0,service.list(f.other(),1,20).total());}
    @Test void startedEventIsRejectedByDatabaseClock()throws Exception{var f=new RefundFixture(100,true);error(400,()->service.create(f.buyer(),request(f),UUID.randomUUID()));assertTrue(service.tickets(f.buyer(),f.order).stream().noneMatch(RefundTicketOptionDto::isRefundable));}
    @Test void lockWaitAcrossEventStartCannotAcceptExpiredRequest()throws Exception{
        var f=new RefundFixture(100,3);
        try(var blocker=owner();var pool=Executors.newSingleThreadExecutor()){
            blocker.setAutoCommit(false);execute(blocker,"SELECT id FROM dbo.tc_tickets WITH(UPDLOCK,HOLDLOCK) WHERE id=?",f.tickets.getFirst());
            var future=pool.submit(()->{try{service.create(f.buyer(),request(f),UUID.randomUUID());return 201;}catch(RefundException e){return e.status();}});
            Thread.sleep(300);assertFalse(future.isDone());Thread.sleep(3000);blocker.commit();assertEquals(400,future.get(10,TimeUnit.SECONDS));
        }
        assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.order));
    }
    @Test void pendingTicketWithAnotherKeyReturnsConflict()throws Exception{var f=new RefundFixture(100,false);service.create(f.buyer(),request(f),UUID.randomUUID());error(409,()->service.create(f.buyer(),request(f),UUID.randomUUID()));}
    @Test void durableReplayReturnsOriginalResponseAndChangedBodyConflicts()throws Exception{var f=new RefundFixture(100,false);var key=UUID.randomUUID();var first=service.create(f.buyer(),request(f),key);assertEquals(first,service.create(f.buyer(),request(f),key));error(409,()->service.create(f.buyer(),new CreateRefundRequestDto(f.order,f.tickets,"Một lý do khác hoàn toàn."),key));assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.order));}
    @Test void zeroAmountRequestCreatesNoPaymentOrTransfer()throws Exception{var f=new RefundFixture(0,false);var r=service.create(f.buyer(),request(f),UUID.randomUUID());assertEquals("0",r.totalRefundAmount());assertNull(scalar("SELECT payment_id FROM dbo.tc_refund_obligations WHERE id=?",r.refundId()));assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=?",r.refundId()));}
    @Test void inactiveOrChangedAuthVersionCannotUseOldSession()throws Exception{var f=new RefundFixture(100,false);try(var c=owner()){execute(c,"UPDATE dbo.tc_users SET auth_version=auth_version+1 WHERE id=?",f.buyer);}error(401,()->service.create(f.buyer(),request(f),UUID.randomUUID()));error(401,()->service.list(f.buyer(),1,20));}
    @Test void selectedTicketsMustAllBelongToSameOrder()throws Exception{var a=new RefundFixture(100,false);var b=new RefundFixture(100,false);error(400,()->service.create(a.buyer(),new CreateRefundRequestDto(a.order,List.of(a.tickets.getFirst(),b.tickets.getFirst()),"Không thể tham dự sự kiện."),UUID.randomUUID()));assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",a.order));}
    @Test void twoConnectionsWithSameKeyCreateExactlyOneRequest()throws Exception{race(true);}
    @Test void twoConnectionsWithDifferentKeysHaveOneWinnerAndOneConflict()throws Exception{race(false);}
    private void race(boolean same)throws Exception{var f=new RefundFixture(100,false);UUID one=UUID.randomUUID(),two=same?one:UUID.randomUUID();var ready=new CountDownLatch(2);var go=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var tasks=List.of(one,two).stream().map(key->pool.submit(()->{ready.countDown();assertTrue(go.await(5,TimeUnit.SECONDS));try{return service.create(f.buyer(),request(f),key);}catch(RefundException e){return e;}})).toList();assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();Object a=tasks.get(0).get(20,TimeUnit.SECONDS),b=tasks.get(1).get(20,TimeUnit.SECONDS);if(same){assertInstanceOf(RefundSummaryDto.class,a);assertEquals(a,b);}else{assertTrue(a instanceof RefundSummaryDto ^ b instanceof RefundSummaryDto);assertEquals(409,((RefundException)(a instanceof RefundException?a:b)).status());}assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.order));}}
    @Test void errorAfterTicketMutationRollsBackRequestLinksTicketsAuditAndIdempotency()throws Exception{
        var f=new RefundFixture(100,false);var key=UUID.randomUUID();
        try(var c=owner()){execute(c,"CREATE OR ALTER TRIGGER dbo.trg_Khanh03_Fault ON dbo.tc_audit_logs AFTER INSERT AS IF EXISTS(SELECT 1 FROM inserted WHERE aggregate_type='Refund' AND actor_id='"+f.buyer+"') THROW 53999,'Injected post-mutation failure',1;");}
        try{error(503,()->service.create(f.buyer(),request(f),key));assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.order));assertEquals("2",scalar("SELECT COUNT(*) FROM dbo.tc_tickets WHERE order_item_id=? AND status='ACTIVE'",f.item));assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_request_idempotency WHERE actor_id=?",f.buyer));assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_audit_logs WHERE action='REFUND_REQUESTED' AND actor_id=?",f.buyer));}
        finally{try(var c=owner()){execute(c,"DROP TRIGGER dbo.trg_Khanh03_Fault");}}
        assertNotNull(service.create(f.buyer(),request(f),key));
    }
    @Test void buyerLoginCannotBypassStoredProcedureWithDirectWrites()throws Exception{try(var c=RefundFixture.connect("BUYER")){assertThrows(SQLException.class,()->execute(c,"UPDATE dbo.tc_tickets SET status='REFUNDED' WHERE 1=0"));assertThrows(SQLException.class,()->execute(c,"INSERT dbo.tc_refund_obligations(id,order_id,requester_id,amount,purpose,reason,reason_type,status) VALUES(NEWID(),NEWID(),NEWID(),0,'CUSTOMER_REFUND',N'x','CUSTOMER_REQUEST','REQUESTED')"));}}
}
