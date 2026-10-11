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

public final class OrderRepository {
    private final DataSource buyer;
    public OrderRepository(DataSource buyer) { this.buyer = Objects.requireNonNull(buyer); }

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
}
