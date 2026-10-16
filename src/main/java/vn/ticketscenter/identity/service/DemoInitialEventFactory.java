package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository.InitialEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Builds the starter event that approving an organisation creates in demo mode. Real organisers create their own
 * event with actual dates and venue in the event editor, so outside demo mode no event is made.
 */
public class DemoInitialEventFactory {

    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration DEFAULT_LENGTH = Duration.ofHours(3);
    private static final String DEFAULT_VENUE = "Trung tâm Hội nghị Quốc gia";

    /** The event for an organisation being approved, or {@code null} when none should be created. */
    public InitialEvent create(Organization organization) {
        if (!ServerConfig.isDemoData()) {
            return null;
        }
        String title = "Sự kiện khởi tạo - " + organization.getName();
        String venue = DEFAULT_VENUE;
        Instant start = Instant.now().plus(Duration.ofDays(14));
        Instant end = start.plus(DEFAULT_LENGTH);

        EventScheduleTag.Schedule schedule = EventScheduleTag.parse(organization.getDescription());
        if (schedule != null) {
            if (schedule.title() != null) {
                title = schedule.title();
            }
            if (schedule.venue() != null) {
                venue = schedule.venue();
            }
            Instant[] window = windowOf(schedule);
            if (window != null) {
                start = window[0];
                end = window[1];
            }
        }
        return new InitialEvent(title, venue, start, end);
    }

    /** The start and end the tag describes, or {@code null} when it has no usable date. */
    private static Instant[] windowOf(EventScheduleTag.Schedule schedule) {
        if (schedule.date() == null) {
            return null;
        }
        try {
            if (schedule.date().contains("T")) {
                Instant start = Instant.parse(schedule.date());
                return new Instant[]{start, start.plus(DEFAULT_LENGTH)};
            }
            LocalDate day = LocalDate.parse(schedule.date());
            Instant start = day.atTime(timeOrDefault(schedule.startTime(), LocalTime.of(9, 0)))
                    .atZone(LOCAL_ZONE).toInstant();
            Instant end = start.plus(DEFAULT_LENGTH);
            if (schedule.endTime() != null) {
                try {
                    end = day.atTime(LocalTime.parse(schedule.endTime())).atZone(LOCAL_ZONE).toInstant();
                } catch (RuntimeException unparsable) {
                    end = start.plus(DEFAULT_LENGTH);
                }
            }
            return new Instant[]{start, end};
        } catch (RuntimeException unparsable) {
            return null;
        }
    }

    private static LocalTime timeOrDefault(String value, LocalTime fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException unparsable) {
            return fallback;
        }
    }
}
