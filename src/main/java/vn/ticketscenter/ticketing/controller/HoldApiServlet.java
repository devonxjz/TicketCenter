package vn.ticketscenter.ticketing.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.ticketing.dto.*;
import vn.ticketscenter.ticketing.service.*;

import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {"/api/holds", "/api/holds/*", "/api/me/hold", "/api/hold-options"})
public final class HoldApiServlet extends HttpServlet {
    private final HoldService supplied;

    public HoldApiServlet() { this.supplied = null; }
    public HoldApiServlet(HoldService service) { this.supplied = Objects.requireNonNull(service); }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try {
            var principal = SecurityContext.getCurrentUser(request).orElseThrow(() ->
                    new HoldException(401, "UNAUTHENTICATED", "A current session is required."));
            HoldActor actor = new HoldActor(principal.actorId(), principal.authVersion());
            String path = request.getRequestURI().substring(request.getContextPath().length());
            String method = request.getMethod();
            if (method.equals("POST")) {
                if (request.getContentLengthLong() > HoldRequestParser.MAX_BODY_BYTES)
                    throw new HoldException(413, "BODY_TOO_LARGE", "Hold request body is too large.");
                String media = request.getContentType();
                if (media == null || !media.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
                    throw new HoldException(415, "UNSUPPORTED_MEDIA_TYPE", "Use application/json.");
                if (!SecurityContext.validateCsrfToken(request))
                    throw new HoldException(403, "CSRF_INVALID", "A valid CSRF token is required.");
                UUID key = uniqueHeaderKey(request);
                HoldCommand command;
                if (path.equals("/api/holds")) command = new HoldCommand.Create(key, HoldRequestParser.parseCreate(request.getInputStream()));
                else if (path.matches("/api/holds/[^/]+/cancel")) {
                    HoldRequestParser.parseCancel(request.getInputStream());
                    command = new HoldCommand.Cancel(key, uuid(path.substring(11, path.length() - 7)));
                } else throw new HoldException(404, "NOT_FOUND", "Hold endpoint was not found.");
                var receipt = service().mutate(actor, command);
                response.setStatus(receipt.httpStatus());
                response.getWriter().write(receipt.body());
            } else if (method.equals("GET") && path.equals("/api/me/hold")) {
                response.getWriter().write(service().current(actor).body());
            } else if (method.equals("GET") && path.equals("/api/hold-options")) {
                if (!request.getParameterMap().keySet().equals(Set.of("eventId"))
                        || request.getParameterValues("eventId").length != 1) throw invalid();
                response.getWriter().write(service().options(actor, uuid(request.getParameter("eventId"))));
            } else throw new HoldException(404, "NOT_FOUND", "Hold endpoint was not found.");
        } catch (HoldRequestParser.ParseException failure) {
            error(response, failure.failure() == HoldRequestParser.Failure.BODY_TOO_LARGE
                    ? new HoldException(413, "BODY_TOO_LARGE", "Hold request body is too large.") : invalid());
        } catch (HoldException failure) {
            error(response, failure);
        } catch (IllegalArgumentException failure) {
            error(response, invalid());
        }
    }

    private HoldService service() { return supplied == null ? HoldRuntime.resolve(getServletContext()) : supplied; }

    private static UUID uniqueHeaderKey(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("Idempotency-Key");
        if (headers == null || !headers.hasMoreElements()) throw invalid();
        String key = headers.nextElement();
        if (headers.hasMoreElements()) throw invalid();
        return uuid(key);
    }

    private static UUID uuid(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw invalid();
        UUID id = UUID.fromString(value);
        if (id.equals(new UUID(0, 0))) throw invalid();
        return id;
    }

    private static HoldException invalid() { return new HoldException(400, "INVALID_REQUEST", "Invalid Hold request."); }

    private static void error(HttpServletResponse response, HoldException failure) throws IOException {
        response.setStatus(failure.status());
        response.getWriter().write(new JSONObject().put("error", new JSONObject()
                .put("code", failure.code()).put("message", failure.getMessage())).toString());
    }
}
