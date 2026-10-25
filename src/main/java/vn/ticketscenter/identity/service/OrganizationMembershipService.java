package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.identity.dto.AddMemberRequestDto;
import vn.ticketscenter.identity.dto.ChangeMemberRoleRequestDto;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public interface OrganizationMembershipService {
    @FunctionalInterface
    interface TransactionExecutor {
        <T> T execute(DatabasePrincipal principal, Function<EntityManager, T> work);
    }

    void assertManagerAccess(EntityManager em, UUID actorId, UUID orgId);

    List<MembershipSummaryDto> getMyMemberships(UUID actorId);

    List<OrganizationMemberDto> getOrganizationMembers(UUID actorId, UUID orgId);

    OrganizationMemberDto addMember(UUID actorId, UUID orgId, AddMemberRequestDto dto);

    OrganizationMemberDto changeRole(UUID actorId, UUID orgId, UUID targetUserId,
            ChangeMemberRoleRequestDto dto);

    OrganizationMemberDto deactivateMember(UUID actorId, UUID orgId, UUID targetUserId);

    OrganizationMemberDto activateMember(UUID actorId, UUID orgId, UUID targetUserId);
}
