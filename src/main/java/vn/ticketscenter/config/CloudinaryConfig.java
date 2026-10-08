package vn.ticketscenter.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

public record CloudinaryConfig(String cloudName, String apiKey, String apiSecret) {
    public CloudinaryConfig {
        requireValue(cloudName, "CLOUDINARY_CLOUD_NAME");
        requireValue(apiKey, "CLOUDINARY_API_KEY");
        requireValue(apiSecret, "CLOUDINARY_API_SECRET");
    }

    public static CloudinaryConfig fromEnvironment() {
        return new CloudinaryConfig(
                AppConfig.get(AppConfig.Keys.CLOUDINARY_CLOUD_NAME),
                AppConfig.get(AppConfig.Keys.CLOUDINARY_API_KEY),
                AppConfig.get(AppConfig.Keys.CLOUDINARY_API_SECRET));
    }

    public Cloudinary client() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is not configured");
    }
}
