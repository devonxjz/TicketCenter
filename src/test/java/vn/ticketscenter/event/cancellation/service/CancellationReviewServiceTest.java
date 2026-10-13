package vn.ticketscenter.event.cancellation.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import static org.junit.jupiter.api.Assertions.*;

class CancellationReviewServiceTest {
    private static final UUID EVENT=UUID.randomUUID(),KEY=UUID.randomUUID();
    private static final UserPrincipal ADMIN=new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,4);
    private static final UserPrincipal CUSTOMER=new UserPrincipal(UUID.randomUUID(),PlatformRole.CUSTOMER,4);
    @Test void onlyAdminMayDecideAndAnonymousCannotRead() {
        var calls=new AtomicInteger();var service=new CancellationReviewService((a,e,d,r,k)->{calls.incrementAndGet();return "{}";},e->{});
        assertEquals(401,assertThrows(RefundException.class,()->service.execute(null,EVENT,"READ",null,null)).status());
        for(String action:List.of("APPROVE","REJECT"))assertEquals(403,assertThrows(RefundException.class,()->service.execute(CUSTOMER,EVENT,action,"Dangerous weather conditions",KEY)).status());
        assertEquals(0,calls.get());
    }
    @Test void storeReceivesCurrentActorAndRetryKeyWithoutFakingCompletion() {
        var calls=new AtomicInteger();var invalidations=new AtomicInteger();
        var service=new CancellationReviewService((a,e,d,r,k)->{
            assertSame(ADMIN,a);assertEquals(EVENT,e);assertEquals(KEY,k);assertEquals("REQUEST",d);assertEquals("Dangerous weather conditions",r);calls.incrementAndGet();return "{\"requestStatus\":\"PENDING\"}";
        },e->invalidations.incrementAndGet());
        assertTrue(service.execute(ADMIN,EVENT,"REQUEST","  Dangerous weather conditions  ",KEY).contains("PENDING"));
        service.execute(ADMIN,EVENT,"REQUEST","Dangerous weather conditions",KEY);
        assertEquals(2,calls.get());assertEquals(0,invalidations.get());
    }
    @Test void invalidReasonAndMissingKeyNeverReachDatabase() {
        var service=new CancellationReviewService((a,e,d,r,k)->{fail("Must not mutate");return "{}";},e->{});
        assertThrows(RefundException.class,()->service.execute(ADMIN,EVENT,"REJECT","short",KEY));
        assertThrows(RefundException.class,()->service.execute(ADMIN,EVENT,"REQUEST","Dangerous weather conditions",null));
        assertThrows(RefundException.class,()->service.execute(ADMIN,EVENT,"APPROVE","Caller override",KEY));
    }
    @Test void failureDoesNotInvalidateAndCommittedApprovalSurvivesCacheOutage() {
        var invalidations=new AtomicInteger();
        var failing=new CancellationReviewService((a,e,d,r,k)->{throw new RefundException(409,"CANCELLATION_CONFLICT","Already decided");},e->invalidations.incrementAndGet());
        assertThrows(RefundException.class,()->failing.execute(ADMIN,EVENT,"APPROVE",null,KEY));assertEquals(0,invalidations.get());
        var committed=new CancellationReviewService((a,e,d,r,k)->"{\"requestStatus\":\"APPROVED\"}",e->{throw new IllegalStateException("Cache unavailable");});
        assertTrue(committed.execute(ADMIN,EVENT,"APPROVE",null,KEY).contains("APPROVED"));
    }
}
