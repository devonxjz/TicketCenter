package vn.ticketscenter.order.integration;

import org.json.JSONObject;
import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.order.cache.RedisOrderCache;
import vn.ticketscenter.order.dto.*;
import vn.ticketscenter.order.service.*;
import vn.ticketscenter.ticketing.dto.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

class OrderSqlIT {
    OrderFixture f;
    @BeforeEach void setup() throws Exception { f = new OrderFixture(); }
    @AfterEach void cleanup() throws Exception { if (f != null) f.close(); }
    record Checkout(OrderActor actor, UUID hold, UUID order, OrderCommand.Create command, OrderMutationReceipt receipt) { }

    Checkout create(HoldActor buyer, OrderFixture.Inventory stock) {
        UUID hold = OrderFixture.holdId(f.mutate(buyer, f.standing(UUID.randomUUID(), stock, 1)));
        var actor = new OrderActor(buyer.id(), buyer.authVersion());
        var command = new OrderCommand.Create(UUID.randomUUID(), new CreateOrderRequest(hold));
        var receipt = f.orderService.mutate(actor, command);
        return new Checkout(actor, hold, receipt.orderId(), command, receipt);
    }
    Checkout create() { return create(f.actor, f.inventory); }
    OrderCommand.ChangeCoupon change(Checkout checkout, String code) {
        return new OrderCommand.ChangeCoupon(UUID.randomUUID(), code == null ? new CouponMutation.RemoveCoupon(checkout.order()) : new CouponMutation.SetCoupon(checkout.order(), code));
    }
    OrderMutationReceipt apply(Checkout checkout, String code) { return f.orderService.mutate(checkout.actor(), change(checkout, code)); }
    UUID coupon(String code, int maxUses, String type, String amount) throws SQLException {
        UUID id = UUID.randomUUID(); f.coupons.add(id);
        f.sql("INSERT dbo.tc_coupons(id,organization_id,code,discount_type,percentage_value,fixed_amount,max_uses,valid_from,valid_to)"
                + " VALUES(?,?,?,?,?,?,?,DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()))",
                id, f.organization, code, type, type.equals("PERCENTAGE") ? amount : null, type.equals("FIXED_AMOUNT") ? amount : null, maxUses);
        return id;
    }
    JSONObject current(Checkout checkout) { return new JSONObject(f.orderService.checkout(checkout.actor(), checkout.hold()).body()).getJSONObject("order"); }

    @Test void oneOrderPerHoldKeepsPriceSnapshotAndActiveHoldUnderDifferentKeys() throws Exception {
        Checkout checkout = create();
        JSONObject original = new JSONObject(checkout.receipt().body());
        assertEquals("150000", original.getString("totalAmount"));
        assertEquals(f.inventory.standing().toString(), original.getJSONArray("items").getJSONObject(0).getString("zoneId"));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE id=? AND status='ACTIVE'", checkout.hold()));
        f.sql("UPDATE dbo.tc_zones SET price=999999 WHERE id=?", f.inventory.standing());
        var repeated = f.orderService.mutate(checkout.actor(), new OrderCommand.Create(UUID.randomUUID(), new CreateOrderRequest(checkout.hold())));
        assertEquals(checkout.order(), repeated.orderId());
        assertEquals("150000", current(checkout).getString("totalAmount"));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_orders WHERE hold_id=?", checkout.hold()));
        assertEquals(checkout.receipt().body(), f.orderService.mutate(checkout.actor(), checkout.command()).body());
    }

    @Test void replayRecoversLostCommitAndHistoricalReceiptCannotReplaceCurrentCoupon() throws Exception {
        Checkout checkout = create(); coupon("A", 2, "PERCENTAGE", "20"); coupon("B", 2, "PERCENTAGE", "10");
        var command = change(checkout, "A"); f.buyer.loseNextAcknowledgement.set(true);
        var first = f.orderService.mutate(checkout.actor(), command);
        assertTrue(first.replayed()); assertEquals("30000", new JSONObject(first.body()).getString("discountAmount"));
        apply(checkout, "B"); f.orderService.checkout(checkout.actor(), checkout.hold());
        assertEquals(first.body(), f.orderService.mutate(checkout.actor(), command).body());
        assertTrue(f.orderCache.lookup(checkout.actor().id(), checkout.hold()).order().isEmpty());
        assertEquals("B", current(checkout).getString("appliedCouponCode"));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE order_id=? AND status='RESERVED'", checkout.order()));
    }

    @Test void currentAuthorizationPrecedesExistingOrderReadAndEveryReplay() throws Exception {
        Checkout checkout = create(); var other = new OrderActor(f.actor().id(), 0);
        assertEquals(403, assertThrows(OrderException.class, () -> f.orderService.checkout(other, checkout.hold())).status());
        assertEquals(403, assertThrows(OrderException.class, () -> f.orderService.mutate(other, new OrderCommand.Create(UUID.randomUUID(), new CreateOrderRequest(checkout.hold())))).status());
        assertEquals(403, assertThrows(OrderException.class, () -> f.orderService.eligibility(other, checkout.order(), "A")).status());
        f.sql("UPDATE dbo.tc_users SET auth_version=auth_version+1 WHERE id=?", checkout.actor().id());
        assertEquals(401, assertThrows(OrderException.class, () -> f.orderService.mutate(checkout.actor(), checkout.command())).status());
        var currentActor = new OrderActor(checkout.actor().id(), 1);
        f.sql("UPDATE dbo.tc_users SET email_verified_at=NULL WHERE id=?", checkout.actor().id());
        assertEquals(403, assertThrows(OrderException.class, () -> f.orderService.mutate(currentActor, checkout.command())).status());
        assertEquals(403, assertThrows(OrderException.class, () -> f.orderService.checkout(currentActor, checkout.hold())).status());
    }

    @Test void currentOrderOwnershipIsRequiredEvenWhenHoldOwnerStillMatches() throws Exception {
        Checkout checkout=create();UUID foreign=f.actor().id();
        f.sql("UPDATE dbo.tc_orders SET user_id=? WHERE id=?",foreign,checkout.order());
        try {
            assertEquals(403,assertThrows(OrderException.class,() -> current(checkout)).status());
            assertEquals(403,assertThrows(OrderException.class,() -> f.orderService.mutate(checkout.actor(),checkout.command())).status());
            assertEquals(403,assertThrows(OrderException.class,() -> apply(checkout,"A")).status());
        } finally { f.sql("UPDATE dbo.tc_orders SET user_id=? WHERE id=?",checkout.actor().id(),checkout.order()); }
    }

    @Test void keyFingerprintConflictIncludesOperationTargetAndExactCode() throws Exception {
        Checkout checkout = create(); coupon("A", 2, "PERCENTAGE", "20");
        var applied = change(checkout, "A"); f.orderService.mutate(checkout.actor(), applied);
        var changed = new OrderCommand.ChangeCoupon(applied.key(), new CouponMutation.RemoveCoupon(checkout.order()));
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(OrderException.class, () -> f.orderService.mutate(checkout.actor(), changed)).code());
        var crossOperation = new OrderCommand.ChangeCoupon(checkout.command().key(), new CouponMutation.SetCoupon(checkout.order(), "A"));
        assertEquals(409, assertThrows(OrderException.class, () -> f.orderService.mutate(checkout.actor(), crossOperation)).status());
        assertEquals("A", current(checkout).getString("appliedCouponCode"));
    }

    @Test void replaceRemoveAndFailedReplaceHaveOneAtomicReservation() throws Exception {
        Checkout first = create(); Checkout second = create(f.actor(), f.inventory(2));
        UUID a = coupon("A", 2, "PERCENTAGE", "20"), b = coupon("B", 1, "FIXED_AMOUNT", "999999");
        apply(first, "A"); apply(second, "B");
        assertEquals("45000", current(second).getString("discountAmount"));
        assertEquals("COUPON_QUOTA_EXHAUSTED", assertThrows(OrderException.class, () -> apply(first, "B")).code());
        assertEquals("A", current(first).getString("appliedCouponCode"));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=? AND status='RESERVED'", a));
        long version = current(first).getLong("version"); apply(first, "A");
        assertEquals(version, current(first).getLong("version"));
        apply(second, null); apply(first, "B");
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=? AND status='RESERVED'", a));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=? AND status='RESERVED'", b));
        apply(first, null); long removedVersion = current(first).getLong("version"); apply(first, null);
        assertEquals(removedVersion, current(first).getLong("version"));
        assertTrue(current(first).isNull("appliedCouponCode")); assertEquals("150000", current(first).getString("totalAmount"));
    }

    @Test void validReservationRetainsTermsAfterCouponConfigurationChanges() throws Exception {
        Checkout checkout = create(); UUID a = coupon("A", 1, "PERCENTAGE", "20"); apply(checkout, "A");
        long version = current(checkout).getLong("version");
        f.sql("UPDATE dbo.tc_coupons SET active=0,percentage_value=5,valid_to=DATEADD(second,-1,SYSUTCDATETIME()) WHERE id=?", a);
        apply(checkout, "A");
        assertEquals(version, current(checkout).getLong("version")); assertEquals("30000", current(checkout).getString("discountAmount"));
        JSONObject preview = new JSONObject(f.orderService.eligibility(checkout.actor(), checkout.order(), "A"));
        assertTrue(preview.getBoolean("eligible")); assertEquals(0, preview.getInt("remainingUses"));
        assertEquals("30000", preview.getString("discountAmount")); assertEquals(current(checkout).getString("expiresAt"), preview.getString("validUntil"));
    }

    @Test void eligibilityNeverReservesAndReportsCanonicalTimeAndOrganizationReasons() throws Exception {
        Checkout checkout = create(); UUID a = coupon("A", 1, "PERCENTAGE", "20");
        var preview = new JSONObject(f.orderService.eligibility(checkout.actor(), checkout.order(), "A"));
        assertTrue(preview.getBoolean("eligible")); assertEquals("30000", preview.getString("discountAmount"));
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=?", a));
        assertEquals("NOT_FOUND", new JSONObject(f.orderService.eligibility(checkout.actor(), checkout.order(), "missing")).getString("reason"));
        UUID foreignOrg = UUID.randomUUID();
        f.sql("INSERT dbo.tc_organizations(id,name,contact_email,status) VALUES(?,'Other',?,'APPROVED')", foreignOrg, foreignOrg + "@example.test");
        try {
            f.sql("UPDATE dbo.tc_coupons SET organization_id=? WHERE id=?", foreignOrg, a);
            assertEquals("WRONG_ORGANIZATION", new JSONObject(f.orderService.eligibility(checkout.actor(), checkout.order(), "A")).getString("reason"));
        } finally { f.sql("UPDATE dbo.tc_coupons SET organization_id=? WHERE id=?", f.organization, a); f.sql("DELETE dbo.tc_organizations WHERE id=?", foreignOrg); }
        f.sql("UPDATE dbo.tc_coupons SET valid_from=DATEADD(hour,1,SYSUTCDATETIME()) WHERE id=?", a);
        assertEquals("COUPON_NOT_STARTED", assertThrows(OrderException.class, () -> apply(checkout, "A")).code());
        assertThrows(SQLException.class, () -> f.sql("UPDATE dbo.tc_coupons SET percentage_value=31 WHERE id=?", a));
    }

    @Test void twentyDistinctOrdersAcrossEventsCompeteForOneCouponUse() throws Exception {
        UUID coupon = coupon("LAST", 1, "PERCENTAGE", "20");
        List<Checkout> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) orders.add(create(f.actor(), f.inventory(2)));
        List<Object> results = race(20, i -> attempt(() -> apply(orders.get(i), "LAST")));
        assertEquals(1, results.stream().filter(OrderMutationReceipt.class::isInstance).count());
        assertEquals(19, results.stream().filter(OrderException.class::isInstance).count());
        for (Object result : results) if (result instanceof OrderException failure) assertEquals("COUPON_QUOTA_EXHAUSTED", failure.code());
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=? AND status='RESERVED'", coupon));
    }

    @Test void concurrentCrossEventCouponSwapUsesConsistentLockOrder() throws Exception {
        Checkout first = create(), second = create(f.actor(), f.inventory(2));
        UUID a = coupon("A", 2, "PERCENTAGE", "20"), b = coupon("B", 2, "PERCENTAGE", "10");
        apply(first, "A"); apply(second, "B");
        List<Object> results = race(2, i -> attempt(() -> apply(i == 0 ? first : second, i == 0 ? "B" : "A")));
        assertEquals(2, results.stream().filter(OrderMutationReceipt.class::isInstance).count());
        assertEquals("B", current(first).getString("appliedCouponCode")); assertEquals("A", current(second).getString("appliedCouponCode"));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=? AND status='RESERVED'", a));
        assertEquals(1, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=? AND status='RESERVED'", b));
    }

    @Test void expiryDuringRealCouponLockWaitRejectsMutationWithoutReceipt() throws Exception {
        Checkout checkout = create(); UUID a = coupon("A", 1, "PERCENTAGE", "20");
        f.sql("UPDATE dbo.tc_ticket_holds SET expires_at=DATEADD(second,3,SYSUTCDATETIME()) WHERE id=?", checkout.hold());
        var command = change(checkout, "A");
        try (Connection blocker = f.owner.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement("UPDATE dbo.tc_coupons SET version=version WHERE id=?")) { lock.setString(1,a.toString()); lock.executeUpdate(); }
            int session = session(blocker);
            Future<Object> result = workers.submit(() -> attempt(() -> f.orderService.mutate(checkout.actor(), command)));
            awaitBlocked(session);
            awaitExpired(checkout.hold());
            blocker.rollback();
            assertEquals(409, ((OrderException)result.get(20,TimeUnit.SECONDS)).status());
        }
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE order_id=?", checkout.order()));
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_request_idempotency WHERE actor_id=? AND idempotency_key=?", checkout.actor().id(), command.key().toString()));
    }

    @Test void checkoutCurrentClockFollowsActualCouponWait() throws Exception {
        Checkout checkout = create(); UUID a = coupon("A", 1, "PERCENTAGE", "20"); apply(checkout, "A");
        f.sql("UPDATE dbo.tc_ticket_holds SET expires_at=DATEADD(second,3,SYSUTCDATETIME()) WHERE id=?", checkout.hold());
        try (Connection blocker = f.owner.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock=blocker.prepareStatement("UPDATE dbo.tc_coupons SET version=version WHERE id=?")) { lock.setString(1,a.toString()); lock.executeUpdate(); }
            Future<CurrentCheckout> result=workers.submit(() -> f.orders.checkout(checkout.actor(), checkout.hold()));
            awaitBlocked(session(blocker)); awaitExpired(checkout.hold()); blocker.rollback();
            CurrentCheckout read=result.get(20,TimeUnit.SECONDS);
            assertFalse(read.order().orElseThrow().canEditCoupon());
            assertFalse(read.serverTime().isBefore(read.order().orElseThrow().snapshot().expiresAt()));
        }
    }

    @Test void cancelExpiryAndPaymentResolutionPreventCouponMutationAndReturnQuotaOnce() throws Exception {
        Checkout checkout = create(); UUID a = coupon("A", 1, "PERCENTAGE", "20"); apply(checkout, "A");
        f.sql("INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status) VALUES(NEWID(),?,?,150000,'UNKNOWN')", checkout.order(), UUID.randomUUID().toString());
        assertEquals("PAYMENT_UNRESOLVED", assertThrows(OrderException.class, () -> apply(checkout,null)).code());
        assertEquals("UNKNOWN", current(checkout).getString("paymentResolution")); assertFalse(current(checkout).getBoolean("canEditCoupon"));
        f.sql("DELETE dbo.tc_payments WHERE order_id=?", checkout.order());
        f.mutate(f.actor, new HoldCommand.Cancel(UUID.randomUUID(), checkout.hold()));
        assertEquals("CANCELLED", current(checkout).getString("status"));
        assertEquals(409, assertThrows(OrderException.class, () -> apply(checkout,null)).status());
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=? AND status='RESERVED'", a));
        assertEquals(checkout.receipt().body(), f.orderService.mutate(checkout.actor(),checkout.command()).body());
        Checkout expired=create(f.actor(), f.inventory(2)); apply(expired,"A"); f.expire(expired.hold());
        assertEquals(1,f.repository.expire(50).size()); assertEquals(0,f.repository.expire(50).size());
        assertEquals("EXPIRED",current(expired).getString("status"));
        assertEquals(0,f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=? AND status='RESERVED'",a));
    }

    @Test void redisLateFillRollbackAndOutageCannotOverrideSqlAuthority() throws Exception {
        Checkout checkout = create(); UUID a=coupon("A",1,"PERCENTAGE","20");
        var lookup=f.orderCache.lookup(checkout.actor().id(),checkout.hold());
        var before=f.orders.checkout(checkout.actor(),checkout.hold()).order().orElseThrow();
        apply(checkout,"A"); assertFalse(f.orderCache.fill(lookup.token().orElseThrow(),before));
        current(checkout); String cached;
        try(Jedis jedis=f.redis.getResource()) {
            cached=jedis.get(RedisOrderCache.summaryKey(checkout.order())); assertNotNull(cached);
            long ttl=jedis.pttl(RedisOrderCache.summaryKey(checkout.order()));assertTrue(ttl>0 && ttl<=600000);
            assertTrue(jedis.pttl(RedisOrderCache.fenceKey(checkout.actor().id(),checkout.hold()))>0);
            var receiptKeys=jedis.keys("tc:idem:order:"+checkout.actor().id()+":*");
            for(String key:receiptKeys) { long receiptTtl=jedis.pttl(key);assertTrue(receiptTtl>0 && receiptTtl<=600000); }
        }
        assertEquals(400,assertThrows(OrderException.class,() -> apply(checkout,"missing")).status());
        try(Jedis jedis=f.redis.getResource()) { assertEquals(cached,jedis.get(RedisOrderCache.summaryKey(checkout.order()))); }
        f.buyer.unavailable=true;
        assertEquals(503,assertThrows(OrderException.class,() -> current(checkout)).status()); f.buyer.unavailable=false;
        var outage=new OrderService(f.orders,new RedisOrderCache(() -> { throw new IllegalStateException("Redis unavailable"); },java.time.Clock.systemUTC()));
        var command=change(checkout,null); var removed=outage.mutate(checkout.actor(),command);
        assertEquals("0",new JSONObject(removed.body()).getString("discountAmount")); assertEquals(removed.body(),outage.mutate(checkout.actor(),command).body());
        assertTrue(new JSONObject(outage.checkout(checkout.actor(),checkout.hold()).body()).getJSONObject("order").isNull("appliedCouponCode"));
    }

    @Test void consumedUsesStillExhaustQuotaAndManagerCannotReduceItBelowUsage() throws Exception {
        Checkout first=create(),second=create(f.actor(),f.inventory(2)),third=create(f.actor(),f.inventory(2));
        UUID a=coupon("A",2,"PERCENTAGE","20");apply(first,"A");apply(second,"A");
        assertThrows(SQLException.class,() -> f.sql("UPDATE dbo.tc_coupons SET max_uses=1 WHERE id=?",a));
        f.sql("UPDATE dbo.tc_coupon_redemptions SET status='CONSUMED',consumed_at=SYSUTCDATETIME() WHERE order_id=?",first.order());
        var preview=new JSONObject(f.orderService.eligibility(third.actor(),third.order(),"A"));
        assertFalse(preview.getBoolean("eligible"));assertEquals("QUOTA_EXHAUSTED",preview.getString("reason"));
        assertEquals("COUPON_QUOTA_EXHAUSTED",assertThrows(OrderException.class,() -> apply(third,"A")).code());
        assertEquals(2,f.count("SELECT COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=? AND status IN('RESERVED','CONSUMED')",a));
    }

    @Test void buyerCannotReadRawOrderOrManufactureReceiptOrCallLegacyBypass() throws Exception {
        Checkout checkout = create();
        try(Connection buyer=f.buyer.getConnection();Statement sql=buyer.createStatement()) {
            assertThrows(SQLException.class,() -> sql.executeQuery("SELECT * FROM dbo.tc_orders"));
            assertThrows(SQLException.class,() -> sql.executeQuery("SELECT * FROM dbo.tc_request_idempotency"));
            assertThrows(SQLException.class,() -> sql.executeQuery("EXEC dbo.usp_CreateOrderFromHold '"+checkout.hold()+"','"+checkout.actor().id()+"'"));
            assertThrows(SQLException.class,() -> sql.executeQuery("EXEC dbo.usp_ApplyOrderCoupon '"+checkout.order()+"','"+checkout.actor().id()+"',NULL"));
        }
    }

    private int session(Connection connection) throws SQLException { try(Statement sql=connection.createStatement();ResultSet rows=sql.executeQuery("SELECT @@SPID")) { rows.next();return rows.getInt(1); } }
    private void awaitBlocked(int blocker) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<end) {
            if(f.count("SELECT COUNT(*) FROM sys.dm_exec_requests WHERE blocking_session_id=? AND wait_type LIKE 'LCK_M%'",blocker)>0) return;
            Thread.sleep(20);
        }
        fail("Buyer did not enter the asserted SQL lock wait");
    }
    private void awaitExpired(UUID hold) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<end) { if(f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE id=? AND expires_at<=SYSUTCDATETIME()",hold)>0) return;Thread.sleep(20); }
        fail("Fixture Hold did not cross its SQL UTC deadline");
    }
    private static Object attempt(Callable<?> action) { try { return action.call(); } catch(OrderException failure) { return failure; } catch(Exception failure) { throw new RuntimeException(failure); } }
    private static List<Object> race(int count,IntFunction<Object> action) throws Exception {
        CountDownLatch ready=new CountDownLatch(count),start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(count)) {
            List<Future<Object>> futures=new ArrayList<>();
            for(int i=0;i<count;i++) { int index=i;futures.add(workers.submit(() -> { ready.countDown();if(!start.await(10,TimeUnit.SECONDS)) throw new TimeoutException();return action.apply(index); })); }
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();List<Object> results=new ArrayList<>();
            for(var future:futures) results.add(future.get(40,TimeUnit.SECONDS));return results;
        }
    }
}
