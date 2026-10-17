package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;

/**
 * The {@code [Event: title | Date: d | Start: s | End: e | Venue: v]} tag an organiser's request carries in its
 * description: written when the request is submitted, read back to build the demo initial event.
 */
public final class EventScheduleTag {

    /** Prefix that marks a description as already carrying a schedule. */
    public static final String MARKER = "[Event:";

    private static final String OPENING = "[Event: ";
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    /** Values read from a tag; any of them may be {@code null}. */
    public record Schedule(
            String title, String date, String startTime, String endTime, String venue) {}

    private EventScheduleTag() {}

    /**
     * The request description with the schedule tag appended. A request without an event title keeps its plain
     * description; a description that already holds a tag is left as it is.
     */
    public static String appendTo(OrganizationRequestCreateDto request) {
        String description = isBlank(request.description()) ? null : request.description().trim();
        String tag = format(request);
        if (tag == null) {
            return description;
        }
        if (description == null) {
            description = tag;
        } else if (!description.contains(MARKER)) {
            description = description + "\n" + tag;
        }
        return description.length() > MAX_DESCRIPTION_LENGTH
                ? description.substring(0, MAX_DESCRIPTION_LENGTH)
                : description;
    }

    private static String format(OrganizationRequestCreateDto request) {
        if (isBlank(request.eventTitle())) {
            return null;
        }
        StringBuilder tag = new StringBuilder(OPENING).append(request.eventTitle().trim());
        appendField(tag, "Date", request.eventDate());
        appendField(tag, "Start", request.eventStartTime());
        appendField(tag, "End", request.eventEndTime());
        appendField(tag, "Venue", request.eventVenue());
        return tag.append(']').toString();
    }

    private static void appendField(StringBuilder tag, String name, String value) {
        if (!isBlank(value)) {
            tag.append(" | ").append(name).append(": ").append(value.trim());
        }
    }

    /** Reads the tag out of a description, or returns {@code null} when it has none. */
    public static Schedule parse(String description) {
        if (description == null || !description.contains(OPENING)) {
            return null;
        }
        int from = description.indexOf(OPENING) + OPENING.length();
        int to = description.indexOf(']', from);
        String content = to != -1 ? description.substring(from, to) : description.substring(from);

        String title = null;
        String date = null;
        String start = null;
        String end = null;
        String venue = null;
        for (String part : content.split("\\|")) {
            String field = part.trim();
            if (!field.contains(":")) {
                if (!field.isBlank()) {
                    title = field;
                }
                continue;
            }
            String[] keyAndValue = field.split(":", 2);
            String key = keyAndValue[0].trim().toLowerCase();
            String value = keyAndValue[1].trim();
            if (value.isBlank()) {
                continue;
            }
            switch (key) {
                case "venue" -> venue = value;
                case "date" -> date = value;
                case "start", "starttime" -> start = value;
                case "end", "endtime" -> end = value;
                default -> {}
            }
        }
        return new Schedule(title, date, start, end, venue);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
