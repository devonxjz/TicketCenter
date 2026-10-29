package vn.ticketscenter.config.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request checks shared by the JSON API servlets. Each check only answers; the servlet throws its own
 * feature error, so status codes and messages stay per feature.
 */
public final class ApiRequest {
    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ApiRequest() {}

    /** The request path below the context path, e.g. {@code /api/orders/…}. */
    public static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** True when the media type, ignoring parameters such as charset, is application/json. */
    public static boolean isJson(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.split(";", 2)[0].strip().equalsIgnoreCase("application/json");
    }

    /** Canonical 8-4-4-4-12 hex text only; {@link UUID#fromString} alone also accepts shortened forms. */
    public static Optional<UUID> uuid(String text) {
        return text != null && UUID_TEXT.matcher(text).matches()
                ? Optional.of(UUID.fromString(text))
                : Optional.empty();
    }

    /** The header's value when it was sent exactly once. */
    public static Optional<String> singleHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) {
            return Optional.empty();
        }
        String value = values.nextElement();
        return values.hasMoreElements() ? Optional.empty() : Optional.ofNullable(value);
    }

    /** The parameter's value when it is the request's only parameter and appears once. */
    public static Optional<String> onlyParameter(HttpServletRequest request, String name) {
        if (!request.getParameterMap().keySet().equals(Set.of(name))
                || request.getParameterValues(name).length != 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(request.getParameter(name));
    }

    /** A positive integer parameter; anything missing, malformed or not positive falls back. */
    public static int positiveInt(String text, int fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(text.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** UTF-8 JSON that proxies and browsers must not cache. */
    public static void prepareJson(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
    }
}
