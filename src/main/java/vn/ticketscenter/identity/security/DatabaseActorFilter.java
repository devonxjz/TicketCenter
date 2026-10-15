package vn.ticketscenter.identity.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import vn.ticketscenter.config.persistence.DatabaseActorContext;

import java.io.IOException;

@WebFilter(urlPatterns="/*")
public final class DatabaseActorFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        withActor(request, response, chain);
    }

    /** Named forwards may bypass REQUEST filters; establish and restore their actor explicitly. */
    public static void withActor(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var previous = DatabaseActorContext.current();
        DatabaseActorContext.set(SecurityContext.getCurrentUser((HttpServletRequest) request)
                .map(UserPrincipal::actorId).orElse(null));
        try {
            chain.doFilter(request, response);
        } finally {
            DatabaseActorContext.set(previous);
        }
    }
}
