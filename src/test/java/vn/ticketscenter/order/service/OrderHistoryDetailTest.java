package vn.ticketscenter.order.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.controller.OrderApiServlet;
import vn.ticketscenter.order.dto.OrderActor;
import vn.ticketscenter.order.dto.OrderDetailDto;
import vn.ticketscenter.order.dto.OrderHistoryItemDto;
import vn.ticketscenter.order.repository.OrderRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OrderHistoryDetailTest {

    private static final UUID BUYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_BUYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private FakeOrderRepo fakeRepo;
    private OrderService orderService;
    private OrderApiServlet servlet;
    private UserPrincipal buyerPrincipal;
    private UserPrincipal otherBuyerPrincipal;

    @BeforeEach
    void setUp() {
        fakeRepo = new FakeOrderRepo();
        orderService = new OrderService(fakeRepo, new vn.ticketscenter.order.cache.RedisOrderCache(() -> null, java.time.Clock.systemUTC()));
        servlet = new OrderApiServlet(orderService);

        buyerPrincipal = new UserPrincipal(BUYER_ID, PlatformRole.CUSTOMER, 1);
        otherBuyerPrincipal = new UserPrincipal(OTHER_BUYER_ID, PlatformRole.CUSTOMER, 1);
    }

    @Test
    void orderHistoryReturnsBuyerOrders() {
        OrderActor actor = new OrderActor(BUYER_ID, 1);
        fakeRepo.history.add(new OrderHistoryItemDto(
                ORDER_ID, "ORD-001", UUID.randomUUID(), "Concert 2026", "National Stadium",
                Instant.now(), new BigDecimal("200000"), BigDecimal.ZERO, new BigDecimal("200000"),
                "PAID", Instant.now(), Instant.now(), null, 2L
        ));

        OrderRepository.OrderHistoryPage page = orderService.orderHistory(actor, 1, 10, null);
        assertEquals(1, page.total());
        assertEquals(1, page.orders().size());
        assertEquals("ORD-001", page.orders().get(0).orderCode());
    }

    @Test
    void orderDetailReturnsForBuyerAndRejectsOtherBuyer() {
        OrderActor buyerActor = new OrderActor(BUYER_ID, 1);
        OrderActor otherActor = new OrderActor(OTHER_BUYER_ID, 1);

        fakeRepo.details.put(ORDER_ID, sampleOrderDetail(ORDER_ID, BUYER_ID));

        // Buyer can view
        OrderDetailDto detail = orderService.orderDetail(buyerActor, ORDER_ID);
        assertEquals(ORDER_ID, detail.id());
        assertEquals(BUYER_ID, detail.buyerId());

        // Other buyer is rejected with 403
        OrderException exForbidden = assertThrows(OrderException.class,
                () -> orderService.orderDetail(otherActor, ORDER_ID));
        assertEquals(403, exForbidden.status());
        assertEquals("FORBIDDEN", exForbidden.code());

        // Non-existent order returns 404
        UUID missingId = UUID.randomUUID();
        OrderException exNotFound = assertThrows(OrderException.class,
                () -> orderService.orderDetail(buyerActor, missingId));
        assertEquals(404, exNotFound.status());
        assertEquals("ORDER_NOT_FOUND", exNotFound.code());
    }

    @Test
    void apiMeOrdersUnauthenticatedReturns401() throws Exception {
        Result r = call(null, "GET", "/api/me/orders", Map.of());
        assertEquals(401, r.status);
        assertTrue(r.body.toString().contains("UNAUTHENTICATED"));
    }

    @Test
    void apiMeOrdersAuthenticatedReturns200() throws Exception {
        fakeRepo.history.add(new OrderHistoryItemDto(
                ORDER_ID, "ORD-123", UUID.randomUUID(), "Test Event", "Venue",
                Instant.now(), new BigDecimal("150000"), BigDecimal.ZERO, new BigDecimal("150000"),
                "PAID", Instant.now(), Instant.now(), null, 1L
        ));

        Result r = call(buyerPrincipal, "GET", "/api/me/orders", Map.of("page", "1", "size", "10"));
        assertEquals(200, r.status);
        assertTrue(r.body.toString().contains("ORD-123"));
    }

    @Test
    void apiOrderDetailReturns200ForOwnerAnd403ForNonOwner() throws Exception {
        fakeRepo.details.put(ORDER_ID, sampleOrderDetail(ORDER_ID, BUYER_ID));

        // Owner call
        Result rOwner = call(buyerPrincipal, "GET", "/api/orders/" + ORDER_ID, Map.of());
        assertEquals(200, rOwner.status);
        assertTrue(rOwner.body.toString().contains("ORD-SAMPLE"));

        // Non-owner call
        Result rOther = call(otherBuyerPrincipal, "GET", "/api/orders/" + ORDER_ID, Map.of());
        assertEquals(403, rOther.status);
        assertTrue(rOther.body.toString().contains("FORBIDDEN"));
    }

    private OrderDetailDto sampleOrderDetail(UUID orderId, UUID buyerId) {
        Instant now = Instant.now();
        return new OrderDetailDto(
                orderId, buyerId, "ORD-SAMPLE",
                UUID.randomUUID(), "Sample Concert", "Stadium", "123 Main St",
                now, now.plusSeconds(7200),
                new BigDecimal("200000"), BigDecimal.ZERO, new BigDecimal("200000"),
                "PAID", now, now, null, "SUCCESS", "TXN123", "PAY123", now,
                List.of(new OrderDetailDto.OrderItemDetail(UUID.randomUUID(), "VIP", "A-1", 1, new BigDecimal("200000"), new BigDecimal("200000"))),
                List.of(new OrderDetailDto.OrderTicketDetail(UUID.randomUUID(), "TCK-1", "ISSUED", 200000L))
        );
    }

    private Result call(UserPrincipal actor, String method, String uri, Map<String, String> params) throws Exception {
        Result result = new Result();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getAttribute" -> SecurityContext.REQUEST_USER_ATTRIBUTE.equals(args[0]) ? actor : null;
                    case "getParameter" -> params.get((String) args[0]);
                    default -> null;
                }
        );

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> {
                    if ("setStatus".equals(m.getName())) result.status = (Integer) args[0];
                    if ("sendError".equals(m.getName())) result.status = (Integer) args[0];
                    if ("getWriter".equals(m.getName())) return new PrintWriter(result.body);
                    return null;
                }
        );

        servlet.service(request, response);
        return result;
    }

    static class Result {
        int status = 200;
        StringWriter body = new StringWriter();
    }

    private static class FakeOrderRepo extends OrderRepository {
        final List<OrderHistoryItemDto> history = new ArrayList<>();
        final Map<UUID, OrderDetailDto> details = new HashMap<>();

        @Override
        public OrderHistoryPage findOrdersByBuyerId(UUID buyerId, int page, int size, String status) {
            return new OrderHistoryPage(page, size, history.size(), new ArrayList<>(history));
        }

        @Override
        public Optional<OrderDetailDto> findOrderDetail(UUID orderId) {
            return Optional.ofNullable(details.get(orderId));
        }
    }
}
