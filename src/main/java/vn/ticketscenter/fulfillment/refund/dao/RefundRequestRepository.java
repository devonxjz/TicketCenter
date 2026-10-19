package vn.ticketscenter.fulfillment.refund.dao;

import static vn.ticketscenter.config.persistence.SqlCall.instant;
import static vn.ticketscenter.config.persistence.SqlCall.uuid;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.SqlCall;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.refund.dto.CreateRefundRequestDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundDetailDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundPageDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundSummaryDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

/** Buyer refund reads from views and the SP-owned write, inside the shared JPA transaction. */
public class RefundRequestRepository implements RefundRequestStore {

    private static final String UNAVAILABLE =
            "Không thể thực hiện yêu cầu hoàn vé. Vui lòng kiểm tra quyền, thời hạn và trạng thái vé.";

    private static final SqlCall.ErrorTable ERRORS = SqlCall.ErrorTable.of()
            .on(53000, 400, "INVALID_REQUEST", "Yêu cầu không hợp lệ.")
            .on(53005, 400, "REFUND_NOT_ELIGIBLE", "Vé không đủ điều kiện hoàn trả.")
            .on(53001, 401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.")
            .on(53003, 403, "ORDER_FORBIDDEN", "Bạn không có quyền xem đơn hàng này.")
            .on(53004, 404, "ORDER_NOT_FOUND", "Đơn hàng không tồn tại.")
            .on(53009, 409, "REFUND_CONFLICT", "Xung đột yêu cầu hoàn vé.")
            .fallback((code, message) -> new RefundException(503, "DATABASE_UNAVAILABLE", UNAVAILABLE));

    private static final String REFUNDABLE_TICKETS = """
            SELECT t.*,
                   CASE
                       WHEN t.has_open_refund = 1 OR t.status = 'REFUND_PENDING' THEN N'Vé đang chờ xử lý hoàn tiền.'
                       WHEN t.status = 'USED' THEN N'Vé đã được sử dụng.'
                       WHEN t.status <> 'ACTIVE' THEN N'Vé không còn hiệu lực.'
                       WHEN o.order_status <> 'PAID' THEN N'Đơn hàng chưa thanh toán.'
                       WHEN o.event_status <> 'PUBLISHED' THEN N'Sự kiện không còn công khai.'
                       WHEN SYSUTCDATETIME() >= o.start_time THEN N'Đã quá thời hạn yêu cầu hoàn vé.'
                       ELSE NULL
                   END AS unrefundable_reason
            FROM dbo.v_refundable_ticket_options t
            JOIN dbo.v_refundable_orders o ON o.order_id = t.order_id
            WHERE t.order_id = ?
            ORDER BY t.ticket_code, t.ticket_id
            """;

    private static final String PAID_ORDERS = """
            SELECT TOP(100) order_id, order_code, event_name
            FROM dbo.v_refundable_orders
            WHERE owner_id = ? AND order_status = 'PAID'
            ORDER BY start_time DESC, order_id
            """;

    private static final String REFUND_PAGE = """
            SELECT * FROM dbo.v_refunds
            WHERE owner_id = ?
            ORDER BY created_at DESC, refund_id
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

    private record RefundRow(String ownerId, RefundSummaryDto summary, UUID attemptId, Instant processedAt) {}

    private final TransactionManager transactions;

    public RefundRequestRepository(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public boolean active(UserPrincipal actor) {
        return run(c -> SqlCall.queryOptional(
                        c,
                        "SELECT 1 FROM dbo.vw_ActorAuthority WHERE id=? AND status='ACTIVE' AND auth_version=?",
                        row -> true,
                        ERRORS,
                        actor.actorId(),
                        actor.authVersion())
                .isPresent());
    }

    @Override
    public List<RefundTicketOptionDto> tickets(UserPrincipal actor, UUID orderId) {
        return run(c -> {
            requireOrderOwner(c, actor, orderId);
            return SqlCall.queryList(
                    c,
                    REFUNDABLE_TICKETS,
                    row -> ticket(row, row.getString("unrefundable_reason")),
                    ERRORS,
                    orderId);
        });
    }

    @Override
    public List<OrderOption> orders(UserPrincipal actor) {
        return run(c -> SqlCall.queryList(
                c,
                PAID_ORDERS,
                row -> new OrderOption(uuid(row, "order_id"), row.getString("order_code"), row.getString("event_name")),
                ERRORS,
                actor.actorId()));
    }

    @Override
    public RefundSummaryDto create(UserPrincipal actor, CreateRefundRequestDto request, UUID key) {
        String ticketIds = request.ticketIds().stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
        return run(c -> SqlCall.call(
                c,
                "{call dbo.sp_refund_request_for_tickets(?,?,?,?,?,?,?)}",
                RefundRequestRepository::summary,
                ERRORS,
                request.orderId(),
                actor.actorId(),
                ticketIds,
                request.reason(),
                SqlCall.OutParam.of(Types.VARCHAR),
                key,
                actor.authVersion()));
    }

    @Override
    public RefundPageDto list(UserPrincipal actor, int page, int size) {
        return run(c -> {
            long total = SqlCall.call(
                    c,
                    "SELECT COUNT_BIG(*) FROM dbo.v_refunds WHERE owner_id=?",
                    rows -> rows.getLong(1),
                    ERRORS,
                    actor.actorId());
            List<RefundSummaryDto> items = SqlCall.queryList(
                    c,
                    REFUND_PAGE,
                    RefundRequestRepository::summary,
                    ERRORS,
                    actor.actorId(),
                    (page - 1) * size,
                    size);
            return new RefundPageDto(items, total, page, size);
        });
    }

    @Override
    public RefundDetailDto detail(UserPrincipal actor, UUID refundId) {
        return run(c -> {
            RefundRow refund = SqlCall.call(
                    c,
                    "SELECT * FROM dbo.v_refunds WHERE refund_id=?",
                    RefundRequestRepository::refundRow,
                    ERRORS,
                    refundId);
            if (refund == null) {
                throw new RefundException(404, "REFUND_NOT_FOUND", "Yêu cầu hoàn tiền không tồn tại.");
            }
            if (!actor.actorId().toString().equalsIgnoreCase(refund.ownerId())) {
                throw new RefundException(403, "REFUND_FORBIDDEN", "Bạn không có quyền xem yêu cầu này.");
            }

            var tickets = SqlCall.queryList(
                    c,
                    "SELECT * FROM dbo.v_refund_tickets WHERE refund_id=? ORDER BY ticket_id",
                    row -> ticket(row, "Vé thuộc yêu cầu hoàn tiền này."),
                    ERRORS,
                    refundId);
            var attempts = SqlCall.queryList(
                    c,
                    "SELECT * FROM dbo.v_refund_attempts WHERE refund_id=? ORDER BY attempt_no",
                    RefundRequestRepository::attempt,
                    ERRORS,
                    refundId);
            return new RefundDetailDto(refund.summary(), refund.attemptId(), refund.processedAt(), tickets, attempts);
        });
    }

    private <T> T run(SqlCall.SqlWork<T> work) {
        return SqlCall.inTransaction(transactions, DatabasePrincipal.BUYER, work, ERRORS);
    }

    private static void requireOrderOwner(Connection c, UserPrincipal actor, UUID orderId) {
        var ownerId = SqlCall.queryOptional(
                c,
                "SELECT owner_id FROM dbo.v_refundable_orders WHERE order_id=?",
                row -> row.getString(1),
                ERRORS,
                orderId);
        if (ownerId.isEmpty()) {
            throw new RefundException(404, "ORDER_NOT_FOUND", "Đơn hàng không tồn tại.");
        }
        if (!actor.actorId().toString().equalsIgnoreCase(ownerId.get())) {
            throw new RefundException(403, "ORDER_FORBIDDEN", "Bạn không có quyền xem đơn hàng này.");
        }
    }

    private static RefundRow refundRow(ResultSet row) throws SQLException {
        return new RefundRow(
                row.getString("owner_id"),
                summary(row),
                uuid(row, "current_attempt_id"),
                instant(row, "processed_at"));
    }

    private static RefundSummaryDto summary(ResultSet row) throws SQLException {
        return new RefundSummaryDto(
                uuid(row, "refund_id"),
                uuid(row, "order_id"),
                row.getBigDecimal("amount").toPlainString(),
                row.getString("status"),
                row.getInt("ticket_count"),
                instant(row, "created_at"),
                row.getString("event_name"),
                row.getString("reason"),
                row.getString("rejection_reason"));
    }

    private static RefundTicketOptionDto ticket(ResultSet row, String unrefundableReason) throws SQLException {
        String seat = row.getString("seat_label_snapshot");
        return new RefundTicketOptionDto(
                uuid(row, "ticket_id"),
                row.getString("ticket_code"),
                seat == null ? row.getString("zone_name_snapshot") : seat,
                row.getBigDecimal("paid_amount").toPlainString(),
                row.getString("status"),
                unrefundableReason == null,
                unrefundableReason);
    }

    private static RefundDetailDto.Attempt attempt(ResultSet row) throws SQLException {
        return new RefundDetailDto.Attempt(
                uuid(row, "attempt_id"),
                row.getInt("attempt_no"),
                row.getString("status"),
                instant(row, "created_at"),
                instant(row, "processed_at"));
    }
}
