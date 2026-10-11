package vn.ticketscenter.payment.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.payment.cache.RedisPaymentIdempotency;
import vn.ticketscenter.payment.repository.PaymentRepository;

import java.time.Clock;

@WebListener
public final class PaymentRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = PaymentService.class.getName();
    private static final String POOL_ATTRIBUTE = PaymentRuntime.class.getName() + ".pool";
    private static final String CLOSED_ATTRIBUTE = PaymentRuntime.class.getName() + ".closed";

    public static PaymentService resolve(ServletContext context) {
        synchronized (context) {
            if (Boolean.TRUE.equals(context.getAttribute(CLOSED_ATTRIBUTE))) {
                throw PaymentException.unavailable();
            }
            if (context.getAttribute(SERVICE_ATTRIBUTE) instanceof PaymentService service) {
                return service;
            }
            HikariDataSource pool = null;
            try {
                String user = AppConfig.get("db.buyer.user");
                String password = AppConfig.get("db.buyer.password");
                if (user == null || user.isBlank() || password == null || password.isBlank()) {
                    throw PaymentException.unavailable();
                }
                HikariConfig config = new HikariConfig();
                config.setPoolName("payment-buyer");
                config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
                config.setUsername(user);
                config.setPassword(password);
                config.setMaximumPoolSize(1);
                config.setMinimumIdle(0);
                config.setInitializationFailTimeout(-1);
                config.setConnectionTimeout(10_000);
                config.setAutoCommit(true);
                pool = new HikariDataSource(config);

                String tmnCode = AppConfig.get(AppConfig.Keys.VNPAY_TMN_CODE);
                String hashSecret = AppConfig.get(AppConfig.Keys.VNPAY_HASH_SECRET);
                String payUrl = AppConfig.get(AppConfig.Keys.VNPAY_PAY_URL);
                String returnUrl = AppConfig.get(AppConfig.Keys.VNPAY_RETURN_URL);
                if (tmnCode == null || hashSecret == null || payUrl == null || returnUrl == null) {
                    throw PaymentException.unavailable();
                }

                VnpaySigner signer = new VnpaySigner(tmnCode, hashSecret, payUrl, returnUrl, Clock.systemUTC());
                var redis = RedisClientProvider.getInstance();
                RedisPaymentIdempotency idempotency = new RedisPaymentIdempotency(redis::getResource);
                PaymentRepository repository = new PaymentRepository(ConnectionBudgets.wrap(pool));

                PaymentService service = new PaymentService(repository, signer, idempotency);
                context.setAttribute(POOL_ATTRIBUTE, pool);
                context.setAttribute(SERVICE_ATTRIBUTE, service);
                return service;
            } catch (RuntimeException failure) {
                if (pool != null) pool.close();
                throw PaymentException.unavailable();
            }
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        event.getServletContext().setAttribute(CLOSED_ATTRIBUTE, false);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        synchronized (event.getServletContext()) {
            event.getServletContext().setAttribute(CLOSED_ATTRIBUTE, true);
            if (event.getServletContext().getAttribute(POOL_ATTRIBUTE) instanceof HikariDataSource pool) {
                pool.close();
            }
            event.getServletContext().removeAttribute(SERVICE_ATTRIBUTE);
            event.getServletContext().removeAttribute(POOL_ATTRIBUTE);
        }
    }
}
