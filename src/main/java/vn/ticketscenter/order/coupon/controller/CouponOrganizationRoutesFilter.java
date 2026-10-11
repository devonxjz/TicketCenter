package vn.ticketscenter.order.coupon.controller;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebFilter(urlPatterns = {"/api/organizations/*", "/api/admin/organizations/*"},
        dispatcherTypes = DispatcherType.REQUEST)
public final class CouponOrganizationRoutesFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String path = http.getRequestURI().substring(http.getContextPath().length());
        if (!path.matches("^/api/(admin/)?organizations/[^/]+/coupons$")) {
            chain.doFilter(request, response);
            return;
        }
        var dispatcher = request.getServletContext().getNamedDispatcher("CouponAdminServlet");
        if (dispatcher == null) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        dispatcher.forward(request, response);
    }
}
