package vn.ticketscenter.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.controller.LogoutServlet;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventNavigationWebFlowTest - Điều hướng trang chủ và xác thực xem sự kiện")
class EventNavigationWebFlowTest {

    private final Map<String, Object> sessionAttributes = new HashMap<>();
    private final Map<String, Object> requestAttributes = new HashMap<>();
    private final Map<String, String> requestHeaders = new HashMap<>();
    private final Map<String, String> responseHeaders = new HashMap<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void setUp() {
        sessionAttributes.clear();
        requestAttributes.clear();
        requestHeaders.clear();
        responseHeaders.clear();
        responseStatus.set(200);
    }

    private HttpSession createMockSession() {
        return (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getAttribute" -> sessionAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        sessionAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "removeAttribute" -> {
                        sessionAttributes.remove((String) args[0]);
                        yield null;
                    }
                    case "invalidate" -> {
                        sessionAttributes.clear();
                        yield null;
                    }
                    case "getId" -> "mock-session-id";
                    default -> null;
                }
        );
    }

    private HttpServletRequest createMockRequest(String method, String uri, boolean hasSession) {
        HttpSession session = hasSession ? createMockSession() : null;
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getQueryString" -> null;
                    case "getHeader" -> requestHeaders.get((String) args[0]);
                    case "getAttribute" -> requestAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        requestAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "removeAttribute" -> {
                        requestAttributes.remove((String) args[0]);
                        yield null;
                    }
                    case "getSession" -> {
                        if (args != null && args.length == 1) {
                            boolean create = (Boolean) args[0];
                            if (!create) yield session;
                        }
                        yield session != null ? session : createMockSession();
                    }
                    case "getRequestDispatcher" -> (jakarta.servlet.RequestDispatcher) Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{jakarta.servlet.RequestDispatcher.class},
                            (p, dm, dargs) -> {
                                requestAttributes.put("FORWARDED_TO", args[0]);
                                return null;
                            }
                    );
                    default -> null;
                }
        );
    }

    private HttpServletResponse createMockResponse() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "setStatus" -> {
                        responseStatus.set((Integer) args[0]);
                        yield null;
                    }
                    case "getStatus" -> responseStatus.get();
                    case "setHeader" -> {
                        responseHeaders.put((String) args[0], (String) args[1]);
                        yield null;
                    }
                    case "sendRedirect" -> {
                        responseStatus.set(302);
                        responseHeaders.put("Location", (String) args[0]);
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("HomeServlet chuyển tiếp yêu cầu đến trang chủ index.jsp")
    void testHomeServletForwardsToIndexJsp() throws Exception {
        HomeServlet servlet = new HomeServlet();
        HttpServletRequest req = createMockRequest("GET", "/", false);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals("/WEB-INF/views/public/index.jsp", requestAttributes.get("FORWARDED_TO"));
    }

    @Test
    @DisplayName("EventDetailServlet cho phép khách xem trang chi tiết công khai")
    void testEventDetailIsPublicWhenUnauthenticated() throws Exception {
        EventDetailServlet servlet = new EventDetailServlet();
        HttpServletRequest req = createMockRequest("GET", "/event-detail", false);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(200, responseStatus.get());
        assertEquals("/WEB-INF/views/public/event-detail.jsp", requestAttributes.get("FORWARDED_TO"));
        assertEquals("no-store", responseHeaders.get("Cache-Control"));
    }

    @Test
    @DisplayName("EventDetailServlet hiển thị event-detail.jsp khi đã đăng nhập")
    void testEventDetailForwardsToJspWhenAuthenticated() throws Exception {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 1);
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, principal);

        EventDetailServlet servlet = new EventDetailServlet();
        HttpServletRequest req = createMockRequest("GET", "/event-detail", true);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals("/WEB-INF/views/public/event-detail.jsp", requestAttributes.get("FORWARDED_TO"));
    }

    @Test
    @DisplayName("GET /auth/logout chuyển hướng nhưng không thay đổi phiên")
    void testLogoutServletGetRedirectsHome() throws Exception {
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, "dummy");
        LogoutServlet servlet = new LogoutServlet();
        HttpServletRequest req = createMockRequest("GET", "/auth/logout", true);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(302, responseStatus.get());
        assertEquals("/", responseHeaders.get("Location"));
        assertEquals("dummy",sessionAttributes.get(SecurityContext.SESSION_USER_ATTRIBUTE), "GET must not invalidate the session");
    }
}
