package vn.ticketscenter.fulfillment.checkin.repository;

import org.hibernate.Session;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.checkin.CheckInException;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CheckInScanRepository {
    public record ScanResult(String result, Instant scannedAt, UUID ticketId) { }

    private final TransactionManager transactions;

    public CheckInScanRepository(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    protected CheckInScanRepository() { this.transactions = null; }

    public ScanResult scanSignedTicket(UUID eventId, UUID actorId, UUID ticketId) {
        return scan("{call dbo.usp_CheckInSignedTicket(?, ?, ?)}", eventId, actorId, ticketId);
    }

    public ScanResult scanTicketCode(UUID eventId, UUID actorId, String ticketCode) {
        return scan("{call dbo.usp_CheckInTicket(?, ?, ?)}", eventId, actorId, ticketCode);
    }

    private ScanResult scan(String procedure, UUID eventId, UUID actorId, Object ticket) {
        try {
            return transactions.execute(DatabasePrincipal.CHECK_IN, entityManager ->
                    entityManager.unwrap(Session.class).doReturningWork(connection -> {
                        try (CallableStatement statement = connection.prepareCall(procedure)) {
                            statement.setQueryTimeout(10);
                            statement.setObject(1, eventId);
                            statement.setObject(2, actorId);
                            statement.setObject(3, ticket);
                            try (ResultSet rows = statement.executeQuery()) {
                                if (!rows.next()) throw new SQLException("Check-in result missing");
                                Object value = rows.getObject("ticket_id");
                                return new ScanResult(rows.getString("result"),
                                        rows.getTimestamp("scanned_at").toInstant(),
                                        value == null ? null : UUID.fromString(value.toString()));
                            }
                        }
                    }));
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql) {
                    if (sql.getErrorCode() == 51241) throw new CheckInException(403, "CHECK_IN_FORBIDDEN", "Bạn không có quyền soát vé tại sự kiện này.");
                    if (sql.getErrorCode() == 51240) throw new CheckInException(404, "EVENT_NOT_FOUND", "Sự kiện không tồn tại.");
                }
            }
            throw new CheckInException(503, "CHECK_IN_UNAVAILABLE", "Hệ thống soát vé tạm thời không khả dụng.");
        }
    }
}
