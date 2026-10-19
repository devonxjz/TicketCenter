package vn.ticketscenter.fulfillment.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.ticket.cache.TicketCache;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import vn.ticketscenter.fulfillment.ticket.dao.TicketRepository;
import vn.ticketscenter.fulfillment.ticket.dao.TicketRepository.TicketRecord;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TicketServiceTest {
    private static final UUID BUYER_A = UUID.randomUUID();
    private static final UUID BUYER_B = UUID.randomUUID();
    private static final String SECRET = "unit-test-secret-key-999";

    private static class StubTicketRepository extends TicketRepository {
        final Map<UUID, TicketRecord> records = new HashMap<>();

        public StubTicketRepository() {
            super();
        }

        @Override
        public List<TicketSummaryDto> findTicketsByBuyerId(UUID buyerId) {
            return records.values().stream()
                    .filter(r -> r.buyerId().equals(buyerId))
                    .map(TicketRecord::toSummaryDto)
                    .toList();
        }

        @Override
        public List<TicketSummaryDto> findTicketsByBuyerId(UUID buyerId, int authVersion) {
            return findTicketsByBuyerId(buyerId);
        }

        @Override
        public Optional<TicketRecord> findById(UUID ticketId) {
            return Optional.ofNullable(records.get(ticketId));
        }

        @Override
        public Optional<TicketRecord> findById(UUID ticketId, UUID buyerId, int authVersion) {
            return findById(ticketId);
        }
    }

    private static class StubTicketCache implements TicketCache {
        final Map<UUID, TicketDetailDto> details = new HashMap<>();
        final Map<String, byte[]> qrMap = new HashMap<>();
        int qrCacheHits = 0;

        @Override
        public Optional<TicketDetailDto> getTicketDetail(UUID ticketId) {
            return Optional.ofNullable(details.get(ticketId));
        }

        @Override
        public void putTicketDetail(TicketDetailDto ticket) {
            details.put(ticket.id(), ticket);
        }

        @Override
        public Optional<byte[]> getTicketQrPng(UUID ticketId, long version) {
            byte[] bytes = qrMap.get(ticketId + ":" + version);
            if (bytes != null) qrCacheHits++;
            return Optional.ofNullable(bytes);
        }

        @Override
        public void putTicketQrPng(UUID ticketId, long version, byte[] pngBytes) {
            qrMap.put(ticketId + ":" + version, pngBytes);
        }

        @Override
        public void invalidate(UUID ticketId) {
            details.remove(ticketId);
            qrMap.keySet().removeIf(k -> k.startsWith(ticketId.toString()));
        }
    }

    private StubTicketRepository repository;
    private StubTicketCache cache;
    private SignedTicketQrSigner signer;
    private TicketService service;

    @BeforeEach
    void setUp() {
        repository = new StubTicketRepository();
        cache = new StubTicketCache();
        signer = new SignedTicketQrSigner(SECRET);
        service = new TicketService(repository, signer, cache);
    }

    @Test
    void getMyTicketsRequiresActorIdAndReturnsTickets() {
        assertThrows(TicketException.class, () -> service.getMyTickets(null));

        UUID ticket1 = UUID.randomUUID();
        UUID ticket2 = UUID.randomUUID();

        repository.records.put(ticket1, new TicketRecord(
                ticket1, UUID.randomUUID(), BUYER_A, "ORD-1", "TCK-1",
                UUID.randomUUID(), "Show A", "Venue A", "Address A",
                Instant.now(), null, "VIP", "Seat 1", 100000L, "ACTIVE", Instant.now(), 1L
        ));
        repository.records.put(ticket2, new TicketRecord(
                ticket2, UUID.randomUUID(), BUYER_B, "ORD-2", "TCK-2",
                UUID.randomUUID(), "Show B", "Venue B", "Address B",
                Instant.now(), null, "Standard", null, 50000L, "ACTIVE", Instant.now(), 1L
        ));

        List<TicketSummaryDto> ticketsA = service.getMyTickets(BUYER_A);
        assertEquals(1, ticketsA.size());
        assertEquals("TCK-1", ticketsA.get(0).ticketCode());

        List<TicketSummaryDto> ticketsB = service.getMyTickets(BUYER_B);
        assertEquals(1, ticketsB.size());
        assertEquals("TCK-2", ticketsB.get(0).ticketCode());
    }

    @Test
    void getTicketDetailEnforcesOwnership() {
        UUID ticketId = UUID.randomUUID();
        repository.records.put(ticketId, new TicketRecord(
                ticketId, UUID.randomUUID(), BUYER_A, "ORD-1", "TCK-1",
                UUID.randomUUID(), "Show A", "Venue A", "Address A",
                Instant.now(), null, "VIP", "Seat 1", 100000L, "ACTIVE", Instant.now(), 1L
        ));

        // Chủ sở hữu truy cập -> Thành công
        TicketDetailDto detail = service.getTicketDetail(BUYER_A, ticketId);
        assertNotNull(detail);
        assertEquals("TCK-1", detail.ticketCode());

        // Người dùng khác cố tình truy cập -> 403 Forbidden
        TicketException ex = assertThrows(TicketException.class, () -> service.getTicketDetail(BUYER_B, ticketId));
        assertEquals(403, ex.status());

        // Vé không tồn tại -> 404 Not Found
        TicketException notFound = assertThrows(TicketException.class, () -> service.getTicketDetail(BUYER_A, UUID.randomUUID()));
        assertEquals(404, notFound.status());
    }

    @Test
    void detailReflectsCurrentCheckInStateEvenWhenCached() {
        UUID ticketId = UUID.randomUUID();
        TicketRecord active = new TicketRecord(ticketId, UUID.randomUUID(), BUYER_A, "ORD-1", "TCK-1",
                UUID.randomUUID(), "Show A", "Venue A", "Address A", Instant.now(), null,
                "VIP", "Seat 1", 100000L, "ACTIVE", Instant.now(), 1L);
        repository.records.put(ticketId, active);
        assertEquals("ACTIVE", service.getTicketDetail(BUYER_A, ticketId).status());
        repository.records.put(ticketId, new TicketRecord(ticketId, active.orderId(), BUYER_A, "ORD-1", "TCK-1",
                active.eventId(), "Show A", "Venue A", "Address A", active.startTime(), null,
                "VIP", "Seat 1", 100000L, "USED", active.issuedAt(), 2L));
        assertEquals("USED", service.getTicketDetail(BUYER_A, ticketId).status());
    }

    @Test
    void getTicketQrPngGeneratesSignedQrAndCachesPngBytes() {
        UUID ticketId = UUID.randomUUID();
        repository.records.put(ticketId, new TicketRecord(
                ticketId, UUID.randomUUID(), BUYER_A, "ORD-1", "TCK-1",
                UUID.randomUUID(), "Concert 2026", "Stadium", "Hanoi",
                Instant.now(), null, "Zone VIP", "A-01", 200000L, "ACTIVE", Instant.now(), 1L
        ));

        // Lần gọi đầu tiên: sinh QR và cache
        byte[] png1 = service.getTicketQrPng(BUYER_A, ticketId, 300, 300);
        assertNotNull(png1);
        assertTrue(png1.length > 0);
        assertEquals(0, cache.qrCacheHits);

        // Lần gọi thứ 2: lấy từ cache
        byte[] png2 = service.getTicketQrPng(BUYER_A, ticketId, 300, 300);
        assertArrayEquals(png1, png2);
        assertEquals(1, cache.qrCacheHits);

        // Người lạ không được lấy QR -> 403
        assertThrows(TicketException.class, () -> service.getTicketQrPng(BUYER_B, ticketId, 300, 300));
    }

    @Test
    void invalidatesCacheCorrectly() {
        UUID ticketId = UUID.randomUUID();
        repository.records.put(ticketId, new TicketRecord(
                ticketId, UUID.randomUUID(), BUYER_A, "ORD-1", "TCK-1",
                UUID.randomUUID(), "Concert", "Venue", "Address",
                Instant.now(), null, "Zone", null, 100000L, "ACTIVE", Instant.now(), 1L
        ));

        service.getTicketQrPng(BUYER_A, ticketId, 300, 300);
        assertTrue(cache.qrMap.containsKey(ticketId + ":1"));

        service.invalidate(ticketId);
        assertFalse(cache.qrMap.containsKey(ticketId + ":1"));
    }
}
