package vn.ticketscenter.config.persistence;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.config.seed.AdminSeeder;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.config.worker.EventCancellationJob;
import vn.ticketscenter.config.worker.RefundJob;
import vn.ticketscenter.event.repository.EventCancellationRepository;
import vn.ticketscenter.fulfillment.repository.RefundRepository;
import vn.ticketscenter.payment.integration.RefundGateway;
import vn.ticketscenter.payment.integration.SimulatedRefundGateway;

import java.time.Clock;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public final class PersistenceListener implements ServletContextListener {

    public static final String REGISTRY_ATTRIBUTE = PersistenceRegistry.class.getName();
    private static final Logger LOGGER = Logger.getLogger(PersistenceListener.class.getName());
    private ScheduledExecutorService cancellationWorker;
    private ScheduledExecutorService refundWorker;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        AdminSeeder.validateProductionSecret(ServerConfig.getEnv(), AppConfig.get("admin.password"));
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            new AdminSeeder(new PasswordHasher(), Clock.systemUTC()).seed(registry.transactionManager());
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Database persistence initialization failed", exception);
        }
        event.getServletContext().setAttribute(REGISTRY_ATTRIBUTE, registry);
        if (registry.hasPrincipal(DatabasePrincipal.WORKER)) {
            EventCancellationJob job = new EventCancellationJob(
                    registry.transactionManager(), new EventCancellationRepository(), 50);
            cancellationWorker = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().name("event-cancellation-worker").daemon(true).factory());
            cancellationWorker.scheduleWithFixedDelay(() -> runCancellation(job), 0, 5, TimeUnit.SECONDS);
            RefundJob refundJob = new RefundJob(registry.transactionManager(), new RefundRepository(),
                    new SimulatedRefundGateway(
                            Path.of(AppConfig.get(AppConfig.Keys.REFUND_STATE_DIRECTORY, "data/refund-gateway")),
                            RefundGateway.Status.valueOf(AppConfig.get(AppConfig.Keys.REFUND_SIMULATED_OUTCOME, "SUCCEEDED").toUpperCase()),
                            Boolean.parseBoolean(AppConfig.get(AppConfig.Keys.REFUND_TIMEOUT_AFTER_SIDE_EFFECT, "false"))),
                    50);
            refundWorker = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().name("refund-worker").daemon(true).factory());
            refundWorker.scheduleWithFixedDelay(() -> runRefund(refundJob), 0, 5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (cancellationWorker != null) cancellationWorker.shutdownNow();
        if (refundWorker != null) refundWorker.shutdownNow();
        Object registry = event.getServletContext().getAttribute(REGISTRY_ATTRIBUTE);
        if (registry instanceof PersistenceRegistry persistenceRegistry) {
            persistenceRegistry.close();
        }
        try {
            vn.ticketscenter.config.redis.RedisClientProvider.getInstance().close();
        } catch (Exception ignored) {
        }
    }

    private static void runCancellation(EventCancellationJob job) {
        try {
            job.runOnce();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Event cancellation batch failed; it will be retried", exception);
        }
    }

    private static void runRefund(RefundJob job) {
        try {
            job.runOnce();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Refund batch failed; it will be retried", exception);
        }
    }
}
