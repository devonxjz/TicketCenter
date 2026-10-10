# Platform test isolation — VUONG Task 3

Default `mvn -B test` uses the existing JUnit 5 auto-discovered `ExternalTestCondition` on the test classpath. No Maven dependency/profile or production code changes are required. `junit-platform.properties` enables service discovery; do not disable extension autodetection. Conditions execute before `@BeforeAll` and test bodies. New external suites must use `@Tag("db")` or `@Tag("smtp")` at class level whenever setup can perform IO. Methods with isolated external work can carry the tag individually. Every class ending in `IT` also requires DB opt-in.

The registry covers existing untagged DB suites (DemoAccountSeeder, OtpRepository, OtpService, PasswordResetService), the mixed AuthService/JpaConnection external methods, real SMTP in EmailSender, and combined DB/SMTP PasswordResetIT. Pure methods remain enabled. Default run skips external cases with an explicit reason, rather than attempting a connection and treating connection failure as an assumption.

## Safe local run

Select JDK 25, then run `./src/test/scripts/platform-safe-tests.ps1 -JavaHome '<JDK25 directory>'`. This launcher explicitly turns DB/SMTP/Redis flags off, runs a clean Java suite, reports actual totals and named skipped cases, then runs Node initializer/report-summary/finance UI behavior tests. Bare `mvn test` is safe when opt-in flags are absent. The launcher also prevents stale report files from inflating totals. Keep private fixture configuration and redirected logs outside `target`: Maven clean deletes build outputs, and Windows cannot delete an actively written log.

## Explicit DB opt-in

DB tests are allowed only with explicit opt-in and a dedicated runtime target:

- JVM flags `-Dtc.tests.db=true` and `-Dtc.tests.db.name=TC_Vuong_Test_<run>`.
- Explicit `DB_NAME` environment value (or `db.name` JVM parameter) identical to the selected name.
- Explicit `DB_URL` environment value (or `db.url` JVM parameter): `jdbc:sqlserver://127.0.0.1:<port>;databaseName=TC_Vuong_Test_<run>;loginTimeout=5;encrypt=false;trustServerCertificate=true` (localhost and IPv6 loopback are also accepted).
- `DB_USER`/`DB_PASSWORD` environment values supply private fixture authentication. Bind `DB_ADMIN_USER`/`DB_ADMIN_PASSWORD`, and other required principal overrides when the selected owner suite uses them, to the same private fixture login. JVM authentication parameters are supported but should be avoided to keep secrets out of argv and report properties. Integrated authentication is supported when configured by the fixture owner. Never put credentials in the URL or logs.

Only a small JDBC option allowlist is accepted; remote targets, duplicate databaseName, failover/redirection options and missing timeouts fail before setup. The condition then performs a bounded read-only preflight requiring `dbo.tc_vuong_test_fixture` with `fixture_key='vuong-finance-v1'`. SQL exceptions are replaced with a fixed message without exception causes. The table is supplied by `database/tests/vuong/001_uuid_test_schema.sql`; production migration 027 does not create the owner schema. The preflight establishes target identity, not permission to reuse someone else's test database. Use a newly created dedicated fixture.

Missing target/marker/configuration after opt-in fails the test container, never becomes a successful skip. The CI DB job additionally fails if any selected test is skipped. It requires finance, read-side and audit JDBC test sources to exist before execution rather than silently selecting absent DayXXIT suites. It applies fixture 001 and migration 027 in a disposable SQL Server service, and never enables SMTP. Older owners' JPA/seed suites are separately pending their compatible base schema; the finance fixture is not full owner acceptance. Fixture compatibility, permissions/concurrency and real JDBC assertions remain acceptance gates until that job is actually executed successfully.

## Explicit standalone Redis acceptance

`FinanceRedisRuntimeTest` is disabled unless `-Dtc.tests.redis=true`. Use a fresh dedicated Redis process, loopback127.0.0.1, unprivileged port, no persistence; set `tc:fixture:vuong-finance-v1` to `vuong-finance-v1`. Bind `TC_REDIS_TEST_PORT` and explicit `-Dredis.host=127.0.0.1 -Dredis.port=<same port> -Dredis.password= -Dredis.timeout=250 -Dfinance.redis.enabled=true`, then select `-Dtest=FinanceRedisRuntimeTest`. It checks the marker before writes, verifies actual cache keys/TTLs/invalidation, shuts down that standalone server and verifies outage fallback. Never point it at a shared Redis. The manual CI Redis job supplies a disposable service and requires this case to execute without skips.

## SMTP boundary

SMTP tests default to disabled. Explicit opt-in additionally requires JVM flags `tc.tests.smtp=true`, `tc.tests.smtp.sink=loopback-tls-no-relay`, and explicit `mail.smtp.host`, `mail.smtp.port`, `mail.smtp.user`, `mail.smtp.password` (or normalized `MAIL_SMTP_*` environment values). Host must be loopback; port must be 1024–65535. The sink owner must provide a TLS/STARTTLS-capable capture sink with relay disabled. Existing owner tests use their existing recipient strings, so never point these tests at a relay. PasswordResetIT also requires DB opt-in and marker preflight. No CI job enables SMTP and no SMTP test was run for this change. A loopback marker is a declaration of controlled sink configuration, not proof of a sink's relay policy.

## CI actions and reporting

Push and PR checks run safe Java/Node tests, quality/security checks and local WAR/container builds. Feature pushes cannot log into/publish a registry, publish `latest`, start a full WAR, run shared workers or send real mail. Image login/publish require an explicit workflow_dispatch input on main/dev. DB fixture tests and isolated HTTP smoke also require manual dispatch. The smoke uses metadata-complete selective servlet/filter deployment from `swagger-smoke.ps1`; no persistence listener, seed or worker starts. It is not full application E2E acceptance.

Production workflow is an explicitly dispatched protected rollout simulation only; automatic tag/release publishing was removed. Actual hosting deployment and release publication require a separate future authorized implementation.

`test-report-summary.cjs` reads only XML counts and Java case names for its output; it never echoes report properties, captured output or exceptions. A missing report, zero executed tests, failures/errors or opt-in skips produce a nonzero exit. Summaries report observed step outcomes instead of unconditional PASS claims. Security scanner warning-only configuration remains warning-only and is not a clean security acceptance claim.

## Verification scope

Harmless nested JUnit engine probes test disabled setup, mixed pure execution and opt-in configuration failure without DB or SMTP. Target policy tests cover dedicated names, loopback URLs, duplicate/failover rejection and actual mail configuration property names. Node subprocess probes cover missing/empty/skipped/failing report results, correct skipped method names and output redaction. Full DB/SMTP/owner catalog E2E and GitHub CI execution remain separate gates.
