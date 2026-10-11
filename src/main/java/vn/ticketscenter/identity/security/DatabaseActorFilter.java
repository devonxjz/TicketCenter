package vn.ticketscenter.identity.security;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import vn.ticketscenter.config.persistence.DatabaseActorContext;
import java.io.IOException;

@WebFilter(urlPatterns="/*")
public final class DatabaseActorFilter implements Filter {
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        DatabaseActorContext.set(SecurityContext.getCurrentUser((HttpServletRequest) request)
                .map(UserPrincipal::actorId).orElse(null));
        try { chain.doFilter(request,response); } finally { DatabaseActorContext.clear(); }
    }
}
