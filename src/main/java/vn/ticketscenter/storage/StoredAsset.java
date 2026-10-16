package vn.ticketscenter.storage;

public record StoredAsset(String secureUrl, String publicId) {
    public StoredAsset {
        if (secureUrl == null || secureUrl.isBlank())
            throw new IllegalArgumentException("secure URL is required");
        if (publicId == null || publicId.isBlank())
            throw new IllegalArgumentException("public ID is required");
    }
}
