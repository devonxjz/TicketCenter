package vn.ticketscenter.config.redis;

public final class RedisScripts {
    public static final String REPLACE_IF_EQUALS_PX =
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2])
            return 1
            """;

    private RedisScripts() {}
}
