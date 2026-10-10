package vn.ticketscenter.dashboard.service;

import java.util.Objects;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.redis.RedisClientProvider;

/** Optional shared admin projection. Current DB authority is required before get(). */
public final class DashboardCache {
    private static final String KEY="tc:admin:overview:v1";
    public interface Backend {String get(String key);void set(String key,String value,int ttl);void delete(String key);}
    private final Backend backend;
    public DashboardCache(Backend backend){this.backend=Objects.requireNonNull(backend);}
    public static DashboardCache none(){return new DashboardCache(new Backend(){
        public String get(String key){return null;}public void set(String key,String value,int ttl){}public void delete(String key){}
    });}
    public static DashboardCache configured(){return AppConfig.getBoolean("finance.redis.enabled",true)?redis():none();}
    public static DashboardCache redis(){return new DashboardCache(new Backend(){
        public String get(String key){try(var client=RedisClientProvider.getInstance().getResource()){return client.get(key);}}
        public void set(String key,String value,int ttl){try(var client=RedisClientProvider.getInstance().getResource()){client.setex(key,ttl,value);}}
        public void delete(String key){try(var client=RedisClientProvider.getInstance().getResource()){client.del(key);}}
    });}
    public String get(){try{return backend.get(KEY);}catch(RuntimeException ignored){return null;}}
    public void put(String json){if(json==null || json.length()>1_000_000)return;try{backend.set(KEY,json,60);}catch(RuntimeException ignored){}}
    public void invalidate(){try{backend.delete(KEY);}catch(RuntimeException ignored){}}
    /** Root calls only after a committed finance write; never during an open write transaction. */
    public static void invalidateAdminAfterCommit(){configured().invalidate();}
}
