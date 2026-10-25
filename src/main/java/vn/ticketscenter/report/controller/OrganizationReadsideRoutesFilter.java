package vn.ticketscenter.report.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.settlement.infrastructure.FinanceException;

/** Dispatches only the readside operations; the Event servlet retains its organization routes. */
@WebFilter(
        filterName = "OrganizationReadsideRoutesFilter",
        urlPatterns = "/api/organizations/*",
        dispatcherTypes = DispatcherType.REQUEST)
public final class OrganizationReadsideRoutesFilter implements Filter {
    public static final String ROUTE_ATTRIBUTE =
            OrganizationReadsideRoutesFilter.class.getName() + ".route";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var http = (HttpServletRequest) request;
        String path = ApiRequest.path(http);
        String prefix = "/api/organizations";
        String route = path.startsWith(prefix + "/") ? path.substring(prefix.length()) : "";
        String[] parts = route.split("/", -1);
        if (parts.length < 3 || !(parts[2].equals("reports") || parts[2].equals("overview"))) {
            chain.doFilter(request, response);
            return;
        }
        var output = (HttpServletResponse) response;
        if (!http.getMethod().equals("GET")) {
            ReadsideHttp.error(output, new FinanceException("METHOD_NOT_ALLOWED", 405));
            output.setHeader("Allow", "GET");
            return;
        }
        var dispatcher = request.getServletContext().getNamedDispatcher("FinanceReportServlet");
        if (dispatcher == null) {
            ReadsideHttp.error(output, new FinanceException("REPORT_UNAVAILABLE", 503));
            return;
        }
        request.setAttribute(ROUTE_ATTRIBUTE, route);
        dispatcher.forward(request, response);
    }
}
