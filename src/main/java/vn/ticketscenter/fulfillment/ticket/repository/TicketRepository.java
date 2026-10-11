package vn.ticketscenter.fulfillment.ticket.repository;

import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;

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
        Objects.requireNonNull(buyerId, "buyerId is required");
        String sql = """
                SELECT t.id, t.ticket_code, t.paid_amount, t.status, t.issued_at, t.version,
                       oi.order_id, oi.zone_name_snapshot, oi.seat_label_snapshot,
                       e.title AS event_title, e.venue_name, e.start_time
                FROM dbo.tc_tickets t WITH (NOLOCK)
                JOIN dbo.tc_order_items oi WITH (NOLOCK) ON oi.id = t.order_item_id
                JOIN dbo.tc_orders o WITH (NOLOCK) ON o.id = oi.order_id
                JOIN dbo.tc_events e WITH (NOLOCK) ON e.id = o.event_id
                WHERE o.user_id = ?
                ORDER BY t.issued_at DESC
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
                            toInstant(rs.getTimestamp("start_time")),
                            rs.getString("zone_name_snapshot"),
                            rs.getString("seat_label_snapshot"),
                            rs.getLong("paid_amount"),
                            rs.getString("status"),
                            toInstant(rs.getTimestamp("issued_at")),
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
        Objects.requireNonNull(ticketId, "ticketId is required");
        String sql = """
                SELECT t.id, t.ticket_code, t.paid_amount, t.status, t.issued_at, t.version,
                       oi.order_id, oi.zone_name_snapshot, oi.seat_label_snapshot,
                       o.order_code, o.user_id AS buyer_id,
                       e.id AS event_id, e.title AS event_title, e.venue_name, e.venue_address, e.start_time, e.end_time
                FROM dbo.tc_tickets t WITH (NOLOCK)
                JOIN dbo.tc_order_items oi WITH (NOLOCK) ON oi.id = t.order_item_id
                JOIN dbo.tc_orders o WITH (NOLOCK) ON o.id = oi.order_id
                JOIN dbo.tc_events e WITH (NOLOCK) ON e.id = o.event_id
                WHERE t.id = ?
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
                            toInstant(rs.getTimestamp("start_time")),
                            toInstant(rs.getTimestamp("end_time")),
                            rs.getString("zone_name_snapshot"),
                            rs.getString("seat_label_snapshot"),
                            rs.getLong("paid_amount"),
                            rs.getString("status"),
                            toInstant(rs.getTimestamp("issued_at")),
                            rs.getLong("version")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query ticket by id", e);
        }
        return Optional.empty();
    }

    /**
     * Lấy tất cả các vé thuộc về một đơn hàng (orderId).
     */
    public List<TicketRecord> findTicketsByOrderId(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId is required");
        String sql = """
                SELECT t.id, t.ticket_code, t.paid_amount, t.status, t.issued_at, t.version,
                       oi.order_id, oi.zone_name_snapshot, oi.seat_label_snapshot,
                       o.order_code, o.user_id AS buyer_id,
                       e.id AS event_id, e.title AS event_title, e.venue_name, e.venue_address, e.start_time, e.end_time
                FROM dbo.tc_tickets t WITH (NOLOCK)
                JOIN dbo.tc_order_items oi WITH (NOLOCK) ON oi.id = t.order_item_id
                JOIN dbo.tc_orders o WITH (NOLOCK) ON o.id = oi.order_id
                JOIN dbo.tc_events e WITH (NOLOCK) ON e.id = o.event_id
                WHERE o.id = ?
                ORDER BY t.issued_at ASC
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
                            toInstant(rs.getTimestamp("start_time")),
                            toInstant(rs.getTimestamp("end_time")),
                            rs.getString("zone_name_snapshot"),
                            rs.getString("seat_label_snapshot"),
                            rs.getLong("paid_amount"),
                            rs.getString("status"),
                            toInstant(rs.getTimestamp("issued_at")),
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
