package vn.ticketscenter.settlement.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Existing menu URLs enter the same live pages; canonical pages retain their SQL authorization. */
@WebFilter(filterName="FinancePrototypeNavigationFilter", urlPatterns={
        "/admin", "/admin/dashboard", "/admin/settlements", "/admin/audit-logs", "/organizer/reports"
})
public final class FinancePrototypeNavigationFilter implements Filter {
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var http=(HttpServletRequest) request;
        if (!"GET".equals(http.getMethod()) && !"HEAD".equals(http.getMethod())) {
            chain.doFilter(request,response);
            return;
        }
        String target=switch (http.getServletPath()) {
            case "/admin", "/admin/dashboard" -> "/finance/overview";
            case "/admin/settlements" -> "/finance/settlements";
            case "/admin/audit-logs" -> "/finance/audit";
            case "/organizer/reports" -> "/finance/reports";
            default -> null;
        };
        if (target==null) {
            chain.doFilter(request,response);
            return;
        }
        var reply=(HttpServletResponse) response;
        reply.setHeader("Cache-Control","no-store");
        reply.sendRedirect(http.getContextPath()+target);
    }
}
