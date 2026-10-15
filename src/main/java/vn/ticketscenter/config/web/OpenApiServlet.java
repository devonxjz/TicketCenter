package vn.ticketscenter.config.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

@WebServlet(name = "OpenApiServlet", urlPatterns = {
        "/swagger", "/swagger/", "/api/openapi.json", "/swagger/openapi.json"
})
public final class OpenApiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();

        if (path.endsWith("/swagger") || path.endsWith("/swagger/")) {
            response.sendRedirect(request.getContextPath() + "/swagger/index.html");
            return;
        }

        try (InputStream in = getClass().getResourceAsStream("/openapi.json")) {
            if (in == null) {
                HttpResponses.error(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "OpenAPI spec not found");
                return;
            }
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setStatus(HttpServletResponse.SC_OK);
            in.transferTo(response.getOutputStream());
        }
    }
}
