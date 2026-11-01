package vn.ticketscenter.order.integration;

import vn.ticketscenter.config.web.TestServlets;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import vn.ticketscenter.config.web.RequestValidationFilter;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.ticketing.cache.RedisHoldIdempotency;
import vn.ticketscenter.order.controller.OrderApiServlet;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OrderHttpIT {
    OrderFixture f;
    Object tomcat;
    URLClassLoader loader;
    Path base;
    String url, csrf;
    HttpClient client;

    @BeforeEach void start() throws Exception {
        f = new OrderFixture();
        base = Files.createTempDirectory("liem02-http-");
        Path home = Path.of(System.getProperty("user.home"), ".m2", "repository", "org", "apache", "tomcat");
        loader = new URLClassLoader(new URL[]{
                home.resolve("embed/tomcat-embed-core/11.0.25/tomcat-embed-core-11.0.25.jar").toUri().toURL(),
                home.resolve("tomcat-annotations-api/11.0.25/tomcat-annotations-api-11.0.25.jar").toUri().toURL()}, getClass().getClassLoader());
        loader.loadClass("org.apache.catalina.webresources.TomcatURLStreamHandlerFactory").getMethod("disable").invoke(null);
        Class<?> type = loader.loadClass("org.apache.catalina.startup.Tomcat");
        tomcat = type.getConstructor().newInstance();
        invoke(tomcat, "setBaseDir", new Class<?>[]{String.class}, base.toString());
        invoke(tomcat, "setPort", new Class<?>[]{int.class}, 0);
        Object connector = invoke(tomcat, "getConnector", new Class<?>[]{});
        Object context = invoke(tomcat, "addContext", new Class<?>[]{String.class, String.class}, "", base.toString());
        // the servlet looks its service up in the context; a filter is initialised when the context starts
        filter(context, "fakes", new Filter() {
            @Override public void init(FilterConfig config) { TestServlets.store(config.getServletContext(), f.orderService); }
            @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException { chain.doFilter(request, response); }
        });
        filter(context, "request-validation", new RequestValidationFilter());
        filter(context, "csrf", new CsrfFilter());
        Class<?> contextType = loader.loadClass("org.apache.catalina.Context");
        type.getMethod("addServlet", contextType, String.class, Servlet.class).invoke(null, context, "holds", new OrderApiServlet());
        for (String path : List.of("/api/orders", "/api/orders/*", "/api/checkout"))
            invoke(context, "addServletMappingDecoded", new Class<?>[]{String.class, String.class}, path, "holds");
        type.getMethod("addServlet", contextType, String.class, Servlet.class).invoke(null, context, "fixture-session", new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                SecurityContext.setSessionUser(request, new UserPrincipal(f.actor.id(), PlatformRole.CUSTOMER, f.actor.authVersion()));
                response.getWriter().write(SecurityContext.getOrCreateCsrfToken(request));
            }
        });
        invoke(context, "addServletMappingDecoded", new Class<?>[]{String.class, String.class}, "/fixture-session", "fixture-session");
        invoke(tomcat, "start", new Class<?>[]{});
        url = "http://127.0.0.1:" + invoke(connector, "getLocalPort", new Class<?>[]{});
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
        csrf = get("/fixture-session").body();
    }

    private void filter(Object context, String name, Filter filter) throws Exception {
        Class<?> definition = loader.loadClass("org.apache.tomcat.util.descriptor.web.FilterDef");
        Object value = definition.getConstructor().newInstance();
        invoke(value, "setFilterName", new Class<?>[]{String.class}, name);
        invoke(value, "setFilter", new Class<?>[]{Filter.class}, filter);
        invoke(context, "addFilterDef", new Class<?>[]{definition}, value);
        Class<?> map = loader.loadClass("org.apache.tomcat.util.descriptor.web.FilterMap");
        Object mapping = map.getConstructor().newInstance();
        invoke(mapping, "setFilterName", new Class<?>[]{String.class}, name);
        invoke(mapping, "addURLPattern", new Class<?>[]{String.class}, "/api/*");
        invoke(context, "addFilterMap", new Class<?>[]{map}, mapping);
    }

    @AfterEach void stop() throws Exception {
        try {
            if (tomcat != null) { invoke(tomcat, "stop", new Class<?>[]{}); invoke(tomcat, "destroy", new Class<?>[]{}); }
        } finally {
            if (loader != null) loader.close();
            if (f != null) f.close();
            if (base != null) try (var paths = Files.walk(base)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    @Test void realCookiesCsrfOrderCouponPreviewRemoveAndReplayUseFrozenContract() throws Exception {
        UUID hold = OrderFixture.holdId(f.mutate(f.actor, f.standing(1)));
        UUID key = UUID.randomUUID(); String create = "{\"holdId\":\"" + hold + "\"}";
        assertTrue(new JSONObject(get("/api/checkout?holdId=" + hold).body()).isNull("order"));
        var created = post("/api/orders",key,create,csrf,"application/json");
        assertEquals(201,created.statusCode(),created.body());
        JSONObject order = new JSONObject(created.body()); UUID id=UUID.fromString(order.getString("orderId"));
        assertEquals("150000",order.getString("subtotal"));
        assertEquals(created.body(),post("/api/orders",key,create,csrf,"application/json").body());
        assertEquals("ACTIVE",new JSONObject(get("/api/checkout?holdId="+hold).body()).getJSONObject("order").getString("holdStatus"));
        UUID coupon=UUID.randomUUID();f.coupons.add(coupon);
        f.sql("INSERT dbo.tc_coupons(id,organization_id,code,discount_type,percentage_value,max_uses,valid_from,valid_to) VALUES(?,?,'SALE','PERCENTAGE',20,1,DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()))",coupon,f.organization);
        var preview=get("/api/orders/"+id+"/coupon-eligibility?couponCode=SALE");assertEquals(200,preview.statusCode());
        assertTrue(new JSONObject(preview.body()).getBoolean("eligible"));
        UUID applyKey=UUID.randomUUID();var applied=post("/api/orders/"+id+"/coupon",applyKey,"{\"couponCode\":\"SALE\"}",csrf,"application/json");
        assertEquals(200,applied.statusCode(),applied.body());assertEquals("30000",new JSONObject(applied.body()).getString("discountAmount"));
        assertEquals(applied.body(),post("/api/orders/"+id+"/coupon",applyKey,"{\"couponCode\":\"SALE\"}",csrf,"application/json").body());
        var conflict=post("/api/orders/"+id+"/coupon",applyKey,"{\"couponCode\":null}",csrf,"application/json");
        assertEquals(409,conflict.statusCode());assertEquals("IDEMPOTENCY_CONFLICT",new JSONObject(conflict.body()).getJSONObject("error").getString("code"));
        assertEquals(400,post("/api/orders/"+id+"/coupon",UUID.randomUUID(),"{\"couponCode\":\"SALE\",\"totalAmount\":\"0\"}",csrf,"application/json").statusCode());
        var removed=post("/api/orders/"+id+"/coupon",UUID.randomUUID(),"{\"couponCode\":null}",csrf,"application/json");
        assertEquals(200,removed.statusCode());assertTrue(new JSONObject(removed.body()).isNull("appliedCouponCode"));
        assertEquals("no-store",preview.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test void strictBoundaryRejectsAnonymousCsrfUnknownFieldsDuplicateKeyAndIdentityInput() throws Exception {
        UUID hold = OrderFixture.holdId(f.mutate(f.actor,f.standing(1)));String create="{\"holdId\":\""+hold+"\"}";
        var anonymous=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url+"/api/checkout?holdId="+hold)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,anonymous.statusCode());assertNotNull(new JSONObject(anonymous.body()).getJSONObject("error").getString("correlationId"));
        assertEquals(403,post("/api/orders",UUID.randomUUID(),create,"wrong","application/json").statusCode());
        for(String invalid:List.of("{}",create.replace("}",",\"price\":\"1\"}"),create.replace("}",",\"holdId\":\""+hold+"\"}")))
            assertEquals(400,post("/api/orders",UUID.randomUUID(),invalid,csrf,"application/json").statusCode());
        assertEquals(415,post("/api/orders",UUID.randomUUID(),create,csrf,"text/plain").statusCode());
        assertEquals(413,post("/api/orders",UUID.randomUUID()," ".repeat(16385),csrf,"application/json").statusCode());
        assertEquals(400,get("/api/checkout?holdId="+hold+"&holdId="+hold).statusCode());
        assertEquals(400,get("/api/checkout?holdId="+hold+"&actorId="+f.actor.id()).statusCode());
        var duplicate=client.send(HttpRequest.newBuilder(URI.create(url+"/api/orders")).header("Content-Type","application/json").header("X-CSRF-Token",csrf)
                .header("Idempotency-Key",UUID.randomUUID().toString()).header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString(create)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(400,duplicate.statusCode());
        var chunked=client.send(HttpRequest.newBuilder(URI.create(url+"/api/orders")).header("Content-Type","application/json").header("X-CSRF-Token",csrf)
                .header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(new byte[16385]))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(413,chunked.statusCode());
        var foreign=f.actor();UUID foreignHold=OrderFixture.holdId(f.mutate(foreign,f.standing(1)));
        assertEquals(403,get("/api/checkout?holdId="+foreignHold).statusCode());
        assertEquals(403,post("/api/orders",UUID.randomUUID(),"{\"holdId\":\""+foreignHold+"\"}",csrf,"application/json").statusCode());
        assertEquals(0,f.count("SELECT COUNT(*) FROM dbo.tc_orders WHERE hold_id=?",hold));
    }

    @Test void currentDatabaseRevocationAndAmbiguousCommitRemainSafeOverHttp() throws Exception {
        UUID hold=OrderFixture.holdId(f.mutate(f.actor,f.standing(1))),key=UUID.randomUUID();String create="{\"holdId\":\""+hold+"\"}";
        f.buyer.loseNextAcknowledgement.set(true);var created=post("/api/orders",key,create,csrf,"application/json");assertEquals(201,created.statusCode(),created.body());
        assertEquals(1,f.count("SELECT COUNT(*) FROM dbo.tc_orders WHERE hold_id=?",hold));
        f.sql("UPDATE dbo.tc_users SET auth_version=1 WHERE id=?",f.actor.id());
        var rejected=post("/api/orders",key,create,csrf,"application/json");assertEquals(401,rejected.statusCode());
        assertEquals("UNAUTHENTICATED",new JSONObject(rejected.body()).getJSONObject("error").getString("code"));
        assertEquals(401,get("/api/checkout?holdId="+hold).statusCode());
    }

    HttpResponse<String> post(String path,UUID key,String body,String token,String type) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url+path)).timeout(Duration.ofSeconds(40)).header("Content-Type",type).header("X-CSRF-Token",token)
                .header("Idempotency-Key",key.toString()).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url+path)).timeout(Duration.ofSeconds(40)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    static Object invoke(Object target,String name,Class<?>[] types,Object... args) throws Exception {
        try { return target.getClass().getMethod(name,types).invoke(target,args); }
        catch(InvocationTargetException failure) { if(failure.getCause() instanceof Exception cause) throw cause;throw failure; }
    }
}
