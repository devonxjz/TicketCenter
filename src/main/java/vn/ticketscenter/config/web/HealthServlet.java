package vn.ticketscenter.config.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Health check endpoint phục vụ kiểm tra liveness của ứng dụng.
 * Hợp đồng: GET /health/live -> HTTP 200 {"data":{"status":"UP"}}
 */
@WebServlet(name = "HealthServlet", urlPatterns = {"/health/live"})
public class HealthServlet extends HttpServlet {

    private static final String RESPONSE_JSON = "{\"data\":{\"status\":\"UP\"}}";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(RESPONSE_JSON);
    }
}
