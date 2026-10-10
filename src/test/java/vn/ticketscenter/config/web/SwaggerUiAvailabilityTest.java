package vn.ticketscenter.config.web;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SwaggerUiAvailabilityTest {
    @Test
    void redirectsToExistingUiInRootAndWarContexts() throws Exception {
        assertTrue(Files.isRegularFile(Path.of("src/main/webapp/swagger/index.html")), "Redirect destination must exist in the WAR");
        for (String context : new String[]{"", "/ticketscenter"}) {
            for (String suffix : new String[]{"/swagger", "/swagger/"}) {
                Map<String,String> values = new HashMap<>();
                new OpenApiServlet().doGet(request(context, suffix), response(values, new AtomicInteger(), new ByteArrayOutputStream()));
                assertEquals(context + "/swagger/index.html", values.get("redirect"));
            }
        }
    }

    @Test
    void servesParseableSourceSpecificationWithoutDatabaseOrLogin() throws Exception {
        for (String suffix : new String[]{"/api/openapi.json", "/swagger/openapi.json"}) {
            Map<String,String> values = new HashMap<>();
            AtomicInteger status = new AtomicInteger();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            new OpenApiServlet().doGet(request("/ticketscenter", suffix), response(values, status, body));
            assertEquals(200, status.get());
            assertEquals("application/json; charset=UTF-8", values.get("content-type"));
            JSONObject spec = new JSONObject(body.toString(StandardCharsets.UTF_8));
            assertEquals("3.0.3", spec.getString("openapi"));
            assertTrue(spec.getJSONObject("paths").has("/api/auth/csrf"));
        }
    }

    private HttpServletRequest request(String context, String path) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getContextPath" -> context;
                    case "getRequestURI" -> context + path;
                    default -> null;
                });
    }

    private HttpServletResponse response(Map<String,String> values, AtomicInteger status, ByteArrayOutputStream body) {
        ServletOutputStream output = new ServletOutputStream() {
            @Override public void write(int value) { body.write(value); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) { }
        };
        return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "sendRedirect" -> { values.put("redirect", (String) args[0]); yield null; }
                    case "setContentType" -> { values.put("content-type", (String) args[0]); yield null; }
                    case "setHeader" -> { values.put((String) args[0], (String) args[1]); yield null; }
                    case "setStatus" -> { status.set((Integer) args[0]); yield null; }
                    case "getOutputStream" -> output;
                    default -> null;
                });
    }
}
