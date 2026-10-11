# KHANH-02 signed check-in

Issue [#22](https://github.com/devonxjz/TicketCenter/issues/22), branch
`feat/khanh/khanh-02-qr-checkin`, PR base `dev`.

## Runtime contract

Set `TC_TICKET_SIGNING_SECRET` in the process environment/secret store. It must be
the **same UTF-8 string as LIEM-05's issuer**, at least 32 UTF-8 bytes. It is not
base64-decoded. Never put it in tracked properties, client JavaScript or a URL.
Missing configuration returns 503; there is no default signing key. The test
signer is restricted to test sources and is not an issuance endpoint.

QR = `base64url(payloadUtf8).base64url(HMAC-SHA256(payloadUtf8,secretUtf8))`, without
padding; payload has exactly two string fields: `id` (UUID) and `name`. Signature
comparison is constant-time and occurs before JSON parsing. Limits: 4,096 QR
characters, 2,048 decoded payload bytes, 200 name characters, 8,192 HTTP body
bytes/characters. The signed name is display-only; SQL ticket/order/event and
current active organization membership decide admission.

POST `/api/check-ins` accepts `{eventId,qrContent}` or `{eventId,ticketCode}` with
UUID `eventId`, UUID `Idempotency-Key`, `X-CSRF-Token`, JSON content type and a
valid session. Manual codes use the same stored procedure and authorization.
Responses use the project's `{data:{success,status,...}}` envelope, UUID strings
and UTC ISO timestamps. Success is 200; already used is 409 with original
`checkedInAt`; invalid signature, wrong event, unusable ticket and closed event
are 400. Authorization/session errors use the existing `{error:...}` envelope.

GET `/api/events/{UUID}/check-ins?pageSize=20` accepts 1–100 rows and checks
current membership. It returns recent logs in descending scan-time order,
without QR content, ticket codes, customer emails or financial columns.
`/organizer/checkin-scanner?eventId={UUID}` and `/check-ins/scanner?eventId={UUID}`
open UI-16. Camera and local-image decoding use the vendored html5-qrcode 2.3.8
library; the image is decoded locally. Camera requires a secure context and
browser permission. Manual input, green/red feedback, beep and recent history
are available. An uncertain network result blocks a new scan and exposes a
retry that preserves the original key and body.

## Canonical SQL schema

Apply `database/migrations/030_khanh02_signed_checkin.sql` after the canonical
schema (001–008) and existing check-in grants. The issue's example
`ISSUED → CHECKED_IN` maps to **`tc_tickets.ACTIVE → USED`** in SPEC/models;
`tc_ticket_check_ins` maps to **`tc_ticket_check_in_logs`** with `actor_id` and
`scanned_at`. Historical migrations and Ticket/Order entities are unchanged.

`sp_ticket_check_in` verifies active user/membership, locks event/request/ticket,
reads SQL UTC time after lock acquisition, checks published event and
`[start_time-60 minutes,end_time)`, then commits ticket version/used time, one
SUCCESS log, audit and durable idempotency receipt together. Its UUID inputs
replace the issue's illustrative numeric IDs. Its extended parameters add
request key/fingerprint and manual-code support; SQL owns scan time rather than
trusting a caller's clock. `sp_checkin_recent_logs` exposes the limited history.
Execute grants go to existing `tc_checkin`/`tc_manager` roles. No login is created.

Redis uses ticket NX/EX lock 5 seconds and idempotency TTL 3,600 seconds. SQL is
authoritative even if the lock is busy or Redis is unavailable. Durable receipts
survive restarts/outages and reject reuse with another body; authorization is
rechecked on every replay. Expired keys remain durable tombstones: do not purge
them blindly. After commit, ticket detail/QR variants and actor event-list
counters are invalidated. No raw QR is stored in SQL or Redis.

For this project's configured Azure target, `apply-project-contract.ps1` checks
the exact server/database, refuses unrecorded existing scan objects, executes
all batches and records SHA-256 in one transaction. A recorded different hash
is rejected. `verify-project-contract.ps1` is read-only and does not scan real
tickets. Do not change an installed migration; use a new migration for changes.

## Reproduce on Windows

Prerequisites: JDK 25, Maven, SQL Server on loopback:1433, sqlcmd, Redis on 16379,
Python 3 and Tomcat 11.0.25 (the local runner uses the existing portable
`target/khanh01-runtime/apache-tomcat-11.0.25`). Run from the repository root.

```powershell
# First run only: refuses an existing database/secret file.
./src/test/resources/checkin/khanh02/setup-local.ps1
. ./src/test/resources/checkin/khanh02/load-local.ps1
mvn -B '-Dtest=SignedQrDecoderTest,TicketCheckInServiceTest,TicketCheckInServletTest,CheckInConcurrencyTest,RedisCheckInScanCacheTest,TicketCheckInSqlServerTest,CheckInServletsTest' test package dependency:build-classpath '-Dmdep.outputFile=target/khanh02-classpath.txt'
node --test src/test/js/checkin-scanner.test.cjs
$cp='target/test-classes;target/classes;'+[IO.File]::ReadAllText((Resolve-Path target/khanh02-classpath.txt))
& "$env:JAVA_HOME/bin/java.exe" -cp $cp vn.ticketscenter.fulfillment.checkin.integration.ScanWebFixture
./src/test/resources/checkin/khanh02/start-local-web.ps1
# Wait until /ticketscenter/api/auth/csrf returns 200.
python src/test/resources/checkin/khanh02/web-acceptance.py
```

The disposable database is exactly `TicketCenter_Khanh02_Test`; mutation helpers
reject other names/non-loopback hosts. The local setup creates test-only owner
and restricted check-in accounts, protects credentials using Windows DPAPI in
ignored `docs/handoff`, and never uses Azure for fixtures. Generated identities,
signed QR text/PNG, deployment and credentials are ignored. Regenerate the web
fixture before repeating HTTP acceptance, because the first run consumes its
ticket. Default `mvn -B test` deliberately skips external integration tests;
module acceptance must set `TC_SCAN_INTEGRATION=true` and assert zero skips.

## CI and integration glue

`.github/workflows/khanh02-checkin.yml` provisions disposable SQL Server/Redis,
installs schema with `ScanCiSetup`, runs all feature tests without skips, runs
scanner JavaScript tests and boots the full packaged WAR on Tomcat. CI signing
and SQL/fixture passwords are random and masked. Only test reports, sanitized
HTTP outcomes and server logs are uploaded; signed QR and credentials are not.
Tomcat downloads are verified using the official SHA-512 digest.

The shared `web.xml` and `OrganizationApiRoutesFilter` resolve the existing
Event/Report `/api/organizations/*` registration collision without editing their
module implementations. Descriptor precedence keeps Event's wildcard; the
filter forwards only the report subresource after validating the session.
JSTL dependencies from the current dev branch allow the existing auth JSP to
compile on Tomcat 11; no duplicate dependency declarations are added. The shared
route prerequisites match KHANH-03's independent integration glue.
The check-in-owned filter forwards the scanner alias by servlet name to avoid
colliding with OrganizerServlet. Resource version query strings prevent stale
JavaScript when reproducible WAR timestamps are identical across builds.

No changes to LIEM-05's QR issuance, report/event implementations, other tasks,
shared business data or existing demo-data files are included. Human approval
and green required checks remain the merge gate; the author does not self-approve.
