package vn.ticketscenter.config.web;

import vn.ticketscenter.report.controller.OrganizationReadsideRoutesFilter;
import vn.ticketscenter.identity.service.SessionValidator;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.identity.IdentityServices;
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
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = ApiRequest.path(httpRequest);
        if (!path.matches("/api/organizations/[^/]+/reports")) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (!"GET".equals(httpRequest.getMethod())) {
            httpResponse.setHeader("Allow", "GET");
            HttpResponses.error(
                    httpResponse, 405, "METHOD_NOT_ALLOWED", "Đường dẫn báo cáo chỉ hỗ trợ GET.");
            return;
        }
        // Named forwarding can run before annotation-ordered REQUEST filters.
        UserPrincipal principal = SecurityContext.getCurrentUser(httpRequest).orElse(null);
        if (principal == null) {
            HttpResponses.error(httpResponse, 401, "UNAUTHENTICATED", "Bạn cần đăng nhập.");
            return;
        }
        SessionValidator auth = IdentityServices.sessionValidator(httpRequest.getServletContext());
        if (auth == null) {
            HttpResponses.error(httpResponse, 503, "AUTH_UNAVAILABLE", "Hệ thống xác thực chưa sẵn sàng.");
            return;
        }
        if (!auth.isSessionValid(principal.actorId(), principal.authVersion())) {
            SecurityContext.clearSession(httpRequest);
            HttpResponses.error(httpResponse, 401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.");
            return;
        }
        RequestDispatcher dispatcher = request.getServletContext().getNamedDispatcher("FinanceReportServlet");
        if (dispatcher == null) {
            HttpResponses.error(httpResponse, 503, "REPORT_UNAVAILABLE", "Hệ thống báo cáo chưa sẵn sàng.");
            return;
        }
        // Preserve the wildcard route contract even if another servlet receives this request later.
        request.setAttribute(
                OrganizationReadsideRoutesFilter.ROUTE_ATTRIBUTE,
                path.substring("/api/organizations".length()));
        var routed =
                new HttpServletRequestWrapper(httpRequest) {
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
