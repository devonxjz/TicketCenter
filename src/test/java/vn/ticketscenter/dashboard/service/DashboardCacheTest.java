package vn.ticketscenter.dashboard.service;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.infrastructure.*;
import static org.junit.jupiter.api.Assertions.*;

class DashboardCacheTest {
    final UserPrincipal admin=new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,0);
    final Clock clock=Clock.fixed(Instant.parse("2026-10-01T14:00:00Z"),ZoneOffset.UTC);
    @Test void cacheMissLoadsSqlAndStoresSixtySecondGlobalProjectionThenHitReloadsAuthority()throws Exception{
        var db=new Sql();var store=new Memory();var service=new DashboardServiceImpl(db::connection,new DashboardCache(store),clock);
        String first=service.admin(admin);int readQueries=db.aggregations;
        assertTrue(first.contains("\"totalUsers\":7"),first);
        assertEquals(first,store.values.get("tc:admin:overview:v1"));assertEquals(60,store.ttl);
        assertEquals(first,service.admin(admin));assertEquals(2,db.authorizations);assertEquals(readQueries,db.aggregations);
        assertEquals(2,db.closed);
    }
    @Test void staleAuthorityCannotUsePreviouslyCachedAdminPayload(){
        var db=new Sql();db.version=1;var store=new Memory();store.values.put("tc:admin:overview:v1","secret cached projection");
        assertEquals(401,assertThrows(FinanceException.class,()->new DashboardServiceImpl(db::connection,new DashboardCache(store),clock).admin(admin)).status());
        assertEquals(0,store.reads);assertEquals(0,db.aggregations);
    }
    @Test void demotedAdminCannotReadGlobalCache(){
        var db=new Sql();db.role="CUSTOMER";var store=new Memory();store.values.put("tc:admin:overview:v1","secret cached projection");
        assertEquals(403,assertThrows(FinanceException.class,()->new DashboardServiceImpl(db::connection,new DashboardCache(store),clock).admin(admin)).status());
        assertEquals(0,store.reads);assertEquals(0,db.aggregations);
    }
    @Test void cacheOutageFallsBackToAuthorizedSqlIncludingChart()throws Exception{
        var db=new Sql();DashboardCache.Backend offline=new DashboardCache.Backend(){
            public String get(String key){throw new IllegalStateException("offline");}
            public void set(String key,String value,int ttl){throw new IllegalStateException("offline");}
            public void delete(String key){throw new IllegalStateException("offline");}
        };
        String result=new DashboardServiceImpl(db::connection,new DashboardCache(offline),clock).admin(admin);
        assertTrue(result.contains("\"dailyMetrics\""),result);assertTrue(result.contains("\"activeOrganizations\":7"),result);
        assertEquals(1,db.authorizations);assertTrue(db.aggregations>0);
    }
    @Test void invalidationDropsPreviousProjection(){
        var store=new Memory();var cache=new DashboardCache(store);cache.put("old");cache.invalidate();assertNull(cache.get());
    }
    @Test void organizationOverviewBypassesGlobalAdminCache()throws Exception{
        var db=new Sql();var store=new Memory();store.values.put("tc:admin:overview:v1","wrong global projection");
        String result=new DashboardServiceImpl(db::connection,new DashboardCache(store),clock).organization(admin,UUID.randomUUID());
        assertFalse(result.contains("wrong global projection"));assertTrue(result.contains("\"eventCount\":7"),result);assertEquals(0,store.reads);
    }
    static final class Memory implements DashboardCache.Backend {
        final Map<String,String> values=new HashMap<>();int ttl,reads;
        public String get(String key){reads++;return values.get(key);}
        public void set(String key,String value,int ttl){this.ttl=ttl;values.put(key,value);}
        public void delete(String key){values.remove(key);}
    }
    static final class Sql {
        int authorizations,aggregations,closed,version;String role="ADMIN";boolean zeroRecent;
        Connection connection(){return proxy(Connection.class,(name,args)-> {
            if(name.equals("close")){closed++;return null;}
            if(!name.equals("prepareStatement"))return null;
            boolean auth=((String)args[0]).startsWith("SELECT status,auth_version");
            if(auth)authorizations++;else aggregations++;
            Instant[] from={null};
            return proxy(PreparedStatement.class,(op,values)-> {
                if(op.equals("setTimestamp") && (int)values[0]==2){from[0]=((Timestamp)values[1]).toInstant();return null;}
                if(!op.equals("executeQuery"))return null;
                boolean[] first={true};
                return proxy(ResultSet.class,(method,columns)->switch(method){
                    case "next"->{boolean result=first[0];first[0]=false;yield result;}
                    case "getString"->"status".equals(columns[0])?"ACTIVE":role;
                    case "getInt"->version;case "getLong"->7L;case "getBigDecimal"->zeroRecent&&!Instant.EPOCH.equals(from[0])?BigDecimal.ZERO:new BigDecimal("10");default->null;
                });
            });
        });}
    }
    @Test void historicalRevenueRemainsVisibleWhenRecentPeriodHasNoTransactions()throws Exception{
        var db=new Sql();db.zeroRecent=true;
        String result=new DashboardServiceImpl(db::connection,clock).admin(admin);
        assertTrue(result.contains("\"todayCohortGrossRevenue\":\"0\""),result);
        assertTrue(result.contains("\"cumulativeGrossRevenue\":\"10\""),result);
        assertTrue(result.contains("\"cumulativeNetCashFlow\":\"10\""),result);
    }
    interface Call{Object invoke(String name,Object[] args)throws Throwable;}
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,Call call){return(T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->call.invoke(m.getName(),a));}
}
