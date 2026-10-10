# Verify Event image uploads

Use a dedicated local database named `TicketCenter_LIEM43_Test`. Do not run the default test suite against the shared Azure database. The existing `MigrationSuiteVerificationTest` writes to `TicketCenterticke`.

1. Create the dedicated test database with SQL Server Express. Apply canonical table, constraint, and index scripts with quoted identifiers enabled.
2. Apply the canonical functions and views after their dependencies exist. The current consolidated files have ordering dependencies between functions and views. Apply the view batches first, then rerun functions and views. Apply procedures, triggers, runtime roles, and migration 028.
3. Run the focused unit tests.

```powershell
mvn -B '-Dtest=EventServiceTest,EventMultipartParserTest,EventImagePlaceholdersTest,EventNavigationWebFlowTest,EventJdbcTest,SecurityContextTest' test
node --test src/test/js/event-images.test.cjs
```

4. Cache the official test container jars once, then run the local SQL and HTTP tests with the SQL Express TCP port.

```powershell
$taskArtifacts = @(
    'org.apache.tomcat.embed:tomcat-embed-core:11.0.25',
    'org.apache.tomcat.embed:tomcat-embed-jasper:11.0.25',
    'org.apache.tomcat.embed:tomcat-embed-el:11.0.25',
    'org.apache.tomcat:tomcat-annotations-api:11.0.25',
    'org.eclipse.jdt:ecj:3.46.0',
    'jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api:3.0.1',
    'org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1'
)
foreach ($taskArtifact in $taskArtifacts) {
    mvn -B dependency:get "-Dartifact=$taskArtifact"
    if ($LASTEXITCODE -ne 0) { throw 'Test container dependency download failed' }
}
```

```powershell
./src/test/resources/event-images/verify-local.ps1 -TcpPort 63657
```

The script creates a temporary login in the dedicated database, holds its password only in the process environment, and drops the principal after verification. Tests check the actual database identity before fixture writes. They use a fake provider for failure injection and real SQL transactions. Runtime-role checks impersonate `tc_manager`, `tc_buyer`, and `tc_worker`.

5. To verify the real provider and JSP form, run the same script with `-Serve`. The cached official Tomcat 11.0.25, JSTL 3.0.1, and ECJ 3.46.0 jars are required by the test utility. Open the printed `LOCAL_TEST_URL`.

```powershell
./src/test/resources/event-images/verify-local.ps1 -TcpPort 63657 -Serve
```

The server binds loopback and uses a fixture session. It exercises production Event servlets, SQL, storage, and JSPs. It does not prove the production login flow. Create a draft without a file, edit it without replacing its image, and replace the published fixture's cover. Check catalog and detail at desktop and 390-pixel mobile widths.

6. Create `target/event-image-web-test/stop` to stop the fixture. The utility removes only its allocated test assets and fixture rows, then drops the temporary principal.

```powershell
New-Item -ItemType File target/event-image-web-test/stop
```

7. Build the WAR after the focused tests pass.

```powershell
mvn -B -DskipTests package
```

The build skips tests only during packaging. It does not replace the earlier test runs.

## Audit and migrate a configured database

Install migration 028 through the DB owner's normal migration process. Keep Cloudinary and database credentials in the existing configuration or environment. Do not put them in commands, reports, or Git.

1. Build the application and dependency classpath.

```powershell
mvn -B -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/event-image-classpath.txt'
$taskClasspath = 'target/classes;' + (Get-Content target/event-image-classpath.txt -Raw).Trim()
```

2. Audit the exact target database before applying changes.

```powershell
java -cp $taskClasspath vn.ticketscenter.event.image.EventImageMigration audit TicketCenterticke target/event-images-before.tsv
```

3. Apply the audited migration and review each outcome.

```powershell
java -cp $taskClasspath vn.ticketscenter.event.image.EventImageMigration apply TicketCenterticke target/event-images-applied.tsv
```

4. Run apply again with a new report path. Existing valid assets must report `VERIFIED_EXISTING`. Retain both reports and source files.

```powershell
java -cp $taskClasspath vn.ticketscenter.event.image.EventImageMigration apply TicketCenterticke target/event-images-rerun.tsv
```

If a row reports failure, inspect its durable request and current Event version before retrying. Never manually delete a Cloudinary image after an uncertain SQL response. The request snapshot determines whether the save committed.
