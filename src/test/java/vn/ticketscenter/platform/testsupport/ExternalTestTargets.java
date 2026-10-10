package vn.ticketscenter.platform.testsupport;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class ExternalTestTargets {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "[::1]");

    static void requireDatabase(Function<String, String> config) {
        String name = config.apply("tc.tests.db.name");
        if (!name.matches("TC_Vuong_Test_[A-Za-z0-9_]+") || !name.equals(config.apply("db.name"))) {
            fail("DB opt-in requires matching dedicated tc.tests.db.name and db.name");
        }
        String url = config.apply("db.url");
        if (!url.startsWith("jdbc:sqlserver://")) fail("DB opt-in requires an explicit loopback JDBC URL");
        String[] parts = url.substring("jdbc:sqlserver://".length()).split(";", -1);
        String endpoint = parts[0];
        if (!endpoint.matches("(?:localhost|127\\.0\\.0\\.1|\\[::1\\])(?::[0-9]{1,5})?")) {
            fail("DB opt-in requires a loopback SQL Server target");
        }
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty() && i == parts.length - 1) continue;
            String[] pair = parts[i].split("=", 2);
            if (pair.length != 2 || options.putIfAbsent(pair[0], pair[1]) != null) {
                fail("DB opt-in JDBC URL contains invalid or duplicate options");
            }
        }
        if (!name.equals(options.get("databaseName")) || !"5".equals(options.get("loginTimeout"))) {
            fail("DB opt-in JDBC URL must select the dedicated database and loginTimeout=5");
        }
        for (var option : options.entrySet()) {
            boolean allowed = switch (option.getKey()) {
                case "databaseName" -> true;
                case "loginTimeout" -> "5".equals(option.getValue());
                case "encrypt", "trustServerCertificate", "integratedSecurity" -> Set.of("true", "false").contains(option.getValue());
                case "authenticationScheme" -> Set.of("JavaKerberos", "NativeAuthentication").contains(option.getValue());
                default -> false;
            };
            if (!allowed) fail("DB opt-in JDBC URL contains an unsupported option");
        }
    }

    static void requireSmtpSink(Function<String, String> config) {
        if (!"loopback-tls-no-relay".equals(config.apply("tc.tests.smtp.sink"))
                || !LOOPBACK.contains(config.apply("mail.smtp.host"))
                || !config.apply("mail.smtp.port").matches("[0-9]{1,5}")
                || config.apply("mail.smtp.user").isBlank() || config.apply("mail.smtp.password").isBlank()) {
            fail("SMTP opt-in requires explicit loopback TLS sink configuration with relay disabled");
        }
        int port = Integer.parseInt(config.apply("mail.smtp.port"));
        if (port < 1024 || port > 65535) fail("SMTP opt-in requires a dedicated unprivileged sink port");
    }

    private static void fail(String reason) { throw new IllegalStateException(reason); }
}
