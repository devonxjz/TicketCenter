package vn.ticketscenter.config.seed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoSeedGateTest {
    @Test
    void demoAccountsNeedTheFlagAndAreNeverSeededInProduction() {
        assertTrue(DemoAccountSeeder.enabled("development", true));
        assertFalse(DemoAccountSeeder.enabled("development", false));
        assertFalse(DemoAccountSeeder.enabled("production", true));
        assertFalse(DemoAccountSeeder.enabled("PRODUCTION", true));
    }
}
