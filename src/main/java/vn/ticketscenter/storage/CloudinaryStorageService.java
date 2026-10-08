package vn.ticketscenter.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import vn.ticketscenter.config.CloudinaryConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

public final class CloudinaryStorageService implements StorageService {
    private static final String IMAGE_RESOURCE_TYPE = "image";
    private final Cloudinary cloudinary;

    public CloudinaryStorageService(CloudinaryConfig config) {
        this.cloudinary = config.client();
    }

    public static CloudinaryStorageService fromEnvironment() {
        return new CloudinaryStorageService(CloudinaryConfig.fromEnvironment());
    }

    @Override
    public StoredAsset upload(InputStream content, String folder) throws IOException {
        byte[] bytes = ImageContentValidator.readAndValidate(content);
        String safeFolder = validateFolder(folder);
        String publicId = safeFolder + "/" + UUID.randomUUID();
        Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                "resource_type", IMAGE_RESOURCE_TYPE,
                "public_id", publicId,
                "overwrite", false,
                "unique_filename", false,
                "use_filename", false));
        Object secureUrl = result.get("secure_url");
        Object returnedPublicId = result.get("public_id");
        if (!(secureUrl instanceof String url) || !(returnedPublicId instanceof String id)) {
            throw new IOException("Cloudinary upload returned incomplete asset metadata");
        }
        return new StoredAsset(url, id);
    }

    @Override
    public void delete(String publicId) throws IOException {
        if (publicId == null || publicId.isBlank()) return;
        Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                "resource_type", IMAGE_RESOURCE_TYPE,
                "invalidate", true));
        Object status = result.get("result");
        if (status != null && !"ok".equals(status) && !"not found".equals(status)) {
            throw new IOException("Cloudinary delete failed");
        }
    }

    @Override
    public String getUrl(String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        return cloudinary.url().secure(true).resourceType(IMAGE_RESOURCE_TYPE).generate(publicId);
    }

    private String validateFolder(String folder) {
        if (folder == null || !folder.matches("[a-z0-9][a-z0-9/_-]{0,119}")) {
            throw new IllegalArgumentException("invalid storage folder");
        }
        return folder;
    }
}
