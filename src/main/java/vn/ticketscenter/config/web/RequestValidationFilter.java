package vn.ticketscenter.config.web;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;

import java.io.IOException;
import java.util.Set;

@WebFilter(urlPatterns = "/api/*")
public final class RequestValidationFilter implements Filter {

    private static final long MAX_BODY_BYTES = 64 * 1024;
    private static final long MAX_COVER_BODY_BYTES = 5_300_000;
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("Cache-Control", "no-store");
        boolean imageUpload = isImageUpload(httpRequest);
        long limit = imageUpload ? MAX_COVER_BODY_BYTES : MAX_BODY_BYTES;
        if (httpRequest.getContentLengthLong() > limit) {
            HttpResponses.error(httpResponse, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "Request payload is too large");
            return;
        }
        if (BODY_METHODS.contains(httpRequest.getMethod())) {
            String contentType = httpRequest.getContentType();
            if (contentType == null || !(contentType.startsWith("application/json")
                    || contentType.startsWith("application/x-www-form-urlencoded")
                    || (imageUpload && contentType.startsWith("multipart/form-data")))) {
                HttpResponses.error(httpResponse, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                        "CONTENT_TYPE_INVALID", "Unsupported content type");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isImageUpload(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.startsWith("multipart/form-data")) return false;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.matches("/api/events/[0-9a-fA-F-]{36}/(cover|image)")
                || path.equals("/api/me/profile");
    }
}
