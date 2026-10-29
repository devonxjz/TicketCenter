package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.controller.support.PathIds;
import vn.ticketscenter.identity.controller.support.RequestFields;
import vn.ticketscenter.identity.dto.ApproveOrganizationRequestDto;
import vn.ticketscenter.identity.dto.OrganizationDecisionResultDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestPageDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.OrganizationApprovalService;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The admin's side of organisation requests: list, inspect, approve and reject. */
@WebServlet(
        name = "AdminOrganizationApprovalServlet",
        urlPatterns = {"/api/admin/organization-requests", "/api/admin/organization-requests/*"})
public class AdminOrganizationApprovalServlet extends HttpServlet {

    private static final String BASE = "/api/admin/organization-requests";
    private static final Pattern DETAIL_PATH = Pattern.compile("^" + BASE + "/([^/]+)$");
    private static final Pattern APPROVE_PATH = Pattern.compile("^" + BASE + "/([^/]+)/approve$");
    private static final Pattern REJECT_PATH = Pattern.compile("^" + BASE + "/([^/]+)/reject$");

    private OrganizationApprovalService approvalService;

    public AdminOrganizationApprovalServlet() {}

    public AdminOrganizationApprovalServlet(OrganizationApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    private OrganizationApprovalService getService(ServletContext context) {
        if (this.approvalService != null) {
            return this.approvalService;
        }
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.organizationApprovalService();
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserPrincipal admin = requireAdmin(req, resp);
        if (admin == null) {
            return;
        }
        OrganizationApprovalService service = getService(req.getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(resp);
            return;
        }

        String path = PathIds.path(req);
        if (path.equals(BASE) || path.equals(BASE + "/pending")) {
            list(req, resp, service, admin, path.endsWith("/pending"));
            return;
        }
        Matcher detailPath = DETAIL_PATH.matcher(path);
        if (detailPath.matches()) {
            readDetail(resp, service, admin, detailPath.group(1));
            return;
        }
        notFound(resp);
    }

    private void list(
            HttpServletRequest req,
            HttpServletResponse resp,
            OrganizationApprovalService service,
            UserPrincipal admin,
            boolean pendingOnly)
            throws IOException {
        int page = ApiRequest.positiveInt(req.getParameter("page"), 1);
        int pageSize = ApiRequest.positiveInt(req.getParameter("pageSize"), 20);
        OrganizationStatus status =
                pendingOnly
                        ? OrganizationStatus.PENDING_APPROVAL
                        : statusOf(req.getParameter("status"));

        List<OrganizationRequestSummaryDto> items =
                service.listRequests(admin.actorId(), status, page, pageSize);
        long total = service.countRequests(admin.actorId(), status);
        HttpResponses.data(
                resp, new OrganizationRequestPageDto(items, total, page, pageSize).toJson());
    }

    private void readDetail(
            HttpServletResponse resp,
            OrganizationApprovalService service,
            UserPrincipal admin,
            String idText)
            throws IOException {
        UUID organizationId = organizationId(resp, idText);
        if (organizationId == null) {
            return;
        }
        try {
            HttpResponses.data(
                    resp, service.getRequestDetail(admin.actorId(), organizationId).toJson());
        } catch (NoSuchElementException | SecurityException e) {
            writeFailure(resp, e);
        }
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserPrincipal admin = requireAdmin(req, resp);
        if (admin == null) {
            return;
        }
        OrganizationApprovalService service = getService(req.getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(resp);
            return;
        }

        String path = PathIds.path(req);
        Matcher approvePath = APPROVE_PATH.matcher(path);
        if (approvePath.matches()) {
            approve(req, resp, service, admin, approvePath.group(1));
            return;
        }
        Matcher rejectPath = REJECT_PATH.matcher(path);
        if (rejectPath.matches()) {
            reject(req, resp, service, admin, rejectPath.group(1));
            return;
        }
        notFound(resp);
    }

    private void approve(
            HttpServletRequest req,
            HttpServletResponse resp,
            OrganizationApprovalService service,
            UserPrincipal admin,
            String idText)
            throws IOException {
        UUID organizationId = organizationId(resp, idText);
        if (organizationId == null) {
            return;
        }
        try {
            ApproveOrganizationRequestDto policy =
                    ApproveOrganizationRequestDto.from(RequestFields.readJsonOrEmpty(req).asMap());
            OrganizationRequestDetailDto detail =
                    service.approve(
                            admin.actorId(),
                            organizationId,
                            Optional.of(policy.commissionPolicy()));
            HttpResponses.data(
                    resp, OrganizationDecisionResultDto.approved(organizationId, detail).toJson());
        } catch (NoSuchElementException
                | IllegalStateException
                | IllegalArgumentException
                | SecurityException e) {
            writeFailure(resp, e);
        }
    }

    private void reject(
            HttpServletRequest req,
            HttpServletResponse resp,
            OrganizationApprovalService service,
            UserPrincipal admin,
            String idText)
            throws IOException {
        UUID organizationId = organizationId(resp, idText);
        if (organizationId == null) {
            return;
        }
        RequestFields body = RequestFields.readJsonOrEmpty(req);
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            reason = body.get("rejectionReason");
        }
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 500) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Lý do từ chối phải từ 10 đến 500 ký tự");
            return;
        }
        String trimmedReason = reason.trim();
        try {
            OrganizationRequestDetailDto detail =
                    service.reject(admin.actorId(), organizationId, trimmedReason);
            HttpResponses.data(
                    resp,
                    OrganizationDecisionResultDto.rejected(organizationId, trimmedReason, detail)
                            .toJson());
        } catch (NoSuchElementException
                | IllegalStateException
                | IllegalArgumentException
                | SecurityException e) {
            writeFailure(resp, e);
        }
    }

    private static void writeFailure(HttpServletResponse resp, RuntimeException failure)
            throws IOException {
        switch (failure) {
            case NoSuchElementException e -> HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_NOT_FOUND,
                    "ORGANIZATION_NOT_FOUND",
                    "Không tìm thấy hồ sơ yêu cầu tổ chức");
            case IllegalStateException e -> HttpResponses.error(
                    resp, HttpServletResponse.SC_CONFLICT, "CONFLICT", e.getMessage());
            case IllegalArgumentException e -> HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_ERROR",
                    e.getMessage());
            default -> HttpResponses.error(
                    resp, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", failure.getMessage());
        }
    }

    /** The organisation id from the path, or {@code null} after answering 400. */
    private static UUID organizationId(HttpServletResponse resp, String idText) throws IOException {
        UUID id = PathIds.uuidOrNull(idText);
        if (id == null) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Mã tổ chức không hợp lệ");
        }
        return id;
    }

    /** The signed-in admin, or {@code null} after answering 401 or 403. */
    private static UserPrincipal requireAdmin(HttpServletRequest req, HttpServletResponse resp)
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
        if (!principal.get().isAdmin()) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN",
                    "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
            return null;
        }
        return principal.get();
    }

    private static void notFound(HttpServletResponse resp) throws IOException {
        HttpResponses.error(
                resp, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Đường dẫn không tồn tại");
    }

    private static OrganizationStatus statusOf(String param) {
        if (param == null || param.isBlank() || "ALL".equalsIgnoreCase(param.trim())) {
            return null;
        }
        try {
            return OrganizationStatus.valueOf(param.trim().toUpperCase());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
