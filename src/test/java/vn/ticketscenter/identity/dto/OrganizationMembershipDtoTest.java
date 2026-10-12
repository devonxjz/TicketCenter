package vn.ticketscenter.identity.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.OrganizationRole;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class OrganizationMembershipDtoTest {

    @Test
    @DisplayName("Verify OrganizationMemberDto getters and records")
    void testOrganizationMemberDto() {
        UUID membershipId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant joinedAt = Instant.now();

        OrganizationMemberDto dto = new OrganizationMemberDto(
                membershipId,
                userId,
                "johndoe",
                "John Doe",
                "john@example.com",
                OrganizationRole.MANAGER,
                true,
                joinedAt
        );

        assertEquals(membershipId, dto.membershipId());
        assertEquals(userId, dto.userId());
        assertEquals("johndoe", dto.userName());
        assertEquals("John Doe", dto.fullName());
        assertEquals("john@example.com", dto.email());
        assertEquals(OrganizationRole.MANAGER, dto.role());
        assertTrue(dto.isActive());
        assertEquals(joinedAt, dto.joinedAt());
    }

    @Test
    @DisplayName("Verify AddMemberRequestDto validation success")
    void testAddMemberRequestDtoValidationSuccess() {
        AddMemberRequestDto dto1 = new AddMemberRequestDto("user1", OrganizationRole.MANAGER);
        assertDoesNotThrow(dto1::validate);

        AddMemberRequestDto dto2 = new AddMemberRequestDto("user2", OrganizationRole.CHECK_IN_STAFF);
        assertDoesNotThrow(dto2::validate);
    }

    @Test
    @DisplayName("Verify AddMemberRequestDto validation failure on invalid input")
    void testAddMemberRequestDtoValidationFailure() {
        assertThrows(IllegalArgumentException.class, () ->
                new AddMemberRequestDto(null, OrganizationRole.MANAGER).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new AddMemberRequestDto("", OrganizationRole.MANAGER).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new AddMemberRequestDto("   ", OrganizationRole.MANAGER).validate());

        assertThrows(IllegalArgumentException.class, () ->
                new AddMemberRequestDto("validuser", null).validate());
    }

    @Test
    @DisplayName("Verify ChangeMemberRoleRequestDto validation success")
    void testChangeMemberRoleRequestDtoValidationSuccess() {
        ChangeMemberRoleRequestDto dto1 = new ChangeMemberRoleRequestDto(OrganizationRole.MANAGER);
        assertDoesNotThrow(dto1::validate);

        ChangeMemberRoleRequestDto dto2 = new ChangeMemberRoleRequestDto(OrganizationRole.CHECK_IN_STAFF);
        assertDoesNotThrow(dto2::validate);
    }

    @Test
    @DisplayName("Verify ChangeMemberRoleRequestDto validation failure on null role")
    void testChangeMemberRoleRequestDtoValidationFailure() {
        assertThrows(IllegalArgumentException.class, () ->
                new ChangeMemberRoleRequestDto(null).validate());
    }

    @Test
    @DisplayName("Verify MembershipSummaryDto getters and records")
    void testMembershipSummaryDto() {
        UUID orgId = UUID.randomUUID();
        MembershipSummaryDto dto = new MembershipSummaryDto(
                orgId,
                "Company XYZ",
                OrganizationRole.CHECK_IN_STAFF,
                true
        );

        assertEquals(orgId, dto.organizationId());
        assertEquals("Company XYZ", dto.organizationName());
        assertEquals(OrganizationRole.CHECK_IN_STAFF, dto.role());
        assertTrue(dto.isActive());
    }
}
