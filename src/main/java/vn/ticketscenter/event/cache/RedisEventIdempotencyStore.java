package vn.ticketscenter.event.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.web.*;
import vn.ticketscenter.event.dao.EventManagementRepository.Result;
import vn.ticketscenter.event.service.EventProblem;
import java.io.StringReader;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** A lease gates work; the SQL receipt is authoritative even if Redis completion fails. */
public final class RedisEventIdempotencyStore implements EventIdempotencyStore {
    private static final Logger LOGGER =
            Logger.getLogger(RedisEventIdempotencyStore.class.getName());
    private final Supplier<Jedis> connections;

    public RedisEventIdempotencyStore() {
        this(() -> RedisClientProvider.getInstance().getResource());
    }

    public RedisEventIdempotencyStore(Supplier<Jedis> connections) {
        this.connections = connections;
    }

    @Override
    public Lease acquire(String key, String hash) {
        String value =
                "{\"status\":\"PROCESSING\",\"hash\":"
                        + HttpResponses.jsonString(hash)
                        + ",\"token\":"
                        + HttpResponses.jsonString(UUID.randomUUID().toString())
                        + "}";
        try (Jedis redis = connections.get()) {
            if ("OK".equals(redis.set(key, value, SetParams.setParams().nx().ex(120)))) {
                return new Lease(key, value);
            }
            String current = redis.get(key);
            if (current == null) {
                throw EventProblem.conflict(
                        "Yêu cầu đang được thử lại. Vui lòng thử lại cùng key.");
            }
            Map<String, String> entry = JsonObjectParser.parse(new StringReader(current));
            if (!hash.equals(entry.get("hash"))) {
                throw EventProblem.conflict("Idempotency key đã dùng cho nội dung khác.");
            }
            throw EventProblem.conflict("Yêu cầu đang được xử lý hoặc cần đối chiếu kết quả DB.");
        } catch (EventProblem e) {
            throw e;
        } catch (Exception e) {
            throw EventProblem.unavailable();
        }
    }

    @Override
    public void completed(Lease lease, String hash, Result response) {
        String value =
                "{\"status\":\"COMPLETED\",\"hash\":"
                        + HttpResponses.jsonString(hash)
                        + ",\"statusCode\":"
                        + HttpResponses.jsonString(Integer.toString(response.status()))
                        + ",\"response\":"
                        + HttpResponses.jsonString(response.json())
                        + "}";
        try (Jedis redis = connections.get()) {
            redis.eval(
                    "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('SET',KEYS[1],ARGV[2],'EX',86400) end return 0",
                    List.of(lease.key()),
                    List.of(lease.value(), value));
        } catch (RuntimeException e) {
            LOGGER.warning(
                    "Event idempotency Redis completion unavailable; durable SQL receipt retained.");
        }
    }

    @Override
    public void release(Lease lease) {
        try (Jedis redis = connections.get()) {
            redis.eval(
                    "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end return 0",
                    List.of(lease.key()),
                    List.of(lease.value()));
        } catch (RuntimeException ignored) {
        }
    }
}
