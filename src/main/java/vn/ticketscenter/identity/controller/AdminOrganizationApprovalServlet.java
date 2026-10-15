package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.OrganizationRequestService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@WebServlet(
        name = "AdminOrganizationApprovalServlet",
        urlPatterns = {
                "/api/admin/organization-requests",
                "/api/admin/organization-requests/*"
        }
)
public class AdminOrganizationApprovalServlet extends HttpServlet {

    private OrganizationRequestService organizationRequestService;

    public AdminOrganizationApprovalServlet() {
    }

    public AdminOrganizationApprovalServlet(OrganizationRequestService organizationRequestService) {
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
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(req);
        if (principalOpt.isEmpty()) {
            HttpResponses.error(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }
        UserPrincipal principal = principalOpt.get();
        if (!principal.isAdmin()) {
            HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN", "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
            return;
        }

        OrganizationRequestService service = getService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        String path = path(req);
        if (path.equals("/api/admin/organization-requests") || path.equals("/api/admin/organization-requests/pending")) {
            int page = parsePositiveInt(req.getParameter("page"), 1);
            int pageSize = parsePositiveInt(req.getParameter("pageSize"), 20);

            OrganizationStatus status = null;
            if (path.endsWith("/pending")) {
                status = OrganizationStatus.PENDING_APPROVAL;
            } else {
                String statusParam = req.getParameter("status");
                if (statusParam != null && !statusParam.isBlank() && !"ALL".equalsIgnoreCase(statusParam.trim())) {
                    try {
                        status = OrganizationStatus.valueOf(statusParam.trim().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            List<OrganizationRequestSummaryDto> list = service.getRequestsForAdmin(principal.actorId(), status, page, pageSize);
            long total = service.countRequestsForAdmin(principal.actorId(), status);
            String itemsJson = list.stream()
                    .map(OrganizationRequestSummaryDto::toJson)
                    .collect(Collectors.joining(",", "[", "]"));

            HttpResponses.data(resp, "{\"items\":" + itemsJson + ",\"total\":" + total + ",\"page\":" + page + ",\"pageSize\":" + pageSize + "}");
            return;
        }

        if (path.matches("^/api/admin/organization-requests/[^/]+$")) {
            String idStr = path.substring("/api/admin/organization-requests/".length());
            UUID organizationId;
            try {
                organizationId = UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "VALIDATION_ERROR", "Mã tổ chức không hợp lệ");
                return;
            }

            try {
                OrganizationRequestDetailDto detail = service.getRequestDetail(principal.actorId(), true, organizationId);
                HttpResponses.data(resp, detail.toJson());
            } catch (NoSuchElementException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND,
                        "ORGANIZATION_NOT_FOUND", "Không tìm thấy hồ sơ yêu cầu tổ chức");
            } catch (SecurityException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", e.getMessage());
            }
            return;
        }

        HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Đường dẫn không tồn tại");
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(req);
        if (principalOpt.isEmpty()) {
            HttpResponses.error(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }
        UserPrincipal principal = principalOpt.get();
        if (!principal.isAdmin()) {
            HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN", "Chỉ quản trị viên mới có quyền thực hiện thao tác này");
            return;
        }

        OrganizationRequestService service = getService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        String path = path(req);

        if (path.matches("^/api/admin/organization-requests/[^/]+/approve$")) {
            String idStr = path.substring("/api/admin/organization-requests/".length(), path.length() - "/approve".length());
            UUID organizationId;
            try {
                organizationId = UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "VALIDATION_ERROR", "Mã tổ chức không hợp lệ");
                return;
            }

            Map<String, String> body = Map.of();
            try {
                body = JsonObjectParser.parse(req.getReader());
            } catch (Exception ignored) {
            }

            if (body.containsKey("initialPolicy") && body.get("initialPolicy") != null) {
                try {
                    Map<String, String> nested = JsonObjectParser.parse(new java.io.StringReader(body.get("initialPolicy")));
                    Map<String, String> merged = new java.util.HashMap<>(body);
                    merged.putAll(nested);
                    body = merged;
                } catch (Exception ignored) {
                }
            }

            BigDecimal ratePercent = new BigDecimal("5.0");
            long fixedFee = 10000L;
            Instant effectiveFrom = Instant.now();

            if (body.containsKey("ratePercent") && body.get("ratePercent") != null) {
                try {
                    ratePercent = new BigDecimal(body.get("ratePercent").replace("%", "").trim());
                    if (ratePercent.compareTo(BigDecimal.ZERO) < 0 || ratePercent.compareTo(new BigDecimal("100")) > 0) {
                        HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                                "VALIDATION_ERROR", "Tỷ lệ hoa hồng phải từ 0% đến 100%");
                        return;
                    }
                } catch (Exception e) {
                    HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "VALIDATION_ERROR", "Tỷ lệ hoa hồng không hợp lệ");
                    return;
                }
            }

            if (body.containsKey("fixedFee") && body.get("fixedFee") != null) {
                try {
                    fixedFee = new BigDecimal(body.get("fixedFee").trim()).longValue();
                    if (fixedFee < 0) {
                        HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                                "VALIDATION_ERROR", "Phí cố định không hợp lệ");
                        return;
                    }
                } catch (Exception e) {
                    HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "VALIDATION_ERROR", "Phí cố định không hợp lệ");
                    return;
                }
            }

            String dateStr = body.get("effectiveFrom");
            if (dateStr == null || dateStr.isBlank()) {
                dateStr = body.get("effectiveDate");
            }
            if (dateStr != null && !dateStr.isBlank()) {
                try {
                    effectiveFrom = parseInstant(dateStr);
                } catch (Exception e) {
                    HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "VALIDATION_ERROR", "Ngày hiệu lực không hợp lệ");
                    return;
                }
            }

            Instant effectiveTo = null;
            String toStr = body.get("effectiveTo");
            if (toStr != null && !toStr.isBlank()) {
                try {
                    effectiveTo = parseInstant(toStr);
                } catch (Exception e) {
                    HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "VALIDATION_ERROR", "Ngày kết thúc hiệu lực không hợp lệ");
                    return;
                }
            }

            if (effectiveTo != null && !effectiveFrom.isBefore(effectiveTo)) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "VALIDATION_ERROR", "Ngày bắt đầu phải trước ngày kết thúc");
                return;
            }

            try {
                OrganizationRequestDetailDto detail = (effectiveTo != null)
                        ? service.approveRequest(principal.actorId(), organizationId, ratePercent, fixedFee, effectiveFrom, effectiveTo)
                        : service.approveRequest(principal.actorId(), organizationId, ratePercent, fixedFee, effectiveFrom);
                HttpResponses.data(resp, "{\"success\":true,\"status\":\"APPROVED\",\"organizationId\":"
                        + HttpResponses.jsonString(organizationId.toString())
                        + ",\"assignedManagerId\":" + (detail.requesterId() != null ? HttpResponses.jsonString(detail.requesterId().toString()) : "null")
                        + ",\"commissionRuleId\":" + (detail.commissionRuleId() != null ? HttpResponses.jsonString(detail.commissionRuleId().toString()) : "null")
                        + ",\"message\":\"Đã phê duyệt tổ chức thành công.\""
                        + ",\"detail\":" + detail.toJson() + "}");
            } catch (NoSuchElementException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND,
                        "ORGANIZATION_NOT_FOUND", "Không tìm thấy hồ sơ yêu cầu tổ chức");
            } catch (IllegalStateException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_CONFLICT,
                        "CONFLICT", e.getMessage());
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "VALIDATION_ERROR", e.getMessage());
            } catch (SecurityException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", e.getMessage());
            }
            return;
        }

        if (path.matches("^/api/admin/organization-requests/[^/]+/reject$")) {
            String idStr = path.substring("/api/admin/organization-requests/".length(), path.length() - "/reject".length());
            UUID organizationId;
            try {
                organizationId = UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "VALIDATION_ERROR", "Mã tổ chức không hợp lệ");
                return;
            }

            Map<String, String> body = Map.of();
            try {
                body = JsonObjectParser.parse(req.getReader());
            } catch (Exception ignored) {
            }

            String reason = body.get("reason");
            if (reason == null || reason.isBlank()) {
                reason = body.get("rejectionReason");
            }

            if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 500) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "VALIDATION_ERROR", "Lý do từ chối phải từ 10 đến 500 ký tự");
                return;
            }

            try {
                OrganizationRequestDetailDto detail = service.rejectRequest(principal.actorId(), organizationId, reason.trim());
                HttpResponses.data(resp, "{\"success\":true,\"status\":\"REJECTED\",\"organizationId\":"
                        + HttpResponses.jsonString(organizationId.toString())
                        + ",\"rejectionReason\":" + HttpResponses.jsonString(reason.trim())
                        + ",\"message\":\"Đã từ chối yêu cầu thành lập tổ chức.\""
                        + ",\"detail\":" + detail.toJson() + "}");
            } catch (NoSuchElementException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND,
                        "ORGANIZATION_NOT_FOUND", "Không tìm thấy hồ sơ yêu cầu tổ chức");
            } catch (IllegalStateException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_CONFLICT,
                        "CONFLICT", e.getMessage());
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "VALIDATION_ERROR", e.getMessage());
            } catch (SecurityException e) {
                HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", e.getMessage());
            }
            return;
        }

        HttpResponses.error(resp, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Đường dẫn không tồn tại");
    }

    private static String path(HttpServletRequest req) {
        return req.getRequestURI().substring(req.getContextPath().length());
    }

    private static Instant parseInstant(String val) {
        if (val == null || val.isBlank()) return null;
        String s = val.trim();
        return s.contains("T") ? Instant.parse(s) : LocalDate.parse(s).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
    }

    private static int parsePositiveInt(String val, int defaultVal) {
        if (val == null || val.isBlank()) {
            return defaultVal;
        }
        try {
            int num = Integer.parseInt(val.trim());
            return num > 0 ? num : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
