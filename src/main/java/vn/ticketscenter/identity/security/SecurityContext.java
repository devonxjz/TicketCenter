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
     * The UserPrincipal from the request attribute (set by a filter) or the HttpSession.
     */
    public static Optional<UserPrincipal> getCurrentUser(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

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
            // the session was invalidated
        }

        return Optional.empty();
    }

    /**
     * The signed-in user; throws IllegalArgumentException when there is no session.
     */
    public static UserPrincipal requireCurrentUser(HttpServletRequest request) {
        return getCurrentUser(request)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu phiên đăng nhập"));
    }

    /**
     * Starts a signed-in session:
     * 1. a new session id (against session fixation),
     * 2. the UserPrincipal in the session and the request,
     * 3. a rotated CSRF token (against CSRF token fixation).
     */
    public static void setSessionUser(HttpServletRequest request, UserPrincipal principal) {
        HttpSession session = request.getSession(true);
        try {
            request.changeSessionId();
        } catch (IllegalStateException ignored) {
            // the container does not support it, or the session is new
        }

        session.setAttribute(SESSION_USER_ATTRIBUTE, principal);
        request.setAttribute(REQUEST_USER_ATTRIBUTE, principal);

        // Rotate CSRF token on privilege elevation
        String newCsrfToken = UUID.randomUUID().toString();
        session.setAttribute(CSRF_TOKEN_ATTRIBUTE, newCsrfToken);
    }

    /**
     * Ends the session (idempotent; repeated calls do not fail).
     */
    public static void clearSession(HttpServletRequest request) {
        if (request == null) {
            return;
        }
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
     * The session's CSRF token, created on first use.
     * Synchronised on the session so parallel requests get the same token.
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
     * Checks the CSRF token from the header or parameter in constant time.
     */
    public static boolean validateCsrfToken(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return false;
            }

            String sessionToken = (String) session.getAttribute(CSRF_TOKEN_ATTRIBUTE);
            if (sessionToken == null || sessionToken.isBlank()) {
                return false;
            }

            String requestToken = request.getHeader(CSRF_HEADER_NAME);
            if (requestToken == null || requestToken.isBlank()) {
                requestToken = request.getParameter(CSRF_PARAM_NAME);
            }
            if (requestToken == null || requestToken.isBlank()) {
                return false;
            }

            return MessageDigest.isEqual(
                    sessionToken.getBytes(StandardCharsets.UTF_8),
                    requestToken.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
