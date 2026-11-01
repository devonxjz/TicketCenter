package vn.ticketscenter.order.controller;

import vn.ticketscenter.config.web.TestServlets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.cache.RedisOrderCache;
import vn.ticketscenter.order.dao.OrderRepository;
import vn.ticketscenter.order.service.OrderService;
import vn.ticketscenter.order.service.OrderServiceImpl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OrderApiErrorTest {
    @Test
    void unexpectedRepositoryFailureDoesNotExposeInternalDetails() throws Exception {
        String privateDetail = "jdbc:sqlserver://private-host;password=internal-test-secret";
        var repository = new OrderRepository() {
            @Override public OrderHistoryPage findOrdersByBuyerId(UUID buyer, int version, int page, int size, String status) {
                throw new IllegalStateException(privateDetail);
            }
        };
        var servlet = TestServlets.init(new OrderApiServlet(), new OrderServiceImpl(repository, new RedisOrderCache(() -> null, Clock.systemUTC())));
        var actor = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 1);
        var request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getRequestURI" -> "/api/me/orders";
                    case "getContextPath" -> "";
                    case "getAttribute" -> SecurityContext.REQUEST_USER_ATTRIBUTE.equals(args[0]) ? actor : null;
                    default -> null;
                });
        var body = new StringWriter();
        var status = new AtomicInteger(200);
        var response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setStatus")) status.set((Integer) args[0]);
                    return method.getName().equals("getWriter") ? new PrintWriter(body) : null;
                });
        servlet.service(request, response);
        assertEquals(500, status.get());
        assertTrue(body.toString().contains("INTERNAL_ERROR"));
        assertTrue(body.toString().contains("correlationId"));
        assertFalse(body.toString().contains(privateDetail));
        assertFalse(body.toString().contains("internal-test-secret"));
    }
}
