package vn.ticketscenter.config.web;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

public final class HttpResponses {

    private HttpResponses() {
    }

    public static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"correlationId\":\"" + UUID.randomUUID() + "\"}}");
    }

    public static void data(HttpServletResponse response, String jsonObject) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"data\":" + jsonObject + "}");
    }

    public static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            escaped.append(switch (current) {
                case '"' -> "\\\"";
                case '\\' -> "\\\\";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                case '\t' -> "\\t";
                default -> current < 0x20 ? String.format("\\u%04x", (int) current) : current;
            });
        }
        return escaped.append('"').toString();
    }
}
