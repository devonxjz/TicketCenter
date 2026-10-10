package vn.ticketscenter.event.integration;

import static vn.ticketscenter.event.integration.EventImageHttpIT.invoke;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import vn.ticketscenter.event.controller.EventApiServlet;
import vn.ticketscenter.event.controller.EventDetailServlet;
import vn.ticketscenter.event.controller.HomeServlet;
import vn.ticketscenter.event.dto.EventSave;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.event.service.EventService;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.storage.CloudinaryStorageService;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EventImageWebFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("serve-local-test"))
            throw new IllegalArgumentException("Pass serve-local-test");
        try (EventImageFixture fixture = new EventImageFixture()) {
            var storage = CloudinaryStorageService.fromEnvironment();
            EventService service = new EventService(new EventRepository(fixture.source), storage);
            var published =
                    service.save(
                            fixture.actor,
                            new EventSave(
                                    UUID.randomUUID(),
                                    EventSave.Kind.CREATE,
                                    fixture.organization,
                                    0,
                                    fixture.fields("Cloudinary browser verification"),
                                    EventImageFixture.png(0x12bb88)));
            fixture.sql("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?", published.id());
            Path repository = Path.of(System.getProperty("user.home"), ".m2", "repository");
            List<URL> jars = new ArrayList<>();
            for (String relative :
                    List.of(
                            "org/apache/tomcat/embed/tomcat-embed-core/11.0.25/tomcat-embed-core-11.0.25.jar",
                            "org/apache/tomcat/embed/tomcat-embed-jasper/11.0.25/tomcat-embed-jasper-11.0.25.jar",
                            "org/apache/tomcat/embed/tomcat-embed-el/11.0.25/tomcat-embed-el-11.0.25.jar",
                            "org/apache/tomcat/tomcat-annotations-api/11.0.25/tomcat-annotations-api-11.0.25.jar",
                            "org/eclipse/jdt/ecj/3.46.0/ecj-3.46.0.jar",
                            "jakarta/servlet/jsp/jstl/jakarta.servlet.jsp.jstl-api/3.0.1/jakarta.servlet.jsp.jstl-api-3.0.1.jar",
                            "org/glassfish/web/jakarta.servlet.jsp.jstl/3.0.1/jakarta.servlet.jsp.jstl-3.0.1.jar")) {
                Path jar = repository.resolve(relative);
                if (!Files.isRegularFile(jar))
                    throw new IllegalStateException(
                            "Missing official local web test dependency " + jar.getFileName());
                jars.add(jar.toUri().toURL());
            }
            Path base = Path.of("target", "event-image-web-test").toAbsolutePath();
            Files.createDirectories(base);
            Path stop = base.resolve("stop");
            Files.deleteIfExists(stop);
            try (URLClassLoader loader =
                    new URLClassLoader(
                            jars.toArray(URL[]::new),
                            EventImageWebFixture.class.getClassLoader())) {
                loader.loadClass("org.apache.catalina.webresources.TomcatURLStreamHandlerFactory")
                        .getMethod("disable")
                        .invoke(null);
                Class<?> type = loader.loadClass("org.apache.catalina.startup.Tomcat");
                Class<?> contextType = loader.loadClass("org.apache.catalina.Context");
                Object tomcat = type.getConstructor().newInstance();
                invoke(tomcat, "setBaseDir", new Class<?>[] {String.class}, base.toString());
                invoke(tomcat, "setPort", new Class<?>[] {int.class}, 0);
                Object connector = invoke(tomcat, "getConnector", new Class<?>[] {});
                invoke(
                        connector,
                        "setProperty",
                        new Class<?>[] {String.class, String.class},
                        "address",
                        "127.0.0.1");
                Object context =
                        invoke(
                                tomcat,
                                "addContext",
                                new Class<?>[] {String.class, String.class},
                                "",
                                Path.of("src/main/webapp").toAbsolutePath().toString());
                invoke(context, "setParentClassLoader", new Class<?>[] {ClassLoader.class}, loader);
                invoke(
                        context,
                        "addServletContainerInitializer",
                        new Class<?>[] {ServletContainerInitializer.class, java.util.Set.class},
                        loader.loadClass("org.apache.jasper.servlet.JasperInitializer")
                                .getConstructor()
                                .newInstance(),
                        null);
                Object api =
                        servlet(
                                type,
                                contextType,
                                context,
                                "events",
                                new EventApiServlet(service),
                                "/api/events",
                                "/api/events/*",
                                "/api/organizations/*",
                                "/api/manager/events",
                                "/api/manager/events/*",
                                "/api/manager/event-options");
                invoke(
                        api,
                        "setMultipartConfigElement",
                        new Class<?>[] {MultipartConfigElement.class},
                        new MultipartConfigElement(base.toString(), 5_242_880, 5_400_000, 0));
                servlet(type, contextType, context, "home", new HomeServlet(), "");
                servlet(
                        type,
                        contextType,
                        context,
                        "detail",
                        new EventDetailServlet(),
                        "/event-detail");
                servlet(
                        type,
                        contextType,
                        context,
                        "editor",
                        forward("/WEB-INF/views/organizer/event-editor.jsp"),
                        "/organizer/event-editor",
                        "/organizer/events/new");
                servlet(
                        type,
                        contextType,
                        context,
                        "manager",
                        forward("/WEB-INF/views/organizer/events.jsp"),
                        "/organizer/events");
                servlet(
                        type,
                        contextType,
                        context,
                        "jsp",
                        (Servlet)
                                loader.loadClass("org.apache.jasper.servlet.JspServlet")
                                        .getConstructor()
                                        .newInstance(),
                        "*.jsp");
                servlet(
                        type,
                        contextType,
                        context,
                        "static",
                        (Servlet)
                                loader.loadClass("org.apache.catalina.servlets.DefaultServlet")
                                        .getConstructor()
                                        .newInstance(),
                        "/");
                servlet(
                        type,
                        contextType,
                        context,
                        "session",
                        new HttpServlet() {
                            @Override
                            protected void doGet(
                                    HttpServletRequest request, HttpServletResponse response)
                                    throws IOException {
                                SecurityContext.setSessionUser(request, fixture.actor);
                                response.sendRedirect("/organizer/events");
                            }
                        },
                        "/fixture-session");
                servlet(
                        type,
                        contextType,
                        context,
                        "csrf",
                        new HttpServlet() {
                            @Override
                            protected void doGet(
                                    HttpServletRequest request, HttpServletResponse response)
                                    throws IOException {
                                response.setContentType("application/json");
                                response.getWriter()
                                        .write(
                                                "{\"data\":{\"token\":\""
                                                        + SecurityContext.getOrCreateCsrfToken(
                                                                request)
                                                        + "\"}}");
                            }
                        },
                        "/api/auth/csrf");
                try {
                    invoke(tomcat, "start", new Class<?>[] {});
                    int port = (int) invoke(connector, "getLocalPort", new Class<?>[] {});
                    System.out.println(
                            "LOCAL_TEST_URL=http://127.0.0.1:" + port + "/fixture-session");
                    System.out.println("PUBLISHED_EVENT_ID=" + published.id());
                    System.out.println(
                            "Create target/event-image-web-test/stop to clean and stop this"
                                + " fixture.");
                    while (!Files.exists(stop)) Thread.sleep(250);
                } finally {
                    invoke(tomcat, "stop", new Class<?>[] {});
                    invoke(tomcat, "destroy", new Class<?>[] {});
                    List<String> testAssets = new ArrayList<>();
                    try (var connection = fixture.source.getConnection();
                            var query =
                                    connection.prepareStatement(
                                            "SELECT public_id FROM dbo.tc_event_image_requests"
                                                + " WHERE organization_id=? AND public_id IS NOT"
                                                + " NULL")) {
                        query.setString(1, fixture.organization.toString());
                        try (var rows = query.executeQuery()) {
                            while (rows.next()) testAssets.add(rows.getString(1));
                        }
                    }
                    for (String publicId : testAssets) storage.delete(publicId);
                }
            }
        }
    }

    private static HttpServlet forward(String jsp) {
        return new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response)
                    throws jakarta.servlet.ServletException, IOException {
                request.getRequestDispatcher(jsp).forward(request, response);
            }
        };
    }

    private static Object servlet(
            Class<?> tomcat,
            Class<?> contextType,
            Object context,
            String name,
            Servlet servlet,
            String... paths)
            throws Exception {
        Object wrapper =
                tomcat.getMethod("addServlet", contextType, String.class, Servlet.class)
                        .invoke(null, context, name, servlet);
        for (String path : paths)
            invoke(
                    context,
                    "addServletMappingDecoded",
                    new Class<?>[] {String.class, String.class},
                    path,
                    name);
        return wrapper;
    }
}
