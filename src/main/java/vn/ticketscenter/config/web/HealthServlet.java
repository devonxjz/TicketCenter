package vn.ticketscenter.config.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Liveness check of the application.
 * Contract: GET /health/live -> HTTP 200 {"data":{"status":"UP"}}
 */
@WebServlet(
        name = "HealthServlet",
        urlPatterns = {"/health/live"})
public class HealthServlet extends HttpServlet {

    private static final String RESPONSE_JSON = "{\"data\":{\"status\":\"UP\"}}";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(RESPONSE_JSON);
    }
}
