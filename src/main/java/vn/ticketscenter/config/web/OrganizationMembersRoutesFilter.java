package vn.ticketscenter.config.web;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

@WebFilter(
        filterName = "OrganizationMembersRoutesFilter",
        urlPatterns = "/api/organizations/*",
        dispatcherTypes = DispatcherType.REQUEST)
public final class OrganizationMembersRoutesFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String path = ApiRequest.path(http);

        if (path.matches("^/api/organizations/[^/]+/members(?:/.*)?$")) {
            var dispatcher =
                    request.getServletContext().getNamedDispatcher("OrganizationMembersServlet");
            if (dispatcher != null) {
                dispatcher.forward(request, response);
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
