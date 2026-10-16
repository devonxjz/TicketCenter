package vn.ticketscenter.identity.config;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.identity.integration.mail.EmailSender;
import vn.ticketscenter.identity.integration.mail.SmtpEmailSender;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository;
import vn.ticketscenter.identity.dao.OrganizationRepository;
import vn.ticketscenter.identity.dao.OtpRepository;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.identity.service.DemoInitialEventFactory;
import vn.ticketscenter.identity.service.LoginService;
import vn.ticketscenter.identity.service.OrganizationApprovalService;
import vn.ticketscenter.identity.service.OrganizationMembershipService;
import vn.ticketscenter.identity.service.OrganizationRequestIdempotency;
import vn.ticketscenter.identity.service.OrganizationRequestService;
import vn.ticketscenter.identity.service.OtpHasher;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.PasswordResetService;
import vn.ticketscenter.identity.service.ProfileService;
import vn.ticketscenter.identity.service.RegistrationService;
import vn.ticketscenter.identity.service.ResetTokenCodec;
import vn.ticketscenter.identity.service.SessionValidator;
import vn.ticketscenter.identity.service.UserSessionCache;

import java.util.Objects;

/**
 * Composition root of the identity feature: wires every service once and shares the result through the
 * {@link ServletContext}. Servlets and filters look services up with {@link #from(ServletContext)} instead
 * of building their own.
 */
public final class IdentityServices {

    public static final String ATTRIBUTE = "vn.ticketscenter.identity.SERVICES";

    /** Context attribute that, when it holds a {@link SessionValidator}, replaces the built one. */
    public static final String SESSION_VALIDATOR_ATTRIBUTE = "vn.ticketscenter.identity.SESSION_VALIDATOR";

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final SessionValidator sessionValidator;
    private final ProfileService profileService;
    private final OtpService otpService;
    private final PasswordResetService passwordResetService;
    private final OrganizationRequestService organizationRequestService;
    private final OrganizationApprovalService organizationApprovalService;
    private final OrganizationMembershipService organizationMembershipService;

    public IdentityServices(TransactionManager transactions, RedisClientProvider redis, EmailSender emailSender) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        UserRepository users = new UserRepository();
        PasswordHasher hasher = new PasswordHasher();
        OtpHasher otpHasher = new OtpHasher();
        ResetTokenCodec resetTokens = new ResetTokenCodec();

        UserSessionCache sessionCache = new UserSessionCache(redis);
        OrganizationMembershipRepository memberships = new OrganizationMembershipRepository();

        this.registrationService = new RegistrationService(transactions, users, hasher);
        this.loginService = new LoginService(transactions, users, memberships, hasher);
        this.sessionValidator = new SessionValidator(transactions, users, sessionCache);
        this.profileService = new ProfileService(transactions, users);
        this.otpService = new OtpService(
                transactions, new OtpRepository(), users, otpHasher, resetTokens, emailSender);
        this.passwordResetService =
                new PasswordResetService(transactions, users, otpService, resetTokens, hasher, sessionCache);
        OrganizationRepository organizations = new OrganizationRepository();
        OrganizationDecisionRepository decisions = new OrganizationDecisionRepository();
        this.organizationRequestService = new OrganizationRequestService(
                transactions, organizations, users, decisions, OrganizationRequestIdempotency.redis(redis));
        this.organizationApprovalService = new OrganizationApprovalService(
                transactions, organizations, users, decisions, new DemoInitialEventFactory());
        this.organizationMembershipService = new OrganizationMembershipService(transactions, memberships, users);
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

    /**
     * The session validator for the context. A validator stored under {@link #SESSION_VALIDATOR_ATTRIBUTE}
     * takes precedence, which lets a test replace the real one.
     */
    public static SessionValidator sessionValidator(ServletContext context) {
        if (context == null) {
            return null;
        }
        if (context.getAttribute(SESSION_VALIDATOR_ATTRIBUTE) instanceof SessionValidator override) {
            return override;
        }
        IdentityServices services = from(context);
        return services == null ? null : services.sessionValidator;
    }

    public RegistrationService registrationService() {
        return registrationService;
    }

    public LoginService loginService() {
        return loginService;
    }

    public ProfileService profileService() {
        return profileService;
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

    public OrganizationApprovalService organizationApprovalService() {
        return organizationApprovalService;
    }

    public OrganizationMembershipService organizationMembershipService() {
        return organizationMembershipService;
    }
}
