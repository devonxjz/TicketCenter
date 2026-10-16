package vn.ticketscenter.config;

import java.nio.charset.StandardCharsets;

/** One signing key contract shared by ticket issuance and check-in. */
public final class TicketQrConfig {
    private TicketQrConfig() {}

    public static String signingSecret() {
        String secret = AppConfig.get("TC_TICKET_SIGNING_SECRET");
        if (secret == null || secret.isBlank()) secret = AppConfig.get("ticket.qr.secret");
        if (secret == null || secret.isBlank()) secret = AppConfig.get("app.secret.key");
        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.equals("<your_secret_key_here>")) {
            throw new IllegalStateException("Ticket signing secret needs at least 32 UTF-8 bytes");
        }
        return secret;
    }
}
