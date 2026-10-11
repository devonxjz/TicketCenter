package vn.ticketscenter.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.User;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EventScheduleTest {

    @Test
    @DisplayName("Event constructor initializes schedule and sale window properly")
    void testEventScheduleConstructor() {
        User requester = new User("host@test.vn", "hostuser", "Host User", "hash");
        Organization org = new Organization("Music Fest", "fest@test.vn", requester);

        Instant startTime = Instant.now().plus(Duration.ofDays(10));
        Instant endTime = startTime.plus(Duration.ofHours(4));

        Event event = new Event(
                org,
                "Summer Fest",
                "Annual summer festival",
                EventCategory.CONCERT,
                "Central Park",
                "Hanoi",
                startTime,
                endTime
        );

        assertEquals(org, event.getOrganization());
        assertEquals("Summer Fest", event.getTitle());
        assertEquals("Annual summer festival", event.getDescription());
        assertEquals(EventCategory.CONCERT, event.getCategory());
        assertEquals("Central Park", event.getVenueName());
        assertEquals("Hanoi", event.getVenueAddress());
        assertEquals(startTime, event.getStartTime());
        assertEquals(endTime, event.getEndTime());
        assertEquals(startTime.minus(Duration.ofDays(7)), event.getSaleStart());
        assertEquals(startTime, event.getSaleEnd());
    }

    @Test
    @DisplayName("Event constructor handles null start time by defaulting sale window")
    void testEventScheduleConstructorNullStart() {
        User requester = new User("host@test.vn", "hostuser", "Host User", "hash");
        Organization org = new Organization("Music Fest", "fest@test.vn", requester);

        Event event = new Event(
                org,
                "TBD Fest",
                "TBD festival",
                EventCategory.OTHER,
                "TBD Venue",
                "Hanoi",
                null,
                null
        );

        assertNotNull(event.getSaleStart());
        assertNotNull(event.getSaleEnd());
    }

    @Test
    @DisplayName("setSchedule modifies sale window and event schedule")
    void testSetSchedule() {
        User requester = new User("host@test.vn", "hostuser", "Host User", "hash");
        Organization org = new Organization("Music Fest", "fest@test.vn", requester);

        Event event = new Event(
                org,
                "Festival",
                "Festival desc",
                EventCategory.CONCERT,
                "Stadium",
                "Hanoi"
        );

        Instant saleStart = Instant.now();
        Instant saleEnd = saleStart.plus(Duration.ofDays(5));
        Instant startTime = saleEnd.plus(Duration.ofDays(2));
        Instant endTime = startTime.plus(Duration.ofHours(3));

        event.setSchedule(saleStart, saleEnd, startTime, endTime);

        assertEquals(saleStart, event.getSaleStart());
        assertEquals(saleEnd, event.getSaleEnd());
        assertEquals(startTime, event.getStartTime());
        assertEquals(endTime, event.getEndTime());
    }
}
