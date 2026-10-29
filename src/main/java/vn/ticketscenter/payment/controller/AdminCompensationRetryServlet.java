package vn.ticketscenter.payment.controller;

import vn.ticketscenter.config.error.AppException;
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
        try {
            Matcher route = route(request);
            UserPrincipal actor = requireAdmin(request);
            // Checked here as well as in CsrfFilter so the write never depends on filter wiring alone.
            if (!SecurityContext.validateCsrfToken(request)) {
                throw new AppException(
                        403, "CSRF_TOKEN_INVALID", "Yêu cầu thiếu hoặc mã CSRF không hợp lệ");
            }
            UUID payment = paymentId(route);

            // retry the compensation and write the JSON reply
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
        } catch (AppException rejected) {
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

    /** Checked before the actor so unknown routes answer 404. */
    private static Matcher route(HttpServletRequest request) {
        Matcher route = ROUTE.matcher(ApiRequest.path(request));
        if (!route.matches()) {
            throw new AppException(404, "ROUTE_NOT_FOUND", "Đường dẫn không hợp lệ.");
        }
        return route;
    }

    private static UUID paymentId(Matcher route) {
        try {
            return UUID.fromString(route.group(1));
        } catch (IllegalArgumentException invalid) {
            throw new AppException(400, "INVALID_PAYMENT_ID", "Mã thanh toán không hợp lệ.");
        }
    }

    private static UserPrincipal requireAdmin(HttpServletRequest request) {
        UserPrincipal actor = SecurityContext.getCurrentUser(request).orElse(null);
        if (actor == null) {
            throw new AppException(401, "UNAUTHENTICATED", "Bạn cần đăng nhập.");
        }
        if (!actor.isAdmin()) {
            throw new AppException(
                    403, "ADMIN_REQUIRED", "Chỉ quản trị viên mới được thử lại bù trừ.");
        }
        return actor;
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
