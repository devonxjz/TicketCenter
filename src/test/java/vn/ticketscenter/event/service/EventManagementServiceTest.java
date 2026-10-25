package vn.ticketscenter.event.service;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.cache.*;
import vn.ticketscenter.event.dto.EventCommand;
import vn.ticketscenter.event.dao.*;
import vn.ticketscenter.event.dao.EventManagementRepository.*;
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
    private EventManagementService service(Repository repository,Idempotency store){return new EventManagementServiceImpl(repository,store,(e,o)->calls.add("invalidate"));}
    @Test void committedDeleteSurvivesJournalFailureAndDurableReplayRepairsIt() {
        var deleted=new Result(200,"{\"data\":{\"status\":\"DELETED\"}}",id,id,"{\"publicId\":\"fixture/cover\",\"coverImageUrl\":\"https://fixture.example.test/cover.png\"}");
        var repo=new Repository() {
            @Override public Optional<Result> receipt(Actor a,String scope,String key,String hash){return replay?Optional.of(deleted):Optional.empty();}
            @Override public Result command(Actor a,String key,String hash,EventCommand c){calls.add("commit");replay=true;return deleted;}
        };
        var attempts=new java.util.concurrent.atomic.AtomicInteger();var recorded=new ArrayList<vn.ticketscenter.storage.StoredAsset>();
        var service=new EventManagementServiceImpl(repo,new Idempotency(),(e,o)->calls.add("invalidate"),(e,a)->{
            if(attempts.incrementAndGet()==1)throw new IllegalStateException("Journal unavailable");recorded.add(a);
        });
        var deleting=new EventCommand("DELETE",null,id,null,Map.of());
        assertEquals(deleted,service.mutate(actor,"delete-key",deleting));assertTrue(recorded.isEmpty());
        assertEquals(deleted,service.mutate(actor,"delete-key",deleting));assertEquals(1,recorded.size());assertEquals("fixture/cover",recorded.getFirst().publicId());
        assertEquals(1,calls.stream().filter("commit"::equals).count(),"Cleanup replay must not repeat the business deletion");
    }
    @Test void invalidationHappensOnlyAfterCommitAndSuccessIsPersisted(){assertEquals(result,service(new Repository(),new Idempotency()).mutate(actor,"key",command));assertEquals(List.of("receipt","authorize","acquire","commit","invalidate","completed"),calls);}
    @Test void rollbackReleasesOwnLeaseAndDoesNotInvalidate(){var repo=new Repository();repo.fail=true;assertThrows(EventProblem.class,()->service(repo,new Idempotency()).mutate(actor,"key",command));assertFalse(calls.contains("invalidate"));assertTrue(calls.contains("release"));}
    @Test void forbiddenActorNeverAcquiresLeaseOrWrites(){var repo=new Repository();repo.forbidden=true;assertThrows(EventProblem.class,()->service(repo,new Idempotency()).mutate(actor,"key",command));assertEquals(List.of("receipt","authorize"),calls);}
    @Test void durableReplayAvoidsBusinessWriteAndCanRetryCacheInvalidation(){var repo=new Repository();repo.replay=true;assertEquals(result,service(repo,new Idempotency()).mutate(actor,"key",command));assertEquals(List.of("receipt","invalidate"),calls);}
    @Test void redisCompletionFailureDoesNotTurnCommittedWriteIntoFailure(){var store=new Idempotency();store.failCompletion=true;assertEquals(result,service(new Repository(),store).mutate(actor,"key",command));assertFalse(calls.contains("release"));}
}
