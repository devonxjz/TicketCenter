package vn.ticketscenter.platform.testsupport;

import java.util.function.Function;

final class ExternalTestConfiguration {
    static String resolve(String key, Function<String, String> parameters, Function<String, String> environment) {
        String explicit = parameters.apply(key);
        if (explicit != null && !explicit.isBlank()) return explicit;
        if (!key.startsWith("db.") && !key.startsWith("mail.smtp.")) return "";
        String value = environment.apply(key.replace('.', '_').toUpperCase(java.util.Locale.ROOT));
        return value == null ? "" : value;
    }
}
