package vn.ticketscenter.event.service;

import vn.ticketscenter.event.service.EventCatalogRuntime;
import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.event.cache.*;
import vn.ticketscenter.event.dao.*;
import vn.ticketscenter.event.service.*;
import vn.ticketscenter.storage.*;
import java.io.*;
import java.util.Optional;

public final class EventManagementRuntime {
    public record Services(EventManagementService management, EventCoverService cover) {}

    public static final String ATTRIBUTE = Services.class.getName();

    private EventManagementRuntime() {}

    public static Services resolve(ServletContext context) {
        return FeatureContext.cached(context, ATTRIBUTE, () -> create(context));
    }

    private static Services create(ServletContext context) {
        PersistenceRegistry registry = FeatureContext.registry(context, null, EventProblem::unavailable);
        var repository =
                new EventManagementRepository(new EventTransactions(registry.transactionManager()));
        EventIdempotencyStore idempotency = EventIdempotencyStore.configured();
        var orphans = new EventCoverOrphanJournal();
        var management =
                new EventManagementServiceImpl(
                        repository, idempotency, EventCatalogRuntime.cache(context), orphans);
        // Missing provider credentials must not disable draft/inventory/review endpoints.
        StorageService storage =
                new StorageService() {
                    public StoredAsset upload(InputStream content, String folder)
                            throws IOException {
                        return resolveStorage().upload(content, folder);
                    }

                    public StoredAsset ensure(InputStream content, String id)
                            throws IOException {
                        return resolveStorage().ensure(content, id);
                    }

                    public Optional<StoredAsset> find(String id) throws IOException {
                        return resolveStorage().find(id);
                    }

                    public void delete(String publicId) throws IOException {
                        resolveStorage().delete(publicId);
                    }

                    public String getUrl(String publicId) {
                        return resolveStorage().getUrl(publicId);
                    }
                };
        return new Services(
                management, new EventCoverServiceImpl(repository, idempotency, management, storage, orphans));
    }

    private static StorageService resolveStorage() {
        return StorageServiceImpl.configured();
    }
}
