package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.repository.OrganizationDecisionRepository.InitialEvent;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoInitialEventFactoryTest {

    private final DemoInitialEventFactory factory = new DemoInitialEventFactory();
    private String previousMode;
    private String previousEnvironment;

    @BeforeEach
    void setUp() {
        previousMode = System.getProperty("app.data.mode");
        previousEnvironment = System.getProperty("app.env");
        System.setProperty("app.data.mode", "demo");
        System.setProperty("app.env", "test");
    }

    @AfterEach
    void tearDown() {
        restore("app.data.mode", previousMode);
        restore("app.env", previousEnvironment);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static Organization organization(String description) {
        return new Organization("Acme", "a@b.vn", null, description, new User("u@b.vn", "user", "User", "hash"));
    }

    @Test
    void realDataModeCreatesNoEvent() {
        System.setProperty("app.data.mode", "real");
        assertNull(factory.create(organization("[Event: Gala | Date: 2026-12-01]")));
    }

    @Test
    void withoutATagItUsesTheDefaultsTwoWeeksAhead() {
        Instant before = Instant.now();

        InitialEvent event = factory.create(organization("plain description"));

        assertEquals("Sự kiện khởi tạo - Acme", event.title());
        assertEquals("Trung tâm Hội nghị Quốc gia", event.venue());
        assertTrue(event.startTime().isAfter(before.plus(Duration.ofDays(13))));
        assertEquals(Duration.ofHours(3), Duration.between(event.startTime(), event.endTime()));
    }

    @Test
    void aFullTagBecomesLocalTimeInHoChiMinhCity() {
        InitialEvent event = factory.create(organization(
                "[Event: Gala | Date: 2026-12-01 | Start: 19:00 | End: 22:30 | Venue: Opera House]"));

        assertEquals("Gala", event.title());
        assertEquals("Opera House", event.venue());
        assertEquals(Instant.parse("2026-12-01T12:00:00Z"), event.startTime());
        assertEquals(Instant.parse("2026-12-01T15:30:00Z"), event.endTime());
    }

    @Test
    void missingTimesDefaultToNineOClockAndThreeHours() {
        InitialEvent event = factory.create(organization("[Event: Gala | Date: 2026-12-01]"));

        assertEquals(Instant.parse("2026-12-01T02:00:00Z"), event.startTime());
        assertEquals(Instant.parse("2026-12-01T05:00:00Z"), event.endTime());
    }

    @Test
    void anInstantDateKeepsItsOwnStartAndAddsThreeHours() {
        InitialEvent event = factory.create(organization("[Event: Gala | Date: 2026-12-01T10:00:00Z]"));

        assertEquals(Instant.parse("2026-12-01T10:00:00Z"), event.startTime());
        assertEquals(Instant.parse("2026-12-01T13:00:00Z"), event.endTime());
    }

    @Test
    void anUnparsableDateFallsBackToTheDefaultWindow() {
        InitialEvent event = factory.create(organization("[Event: Gala | Date: someday]"));

        assertEquals("Gala", event.title());
        assertTrue(event.startTime().isAfter(Instant.now().plus(Duration.ofDays(13))));
    }
}
