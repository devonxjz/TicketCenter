package vn.ticketscenter.fulfillment.checkin.repository;

import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckInEventRepositoryTest {
    private static final UUID ACTOR=UUID.randomUUID(),ORG=UUID.randomUUID(),EVENT=UUID.randomUUID();
    private final List<Query> queries=new ArrayList<>();
    private final Deque<List<Object[]>> results=new ArrayDeque<>();
    private final Connection connection=proxy(Connection.class,(method,args)->{
        if(!method.equals("prepareStatement"))return null;
        Query query=new Query((String)args[0]);queries.add(query);
        return proxy(PreparedStatement.class,(m,a)->switch(m){
            case "setString","setObject" -> {query.bindings.put((int)a[0],a[1]);yield null;}
            case "setQueryTimeout" -> {query.timeout=(int)a[0];yield null;}
            case "executeQuery" -> rows(results.removeFirst());
            default -> null;
        });
    });
    private final CheckInEventRepository repository=new CheckInEventRepository(transactions());
    @Test void organizationScopeComesFromActiveDatabaseMembershipWithBoundActor(){
        results.add(Collections.singletonList(new Object[]{ORG.toString()}));
        assertEquals(List.of(ORG),repository.authorizedOrganizations(ACTOR));
        Query query=queries.getFirst();assertEquals(ACTOR.toString(),query.bindings.get(1));
        assertTrue(query.sql.contains("m.active=1"));assertTrue(query.sql.contains("u.status='ACTIVE'"));
        assertTrue(query.sql.contains("'MANAGER','CHECK_IN_STAFF'"));assertEquals(5,query.timeout);
    }
    @Test void listBindsOrganizationActorAndUtcDayAndUsesStableOrdering(){
        results.add(List.of());Instant now=Instant.parse("2026-10-09T19:24:00Z");
        assertTrue(repository.accessibleEvents(ACTOR,ORG,now).isEmpty());
        Query query=queries.getFirst();assertEquals(ORG.toString(),query.bindings.get(1));
        assertEquals(LocalDateTime.parse("2026-10-09T00:00:00"),query.bindings.get(2));
        assertEquals(ACTOR.toString(),query.bindings.get(3));assertTrue(query.sql.contains("ORDER BY e.start_time,e.id"));
        assertFalse(query.sql.contains("paid_amount"));assertFalse(query.sql.contains("revenue"));
    }
    @Test void existingEventWithoutMembershipIsForbidden(){
        results.add(Collections.singletonList(eventRow()));results.add(List.of());
        assertEquals(403,assertThrows(CheckInException.class,()->repository.accessibleEvent(ACTOR,EVENT)).httpStatus());
        assertEquals(EVENT.toString(),queries.getFirst().bindings.get(1));
        assertEquals(ACTOR.toString(),queries.get(1).bindings.get(1));assertEquals(ORG.toString(),queries.get(1).bindings.get(2));
    }
    @Test void unknownEventIsNotFoundWithoutQueryingMembership(){
        results.add(List.of());assertEquals(404,assertThrows(CheckInException.class,()->repository.accessibleEvent(ACTOR,EVENT)).httpStatus());
        assertEquals(1,queries.size());
    }
    @Test void timestampsAreUtcRegardlessOfHostTimezone(){
        results.add(Collections.singletonList(eventRow()));results.add(Collections.singletonList(new Object[]{1}));
        var event=repository.accessibleEvent(ACTOR,EVENT);assertEquals(Instant.parse("2026-10-09T19:00:00Z"),event.startTime());
        results.add(Collections.singletonList(new Object[]{true}));assertTrue(repository.isCheckInOpen(EVENT,event.windowStart()));
        Query function=queries.getLast();assertEquals("SELECT dbo.fn_event_is_check_in_open(?,?)",function.sql);
        assertEquals(EVENT.toString(),function.bindings.get(1));assertEquals(LocalDateTime.parse("2026-10-09T18:00:00"),function.bindings.get(2));
    }
    @Test void ticketCountsReadOnlyIdentifiersAndStates(){
        results.add(Collections.singletonList(eventRow()));results.add(Collections.singletonList(new Object[]{1}));
        var event=repository.accessibleEvent(ACTOR,EVENT);results.add(Collections.singletonList(new Object[]{580L,142L}));
        var counted=repository.withTicketCounts(event);assertEquals(580,counted.issued());assertEquals(142,counted.checkedIn());
        Query query=queries.getLast();assertFalse(query.sql.contains("paid_amount"));assertFalse(query.sql.contains("price"));
        assertEquals(EVENT.toString(),query.bindings.get(1));
    }
    @Test void warmWindowGuardBindsActorAndCachedScheduleWithoutReloadingEventLabels(){
        var cached=new CheckInEventRepository.EventInfo(EVENT,ORG,"Title","Venue",Instant.EPOCH,Instant.EPOCH.plusSeconds(7200),"PUBLISHED",0,0);
        results.add(Collections.singletonList(new Object[]{true,true}));
        assertTrue(repository.isCachedWindowCurrent(ACTOR,EVENT,cached));
        Query query=queries.getFirst();assertEquals(1,queries.size());
        assertEquals(ACTOR.toString(),query.bindings.get(1));assertEquals(ORG.toString(),query.bindings.get(2));
        assertEquals(LocalDateTime.ofInstant(cached.startTime(),ZoneOffset.UTC),query.bindings.get(3));
        assertEquals(EVENT.toString(),query.bindings.get(6));
        assertTrue(query.sql.contains("u.status='ACTIVE'"));assertTrue(query.sql.contains("m.active=1"));
        assertFalse(query.sql.contains("e.title"));assertFalse(query.sql.contains("price"));
    }
    @Test void warmWindowGuardCannotAuthorizeRevokedMembershipOrDeletedEvent(){
        var cached=new CheckInEventRepository.EventInfo(EVENT,ORG,"Title","Venue",Instant.EPOCH,Instant.EPOCH.plusSeconds(7200),"PUBLISHED",0,0);
        results.add(Collections.singletonList(new Object[]{false,true}));
        assertEquals(403,assertThrows(CheckInException.class,()->repository.isCachedWindowCurrent(ACTOR,EVENT,cached)).httpStatus());
        results.add(List.of());
        assertEquals(404,assertThrows(CheckInException.class,()->repository.isCachedWindowCurrent(ACTOR,EVENT,cached)).httpStatus());
    }
    @Test void datetimeoffsetPreservesTheActualInstant(){
        var data=eventRow();data[4]=OffsetDateTime.parse("2026-10-10T02:00:00+07:00");
        data[5]=OffsetDateTime.parse("2026-10-10T05:00:00+07:00");
        results.add(Collections.singletonList(data));results.add(Collections.singletonList(new Object[]{1}));
        assertEquals(Instant.parse("2026-10-09T19:00:00Z"),repository.accessibleEvent(ACTOR,EVENT).startTime());
    }
    private Object[] eventRow(){return new Object[]{EVENT.toString(),ORG.toString(),"Đêm nhạc","Nhà hát",LocalDateTime.parse("2026-10-09T19:00:00"),LocalDateTime.parse("2026-10-09T22:00:00"),"PUBLISHED"};}
    private TransactionManager transactions(){
        EntityTransaction transaction=proxy(EntityTransaction.class,(m,a)->m.equals("isActive")?true:null);
        Session session=proxy(Session.class,(m,a)->m.equals("doReturningWork")?((ReturningWork<?>)a[0]).execute(connection):null);
        EntityManager manager=proxy(EntityManager.class,(m,a)->switch(m){case "getTransaction" -> transaction;case "unwrap" -> session;default -> null;});
        EntityManagerFactory factory=proxy(EntityManagerFactory.class,(m,a)->m.equals("createEntityManager")?manager:null);
        return new TransactionManager(Map.of(DatabasePrincipal.CHECK_IN,factory));
    }
    private ResultSet rows(List<Object[]> data){
        int[] cursor={-1};return proxy(ResultSet.class,(m,a)->switch(m){
            case "next" -> ++cursor[0]<data.size();
            case "getString" -> String.valueOf(data.get(cursor[0])[(int)a[0]-1]);
            case "getObject","getBoolean","getLong" -> data.get(cursor[0])[(int)a[0]-1];
            case "getMetaData" -> proxy(ResultSetMetaData.class,(name,arguments)->name.equals("getColumnType")
                    ? (data.get(cursor[0])[(int)arguments[0]-1] instanceof OffsetDateTime ? -155 : Types.TIMESTAMP) : null);
            default -> null;
        });
    }
    private static final class Query {final String sql;final Map<Integer,Object> bindings=new HashMap<>();int timeout;Query(String sql){this.sql=sql;}}
    private interface Call {Object run(String method,Object[] args)throws Throwable;}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,Call call){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(self,method,args)->{
            Object value=call.run(method.getName(),args==null?new Object[0]:args);
            if(value==null&&method.getReturnType()==boolean.class)return false;
            if(value==null&&method.getReturnType()==int.class)return 0;
            return value;
        });
    }
}
