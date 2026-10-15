package vn.ticketscenter.config.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;

import java.io.IOException;
import java.util.function.BooleanSupplier;

@WebServlet(name = "ReadinessServlet", urlPatterns = "/health/ready")
public final class ReadinessServlet extends HttpServlet {

    private final BooleanSupplier readinessOverride;

    public ReadinessServlet() {
        this.readinessOverride = null;
    }

    public ReadinessServlet(BooleanSupplier readinessOverride) {
        this.readinessOverride = readinessOverride;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean ready = readinessOverride != null ? readinessOverride.getAsBoolean() : isReady(request);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(ready ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.getWriter().write(ready
                ? "{\"data\":{\"status\":\"UP\"}}"
                : "{\"error\":{\"code\":\"DEPENDENCY_UNAVAILABLE\"}}");
    }

    private boolean isReady(HttpServletRequest request) {
        Object value = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        return value instanceof PersistenceRegistry registry && registry.isReady();
    }
}
