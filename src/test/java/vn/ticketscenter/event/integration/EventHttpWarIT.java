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
    @Test void completeDongFlowRunsThroughActualWarAndRendersAllOwnedPages() throws Exception {
        base=AppConfig.get("TC_DONG_HTTP_URL");assertNotNull(base,"Start a dedicated local WAR and provide TC_DONG_HTTP_URL");
        URI uri=URI.create(base);assertTrue(Set.of("127.0.0.1","localhost").contains(uri.getHost()),"Only a local acceptance runtime is allowed");
        var fixture=new DongSqlFixture();String password=UUID.randomUUID()+"!DongAcceptance";
        try(Connection c=DongSqlFixture.connection(null)){for(UUID id:List.of(fixture.manager,fixture.other,fixture.admin))DongSqlFixture.execute(c,"UPDATE dbo.tc_users SET password_hash=? WHERE id=?",new PasswordHasher().hash(password),id);}
        var anonymous=client();assertEquals(200,get(anonymous,"/api/events").statusCode());assertEquals(401,get(anonymous,"/api/organizations/"+fixture.org+"/events").statusCode());
        var manager=client();var admin=client();var other=client();login(manager,fixture.manager,password);login(admin,fixture.admin,password);login(other,fixture.other,password);
        assertEquals(403,get(manager,"/api/admin/events").statusCode());assertEquals(403,get(other,"/api/organizations/"+fixture.org+"/events").statusCode());
        assertEquals(403,get(manager,"/events/review").statusCode());
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
        String token=EventResponseValues.string(get(manager,"/api/auth/csrf").body(),"token").orElseThrow();
        String boundary="dong-"+UUID.randomUUID();var multipart=new java.io.ByteArrayOutputStream();
        multipart.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cover.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(DongSqlFixture.png());multipart.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var upload=manager.send(HttpRequest.newBuilder(URI.create(base+"/api/events/"+event+"/cover")).timeout(Duration.ofSeconds(40)).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-Token",token).header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,upload.statusCode(),"Multipart configuration and real provider must work through the filter route");
        assertEquals(200,post(manager,"/api/events/"+event+"/submit","{}",UUID.randomUUID().toString(),true).statusCode());
        assertEquals(200,post(admin,"/api/admin/events/"+event+"/publish","{\"commissionRuleId\":\""+fixture.rule+"\"}",UUID.randomUUID().toString(),true).statusCode());
        assertEquals(200,get(anonymous,"/api/events/"+event).statusCode());assertEquals(200,get(anonymous,"/event-detail?eventId="+event).statusCode());
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
