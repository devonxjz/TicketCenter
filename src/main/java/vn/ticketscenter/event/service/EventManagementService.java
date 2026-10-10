package vn.ticketscenter.event.service;

import vn.ticketscenter.event.cache.*;
import vn.ticketscenter.event.dto.EventCommand;
import vn.ticketscenter.event.repository.EventManagementRepository;
import vn.ticketscenter.event.repository.EventManagementRepository.*;
import java.util.*;

public final class EventManagementService {
    private final EventManagementRepository repository;
    private final EventIdempotencyStore idempotency;
    private final EventCacheInvalidator invalidation;
    public EventManagementService(EventManagementRepository repository,EventIdempotencyStore idempotency,EventCacheInvalidator invalidation) {
        this.repository=repository;this.idempotency=idempotency;this.invalidation=invalidation;
    }
    public String read(Actor actor,boolean admin,String kind,UUID org,UUID event,UUID zone,String status,int page,int size) {
        return repository.read(actor,admin,kind,org,event,zone,status,page,size);
    }
    public Result mutate(Actor actor,String key,EventCommand command) {
        validateKey(key);String hash=command.hash();
        var old=repository.receipt(actor,command.scope(),key,hash);
        if(old.isPresent()) { invalidate(old.get());return old.get(); }
        repository.authorize(actor,command);
        var lease=idempotency.acquire(redisKey(actor,command.scope(),key),hash);
        try {
            var result=repository.command(actor,key,hash,command);
            invalidate(result); try { idempotency.completed(lease,hash,result); } catch(RuntimeException ignored) { } return result;
        } catch(RuntimeException e) { try { idempotency.release(lease); } catch(RuntimeException ignored) { }throw e; }
    }
    public void invalidate(Result result) {
        try { invalidation.invalidateCommitted(result.eventId(),result.organizationId()); }
        catch(RuntimeException ignored) { /* A committed result is never reported as a failed write. */ }
    }
    public static void validateKey(String key) {
        if(key==null || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw EventProblem.invalid("Cần Idempotency-Key hợp lệ (tối đa 128 ký tự).");
    }
    public static String redisKey(Actor actor,String scope,String key) {
        return (scope.equals("EVENT_REVIEW")?"tc:idem:event-review:":"tc:idem:event:")+actor.id()+":"+key;
    }
}
