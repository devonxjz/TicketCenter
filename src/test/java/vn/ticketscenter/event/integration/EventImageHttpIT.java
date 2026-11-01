package vn.ticketscenter.event.integration;

import vn.ticketscenter.config.web.TestServlets;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.junit.jupiter.api.*;

import vn.ticketscenter.config.web.RequestValidationFilter;
import vn.ticketscenter.event.controller.EventApiServlet;
import vn.ticketscenter.identity.security.SecurityContext;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

class EventImageHttpIT {
    EventImageFixture f;
    Object tomcat;
    URLClassLoader loader;
    Path base;
    String url, csrf;
    HttpClient client;

    @BeforeEach
    void start() throws Exception {
        f = new EventImageFixture();
        base = Files.createTempDirectory("liem43-http-");
        Path home =
                Path.of(
                        System.getProperty("user.home"),
                        ".m2",
                        "repository",
                        "org",
                        "apache",
                        "tomcat");
        List<URL> jars = new ArrayList<>();
        jars.add(
                home.resolve("embed/tomcat-embed-core/11.0.25/tomcat-embed-core-11.0.25.jar")
                        .toUri()
                        .toURL());
        jars.add(
                home.resolve("tomcat-annotations-api/11.0.25/tomcat-annotations-api-11.0.25.jar")
                        .toUri()
                        .toURL());
        loader = new URLClassLoader(jars.toArray(URL[]::new), getClass().getClassLoader());
        loader.loadClass("org.apache.catalina.webresources.TomcatURLStreamHandlerFactory")
                .getMethod("disable")
                .invoke(null);
        Class<?> type = loader.loadClass("org.apache.catalina.startup.Tomcat");
        tomcat = type.getConstructor().newInstance();
        invoke(tomcat, "setBaseDir", new Class<?>[] {String.class}, base.toString());
        invoke(tomcat, "setPort", new Class<?>[] {int.class}, 0);
        Object connector = invoke(tomcat, "getConnector", new Class<?>[] {});
        Object context =
                invoke(
                        tomcat,
                        "addContext",
                        new Class<?>[] {String.class, String.class},
                        "",
                        base.toString());
        Class<?> contextType = loader.loadClass("org.apache.catalina.Context");
        Class<?> filterDefType =
                loader.loadClass("org.apache.tomcat.util.descriptor.web.FilterDef");
        Object validation = filterDefType.getConstructor().newInstance();
        invoke(validation, "setFilterName", new Class<?>[] {String.class}, "request-validation");
        invoke(
                validation,
                "setFilter",
                new Class<?>[] {Filter.class},
                new RequestValidationFilter());
        invoke(context, "addFilterDef", new Class<?>[] {filterDefType}, validation);
        Class<?> filterMapType =
                loader.loadClass("org.apache.tomcat.util.descriptor.web.FilterMap");
        Object mapping = filterMapType.getConstructor().newInstance();
        invoke(mapping, "setFilterName", new Class<?>[] {String.class}, "request-validation");
        invoke(mapping, "addURLPattern", new Class<?>[] {String.class}, "/api/*");
        invoke(context, "addFilterMap", new Class<?>[] {filterMapType}, mapping);
        Object wrapper =
                type.getMethod("addServlet", contextType, String.class, Servlet.class)
                        .invoke(null, context, "events", new EventApiServlet() { @Override public void init() { TestServlets.store(getServletContext(), f.service); } });
        invoke(
                wrapper,
                "setMultipartConfigElement",
                new Class<?>[] {MultipartConfigElement.class},
                new MultipartConfigElement(base.toString(), 5_242_880, 5_400_000, 0));
        for (String path :
                List.of(
                        "/api/events",
                        "/api/events/*",
                        "/api/organizations/*",
                        "/api/manager/events",
                        "/api/manager/events/*",
                        "/api/manager/event-options"))
            invoke(
                    context,
                    "addServletMappingDecoded",
                    new Class<?>[] {String.class, String.class},
                    path,
                    "events");
        type.getMethod("addServlet", contextType, String.class, Servlet.class)
                .invoke(
                        null,
                        context,
                        "fixture-session",
                        new HttpServlet() {
                            @Override
                            protected void doGet(
                                    HttpServletRequest request, HttpServletResponse response)
                                    throws IOException {
                                SecurityContext.setSessionUser(request, f.actor);
                                response.setContentType("text/plain");
                                response.getWriter()
                                        .write(SecurityContext.getOrCreateCsrfToken(request));
                            }
                        });
        invoke(
                context,
                "addServletMappingDecoded",
                new Class<?>[] {String.class, String.class},
                "/fixture-session",
                "fixture-session");
        invoke(tomcat, "start", new Class<?>[] {});
        int port = (int) invoke(connector, "getLocalPort", new Class<?>[] {});
        url = "http://127.0.0.1:" + port;
        client =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
        csrf =
                client.send(
                                HttpRequest.newBuilder(URI.create(url + "/fixture-session"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .body();
    }

    @AfterEach
    void stop() throws Exception {
        try {
            if (tomcat != null) {
                invoke(tomcat, "stop", new Class<?>[] {});
                invoke(tomcat, "destroy", new Class<?>[] {});
            }
        } finally {
            if (loader != null) loader.close();
            if (f != null) f.close();
            if (base != null)
                try (var paths = Files.walk(base)) {
                    for (Path p : paths.sorted(Comparator.reverseOrder()).toList())
                        Files.deleteIfExists(p);
                }
        }
    }

    @Test
    void actualMultipartCreateEditKeepAndRead() throws Exception {
        UUID key = UUID.randomUUID();
        byte[] body = multipart(fields("HTTP Event", null), EventImageFixture.png(21), false);
        HttpResponse<String> created =
                post("/api/organizations/" + f.organization + "/events", key, body, csrf);
        assertEquals(200, created.statusCode(), created.body());
        assertTrue(created.body().contains("https://res.cloudinary.com/fixture/"));
        assertEquals(
                created.body(),
                post("/api/organizations/" + f.organization + "/events", key, body, csrf).body());
        UUID event = f.service.managedEvents(f.actor, f.organization).getFirst().id();
        String cover = f.service.managedEvent(f.actor, event).imagePublicId();
        HttpResponse<String> edited =
                post(
                        "/api/events/" + event + "/edit",
                        UUID.randomUUID(),
                        multipart(fields("HTTP Edited", "0"), null, false),
                        csrf);
        assertEquals(200, edited.statusCode(), edited.body());
        assertEquals(cover, f.service.managedEvent(f.actor, event).imagePublicId());
        assertEquals("HTTP Edited", f.service.managedEvent(f.actor, event).title());
        HttpResponse<String> editor = get("/api/manager/events/" + event);
        assertEquals(200, editor.statusCode());
        assertTrue(editor.body().contains("HTTP Edited"));
        f.sql("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?", event);
        assertEquals(200, get("/api/events/" + event).statusCode());
        assertTrue(get("/api/events").body().contains("HTTP Edited"));
    }

    @Test
    void noSessionAndWrongCsrfCannotMutate() throws Exception {
        byte[] body = multipart(fields("Forbidden", null), EventImageFixture.png(22), false);
        assertEquals(
                403,
                post(
                                "/api/organizations/" + f.organization + "/events",
                                UUID.randomUUID(),
                                body,
                                "wrong")
                        .statusCode());
        HttpClient anonymous = HttpClient.newHttpClient();
        HttpResponse<String> result =
                anonymous.send(
                        request(
                                "/api/organizations/" + f.organization + "/events",
                                UUID.randomUUID(),
                                body,
                                csrf),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(401, result.statusCode());
        assertEquals(
                0,
                f.count(
                        "SELECT COUNT(*) FROM dbo.tc_events WHERE organization_id=?",
                        f.organization));
    }

    @Test
    void duplicateFieldsUnknownFieldsAndMalformedFileAreRejected() throws Exception {
        assertEquals(
                400,
                post(
                                "/api/organizations/" + f.organization + "/events",
                                UUID.randomUUID(),
                                multipart(fields("Duplicate", null), null, true),
                                csrf)
                        .statusCode());
        Map<String, String> extra = fields("Extra", null);
        extra.put("cover_image_url", "https://attacker.test/image.png");
        assertEquals(
                400,
                post(
                                "/api/organizations/" + f.organization + "/events",
                                UUID.randomUUID(),
                                multipart(extra, null, false),
                                csrf)
                        .statusCode());
        HttpResponse<String> invalid =
                post(
                        "/api/organizations/" + f.organization + "/events",
                        UUID.randomUUID(),
                        multipart(
                                fields("Bad image", null),
                                "not an image".getBytes(StandardCharsets.UTF_8),
                                false),
                        csrf);
        assertEquals(400, invalid.statusCode(), invalid.body());
        Map<String, String> malformedTime = fields("Bad time", null);
        malformedTime.put("saleStart", "not-a-time");
        assertEquals(
                400,
                post(
                                "/api/organizations/" + f.organization + "/events",
                                UUID.randomUUID(),
                                multipart(malformedTime, null, false),
                                csrf)
                        .statusCode());
        assertEquals(
                0,
                f.count(
                        "SELECT COUNT(*) FROM dbo.tc_events WHERE organization_id=?",
                        f.organization));
    }

    @Test
    void existingCoverRouteUsesSameMutationFlow() throws Exception {
        var event = f.create();
        byte[] body =
                multipart(
                        new LinkedHashMap<>(Map.of("expectedVersion", "0")),
                        EventImageFixture.png(23),
                        false);
        HttpResponse<String> result =
                post("/api/events/" + event.id() + "/cover", UUID.randomUUID(), body, csrf);
        assertEquals(200, result.statusCode(), result.body());
        assertEquals(1, f.service.managedEvent(f.actor, event.id()).version());
        assertNotEquals(
                event.imagePublicId(), f.service.managedEvent(f.actor, event.id()).imagePublicId());
    }

    @Test
    void productionFilterAllowsLargeImageOnlyOnUploadRoutes() throws Exception {
        var image =
                new java.awt.image.BufferedImage(
                        256, 256, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var random = new Random(43);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, random.nextInt());
        var encoded = new ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(image, "png", encoded));
        byte[] body = multipart(fields("Large HTTP image", null), encoded.toByteArray(), false);
        assertTrue(body.length > 64 * 1024);
        var created =
                post(
                        "/api/organizations/" + f.organization + "/events",
                        UUID.randomUUID(),
                        body,
                        csrf);
        assertEquals(200, created.statusCode(), created.body());
        assertEquals(
                413,
                post(
                                "/api/events/" + UUID.randomUUID() + "/unrelated",
                                UUID.randomUUID(),
                                body,
                                csrf)
                        .statusCode());
        assertEquals(
                413,
                post(
                                "/api/organizations/" + f.organization + "/events",
                                UUID.randomUUID(),
                                new byte[5_400_001],
                                csrf)
                        .statusCode());
        assertEquals(1, f.service.managedEvents(f.actor, f.organization).size());
    }

    Map<String, String> fields(String title, String version) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("title", title);
        values.put("description", "HTTP fixture");
        values.put("categoryId", f.category.toString());
        values.put("venueName", "HTTP venue");
        values.put("venueAddress", "HTTP address");
        values.put("saleStart", "2030-01-01T00:00:00Z");
        values.put("saleEnd", "2030-01-01T01:00:00Z");
        values.put("startTime", "2030-01-01T02:00:00Z");
        values.put("endTime", "2030-01-01T03:00:00Z");
        if (version != null) values.put("expectedVersion", version);
        return values;
    }

    static byte[] multipart(Map<String, String> values, byte[] image, boolean duplicate)
            throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (var entry : values.entrySet()) part(b, entry.getKey(), entry.getValue());
        if (duplicate) part(b, "title", "Second title");
        if (image != null) {
            b.write(
                    ("--liem43-boundary\r\n"
                                    + "Content-Disposition: form-data; name=\"file\";"
                                    + " filename=\"fixture.png\"\r\n"
                                    + "Content-Type: image/png\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
            b.write(image);
            b.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        b.write("--liem43-boundary--\r\n".getBytes(StandardCharsets.UTF_8));
        return b.toByteArray();
    }

    static void part(ByteArrayOutputStream b, String key, String value) throws IOException {
        b.write(
                ("--liem43-boundary\r\nContent-Disposition: form-data; name=\""
                                + key
                                + "\"\r\n\r\n"
                                + value
                                + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    HttpRequest request(String path, UUID key, byte[] body, String token) {
        return HttpRequest.newBuilder(URI.create(url + path))
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "multipart/form-data; boundary=liem43-boundary")
                .header("Idempotency-Key", key.toString())
                .header("X-CSRF-Token", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    HttpResponse<String> post(String path, UUID key, byte[] body, String token) throws Exception {
        return client.send(request(path, key, body, token), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(url + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static Object invoke(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        try {
            return target.getClass().getMethod(name, types).invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }
}
