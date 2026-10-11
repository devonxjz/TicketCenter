package vn.ticketscenter.fulfillment.ticket.cache;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RedisTicketCacheTest {
    private static class MockJedis extends Jedis {
        final Map<String, String> store = new HashMap<>();
        boolean failNext = false;

        @Override
        public String setex(String key, long seconds, String value) {
            if (failNext) throw new RuntimeException("Redis connection error");
            store.put(key, value);
            return "OK";
        }

        @Override
        public String get(String key) {
            if (failNext) throw new RuntimeException("Redis connection error");
            return store.get(key);
        }

        @Override
        public long del(String key) {
            if (failNext) throw new RuntimeException("Redis connection error");
            return store.remove(key) != null ? 1 : 0;
        }

        @Override
        public long del(String... keys) {
            if (failNext) throw new RuntimeException("Redis connection error");
            long count = 0;
            for (String k : keys) {
                if (store.remove(k) != null) count++;
            }
            return count;
        }

        @Override
        public ScanResult<String> scan(String cursor, ScanParams params) {
            if (failNext) throw new RuntimeException("Redis connection error");
            List<String> keys = new ArrayList<>(store.keySet());
            return new ScanResult<>("0", keys);
        }

        @Override
        public void close() { }
    }

    @Test
    void cachesAndRetrievesTicketDetail() {
        MockJedis jedis = new MockJedis();
        RedisTicketCache cache = new RedisTicketCache(() -> jedis);

        UUID ticketId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        TicketDetailDto dto = new TicketDetailDto(
                ticketId, orderId, "ORD-123", "TCK-999", UUID.randomUUID(),
                "Event Music", "Grand Theater", "123 Street",
                Instant.now(), Instant.now().plusSeconds(3600), "VIP", "Row A",
                500000L, "ACTIVE", Instant.now(), 1L
        );

        assertTrue(cache.getTicketDetail(ticketId).isEmpty());
        cache.putTicketDetail(dto);

        var retrieved = cache.getTicketDetail(ticketId);
        assertTrue(retrieved.isPresent());
        assertEquals(dto.ticketCode(), retrieved.get().ticketCode());
        assertEquals(dto.eventTitle(), retrieved.get().eventTitle());
        assertEquals(dto.paidAmount(), retrieved.get().paidAmount());
    }

    @Test
    void cachesAndRetrievesQrPngWithVersioning() {
        MockJedis jedis = new MockJedis();
        RedisTicketCache cache = new RedisTicketCache(() -> jedis);

        UUID ticketId = UUID.randomUUID();
        byte[] pngV1 = new byte[]{1, 2, 3, 4};
        byte[] pngV2 = new byte[]{5, 6, 7, 8};

        assertTrue(cache.getTicketQrPng(ticketId, 1L).isEmpty());
        cache.putTicketQrPng(ticketId, 1L, pngV1);
        cache.putTicketQrPng(ticketId, 2L, pngV2);

        var retrievedV1 = cache.getTicketQrPng(ticketId, 1L);
        var retrievedV2 = cache.getTicketQrPng(ticketId, 2L);

        assertTrue(retrievedV1.isPresent());
        assertArrayEquals(pngV1, retrievedV1.get());
        assertTrue(retrievedV2.isPresent());
        assertArrayEquals(pngV2, retrievedV2.get());
        assertTrue(cache.getTicketQrPng(ticketId, 3L).isEmpty());
    }

    @Test
    void invalidatesTicketDetailAndQrKeys() {
        MockJedis jedis = new MockJedis();
        RedisTicketCache cache = new RedisTicketCache(() -> jedis);

        UUID ticketId = UUID.randomUUID();
        TicketDetailDto dto = new TicketDetailDto(
                ticketId, UUID.randomUUID(), "ORD-123", "TCK-999", UUID.randomUUID(),
                "Event Music", "Grand Theater", "123 Street",
                Instant.now(), Instant.now().plusSeconds(3600), "VIP", "Row A",
                500000L, "ACTIVE", Instant.now(), 1L
        );

        cache.putTicketDetail(dto);
        cache.putTicketQrPng(ticketId, 1L, new byte[]{1, 2, 3});

        assertTrue(cache.getTicketDetail(ticketId).isPresent());
        assertTrue(cache.getTicketQrPng(ticketId, 1L).isPresent());

        cache.invalidate(ticketId);

        assertTrue(cache.getTicketDetail(ticketId).isEmpty());
        assertTrue(cache.getTicketQrPng(ticketId, 1L).isEmpty());
    }

    @Test
    void degradesGracefullyWhenRedisFails() {
        MockJedis jedis = new MockJedis();
        RedisTicketCache cache = new RedisTicketCache(() -> jedis);
        jedis.failNext = true;

        UUID id = UUID.randomUUID();
        // Không ném Exception ra ngoài
        assertDoesNotThrow(() -> cache.getTicketDetail(id));
        assertDoesNotThrow(() -> cache.getTicketQrPng(id, 1L));
        assertDoesNotThrow(() -> cache.invalidate(id));
        assertTrue(cache.getTicketDetail(id).isEmpty());
    }
}
