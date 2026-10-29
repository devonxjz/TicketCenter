package vn.ticketscenter.event.controller;

import vn.ticketscenter.event.service.EventCatalogRuntime;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;

import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.EventException;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.service.*;
import vn.ticketscenter.identity.security.*;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Collectors;

@WebServlet(
        name = "EventApiServlet",
        loadOnStartup = 1,
        urlPatterns = {
            "/api/organizations/*",
            "/api/manager/events",
            "/api/manager/events/*",
            "/api/manager/event-options"
        })
@MultipartConfig(maxFileSize = 5_242_880, maxRequestSize = 5_400_000, fileSizeThreshold = 0)
public class EventApiServlet extends HttpServlet {

    private EventService service(HttpServletRequest request) {
        return EventRuntime.resolve(getServletContext());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            String path = path(request);
            EventService service = service(request);
            String result;
            if (path.equals("/api/events"))
                result =
                        service.publicEvents().stream()
                                .map(EventDto::toJson)
                                .collect(Collectors.joining(",", "[", "]"));
            else if (path.matches("/api/events/[^/]+"))
                result = service.publicEvent(EventMultipartParser.id(last(path))).toJson();
            else if (path.equals("/api/manager/events"))
                result =
                        service
                                .managedEvents(
                                        actor(request),
                                        EventMultipartParser.id(
                                                request.getParameter("organizationId")))
                                .stream()
                                .map(EventDto::toJson)
                                .collect(Collectors.joining(",", "[", "]"));
            else if (path.matches("/api/manager/events/[^/]+"))
                result =
                        service.managedEvent(actor(request), EventMultipartParser.id(last(path)))
                                .toJson();
            else if (path.equals("/api/manager/event-options"))
                result = service.options(actor(request));
            else throw new EventException(404, "ROUTE_NOT_FOUND", "Event route was not found");
            HttpResponses.data(response, result);
        } catch (RuntimeException e) {
            error(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            UserPrincipal principal = actor(request);
            if (!SecurityContext.validateCsrfToken(request))
                throw new EventException(
                        403, "CSRF_TOKEN_INVALID", "A current CSRF token is required");
            String path = path(request);
            EventSave.Kind kind;
            UUID target;
            if (path.matches("/api/organizations/[^/]+/events")) {
                kind = EventSave.Kind.CREATE;
                target = EventMultipartParser.id(path.split("/")[3]);
            } else if (path.matches("/api/events/[^/]+/edit")) {
                kind = EventSave.Kind.EDIT;
                target = EventMultipartParser.id(path.split("/")[3]);
            } else if (path.matches("/api/events/[^/]+/cover")) {
                kind = EventSave.Kind.COVER;
                target = EventMultipartParser.id(path.split("/")[3]);
            } else throw new EventException(404, "ROUTE_NOT_FOUND", "Event route was not found");
            EventSave command;
            try {
                command = EventMultipartParser.parse(request, kind, target);
            } catch (IllegalStateException e) {
                throw new EventException(
                        413, "PAYLOAD_TOO_LARGE", "Multipart request exceeds its limit");
            } catch (ServletException | IOException e) {
                throw new EventException(
                        400, "MULTIPART_INVALID", "Multipart request cannot be read");
            }
            if (command.fields() != null) {
                EventFields f = command.fields();
                EventCommand.input(
                        kind == EventSave.Kind.CREATE ? "CREATE" : "EDIT",
                        kind == EventSave.Kind.CREATE ? target : null,
                        kind == EventSave.Kind.EDIT ? target : null,
                        null,
                        java.util.Map.of(
                                "title",
                                f.title(),
                                "description",
                                f.description(),
                                "categoryId",
                                f.categoryId().toString(),
                                "venueName",
                                f.venueName(),
                                "venueAddress",
                                f.venueAddress(),
                                "saleStartTime",
                                f.saleStart().toString(),
                                "saleEndTime",
                                f.saleEnd().toString(),
                                "startTime",
                                f.startTime().toString(),
                                "endTime",
                                f.endTime().toString()),
                        java.time.Clock.systemUTC());
            }
            EventDto saved = service(request).save(principal, command);
            try {
                EventCatalogRuntime.cache(request.getServletContext())
                        .invalidateCommitted(saved.id(), saved.organizationId());
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(EventApiServlet.class.getName())
                        .log(
                                java.util.logging.Level.WARNING,
                                "Event catalog cache invalidation skipped",
                                e);
            }
            HttpResponses.data(response, saved.toJson());
        } catch (RuntimeException e) {
            error(response, e);
        }
    }

    private static String path(HttpServletRequest request) {
        return ApiRequest.path(request);
    }

    private static String last(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static UserPrincipal actor(HttpServletRequest request) {
        return SecurityContext.getCurrentUser(request)
                .orElseThrow(() -> new EventException(401, "AUTH_REQUIRED", "Sign in first"));
    }

    private static void error(HttpServletResponse response, RuntimeException exception)
            throws IOException {
        if (exception instanceof EventException e)
            HttpResponses.error(response, e);
        else if (exception instanceof EventProblem e)
            HttpResponses.error(response, e);
        else if (exception instanceof IllegalArgumentException)
            HttpResponses.error(response, 400, "EVENT_INPUT_INVALID", "Event input is invalid");
        else {
            java.util.logging.Logger.getLogger(EventApiServlet.class.getName())
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Unexpected event service error",
                            exception);
            HttpResponses.error(
                    response, 503, "EVENT_UNAVAILABLE", "Event service is temporarily unavailable");
        }
    }
}
