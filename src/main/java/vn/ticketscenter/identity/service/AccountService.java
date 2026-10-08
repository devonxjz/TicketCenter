package vn.ticketscenter.identity.service;

import java.util.Locale;

public final class AccountService {
    private AccountService() {}

    public static String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
