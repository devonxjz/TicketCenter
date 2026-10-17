package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository;
import vn.ticketscenter.identity.dao.UserRepository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationMembershipConcurrencyTest {

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
    @DisplayName("MEM-06: Concurrency test - 2 luồng đồng thời deactivate 2 Quản lý khi tổ chức chỉ có 2 Quản lý")
    void testConcurrentDeactivation_GuaranteesAtLeastOneManagerRemains() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID manager1Id = UUID.randomUUID();
        UUID manager2Id = UUID.randomUUID();

        User manager1User = new User("m1@vwm.vn", "manager1", "Manager One", "hash");
        setId(manager1User, manager1Id);
        User manager2User = new User("m2@vwm.vn", "manager2", "Manager Two", "hash");
        setId(manager2User, manager2Id);

        Organization org = new Organization("Tổ chức Test", "test@org.vn", manager1User);
        setId(org, orgId);

        OrganizationMembership mem1 = new OrganizationMembership(manager1User, org, OrganizationRole.MANAGER);
        setMembershipId(mem1, UUID.randomUUID());
        OrganizationMembership mem2 = new OrganizationMembership(manager2User, org, OrganizationRole.MANAGER);
        setMembershipId(mem2, UUID.randomUUID());

        Map<String, OrganizationMembership> memberships = new ConcurrentHashMap<>();
        memberships.put(orgId + "_" + manager1Id, mem1);
        memberships.put(orgId + "_" + manager2Id, mem2);

        Map<UUID, User> users = new HashMap<>();
        users.put(manager1Id, manager1User);
        users.put(manager2Id, manager2User);

        Object orgLock = new Object();

        OrganizationMembershipRepository repo = new OrganizationMembershipRepository() {
            @Override
            public Optional<OrganizationMembership> findByOrgAndUser(EntityManager em, UUID oId, UUID uId) {
                return Optional.ofNullable(memberships.get(oId + "_" + uId));
            }

            @Override
            public long countActiveManagers(EntityManager em, UUID oId) {
                return memberships.values().stream()
                        .filter(m -> m.getOrganization().getId().equals(oId)
                                && m.getRole() == OrganizationRole.MANAGER
                                && m.isActive())
                        .count();
            }

            @Override
            public OrganizationMembership save(EntityManager em, OrganizationMembership m) {
                memberships.put(m.getOrganization().getId() + "_" + m.getUser().getId(), m);
                return m;
            }
        };

        UserRepository userRepo = new UserRepository() {
            @Override
            public Optional<User> findById(EntityManager em, UUID id) {
                return Optional.ofNullable(users.get(id));
            }
        };

        OrganizationMembershipService.TransactionExecutor tx = new OrganizationMembershipService.TransactionExecutor() {
            @Override
            public <T> T execute(vn.ticketscenter.config.persistence.DatabasePrincipal principal, java.util.function.Function<EntityManager, T> work) {
                synchronized (orgLock) {
                    EntityManager emStub = (EntityManager) java.lang.reflect.Proxy.newProxyInstance(
                            EntityManager.class.getClassLoader(),
                            new Class<?>[]{EntityManager.class},
                            (proxy, method, args) -> {
                                if ("find".equals(method.getName()) && args[0] == User.class) {
                                    return users.get(args[1]);
                                }
                                return null;
                            }
                    );
                    return work.apply(emStub);
                }
            }
        };

        OrganizationMembershipService service = new OrganizationMembershipService(tx, repo, userRepo, null);

        UUID adminId = UUID.randomUUID();
        User adminUser = new User("admin@tc.vn", "admin_user", "Admin", "hash");
        setId(adminUser, adminId);
        adminUser.grantPlatformRole("ADMIN");
        users.put(adminId, adminUser);

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch readyLatch = new CountDownLatch(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicBoolean lastManagerBlocked = new AtomicBoolean(false);

        // Thread 1: deactivates manager 1
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                OrganizationMemberDto dto = service.deactivateMember(adminId, orgId, manager1Id);
                if (!dto.isActive()) {
                    successCount.incrementAndGet();
                }
            } catch (IllegalStateException ex) {
                if (ex.getMessage().contains("CANNOT_DEACTIVATE_LAST_MANAGER")) {
                    lastManagerBlocked.set(true);
                }
                failureCount.incrementAndGet();
            } catch (Exception ex) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: deactivates manager 2
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                OrganizationMemberDto dto = service.deactivateMember(adminId, orgId, manager2Id);
                if (!dto.isActive()) {
                    successCount.incrementAndGet();
                }
            } catch (IllegalStateException ex) {
                if (ex.getMessage().contains("CANNOT_DEACTIVATE_LAST_MANAGER")) {
                    lastManagerBlocked.set(true);
                }
                failureCount.incrementAndGet();
            } catch (Exception ex) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Threads must be ready");
        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Threads must finish execution");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Chỉ đúng 1 luồng được phép deactivate thành công");
        assertEquals(1, failureCount.get(), "Luồng thứ 2 bắt buộc phải thất bại");
        assertTrue(lastManagerBlocked.get(), "Luồng thất bại phải nhận được lỗi CANNOT_DEACTIVATE_LAST_MANAGER");

        long activeCount = memberships.values().stream().filter(OrganizationMembership::isActive).count();
        assertEquals(1, activeCount, "Tổ chức luôn luôn duy trì đúng 1 Manager còn hoạt động");
    }
}
