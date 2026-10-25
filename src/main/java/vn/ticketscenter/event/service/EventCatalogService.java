package vn.ticketscenter.event.service;

import vn.ticketscenter.event.dto.EventFilter;
import java.util.UUID;

public interface EventCatalogService {
    final class NotFound extends vn.ticketscenter.config.error.AppException {
        public NotFound() {
            super(404, "NOT_FOUND", "Not found");
        }
    }

    String events(EventFilter filter);

    String event(UUID id);

    String zones(UUID id);

    String seats(UUID id);

    String categories();

    String image(UUID id);

    void warmAsync();

    int warm();
}
