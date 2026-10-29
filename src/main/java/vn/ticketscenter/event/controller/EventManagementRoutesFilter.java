package vn.ticketscenter.event.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import vn.ticketscenter.config.web.ApiRequest;

/** Dispatch only Event-owned actions; preserve the existing catalog and other domains. */
@WebFilter(
        urlPatterns = {
            "/api/events/*",
            "/api/zones/*",
            "/api/organizations/*",
            "/api/admin/events/*"
        })
public final class EventManagementRoutesFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = ApiRequest.path(httpRequest);
        String[] p = path.split("/", -1);
        boolean event =
                p.length == 5
                        && p[2].equals("events")
                        && ((httpRequest.getMethod().equals("POST")
                                        && Set.of("edit", "delete", "submit", "zones", "cover")
                                                .contains(p[4]))
                                || (httpRequest.getMethod().equals("GET")
                                        && Set.of("layout", "image").contains(p[4])));
        boolean zone =
                p.length == 5
                        && p[2].equals("zones")
                        && httpRequest.getMethod().equals("POST")
                        && Set.of("edit", "delete", "price").contains(p[4]);
        boolean organization =
                (p.length == 5 || p.length == 6)
                        && p[2].equals("organizations")
                        && p[4].equals("events")
                        && Set.of("GET", "POST").contains(httpRequest.getMethod());
        boolean admin =
                p.length >= 5
                        && p[2].equals("admin")
                        && p[3].equals("events")
                        && ((p.length == 5 && httpRequest.getMethod().equals("GET"))
                                || (p.length == 6
                                        && ((httpRequest.getMethod().equals("GET")
                                                        && Set.of("layout", "commission-rules")
                                                                .contains(p[5]))
                                                || (httpRequest.getMethod().equals("POST")
                                                        && Set.of("publish", "reject")
                                                                .contains(p[5])))));
        if (event || zone || organization || admin)
            EventManagementWeb.handle(httpRequest, (HttpServletResponse) response);
        else chain.doFilter(request, response);
    }
}
