package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventScheduleTagTest {

    private static OrganizationRequestCreateDto request(String description, String title, String date,
                                                       String start, String end, String venue) {
        return new OrganizationRequestCreateDto("Acme", "a@b.vn", null, description, title, date, start, end, venue);
    }

    @Test
    void withoutAnEventTitleTheDescriptionIsOnlyTrimmed() {
        assertEquals("About us", EventScheduleTag.appendTo(request("  About us ", null, "2026-12-01", null, null, null)));
        assertNull(EventScheduleTag.appendTo(request("   ", " ", null, null, null, null)));
    }

    @Test
    void tagCarriesEveryFilledFieldInOrder() {
        String description = EventScheduleTag.appendTo(
                request("About us", " Rock Night ", "2026-12-01", "19:00", "22:00", "Stadium"));

        assertEquals("About us\n[Event: Rock Night | Date: 2026-12-01 | Start: 19:00 | End: 22:00 | Venue: Stadium]",
                description);
    }

    @Test
    void descriptionBecomesTheTagWhenThereWasNone() {
        assertEquals("[Event: Rock Night | Venue: Stadium]",
                EventScheduleTag.appendTo(request(null, "Rock Night", null, null, null, "Stadium")));
    }

    @Test
    void existingTagIsNotDuplicated() {
        String existing = "Hello [Event: Old | Venue: Somewhere]";
        assertEquals(existing, EventScheduleTag.appendTo(request(existing, "New", null, null, null, null)));
    }

    @Test
    void parseReadsBackWhatAppendWrote() {
        String description = EventScheduleTag.appendTo(
                request("About us", "Rock Night", "2026-12-01", "19:00", "22:00", "Stadium"));

        EventScheduleTag.Schedule schedule = EventScheduleTag.parse(description);

        assertEquals(new EventScheduleTag.Schedule("Rock Night", "2026-12-01", "19:00", "22:00", "Stadium"),
                schedule);
    }

    @Test
    void parseAcceptsAnUnterminatedTagAndAliasedKeys() {
        EventScheduleTag.Schedule schedule =
                EventScheduleTag.parse("[Event: Gala | StartTime: 18:30 | endtime: 21:00");

        assertEquals("Gala", schedule.title());
        assertEquals("18:30", schedule.startTime());
        assertEquals("21:00", schedule.endTime());
        assertNull(schedule.date());
    }

    @Test
    void parseReturnsNullWithoutATag() {
        assertNull(EventScheduleTag.parse("just text"));
        assertNull(EventScheduleTag.parse(null));
    }
}
