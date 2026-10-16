package vn.ticketscenter.settlement.dao;

import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import vn.ticketscenter.audit.dto.AuditFilter;
import vn.ticketscenter.audit.dao.AuditRepository;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.infrastructure.*;
import vn.ticketscenter.settlement.payout.*;
import vn.ticketscenter.settlement.commission.dto.CommissionRuleCreateDto;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.report.dao.ReportRepository;
import vn.ticketscenter.report.csv.StreamingCsvExporter;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only on a marked, loopback schema-only copy of the team's Azure database. */
@Tag("db")
class FinanceTeamSchemaIntegrationTest {
    Connection connection;
    FinanceDatabase database;
    FinanceRepository repository;
    final UUID admin=UUID.randomUUID(),org=UUID.randomUUID(),event=UUID.randomUUID();
    final UUID category=UUID.randomUUID(),hold=UUID.randomUUID(),rule=UUID.randomUUID(),zone=UUID.randomUUID();
    final UUID order=UUID.randomUUID(),payment=UUID.randomUUID();
    final UserPrincipal principal=new UserPrincipal(admin,PlatformRole.ADMIN,0);

    @BeforeEach void seed() throws Exception {
        String selected=System.getProperty("tc.tests.db.name");
        assertNotNull(selected);assertTrue(selected.matches("TC_Vuong_Test_[A-Za-z0-9_]+"));
        connection=DriverManager.getConnection(DatabaseConfig.getJdbcUrl(),DatabaseConfig.getUser(),DatabaseConfig.getPassword());
        assertEquals(selected,connection.getCatalog());
        assertTrue(connection.getMetaData().getURL().matches("(?i)^jdbc:sqlserver://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?;.*"));
        assertEquals("vuong-team-schema-v1",scalar("SELECT fixture_key FROM dbo.tc_vuong_test_fixture WHERE fixture_key='vuong-team-schema-v1'"));
        database=()->DriverManager.getConnection(DatabaseConfig.getJdbcUrl(),DatabaseConfig.getUser(),DatabaseConfig.getPassword());
        repository=new FinanceRepository(database);
        sql("INSERT dbo.tc_users(id,email,user_name,full_name,password_hash,platform_role) VALUES(?,?,?,'Team test','TEST_ONLY_NOT_A_PASSWORD','ADMIN')",admin,admin+"@test.invalid",admin.toString());
        sql("INSERT dbo.tc_organizations(id,name,contact_email) VALUES(?,'Team test organization','org@test.invalid')",org);
        sql("INSERT dbo.tc_event_categories(id,name,slug) VALUES(?,'Team test',?)",category,category.toString());
        sql("INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES(?,?,5,2,'2020-01-01','2099-01-01')",rule,org);
        sql("INSERT dbo.tc_events(id,organization_id,category_id,commission_rule_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status) VALUES(?,?,?,?,'Team test','','Venue','Address','2019-01-01','2019-02-01','2019-03-01','2019-03-02','DRAFT')",event,org,category,rule);
        sql("INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES(?,?,'Test zone','STANDING',0,100)",zone,event);
        sql("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?",event);
        sql("INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,created_at,expires_at) VALUES(?,?,?,'CONSUMED','2025-01-01','2025-01-02')",hold,admin,event);
        sql("INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,status,subtotal_amount,discount_amount,total_amount,paid_at) VALUES(?,?,?,?,?,'PAID',100,0,100,'2026-01-01')",order,admin,event,hold,order.toString());
        sql("INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,captured_at) VALUES(?,?,100,'CAPTURED',?,'2026-01-01')",payment,order,payment.toString());
        sql("UPDATE dbo.tc_orders SET accepted_payment_id=? WHERE id=?",payment,order);
    }
    @AfterEach void close() throws Exception {if(connection!=null)connection.close();}

    @Test void financeReadsUseLegacyBuyerAndCaptureTimeWithoutRenamingPhysicalColumns() throws Exception {
        assertEquals(admin.toString(),scalar("SELECT CONVERT(varchar(36),buyer_id) FROM dbo.vw_VuongOrders WHERE id=?",order).toString().toLowerCase(Locale.ROOT));
        assertEquals("2026-01-01T00:00:00",scalar("SELECT CONVERT(varchar(30),paid_at,126) FROM dbo.vw_VuongPayments WHERE id=?",payment));
        assertNotNull(scalar("SELECT COL_LENGTH('dbo.tc_orders','user_id')"));
        assertNull(scalar("SELECT COL_LENGTH('dbo.tc_orders','buyer_id')"));
    }
    @Test void adminOverviewAggregatesRealTeamSchemaAndRejectsRevokedAdminBeforeCache() throws Exception {
        var rows=new vn.ticketscenter.dashboard.dao.DashboardRepository().overview(connection,null,Instant.parse("2026-01-01T14:00:00Z"));
        assertEquals(((Number)scalar("SELECT COUNT_BIG(*) FROM dbo.tc_users")).longValue(),rows.get("totalUsers"));
        assertEquals(((Number)scalar("SELECT COUNT_BIG(*) FROM dbo.tc_organizations WHERE status='APPROVED'")).longValue(),rows.get("activeOrganizations"));
        var own=new vn.ticketscenter.dashboard.dao.DashboardRepository().overview(connection,org,Instant.parse("2026-01-01T14:00:00Z"));
        assertEquals(new BigDecimal("100"),own.get("todayCohortGrossRevenue"));assertEquals(new BigDecimal("7"),own.get("todayCohortCommission"));
        assertEquals(7,((java.util.List<?>)rows.get("dailyMetrics")).size());
        var service=new vn.ticketscenter.dashboard.service.DashboardService(database,java.time.Clock.fixed(Instant.parse("2026-01-01T14:00:00Z"),java.time.ZoneOffset.UTC));
        assertTrue(service.admin(principal).contains("\"timezone\":\"UTC\""));
        sql("UPDATE dbo.tc_users SET platform_role='CUSTOMER' WHERE id=?",admin);
        assertEquals(403,assertThrows(FinanceException.class,()->service.admin(principal)).status());
    }
    @Test void actualTeamSettlementAndPayoutKeepExactBalancesAndDurableReplay() throws Exception {
        var summary=repository.recalculate(principal,event);assertEquals(new BigDecimal("93"),summary.get("netPayable"));
        UUID settlement=UUID.fromString(summary.get("settlementId").toString());
        assertEquals("CONFIRMED",repository.confirm(principal,settlement).get("status"));
        var service=new PayoutExecutionService(repository,new SimulatedPayoutAdapter(database,"SUCCEEDED",false));
        UUID key=UUID.randomUUID();var request=new PayoutRequestDto(new BigDecimal("40"),"123456789","TestBank","TestHolder","");
        var paid=service.execute(principal,settlement,key,request);assertEquals(new BigDecimal("40"),paid.get("paidAmount"));assertEquals(new BigDecimal("53"),paid.get("availableToPay"));
        assertEquals(true,service.execute(principal,settlement,key,request).get("isReplay"));
        assertEquals(1,((Number)scalar("SELECT call_count FROM dbo.tc_vuong_simulated_transfers WHERE payout_id=?",key)).intValue());
    }
    @Test void retainedOwnerSummaryAliasesStillCompileAgainstTheNormalizedFinancialView() throws Exception {
        assertEquals(new BigDecimal("100"),scalar("SELECT ticket_gross_amount FROM dbo.vw_OrderFinancialSummary WHERE order_id=?",order));
        assertEquals(new BigDecimal("0"),scalar("SELECT ticket_refund_amount FROM dbo.vw_OrderFinancialSummary WHERE order_id=?",order));
        assertEquals(new BigDecimal("100"),scalar("SELECT remaining_ticket_amount FROM dbo.vw_OrderFinancialSummary WHERE order_id=?",order));
        sql("EXEC sys.sp_refreshsqlmodule N'dbo.usp_CalculateEventSettlement';");
        sql("EXEC sys.sp_refreshsqlmodule N'dbo.usp_ConfirmEventSettlement';");
    }
    @Test void failedCompensationAttemptStopsBlockingOnlyAfterFullVerifiedCompensation() throws Exception {
        UUID capture=UUID.randomUUID(),failed=UUID.randomUUID(),partial=UUID.randomUUID(),remainder=UUID.randomUUID();
        sql("INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,captured_at) VALUES(?,?,100,'CAPTURED',?,'2026-01-01')",capture,order,capture.toString());
        sql("INSERT dbo.tc_refunds(id,payment_id,purpose,amount,status) VALUES(?,?,'PAYMENT_COMPENSATION',100,'FAILED')",failed,capture);
        String blocker="SELECT COUNT(*) FROM dbo.fn_GetSettlementBlockers(?,'2026-02-01') WHERE blocker_type='REFUND' AND object_id=?";
        assertEquals(1,((Number)scalar(blocker,event,failed)).intValue());
        sql("INSERT dbo.tc_refunds(id,payment_id,purpose,amount,status,processed_at) VALUES(?,?,'PAYMENT_COMPENSATION',40,'SUCCEEDED','2026-01-02')",partial,capture);
        assertEquals(1,((Number)scalar(blocker,event,failed)).intValue(),"Partial compensation must not hide unresolved debt");
        sql("INSERT dbo.tc_refunds(id,payment_id,purpose,amount,status,processed_at) VALUES(?,?,'PAYMENT_COMPENSATION',60,'UNKNOWN',NULL)",remainder,capture);
        assertEquals(1,((Number)scalar(blocker,event,failed)).intValue(),"Unknown results must not release blockers");
        sql("UPDATE dbo.tc_refunds SET status='SUCCEEDED',processed_at='2026-01-03' WHERE id=?",remainder);
        assertEquals(0,((Number)scalar(blocker,event,failed)).intValue(),"Full verified compensation supersedes the failed attempt");
        assertEquals(1,((Number)scalar("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE id=? AND status='RETRYABLE'",failed)).intValue(),"Keep the physical failed history");
        assertEquals(new BigDecimal("100"),scalar("SELECT compensation_refund FROM dbo.fn_GetOrganizationCashFlow(?,'2026-01-01','2026-02-01') WHERE event_id=?",org,event));
        assertEquals("CONFIRMED",repository.confirm(principal,UUID.fromString(repository.recalculate(principal,event).get("settlementId").toString())).get("status"));
    }
    @Test void ownerRefundAttemptMirrorIsCountedAsOneObligationAndFreezesAfterConfirm() throws Exception {
        UUID attempt=UUID.randomUUID();
        UUID request=UUID.randomUUID();
        sql("INSERT dbo.tc_refund_requests(id,order_id,requester_id,reason,reason_type,status) VALUES(?,?,?,'Test refund','CUSTOMER_REQUEST','APPROVED')",request,order,admin);
        sql("INSERT dbo.tc_refunds(id,refund_request_id,payment_id,purpose,amount,status,processed_at) VALUES(?,?,?,'CUSTOMER_REFUND',10,'SUCCEEDED','2026-01-02')",attempt,request,payment);
        assertEquals(1,((Number)scalar("SELECT COUNT(*) FROM dbo.vw_VuongRefunds WHERE order_id=?",order)).intValue());
        var summary=repository.recalculate(principal,event);assertEquals(new BigDecimal("10"),summary.get("totalRefund"));assertEquals(new BigDecimal("83"),summary.get("netPayable"));
        UUID settlement=UUID.fromString(summary.get("settlementId").toString());repository.confirm(principal,settlement);
        var error=assertThrows(SQLException.class,()->sql("UPDATE dbo.tc_refunds SET amount=11 WHERE id=?",attempt));assertEquals(52709,error.getErrorCode());
    }
    @Test void legacyAuditHasStableUuidDetailAndRedactsSecretsWithoutRekeyingHistory() throws Exception {
        sql("INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail,created_at) VALUES(?,'TEST_TEAM','Event',?,N'{\"eventId\":\""+event+"\",\"passwordHash\":\"DO_NOT_EXPOSE\"}','2026-01-01')",admin,event);
        var filter=new AuditFilter(Instant.parse("2025-01-01T00:00:00Z"),Instant.parse("2027-01-01T00:00:00Z"),"TEST_TEAM",null,null,org,event,admin,1,50);
        var audit=new AuditRepository();var page=audit.list(connection,filter);
        var rows=(List<?>)page.get("rows");assertEquals(1,rows.size());var row=(Map<?,?>)rows.getFirst();
        UUID id=UUID.fromString(row.get("id").toString());assertEquals(row,audit.detail(connection,id));
        assertFalse(row.toString().contains("DO_NOT_EXPOSE"));
        assertEquals("bigint",scalar("SELECT TYPE_NAME(user_type_id) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_audit_logs') AND name='id'"));
    }
    @Test void commissionReplayAndLinkedImmutabilityUseTheActualTeamTable() {
        var terms=new CommissionRuleCreateDto(new BigDecimal("5"),2,Instant.parse("2020-01-01T00:00:00Z"),Instant.parse("2099-01-01T00:00:00Z"));
        UUID key=UUID.randomUUID();var created=repository.createRule(principal,org,key,terms);
        assertEquals(created.get("ruleId"),repository.createRule(principal,org,key,terms).get("ruleId"));
        assertEquals("RULE_ALREADY_ATTACHED_TO_EVENTS",assertThrows(FinanceException.class,()->repository.updateRule(principal,rule,org,UUID.randomUUID(),terms)).code());
    }
    @Test void constrainedDatabaseRoleCanUseStaticTotalsAndLegacyAuditChainButCannotWriteTables() throws Exception {
        String user="tc_team_"+UUID.randomUUID().toString().replace("-","");
        sql("CREATE USER ["+user+"] WITHOUT LOGIN; ALTER ROLE tc_platform_admin ADD MEMBER ["+user+"];");
        try {
            sql("EXECUTE AS USER='"+user+"';");
            try {
                var result=FinanceJdbc.call(connection,"{call dbo.usp_RecalculateSettlement(?,?,?)}",event,admin,0);
                assertEquals(new BigDecimal("93"),result.get("netPayable"));
                assertEquals(229,assertThrows(SQLException.class,()->sql("UPDATE dbo.tc_settlements SET gross_revenue=0 WHERE event_id=?",event)).getErrorCode());
                assertEquals(229,assertThrows(SQLException.class,()->sql("EXEC dbo.usp_VuongUpdateSettlementTotals ?",UUID.fromString(result.get("settlementId").toString()))).getErrorCode());
            } finally {sql("REVERT;");}
        } finally {sql("DROP USER ["+user+"];");}
    }
    @Test void csvEvaluatesTenThousandRealOrdersWithExactTotalsAndBoundedExportHeap() throws Exception {
        String seed="CREATE TABLE #team_csv_ids(id uniqueidentifier PRIMARY KEY); INSERT #team_csv_ids SELECT TOP (9999) NEWID() FROM sys.all_objects a CROSS JOIN sys.all_objects b; "
            +"INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,created_at,expires_at) SELECT id,?,?,'CONSUMED','2025-01-01','2025-01-02' FROM #team_csv_ids; "
            +"INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,status,subtotal_amount,discount_amount,total_amount,paid_at) SELECT id,?,?,id,'LOAD-'+CONVERT(varchar(36),id),'PAID',100,0,100,'2026-01-01' FROM #team_csv_ids; "
            +"INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at) SELECT NEWID(),id,'LOAD-'+CONVERT(varchar(36),id),100,'CAPTURED','2026-01-01' FROM #team_csv_ids; "
            +"UPDATE o SET accepted_payment_id=p.id FROM dbo.tc_orders o JOIN #team_csv_ids i ON i.id=o.id JOIN dbo.tc_payments p ON p.order_id=o.id;";
        // Bulk fixture construction is not export latency. Production bind timeouts stay unchanged.
        try(var q=connection.prepareStatement(seed)){FinanceJdbc.bind(q,admin,event,admin,event);q.setQueryTimeout(120);q.executeUpdate();}
        assertEquals(10000,((Number)scalar("SELECT COUNT(*) FROM dbo.tc_orders WHERE event_id=?",event)).intValue());
        var filter=new ReportFilter(ReportFilter.Metric.COHORT_REVENUE,org,event,Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2026-02-01T00:00:00Z"),1,20);
        var output=new ByteArrayOutputStream();long before=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        long started=System.nanoTime();
        try(var cursor=new ReportRepository().cursor(connection,filter)){
            StreamingCsvExporter.write(output,cursor.headers(),cursor);
        }
        long after=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        String csv=output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));assertTrue(csv.contains("10000"));assertTrue(csv.contains("1000000"));assertTrue(csv.contains("70000"));assertTrue(csv.contains("930000"));
        assertEquals(3,csv.split("\r\n",-1).length,"This event report has one data row summarizing all 10,000 orders");
        assertTrue(after-before<32L*1024*1024,"Streaming export heap growth must stay below 32 MiB");
        System.out.printf("TEAM_CSV orders=10000 reportRows=1 bytes=%d exportMs=%d heapBefore=%d heapAfter=%d%n",output.size(),(System.nanoTime()-started)/1_000_000,before,after);
    }
    @Test void eventSalesUsesDistinctSuccessfulCheckinsOnActualTeamSchemaForJsonAndCsv()throws Exception{
        UUID item=UUID.randomUUID(),used=UUID.randomUUID(),refunded=UUID.randomUUID(),active=UUID.randomUUID();
        sql("INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES(?,?,?,3,0,'Test zone')",item,order,zone);
        for(UUID ticket:List.of(used,refunded,active))sql("INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status) VALUES(?,?,?,0x01,0,?)",ticket,item,ticket.toString(),ticket.equals(used)?"USED":ticket.equals(refunded)?"REFUNDED":"ACTIVE");
        for(UUID ticket:List.of(used,refunded,used))sql("INSERT dbo.tc_ticket_check_in_logs(event_id,ticket_id,actor_id,result,scanned_at) VALUES(?,?,?,'SUCCESS','2019-03-01')",event,ticket,admin);
        sql("INSERT dbo.tc_ticket_check_in_logs(event_id,ticket_id,actor_id,result,scanned_at) VALUES(?,?,?,'ALREADY_USED','2019-03-01')",event,active,admin);
        var filter=new ReportFilter(ReportFilter.Metric.EVENT_SALES,org,event,Instant.parse("2019-03-01T00:00:00Z"),Instant.parse("2019-03-03T00:00:00Z"),1,1);
        var report=new ReportRepository().page(connection,filter);var summary=(Map<?,?>)report.get("summary");assertEquals(2L,summary.get("ticketsCheckedInCount"));assertEquals(3L,summary.get("ticketCount"));assertEquals(new BigDecimal("100"),summary.get("grossRevenue"));
        String json=new vn.ticketscenter.report.service.ReportService(database,vn.ticketscenter.report.service.ReportCache.none()).json(principal,filter);assertTrue(json.contains("\"ticketsCheckedInCount\":2"),json);
        var output=new ByteArrayOutputStream();try(var cursor=new ReportRepository().cursor(connection,filter)){StreamingCsvExporter.write(output,cursor.headers(),cursor);}
        String csv=output.toString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(csv.contains("\"ticketsCheckedInCount\""));assertTrue(csv.contains("\"3\",\"2\",\"1\",\"2\",\"100\""),csv);
        String reader="tc_report_"+UUID.randomUUID().toString().replace("-","");sql("CREATE USER ["+reader+"] WITHOUT LOGIN; ALTER ROLE tc_manager ADD MEMBER ["+reader+"]; DENY SELECT ON dbo.tc_tickets TO ["+reader+"];");
        try{sql("EXECUTE AS USER='"+reader+"';");try{assertEquals(2L,((Map<?,?>)new ReportRepository().page(connection,filter).get("summary")).get("ticketsCheckedInCount"));}finally{sql("REVERT;");}}finally{sql("DROP USER ["+reader+"];");}
    }
    private void sql(String text,Object... params)throws SQLException{try(var q=connection.prepareStatement(text)){FinanceJdbc.bind(q,params);q.executeUpdate();}}
    private Object scalar(String text,Object... params)throws SQLException{try(var q=connection.prepareStatement(text)){FinanceJdbc.bind(q,params);try(var rows=q.executeQuery()){assertTrue(rows.next());return rows.getObject(1);}}}
}
