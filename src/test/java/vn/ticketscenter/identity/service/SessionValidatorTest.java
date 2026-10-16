package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cache hit must decide the answer without touching SQL; the empty TransactionManager would fail if it did. */
class SessionValidatorTest {

    private static SessionValidator validatorWithCached(UserSessionCache.Entry entry) {
        UserSessionCache cache = new UserSessionCache(null) {
            @Override
            public Optional<Entry> get(UUID userId) {
                return Optional.ofNullable(entry);
            }
        };
        return new SessionValidator(new TransactionManager(Map.of()), new UserRepository(), cache);
    }

    @Test
    void nullActorIsNeverValid() {
        assertFalse(new SessionValidator(new TransactionManager(Map.of()), new UserRepository())
                .isSessionValid(null, 0));
    }

    @Test
    void cachedActiveSessionWithCurrentVersionIsValid() {
        SessionValidator validator = validatorWithCached(new UserSessionCache.Entry("ACTIVE", 3));
        assertTrue(validator.isSessionValid(UUID.randomUUID(), 3));
    }

    @Test
    void cachedSessionWithStaleVersionIsRejected() {
        SessionValidator validator = validatorWithCached(new UserSessionCache.Entry("ACTIVE", 4));
        assertFalse(validator.isSessionValid(UUID.randomUUID(), 3));
    }

    @Test
    void cachedDisabledAccountIsRejected() {
        SessionValidator validator = validatorWithCached(new UserSessionCache.Entry(UserStatus.DISABLED.name(), 3));
        assertFalse(validator.isSessionValid(UUID.randomUUID(), 3));
    }
}
