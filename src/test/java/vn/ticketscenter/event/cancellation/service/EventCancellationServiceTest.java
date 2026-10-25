package vn.ticketscenter.event.cancellation.service;

import vn.ticketscenter.event.cancellation.dao.EventCancellationStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.Instant;
import vn.ticketscenter.event.cancellation.dto.*;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import static org.junit.jupiter.api.Assertions.*;

class EventCancellationServiceTest {
    static CancellationProgressDto result() {return new CancellationProgressDto(UUID.randomUUID(),UUID.randomUUID(),"IN_PROGRESS","Thời tiết nguy hiểm",Instant.now(),4,2,null,Instant.now(),0,0,1,1,1);}
    @Test void anonymousAndCustomerCannotInvokeStore() {
        var store=new Store();var service=new EventCancellationServiceImpl(store,new Cache());
        assertEquals(401,assertThrows(RefundException.class,()->service.progress(null,UUID.randomUUID())).status());
        assertEquals(403,assertThrows(RefundException.class,()->service.progress(new UserPrincipal(UUID.randomUUID(),PlatformRole.CUSTOMER,0),UUID.randomUUID())).status());
        assertEquals(0,store.checks);
    }
    @Test void committedCancellationSucceedsDuringRedisOutage() {
        var store=new Store();var service=new EventCancellationServiceImpl(store,new Cache(){@Override public void cancelled(CancellationProgressDto p){assertTrue(store.committed);throw new IllegalStateException("Redis down");}});
        var result=service.cancel(new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,0),store.result.eventId(),new EventCancelRequestDto("Thời tiết nguy hiểm"),UUID.randomUUID());
        assertEquals(store.result.eventId(),result.eventId());assertEquals(1,store.checks);
    }
    @Test void currentSqlAuthorityIsRequiredBeforeEveryProgressResponse() {
        var store=new Store();store.denied=true;var service=new EventCancellationServiceImpl(store,new Cache());
        assertEquals(401,assertThrows(RefundException.class,()->service.progress(new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,0),store.result.eventId())).status());
        assertEquals(0,store.reads);
    }
    @Test void progressSeparatesPreparationFromTransferAndExceptions() {
        var store=new Store();var service=new EventCancellationServiceImpl(store,new Cache());
        var json=service.progress(new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,0),store.result.eventId());
        assertTrue(json.contains("\"progressPercentage\":50.0"));assertTrue(json.contains("\"unknownRefunds\":1"));assertTrue(json.contains("\"usedTicketExceptions\":1"));assertFalse(json.contains("organizationId"));
    }
    static class Cache extends CancellationProgressCache {
        Cache(){super(()->{throw new AssertionError("Unexpected Redis network call");});}
        @Override public void cancelled(CancellationProgressDto p) { }
        @Override public void publish(CancellationProgressDto p) { }
        @Override public Optional<String> get(UUID id) { return Optional.empty(); }
    }
    static class Store implements EventCancellationStore {
        CancellationProgressDto result=result();int checks,reads;boolean committed,denied;
        public void requireAdmin(UserPrincipal a){checks++;if(denied)throw new RefundException(401,"SESSION_EXPIRED","Expired");}
        public CancellationProgressDto cancel(UserPrincipal a,UUID id,EventCancelRequestDto r,UUID key){committed=true;return result;}
        public CancellationProgressDto progress(UserPrincipal a,UUID id){reads++;return result;}
    }
}
