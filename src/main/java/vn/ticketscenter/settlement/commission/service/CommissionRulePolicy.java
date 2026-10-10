package vn.ticketscenter.settlement.commission.service;

import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.commission.CommissionRuleException;

/** Guards using the authenticated principal and backend-provided event count. */
public final class CommissionRulePolicy {
    private CommissionRulePolicy() {
    }

    public static void requireAdmin(UserPrincipal actor) {
        if (actor == null) {
            throw new CommissionRuleException("AUTHENTICATION_REQUIRED", "Authentication is required.");
        }
        if (!actor.isAdmin()) {
            throw new CommissionRuleException("ADMIN_REQUIRED", "Platform ADMIN role is required.");
        }
    }

    /** The caller must obtain the count inside the transaction protecting the edit. */
    public static void requireEditable(UserPrincipal actor, long associatedEventsCount) {
        requireAdmin(actor);
        if (associatedEventsCount < 0) {
            throw new IllegalStateException("Associated event count must be nonnegative.");
        }
        if (associatedEventsCount > 0) {
            throw new CommissionRuleException("RULE_ALREADY_ATTACHED_TO_EVENTS",
                    "A rule attached to events cannot be edited.");
        }
    }
}
