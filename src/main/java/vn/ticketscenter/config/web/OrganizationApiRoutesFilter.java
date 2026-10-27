package vn.ticketscenter.config.web;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.security.*;

/** Shared dispatch only: report and event logic remain with their original owners. */
@WebFilter(
        filterName = "OrganizationApiRoutesFilter",
        urlPatterns = "/api/organizations/*",
        dispatcherTypes = DispatcherType.REQUEST)
public final class OrganizationApiRoutesFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var http = (HttpServletRequest) request;
        String path = ApiRequest.path(http);
        if (!path.matches("/api/organizations/[^/]+/reports")) {
            chain.doFilter(request, response);
            return;
        }
        var out = (HttpServletResponse) response;
        if (!"GET".equals(http.getMethod())) {
            out.setHeader("Allow", "GET");
            HttpResponses.error(
                    out, 405, "METHOD_NOT_ALLOWED", "Đường dẫn báo cáo chỉ hỗ trợ GET.");
            return;
        }
        // Named forwarding can run before annotation-ordered REQUEST filters.
        var principal = SecurityContext.getCurrentUser(http).orElse(null);
        if (principal == null) {
            HttpResponses.error(out, 401, "UNAUTHENTICATED", "Bạn cần đăng nhập.");
            return;
        }
        var auth = IdentityServices.sessionValidator(http.getServletContext());
        if (auth == null) {
            HttpResponses.error(out, 503, "AUTH_UNAVAILABLE", "Hệ thống xác thực chưa sẵn sàng.");
            return;
        }
        if (!auth.isSessionValid(principal.actorId(), principal.authVersion())) {
            SecurityContext.clearSession(http);
            HttpResponses.error(out, 401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.");
            return;
        }
        var dispatcher = request.getServletContext().getNamedDispatcher("FinanceReportServlet");
        if (dispatcher == null) {
            HttpResponses.error(out, 503, "REPORT_UNAVAILABLE", "Hệ thống báo cáo chưa sẵn sàng.");
            return;
        }
        // Preserve the wildcard route contract even if another servlet receives this request later.
        request.setAttribute(
                vn.ticketscenter.report.controller.OrganizationReadsideRoutesFilter.ROUTE_ATTRIBUTE,
                path.substring("/api/organizations".length()));
        var routed =
                new HttpServletRequestWrapper(http) {
                    @Override
                    public String getServletPath() {
                        return "/api/organizations";
                    }

                    @Override
                    public String getPathInfo() {
                        return path.substring("/api/organizations".length());
                    }
                };
        DatabaseActorFilter.withActor(routed, response, dispatcher::forward);
    }
}
