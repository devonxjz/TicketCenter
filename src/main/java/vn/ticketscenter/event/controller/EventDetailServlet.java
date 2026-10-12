package vn.ticketscenter.event.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "EventDetailServlet", urlPatterns = {"/event-detail", "/events/detail"})
public class EventDetailServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-store");
        req.getRequestDispatcher("/WEB-INF/views/public/event-detail.jsp").forward(req, resp);
    }
}
