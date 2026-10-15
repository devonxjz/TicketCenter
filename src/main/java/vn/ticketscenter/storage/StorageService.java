package vn.ticketscenter.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface StorageService {
    StoredAsset upload(InputStream content, String folder) throws IOException;

    StoredAsset ensure(InputStream content, String immutablePublicId) throws IOException;

    Optional<StoredAsset> find(String publicId) throws IOException;

    void delete(String publicId) throws IOException;

    String getUrl(String publicId);
}
