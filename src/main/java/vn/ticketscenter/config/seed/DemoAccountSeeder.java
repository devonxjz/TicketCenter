package vn.ticketscenter.config.seed;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationMembership;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.EmailNormalizer;
import vn.ticketscenter.identity.service.PasswordHasher;

import java.time.Clock;
import java.util.logging.Logger;

public final class DemoAccountSeeder {

    private static final Logger LOGGER = Logger.getLogger(DemoAccountSeeder.class.getName());

    private final PasswordHasher passwords;
    private final Clock clock;

    public DemoAccountSeeder(PasswordHasher passwords, Clock clock) {
        this.passwords = passwords;
        this.clock = clock;
    }

    /** Demo accounts carry a published password, so they are opt-in and never created in production. */
    public static boolean enabled(String environment, boolean flag) {
        return flag && !"production".equalsIgnoreCase(environment);
    }

    public void seed(TransactionManager transactions) {
        if (!ServerConfig.isDemoData()) return;
        transactions.execute(DatabasePrincipal.AUTH, entityManager -> {
            UserRepository users = new UserRepository();

            // 1. Manager: abcc@manager.com (mật khẩu: 123)
            User manager = seedUser(entityManager, users, "abcc@manager.com", "abcc_manager", "Manager Demo", "123", PlatformRole.CUSTOMER);

            // 2. Staff: abcc@staff.com (mật khẩu: 123)
            User staff = seedUser(entityManager, users, "abcc@staff.com", "abcc_staff", "Staff Demo", "123", PlatformRole.CUSTOMER);

            // 3. Buyer: buyer@ticketscenter.vn (mật khẩu: 123)
            seedUser(entityManager, users, "buyer@ticketscenter.vn", "buyer1", "Khách Hàng Mẫu", "123", PlatformRole.CUSTOMER);

            // 4. Tổ chức mẫu: Vé Hay Event Center (APPROVED)
            Organization org = entityManager.createQuery(
                    "SELECT o FROM Organization o WHERE o.name = :name", Organization.class)
                    .setParameter("name", "Vé Hay Event Center")
                    .getResultList().stream().findFirst().orElse(null);

            if (org == null) {
                org = new Organization("Vé Hay Event Center", "abcc@manager.com", manager);
                org.approve();
                entityManager.persist(org);
            } else {
                org.approve();
            }

            // 5. Cấp quyền thành viên tổ chức
            ensureMembership(entityManager, manager, org, OrganizationRole.MANAGER);
            ensureMembership(entityManager, staff, org, OrganizationRole.CHECK_IN_STAFF);

            // 6. Đảm bảo tài khoản admin@fixture.invalid có tên đăng nhập là 'admin'
            User admin = users.findByNormalizedEmail(entityManager, EmailNormalizer.normalize(AppConfig.get("admin.email", "admin@fixture.invalid"))).orElse(null);
            if (admin != null && !"admin".equalsIgnoreCase(admin.getUserName())) {
                Long count = entityManager.createQuery("SELECT COUNT(u) FROM User u WHERE LOWER(u.userName) = 'admin' AND u.id != :id", Long.class)
                        .setParameter("id", admin.getId())
                        .getSingleResult();
                if (count == null || count == 0) {
                    admin.changeUserName("admin");
                    users.save(entityManager, admin);
                }
            }

            LOGGER.info("✅ Demo accounts seeded successfully: abcc@manager.com (MANAGER), abcc@staff.com (CHECK_IN_STAFF), buyer@ticketscenter.vn (CUSTOMER)");
            return null;
        });
    }

    private User seedUser(EntityManager em, UserRepository users, String email, String defaultUserName, String fullName, String password, PlatformRole platformRole) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        User user = users.findByNormalizedEmail(em, normalizedEmail).orElse(null);

        if (user == null) {
            String userName = defaultUserName;
            Long count = em.createQuery("SELECT COUNT(u) FROM User u WHERE LOWER(u.userName) = :uname", Long.class)
                    .setParameter("uname", defaultUserName.toLowerCase())
                    .getSingleResult();
            if (count != null && count > 0) {
                userName = normalizedEmail;
            }

            user = new User(email, userName, passwords.hash(password), fullName, clock.instant());
            user.verifyEmail(clock.instant());
            user.grantPlatformRole(platformRole.name());
            users.save(em, user);
        } else {
            user.changePassword(passwords.hash(password));
            user.verifyEmail(clock.instant());
            user.grantPlatformRole(platformRole.name());
            if (defaultUserName != null && !defaultUserName.equalsIgnoreCase(user.getUserName())) {
                Long count = em.createQuery("SELECT COUNT(u) FROM User u WHERE LOWER(u.userName) = :uname AND u.id != :id", Long.class)
                        .setParameter("uname", defaultUserName.toLowerCase())
                        .setParameter("id", user.getId())
                        .getSingleResult();
                if (count == null || count == 0) {
                    user.changeUserName(defaultUserName);
                }
            }
            users.save(em, user);
        }
        return user;
    }

    private void ensureMembership(EntityManager em, User user, Organization org, OrganizationRole role) {
        OrganizationMembership membership = em.createQuery(
                "SELECT m FROM OrganizationMembership m WHERE m.user.id = :userId AND m.organization.id = :orgId", OrganizationMembership.class)
                .setParameter("userId", user.getId())
                .setParameter("orgId", org.getId())
                .getResultList().stream().findFirst().orElse(null);

        if (membership == null) {
            membership = new OrganizationMembership(user, org, role);
            membership.setActive(true);
            em.persist(membership);
        } else {
            membership.setActive(true);
        }
    }
}
