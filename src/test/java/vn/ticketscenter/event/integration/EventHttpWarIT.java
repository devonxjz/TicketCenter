package vn.ticketscenter.event.integration;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.dto.EventResponseValues;
import vn.ticketscenter.identity.service.PasswordHasher;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real WAR requests through authentication/session/CSRF/dispatch filters, SQL Server and Cloudinary. */
class EventHttpWarIT {
    private String base;
    @org.junit.jupiter.api.BeforeEach void requireDedicatedAcceptance() {
        assertTrue(AppConfig.getBoolean("TC_DONG_CLOUDINARY_ACCEPTANCE",false),"Explicit real-provider acceptance opt-in required");
        String url=AppConfig.get("TC_DONG_HTTP_URL");assertNotNull(url);URI uri=URI.create(url);
        assertTrue(Set.of("127.0.0.1","localhost").contains(uri.getHost()),"Only the dedicated loopback WAR may be tested");
    }
    private HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();}
    private HttpResponse<String> get(HttpClient client,String path) throws Exception {return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    private HttpResponse<String> post(HttpClient client,String path,String body,String key,boolean csrf) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json;charset=UTF-8");
        if(key!=null)request.header("Idempotency-Key",key);
        if(csrf){var token=get(client,"/api/auth/csrf");assertEquals(200,token.statusCode());request.header("X-CSRF-Token",EventResponseValues.string(token.body(),"token").orElseThrow());}
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private void login(HttpClient client,UUID user,String password) throws Exception {
        String name="dong_"+user.toString().replace("-","");
        var response=post(client,"/api/auth/login","{\"identifier\":"+HttpResponses.jsonString(name+"@example.test")+",\"password\":"+HttpResponses.jsonString(password)+"}",null,false);
        assertEquals(200,response.statusCode(),"Fixture login failed");
    }
    private HttpResponse<String> multipart(HttpClient client,String path,Map<String,String> fields,byte[] file,String key) throws Exception {
        String boundary="dong-"+UUID.randomUUID();var body=new java.io.ByteArrayOutputStream();
        for(var field:fields.entrySet()) body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        if(file!=null) {body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cover.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));body.write(file);body.write("\r\n".getBytes(StandardCharsets.UTF_8));}
        body.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        String csrf=EventResponseValues.string(get(client,"/api/auth/csrf").body(),"token").orElseThrow();
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(60)).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-Token",csrf).header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void mergedImageWorkflowCreatesDefaultCoverAndVersionedUploadInvalidatesPublicCache() throws Exception {
        base=AppConfig.get("TC_DONG_HTTP_URL");assertNotNull(base);var fixture=new DongSqlFixture();String password=UUID.randomUUID()+"!DongAcceptance";
        try(Connection c=DongSqlFixture.connection(null)){for(UUID id:List.of(fixture.manager,fixture.admin))DongSqlFixture.execute(c,"UPDATE dbo.tc_users SET password_hash=? WHERE id=?",new PasswordHasher().hash(password),id);}
        var manager=client();var admin=client();var anonymous=client();login(manager,fixture.manager,password);login(admin,fixture.admin,password);
        var fields=new TreeMap<>(fixture.create().fields());fields.put("title","Đêm nhạc nghiệm thu Đông ảnh mặc định");fields.put("saleStart",fields.remove("saleStartTime"));fields.put("saleEnd",fields.remove("saleEndTime"));
        String path="/api/organizations/"+fixture.org+"/events",key=UUID.randomUUID().toString();
        var shortTitle=new TreeMap<>(fields);shortTitle.put("title","bad");assertEquals(400,multipart(manager,path,shortTitle,null,UUID.randomUUID().toString()).statusCode());
        var pastOpening=new TreeMap<>(fields);pastOpening.put("saleStart",java.time.Instant.now().minusSeconds(86400).toString());assertEquals(400,multipart(manager,path,pastOpening,null,UUID.randomUUID().toString()).statusCode());
        var created=multipart(manager,path,fields,null,key);assertEquals(200,created.statusCode(),"Merged default-artwork workflow must remain reachable");
        assertEquals(created.body(),multipart(manager,path,fields,null,key).body());UUID event=UUID.fromString(EventResponseValues.string(created.body(),"id").orElseThrow());
        String firstCover=EventResponseValues.string(created.body(),"coverImageUrl").orElseThrow();assertTrue(firstCover.startsWith("https://res.cloudinary.com/"));
        fixture.seated(event,"Merged HTTP",1,2);assertEquals(200,post(manager,"/api/events/"+event+"/submit","{}",UUID.randomUUID().toString(),true).statusCode());
        assertEquals(200,post(admin,"/api/admin/events/"+event+"/publish","{\"commissionRuleId\":\""+fixture.rule+"\"}",UUID.randomUUID().toString(),true).statusCode());
        assertEquals(firstCover,EventResponseValues.string(get(anonymous,"/api/events/"+event).body(),"coverImageUrl").orElseThrow());
        long version;try(Connection c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT version FROM dbo.tc_events WHERE id=?")){q.setString(1,event.toString());try(var rows=q.executeQuery()){rows.next();version=rows.getLong(1);}}
        var coverFields=Map.of("expectedVersion",Long.toString(version));String coverKey=UUID.randomUUID().toString();
        var cover=multipart(manager,"/api/events/"+event+"/cover",coverFields,DongSqlFixture.png(),coverKey);assertEquals(200,cover.statusCode());
        String updated=EventResponseValues.string(cover.body(),"coverImageUrl").orElseThrow();assertNotEquals(firstCover,updated);
        assertEquals(updated,EventResponseValues.string(get(anonymous,"/api/events/"+event).body(),"coverImageUrl").orElseThrow(),"Merged image writes must invalidate the catalog cache after commit");
        assertEquals(cover.body(),multipart(manager,"/api/events/"+event+"/cover",coverFields,DongSqlFixture.png(),coverKey).body());
    }
    @Test void compatibilityDraftDeleteJournalsCoverAfterCommitAndReplaysWithoutExposingCleanup() throws Exception {
        base=AppConfig.get("TC_DONG_HTTP_URL");var fixture=new DongSqlFixture();String password=UUID.randomUUID()+"!DongAcceptance";
        try(Connection c=DongSqlFixture.connection(null)){DongSqlFixture.execute(c,"UPDATE dbo.tc_users SET password_hash=? WHERE id=?",new PasswordHasher().hash(password),fixture.manager);}
        var manager=client();login(manager,fixture.manager,password);
        var fields=new TreeMap<>(fixture.create().fields());fields.put("title","Đêm nhạc nghiệm thu Đông xoá ảnh tương thích");
        var created=post(manager,"/api/organizations/"+fixture.org+"/events",new vn.ticketscenter.event.dto.EventCommand("CREATE",fixture.org,null,null,fields).json(),UUID.randomUUID().toString(),true);
        assertEquals(201,created.statusCode());UUID event=UUID.fromString(EventResponseValues.string(created.body(),"eventId").orElseThrow());
        var cover=multipart(manager,"/api/events/"+event+"/cover",Map.of(),DongSqlFixture.png(),UUID.randomUUID().toString());assertEquals(200,cover.statusCode());
        String publicId;
        try(Connection c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT event_image_public_id FROM dbo.tc_events WHERE id=?")) {
            q.setString(1,event.toString());try(var rows=q.executeQuery()){rows.next();publicId=rows.getString(1);}
        }
        assertTrue(publicId.startsWith("ticketscenter/events/"+event+"/"));
        String key=UUID.randomUUID().toString();var deleted=post(manager,"/api/events/"+event+"/delete","{}",key,true);assertEquals(200,deleted.statusCode());
        assertFalse(deleted.body().contains(publicId));assertFalse(deleted.body().contains("cleanup_json"));
        Path journal=Path.of("target/dong-runtime/cover-orphans",vn.ticketscenter.event.dto.EventCommand.sha256(publicId)+".json");
        assertTrue(Files.exists(journal),"Production runtime must wire the postcommit orphan journal");
        assertEquals(publicId,EventResponseValues.string(Files.readString(journal),"publicId").orElseThrow());
        Files.delete(journal);assertEquals(deleted.body(),post(manager,"/api/events/"+event+"/delete","{}",key,true).body());
        assertTrue(Files.exists(journal),"Durable replay must repair a lost journal entry without another deletion");
        try(Connection c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE event_image_public_id=?)+(SELECT COUNT_BIG(*) FROM dbo.tc_users WHERE avatar_public_id=?)")) {
            q.setString(1,publicId);q.setString(2,publicId);try(var rows=q.executeQuery()){rows.next();assertEquals(0,rows.getLong(1),"Only this unreferenced generated asset may be cleaned");}
        }
        vn.ticketscenter.storage.CloudinaryStorageService.fromEnvironment().delete(publicId);
    }
    @Test void completeDongFlowRunsThroughActualWarAndRendersAllOwnedPages() throws Exception {
        base=AppConfig.get("TC_DONG_HTTP_URL");assertNotNull(base,"Start a dedicated local WAR and provide TC_DONG_HTTP_URL");
        URI uri=URI.create(base);assertTrue(Set.of("127.0.0.1","localhost").contains(uri.getHost()),"Only a local acceptance runtime is allowed");
        var fixture=new DongSqlFixture();String password=UUID.randomUUID()+"!DongAcceptance";
        try(Connection c=DongSqlFixture.connection(null)){for(UUID id:List.of(fixture.manager,fixture.other,fixture.admin))DongSqlFixture.execute(c,"UPDATE dbo.tc_users SET password_hash=? WHERE id=?",new PasswordHasher().hash(password),id);}
        var anonymous=client();assertEquals(200,get(anonymous,"/api/events").statusCode());assertEquals(401,get(anonymous,"/api/organizations/"+fixture.org+"/events").statusCode());
        var manager=client();var admin=client();var other=client();login(manager,fixture.manager,password);login(admin,fixture.admin,password);login(other,fixture.other,password);
        assertEquals(403,get(manager,"/api/admin/events").statusCode());assertEquals(403,get(other,"/api/organizations/"+fixture.org+"/events").statusCode());
        assertEquals(403,get(manager,"/events/review").statusCode());
        var reportRoute=get(manager,"/api/organizations/"+fixture.org+"/reports");assertEquals(400,reportRoute.statusCode());assertTrue(reportRoute.body().contains("INVALID_REPORT_FILTER"),"Organization Report requests must remain with their existing controller");
        var overview=get(manager,"/api/organizations/"+fixture.org+"/overview");assertEquals(200,overview.statusCode(),"The merged readside dispatcher must preserve organization overview beside Event routes");
        for(String path:List.of("/events/manage","/events/manage/editor?organizationId="+fixture.org)) {var page=get(manager,path);assertEquals(200,page.statusCode(),"Owned JSP did not render");assertTrue(page.body().contains("dong-events.js"));}
        var review=get(admin,"/events/review");assertEquals(200,review.statusCode());assertTrue(review.body().contains("review-zones"));
        var fields=new TreeMap<>(fixture.create().fields());fields.put("title","Đêm nhạc nghiệm thu Đông");String body=new vn.ticketscenter.event.dto.EventCommand("CREATE",fixture.org,null,null,fields).json();
        String path="/api/organizations/"+fixture.org+"/events",key=UUID.randomUUID().toString();
        assertEquals(403,post(manager,path,body,key,false).statusCode());assertEquals(400,post(manager,path,body,null,true).statusCode());
        var created=post(manager,path,body,key,true);assertEquals(201,created.statusCode());assertEquals(created.body(),post(manager,path,body,key,true).body());
        UUID event=UUID.fromString(EventResponseValues.string(created.body(),"eventId").orElseThrow());
        assertTrue(get(manager,path+"/"+event).body().contains("Đêm nhạc nghiệm thu Đông"),"UTF-8 input must survive the servlet boundary");
        assertEquals(403,post(other,"/api/events/"+event+"/edit","{\"description\":\"Wrong owner\"}",UUID.randomUUID().toString(),true).statusCode());
        var zone=post(manager,"/api/events/"+event+"/zones","{\"name\":\"VIP HTTP\",\"zoneType\":\"SEATED\",\"basePrice\":\"300000\",\"rows\":2,\"seatsPerRow\":2}",UUID.randomUUID().toString(),true);assertEquals(201,zone.statusCode());
        assertTrue(get(manager,"/api/events/"+event+"/layout").body().contains("A1"));
        String oversizedKey=UUID.randomUUID().toString();
        var oversized=multipart(manager,"/api/events/"+event+"/cover",Map.of("expectedVersion","0"),new byte[5_242_881],oversizedKey);
        assertEquals(413,oversized.statusCode(),"Versioned cover parsing must report the upload limit, not a dependency failure");
        try(Connection c=DongSqlFixture.connection(null);var q=c.prepareStatement("SELECT COUNT_BIG(*) FROM dbo.tc_event_image_requests WHERE actor_id=? AND request_id=?")) {
            q.setString(1,fixture.manager.toString());q.setString(2,oversizedKey);try(var rows=q.executeQuery()){rows.next();assertEquals(0,rows.getLong(1),"Oversized multipart must fail before the durable image claim");}
        }
        String token=EventResponseValues.string(get(manager,"/api/auth/csrf").body(),"token").orElseThrow();
        String boundary="dong-"+UUID.randomUUID();var multipart=new java.io.ByteArrayOutputStream();
        multipart.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cover.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(DongSqlFixture.png());multipart.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var upload=manager.send(HttpRequest.newBuilder(URI.create(base+"/api/events/"+event+"/cover")).timeout(Duration.ofSeconds(40)).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-Token",token).header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,upload.statusCode(),"Multipart configuration and real provider must work through the filter route");
        assertEquals(200,post(manager,"/api/events/"+event+"/submit","{}",UUID.randomUUID().toString(),true).statusCode());
        assertEquals(200,post(admin,"/api/admin/events/"+event+"/publish","{\"commissionRuleId\":\""+fixture.rule+"\"}",UUID.randomUUID().toString(),true).statusCode());
        assertEquals(200,get(anonymous,"/api/events/"+event).statusCode());
        for(String query:List.of("eventId=","id=")) {
            var detail=get(anonymous,"/event-detail?"+query+event);assertEquals(200,detail.statusCode());
            assertTrue(detail.body().contains("event-hold-link"));assertTrue(detail.body().contains("select-tickets?eventId="));
            assertTrue(detail.body().contains("zone-status"),"Keep the Event seat projection beside the Hold handoff");
        }
        var selection=get(anonymous,"/select-tickets?eventId="+event);assertEquals(200,selection.statusCode());
        assertTrue(selection.body().contains("ticket-hold.js"),"The merged Hold page must remain reachable from Event detail");
        assertEquals(302,get(anonymous,"/api/events/"+event+"/image").statusCode());
        // Keep this isolated fixture briefly for browser acceptance, credentials only in ignored target output.
        Files.createDirectories(Path.of("target/dong-runtime"));
        String profile="{\"baseUrl\":"+HttpResponses.jsonString(base)+",\"manager\":"+HttpResponses.jsonString("dong_"+fixture.manager.toString().replace("-","")+"@example.test")
            +",\"admin\":"+HttpResponses.jsonString("dong_"+fixture.admin.toString().replace("-","")+"@example.test")+",\"password\":"+HttpResponses.jsonString(password)
            +",\"eventId\":\""+event+"\",\"organizationId\":\""+fixture.org+"\"}";
        Files.writeString(Path.of("target/dong-runtime/browser-fixture.json"),profile);
        try(Connection c=DongSqlFixture.connection(null)){DongSqlFixture.execute(c,"UPDATE dbo.tc_users SET auth_version=auth_version+1 WHERE id=?",fixture.other);}
        assertEquals(401,get(other,"/api/organizations/"+fixture.otherOrg+"/events").statusCode());
    }
}
