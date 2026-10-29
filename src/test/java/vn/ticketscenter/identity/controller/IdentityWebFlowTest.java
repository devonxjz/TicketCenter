package vn.ticketscenter.identity.controller;

import vn.ticketscenter.config.web.TestServlets;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.dto.CurrentUserDto;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.identity.security.CsrfFilter;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.SessionValidationFilter;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.LoginService;
import vn.ticketscenter.identity.service.LoginServiceImpl;
import vn.ticketscenter.identity.service.ProfileService;
import vn.ticketscenter.identity.service.ProfileServiceImpl;
import vn.ticketscenter.identity.service.RegistrationService;
import vn.ticketscenter.identity.service.RegistrationServiceImpl;
import vn.ticketscenter.identity.service.SessionValidator;
import vn.ticketscenter.identity.service.PasswordHasher;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class IdentityWebFlowTest {

    private Map<String, Object> sessionAttributes;
    private Map<String, Object> requestAttributes;
    private Map<String, String> requestHeaders;
    private Map<String, String> requestParams;
    private Map<String, String> responseHeaders;
    private java.io.StringWriter responseBuffer;
    private AtomicInteger responseStatus;
    private AtomicBoolean chainExecuted;

    @BeforeEach
    void setUp() {
        sessionAttributes = new HashMap<>();
        requestAttributes = new HashMap<>();
        requestHeaders = new HashMap<>();
        requestParams = new HashMap<>();
        responseHeaders = new HashMap<>();
        responseBuffer = new java.io.StringWriter();
        responseStatus = new AtomicInteger(200);
        chainExecuted = new AtomicBoolean(false);
    }

    private HttpSession createMockSession() {
        return (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> switch (method.getName()) {
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
                    case "getId" -> "mock-session-id-12345";
                    default -> null;
                }
        );
    }

    private HttpServletRequest createMockRequest(String method, String uri, boolean hasSession) {
        return createMockRequest(method, uri, hasSession, null);
    }

    private HttpServletRequest createMockRequest(String method, String uri, boolean hasSession, String jsonBody) {
        HttpSession session = hasSession ? createMockSession() : null;
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getHeader" -> requestHeaders.get((String) args[0]);
                    case "getParameter" -> requestParams.get((String) args[0]);
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
                    case "changeSessionId" -> "new-session-id-" + UUID.randomUUID();
                    case "getContentType" -> jsonBody != null ? "application/json" : "application/x-www-form-urlencoded";
                    case "getReader" -> new BufferedReader(new StringReader(jsonBody != null ? jsonBody : ""));
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
                    case "getHeader" -> responseHeaders.get((String) args[0]);
                    case "getWriter" -> new PrintWriter(responseBuffer) {
                        @Override
                        public void write(String s) {
                            responseBuffer.write(s);
                        }
                    };
                    case "setContentType", "setCharacterEncoding" -> null;
                    default -> null;
                }
        );
    }

    private FilterChain createMockChain() {
        return (req, resp) -> chainExecuted.set(true);
    }

    @Test
    @DisplayName("CsrfServlet phát hành token gắn session và trả về JSON")
    void testCsrfServletIssuesToken() throws Exception {
        CsrfServlet servlet = new CsrfServlet();
        HttpServletRequest req = createMockRequest("GET", "/api/auth/csrf", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(200, responseStatus.get());
        String body = responseBuffer.toString();
        assertTrue(body.contains("\"token\""), "Response must contain CSRF token JSON");
        assertNotNull(sessionAttributes.get(SecurityContext.CSRF_TOKEN_ATTRIBUTE));
    }

    @Test
    @DisplayName("CsrfFilter chặn yêu cầu POST thiếu mã CSRF với HTTP 403")
    void testCsrfFilterBlocksMissingToken() throws Exception {
        CsrfFilter filter = new CsrfFilter();
        HttpServletRequest req = createMockRequest("POST", "/api/tickets/buy", true);
        HttpServletResponse resp = createMockResponse();

        filter.doFilter(req, resp, createMockChain());

        assertFalse(chainExecuted.get(), "FilterChain must not be executed");
        assertEquals(403, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("CSRF_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("CsrfFilter cho phép yêu cầu POST khi có header X-CSRF-Token hợp lệ")
    void testCsrfFilterAllowsValidToken() throws Exception {
        CsrfFilter filter = new CsrfFilter();
        String validToken = UUID.randomUUID().toString();
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, validToken);
        requestHeaders.put("X-CSRF-Token", validToken);

        HttpServletRequest req = createMockRequest("POST", "/api/tickets/buy", true);
        HttpServletResponse resp = createMockResponse();

        filter.doFilter(req, resp, createMockChain());

        assertTrue(chainExecuted.get(), "FilterChain must be executed when token is valid");
        assertEquals(200, responseStatus.get());
    }

    @Test
    @DisplayName("CsrfFilter cho phép đăng xuất lặp (idempotent logout) khi phiên đã hủy")
    void testCsrfFilterAllowsIdempotentLogoutWithoutSession() throws Exception {
        CsrfFilter filter = new CsrfFilter();
        HttpServletRequest req = createMockRequest("POST", "/api/auth/logout", false);
        HttpServletResponse resp = createMockResponse();

        filter.doFilter(req, resp, createMockChain());

        assertTrue(chainExecuted.get(), "FilterChain must proceed for idempotent logout when session is null");
    }

    @Test
    @DisplayName("CsrfFilter bỏ qua kiểm tra CSRF đối với webhook thanh toán payOS")
    void testCsrfFilterBypassesPayosWebhook() throws Exception {
        CsrfFilter filter = new CsrfFilter();
        HttpServletRequest req = createMockRequest("POST", "/api/payments/payos/webhook", false);
        HttpServletResponse resp = createMockResponse();

        filter.doFilter(req, resp, createMockChain());

        assertTrue(chainExecuted.get(), "FilterChain must proceed for payOS webhook");
    }

    @Test
    @DisplayName("CsrfFilter cho phép các API xác thực OTP và đặt lại mật khẩu công khai đi qua không cần CSRF token")
    void testCsrfFilterAllowsPublicAuthApis() throws Exception {
        CsrfFilter filter = new CsrfFilter();
        java.util.List<String> publicApis = java.util.List.of(
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/otp/send",
                "/api/auth/otp/verify",
                "/api/auth/password/forgot",
                "/api/auth/password/reset"
        );

        for (String api : publicApis) {
            chainExecuted.set(false);
            responseStatus.set(200);
            HttpServletRequest req = createMockRequest("POST", api, false);
            HttpServletResponse resp = createMockResponse();

            filter.doFilter(req, resp, createMockChain());

            assertTrue(chainExecuted.get(), "FilterChain must proceed for public auth API: " + api);
            assertNotEquals(403, responseStatus.get(), "Must not return 403 Forbidden for: " + api);
        }
    }

    @Test
    @DisplayName("SessionValidationFilter từ chối 401 khi authVersion không khớp")
    void testSessionValidationFilterStaleAuthVersion() throws Exception {
        UUID actorId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 0);
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, principal);

        SessionValidator mockService = new SessionValidator(
                new TransactionManager(Map.of()), new UserRepository()) {
            @Override
            public boolean isSessionValid(UUID id, int version) {
                return false; // Phiên hết hạn / authVersion cũ
            }
        };

        SessionValidationFilter filter = new SessionValidationFilter();
        ServletContext context = TestServlets.context(mockService);
        HttpServletRequest req =
                new HttpServletRequestWrapper(createMockRequest("GET", "/api/me", true)) {
                    @Override
                    public ServletContext getServletContext() {
                        return context;
                    }
                };
        HttpServletResponse resp = createMockResponse();

        filter.doFilter(req, resp, createMockChain());

        assertFalse(chainExecuted.get(), "FilterChain must not proceed for invalid session");
        assertEquals(401, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("SESSION_EXPIRED"));
        assertTrue(sessionAttributes.isEmpty(), "Stale session must be cleared");
    }

    @Test
    @DisplayName("MeServlet từ chối 401 khi chưa đăng nhập")
    void testMeServletUnauthenticated() throws Exception {
        MeServlet servlet = new MeServlet();
        HttpServletRequest req = createMockRequest("GET", "/api/me", false);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(401, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("MeServlet trả về thông tin người dùng hợp lệ khi đã đăng nhập")
    void testMeServletAuthenticated() throws Exception {
        UUID actorId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1);
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, principal);

        ProfileService mockService = new ProfileServiceImpl(
                new TransactionManager(Map.of()), new UserRepository()) {
            @Override
            public CurrentUserDto getCurrentUser(UUID id) {
                return new CurrentUserDto(actorId, "user@test.vn", "testuser", "Nguyen Van A", PlatformRole.CUSTOMER, false);
            }
        };

        MeServlet servlet = TestServlets.init(new MeServlet(), mockService);
        HttpServletRequest req = createMockRequest("GET", "/api/me", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(200, responseStatus.get());
        String body = responseBuffer.toString();
        assertTrue(body.contains(actorId.toString()));
        assertTrue(body.contains("user@test.vn"));
        assertTrue(body.contains("testuser"));
        assertFalse(body.contains("password"));
    }

    @Test
    @DisplayName("LogoutServlet xóa toàn bộ phiên và trả về loggedOut: true")
    void testLogoutServletSuccess() throws Exception {
        sessionAttributes.put("TC_CURRENT_USER", "dummy-user");
        sessionAttributes.put("TC_CSRF_TOKEN", "dummy-token");
        requestHeaders.put("X-CSRF-Token", "dummy-token");

        LogoutServlet servlet = new LogoutServlet();
        HttpServletRequest req = createMockRequest("POST", "/api/auth/logout", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("\"loggedOut\":true"));
        assertTrue(sessionAttributes.isEmpty(), "Session attributes must be cleared after logout");
    }

    @Test
    void logoutPostWithoutCsrfPreservesAnExistingSessionEvenOnTheFormRoute() throws Exception {
        sessionAttributes.put("TC_CURRENT_USER", "dummy-user");
        sessionAttributes.put("TC_CSRF_TOKEN", "dummy-token");
        LogoutServlet servlet=new LogoutServlet();
        servlet.service(createMockRequest("POST","/auth/logout",true),createMockResponse());
        assertEquals(403,responseStatus.get());
        assertEquals("dummy-user",sessionAttributes.get("TC_CURRENT_USER"));
    }

    @Test
    @DisplayName("LoginServlet doGet chuyển tiếp đến JSP login")
    void testLoginServletDoGetForwardsToJsp() throws Exception {
        LoginServlet servlet = new LoginServlet();
        HttpServletRequest req = createMockRequest("GET", "/auth/login", false);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals("/WEB-INF/views/auth/login.jsp", requestAttributes.get("FORWARDED_TO"));
        assertNotNull(requestAttributes.get("csrfToken"));
    }

    @Test
    @DisplayName("RegisterServlet doGet chuyển tiếp đến JSP register")
    void testRegisterServletDoGetForwardsToJsp() throws Exception {
        RegisterServlet servlet = new RegisterServlet();
        HttpServletRequest req = createMockRequest("GET", "/auth/register", false);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals("/WEB-INF/views/auth/register.jsp", requestAttributes.get("FORWARDED_TO"));
        assertNotNull(requestAttributes.get("csrfToken"));
    }

    @Test
    @DisplayName("LoginServlet thành công thiết lập phiên và trả về header X-CSRF-Token")
    void testLoginServletSuccessSetsCsrfHeader() throws Exception {
        UUID actorId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 0);

        LoginService mockService = new LoginServiceImpl(
                new TransactionManager(Map.of()), new UserRepository(), new PasswordHasher()) {
            @Override
            public UserPrincipal login(LoginRequestDto request) {
                return principal;
            }
        };

        LoginServlet servlet = TestServlets.init(new LoginServlet(), mockService);
        requestParams.put("identifier", "user@test.vn");
        requestParams.put("password", "SecurePassword123!");

        HttpServletRequest req = createMockRequest("POST", "/api/auth/login", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("\"authenticated\":true"));
        assertTrue(responseBuffer.toString().contains("\"redirect\":\"/\""));
        assertTrue(responseBuffer.toString().contains("\"role\":\"CUSTOMER\""));
        assertNotNull(responseHeaders.get("X-CSRF-Token"), "Response must expose X-CSRF-Token header");
        assertEquals(principal, sessionAttributes.get(SecurityContext.SESSION_USER_ATTRIBUTE));
    }

    @Test
    @DisplayName("LoginServlet: Admin đăng nhập điều hướng đến trang mua vé")
    void testLoginServletAdminRedirectsToCatalog() throws Exception {
        UUID actorId = UUID.randomUUID();
        UserPrincipal admin = new UserPrincipal(actorId, PlatformRole.ADMIN, 0);

        LoginService mockService = new LoginServiceImpl(
                new TransactionManager(Map.of()), new UserRepository(), new PasswordHasher()) {
            @Override
            public UserPrincipal login(LoginRequestDto request) {
                return admin;
            }
        };

        LoginServlet servlet = TestServlets.init(new LoginServlet(), mockService);
        requestParams.put("identifier", "admin@test.vn");
        requestParams.put("password", "Pass12345678!");

        HttpServletRequest req = createMockRequest("POST", "/api/auth/login", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("\"redirect\":\"/\""));
        assertTrue(responseBuffer.toString().contains("\"role\":\"ADMIN\""));
    }

    @Test
    @DisplayName("LoginServlet: Staff đăng nhập điều hướng đến trang mua vé")
    void testLoginServletStaffRedirectsToCatalog() throws Exception {
        UUID actorId = UUID.randomUUID();
        UserPrincipal staff = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 0, vn.ticketscenter.identity.model.OrganizationRole.CHECK_IN_STAFF);

        LoginService mockService = new LoginServiceImpl(
                new TransactionManager(Map.of()), new UserRepository(), new PasswordHasher()) {
            @Override
            public UserPrincipal login(LoginRequestDto request) {
                return staff;
            }
        };

        LoginServlet servlet = TestServlets.init(new LoginServlet(), mockService);
        requestParams.put("identifier", "staff@test.vn");
        requestParams.put("password", "Pass12345678!");

        HttpServletRequest req = createMockRequest("POST", "/api/auth/login", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("\"redirect\":\"/\""));
        assertTrue(responseBuffer.toString().contains("\"role\":\"STAFF\""));
    }

    @Test
    @DisplayName("LoginServlet: Manager đăng nhập điều hướng đến trang mua vé")
    void testLoginServletManagerRedirectsToCatalog() throws Exception {
        UUID actorId = UUID.randomUUID();
        UserPrincipal manager = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 0, vn.ticketscenter.identity.model.OrganizationRole.MANAGER);

        LoginService mockService = new LoginServiceImpl(
                new TransactionManager(Map.of()), new UserRepository(), new PasswordHasher()) {
            @Override
            public UserPrincipal login(LoginRequestDto request) {
                return manager;
            }
        };

        LoginServlet servlet = TestServlets.init(new LoginServlet(), mockService);
        requestParams.put("identifier", "manager@test.vn");
        requestParams.put("password", "Pass12345678!");

        HttpServletRequest req = createMockRequest("POST", "/api/auth/login", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("\"redirect\":\"/\""));
        assertTrue(responseBuffer.toString().contains("\"role\":\"MANAGER\""));
    }

    @Test
    @DisplayName("LoginServlet trả về 401 ACCOUNT_DISABLED khi tài khoản bị khóa")
    void testLoginServletAccountDisabled() throws Exception {
        LoginService mockService = new LoginServiceImpl(
                new TransactionManager(Map.of()), new UserRepository(), new PasswordHasher()) {
            @Override
            public UserPrincipal login(LoginRequestDto request) {
                throw new IdentityException(IdentityError.ACCOUNT_DISABLED, "Tài khoản của bạn đã bị vô hiệu hóa");
            }
        };

        LoginServlet servlet = TestServlets.init(new LoginServlet(), mockService);
        requestParams.put("identifier", "disabled@test.vn");
        requestParams.put("password", "SecurePassword123!");

        HttpServletRequest req = createMockRequest("POST", "/api/auth/login", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(401, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("RegisterServlet trả về 200 registered: true khi thông tin hợp lệ")
    void testRegisterServletSuccess() throws Exception {
        RegistrationService mockService = new RegistrationServiceImpl(
                new TransactionManager(Map.of()), new UserRepository(), new PasswordHasher()) {
            @Override
            public boolean register(vn.ticketscenter.identity.dto.RegisterRequestDto request) {
                return true;
            }
        };

        RegisterServlet servlet = TestServlets.init(new RegisterServlet(), mockService);
        requestParams.put("userName", "newuser");
        requestParams.put("fullName", "Nguyen Van New");
        requestParams.put("email", "newuser@test.vn");
        requestParams.put("password", "SecurePassword123!");

        HttpServletRequest req = createMockRequest("POST", "/api/auth/register", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("\"registered\":true"));
    }

    @Test
    @DisplayName("RegisterServlet trả về 400 VALIDATION_FAILED khi dữ liệu không hợp lệ")
    void testRegisterServletValidationFailed() throws Exception {
        RegistrationService mockService = new RegistrationServiceImpl(
                new TransactionManager(Map.of()), new UserRepository(), new PasswordHasher()) {
            @Override
            public boolean register(vn.ticketscenter.identity.dto.RegisterRequestDto request) {
                throw new IllegalArgumentException("Mật khẩu phải có độ dài từ 12 đến 256 ký tự");
            }
        };

        RegisterServlet servlet = TestServlets.init(new RegisterServlet(), mockService);
        requestParams.put("userName", "newuser");
        requestParams.put("fullName", "Nguyen Van New");
        requestParams.put("email", "newuser@test.vn");
        requestParams.put("password", "short");

        HttpServletRequest req = createMockRequest("POST", "/api/auth/register", true);
        HttpServletResponse resp = createMockResponse();

        servlet.service(req, resp);

        assertEquals(400, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("VALIDATION_FAILED"));
    }
}
