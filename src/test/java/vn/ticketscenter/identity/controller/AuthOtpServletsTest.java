package vn.ticketscenter.identity.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.dto.ForgotPasswordRequestDto;
import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyResponseDto;
import vn.ticketscenter.identity.dto.PasswordResetRequestDto;
import vn.ticketscenter.identity.integration.mail.FakeEmailSender;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.repository.OtpRepository;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.OtpHasher;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.PasswordResetService;
import vn.ticketscenter.identity.service.ResetTokenCodec;
import vn.ticketscenter.identity.service.UserSessionCache;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AuthOtpServletsTest {

    private Map<String, Object> sessionAttributes;
    private Map<String, Object> requestAttributes;
    private Map<String, String> requestParams;
    private StringWriter responseBuffer;
    private AtomicInteger responseStatus;

    @BeforeEach
    void setUp() {
        sessionAttributes = new HashMap<>();
        requestAttributes = new HashMap<>();
        requestParams = new HashMap<>();
        responseBuffer = new StringWriter();
        responseStatus = new AtomicInteger(200);
    }

    private HttpServletRequest createMockRequest(String method, String uri, String jsonBody) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getParameter" -> requestParams.get((String) args[0]);
                    case "getAttribute" -> requestAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        requestAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getSession" -> createMockSession();
                    case "getContentType" -> jsonBody != null ? "application/json;charset=UTF-8" : "application/x-www-form-urlencoded";
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

    @Test
    @DisplayName("OtpSendServlet gửi OTP thành công trả về HTTP 200 sent: true")
    void testOtpSendServletSuccess() throws Exception {
        OtpService mockOtpService = new OtpService(
                new TransactionManager(Map.of()), new OtpRepository(), new UserRepository(),
                new OtpHasher("secret"), new ResetTokenCodec("secret"), new FakeEmailSender()) {
            @Override
            public boolean sendOtp(OtpSendRequestDto request) {
                return true;
            }
        };

        OtpSendServlet servlet = new OtpSendServlet(mockOtpService);
        String json = "{\"email\":\"user@test.vn\",\"purpose\":\"VERIFY_EMAIL\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/auth/otp/send", json);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("\"sent\":true"));
    }

    @Test
    @DisplayName("OtpSendServlet trả về 429 khi cooldown đang kích hoạt")
    void testOtpSendServletCooldown() throws Exception {
        OtpService mockOtpService = new OtpService(
                new TransactionManager(Map.of()), new OtpRepository(), new UserRepository(),
                new OtpHasher("secret"), new ResetTokenCodec("secret"), new FakeEmailSender()) {
            @Override
            public boolean sendOtp(OtpSendRequestDto request) {
                throw new IdentityException(IdentityError.COOLDOWN_ACTIVE, "COOLDOWN_ACTIVE: Vui lòng đợi 45 giây");
            }
        };

        OtpSendServlet servlet = new OtpSendServlet(mockOtpService);
        String json = "{\"email\":\"user@test.vn\",\"purpose\":\"VERIFY_EMAIL\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/auth/otp/send", json);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(429, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("COOLDOWN_ACTIVE"));
    }

    @Test
    @DisplayName("OtpVerifyServlet xác thực thành công cho RESET_PASSWORD trả về resetToken")
    void testOtpVerifyServletSuccess() throws Exception {
        OtpService mockOtpService = new OtpService(
                new TransactionManager(Map.of()), new OtpRepository(), new UserRepository(),
                new OtpHasher("secret"), new ResetTokenCodec("secret"), new FakeEmailSender()) {
            @Override
            public OtpVerifyResponseDto verifyOtp(OtpVerifyRequestDto request) {
                return OtpVerifyResponseDto.forPasswordReset("Xác minh thành công", "mock.reset.token");
            }
        };

        OtpVerifyServlet servlet = new OtpVerifyServlet(mockOtpService);
        String json = "{\"email\":\"user@test.vn\",\"purpose\":\"RESET_PASSWORD\",\"code\":\"123456\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/auth/otp/verify", json);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("\"resetToken\":\"mock.reset.token\""));
    }

    @Test
    @DisplayName("ForgotPasswordServlet trả về HTTP 200 với thông điệp trung tính")
    void testForgotPasswordServletSuccess() throws Exception {
        PasswordResetService mockService = new PasswordResetService(
                new TransactionManager(Map.of()), new UserRepository(),
                new OtpService(new TransactionManager(Map.of()), new OtpRepository(), new UserRepository(), new OtpHasher("secret"), new ResetTokenCodec("secret"), new FakeEmailSender()),
                new ResetTokenCodec("secret"), new PasswordHasher(), UserSessionCache.disabled()) {
            @Override
            public String forgotPassword(ForgotPasswordRequestDto request) {
                return PasswordResetService.NEUTRAL_FORGOT_PASSWORD_MESSAGE;
            }
        };

        ForgotPasswordServlet servlet = new ForgotPasswordServlet(mockService);
        String json = "{\"email\":\"user@test.vn\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/auth/password/forgot", json);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("Nếu địa chỉ email tồn tại"));
    }

    @Test
    @DisplayName("PasswordResetServlet đổi mật khẩu thành công trả về reset: true")
    void testPasswordResetServletSuccess() throws Exception {
        PasswordResetService mockService = new PasswordResetService(
                new TransactionManager(Map.of()), new UserRepository(),
                new OtpService(new TransactionManager(Map.of()), new OtpRepository(), new UserRepository(), new OtpHasher("secret"), new ResetTokenCodec("secret"), new FakeEmailSender()),
                new ResetTokenCodec("secret"), new PasswordHasher(), UserSessionCache.disabled()) {
            @Override
            public boolean resetPassword(PasswordResetRequestDto request) {
                return true;
            }
        };

        PasswordResetServlet servlet = new PasswordResetServlet(mockService);
        String json = "{\"token\":\"valid.token.sig\",\"newPassword\":\"BrandNewPass123!\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/auth/password/reset", json);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("\"reset\":true"));
    }

    @Test
    @DisplayName("doGet chuyển tiếp đến đúng JSP cho các Servlet giao diện OTP và Reset Password")
    void testServletsDoGetForwardToJsp() throws Exception {
        OtpVerifyServlet verifyServlet = new OtpVerifyServlet();
        HttpServletRequest req1 = createMockRequest("GET", "/auth/verify-email", null);
        HttpServletResponse resp1 = createMockResponse();
        verifyServlet.doGet(req1, resp1);
        assertEquals("/WEB-INF/views/auth/verify-email.jsp", requestAttributes.get("FORWARDED_TO"));

        ForgotPasswordServlet forgotServlet = new ForgotPasswordServlet();
        HttpServletRequest req2 = createMockRequest("GET", "/auth/forgot-password", null);
        HttpServletResponse resp2 = createMockResponse();
        forgotServlet.doGet(req2, resp2);
        assertEquals("/WEB-INF/views/auth/forgot-password.jsp", requestAttributes.get("FORWARDED_TO"));

        PasswordResetServlet resetServlet = new PasswordResetServlet();
        requestParams.put("token", "sample-token-123");
        HttpServletRequest req3 = createMockRequest("GET", "/auth/reset-password", null);
        HttpServletResponse resp3 = createMockResponse();
        resetServlet.doGet(req3, resp3);
        assertEquals("/WEB-INF/views/auth/reset-password.jsp", requestAttributes.get("FORWARDED_TO"));
        assertEquals("sample-token-123", requestAttributes.get("token"));
    }
}
