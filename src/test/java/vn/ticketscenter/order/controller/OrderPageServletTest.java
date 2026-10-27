package vn.ticketscenter.order.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.dto.OrderDetailDto;
import vn.ticketscenter.order.dto.OrderHistoryItemDto;
import vn.ticketscenter.order.dao.OrderRepository;
import vn.ticketscenter.order.service.OrderService;
import vn.ticketscenter.order.service.OrderServiceImpl;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OrderPageServletTest {

    private static final UUID BUYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private FakeOrderRepo fakeRepo;
    private OrderService orderService;
    private OrderPageServlet servlet;
    private UserPrincipal buyerPrincipal;

    @BeforeEach
    void setUp() {
        fakeRepo = new FakeOrderRepo();
        orderService = new OrderServiceImpl(fakeRepo, new vn.ticketscenter.order.cache.RedisOrderCache(() -> null, java.time.Clock.systemUTC()));
        servlet = new OrderPageServlet(orderService);
        buyerPrincipal = new UserPrincipal(BUYER_ID, PlatformRole.CUSTOMER, 1);
    }

    @Test
    void unauthenticatedRedirectsToLogin() throws Exception {
        Result r = call(null, "GET", "/me/orders", Map.of());
        assertEquals("/auth/login?redirect=%2Fme%2Forders", r.redirectUrl);
    }

    @Test
    void anonymousOrdersPreserveApplicationContextAndFilterQuery() throws Exception {
        Result r = call(null, "GET", "/ticketscenter/me/orders", Map.of(), "/ticketscenter", "page=2&status=PAID");
        assertEquals("/ticketscenter/auth/login?redirect=%2Fticketscenter%2Fme%2Forders%3Fpage%3D2%26status%3DPAID", r.redirectUrl);
    }

    @Test
    void authenticatedMeOrdersForwardsToJsp() throws Exception {
        fakeRepo.history.add(new OrderHistoryItemDto(
                ORDER_ID, "ORD-1", UUID.randomUUID(), "Event", "Venue",
                Instant.now(), new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"),
                "PAID", Instant.now(), Instant.now(), null, 1L
        ));

        Result r = call(buyerPrincipal, "GET", "/me/orders", Map.of("page", "1", "size", "5"));
        assertEquals("/WEB-INF/views/order/my-orders.jsp", r.forwardPath);
        assertNotNull(r.attributes.get("historyPage"));
    }

    @Test
    void authenticatedOrderDetailForwardsToJsp() throws Exception {
        fakeRepo.details.put(ORDER_ID, sampleOrderDetail(ORDER_ID, BUYER_ID));

        Result r = call(buyerPrincipal, "GET", "/orders/" + ORDER_ID, Map.of());
        assertEquals("/WEB-INF/views/order/order-detail.jsp", r.forwardPath);
        assertNotNull(r.attributes.get("order"));
    }

    private OrderDetailDto sampleOrderDetail(UUID orderId, UUID buyerId) {
        Instant now = Instant.now();
        return new OrderDetailDto(
                orderId, buyerId, "ORD-SAMPLE",
                UUID.randomUUID(), "Sample Concert", "Stadium", "123 Main St",
                now, now.plusSeconds(7200),
                new BigDecimal("200000"), BigDecimal.ZERO, new BigDecimal("200000"),
                "PAID", now, now, null, "SUCCESS", "TXN123", "PAY123", now,
                List.of(), List.of()
        );
    }

    private Result call(UserPrincipal actor, String method, String uri, Map<String, String> params) throws Exception {
        return call(actor, method, uri, params, "", null);
    }

    private Result call(UserPrincipal actor, String method, String uri, Map<String, String> params, String context, String query) throws Exception {
        Result result = new Result();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> context;
                    case "getQueryString" -> query;
                    case "getAttribute" -> SecurityContext.REQUEST_USER_ATTRIBUTE.equals(args[0]) ? actor : result.attributes.get(args[0]);
                    case "setAttribute" -> {
                        result.attributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getParameter" -> params.get((String) args[0]);
                    case "getRequestDispatcher" -> (RequestDispatcher) Proxy.newProxyInstance(
                            RequestDispatcher.class.getClassLoader(),
                            new Class<?>[]{RequestDispatcher.class},
                            (p, dm, dargs) -> {
                                if ("forward".equals(dm.getName())) {
                                    result.forwardPath = (String) args[0];
                                }
                                return null;
                            }
                    );
                    default -> null;
                }
        );

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    if ("sendRedirect".equals(m.getName())) result.redirectUrl = (String) args[0];
                    if ("sendError".equals(m.getName())) result.status = (Integer) args[0];
                    return null;
                }
        );

        servlet.doGet(request, response);
        return result;
    }

    static class Result {
        int status = 200;
        String redirectUrl;
        String forwardPath;
        Map<String, Object> attributes = new HashMap<>();
    }

    private static class FakeOrderRepo extends OrderRepository {
        final List<OrderHistoryItemDto> history = new ArrayList<>();
        final Map<UUID, OrderDetailDto> details = new HashMap<>();

        @Override
        public OrderHistoryPage findOrdersByBuyerId(UUID buyerId, int page, int size, String status) {
            return new OrderHistoryPage(page, size, history.size(), new ArrayList<>(history));
        }

        @Override
        public OrderHistoryPage findOrdersByBuyerId(UUID buyerId, int authVersion, int page, int size, String status) {
            return findOrdersByBuyerId(buyerId, page, size, status);
        }

        @Override
        public Optional<OrderDetailDto> findOrderDetail(UUID orderId) {
            return Optional.ofNullable(details.get(orderId));
        }

        @Override
        public Optional<OrderDetailDto> findOrderDetail(UUID orderId, UUID buyerId, int authVersion) {
            return findOrderDetail(orderId);
        }
    }
}
