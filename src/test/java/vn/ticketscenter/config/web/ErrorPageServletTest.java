package vn.ticketscenter.config.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorPageServletTest {

    private static final class Exchange {
        final Map<String, Object> attributes = new HashMap<>();
        final StringWriter body = new StringWriter();
        String forwardedTo;
        int status;

        Exchange(Integer errorStatus, String uri, Throwable failure) {
            if (errorStatus != null) attributes.put(RequestDispatcher.ERROR_STATUS_CODE, errorStatus);
            attributes.put(RequestDispatcher.ERROR_REQUEST_URI, uri);
            if (failure != null) attributes.put(RequestDispatcher.ERROR_EXCEPTION, failure);
        }

        void run(String accept) throws Exception {
            var dispatcher = (RequestDispatcher) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{RequestDispatcher.class},
                    (proxy, method, args) -> null);
            var request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletRequest.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAttribute" -> attributes.get((String) args[0]);
                        case "setAttribute" -> attributes.put((String) args[0], args[1]);
                        case "getContextPath" -> "/ticketscenter";
                        case "getHeader" -> "Accept".equals(args[0]) ? accept : null;
                        case "getRequestDispatcher" -> { forwardedTo = (String) args[0]; yield dispatcher; }
                        default -> null;
                    });
            var response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletResponse.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setStatus" -> { status = (Integer) args[0]; yield null; }
                        case "getWriter" -> new PrintWriter(body, true);
                        default -> null;
                    });
            new ErrorPageServlet().service(request, response);
        }
    }

    @Test void missingPageShowsNotFoundView() throws Exception {
        Exchange exchange = new Exchange(404, "/ticketscenter/khong-ton-tai", null);
        exchange.run("text/html,application/xhtml+xml");
        assertEquals(404, exchange.status);
        assertEquals("/WEB-INF/views/error/not-found.jsp", exchange.forwardedTo);
        assertEquals("Không tìm thấy trang", exchange.attributes.get("errorTitle"));
    }

    @Test void unhandledExceptionShowsServerErrorViewWithoutLeakingDetails() throws Exception {
        Exchange exchange = new Exchange(500, "/ticketscenter/me/orders", new IllegalStateException("secret SQL detail"));
        exchange.run("text/html");
        assertEquals(500, exchange.status);
        assertEquals("/WEB-INF/views/error/server-error.jsp", exchange.forwardedTo);
        assertFalse(String.valueOf(exchange.attributes.get("errorMessage")).contains("secret"));
    }

    @Test void apiErrorsKeepTheJsonEnvelope() throws Exception {
        Exchange exchange = new Exchange(500, "/ticketscenter/api/orders", new IllegalStateException("secret SQL detail"));
        exchange.run("*/*");
        assertEquals(500, exchange.status);
        assertNull(exchange.forwardedTo);
        assertTrue(exchange.body.toString().contains("\"code\":\"SERVER_ERROR\""));
        assertFalse(exchange.body.toString().contains("secret"));
    }

    @Test void directAccessIsTreatedAsNotFound() throws Exception {
        Exchange exchange = new Exchange(null, null, null);
        exchange.run("text/html");
        assertEquals(404, exchange.status);
        assertEquals("/WEB-INF/views/error/not-found.jsp", exchange.forwardedTo);
    }
}
