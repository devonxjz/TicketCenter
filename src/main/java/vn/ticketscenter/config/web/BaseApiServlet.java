package vn.ticketscenter.config.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Base servlet for API endpoints.
 * Handles error responses, content-type setup, and JSON serialization automatically.
 * Subclasses only need to implement doRequest() with business logic.
 */
public abstract class BaseApiServlet extends HttpServlet {

    @Override
    public final void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        doRequest(req, res);
    }

    @Override
    public final void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        doRequest(req, res);
    }

    @Override
    public final void doPut(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        doRequest(req, res);
    }

    @Override
    public final void doDelete(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        doRequest(req, res);
    }

    /**
     * Override this to handle business logic.
     * Use respondData/respondCreated/respondError helpers to send responses.
     */
    protected abstract void doRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException;

    /**
     * Send successful response with 200 OK.
     */
    protected final void respondData(HttpServletResponse res, String jsonObject) throws IOException {
        HttpResponses.data(res, jsonObject);
    }

    /**
     * Send successful response with DTO serialized to JSON.
     */
    protected final void respondData(HttpServletResponse res, JsonSerializable dto) throws IOException {
        HttpResponses.dataDto(res, dto);
    }

    /**
     * Send created response with 201 Created.
     */
    protected final void respondCreated(HttpServletResponse res, String jsonObject) throws IOException {
        HttpResponses.created(res, jsonObject);
    }

    /**
     * Send created response with DTO serialized to JSON.
     */
    protected final void respondCreated(HttpServletResponse res, JsonSerializable dto) throws IOException {
        HttpResponses.createdDto(res, dto);
    }

    /**
     * Send error response.
     */
    protected final void respondError(HttpServletResponse res, int status, String code, String message)
            throws IOException {
        HttpResponses.error(res, status, code, message);
    }

    /**
     * Send error response with exception message.
     */
    protected final void respondError(HttpServletResponse res, int status, Exception e)
            throws IOException {
        HttpResponses.error(res, status, e.getClass().getSimpleName(), e.getMessage());
    }

    /**
     * Respond with Bad Request (400).
     */
    protected final void respondBadRequest(HttpServletResponse res, String message) throws IOException {
        respondError(res, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", message);
    }

    /**
     * Respond with Unauthorized (401).
     */
    protected final void respondUnauthorized(HttpServletResponse res) throws IOException {
        respondError(res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    /**
     * Respond with Forbidden (403).
     */
    protected final void respondForbidden(HttpServletResponse res) throws IOException {
        respondError(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    /**
     * Respond with Not Found (404).
     */
    protected final void respondNotFound(HttpServletResponse res) throws IOException {
        respondError(res, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    /**
     * Respond with Internal Server Error (500).
     */
    protected final void respondInternalError(HttpServletResponse res, Throwable cause) throws IOException {
        respondError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", cause.getMessage());
    }
}
