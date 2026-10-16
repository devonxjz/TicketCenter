package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.AddMemberRequestDto;
import vn.ticketscenter.identity.dto.ChangeMemberRoleRequestDto;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public class OrganizationMembershipService {

    @FunctionalInterface
    public interface TransactionExecutor {
        <T> T execute(DatabasePrincipal principal, Function<EntityManager, T> work);
    }

    private final TransactionExecutor transactions;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final DatabasePrincipal databasePrincipal;

    public OrganizationMembershipService(
            TransactionManager transactions,
            OrganizationMembershipRepository membershipRepository,
            UserRepository userRepository) {
        this(
                transactions != null ? transactions::execute : null,
                membershipRepository,
                userRepository,
                DatabasePrincipal.AUTH);
    }

    public OrganizationMembershipService(
            TransactionExecutor transactions,
            OrganizationMembershipRepository membershipRepository,
            UserRepository userRepository,
            DatabasePrincipal databasePrincipal) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.membershipRepository =
                Objects.requireNonNull(
                        membershipRepository, "membershipRepository must not be null");
        this.userRepository =
                Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.databasePrincipal =
                databasePrincipal != null ? databasePrincipal : DatabasePrincipal.AUTH;
    }

    protected OrganizationMembershipService() {
        this.transactions = null;
        this.membershipRepository = null;
        this.userRepository = null;
        this.databasePrincipal = DatabasePrincipal.AUTH;
    }

    public void assertManagerAccess(EntityManager em, UUID actorId, UUID orgId) {
        if (actorId == null || orgId == null) {
            throw new SecurityException("Yêu cầu thông tin người dùng và tổ chức");
        }
        User actor = em.find(User.class, actorId);
        if (actor != null && actor.getPlatformRole() == PlatformRole.ADMIN) {
            return;
        }
        var membership = membershipRepository.findByOrgAndUser(em, orgId, actorId);
        boolean activeManager =
                membership.isPresent()
                        && membership.get().isActive()
                        && membership.get().getRole() == OrganizationRole.MANAGER;
        if (!activeManager) {
            throw new SecurityException(
                    "FORBIDDEN: Chỉ Quản lý (MANAGER) của tổ chức mới có quyền thực hiện thao tác này");
        }
    }

    public List<MembershipSummaryDto> getMyMemberships(UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }
        return transactions.execute(
                databasePrincipal, em -> membershipRepository.findMembershipsByUserId(em, actorId));
    }

    public List<OrganizationMemberDto> getOrganizationMembers(UUID actorId, UUID orgId) {
        if (actorId == null || orgId == null) {
            throw new IllegalArgumentException("actorId and orgId must not be null");
        }
        return transactions.execute(
                databasePrincipal,
                em -> {
                    assertManagerAccess(em, actorId, orgId);
                    return membershipRepository.findMembersByOrgId(em, orgId);
                });
    }

    public OrganizationMemberDto addMember(UUID actorId, UUID orgId, AddMemberRequestDto dto) {
        if (actorId == null || orgId == null || dto == null) {
            throw new IllegalArgumentException("Thông tin thêm thành viên không hợp lệ");
        }
        dto.validate();

        return transactions.execute(
                databasePrincipal,
                em -> {
                    assertManagerAccess(em, actorId, orgId);

                    User targetUser =
                            userRepository
                                    .findByNormalizedUserName(em, dto.userName())
                                    .or(
                                            () ->
                                                    userRepository.findByNormalizedEmail(
                                                            em, dto.userName()))
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Tài khoản người dùng không tồn tại: "
                                                                    + dto.userName()));

                    if (targetUser.getStatus() != UserStatus.ACTIVE) {
                        throw new IllegalStateException(
                                "Tài khoản người dùng không ở trạng thái hoạt động");
                    }

                    var existingOpt =
                            membershipRepository.findByOrgAndUser(em, orgId, targetUser.getId());
                    if (existingOpt.isPresent()) {
                        OrganizationMembership existing = existingOpt.get();
                        if (existing.isActive()) {
                            throw new IllegalStateException(
                                    "MEMBER_ALREADY_EXISTS: Thành viên đã đang hoạt động trong tổ chức");
                        }
                        existing.setActive(true);
                        existing.changeRole(dto.role());
                        membershipRepository.save(em, existing);
                        return toDto(existing, targetUser);
                    }

                    Organization org = em.find(Organization.class, orgId);
                    if (org == null) {
                        throw new IllegalArgumentException("Tổ chức không tồn tại: " + orgId);
                    }

                    OrganizationMembership newMembership =
                            new OrganizationMembership(targetUser, org, dto.role());
                    membershipRepository.save(em, newMembership);
                    return toDto(newMembership, targetUser);
                });
    }

    public OrganizationMemberDto changeRole(
            UUID actorId, UUID orgId, UUID targetUserId, ChangeMemberRoleRequestDto dto) {
        if (actorId == null || orgId == null || targetUserId == null || dto == null) {
            throw new IllegalArgumentException("Thông tin đổi vai trò không hợp lệ");
        }
        dto.validate();

        return transactions.execute(
                databasePrincipal,
                em -> {
                    assertManagerAccess(em, actorId, orgId);

                    OrganizationMembership membership =
                            membershipRepository
                                    .findByOrgAndUser(em, orgId, targetUserId)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Thành viên không tồn tại trong tổ chức"));

                    boolean demotingActiveManager =
                            membership.getRole() == OrganizationRole.MANAGER
                                    && dto.role() != OrganizationRole.MANAGER
                                    && membership.isActive();
                    if (demotingActiveManager) {
                        long activeManagers = membershipRepository.countActiveManagers(em, orgId);
                        if (activeManagers <= 1) {
                            throw new IllegalStateException(
                                    "CANNOT_DEACTIVATE_LAST_MANAGER: Không thể hạ quyền Quản lý duy nhất còn lại của tổ chức");
                        }
                    }

                    membership.changeRole(dto.role());
                    membershipRepository.save(em, membership);
                    return toDto(membership, membership.getUser());
                });
    }

    public OrganizationMemberDto deactivateMember(UUID actorId, UUID orgId, UUID targetUserId) {
        if (actorId == null || orgId == null || targetUserId == null) {
            throw new IllegalArgumentException("Thông tin vô hiệu hóa không hợp lệ");
        }

        return transactions.execute(
                databasePrincipal,
                em -> {
                    assertManagerAccess(em, actorId, orgId);

                    OrganizationMembership membership =
                            membershipRepository
                                    .findByOrgAndUser(em, orgId, targetUserId)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Thành viên không tồn tại trong tổ chức"));

                    if (!membership.isActive()) {
                        return toDto(membership, membership.getUser());
                    }

                    if (membership.getRole() == OrganizationRole.MANAGER) {
                        long activeManagers = membershipRepository.countActiveManagers(em, orgId);
                        if (activeManagers <= 1) {
                            throw new IllegalStateException(
                                    "CANNOT_DEACTIVATE_LAST_MANAGER: "
                                            + "Không thể vô hiệu hóa Quản lý duy nhất còn lại của tổ chức");
                        }
                    }

                    membership.setActive(false);
                    membershipRepository.save(em, membership);
                    return toDto(membership, membership.getUser());
                });
    }

    public OrganizationMemberDto activateMember(UUID actorId, UUID orgId, UUID targetUserId) {
        if (actorId == null || orgId == null || targetUserId == null) {
            throw new IllegalArgumentException("Thông tin kích hoạt không hợp lệ");
        }

        return transactions.execute(
                databasePrincipal,
                em -> {
                    assertManagerAccess(em, actorId, orgId);

                    OrganizationMembership membership =
                            membershipRepository
                                    .findByOrgAndUser(em, orgId, targetUserId)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Thành viên không tồn tại trong tổ chức"));

                    membership.setActive(true);
                    membershipRepository.save(em, membership);
                    return toDto(membership, membership.getUser());
                });
    }

    private static OrganizationMemberDto toDto(OrganizationMembership m, User u) {
        return new OrganizationMemberDto(
                m.getId(),
                u.getId(),
                u.getUserName(),
                u.getFullName(),
                u.getEmail(),
                m.getRole(),
                m.isActive(),
                m.getJoinedAt());
    }
}
