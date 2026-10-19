package vn.ticketscenter.config.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;

public final class RedisScan {
    private RedisScan() {}

    public static void deleteMatching(Jedis redis, String pattern) {
        String cursor = "0";
        do {
            var page = redis.scan(cursor, new ScanParams().match(pattern).count(100));
            if (!page.getResult().isEmpty()) redis.del(page.getResult().toArray(String[]::new));
            cursor = page.getCursor();
        } while (!"0".equals(cursor));
    }
}
