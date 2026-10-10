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

@WebServlet(name = "EventDetailServlet", urlPatterns = {"/event-detail", "/events/detail"})
public class EventDetailServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Optional<UserPrincipal> user = SecurityContext.getCurrentUser(req);
        if (user.isEmpty()) {
            String targetUri = req.getRequestURI();
            if (req.getQueryString() != null && !req.getQueryString().isBlank()) {
                targetUri += "?" + req.getQueryString();
            }
            resp.sendRedirect(req.getContextPath() + "/auth/login?redirect=" + URLEncoder.encode(targetUri, StandardCharsets.UTF_8));
            return;
        }

        req.getRequestDispatcher("/WEB-INF/views/public/event-detail.jsp").forward(req, resp);
    }
}
