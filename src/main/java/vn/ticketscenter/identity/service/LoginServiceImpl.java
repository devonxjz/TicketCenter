package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.IdentityConfig;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.model.UserStatus;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.util.Objects;

public class LoginServiceImpl implements LoginService {

    /** Same cost as a real hash, so an unknown account takes as long as a wrong password. */
    private static final String DUMMY_HASH = new PasswordHasher().hash("dummy-password-for-timing");

    private final TransactionManager transactions;
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final PasswordHasher passwordHasher;

    public LoginServiceImpl(
            TransactionManager transactions,
            UserRepository userRepository,
            OrganizationMembershipRepository membershipRepository,
            PasswordHasher passwordHasher) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.userRepository =
                Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.membershipRepository =
                Objects.requireNonNull(
                        membershipRepository, "membershipRepository must not be null");
        this.passwordHasher =
                Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
    }

    public LoginServiceImpl(
            TransactionManager transactions,
            UserRepository userRepository,
            PasswordHasher passwordHasher) {
        this(transactions, userRepository, new OrganizationMembershipRepository(), passwordHasher);
    }

    @Override
    public UserPrincipal login(LoginRequestDto request) {
        if (request == null || isBlank(request.identifier()) || isBlank(request.password())) {
            throw invalidCredentials();
        }
        String password = request.password();
        User user = findByIdentifier(request.identifier().trim());
        verifyPasswordOrFail(user, password);
        requireActive(user);
        upgradeHashIfNeeded(user, password);
        return toPrincipal(user, primaryRoleOf(user));
    }

    private User findByIdentifier(String identifier) {
        String email = EmailNormalizer.normalize(identifier);
        return transactions.execute(
                DatabasePrincipal.AUTH,
                em -> {
                    User found = userRepository.findByNormalizedEmail(em, email).orElse(null);
                    if (found == null) {
                        found =
                                userRepository
                                        .findByNormalizedUserName(em, identifier)
                                        .orElse(null);
                    }
                    if (found == null && "admin".equalsIgnoreCase(identifier)) {
                        String adminEmail =
                                EmailNormalizer.normalize(IdentityConfig.getAdminEmail());
                        found = userRepository.findAdminByAlias(em, adminEmail).orElse(null);
                    }
                    return found;
                });
    }

    private void verifyPasswordOrFail(User user, String password) {
        if (user == null) {
            passwordHasher.verify(password, DUMMY_HASH);
            throw invalidCredentials();
        }
        if (!passwordHasher.verify(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
    }

    private static void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IdentityException(
                    IdentityError.ACCOUNT_DISABLED, "Tài khoản của bạn đã bị vô hiệu hóa");
        }
    }

    /** Re-hashes passwords stored with fewer iterations while the plain password is at hand. */
    private void upgradeHashIfNeeded(User user, String password) {
        if (!passwordHasher.needsRehash(user.getPasswordHash())) {
            return;
        }
        String upgraded = passwordHasher.hash(password);
        try {
            transactions.execute(
                    DatabasePrincipal.AUTH,
                    em -> {
                        userRepository
                                .findById(em, user.getId())
                                .ifPresent(
                                        current -> {
                                            current.rehashPassword(upgraded);
                                            userRepository.save(em, current);
                                        });
                        return null;
                    });
        } catch (RuntimeException ignored) {
            // The login itself succeeded; the upgrade is retried on the next login.
        }
    }

    private OrganizationRole primaryRoleOf(User user) {
        return transactions.execute(
                DatabasePrincipal.AUTH,
                em -> {
                    try {
                        return membershipRepository.findPrimaryRole(em, user.getId()).orElse(null);
                    } catch (RuntimeException unavailable) {
                        // A user without a readable membership simply signs in without an
                        // organisation role.
                        return null;
                    }
                });
    }

    private static UserPrincipal toPrincipal(User user, OrganizationRole organizationRole) {
        return new UserPrincipal(
                user.getId(),
                user.getPlatformRole(),
                user.getAuthVersion(),
                organizationRole,
                user.getEmail());
    }

    private static IdentityException invalidCredentials() {
        return new IdentityException(
                IdentityError.INVALID_CREDENTIALS, "Email hoặc mật khẩu không chính xác");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
