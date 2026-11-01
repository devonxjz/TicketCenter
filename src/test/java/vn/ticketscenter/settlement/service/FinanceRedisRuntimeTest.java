package vn.ticketscenter.settlement.service;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.dashboard.cache.DashboardCache;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.report.cache.ReportCache;
import vn.ticketscenter.settlement.cache.SettlementReadCache;
import vn.ticketscenter.settlement.commission.cache.CommissionRuleCache;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit standalone ephemeral Redis only; shuts down this marked fixture after assertions. */
@EnabledIfSystemProperty(named="tc.tests.redis",matches="true")
class FinanceRedisRuntimeTest {
    @Test void actualRedisKeysTtlsInvalidationAndServerOutage() {
        assertEquals("127.0.0.1",System.getProperty("redis.host"),"Explicit loopback fixture required");
        String port=System.getProperty("redis.port");
        assertNotNull(port);assertEquals(port,System.getenv("TC_REDIS_TEST_PORT"));
        assertTrue(Integer.parseInt(port)>1024);
        var pool=RedisClientProvider.getInstance();
        UUID org=UUID.randomUUID(),event=UUID.randomUUID();
        var commission=CommissionRuleCache.redis();var settlement=SettlementReadCache.redis();
        var dashboard=DashboardCache.redis();var report=ReportCache.redis();
        var filter=new ReportFilter(ReportFilter.Metric.COHORT_REVENUE,org,null,Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2026-02-01T00:00:00Z"),1,20);
        var actor=new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,0);
        String commissionKey="tc:commission:rules:v1:"+org,summaryKey="tc:settlement:summary:v1:"+event,blockerKey="tc:settlement:blockers:v1:"+event;
        try(var redis=pool.getResource()) {
            assertEquals("vuong-finance-v1",redis.get("tc:fixture:vuong-finance-v1"),"Standalone test marker required before writes/shutdown");
            assertNull(redis.get("tc:admin:overview:v1"),"Use a fresh dedicated Redis process");
            assertTrue(redis.info("server").contains("redis_version:"));
        }
        assertEquals("[]",commission.read(org,()->"[]"));
        assertEquals("[]",commission.read(org,()->{throw new AssertionError("Expected actual Redis hit");}));
        settlement.read(event,SettlementReadCache.Kind.SUMMARY,()->"{\"netPayable\":\"93\"}");
        settlement.read(event,SettlementReadCache.Kind.BLOCKERS,()->"[]");dashboard.put("{\"data\":{}}");
        String reportKey=report.key(actor,filter);assertNotNull(reportKey);report.put(reportKey,"{\"rows\":[]}");
        assertEquals("{\"rows\":[]}",report.get(reportKey));assertEquals("{\"data\":{}}",dashboard.get());
        try(var redis=pool.getResource()) {
            assertTtl(redis.ttl(commissionKey),300);assertTtl(redis.ttl(summaryKey),60);assertTtl(redis.ttl(blockerKey),30);
            assertTtl(redis.ttl("tc:admin:overview:v1"),60);assertTtl(redis.ttl(reportKey),60);
        }
        commission.invalidate(org);settlement.invalidate(event);DashboardCache.invalidateAdminAfterCommit();ReportCache.invalidateAfterCommit(org);
        assertNotEquals(reportKey,report.key(actor,filter));assertNull(dashboard.get());
        try(var redis=pool.getResource()) {
            assertNull(redis.get(commissionKey));assertNull(redis.get(summaryKey));assertNull(redis.get(blockerKey));
            redis.shutdown(); // No persistence; only the explicitly marked standalone fixture.
        }
        assertEquals("SQL rules",commission.read(org,()->"SQL rules"));
        assertEquals("SQL summary",settlement.read(event,SettlementReadCache.Kind.SUMMARY,()->"SQL summary"));
        assertNull(dashboard.get());dashboard.put("ignored");dashboard.invalidate();
        assertNull(report.key(actor,filter));report.put(reportKey,"ignored");assertNull(report.get(reportKey));
        ReportCache.invalidateAfterCommit(org);pool.close();
    }
    private static void assertTtl(long actual,int expected){assertTrue(actual>0 && actual<=expected,"Bounded actual server TTL");}
}
