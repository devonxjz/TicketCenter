package vn.ticketscenter.ticketing.integration;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import vn.ticketscenter.config.web.RequestValidationFilter;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.ticketing.cache.RedisHoldIdempotency;
import vn.ticketscenter.ticketing.controller.HoldApiServlet;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HoldHttpIT {
    HoldFixture f;
    Object tomcat;
    URLClassLoader loader;
    Path base;
    String url, csrf;
    HttpClient client;

    @BeforeEach void start() throws Exception {
        f = new HoldFixture();
        base = Files.createTempDirectory("liem01-http-");
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
        filter(context, "request-validation", new RequestValidationFilter());
        filter(context, "csrf", new CsrfFilter());
        Class<?> contextType = loader.loadClass("org.apache.catalina.Context");
        type.getMethod("addServlet", contextType, String.class, Servlet.class).invoke(null, context, "holds", new HoldApiServlet(f.service));
        for (String path : List.of("/api/holds", "/api/holds/*", "/api/me/hold", "/api/hold-options"))
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

    @Test void actualCookiesCsrfCreateReadReplayCancelAndOptionsUseBareWireContract() throws Exception {
        var options = get("/api/hold-options?eventId=" + f.inventory.event());
        assertEquals(200, options.statusCode());
        assertEquals(f.inventory.event().toString(), new JSONObject(options.body()).getString("eventId"));
        UUID key = UUID.randomUUID();
        var created = post("/api/holds", key, selection(), csrf, "application/json");
        assertEquals(201, created.statusCode(), created.body());
        JSONObject body = new JSONObject(created.body());
        assertEquals(Set.of("id", "eventId", "eventTitle", "createdAt", "expiresAt", "outcome", "items"), body.keySet());
        assertEquals("Hold fixture", body.getString("eventTitle"));
        JSONObject item = body.getJSONArray("items").getJSONObject(0);
        assertEquals("Floor", item.getString("zoneName"));
        assertTrue(item.isNull("seatLabel"));
        assertEquals("CREATED", body.getString("outcome"));
        assertEquals(created.body(), post("/api/holds", key, selection(), csrf, "application/json").body());
        var active = get("/api/me/hold");
        assertEquals("ACTIVE", new JSONObject(active.body()).getJSONObject("hold").getString("status"));
        assertEquals("no-store", active.headers().firstValue("Cache-Control").orElseThrow());
        var cancelled = post("/api/holds/" + body.getString("id") + "/cancel", UUID.randomUUID(), "{}", csrf, "application/json");
        assertEquals(200, cancelled.statusCode(), cancelled.body());
        assertEquals("CANCELLED", new JSONObject(cancelled.body()).getString("outcome"));
        assertTrue(new JSONObject(get("/api/me/hold").body()).isNull("hold"));
        assertEquals(created.body(), post("/api/holds", key, selection(), csrf, "application/json").body());
    }

    @Test void authenticationCsrfAndStrictInputRejectWrites() throws Exception {
        var anonymous = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url + "/api/me/hold")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode());
        assertEquals("UNAUTHENTICATED", new JSONObject(anonymous.body()).getJSONObject("error").getString("code"));
        var wrongCsrf = post("/api/holds", UUID.randomUUID(), selection(), "wrong", "application/json");
        assertEquals(403, wrongCsrf.statusCode());
        assertEquals("CSRF_TOKEN_INVALID", new JSONObject(wrongCsrf.body()).getJSONObject("error").getString("code"));
        for (String invalid : List.of(selection().replace("\"selections\"", "\"unknown\""), selection().replace("\"quantity\":1", "\"quantity\":9"), "{}", selection().replace("\"eventId\":", "\"eventId\":\"bad\",\"eventId\":")))
            assertEquals(400, post("/api/holds", UUID.randomUUID(), invalid, csrf, "application/json").statusCode());
        assertEquals(415, post("/api/holds", UUID.randomUUID(), selection(), csrf, "text/plain").statusCode());
        assertEquals(413, post("/api/holds", UUID.randomUUID(), " ".repeat(65_537), csrf, "application/json").statusCode());
        assertEquals(400, get("/api/hold-options?eventId=" + f.inventory.event() + "&eventId=" + f.inventory.event()).statusCode());
        assertEquals(0, f.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE user_id=?", f.actor.id()));
    }

    @Test void sqlAuthorizationAfterCachedSuccessAndSqlFailureReturnCorrectStatus() throws Exception {
        UUID key = UUID.randomUUID();
        assertEquals(201, post("/api/holds", key, selection(), csrf, "application/json").statusCode());
        assertEquals(200, get("/api/me/hold").statusCode());
        f.sql("UPDATE dbo.tc_users SET auth_version=auth_version+1 WHERE id=?", f.actor.id());
        assertEquals(401, post("/api/holds", key, selection(), csrf, "application/json").statusCode());
        assertEquals(401, get("/api/me/hold").statusCode());
        f.buyer.unavailable = true;
        var result = get("/api/me/hold");
        assertEquals(503, result.statusCode());
        assertEquals("HOLD_UNAVAILABLE", new JSONObject(result.body()).getJSONObject("error").getString("code"));
        f.buyer.unavailable = false;
    }

    String selection() { return "{\"eventId\":\"" + f.inventory.event() + "\",\"selections\":[{\"zoneId\":\"" + f.inventory.standing() + "\",\"seatId\":null,\"quantity\":1}]}"; }

    HttpResponse<String> post(String path, UUID key, String body, String token, String media) throws Exception {
        f.redisKeys.add(RedisHoldIdempotency.redisKey(f.actor.id(), key.toString()));
        return client.send(HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(40))
                .header("Content-Type", media).header("Idempotency-Key", key.toString()).header("X-CSRF-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(40)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        try { return target.getClass().getMethod(name, types).invoke(target, args); }
        catch (InvocationTargetException failure) { if (failure.getCause() instanceof Exception cause) throw cause; throw failure; }
    }
}
