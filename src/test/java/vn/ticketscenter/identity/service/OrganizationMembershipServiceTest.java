package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository.MembershipRow;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.identity.dto.AddMemberRequestDto;
import vn.ticketscenter.identity.dto.ChangeMemberRoleRequestDto;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The membership rules (who may manage, the last-manager guard, reactivation, audit) live in
 * usp_AddMember / usp_ChangeMemberRole and are exercised against SQL Server. These tests cover what the service
 * still owns: handing the command to the procedure under the right principal, building the response and passing
 * the translated failures through.
 */
class OrganizationMembershipServiceTest {

    private record Write(String command, UUID orgId, UUID actorId, UUID userId, OrganizationRole role, Boolean active) { }

    private UUID actorManagerId;
    private UUID targetUserId;
    private UUID orgId;
    private User actorManager;
    private User targetUser;
    private Organization organization;
    private OrganizationMembership actorMembership;

    private Map<String, OrganizationMembership> membershipsByOrgAndUser;
    private Map<UUID, User> usersById;
    private Map<String, User> usersByUsername;

    private final List<Write> writes = new ArrayList<>();
    private final List<DatabasePrincipal> principals = new ArrayList<>();
    private RuntimeException procedureFailure;
    private final Instant joinedAt = Instant.parse("2026-10-01T08:00:00Z");

    private OrganizationMembershipService service;

    @BeforeEach
    void setUp() throws Exception {
        actorManagerId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        writes.clear();
        principals.clear();
        procedureFailure = null;

        actorManager = new User("manager@vwm.vn", "manager_vwm", "Manager User", "hash");
        setId(actorManager, actorManagerId);
        targetUser = new User("staff@vwm.vn", "staff1", "Staff User", "hash");
        setId(targetUser, targetUserId);
        organization = new Organization("Bảo tàng Phụ nữ VN", "contact@vwm.vn", actorManager);
        setId(organization, orgId);

        actorMembership = new OrganizationMembership(actorManager, organization, OrganizationRole.MANAGER);
        setMembershipId(actorMembership, UUID.randomUUID());
        membershipsByOrgAndUser = new HashMap<>();
        membershipsByOrgAndUser.put(orgId + "_" + actorManagerId, actorMembership);

        usersById = new HashMap<>();
        usersById.put(actorManagerId, actorManager);
        usersById.put(targetUserId, targetUser);
        usersByUsername = new HashMap<>();
        usersByUsername.put("manager_vwm", actorManager);
        usersByUsername.put("staff1", targetUser);

        OrganizationMembershipRepository repo = new OrganizationMembershipRepository() {
            @Override
            public Optional<OrganizationMembership> findByOrgAndUser(EntityManager em, UUID oId, UUID uId) {
                return Optional.ofNullable(membershipsByOrgAndUser.get(oId + "_" + uId));
            }

            @Override
            public List<MembershipSummaryDto> findMembershipsByUserId(EntityManager em, UUID uId) {
                List<MembershipSummaryDto> list = new ArrayList<>();
                for (OrganizationMembership m : membershipsByOrgAndUser.values()) {
                    if (m.getUser().getId().equals(uId)) {
                        list.add(new MembershipSummaryDto(
                                m.getOrganization().getId(), m.getOrganization().getName(), m.getRole(), m.isActive()));
                    }
                }
                return list;
            }

            @Override
            public MembershipRow addMember(EntityManager em, UUID oId, UUID actor, UUID uId, OrganizationRole role) {
                writes.add(new Write("add", oId, actor, uId, role, null));
                if (procedureFailure != null) throw procedureFailure;
                return new MembershipRow(UUID.randomUUID(), uId, role, true, joinedAt);
            }

            @Override
            public MembershipRow changeMember(EntityManager em, UUID oId, UUID actor, UUID uId,
                                              OrganizationRole role, Boolean active) {
                writes.add(new Write("change", oId, actor, uId, role, active));
                if (procedureFailure != null) throw procedureFailure;
                return new MembershipRow(UUID.randomUUID(), uId,
                        role != null ? role : OrganizationRole.CHECK_IN_STAFF, active == null || active, joinedAt);
            }
        };

        UserRepository userRepo = new UserRepository() {
            @Override
            public Optional<User> findByNormalizedUserName(EntityManager em, String userName) {
                if (userName == null) return Optional.empty();
                return Optional.ofNullable(usersByUsername.get(userName.toLowerCase(Locale.ROOT)));
            }

            @Override
            public Optional<User> findByNormalizedEmail(EntityManager em, String email) {
                return usersById.values().stream()
                        .filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
            }

            @Override
            public Optional<User> findById(EntityManager em, UUID id) {
                return Optional.ofNullable(usersById.get(id));
            }
        };

        OrganizationMembershipService.TransactionExecutor tx = new OrganizationMembershipService.TransactionExecutor() {
            @Override
            public <T> T execute(DatabasePrincipal principal, java.util.function.Function<EntityManager, T> work) {
                principals.add(principal);
                EntityManager emStub = (EntityManager) java.lang.reflect.Proxy.newProxyInstance(
                        EntityManager.class.getClassLoader(),
                        new Class<?>[]{EntityManager.class},
                        (proxy, method, args) -> {
                            if ("find".equals(method.getName()) && args[0] == User.class) {
                                return usersById.get((UUID) args[1]);
                            }
                            return null;
                        });
                return work.apply(emStub);
            }
        };

        service = new OrganizationMembershipService(tx, repo, userRepo, DatabasePrincipal.AUTH);
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field field = entity.getClass().getSuperclass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static void setStatus(User user, UserStatus status) throws Exception {
        Field field = User.class.getDeclaredField("status");
        field.setAccessible(true);
        field.set(user, status);
    }

    private static void setMembershipId(OrganizationMembership membership, UUID id) throws Exception {
        Field field = OrganizationMembership.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(membership, id);
    }

    @Test
    @DisplayName("MEM-01: thêm thành viên gọi procedure một lần và dựng response từ dòng procedure trả về")
    void addMemberDelegatesToProcedure() {
        OrganizationMemberDto result = service.addMember(actorManagerId, orgId,
                new AddMemberRequestDto("staff1", OrganizationRole.CHECK_IN_STAFF));

        assertEquals(List.of(new Write("add", orgId, actorManagerId, targetUserId, OrganizationRole.CHECK_IN_STAFF, null)),
                writes);
        assertEquals(targetUserId, result.userId());
        assertEquals("staff1", result.userName());
        assertEquals("staff@vwm.vn", result.email());
        assertEquals(OrganizationRole.CHECK_IN_STAFF, result.role());
        assertTrue(result.isActive());
        assertEquals(joinedAt, result.joinedAt());
    }

    @Test
    @DisplayName("Đọc dưới AUTH, ghi dưới MANAGER: procedure mới là nơi cấp quyền ghi")
    void writesRunAsManagerPrincipal() {
        service.addMember(actorManagerId, orgId, new AddMemberRequestDto("staff1", OrganizationRole.MANAGER));
        assertEquals(List.of(DatabasePrincipal.AUTH, DatabasePrincipal.MANAGER), principals);

        principals.clear();
        service.deactivateMember(actorManagerId, orgId, targetUserId);
        assertEquals(List.of(DatabasePrincipal.MANAGER, DatabasePrincipal.AUTH), principals);
    }

    @Test
    @DisplayName("MEM-01: tài khoản không tồn tại -> lỗi, không gọi procedure")
    void addMemberUnknownUserNeverWrites() {
        assertThrows(IllegalArgumentException.class, () -> service.addMember(actorManagerId, orgId,
                new AddMemberRequestDto("ghost", OrganizationRole.CHECK_IN_STAFF)));
        assertTrue(writes.isEmpty());
    }

    @Test
    @DisplayName("Tài khoản không ACTIVE -> lỗi, không gọi procedure")
    void addMemberInactiveUserNeverWrites() throws Exception {
        setStatus(targetUser, UserStatus.DISABLED);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.addMember(actorManagerId, orgId,
                new AddMemberRequestDto("staff1", OrganizationRole.CHECK_IN_STAFF)));
        assertTrue(ex.getMessage().contains("không ở trạng thái hoạt động"));
        assertTrue(writes.isEmpty());
    }

    @Test
    @DisplayName("Người ngoài tổ chức không dò được tài khoản nào tồn tại (403 trước khi tra user)")
    void addMemberByOutsiderIsForbiddenBeforeUserLookup() throws Exception {
        UUID outsiderId = UUID.randomUUID();
        User outsider = new User("outsider@gmail.com", "outsider", "Outsider User", "hash");
        setId(outsider, outsiderId);
        usersById.put(outsiderId, outsider);

        assertThrows(SecurityException.class, () -> service.addMember(outsiderId, orgId,
                new AddMemberRequestDto("ghost-or-real", OrganizationRole.CHECK_IN_STAFF)));
        assertTrue(writes.isEmpty());
    }

    @Test
    @DisplayName("MEM-01 Conflict: lỗi MEMBER_ALREADY_EXISTS từ procedure được giữ nguyên")
    void addMemberPropagatesConflict() {
        procedureFailure = new IllegalStateException("MEMBER_ALREADY_EXISTS: đã hoạt động");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.addMember(actorManagerId, orgId,
                new AddMemberRequestDto("staff1", OrganizationRole.CHECK_IN_STAFF)));
        assertTrue(ex.getMessage().contains("MEMBER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("Đổi role gửi role mới và giữ nguyên active (null)")
    void changeRoleSendsRoleOnly() {
        OrganizationMemberDto result = service.changeRole(actorManagerId, orgId, targetUserId,
                new ChangeMemberRoleRequestDto(OrganizationRole.MANAGER));

        assertEquals(List.of(new Write("change", orgId, actorManagerId, targetUserId, OrganizationRole.MANAGER, null)),
                writes);
        assertEquals(OrganizationRole.MANAGER, result.role());
    }

    @Test
    @DisplayName("Vô hiệu hóa gửi active=false và giữ nguyên role (null)")
    void deactivateSendsActiveFalseOnly() {
        OrganizationMemberDto result = service.deactivateMember(actorManagerId, orgId, targetUserId);

        assertEquals(List.of(new Write("change", orgId, actorManagerId, targetUserId, null, false)), writes);
        assertFalse(result.isActive());
    }

    @Test
    @DisplayName("Kích hoạt gửi active=true và giữ nguyên role (null)")
    void activateSendsActiveTrueOnly() {
        OrganizationMemberDto result = service.activateMember(actorManagerId, orgId, targetUserId);

        assertEquals(List.of(new Write("change", orgId, actorManagerId, targetUserId, null, true)), writes);
        assertTrue(result.isActive());
    }

    @Test
    @DisplayName("MEM-04/05: lỗi 'Quản lý cuối cùng' do trigger ném được giữ nguyên, không dựng response")
    void lastManagerFailurePropagates() {
        procedureFailure = new IllegalStateException("CANNOT_DEACTIVATE_LAST_MANAGER: không thể");

        IllegalStateException deactivate = assertThrows(IllegalStateException.class,
                () -> service.deactivateMember(actorManagerId, orgId, actorManagerId));
        IllegalStateException demote = assertThrows(IllegalStateException.class,
                () -> service.changeRole(actorManagerId, orgId, actorManagerId,
                        new ChangeMemberRoleRequestDto(OrganizationRole.CHECK_IN_STAFF)));
        assertTrue(deactivate.getMessage().contains("CANNOT_DEACTIVATE_LAST_MANAGER"));
        assertTrue(demote.getMessage().contains("CANNOT_DEACTIVATE_LAST_MANAGER"));
    }

    @Test
    @DisplayName("403 từ procedure -> không tra thêm user (không lộ thông tin cho người không có quyền)")
    void forbiddenFromProcedureSkipsUserRead() {
        procedureFailure = new SecurityException("FORBIDDEN: chỉ quản lý");
        assertThrows(SecurityException.class, () -> service.changeRole(UUID.randomUUID(), orgId, targetUserId,
                new ChangeMemberRoleRequestDto(OrganizationRole.MANAGER)));
        assertEquals(List.of(DatabasePrincipal.MANAGER), principals, "only the write transaction may have run");
    }

    @Test
    @DisplayName("MEM-03: Người ngoài tổ chức xem danh sách thành viên -> 403 Forbidden")
    void testGetOrganizationMembers_UnauthorizedActorThrowsForbidden() throws Exception {
        UUID outsiderId = UUID.randomUUID();
        User outsider = new User("outsider@gmail.com", "outsider", "Outsider User", "hash");
        setId(outsider, outsiderId);
        usersById.put(outsiderId, outsider);

        SecurityException ex = assertThrows(SecurityException.class, () ->
                service.getOrganizationMembers(outsiderId, orgId));
        assertTrue(ex.getMessage().contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("Tham số null bị từ chối trước khi mở transaction")
    void nullArgumentsAreRejectedBeforeAnyTransaction() {
        assertThrows(IllegalArgumentException.class, () -> service.deactivateMember(null, orgId, targetUserId));
        assertThrows(IllegalArgumentException.class, () -> service.activateMember(actorManagerId, null, targetUserId));
        assertThrows(IllegalArgumentException.class, () -> service.changeRole(actorManagerId, orgId, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.addMember(actorManagerId, orgId, null));
        assertTrue(principals.isEmpty());
    }

    @Test
    @DisplayName("GET /api/me/memberships: Lấy danh sách tổ chức của tài khoản hiện tại")
    void testGetMyMemberships() {
        List<MembershipSummaryDto> result = service.getMyMemberships(actorManagerId);

        assertEquals(1, result.size());
        assertEquals(orgId, result.get(0).organizationId());
        assertEquals("Bảo tàng Phụ nữ VN", result.get(0).organizationName());
        assertEquals(OrganizationRole.MANAGER, result.get(0).role());
        assertTrue(result.get(0).isActive());
    }
}
