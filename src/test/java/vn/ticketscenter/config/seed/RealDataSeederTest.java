package vn.ticketscenter.config.seed;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.PasswordHasher;

import java.time.Clock;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RealDataSeederTest {
    @Test
    void realDataModeMustNotOpenATransactionToCreateDemoAccounts() {
        String previous = System.getProperty("app.data.mode");
        System.setProperty("app.data.mode", "real");
        try {
            var unavailableDatabase = new TransactionManager(Map.of());
            assertDoesNotThrow(() -> new DemoAccountSeeder(new PasswordHasher(), Clock.systemUTC())
                    .seed(unavailableDatabase));
        } finally {
            if (previous == null) System.clearProperty("app.data.mode");
            else System.setProperty("app.data.mode", previous);
        }
    }

    @Test
    void realDataModeMustNotCreateOrResetAdminEvenIfSeedingWasEnabled() {
        String previousMode = System.getProperty("app.data.mode");
        String previousSeed = System.getProperty("admin.seed.enabled");
        System.setProperty("app.data.mode", "real");
        System.setProperty("admin.seed.enabled", "true");
        try {
            var unavailableDatabase = new TransactionManager(Map.of());
            assertDoesNotThrow(() -> new AdminSeeder(new PasswordHasher(), Clock.systemUTC())
                    .seed(unavailableDatabase));
        } finally {
            if (previousMode == null) System.clearProperty("app.data.mode");
            else System.setProperty("app.data.mode", previousMode);
            if (previousSeed == null) System.clearProperty("admin.seed.enabled");
            else System.setProperty("admin.seed.enabled", previousSeed);
        }
    }
}
