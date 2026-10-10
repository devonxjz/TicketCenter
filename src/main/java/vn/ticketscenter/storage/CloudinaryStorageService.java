package vn.ticketscenter.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.utils.ObjectUtils;

import vn.ticketscenter.config.CloudinaryConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CloudinaryStorageService implements StorageService {
    private static final int TIMEOUT_MS = 20_000;
    private final Cloudinary cloudinary;
    private final String cloudName;

    public CloudinaryStorageService(CloudinaryConfig config) {
        cloudinary = config.client();
        cloudName = config.cloudName();
    }

    public static CloudinaryStorageService fromEnvironment() {
        return new CloudinaryStorageService(CloudinaryConfig.fromEnvironment());
    }

    @Override
    public StoredAsset upload(InputStream content, String folder) throws IOException {
        if (folder == null || !folder.matches("[a-z0-9][a-z0-9/_-]{0,119}")) {
            throw new IllegalArgumentException("invalid storage folder");
        }
        return ensure(content, folder + "/" + UUID.randomUUID());
    }

    @Override
    public StoredAsset ensure(InputStream content, String immutablePublicId) throws IOException {
        validateId(immutablePublicId);
        byte[] bytes = ImageContentValidator.readAndValidate(content);
        String digest = digest(bytes);
        Optional<Map<?, ?>> existing = resource(immutablePublicId);
        if (existing.isPresent()) return verified(existing.get(), immutablePublicId, digest);
        try {
            Map<?, ?> uploaded =
                    cloudinary
                            .uploader()
                            .upload(
                                    bytes,
                                    ObjectUtils.asMap(
                                            "resource_type",
                                            "image",
                                            "public_id",
                                            immutablePublicId,
                                            "overwrite",
                                            false,
                                            "unique_filename",
                                            false,
                                            "use_filename",
                                            false,
                                            "context",
                                            "tc_sha256=" + digest,
                                            "timeout",
                                            TIMEOUT_MS));
            if (Boolean.TRUE.equals(uploaded.get("existing"))) {
                return verified(
                        resource(immutablePublicId)
                                .orElseThrow(
                                        () ->
                                                new IOException(
                                                        "Existing Cloudinary image could not be"
                                                            + " verified")),
                        immutablePublicId,
                        digest);
            }
            return asset(uploaded, immutablePublicId);
        } catch (IOException failure) {
            // A timeout can happen after the provider has accepted the upload.
            Optional<Map<?, ?>> recovered = resource(immutablePublicId);
            if (recovered.isPresent()) return verified(recovered.get(), immutablePublicId, digest);
            throw new IOException("Cloudinary image upload failed", failure);
        } catch (RuntimeException failure) {
            throw new IOException("Cloudinary image upload failed", failure);
        }
    }

    @Override
    public Optional<StoredAsset> find(String publicId) throws IOException {
        validateId(publicId);
        Optional<Map<?, ?>> result = resource(publicId);
        return result.isEmpty() ? Optional.empty() : Optional.of(asset(result.get(), publicId));
    }

    @Override
    public void delete(String publicId) throws IOException {
        validateId(publicId);
        try {
            Map<?, ?> result =
                    cloudinary
                            .uploader()
                            .destroy(
                                    publicId,
                                    ObjectUtils.asMap(
                                            "resource_type",
                                            "image",
                                            "invalidate",
                                            true,
                                            "timeout",
                                            TIMEOUT_MS));
            if (!"ok".equals(result.get("result")) && !"not found".equals(result.get("result"))) {
                throw new IOException("Cloudinary delete failed");
            }
        } catch (RuntimeException failure) {
            throw new IOException("Cloudinary delete failed", failure);
        }
    }

    @Override
    public String getUrl(String publicId) {
        validateId(publicId);
        return cloudinary.url().secure(true).resourceType("image").generate(publicId);
    }

    private Optional<Map<?, ?>> resource(String publicId) throws IOException {
        try {
            return Optional.of(
                    cloudinary
                            .api()
                            .resource(
                                    publicId,
                                    ObjectUtils.asMap(
                                            "resource_type",
                                            "image",
                                            "context",
                                            true,
                                            "timeout",
                                            TIMEOUT_MS)));
        } catch (NotFound absent) {
            return Optional.empty();
        } catch (Exception failure) {
            throw new IOException("Cloudinary image lookup failed", failure);
        }
    }

    private StoredAsset verified(Map<?, ?> result, String publicId, String digest)
            throws IOException {
        Object context = result.get("context");
        Object custom = context instanceof Map<?, ?> values ? values.get("custom") : null;
        Object storedDigest = custom instanceof Map<?, ?> values ? values.get("tc_sha256") : null;
        if (!digest.equals(storedDigest))
            throw new IOException("Cloudinary image ID belongs to different content");
        return asset(result, publicId);
    }

    private StoredAsset asset(Map<?, ?> result, String expectedId) throws IOException {
        if (!(result.get("secure_url") instanceof String url)
                || !expectedId.equals(result.get("public_id"))) {
            throw new IOException("Cloudinary returned incomplete image metadata");
        }
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme())
                    || !"res.cloudinary.com".equals(uri.getHost())
                    || uri.getUserInfo() != null
                    || !uri.getPath().startsWith("/" + cloudName + "/image/upload/")) {
                throw new IllegalArgumentException();
            }
            return new StoredAsset(url, expectedId);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Cloudinary returned an unsafe image URL");
        }
    }

    private static void validateId(String publicId) {
        if (publicId == null
                || !publicId.matches("[a-zA-Z0-9][a-zA-Z0-9/_-]{0,254}")
                || publicId.contains("//")
                || publicId.endsWith("/")) {
            throw new IllegalArgumentException("invalid image public ID");
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
