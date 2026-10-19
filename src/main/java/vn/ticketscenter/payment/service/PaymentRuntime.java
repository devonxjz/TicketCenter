package vn.ticketscenter.payment.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.PayosConfig;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.SingleConnectionPools;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.worker.PaymentReconciliationJob;
import vn.ticketscenter.payment.cache.RedisPaymentIdempotency;
import vn.ticketscenter.payment.integration.PayosClient;
import vn.ticketscenter.payment.integration.PayosGateway;
import vn.ticketscenter.payment.dao.PaymentRepository;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wires the payment feature (servlet replacement for Spring's beans): DB pools → repository, payOS client and
 * Redis idempotency → {@link PaymentService}. Built on first use, shared by requests and the reconciliation job,
 * closed with the web application.
 */
@WebListener
public final class PaymentRuntime implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(PaymentRuntime.class.getName());
    private static final String COMPONENTS = PaymentRuntime.class.getName() + ".components";
    private static final String CLOSED = PaymentRuntime.class.getName() + ".closed";

    private ScheduledExecutorService reconciliation;

    /** Only touched by the reconciliation thread; keeps its retry back-off between runs. */
    private PaymentReconciliationJob reconciliationJob;

    public static PaymentService resolve(ServletContext context) {
        return components(context).service();
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        context.setAttribute(CLOSED, false);
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
        if (reconciliation != null) reconciliation.shutdownNow();
        ServletContext context = event.getServletContext();
        synchronized (context) {
            context.setAttribute(CLOSED, true);
            if (context.getAttribute(COMPONENTS) instanceof Components components)
                components.close();
            context.removeAttribute(COMPONENTS);
        }
    }

    /** Off when payment.reconciliation.enabled=false; on by default whenever payOS credentials exist. */
    private static boolean reconciliationEnabled() {
        return PayosConfig.isConfigured()
                && AppConfig.getBoolean("payment.reconciliation.enabled", true);
    }

    private void reconcile(ServletContext context) {
        try {
            if (reconciliationJob == null)
                reconciliationJob = components(context).reconciliationJob();
            reconciliationJob.runOnce();
        } catch (RuntimeException failure) {
            LOGGER.log(
                    Level.WARNING,
                    "Payment reconciliation batch failed; it will be retried",
                    failure);
        }
    }

    private static Components components(ServletContext context) {
        synchronized (context) {
            if (Boolean.TRUE.equals(context.getAttribute(CLOSED)))
                throw PaymentException.unavailable();
            if (context.getAttribute(COMPONENTS) instanceof Components existing) return existing;
            Components created = Components.create();
            context.setAttribute(COMPONENTS, created);
            return created;
        }
    }

    /** Everything the payment feature needs, created together and closed together. */
    private record Components(
            PaymentService service,
            PaymentRepository repository,
            PayosGateway payos,
            RedisPaymentIdempotency idempotency,
            HikariDataSource buyerPool,
            HikariDataSource workerPool) {

        static Components create() {
            if (!PayosConfig.isConfigured()) {
                LOGGER.warning(
                        "payOS is not configured (payos.client.id / payos.api.key / payos.checksum.key)");
                throw PaymentException.unavailable();
            }
            PayosGateway payos = null;
            HikariDataSource buyerPool = null;
            HikariDataSource workerPool = null;
            try {
                payos = new PayosClient(PayosConfig.fromEnvironment());
                RedisPaymentIdempotency idempotency = newIdempotency();
                buyerPool = newPool("buyer");
                workerPool = newPool("worker");
                PaymentRepository repository =
                        new PaymentRepository(
                                ConnectionBudgets.wrap(buyerPool),
                                ConnectionBudgets.wrap(workerPool));
                PaymentService service =
                        new PaymentService(repository, payos, idempotency, Clock.systemUTC());
                return new Components(
                        service, repository, payos, idempotency, buyerPool, workerPool);
            } catch (RuntimeException failure) {
                if (payos != null) payos.close();
                if (buyerPool != null) buyerPool.close();
                if (workerPool != null) workerPool.close();
                LOGGER.log(Level.WARNING, "Payment feature could not start", failure);
                throw PaymentException.unavailable();
            }
        }

        /** Settles PENDING/UNKNOWN payments by asking payOS; links never seen after the window are failures. */
        PaymentReconciliationJob reconciliationJob() {
            Clock clock = Clock.systemUTC();
            return new PaymentReconciliationJob(
                    repository::findPaymentsToReconcile,
                    c ->
                            payos.query(
                                    c.txnRef(),
                                    c.amount(),
                                    clock.instant()
                                            .isAfter(
                                                    c.createdAt()
                                                            .plus(PayosGateway.PAYMENT_WINDOW))),
                    (c, status, reference) ->
                            repository.applyPaymentResult(
                                    c.orderId(), c.paymentId(), status, reference),
                    idempotency::acquireOrderLock,
                    20);
        }

        void close() {
            payos.close();
            buyerPool.close();
            workerPool.close();
        }

        private static RedisPaymentIdempotency newIdempotency() {
            if (AppConfig.getBoolean("finance.redis.enabled", true)) {
                try {
                    var redis = RedisClientProvider.getInstance();
                    if (redis.isAvailable()) return new RedisPaymentIdempotency(redis::getResource);
                } catch (RuntimeException unavailable) {
                    // fall through to the in-memory store
                }
            }
            LOGGER.info(
                    "Redis is unavailable or disabled; falling back to in-memory payment idempotency");
            return RedisPaymentIdempotency.inMemory();
        }

        private static HikariDataSource newPool(String principal) {
            String user = AppConfig.get("db." + principal + ".user");
            String password = AppConfig.get("db." + principal + ".password");
            if (user == null || password == null)
                throw new IllegalStateException("db." + principal + ".user/password are required");
            return SingleConnectionPools.open("payment-" + principal, user, password);
        }
    }
}
