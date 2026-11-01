package vn.ticketscenter.order.coupon.controller;

import java.io.BufferedReader;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.coupon.dto.CouponAdminDto;
import vn.ticketscenter.order.coupon.dto.CreateCouponRequest;
import vn.ticketscenter.order.coupon.dto.UpdateCouponRequest;
import vn.ticketscenter.order.coupon.service.CouponAdminRuntime;
import vn.ticketscenter.order.coupon.service.CouponAdminService;
import vn.ticketscenter.order.service.OrderException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Coupon administration API (LIEM-06 / UI-14).
 * - GET /api/admin/coupons: every coupon (admin)
 * - GET /api/organizations/{orgId}/coupons: an organisation's coupons
 * - POST /api/organizations/{orgId}/coupons: create a coupon
 * - GET /api/coupons/{id}: one coupon
 * - PUT /api/coupons/{id}: update a coupon
 * - POST /api/coupons/{id}/activate: activate a coupon
 * - POST /api/coupons/{id}/deactivate: deactivate a coupon
 * - DELETE /api/coupons/{id}: delete a coupon
 */
@WebServlet(
        name = "CouponAdminServlet",
        urlPatterns = {"/api/admin/coupons", "/api/coupons/*"})
public class CouponAdminServlet extends HttpServlet {
    private static final Logger LOG = Logger.getLogger(CouponAdminServlet.class.getName());

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ApiRequest.prepareJson(response);
        String method = request.getMethod();
        String path = ApiRequest.path(request);

        try {
            UserPrincipal principal =
                    SecurityContext.getCurrentUser(request)
                            .orElseThrow(
                                    () ->
                                            new OrderException(
                                                    401,
                                                    "UNAUTHENTICATED",
                                                    "A current session is required."));

            if ("GET".equals(method)) {
                handleGet(request, response, principal, path);
            } else if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                handleMutation(request, response, principal, method, path);
            } else {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (OrderException e) {
            HttpResponses.error(response, e);
        } catch (IllegalArgumentException e) {
            HttpResponses.error(response, 400, "BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Coupon request failed", e);
            HttpResponses.error(response, 500, "INTERNAL_ERROR", "Đã xảy ra lỗi hệ thống.");
        }
    }

    private void handleGet(
            HttpServletRequest request,
            HttpServletResponse response,
            UserPrincipal principal,
            String path)
            throws IOException {
        int page = ApiRequest.positiveInt(request.getParameter("page"), 1);
        int size = ApiRequest.positiveInt(request.getParameter("size"), 20);

        if ("/api/admin/coupons".equals(path)) {
            String orgParam = request.getParameter("organizationId");
            UUID filterOrgId =
                    (orgParam != null && !orgParam.isBlank()) ? UUID.fromString(orgParam) : null;
            List<CouponAdminDto> coupons =
                    service().listAllCoupons(principal, page, size, filterOrgId);
            writeCouponsResponse(response, coupons);
        } else if (path.matches("^/api/(admin/)?organizations/[^/]+/coupons$")) {
            UUID orgId = parseUuid(path.split("/")[path.contains("admin") ? 4 : 3]);
            List<CouponAdminDto> coupons =
                    service().listOrganizationCoupons(principal, orgId, page, size);
            writeCouponsResponse(response, coupons);
        } else if (path.matches("^/api/coupons/[^/]+$")) {
            UUID couponId = parseUuid(path.split("/")[3]);
            CouponAdminDto coupon = service().getCoupon(principal, couponId);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(coupon.toJson());
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleMutation(
            HttpServletRequest request,
            HttpServletResponse response,
            UserPrincipal principal,
            String method,
            String path)
            throws IOException {
        // Checked here as well as in CsrfFilter: this servlet is also reached by a named forward.
        if (!SecurityContext.validateCsrfToken(request)) {
            throw new OrderException(
                    403, "CSRF_TOKEN_INVALID", "Yêu cầu thiếu hoặc mã CSRF không hợp lệ.");
        }
        String idemKey = request.getHeader("Idempotency-Key");
        if (idemKey == null || idemKey.isBlank()) {
            throw new OrderException(
                    400,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required for coupon mutations.");
        }

        if (path.matches("^/api/(admin/)?organizations/[^/]+/coupons$") && "POST".equals(method)) {
            UUID orgId = parseUuid(path.split("/")[path.contains("admin") ? 4 : 3]);
            String body = readBody(request);
            CreateCouponRequest req = CreateCouponRequest.fromJson(body);
            CouponAdminDto created = service().createCoupon(principal, orgId, req);
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(created.toJson());
        } else if (path.matches("^/api/coupons/[^/]+/activate$") && "POST".equals(method)) {
            UUID couponId = parseUuid(path.split("/")[3]);
            service().setStatus(principal, couponId, true);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"success\":true,\"active\":true}");
        } else if (path.matches("^/api/coupons/[^/]+/deactivate$") && "POST".equals(method)) {
            UUID couponId = parseUuid(path.split("/")[3]);
            service().setStatus(principal, couponId, false);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"success\":true,\"active\":false}");
        } else if ((path.matches("^/api/coupons/[^/]+/delete$") && "POST".equals(method))
                || (path.matches("^/api/coupons/[^/]+$") && "DELETE".equals(method))) {
            UUID couponId = parseUuid(path.split("/")[3]);
            service().deleteCoupon(principal, couponId);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"success\":true,\"deleted\":true}");
        } else if ((path.matches("^/api/coupons/[^/]+/edit$") && "POST".equals(method))
                || (path.matches("^/api/coupons/[^/]+$") && "PUT".equals(method))) {
            UUID couponId = parseUuid(path.split("/")[3]);
            String body = readBody(request);
            UpdateCouponRequest req = UpdateCouponRequest.fromJson(body);
            CouponAdminDto updated = service().updateCoupon(principal, couponId, req);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(updated.toJson());
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void writeCouponsResponse(HttpServletResponse response, List<CouponAdminDto> coupons)
            throws IOException {
        String json = coupons.stream().map(CouponAdminDto::toJson).collect(Collectors.joining(","));
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{\"coupons\":[" + json + "]}");
    }

    private CouponAdminService service() {
        return CouponAdminRuntime.resolve(getServletContext());
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        try (BufferedReader reader = request.getReader()) {
            return reader.lines().collect(Collectors.joining());
        }
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new OrderException(400, "BAD_REQUEST", "Invalid UUID format: " + s);
        }
    }

}
