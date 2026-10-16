package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.dto.CurrentUserDto;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.dto.RegisterRequestDto;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(vn.ticketscenter.platform.testsupport.ExternalTestCondition.class)
public class AuthFlowTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final UserRepository userRepository = new UserRepository();

    @Test
    @DisplayName("Đăng ký từ chối email không hợp lệ hoặc để trống")
    void testRegisterInvalidEmail() {
        RegistrationService service =
                new RegistrationService(new TransactionManager(java.util.Map.of()), userRepository, passwordHasher);

        assertThrows(IllegalArgumentException.class, () ->
                service.register(new RegisterRequestDto("validuser", "Full Name", "invalid-email", "StrongPass123!")));

        assertThrows(IllegalArgumentException.class, () ->
                service.register(new RegisterRequestDto("validuser", "Full Name", "", "StrongPass123!")));
    }

    @Test
    @DisplayName("Đăng ký từ chối mật khẩu dưới 12 ký tự")
    void testRegisterPasswordTooShort() {
        RegistrationService service =
                new RegistrationService(new TransactionManager(java.util.Map.of()), userRepository, passwordHasher);

        assertThrows(IllegalArgumentException.class, () ->
                service.register(new RegisterRequestDto("validuser", "Full Name", "user@test.vn", "Short123!")));
    }

    @Test
    @DisplayName("Đăng nhập từ chối thông tin rỗng")
    void testLoginNullOrBlankInputs() {
        LoginService service =
                new LoginService(new TransactionManager(java.util.Map.of()), userRepository, passwordHasher);

        IdentityException blankIdentifier = assertThrows(IdentityException.class, () ->
                service.login(new LoginRequestDto("", "password")));
        assertEquals(IdentityError.INVALID_CREDENTIALS, blankIdentifier.error());

        IdentityException blankPassword = assertThrows(IdentityException.class, () ->
                service.login(new LoginRequestDto("user@test.vn", "")));
        assertEquals(IdentityError.INVALID_CREDENTIALS, blankPassword.error());
    }


    @Test
    @DisplayName("CurrentUserDto đảm bảo vệ sinh dữ liệu: không chứa passwordHash hoặc secret")
    void testCurrentUserDtoSanitization() {
        UUID id = UUID.randomUUID();
        CurrentUserDto dto = new CurrentUserDto(id, "test@test.vn", "testuser", "Test User", PlatformRole.CUSTOMER, false);

        assertEquals(id, dto.id());
        assertEquals("test@test.vn", dto.email());
        assertEquals("testuser", dto.userName());
        assertEquals(PlatformRole.CUSTOMER, dto.platformRole());
        assertFalse(dto.admin());

        // Kiểm tra qua Reflection: Không tồn tại trường password, hash hay secret
        for (Field field : CurrentUserDto.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("password"), "DTO must not contain password field");
            assertFalse(name.contains("hash"), "DTO must not contain hash field");
            assertFalse(name.contains("secret"), "DTO must not contain secret field");
            assertFalse(name.contains("salt"), "DTO must not contain salt field");
        }
    }

    @Test
    @DisplayName("Kiểm thử chu trình đầy đủ: Đăng ký -> Đăng nhập -> Đối soát Session -> Đổi authVersion khi có DB")
    void testFullAuthFlowWithDatabase() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not available for integration testing");
            }
            registry.transactionManager().execute(DatabasePrincipal.AUTH, em -> {

                em.createNativeQuery("""
                    IF COL_LENGTH(N'dbo.tc_users', N'updated_at') IS NULL
                    BEGIN
                        ALTER TABLE dbo.tc_users ADD updated_at DATETIME2(6) NULL;
                    END
                """).executeUpdate();
                em.createNativeQuery("""
                    UPDATE dbo.tc_users SET updated_at = created_at WHERE updated_at IS NULL
                """).executeUpdate();
                em.createNativeQuery("""
                    ALTER TABLE dbo.tc_users ALTER COLUMN normalized_email NVARCHAR(320) NULL
                """).executeUpdate();
                em.createNativeQuery("""
                    CREATE OR ALTER TRIGGER dbo.trg_tc_users_normalize
                    ON dbo.tc_users AFTER INSERT, UPDATE
                    AS
                    BEGIN
                        SET NOCOUNT ON;
                        UPDATE u
                        SET normalized_email = LOWER(u.email)
                        FROM dbo.tc_users u
                        JOIN inserted i ON u.id = i.id
                        WHERE u.normalized_email IS NULL OR u.normalized_email <> LOWER(u.email);
                    END
                """).executeUpdate();
                return null;
            });
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Database connection not available: " + e.getMessage());
            return;
        }

        try {
            TransactionManager tm = registry.transactionManager();
            RegistrationService registration = new RegistrationService(tm, userRepository, passwordHasher);
            LoginService login = new LoginService(tm, userRepository, passwordHasher);
            SessionValidator sessions = new SessionValidator(tm, userRepository);
            ProfileService profiles = new ProfileService(tm, userRepository);

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "test." + suffix + "@ticketscenter.vn";
            String userName = "user" + suffix;
            String password = "SecurePassword123!";

            // 1. Đăng ký thành công
            boolean registered = registration.register(new RegisterRequestDto(userName, "Nguyen Van Test", email, password));
            assertTrue(registered, "Registration should succeed");

            // 2. Đăng ký lại cùng email hoặc userName phải bị từ chối 409 (IdentityException)
            assertThrows(IdentityException.class, () ->
                    registration.register(new RegisterRequestDto("diff" + suffix, "Nguyen Van Test", email, password)));

            assertThrows(IdentityException.class, () ->
                    registration.register(new RegisterRequestDto(userName, "Nguyen Van Test", "diff." + suffix + "@tc.vn", password)));

            // 3. Đăng nhập sai mật khẩu -> từ chối
            assertThrows(IdentityException.class, () ->
                    login.login(new LoginRequestDto(email, "WrongPassword123!")));

            // 4. Đăng nhập đúng -> trả về UserPrincipal
            UserPrincipal principal = login.login(new LoginRequestDto(email, password));
            assertNotNull(principal);
            assertEquals(0, principal.authVersion());
            assertEquals(PlatformRole.CUSTOMER, principal.platformRole());

            // 5. Kiểm tra phiên hợp lệ và lấy thông tin user
            assertTrue(sessions.isSessionValid(principal.actorId(), principal.authVersion()));
            CurrentUserDto currentUser = profiles.getCurrentUser(principal.actorId());
            assertNotNull(currentUser);
            assertEquals(email, currentUser.email());
            assertEquals(userName, currentUser.userName());
            assertEquals(PlatformRole.CUSTOMER, currentUser.platformRole());
            assertFalse(currentUser.admin());

            // 6. Kiểm tra thu hồi toàn bộ phiên qua revokeAllSessions
            sessions.revokeAllSessions(principal.actorId());

            // Session mang authVersion cũ (0) lập tức bị từ chối
            assertFalse(sessions.isSessionValid(principal.actorId(), 0),
                    "Session with stale authVersion must be invalidated");

            // Session với authVersion mới (1) hợp lệ
            assertTrue(sessions.isSessionValid(principal.actorId(), 1));

            // 7. Giả lập khóa tài khoản (DISABLED)
            tm.execute(DatabasePrincipal.AUTH, em ->
                    userRepository.updateStatus(em, principal.actorId(), UserStatus.DISABLED));

            // Session bị từ chối khi tài khoản disabled
            assertFalse(sessions.isSessionValid(principal.actorId(), 1),
                    "Disabled user session must be rejected");

            // Đăng nhập lại bị chặn ACCOUNT_DISABLED
            IdentityException disabled = assertThrows(IdentityException.class, () ->
                    login.login(new LoginRequestDto(email, password)));
            assertEquals(IdentityError.ACCOUNT_DISABLED, disabled.error());

        } finally {
            registry.close();
        }
    }
}
