package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.error.AppException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.controller.support.PathIds;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.OrganizationRequestService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The requester's side of organisation requests: submit, list their own, read one. */
@WebServlet(
        name = "OrganizationRequestServlet",
        urlPatterns = {
            "/api/organization-requests",
            "/api/organization-requests/*",
            "/api/me/organization-requests"
        })
public class OrganizationRequestServlet extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(OrganizationRequestServlet.class.getName());
    private static final Pattern DETAIL_PATH =
            Pattern.compile("^/api/organization-requests/([^/]+)$");

    private OrganizationRequestService organizationRequestService;

    public OrganizationRequestServlet() {}

    public OrganizationRequestServlet(OrganizationRequestService organizationRequestService) {
        this.organizationRequestService = organizationRequestService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UserPrincipal principal = requirePrincipal(req);
            OrganizationRequestService service = service(req.getServletContext());
            String path = PathIds.path(req);

            // perform action and build the JSON reply
            String json;
            Matcher detailPath = DETAIL_PATH.matcher(path);
            if (path.equals("/api/me/organization-requests")) {
                json = listMyRequests(service, principal);
            } else if (detailPath.matches()) {
                json = showRequest(service, principal, detailPath.group(1));
            } else {
                throw notFound();
            }
            HttpResponses.data(resp, json);
        } catch (AppException rejected) {
            HttpResponses.error(resp, rejected);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UserPrincipal principal = requirePrincipal(req);
            OrganizationRequestService service = service(req.getServletContext());
            if (!PathIds.path(req).equals("/api/organization-requests")) {
                throw notFound();
            }

            // submit the request and write the JSON reply
            OrganizationRequestDetailDto detail = submitRequest(req, service, principal);
            HttpResponses.created(resp, detail.toJson());
        } catch (AppException rejected) {
            HttpResponses.error(resp, rejected);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Unexpected error submitting organization request", e);
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR",
                    "Có lỗi xảy ra trong quá trình xử lý yêu cầu");
        }
    }

    private static String listMyRequests(
            OrganizationRequestService service, UserPrincipal principal) {
        List<OrganizationRequestSummaryDto> mine = service.getMyRequests(principal.actorId());
        return mine.stream()
                .map(OrganizationRequestSummaryDto::toJson)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String showRequest(
            OrganizationRequestService service, UserPrincipal principal, String idText) {
        UUID organizationId = PathIds.uuidOrNull(idText);
        if (organizationId == null) {
            throw new AppException(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_ORGANIZATION_ID",
                    "Mã tổ chức không hợp lệ");
        }
        try {
            return service.getRequestDetail(
                            principal.actorId(), principal.isAdmin(), organizationId)
                    .toJson();
        } catch (NoSuchElementException e) {
            throw new AppException(
                    HttpServletResponse.SC_NOT_FOUND,
                    "ORGANIZATION_NOT_FOUND",
                    "Không tìm thấy hồ sơ yêu cầu tổ chức");
        } catch (SecurityException e) {
            throw new AppException(
                    HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN",
                    "Bạn không có quyền truy cập hồ sơ này");
        }
    }

    private static OrganizationRequestDetailDto submitRequest(
            HttpServletRequest req, OrganizationRequestService service, UserPrincipal principal) {
        // get parameters from the JSON body
        Map<String, String> json;
        try {
            json = JsonObjectParser.parse(req.getReader());
        } catch (IllegalArgumentException | IOException e) {
            throw new AppException(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_PAYLOAD",
                    "Nội dung yêu cầu không phải định dạng JSON hợp lệ");
        }
        OrganizationRequestCreateDto dto =
                new OrganizationRequestCreateDto(
                        json.get("name"),
                        json.get("contactEmail"),
                        json.get("contactPhone"),
                        json.get("description"),
                        json.get("eventTitle"),
                        json.get("eventDate"),
                        json.get("eventStartTime"),
                        json.get("eventEndTime"),
                        json.get("eventVenue"));

        // store the request; validation failures become 400 replies
        try {
            return service.submitRequest(
                    principal.actorId(), dto, req.getHeader("Idempotency-Key"));
        } catch (IllegalArgumentException e) {
            throw new AppException(
                    HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            String message = e.getMessage() != null ? e.getMessage() : "";
            throw new AppException(HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", message);
        }
    }

    private OrganizationRequestService service(ServletContext context) {
        if (organizationRequestService != null) {
            return organizationRequestService;
        }
        IdentityServices services = IdentityServices.from(context);
        if (services == null) {
            throw IdentityErrorWriter.unavailable();
        }
        return services.organizationRequestService();
    }

    private static UserPrincipal requirePrincipal(HttpServletRequest req) {
        Optional<UserPrincipal> principal = SecurityContext.getCurrentUser(req);
        if (principal.isEmpty()) {
            throw new AppException(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
        }
        return principal.get();
    }

    private static AppException notFound() {
        return new AppException(
                HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Đường dẫn không tồn tại");
    }
}
