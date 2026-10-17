package vn.ticketscenter.storage;

import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.dto.EventCommand;
import vn.ticketscenter.event.service.EventCoverService;
import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;

/** Durable metadata for cleanup only after checking that no Event references the asset. No image bytes/secrets. */
public final class EventCoverOrphanJournal implements EventCoverService.Orphans {
    private static final Logger LOGGER = Logger.getLogger(EventCoverOrphanJournal.class.getName());
    private final Path directory;

    public EventCoverOrphanJournal() {
        this(Path.of(AppConfig.get("event.cover.orphan.directory", "target/event-cover-orphans")));
    }

    public EventCoverOrphanJournal(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public void record(UUID event, StoredAsset asset) {
        try {
            Files.createDirectories(directory);
            String data =
                    "{\"eventId\":"
                            + HttpResponses.jsonString(event.toString())
                            + ",\"publicId\":"
                            + HttpResponses.jsonString(asset.publicId())
                            + ",\"status\":\"VERIFY_DB_REFERENCE_BEFORE_DELETE\"}";
            Path file = directory.resolve(EventCommand.sha256(asset.publicId()) + ".json");
            Files.writeString(
                    file, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.warning("Event cover metadata recorded for safe orphan reconciliation.");
        } catch (Exception e) {
            LOGGER.severe(
                    "Event cover orphan journal is unavailable; asset requires manual reconciliation.");
            throw new IllegalStateException("Orphan journal unavailable");
        }
    }
}
