package vn.ticketscenter.config.persistence;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.config.worker.EventCancellationJob;
import vn.ticketscenter.config.worker.OutboxEmailJob;
import vn.ticketscenter.config.worker.OutboxWorker;
import vn.ticketscenter.config.worker.RefundJob;
import vn.ticketscenter.event.repository.EventCancellationRepository;
import vn.ticketscenter.payment.integration.RefundGateway;
import vn.ticketscenter.payment.integration.SimulatedRefundGateway;
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
    private ScheduledExecutorService outboxExecutor;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Database persistence initialization failed", exception);
        }
        event.getServletContext().setAttribute(REGISTRY_ATTRIBUTE, registry);
        if (registry.hasPrincipal(DatabasePrincipal.BUYER)) {
            try {
                vn.ticketscenter.event.controller.EventCatalogRuntime.resolve(event.getServletContext()).warmAsync();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Không thể khởi động warm-up Redis cho event catalog", ex);
            }
        }
        if (registry.hasPrincipal(DatabasePrincipal.WORKER)) {
            EventCancellationJob job = new EventCancellationJob(
                    registry.transactionManager(), new EventCancellationRepository(), 50);
            cancellationWorker = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().name("event-cancellation-worker").daemon(true).factory());
            cancellationWorker.scheduleWithFixedDelay(() -> runCancellation(job), 0, 5, TimeUnit.SECONDS);
            // payOS has no refund API: refunds go through the simulated gateway (receipts stored on disk).
            if (AppConfig.getBoolean("refund.worker.enabled", false)) {
                try {
                    RefundJob refundJob = new RefundJob(registry.transactionManager(), simulatedRefundGateway(), 50);
                    refundWorker = Executors.newSingleThreadScheduledExecutor(
                            Thread.ofPlatform().name("refund-worker").daemon(true).factory());
                    refundWorker.scheduleWithFixedDelay(() -> runRefund(refundJob), 0, 5, TimeUnit.SECONDS);
                } catch (RuntimeException unavailable) {
                    LOGGER.warning("Simulated refund worker configuration is invalid; refunds require reconciliation");
                }
            }
            if (ServerConfig.isDemoData() && !"false".equalsIgnoreCase(String.valueOf(AppConfig.get("outbox.worker.enabled")).trim())) {
                String owner = hostname() + "-" + java.util.UUID.randomUUID();
                OutboxWorker outbox = new OutboxWorker(registry.transactionManager(),
                        new OutboxEmailJob(OutboxEmailJob.databaseLoader(registry.transactionManager()),
                                new vn.ticketscenter.identity.integration.mail.SmtpEmailSender()), owner);
                outboxExecutor = Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().name("outbox-worker").daemon(true).factory());
                outboxExecutor.scheduleWithFixedDelay(() -> runOutbox(outbox), 10, 10, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (cancellationWorker != null) cancellationWorker.shutdownNow();
        if (refundWorker != null) refundWorker.shutdownNow();
        if (outboxExecutor != null) outboxExecutor.shutdownNow();
        Object registry = event.getServletContext().getAttribute(REGISTRY_ATTRIBUTE);
        if (registry instanceof PersistenceRegistry persistenceRegistry) {
            persistenceRegistry.close();
        }
        try {
            vn.ticketscenter.config.redis.RedisClientProvider.getInstance().close();
        } catch (Exception ignored) {
        }
    }

    private static RefundGateway simulatedRefundGateway() {
        return new SimulatedRefundGateway(
                java.nio.file.Path.of(AppConfig.get(AppConfig.Keys.REFUND_STATE_DIRECTORY, "data/refund-gateway")),
                RefundGateway.Status.valueOf(AppConfig.get(AppConfig.Keys.REFUND_SIMULATED_OUTCOME, "SUCCEEDED")),
                AppConfig.getBoolean(AppConfig.Keys.REFUND_TIMEOUT_AFTER_SIDE_EFFECT, false));
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

    private static void runOutbox(OutboxWorker worker) {
        try {
            worker.runOnce();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Outbox batch failed; it will be retried", exception);
        }
    }

    private static String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.io.IOException | RuntimeException exception) {
            return "node";
        }
    }
}
