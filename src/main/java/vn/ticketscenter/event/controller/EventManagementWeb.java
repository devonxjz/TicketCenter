package vn.ticketscenter.event.controller;

import jakarta.servlet.http.*;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.dao.EventManagementRepository.Actor;
import vn.ticketscenter.event.service.*;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.storage.ImageContentValidator;
import java.io.*;
import java.net.URI;
import java.time.Clock;
import java.util.*;

public final class EventManagementWeb {
    private EventManagementWeb() {}

    public static void handle(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        request.setCharacterEncoding("UTF-8");
        try {
            String path = ApiRequest.path(request);
            String[] p = path.split("/", -1);
            boolean post = request.getMethod().equals("POST");
            if (!post && p.length == 5 && p[2].equals("events") && p[4].equals("image")) {
                String url =
                        EventCatalogRuntime.resolve(request.getServletContext())
                                .image(EventCommand.uuid(p[3]));
                URI target = URI.create(url);
                if (!vn.ticketscenter.storage.StorageServiceImpl.isLocalUrl(url)
                        && (!Set.of("https", "http").contains(target.getScheme())
                                || target.getHost() == null
                                || target.getUserInfo() != null))
                    throw new EventProblem(404, "EVENT_IMAGE_NOT_FOUND", "Chưa có ảnh sự kiện.");
                response.setHeader("Cache-Control", "no-store");
                response.sendRedirect(url);
                return;
            }
            var user =
                    SecurityContext.getCurrentUser(request)
                            .orElseThrow(
                                    () ->
                                            new EventProblem(
                                                    401,
                                                    "AUTH_REQUIRED",
                                                    "Cần đăng nhập để quản lý sự kiện."));
            var actor = new Actor(user.actorId(), user.authVersion());
            if (post && !SecurityContext.validateCsrfToken(request))
                throw new EventProblem(403, "CSRF_TOKEN_INVALID", "Mã CSRF không hợp lệ.");
            UUID org = null, event = null, zone = null;
            boolean admin = false, priceOnly = false;
            String kind = null, operation = null;
            if (path.equals("/api/event-organizations") && !post) kind = "ORGANIZATIONS";
            else if (p.length >= 5 && p[2].equals("organizations") && p[4].equals("events")) {
                org = EventCommand.uuid(p[3]);
                if (p.length == 5) {
                    if (post) operation = "CREATE";
                    else kind = "LIST";
                } else if (p.length == 6 && !post) {
                    event = EventCommand.uuid(p[5]);
                    kind = "DETAIL";
                }
            } else if (p.length >= 4 && p[2].equals("admin") && p[3].equals("events")) {
                admin = true;
                if (p.length == 4 && !post) kind = "LIST";
                else if (p.length == 5 && !post) {
                    event = EventCommand.uuid(p[4]);
                    kind = "DETAIL";
                } else if (p.length == 6) {
                    event = EventCommand.uuid(p[4]);
                    if (post && Set.of("publish", "reject").contains(p[5]))
                        operation = p[5].toUpperCase(Locale.ROOT);
                    else if (!post && p[5].equals("commission-rules")) kind = "RULES";
                    else if (!post && p[5].equals("layout")) kind = "LAYOUT";
                }
            } else if (p.length == 5 && p[2].equals("events")) {
                event = EventCommand.uuid(p[3]);
                if (!post && p[4].equals("layout")) kind = "LAYOUT";
                else if (post)
                    operation =
                            switch (p[4]) {
                                case "edit" -> "EDIT";
                                case "delete" -> "DELETE";
                                case "submit" -> "SUBMIT";
                                case "zones" -> "ZONE_CREATE";
                                case "cover" -> "COVER";
                                default -> null;
                            };
            } else if (p.length == 5 && p[2].equals("zones") && post) {
                zone = EventCommand.uuid(p[3]);
                priceOnly = p[4].equals("price");
                operation =
                        switch (p[4]) {
                            case "edit", "price" -> "ZONE_EDIT";
                            case "delete" -> "ZONE_DELETE";
                            default -> null;
                        };
            }
            if (kind == null && operation == null)
                throw new EventProblem(404, "EVENT_ROUTE_NOT_FOUND", "Đường dẫn không hợp lệ.");
            if (!post) {
                int page = integer(parameter(request, "page"), 1),
                        size = integer(parameter(request, "pageSize"), 20);
                String status = parameter(request, "status");
                if (status == null && admin && kind.equals("LIST")) status = "PENDING_APPROVAL";
                if (status != null
                        && !Set.of(
                                        "DRAFT",
                                        "PENDING_APPROVAL",
                                        "REJECTED",
                                        "PUBLISHED",
                                        "CANCELLED")
                                .contains(status))
                    throw EventProblem.invalid("Trạng thái lọc không hợp lệ.");
                if (size > 100 || (long) (page - 1) * size > 1000000)
                    throw EventProblem.invalid("Phân trang ngoài giới hạn.");
                var services = EventManagementRuntime.resolve(request.getServletContext());
                write(
                        response,
                        200,
                        services.management()
                                .read(actor, admin, kind, org, event, zone, status, page, size));
                return;
            }
            String key = request.getHeader("Idempotency-Key");
            EventManagementService.validateKey(key);
            boolean multipart =
                    request.getContentType() != null
                            && request.getContentType()
                                    .toLowerCase(Locale.ROOT)
                                    .startsWith("multipart/form-data");
            boolean versionedCover = false;
            if (multipart && operation.equals("COVER")) {
                try {
                    versionedCover = request.getParameter("expectedVersion") != null;
                } catch (IllegalStateException e) {
                    throw new EventProblem(413, "EVENT_IMAGE_TOO_LARGE", "Ảnh tối đa 5 MB.");
                }
            }
            if (multipart
                    && (operation.equals("CREATE") || operation.equals("EDIT") || versionedCover)) {
                try {
                    request.getServletContext()
                            .getNamedDispatcher("EventApiServlet")
                            .forward(request, response);
                } catch (jakarta.servlet.ServletException e) {
                    throw EventProblem.invalid("Dữ liệu upload không hợp lệ.");
                }
                return;
            }
            if (operation.equals("COVER")) {
                if (request.getContentType() == null
                        || !request.getContentType()
                                .toLowerCase(Locale.ROOT)
                                .startsWith("multipart/form-data"))
                    throw EventProblem.invalid("Cần gửi ảnh bằng multipart/form-data.");
                Part file;
                try {
                    file = request.getPart("file");
                } catch (IllegalStateException e) {
                    throw new EventProblem(413, "EVENT_IMAGE_TOO_LARGE", "Ảnh tối đa 5 MB.");
                } catch (jakarta.servlet.ServletException e) {
                    throw EventProblem.invalid("Dữ liệu upload không hợp lệ.");
                }
                if (file == null) throw EventProblem.invalid("Thiếu tệp ảnh.");
                if (file.getSize() > ImageContentValidator.DEFAULT_MAX_BYTES)
                    throw new EventProblem(413, "EVENT_IMAGE_TOO_LARGE", "Ảnh tối đa 5 MB.");
                try (InputStream content = file.getInputStream()) {
                    var result =
                            EventManagementRuntime.resolve(request.getServletContext())
                                    .cover()
                                    .upload(actor, event, key, content);
                    write(response, result.status(), result.json());
                } finally {
                    try {
                        file.delete();
                    } catch (IOException ignored) {
                    }
                }
                return;
            }
            if (request.getContentType() != null
                    && !request.getContentType()
                            .toLowerCase(Locale.ROOT)
                            .startsWith("application/json"))
                throw new EventProblem(415, "EVENT_JSON_REQUIRED", "Cần gửi application/json.");
            Map<String, String> fields = EventRequestParser.parse(request.getReader());
            // The price route is the zone edit restricted to the one field a published layout may
            // still change.
            if (priceOnly
                    && (!fields.containsKey("basePrice")
                            || !Set.of("basePrice", "expectedVersion")
                                    .containsAll(fields.keySet())))
                throw EventProblem.invalid("Chỉ được gửi basePrice và expectedVersion.");
            var command =
                    EventCommand.input(operation, org, event, zone, fields, Clock.systemUTC());
            var result =
                    EventManagementRuntime.resolve(request.getServletContext())
                            .management()
                            .mutate(actor, key, command);
            write(response, result.status(), result.json());
        } catch (EventProblem e) {
            HttpResponses.error(response, e);
        } catch (EventCatalogService.NotFound e) {
            HttpResponses.error(
                    response, 404, "EVENT_NOT_FOUND", "Không tìm thấy sự kiện công khai.");
        } catch (IllegalArgumentException e) {
            HttpResponses.error(
                    response,
                    400,
                    "INVALID_EVENT_INPUT",
                    "Thông tin sự kiện hoặc ảnh không hợp lệ.");
        } catch (RuntimeException e) {
            HttpResponses.error(
                    response,
                    503,
                    "EVENT_DEPENDENCY_UNAVAILABLE",
                    "Chưa thể xử lý sự kiện. Vui lòng thử lại.");
        }
    }

    private static void write(HttpServletResponse response, int status, String json)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json);
    }

    private static String parameter(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) return null;
        if (values.length != 1) throw EventProblem.invalid("Tham số bị lặp.");
        return values[0].isBlank() ? null : values[0].strip();
    }

    private static int integer(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            if (!raw.matches("[0-9]{1,8}")) throw new NumberFormatException();
            int value = Integer.parseInt(raw);
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw EventProblem.invalid("Phân trang không hợp lệ.");
        }
    }
}
