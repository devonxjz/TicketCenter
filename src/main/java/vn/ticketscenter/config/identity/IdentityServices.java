package vn.ticketscenter.config.identity;

import java.util.Optional;
import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.identity.integration.mail.EmailSender;
import vn.ticketscenter.identity.integration.mail.EmailSenderImpl;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository;
import vn.ticketscenter.identity.dao.OrganizationMembershipRepository;
import vn.ticketscenter.identity.dao.OrganizationRepository;
import vn.ticketscenter.identity.dao.OtpRepository;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.identity.service.DemoInitialEventFactory;
import vn.ticketscenter.identity.service.LoginService;
import vn.ticketscenter.identity.service.LoginServiceImpl;
import vn.ticketscenter.identity.service.OrganizationApprovalService;
import vn.ticketscenter.identity.service.OrganizationApprovalServiceImpl;
import vn.ticketscenter.identity.service.OrganizationMembershipService;
import vn.ticketscenter.identity.service.OrganizationMembershipServiceImpl;
import vn.ticketscenter.identity.cache.OrganizationRequestIdempotency;
import vn.ticketscenter.identity.service.OrganizationRequestService;
import vn.ticketscenter.identity.service.OrganizationRequestServiceImpl;
import vn.ticketscenter.identity.service.OtpHasher;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.OtpServiceImpl;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.PasswordResetService;
import vn.ticketscenter.identity.service.PasswordResetServiceImpl;
import vn.ticketscenter.identity.service.ProfileService;
import vn.ticketscenter.identity.service.ProfileServiceImpl;
import vn.ticketscenter.identity.service.RegistrationService;
import vn.ticketscenter.identity.service.RegistrationServiceImpl;
import vn.ticketscenter.identity.service.ResetTokenCodec;
import vn.ticketscenter.identity.service.SessionValidator;
import vn.ticketscenter.identity.cache.UserSessionCache;

import java.util.Objects;

/**
 * Composition root of the identity feature: wires every service once and shares the result through the
 * {@link ServletContext}. Servlets and filters look services up with {@link #from(ServletContext)} instead
 * of building their own.
 */
public record IdentityServices(
        RegistrationService registrationService,
        LoginService loginService,
        SessionValidator sessionValidator,
        ProfileService profileService,
        OtpService otpService,
        PasswordResetService passwordResetService,
        OrganizationRequestService organizationRequestService,
        OrganizationApprovalService organizationApprovalService,
        OrganizationMembershipService organizationMembershipService) {

    public static final String ATTRIBUTE = "vn.ticketscenter.identity.SERVICES";

    /** Context attribute that, when it holds a {@link SessionValidator}, replaces the built one. */
    public static final String SESSION_VALIDATOR_ATTRIBUTE =
            "vn.ticketscenter.identity.SESSION_VALIDATOR";

    public static IdentityServices create(
            TransactionManager transactions, RedisClientProvider redis, EmailSender emailSender) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        var users = new UserRepository();
        var hasher = new PasswordHasher();
        var resetTokens = new ResetTokenCodec();
        var sessionCache = new UserSessionCache(redis);
        var memberships = new OrganizationMembershipRepository();
        var organizations = new OrganizationRepository();
        var decisions = new OrganizationDecisionRepository();
        var otpService =
                new OtpServiceImpl(
                        transactions, new OtpRepository(), users, new OtpHasher(), resetTokens, emailSender);
        return new IdentityServices(
                new RegistrationServiceImpl(transactions, users, hasher),
                new LoginServiceImpl(transactions, users, memberships, hasher),
                new SessionValidator(transactions, users, sessionCache),
                new ProfileServiceImpl(transactions, users),
                otpService,
                new PasswordResetServiceImpl(transactions, users, otpService, resetTokens, hasher, sessionCache),
                new OrganizationRequestServiceImpl(
                        transactions,
                        organizations,
                        users,
                        decisions,
                        OrganizationRequestIdempotency.redis(redis)),
                new OrganizationApprovalServiceImpl(
                        transactions, organizations, users, decisions, new DemoInitialEventFactory()),
                new OrganizationMembershipServiceImpl(transactions, memberships, users));
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
        if (context.getAttribute(ATTRIBUTE) instanceof IdentityServices services) {
            return services;
        }
        synchronized (context) {
            if (context.getAttribute(ATTRIBUTE) instanceof IdentityServices services) {
                return services;
            }
            Optional<PersistenceRegistry> registry = FeatureContext.registry(context).filter(PersistenceRegistry::isReady);
            if (registry.isEmpty()) {
                return null;
            }
            TransactionManager transactions = registry.get().transactionManager();
            IdentityServices services = create(transactions, RedisClientProvider.getInstance(), new EmailSenderImpl());
            context.setAttribute(ATTRIBUTE, services);
            return services;
        }
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
        return services == null ? null : services.sessionValidator();
    }
}
