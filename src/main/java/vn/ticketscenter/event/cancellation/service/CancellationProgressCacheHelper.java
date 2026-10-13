package vn.ticketscenter.event.cancellation.service;

import java.util.*;
import java.util.function.Supplier;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.event.cache.RedisEventCatalogCache;
import vn.ticketscenter.fulfillment.checkin.cache.RedisCheckInEventCache;
import vn.ticketscenter.event.cancellation.dto.CancellationProgressDto;
import java.io.StringReader;
import java.time.Instant;
import java.math.BigDecimal;
import vn.ticketscenter.config.web.JsonObjectParser;

public class CancellationProgressCacheHelper {
    private final Supplier<Jedis> connections;
    public CancellationProgressCacheHelper() { this(() -> RedisClientProvider.getInstance().getResource()); }
    public CancellationProgressCacheHelper(Supplier<Jedis> connections) { this.connections = connections; }
    public void publish(CancellationProgressDto progress) {
        try (var redis = connections.get()) { redis.setex("tc:event:cancel-progress:"+progress.eventId(),10,progress.json()); }
    }
    public Optional<String> get(UUID id) {
        try(var redis=connections.get()) {
            String json=redis.get("tc:event:cancel-progress:"+id);
            if(json==null||json.length()>8192)return Optional.empty();
            var fields=JsonObjectParser.parse(new StringReader(json));
            if(!fields.keySet().equals(Set.of("eventId","status","reason","cancelledAt","totalOrders","processedOrders","progressPercentage","updatedAt","successfulRefunds","failedRefunds","unknownRefunds","pendingRefunds","usedTicketExceptions"))
                    ||!id.toString().equals(fields.get("eventId"))||!Set.of("PENDING","IN_PROGRESS","COMPLETED").contains(fields.get("status")))return Optional.empty();
            for(String key:List.of("totalOrders","processedOrders","successfulRefunds","failedRefunds","unknownRefunds","pendingRefunds","usedTicketExceptions"))
                if(!fields.get(key).matches("[0-9]+")||Long.parseLong(fields.get(key))<0)return Optional.empty();
            if(Long.parseLong(fields.get("processedOrders"))>Long.parseLong(fields.get("totalOrders")))return Optional.empty();
            var percentage=new BigDecimal(fields.get("progressPercentage"));
            if(percentage.signum()<0||percentage.compareTo(BigDecimal.valueOf(100))>0)return Optional.empty();
            Instant.parse(fields.get("updatedAt"));Instant.parse(fields.get("cancelledAt"));
            if(fields.get("reason")==null||fields.get("reason").length()>500)return Optional.empty();
            var normalized=new CancellationProgressDto(id,null,fields.get("status"),fields.get("reason"),
                    Instant.parse(fields.get("cancelledAt")),Long.parseLong(fields.get("totalOrders")),
                    Long.parseLong(fields.get("processedOrders")),null,Instant.parse(fields.get("updatedAt")),
                    Long.parseLong(fields.get("successfulRefunds")),Long.parseLong(fields.get("failedRefunds")),
                    Long.parseLong(fields.get("unknownRefunds")),Long.parseLong(fields.get("pendingRefunds")),
                    Long.parseLong(fields.get("usedTicketExceptions"))).json();
            // The shared flat parser can coerce quoted numbers. Only accept the exact typed
            // serialization produced by publish(), including the derived percentage.
            if(!normalized.equals(json))return Optional.empty();
            return Optional.of(json); // A hit does not renew the ten-second TTL.
        }catch(Exception unavailable){return Optional.empty();}
    }
    public void processed(UUID id) {
        try(var redis=connections.get()) {
            redis.del("tc:event:cancel-progress:"+id,"tc:admin:pending_refunds_count");
            clearTicketProjections(redis);
        }
    }
    public void cancelled(CancellationProgressDto progress) {
        new RedisEventCatalogCache(connections).invalidateCommitted(progress.eventId(),progress.organizationId());
        new RedisCheckInEventCache(connections).invalidate(progress.eventId(),progress.organizationId());
        try (var redis = connections.get()) {
            // Ticket/hold projections may still show old purchase/refund actions until their short TTL.
            // Erase those projections; their producers independently verify SQL on every mutation.
            clearTicketProjections(redis);
            redis.del("tc:event:cancel-progress:"+progress.eventId(),"tc:admin:pending_refunds_count");
        }
    }
    private static void clearTicketProjections(Jedis redis) {
            for (String pattern : List.of("tc:user:active-hold:*","tc:order:summary:*","tc:ticket:detail:v1:*","tc:ticket:qr:v1:*","tc:user:tickets:v1:*")) {
                String cursor="0";
                do { var page=redis.scan(cursor,new ScanParams().match(pattern).count(100));
                    if (!page.getResult().isEmpty()) redis.del(page.getResult().toArray(String[]::new));
                    cursor=page.getCursor();
                } while (!cursor.equals("0"));
            }
    }
}
