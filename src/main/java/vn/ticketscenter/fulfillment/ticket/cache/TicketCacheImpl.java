package vn.ticketscenter.fulfillment.ticket.cache;

import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;

import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import vn.ticketscenter.config.redis.RedisScan;

/**
 * Hiện thực bộ nhớ đệm Redis cho chi tiết vé và ảnh PNG QR (LIEM-05).
 * - Key chi tiết: tc:ticket:detail:v1:<id>
 * - Key QR: tc:ticket:qr:v1:<id>:<version>
 * - TTL: 1800 giây (30 phút).
 * - Tự động thoái lui khi Redis offline; Database luôn là Source of Truth.
 */
public final class TicketCacheImpl implements TicketCache {
    public static final int DEFAULT_TTL_SECONDS = 1800;

    private final Supplier<Jedis> connections;

    public TicketCacheImpl(Supplier<Jedis> connections) {
        this.connections = Objects.requireNonNull(connections, "connections supplier is required");
    }

    public static String detailKey(UUID ticketId) {
        return "tc:ticket:detail:v1:" + ticketId;
    }

    public static String qrKey(UUID ticketId, long version) {
        return "tc:ticket:qr:v1:" + ticketId + ":" + version;
    }

    @Override
    public Optional<TicketDetailDto> getTicketDetail(UUID ticketId) {
        if (ticketId == null) return Optional.empty();
        try (Jedis redis = connections.get()) {
            String value = redis.get(detailKey(ticketId));
            if (value == null || value.isBlank()) return Optional.empty();
            return Optional.of(decodeDetail(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void putTicketDetail(TicketDetailDto ticket) {
        if (ticket == null) return;
        try (Jedis redis = connections.get()) {
            redis.setex(detailKey(ticket.id()), DEFAULT_TTL_SECONDS, ticket.toJson());
        } catch (Exception ignored) {
            // Redis chỉ đóng vai trò tăng tốc độ đọc
        }
    }

    @Override
    public Optional<byte[]> getTicketQrPng(UUID ticketId, long version) {
        if (ticketId == null) return Optional.empty();
        try (Jedis redis = connections.get()) {
            String value = redis.get(qrKey(ticketId, version));
            if (value == null || value.isBlank()) return Optional.empty();
            return Optional.of(Base64.getDecoder().decode(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void putTicketQrPng(UUID ticketId, long version, byte[] pngBytes) {
        if (ticketId == null || pngBytes == null || pngBytes.length == 0) return;
        try (Jedis redis = connections.get()) {
            String encoded = Base64.getEncoder().encodeToString(pngBytes);
            redis.setex(qrKey(ticketId, version), DEFAULT_TTL_SECONDS, encoded);
        } catch (Exception ignored) {
            // Không chặn luồng chính nếu cache lỗi
        }
    }

    @Override
    public void invalidate(UUID ticketId) {
        if (ticketId == null) return;
        try (Jedis redis = connections.get()) {
            redis.del(detailKey(ticketId));
            // Xóa tất cả version của QR thuộc vé này
            RedisScan.deleteMatching(redis, "tc:ticket:qr:v1:" + ticketId + ":*");
        } catch (Exception ignored) {
            // Lỗi cache không làm gián đoạn hệ thống
        }
    }

    private static TicketDetailDto decodeDetail(String json) {
        JSONObject obj = new JSONObject(json);
        return new TicketDetailDto(
                UUID.fromString(obj.getString("id")),
                UUID.fromString(obj.getString("orderId")),
                obj.optString("orderCode", ""),
                obj.getString("ticketCode"),
                obj.has("eventId") && !obj.getString("eventId").isBlank()
                        ? UUID.fromString(obj.getString("eventId"))
                        : null,
                obj.getString("eventTitle"),
                obj.optString("venueName", ""),
                obj.optString("venueAddress", ""),
                obj.has("startTime") && !obj.getString("startTime").isBlank()
                        ? Instant.parse(obj.getString("startTime"))
                        : null,
                obj.has("endTime") && !obj.getString("endTime").isBlank()
                        ? Instant.parse(obj.getString("endTime"))
                        : null,
                obj.optString("zoneName", ""),
                obj.optString("seatLabel", ""),
                obj.getLong("paidAmount"),
                obj.getString("status"),
                obj.has("issuedAt") && !obj.getString("issuedAt").isBlank()
                        ? Instant.parse(obj.getString("issuedAt"))
                        : null,
                obj.optLong("version", 0));
    }
}
