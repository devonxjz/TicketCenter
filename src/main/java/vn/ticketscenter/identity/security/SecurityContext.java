package vn.ticketscenter.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

public final class SecurityContext {

    public static final String SESSION_USER_ATTRIBUTE = "TC_CURRENT_USER";
    public static final String REQUEST_USER_ATTRIBUTE = "TC_REQUEST_USER";
    public static final String CSRF_TOKEN_ATTRIBUTE = "TC_CSRF_TOKEN";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";
    public static final String CSRF_PARAM_NAME = "_csrf";

    private SecurityContext() {}

    /**
     * Lấy UserPrincipal từ Request Attribute (được gắn bởi Filter) hoặc từ HttpSession.
     */
    public static Optional<UserPrincipal> getCurrentUser(HttpServletRequest request) {
        if (request == null) return Optional.empty();

        Object reqAttr = request.getAttribute(REQUEST_USER_ATTRIBUTE);
        if (reqAttr instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }

        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object sessAttr = session.getAttribute(SESSION_USER_ATTRIBUTE);
                if (sessAttr instanceof UserPrincipal principal) {
                    request.setAttribute(REQUEST_USER_ATTRIBUTE, principal);
                    return Optional.of(principal);
                }
            }
        } catch (IllegalStateException ignored) {
            // Session đã bị invalidate
        }

        return Optional.empty();
    }

    /**
     * Yêu cầu người dùng phải đăng nhập, ném IllegalArgumentException nếu chưa có phiên.
     */
    public static UserPrincipal requireCurrentUser(HttpServletRequest request) {
        return getCurrentUser(request)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu phiên đăng nhập"));
    }

    /**
     * Thiết lập phiên đăng nhập mới:
     * 1. Cấp mới Session ID (chống session fixation).
     * 2. Lưu UserPrincipal vào Session và Request.
     * 3. Xoay vòng (rotate) CSRF Token để chống CSRF Token Fixation.
     */
    public static void setSessionUser(HttpServletRequest request, UserPrincipal principal) {
        HttpSession session = request.getSession(true);
        try {
            request.changeSessionId();
        } catch (IllegalStateException ignored) {
            // Nếu container chưa hỗ trợ hoặc session mới tạo
        }

        session.setAttribute(SESSION_USER_ATTRIBUTE, principal);
        request.setAttribute(REQUEST_USER_ATTRIBUTE, principal);

        // Rotate CSRF token on privilege elevation
        String newCsrfToken = UUID.randomUUID().toString();
        session.setAttribute(CSRF_TOKEN_ATTRIBUTE, newCsrfToken);
    }

    /**
     * Hủy phiên làm việc an toàn (lũy thỏa, không lỗi khi gọi lặp).
     */
    public static void clearSession(HttpServletRequest request) {
        if (request == null) return;
        request.removeAttribute(REQUEST_USER_ATTRIBUTE);
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        } catch (IllegalStateException ignored) {

        }
    }

    /**
     * Lấy hoặc sinh mới CSRF Token cho phiên hiện tại.
     * Đồng bộ hóa trên session để tránh race condition khi có request song song.
     */
    public static String getOrCreateCsrfToken(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session is required to generate CSRF token");
        }
        synchronized (session) {
            String token = (String) session.getAttribute(CSRF_TOKEN_ATTRIBUTE);
            if (token == null || token.isBlank()) {
                token = UUID.randomUUID().toString();
                session.setAttribute(CSRF_TOKEN_ATTRIBUTE, token);
            }
            return token;
        }
    }

    public static String getOrCreateCsrfToken(HttpServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required to generate CSRF token");
        }
        return getOrCreateCsrfToken(request.getSession(true));
    }

    /**
     * Xác thực CSRF Token từ header hoặc parameter chống Timing Attack.
     */
    public static boolean validateCsrfToken(HttpServletRequest request) {
        if (request == null) return false;
        try {
            HttpSession session = request.getSession(false);
            if (session == null) return false;

            String sessionToken = (String) session.getAttribute(CSRF_TOKEN_ATTRIBUTE);
            if (sessionToken == null || sessionToken.isBlank()) return false;

            String requestToken = request.getHeader(CSRF_HEADER_NAME);
            if (requestToken == null || requestToken.isBlank()) {
                requestToken = request.getParameter(CSRF_PARAM_NAME);
            }
            if (requestToken == null || requestToken.isBlank()) return false;

            return MessageDigest.isEqual(
                    sessionToken.getBytes(StandardCharsets.UTF_8),
                    requestToken.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
