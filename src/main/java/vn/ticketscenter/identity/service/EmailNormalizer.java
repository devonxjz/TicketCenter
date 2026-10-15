package vn.ticketscenter.identity.service;

import java.util.Locale;

/** The one definition of a normalised email: trimmed and lower-cased, never null. */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
