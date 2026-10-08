package vn.ticketscenter.config.seed;

import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.dao.UserDAO;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.identity.service.PasswordHasher;

import java.time.Clock;

public final class AdminSeeder {

    private final PasswordHasher passwords;
    private final Clock clock;

    public AdminSeeder(PasswordHasher passwords, Clock clock) {
        this.passwords = passwords;
        this.clock = clock;
    }

    public void seed(TransactionManager transactions) {
        String password = AppConfig.get("admin.password");
        validateProductionSecret(ServerConfig.getEnv(), password);
        if (!AppConfig.getBoolean("admin.seed.enabled", false)) return;
        if (password == null || password.isBlank()) throw new IllegalStateException("Admin seed password is required");
        String email = AppConfig.get("admin.email", "adminTIcket@gmail.com");
        String normalizedEmail = AccountService.normalizeEmail(email);
        transactions.execute(DatabasePrincipal.AUTH, entityManager -> {
            UserDAO users = new UserDAO(entityManager);
            User existing = users.findByNormalizedEmail(normalizedEmail).orElse(null);
            if (existing != null) {
                if (!existing.hasPlatformRole("ADMIN")) {
                    throw new IllegalStateException("Admin seed email already belongs to a non-admin account");
                }
                return null;
            }
            User admin = new User(email, normalizedEmail, passwords.hash(password), "Administrator", clock.instant());
            admin.verifyEmail(clock.instant());
            admin.grantPlatformRole("ADMIN");
            users.save(admin);
            return null;
        });
    }

    public static void validateProductionSecret(String environment, String password) {
        if ("production".equalsIgnoreCase(environment)
                && (password == null || password.isBlank() || "admin".equals(password))) {
            throw new IllegalStateException("A non-demo admin password is required in production");
        }
    }
}
