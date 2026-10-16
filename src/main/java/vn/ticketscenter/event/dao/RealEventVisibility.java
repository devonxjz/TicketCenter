package vn.ticketscenter.event.dao;

/** Public visibility is owned by the database views, never inferred from event text. */
public final class RealEventVisibility {
    private RealEventVisibility() { }

    public static String predicate(String alias) {
        if (!alias.matches("[A-Za-z][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid SQL alias");
        return "1=1";
    }
}
