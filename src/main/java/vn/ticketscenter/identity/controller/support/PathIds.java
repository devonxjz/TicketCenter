package vn.ticketscenter.identity.controller.support;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/** Helpers for UUIDs that appear in a request path. */
public final class PathIds {

    private PathIds() {
    }

    /** The request path relative to the application context. */
    public static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** The UUID, or {@code null} when {@code text} is absent or not a UUID. */
    public static UUID uuidOrNull(String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
