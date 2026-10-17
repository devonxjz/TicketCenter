package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Decides whether a signed-in session is still usable and can revoke all of a user's sessions. */
public class SessionValidator {

    private final TransactionManager transactions;
    private final UserRepository userRepository;
    private final UserSessionCache cache;

    public SessionValidator(
            TransactionManager transactions,
            UserRepository userRepository,
            UserSessionCache cache) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.userRepository =
                Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    public SessionValidator(TransactionManager transactions, UserRepository userRepository) {
        this(transactions, userRepository, UserSessionCache.disabled());
    }

    /**
     * True while the user is ACTIVE and the session's auth version is current. Reads the Redis cache first
     * and falls back to SQL Server on a miss or when Redis fails.
     */
    public boolean isSessionValid(UUID actorId, int expectedAuthVersion) {
        if (actorId == null) {
            return false;
        }
        Optional<UserSessionCache.Entry> cached = cache.get(actorId);
        if (cached.isPresent()) {
            return cached.get().matches(expectedAuthVersion);
        }

        User user =
                transactions.execute(
                        DatabasePrincipal.AUTH,
                        em -> userRepository.findById(em, actorId).orElse(null));
        if (user == null
                || user.getStatus() != UserStatus.ACTIVE
                || user.getAuthVersion() != expectedAuthVersion) {
            cache.evict(actorId);
            return false;
        }
        cache.put(actorId, user.getStatus(), user.getAuthVersion());
        return true;
    }

    /** Invalidates every session of the user by bumping the auth version. */
    public void revokeAllSessions(UUID actorId) {
        if (actorId == null) {
            return;
        }
        transactions.execute(
                DatabasePrincipal.AUTH,
                em -> {
                    User user = userRepository.findById(em, actorId).orElse(null);
                    if (user != null) {
                        userRepository.updateAuthVersion(em, actorId, user.getAuthVersion() + 1);
                        cache.evict(actorId);
                    }
                    return null;
                });
    }
}
