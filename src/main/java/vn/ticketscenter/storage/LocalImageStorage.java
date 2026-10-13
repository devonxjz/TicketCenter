package vn.ticketscenter.storage;

import vn.ticketscenter.config.AppConfig;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Persistent image storage for installations without a configured cloud provider. */
public final class LocalImageStorage implements StorageService {
    public static final String ROUTE = "/assets/event-images/";
    private final Path directory;
    private final String context;

    public LocalImageStorage(Path directory, String context) {
        this.directory = directory.toAbsolutePath().normalize();
        if (context == null || !context.matches("(?:/[A-Za-z0-9_-]+)?"))
            throw new IllegalArgumentException("invalid application context");
        this.context = context;
    }

    public static LocalImageStorage fromConfiguration() {
        String configured = AppConfig.get(AppConfig.Keys.EVENT_IMAGE_DIRECTORY);
        String base = AppConfig.get(AppConfig.Keys.APP_BASE_URL);
        String context = base == null || base.isBlank() ? "/ticketscenter" : URI.create(base).getPath();
        if (context.endsWith("/")) context = context.substring(0, context.length() - 1);
        return new LocalImageStorage(Path.of(configured == null || configured.isBlank() ? "data/event-images" : configured), context);
    }

    public static StorageService configured() {
        String cloud = AppConfig.get(AppConfig.Keys.CLOUDINARY_CLOUD_NAME);
        return cloud == null || cloud.isBlank() ? fromConfiguration() : CloudinaryStorageService.fromEnvironment();
    }

    public static boolean isLocalUrl(String url) {
        return url != null && url.matches("(?:/[A-Za-z0-9_-]+)?" + ROUTE + "[a-f0-9]{64}\\.img");
    }

    public Optional<Path> file(String filename) {
        if (filename == null || !filename.matches("[a-f0-9]{64}\\.img")) return Optional.empty();
        Path file = directory.resolve(filename);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Optional.of(file) : Optional.empty();
    }

    @Override public StoredAsset upload(InputStream content, String folder) throws IOException {
        return ensure(content, folder + "/" + UUID.randomUUID());
    }

    @Override public StoredAsset ensure(InputStream content, String publicId) throws IOException {
        String key = key(publicId);
        byte[] bytes = ImageContentValidator.readAndValidate(content);
        Optional<StoredAsset> existing = find(publicId);
        if (existing.isPresent()) {
            Path file = directory.resolve(existing.get().secureUrl().substring(existing.get().secureUrl().lastIndexOf('/') + 1));
            if (!Arrays.equals(bytes, Files.readAllBytes(file))) throw new IOException("Image ID belongs to different content");
            return existing.get();
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(key + ".img");
        Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, target); }
            catch (FileAlreadyExistsException race) {
                if (!Arrays.equals(bytes, Files.readAllBytes(target))) throw new IOException("Image ID belongs to different content", race);
            }
        } finally { Files.deleteIfExists(temporary); }
        return new StoredAsset(context + ROUTE + target.getFileName(), publicId);
    }

    @Override public Optional<StoredAsset> find(String publicId) {
        String key = key(publicId);
        String filename = key + ".img";
        return file(filename).map(file -> new StoredAsset(context + ROUTE + filename, publicId));
    }

    @Override public void delete(String publicId) throws IOException {
        Files.deleteIfExists(directory.resolve(key(publicId) + ".img"));
    }

    @Override public String getUrl(String publicId) {
        return find(publicId).orElseThrow(() -> new IllegalArgumentException("image was not found")).secureUrl();
    }

    private static String key(String publicId) {
        if (publicId == null || !publicId.matches("[a-zA-Z0-9][a-zA-Z0-9/_-]{0,254}") || publicId.contains("//") || publicId.endsWith("/"))
            throw new IllegalArgumentException("invalid image public ID");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicId.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
