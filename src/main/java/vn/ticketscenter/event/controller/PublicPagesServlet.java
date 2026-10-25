package vn.ticketscenter.event.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

@WebServlet(
        name = "PublicPagesServlet",
        urlPatterns = {
            "/checkout",
            "/payment-result",
            "/my-orders",
            "/tickets",
            "/my-tickets",
            "/refund-request",
            "/profile",
            "/organizer-request",
            "/organizer/request"
        })
public class PublicPagesServlet extends HttpServlet {

    private static final Set<String> AUTH_REQUIRED_PAGES =
            Set.of(
                    "/checkout",
                    "/my-orders",
                    "/tickets",
                    "/my-tickets",
                    "/refund-request",
                    "/profile",
                    "/organizer-request",
                    "/organizer/request");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = ApiRequest.path(req);

        if (AUTH_REQUIRED_PAGES.contains(path)) {
            Optional<UserPrincipal> user = SecurityContext.getCurrentUser(req);
            if (user.isEmpty()) {
                String targetUri = req.getRequestURI();
                if (req.getQueryString() != null && !req.getQueryString().isBlank()) {
                    targetUri += "?" + req.getQueryString();
                }
                resp.sendRedirect(
                        req.getContextPath()
                                + "/auth/login?redirect="
                                + URLEncoder.encode(targetUri, StandardCharsets.UTF_8));
                return;
            }
        }

        // Buyer pages with their own data-backed servlets are reached by redirect, never by a
        // static view.
        String redirect =
                switch (path) {
                    case "/my-orders", "/payment-result" -> "/me/orders";
                    case "/tickets", "/my-tickets" -> "/me/tickets";
                    case "/refund-request" -> "/my-refunds";
                    default -> null;
                };
        if (redirect != null) {
            resp.sendRedirect(req.getContextPath() + redirect);
            return;
        }
        String view =
                switch (path) {
                    case "/checkout" -> "/WEB-INF/views/public/checkout.jsp";
                    case "/profile" -> "/WEB-INF/views/account/profile.jsp";
                    case "/organizer-request", "/organizer/request" ->
                            "/WEB-INF/views/public/organizer-request.jsp";
                    default -> "/WEB-INF/views/public/index.jsp";
                };
        req.getRequestDispatcher(view).forward(req, resp);
    }
}
