package vn.ticketscenter.database;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.DatabaseActorContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL Server checks in the existing dedicated loopback fixture; never Azure. */
@EnabledIfEnvironmentVariable(named="TC_SQL_REPORT_TEST",matches="true")
public class SqlReportSafetyIT {
    record Fixture(UUID user, UUID org, UUID category, UUID event, UUID zone, UUID hold, UUID order, UUID item, UUID payment) {}
    static Connection owner() throws SQLException { return ScanFixture.owner(); }
    static void sql(Connection c,String s,Object...a) throws SQLException { ScanFixture.execute(c,s,a); }
    static String scalar(String s,Object...a)throws SQLException{return ScanFixture.scalar(s,a);}
    static Fixture fixture() throws Exception {
        Fixture f=new Fixture(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        try(var c=owner()) { c.setAutoCommit(false);
            sql(c,"INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,email_verified_at,platform_role) VALUES(?,?,?,N'test-only',N'SQL report',SYSUTCDATETIME(),'ADMIN')",f.user,f.user+"@fixture.invalid",f.user.toString());
            sql(c,"INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?,'ADMIN')",f.user);
            sql(c,"INSERT dbo.tc_organizations(id,name,contact_email) VALUES(?,N'SQL report',?)",f.org,f.org+"@fixture.invalid");
            sql(c,"INSERT dbo.tc_organization_memberships(user_id,organization_id,role,active) VALUES(?,?,'MANAGER',1)",f.user,f.org);
            sql(c,"INSERT dbo.tc_event_categories(id,name,slug) VALUES(?,N'SQL report',?)",f.category,f.category.toString());
            sql(c,"INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time) VALUES(?,?,?,N'SQL report',N'Test',N'A',N'A',DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(hour,2,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()),DATEADD(day,2,SYSUTCDATETIME()))",f.event,f.org,f.category);
            sql(c,"INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,held_quantity) VALUES(?,?,N'A','STANDING',1000,100,1)",f.zone,f.event);
            sql(c,"UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?",f.event);
            sql(c,"INSERT dbo.tc_ticket_holds(id,user_id,event_id,expires_at) VALUES(?,?,?,DATEADD(minute,10,SYSUTCDATETIME()))",f.hold,f.user,f.event);
            sql(c,"INSERT dbo.tc_ticket_hold_items(hold_id,zone_id,quantity,unit_price) VALUES(?,?,1,1000)",f.hold,f.zone);
            sql(c,"INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount) VALUES(?,?,?,?,?,1000,0,1000)",f.order,f.user,f.event,f.hold,f.order.toString());
            sql(c,"INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES(?,?,?,1,1000,N'A')",f.item,f.order,f.zone);
            sql(c,"INSERT dbo.tc_payments(id,order_id,txn_ref,amount) VALUES(?,?,?,1000)",f.payment,f.order,f.payment.toString());
            c.commit();
        } return f;
    }
    static int capture(Fixture f, UUID p, String ref) throws Exception {
        try(var c=owner();var q=c.prepareStatement("EXEC dbo.usp_ApplyPaymentResult @order_id=?,@payment_id=?,@verified_result=?,@ticket_codes=?")) {
            q.setString(1,f.order.toString());q.setString(2,p.toString());q.setString(3,"{\"status\":\"CAPTURED\",\"providerReference\":\""+ref+"\"}");
            q.setString(4,"[{\"ticketCode\":\""+UUID.randomUUID()+"\",\"qrSecretHashHex\":\"01020304\"}]");
            q.setQueryTimeout(20);boolean result=q.execute();int sets=0;
            while(true){if(result){sets++;try(var r=q.getResultSet()){while(r.next()) { /* consume */ }}} else if(q.getUpdateCount()==-1)break;result=q.getMoreResults();}
            return sets;
        }
    }
    @Test void simultaneousCallbacksIssueOnceWithoutCompensation()throws Exception {
        var f=fixture();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(2);
        try(var gate=owner()) {gate.setAutoCommit(false);sql(gate,"UPDATE dbo.tc_events SET version=version WHERE id=?",f.event);
            var a=pool.submit(()->{start.countDown();return capture(f,f.payment,f.payment.toString());});
            var b=pool.submit(()->{start.countDown();return capture(f,f.payment,f.payment.toString());});
            assertTrue(start.await(5,TimeUnit.SECONDS));Thread.sleep(750);gate.commit();
            int setsA=a.get(25,TimeUnit.SECONDS),setsB=b.get(25,TimeUnit.SECONDS);
            assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.vw_RefundAttemptLedger WHERE payment_id=?",f.payment),"A repeated callback must not compensate the accepted payment");
            assertEquals(1,setsA);assertEquals(1,setsB);
        }finally{pool.shutdownNow();}
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_tickets WHERE order_item_id=?",f.item));
        assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.vw_RefundAttemptLedger WHERE payment_id=?",f.payment));
        assertEquals(f.payment.toString().toUpperCase(Locale.ROOT),scalar("SELECT CONVERT(varchar(36),accepted_payment_id) FROM dbo.tc_orders WHERE id=?",f.order).toUpperCase(Locale.ROOT));
    }
    @Test void secondRealCaptureIsRecordedAndCompensatedExactlyOnce()throws Exception {
        var f=fixture();capture(f,f.payment,f.payment.toString());UUID extra=UUID.randomUUID();
        try(var c=owner()){sql(c,"INSERT dbo.tc_payments(id,order_id,txn_ref,amount) VALUES(?,?,?,1000)",extra,f.order,extra.toString());}
        capture(f,extra,extra.toString());capture(f,extra,extra.toString());
        assertEquals("2",scalar("SELECT COUNT(*) FROM dbo.tc_payments WHERE order_id=? AND status='CAPTURED'",f.order));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.vw_RefundAttemptLedger WHERE payment_id=? AND purpose='PAYMENT_COMPENSATION'",extra));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_tickets WHERE order_item_id=?",f.item));
    }
    static UUID compensation(Fixture f)throws Exception {
        UUID r=UUID.randomUUID();try(var c=owner()){
            sql(c,"UPDATE dbo.tc_payments SET status='CAPTURED',captured_at=SYSUTCDATETIME(),provider_reference=? WHERE id=?",f.payment.toString(),f.payment);
            sql(c,"INSERT dbo.tc_refund_obligations(id,order_id,payment_id,amount,purpose,status,current_attempt_id) SELECT ?,order_id,id,1000,'PAYMENT_COMPENSATION','PROCESSING',? FROM dbo.tc_payments WHERE id=?",r,r,f.payment);
            sql(c,"INSERT dbo.tc_refund_transfer_logs(id,refund_id,attempt_no,status,result) VALUES(?,?,1,'PENDING','PENDING')",r,r);
        }return r;
    }
    static void refund(UUID id,String status,String reference)throws Exception {try(var c=owner()){sql(c,"EXEC dbo.usp_ApplyRefundResult ?,?",id,"{\"status\":\""+status+"\",\"providerReference\":\""+reference+"\"}");}}
    @Test void failedRefundAcceptsVerifiedLateSuccessAndReplay()throws Exception {
        var f=fixture();UUID r=compensation(f);refund(r,"FAILED","REF-"+r);refund(r,"SUCCEEDED","REF-"+r);refund(r,"SUCCEEDED","REF-"+r);
        assertEquals("SUCCEEDED",scalar("SELECT status FROM dbo.tc_refund_transfer_logs WHERE id=?",r));
        assertEquals("2",scalar("SELECT COUNT(*) FROM dbo.tc_gateway_refund_receipts WHERE refund_id=?",r));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_outbox WHERE idempotency_key=?","refund-succeeded:"+r));
    }
    @Test void conflictingGatewayTruthSurvivesForReconciliation()throws Exception {
        var f=fixture();UUID r=compensation(f);refund(r,"FAILED","REF-"+r);refund(r,"SUCCEEDED","WRONG-"+r);
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_gateway_refund_receipts WHERE refund_id=? AND observed_status='SUCCEEDED' AND resolution='NEEDS_RECONCILIATION'",r));
        assertEquals("NEEDS_RECONCILIATION",scalar("SELECT status FROM dbo.tc_refund_obligations WHERE id=?",r));
    }
    @Test void recalculationThreeTimesThenConfirmationKeepsSnapshot()throws Exception {
        var f=fixture();capture(f,f.payment,f.payment.toString());UUID rule=UUID.randomUUID();
        try(var c=owner()) {
            // Fixture end-time correction before settlement; owner-only, never shared data.
            sql(c,"INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES(?,?,5,0,'2000-01-01','2099-01-01')",rule,f.org);
            sql(c,"UPDATE dbo.tc_events SET commission_rule_id=?,sale_start='2020-01-01',sale_end='2020-01-02',start_time='2020-01-03',end_time='2020-01-04' WHERE id=?",rule,f.event);
            for(int n=0;n<3;n++)sql(c,"EXEC dbo.usp_RecalculateSettlement @event_id=?,@actor_id=?",f.event,f.user);
            sql(c,"DECLARE @sid uniqueidentifier=(SELECT id FROM dbo.tc_settlements WHERE event_id=?); EXEC dbo.usp_ConfirmSettlement @sid,?",f.event,f.user);
        }
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_settlement_order_snapshots s JOIN dbo.tc_settlements t ON t.id=s.settlement_id WHERE t.event_id=?",f.event));
        assertEquals("CONFIRMED",scalar("SELECT status FROM dbo.tc_settlements WHERE event_id=?",f.event));
    }
    static void deny(String principal,String s,Object...args)throws Exception{
        try(var c=owner()){sql(c,"EXECUTE AS USER = '"+principal+"'");assertThrows(SQLException.class,()->sql(c,s,args));}
    }
    @Test void directManagerWritesAndForgedAuditAreDenied()throws Exception{
        var f=fixture();deny("sql_report_manager","UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?",f.event);
        deny("sql_report_manager","UPDATE dbo.tc_zones SET sold_quantity=0 WHERE id=?",f.zone);
        deny("sql_report_manager","UPDATE dbo.tc_seats SET status='AVAILABLE' WHERE zone_id=?",f.zone);
        deny("sql_report_manager","INSERT dbo.tc_organization_memberships(user_id,organization_id,role) VALUES(?,?,'MANAGER')",f.user,f.org);
        for(String p:List.of("sql_report_manager","sql_report_admin"))deny(p,"INSERT dbo.tc_audit_logs(action,aggregate_type,aggregate_id) VALUES('FORGED','EVENT',?)",f.event);
    }
    @Test void authCannotMintAdminButCanResetPassword()throws Exception{
        var f=fixture();deny("sql_report_auth","INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?,'ADMIN')",f.user);
        deny("sql_report_auth","UPDATE dbo.tc_users SET platform_role='ADMIN' WHERE id=?",f.user);
        UUID u=UUID.randomUUID();deny("sql_report_auth","INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,platform_role) VALUES(?,?,?,'x','x','ADMIN')",u,u+"@fixture.invalid",u.toString());
        try(var c=owner()){sql(c,"EXECUTE AS USER = 'sql_report_auth'");sql(c,"UPDATE dbo.tc_users SET password_hash=N'new-test',auth_version=auth_version+1 WHERE id=?",f.user);}
        assertEquals("new-test",scalar("SELECT password_hash FROM dbo.tc_users WHERE id=?",f.user));
    }
    @Test void buyerCannotApplyPaidCaptureOrInvokeLegacyFinance()throws Exception{
        var f=fixture();deny("sql_report_buyer","EXEC dbo.usp_ApplyPaymentResult @order_id=?,@payment_id=?,@verified_result=N'{\"status\":\"CAPTURED\"}'",f.order,f.payment);
        deny("sql_report_worker","EXEC dbo.usp_ReviewRefundRequest @request_id=?,@decision='REJECT'",UUID.randomUUID());
    }
    @Test void scopedMemberProcedureRejectsForeignOrganization()throws Exception{
        var f=fixture();var foreign=fixture();
        deny("sql_report_manager","EXEC dbo.usp_AddMember ?,?,?,'MANAGER'",foreign.org,f.user,f.user);
        UUID u=UUID.randomUUID();try(var c=owner()){
            sql(c,"INSERT dbo.tc_users(id,email,user_name,password_hash,full_name) VALUES(?,?,?,'test','Member')",u,u+"@fixture.invalid",u.toString());
            sql(c,"EXEC sys.sp_set_session_context @key=N'actor_id',@value=?",f.user.toString());
            sql(c,"EXECUTE AS USER = 'sql_report_manager'");sql(c,"EXEC dbo.usp_AddMember ?,?,?,'CHECK_IN_STAFF'",f.org,f.user,u);
        }
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_audit_logs WHERE action='MEMBER_ADDED' AND aggregate_id=?",f.org));
    }
    @Test void buyerAndManagerReadsExcludeAnotherOwnersData()throws Exception{
        var f=fixture();var foreign=fixture();capture(f,f.payment,f.payment.toString());capture(foreign,foreign.payment,foreign.payment.toString());
        for(String principal:List.of("sql_report_buyer","sql_report_manager"))try(var c=owner()){
            sql(c,"EXEC sys.sp_set_session_context @key=N'actor_id',@value=?",f.user.toString());
            sql(c,"EXECUTE AS USER = '"+principal+"'");
            try(var q=c.prepareStatement("SELECT COUNT(*) FROM dbo.vw_TicketDetails WHERE order_id=?")){
                q.setString(1,foreign.order.toString());try(var r=q.executeQuery()){assertTrue(r.next());assertEquals(0,r.getInt(1));}
                q.setString(1,f.order.toString());try(var r=q.executeQuery()){assertTrue(r.next());assertEquals(1,r.getInt(1));}
            }
            sql(c,"EXEC sys.sp_set_session_context @key=N'actor_id',@value=NULL");
            try(var q=c.prepareStatement("SELECT COUNT(*) FROM dbo.vw_TicketDetails" );var r=q.executeQuery()){r.next();assertEquals(0,r.getInt(1));}
        }
        deny("sql_report_buyer","SELECT code FROM dbo.vw_CouponUsage");
    }
    @Test void freeOrderCompletesThroughBuyerFacade()throws Exception {
        var f=fixture();try(var c=owner()){
            sql(c,"UPDATE dbo.tc_order_items SET unit_price=0 WHERE id=?",f.item);
            sql(c,"UPDATE dbo.tc_orders SET subtotal_amount=0,total_amount=0 WHERE id=?",f.order);
            sql(c,"UPDATE dbo.tc_payments SET status='FAILED' WHERE id=?",f.payment);
            sql(c,"EXEC sys.sp_set_session_context @key=N'actor_id',@value=?",f.user.toString());sql(c,"EXECUTE AS USER = 'sql_report_buyer'");
            sql(c,"EXEC dbo.usp_CompleteFreeOrder ?,?,?",f.order,f.user,"[{\"ticketCode\":\""+UUID.randomUUID()+"\",\"qrSecretHashHex\":\"01020304\"}]");
        }
        assertEquals("PAID",scalar("SELECT status FROM dbo.tc_orders WHERE id=?",f.order));
        assertEquals("0",scalar("SELECT COUNT(*) FROM dbo.tc_payments WHERE order_id=? AND status='CAPTURED'",f.order));
    }
    @Test void cappedCouponRespectsItsConfiguredMaximum()throws Exception {
        var f=fixture();UUID coupon=UUID.randomUUID();try(var c=owner()){
            sql(c,"INSERT dbo.tc_coupons(id,organization_id,code,discount_type,percentage_value,max_discount_amount,max_uses,valid_from,valid_to) VALUES(?,?,?,'PERCENTAGE',30,100,20,DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()))",coupon,f.org,coupon.toString());
            sql(c,"UPDATE dbo.tc_payments SET status='FAILED' WHERE id=?",f.payment);
            sql(c,"EXEC dbo.usp_ApplyOrderCoupon ?,?,?",f.order,f.user,coupon.toString());
        }
        assertEquals("100",scalar("SELECT discount_amount FROM dbo.tc_orders WHERE id=?",f.order));
    }
    @Test void workerReleasesExpiredHoldWithoutHidingPendingCapture()throws Exception {
        var f=fixture();try(var c=owner()){
            sql(c,"UPDATE dbo.tc_ticket_holds SET created_at=DATEADD(minute,-20,SYSUTCDATETIME()),expires_at=DATEADD(minute,-10,SYSUTCDATETIME()) WHERE id=?",f.hold);
            sql(c,"UPDATE dbo.tc_payments SET created_at=DATEADD(minute,-10,SYSUTCDATETIME()) WHERE id=?",f.payment);
            sql(c,"EXECUTE AS USER = 'sql_report_worker'");sql(c,"EXEC dbo.usp_ExpireHolds 100");
            try(var q=c.prepareStatement("SELECT COUNT(*) FROM dbo.vw_PaymentsToReconcile WHERE id=?")){q.setString(1,f.payment.toString());try(var r=q.executeQuery()){r.next();assertEquals(1,r.getInt(1));}}
        }
        assertEquals("RELEASED",scalar("SELECT status FROM dbo.tc_ticket_holds WHERE id=?",f.hold));
        assertEquals("0",scalar("SELECT held_quantity FROM dbo.tc_zones WHERE id=?",f.zone));
        assertEquals("PENDING",scalar("SELECT status FROM dbo.tc_payments WHERE id=?",f.payment));
    }
    @Test void publishedCoverChangesWithoutChangingEventTerms()throws Exception {
        var f=fixture();try(var c=owner()){
            sql(c,"EXEC sys.sp_set_session_context @key=N'actor_id',@value=?",f.user.toString());sql(c,"EXECUTE AS USER = 'sql_report_manager'");
            sql(c,"DECLARE @v bigint=(SELECT version FROM dbo.tc_events WHERE id=?); EXEC dbo.usp_SaveEventDraft @event_id=?,@organization_id=?,@actor_id=?,@create=0,@expected_version=@v,@cover_only=1,@image_url='https://res.cloudinary.com/test/image/upload/test.jpg',@image_public_id='test'",f.event,f.event,f.org,f.user);
        }
        assertEquals("PUBLISHED",scalar("SELECT status FROM dbo.tc_events WHERE id=?",f.event));
        assertEquals("SQL report",scalar("SELECT title FROM dbo.tc_events WHERE id=?",f.event));
        assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_audit_logs WHERE action='EVENT_DRAFT_UPDATED' AND aggregate_id=?",f.event));
    }
    @Test void pooledConnectionsDoNotCarryActorsToNextRequest()throws Exception {
        HikariConfig cfg=new HikariConfig();cfg.setJdbcUrl(ScanFixture.required("TC_SCAN_TEST_URL"));cfg.setUsername(ScanFixture.required("TC_SCAN_OWNER_USER"));cfg.setPassword(ScanFixture.required("TC_SCAN_OWNER_PASSWORD"));cfg.setMaximumPoolSize(1);cfg.setMinimumIdle(0);
        try(var pool=new HikariDataSource(cfg)){
            var source=ConnectionBudgets.wrap(pool);UUID actor=UUID.randomUUID();DatabaseActorContext.set(actor);
            try(var c=source.getConnection();var q=c.createStatement();var r=q.executeQuery("SELECT CONVERT(varchar(36),SESSION_CONTEXT(N'actor_id'))")){r.next();assertEquals(actor.toString(),r.getString(1));}
            DatabaseActorContext.clear();
            try(var c=source.getConnection();var q=c.createStatement();var r=q.executeQuery("SELECT SESSION_CONTEXT(N'actor_id')")){r.next();assertNull(r.getObject(1));}
        }finally{DatabaseActorContext.clear();}
    }
    @Test void thousandHoldAttemptsDoNotOversellStandingInventory()throws Exception {
        var f=fixture();var users=new ArrayList<UUID>();for(int n=0;n<1000;n++)users.add(UUID.randomUUID());
        try(var c=owner()) {c.setAutoCommit(false);try(var q=c.prepareStatement("INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,email_verified_at) VALUES(?,?,?,'test','Load',SYSUTCDATETIME())")){
            for(UUID u:users){q.setString(1,u.toString());q.setString(2,u+"@fixture.invalid");q.setString(3,u.toString());q.addBatch();}q.executeBatch();}c.commit();}
        var pool=Executors.newFixedThreadPool(16);int accepted=0;
        try {
            var calls=new ArrayList<Callable<Boolean>>();
            for(UUID u:users)calls.add(()->{for(int attempt=0;attempt<4;attempt++)try(var c=owner()){
                sql(c,"EXEC dbo.usp_CreateTicketHold ?,?,?",u,f.event,"[{\"zoneId\":\""+f.zone+"\",\"quantity\":1}]");return true;
            }catch(SQLException ex){if(ex.getErrorCode()==51229)return false;if(ex.getErrorCode()!=1205)throw ex;Thread.sleep(10L*(attempt+1));}throw new IllegalStateException("Deadlock retries exhausted");});
            for(var result:pool.invokeAll(calls))if(result.get())accepted++;
        } finally {pool.shutdownNow();}
        assertEquals(99,accepted);assertEquals("100",scalar("SELECT held_quantity FROM dbo.tc_zones WHERE id=?",f.zone));
        assertEquals("100",scalar("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE event_id=? AND status='ACTIVE'",f.event));
    }
}
