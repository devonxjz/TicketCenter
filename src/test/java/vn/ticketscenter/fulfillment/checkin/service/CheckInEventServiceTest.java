package vn.ticketscenter.fulfillment.checkin.service;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInEventCache;
import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository;
import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository.EventInfo;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckInEventServiceTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant START = Instant.parse("2026-11-15T19:00:00Z");
    private final FakeRepository repository = new FakeRepository();
    private final MemoryCache cache = new MemoryCache();
    private final MutableClock clock = new MutableClock(START.minusSeconds(3600));
    private final CheckInEventService service = new CheckInEventService(repository, cache, clock);

    @Test void sixtyOneMinutesBeforeStartIsClosed() {
        clock.now=START.minusSeconds(3660);
        var result=service.window(ACTOR,EVENT);
        assertFalse(result.isOpen());assertEquals("NOT_YET_OPEN",result.status());
        assertEquals("Chưa đến thời gian mở cổng soát vé.",result.message());
    }
    @Test void exactlySixtyMinutesBeforeStartIsOpen() {
        var result=service.window(ACTOR,EVENT);
        assertTrue(result.isOpen());assertEquals("OPEN",result.status());
        assertEquals(START.minusSeconds(3600),result.windowStart());assertEquals(clock.now,result.currentServerTime());
    }
    @Test void endTimeIsExclusive() {
        clock.now=repository.event.endTime();
        assertFalse(service.window(ACTOR,EVENT).isOpen());
        assertEquals("ENDED",service.window(ACTOR,EVENT).status());
        clock.now=clock.now.minusNanos(1);assertTrue(service.window(ACTOR,EVENT).isOpen());
    }
    @Test void cachedWindowDoesNotFreezeServerTimeOrOpeningBoundary() {
        clock.now=START.minusSeconds(3601);assertFalse(service.window(ACTOR,EVENT).isOpen());
        clock.now=START.minusSeconds(3600);assertTrue(service.window(ACTOR,EVENT).isOpen());
        assertEquals(clock.now,service.window(ACTOR,EVENT).currentServerTime());
    }
    @Test void anotherOrganizationIsForbiddenBeforeReadingCache() {
        CheckInException error=assertThrows(CheckInException.class,()->service.events(ACTOR,UUID.randomUUID()));
        assertEquals(403,error.status());assertEquals(0,cache.listReads);
    }
    @Test void outsiderCannotReadEventWindow() {
        repository.allowed=false;
        assertEquals(403,assertThrows(CheckInException.class,()->service.window(ACTOR,EVENT)).status());
        assertEquals(1,cache.windowReads); // Internal reads do not authorize or produce a response.
    }
    @Test void noMembershipCannotListAllOrganizations() {
        repository.allowed=false;
        assertEquals(403,assertThrows(CheckInException.class,()->service.events(ACTOR,null)).status());
    }
    @Test void revokedMembershipImmediatelyBlocksPreviouslyCachedWindowAndList() {
        service.window(ACTOR,EVENT);service.events(ACTOR,ORG);repository.allowed=false;
        assertEquals(403,assertThrows(CheckInException.class,()->service.window(ACTOR,EVENT)).status());
        assertEquals(403,assertThrows(CheckInException.class,()->service.events(ACTOR,ORG)).status());
    }
    @Test void cancelledEventRejectsWindowEvenWithAnOpenCacheEntry() {
        service.window(ACTOR,EVENT);repository.status("CANCELLED");
        var error=assertThrows(CheckInException.class,()->service.window(ACTOR,EVENT));
        assertEquals(400,error.status());assertEquals("EVENT_CANCELLED",error.code());
        assertFalse(service.events(ACTOR,ORG).getFirst().isCheckInOpen());
        assertEquals("CANCELLED",service.events(ACTOR,ORG).getFirst().status());
    }
    @Test void unpublishedEventNeverOpens() {
        repository.status("DRAFT");
        var result=service.window(ACTOR,EVENT);assertFalse(result.isOpen());assertEquals("UNAVAILABLE",result.status());
    }
    @Test void missingEventIsNotFound() {
        repository.missing=true;
        assertEquals(404,assertThrows(CheckInException.class,()->service.window(ACTOR,EVENT)).status());
    }
    @Test void scheduleChangeInvalidatesBothCachesAndUsesNewTime() {
        service.window(ACTOR,EVENT);service.events(ACTOR,ORG);
        repository.event=new EventInfo(EVENT,ORG,"Đêm nhạc","Nhà hát",START.plusSeconds(7200),START.plusSeconds(10800),"PUBLISHED",0,0);
        int previous=cache.invalidations;
        assertFalse(service.window(ACTOR,EVENT).isOpen());assertTrue(cache.invalidations>previous);
        assertFalse(service.events(ACTOR,ORG).getFirst().isCheckInOpen());
    }
    @Test void cacheHitReusesTicketCountsWithoutExtendingTtl() {
        assertEquals(580,service.events(ACTOR,ORG).getFirst().totalTicketsIssued());
        assertEquals(142,service.events(ACTOR,ORG).getFirst().totalCheckedIn());
        service.events(ACTOR,ORG);assertEquals(1,repository.countQueries);assertEquals(1,cache.listWrites);
        cache.events.clear();service.events(ACTOR,ORG);assertEquals(2,repository.countQueries);
    }
    @Test void rewritingListRefreshesAllCountersBeforeStartingAnotherTtl() {
        repository.additional=new EventInfo(UUID.randomUUID(),ORG,"Second event","Venue",START,START.plusSeconds(7200),"PUBLISHED",0,0);
        service.events(ACTOR,ORG);assertEquals(2,repository.countQueries);
        repository.issued=581;repository.status("CANCELLED");
        var list=service.events(ACTOR,ORG);
        assertTrue(list.stream().allMatch(e->e.totalTicketsIssued()==581));assertEquals(4,repository.countQueries);
    }
    @Test void staleCachedListCannotRevealEventOutsideCurrentDatabaseScope() {
        service.events(ACTOR,ORG);repository.visible=false;
        assertTrue(service.events(ACTOR,ORG).isEmpty());
    }
    @Test void windowCacheHitAvoidsReloadingProjectionButRechecksDatabaseAndCanonicalFunction() {
        service.window(ACTOR,EVENT);service.window(ACTOR,EVENT);
        assertEquals(1,repository.fullWindowQueries);assertEquals(1,repository.guardQueries);
        assertEquals(2,repository.functionQueries);
    }
    @Test void explicitInvalidationExposesContractForScheduleAndCancellationOwners() {
        service.events(ACTOR,ORG);service.window(ACTOR,EVENT);
        service.invalidateEvent(EVENT,ORG);assertTrue(cache.events.isEmpty());assertTrue(cache.windows.isEmpty());
    }
    @Test void legacyFunctionMismatchFailsClosed() {
        repository.functionOverride=false;
        var error=assertThrows(CheckInException.class,()->service.window(ACTOR,EVENT));
        assertEquals(503,error.status());assertEquals("CHECK_IN_CONTRACT_MISMATCH",error.code());
    }
    @Test void unauthenticatedRequestsNeverReachRepository() {
        assertEquals(401,assertThrows(CheckInException.class,()->service.events(null,ORG)).status());
        assertEquals(401,assertThrows(CheckInException.class,()->service.window(null,EVENT)).status());
        assertEquals(0,repository.accessQueries);
    }

    private static final class FakeRepository extends CheckInEventRepository {
        EventInfo event=new EventInfo(EVENT,ORG,"Đêm nhạc","Nhà hát",START,START.plusSeconds(12600),"PUBLISHED",0,0);
        boolean allowed=true,missing=false,visible=true;int countQueries,accessQueries,fullWindowQueries,guardQueries,functionQueries;Boolean functionOverride;
        EventInfo additional;long issued=580;
        FakeRepository(){super(null);}
        void status(String status){event=new EventInfo(EVENT,ORG,event.title(),event.venue(),event.startTime(),event.endTime(),status,0,0);}
        @Override public List<UUID> authorizedOrganizations(UUID actor){accessQueries++;return allowed?List.of(ORG):List.of();}
        @Override public List<EventInfo> accessibleEvents(UUID actor,UUID org,Instant now){return allowed&&visible?(additional==null?List.of(event):List.of(event,additional)):List.of();}
        @Override public EventInfo accessibleEvent(UUID actor,UUID id){
            accessQueries++;fullWindowQueries++;
            if(missing)throw new CheckInException(404,"EVENT_NOT_FOUND","Sự kiện không tồn tại.");
            if(!allowed)throw new CheckInException(403,"CHECK_IN_FORBIDDEN","Không có quyền.");return event;
        }
        @Override public EventInfo withTicketCounts(EventInfo e){countQueries++;return new EventInfo(e.eventId(),e.organizationId(),e.title(),e.venue(),e.startTime(),e.endTime(),e.status(),issued,142);}
        @Override public boolean isCachedWindowCurrent(UUID actor,UUID id,EventInfo cached){
            accessQueries++;guardQueries++;
            if(missing)throw new CheckInException(404,"EVENT_NOT_FOUND","Không tồn tại.");
            if(!allowed)throw new CheckInException(403,"CHECK_IN_FORBIDDEN","Không có quyền.");
            return event.eventId().equals(cached.eventId()) && event.organizationId().equals(cached.organizationId())
                    && event.startTime().equals(cached.startTime()) && event.endTime().equals(cached.endTime()) && event.status().equals(cached.status());
        }
        @Override public boolean isCheckInOpen(UUID id,Instant now){functionQueries++;return functionOverride==null?event.isOpen(now):functionOverride;}
    }
    private static final class MemoryCache implements CheckInEventCache {
        final Map<UUID,List<EventInfo>> events=new HashMap<>();final Map<UUID,EventInfo> windows=new HashMap<>();
        int listReads,listWrites,windowReads,invalidations;
        public Optional<List<EventInfo>> events(UUID actor,UUID org){listReads++;return Optional.ofNullable(events.get(org));}
        public void putEvents(UUID actor,UUID org,List<EventInfo> value){listWrites++;events.put(org,value);}
        public Optional<EventInfo> window(UUID id){windowReads++;return Optional.ofNullable(windows.get(id));}
        public void putWindow(EventInfo event){windows.put(event.eventId(),event);}
        public void invalidate(UUID id,UUID org){invalidations++;windows.remove(id);events.remove(org);}
    }
    private static final class MutableClock extends Clock {
        Instant now;MutableClock(Instant now){this.now=now;}
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
}
