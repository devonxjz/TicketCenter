package vn.ticketscenter.order.repository;

import org.json.JSONArray;
import org.json.JSONObject;
import vn.ticketscenter.order.dto.*;
import vn.ticketscenter.order.model.OrderStatus;
import vn.ticketscenter.order.service.OrderException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class OrderRepository {
    private final DataSource buyer;
    public OrderRepository(DataSource buyer) { this.buyer = Objects.requireNonNull(buyer); }
    public OrderRepository() { this.buyer = null; }

    public OrderMutationReceipt mutate(OrderActor actor, OrderCommand command) {
        if (command instanceof OrderCommand.Create create)
            return call("EXEC dbo.usp_CreateOrderRequest ?,?,?,?,?", rows -> receipt(rows, command),
                    actor.id(), actor.authVersion(), command.key(), command.fingerprint(actor.id()), create.request().holdId());
        OrderCommand.ChangeCoupon change = (OrderCommand.ChangeCoupon) command;
        String code = change.mutation() instanceof CouponMutation.SetCoupon set ? set.couponCode() : null;
        return call("EXEC dbo.usp_ApplyOrderCouponRequest ?,?,?,?,?,?", rows -> receipt(rows, command),
                actor.id(), actor.authVersion(), command.key(), command.fingerprint(actor.id()), change.targetId(), code);
    }

    public CurrentCheckout checkout(OrderActor actor, UUID holdId) {
        return call("EXEC dbo.usp_ReadCheckout ?,?,?", rows -> {
            CurrentCheckout current = checkout(payload(rows));
            if (current.order().isPresent() && (!current.order().get().snapshot().buyerId().equals(actor.id())
                    || !current.order().get().snapshot().holdId().equals(holdId))) throw OrderException.unavailable();
            return current;
        }, actor.id(), actor.authVersion(), holdId);
    }

    public String eligibleCoupons(OrderActor actor, UUID holdId) {
        return call("EXEC dbo.usp_ListCheckoutCoupons ?,?,?", rows -> {
            String body = payload(rows);
            JSONObject result = new JSONObject(body);
            Instant.parse(result.getString("serverTime"));
            if (!holdId.equals(UUID.fromString(result.getString("holdId")))) throw OrderException.unavailable();
            for (Object entry : result.getJSONArray("coupons")) {
                JSONObject choice = (JSONObject) entry;
                money(choice, "discountAmount"); money(choice, "totalAmount");
                Instant.parse(choice.getString("validUntil"));
            }
            return body;
        }, actor.id(), actor.authVersion(), holdId);
    }

    public String eligibility(OrderActor actor, UUID orderId, String couponCode) {
        new CouponMutation.SetCoupon(orderId, couponCode);
        return call("EXEC dbo.usp_ReadOrderCouponEligibility ?,?,?,?", rows -> {
            String body = payload(rows);
            JSONObject preview = new JSONObject(body);
            Instant.parse(preview.getString("serverTime"));
            if (!orderId.equals(UUID.fromString(preview.getString("orderId"))) || preview.getInt("remainingUses") < 0)
                throw OrderException.unavailable();
            if (preview.getBoolean("eligible")) {
                money(preview, "discountAmount"); money(preview, "totalAmount"); Instant.parse(preview.getString("validUntil"));
            }
            return body;
        }, actor.id(), actor.authVersion(), orderId, couponCode);
    }

    public record OrderHistoryPage(int page, int size, long total, List<OrderHistoryItemDto> orders) {
        public String toJson() {
            String ordersJson = orders.stream().map(OrderHistoryItemDto::toJson).collect(java.util.stream.Collectors.joining(","));
            return "{\"page\":" + page + ",\"size\":" + size + ",\"total\":" + total + ",\"orders\":[" + ordersJson + "]}";
        }
    }

    public OrderHistoryPage findOrdersByBuyerId(UUID buyerId, int page, int size, String statusFilter) {
        return findOrdersByBuyerId(buyerId, -1, page, size, statusFilter);
    }

    public OrderHistoryPage findOrdersByBuyerId(UUID buyerId, int authVersion, int page, int size, String statusFilter) {
        Objects.requireNonNull(buyerId, "buyerId is required");
        int targetPage = Math.max(1, page);
        int targetSize = Math.min(50, Math.max(1, size));
        int offset = (targetPage - 1) * targetSize;

        String countSql = """
                SELECT COUNT_BIG(*) FROM dbo.vw_BuyerOrderDetails o
                WHERE o.buyer_id = ? AND (? IS NULL OR o.status = ?)
                """;
        String selectSql = """
                SELECT o.id, o.order_code, o.event_id, o.event_title, o.venue_name, o.start_time,
                       o.subtotal_amount, o.discount_amount, o.total_amount, o.status,
                       o.created_at, o.paid_at, o.coupon_code, o.ticket_count
                FROM dbo.vw_BuyerOrderDetails o
                WHERE o.buyer_id = ? AND (? IS NULL OR o.status = ?)
                ORDER BY o.created_at DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;

        long total = 0;
        List<OrderHistoryItemDto> items = new ArrayList<>();

        try (Connection conn = buyer.getConnection()) {
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                countPs.setObject(1, buyerId);
                countPs.setString(2, statusFilter);
                countPs.setString(3, statusFilter);
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) total = rs.getLong(1);
                }
            }

            try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                selectPs.setObject(1, buyerId);
                selectPs.setString(2, statusFilter);
                selectPs.setString(3, statusFilter);
                selectPs.setInt(4, offset);
                selectPs.setInt(5, targetSize);
                try (ResultSet rs = selectPs.executeQuery()) {
                    while (rs.next()) {
                        Timestamp startTs = rs.getTimestamp("start_time", utc());
                        Timestamp createdTs = rs.getTimestamp("created_at", utc());
                        Timestamp paidTs = rs.getTimestamp("paid_at", utc());
                        items.add(new OrderHistoryItemDto(
                                UUID.fromString(rs.getString("id")),
                                rs.getString("order_code"),
                                rs.getString("event_id") != null ? UUID.fromString(rs.getString("event_id")) : null,
                                rs.getString("event_title"),
                                rs.getString("venue_name"),
                                startTs != null ? startTs.toInstant() : null,
                                rs.getBigDecimal("subtotal_amount"),
                                rs.getBigDecimal("discount_amount"),
                                rs.getBigDecimal("total_amount"),
                                rs.getString("status"),
                                createdTs != null ? createdTs.toInstant() : null,
                                paidTs != null ? paidTs.toInstant() : null,
                                rs.getString("coupon_code"),
                                rs.getLong("ticket_count")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            throw OrderException.unavailable();
        }

        return new OrderHistoryPage(targetPage, targetSize, total, items);
    }

    public Optional<OrderDetailDto> findOrderDetail(UUID orderId) {
        return findOrderDetail(orderId, null, -1);
    }

    public Optional<OrderDetailDto> findOrderDetail(UUID orderId, UUID actorId, int authVersion) {
        Objects.requireNonNull(orderId, "orderId is required");
        String orderSql = """
                SELECT o.id, o.buyer_id, o.order_code, o.event_id, o.event_title,
                       o.venue_name, o.venue_address, o.start_time, o.end_time,
                       o.subtotal_amount, o.discount_amount, o.total_amount, o.status,
                       o.created_at, o.paid_at, o.coupon_code,
                       o.payment_status, o.txn_ref, o.provider_reference, o.captured_at
                FROM dbo.vw_BuyerOrderDetails o
                WHERE o.id = ?
                """;
        String itemsSql = """
                SELECT oi.id, oi.zone_name_snapshot, oi.seat_label_snapshot, oi.quantity, oi.unit_price,
                       (oi.quantity * oi.unit_price) as line_total
                FROM dbo.vw_BuyerOrderItems oi
                WHERE oi.order_id = ?
                """;
        String ticketsSql = """
                SELECT t.id, t.ticket_code, t.status, t.paid_amount
                FROM dbo.vw_TicketRuntimeDetails t
                WHERE t.order_id = ?
                ORDER BY t.issued_at ASC
                """;

        try (Connection conn = buyer.getConnection()) {
            UUID buyerId;
            String orderCode, eventTitle, venueName, venueAddress, status, couponCode, paymentStatus, txnRef, providerRef;
            UUID eventId;
            BigDecimal subtotal, discount, total;
            Instant startTime, endTime, createdAt, paidAt, paymentCapturedAt;

            try (PreparedStatement ps = conn.prepareStatement(orderSql)) {
                ps.setObject(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    buyerId = UUID.fromString(rs.getString("buyer_id"));
                    orderCode = rs.getString("order_code");
                    eventId = rs.getString("event_id") != null ? UUID.fromString(rs.getString("event_id")) : null;
                    eventTitle = rs.getString("event_title");
                    venueName = rs.getString("venue_name");
                    venueAddress = rs.getString("venue_address");
                    Timestamp st = rs.getTimestamp("start_time", utc());
                    startTime = st != null ? st.toInstant() : null;
                    Timestamp et = rs.getTimestamp("end_time", utc());
                    endTime = et != null ? et.toInstant() : null;
                    subtotal = rs.getBigDecimal("subtotal_amount");
                    discount = rs.getBigDecimal("discount_amount");
                    total = rs.getBigDecimal("total_amount");
                    status = rs.getString("status");
                    Timestamp ct = rs.getTimestamp("created_at", utc());
                    createdAt = ct != null ? ct.toInstant() : null;
                    Timestamp pt = rs.getTimestamp("paid_at", utc());
                    paidAt = pt != null ? pt.toInstant() : null;
                    couponCode = rs.getString("coupon_code");
                    paymentStatus = rs.getString("payment_status");
                    txnRef = rs.getString("txn_ref");
                    providerRef = rs.getString("provider_reference");
                    Timestamp cap = rs.getTimestamp("captured_at", utc());
                    paymentCapturedAt = cap != null ? cap.toInstant() : null;
                }
            }

            List<OrderDetailDto.OrderItemDetail> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setObject(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new OrderDetailDto.OrderItemDetail(
                                UUID.fromString(rs.getString("id")),
                                rs.getString("zone_name_snapshot"),
                                rs.getString("seat_label_snapshot"),
                                rs.getInt("quantity"),
                                rs.getBigDecimal("unit_price"),
                                rs.getBigDecimal("line_total")
                        ));
                    }
                }
            }

            List<OrderDetailDto.OrderTicketDetail> tickets = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(ticketsSql)) {
                ps.setObject(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tickets.add(new OrderDetailDto.OrderTicketDetail(
                                UUID.fromString(rs.getString("id")),
                                rs.getString("ticket_code"),
                                rs.getString("status"),
                                rs.getLong("paid_amount")
                        ));
                    }
                }
            }

            return Optional.of(new OrderDetailDto(
                    orderId, buyerId, orderCode, eventId, eventTitle, venueName, venueAddress,
                    startTime, endTime, subtotal, discount, total, status, createdAt, paidAt,
                    couponCode, paymentStatus, txnRef, providerRef, paymentCapturedAt,
                    items, tickets
            ));
        } catch (SQLException e) {
            throw OrderException.unavailable();
        }
    }

    public void assertOrderAccess(Connection conn, UUID orderId) {
        if (conn == null) return;
        Objects.requireNonNull(orderId, "orderId is required");
        try (CallableStatement cs = conn.prepareCall("{call dbo.usp_AssertOrderAccess(?)}")) {
            cs.setObject(1, orderId);
            cs.execute();
        } catch (SQLException e) {
            int code = e.getErrorCode();
            if (code == 51403) {
                throw new OrderException(403, "FORBIDDEN", "You do not own this order.");
            }
            if (code == 51404) {
                throw new OrderException(404, "ORDER_NOT_FOUND", "Order was not found.");
            }
            throw OrderException.unavailable();
        }
    }

    public void assertOrderAccess(UUID orderId, UUID actorId) {
        if (buyer == null) return;
        Objects.requireNonNull(orderId, "orderId is required");
        try (Connection conn = buyer.getConnection()) {
            assertOrderAccess(conn, orderId);
        } catch (SQLException e) {
            throw OrderException.unavailable();
        }
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
        var payment = CurrentCheckout.PaymentResolution.valueOf(order.getString("paymentResolution"));
        boolean editable = order.getBoolean("canEditCoupon");
        if (editable && (state != CurrentCheckout.HoldState.ACTIVE || payment != CurrentCheckout.PaymentResolution.CLEAR
                || snapshot.status() != OrderStatus.PENDING_PAYMENT || !now.isBefore(snapshot.expiresAt())))
            throw new IllegalArgumentException("Invalid editable state");
        return new CurrentCheckout(body, now, Optional.of(new CurrentCheckout.CurrentOrder(order.toString(), snapshot, version, state, payment, editable)));
    }

    private static OrderMutationReceipt receipt(ResultSet rows, OrderCommand command) throws SQLException {
        String body = payload(rows);
        JSONObject json = new JSONObject(body);
        UUID orderId = id(json, "orderId"), holdId = UUID.fromString(rows.getString("hold_id"));
        Instant expires = Instant.parse(rows.getString("expires_at"));
        if (command instanceof OrderCommand.Create) snapshot(json);
        else new OrderSnapshot.Totals(money(json, "subtotal"), money(json, "discountAmount"), money(json, "totalAmount"));
        if (json.getLong("version") < 0) throw OrderException.unavailable();
        return new OrderMutationReceipt(command.httpStatus(), body, rows.getBoolean("replayed"), orderId, holdId, expires);
    }

    private static OrderSnapshot snapshot(JSONObject order) {
        List<OrderSnapshot.Item> items = new ArrayList<>();
        JSONArray array = order.getJSONArray("items");
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            var value = new OrderSnapshot.Item(id(item, "orderItemId"), id(item, "zoneId"),
                    item.isNull("seatId") ? null : id(item, "seatId"), item.getString("zoneName"),
                    item.isNull("seatLabel") ? Optional.empty() : Optional.of(item.getString("seatLabel")), item.getInt("quantity"), money(item, "unitPrice"));
            if (!value.lineTotal().equals(money(item, "lineTotal"))) throw new IllegalArgumentException("Line total differs");
            items.add(value);
        }
        return new OrderSnapshot(id(order, "orderId"), order.getString("orderCode"), id(order, "userId"), id(order, "eventId"), id(order, "holdId"),
                OrderStatus.valueOf(order.getString("status")), Instant.parse(order.getString("expiresAt")), items,
                new OrderSnapshot.Totals(money(order, "subtotal"), money(order, "discountAmount"), money(order, "totalAmount")),
                order.isNull("appliedCouponCode") ? Optional.empty() : Optional.of(order.getString("appliedCouponCode")));
    }

    private static BigDecimal money(JSONObject value, String key) {
        String text = value.getString(key);
        if (!text.matches("0|[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid whole VND");
        return new BigDecimal(text);
    }
    private static UUID id(JSONObject value, String key) { return UUID.fromString(value.getString(key)); }
    private static String payload(ResultSet rows) throws SQLException {
        String body = rows.getString("response_payload");
        if (body == null || body.length() > 64 * 1024) throw OrderException.unavailable();
        new JSONObject(body);
        return body;
    }
    @FunctionalInterface private interface Read<T> { T read(ResultSet rows) throws SQLException; }
    private <T> T call(String sql, Read<T> read, Object... args) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try (Connection connection = buyer.getConnection()) {
                connection.setAutoCommit(true);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(20);
                    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i] instanceof UUID id ? id.toString() : args[i]);
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw OrderException.unavailable();
                        return read.read(rows);
                    }
                }
            } catch (SQLException failure) {
                OrderException mapped = domain(failure);
                if (mapped != null) throw mapped;
                if (attempt == 2 || !retryable(failure)) throw OrderException.unavailable();
            } catch (OrderException failure) { throw failure; }
            catch (RuntimeException invalidResponse) { throw OrderException.unavailable(); }
        }
        throw OrderException.unavailable();
    }
    private static boolean retryable(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            if (error.getErrorCode() == 1205 || error.getErrorCode() == 1222 || error.getErrorCode() == 51309) return true;
            String state = error.getSQLState();
            if (state != null && (state.startsWith("08") || state.equals("HYT00") || state.equals("HYT01"))) return true;
        }
        return false;
    }
    private static OrderException domain(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            OrderException mapped = switch (error.getErrorCode()) {
                case 51301 -> new OrderException(401, "UNAUTHENTICATED", "A current session is required.");
                case 51303, 51403 -> new OrderException(403, "FORBIDDEN", "A current verified owner is required.");
                case 51400 -> new OrderException(400, "HOLD_INVALID", "An active unexpired Hold is required.");
                case 51404 -> new OrderException(404, "ORDER_NOT_FOUND", "Order was not found.");
                case 51408 -> new OrderException(409, "IDEMPOTENCY_CONFLICT", "Use a new key for a changed request.");
                case 51406 -> new OrderException(409, "ORDER_CONFLICT", "Refresh checkout before changing the Order.");
                case 51407 -> new OrderException(409, "PAYMENT_UNRESOLVED", "Payment must be resolved before changing the Coupon.");
                case 51405 -> new OrderException(400, couponCode(error.getMessage()), "Coupon is not eligible for this Order.");
                default -> null;
            };
            if (mapped != null) return mapped;
        }
        return null;
    }
    private static String couponCode(String message) {
        for (String reason : List.of("NOT_FOUND", "WRONG_ORGANIZATION", "INACTIVE", "NOT_STARTED", "EXPIRED", "QUOTA_EXHAUSTED", "INVALID_CONFIGURATION"))
            if (message.contains("COUPON_" + reason)) return "COUPON_" + reason;
        return "COUPON_INVALID";
    }

    private static java.util.Calendar utc() {
        return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
    }
}
