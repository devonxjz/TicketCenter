package vn.ticketscenter.event.cancellation.service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import vn.ticketscenter.event.cancellation.dto.EventCancelRequestDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

/** SQL owns current authority, durable receipts, state and the atomic approval/cancellation. */
public final class CancellationReviewService {
    @FunctionalInterface
    public interface Store {
        String execute(UserPrincipal actor, UUID eventId, String action, String reason, UUID key);
    }

    private static final Set<String> ACTIONS = Set.of("READ", "REQUEST", "APPROVE", "REJECT");
    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT");
    private final Store store;
    private final Consumer<UUID> approved;

    public CancellationReviewService(Store store, Consumer<UUID> approved) {
        this.store = Objects.requireNonNull(store);
        this.approved = Objects.requireNonNull(approved);
    }

    public String execute(UserPrincipal actor, UUID eventId, String action, String reason, UUID key) {
        if (actor == null) throw new RefundException(401, "AUTH_REQUIRED", "Cần đăng nhập.");
        if (eventId == null || !ACTIONS.contains(action)) throw invalid();
        if (DECISIONS.contains(action) && !actor.isAdmin()) {
            throw new RefundException(403, "ADMIN_REQUIRED", "Chỉ quản trị viên được quyết định yêu cầu hủy.");
        }
        if (!action.equals("READ") && key == null) throw invalid();
        if (action.equals("REQUEST") || action.equals("REJECT")) {
            reason = new EventCancelRequestDto(reason).reason();
        } else if (reason != null) {
            throw invalid();
        }

        String result = store.execute(actor, eventId, action, reason, key);
        if (action.equals("APPROVE")) {
            try {
                approved.accept(eventId);
            } catch (RuntimeException ignored) {
                // Approval has committed; cache invalidation is best effort.
            }
        }
        return result;
    }

    private static RefundException invalid() {
        return new RefundException(400, "CANCELLATION_INVALID", "Thông tin hủy không hợp lệ.");
    }
}
