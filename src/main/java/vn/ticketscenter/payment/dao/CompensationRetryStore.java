package vn.ticketscenter.payment.dao;

import vn.ticketscenter.identity.security.UserPrincipal;

import java.util.UUID;

/** Starts a new compensation for a payment whose last compensation failed. */
public interface CompensationRetryStore {

    /** @return the id of the new PENDING compensation */
    UUID retry(UserPrincipal actor, UUID payment);
}
