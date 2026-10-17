package vn.ticketscenter.fulfillment.checkin.integration;

import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.cache.RedisCheckInEventCache;
import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository;
import vn.ticketscenter.fulfillment.checkin.service.CheckInEventService;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL Server + real Redis acceptance; explicitly selected, no assumptions/skips. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckInEventAcceptanceIT {
    EntityManagerFactory factory;
    CheckInEventRepository repository;
    final RedisCheckInEventCache cache=new RedisCheckInEventCache(CheckInEventAcceptanceIT::redis);
    UUID actor,manager,outsider,org,event,cancelled,foreign;
    @BeforeAll void connect() {
        assertEquals("TicketCenter_Khanh01_Test",DatabaseConfig.getDatabaseName(),"Mutating acceptance requires dedicated local fixture");
        assertEquals("127.0.0.1",DatabaseConfig.getHost());
        actor=id("ACTOR");manager=id("MANAGER");outsider=id("OUTSIDER");org=id("ORG");event=id("EVENT");cancelled=id("CANCELLED");foreign=id("FOREIGN");
        factory=Persistence.createEntityManagerFactory("ticketscenter",Map.of(
                "jakarta.persistence.jdbc.url",DatabaseConfig.getJdbcUrl(),
                "jakarta.persistence.jdbc.user",AppConfig.get("TC_SQL_TEST_LOGIN"),
                "jakarta.persistence.jdbc.password",AppConfig.get("TC_SQL_TEST_PASSWORD")));
        repository=new CheckInEventRepository(new TransactionManager(Map.of(DatabasePrincipal.CHECK_IN,factory)));
        try(var redis=redis()){assertEquals("PONG",redis.ping());}
    }
    @AfterAll void close(){if(factory!=null)factory.close();}
    @BeforeEach void clearFixtureCache(){cache.invalidate(event,org);}
    @Test void staffAndManagerListOnlyTheirOrganizationAndReadRealTicketCounts() {
        var service=service(Instant.now());
        for(var user:List.of(actor,manager)) {
            var list=service.events(user,org);
            assertFalse(list.isEmpty());assertTrue(list.stream().noneMatch(e->e.eventId().equals(foreign)));
            var summary=list.stream().filter(e->e.eventId().equals(event)).findFirst().orElseThrow();
            assertEquals(3,summary.totalTicketsIssued());assertEquals(1,summary.totalCheckedIn());
        }
    }
    @Test void outsidersAndForeignOrganizationAreDenied() {
        assertError(403,()->service(Instant.now()).events(outsider,org));
        assertError(403,()->service(Instant.now()).window(outsider,event));
        assertError(403,()->service(Instant.now()).window(actor,foreign));
    }
    @Test void canonicalSqlAndServiceAgreeAtBothBoundariesWithWarmCache() {
        var info=repository.accessibleEvent(actor,event);
        assertFalse(service(info.startTime().minusSeconds(3660)).window(actor,event).isOpen());
        assertTrue(service(info.startTime().minusSeconds(3600)).window(actor,event).isOpen());
        assertTrue(service(info.endTime().minusMillis(1)).window(actor,event).isOpen());
        assertFalse(service(info.endTime()).window(actor,event).isOpen());
    }
    @Test void cancelledAndMissingEventsHaveExpectedErrors() {
        assertError(400,()->service(Instant.now()).window(actor,cancelled));
        assertError(404,()->service(Instant.now()).window(actor,UUID.randomUUID()));
    }
    @Test void revokedMembershipCannotUseWarmCache() throws Exception {
        var service=service(repository.accessibleEvent(actor,event).windowStart());
        service.events(actor,org);assertTrue(service.window(actor,event).isOpen());
        change("UPDATE dbo.tc_organization_memberships SET active=0 WHERE user_id=? AND organization_id=?",actor,org);
        try {assertError(403,()->service.events(actor,org));assertError(403,()->service.window(actor,event));}
        finally {change("UPDATE dbo.tc_organization_memberships SET active=1 WHERE user_id=? AND organization_id=?",actor,org);}
    }
    @Test void inactiveUserCannotUseWarmCache() throws Exception {
        var service=service(repository.accessibleEvent(actor,event).windowStart());
        service.events(actor,org);service.window(actor,event);
        change("UPDATE dbo.tc_users SET status='DISABLED' WHERE id=?",actor);
        try {assertError(403,()->service.events(actor,org));assertError(403,()->service.window(actor,event));}
        finally {change("UPDATE dbo.tc_users SET status='ACTIVE' WHERE id=?",actor);}
    }
    @Test void cancellationAndScheduleChangeImmediatelyOverrideWarmCache() throws Exception {
        var info=repository.accessibleEvent(actor,event);var service=service(info.windowStart());
        service.events(actor,org);assertTrue(service.window(actor,event).isOpen());
        change("UPDATE dbo.tc_events SET status='CANCELLED' WHERE id=?",event);
        try {assertError(400,()->service.window(actor,event));assertFalse(service.events(actor,org).stream().filter(e->e.eventId().equals(event)).findFirst().orElseThrow().isCheckInOpen());}
        finally {change("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?",event);}
        service.window(actor,event);
        change("UPDATE dbo.tc_events SET start_time=DATEADD(hour,1,start_time),end_time=DATEADD(hour,1,end_time) WHERE id=?",event);
        try {assertFalse(service.window(actor,event).isOpen());}
        finally {change("UPDATE dbo.tc_events SET start_time=DATEADD(hour,-1,start_time),end_time=DATEADD(hour,-1,end_time) WHERE id=?",event);}
    }
    @Test void redisKeysTtlsInvalidationAndExpiryAreReal() throws Exception {
        var info=repository.withTicketCounts(repository.accessibleEvent(actor,event));
        cache.putEvents(actor,org,List.of(info));cache.putEvents(manager,org,List.of(info));cache.putWindow(info);
        try(var redis=redis()) {
            var listKey=RedisCheckInEventCache.eventsKey(actor,org);var windowKey=RedisCheckInEventCache.windowKey(event);
            assertTrue(redis.ttl(listKey)>0&&redis.ttl(listKey)<=60);
            assertTrue(redis.ttl(windowKey)>0&&redis.ttl(windowKey)<=30);
            String json=redis.get(listKey);assertFalse(json.contains("price"));assertFalse(json.contains("paid_amount"));assertFalse(json.contains("revenue"));
            redis.pexpire(listKey,1);Thread.sleep(15);assertTrue(cache.events(actor,org).isEmpty());
            cache.invalidate(event,org);assertFalse(redis.exists(windowKey));assertFalse(redis.exists(RedisCheckInEventCache.eventsKey(manager,org)));
        }
    }
    @Test void corruptRedisDataFallsBackToRealSql() {
        try(var redis=redis()){redis.setex(RedisCheckInEventCache.eventsKey(actor,org),60,"invalid-json");}
        assertFalse(service(Instant.now()).events(actor,org).isEmpty());
    }
    @Test void checkInPrincipalCannotReadMoneyOrWriteEvents() throws Exception {
        try(var connection=DriverManager.getConnection(DatabaseConfig.getJdbcUrl(),AppConfig.get("TC_SQL_TEST_LOGIN"),AppConfig.get("TC_SQL_TEST_PASSWORD"));var query=connection.createStatement()) {
            assertThrows(SQLException.class,()->query.executeQuery("SELECT paid_amount FROM dbo.tc_tickets"));
            assertThrows(SQLException.class,()->query.executeQuery("SELECT total_amount FROM dbo.tc_orders"));
            assertThrows(SQLException.class,()->query.executeUpdate("UPDATE dbo.tc_events SET status=status WHERE 1=0"));
        }
    }
    static Jedis redis(){return new Jedis(AppConfig.get("redis.host"),AppConfig.getInt("redis.port",16379),1000);}
    CheckInEventService service(Instant now){return new CheckInEventService(repository,cache,Clock.fixed(now,ZoneOffset.UTC));}
    static UUID id(String kind){return UUID.fromString(Objects.requireNonNull(AppConfig.get("TC_CHECKIN_TEST_"+kind+"_ID")));}
    static void assertError(int status,Runnable action){assertEquals(status,assertThrows(CheckInException.class,action::run).httpStatus());}
    static void change(String sql,UUID... ids)throws Exception {
        try(var c=DriverManager.getConnection(DatabaseConfig.getJdbcUrl(),DatabaseConfig.getUser(),DatabaseConfig.getPassword());var q=c.prepareStatement(sql)) {
            for(int i=0;i<ids.length;i++)q.setString(i+1,ids[i].toString());assertEquals(1,q.executeUpdate());
        }
    }
}
