package vn.ticketscenter.identity.config;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.identity.integration.mail.EmailSender;
import vn.ticketscenter.identity.integration.mail.SmtpEmailSender;
import vn.ticketscenter.identity.repository.OrganizationMembershipRepository;
import vn.ticketscenter.identity.repository.OrganizationRepository;
import vn.ticketscenter.identity.repository.OtpRepository;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.AuthService;
import vn.ticketscenter.identity.service.OrganizationMembershipService;
import vn.ticketscenter.identity.service.OrganizationRequestService;
import vn.ticketscenter.identity.service.OtpCryptoHelper;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.PasswordResetService;

import java.util.Objects;

/**
 * Composition root of the identity feature: wires every service once and shares the result through the
 * {@link ServletContext}. Servlets and filters look services up with {@link #from(ServletContext)} instead
 * of building their own.
 */
public final class IdentityServices {

    public static final String ATTRIBUTE = "vn.ticketscenter.identity.SERVICES";

    private final AuthService authService;
    private final OtpService otpService;
    private final PasswordResetService passwordResetService;
    private final OrganizationRequestService organizationRequestService;
    private final OrganizationMembershipService organizationMembershipService;

    public IdentityServices(TransactionManager transactions, RedisClientProvider redis, EmailSender emailSender) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        UserRepository users = new UserRepository();
        PasswordHasher hasher = new PasswordHasher();
        OtpCryptoHelper otpCrypto = new OtpCryptoHelper();

        this.authService = new AuthService(transactions, users, hasher, redis);
        this.otpService = new OtpService(transactions, new OtpRepository(), users, otpCrypto, emailSender);
        this.passwordResetService = new PasswordResetService(transactions, users, otpService, otpCrypto, hasher, redis);
        this.organizationRequestService =
                new OrganizationRequestService(transactions, new OrganizationRepository(), users, redis);
        this.organizationMembershipService =
                new OrganizationMembershipService(transactions, new OrganizationMembershipRepository(), users);
    }

    /**
     * Returns the services bound to the context, building them on first use once persistence is ready.
     *
     * @return the services, or {@code null} while the database is not available
     */
    public static IdentityServices from(ServletContext context) {
        if (context == null) {
            return null;
        }
        Object existing = context.getAttribute(ATTRIBUTE);
        if (existing instanceof IdentityServices services) {
            return services;
        }
        synchronized (context) {
            existing = context.getAttribute(ATTRIBUTE);
            if (existing instanceof IdentityServices services) {
                return services;
            }
            Object registry = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (registry instanceof PersistenceRegistry ready && ready.isReady()) {
                IdentityServices services = new IdentityServices(
                        ready.transactionManager(), RedisClientProvider.getInstance(), new SmtpEmailSender());
                context.setAttribute(ATTRIBUTE, services);
                return services;
            }
        }
        return null;
    }

    public AuthService authService() {
        return authService;
    }

    public OtpService otpService() {
        return otpService;
    }

    public PasswordResetService passwordResetService() {
        return passwordResetService;
    }

    public OrganizationRequestService organizationRequestService() {
        return organizationRequestService;
    }

    public OrganizationMembershipService organizationMembershipService() {
        return organizationMembershipService;
    }
}
