package vn.ticketscenter.payment.controller;

import vn.ticketscenter.config.web.ApiRequest;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.payment.dao.CompensationRetryRepository;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "AdminCompensationRetryServlet", urlPatterns = "/api/admin/payments/*")
public class AdminCompensationRetryServlet extends HttpServlet {
    private static final Logger LOGGER =
            Logger.getLogger(AdminCompensationRetryServlet.class.getName());
    private static final Pattern ROUTE =
            Pattern.compile("^/api/admin/payments/([0-9a-fA-F-]{36})/compensation/retry$");

    private final BiFunction<UserPrincipal, UUID, UUID> injected;

    public AdminCompensationRetryServlet() {
        this(null);
    }

    public AdminCompensationRetryServlet(BiFunction<UserPrincipal, UUID, UUID> retry) {
        this.injected = retry;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Matcher route =
                ROUTE.matcher(ApiRequest.path(request));
        if (!route.matches()) {
            HttpResponses.error(response, 404, "ROUTE_NOT_FOUND", "Đường dẫn không hợp lệ.");
            return;
        }
        UserPrincipal actor = SecurityContext.getCurrentUser(request).orElse(null);
        if (actor == null) {
            HttpResponses.error(response, 401, "UNAUTHENTICATED", "Bạn cần đăng nhập.");
            return;
        }
        if (!actor.isAdmin()) {
            HttpResponses.error(
                    response, 403, "ADMIN_REQUIRED", "Chỉ quản trị viên mới được thử lại bù trừ.");
            return;
        }
        // Checked here as well as in CsrfFilter so the write never depends on filter wiring alone.
        if (!SecurityContext.validateCsrfToken(request)) {
            HttpResponses.error(
                    response, 403, "CSRF_TOKEN_INVALID", "Yêu cầu thiếu hoặc mã CSRF không hợp lệ");
            return;
        }
        UUID payment;
        try {
            payment = UUID.fromString(route.group(1));
        } catch (IllegalArgumentException invalid) {
            HttpResponses.error(response, 400, "INVALID_PAYMENT_ID", "Mã thanh toán không hợp lệ.");
            return;
        }
        try {
            UUID compensation =
                    injected != null
                            ? injected.apply(actor, payment)
                            : retry(request, actor, payment);
            HttpResponses.data(
                    response,
                    "{\"compensationId\":\""
                            + compensation
                            + "\",\"paymentId\":\""
                            + payment
                            + "\",\"status\":\"PENDING\"}");
        } catch (CompensationRetryRepository.Rejected rejected) {
            HttpResponses.error(response, rejected);
        } catch (RuntimeException failure) {
            LOGGER.log(Level.SEVERE, "Compensation retry failed", failure);
            HttpResponses.error(
                    response,
                    503,
                    "COMPENSATION_UNAVAILABLE",
                    "Chưa thể xử lý yêu cầu. Vui lòng thử lại.");
        }
    }

    private UUID retry(HttpServletRequest request, UserPrincipal actor, UUID payment) {
        if (!(request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE)
                        instanceof PersistenceRegistry registry)
                || !registry.hasPrincipal(DatabasePrincipal.ADMIN)) {
            throw new IllegalStateException("Admin database principal is not configured");
        }
        return new CompensationRetryRepository(registry.transactionManager()).retry(actor, payment);
    }
}
