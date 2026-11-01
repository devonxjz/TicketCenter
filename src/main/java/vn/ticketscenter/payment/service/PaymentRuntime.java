package vn.ticketscenter.payment.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.payment.PayosConfig;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.worker.PaymentReconciliationJob;
import vn.ticketscenter.payment.cache.RedisPaymentIdempotency;
import vn.ticketscenter.payment.integration.PayosGatewayImpl;
import vn.ticketscenter.payment.integration.PayosGateway;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.payment.dao.CompensationRetryRepository;
import vn.ticketscenter.payment.dao.CompensationRetryStore;
import vn.ticketscenter.payment.dao.PaymentRepository;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.ADMIN;
import static vn.ticketscenter.config.persistence.DatabasePrincipal.BUYER;
import static vn.ticketscenter.config.persistence.DatabasePrincipal.WORKER;

/**
 * Wires the payment feature (servlet replacement for Spring's beans): DB pools → repository, payOS client and
 * Redis idempotency → {@link PaymentService}. Built on first use, shared by requests and the reconciliation job,
 * closed with the web application.
 */
@WebListener
public final class PaymentRuntime implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(PaymentRuntime.class.getName());
    private static final String COMPONENTS = PaymentRuntime.class.getName() + ".components";

    private ScheduledExecutorService reconciliation;

    /** Only touched by the reconciliation thread; keeps its retry back-off between runs. */
    private PaymentReconciliationJob reconciliationJob;

    public static final String SERVICE_ATTRIBUTE = PaymentService.class.getName();

    /** The payment service; one already placed in the context under {@link #SERVICE_ATTRIBUTE} wins. */
    public static PaymentService resolve(ServletContext context) {
        if (context.getAttribute(SERVICE_ATTRIBUTE) instanceof PaymentService service) {
            return service;
        }
        return components(context).service();
    }

    /** Admin retry of a failed compensation; needs the ADMIN database principal. */
    public static CompensationRetryStore compensationRetry(ServletContext context) {
        return FeatureContext.cached(
                context, CompensationRetryStore.class.getName(), () -> createCompensationRetry(context));
    }

    private static CompensationRetryStore createCompensationRetry(ServletContext context) {
        PersistenceRegistry registry =
                FeatureContext.registry(context)
                        .filter(pool -> pool.hasPrincipal(ADMIN))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Admin database principal is not configured"));
        return new CompensationRetryRepository(registry.transactionManager());
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        FeatureContext.open(context, COMPONENTS);
        if (!reconciliationEnabled()) {
            LOGGER.info("Payment reconciliation disabled");
            return;
        }
        reconciliation =
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().name("payment-reconciliation").daemon(true).factory());
        reconciliation.scheduleWithFixedDelay(() -> reconcile(context), 30, 60, TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (reconciliation != null) {
            reconciliation.shutdownNow();
        }
        FeatureContext.close(event.getServletContext(), COMPONENTS);
    }

    /** Off when payment.reconciliation.enabled=false; on by default whenever payOS credentials exist. */
    private static boolean reconciliationEnabled() {
        return PayosConfig.isConfigured()
                && AppConfig.getBoolean("payment.reconciliation.enabled", true);
    }

    private void reconcile(ServletContext context) {
        try {
            if (reconciliationJob == null) {
                reconciliationJob = components(context).reconciliationJob();
            }
            reconciliationJob.runOnce();
        } catch (RuntimeException failure) {
            LOGGER.log(
                    Level.WARNING,
                    "Payment reconciliation batch failed; it will be retried",
                    failure);
        }
    }

    private static Components components(ServletContext context) {
        return FeatureContext.resolve(context, COMPONENTS, Components::create, PaymentRuntime::unavailable);
    }

    private static RuntimeException unavailable(RuntimeException failure) {
        if (failure instanceof PaymentException payment) {
            return payment;
        }
        LOGGER.log(Level.WARNING, "Payment feature could not start", failure);
        return PaymentException.unavailable();
    }

    /** Everything the payment feature needs, created together and closed together. */
    private record Components(
            PaymentService service,
            PaymentRepository repository,
            PayosGateway payos,
            RedisPaymentIdempotency idempotency) {

        static Components create(FeatureContext.Resources resources) {
            if (!PayosConfig.isConfigured()) {
                LOGGER.warning(
                        "payOS is not configured (payos.client.id / payos.api.key / payos.checksum.key)");
                throw PaymentException.unavailable();
            }
            PayosGateway payos = resources.track(new PayosGatewayImpl(PayosConfig.fromEnvironment()));
            RedisPaymentIdempotency idempotency = newIdempotency();
            HikariDataSource buyer = resources.strictPool("payment-buyer", BUYER);
            HikariDataSource worker = resources.strictPool("payment-worker", WORKER);
            PaymentRepository repository =
                    new PaymentRepository(ConnectionBudgets.wrap(buyer), ConnectionBudgets.wrap(worker));
            PaymentService service = new PaymentServiceImpl(repository, payos, idempotency, Clock.systemUTC());
            return new Components(service, repository, payos, idempotency);
        }

        /** Settles PENDING/UNKNOWN payments by asking payOS; links never seen after the window are failures. */
        PaymentReconciliationJob reconciliationJob() {
            Clock clock = Clock.systemUTC();
            return new PaymentReconciliationJob(
                    repository::findPaymentsToReconcile,
                    c -> {
                        boolean expired =
                                clock.instant().isAfter(c.createdAt().plus(PayosGateway.PAYMENT_WINDOW));
                        return payos.query(c.txnRef(), c.amount(), expired);
                    },
                    (c, status, reference) ->
                            repository.applyPaymentResult(c.orderId(), c.paymentId(), status, reference),
                    idempotency::acquireOrderLock,
                    20);
        }

        private static RedisPaymentIdempotency newIdempotency() {
            if (AppConfig.getBoolean("finance.redis.enabled", true)) {
                try {
                    RedisClientProvider redis = RedisClientProvider.getInstance();
                    if (redis.isAvailable()) {
                        return new RedisPaymentIdempotency(redis::getResource);
                    }
                } catch (RuntimeException unavailable) {
                    // fall through to the in-memory store
                }
            }
            LOGGER.info(
                    "Redis is unavailable or disabled; falling back to in-memory payment idempotency");
            return RedisPaymentIdempotency.inMemory();
        }
    }
}
