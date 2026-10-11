package vn.ticketscenter.config.persistence;

import java.util.UUID;

/** Request-local actor, copied to each checked-out SQL connection, never accepted from an HTTP parameter. */
public final class DatabaseActorContext {
    private static final ThreadLocal<UUID> ACTOR = new ThreadLocal<>();
    private DatabaseActorContext() {}
    public static UUID current() { return ACTOR.get(); }
    public static void set(UUID actor) { if (actor == null) ACTOR.remove(); else ACTOR.set(actor); }
    public static void clear() { ACTOR.remove(); }
}
