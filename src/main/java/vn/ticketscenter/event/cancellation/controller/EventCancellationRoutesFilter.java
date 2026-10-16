package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.DatabaseActorFilter;

@WebFilter(urlPatterns = {"/api/admin/events/*", "/api/events/*"})
public final class EventCancellationRoutesFilter implements Filter {
    private static final Set<String> REVIEW_ENDPOINTS =
            Set.of("cancellation-request", "cancellation-approve", "cancellation-reject");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var req = (HttpServletRequest) request;
        var res = (HttpServletResponse) response;
        String path = req.getRequestURI().substring(req.getContextPath().length());
        String[] pieces = path.split("/", -1);
        boolean review =
                (pieces.length == 5
                                && pieces[2].equals("events")
                                && pieces[4].equals("cancellation-request"))
                        || (pieces.length == 6
                                && pieces[2].equals("admin")
                                && pieces[3].equals("events")
                                && REVIEW_ENDPOINTS.contains(pieces[5]));
        if (review) {
            boolean requestEndpoint = pieces[pieces.length - 1].equals("cancellation-request");
            boolean read = requestEndpoint && req.getMethod().equals("GET");
            if (!read && !req.getMethod().equals("POST")) {
                methodNotAllowed(res, requestEndpoint ? "GET, POST" : "POST");
                return;
            }
            DatabaseActorFilter.withActor(
                    request,
                    response,
                    (r, s) ->
                            req.getServletContext()
                                    .getNamedDispatcher("CancellationReviewServlet")
                                    .forward(r, s));
            return;
        }
        if (pieces.length == 6
                && (pieces[5].equals("cancel") || pieces[5].equals("cancellation-progress"))) {
            boolean cancel = pieces[5].equals("cancel");
            String method = cancel ? "POST" : "GET";
            if (!req.getMethod().equals(method)) {
                methodNotAllowed(res, method);
                return;
            }
            DatabaseActorFilter.withActor(
                    request,
                    response,
                    (r, s) ->
                            req.getServletContext()
                                    .getNamedDispatcher(
                                            cancel
                                                    ? "AdminEventCancelServlet"
                                                    : "AdminCancellationProgressServlet")
                                    .forward(r, s));
        } else {
            chain.doFilter(request, response);
        }
    }

    private static void methodNotAllowed(HttpServletResponse response, String allowed)
            throws IOException {
        response.setHeader("Allow", allowed);
        HttpResponses.error(response, 405, "METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ.");
    }
}
