package vn.ticketscenter.identity.controller.support;

import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Turns a failed identity request into the JSON error envelope, so servlets need no catch chains. */
public final class IdentityErrorWriter {

    private static final Logger LOGGER = Logger.getLogger(IdentityErrorWriter.class.getName());

    private IdentityErrorWriter() {
    }

    /**
     * Writes {@link IdentityException}s with their own code and status, argument validation failures as
     * {@code VALIDATION_FAILED}, and anything else as {@code SERVER_ERROR} with {@code serverErrorMessage}.
     */
    public static void write(HttpServletResponse response, RuntimeException failure, String serverErrorMessage)
            throws IOException {
        if (failure instanceof IdentityException identity) {
            IdentityError error = identity.error();
            HttpResponses.error(response, error.status(), error.code(), identity.getMessage());
        } else if (failure instanceof IllegalArgumentException invalid) {
            HttpResponses.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    IdentityError.VALIDATION_FAILED.code(), invalid.getMessage());
        } else {
            serverError(response, failure, serverErrorMessage);
        }
    }

    /** Writes a 500 {@code SERVER_ERROR}; the cause is logged, never sent to the client. */
    public static void serverError(HttpServletResponse response, RuntimeException failure, String message)
            throws IOException {
        LOGGER.log(Level.WARNING, "Identity request failed", failure);
        HttpResponses.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR", message);
    }

    /** The 503 every identity endpoint returns while persistence is not ready. */
    public static void databaseUnavailable(HttpServletResponse response) throws IOException {
        HttpResponses.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
    }
}
