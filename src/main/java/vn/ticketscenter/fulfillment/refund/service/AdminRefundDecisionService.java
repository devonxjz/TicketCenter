package vn.ticketscenter.fulfillment.refund.service;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.repository.AdminRefundStore;
import vn.ticketscenter.identity.security.UserPrincipal;

public final class AdminRefundDecisionService {
    private static final Set<String> STATES = Set.of(
            "REQUESTED", "APPROVED", "PROCESSING", "COMPLETED", "REJECTED", "RETRYABLE", "NEEDS_RECONCILIATION");

    private final AdminRefundStore store;
    private final AdminRefundCache cache;

    public AdminRefundDecisionService(AdminRefundStore store, AdminRefundCache cache) {
        this.store = Objects.requireNonNull(store);
        this.cache = Objects.requireNonNull(cache);
    }

    public AdminRefundPageDto list(UserPrincipal actor, String status, int page, int size) {
        authorize(actor);
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw RefundException.invalid();
        return store.list(canonicalStatus(status), page, size);
    }

    public AdminRefundDetailDto detail(UserPrincipal actor, UUID id) {
        authorize(actor);
        if (id == null) throw RefundException.invalid();
        return store.detail(id);
    }

    public AdminRefundMutationDto decide(UserPrincipal actor, UUID id, AdminRefundDecisionRequestDto request, UUID key) {
        authorize(actor);
        if (id == null || request == null || key == null) throw RefundException.invalid();
        return committed(actor, key, store.decide(actor, id, request, key));
    }

    public AdminRefundMutationDto retry(UserPrincipal actor, UUID id, AdminRefundRetryRequestDto request) {
        authorize(actor);
        if (id == null || request == null) throw RefundException.invalid();
        return committed(actor, request.idempotencyKey(), store.retry(actor, id, request.idempotencyKey()));
    }

    private void authorize(UserPrincipal actor) {
        if (actor == null) throw new RefundException(401, "UNAUTHENTICATED", "Bạn cần đăng nhập.");
        if (!actor.isAdmin()) {
            throw new RefundException(403, "ADMIN_REQUIRED", "Chỉ quản trị viên được phép xử lý hoàn tiền.");
        }
        // Recheck the current status, authVersion and role in SQL.
        store.requireAdmin(actor);
    }

    private static String canonicalStatus(String status) {
        if (status == null || status.isBlank()) return null;
        status = switch (status) {
            case "FAILED" -> "RETRYABLE";
            case "UNKNOWN" -> "NEEDS_RECONCILIATION";
            default -> status;
        };
        if (!STATES.contains(status)) throw RefundException.invalid();
        return status;
    }

    private AdminRefundMutationDto committed(UserPrincipal actor, UUID key, AdminRefundMutationDto result) {
        try {
            cache.committed(actor.actorId(), key, result);
        } catch (RuntimeException ignored) {
            // The store has committed; a cache failure cannot undo the decision.
        }
        return result;
    }
}
