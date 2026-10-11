# KHANH-01 — Local SQL, Redis and Tomcat acceptance

Historical 2026-10-09 evidence. See [2026-10-10 acceptance](KHANH-01-completion.md) for current Azure, full Maven and CI results. Earlier Azure/SMTP blockers below are resolved through scoped provisioning and local SMTP capture.

2026-10-09, Asia/Saigon. Branch: `feat/khanh/khanh-01-checkin-events`; issue #21; target PR `dev`.

Local backend/database/UI-15 acceptance PASS. Shared Azure deployment and merge approval are separate dependencies, not claimed complete. Implementation details and contract rationale: [KHANH-01.md](KHANH-01.md).

## Database delivered

SSMS server: `.` / `DESKTOP-LMRV6GA`, Windows Authentication. Dedicated database: **TicketCenter_Khanh01_Test**. TCP is enabled only on **127.0.0.1:1433**; original protocol backup is in `target/khanh01-original-sql-tcp.xml`. No Azure/shared database or other local database was modified.

The isolated baseline is generated from the locked JPA model with foreign keys. Test-only `src/test/resources/checkin/local-contract.sql` defines canonical `fn_event_is_check_in_open(uniqueidentifier, datetime2(7))`, PUBLISHED only, `[start-60 minutes,end)`. CHECK_IN receives identifier/status column grants, EXECUTE on the scalar UDF and VIEW DEFINITION for schema validation; financial reads and Event writes are denied. Local UTF-8 collation preserves Vietnamese in varchar Event title/venue. An optional normalized_email column supports the existing identity regression test.

Synthetic fixture: two organizations; manager/staff/outsider; open, early, cancelled, ended and foreign Event; Hold → Order → Item → three Tickets, one USED. Setup refuses existing database/nonempty seed. Acceptance mutations restore membership, schedule and User/Event status in finally blocks.

New owner/CHECK_IN SQL credentials are scoped to this database, not server administration. Generated passwords are Windows DPAPI SecureStrings in ignored `docs/handoff/khanh01-local-secrets.xml`; IDs are in `docs/handoff/khanh01-fixture.properties`. This keeps credentials safe from Maven clean. No passwords or runtime binaries are committed. Shared model/config/POM/database/OpenAPI/CI files remain unchanged; local SQL scripts are corresponding test resources.

## Verification

JDK 25.0.4.1, SQL Server 2022, Tomcat 11.0.25, Redis 8.10.2 portable Cygwin build, existing Maven/dependencies.

| Evidence | Result | Local log/report |
|---|---|---|
| Safe regression (`*Test,!EmailSenderTest`) | 96 tests, zero failures/errors/skips; WAR packaged | target/khanh01-regression.log |
| Email validation/fake sender methods | 3 tests pass | target/khanh01-safe-mail.log |
| SQL Server + Redis acceptance | 11 tests, zero failures/errors/skips | target/khanh01-local-integration.log, target/failsafe-reports |
| Actual Tomcat login/API/JSP | 13 HTTP checks pass | target/khanh01-web-acceptance.json |
| Redis stopped: SQL fallback via actual Tomcat | Same 13 HTTP checks pass; Redis restarted | target/khanh01-web-redis-outage.json |

Thus 99 safe regression tests and 11 real integration tests pass. The two existing methods that send email to another person (`EmailSenderTest.smtpEmailSenderShouldSendRealEmail`, `PasswordResetIT.testSendRealOtpEmailToUser`) are intentionally not executed. Plain full `mvn test` / external SMTP acceptance is not claimed.

The 42 KHANH-01 unit checks cover DB role scoping, 61/60 minute and end boundaries, cancellation, cache authorization/state/clock behavior, corruption/outage, counter TTL, route dispatch, session checks, SQL binding and UTC. The 11 real SQL/Redis checks additionally prove canonical UUID function metadata, staff/manager vs outsider, inactive User/revoked membership with warm cache, real 3/1 ticket counts, cancellation/schedule changes, real Redis TTL/expiry/SCAN invalidation, corrupt-cache DB fallback, financial read denial and write denial.

Real SQL found a seven-hour shift when reading datetimeoffset as LocalDateTime on Asia/Saigon. Repository now reads OffsetDateTime → Instant; datetime2 remains UTC. A regression verifies offsets. Rewriting a cached list now refreshes all counters together; otherwise repeated schedule changes could indefinitely renew unrelated old counters.

HTTP checks exercise proper CSRF/login sessions: anonymous list 401; staff/manager 200; open window 200; cancelled 400; foreign organization 403; missing 404; invalid UUID 400; actual UI-15 JSP compiles and responds 200. Mobile fixture evidence remains in the original document. A new authenticated browser screenshot is not claimed: in-app browser returned ERR_BLOCKED_BY_CLIENT for local HTTP. HTTP API/JSP acceptance passed independently.

## Run again

PowerShell 7, repository root; JDK 25/Maven installed:

```powershell
. ./src/test/resources/checkin/load-local.ps1
mvn -B -Psqlserver-it '-Dtest=CheckInEventServiceTest,RedisCheckInEventCacheTest,CheckInServletsTest,CheckInEventRepositoryTest' '-Dit.test=CheckInEventSqlServerIT,CheckInEventAcceptanceIT' verify
./src/test/resources/checkin/verify-local-web.ps1
```

Runtime is currently running at **http://127.0.0.1:18081/ticketscenter/check-ins**. Start only if stopped: `start-local-cache.ps1`, then `start-local-web.ps1`, in the same checkin resources directory. Tomcat/Redis are isolated hidden processes bound to loopback; no global service was installed.

Fixture usernames: `khanh01-manager`, `khanh01-staff`, `khanh01-outsider`. `load-local.ps1` privately loads the fixture password into the current process's `TC_CHECKIN_FIXTURE_PASSWORD`. Get `/api/auth/csrf` first to establish the existing login form/API's CSRF session.

Fresh setup: `download-local-runtime.ps1` downloads pinned, checksummed runtimes. Redis uses the development-only [redis-windows portable build](https://github.com/redis-windows/redis-windows/releases/tag/8.10.2), SHA256 `6de5cc7f5adbf97b5928b13766383d4ad424626ef3d8b313ffff12d820ec6fc1`; Tomcat checksum is from Apache. These runtimes are not a production deployment.

Compile tests and dependency classpath (`mvn -B -DskipTests test-compile dependency:build-classpath '-Dmdep.outputFile=target/khanh01-classpath.txt'`). Run `LocalCheckInFixture ddl` with target/test-classes, target/classes and that classpath; this only writes target/khanh01-model-schema.sql. Ensure local SQL TCP is available, run setup-local.ps1, load-local.ps1, then LocalCheckInFixture (seed) and LocalCheckInFixture tickets. Scripts refuse shared/Azure database writes and existing data overwrite. Maven clean removes the downloaded target runtimes; stop the local test processes first and redownload them when needed. Local DPAPI secrets/IDs survive in the ignored handoff directory.

## Remaining shared gates

Database owner must deploy the UUID canonical function and scoped grants to the shared database: checked-in legacy database/functions.sql still uses BIGINT/events, and issue ownership forbids changing it here. Event owner must wire `invalidateEvent` after committed cancellation/schedule changes; requests already check DB truth immediately. UI-16 / QR scanner belongs to KHANH-02. Obtain green GitHub Actions plus another person's approval before merge; do not merge this branch automatically.

The draft handoff commit uses `[skip ci]`: the current pipeline runs the two real external-email tests above, and task authorization does not authorize emailing that person. Workflows are unchanged. This deliberately leaves Actions unverified/pending, never green; [GitHub documents this behavior](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/skip-workflow-runs). The pipeline owner must isolate or explicitly arrange external SMTP testing and then trigger checks before merge.
