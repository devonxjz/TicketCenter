package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.security.AuthSecurityListener;
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
import java.util.stream.Collectors;

@WebServlet(
        name = "OrganizationRequestServlet",
        urlPatterns = {
                "/api/organization-requests",
                "/api/organization-requests/*",
                "/api/me/organization-requests",
                "/api/admin/organization-requests",
                "/api/admin/organization-requests/*"
        }
)
public class OrganizationRequestServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(OrganizationRequestServlet.class.getName());

    private OrganizationRequestService organizationRequestService;

    public OrganizationRequestServlet() {
    }

    public OrganizationRequestServlet(OrganizationRequestService organizationRequestService) {
        this.organizationRequestService = organizationRequestService;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        if (this.organizationRequestService == null && config != null) {
            this.organizationRequestService = AuthSecurityListener.resolveOrganizationRequestService(config.getServletContext());
        }
    }

    private OrganizationRequestService getService(ServletContext context) {
        if (this.organizationRequestService != null) {
            return this.organizationRequestService;
        }
        return AuthSecurityListener.resolveOrganizationRequestService(context);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(req);
        if (principalOpt.isEmpty()) {
            HttpResponses.error(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }

        OrganizationRequestService service = getService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        String path = path(req);
        UserPrincipal principal = principalOpt.get();

        if (path.matches("^/api/admin/organization-requests/[^/]+/approve$")) {
            if (!principal.isAdmin()) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
                return;
            }
            String idStr = path.substring("/api/admin/organization-requests/".length(), path.length() - "/approve".length());
            UUID organizationId;
            try {
                organizationId = UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_ORGANIZATION_ID", "Mã tổ chức không hợp lệ");
                return;
            }

            try {
                OrganizationRequestDetailDto detail = service.approveRequest(principal.actorId(), organizationId);
                HttpResponses.data(resp, detail.toJson());
            } catch (NoSuchElementException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND,
                        "ORGANIZATION_NOT_FOUND", "Không tìm thấy hồ sơ yêu cầu tổ chức");
            } catch (IllegalStateException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_STATE", e.getMessage());
            } catch (SecurityException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected error approving organization request", e);
                HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR", "Có lỗi xảy ra trong quá trình xử lý yêu cầu");
            }
            return;
        }

        if (path.equals("/api/organization-requests")) {
            Map<String, String> json;
            try {
                json = JsonObjectParser.parse(req.getReader());
            } catch (IllegalArgumentException | IOException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_PAYLOAD", "Nội dung yêu cầu không phải định dạng JSON hợp lệ");
                return;
            }

            String name = json.get("name");
            String contactEmail = json.get("contactEmail");
            String contactPhone = json.get("contactPhone");
            String description = json.get("description");
            String eventTitle = json.get("eventTitle");
            String eventDate = json.get("eventDate");
            String eventStartTime = json.get("eventStartTime");
            String eventEndTime = json.get("eventEndTime");
            String eventVenue = json.get("eventVenue");
            OrganizationRequestCreateDto dto = new OrganizationRequestCreateDto(
                    name, contactEmail, contactPhone, description,
                    eventTitle, eventDate, eventStartTime, eventEndTime, eventVenue
            );

            String idempotencyKey = req.getHeader("Idempotency-Key");

            try {
                OrganizationRequestDetailDto detail = service.submitRequest(principal.actorId(), dto, idempotencyKey);
                HttpResponses.created(resp, detail.toJson());
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "VALIDATION_ERROR", e.getMessage());
            } catch (IllegalStateException e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.startsWith("ORGANIZATION_REQUEST_EXISTS")) {
                    HttpResponses.error(resp, HttpServletResponse.SC_CONFLICT,
                            "ORGANIZATION_REQUEST_EXISTS", "Bạn đang có một yêu cầu tạo tổ chức đang chờ xét duyệt");
                } else if (msg.startsWith("IDEMPOTENCY_CONFLICT")) {
                    HttpResponses.error(resp, HttpServletResponse.SC_CONFLICT,
                            "IDEMPOTENCY_CONFLICT", "Idempotency-Key đã được sử dụng với nội dung khác");
                } else if (msg.startsWith("USER_SUSPENDED")) {
                    HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                            "USER_SUSPENDED", "Tài khoản của bạn hiện không thể gửi yêu cầu");
                } else {
                    HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "BAD_REQUEST", msg);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected error submitting organization request", e);
                HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR", "Có lỗi xảy ra trong quá trình xử lý yêu cầu");
            }
            return;
        }

        HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND,
                "NOT_FOUND", "Đường dẫn không tồn tại");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(req);
        if (principalOpt.isEmpty()) {
            HttpResponses.error(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }

        OrganizationRequestService service = getService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        String path = path(req);
        UserPrincipal principal = principalOpt.get();

        if (path.equals("/api/admin/organization-requests") || path.equals("/api/admin/organization-requests/pending")) {
            if (!principal.isAdmin()) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
                return;
            }
            try {
                List<OrganizationRequestSummaryDto> list = service.getPendingRequestsForAdmin(principal.actorId());
                long count = service.countPendingRequestsForAdmin(principal.actorId());
                String itemsJson = list.stream()
                        .map(OrganizationRequestSummaryDto::toJson)
                        .collect(Collectors.joining(",", "[", "]"));
                HttpResponses.data(resp, "{\"count\":" + count + ",\"items\":" + itemsJson + "}");
            } catch (SecurityException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
            }
            return;
        }

        if (path.equals("/api/me/organization-requests")) {
            List<OrganizationRequestSummaryDto> list = service.getMyRequests(principal.actorId());
            String jsonList = list.stream()
                    .map(OrganizationRequestSummaryDto::toJson)
                    .collect(Collectors.joining(",", "[", "]"));
            HttpResponses.data(resp, jsonList);
            return;
        }

        if (path.matches("^/api/organization-requests/[^/]+$")) {
            String idStr = path.substring("/api/organization-requests/".length());
            UUID organizationId;
            try {
                organizationId = UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_ORGANIZATION_ID", "Mã tổ chức không hợp lệ");
                return;
            }

            try {
                OrganizationRequestDetailDto detail = service.getRequestDetail(
                        principal.actorId(),
                        principal.isAdmin(),
                        organizationId
                );
                HttpResponses.data(resp, detail.toJson());
            } catch (NoSuchElementException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND,
                        "ORGANIZATION_NOT_FOUND", "Không tìm thấy hồ sơ yêu cầu tổ chức");
            } catch (SecurityException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "Bạn không có quyền truy cập hồ sơ này");
            }
            return;
        }

        if (path.matches("^/api/admin/organization-requests/[^/]+$")) {
            if (!principal.isAdmin()) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
                return;
            }
            String idStr = path.substring("/api/admin/organization-requests/".length());
            UUID organizationId;
            try {
                organizationId = UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_ORGANIZATION_ID", "Mã tổ chức không hợp lệ");
                return;
            }

            try {
                OrganizationRequestDetailDto detail = service.getRequestDetail(
                        principal.actorId(),
                        true,
                        organizationId
                );
                HttpResponses.data(resp, detail.toJson());
            } catch (NoSuchElementException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND,
                        "ORGANIZATION_NOT_FOUND", "Không tìm thấy hồ sơ yêu cầu tổ chức");
            } catch (SecurityException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
            }
            return;
        }

        HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND,
                "NOT_FOUND", "Đường dẫn không tồn tại");
    }

    private static String path(HttpServletRequest req) {
        return req.getRequestURI().substring(req.getContextPath().length());
    }
}
