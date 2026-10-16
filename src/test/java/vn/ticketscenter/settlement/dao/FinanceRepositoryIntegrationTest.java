package vn.ticketscenter.settlement.dao;

import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.commission.dto.CommissionRuleCreateDto;
import vn.ticketscenter.settlement.infrastructure.*;
import vn.ticketscenter.settlement.payout.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("db")
class FinanceRepositoryIntegrationTest {
    final FinanceDatabase db=()->DriverManager.getConnection(DatabaseConfig.getJdbcUrl(),DatabaseConfig.getUser(),DatabaseConfig.getPassword());
    UUID actor=UUID.randomUUID(),org=UUID.randomUUID(),event=UUID.randomUUID();
    UserPrincipal principal=new UserPrincipal(actor,PlatformRole.ADMIN,0);
    FinanceRepository repository=new FinanceRepository(db);
    @BeforeEach void seed() throws Exception {
        try(var c=db.open()) {
            if(!c.getCatalog().startsWith("TC_Vuong_Test_")) throw new IllegalStateException("Isolated fixture required");
            sql(c,"INSERT dbo.tc_users(id,email,user_name,full_name,platform_role) VALUES(?,?,?,'JDBC Admin','ADMIN')",actor,actor+"@test.invalid",actor.toString());
            sql(c,"INSERT dbo.tc_organizations(id,name,contact_email) VALUES(?,'JDBC Org','org@test.invalid')",org);
        }
    }
    CommissionRuleCreateDto terms() { return new CommissionRuleCreateDto(new BigDecimal("5"),2,Instant.parse("2020-01-01T00:00:00Z"),Instant.parse("2099-01-01T00:00:00Z")); }
    void paidEvent(UUID rule) throws Exception {
        UUID order=UUID.randomUUID(),payment=UUID.randomUUID();
        try(var c=db.open()) {
            sql(c,"INSERT dbo.tc_events(id,organization_id,commission_rule_id,title,end_time) VALUES(?,?,?,'JDBC Event','2020-01-01')",event,org,rule);
            sql(c,"INSERT dbo.tc_orders(id,buyer_id,event_id,order_code,status,subtotal_amount,total_amount,paid_at) VALUES(?,?,?,?,'PAID',100,100,'2026-01-01')",order,actor,event,order.toString());
            sql(c,"INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,paid_at) VALUES(?,?,100,'CAPTURED',?,'2026-01-01')",payment,order,payment.toString());
            sql(c,"UPDATE dbo.tc_orders SET accepted_payment_id=? WHERE id=?",payment,order);
        }
    }
    @Test void commissionReplayAndEventLinkedEditAreDurable() throws Exception {
        UUID key=UUID.randomUUID(); var first=repository.createRule(principal,org,key,terms());
        var replay=repository.createRule(principal,org,key,terms()); assertEquals(first.get("ruleId"),replay.get("ruleId")); assertEquals(true,replay.get("isReplay"));
        UUID rule=UUID.fromString(first.get("ruleId").toString()); paidEvent(rule);
        assertEquals("RULE_ALREADY_ATTACHED_TO_EVENTS",assertThrows(FinanceException.class,()->repository.updateRule(principal,rule,org,UUID.randomUUID(),terms())).code());
    }
    @Test void recalcConfirmAndPartialPayoutUseStoredExactBalances() throws Exception {
        UUID rule=UUID.fromString(repository.createRule(principal,org,UUID.randomUUID(),terms()).get("ruleId").toString()); paidEvent(rule);
        var summary=repository.recalculate(principal,event); assertEquals(new BigDecimal("93"),summary.get("netPayable"));
        UUID sid=UUID.fromString(summary.get("settlementId").toString());
        assertEquals(Instant.parse("2020-01-01T00:00:00Z"),repository.summary(principal,event).get("endTime"));
        assertEquals("CONFIRMED",repository.confirm(principal,sid).get("status"));
        assertEquals(true,repository.confirm(principal,sid).get("isReplay"));
        var service=new PayoutExecutionService(repository,new SimulatedPayoutAdapter(db,"SUCCEEDED",false));
        UUID key=UUID.randomUUID(); var request=new PayoutRequestDto(new BigDecimal("40"),"123456789","TestBank","TestHolder","");
        var paid=service.execute(principal,sid,key,request); assertEquals("SUCCEEDED",paid.get("status"));
        assertEquals(new BigDecimal("40"),paid.get("paidAmount")); assertEquals(new BigDecimal("53"),paid.get("availableToPay"));
        assertEquals(true,service.execute(principal,sid,key,request).get("isReplay"));
        try(var c=db.open(); var q=c.prepareStatement("SELECT call_count FROM dbo.tc_vuong_simulated_transfers WHERE payout_id=?")) {
            FinanceJdbc.bind(q,key); try(var rows=q.executeQuery()) { assertTrue(rows.next()); assertEquals(1,rows.getInt(1)); }
        }
    }
    @Test void providerTimeoutAfterDurableSuccessKeepsUnknownUntilReconciliation() throws Exception {
        UUID rule=UUID.fromString(repository.createRule(principal,org,UUID.randomUUID(),terms()).get("ruleId").toString()); paidEvent(rule);
        UUID sid=UUID.fromString(repository.recalculate(principal,event).get("settlementId").toString()); repository.confirm(principal,sid);
        var service=new PayoutExecutionService(repository,new SimulatedPayoutAdapter(db,"SUCCEEDED",true));
        var result=service.execute(principal,sid,UUID.randomUUID(),new PayoutRequestDto(new BigDecimal("40"),"123","TestBank","TestHolder",""));
        assertEquals("PENDING",result.get("status")); assertEquals("UNKNOWN",result.get("verificationState")); assertEquals(new BigDecimal("40"),result.get("pendingAmount"));
        assertEquals("PAYOUT_RECONCILIATION_REQUIRED",assertThrows(FinanceException.class,()->service.execute(principal,sid,UUID.randomUUID(),new PayoutRequestDto(new BigDecimal("40"),"123","TestBank","TestHolder",""))).code());
        UUID payoutId=UUID.fromString(result.get("payoutId").toString());
        // Recovery after a new page/process does not depend on retaining bank terms in memory.
        assertEquals("NOT_FOUND",assertThrows(FinanceException.class,()->repository.payoutForReconciliation(principal,sid,UUID.randomUUID())).code());
        var verified=new vn.ticketscenter.settlement.service.FinanceMutationService(repository,new SimulatedPayoutAdapter(db,"SUCCEEDED",false)).reconcile(principal,sid,payoutId,null);
        assertEquals("SUCCEEDED",verified.get("status")); assertEquals(new BigDecimal("40"),verified.get("paidAmount")); assertEquals(new BigDecimal("0"),verified.get("pendingAmount"));
    }
    @Test void staleSessionDeniedAndNoPaidOrdersCreateNoHeader() throws Exception {
        try(var c=db.open()) { sql(c,"INSERT dbo.tc_events(id,organization_id,title,end_time) VALUES(?,?,'Empty Event','2020-01-01')",event,org); }
        var zero=repository.recalculate(principal,event); assertNull(zero.get("settlementId")); assertEquals(new BigDecimal("0"),zero.get("netPayable"));
        try(var c=db.open()) { sql(c,"UPDATE dbo.tc_users SET auth_version=1 WHERE id=?",actor); }
        assertEquals(401,assertThrows(FinanceException.class,()->repository.rules(principal,org)).status());
    }
    @Test void resultWriteFailureCannotBeReplacedWithANewTransfer() throws Exception {
        UUID rule=UUID.fromString(repository.createRule(principal,org,UUID.randomUUID(),terms()).get("ruleId").toString());paidEvent(rule);
        UUID sid=UUID.fromString(repository.recalculate(principal,event).get("settlementId").toString());repository.confirm(principal,sid);
        var provider=new SimulatedPayoutAdapter(db,"SUCCEEDED",false);
        PayoutStore interruptedStore=new PayoutStore() {
            public Map<String,Object> existing(UserPrincipal actor,UUID settlement,UUID payout,PayoutRequestDto request){return repository.existing(actor,settlement,payout,request);}
            public Map<String,Object> reserve(UserPrincipal actor,UUID settlement,UUID payout,PayoutRequestDto request){return repository.reserve(actor,settlement,payout,request);}
            public Map<String,Object> record(UserPrincipal actor,UUID settlement,UUID payout,PayoutRequestDto request,PayoutAdapter.Result verified)throws SQLException{throw new SQLException("Injected result write interruption");}
        };
        UUID payout=UUID.randomUUID();var request=new PayoutRequestDto(new BigDecimal("40"),"123","TestBank","TestHolder","");
        assertThrows(SQLException.class,()->new PayoutExecutionService(interruptedStore,provider).execute(principal,sid,payout,request));
        assertEquals("PENDING",repository.existing(principal,sid,payout,request).get("verificationState"));
        assertEquals("PAYOUT_RECONCILIATION_REQUIRED",assertThrows(FinanceException.class,()->new PayoutExecutionService(repository,provider).execute(principal,sid,UUID.randomUUID(),request)).code());
        var recovered=new vn.ticketscenter.settlement.service.FinanceMutationService(repository,provider).reconcile(principal,sid,payout,null);
        assertEquals("SUCCEEDED",recovered.get("status"));assertEquals(new BigDecimal("40"),recovered.get("paidAmount"));assertEquals(new BigDecimal("0"),recovered.get("pendingAmount"));
        try(var c=db.open();var q=c.prepareStatement("SELECT call_count FROM dbo.tc_vuong_simulated_transfers WHERE payout_id=?")) {
            FinanceJdbc.bind(q,payout);try(var rows=q.executeQuery()){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
        }
    }
    @Test void selectorOptionsComeFromCurrentAuthorizedDatabase() {
        var options=repository.options(principal);
        @SuppressWarnings("unchecked") var organizations=(List<Map<String,Object>>)options.get("organizations");
        assertTrue(organizations.stream().anyMatch(row->row.get("id").toString().equalsIgnoreCase(org.toString())));
    }
    @Test void settlementSelectorExcludesFutureEventsAndReadyListExcludesConfirmedEvents() throws Exception {
        UUID rule=UUID.fromString(repository.createRule(principal,org,UUID.randomUUID(),terms()).get("ruleId").toString()); paidEvent(rule);
        UUID future=UUID.randomUUID();
        try(var c=db.open()) {sql(c,"INSERT dbo.tc_events(id,organization_id,title,end_time) VALUES(?,?,'Future Event','2099-01-01')",future,org);}
        @SuppressWarnings("unchecked") var ended=(List<Map<String,Object>>)repository.options(principal,true).get("events");
        assertTrue(ended.stream().anyMatch(row->row.get("id").toString().equalsIgnoreCase(event.toString())));
        assertFalse(ended.stream().anyMatch(row->row.get("id").toString().equalsIgnoreCase(future.toString())));
        @SuppressWarnings("unchecked") var all=(List<Map<String,Object>>)repository.options(principal).get("events");
        assertTrue(all.stream().anyMatch(row->row.get("id").toString().equalsIgnoreCase(future.toString())));
        assertTrue(repository.readySettlementEvents(principal).stream().anyMatch(row->row.get("id").toString().equalsIgnoreCase(event.toString())));
        UUID sid=UUID.fromString(repository.recalculate(principal,event).get("settlementId").toString());repository.confirm(principal,sid);
        assertFalse(repository.readySettlementEvents(principal).stream().anyMatch(row->row.get("id").toString().equalsIgnoreCase(event.toString())));
    }
    @Test void cachedEventReadsInvalidateAfterCommitAndRefuseStaleAuthority() throws Exception {
        UUID rule=UUID.fromString(repository.createRule(principal,org,UUID.randomUUID(),terms()).get("ruleId").toString());paidEvent(rule);
        var cached=new HashMap<String,String>();
        var cache=new vn.ticketscenter.settlement.service.SettlementReadCache(new vn.ticketscenter.settlement.service.SettlementReadCache.Backend(){
            public String get(String k){return cached.get(k);}public void put(String k,String v,int ttl){cached.put(k,v);}public void delete(String k){cached.remove(k);}
        });
        var mutations=new FinanceRepository(db,id->{},cache::invalidate);
        var service=new vn.ticketscenter.settlement.service.FinanceMutationService(mutations,PayoutAdapter.unavailable(),vn.ticketscenter.settlement.commission.service.CommissionRuleCache.none(),cache);
        assertTrue(service.readJson(principal,"summary",event).contains("\"settlementId\":null"));service.readJson(principal,"blockers",event);assertEquals(2,cached.size());
        UUID sid=UUID.fromString(mutations.recalculate(principal,event).get("settlementId").toString());assertTrue(cached.isEmpty());
        service.readJson(principal,"summary",event);mutations.confirm(principal,sid);assertTrue(cached.isEmpty());
        assertTrue(service.readJson(principal,"summary",event).contains("CONFIRMED"));assertEquals(1,cached.size());
        try(var c=db.open()){sql(c,"UPDATE dbo.tc_users SET auth_version=1 WHERE id=?",actor);}
        assertEquals(401,assertThrows(FinanceException.class,()->service.readJson(principal,"summary",event)).status());
    }
    private static void sql(Connection c,String sql,Object... values) throws SQLException {
        try(var q=c.prepareStatement(sql)) { FinanceJdbc.bind(q,values); q.executeUpdate(); }
    }
}
