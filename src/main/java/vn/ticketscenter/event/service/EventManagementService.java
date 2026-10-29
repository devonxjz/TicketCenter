package vn.ticketscenter.event.service;

import vn.ticketscenter.event.dto.EventCommand;
import vn.ticketscenter.event.dao.EventManagementRepository.*;
import java.util.*;

public interface EventManagementService {
    static void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw EventProblem.invalid("Cần Idempotency-Key hợp lệ (tối đa 128 ký tự).");
        }
    }

    static String redisKey(Actor actor, String scope, String key) {
        return (scope.equals("EVENT_REVIEW") ? "tc:idem:event-review:" : "tc:idem:event:")
                + actor.id()
                + ":"
                + key;
    }

    String read(
            Actor actor,
            boolean admin,
            String kind,
            UUID org,
            UUID event,
            UUID zone,
            String status,
            int page,
            int size);

    Result mutate(Actor actor, String key, EventCommand command);

    void invalidate(Result result);
}
