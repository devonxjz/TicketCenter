package vn.ticketscenter.fulfillment.ticket.dao;

import vn.ticketscenter.config.persistence.SqlCall;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import vn.ticketscenter.fulfillment.ticket.service.TicketException;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

import static vn.ticketscenter.config.persistence.SqlCall.instant;

/**
 * Kho dữ liệu truy vấn vé từ SQL Server (LIEM-05).
 */
public class TicketRepository {
    private final DataSource dataSource;

    public TicketRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    protected TicketRepository() {
        this.dataSource = null;
    }

    public record TicketRecord(
            UUID id,
            UUID orderId,
            UUID buyerId,
            String orderCode,
            String ticketCode,
            UUID eventId,
            String eventTitle,
            String venueName,
            String venueAddress,
            Instant startTime,
            Instant endTime,
            String zoneName,
            String seatLabel,
            long paidAmount,
            String status,
            Instant issuedAt,
            long version) {
        public TicketDetailDto toDetailDto() {
            return new TicketDetailDto(
                    id,
                    orderId,
                    orderCode,
                    ticketCode,
                    eventId,
                    eventTitle,
                    venueName,
                    venueAddress,
                    startTime,
                    endTime,
                    zoneName,
                    seatLabel,
                    paidAmount,
                    status,
                    issuedAt,
                    version);
        }

        public TicketSummaryDto toSummaryDto() {
            return new TicketSummaryDto(
                    id,
                    orderId,
                    ticketCode,
                    eventTitle,
                    venueName,
                    startTime,
                    zoneName,
                    seatLabel,
                    paidAmount,
                    status,
                    issuedAt,
                    version);
        }
    }

    private static final SqlCall.ErrorTable ACCESS_ERRORS =
            SqlCall.ErrorTable.of()
                    .on(51403, m -> TicketException.forbidden())
                    .on(51404, m -> TicketException.notFound())
                    .fallback((code, message) -> TicketException.unavailable());

    /**
     * Lấy danh sách tất cả vé thuộc về người mua (buyerId).
     */
    public List<TicketSummaryDto> findTicketsByBuyerId(UUID buyerId) {
        return findTicketsByBuyerId(buyerId, -1);
    }

    public List<TicketSummaryDto> findTicketsByBuyerId(UUID buyerId, int authVersion) {
        Objects.requireNonNull(buyerId, "buyerId is required");
        return query(
                """
                SELECT id,ticket_code,paid_amount,status,issued_at,version,
                       order_id,zone_name_snapshot,seat_label_snapshot,event_title,venue_name,start_time
                FROM dbo.vw_TicketRuntimeDetails WHERE buyer_id=? ORDER BY issued_at DESC
                """,
                rs ->
                        new TicketSummaryDto(
                                UUID.fromString(rs.getString("id")),
                                UUID.fromString(rs.getString("order_id")),
                                rs.getString("ticket_code"),
                                rs.getString("event_title"),
                                rs.getString("venue_name"),
                                instant(rs, "start_time"),
                                rs.getString("zone_name_snapshot"),
                                rs.getString("seat_label_snapshot"),
                                rs.getLong("paid_amount"),
                                rs.getString("status"),
                                instant(rs, "issued_at"),
                                rs.getLong("version")),
                "Failed to query tickets by buyerId",
                buyerId);
    }

    /**
     * Tìm thông tin đầy đủ của một vé theo ticketId.
     */
    public Optional<TicketRecord> findById(UUID ticketId) {
        return findById(ticketId, null, -1);
    }

    public Optional<TicketRecord> findById(UUID ticketId, UUID buyerId, int authVersion) {
        Objects.requireNonNull(ticketId, "ticketId is required");
        return query(
                        "SELECT * FROM dbo.vw_TicketRuntimeDetails WHERE id=?",
                        TicketRepository::ticket,
                        "Failed to query ticket by id",
                        ticketId)
                .stream()
                .findFirst();
    }

    public void assertTicketAccess(Connection conn, UUID ticketId) {
        if (conn == null) return;
        Objects.requireNonNull(ticketId, "ticketId is required");
        SqlCall.execute(conn, "{call dbo.usp_AssertTicketAccess(?)}", ACCESS_ERRORS, ticketId);
    }

    public void assertTicketAccess(UUID ticketId, UUID actorId) {
        if (dataSource == null) return;
        Objects.requireNonNull(ticketId, "ticketId is required");
        SqlCall.onConnection(
                dataSource,
                conn -> {
                    assertTicketAccess(conn, ticketId);
                    return null;
                },
                ACCESS_ERRORS);
    }

    /**
     * Lấy tất cả các vé thuộc về một đơn hàng (orderId).
     */
    public List<TicketRecord> findTicketsByOrderId(UUID orderId) {
        return findTicketsByOrderId(orderId, null, -1);
    }

    public List<TicketRecord> findTicketsByOrderId(UUID orderId, UUID buyerId, int authVersion) {
        Objects.requireNonNull(orderId, "orderId is required");
        return query(
                "SELECT * FROM dbo.vw_TicketRuntimeDetails WHERE order_id=? ORDER BY issued_at ASC",
                TicketRepository::ticket,
                "Failed to query tickets by orderId",
                orderId);
    }

    private static TicketRecord ticket(ResultSet rs) throws SQLException {
        return new TicketRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("order_id")),
                UUID.fromString(rs.getString("buyer_id")),
                rs.getString("order_code"),
                rs.getString("ticket_code"),
                UUID.fromString(rs.getString("event_id")),
                rs.getString("event_title"),
                rs.getString("venue_name"),
                rs.getString("venue_address"),
                instant(rs, "start_time"),
                instant(rs, "end_time"),
                rs.getString("zone_name_snapshot"),
                rs.getString("seat_label_snapshot"),
                rs.getLong("paid_amount"),
                rs.getString("status"),
                instant(rs, "issued_at"),
                rs.getLong("version"));
    }

    /** Reads every row; a database failure surfaces as IllegalStateException, as the callers expect. */
    private <T> List<T> query(String sql, SqlCall.RowMapper<T> mapper, String failure, UUID id) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            List<T> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(mapper.map(rs));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException(failure, e);
        }
    }
}
