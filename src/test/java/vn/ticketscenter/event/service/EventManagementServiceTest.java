package vn.ticketscenter.event.service;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.cache.*;
import vn.ticketscenter.event.dto.EventCommand;
import vn.ticketscenter.event.repository.*;
import vn.ticketscenter.event.repository.EventManagementRepository.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventManagementServiceTest {
    private final UUID id=UUID.randomUUID();
    private final Actor actor=new Actor(id,0);
    private final EventCommand command=new EventCommand("SUBMIT",null,id,null,Map.of());
    private final Result result=new Result(200,"{\"data\":{\"status\":\"PENDING_APPROVAL\"}}",id,id);
    private final List<String> calls=new ArrayList<>();
    private class Repository extends EventManagementRepository {
        boolean replay,fail,forbidden;Repository(){super(null);}
        @Override public Optional<Result> receipt(Actor a,String scope,String key,String hash){calls.add("receipt");return replay?Optional.of(result):Optional.empty();}
        @Override public Context authorize(Actor a,EventCommand c){calls.add("authorize");if(forbidden)throw new EventProblem(403,"FORBIDDEN","Denied");return new Context(id,id);}
        @Override public Result command(Actor a,String key,String hash,EventCommand c){calls.add("commit");if(fail)throw EventProblem.conflict("Rollback");return result;}
    }
    private class Idempotency implements EventIdempotencyStore {
        boolean failCompletion;
        public Lease acquire(String key,String hash){calls.add("acquire");return new Lease(key,"token");}
        public void completed(Lease lease,String hash,Result r){calls.add("completed");if(failCompletion)throw new IllegalStateException("Redis unavailable");}
        public void release(Lease lease){calls.add("release");}
    }
    private EventManagementService service(Repository repository,Idempotency store){return new EventManagementService(repository,store,(e,o)->calls.add("invalidate"));}
    @Test void invalidationHappensOnlyAfterCommitAndSuccessIsPersisted(){assertEquals(result,service(new Repository(),new Idempotency()).mutate(actor,"key",command));assertEquals(List.of("receipt","authorize","acquire","commit","invalidate","completed"),calls);}
    @Test void rollbackReleasesOwnLeaseAndDoesNotInvalidate(){var repo=new Repository();repo.fail=true;assertThrows(EventProblem.class,()->service(repo,new Idempotency()).mutate(actor,"key",command));assertFalse(calls.contains("invalidate"));assertTrue(calls.contains("release"));}
    @Test void forbiddenActorNeverAcquiresLeaseOrWrites(){var repo=new Repository();repo.forbidden=true;assertThrows(EventProblem.class,()->service(repo,new Idempotency()).mutate(actor,"key",command));assertEquals(List.of("receipt","authorize"),calls);}
    @Test void durableReplayAvoidsBusinessWriteAndCanRetryCacheInvalidation(){var repo=new Repository();repo.replay=true;assertEquals(result,service(repo,new Idempotency()).mutate(actor,"key",command));assertEquals(List.of("receipt","invalidate"),calls);}
    @Test void redisCompletionFailureDoesNotTurnCommittedWriteIntoFailure(){var store=new Idempotency();store.failCompletion=true;assertEquals(result,service(new Repository(),store).mutate(actor,"key",command));assertFalse(calls.contains("release"));}
}
