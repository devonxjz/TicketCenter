package vn.ticketscenter.identity.controller.support;

import jakarta.servlet.http.HttpServletRequest;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;

import java.io.IOException;
import java.util.Map;

/** Request fields read from a JSON body or, for other content types, from form parameters. */
public final class RequestFields {

    private final Map<String, String> json;
    private final HttpServletRequest request;

    private RequestFields(Map<String, String> json, HttpServletRequest request) {
        this.json = json;
        this.request = request;
    }

    /** @throws IdentityException {@code INVALID_JSON} when a JSON body cannot be parsed */
    public static RequestFields read(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.startsWith("application/json")) {
            return new RequestFields(null, request);
        }
        return new RequestFields(parseJson(request), request);
    }

    /** Reads the body as JSON whatever the content type. */
    public static RequestFields readJson(HttpServletRequest request) {
        return new RequestFields(parseJson(request), request);
    }

    /** Reads a JSON body, treating a missing or malformed body as having no fields. */
    public static RequestFields readJsonOrEmpty(HttpServletRequest request) {
        try {
            return new RequestFields(parseJson(request), request);
        } catch (IdentityException unreadable) {
            return new RequestFields(Map.of(), request);
        }
    }

    public static boolean isJson(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.startsWith("application/json");
    }

    /** All JSON fields, or an empty map when the fields came from a form. */
    public Map<String, String> asMap() {
        return json != null ? json : Map.of();
    }

    public String get(String name) {
        return json != null ? json.get(name) : request.getParameter(name);
    }

    /** The first of {@code names} that is present, or {@code null}. */
    public String first(String... names) {
        for (String name : names) {
            String value = get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, String> parseJson(HttpServletRequest request) {
        try {
            return JsonObjectParser.parse(request.getReader());
        } catch (IOException | RuntimeException invalid) {
            throw new IdentityException(
                    IdentityError.INVALID_JSON, "Định dạng JSON không hợp lệ", invalid);
        }
    }
}
