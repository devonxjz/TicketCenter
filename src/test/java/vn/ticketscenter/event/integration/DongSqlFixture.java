package vn.ticketscenter.event.integration;

import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.event.cache.*;
import vn.ticketscenter.event.dto.EventCommand;
import vn.ticketscenter.event.repository.*;
import vn.ticketscenter.event.repository.EventManagementRepository.*;
import vn.ticketscenter.event.service.*;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.storage.*;
import redis.clients.jedis.Jedis;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

final class DongSqlFixture {
    final UUID manager=UUID.randomUUID(),other=UUID.randomUUID(),admin=UUID.randomUUID(),staff=UUID.randomUUID();
    final UUID org=UUID.randomUUID(),otherOrg=UUID.randomUUID(),category=UUID.randomUUID(),rule=UUID.randomUUID(),otherRule=UUID.randomUUID();
    final Actor actor=new Actor(manager,0),adminActor=new Actor(admin,0),otherActor=new Actor(other,0),staffActor=new Actor(staff,0);
    final Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    final EventTransactions transactions=new EventTransactions(null){
        @Override public <T> T run(DatabasePrincipal principal,Work<T> work){
            try(Connection c=connection(switch(principal){case BUYER->"dong_buyer";case ADMIN->"dong_admin";default->"dong_manager";})){
                c.setAutoCommit(false);
                try{T result=work.execute(c);c.commit();return result;}catch(Exception e){c.rollback();throw EventTransactions.translate(e);}
            }catch(SQLException e){throw EventTransactions.translate(e);}
        }
    };
    final EventManagementRepository repository=new EventManagementRepository(transactions);
    final RedisEventCatalogCache cache=new RedisEventCatalogCache(DongSqlFixture::redis);
    final RedisEventIdempotencyStore idempotency=new RedisEventIdempotencyStore(DongSqlFixture::redis);
    final EventManagementService service=new EventManagementService(repository,idempotency,cache);
    DongSqlFixture() throws Exception {
        try(Connection c=connection(null)){
            c.setAutoCommit(false);
            for(UUID id:List.of(manager,other,admin,staff)) {
                String name="dong_"+id.toString().replace("-","");
                execute(c,"INSERT dbo.tc_users(id,email,user_name,full_name,password_hash,platform_role,email_verified_at) VALUES(?,?,?,?,?,?,SYSUTCDATETIME())",
                    id,name+"@example.test",name,"Dong fixture",new PasswordHasher().hash(UUID.randomUUID().toString()),id.equals(admin)?"ADMIN":"CUSTOMER");
            }
            for(UUID id:List.of(org,otherOrg)) execute(c,"INSERT dbo.tc_organizations(id,name,contact_email,status) VALUES(?,?,?,'APPROVED')",id,"Dong fixture "+id,"fixture@example.test");
            execute(c,"INSERT dbo.tc_organization_memberships(user_id,organization_id,role) VALUES(?,?,'MANAGER')",manager,org);
            execute(c,"INSERT dbo.tc_organization_memberships(user_id,organization_id,role) VALUES(?,?,'MANAGER')",other,otherOrg);
            execute(c,"INSERT dbo.tc_organization_memberships(user_id,organization_id,role) VALUES(?,?,'CHECK_IN_STAFF')",staff,org);
            execute(c,"INSERT dbo.tc_event_categories(id,name,slug) VALUES(?,?,?)",category,"Dong category","dong-"+category);
            for(var pair:List.of(Map.entry(rule,org),Map.entry(otherRule,otherOrg))) execute(c,
                "INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES(?,?,5,0,DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(year,1,SYSUTCDATETIME()))",pair.getKey(),pair.getValue());
            c.commit();
        }
        try(Jedis redis=redis()){assertEquals("PONG",redis.ping(),"Dedicated Redis fixture required");}
    }
    static Connection connection(String user) throws SQLException {
        Connection c=DongFixtureConnectionSettings.configured().open();
        if(user!=null){if(!Set.of("dong_buyer","dong_manager","dong_admin").contains(user))throw new SQLException("Invalid test principal");try(var q=c.createStatement()){q.execute("EXECUTE AS USER='"+user+"'");}}
        return c;
    }
    static Jedis redis(){return new Jedis(AppConfig.get("TC_DONG_REDIS_HOST","127.0.0.1"),AppConfig.getInt("TC_DONG_REDIS_PORT",16479),1000);}
    static void execute(Connection c,String sql,Object...values) throws SQLException {try(var q=c.prepareStatement(sql)){q.setQueryTimeout(10);for(int i=0;i<values.length;i++)q.setObject(i+1,values[i] instanceof UUID id?id.toString():values[i]);q.executeUpdate();}}
    static UUID id(Result result,String field) {var matcher=java.util.regex.Pattern.compile("\""+field+"\":\"([a-f0-9-]{36})\"",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(result.json());assertTrue(matcher.find(),result.json());return UUID.fromString(matcher.group(1));}
    EventCommand create(){return EventCommand.input("CREATE",org,null,null,Map.of("title","Dong integration concert","description","Fixture description","categoryId",category.toString(),"venueName","Local venue","venueAddress","Fixture address", "saleStartTime",now.plusSeconds(86400).toString(),"saleEndTime",now.plusSeconds(86400*2).toString(),"startTime",now.plusSeconds(86400*3).toString(),"endTime",now.plusSeconds(86400*3+7200).toString()),Clock.systemUTC());}
    UUID draft(){return id(service.mutate(actor,UUID.randomUUID().toString(),create()),"eventId");}
    EventCommand command(String op,UUID event,UUID zone,Map<String,String> fields){return EventCommand.input(op,null,event,zone,fields,Clock.systemUTC());}
    UUID seated(UUID event,String name,int rows,int per){return id(service.mutate(actor,UUID.randomUUID().toString(),command("ZONE_CREATE",event,null,Map.of("name",name,"zoneType","SEATED","basePrice","300000","rows",Integer.toString(rows),"seatsPerRow",Integer.toString(per)))),"zoneId");}
    UUID standing(UUID event,String name,int capacity){return id(service.mutate(actor,UUID.randomUUID().toString(),command("ZONE_CREATE",event,null,Map.of("name",name,"zoneType","STANDING","basePrice","0","capacity",Integer.toString(capacity)))),"zoneId");}
    EventCoverService covers(StorageService storage,EventCoverService.Orphans orphans){return new EventCoverService(repository,idempotency,service,storage,orphans);}
    static byte[] png() throws IOException {var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();}
    static final class Storage implements StorageService {
        int uploads,deletes;boolean fail;String lastUrl;
        public StoredAsset upload(InputStream content,String folder) throws IOException{uploads++;if(fail)throw new IOException("provider credentials must remain private");String id=folder+"/"+UUID.randomUUID();lastUrl="https://fixture.example.test/"+id+".png";return new StoredAsset(lastUrl,id);}
        public StoredAsset ensure(InputStream content,String id) throws IOException {return upload(content,id);}
        public Optional<StoredAsset> find(String id){return Optional.empty();}
        public void delete(String id){deletes++;}public String getUrl(String id){return "https://fixture.example.test/"+id;}
    }
}
