package vn.ticketscenter.fulfillment.ticket.repository;

import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import vn.ticketscenter.fulfillment.ticket.service.TicketException;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

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
            long version
    ) {
        public TicketDetailDto toDetailDto() {
            return new TicketDetailDto(
                    id, orderId, orderCode, ticketCode, eventId, eventTitle,
                    venueName, venueAddress, startTime, endTime, zoneName,
                    seatLabel, paidAmount, status, issuedAt, version
            );
        }

        public TicketSummaryDto toSummaryDto() {
            return new TicketSummaryDto(
                    id, orderId, ticketCode, eventTitle, venueName,
                    startTime, zoneName, seatLabel, paidAmount, status,
                    issuedAt, version
            );
        }
    }

    /**
     * Lấy danh sách tất cả vé thuộc về người mua (buyerId).
     */
    public List<TicketSummaryDto> findTicketsByBuyerId(UUID buyerId) {
        return findTicketsByBuyerId(buyerId, -1);
    }

    public List<TicketSummaryDto> findTicketsByBuyerId(UUID buyerId, int authVersion) {
        Objects.requireNonNull(buyerId, "buyerId is required");
        String sql = """
                SELECT id,ticket_code,paid_amount,status,issued_at,version,
                       order_id,zone_name_snapshot,seat_label_snapshot,event_title,venue_name,start_time
                FROM dbo.vw_TicketRuntimeDetails WHERE buyer_id=? ORDER BY issued_at DESC
                """;

        List<TicketSummaryDto> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TicketSummaryDto(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("order_id")),
                            rs.getString("ticket_code"),
                            rs.getString("event_title"),
                            rs.getString("venue_name"),
                            toInstant(rs.getTimestamp("start_time", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))),
                            rs.getString("zone_name_snapshot"),
                            rs.getString("seat_label_snapshot"),
                            rs.getLong("paid_amount"),
                            rs.getString("status"),
                            toInstant(rs.getTimestamp("issued_at", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))),
                            rs.getLong("version")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query tickets by buyerId", e);
        }
        return result;
    }

    /**
     * Tìm thông tin đầy đủ của một vé theo ticketId.
     */
    public Optional<TicketRecord> findById(UUID ticketId) {
        return findById(ticketId, null, -1);
    }

    public Optional<TicketRecord> findById(UUID ticketId, UUID buyerId, int authVersion) {
        Objects.requireNonNull(ticketId, "ticketId is required");
        String sql = """
                SELECT * FROM dbo.vw_TicketRuntimeDetails WHERE id=?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new TicketRecord(
                                UUID.fromString(rs.getString("id")),
                                UUID.fromString(rs.getString("order_id")),
                                UUID.fromString(rs.getString("buyer_id")),
                                rs.getString("order_code"),
                                rs.getString("ticket_code"),
                                UUID.fromString(rs.getString("event_id")),
                                rs.getString("event_title"),
                                rs.getString("venue_name"),
                                rs.getString("venue_address"),
                                toInstant(rs.getTimestamp("start_time", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))),
                                toInstant(rs.getTimestamp("end_time", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))),
                                rs.getString("zone_name_snapshot"),
                                rs.getString("seat_label_snapshot"),
                                rs.getLong("paid_amount"),
                                rs.getString("status"),
                                toInstant(rs.getTimestamp("issued_at", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))),
                                rs.getLong("version")
                        ));
                    }
                return Optional.empty();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to query ticket by id", e);
            }
        }

    public void assertTicketAccess(Connection conn, UUID ticketId) {
        if (conn == null) return;
        Objects.requireNonNull(ticketId, "ticketId is required");
        try (CallableStatement cs = conn.prepareCall("{call dbo.usp_AssertTicketAccess(?)}")) {
            cs.setObject(1, ticketId);
            cs.execute();
        } catch (SQLException e) {
            int code = e.getErrorCode();
            if (code == 51403) {
                throw TicketException.forbidden();
            }
            if (code == 51404) {
                throw TicketException.notFound();
            }
            throw TicketException.unavailable();
        }
    }

    public void assertTicketAccess(UUID ticketId, UUID actorId) {
        if (dataSource == null) return;
        Objects.requireNonNull(ticketId, "ticketId is required");
        try (Connection conn = dataSource.getConnection()) {
            assertTicketAccess(conn, ticketId);
        } catch (SQLException e) {
            throw TicketException.unavailable();
        }
    }

    /**
     * Lấy tất cả các vé thuộc về một đơn hàng (orderId).
     */
    public List<TicketRecord> findTicketsByOrderId(UUID orderId) {
        return findTicketsByOrderId(orderId, null, -1);
    }

    public List<TicketRecord> findTicketsByOrderId(UUID orderId, UUID buyerId, int authVersion) {
        Objects.requireNonNull(orderId, "orderId is required");
        String sql = """
                SELECT * FROM dbo.vw_TicketRuntimeDetails WHERE order_id=? ORDER BY issued_at ASC
                """;

        List<TicketRecord> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TicketRecord(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("order_id")),
                            UUID.fromString(rs.getString("buyer_id")),
                            rs.getString("order_code"),
                            rs.getString("ticket_code"),
                            UUID.fromString(rs.getString("event_id")),
                            rs.getString("event_title"),
                            rs.getString("venue_name"),
                            rs.getString("venue_address"),
                            toInstant(rs.getTimestamp("start_time", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))),
                            toInstant(rs.getTimestamp("end_time", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))),
                            rs.getString("zone_name_snapshot"),
                            rs.getString("seat_label_snapshot"),
                            rs.getLong("paid_amount"),
                            rs.getString("status"),
                            toInstant(rs.getTimestamp("issued_at", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))),
                            rs.getLong("version")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query tickets by orderId", e);
        }
        return result;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
