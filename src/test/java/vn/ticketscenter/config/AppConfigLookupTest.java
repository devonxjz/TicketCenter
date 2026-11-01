package vn.ticketscenter.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigLookupTest {
    static final String NULL_KEY = "<null-key>";
    static final String NO_VALUE = "<null>";
    static Map<String, String> resolved;

    public static final class Probe {
        public static void main(String[] args) {
            for (String key : args) {
                String value = AppConfig.get(NULL_KEY.equals(key) ? null : key);
                System.out.println(key + "=" + (value == null ? NO_VALUE : "[" + value + "]"));
            }
            System.out.println("DEFAULTS=" + AppConfig.get("zz.p13", "dflt") + "," + AppConfig.get("zz.p14", "dflt") + ","
                    + AppConfig.getInt("zz.p14", 7) + "," + AppConfig.getBoolean("zz.p14", true));
        }
    }

    @BeforeAll
    static void run(@TempDir Path directory) throws Exception {
        Map<String, String> sys = new LinkedHashMap<>();
        Map<String, String> env = new LinkedHashMap<>();
        StringBuilder file = new StringBuilder();

        sys.put("zz.p1", "sys1");
        env.put("zz.p1", "exact1");
        env.put("ZZ_P1", "upper1");
        env.put("ZZP1", "compact1");
        file.append("zz.p1=file1\n");

        env.put("zz.p2", "exact2");
        env.put("ZZ_P2", "upper2");
        env.put("ZZP2", "compact2");
        file.append("zz.p2=file2\n");

        env.put("ZZ_P3", "upper3");
        env.put("ZZP3", "compact3");
        file.append("zz.p3=file3\n");

        env.put("ZZP4", "compact4");
        file.append("zz.p4=file4\n");

        file.append("zz.p5=file5\n");

        sys.put("zz.p6", "   ");
        env.put("zz.p6", "exact6");
        sys.put("zz.p7", "");
        file.append("zz.p7=file7\n");
        env.put("zz.p8", "  ");
        env.put("ZZ_P8", "upper8");
        env.put("ZZ_P9", "");
        env.put("ZZP9", "compact9");
        env.put("ZZP10", " ");
        file.append("zz.p10=file10\n");
        file.append("zz.p11=   \n");

        sys.put("zz.p12", "  padded-sys  ");
        env.put("ZZ_P12B", "\tpadded-env\t");
        file.append("zz.p12c= padded-file \n");
        file.append("zz.p14=   \n");

        env.put("TC_SQL_PORT", "5432");
        file.append("db.host=direct-host\nTC_SQL_HOST=alias-host\n");
        file.append("db.name=db-name-value\n");
        file.append("db.user=\nTC_SQL_LOGIN=alias-login\n");
        file.append("db.password=direct-pass\n");
        file.append("db.encrypt=false\n");
        file.append("TC_SQL_TRUST_SERVER_CERT=true\ndb.trustServerCertificate=\n");
        file.append("db.loginTimeout=12\n");

        Path config = directory.resolve("pin.properties");
        Files.writeString(config, file.toString());

        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 11; i++) keys.add("zz.p" + i);
        keys.addAll(List.of("zz.p12", "ZZ_P12B", "zz.p12c", "zz.p13", "db.port", "TC_SQL_PORT",
                "db.host", "TC_SQL_HOST", "TC_SQL_DEV_DB", "TC_SQL_TEST_DB", "TC_SQL_BENCH_DB", "db.name", "db.user", "TC_SQL_LOGIN",
                "db.password", "TC_SQL_PASSWORD", "db.encrypt", "TC_SQL_ENCRYPT", "db.trustServerCertificate", "TC_SQL_TRUST_SERVER_CERT",
                "db.loginTimeout", "TC_SQL_CONNECT_TIMEOUT_SEC", "", "  ", NULL_KEY));

        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-cp", System.getProperty("java.class.path"), "-Dtc.config.file=" + config));
        sys.forEach((k, v) -> command.add("-D" + k + "=" + v));
        command.add(Probe.class.getName());
        command.addAll(keys);
        Path log = directory.resolve("probe.log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().clear();
        builder.environment().putAll(env);
        var process = builder.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Lookup probe timed out");
        }
        assertEquals(0, process.exitValue(), Files.readString(log));
        resolved = new HashMap<>();
        for (String line : Files.readAllLines(log)) {
            int eq = line.indexOf('=');
            if (eq >= 0) resolved.put(line.substring(0, eq), line.substring(eq + 1));
        }
    }

    private static String got(String key) {
        String raw = resolved.get(key);
        assertNotNull(raw, "no probe output for '" + key + "'");
        return NO_VALUE.equals(raw) ? null : raw.substring(1, raw.length() - 1);
    }

    @Test void sourcesAreConsultedInDocumentedOrder() {
        assertEquals("sys1", got("zz.p1"));
        assertEquals("exact2", got("zz.p2"));
        assertEquals("upper3", got("zz.p3"));
        assertEquals("compact4", got("zz.p4"));
        assertEquals("file5", got("zz.p5"));
    }

    @Test void blankValuesFallThroughToTheNextSource() {
        assertEquals("exact6", got("zz.p6"));
        assertEquals("file7", got("zz.p7"));
        assertEquals("upper8", got("zz.p8"));
        assertEquals("compact9", got("zz.p9"));
        assertEquals("file10", got("zz.p10"));
        assertNull(got("zz.p11"));
    }

    @Test void valuesAreTrimmedFromEverySource() {
        assertEquals("padded-sys", got("zz.p12"));
        assertEquals("padded-env", got("ZZ_P12B"));
        assertEquals("padded-file", got("zz.p12c"));
    }

    @Test void missingOrBlankKeyResolvesToNull() {
        assertNull(got("zz.p13"));
        assertNull(got(""));
        assertNull(got("  "));
        assertNull(got(NULL_KEY));
    }

    @Test void defaultsApplyOnlyWhenNothingNonBlankResolves() {
        assertEquals("dflt,dflt,7,true", resolved.get("DEFAULTS"));
    }

    @Test void directFileKeyBeatsItsAlias() {
        assertEquals("direct-host", got("db.host"));
        assertEquals("alias-host", got("TC_SQL_HOST"));
    }

    @Test void dbKeysFallBackToTcSqlAliasesInThePropertiesFile() {
        assertEquals("db-name-value", got("TC_SQL_DEV_DB"));
        assertEquals("db-name-value", got("TC_SQL_TEST_DB"));
        assertEquals("db-name-value", got("TC_SQL_BENCH_DB"));
        assertEquals("alias-login", got("db.user"));
        assertEquals("direct-pass", got("TC_SQL_PASSWORD"));
        assertEquals("false", got("TC_SQL_ENCRYPT"));
        assertEquals("true", got("db.trustServerCertificate"));
        assertEquals("12", got("TC_SQL_CONNECT_TIMEOUT_SEC"));
    }

    @Test void aliasFallbackReadsOnlyThePropertiesFile() {
        assertEquals("5432", got("TC_SQL_PORT"));
        assertNull(got("db.port"));
    }
}
