package vn.ticketscenter.event.image;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.event.service.EventService;
import vn.ticketscenter.storage.CloudinaryStorageService;
import vn.ticketscenter.storage.ImageContentValidator;
import vn.ticketscenter.storage.StorageService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

public final class EventImageMigration {
    private static final Set<String> SOURCE_HOSTS =
            Set.of("images.unsplash.com", "res.cloudinary.com");
    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    private EventImageMigration() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3 || !Set.of("audit", "apply", "cleanup").contains(arguments[0])) {
            throw new IllegalArgumentException(
                    "Usage: EventImageMigration audit|apply|cleanup expectedDatabase report.tsv");
        }
        try (var source = configuredSource()) {
            run(arguments, source);
        }
    }

    private static void run(String[] arguments, DataSource source) throws Exception {
        try (var connection = source.getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT DB_NAME()")) {
            result.next();
            if (!arguments[1].equals(result.getString(1)))
                throw new IllegalStateException(
                        "Database identity does not match expectedDatabase");
        }
        StorageService storage = CloudinaryStorageService.fromEnvironment();
        EventService service = EventService.maintenance(new EventRepository(source), storage);
        Path report = Path.of(arguments[2]).toAbsolutePath();
        if (Files.exists(report))
            throw new IllegalArgumentException("Report already exists; use a new path");
        if (report.getParent() != null) Files.createDirectories(report.getParent());
        if (arguments[0].equals("cleanup")) {
            int count = service.cleanup(100);
            Files.writeString(report, "cleaned_assets\n" + count + "\n", StandardCharsets.UTF_8);
            System.out.println("Cleanup completed: " + count);
            return;
        }
        int migrated = 0;
        int verified = 0;
        int failures = 0;
        try (var output = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
            output.write(
                    "event_id\tversion_before\tcategory\tsource\toriginal_cover\tresult\tpublic_id"
                            + "\tnew_url\n");
            for (var item : service.migrationInventory()) {
                String outcome = "AUDITED";
                String publicId = item.publicId();
                String url = item.imageUrl();
                try {
                    if (arguments[0].equals("apply")) {
                        if (validCloud(url)
                                && publicId != null
                                && storage.find(publicId)
                                        .filter(asset -> asset.secureUrl().equals(item.imageUrl()))
                                        .isPresent()
                                && url.equals(item.originalCoverUrl())) {
                            download(url);
                            outcome = "VERIFIED_EXISTING";
                            verified++;
                        } else {
                            byte[] image;
                            String reason;
                            try {
                                image = download(item.imageUrl());
                                reason = "MIGRATED_SOURCE";
                            } catch (IOException | IllegalArgumentException unavailable) {
                                image = EventImagePlaceholders.png(item.categorySlug());
                                reason =
                                        item.imageUrl() == null || item.imageUrl().isBlank()
                                                ? "PLACEHOLDER_MISSING"
                                                : "PLACEHOLDER_UNAVAILABLE_SOURCE";
                            }
                            String contentHash =
                                    HexFormat.of()
                                            .formatHex(
                                                    MessageDigest.getInstance("SHA-256")
                                                            .digest(image));
                            String identity =
                                    "event-image-v1|"
                                            + item.event().id()
                                            + "|"
                                            + item.event().version()
                                            + "|"
                                            + item.imageUrl()
                                            + "|"
                                            + item.originalCoverUrl()
                                            + "|"
                                            + contentHash;
                            UUID key =
                                    UUID.nameUUIDFromBytes(
                                            identity.getBytes(StandardCharsets.UTF_8));
                            var saved =
                                    service.migrateCover(
                                            item.event().id(), item.event().version(), key, image);
                            publicId = saved.imagePublicId();
                            url = saved.coverImageUrl();
                            if (!validCloud(url)
                                    || storage.find(publicId)
                                            .filter(
                                                    asset ->
                                                            asset.secureUrl()
                                                                    .equals(saved.coverImageUrl()))
                                            .isEmpty()) {
                                throw new IOException("Saved image metadata cannot be verified");
                            }
                            download(url);
                            var persisted =
                                    service.migrationInventory().stream()
                                            .filter(row -> row.event().id().equals(saved.id()))
                                            .findFirst()
                                            .orElseThrow();
                            if (!url.equals(persisted.imageUrl())
                                    || !url.equals(persisted.originalCoverUrl())
                                    || !publicId.equals(persisted.publicId()))
                                throw new IOException("SQL image reference verification failed");
                            outcome = reason;
                            migrated++;
                        }
                    }
                } catch (Exception failure) {
                    outcome = "FAILED_" + failure.getClass().getSimpleName();
                    failures++;
                }
                output.write(
                        String.join(
                                        "\t",
                                        item.event().id().toString(),
                                        Long.toString(item.event().version()),
                                        cell(item.categorySlug()),
                                        safeUrl(item.imageUrl()),
                                        safeUrl(item.originalCoverUrl()),
                                        outcome,
                                        cell(publicId),
                                        safeUrl(url))
                                + "\n");
                output.flush();
                System.out.println(item.event().id() + " " + outcome);
            }
        }
        System.out.println(
                "Image report complete: migrated="
                        + migrated
                        + ", verified="
                        + verified
                        + ", failed="
                        + failures);
        if (failures > 0)
            throw new IllegalStateException("Some images require retry; inspect the report");
    }

    static HikariDataSource configuredSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
        config.setUsername(DatabaseConfig.getUser());
        config.setPassword(DatabaseConfig.getPassword());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(10_000);
        config.setPoolName("event-image-migration");
        return new HikariDataSource(config);
    }

    static byte[] download(String url) throws IOException, InterruptedException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException invalid) {
            throw new IOException("Image source URL is missing or invalid");
        }
        if (!"https".equals(uri.getScheme())
                || !SOURCE_HOSTS.contains(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getPort() != -1 && uri.getPort() != 443)
            throw new IOException("Image source is outside the approved HTTPS hosts");
        for (var address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress())
                throw new IOException("Image source resolves to a private address");
        }
        var response =
                HTTP.send(
                        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Image source is unavailable");
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var decoded = executor.submit(() -> ImageContentValidator.readAndValidate(body));
                try {
                    return decoded.get(20, TimeUnit.SECONDS);
                } catch (TimeoutException timeout) {
                    body.close();
                    decoded.cancel(true);
                    throw new IOException("Image download exceeded its time limit");
                } catch (ExecutionException failure) {
                    if (failure.getCause() instanceof IllegalArgumentException invalid)
                        throw invalid;
                    throw new IOException("Image source cannot be decoded");
                } catch (InterruptedException interrupted) {
                    body.close();
                    decoded.cancel(true);
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
    }

    private static boolean validCloud(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme())
                    && "res.cloudinary.com".equals(uri.getHost())
                    && uri.getUserInfo() == null
                    && uri.getPath().contains("/image/upload/");
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static String cell(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String safeUrl(String value) {
        if (value == null) return "";
        try {
            URI uri = URI.create(value);
            return cell(
                    new URI(
                                    uri.getScheme(),
                                    null,
                                    uri.getHost(),
                                    uri.getPort(),
                                    uri.getPath(),
                                    null,
                                    null)
                            .toString());
        } catch (Exception invalid) {
            return "INVALID_URL";
        }
    }
}
