package vn.ticketscenter.event.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.storage.LocalImageStorage;
import java.io.IOException;
import java.nio.file.*;

@WebServlet(urlPatterns = "/assets/event-images/*")
public final class LocalEventImageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        serve(request, response, true);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        serve(request, response, false);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response, boolean body)
            throws IOException {
        String path = request.getPathInfo();
        var image =
                LocalImageStorage.fromConfiguration().file(path == null ? null : path.substring(1));
        if (image.isEmpty()) {
            response.sendError(404);
            return;
        }
        Path file = image.get();
        String filename = file.getFileName().toString();
        String etag = "\"" + filename + "\"";
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        response.setHeader("ETag", etag);
        if (etag.equals(request.getHeader("If-None-Match"))) {
            response.setStatus(304);
            return;
        }
        try (var input = Files.newInputStream(file)) {
            int signature = input.read();
            response.setContentType(
                    signature == 0x89
                            ? "image/png"
                            : signature == 0xff ? "image/jpeg" : "image/webp");
        }
        response.setContentLengthLong(Files.size(file));
        if (body) Files.copy(file, response.getOutputStream());
    }
}
