package vn.ticketscenter.storage;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {
    StoredAsset upload(InputStream content, String folder) throws IOException;

    void delete(String publicId) throws IOException;

    String getUrl(String publicId);
}
