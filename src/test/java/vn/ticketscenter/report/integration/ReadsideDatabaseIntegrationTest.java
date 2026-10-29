package vn.ticketscenter.report.integration;

import vn.ticketscenter.settlement.service.FinanceException;
import vn.ticketscenter.settlement.dao.FinanceDatabase;
import org.junit.jupiter.api.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.io.*;
import java.time.*;
import java.util.*;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.identity.model.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.report.service.*;
import vn.ticketscenter.audit.dto.AuditFilter;
import vn.ticketscenter.audit.service.AuditService;
import vn.ticketscenter.audit.service.AuditServiceImpl;
import vn.ticketscenter.dashboard.service.DashboardService;
import vn.ticketscenter.dashboard.service.DashboardServiceImpl;
import vn.ticketscenter.dashboard.dao.DashboardRepository;
import vn.ticketscenter.report.cache.ReportCache;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL assertions, transactionally rolled back. No shared DB/SMTP. */
@Tag("db")
class ReadsideDatabaseIntegrationTest {
    Connection connection;FinanceDatabase database;
    final UUID admin=UUID.randomUUID(),manager=UUID.randomUUID(),orgA=UUID.randomUUID(),orgB=UUID.randomUUID(),eventA=UUID.randomUUID(),eventB=UUID.randomUUID(),zeroA=UUID.randomUUID(),rule=UUID.randomUUID(),auditId=UUID.randomUUID();
    UserPrincipal adminActor(){return new UserPrincipal(admin,PlatformRole.ADMIN,0);}
    UserPrincipal managerActor(){return new UserPrincipal(manager,PlatformRole.CUSTOMER,0,OrganizationRole.MANAGER);}
    @BeforeEach void seed()throws Exception{
        connection=DriverManager.getConnection(DatabaseConfig.getJdbcUrl(),DatabaseConfig.getUser(),DatabaseConfig.getPassword());
        String selectedDatabase=System.getProperty("tc.tests.db.name");
        assertNotNull(selectedDatabase,"Explicit isolated test database is required");
        assertTrue(selectedDatabase.matches("TC_Vuong_Test_[A-Za-z0-9_]+"),"Isolated test database prefix is required");
        assertEquals(selectedDatabase,connection.getCatalog());connection.setAutoCommit(false);
        database=()->(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{if(m.getName().equals("close"))return null;try{return m.invoke(connection,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});
        sql("INSERT dbo.tc_users(id,email,user_name,full_name,platform_role) VALUES(?,?,?,'Readside admin','ADMIN')",admin,admin+"@test.invalid",admin.toString());
        sql("INSERT dbo.tc_users(id,email,user_name,full_name,platform_role) VALUES(?,?,?,'Readside manager','CUSTOMER')",manager,manager+"@test.invalid",manager.toString());
        for(UUID org:List.of(orgA,orgB))sql("INSERT dbo.tc_organizations(id,name,contact_email) VALUES(?,'Readside organization','org@test.invalid')",org);
        sql("INSERT dbo.tc_organization_memberships(user_id,organization_id,role) VALUES(?,?,'MANAGER'),(?,?,'CHECK_IN_STAFF')",manager,orgA,manager,orgB);
        sql("INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES(?,?,5,0,'2020-01-01','2099-01-01')",rule,orgA);
        for(UUID event:List.of(eventA,zeroA))sql("INSERT dbo.tc_events(id,organization_id,commission_rule_id,title,end_time) VALUES(?,?,?,'Readside A','2026-10-01T12:00:00')",event,orgA,rule);
        sql("INSERT dbo.tc_events(id,organization_id,title,end_time) VALUES(?,?,'Readside B','2026-10-01T12:00:00')",eventB,orgB);
        UUID current=paidOrder(eventA,100,"2026-10-01T00:00:00"),old=paidOrder(eventA,50,"2026-09-30T23:59:59"),atTo=paidOrder(eventA,5,"2026-10-02T00:00:00");paidOrder(eventB,400,"2026-10-01T12:00:00");
        refund(current,30,"CUSTOMER_REFUND","2026-10-02T00:00:00");refund(old,10,"CUSTOMER_REFUND","2026-10-01T12:00:00");
        UUID extra=UUID.randomUUID();sql("INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,paid_at) VALUES(?,?,20,'CAPTURED',?,'2026-10-01T13:00:00')",extra,current,extra.toString());refund(current,20,"PAYMENT_COMPENSATION","2026-10-01T13:30:00");
        sql("INSERT dbo.tc_audit_logs(id,actor_id,action,entity_type,entity_id,metadata_json,created_at) VALUES(?,?,'TEST_READSIDE','Event',?,?,'2026-10-01T01:00:00')",auditId,admin,eventA.toString(),"{\"eventId\":\""+eventA+"\",\"organizationId\":\""+orgA+"\",\"amount\":\"100\",\"passwordHash\":\"DO_NOT_EXPOSE\",\"otp\":\"123456\"}");
    }
    @AfterEach void rollback()throws Exception{if(connection!=null){try{connection.rollback();}finally{connection.close();}}}
    ReportFilter filter(String metric,UUID org){return ReportFilter.parse(Map.of("metric",metric,"from","2026-10-01T00:00:00Z","to","2026-10-02T00:00:00Z"),org);}
    @Test void cohortCashFlowAndCsvUseCorrectHalfOpenHistoricalDefinitions()throws Exception{
        var service=new ReportServiceImpl(database,ReportCache.none());String cohort=service.json(managerActor(),filter("COHORT_REVENUE",orgA));
        assertTrue(cohort.contains("\"grossRevenue\":\"100\""),cohort);assertTrue(cohort.contains("\"totalRefund\":\"0\""),cohort);assertTrue(cohort.contains(zeroA.toString()),cohort);
        String cash=service.json(managerActor(),filter("CASH_FLOW",orgA));assertTrue(cash.contains("\"ticketCaptured\":\"100\""),cash);assertTrue(cash.contains("\"ticketRefund\":\"10\""),cash);assertTrue(cash.contains("\"compensationCaptured\":\"20\""),cash);assertTrue(cash.contains("\"compensationRefund\":\"20\""),cash);assertTrue(cash.contains("\"netCashFlow\":\"90\""),cash);
        var output=new ByteArrayOutputStream();service.export(managerActor(),filter("CASH_FLOW",orgA),output);String csv=output.toString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(csv.startsWith("\uFEFF"));assertTrue(csv.contains("\"100\",\"20\",\"10\",\"20\",\"90\""),csv);assertFalse(csv.contains(eventB.toString()));
    }
    @Test void exactTenantAndCurrentAuthorityPrecedeAnyOutput()throws Exception{
        var service=new ReportServiceImpl(database,ReportCache.none());var bytes=new ByteArrayOutputStream();assertEquals(403,assertThrows(FinanceException.class,()->service.export(managerActor(),filter("CASH_FLOW",orgB),bytes)).status());assertEquals(0,bytes.size());
        var values=new HashMap<>(Map.of("from","2026-10-01T00:00:00Z","to","2026-10-02T00:00:00Z","eventId",eventB.toString()));assertEquals(404,assertThrows(FinanceException.class,()->service.json(managerActor(),ReportFilter.parse(values,orgA))).status());
        sql("UPDATE dbo.tc_users SET auth_version=1 WHERE id=?",manager);assertEquals(401,assertThrows(FinanceException.class,()->service.export(managerActor(),filter("COHORT_REVENUE",orgA),bytes)).status());assertEquals(0,bytes.size());
    }
    @Test void auditListDetailAreBoundAndRedactedWithStablePagination()throws Exception{
        var service=new AuditServiceImpl(database);var params=new HashMap<>(Map.of("from","2026-10-01T00:00:00Z","to","2026-10-02T00:00:00Z","action","TEST_READSIDE","organizationId",orgA.toString(),"pageSize","1"));
        String rows=service.list(adminActor(),AuditFilter.parse(params));assertTrue(rows.contains(auditId.toString()),rows);assertFalse(rows.contains("DO_NOT_EXPOSE"));assertFalse(rows.contains("123456"));String detail=service.detail(adminActor(),auditId);assertTrue(detail.contains("\"amount\":\"100\""),detail);assertFalse(detail.contains("metadataJson"));assertEquals(403,assertThrows(FinanceException.class,()->service.detail(managerActor(),auditId)).status());
    }
    @Test void actualOverviewAndAllOrganizationAdminScopeComeFromDatabase()throws Exception{
        var overview=new DashboardServiceImpl(database,Clock.fixed(Instant.parse("2026-10-01T14:00:00Z"),ZoneOffset.UTC));String json=overview.admin(adminActor());assertTrue(json.contains("\"draftSettlements\""),json);assertTrue(json.contains("\"todayCashFlow\""),json);
        String organization=overview.organization(managerActor(),orgA);assertTrue(organization.contains("\"eventCount\":2"),organization);
        var bytes=new ByteArrayOutputStream();new ReportServiceImpl(database,ReportCache.none()).export(adminActor(),filter("COHORT_REVENUE",null),bytes);String all=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(all.contains(eventA.toString()),all);assertTrue(all.contains(eventB.toString()),all);
    }
    @Test void currentEventAndSettlementViewsExecuteAgainstFrozenColumns()throws Exception{
        var service=new ReportServiceImpl(database,ReportCache.none());assertTrue(service.json(managerActor(),filter("EVENT_SALES",orgA)).contains(zeroA.toString()));
        sql("EXEC dbo.usp_RecalculateSettlement @event_id=?,@actor_id=?,@emit_result=0",eventA,admin);
        String json=service.json(managerActor(),filter("SETTLEMENT_BALANCE",orgA));assertTrue(json.contains("\"netPayable\":\"109\""),json);
    }
    @Test void organizationOverviewReloadsMembershipAndUserAuthorityAndRejectsMissingOrganization()throws Exception{
        var overview=new DashboardServiceImpl(database,Clock.fixed(Instant.parse("2026-10-01T14:00:00Z"),ZoneOffset.UTC));
        assertEquals(403,assertThrows(FinanceException.class,()->overview.organization(managerActor(),orgB)).status());
        var own=new org.json.JSONObject(overview.organization(managerActor(),orgA)).getJSONObject("data");
        assertEquals(orgA.toString(),own.getString("organizationId"));assertEquals(2,own.getInt("eventCount"));assertEquals(3,own.getInt("paidOrders"));
        assertFalse(own.has("totalUsers"));assertFalse(own.has("activeOrganizations"));assertEquals(7,own.getJSONArray("dailyMetrics").length());
        sql("UPDATE dbo.tc_organization_memberships SET active=0 WHERE user_id=? AND organization_id=?",manager,orgA);
        assertEquals(403,assertThrows(FinanceException.class,()->overview.organization(managerActor(),orgA)).status());
        sql("UPDATE dbo.tc_users SET auth_version=1 WHERE id=?",admin);
        assertEquals(401,assertThrows(FinanceException.class,()->overview.organization(adminActor(),orgA)).status());
        sql("UPDATE dbo.tc_users SET auth_version=0 WHERE id=?",admin);
        assertEquals(404,assertThrows(FinanceException.class,()->overview.organization(adminActor(),UUID.randomUUID())).status());
    }
    @Test void selectorOptionsAndAdminEndpointRespectCurrentTenantAuthority()throws Exception{
        var service=new ReportServiceImpl(database,ReportCache.none());
        String options=service.options(managerActor(),orgA);
        assertTrue(options.contains(orgA.toString()),options);assertTrue(options.contains(eventA.toString()),options);
        assertFalse(options.contains(orgB.toString()),options);assertFalse(options.contains(eventB.toString()),options);
        assertEquals(403,assertThrows(FinanceException.class,()->service.options(managerActor(),orgB)).status());
        assertEquals(403,assertThrows(FinanceException.class,()->service.jsonAdmin(managerActor(),filter("CASH_FLOW",orgA))).status());
        String all=service.options(adminActor(),null);assertTrue(all.contains(orgA.toString()),all);assertTrue(all.contains(orgB.toString()),all);
        sql("UPDATE dbo.tc_organization_memberships SET active=0 WHERE user_id=? AND organization_id=?",manager,orgA);
        assertEquals(403,assertThrows(FinanceException.class,()->service.options(managerActor(),orgA)).status());
    }
    @Test void sevenUtcDaysIncludeZeroDaysAndSeparateCohortFromCashWithoutMoneyPrecisionLoss()throws Exception{
        paidOrder(eventA,new java.math.BigDecimal("9007199254740993"),"2026-09-29T12:00:00");
        var result=new DashboardRepository().overview(connection,orgA,Instant.parse("2026-10-01T14:00:00Z"));
        @SuppressWarnings("unchecked") var days=(List<Map<String,Object>>)result.get("dailyMetrics");
        assertNotNull(days);assertEquals(7,days.size());
        assertEquals("2026-09-25",days.get(0).get("date"));assertEquals("2026-10-01",days.get(6).get("date"));
        assertEquals(Instant.parse("2026-09-25T00:00:00Z"),days.get(0).get("from"));
        assertEquals(Instant.parse("2026-10-02T00:00:00Z"),days.get(6).get("to"));
        assertEquals(new java.math.BigDecimal("0"),days.get(0).get("cohortGrossRevenue"));
        assertEquals(new java.math.BigDecimal("9007199254740993"),days.get(4).get("cohortGrossRevenue"));
        assertEquals(new java.math.BigDecimal("9007199254740993"),days.get(4).get("netCashFlow"));
        assertEquals(new java.math.BigDecimal("100"),days.get(6).get("cohortGrossRevenue"));
        assertEquals(new java.math.BigDecimal("100"),result.get("todayCohortGrossRevenue"));
        assertEquals(new java.math.BigDecimal("90"),days.get(6).get("netCashFlow"));
        String json=new DashboardServiceImpl(database,Clock.fixed(Instant.parse("2026-10-01T14:00:00Z"),ZoneOffset.UTC)).organization(managerActor(),orgA);
        assertTrue(json.contains("\"cohortGrossRevenue\":\"9007199254740993\""),json);
    }
    @Test void adminKpisCountAllUsersAndOnlyApprovedOperationalOrganizations()throws Exception{
        long users,approved;
        try(var q=connection.createStatement();var r=q.executeQuery("SELECT COUNT_BIG(*) FROM dbo.tc_users")){r.next();users=r.getLong(1);}
        try(var q=connection.createStatement();var r=q.executeQuery("SELECT COUNT_BIG(*) FROM dbo.tc_organizations WHERE status='APPROVED'")){r.next();approved=r.getLong(1);}
        UUID disabled=UUID.randomUUID();sql("INSERT dbo.tc_users(id,email,user_name,full_name,status) VALUES(?,?,?,'Inactive dashboard count','DISABLED')",disabled,disabled+"@test.invalid",disabled.toString());
        sql("UPDATE dbo.tc_organizations SET status='REJECTED' WHERE id=?",orgB);
        var result=new DashboardRepository().overview(connection,null,Instant.parse("2026-10-01T14:00:00Z"));
        assertEquals(users+1,result.get("totalUsers"));assertEquals(approved-1,result.get("activeOrganizations"));
    }
    @Test void cachedOverviewStillRejectsRevokedRoleAndStaleAuthVersionUsingActualDatabase()throws Exception{
        String[] value={null};int[] reads={0};
        var cache=new vn.ticketscenter.dashboard.cache.DashboardCache(new vn.ticketscenter.config.redis.RedisKeyValueBackend(){
            public String get(String key){reads[0]++;return value[0];}
            public void setex(String key,int ttl,String json){assertEquals(60,ttl);value[0]=json;}
            public void delete(String key){value[0]=null;}
        });
        var service=new DashboardServiceImpl(database,cache,Clock.fixed(Instant.parse("2026-10-01T14:00:00Z"),ZoneOffset.UTC));
        String first=service.admin(adminActor());assertEquals(first,service.admin(adminActor()));assertEquals(2,reads[0]);
        sql("UPDATE dbo.tc_users SET platform_role='CUSTOMER' WHERE id=?",admin);
        assertEquals(403,assertThrows(FinanceException.class,()->service.admin(adminActor())).status());assertEquals(2,reads[0]);
        sql("UPDATE dbo.tc_users SET platform_role='ADMIN',auth_version=1 WHERE id=?",admin);
        assertEquals(401,assertThrows(FinanceException.class,()->service.admin(adminActor())).status());assertEquals(2,reads[0]);
    }
    @Test void reportSummaryAndTopEventsUseWholeFilteredSetInsteadOfPage()throws Exception{
        var p=new HashMap<>(Map.of("metric","COHORT_REVENUE","from","2026-10-01T00:00:00Z","to","2026-10-02T00:00:00Z","pageSize","1"));
        var data=new vn.ticketscenter.report.dao.ReportRepository().page(connection,ReportFilter.parse(p,orgA));
        var summary=(Map<?,?>)data.get("summary");assertNotNull(summary);assertEquals(2L,summary.get("totalEvents"));
        assertEquals(new java.math.BigDecimal("100"),summary.get("grossRevenue"));assertEquals(new java.math.BigDecimal("5"),summary.get("totalCommission"));assertEquals(new java.math.BigDecimal("95"),summary.get("netPayable"));
        var top=(List<Map<String,Object>>)data.get("topEvents");assertEquals(eventA.toString(),top.get(0).get("eventId"));assertEquals(1,((List<?>)data.get("rows")).size());
        p.put("eventId",zeroA.toString());data=new vn.ticketscenter.report.dao.ReportRepository().page(connection,ReportFilter.parse(p,orgA));summary=(Map<?,?>)data.get("summary");assertEquals(1L,summary.get("totalEvents"));assertEquals(java.math.BigDecimal.ZERO,summary.get("grossRevenue"));
    }
    @Test void checkinCountIncludesPreviouslyUsedRefundedTicketsWithoutMultiplyingMoney()throws Exception{
        UUID order;try(var q=connection.prepareStatement("SELECT TOP(1) id FROM dbo.tc_orders WHERE event_id=? ORDER BY paid_at")){q.setString(1,eventA.toString());try(var r=q.executeQuery()){assertTrue(r.next());order=UUID.fromString(r.getString(1));}}
        UUID item=UUID.randomUUID();sql("INSERT dbo.tc_order_items(id,order_id,quantity,unit_price) VALUES(?,?,3,0)",item,order);
        for(String status:List.of("USED","REFUNDED","ACTIVE")){UUID ticket=UUID.randomUUID();sql("INSERT dbo.tc_tickets(id,order_item_id,ticket_code,status,paid_amount,used_at) VALUES(?,?,?,?,0,?)",ticket,item,ticket.toString(),status,status.equals("ACTIVE")?null:"2026-10-01T12:00:00");}
        var data=new vn.ticketscenter.report.dao.ReportRepository().page(connection,filter("EVENT_SALES",orgA));var summary=(Map<?,?>)data.get("summary");assertNotNull(summary);
        assertEquals(3L,summary.get("ticketCount"));assertEquals(2L,summary.get("ticketsCheckedInCount"));assertEquals(new java.math.BigDecimal("155"),summary.get("grossRevenue"));
        var rows=(List<Map<String,Object>>)data.get("rows");var event=rows.stream().filter(r->r.get("eventId").equals(eventA.toString())).findFirst().orElseThrow();assertEquals(2L,event.get("ticketsCheckedInCount"));
        var out=new ByteArrayOutputStream();new ReportServiceImpl(database,ReportCache.none()).export(managerActor(),filter("EVENT_SALES",orgA),out);assertTrue(out.toString(java.nio.charset.StandardCharsets.UTF_8).contains("\"ticketsCheckedInCount\""));
    }
    @Test void tenThousandEventRowsStreamThroughActualJdbcWithoutCollectingCsv()throws Exception{
        sql("WITH n AS (SELECT TOP (10000) ROW_NUMBER() OVER(ORDER BY (SELECT NULL)) n FROM sys.all_objects a CROSS JOIN sys.all_objects b) INSERT dbo.tc_events(id,organization_id,title,end_time) SELECT NEWID(),?,'Bulk streaming row','2026-10-01T12:00:00' FROM n",orgA);
        class Sink extends OutputStream {long bytes,lines; public void write(int value){bytes++;if(value=='\n')lines++;} public void write(byte[] data,int offset,int length){bytes+=length;for(int i=offset;i<offset+length;i++)if(data[i]=='\n')lines++;}}
        var sink=new Sink();long before=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),started=System.nanoTime();
        new ReportServiceImpl(database,ReportCache.none()).export(managerActor(),filter("EVENT_SALES",orgA),sink);
        assertEquals(10003,sink.lines,"Header plus 10,000 new events and two seeded events; no pagination cutoff");assertTrue(sink.bytes>1_000_000);
        long after=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();assertTrue(after-before<64L*1024*1024,"Actual JDBC export must not retain the whole CSV");
        System.out.printf("REPORT_CSV rows=10002 bytes=%d exportMs=%d heapBefore=%d heapAfter=%d%n",sink.bytes,(System.nanoTime()-started)/1_000_000,before,after);
    }
    UUID paidOrder(UUID event,int amount,String paid)throws Exception{return paidOrder(event,new java.math.BigDecimal(amount),paid);}
    UUID paidOrder(UUID event,java.math.BigDecimal amount,String paid)throws Exception{UUID order=UUID.randomUUID(),payment=UUID.randomUUID();sql("INSERT dbo.tc_orders(id,buyer_id,event_id,order_code,status,subtotal_amount,total_amount,paid_at) VALUES(?,?,?,?,'PAID',?,?,?)",order,manager,event,order.toString(),amount,amount,paid);sql("INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,paid_at) VALUES(?,?,?,'CAPTURED',?,?)",payment,order,amount,payment.toString(),paid);sql("UPDATE dbo.tc_orders SET accepted_payment_id=? WHERE id=?",payment,order);return order;}
    void refund(UUID order,int amount,String purpose,String processed)throws Exception{UUID id=UUID.randomUUID();sql("INSERT dbo.tc_refund_obligations(id,order_id,requester_id,amount,purpose,status,processed_at) VALUES(?,?,?,?,?,'COMPLETED',?)",id,order,purpose.equals("CUSTOMER_REFUND")?admin:null,amount,purpose,processed);sql("INSERT dbo.tc_refund_transfer_logs(id,refund_id,attempt_no,result,processed_at) VALUES(?,?,1,'SUCCEEDED',?)",UUID.randomUUID(),id,processed);}
    void sql(String text,Object...values)throws SQLException{try(var q=connection.prepareStatement(text)){for(int i=0;i<values.length;i++)q.setObject(i+1,values[i] instanceof UUID id?id.toString():values[i]);q.executeUpdate();}}
}
