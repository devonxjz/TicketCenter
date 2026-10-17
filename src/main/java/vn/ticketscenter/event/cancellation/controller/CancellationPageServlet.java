package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import vn.ticketscenter.identity.security.SecurityContext;

@WebServlet(name = "CancellationPageServlet", urlPatterns = "/events/cancellation")
public final class CancellationPageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        var actor = SecurityContext.getCurrentUser(request);
        if (actor.isEmpty()) {
            String target =
                    request.getRequestURI()
                            + (request.getQueryString() == null
                                    ? ""
                                    : "?" + request.getQueryString());
            response.sendRedirect(
                    request.getContextPath()
                            + "/auth/login?redirect="
                            + URLEncoder.encode(target, StandardCharsets.UTF_8));
            return;
        }
        if (!actor.get().isManager()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        request.setAttribute("cancellationAdmin", actor.get().isAdmin());
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/views/event/cancellation.jsp")
                .forward(request, response);
    }
}
