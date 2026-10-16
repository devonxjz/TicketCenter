package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.controller.support.PathIds;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.error.IdentityException;
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

    private OrganizationRequestService getService(ServletContext context) {
        if (this.organizationRequestService != null) {
            return this.organizationRequestService;
        }
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.organizationRequestService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserPrincipal principal = requirePrincipal(req, resp);
        if (principal == null) {
            return;
        }
        OrganizationRequestService service = getService(req.getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(resp);
            return;
        }
        if (PathIds.path(req).equals("/api/organization-requests")) {
            submit(req, resp, service, principal);
            return;
        }
        notFound(resp);
    }

    private void submit(
            HttpServletRequest req,
            HttpServletResponse resp,
            OrganizationRequestService service,
            UserPrincipal principal)
            throws IOException {
        Map<String, String> json;
        try {
            json = JsonObjectParser.parse(req.getReader());
        } catch (IllegalArgumentException | IOException e) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_PAYLOAD",
                    "Nội dung yêu cầu không phải định dạng JSON hợp lệ");
            return;
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

        try {
            OrganizationRequestDetailDto detail =
                    service.submitRequest(
                            principal.actorId(), dto, req.getHeader("Idempotency-Key"));
            HttpResponses.created(resp, detail.toJson());
        } catch (IdentityException e) {
            IdentityErrorWriter.write(resp, e, null);
        } catch (IllegalArgumentException e) {
            HttpResponses.error(
                    resp, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
        } catch (IllegalStateException e) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST",
                    e.getMessage() != null ? e.getMessage() : "");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error submitting organization request", e);
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR",
                    "Có lỗi xảy ra trong quá trình xử lý yêu cầu");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserPrincipal principal = requirePrincipal(req, resp);
        if (principal == null) {
            return;
        }
        OrganizationRequestService service = getService(req.getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(resp);
            return;
        }

        String path = PathIds.path(req);
        if (path.equals("/api/me/organization-requests")) {
            List<OrganizationRequestSummaryDto> mine = service.getMyRequests(principal.actorId());
            HttpResponses.data(
                    resp,
                    mine.stream()
                            .map(OrganizationRequestSummaryDto::toJson)
                            .collect(Collectors.joining(",", "[", "]")));
            return;
        }
        Matcher detailPath = DETAIL_PATH.matcher(path);
        if (detailPath.matches()) {
            readDetail(resp, service, principal, detailPath.group(1));
            return;
        }
        notFound(resp);
    }

    private void readDetail(
            HttpServletResponse resp,
            OrganizationRequestService service,
            UserPrincipal principal,
            String idText)
            throws IOException {
        UUID organizationId = PathIds.uuidOrNull(idText);
        if (organizationId == null) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_ORGANIZATION_ID",
                    "Mã tổ chức không hợp lệ");
            return;
        }
        try {
            HttpResponses.data(
                    resp,
                    service.getRequestDetail(
                                    principal.actorId(), principal.isAdmin(), organizationId)
                            .toJson());
        } catch (NoSuchElementException e) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_NOT_FOUND,
                    "ORGANIZATION_NOT_FOUND",
                    "Không tìm thấy hồ sơ yêu cầu tổ chức");
        } catch (SecurityException e) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN",
                    "Bạn không có quyền truy cập hồ sơ này");
        }
    }

    /** The signed-in user, or {@code null} after answering 401. */
    private static UserPrincipal requirePrincipal(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        Optional<UserPrincipal> principal = SecurityContext.getCurrentUser(req);
        if (principal.isEmpty()) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return null;
        }
        return principal.get();
    }

    private static void notFound(HttpServletResponse resp) throws IOException {
        HttpResponses.error(
                resp, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Đường dẫn không tồn tại");
    }
}
