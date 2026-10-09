package vn.ticketscenter.event.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

@WebServlet(name = "PublicPagesServlet", urlPatterns = {
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

    private static final Set<String> AUTH_REQUIRED_PAGES = Set.of(
            "/checkout",
            "/my-orders",
            "/tickets",
            "/my-tickets",
            "/refund-request",
            "/profile",
            "/organizer-request",
            "/organizer/request"
    );

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        if (AUTH_REQUIRED_PAGES.contains(path)) {
            Optional<UserPrincipal> user = SecurityContext.getCurrentUser(req);
            if (user.isEmpty()) {
                String targetUri = req.getRequestURI();
                if (req.getQueryString() != null && !req.getQueryString().isBlank()) {
                    targetUri += "?" + req.getQueryString();
                }
                resp.sendRedirect(req.getContextPath() + "/auth/login?redirect="
                        + URLEncoder.encode(targetUri, StandardCharsets.UTF_8));
                return;
            }
        }

        String view;
        if (path.equals("/checkout")) {
            view = "/WEB-INF/views/public/checkout.jsp";
        } else if (path.equals("/payment-result")) {
            view = "/WEB-INF/views/public/payment-result.jsp";
        } else if (path.equals("/my-orders")) {
            view = "/WEB-INF/views/public/my-orders.jsp";
        } else if (path.equals("/tickets") || path.equals("/my-tickets")) {
            view = "/WEB-INF/views/public/tickets.jsp";
        } else if (path.equals("/refund-request")) {
            view = "/WEB-INF/views/public/refund-request.jsp";
        } else if (path.equals("/profile")) {
            view = "/WEB-INF/views/public/profile.jsp";
        } else if (path.equals("/organizer-request") || path.equals("/organizer/request")) {
            view = "/WEB-INF/views/public/organizer-request.jsp";
        } else {
            view = "/WEB-INF/views/public/index.jsp";
        }

        req.getRequestDispatcher(view).forward(req, resp);
    }
}
