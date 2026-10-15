package vn.ticketscenter.config.seed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.EmailNormalizer;
import vn.ticketscenter.identity.service.LoginService;
import vn.ticketscenter.identity.service.PasswordHasher;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DemoAccountSeederTest - Kiểm tra và thực thi Seed tài khoản dùng thử")
@org.junit.jupiter.api.extension.ExtendWith(vn.ticketscenter.platform.testsupport.ExternalTestCondition.class)
class DemoAccountSeederTest {

    private static PersistenceRegistry registry;
    private static TransactionManager transactions;
    private static PasswordHasher passwordHasher;
    private static LoginService loginService;
    private static String previousDataMode;

    @BeforeAll
    static void init() {
        previousDataMode = System.getProperty("app.data.mode");
        System.setProperty("app.data.mode", "demo");
        registry = new PersistenceRegistry();
        registry.initialize();
        transactions = registry.transactionManager();
        passwordHasher = new PasswordHasher();
        loginService = new LoginService(transactions, new UserRepository(), passwordHasher);

        // Chạy seed dữ liệu
        new AdminSeeder(passwordHasher, Clock.systemUTC()).seed(transactions);
        new DemoAccountSeeder(passwordHasher, Clock.systemUTC()).seed(transactions);
    }

    @AfterAll
    static void tearDown() {
        if (registry != null) {
            registry.close();
        }
        if (previousDataMode == null) System.clearProperty("app.data.mode");
        else System.setProperty("app.data.mode", previousDataMode);
    }

    @Test
    @DisplayName("Admin: Đăng nhập thành công với admin@fixture.invalid và mật khẩu mật khẩu fixture từ cấu hình")
    void testAdminAccountLogin() {
        UserPrincipal principal = loginService.login(new LoginRequestDto(vn.ticketscenter.config.AppConfig.get("admin.email", "admin@fixture.invalid"), vn.ticketscenter.config.AppConfig.get("admin.password")));
        assertNotNull(principal);
        assertEquals(PlatformRole.ADMIN, principal.platformRole());
        assertTrue(principal.isAdmin());
    }

    @Test
    @DisplayName("Admin: Đăng nhập thành công với tên đăng nhập 'admin' và mật khẩu mật khẩu fixture từ cấu hình")
    void testAdminAccountLoginWithUsername() {
        UserPrincipal principal = loginService.login(new LoginRequestDto("admin", vn.ticketscenter.config.AppConfig.get("admin.password")));
        assertNotNull(principal);
        assertEquals(PlatformRole.ADMIN, principal.platformRole());
        assertTrue(principal.isAdmin());
    }

    @Test
    @DisplayName("Manager: Đăng nhập thành công với abcc@manager.com và mật khẩu 123")
    void testManagerAccountLogin() {
        UserPrincipal principal = loginService.login(new LoginRequestDto("abcc@manager.com", "123"));
        assertNotNull(principal);
        assertEquals(PlatformRole.CUSTOMER, principal.platformRole());
        assertEquals(OrganizationRole.MANAGER, principal.organizationRole());
        assertTrue(principal.isManager());

        // Kiểm tra quyền MANAGER trong tổ chức
        transactions.execute(DatabasePrincipal.AUTH, em -> {
            String normalizedEmail = EmailNormalizer.normalize("abcc@manager.com");
            User user = em.createQuery("SELECT u FROM User u WHERE LOWER(u.email) = :e", User.class)
                    .setParameter("e", normalizedEmail)
                    .getSingleResult();
            assertTrue(user.isEmailVerified(), "Email phải được xác minh trước");

            OrganizationMembership membership = em.createQuery(
                    "SELECT m FROM OrganizationMembership m WHERE m.user.id = :uid", OrganizationMembership.class)
                    .setParameter("uid", user.getId())
                    .getSingleResult();
            assertEquals(OrganizationRole.MANAGER, membership.getRole());
            assertTrue(membership.isActive());
            return null;
        });
    }

    @Test
    @DisplayName("Manager: Đăng nhập thành công với tên đăng nhập 'abcc_manager' và mật khẩu 123")
    void testManagerAccountLoginWithUsername() {
        UserPrincipal principal = loginService.login(new LoginRequestDto("abcc_manager", "123"));
        assertNotNull(principal);
        assertEquals(PlatformRole.CUSTOMER, principal.platformRole());
        assertEquals(OrganizationRole.MANAGER, principal.organizationRole());
        assertTrue(principal.isManager());
    }

    @Test
    @DisplayName("Staff: Đăng nhập thành công với abcc@staff.com và mật khẩu 123")
    void testStaffAccountLogin() {
        UserPrincipal principal = loginService.login(new LoginRequestDto("abcc@staff.com", "123"));
        assertNotNull(principal);
        assertEquals(PlatformRole.CUSTOMER, principal.platformRole());
        assertEquals(OrganizationRole.CHECK_IN_STAFF, principal.organizationRole());
        assertTrue(principal.isStaff());

        // Kiểm tra quyền CHECK_IN_STAFF trong tổ chức
        transactions.execute(DatabasePrincipal.AUTH, em -> {
            String normalizedEmail = EmailNormalizer.normalize("abcc@staff.com");
            User user = em.createQuery("SELECT u FROM User u WHERE LOWER(u.email) = :e", User.class)
                    .setParameter("e", normalizedEmail)
                    .getSingleResult();
            assertTrue(user.isEmailVerified(), "Email phải được xác minh trước");

            OrganizationMembership membership = em.createQuery(
                    "SELECT m FROM OrganizationMembership m WHERE m.user.id = :uid", OrganizationMembership.class)
                    .setParameter("uid", user.getId())
                    .getSingleResult();
            assertEquals(OrganizationRole.CHECK_IN_STAFF, membership.getRole());
            assertTrue(membership.isActive());
            return null;
        });
    }

    @Test
    @DisplayName("Staff: Đăng nhập thành công với tên đăng nhập 'abcc_staff' và mật khẩu 123")
    void testStaffAccountLoginWithUsername() {
        UserPrincipal principal = loginService.login(new LoginRequestDto("abcc_staff", "123"));
        assertNotNull(principal);
        assertEquals(PlatformRole.CUSTOMER, principal.platformRole());
        assertEquals(OrganizationRole.CHECK_IN_STAFF, principal.organizationRole());
        assertTrue(principal.isStaff());
    }

    @Test
    @DisplayName("Buyer: Đăng nhập thành công với buyer@ticketscenter.vn và mật khẩu 123")
    void testBuyerAccountLogin() {
        UserPrincipal principal = loginService.login(new LoginRequestDto("buyer@ticketscenter.vn", "123"));
        assertNotNull(principal);
        assertEquals(PlatformRole.CUSTOMER, principal.platformRole());
        assertNull(principal.organizationRole());
        assertTrue(principal.isCustomerOnly());
    }

    @Test
    @DisplayName("Buyer: Đăng nhập thành công với tên đăng nhập 'buyer1' và mật khẩu 123")
    void testBuyerAccountLoginWithUsername() {
        UserPrincipal principal = loginService.login(new LoginRequestDto("buyer1", "123"));
        assertNotNull(principal);
        assertEquals(PlatformRole.CUSTOMER, principal.platformRole());
        assertNull(principal.organizationRole());
        assertTrue(principal.isCustomerOnly());
    }
}
