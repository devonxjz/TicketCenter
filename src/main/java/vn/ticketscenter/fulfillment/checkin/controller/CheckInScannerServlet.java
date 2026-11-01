package vn.ticketscenter.fulfillment.checkin.controller;

import java.util.UUID;
import vn.ticketscenter.fulfillment.checkin.service.CheckInRuntime;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "CheckInScannerServlet", urlPatterns = "/check-ins/scanner")
public class CheckInScannerServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            UUID actor = CheckInWeb.actor(request);
            UUID event = CheckInWeb.id(request.getParameter("eventId"));
            CheckInRuntime.resolve(request.getServletContext()).window(actor, event);
            response.setHeader("Cache-Control", "no-store");
            request.getRequestDispatcher("/WEB-INF/views/checkin/scanner.jsp")
                    .forward(request, response);
        } catch (RuntimeException exception) {
            CheckInWeb.error(response, exception);
        }
    }
}
