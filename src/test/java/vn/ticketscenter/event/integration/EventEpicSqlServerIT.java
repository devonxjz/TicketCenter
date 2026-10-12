package vn.ticketscenter.event.integration;

import org.junit.jupiter.api.*;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.repository.*;
import vn.ticketscenter.event.repository.EventManagementRepository.*;
import vn.ticketscenter.event.service.*;
import vn.ticketscenter.storage.StoredAsset;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual SQL Server transactions, restricted DB contexts and Redis. Provider I/O uses a named fake. */
class EventEpicSqlServerIT {
    static DongSqlFixture f;
    @BeforeAll static void setup() throws Exception {f=new DongSqlFixture();}
    @Test void eventRepositoryRestoresExistingActorAfterReadMutationAndDeniedRequest() throws Exception {
        UUID event=f.draft();
        try(Connection c=DongSqlFixture.connection("dong_manager")) {
            DongSqlFixture.execute(c,"EXEC sys.sp_set_session_context @key=N'actor_id',@value=?",f.other);
            var transactions=new EventTransactions(null){
                @Override public <T> T run(vn.ticketscenter.config.persistence.DatabasePrincipal principal,Work<T> work){
                    try{return work.execute(c);}catch(SQLException e){throw EventTransactions.translate(e);}
                }
            };
            transactions.run(vn.ticketscenter.config.persistence.DatabasePrincipal.MANAGER,f.manager,connection->{
                assertSessionActor(connection,f.manager);return null;
            });
            assertSessionActor(c,f.other);
            var repository=new EventManagementRepository(transactions);
            assertTrue(repository.read(f.actor,false,"DETAIL",f.org,event,null,null,1,20).contains(event.toString()));
            assertSessionActor(c,f.other);
            var edit=f.command("EDIT",event,null,Map.of("description","Actor-scoped update"));
            assertEquals(200,repository.command(f.actor,UUID.randomUUID().toString(),edit.hash(),edit).status());
            assertSessionActor(c,f.other);
            assertEquals(403,assertThrows(EventProblem.class,()->repository.read(f.otherActor,false,"DETAIL",f.org,event,null,null,1,20)).status());
            assertSessionActor(c,f.other);
            // Direct SQL callers also retain their surrounding context, independently of JDBC cleanup.
            try(var query=c.prepareStatement("EXEC dbo.usp_EventCommand @actor_id=?,@actor_version=0,@scope='EVENT',@idempotency_key=?,@request_hash=?,@operation='EDIT',@event_id=?,@payload=?")) {
                query.setString(1,f.manager.toString());query.setString(2,UUID.randomUUID().toString());query.setString(3,edit.hash());
                query.setString(4,event.toString());query.setString(5,edit.json());query.execute();
                assertSessionActor(c,f.other);
                query.execute(); // Durable replay must restore the context too.
            }
            assertSessionActor(c,f.other);
        }
    }
    private static void assertSessionActor(Connection c,UUID expected) throws SQLException {
        try(var query=c.createStatement();var rows=query.executeQuery("SELECT CONVERT(varchar(36),SESSION_CONTEXT(N'actor_id'))")) {
            assertTrue(rows.next());assertEquals(expected.toString(),rows.getString(1));
        }
    }
    @Test void eventCommandWaitAllowsRevocationAndRejectsExpiredActor() throws Exception {
        var isolated=new DongSqlFixture();UUID event=isolated.draft();
        assertEventWait(isolated,event,isolated.manager,()->assertThrows(EventProblem.class,()->isolated.repository.command(isolated.actor,UUID.randomUUID().toString(),"a".repeat(64),isolated.command("EDIT",event,null,Map.of("description","Revocation race")))).status(),401);
    }
    @Test void eventReadWaitAllowsRevocationAndRejectsExpiredActor() throws Exception {
        var isolated=new DongSqlFixture();UUID event=isolated.draft();
        assertEventWait(isolated,event,isolated.manager,()->assertThrows(EventProblem.class,()->isolated.repository.read(isolated.actor,false,"DETAIL",isolated.org,event,null,null,1,20)).status(),401);
    }
    @Test void directPublishWaitAllowsDemotionAndRejectsStaleLegacyGrant() throws Exception {
        var isolated=new DongSqlFixture();UUID event=isolated.draft();
        try(var c=DongSqlFixture.connection(null)){DongSqlFixture.execute(c,"INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?,'ADMIN')",isolated.admin);}
        assertEventWait(isolated,event,isolated.admin,()->{
            try(var c=DongSqlFixture.connection("dong_admin")){
                return assertThrows(SQLException.class,()->DongSqlFixture.execute(c,"EXEC dbo.usp_PublishEvent ?,?,?",event,isolated.admin,isolated.rule)).getErrorCode();
            }
        },51520);
    }
    private static void assertEventWait(DongSqlFixture isolated,UUID event,UUID user,Callable<Integer> action,int expected)throws Exception{
        var pool=Executors.newSingleThreadExecutor();var ready=new CountDownLatch(1);
        try(var gate=DongSqlFixture.connection(null)){
            gate.setAutoCommit(false);
            DongSqlFixture.execute(gate,"SET LOCK_TIMEOUT 3000; SET DEADLOCK_PRIORITY HIGH; UPDATE dbo.tc_events SET version=version WHERE id=?",event);
            var pending=pool.submit(()->{ready.countDown();return action.call();});
            assertTrue(ready.await(5,TimeUnit.SECONDS));Thread.sleep(600);assertFalse(pending.isDone(),"Request must wait for Event");
            // The revocation write must succeed while the Event is held, rather than deadlocking on a retained User lock.
            DongSqlFixture.execute(gate,"UPDATE dbo.tc_users SET auth_version=auth_version+1,platform_role='CUSTOMER' WHERE id=?",user);
            gate.commit();assertEquals(expected,pending.get(20,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        try(var c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT status FROM dbo.tc_events WHERE id=?")){
            q.setString(1,event.toString());try(var rows=q.executeQuery()){assertTrue(rows.next());assertEquals("DRAFT",rows.getString(1));}
        }
    }
    @Test void unknownCategoryIsValidationAndNeverLeavesAReceiptOrChangesTheDraft() {
        var fields=new TreeMap<>(f.create().fields());fields.put("categoryId",UUID.randomUUID().toString());
        var create=new EventCommand("CREATE",f.org,null,null,fields);String key=UUID.randomUUID().toString();
        assertEquals(400,assertThrows(EventProblem.class,()->f.service.mutate(f.actor,key,create)).status());
        assertTrue(f.repository.receipt(f.actor,"EVENT",key,create.hash()).isEmpty());
        UUID event=f.draft();
        assertEquals(400,assertThrows(EventProblem.class,()->f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("EDIT",event,null,Map.of("categoryId",fields.get("categoryId"))))).status());
        assertEquals(f.category.toString(),EventResponseValues.string(f.repository.read(f.actor,false,"DETAIL",f.org,event,null,null,1,20),"categoryId").orElseThrow());
    }
    @Test void singleEventReadCannotBecomeMalformedJsonBecauseOfListFilters() {
        UUID event=f.draft();String detail=f.repository.read(f.actor,false,"DETAIL",f.org,event,null,"PUBLISHED",2,20);
        assertEquals(event.toString(),EventResponseValues.string(detail,"eventId").orElse(null),"Single-resource reads must not inherit list status/page filters");
        assertTrue(detail.startsWith("{\"data\":{\"eventId\":"),"Detail must always return one valid JSON object or an error");
    }
    @Test void publicationUsesCurrentRoleWithoutLegacyAdminAndRejectsStaleLegacyGrant() throws Exception {
        var isolated=new DongSqlFixture();UUID event=isolated.draft();isolated.seated(event,"Current role",1,2);
        isolated.covers(new DongSqlFixture.Storage(),(e,a)->{}).upload(isolated.actor,event,UUID.randomUUID().toString(),new ByteArrayInputStream(DongSqlFixture.png()));
        isolated.service.mutate(isolated.actor,UUID.randomUUID().toString(),isolated.command("SUBMIT",event,null,Map.of()));
        var command=isolated.command("PUBLISH",event,null,Map.of("commissionRuleId",isolated.rule.toString()));
        var result=isolated.service.mutate(isolated.adminActor,UUID.randomUUID().toString(),command);assertTrue(result.json().contains("PUBLISHED"));
        try(Connection c=DongSqlFixture.connection(null)) {
            DongSqlFixture.execute(c,"INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?,'ADMIN')",isolated.admin);
            DongSqlFixture.execute(c,"UPDATE dbo.tc_users SET platform_role='CUSTOMER' WHERE id=?",isolated.admin);
        }
        assertEquals(403,assertThrows(EventProblem.class,()->isolated.service.mutate(isolated.adminActor,UUID.randomUUID().toString(),command)).status());
        try(Connection c=DongSqlFixture.connection("dong_admin");var q=c.prepareCall("{call dbo.usp_PublishEvent(?,?,?)}")) {
            q.setString(1,event.toString());q.setString(2,isolated.admin.toString());q.setString(3,isolated.rule.toString());
            assertEquals(51520,assertThrows(SQLException.class,q::execute).getErrorCode(),"A stale legacy role must not authorize direct SP publication");
        }
    }
    @Test void draftReplayPayloadConflictAndCrossOrganizationGuards(){
        var command=f.create();String key=UUID.randomUUID().toString();var first=f.service.mutate(f.actor,key,command);
        assertEquals(201,first.status());assertEquals(first,f.service.mutate(f.actor,key,command));
        UUID event=DongSqlFixture.id(first,"eventId");
        var changed=new HashMap<>(command.fields());changed.put("title","Changed payload");
        assertEquals(409,assertThrows(EventProblem.class,()->f.service.mutate(f.actor,key,new EventCommand("CREATE",f.org,null,null,changed))).status());
        assertEquals(403,assertThrows(EventProblem.class,()->f.service.mutate(f.otherActor,"other-edit",f.command("EDIT",event,null,Map.of("description","Other org")))).status());
        assertEquals(403,assertThrows(EventProblem.class,()->f.repository.read(f.staffActor,false,"DETAIL",null,event,null,null,1,20)).status());
        assertTrue(f.repository.read(f.actor,false,"DETAIL",f.org,event,null,null,1,20).contains("DRAFT"));
    }
    @Test void matrixLabelsCapacityAndPublishPrerequisites() throws Exception {
        UUID event=f.draft();UUID zone=f.seated(event,"Matrix",27,2);UUID standing=f.standing(event,"Standing",100);
        String layout=f.repository.read(f.actor,false,"LAYOUT",null,event,null,null,1,20);
        assertTrue(layout.contains("AA2"));assertTrue(layout.contains("\"totalCapacity\":54"));assertTrue(layout.contains("\"basePrice\":\"0\""));
        try(Connection c=DongSqlFixture.connection(null)){DongSqlFixture.execute(c,"UPDATE dbo.tc_zones SET held_quantity=60,sold_quantity=20 WHERE id=?",standing);}
        assertEquals(409,assertThrows(EventProblem.class,()->f.service.mutate(f.actor,"below-quota",f.command("ZONE_EDIT",null,standing,Map.of("basePrice","0","capacity","79")))).status());
        f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("SUBMIT",event,null,Map.of()));
        assertEquals(400,assertThrows(EventProblem.class,()->f.service.mutate(f.adminActor,UUID.randomUUID().toString(),f.command("PUBLISH",event,null,Map.of("commissionRuleId",f.rule.toString())))).status());
        assertEquals(409,assertThrows(EventProblem.class,()->f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("ZONE_DELETE",null,zone,Map.of()))).status());
    }
    @Test void coverSubmitRejectResubmitPublishAndImmediatePublicCache() throws Exception {
        UUID event=f.draft();UUID zone=f.seated(event,"VIP",2,2);var storage=new DongSqlFixture.Storage();List<StoredAsset> orphans=new ArrayList<>();
        var covers=f.covers(storage,(e,a)->orphans.add(a));String key=UUID.randomUUID().toString();byte[] png=DongSqlFixture.png();
        var cover=covers.upload(f.actor,event,key,new ByteArrayInputStream(png));assertEquals(200,cover.status());
        assertEquals(cover,covers.upload(f.actor,event,key,new ByteArrayInputStream(png)));assertEquals(1,storage.uploads);
        f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("SUBMIT",event,null,Map.of()));
        assertEquals(403,assertThrows(EventProblem.class,()->f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("PUBLISH",event,null,Map.of("commissionRuleId",f.rule.toString())))).status());
        f.service.mutate(f.adminActor,UUID.randomUUID().toString(),f.command("REJECT",event,null,Map.of("reason","Please update the event description")));
        f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("EDIT",event,null,Map.of("description","Updated description")));
        f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("SUBMIT",event,null,Map.of()));
        try(Connection c=DongSqlFixture.connection("dong_buyer")) {
            var catalog=new EventCatalogService(EventRepositoryFixture.repository(c),f.cache);
            var filter=EventFilter.parse(Map.of("categoryId",f.category.toString()));catalog.events(filter); // Warm hidden catalog.
            assertThrows(EventCatalogService.NotFound.class,()->catalog.event(event));
            assertEquals(400,assertThrows(EventProblem.class,()->f.service.mutate(f.adminActor,UUID.randomUUID().toString(),f.command("PUBLISH",event,null,Map.of("commissionRuleId",f.otherRule.toString())))).status());
            var published=f.service.mutate(f.adminActor,UUID.randomUUID().toString(),f.command("PUBLISH",event,null,Map.of("commissionRuleId",f.rule.toString())));
            assertTrue(published.json().contains("PUBLISHED"));assertTrue(catalog.events(filter).contains(event.toString()));
            assertTrue(catalog.seats(zone).contains("A1"));assertTrue(catalog.event(event).contains(storage.lastUrl));
            assertEquals(409,assertThrows(EventProblem.class,()->f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("ZONE_EDIT",null,zone,Map.of("basePrice","300000","rows","3","seatsPerRow","3")))).status());
            f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("ZONE_EDIT",null,zone,Map.of("basePrice","350000")));
            assertTrue(catalog.zones(event).contains("350000"));
        }
        assertTrue(orphans.isEmpty());assertEquals(0,storage.deletes);
    }
    @Test void concurrentSameKeyCreatesOneEventAndDuplicateZoneRollsBack() throws Exception {
        EventCommand command=f.create();String key=UUID.randomUUID().toString();
        List<Object> results=race(()->f.service.mutate(f.actor,key,command),()->f.service.mutate(f.actor,key,command));
        var success=results.stream().filter(Result.class::isInstance).map(Result.class::cast).toList();assertFalse(success.isEmpty());
        UUID event=DongSqlFixture.id(success.getFirst(),"eventId");assertEquals(success.getFirst(),f.service.mutate(f.actor,key,command));
        var a=f.command("ZONE_CREATE",event,null,Map.of("name","Same zone","zoneType","SEATED","basePrice","100","rows","1","seatsPerRow","2"));
        results=race(()->f.service.mutate(f.actor,UUID.randomUUID().toString(),a),()->f.service.mutate(f.actor,UUID.randomUUID().toString(),a));
        assertEquals(1,results.stream().filter(Result.class::isInstance).count());assertEquals(1,results.stream().filter(EventProblem.class::isInstance).count());
        try(Connection c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE id=?")){q.setString(1,event.toString());try(var rows=q.executeQuery()){rows.next();assertEquals(1,rows.getLong(1));}}
    }
    @Test void submitRacesWithZoneDeleteWithoutPublishingEmptyInventory() throws Exception {
        UUID event=f.draft(),zone=f.seated(event,"Race zone",1,2);
        var results=race(()->f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("SUBMIT",event,null,Map.of())),
            ()->f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("ZONE_DELETE",null,zone,Map.of())));
        assertEquals(1,results.stream().filter(Result.class::isInstance).count());
        String detail=f.repository.read(f.actor,false,"DETAIL",null,event,null,null,1,20),layout=f.repository.read(f.actor,false,"LAYOUT",null,event,null,null,1,20);
        if(detail.contains("PENDING_APPROVAL")) assertTrue(layout.contains(zone.toString()));else {assertTrue(detail.contains("DRAFT"));assertFalse(layout.contains(zone.toString()));}
    }
    @Test void coverRollbackKeepsOldImageAndRecordsNewOrphan() throws Exception {
        UUID event=f.draft();var storage=new DongSqlFixture.Storage();List<StoredAsset> orphans=new ArrayList<>();var covers=f.covers(storage,(e,a)->orphans.add(a));
        covers.upload(f.actor,event,UUID.randomUUID().toString(),new ByteArrayInputStream(DongSqlFixture.png()));String old=storage.lastUrl;
        try(Connection c=DongSqlFixture.connection(null);var q=c.createStatement()) {
            q.execute("CREATE TRIGGER dbo.trg_Dong_Test_CoverRollback ON dbo.tc_events AFTER UPDATE AS BEGIN IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE i.id='"+event+"' AND i.cover_image_url<>d.cover_image_url) THROW 52009,'Injected test rollback',1; END");
            try {assertThrows(EventProblem.class,()->covers.upload(f.actor,event,UUID.randomUUID().toString(),new ByteArrayInputStream(DongSqlFixture.png())));}
            finally {q.execute("DROP TRIGGER dbo.trg_Dong_Test_CoverRollback");}
        }
        assertEquals(old,EventResponseValues.string(f.repository.read(f.actor,false,"DETAIL",null,event,null,null,1,20),"coverImageUrl").orElseThrow());assertEquals(1,orphans.size());assertEquals(0,storage.deletes);
    }
    @Test void revokedSessionAndCrossOrganizationCoverAreRejectedBeforeProvider() throws Exception {
        UUID event=f.draft();var storage=new DongSqlFixture.Storage();var covers=f.covers(storage,(e,a)->{});
        assertEquals(403,assertThrows(EventProblem.class,()->covers.upload(f.otherActor,event,"cross-cover",new ByteArrayInputStream(DongSqlFixture.png()))).status());assertEquals(0,storage.uploads);
        assertEquals(401,assertThrows(EventProblem.class,()->f.repository.read(new Actor(f.manager,99),false,"DETAIL",null,event,null,null,1,20)).status());
        storage.fail=true;assertEquals(502,assertThrows(EventProblem.class,()->covers.upload(f.actor,event,"provider-failure",new ByteArrayInputStream(DongSqlFixture.png()))).status());
        assertFalse(f.repository.read(f.actor,false,"DETAIL",null,event,null,null,1,20).contains("fixture.example.test"));
    }
    @Test void failedForeignKeyDeleteRestoresAllChildrenAndDeleteReplayWorks() throws Exception {
        UUID event=f.draft();UUID zone=f.seated(event,"Delete zone",1,2);
        var storage=new DongSqlFixture.Storage();var orphans=new ArrayList<StoredAsset>();
        var deleting=new EventManagementService(f.repository,f.idempotency,f.cache,(e,a)->orphans.add(a));
        f.covers(storage,(e,a)->{}).upload(f.actor,event,UUID.randomUUID().toString(),new ByteArrayInputStream(DongSqlFixture.png()));
        try(Connection c=DongSqlFixture.connection(null);var q=c.createStatement()) {
            q.execute("IF OBJECT_ID('dbo.tc_dong_delete_guard','U') IS NULL CREATE TABLE dbo.tc_dong_delete_guard(event_id uniqueidentifier NOT NULL REFERENCES dbo.tc_events(id))");
            DongSqlFixture.execute(c,"INSERT dbo.tc_dong_delete_guard(event_id) VALUES(?)",event);
            assertEquals(409,assertThrows(EventProblem.class,()->deleting.mutate(f.actor,"fk-delete-"+event,f.command("DELETE",event,null,Map.of()))).status());assertTrue(orphans.isEmpty(),"Rolled-back deletion must not queue the referenced cover");
            assertTrue(f.repository.read(f.actor,false,"LAYOUT",null,event,null,null,1,20).contains(zone.toString()));
            DongSqlFixture.execute(c,"DELETE dbo.tc_dong_delete_guard WHERE event_id=?",event);
        }
        String key=UUID.randomUUID().toString();var command=f.command("DELETE",event,null,Map.of());var result=deleting.mutate(f.actor,key,command);
        assertEquals(1,orphans.size(),"Committed deletion must journal its cover for reference-safe cleanup");assertEquals(storage.lastUrl,orphans.getFirst().secureUrl());
        assertNotNull(result.cleanupJson());assertFalse(result.json().contains(orphans.getFirst().publicId()),"Internal cleanup metadata must not appear in the HTTP response");
        assertEquals(result,deleting.mutate(f.actor,key,command));assertEquals(2,orphans.size(),"Replay repairs cleanup journaling after a lost acknowledgement");assertEquals(0,storage.deletes);
        assertEquals(404,assertThrows(EventProblem.class,()->f.repository.read(f.actor,false,"DETAIL",null,event,null,null,1,20)).status());
    }
    @Test void uploadDoesNotHoldTransactionAndStaleCoverCannotOverwriteConcurrentEdit() throws Exception {
        UUID event=f.draft();List<StoredAsset> orphans=new ArrayList<>();var fake=new DongSqlFixture.Storage();
        vn.ticketscenter.storage.StorageService provider=new vn.ticketscenter.storage.StorageService(){
            public StoredAsset upload(InputStream input,String folder) throws IOException {
                try(var pool=Executors.newSingleThreadExecutor()) {
                    pool.submit(()->f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("EDIT",event,null,Map.of("description","Edited during provider I/O")))).get(5,TimeUnit.SECONDS);
                } catch(Exception e) { throw new IOException(e); }
                return fake.upload(input,folder);
            }
            public void delete(String id){fail("No blind provider deletion");}
            public StoredAsset ensure(InputStream input,String id) throws IOException{return upload(input,id);}
            public Optional<StoredAsset> find(String id){return Optional.empty();}
            public String getUrl(String id){return fake.getUrl(id);}
        };
        assertEquals(409,assertThrows(EventProblem.class,()->f.covers(provider,(e,a)->orphans.add(a)).upload(f.actor,event,UUID.randomUUID().toString(),new ByteArrayInputStream(DongSqlFixture.png()))).status());
        assertEquals(1,orphans.size());assertEquals("Edited during provider I/O",EventResponseValues.string(f.repository.read(f.actor,false,"DETAIL",null,event,null,null,1,20),"description").orElseThrow());
        assertTrue(EventResponseValues.string(f.repository.read(f.actor,false,"DETAIL",null,event,null,null,1,20),"coverImageUrl").isEmpty());
    }
    @Test void publishRacesWithZoneDeletionAndWritesSingleStatusAudit() throws Exception {
        UUID event=f.draft(),zone=f.seated(event,"Publish race",1,2);var fake=new DongSqlFixture.Storage();
        f.covers(fake,(e,a)->{}).upload(f.actor,event,UUID.randomUUID().toString(),new ByteArrayInputStream(DongSqlFixture.png()));
        f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("SUBMIT",event,null,Map.of()));
        var result=race(()->f.service.mutate(f.adminActor,UUID.randomUUID().toString(),f.command("PUBLISH",event,null,Map.of("commissionRuleId",f.rule.toString()))),
            ()->f.service.mutate(f.actor,UUID.randomUUID().toString(),f.command("ZONE_DELETE",null,zone,Map.of())));
        assertEquals(1,result.stream().filter(Result.class::isInstance).count());assertEquals(409,((EventProblem)result.stream().filter(EventProblem.class::isInstance).findFirst().orElseThrow()).status());
        assertTrue(f.repository.read(f.actor,false,"DETAIL",null,event,null,null,1,20).contains("PUBLISHED"));assertTrue(f.repository.read(f.actor,false,"LAYOUT",null,event,null,null,1,20).contains(zone.toString()));
        try(Connection c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT COUNT_BIG(*) FROM dbo.tc_audit_logs WHERE aggregate_id=? AND action='EVENT_STATUS_CHANGED'")) {
            q.setString(1,event.toString());try(var rows=q.executeQuery()){rows.next();assertEquals(2,rows.getLong(1),"Exactly one audit each for submit and publish");}
        }
    }
    @Test void realRedisCorruptionGenerationAndOutageAreSafe() throws Exception {
        String key="tc:event:detail:v1:"+UUID.randomUUID();String json="{\"data\":{\"title\":\"cached\"}}";
        try(var redis=DongSqlFixture.redis()) {redis.setex(key,120,"{broken");assertTrue(f.cache.get(key).isEmpty());}
        String generation=f.cache.generation();f.cache.putIfGeneration(key,json,120,generation);assertEquals(json,f.cache.get(key).orElseThrow());
        f.cache.invalidateCommitted(null,null);f.cache.putIfGeneration(key,json,120,generation);assertTrue(f.cache.get(key).isEmpty(),"In-flight old reads cannot refill after invalidation");
        var offline=new vn.ticketscenter.event.cache.RedisEventCatalogCache(()->{throw new IllegalStateException("Redis offline");});
        assertTrue(offline.get(key).isEmpty());assertDoesNotThrow(()->offline.putIfGeneration(key,json,120,null));
        var failClosed=new EventManagementService(f.repository,new vn.ticketscenter.event.cache.RedisEventIdempotencyStore(()->{throw new IllegalStateException("Redis offline");}),f.cache);
        String intent=UUID.randomUUID().toString();var command=f.create();assertEquals(503,assertThrows(EventProblem.class,()->failClosed.mutate(f.actor,intent,command)).status());assertTrue(f.repository.receipt(f.actor,"EVENT",intent,command.hash()).isEmpty());
    }
    @Test void replacedCoverIsJournaledAndReceiptSurvivesRedisEviction() throws Exception {
        UUID event=f.draft();var fake=new DongSqlFixture.Storage();List<StoredAsset> orphans=new ArrayList<>();var covers=f.covers(fake,(e,a)->orphans.add(a));
        String key=UUID.randomUUID().toString();var first=covers.upload(f.actor,event,key,new ByteArrayInputStream(DongSqlFixture.png()));String oldUrl=fake.lastUrl;
        try(var redis=DongSqlFixture.redis()){redis.del(EventManagementService.redisKey(f.actor,"EVENT",key));}
        assertEquals(first,covers.upload(f.actor,event,key,new ByteArrayInputStream(DongSqlFixture.png())));assertEquals(1,fake.uploads);
        covers.upload(f.actor,event,UUID.randomUUID().toString(),new ByteArrayInputStream(DongSqlFixture.png()));
        assertEquals(1,orphans.size());assertEquals(oldUrl,orphans.getFirst().secureUrl());assertEquals(0,fake.deletes);
        var command=f.create();String caseKey="Case-"+UUID.randomUUID();var a=f.service.mutate(f.actor,caseKey,command);var b=f.service.mutate(f.actor,caseKey.toLowerCase(Locale.ROOT),command);
        assertNotEquals(DongSqlFixture.id(a,"eventId"),DongSqlFixture.id(b,"eventId"),"SQL and Redis key comparison must agree");
    }
    private static List<Object> race(Callable<Result> a,Callable<Result> b) throws Exception {
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            var tasks=List.of(a,b).stream().map(task->pool.submit(()->{barrier.await(5,TimeUnit.SECONDS);try{return (Object)task.call();}catch(EventProblem p){return (Object)p;}})).toList();
            return List.of(tasks.get(0).get(20,TimeUnit.SECONDS),tasks.get(1).get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }
}
