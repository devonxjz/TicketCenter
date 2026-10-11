package vn.ticketscenter.identity.dto;

import java.util.regex.Pattern;

public record OrganizationRequestCreateDto(
        String name,
        String contactEmail,
        String contactPhone,
        String description,
        String eventTitle,
        String eventDate,
        String eventStartTime,
        String eventEndTime,
        String eventVenue
) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");

    public OrganizationRequestCreateDto(String name, String contactEmail, String contactPhone, String description) {
        this(name, contactEmail, contactPhone, description, null, null, null, null, null);
    }

    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Organization name must not be blank.");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() < 3 || trimmedName.length() > 150) {
            throw new IllegalArgumentException("Organization name must be between 3 and 150 characters.");
        }
        if (contactEmail == null || contactEmail.isBlank()) {
            throw new IllegalArgumentException("Contact email must not be blank.");
        }
        if (!EMAIL_PATTERN.matcher(contactEmail.trim()).matches()) {
            throw new IllegalArgumentException("Contact email format is invalid.");
        }
        if (contactPhone != null && !contactPhone.isBlank()) {
            if (!PHONE_PATTERN.matcher(contactPhone.trim()).matches()) {
                throw new IllegalArgumentException("Contact phone must be 10 digits starting with 0.");
            }
        }
        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException("Description cannot exceed 2000 characters.");
        }
        if (eventTitle != null && eventTitle.length() > 300) {
            throw new IllegalArgumentException("Event title cannot exceed 300 characters.");
        }
        if (eventVenue != null && eventVenue.length() > 300) {
            throw new IllegalArgumentException("Event venue cannot exceed 300 characters.");
        }
        if (eventDate != null && eventDate.length() > 50) {
            throw new IllegalArgumentException("Event date cannot exceed 50 characters.");
        }
        if (eventStartTime != null && eventStartTime.length() > 50) {
            throw new IllegalArgumentException("Event start time cannot exceed 50 characters.");
        }
        if (eventEndTime != null && eventEndTime.length() > 50) {
            throw new IllegalArgumentException("Event end time cannot exceed 50 characters.");
        }
    }
}
