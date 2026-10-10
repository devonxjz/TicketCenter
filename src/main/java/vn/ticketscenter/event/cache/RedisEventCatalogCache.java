package vn.ticketscenter.event.cache;

import redis.clients.jedis.Jedis;
import vn.ticketscenter.config.redis.RedisClientProvider;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.*;
import redis.clients.jedis.params.ScanParams;

public final class RedisEventCatalogCache implements EventCatalogCache, EventCacheInvalidator {
    private static final int MAX_CHARACTERS = 2_000_000;
    private static final Logger LOGGER = Logger.getLogger(RedisEventCatalogCache.class.getName());
    private final AtomicLong nextWarning = new AtomicLong();
    private static final String GENERATION="tc:event:cache-generation:v1";
    private volatile boolean invalidationPending;
    private final Supplier<Jedis> connections;
    public RedisEventCatalogCache() { this(() -> RedisClientProvider.getInstance().getResource()); }
    public RedisEventCatalogCache(Supplier<Jedis> connections) { this.connections = connections; }
    @Override public Optional<String> get(String key) {
        if(invalidationPending) {
            invalidateCommitted(null,null);
            if(invalidationPending) return Optional.empty();
        }
        try (Jedis redis = connections.get()) {
            String json = redis.get(key);
            return json != null && json.length() <= MAX_CHARACTERS && CachedJsonValidator.isResponse(json)
                    ? Optional.of(json) : Optional.empty();
        } catch (RuntimeException ignored) { unavailable(); return Optional.empty(); }
    }
    @Override public void put(String key, String json, int ttlSeconds) {
        if (json.length() > MAX_CHARACTERS || !CachedJsonValidator.isResponse(json)) return;
        try (Jedis redis = connections.get()) { redis.setex(key, ttlSeconds, json); }
        catch (RuntimeException ignored) { unavailable(); }
    }
    @Override public String generation() {
        try(Jedis redis=connections.get()) { String value=redis.get(GENERATION);return value==null?"0":value; }
        catch(RuntimeException e) { return null; }
    }
    @Override public void putIfGeneration(String key,String json,int ttl,String generation) {
        if(generation==null || invalidationPending || json.length()>MAX_CHARACTERS || !CachedJsonValidator.isResponse(json)) return;
        try(Jedis redis=connections.get()) {
            redis.eval("if (redis.call('GET',KEYS[1]) or '0')==ARGV[1] then return redis.call('SET',KEYS[2],ARGV[2],'EX',ARGV[3]) end return 0",
                List.of(GENERATION,key),List.of(generation,json,Integer.toString(ttl)));
        } catch(RuntimeException e) { unavailable(); }
    }
    @Override public void invalidateCommitted(UUID eventId,UUID organizationId) {
        invalidationPending=true;
        try(Jedis redis=connections.get()) {
            redis.incr(GENERATION); // Old readers cannot refill a cache after this committed mutation.
            for(String pattern:List.of("tc:event:list:v1:*","tc:event:detail:v1:*","tc:event:zones:v1:*","tc:zone:seats:v1:*","tc:org:events:*","tc:admin:events:*")) {
                String cursor="0";
                do {
                    var result=redis.scan(cursor,new ScanParams().match(pattern).count(100));
                    if(!result.getResult().isEmpty()) redis.del(result.getResult().toArray(String[]::new));cursor=result.getCursor();
                } while(!cursor.equals("0"));
            }
            invalidationPending=false;
        } catch(RuntimeException e) { unavailable(); }
    }
    private void unavailable() {
        long now = System.currentTimeMillis(), next = nextWarning.get();
        if (now >= next && nextWarning.compareAndSet(next, now + 60_000))
            LOGGER.warning("Event catalog Redis unavailable; falling back to SQL Server.");
    }
}
