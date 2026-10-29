package vn.ticketscenter.ticketing.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.ticketing.dto.*;
import vn.ticketscenter.ticketing.service.*;

import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {"/api/holds", "/api/holds/*", "/api/me/hold", "/api/hold-options"})
public final class HoldApiServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ApiRequest.prepareJson(response);
        try {
            var principal =
                    SecurityContext.getCurrentUser(request)
                            .orElseThrow(
                                    () -> new HoldException(401, "UNAUTHENTICATED", "A current session is required."));
            var actor = new HoldActor(principal.actorId(), principal.authVersion());
            String path = ApiRequest.path(request);
            String method = request.getMethod();
            if (method.equals("POST")) post(request, response, actor, path);
            else if (method.equals("GET") && path.equals("/api/me/hold"))
                response.getWriter().write(service().current(actor).body());
            else if (method.equals("GET") && path.equals("/api/hold-options")) {
                String eventId = eventIdParameter(request);
                response.getWriter().write(service().options(actor, uuid(eventId)));
            }
            else throw missing();
        } catch (HoldRequestParser.ParseException failure) {
            error(response, failure.failure() == HoldRequestParser.Failure.BODY_TOO_LARGE ? tooLarge() : invalid());
        } catch (HoldException failure) {
            error(response, failure);
        } catch (IllegalArgumentException failure) {
            error(response, invalid());
        }
    }

    private void post(HttpServletRequest request, HttpServletResponse response, HoldActor actor, String path)
            throws IOException {
        UUID key = mutationKey(request);
        HoldCommand command;
        if (path.equals("/api/holds")) {
            command = new HoldCommand.Create(key, HoldRequestParser.parseCreate(request.getInputStream()));
        } else if (path.matches("/api/holds/[^/]+/cancel")) {
            HoldRequestParser.parseCancel(request.getInputStream());
            command = new HoldCommand.Cancel(key, uuid(path.substring(11, path.length() - 7)));
        } else throw missing();
        var receipt = service().mutate(actor, command);
        response.setStatus(receipt.httpStatus());
        response.getWriter().write(receipt.body());
    }

    /** Body size, JSON media type, CSRF token and exactly one Idempotency-Key, in that order. */
    private static UUID mutationKey(HttpServletRequest request) {
        if (request.getContentLengthLong() > HoldRequestParser.MAX_BODY_BYTES) throw tooLarge();
        if (!ApiRequest.isJson(request))
            throw new HoldException(415, "UNSUPPORTED_MEDIA_TYPE", "Use application/json.");
        if (!SecurityContext.validateCsrfToken(request))
            throw new HoldException(403, "CSRF_INVALID", "A valid CSRF token is required.");
        return uuid(ApiRequest.singleHeader(request, "Idempotency-Key").orElseThrow(HoldApiServlet::invalid));
    }

    private static String eventIdParameter(HttpServletRequest request) {
        return ApiRequest.onlyParameter(request, "eventId").orElseThrow(HoldApiServlet::invalid);
    }

    private HoldService service() {
        return HoldRuntime.resolve(getServletContext());
    }

    /** Canonical, non-nil UUID text. */
    private static UUID uuid(String value) {
        return ApiRequest.uuid(value).filter(id -> !id.equals(new UUID(0, 0))).orElseThrow(HoldApiServlet::invalid);
    }

    private static HoldException invalid() {
        return new HoldException(400, "INVALID_REQUEST", "Invalid Hold request.");
    }

    private static HoldException tooLarge() {
        return new HoldException(413, "BODY_TOO_LARGE", "Hold request body is too large.");
    }

    private static HoldException missing() {
        return new HoldException(404, "NOT_FOUND", "Hold endpoint was not found.");
    }

    /** Hold errors carry only code and message (no correlation id), unlike HttpResponses.error. */
    private static void error(HttpServletResponse response, HoldException failure) throws IOException {
        response.setStatus(failure.status());
        var body = new JSONObject().put("code", failure.code()).put("message", failure.getMessage());
        response.getWriter().write(new JSONObject().put("error", body).toString());
    }
}
