package vn.ticketscenter.audit.repository;

import org.junit.jupiter.api.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import vn.ticketscenter.audit.dto.AuditFilter;
import vn.ticketscenter.audit.service.AuditService;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.infrastructure.*;
import vn.ticketscenter.settlement.repository.FinanceJdbc;
import static org.junit.jupiter.api.Assertions.*;

/** Actual financial ownership with compatible historical procedure audit payloads; rolled back. */
@Tag("db")
class AuditDatabaseScopeIntegrationTest {
    Connection connection;
    FinanceDatabase database;
    final UUID admin=UUID.randomUUID(),otherActor=UUID.randomUUID(),orgA=UUID.randomUUID(),orgB=UUID.randomUUID();
    final UUID eventA=UUID.randomUUID(),eventB=UUID.randomUUID(),ruleA=UUID.randomUUID(),ruleB=UUID.randomUUID();
    final UUID confirmedA=UUID.randomUUID(),reservedA=UUID.randomUUID(),resultA=UUID.randomUUID(),commissionA=UUID.randomUUID();
    final UUID confirmedB=UUID.randomUUID(),resultB=UUID.randomUUID(),otherActorA=UUID.randomUUID(),malformed=UUID.randomUUID();
    final UUID metadataFreeTransfer=UUID.randomUUID(),metadataFreeCommission=UUID.randomUUID();
    UUID settlementA,payoutA;

    @BeforeEach void seed() throws Exception {
        String selected=System.getProperty("tc.tests.db.name");
        assertNotNull(selected,"Explicit isolated test database required");
        assertTrue(selected.matches("TC_Vuong_Test_[A-Za-z0-9_]+"));
        connection=DriverManager.getConnection(DatabaseConfig.getJdbcUrl(),DatabaseConfig.getUser(),DatabaseConfig.getPassword());
        assertEquals(selected,connection.getCatalog());
        assertTrue(connection.getMetaData().getURL().matches("(?i)^jdbc:sqlserver://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?;.*"),"Loopback fixture required");
        try(var query=connection.prepareStatement("SELECT fixture_key FROM dbo.tc_vuong_test_fixture WHERE fixture_key='vuong-finance-v1'");var rows=query.executeQuery()){assertTrue(rows.next(),"Dedicated finance fixture required");}
        connection.setAutoCommit(false);
        database=()->(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,args)->{
            if(m.getName().equals("close"))return null;
            try{return m.invoke(connection,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });
        for(UUID actor:List.of(admin,otherActor))sql("INSERT dbo.tc_users(id,email,user_name,full_name,platform_role) VALUES(?,?,?,'Audit fixture','ADMIN')",actor,actor+"@test.invalid",actor.toString());
        for(UUID org:List.of(orgA,orgB))sql("INSERT dbo.tc_organizations(id,name,contact_email) VALUES(?,'Audit organization','org@test.invalid')",org);
        for(var terms:List.of(new UUID[]{ruleA,orgA},new UUID[]{ruleB,orgB}))sql("INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES(?,?,5,0,'2020-01-01','2099-01-01')",terms[0],terms[1]);
        UUID[] financialA=financialEvent(eventA,orgA,ruleA),financialB=financialEvent(eventB,orgB,ruleB);
        settlementA=financialA[0];payoutA=financialA[1];
        audit(confirmedA,admin,"SETTLEMENT_CONFIRMED","Settlement",settlementA,"{\"eventId\":\""+eventA+"\"}");
        audit(reservedA,admin,"PAYOUT_RESERVED","SettlementTransferLog",payoutA,"{\"settlementId\":\""+settlementA+"\",\"amount\":40}");
        audit(resultA,admin,"PAYOUT_RESULT","SettlementTransferLog",payoutA,"{\"settlementId\":\""+settlementA+"\",\"amount\":40,\"result\":\"SUCCEEDED\"}");
        audit(commissionA,admin,"COMMISSION_CREATED","CommissionRule",ruleA,"{\"organizationId\":\""+orgA+"\",\"ratePercent\":5}");
        audit(confirmedB,admin,"SETTLEMENT_CONFIRMED","Settlement",financialB[0],"{\"eventId\":\""+eventB+"\"}");
        audit(resultB,admin,"PAYOUT_RESULT","SettlementTransferLog",financialB[1],"{\"settlementId\":\""+financialB[0]+"\"}");
        audit(otherActorA,otherActor,"PAYOUT_RESULT","SettlementTransferLog",payoutA,"{\"settlementId\":\""+settlementA+"\"}");
        audit(malformed,admin,"PAYOUT_RESULT","SettlementTransferLog",UUID.randomUUID(),"{\"settlementId\":\""+settlementA+"\"");
        audit(metadataFreeTransfer,admin,"PAYOUT_RESULT","SettlementTransferLog",payoutA,"{}");
        audit(metadataFreeCommission,admin,"COMMISSION_CREATED","CommissionRule",ruleA,"{}");
    }
    @AfterEach void rollback() throws Exception {if(connection!=null){try{connection.rollback();}finally{connection.close();}}}

    @Test void organizationScopeIncludesSettlementPayoutAndCommissionOwnership() throws Exception {
        var values=filters();values.put("organizationId",orgA.toString());String json=list(values);
        for(UUID expected:List.of(confirmedA,reservedA,resultA,commissionA,metadataFreeTransfer,metadataFreeCommission))assertTrue(json.contains(expected.toString()),"Missing scoped financial audit "+expected);
        for(UUID excluded:List.of(confirmedB,resultB,malformed))assertFalse(json.contains(excluded.toString()),"Unexpected scope "+excluded);
    }
    @Test void eventAndOrganizationTogetherIncludeRelatedPayoutsButNotOrganizationWideRules() throws Exception {
        var values=filters();values.put("eventId",eventA.toString());values.put("organizationId",orgA.toString());String json=list(values);
        for(UUID expected:List.of(confirmedA,reservedA,resultA,metadataFreeTransfer))assertTrue(json.contains(expected.toString()),"Missing event audit "+expected);
        for(UUID excluded:List.of(confirmedB,resultB,commissionA,metadataFreeCommission,malformed))assertFalse(json.contains(excluded.toString()),"Unexpected event audit "+excluded);
        values.put("organizationId",orgB.toString());assertTrue(list(values).contains("\"total\":0"));
    }
    @Test void actorAndTargetTableAliasesCombineWithFinancialScope() throws Exception {
        var values=filters();values.put("organizationId",orgA.toString());values.put("actorId",otherActor.toString());values.put("targetTable","tc_settlement_transfer_logs");String json=list(values);
        assertTrue(json.contains(otherActorA.toString()));for(UUID excluded:List.of(resultA,reservedA,confirmedA,commissionA,malformed))assertFalse(json.contains(excluded.toString()));
        assertTrue(json.contains("\"total\":1"));
    }
    @Test void staleAdministratorStillCannotReadScopedFinancialAudit() throws Exception {
        sql("UPDATE dbo.tc_users SET auth_version=1 WHERE id=?",admin);
        assertEquals(401,assertThrows(FinanceException.class,()->list(filters())).status());
    }
    String list(Map<String,String> values) throws SQLException{return new AuditService(database).list(new UserPrincipal(admin,PlatformRole.ADMIN,0),AuditFilter.parse(values));}
    Map<String,String> filters(){return new HashMap<>(Map.of("from","2026-10-01T00:00:00Z","to","2026-10-02T00:00:00Z","pageSize","100"));}
    UUID[] financialEvent(UUID event,UUID org,UUID rule) throws Exception {
        UUID order=UUID.randomUUID(),payment=UUID.randomUUID(),payout=UUID.randomUUID();
        sql("INSERT dbo.tc_events(id,organization_id,commission_rule_id,title,end_time) VALUES(?,?,?,'Audit event','2020-01-01')",event,org,rule);
        sql("INSERT dbo.tc_orders(id,buyer_id,event_id,order_code,status,subtotal_amount,total_amount,paid_at) VALUES(?,?,?,?,'PAID',100,100,'2026-01-01')",order,admin,event,order.toString());
        sql("INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,paid_at) VALUES(?,?,100,'CAPTURED',?,'2026-01-01')",payment,order,payment.toString());sql("UPDATE dbo.tc_orders SET accepted_payment_id=? WHERE id=?",payment,order);
        var calculated=FinanceJdbc.call(connection,"{call dbo.usp_RecalculateSettlement(?,?,?)}",event,admin,0);UUID settlement=UUID.fromString(calculated.get("settlementId").toString());
        FinanceJdbc.call(connection,"{call dbo.usp_ConfirmSettlement(?,?,?)}",settlement,admin,0);
        FinanceJdbc.call(connection,"{call dbo.usp_RecordPayout(?,?,?,?,?,?,?,?)}",settlement,payout,admin,40,null,null,new byte[32],0);
        FinanceJdbc.call(connection,"{call dbo.usp_RecordPayout(?,?,?,?,?,?,?,?)}",settlement,payout,admin,40,"SIM_AUDIT","SUCCEEDED",new byte[32],0);
        return new UUID[]{settlement,payout};
    }
    void audit(UUID id,UUID actor,String action,String type,UUID entity,String metadata) throws SQLException {sql("INSERT dbo.tc_audit_logs(id,actor_id,action,entity_type,entity_id,metadata_json,created_at) VALUES(?,?,?,?,?,?,'2026-10-01T01:00:00')",id,actor,action,type,entity.toString(),metadata);}
    void sql(String text,Object...values) throws SQLException {try(var query=connection.prepareStatement(text)){FinanceJdbc.bind(query,values);query.executeUpdate();}}
}
