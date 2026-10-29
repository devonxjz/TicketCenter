package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.error.AppException;
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

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            UserPrincipal admin = requireAdmin(request);
            OrganizationApprovalService service = service(getServletContext());
            String path = PathIds.path(request);

            // perform action and build the JSON reply
            String json;
            Matcher detailPath = DETAIL_PATH.matcher(path);
            if (path.equals(BASE) || path.equals(BASE + "/pending")) {
                json = listRequests(request, service, admin, path.endsWith("/pending"));
            } else if (detailPath.matches()) {
                json = showRequest(service, admin, detailPath.group(1));
            } else {
                throw notFound();
            }
            HttpResponses.data(response, json);
        } catch (AppException rejected) {
            HttpResponses.error(response, rejected);
        } catch (NoSuchElementException | SecurityException failure) {
            writeFailure(response, failure);
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            UserPrincipal admin = requireAdmin(request);
            OrganizationApprovalService service = service(getServletContext());
            String path = PathIds.path(request);

            // perform action and build the JSON reply
            String json;
            Matcher approvePath = APPROVE_PATH.matcher(path);
            Matcher rejectPath = REJECT_PATH.matcher(path);
            if (approvePath.matches()) {
                json = approveRequest(request, service, admin, approvePath.group(1));
            } else if (rejectPath.matches()) {
                json = rejectRequest(request, service, admin, rejectPath.group(1));
            } else {
                throw notFound();
            }
            HttpResponses.data(response, json);
        } catch (AppException rejected) {
            HttpResponses.error(response, rejected);
        } catch (NoSuchElementException
                | IllegalStateException
                | IllegalArgumentException
                | SecurityException failure) {
            writeFailure(response, failure);
        }
    }

    private String listRequests(
            HttpServletRequest request,
            OrganizationApprovalService service,
            UserPrincipal admin,
            boolean pendingOnly) {
        int page = ApiRequest.positiveInt(request.getParameter("page"), 1);
        int pageSize = ApiRequest.positiveInt(request.getParameter("pageSize"), 20);
        OrganizationStatus status;
        if (pendingOnly) {
            status = OrganizationStatus.PENDING_APPROVAL;
        } else {
            status = statusOf(request.getParameter("status"));
        }

        List<OrganizationRequestSummaryDto> items =
                service.listRequests(admin.actorId(), status, page, pageSize);
        long total = service.countRequests(admin.actorId(), status);
        return new OrganizationRequestPageDto(items, total, page, pageSize).toJson();
    }

    private String showRequest(
            OrganizationApprovalService service, UserPrincipal admin, String idText) {
        UUID organizationId = organizationId(idText);
        return service.getRequestDetail(admin.actorId(), organizationId).toJson();
    }

    private String approveRequest(
            HttpServletRequest request,
            OrganizationApprovalService service,
            UserPrincipal admin,
            String idText)
            throws IOException {
        UUID organizationId = organizationId(idText);
        ApproveOrganizationRequestDto policy =
                ApproveOrganizationRequestDto.from(RequestFields.readJsonOrEmpty(request).asMap());
        OrganizationRequestDetailDto detail =
                service.approve(
                        admin.actorId(), organizationId, Optional.of(policy.commissionPolicy()));
        return OrganizationDecisionResultDto.approved(organizationId, detail).toJson();
    }

    private String rejectRequest(
            HttpServletRequest request,
            OrganizationApprovalService service,
            UserPrincipal admin,
            String idText)
            throws IOException {
        UUID organizationId = organizationId(idText);
        RequestFields body = RequestFields.readJsonOrEmpty(request);
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            reason = body.get("rejectionReason");
        }
        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 500) {
            throw new AppException(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Lý do từ chối phải từ 10 đến 500 ký tự");
        }

        String trimmedReason = reason.trim();
        OrganizationRequestDetailDto detail =
                service.reject(admin.actorId(), organizationId, trimmedReason);
        return OrganizationDecisionResultDto.rejected(organizationId, trimmedReason, detail)
                .toJson();
    }

    private OrganizationApprovalService service(ServletContext context) {
        IdentityServices services = IdentityServices.from(context);
        if (services == null) {
            throw IdentityErrorWriter.unavailable();
        }
        return services.organizationApprovalService();
    }

    private static void writeFailure(HttpServletResponse response, RuntimeException failure)
            throws IOException {
        switch (failure) {
            case NoSuchElementException e -> HttpResponses.error(
                    response,
                    HttpServletResponse.SC_NOT_FOUND,
                    "ORGANIZATION_NOT_FOUND",
                    "Không tìm thấy hồ sơ yêu cầu tổ chức");
            case IllegalStateException e -> HttpResponses.error(
                    response, HttpServletResponse.SC_CONFLICT, "CONFLICT", e.getMessage());
            case IllegalArgumentException e -> HttpResponses.error(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_ERROR",
                    e.getMessage());
            default -> HttpResponses.error(
                    response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", failure.getMessage());
        }
    }

    private static UUID organizationId(String idText) {
        UUID id = PathIds.uuidOrNull(idText);
        if (id == null) {
            throw new AppException(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Mã tổ chức không hợp lệ");
        }
        return id;
    }

    private static UserPrincipal requireAdmin(HttpServletRequest request) {
        Optional<UserPrincipal> principal = SecurityContext.getCurrentUser(request);
        if (principal.isEmpty()) {
            throw new AppException(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
        }
        if (!principal.get().isAdmin()) {
            throw new AppException(
                    HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN",
                    "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
        }
        return principal.get();
    }

    private static AppException notFound() {
        return new AppException(
                HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Đường dẫn không tồn tại");
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
