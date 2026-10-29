package vn.ticketscenter.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Sends an anonymous visitor to the login page so they come back to the page they asked for. */
public final class LoginRedirect {

    private LoginRedirect() {}

    public static void send(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String target = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            target += "?" + query;
        }
        response.sendRedirect(
                request.getContextPath()
                        + "/auth/login?redirect="
                        + URLEncoder.encode(target, StandardCharsets.UTF_8));
    }
}
