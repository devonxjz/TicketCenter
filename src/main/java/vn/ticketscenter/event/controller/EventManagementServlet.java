package vn.ticketscenter.event.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet(
        name = "EventManagementServlet",
        urlPatterns = {"/api/admin/events", "/api/event-organizations"})
public class EventManagementServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        EventManagementWeb.handle(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        EventManagementWeb.handle(request, response);
    }
}
