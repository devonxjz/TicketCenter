package vn.ticketscenter.report.service;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
import java.io.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {
    final UUID user=UUID.randomUUID(), org=UUID.randomUUID();
    ReportFilter filter(){return ReportFilter.parse(Map.of("from","2026-10-01T00:00:00Z","to","2026-10-02T00:00:00Z"),org);}
    @Test void deniedMembershipIsCheckedBeforeCacheOrCsvBytes() throws Exception {
        var tracker=new Tracker();tracker.manager=false;var output=new ByteArrayOutputStream();
        var cache=new ReportCache(new ReportCache.Backend(){public String get(String key){fail("cache read before permission");return null;}public void set(String key,String value,int ttl){}public void increment(String key){}});
        var service=new ReportServiceImpl(()->connection(tracker),cache);
        assertEquals(403,assertThrows(FinanceException.class,()->service.json(new UserPrincipal(user,PlatformRole.CUSTOMER,1,OrganizationRole.MANAGER),filter())).status());
        assertThrows(FinanceException.class,()->service.export(new UserPrincipal(user,PlatformRole.CUSTOMER,1),filter(),output));
        assertEquals(0,output.size());assertEquals(0,tracker.reportQueries);
    }
    @Test void staleActorCannotExportEvenWithSessionAdmin() {
        var tracker=new Tracker();tracker.version=2;var output=new ByteArrayOutputStream();
        assertEquals(401,assertThrows(FinanceException.class,()->new ReportServiceImpl(()->connection(tracker),ReportCache.none()).export(new UserPrincipal(user,PlatformRole.ADMIN,1),filter(),output)).status());
        assertEquals(0,output.size());assertEquals(0,tracker.reportQueries);
    }
    @Test void outputFailureClosesCursorStatementAndConnection() {
        var tracker=new Tracker();var output=new OutputStream(){public void write(int b)throws IOException{throw new IOException("disconnect");}};
        assertThrows(IOException.class,()->new ReportServiceImpl(()->connection(tracker),ReportCache.none()).export(new UserPrincipal(user,PlatformRole.CUSTOMER,1),filter(),output));
        assertEquals(1,tracker.reportQueries);assertTrue(tracker.reportCursorClosed);assertTrue(tracker.reportStatementClosed);assertTrue(tracker.connectionClosed);
    }
    @Test void redisFailureFallsBackToAuthorizedDatabase() throws Exception {
        var tracker=new Tracker();var cache=new ReportCache(new ReportCache.Backend(){public String get(String key){throw new IllegalStateException("offline");}public void set(String k,String v,int t){throw new IllegalStateException();}public void increment(String k){throw new IllegalStateException();}});
        assertTrue(new ReportServiceImpl(()->connection(tracker),cache).json(new UserPrincipal(user,PlatformRole.CUSTOMER,1),filter()).contains("COHORT_REVENUE"));assertTrue(tracker.reportQueries>0);
    }
    static class Tracker {boolean manager=true,reportCursorClosed,reportStatementClosed,connectionClosed;int version=1,reportQueries;}
    Connection connection(Tracker tracker){return proxy(Connection.class,(method,args)->{
        if(method.equals("close")){tracker.connectionClosed=true;return null;}
        if(method.equals("prepareStatement")){
            String sql=(String)args[0];boolean users=sql.contains("vw_ActorAuthority"),members=sql.contains("vw_ActiveManagerMemberships"),report=sql.contains("fn_GetOrganizationRevenue");if(report)tracker.reportQueries++;
            return proxy(PreparedStatement.class,(op,a)->{
                if(op.equals("close")){if(report)tracker.reportStatementClosed=true;return null;}
                if(op.equals("executeQuery")){boolean[] first={true};return proxy(ResultSet.class,(name,v)->switch(name){
                    case "next"->{boolean yes=first[0] && (users || members && tracker.manager || sql.startsWith("SELECT COUNT"));first[0]=false;yield yes;}
                    case "getString"->"status".equals(v[0])?"ACTIVE":"CUSTOMER";
                    case "getInt"->tracker.version;case "getLong"->0L;
                    case "close"->{if(report)tracker.reportCursorClosed=true;yield null;}default->null;});}
                return null;});
        }return null;});}
    interface Call{Object call(String method,Object[] args)throws Throwable;}
    @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,Call call){return(T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->call.call(m.getName(),a));}
}
