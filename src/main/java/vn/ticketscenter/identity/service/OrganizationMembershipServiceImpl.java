package vn.ticketscenter.identity.service;

import java.util.Optional;
import vn.ticketscenter.identity.model.OrganizationMembership;
import jakarta.persistence.EntityManager;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.AddMemberRequestDto;
import vn.ticketscenter.identity.dto.ChangeMemberRoleRequestDto;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository.MembershipRow;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class OrganizationMembershipServiceImpl implements OrganizationMembershipService {

    

    /** Membership commands run as the manager role; the procedures authorize the actor themselves. */
    private static final DatabasePrincipal WRITE_PRINCIPAL = DatabasePrincipal.MANAGER;

    private final TransactionExecutor transactions;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final DatabasePrincipal databasePrincipal;

    public OrganizationMembershipServiceImpl(TransactionManager transactions,
                                         OrganizationMembershipRepository membershipRepository,
                                         UserRepository userRepository) {
        this(transactions != null ? transactions::execute : null, membershipRepository, userRepository,
                DatabasePrincipal.AUTH);
    }

    public OrganizationMembershipServiceImpl(TransactionExecutor transactions,
                                         OrganizationMembershipRepository membershipRepository,
                                         UserRepository userRepository,
                                         DatabasePrincipal databasePrincipal) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.membershipRepository =
                Objects.requireNonNull(membershipRepository, "membershipRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.databasePrincipal = databasePrincipal != null ? databasePrincipal : DatabasePrincipal.AUTH;
    }

    protected OrganizationMembershipServiceImpl() {
        this.transactions = null;
        this.membershipRepository = null;
        this.userRepository = null;
        this.databasePrincipal = DatabasePrincipal.AUTH;
    }

    @Override
    public void assertManagerAccess(EntityManager em, UUID actorId, UUID orgId) {
        if (actorId == null || orgId == null) {
            throw new SecurityException("Yêu cầu thông tin người dùng và tổ chức");
        }
        User actor = em.find(User.class, actorId);
        if (actor != null && actor.getPlatformRole() == PlatformRole.ADMIN) {
            return;
        }
        Optional<OrganizationMembership> membership = membershipRepository.findByOrgAndUser(em, orgId, actorId);
        boolean activeManager = membership.isPresent() && membership.get().isActive()
                && membership.get().getRole() == OrganizationRole.MANAGER;
        if (!activeManager) {
            throw new SecurityException(
                    "FORBIDDEN: Chỉ Quản lý (MANAGER) của tổ chức mới có quyền thực hiện thao tác này");
        }
    }

    @Override
    public List<MembershipSummaryDto> getMyMemberships(UUID actorId) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId must not be null");
        }
        return transactions.execute(databasePrincipal, em -> membershipRepository.findMembershipsByUserId(em, actorId));
    }

    @Override
    public List<OrganizationMemberDto> getOrganizationMembers(UUID actorId, UUID orgId) {
        if (actorId == null || orgId == null) {
            throw new IllegalArgumentException("actorId and orgId must not be null");
        }
        return transactions.execute(databasePrincipal, em -> {
            assertManagerAccess(em, actorId, orgId);
            return membershipRepository.findMembersByOrgId(em, orgId);
        });
    }

    @Override
    public OrganizationMemberDto addMember(UUID actorId, UUID orgId, AddMemberRequestDto dto) {
        if (actorId == null || orgId == null || dto == null) {
            throw new IllegalArgumentException("Thông tin thêm thành viên không hợp lệ");
        }
        dto.validate();

        // The guard keeps a non-manager from learning which accounts exist; the procedure re-checks on write.
        User targetUser = transactions.execute(databasePrincipal, em -> {
            assertManagerAccess(em, actorId, orgId);
            return userRepository.findByNormalizedUserName(em, dto.userName())
                    .or(() -> userRepository.findByNormalizedEmail(em, dto.userName()))
                    .orElseThrow(() ->
                            new IllegalArgumentException("Tài khoản người dùng không tồn tại: " + dto.userName()));
        });
        if (targetUser.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Tài khoản người dùng không ở trạng thái hoạt động");
        }

        MembershipRow row = transactions.execute(WRITE_PRINCIPAL, em ->
                membershipRepository.addMember(em, orgId, actorId, targetUser.getId(), dto.role()));
        return toDto(row, targetUser);
    }

    @Override
    public OrganizationMemberDto changeRole(UUID actorId, UUID orgId, UUID targetUserId,
                                            ChangeMemberRoleRequestDto dto) {
        if (actorId == null || orgId == null || targetUserId == null || dto == null) {
            throw new IllegalArgumentException("Thông tin đổi vai trò không hợp lệ");
        }
        dto.validate();
        return changeMember(actorId, orgId, targetUserId, dto.role(), null);
    }

    @Override
    public OrganizationMemberDto deactivateMember(UUID actorId, UUID orgId, UUID targetUserId) {
        if (actorId == null || orgId == null || targetUserId == null) {
            throw new IllegalArgumentException("Thông tin vô hiệu hóa không hợp lệ");
        }
        return changeMember(actorId, orgId, targetUserId, null, false);
    }

    @Override
    public OrganizationMemberDto activateMember(UUID actorId, UUID orgId, UUID targetUserId) {
        if (actorId == null || orgId == null || targetUserId == null) {
            throw new IllegalArgumentException("Thông tin kích hoạt không hợp lệ");
        }
        return changeMember(actorId, orgId, targetUserId, null, true);
    }

    /** One procedure call decides the authorization, the last-manager rule and the audit row. */
    private OrganizationMemberDto changeMember(UUID actorId, UUID orgId, UUID targetUserId,
                                               OrganizationRole role, Boolean active) {
        MembershipRow row = transactions.execute(WRITE_PRINCIPAL, em ->
                membershipRepository.changeMember(em, orgId, actorId, targetUserId, role, active));
        User targetUser = transactions.execute(databasePrincipal, em ->
                userRepository.findById(em, targetUserId)
                        .orElseThrow(() -> new IllegalStateException("Thành viên không có tài khoản: " + targetUserId)));
        return toDto(row, targetUser);
    }

    private static OrganizationMemberDto toDto(MembershipRow m, User u) {
        return new OrganizationMemberDto(
                m.membershipId(),
                u.getId(),
                u.getUserName(),
                u.getFullName(),
                u.getEmail(),
                m.role(),
                m.active(),
                m.joinedAt()
        );
    }
}
