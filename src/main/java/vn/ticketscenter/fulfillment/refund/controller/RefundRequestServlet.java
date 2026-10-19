package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.refund.dto.RefundDetailDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.fulfillment.refund.service.RefundRequestService;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

/**
 * Buyer refund requests: the form page, my-refunds page, JSON list, detail, attempts, and create.
 */
@WebServlet(
        name = "RefundRequestServlet",
        urlPatterns = {
            "/api/refund-requests",
            "/api/refund-requests/*",
            "/refund-requests",
            "/refund-requests/*",
            "/api/me/refund-requests",
            "/my-refunds"
        })
public final class RefundRequestServlet extends HttpServlet {
    private static final String API_PATH = "/api/refund-requests";
    private static final String PAGE_PATH = "/refund-requests";
    private static final String LEGACY_PAGE_PATH = "/refund-request";
    private static final String MY_API_PATH = "/api/me/refund-requests";
    private static final String MY_PAGE_PATH = "/my-refunds";
    private static final String ATTEMPTS_SUFFIX = "/attempts";
    private static final String CREATED_MESSAGE =
            "Yêu cầu hoàn tiền đã được tiếp nhận và chuyển đến Ban quản trị xem xét.";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            UserPrincipal actor = RefundWeb.actor(request);
            RefundRequestService refunds = RefundRuntime.resolve(getServletContext());
            String path = RefundWeb.path(request);

            if (path.equals(PAGE_PATH) || path.equals(LEGACY_PAGE_PATH)) {
                showForm(request, response, actor, refunds);
            } else if (path.equals(MY_PAGE_PATH)) {
                showMyRefunds(request, response, actor, refunds);
            } else if (path.equals(API_PATH) || path.equals(MY_API_PATH)) {
                writeList(request, response, actor, refunds);
            } else {
                writeDetail(response, actor, refunds, detailTail(path));
            }
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            if (!RefundWeb.path(request).equals(API_PATH)) {
                throw new RefundException(
                        405, "METHOD_NOT_ALLOWED", "Đường dẫn này chỉ cho phép xem yêu cầu.");
            }
            UserPrincipal actor = RefundWeb.actor(request);
            var key = RefundWeb.mutation(request);
            var command = RefundWeb.create(request);

            var created = RefundRuntime.resolve(getServletContext()).create(actor, command, key);
            HttpResponses.created(response, created.json(CREATED_MESSAGE));
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }

    private static void showForm(
            HttpServletRequest request,
            HttpServletResponse response,
            UserPrincipal actor,
            RefundRequestService refunds)
            throws IOException, ServletException {
        request.setAttribute("refundOrders", refunds.orders(actor));
        request.setAttribute("selectedOrder", request.getParameter("orderId"));
        SecurityContext.getOrCreateCsrfToken(request);
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/views/refund/request-form.jsp").forward(request, response);
    }

    private static void showMyRefunds(
            HttpServletRequest request,
            HttpServletResponse response,
            UserPrincipal actor,
            RefundRequestService refunds)
            throws IOException, ServletException {
        int page = RefundWeb.page(request.getParameter("page"), 1);
        int size = RefundWeb.page(request.getParameter("pageSize"), 20);
        request.setAttribute("refundPage", refunds.list(actor, page, size));
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/views/refund/my-refunds.jsp")
                .forward(request, response);
    }

    private static void writeList(
            HttpServletRequest request,
            HttpServletResponse response,
            UserPrincipal actor,
            RefundRequestService refunds)
            throws IOException {
        int page = RefundWeb.page(request.getParameter("page"), 1);
        int size = RefundWeb.page(request.getParameter("pageSize"), 20);
        HttpResponses.data(response, refunds.list(actor, page, size).json());
    }

    /** {@code tail} is "{refundId}" or "{refundId}/attempts". */
    private static void writeDetail(
            HttpServletResponse response, UserPrincipal actor, RefundRequestService refunds, String tail)
            throws IOException {
        boolean attemptsOnly = tail.endsWith(ATTEMPTS_SUFFIX);
        String refundId = attemptsOnly ? tail.substring(0, tail.length() - ATTEMPTS_SUFFIX.length()) : tail;

        RefundDetailDto detail = refunds.detail(actor, RefundWeb.id(refundId));
        String json = attemptsOnly ? RefundDetailDto.attemptJson(detail.attempts()) : detail.json();
        HttpResponses.data(response, json);
    }

    private static String detailTail(String path) {
        String prefix = path.startsWith(API_PATH) ? API_PATH : PAGE_PATH;
        return path.substring(prefix.length() + 1);
    }
}
