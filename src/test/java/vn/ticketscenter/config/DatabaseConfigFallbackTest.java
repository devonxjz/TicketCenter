package vn.ticketscenter.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigFallbackTest {
    @TempDir Path directory;

    public static final class Probe {
        public static void main(String[] args) {
            System.out.println("host=" + DatabaseConfig.getHost());
            System.out.println("port=" + DatabaseConfig.getPort());
            System.out.println("name=" + DatabaseConfig.getDatabaseName());
            System.out.println("user=" + DatabaseConfig.getUser());
            System.out.println("password=" + DatabaseConfig.getPassword());
            System.out.println("encrypt=" + DatabaseConfig.isEncrypt());
            System.out.println("trust=" + DatabaseConfig.isTrustServerCertificate());
            System.out.println("timeout=" + DatabaseConfig.getLoginTimeout());
            System.out.println("buyerUser=" + DatabaseConfig.getPrincipalUser(DatabasePrincipal.BUYER));
            System.out.println("buyerPassword=" + DatabaseConfig.getPrincipalPassword(DatabasePrincipal.BUYER));
            System.out.println("url=" + DatabaseConfig.getJdbcUrl());
        }
    }

    private Map<String, String> resolve(Map<String, String> sys, Map<String, String> env) throws Exception {
        Path config = Files.writeString(directory.resolve("empty.properties"), "");
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-cp", System.getProperty("java.class.path"), "-Dtc.config.file=" + config));
        sys.forEach((k, v) -> command.add("-D" + k + "=" + v));
        command.add(Probe.class.getName());
        Path log = directory.resolve("probe.log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().clear();
        builder.environment().putAll(env);
        var process = builder.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Database config probe timed out");
        }
        assertEquals(0, process.exitValue(), Files.readString(log));
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(log)) {
            int eq = line.indexOf('=');
            if (eq >= 0) values.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return values;
    }

    @Test void defaultsApplyWhenNothingIsConfigured() throws Exception {
        Map<String, String> v = resolve(Map.of(), Map.of());
        assertEquals("localhost", v.get("host"));
        assertEquals("1433", v.get("port"));
        assertEquals("TicketsCenter_Dev", v.get("name"));
        assertEquals("sa", v.get("user"));
        assertEquals("", v.get("password"));
        assertEquals("true", v.get("encrypt"));
        assertEquals("false", v.get("trust"));
        assertEquals("30", v.get("timeout"));
        assertEquals("sa", v.get("buyerUser"));
        assertEquals("", v.get("buyerPassword"));
        assertEquals("jdbc:sqlserver://localhost:1433;databaseName=TicketsCenter_Dev;encrypt=true;trustServerCertificate=false;loginTimeout=30;", v.get("url"));
    }

    @Test void tcSqlAliasesApplyWhenDirectKeysAreMissing() throws Exception {
        Map<String, String> v = resolve(Map.of(), Map.of(
                "TC_SQL_HOST", "alias.host", "TC_SQL_PORT", "2433", "TC_SQL_TEST_DB", "aliasTest", "TC_SQL_DEV_DB", "aliasDev",
                "TC_SQL_LOGIN", "aliasLogin", "TC_SQL_PASSWORD", "aliasPw", "TC_SQL_ENCRYPT", "false",
                "TC_SQL_TRUST_SERVER_CERT", "true", "TC_SQL_CONNECT_TIMEOUT_SEC", "7"));
        assertEquals("alias.host", v.get("host"));
        assertEquals("2433", v.get("port"));
        assertEquals("aliasTest", v.get("name"));
        assertEquals("aliasLogin", v.get("user"));
        assertEquals("aliasPw", v.get("password"));
        assertEquals("false", v.get("encrypt"));
        assertEquals("true", v.get("trust"));
        assertEquals("7", v.get("timeout"));
        assertEquals("aliasLogin", v.get("buyerUser"));
        assertEquals("aliasPw", v.get("buyerPassword"));
    }

    @Test void devDatabaseAliasApplies() throws Exception {
        assertEquals("aliasDev", resolve(Map.of(), Map.of("TC_SQL_DEV_DB", "aliasDev")).get("name"));
    }

    @Test void directKeysBeatAliases() throws Exception {
        Map<String, String> sys = new HashMap<>(Map.of(
                "db.host", "direct.host", "db.port", "3433", "db.name", "directDb", "db.user", "directUser",
                "db.password", "directPw", "db.encrypt", "false", "db.trustServerCertificate", "true", "db.loginTimeout", "9"));
        sys.put("db.buyer.user", "buyerUser");
        sys.put("db.buyer.password", "buyerPw");
        Map<String, String> v = resolve(sys, Map.of(
                "TC_SQL_HOST", "alias.host", "TC_SQL_PORT", "2433", "TC_SQL_TEST_DB", "aliasTest", "TC_SQL_LOGIN", "aliasLogin",
                "TC_SQL_PASSWORD", "aliasPw", "TC_SQL_ENCRYPT", "true", "TC_SQL_TRUST_SERVER_CERT", "false", "TC_SQL_CONNECT_TIMEOUT_SEC", "7"));
        assertEquals("direct.host", v.get("host"));
        assertEquals("3433", v.get("port"));
        assertEquals("directDb", v.get("name"));
        assertEquals("directUser", v.get("user"));
        assertEquals("directPw", v.get("password"));
        assertEquals("false", v.get("encrypt"));
        assertEquals("true", v.get("trust"));
        assertEquals("9", v.get("timeout"));
        assertEquals("buyerUser", v.get("buyerUser"));
        assertEquals("buyerPw", v.get("buyerPassword"));
    }

    @Test void nonPositivePortAndTimeoutFallBackAndBooleansParseStrictly() throws Exception {
        Map<String, String> v = resolve(
                Map.of("db.port", "0", "db.loginTimeout", "-5", "db.encrypt", "yes", "db.trustServerCertificate", " TRUE "),
                Map.of("TC_SQL_PORT", "2433", "TC_SQL_CONNECT_TIMEOUT_SEC", "7", "TC_SQL_ENCRYPT", "true", "TC_SQL_TRUST_SERVER_CERT", "false"));
        assertEquals("2433", v.get("port"));
        assertEquals("7", v.get("timeout"));
        assertEquals("false", v.get("encrypt"));
        assertEquals("true", v.get("trust"));
    }
}
