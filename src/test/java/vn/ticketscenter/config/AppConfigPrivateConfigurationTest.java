package vn.ticketscenter.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigPrivateConfigurationTest {
    @TempDir Path directory;
    public static final class Probe {
        public static void main(String[] args) {
            if (!args[0].equals(AppConfig.get("db.host"))) throw new AssertionError("Private host not selected");
            if (!"test-private-sentinel".equals(AppConfig.get("db.password"))) throw new AssertionError("Private settings not selected");
            System.out.println("PRIVATE_CONFIG_OK");
        }
    }
    private Process run(Path config, boolean override, Path log) throws Exception {
        String javaExecutable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        var args=new java.util.ArrayList<>(java.util.List.of(javaExecutable,"-cp",System.getProperty("java.class.path"),"-Dtc.config.file="+config));
        if(override)args.add("-Ddb.host=overridden.invalid");
        args.add(Probe.class.getName());args.add(override?"overridden.invalid":"private.invalid");
        return new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    @Test void externalConfigurationLoadsAndSystemOverrideStillWins() throws Exception {
        Path config=directory.resolve("private.properties");
        Files.writeString(config,"db.host=private.invalid\ndb.password=test-private-sentinel\n");
        for(boolean override:new boolean[]{false,true}){
            Path log=directory.resolve("result-"+override+".log");var process=run(config,override,log);
            if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();fail("Configuration probe timed out");}
            assertEquals(0,process.exitValue(),Files.readString(log));assertTrue(Files.readString(log).contains("PRIVATE_CONFIG_OK"));
        }
    }
    @Test void missingPrivateFileFailsClosedWithoutExposingItsContents() throws Exception {
        Path log=directory.resolve("missing.log");var process=run(directory.resolve("missing.properties"),false,log);
        if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();fail("Configuration probe timed out");}
        assertNotEquals(0,process.exitValue());assertTrue(Files.readString(log).contains("Private application configuration could not be loaded"));
    }
}
