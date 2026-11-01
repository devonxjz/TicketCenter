package vn.ticketscenter.fulfillment.checkin.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.web.HttpResponses;
import java.io.IOException;

/** Lets this feature coexist with future Event routes owned by another member. */
@WebFilter(
        filterName = "CheckInEventRoutesFilter",
        urlPatterns = {
            "/api/events/*",
            "/organizer/checkin",
            "/organizer/checkin-select",
            "/organizer/checkin/select",
            "/organizer/checkin-scanner"
        },
        dispatcherTypes = DispatcherType.REQUEST)
public final class CheckInEventRoutesFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = ApiRequest.path(httpRequest);
        if (path.equals("/organizer/checkin-scanner")) {
            if (!"GET".equals(httpRequest.getMethod())) {
                ((HttpServletResponse) response).setHeader("Allow", "GET");
                HttpResponses.error(
                        (HttpServletResponse) response,
                        405,
                        "METHOD_NOT_ALLOWED",
                        "Chỉ hỗ trợ phương thức GET.");
                return;
            }
            RequestDispatcher scanner =
                    request.getServletContext().getNamedDispatcher("CheckInScannerServlet");
            if (scanner == null) {
                HttpResponses.error(
                        (HttpServletResponse) response,
                        503,
                        "CHECK_IN_UNAVAILABLE",
                        "Hệ thống soát vé chưa sẵn sàng.");
                return;
            }
            scanner.forward(request, response);
            return;
        }
        if (path.equals("/organizer/checkin")
                || path.equals("/organizer/checkin-select")
                || path.equals("/organizer/checkin/select")) {
            if (!"GET".equals(httpRequest.getMethod())) {
                HttpResponses.error(
                        (HttpServletResponse) response,
                        405,
                        "METHOD_NOT_ALLOWED",
                        "Chỉ hỗ trợ phương thức GET.");
                return;
            }
            String org = httpRequest.getParameter("organizationId");
            String query =
                    org == null
                            ? ""
                            : "?organizationId="
                                    + URLEncoder.encode(
                                            org, StandardCharsets.UTF_8);
            ((HttpServletResponse) response)
                    .sendRedirect(httpRequest.getContextPath() + "/check-ins" + query);
            return;
        }
        String prefix = "/api/events/";
        boolean logs = path.endsWith("/check-ins");
        String suffix = logs ? "/check-ins" : "/check-in-window";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            chain.doFilter(request, response);
            return;
        }
        String eventId = path.substring(prefix.length(), path.length() - suffix.length());
        if (eventId.contains("/")) {
            chain.doFilter(request, response);
            return;
        }
        if (!"GET".equals(httpRequest.getMethod())) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setHeader("Allow", "GET");
            HttpResponses.error(httpResponse, 405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ phương thức GET.");
            return;
        }
        RequestDispatcher dispatcher =
                request.getServletContext()
                        .getNamedDispatcher(
                                logs ? "EventCheckInLogsServlet" : "CheckInWindowServlet");
        if (dispatcher == null) {
            HttpResponses.error(
                    (HttpServletResponse) response,
                    503,
                    "CHECK_IN_UNAVAILABLE",
                    "Hệ thống soát vé chưa sẵn sàng.");
            return;
        }
        request.setAttribute(
                logs
                        ? EventCheckInLogsServlet.EVENT_ATTRIBUTE
                        : CheckInWindowServlet.EVENT_ID_ATTRIBUTE,
                eventId);
        dispatcher.forward(request, response);
    }
}
