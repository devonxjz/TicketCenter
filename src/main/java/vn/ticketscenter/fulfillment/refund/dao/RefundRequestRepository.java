package vn.ticketscenter.fulfillment.refund.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.Session;
import vn.ticketscenter.config.error.AppException;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.SqlCall;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestStore.OrderOption;
import vn.ticketscenter.fulfillment.refund.dto.CreateRefundRequestDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundDetailDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundPageDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundSummaryDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

/** JDBC projections and SP-owned writes inside the shared resource-local JPA transaction. */
public class RefundRequestRepository implements RefundRequestStore {

    private static final SqlCall.ErrorTable BUYER_REFUND_ERRORS = SqlCall.ErrorTable.of()
            .on(53000, 400, "INVALID_REQUEST", "Yêu cầu không hợp lệ.")
            .on(53005, 400, "REFUND_NOT_ELIGIBLE", "Vé không đủ điều kiện hoàn trả.")
            .on(53001, 401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.")
            .on(53003, 403, "ORDER_FORBIDDEN", "Bạn không có quyền xem đơn hàng này.")
            .on(53004, 404, "ORDER_NOT_FOUND", "Đơn hàng không tồn tại.")
            .on(53009, 409, "REFUND_CONFLICT", "Xung đột yêu cầu hoàn vé.")
            .fallback((code, msg) -> new RefundException(
                    503, "DATABASE_UNAVAILABLE",
                    "Không thể thực hiện yêu cầu hoàn vé. Vui lòng kiểm tra quyền, thời hạn và trạng thái vé."));

    private final TransactionManager transactions;

    public RefundRequestRepository(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    private <T> T withConnection(SqlCall.SqlWork<T> work) {
        try {
            return transactions.execute(
                    DatabasePrincipal.BUYER,
                    em -> em.unwrap(Session.class).doReturningWork(work::run));
        } catch (AppException e) {
            throw e;
        } catch (Exception failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof AppException app) {
                    throw app;
                }
                if (cause instanceof SQLException sql) {
                    AppException app = BUYER_REFUND_ERRORS.translate(sql.getErrorCode(), sql.getMessage());
                    if (app != null) throw app;
                }
            }
            throw new RefundException(
                    503, "DATABASE_UNAVAILABLE",
                    "Không thể thực hiện yêu cầu hoàn vé. Vui lòng kiểm tra quyền, thời hạn và trạng thái vé.");
        }
    }

    @Override
    public boolean active(UserPrincipal actor) {
        return withConnection(c -> SqlCall.queryOptional(
                c,
                "SELECT 1 FROM dbo.vw_ActorAuthority WHERE id=? AND status='ACTIVE' AND auth_version=?",
                r -> true,
                BUYER_REFUND_ERRORS,
                actor.actorId(),
                actor.authVersion()).orElse(false));
    }

    private static void owner(java.sql.Connection c, UserPrincipal actor, UUID order) {
        var ownerId = SqlCall.queryOptional(
                c,
                "SELECT owner_id FROM dbo.v_refundable_orders WHERE order_id=?",
                r -> r.getString(1),
                BUYER_REFUND_ERRORS,
                order);
        if (ownerId.isEmpty()) {
            throw new RefundException(404, "ORDER_NOT_FOUND", "Đơn hàng không tồn tại.");
        }
        if (!actor.actorId().toString().equalsIgnoreCase(ownerId.get())) {
            throw new RefundException(403, "ORDER_FORBIDDEN", "Bạn không có quyền xem đơn hàng này.");
        }
    }

    @Override
    public List<RefundTicketOptionDto> tickets(
            UserPrincipal actor, UUID order, Instant ignoredClock) {
        return withConnection(c -> {
            owner(c, actor, order);
            return SqlCall.queryList(
                    c,
                    """
                    SELECT t.*, CASE WHEN t.has_open_refund=1 OR t.status='REFUND_PENDING' THEN N'Vé đang chờ xử lý hoàn tiền.'
                     WHEN t.status='USED' THEN N'Vé đã được sử dụng.' WHEN t.status<>'ACTIVE' THEN N'Vé không còn hiệu lực.'
                     WHEN o.order_status<>'PAID' THEN N'Đơn hàng chưa thanh toán.' WHEN o.event_status<>'PUBLISHED' THEN N'Sự kiện không còn công khai.'
                     WHEN SYSUTCDATETIME()>=o.start_time THEN N'Đã quá thời hạn yêu cầu hoàn vé.' ELSE NULL END unrefundable_reason
                    FROM dbo.v_refundable_ticket_options t JOIN dbo.v_refundable_orders o ON o.order_id=t.order_id
                    WHERE t.order_id=? ORDER BY t.ticket_code, t.ticket_id
                    """,
                    r -> ticket(r, r.getString("unrefundable_reason")),
                    BUYER_REFUND_ERRORS,
                    order);
        });
    }

    @Override
    public List<OrderOption> orders(UserPrincipal actor) {
        return withConnection(c -> SqlCall.queryList(
                c,
                "SELECT TOP(100) order_id, order_code, event_name FROM dbo.v_refundable_orders WHERE owner_id=? AND order_status='PAID' ORDER BY start_time DESC, order_id",
                r -> new OrderOption(
                        UUID.fromString(r.getString(1)),
                        r.getString(2),
                        r.getString(3)),
                BUYER_REFUND_ERRORS,
                actor.actorId()));
    }

    @Override
    public RefundSummaryDto create(UserPrincipal actor, CreateRefundRequestDto request, UUID key) {
        return withConnection(c -> {
            String ticketIdsCsv = request.ticketIds().stream().map(UUID::toString).collect(Collectors.joining(","));
            return SqlCall.call(
                    c,
                    "{call dbo.sp_refund_request_for_tickets(?,?,?,?,?,?,?)}",
                    RefundRequestRepository::summary,
                    BUYER_REFUND_ERRORS,
                    request.orderId(),
                    actor.actorId(),
                    ticketIdsCsv,
                    request.reason(),
                    SqlCall.OutParam.of(Types.VARCHAR),
                    key,
                    actor.authVersion());
        });
    }

    @Override
    public RefundPageDto list(UserPrincipal actor, int page, int size) {
        return withConnection(c -> {
            long count = SqlCall.call(
                    c,
                    "SELECT COUNT_BIG(*) FROM dbo.v_refunds WHERE owner_id=?",
                    r -> r.getLong(1),
                    BUYER_REFUND_ERRORS,
                    actor.actorId());
            List<RefundSummaryDto> items = SqlCall.queryList(
                    c,
                    "SELECT * FROM dbo.v_refunds WHERE owner_id=? ORDER BY created_at DESC, refund_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                    RefundRequestRepository::summary,
                    BUYER_REFUND_ERRORS,
                    actor.actorId(),
                    (page - 1) * size,
                    size);
            return new RefundPageDto(items, count, page, size);
        });
    }

    @Override
    public RefundDetailDto detail(UserPrincipal actor, UUID id) {
        return withConnection(c -> {
            var refundRow = SqlCall.call(
                    c,
                    "SELECT * FROM dbo.v_refunds WHERE refund_id=?",
                    r -> new Object[] {
                        r.getString("owner_id"),
                        summary(r),
                        r.getString("current_attempt_id") == null ? null : UUID.fromString(r.getString("current_attempt_id")),
                        instant(r, "processed_at")
                    },
                    BUYER_REFUND_ERRORS,
                    id);
            if (refundRow == null) {
                throw new RefundException(404, "REFUND_NOT_FOUND", "Yêu cầu hoàn tiền không tồn tại.");
            }
            if (!actor.actorId().toString().equalsIgnoreCase((String) refundRow[0])) {
                throw new RefundException(403, "REFUND_FORBIDDEN", "Bạn không có quyền xem yêu cầu này.");
            }
            var refund = (RefundSummaryDto) refundRow[1];
            var attempt = (UUID) refundRow[2];
            var processed = (Instant) refundRow[3];

            var tickets = SqlCall.queryList(
                    c,
                    "SELECT * FROM dbo.v_refund_tickets WHERE refund_id=? ORDER BY ticket_id",
                    r -> ticket(r, "Vé thuộc yêu cầu hoàn tiền này."),
                    BUYER_REFUND_ERRORS,
                    id);

            var attempts = SqlCall.queryList(
                    c,
                    "SELECT * FROM dbo.v_refund_attempts WHERE refund_id=? ORDER BY attempt_no",
                    r -> new RefundDetailDto.Attempt(
                            UUID.fromString(r.getString("attempt_id")),
                            r.getInt("attempt_no"),
                            r.getString("status"),
                            instant(r, "created_at"),
                            instant(r, "processed_at")),
                    BUYER_REFUND_ERRORS,
                    id);

            return new RefundDetailDto(refund, attempt, processed, tickets, attempts);
        });
    }

    private static RefundSummaryDto summary(ResultSet r) throws SQLException {
        return new RefundSummaryDto(
                UUID.fromString(r.getString("refund_id")),
                UUID.fromString(r.getString("order_id")),
                r.getBigDecimal("amount").toPlainString(),
                r.getString("status"),
                r.getInt("ticket_count"),
                instant(r, "created_at"),
                r.getString("event_name"),
                r.getString("reason"),
                r.getString("rejection_reason"));
    }

    private static RefundTicketOptionDto ticket(ResultSet r, String reason) throws SQLException {
        String seat = r.getString("seat_label_snapshot");
        return new RefundTicketOptionDto(
                UUID.fromString(r.getString("ticket_id")),
                r.getString("ticket_code"),
                seat == null ? r.getString("zone_name_snapshot") : seat,
                r.getBigDecimal("paid_amount").toPlainString(),
                r.getString("status"),
                reason == null,
                reason);
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp v = r.getTimestamp(column, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return v == null ? null : v.toInstant();
    }
}
