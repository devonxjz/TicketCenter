package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.identity.dto.AddMemberRequestDto;
import vn.ticketscenter.identity.dto.ChangeMemberRoleRequestDto;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository;
import vn.ticketscenter.identity.dao.UserRepository;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationMembershipServiceTest {

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
    private Map<UUID, Organization> orgsById;

    private OrganizationMembershipService service;

    @BeforeEach
    void setUp() throws Exception {
        actorManagerId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        orgId = UUID.randomUUID();

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

        orgsById = new HashMap<>();
        orgsById.put(orgId, organization);

        OrganizationMembershipRepository repo = new OrganizationMembershipRepository() {
            @Override
            public Optional<OrganizationMembership> findByOrgAndUser(EntityManager em, UUID oId, UUID uId) {
                return Optional.ofNullable(membershipsByOrgAndUser.get(oId + "_" + uId));
            }

            @Override
            public List<OrganizationMemberDto> findMembersByOrgId(EntityManager em, UUID oId) {
                List<OrganizationMemberDto> list = new ArrayList<>();
                for (OrganizationMembership m : membershipsByOrgAndUser.values()) {
                    if (m.getOrganization().getId().equals(oId)) {
                        list.add(new OrganizationMemberDto(
                                m.getId(), m.getUser().getId(), m.getUser().getUserName(),
                                m.getUser().getFullName(), m.getUser().getEmail(),
                                m.getRole(), m.isActive(), m.getJoinedAt()
                        ));
                    }
                }
                return list;
            }

            @Override
            public List<MembershipSummaryDto> findMembershipsByUserId(EntityManager em, UUID uId) {
                List<MembershipSummaryDto> list = new ArrayList<>();
                for (OrganizationMembership m : membershipsByOrgAndUser.values()) {
                    if (m.getUser().getId().equals(uId)) {
                        list.add(new MembershipSummaryDto(
                                m.getOrganization().getId(), m.getOrganization().getName(),
                                m.getRole(), m.isActive()
                        ));
                    }
                }
                return list;
            }

            @Override
            public long countActiveManagers(EntityManager em, UUID oId) {
                return membershipsByOrgAndUser.values().stream()
                        .filter(m -> m.getOrganization().getId().equals(oId)
                                && m.getRole() == OrganizationRole.MANAGER
                                && m.isActive())
                        .count();
            }

            @Override
            public OrganizationMembership save(EntityManager em, OrganizationMembership m) {
                if (m.getId() == null) {
                    try {
                        setMembershipId(m, UUID.randomUUID());
                    } catch (Exception ignored) {}
                }
                membershipsByOrgAndUser.put(m.getOrganization().getId() + "_" + m.getUser().getId(), m);
                return m;
            }
        };

        UserRepository userRepo = new UserRepository() {
            @Override
            public Optional<User> findByNormalizedUserName(EntityManager em, String userName) {
                if (userName == null) return Optional.empty();
                return Optional.ofNullable(usersByUsername.get(userName.toLowerCase(Locale.ROOT)));
            }

            @Override
            public Optional<User> findById(EntityManager em, UUID id) {
                return Optional.ofNullable(usersById.get(id));
            }
        };

        OrganizationMembershipService.TransactionExecutor tx = new OrganizationMembershipService.TransactionExecutor() {
            @Override
            public <T> T execute(DatabasePrincipal principal, java.util.function.Function<EntityManager, T> work) {
                EntityManager emStub = (EntityManager) java.lang.reflect.Proxy.newProxyInstance(
                        EntityManager.class.getClassLoader(),
                        new Class<?>[]{EntityManager.class},
                        (proxy, method, args) -> {
                            if ("find".equals(method.getName())) {
                                Class<?> entityClass = (Class<?>) args[0];
                                UUID id = (UUID) args[1];
                                if (entityClass == User.class) return usersById.get(id);
                                if (entityClass == Organization.class) return orgsById.get(id);
                            }
                            return null;
                        }
                );
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

    private static void setMembershipId(OrganizationMembership membership, UUID id) throws Exception {
        Field field = OrganizationMembership.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(membership, id);
    }

    @Test
    @DisplayName("MEM-01: Thêm thành viên mới thành công (201 Created)")
    void testAddMember_SuccessNewMember() {
        AddMemberRequestDto dto = new AddMemberRequestDto("staff1", OrganizationRole.CHECK_IN_STAFF);

        OrganizationMemberDto result = service.addMember(actorManagerId, orgId, dto);

        assertNotNull(result);
        assertEquals(targetUserId, result.userId());
        assertEquals("staff1", result.userName());
        assertEquals(OrganizationRole.CHECK_IN_STAFF, result.role());
        assertTrue(result.isActive());

        OrganizationMembership saved = membershipsByOrgAndUser.get(orgId + "_" + targetUserId);
        assertNotNull(saved);
        assertEquals(OrganizationRole.CHECK_IN_STAFF, saved.getRole());
        assertTrue(saved.isActive());
    }

    @Test
    @DisplayName("MEM-02: Thêm thành viên đã tồn tại nhưng bị inactive -> Tái kích hoạt active=1")
    void testAddMember_ReactivateExistingInactiveMember() throws Exception {
        OrganizationMembership oldMembership = new OrganizationMembership(targetUser, organization, OrganizationRole.CHECK_IN_STAFF);
        oldMembership.setActive(false);
        setMembershipId(oldMembership, UUID.randomUUID());
        membershipsByOrgAndUser.put(orgId + "_" + targetUserId, oldMembership);

        AddMemberRequestDto dto = new AddMemberRequestDto("staff1", OrganizationRole.MANAGER);
        OrganizationMemberDto result = service.addMember(actorManagerId, orgId, dto);

        assertNotNull(result);
        assertEquals(targetUserId, result.userId());
        assertTrue(result.isActive());
        assertEquals(OrganizationRole.MANAGER, result.role());
        assertTrue(oldMembership.isActive());
        assertEquals(OrganizationRole.MANAGER, oldMembership.getRole());
    }

    @Test
    @DisplayName("MEM-01 Conflict: Thêm thành viên đã active trong tổ chức -> ném lỗi MEMBER_ALREADY_EXISTS")
    void testAddMember_AlreadyActiveThrowsConflict() throws Exception {
        OrganizationMembership activeMembership = new OrganizationMembership(targetUser, organization, OrganizationRole.CHECK_IN_STAFF);
        activeMembership.setActive(true);
        setMembershipId(activeMembership, UUID.randomUUID());
        membershipsByOrgAndUser.put(orgId + "_" + targetUserId, activeMembership);

        AddMemberRequestDto dto = new AddMemberRequestDto("staff1", OrganizationRole.CHECK_IN_STAFF);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.addMember(actorManagerId, orgId, dto));
        assertTrue(ex.getMessage().contains("MEMBER_ALREADY_EXISTS"));
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
    @DisplayName("MEM-04: Vô hiệu hóa Manager cuối cùng -> chặn lại, ném CANNOT_DEACTIVATE_LAST_MANAGER")
    void testDeactivateMember_LastActiveManagerThrowsException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.deactivateMember(actorManagerId, orgId, actorManagerId));

        assertTrue(ex.getMessage().contains("CANNOT_DEACTIVATE_LAST_MANAGER"));
        assertTrue(actorMembership.isActive(), "Trạng thái Manager phải được giữ nguyên là active");
    }

    @Test
    @DisplayName("MEM-05: Đổi role sang CHECK_IN_STAFF của Manager cuối -> chặn lại, ném CANNOT_DEACTIVATE_LAST_MANAGER")
    void testChangeRole_DemoteLastActiveManagerThrowsException() {
        ChangeMemberRoleRequestDto dto = new ChangeMemberRoleRequestDto(OrganizationRole.CHECK_IN_STAFF);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.changeRole(actorManagerId, orgId, actorManagerId, dto));

        assertTrue(ex.getMessage().contains("CANNOT_DEACTIVATE_LAST_MANAGER"));
        assertEquals(OrganizationRole.MANAGER, actorMembership.getRole(), "Role phải được giữ nguyên là MANAGER");
    }

    @Test
    @DisplayName("Deactivate Manager thành công khi tổ chức còn Quản lý khác (count > 1)")
    void testDeactivateMember_SuccessWhenMultipleManagersExist() throws Exception {
        UUID secondManagerId = UUID.randomUUID();
        User secondManagerUser = new User("m2@vwm.vn", "manager2", "Second Manager", "hash");
        setId(secondManagerUser, secondManagerId);
        usersById.put(secondManagerId, secondManagerUser);

        OrganizationMembership secondMembership = new OrganizationMembership(secondManagerUser, organization, OrganizationRole.MANAGER);
        setMembershipId(secondMembership, UUID.randomUUID());
        membershipsByOrgAndUser.put(orgId + "_" + secondManagerId, secondMembership);

        OrganizationMemberDto result = service.deactivateMember(actorManagerId, orgId, actorManagerId);

        assertNotNull(result);
        assertFalse(result.isActive());
        assertFalse(actorMembership.isActive());
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
