package vn.ticketscenter.order.dao;

import org.json.JSONArray;
import org.json.JSONObject;
import vn.ticketscenter.config.persistence.SqlCall;
import vn.ticketscenter.order.dto.*;
import vn.ticketscenter.order.model.OrderStatus;
import vn.ticketscenter.order.service.OrderException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static vn.ticketscenter.config.persistence.SqlCall.instant;
import static vn.ticketscenter.config.persistence.SqlCall.uuid;

public class OrderRepository {
    private static final SqlCall.ErrorTable UNAVAILABLE =
            SqlCall.ErrorTable.of().fallback((code, message) -> OrderException.unavailable());

    /** THROW numbers of the order procedures. */
    private static final SqlCall.ErrorTable ERRORS =
            SqlCall.ErrorTable.of()
                    .on(51301, m -> error(401, "UNAUTHENTICATED", "A current session is required."))
                    .on(new int[] {51303, 51403}, m -> error(403, "FORBIDDEN", "A current verified owner is required."))
                    .on(51400, m -> error(400, "HOLD_INVALID", "An active unexpired Hold is required."))
                    .on(51404, m -> error(404, "ORDER_NOT_FOUND", "Order was not found."))
                    .on(51408, m -> error(409, "IDEMPOTENCY_CONFLICT", "Use a new key for a changed request."))
                    .on(51406, m -> error(409, "ORDER_CONFLICT", "Refresh checkout before changing the Order."))
                    .on(51407, m -> error(409, "PAYMENT_UNRESOLVED", "Payment must be resolved before changing the Coupon."))
                    .on(51405, m -> error(400, couponCode(m), "Coupon is not eligible for this Order."));

    private static final SqlCall.ErrorTable ACCESS_ERRORS =
            SqlCall.ErrorTable.of()
                    .on(51403, m -> error(403, "FORBIDDEN", "You do not own this order."))
                    .on(51404, m -> error(404, "ORDER_NOT_FOUND", "Order was not found."))
                    .fallback((code, message) -> OrderException.unavailable());

    private static OrderException error(int status, String code, String message) {
        return new OrderException(status, code, message);
    }

    private final DataSource buyer;

    public OrderRepository(DataSource buyer) {
        this.buyer = Objects.requireNonNull(buyer);
    }

    public OrderRepository() {
        this.buyer = null;
    }

    public OrderMutationReceipt mutate(OrderActor actor, OrderCommand command) {
        if (command instanceof OrderCommand.Create create)
            return call(
                    "EXEC dbo.usp_CreateOrderRequest ?,?,?,?,?",
                    rows -> receipt(rows, command),
                    actor.id(),
                    actor.authVersion(),
                    command.key(),
                    command.fingerprint(actor.id()),
                    create.request().holdId());
        OrderCommand.ChangeCoupon change = (OrderCommand.ChangeCoupon) command;
        String code =
                change.mutation() instanceof CouponMutation.SetCoupon set ? set.couponCode() : null;
        return call(
                "EXEC dbo.usp_ApplyOrderCouponRequest ?,?,?,?,?,?",
                rows -> receipt(rows, command),
                actor.id(),
                actor.authVersion(),
                command.key(),
                command.fingerprint(actor.id()),
                change.targetId(),
                code);
    }

    public CurrentCheckout checkout(OrderActor actor, UUID holdId) {
        return call(
                "EXEC dbo.usp_ReadCheckout ?,?,?",
                rows -> {
                    CurrentCheckout current = checkout(payload(rows));
                    if (current.order().isPresent()
                            && (!current.order().get().snapshot().buyerId().equals(actor.id())
                                    || !current.order().get().snapshot().holdId().equals(holdId)))
                        throw OrderException.unavailable();
                    return current;
                },
                actor.id(),
                actor.authVersion(),
                holdId);
    }

    public String eligibleCoupons(OrderActor actor, UUID holdId) {
        return call(
                "EXEC dbo.usp_ListCheckoutCoupons ?,?,?",
                rows -> {
                    String body = payload(rows);
                    JSONObject result = new JSONObject(body);
                    Instant.parse(result.getString("serverTime"));
                    if (!holdId.equals(UUID.fromString(result.getString("holdId"))))
                        throw OrderException.unavailable();
                    for (Object entry : result.getJSONArray("coupons")) {
                        JSONObject choice = (JSONObject) entry;
                        money(choice, "discountAmount");
                        money(choice, "totalAmount");
                        Instant.parse(choice.getString("validUntil"));
                    }
                    return body;
                },
                actor.id(),
                actor.authVersion(),
                holdId);
    }

    public String eligibility(OrderActor actor, UUID orderId, String couponCode) {
        new CouponMutation.SetCoupon(orderId, couponCode);
        return call(
                "EXEC dbo.usp_ReadOrderCouponEligibility ?,?,?,?",
                rows -> {
                    String body = payload(rows);
                    JSONObject preview = new JSONObject(body);
                    Instant.parse(preview.getString("serverTime"));
                    if (!orderId.equals(UUID.fromString(preview.getString("orderId")))
                            || preview.getInt("remainingUses") < 0)
                        throw OrderException.unavailable();
                    if (preview.getBoolean("eligible")) {
                        money(preview, "discountAmount");
                        money(preview, "totalAmount");
                        Instant.parse(preview.getString("validUntil"));
                    }
                    return body;
                },
                actor.id(),
                actor.authVersion(),
                orderId,
                couponCode);
    }

    public record OrderHistoryPage(
            int page, int size, long total, List<OrderHistoryItemDto> orders) {
        public String toJson() {
            String ordersJson =
                    orders.stream()
                            .map(OrderHistoryItemDto::toJson)
                            .collect(Collectors.joining(","));
            return "{\"page\":"
                    + page
                    + ",\"size\":"
                    + size
                    + ",\"total\":"
                    + total
                    + ",\"orders\":["
                    + ordersJson
                    + "]}";
        }
    }

    public OrderHistoryPage findOrdersByBuyerId(
            UUID buyerId, int page, int size, String statusFilter) {
        return findOrdersByBuyerId(buyerId, -1, page, size, statusFilter);
    }

    public OrderHistoryPage findOrdersByBuyerId(
            UUID buyerId, int authVersion, int page, int size, String statusFilter) {
        Objects.requireNonNull(buyerId, "buyerId is required");
        int targetPage = Math.max(1, page);
        int targetSize = Math.min(50, Math.max(1, size));
        int offset = (targetPage - 1) * targetSize;

        String filter = "FROM dbo.vw_BuyerOrderDetails o WHERE o.buyer_id = ? AND (? IS NULL OR o.status = ?)";
        String selectSql =
                """
                SELECT o.id, o.order_code, o.event_id, o.event_title, o.venue_name, o.start_time,
                       o.subtotal_amount, o.discount_amount, o.total_amount, o.status,
                       o.created_at, o.paid_at, o.coupon_code, o.ticket_count
                """
                        + filter
                        + " ORDER BY o.created_at DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        return SqlCall.onConnection(
                buyer,
                conn -> {
                    long total =
                            SqlCall.call(
                                    conn,
                                    "SELECT COUNT_BIG(*) " + filter,
                                    rows -> rows.getLong(1),
                                    UNAVAILABLE,
                                    buyerId,
                                    statusFilter,
                                    statusFilter);
                    var items =
                            SqlCall.queryList(
                                    conn,
                                    selectSql,
                                    OrderRepository::historyItem,
                                    UNAVAILABLE,
                                    buyerId,
                                    statusFilter,
                                    statusFilter,
                                    offset,
                                    targetSize);
                    return new OrderHistoryPage(targetPage, targetSize, total, items);
                },
                UNAVAILABLE);
    }

    public Optional<OrderDetailDto> findOrderDetail(UUID orderId) {
        return findOrderDetail(orderId, null, -1);
    }

    public Optional<OrderDetailDto> findOrderDetail(UUID orderId, UUID actorId, int authVersion) {
        Objects.requireNonNull(orderId, "orderId is required");
        return SqlCall.onConnection(
                buyer,
                conn -> {
                    var header =
                            SqlCall.queryOptional(
                                    conn,
                                    """
                                    SELECT o.id, o.buyer_id, o.order_code, o.event_id, o.event_title,
                                           o.venue_name, o.venue_address, o.start_time, o.end_time,
                                           o.subtotal_amount, o.discount_amount, o.total_amount, o.status,
                                           o.created_at, o.paid_at, o.coupon_code,
                                           o.payment_status, o.txn_ref, o.provider_reference, o.captured_at
                                    FROM dbo.vw_BuyerOrderDetails o
                                    WHERE o.id = ?
                                    """,
                                    OrderHeader::read,
                                    UNAVAILABLE,
                                    orderId);
                    if (header.isEmpty()) return Optional.empty();
                    var items =
                            SqlCall.queryList(
                                    conn,
                                    """
                                    SELECT oi.id, oi.zone_name_snapshot, oi.seat_label_snapshot, oi.quantity, oi.unit_price,
                                           (oi.quantity * oi.unit_price) as line_total
                                    FROM dbo.vw_BuyerOrderItems oi
                                    WHERE oi.order_id = ?
                                    """,
                                    rows ->
                                            new OrderDetailDto.OrderItemDetail(
                                                    UUID.fromString(rows.getString("id")),
                                                    rows.getString("zone_name_snapshot"),
                                                    rows.getString("seat_label_snapshot"),
                                                    rows.getInt("quantity"),
                                                    rows.getBigDecimal("unit_price"),
                                                    rows.getBigDecimal("line_total")),
                                    UNAVAILABLE,
                                    orderId);
                    var tickets =
                            SqlCall.queryList(
                                    conn,
                                    """
                                    SELECT t.id, t.ticket_code, t.status, t.paid_amount
                                    FROM dbo.vw_TicketRuntimeDetails t
                                    WHERE t.order_id = ?
                                    ORDER BY t.issued_at ASC
                                    """,
                                    rows ->
                                            new OrderDetailDto.OrderTicketDetail(
                                                    UUID.fromString(rows.getString("id")),
                                                    rows.getString("ticket_code"),
                                                    rows.getString("status"),
                                                    rows.getLong("paid_amount")),
                                    UNAVAILABLE,
                                    orderId);
                    return Optional.of(header.get().toDetail(orderId, items, tickets));
                },
                UNAVAILABLE);
    }

    private static OrderHistoryItemDto historyItem(ResultSet rs) throws SQLException {
        return new OrderHistoryItemDto(
                UUID.fromString(rs.getString("id")),
                rs.getString("order_code"),
                uuid(rs, "event_id"),
                rs.getString("event_title"),
                rs.getString("venue_name"),
                instant(rs, "start_time"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                instant(rs, "created_at"),
                instant(rs, "paid_at"),
                rs.getString("coupon_code"),
                rs.getLong("ticket_count"));
    }

    /** The order row of vw_BuyerOrderDetails; items and tickets are read separately. */
    private record OrderHeader(
            UUID buyerId,
            String orderCode,
            UUID eventId,
            String eventTitle,
            String venueName,
            String venueAddress,
            Instant startTime,
            Instant endTime,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            String status,
            Instant createdAt,
            Instant paidAt,
            String couponCode,
            String paymentStatus,
            String txnRef,
            String providerRef,
            Instant capturedAt) {

        static OrderHeader read(ResultSet rs) throws SQLException {
            return new OrderHeader(
                    UUID.fromString(rs.getString("buyer_id")),
                    rs.getString("order_code"),
                    uuid(rs, "event_id"),
                    rs.getString("event_title"),
                    rs.getString("venue_name"),
                    rs.getString("venue_address"),
                    instant(rs, "start_time"),
                    instant(rs, "end_time"),
                    rs.getBigDecimal("subtotal_amount"),
                    rs.getBigDecimal("discount_amount"),
                    rs.getBigDecimal("total_amount"),
                    rs.getString("status"),
                    instant(rs, "created_at"),
                    instant(rs, "paid_at"),
                    rs.getString("coupon_code"),
                    rs.getString("payment_status"),
                    rs.getString("txn_ref"),
                    rs.getString("provider_reference"),
                    instant(rs, "captured_at"));
        }

        OrderDetailDto toDetail(
                UUID orderId,
                List<OrderDetailDto.OrderItemDetail> items,
                List<OrderDetailDto.OrderTicketDetail> tickets) {
            return new OrderDetailDto(
                    orderId,
                    buyerId,
                    orderCode,
                    eventId,
                    eventTitle,
                    venueName,
                    venueAddress,
                    startTime,
                    endTime,
                    subtotal,
                    discount,
                    total,
                    status,
                    createdAt,
                    paidAt,
                    couponCode,
                    paymentStatus,
                    txnRef,
                    providerRef,
                    capturedAt,
                    items,
                    tickets);
        }
    }

    public void assertOrderAccess(Connection conn, UUID orderId) {
        if (conn == null) return;
        Objects.requireNonNull(orderId, "orderId is required");
        SqlCall.execute(conn, "{call dbo.usp_AssertOrderAccess(?)}", ACCESS_ERRORS, orderId);
    }

    public void assertOrderAccess(UUID orderId, UUID actorId) {
        if (buyer == null) return;
        Objects.requireNonNull(orderId, "orderId is required");
        SqlCall.onConnection(
                buyer,
                conn -> {
                    assertOrderAccess(conn, orderId);
                    return null;
                },
                UNAVAILABLE);
    }

    public static CurrentCheckout checkout(String body) {
        JSONObject root = new JSONObject(body);
        Instant now = Instant.parse(root.getString("serverTime"));
        if (root.isNull("order")) return new CurrentCheckout(body, now, Optional.empty());
        JSONObject order = root.getJSONObject("order");
        OrderSnapshot snapshot = snapshot(order);
        long version = order.getLong("version");
        if (version < 0) throw new IllegalArgumentException("Negative version");
        var state = CurrentCheckout.HoldState.valueOf(order.getString("holdStatus"));
        var payment =
                CurrentCheckout.PaymentResolution.valueOf(order.getString("paymentResolution"));
        boolean editable = order.getBoolean("canEditCoupon");
        if (editable
                && (state != CurrentCheckout.HoldState.ACTIVE
                        || payment != CurrentCheckout.PaymentResolution.CLEAR
                        || snapshot.status() != OrderStatus.PENDING_PAYMENT
                        || !now.isBefore(snapshot.expiresAt())))
            throw new IllegalArgumentException("Invalid editable state");
        return new CurrentCheckout(
                body,
                now,
                Optional.of(
                        new CurrentCheckout.CurrentOrder(
                                order.toString(), snapshot, version, state, payment, editable)));
    }

    private static OrderMutationReceipt receipt(ResultSet rows, OrderCommand command)
            throws SQLException {
        String body = payload(rows);
        JSONObject json = new JSONObject(body);
        UUID orderId = id(json, "orderId"), holdId = UUID.fromString(rows.getString("hold_id"));
        Instant expires = Instant.parse(rows.getString("expires_at"));
        if (command instanceof OrderCommand.Create) snapshot(json);
        else
            new OrderSnapshot.Totals(
                    money(json, "subtotal"),
                    money(json, "discountAmount"),
                    money(json, "totalAmount"));
        if (json.getLong("version") < 0) throw OrderException.unavailable();
        return new OrderMutationReceipt(
                command.httpStatus(), body, rows.getBoolean("replayed"), orderId, holdId, expires);
    }

    private static OrderSnapshot snapshot(JSONObject order) {
        List<OrderSnapshot.Item> items = new ArrayList<>();
        JSONArray array = order.getJSONArray("items");
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            var value =
                    new OrderSnapshot.Item(
                            id(item, "orderItemId"),
                            id(item, "zoneId"),
                            item.isNull("seatId") ? null : id(item, "seatId"),
                            item.getString("zoneName"),
                            item.isNull("seatLabel")
                                    ? Optional.empty()
                                    : Optional.of(item.getString("seatLabel")),
                            item.getInt("quantity"),
                            money(item, "unitPrice"));
            if (!value.lineTotal().equals(money(item, "lineTotal")))
                throw new IllegalArgumentException("Line total differs");
            items.add(value);
        }
        return new OrderSnapshot(
                id(order, "orderId"),
                order.getString("orderCode"),
                id(order, "userId"),
                id(order, "eventId"),
                id(order, "holdId"),
                OrderStatus.valueOf(order.getString("status")),
                Instant.parse(order.getString("expiresAt")),
                items,
                new OrderSnapshot.Totals(
                        money(order, "subtotal"),
                        money(order, "discountAmount"),
                        money(order, "totalAmount")),
                order.isNull("appliedCouponCode")
                        ? Optional.empty()
                        : Optional.of(order.getString("appliedCouponCode")));
    }

    private static BigDecimal money(JSONObject value, String key) {
        String text = value.getString(key);
        if (!text.matches("0|[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Invalid whole VND");
        return new BigDecimal(text);
    }

    private static UUID id(JSONObject value, String key) {
        return UUID.fromString(value.getString(key));
    }

    private static String payload(ResultSet rows) throws SQLException {
        String body = rows.getString("response_payload");
        if (body == null || body.length() > 64 * 1024) throw OrderException.unavailable();
        new JSONObject(body);
        return body;
    }

    private <T> T call(String sql, SqlCall.ResultSetExtractor<T> read, Object... args) {
        return SqlCall.callWithRetry(buyer, sql, read, ERRORS, OrderException::unavailable, args);
    }

    private static String couponCode(String message) {
        for (String reason :
                List.of(
                        "NOT_FOUND",
                        "WRONG_ORGANIZATION",
                        "INACTIVE",
                        "NOT_STARTED",
                        "EXPIRED",
                        "QUOTA_EXHAUSTED",
                        "INVALID_CONFIGURATION"))
            if (message.contains("COUPON_" + reason)) return "COUPON_" + reason;
        return "COUPON_INVALID";
    }
}
