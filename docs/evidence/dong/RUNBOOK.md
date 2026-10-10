# Running the Dong acceptance checks

Run from repository root with Java25 and Maven. All generated binaries, logs, fake-account credentials and screenshots live under ignored `target/`. Do not upload these raw outputs or commit `application.properties` credentials.

## Local build (no external email/DB seed tests)

```powershell
mvn '-Dtest=*Test,!JpaConnectionTest,!DemoAccountSeederTest,!MigrationSuiteVerificationTest,!OtpRepositoryTest,!AuthServiceTest,!PasswordResetServiceTest,!OtpServiceTest,!EmailSenderTest' package
node --check src/main/webapp/assets/js/dong-events.js
node src/test/resources/event/dong-01-ui-check.cjs
```

Expected: 143 selected tests and WAR package. The excluded suites and `PasswordResetIT` are not evidence for this epic. Run their tests separately only in their owners' configured environment.

## Dedicated SQL/Redis/provider environment

1. `src/test/resources/event/setup-dong-local.ps1` provisions only `TicketCenter_Dong_Epic_Test_20261011` on `.\SQLEXPRESS` using Windows authentication. It refuses an existing database unless explicitly resumed with `-ResumeCreatedFixture`. It applies the baseline top-level migrations in dependency order, the SQL coordination proposal and three restricted execution users. Never point the integration suites at a shared database; their fixture creates/modifies test data and rollback triggers.
2. If SQL TCP is disabled, run `start-dong-sql-bridge.ps1` in a hidden background PowerShell process. It relays localhost:11433 to local SQLEXPRESS named pipe; no server restart/config changes. A normal test SQL endpoint can replace the bridge. Save the created PID and verify its command/path before stopping it.
3. Native integrated auth requires official Microsoft `mssql-jdbc_auth-12.8.1.x64.dll`. Obtain version12.8.1 from Maven Central, verify its artifact manifest and Microsoft Authenticode signature. This machine used an ASCII temporary directory `ticketcenter-dong-native-12-8-1` because the Windows Java launcher loses Unicode CLI paths. Never print credentials in arguments/logs.
4. Start a dedicated Redis on `127.0.0.1:16479`, e.g. `redis-server.exe --bind 127.0.0.1 --port 16479 --save "" --appendonly no`. Do not FLUSHALL on a shared Redis. Acceptance used the repository's portable Windows distribution8.10.2 (redis-windows release), ZIP SHA256 `6de5cc7f5adbf97b5928b13766383d4ad424626ef3d8b313ffff12d820ec6fc1`. Verify downloads before executing them; use `Start-Process -WindowStyle Hidden` for background helpers.
5. Cloudinary comes from existing `AppConfig` / secret environment. Do not print API keys/secrets. The real provider test creates a tiny fixture image in its own Event folder, confirms SQL/public URL, removes its own SQL Event, checks no remaining publicId reference and deletes only its created asset. It does not silently skip if credentials are missing.

```powershell
$env:TC_DONG_SQL_URL='jdbc:sqlserver://127.0.0.1:11433;databaseName=TicketCenter_Dong_Epic_Test_20261011;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5'
$env:TC_DONG_REDIS_HOST='127.0.0.1'
$env:TC_DONG_REDIS_PORT='16479'
$native=Join-Path ([IO.Path]::GetTempPath()) 'ticketcenter-dong-native-12-8-1'
mvn '-Dit.test=EventEpicSqlServerIT' "-DargLine=--enable-native-access=ALL-UNNAMED -Djava.library.path=$native" failsafe:integration-test failsafe:verify
$env:TC_DONG_CLOUDINARY_ACCEPTANCE='true'
mvn '-Dit.test=EventCloudinarySqlServerIT' "-DargLine=--enable-native-access=ALL-UNNAMED -Djava.library.path=$native" failsafe:integration-test failsafe:verify
```

Expected: 12 SQL/Redis tests +1 real Cloudinary test. `trustServerCertificate=true` applies to this loopback test endpoint only; production requires a trusted certificate. Recompile test sources if changed before direct failsafe execution.

## WAR and browser acceptance

Unpack verified official Apache Tomcat11.0.25 into `target/dong-runtime/apache-tomcat-11.0.25` (official ZIP SHA512 verified at download). Supply the project's existing JSTL API3.0.0 and Glassfish implementation3.0.1 in **that dedicated server's lib**. `start-dong-war.ps1` copies the built WAR, binds HTTP to localhost:18082, disables shutdown listener, uses the dedicated DB/Redis, limits pools, disables admin seed and routes mail to localhost:1. It validates prerequisites and refuses to launch over a saved live process. Java paths are relative to avoid Unicode launcher issues. Supply `-JavaDirectory` / `-NativeDirectory` if different.

```powershell
./src/test/resources/event/start-dong-war.ps1
$env:TC_DONG_HTTP_URL='http://127.0.0.1:18082/ticketscenter'
mvn '-Dit.test=EventHttpWarIT' "-DargLine=--enable-native-access=ALL-UNNAMED -Djava.library.path=$native" failsafe:integration-test failsafe:verify
```

Wait for the deployed app before invoking the HTTP test. Expected: 1 complete HTTP test. It exercises real auth cookies, CSRF, current roles, owner checks, JSP rendering, UTF8 JSON, idempotent creation, seats, actual multipart/Cloudinary, canonical publish/public read/image302 and auth-version revocation. It intentionally retains its fixture for browser checks and writes fake credentials only to ignored `target/dong-runtime/browser-fixture.json`.

Browser paths: `/events/manage`, `/events/manage/editor?organizationId=...&eventId=...`, `/events/review?eventId=...`, `/event-detail?eventId=...`. Existing organizer/admin page routes render the same owned JSPs. Test manager list/create/SEATED/STANDING/submit/PENDING locks; admin full schedule/layout/missing-cover error/rejection; manager rejected form; published description-only edit and public immediate refresh; empty/error/403 states; mobile layout and keyboard focus. The common dashboard is another owner's prototype and is not this epic's delivered UI.

To clean the HTTP fixture afterward, run `EventHttpFixtureCleanupIT` with the same dedicated URL and explicit `TC_DONG_CLOUDINARY_ACCEPTANCE=true`. It targets only known HTTP fixture title/assets in the dedicated DB, checks for transactions, clears those test Event references and confirms no SQL reference before provider deletion. Keep the dedicated DB for reproducibility; never drop another database. Stop only the verified helper PIDs created for this test session. Browser screenshots/evidence are disposable outputs.

## Shared rollout / orphan reconciliation

The SQL proposal is not deployed to the shared application. Owner adopts it into a new reviewed migration, supplies role synchronization and grants, then deploys the WAR. Verify the actual runtime logins as well as the tested execution contexts. Use the current `data` envelope/UUID/UTC/VND contracts; do not copy incompatible numeric-ID examples from issues.

Set `event.cover.orphan.directory` through existing configuration lookup to a persistent writable directory. Journal contains only Event/publicId metadata. For each record, verify **all** `tc_events.event_image_public_id` references after any ambiguous operation settles. Delete only an unreferenced asset, then mark/clear its record after successful provider acknowledgement. If SQL/provider/journal is unavailable, retain the asset and reconcile later. Cover replacement and draft deletion require this lifecycle review; do not delete an old/current image before SQL commit or trust a URL comparison without decoding SQL JSON.

Hook other owners' committed hold/release/refund/cancellation changes into the Event cache namespaces; catalog TTL alone is not an immediate cross-domain invalidation guarantee. Obtain independent review and real CI before merge/issue closure.
