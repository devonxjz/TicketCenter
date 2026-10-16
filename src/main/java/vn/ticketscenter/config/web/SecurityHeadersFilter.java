package vn.ticketscenter.config.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Baseline response headers for every page and API. Mapped first in web.xml. The policy only restricts
 * framing, base URI and plugins: pages still use inline scripts and CDN assets, so script sources stay open.
 */
public final class SecurityHeadersFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse http = (HttpServletResponse) response;
        http.setHeader("X-Content-Type-Options", "nosniff");
        http.setHeader("X-Frame-Options", "DENY");
        http.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        http.setHeader(
                "Content-Security-Policy",
                "frame-ancestors 'none'; base-uri 'self'; object-src 'none'");
        if (request.isSecure()) {
            http.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        chain.doFilter(request, response);
    }
}
