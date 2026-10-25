package vn.ticketscenter.fulfillment.refund.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.SqlCall;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundDecisionRequestDto;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundDetailDto;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundMutationDto;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundPageDto;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundSummaryDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

import static vn.ticketscenter.config.persistence.SqlCall.instant;
import static vn.ticketscenter.config.persistence.SqlCall.uuid;

/**
 * Shared resource-local transaction in production; DataSource constructor enables real isolated fixtures.
 */
public final class AdminRefundRepository implements AdminRefundStore {

    private static final SqlCall.ErrorTable ERRORS = SqlCall.ErrorTable.of()
            .on(new int[] {54100, 51400, 51405}, 400, "REFUND_DECISION_INVALID", "Không thể xử lý hồ sơ hoàn tiền. Vui lòng kiểm tra quyền và trạng thái.")
            .on(54101, 401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.")
            .on(new int[] {54103, 51402}, 403, "ADMIN_REQUIRED", "Bạn không còn quyền quản trị viên.")
            .on(new int[] {54104, 51401}, 404, "REFUND_NOT_FOUND", "Yêu cầu hoàn tiền không tồn tại.")
            .on(new int[] {54109, 51403, 51404, 51406, 51407, 51805, 51806}, 409, "REFUND_DECISION_CONFLICT",
                    "Trạng thái hoàn tiền đã thay đổi hoặc cần đối soát. Hãy tải lại hồ sơ.")
            .fallback((code, msg) -> unavailable());

    private final TransactionManager transactions;
    private final DataSource supplied;

    public AdminRefundRepository(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions);
        this.supplied = null;
    }

    public AdminRefundRepository(DataSource supplied) {
        this.supplied = Objects.requireNonNull(supplied);
        this.transactions = null;
    }

    private record ActorRow(String status, int authVersion, boolean admin) {}

    private record Header(AdminRefundSummaryDto refund, UUID currentAttempt, UUID reviewer, Instant decidedAt) {}

    private <T> T withConnection(SqlCall.SqlWork<T> work) {
        try {
            return transactions != null
                    ? SqlCall.inSession(transactions, DatabasePrincipal.ADMIN, work)
                    : SqlCall.inTransaction(supplied, work, ERRORS);
        } catch (RuntimeException failure) {
            throw SqlCall.failure(failure, ERRORS, AdminRefundRepository::unavailable);
        }
    }

    private static RefundException unavailable() {
        return new RefundException(503, "DATABASE_UNAVAILABLE", "Hệ thống hoàn tiền tạm thời không khả dụng.");
    }

    @Override
    public void requireAdmin(UserPrincipal actor) {
        withConnection(c -> {
            var row = SqlCall.queryOptional(
                    c,
                    "SELECT status, auth_version, is_admin FROM dbo.v_admin_refund_actor WHERE id=?",
                    r -> new ActorRow(r.getString(1), r.getInt(2), r.getBoolean(3)),
                    ERRORS,
                    actor.actorId());
            if (row.isEmpty()
                    || !"ACTIVE".equals(row.get().status())
                    || row.get().authVersion() != actor.authVersion()) {
                throw new RefundException(401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.");
            }
            if (!row.get().admin()) {
                throw new RefundException(403, "ADMIN_REQUIRED", "Bạn không còn quyền quản trị viên.");
            }
            return null;
        });
    }

    @Override
    public AdminRefundPageDto list(String status, int page, int size) {
        return withConnection(c -> {
            String where = status == null ? "" : " WHERE status=?";
            Object[] countArgs = status == null ? new Object[] {} : new Object[] {status};
            long total = SqlCall.call(
                    c,
                    "SELECT COUNT_BIG(*) FROM dbo.v_admin_refund_requests" + where,
                    r -> r.getLong(1),
                    ERRORS,
                    countArgs);
            Object[] pageArgs = status == null
                    ? new Object[] {(page - 1) * size, size}
                    : new Object[] {status, (page - 1) * size, size};
            List<AdminRefundSummaryDto> items = SqlCall.queryList(
                    c,
                    "SELECT * FROM dbo.v_admin_refund_requests" + where
                            + " ORDER BY created_at DESC, refund_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                    AdminRefundRepository::summary,
                    ERRORS,
                    pageArgs);
            return new AdminRefundPageDto(items, total, page, size);
        });
    }

    @Override
    public AdminRefundDetailDto detail(UUID id) {
        return withConnection(c -> {
            var header = SqlCall.call(
                    c,
                    "SELECT * FROM dbo.v_admin_refund_requests WHERE refund_id=?",
                    r -> new Header(
                            summary(r),
                            uuid(r, "current_attempt_id"),
                            uuid(r, "reviewer_id"),
                            instant(r, "decided_at")),
                    ERRORS,
                    id);
            if (header == null) {
                throw new RefundException(404, "REFUND_NOT_FOUND", "Yêu cầu hoàn tiền không tồn tại.");
            }
            var tickets = SqlCall.queryList(
                    c,
                    "SELECT * FROM dbo.v_refund_tickets WHERE refund_id=? ORDER BY ticket_id",
                    r -> new RefundTicketOptionDto(
                            uuid(r, "ticket_id"),
                            r.getString("ticket_code"),
                            r.getString("seat_label_snapshot") == null
                                    ? r.getString("zone_name_snapshot")
                                    : r.getString("seat_label_snapshot"),
                            r.getBigDecimal("paid_amount").toPlainString(),
                            r.getString("status"),
                            false,
                            null),
                    ERRORS,
                    id);

            var attempts = SqlCall.queryList(
                    c,
                    "SELECT * FROM dbo.v_admin_refund_attempts WHERE refund_id=? ORDER BY attempt_no, attempt_id",
                    r -> new AdminRefundDetailDto.Attempt(
                            uuid(r, "attempt_id"),
                            r.getInt("attempt_no"),
                            r.getString("status"),
                            r.getString("provider_reference"),
                            instant(r, "created_at"),
                            instant(r, "processed_at")),
                    ERRORS,
                    id);

            String note = SqlCall.call(
                    c,
                    "SELECT TOP(1) note FROM dbo.vw_AdminRefundApprovalNotes WHERE refund_id=? ORDER BY created_at DESC, id DESC",
                    r -> r.getString(1),
                    ERRORS,
                    id);

            return new AdminRefundDetailDto(
                    header.refund(),
                    header.currentAttempt(),
                    header.reviewer(),
                    header.decidedAt(),
                    note,
                    tickets,
                    attempts);
        });
    }

    @Override
    public AdminRefundMutationDto decide(
            UserPrincipal actor, UUID id, AdminRefundDecisionRequestDto request, UUID key) {
        return withConnection(c -> {
            String proc = "APPROVE".equals(request.decision()) ? "sp_refund_approve" : "sp_refund_reject";
            String noteOrReason = "APPROVE".equals(request.decision()) ? request.note() : request.rejectReason();
            return SqlCall.call(
                    c,
                    "EXEC dbo." + proc + " ?,?,?,?,?",
                    AdminRefundRepository::mutation,
                    ERRORS,
                    id,
                    actor.actorId(),
                    noteOrReason,
                    key,
                    actor.authVersion());
        });
    }

    @Override
    public AdminRefundMutationDto retry(UserPrincipal actor, UUID id, UUID key) {
        return withConnection(c -> SqlCall.call(
                c,
                "EXEC dbo.sp_refund_retry ?,?,?,?",
                AdminRefundRepository::mutation,
                ERRORS,
                id,
                actor.actorId(),
                key,
                actor.authVersion()));
    }

    private static AdminRefundMutationDto mutation(ResultSet r) throws SQLException {
        String csv = r.getString("ticket_ids");
        return new AdminRefundMutationDto(
                uuid(r, "refund_id"),
                r.getString("status"),
                r.getString("canonical_status"),
                uuid(r, "attempt_id"),
                uuid(r, "buyer_id"),
                csv == null ? List.of() : Arrays.stream(csv.split(",")).map(UUID::fromString).toList());
    }

    private static AdminRefundSummaryDto summary(ResultSet r) throws SQLException {
        return new AdminRefundSummaryDto(
                uuid(r, "refund_id"),
                uuid(r, "order_id"),
                uuid(r, "buyer_id"),
                r.getString("buyer_name"),
                r.getString("buyer_email"),
                r.getString("event_name"),
                r.getBigDecimal("amount").toPlainString(),
                r.getInt("ticket_count"),
                r.getString("reason"),
                r.getString("status"),
                instant(r, "created_at"),
                r.getString("rejection_reason"),
                r.getBoolean("can_decide"),
                r.getBoolean("can_retry"));
    }
}
