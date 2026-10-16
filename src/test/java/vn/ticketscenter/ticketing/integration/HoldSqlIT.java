package vn.ticketscenter.ticketing.integration;

import org.json.JSONObject;
import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.ticketing.cache.*;
import vn.ticketscenter.ticketing.dto.*;
import vn.ticketscenter.ticketing.dao.HoldRepository;
import vn.ticketscenter.ticketing.service.*;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

class HoldSqlIT {
    HoldFixture f;

    @BeforeEach void fixture() throws Exception { f = new HoldFixture(); }
    @AfterEach void cleanup() throws Exception { if (f != null) f.close(); }

    @Test void restrictedPrincipalsUseOnlyFacadesAndSqlOwnsExactly600SecondReceipt() throws Exception {
        try (Connection buyer = f.buyer.getConnection(); Statement query = buyer.createStatement()) {
            assertThrows(SQLException.class, () -> query.executeQuery("SELECT * FROM dbo.tc_ticket_holds"));
            assertThrows(SQLException.class, () -> query.executeQuery("SELECT auth_version FROM dbo.tc_users"));
        }
        try (Connection worker = f.worker.getConnection(); Statement query = worker.createStatement()) {
            assertThrows(SQLException.class, () -> query.executeQuery("SELECT * FROM dbo.tc_ticket_holds"));
        }
        HoldCommand.Create command = f.standing(2);
        var receipt = f.mutate(f.actor, command);
        JSONObject body = new JSONObject(receipt.body());
        assertEquals(201, receipt.httpStatus());
        assertEquals("CREATED", body.getString("outcome"));
        assertEquals(600, Duration.between(Instant.parse(body.getString("createdAt")), Instant.parse(body.getString("expiresAt"))).getSeconds());
        assertEquals("150000", body.getJSONArray("items").getJSONObject(0).getString("unitPrice"));
        assertEquals(2, f.count("SELECT held_quantity FROM dbo.tc_zones WHERE id=?", f.inventory.standing()));
        try (Jedis jedis = f.redis.getResource()) {
            long ttl = jedis.pttl(RedisHoldIdempotency.redisKey(f.actor.id(), command.key().toString()));
            assertTrue(ttl > 0 && ttl <= 600_000);
        }
        assertEquals(HoldFixture.holdId(receipt), f.service.current(f.actor).projection().orElseThrow().id());
        JSONObject options = new JSONObject(f.service.options(f.actor, f.inventory.event()));
        assertEquals(2, options.getJSONArray("zones").length());
        for (Object value : options.getJSONArray("zones")) {
            JSONObject zone = (JSONObject)value;
            assertInstanceOf(org.json.JSONArray.class, zone.get("availableSeats"));
        }
    }

    @Test void snapshotsIncludeSelectedDisplayLabelsAndHistoricalReplayKeepsThem() throws Exception {
        var create = new HoldCommand.Create(UUID.randomUUID(), new CreateHoldRequest(f.inventory.event(), List.of(
                new HoldSelection(f.inventory.standing(), null, 2),
                new HoldSelection(f.inventory.seated(), f.inventory.seat(), 1))));
        var receipt = f.mutate(f.actor, create);
        JSONObject body = new JSONObject(receipt.body());
        assertEquals("Hold fixture", body.getString("eventTitle"));
        assertEquals(2, body.getJSONArray("items").length());
        for (Object value : body.getJSONArray("items")) {
            JSONObject item = (JSONObject) value;
            if (item.isNull("seatId")) {
                assertEquals("Floor", item.getString("zoneName"));
                assertTrue(item.has("seatLabel") && item.isNull("seatLabel"));
                assertEquals(2, item.getInt("quantity"));
            } else {
                assertEquals(f.inventory.seat().toString(), item.getString("seatId"));
                assertEquals("Balcony", item.getString("zoneName"));
                assertEquals("A1", item.getString("seatLabel"));
            }
        }
        JSONObject current = new JSONObject(f.service.current(f.actor).body()).getJSONObject("hold");
        assertEquals("Hold fixture", current.getString("eventTitle"));
        assertEquals(body.getJSONArray("items").toString(), current.getJSONArray("items").toString());
        f.mutate(f.actor, new HoldCommand.Cancel(UUID.randomUUID(), HoldFixture.holdId(receipt)));
        assertEquals(receipt.body(), f.mutate(f.actor, create).body());
    }

    @Test void tenBuyersHaveOneSeatWinner() throws Exception {
        List<HoldActor> buyers = buyers(10);
        List<Object> results = race(10, index -> attempt(() -> f.mutate(buyers.get(index), f.seat())));
        assertEquals(1, results.stream().filter(HoldMutationReceipt.class::isInstance).count());
        assertEquals(9, conflicts(results));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_hold_items WHERE seat_id=?", f.inventory.seat()));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_seats WHERE id=? AND status='HELD'", f.inventory.seat()));
    }

    @Test void standingCapacityNeverOversellsAndMixedSelectionRollsBack() throws Exception {
        List<HoldActor> buyers = buyers(10);
        List<Object> results = race(10, index -> attempt(() -> f.mutate(buyers.get(index), f.standing(2))));
        assertEquals(4, results.stream().filter(HoldMutationReceipt.class::isInstance).count());
        assertEquals(6, conflicts(results));
        assertEquals(8, f.count("SELECT held_quantity FROM dbo.tc_zones WHERE id=?", f.inventory.standing()));
        var mixed = new HoldCommand.Create(UUID.randomUUID(), new CreateHoldRequest(f.inventory.event(), List.of(
                new HoldSelection(f.inventory.seated(), f.inventory.seat(), 1), new HoldSelection(f.inventory.standing(), null, 1))));
        assertEquals(409, assertThrows(HoldException.class, () -> f.mutate(f.actor, mixed)).status());
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_seats WHERE id=? AND status='AVAILABLE'", f.inventory.seat()));
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE user_id=?", f.actor.id()));
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_request_idempotency WHERE actor_id=?", f.actor.id()));
    }

    @Test void sameKeyRaceReplaysOneImmutableResultAndDifferentPayloadConflicts() throws Exception {
        HoldCommand.Create command = f.standing(1);
        List<Object> results = race(10, index -> f.mutate(f.actor, command));
        Set<String> bodies = new HashSet<>();
        for (Object result : results) bodies.add(((HoldMutationReceipt) result).body());
        assertEquals(1, bodies.size());
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE user_id=?", f.actor.id()));
        var changed = f.standing(command.key(), f.inventory, 2);
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(HoldException.class, () -> f.mutate(f.actor, changed)).code());
        UUID hold = HoldFixture.holdId((HoldMutationReceipt) results.getFirst());
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(HoldException.class,
                () -> f.mutate(f.actor, new HoldCommand.Cancel(command.key(), hold))).code());
    }

    @Test void twoActorKeysAcrossEventsLeaveOneActiveHold() throws Exception {
        var another = f.inventory(8);
        List<HoldCommand.Create> commands = List.of(f.standing(1), f.standing(UUID.randomUUID(), another, 1));
        List<Object> results = race(2, index -> attempt(() -> f.mutate(f.actor, commands.get(index))));
        assertEquals(1, results.stream().filter(HoldMutationReceipt.class::isInstance).count());
        assertEquals(1, conflicts(results));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE user_id=? AND status='ACTIVE'", f.actor.id()));
    }

    @Test void unknownCommitAndRedisLossRecoverSameCommandWithoutReservingAgain() throws Exception {
        var command = f.standing(2);
        f.buyer.loseNextAcknowledgement.set(true);
        var first = f.mutate(f.actor, command);
        assertTrue(first.replayed());
        try (Jedis jedis = f.redis.getResource()) { jedis.del(RedisHoldIdempotency.redisKey(f.actor.id(), command.key().toString())); }
        var replay = f.mutate(f.actor, command);
        assertEquals(first.body(), replay.body());
        assertTrue(replay.replayed());
        assertEquals(2, f.count("SELECT held_quantity FROM dbo.tc_zones WHERE id=?", f.inventory.standing()));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_request_idempotency WHERE actor_id=?", f.actor.id()));
        HoldService withoutRedis = new HoldService(f.repository,
                new RedisActiveHoldCache(() -> { throw new IllegalStateException("offline"); }, Clock.systemUTC()),
                new RedisHoldIdempotency(() -> { throw new IllegalStateException("offline"); }));
        assertEquals(first.body(), withoutRedis.mutate(f.actor, command).body());
        assertEquals(HoldFixture.holdId(first), withoutRedis.current(f.actor).projection().orElseThrow().id());
    }

    @Test void historicalReceiptSurvivesReleaseButCurrentAuthorizationAndOwnershipAreAlwaysChecked() throws Exception {
        var create = f.standing(1);
        var receipt = f.mutate(f.actor, create);
        UUID hold = HoldFixture.holdId(receipt);
        var cancel = new HoldCommand.Cancel(UUID.randomUUID(), hold);
        var cancelled = f.mutate(f.actor, cancel);
        assertEquals("CANCELLED", new JSONObject(cancelled.body()).getString("outcome"));
        assertEquals(receipt.body(), f.mutate(f.actor, create).body());
        assertTrue(f.service.current(f.actor).projection().isEmpty());
        var unchanged = f.mutate(f.actor, new HoldCommand.Cancel(UUID.randomUUID(), hold));
        assertTrue(unchanged.changes().isEmpty());
        HoldActor stranger = f.actor();
        assertEquals(404, assertThrows(HoldException.class,
                () -> f.mutate(stranger, new HoldCommand.Cancel(UUID.randomUUID(), hold))).status());
        assertEquals(404, assertThrows(HoldException.class,
                () -> f.mutate(stranger, new HoldCommand.Cancel(UUID.randomUUID(), UUID.randomUUID()))).status());
        f.sql("UPDATE dbo.tc_users SET auth_version=auth_version+1 WHERE id=?", f.actor.id());
        assertEquals(401, assertThrows(HoldException.class, () -> f.mutate(f.actor, create)).status());
        assertEquals(401, assertThrows(HoldException.class, () -> f.mutate(f.actor, cancel)).status());
        assertEquals(401, assertThrows(HoldException.class, () -> f.service.current(f.actor)).status());
        f.sql("UPDATE dbo.tc_users SET email_verified_at=NULL WHERE id=?", f.actor.id());
        var currentActor = new HoldActor(f.actor.id(), 1);
        assertEquals(403, assertThrows(HoldException.class, () -> f.mutate(currentActor, create)).status());
        f.sql("UPDATE dbo.tc_users SET email_verified_at=SYSUTCDATETIME(),status='DISABLED' WHERE id=?", f.actor.id());
        assertEquals(403, assertThrows(HoldException.class, () -> f.mutate(currentActor, create)).status());
        assertEquals(403, assertThrows(HoldException.class, () -> f.service.current(currentActor)).status());
    }

    @Test void expiredCrossEventHoldIsReleasedBeforeReplacementAndStaleCancelDoesNotLockNewEvent() throws Exception {
        var old = f.mutate(f.actor, f.standing(2));
        UUID oldHold = HoldFixture.holdId(old);
        f.expire(oldHold);
        var other = f.inventory(8);
        var fresh = f.mutate(f.actor, f.standing(UUID.randomUUID(), other, 1));
        assertEquals(2, fresh.changes().size());
        assertEquals(0, f.count("SELECT held_quantity FROM dbo.tc_zones WHERE id=?", f.inventory.standing()));
        assertEquals(1, f.count("SELECT held_quantity FROM dbo.tc_zones WHERE id=?", other.standing()));
        try (Connection blocker = f.owner.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement("UPDATE dbo.tc_events SET version=version WHERE id=?")) {
                lock.setString(1, other.event().toString()); lock.executeUpdate();
            }
            var result = executor.submit(() -> f.mutate(f.actor, new HoldCommand.Cancel(UUID.randomUUID(), oldHold)));
            assertTrue(result.get(3, TimeUnit.SECONDS).changes().isEmpty());
            blocker.rollback();
        }
        assertEquals(HoldFixture.holdId(fresh), f.service.current(f.actor).projection().orElseThrow().id());
    }

    @Test void saleEndCrossedWhileWaitingDoesNotCreateExpiredSaleHold() throws Exception {
        f.sql("UPDATE dbo.tc_events SET sale_end=DATEADD(second,2,SYSUTCDATETIME()) WHERE id=?", f.inventory.event());
        try (Connection blocker = f.owner.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement("UPDATE dbo.tc_events SET version=version WHERE id=?")) {
                lock.setString(1, f.inventory.event().toString()); lock.executeUpdate();
            }
            var result = executor.submit(() -> attempt(() -> f.mutate(f.actor, f.standing(1))));
            assertThrows(TimeoutException.class, () -> result.get(200, TimeUnit.MILLISECONDS));
            try (Statement wait = blocker.createStatement()) { wait.execute("WAITFOR DELAY '00:00:03'"); }
            blocker.commit();
            assertEquals(409, ((HoldException) result.get(10, TimeUnit.SECONDS)).status());
        }
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE user_id=?", f.actor.id()));
    }

    @Test void holdExpiryCrossedWhileWaitingAllowsCrossEventReplacement() throws Exception {
        UUID old = HoldFixture.holdId(f.mutate(f.actor, f.standing(2)));
        f.sql("UPDATE dbo.tc_ticket_holds SET created_at=DATEADD(second,-598,SYSUTCDATETIME()),expires_at=DATEADD(second,2,SYSUTCDATETIME()) WHERE id=?", old);
        var other = f.inventory(8);
        try (Connection blocker = f.owner.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement("UPDATE dbo.tc_events SET version=version WHERE id=?")) {
                lock.setString(1, f.inventory.event().toString()); lock.executeUpdate();
            }
            var result = executor.submit(() -> f.mutate(f.actor, f.standing(UUID.randomUUID(), other, 1)));
            assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
            try (Statement wait = blocker.createStatement()) { wait.execute("WAITFOR DELAY '00:00:03'"); }
            blocker.commit();
            assertEquals(201, result.get(10, TimeUnit.SECONDS).httpStatus());
        }
        assertEquals(0, f.count("SELECT held_quantity FROM dbo.tc_zones WHERE id=?", f.inventory.standing()));
        assertEquals(1, f.count("SELECT held_quantity FROM dbo.tc_zones WHERE id=?", other.standing()));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE user_id=? AND status='ACTIVE'", f.actor.id()));
    }

    @Test void oppositeOrderPaymentWriterCausesKnownDeadlockAndCancelRecoversSameCommand() throws Exception {
        UUID hold = HoldFixture.holdId(f.mutate(f.actor, f.standing(2)));
        UUID order;
        try (Connection connection = f.buyer.getConnection(); PreparedStatement create = connection.prepareStatement("EXEC dbo.usp_CreateOrderFromHold ?,?")) {
            create.setString(1, hold.toString()); create.setString(2, f.actor.id().toString());
            try (ResultSet rows = create.executeQuery()) { assertTrue(rows.next()); order = UUID.fromString(rows.getString("order_id")); }
        }
        try (var executor = Executors.newSingleThreadExecutor(); Connection payment = f.owner.getConnection()) {
            payment.setAutoCommit(false);
            int session;
            try (Statement query = payment.createStatement()) {
                query.execute("SET DEADLOCK_PRIORITY HIGH");
                try (ResultSet rows = query.executeQuery("SELECT @@SPID")) { rows.next(); session = rows.getInt(1); }
            }
            try (PreparedStatement lock = payment.prepareStatement("UPDATE dbo.tc_orders SET version=version WHERE id=?")) {
                lock.setString(1, order.toString()); lock.executeUpdate();
            }
            var cancel = executor.submit(() -> f.mutate(f.actor, new HoldCommand.Cancel(UUID.randomUUID(), hold)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (f.count("SELECT COUNT(*) FROM sys.dm_exec_requests WHERE blocking_session_id=? AND wait_type LIKE 'LCK%'", session) == 0
                    && System.nanoTime() < deadline) Thread.sleep(50);
            assertTrue(f.count("SELECT COUNT(*) FROM sys.dm_exec_requests WHERE blocking_session_id=? AND wait_type LIKE 'LCK%'", session) > 0);
            try (PreparedStatement begin = payment.prepareStatement("EXEC dbo.usp_BeginOrderPayment ?,?")) {
                begin.setQueryTimeout(15);
                begin.setString(1, order.toString()); begin.setString(2, f.actor.id().toString());
                try (ResultSet rows = begin.executeQuery()) { assertTrue(rows.next()); assertNotNull(rows.getString("payment_id")); }
            }
            payment.commit();
            assertEquals(200, cancel.get(20, TimeUnit.SECONDS).httpStatus());
        }
        assertTrue(f.buyer.deadlocks.get() >= 1);
        assertEquals(0, f.count("SELECT held_quantity FROM dbo.tc_zones WHERE id=?", f.inventory.standing()));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_orders WHERE id=? AND status='CANCELLED'", order));
    }

    @Test void legacyCanonicalCreateChangesPreflightEventSetAndFacadeRetriesWholeTransaction() throws Exception {
        var other = f.inventory(8);
        try (var executor = Executors.newSingleThreadExecutor(); Connection blocker = f.owner.getConnection()) {
            blocker.setAutoCommit(false);
            int session;
            try (Statement query = blocker.createStatement(); ResultSet rows = query.executeQuery("SELECT @@SPID")) { rows.next(); session = rows.getInt(1); }
            try (PreparedStatement lock = blocker.prepareStatement("UPDATE dbo.tc_events SET version=version WHERE id=?")) {
                lock.setString(1, other.event().toString()); lock.executeUpdate();
            }
            var result = executor.submit(() -> attempt(() -> f.mutate(f.actor, f.standing(UUID.randomUUID(), other, 1))));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (f.count("SELECT COUNT(*) FROM sys.dm_exec_requests WHERE blocking_session_id=? AND wait_type LIKE 'LCK%'", session) == 0
                    && System.nanoTime() < deadline) Thread.sleep(50);
            assertTrue(f.count("SELECT COUNT(*) FROM sys.dm_exec_requests WHERE blocking_session_id=? AND wait_type LIKE 'LCK%'", session) > 0);
            try (Connection canonical = f.buyer.getConnection(); PreparedStatement create = canonical.prepareStatement("EXEC dbo.usp_CreateTicketHold ?,?,?")) {
                create.setString(1, f.actor.id().toString()); create.setString(2, f.inventory.event().toString());
                create.setString(3, "[{\"zoneId\":\"" + f.inventory.standing() + "\",\"quantity\":1}]");
                try (ResultSet rows = create.executeQuery()) { assertTrue(rows.next()); assertNotNull(rows.getString("hold_id")); }
            }
            blocker.rollback();
            assertEquals(409, ((HoldException)result.get(15, TimeUnit.SECONDS)).status());
        }
        assertTrue(f.buyer.preflightChanges.get() >= 1);
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE user_id=? AND status='ACTIVE'", f.actor.id()));
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_request_idempotency WHERE actor_id=?", f.actor.id()));
    }

    @Test void twoWorkersReleaseOnceAndProtectConsumedHold() throws Exception {
        UUID expired = HoldFixture.holdId(f.mutate(f.actor, f.standing(2)));
        f.expire(expired);
        HoldActor another = f.actor();
        UUID consumed = HoldFixture.holdId(f.mutate(another, f.seat()));
        f.sql("UPDATE dbo.tc_ticket_holds SET status='CONSUMED' WHERE id=?", consumed);
        f.expire(consumed);
        List<Object> results = race(2, index -> new HoldRepository(f.buyer, f.worker).expire(50).size());
        assertEquals(1, results.stream().mapToInt(value -> (int)value).sum());
        assertEquals(0, f.count("SELECT held_quantity FROM dbo.tc_zones WHERE id=?", f.inventory.standing()));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE id=? AND status='CONSUMED'", consumed));
        assertEquals(409, assertThrows(HoldException.class,
                () -> f.mutate(another, new HoldCommand.Cancel(UUID.randomUUID(), consumed))).status());
        assertEquals(0, f.service.expire(50));
    }

    @Test void rollbackAndSqlOutageNeverInvalidateCacheOrReturnCachedAuthority() throws Exception {
        UUID hold = HoldFixture.holdId(f.mutate(f.actor, f.standing(2)));
        f.service.current(f.actor);
        String key = RedisActiveHoldCache.redisKey(f.actor.id());
        String before;
        try (Jedis jedis = f.redis.getResource()) { before = jedis.get(key); }
        f.sql("UPDATE dbo.tc_zones SET held_quantity=0 WHERE id=?", f.inventory.standing());
        assertEquals(503, assertThrows(HoldException.class,
                () -> f.mutate(f.actor, new HoldCommand.Cancel(UUID.randomUUID(), hold))).status());
        try (Jedis jedis = f.redis.getResource()) { assertEquals(before, jedis.get(key)); }
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE id=? AND status='ACTIVE'", hold));
        f.buyer.unavailable = true;
        assertEquals(503, assertThrows(HoldException.class, () -> f.service.current(f.actor)).status());
        assertEquals(503, assertThrows(HoldException.class, () -> f.service.options(f.actor, f.inventory.event())).status());
        f.buyer.unavailable = false;
    }

    @Test void cancelBetweenSqlReadAndRedisFillRejectsOldSnapshot() throws Exception {
        UUID hold = HoldFixture.holdId(f.mutate(f.actor, f.standing(1)));
        f.buyer.afterCurrentRead = () -> f.mutate(f.actor, new HoldCommand.Cancel(UUID.randomUUID(), hold));
        f.service.current(f.actor);
        f.buyer.afterCurrentRead = () -> {};
        assertTrue(f.cache.lookup(f.actor.id()).projection().isEmpty());
        assertTrue(f.service.current(f.actor).projection().isEmpty());
    }

    @Test void sqlAndRedisSupportFullCanonical19DigitPrice() throws Exception {
        f.sql("UPDATE dbo.tc_events SET status='DRAFT' WHERE id=?", f.inventory.event());
        f.sql("UPDATE dbo.tc_zones SET price=9999999999999999999 WHERE id=?", f.inventory.standing());
        f.sql("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?", f.inventory.event());
        var result = f.mutate(f.actor, f.standing(1));
        assertEquals("9999999999999999999", new JSONObject(result.body()).getJSONArray("items").getJSONObject(0).getString("unitPrice"));
        assertEquals("9999999999999999999", f.service.current(f.actor).projection().orElseThrow().items().getFirst().unitPrice().toPlainString());
        assertEquals("9999999999999999999", f.cache.lookup(f.actor.id()).projection().orElseThrow().items().getFirst().unitPrice().toPlainString());
    }

    @Test void largeSeatedCatalogReturnsEveryCanonicalSeatAndCanReserveSelectedUuid() throws Exception {
        f.sql("UPDATE dbo.tc_events SET status='DRAFT' WHERE id=?", f.inventory.event());
        f.sql("WITH numbers AS(SELECT TOP(1100) ROW_NUMBER() OVER(ORDER BY object_id) n FROM sys.all_objects)"
                + " INSERT dbo.tc_seats(id,zone_id,row_name,seat_number,label)"
                + " SELECT NEWID(),?,'B',CONVERT(int,n)+1,'B'+CONVERT(varchar(10),n) FROM numbers", f.inventory.seated());
        f.sql("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?", f.inventory.event());
        String catalog = f.service.options(f.actor, f.inventory.event());
        assertTrue(catalog.length() > 64 * 1024);
        org.json.JSONArray seats = null;
        for (Object value : new JSONObject(catalog).getJSONArray("zones")) {
            JSONObject zone = (JSONObject)value;
            if (zone.getString("type").equals("SEATED")) seats = zone.getJSONArray("availableSeats");
        }
        assertNotNull(seats);
        assertEquals(1101, seats.length());
        Set<UUID> ids = new HashSet<>();
        for (Object value : seats) ids.add(UUID.fromString(((JSONObject)value).getString("id")));
        assertEquals(1101, ids.size());
        UUID chosen = UUID.fromString(seats.getJSONObject(1100).getString("id"));
        var command = new HoldCommand.Create(UUID.randomUUID(), new CreateHoldRequest(f.inventory.event(),
                List.of(new HoldSelection(f.inventory.seated(), chosen, 1))));
        var created = f.mutate(f.actor, command);
        assertEquals(chosen.toString(), new JSONObject(created.body()).getJSONArray("items").getJSONObject(0).getString("seatId"));
    }

    private List<HoldActor> buyers(int count) throws SQLException {
        List<HoldActor> buyers = new ArrayList<>();
        for (int i = 0; i < count; i++) buyers.add(f.actor());
        return buyers;
    }

    private static long conflicts(List<Object> results) {
        for (Object result : results) if (result instanceof HoldException failure) assertEquals(409, failure.status());
        return results.stream().filter(HoldException.class::isInstance).count();
    }

    private static Object attempt(Callable<?> action) {
        try { return action.call(); }
        catch (HoldException failure) { return failure; }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private static List<Object> race(int count, IntFunction<Object> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(count), start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(count)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(workers.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException(); return action.apply(index); }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(40, TimeUnit.SECONDS));
            return results;
        }
    }
}
