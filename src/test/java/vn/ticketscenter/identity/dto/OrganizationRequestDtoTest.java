package vn.ticketscenter.identity.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.User;


import static org.junit.jupiter.api.Assertions.*;

public class OrganizationRequestDtoTest {

    @Test
    @DisplayName("Verify validate passes for valid create request DTO")
    void testValidateSuccess() {
        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Standard Event Co",
                "hello@events.org",
                "0912345678",
                "Event planning description"
        );
        assertDoesNotThrow(dto::validate);
    }

    @Test
    @DisplayName("Verify validate passes when optional fields are null or blank")
    void testValidateOptionalFields() {
        OrganizationRequestCreateDto dtoNull = new OrganizationRequestCreateDto(
                "Minimal Org",
                "admin@tickets.vn",
                null,
                null
        );
        assertDoesNotThrow(dtoNull::validate);

        OrganizationRequestCreateDto dtoBlank = new OrganizationRequestCreateDto(
                "Minimal Org Two",
                "admin2@tickets.vn",
                "",
                ""
        );
        assertDoesNotThrow(dtoBlank::validate);
    }

    @Test
    @DisplayName("Verify validate passes on exact name and description length boundaries")
    void testValidateBoundaries() {
        String name3 = "ABC";
        String name150 = "A".repeat(150);
        String desc2000 = "D".repeat(2000);

        OrganizationRequestCreateDto dtoMin = new OrganizationRequestCreateDto(
                name3, "min@example.com", null, null);
        assertDoesNotThrow(dtoMin::validate);

        OrganizationRequestCreateDto dtoMax = new OrganizationRequestCreateDto(
                name150, "max@example.com", "0900000000", desc2000);
        assertDoesNotThrow(dtoMax::validate);
    }

    @Test
    @DisplayName("Verify validate throws when name is invalid")
    void testValidateInvalidName() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto(null, "valid@test.com", null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("  ", "valid@test.com", null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("AB", "valid@test.com", null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("A".repeat(151), "valid@test.com", null, null).validate());
    }

    @Test
    @DisplayName("Verify validate throws when email is invalid")
    void testValidateInvalidEmail() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", null, null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "  ", null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "notanemail", null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "user@domain", null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "@domain.com", null, null).validate());
    }

    @Test
    @DisplayName("Verify validate throws when phone is invalid")
    void testValidateInvalidPhone() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", "1234567890", null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", "012345678", null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", "01234567890", null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", "012345678a", null).validate());
    }

    @Test
    @DisplayName("Verify validate throws when description exceeds 2000 chars")
    void testValidateInvalidDescription() {
        String longDesc = "A".repeat(2001);
        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", null, longDesc).validate());
    }

    @Test
    @DisplayName("Verify OrganizationRequestSummaryDto mapping and getters")
    void testSummaryDtoMapping() {
        assertNull(OrganizationRequestSummaryDto.from(null));

        Organization org = new Organization("My Org", "org@test.com", null);
        org.submitForApproval();

        OrganizationRequestSummaryDto summary = OrganizationRequestSummaryDto.from(org);
        assertNotNull(summary);
        assertEquals("My Org", summary.name());
        assertEquals("org@test.com", summary.contactEmail());
        assertEquals(OrganizationStatus.PENDING_APPROVAL, summary.status());
        assertNotNull(summary.submittedAt());
        assertNull(summary.rejectionReason());
    }

    @Test
    @DisplayName("Verify OrganizationRequestDetailDto mapping and getters")
    void testDetailDtoMapping() {
        assertNull(OrganizationRequestDetailDto.from(null));

        User requester = new User("user@test.com", "username", "Full Name", "secret");
        Organization org = new Organization(
                "Detail Org",
                "detail@test.com",
                "0987654321",
                "Detailed overview",
                requester
        );
        org.reject("Missing business license");

        OrganizationRequestDetailDto detail = OrganizationRequestDetailDto.from(org);
        assertNotNull(detail);
        assertEquals("Detail Org", detail.name());
        assertEquals("detail@test.com", detail.contactEmail());
        assertEquals("0987654321", detail.contactPhone());
        assertEquals("Detailed overview", detail.description());
        assertEquals(OrganizationStatus.REJECTED, detail.status());
        assertEquals("Missing business license", detail.rejectionReason());
        assertNull(detail.submittedAt());
    }

    @Test
    @DisplayName("Verify validate passes with event schedule fields")
    void testEventScheduleFieldsSuccess() {
        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Event Org",
                "org@test.com",
                "0912345678",
                "Organizing music festivals",
                "Music Fest 2026",
                "2026-11-20",
                "18:00",
                "22:00",
                "Hanoi Opera House"
        );
        assertDoesNotThrow(dto::validate);
        assertEquals("Music Fest 2026", dto.eventTitle());
        assertEquals("2026-11-20", dto.eventDate());
        assertEquals("18:00", dto.eventStartTime());
        assertEquals("22:00", dto.eventEndTime());
        assertEquals("Hanoi Opera House", dto.eventVenue());
    }

    @Test
    @DisplayName("Verify backwards compatible constructor leaves schedule fields null")
    void testBackwardsCompatibleConstructor() {
        OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                "Event Org",
                "org@test.com",
                "0912345678",
                "Organizing music festivals"
        );
        assertDoesNotThrow(dto::validate);
        assertNull(dto.eventTitle());
        assertNull(dto.eventDate());
        assertNull(dto.eventStartTime());
        assertNull(dto.eventEndTime());
        assertNull(dto.eventVenue());
    }

    @Test
    @DisplayName("Verify validate throws when event schedule fields exceed limits")
    void testValidateInvalidEventScheduleFields() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", null, null,
                        "A".repeat(301), null, null, null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", null, null,
                        null, null, null, null, "B".repeat(301)).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", null, null,
                        null, "C".repeat(51), null, null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", null, null,
                        null, null, "D".repeat(51), null, null).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new OrganizationRequestCreateDto("Acme", "valid@test.com", null, null,
                        null, null, null, "E".repeat(51), null).validate());
    }
}
