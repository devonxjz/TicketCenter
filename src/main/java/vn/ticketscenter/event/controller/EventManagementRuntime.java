package vn.ticketscenter.event.controller;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.event.cache.*;
import vn.ticketscenter.event.repository.*;
import vn.ticketscenter.event.service.*;
import vn.ticketscenter.storage.*;
import java.io.*;

public final class EventManagementRuntime {
    public record Services(EventManagementService management,EventCoverService cover) { }
    public static final String ATTRIBUTE=Services.class.getName();
    private EventManagementRuntime() { }
    public static Services resolve(ServletContext context) {
        synchronized(context) {
            Object existing=context.getAttribute(ATTRIBUTE);if(existing instanceof Services services) return services;
            Object value=context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if(!(value instanceof PersistenceRegistry registry)) throw EventProblem.unavailable();
            var repository=new EventManagementRepository(new EventTransactions(registry.transactionManager()));
            var idempotency=new RedisEventIdempotencyStore();
            var orphans=new EventCoverOrphanJournal();
            var management=new EventManagementService(repository,idempotency,EventCatalogRuntime.cache(context),orphans);
            // Missing provider credentials must not disable draft/inventory/review endpoints.
            StorageService storage=new StorageService() {
                public StoredAsset upload(InputStream content,String folder) throws IOException { return CloudinaryStorageService.fromEnvironment().upload(content,folder); }
                public StoredAsset ensure(InputStream content,String id) throws IOException { return CloudinaryStorageService.fromEnvironment().ensure(content,id); }
                public java.util.Optional<StoredAsset> find(String id) throws IOException { return CloudinaryStorageService.fromEnvironment().find(id); }
                public void delete(String publicId) throws IOException { CloudinaryStorageService.fromEnvironment().delete(publicId); }
                public String getUrl(String publicId) { return CloudinaryStorageService.fromEnvironment().getUrl(publicId); }
            };
            var services=new Services(management,new EventCoverService(repository,idempotency,management,storage,orphans));
            context.setAttribute(ATTRIBUTE,services);return services;
        }
    }
}
