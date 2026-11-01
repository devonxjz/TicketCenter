package vn.ticketscenter.event.dto;

import java.util.Objects;
import java.util.UUID;

public record EventSave(
        UUID requestId,
        Kind kind,
        UUID targetId,
        long expectedVersion,
        EventFields fields,
        byte[] image) {
    public enum Kind {
        CREATE,
        EDIT,
        COVER,
        MIGRATE
    }

    public EventSave {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(targetId);
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Invalid expectedVersion");
        }
        if ((kind == Kind.CREATE || kind == Kind.EDIT) && fields == null) {
            throw new IllegalArgumentException("Event fields required");
        }
        if ((kind == Kind.COVER || kind == Kind.MIGRATE) && image == null) {
            throw new IllegalArgumentException("Image required");
        }
        image = image == null ? null : image.clone();
    }

    @Override
    public byte[] image() {
        return image == null ? null : image.clone();
    }
}
