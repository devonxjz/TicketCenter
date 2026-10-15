package vn.ticketscenter.fulfillment.checkin.repository;

import org.hibernate.Session;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;
import vn.ticketscenter.fulfillment.checkin.dto.RecentCheckInLogDto;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TicketCheckInRepository {
    private final TransactionManager transactions;

    public TicketCheckInRepository(TransactionManager transactions) { this.transactions = transactions; }

    public CheckInScanResponseDto scan(UUID actor, UUID key, UUID event, UUID ticket, String code,
            String name, String fingerprint, boolean invalid) {
        return work(connection -> {
            try (CallableStatement query = connection.prepareCall("{call dbo.sp_ticket_check_in(?,?,?,?,?,?,?,?)}")) {
                query.setString(1, event.toString());
                query.setString(2, actor.toString());
                query.setString(3, ticket == null ? null : ticket.toString());
                query.setString(4, code);
                query.setNString(5, name);
                query.setString(6, key.toString());
                query.setString(7, fingerprint);
                query.setBoolean(8, invalid);
                query.setQueryTimeout(15);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Check-in result missing");
                    return new CheckInScanResponseDto(rows.getString("result"), id(rows, "ticket_id"),
                            rows.getString("ticket_code"), rows.getString("customer_name"), rows.getString("zone_name"),
                            rows.getString("seat_label"), instant(rows, "checked_in_at"), instant(rows, "scanned_at"),
                            id(rows, "organization_id"));
                }
            }
        });
    }

    public List<RecentCheckInLogDto> recent(UUID actor, UUID event, int limit) {
        return work(connection -> {
            try (CallableStatement query = connection.prepareCall("{call dbo.sp_checkin_recent_logs(?,?,?)}")) {
                query.setString(1, event.toString());
                query.setString(2, actor.toString());
                query.setInt(3, limit);
                query.setQueryTimeout(5);
                try (ResultSet rows = query.executeQuery()) {
                    List<RecentCheckInLogDto> logs = new ArrayList<>();
                    while (rows.next()) {
                        logs.add(new RecentCheckInLogDto(id(rows, "id"), id(rows, "ticket_id"), rows.getString("result"),
                                instant(rows, "scanned_at"), rows.getString("staff_name"), rows.getString("zone_name"),
                                rows.getString("seat_label")));
                    }
                    return List.copyOf(logs);
                }
            }
        });
    }

    private <T> T work(SqlWork<T> work) {
        try {
            return transactions.execute(DatabasePrincipal.CHECK_IN,
                    entityManager -> entityManager.unwrap(Session.class).doReturningWork(work::run));
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (!(cause instanceof SQLException sql)) continue;
                switch (sql.getErrorCode()) {
                    case 53201 -> throw new CheckInException(403, "CHECK_IN_FORBIDDEN",
                            "Bạn không có quyền soát vé tại sự kiện này.");
                    case 53202 -> throw new CheckInException(404, "EVENT_NOT_FOUND", "Sự kiện không tồn tại.");
                    case 53203 -> throw new CheckInException(409, "IDEMPOTENCY_CONFLICT",
                            "Mã yêu cầu đã dùng cho một thao tác khác.");
                }
            }
            throw new CheckInException(503, "CHECK_IN_UNAVAILABLE",
                    "Hệ thống soát vé tạm thời không khả dụng. Hãy thử lại cùng mã yêu cầu.");
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private static UUID id(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        if (rows.getObject(column) == null) return null;
        // SQL Server DATETIMEOFFSET must retain its offset.
        if (rows.getMetaData().getColumnType(rows.findColumn(column)) == -155) {
            return rows.getObject(column, OffsetDateTime.class).toInstant();
        }
        return rows.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
}
