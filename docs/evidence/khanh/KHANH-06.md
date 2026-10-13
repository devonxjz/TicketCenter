# KHANH-06 — Event cancellation and resumable batch refunds

Issue: [#26](https://github.com/devonxjz/TicketCenter/issues/26).
Branch: `feat/khanh/khanh-06-event-cancellation`, from refreshed `dev` at
`67fec06` (already includes KHANH-01–05). The live issue was read on
2026-10-13 and its body exactly matches the locally saved assignment.
SPEC, business rules and historical migration files are unchanged.

## Delivered behavior

- `POST /api/admin/events/{eventId}/cancel`: current ACTIVE ADMIN/authVersion,
  CSRF, JSON, UUID Idempotency-Key and a 10–500-character reason. SQL locks Event
  before the actor and rechecks the start boundary after waiting. A saved intent
  replays after a lost response; different reasons with the same key, or another
  cancellation key for an already cancelled event, return 409.
- `GET /api/admin/events/{eventId}/cancellation-progress`: authenticated current
  ADMIN, typed envelope and UUID/UTC values. Redis retains a validated progress
  snapshot for ten seconds without renewing TTL on a hit; SQL still verifies
  current authority on every request. Redis corruption/outage falls back to SQL.
- UI-20 at `/events/review`: confirmation dialog, required reason, progress bar,
  separate transfer counters and USED-ticket exceptions, safe text rendering,
  mobile layout, visibility-aware polling and durable retry of an uncertain
  request with the original key/body. The parent review refreshes after successful
  cancellation so its displayed Event status becomes CANCELLED.
- Immediate transaction marks CANCELLED, releases ACTIVE holds and reserved
  coupons through canonical SP07, writes the existing cancellation outbox/audit,
  freezes the PAID-order cohort and saves the idempotency receipt. Sales, new
  holds, payment acceptance and check-in independently consult Event state.
- The existing five-second `EventCancellationJob` scheduler now runs real bounded
  work. Each order's canonical SP17 adoption and cursor/count update commit
  atomically under the Event lock. Repeated/concurrent workers do not add another
  obligation or transfer. Restart reads remaining SQL work rather than an
  in-memory position. No gateway I/O occurs in these transactions.
- REQUESTED refunds keep the original obligation and buyer reason and become
  EVENT_CANCELLATION automatically. APPROVED/PROCESSING/UNKNOWN obligations retain
  their original ticket set, amount and attempt. Remaining ACTIVE tickets get a
  separate canonical obligation; USED tickets are exceptions, never automatically
  refunded or restored. Zero-value obligations complete through SP10 without
  fabricated payments/attempts. KHANH-05 performs the simulated transfers.

`COMPLETED` in **job progress** means all orders have been prepared/adopted.
`successfulRefunds`, `pendingRefunds`, `failedRefunds`, `unknownRefunds` and
`usedTicketExceptions` describe actual financial/admission outcomes separately.
The progress is not a claim that every transfer succeeded.

## SQL and architecture

Additive migration `058_khanh06_event_cancellation.sql` adds technical job,
order-queue and receipt tables, ADMIN/WORKER views and procedures. Number 058
avoids the project database's already installed migrations 051–057. It creates
no new business-model class and retains canonical SP13/SP17/SP10/SP07 ownership.
The same contract supports the retained legacy adapter and canonical migration
050 without changing either historical schema implementation.

Runtime writes use JPA `StoredProcedureQuery` inside the existing resource-local
`TransactionManager` with ADMIN or WORKER. Reads bind values and convert SQL
timestamps with an explicit UTC calendar. All nested SP result sets are consumed
before commit, so late SQL errors cannot be hidden behind an intermediate SP10
projection. Runtime principals have projection/procedure grants and explicit
direct queue/receipt read-write denials. Display invalidation preserves Redis
leases, durable idempotency entries and order fences.

## Observed local acceptance — 2026-10-13

| Check | Actual result |
|---|---|
| Focused acceptance on legacy-adapter SQL/Redis | 31 tests, zero failures/errors/skips |
| SQL cases on newly created canonical-050 fixture | 16 tests, zero failures/errors/skips |
| Final focused acceptance on canonical-050 SQL/Redis | 31 tests, zero failures/errors/skips |
| Complete WAR on Tomcat 11 with native restricted ADMIN and WORKER | 23 HTTP/JSP checks; partial SQL batch resumed; actual Tomcat restart retains two obligations/two transfers and returns inventory exactly once |
| Browser on final canonical WAR | Real login, dialog/validation, lost committed response, same-key retry, refreshed CANCELLED review, completed progress, desktop/mobile screenshots, no JavaScript errors or mobile overflow |
| Safe-default Maven regression | 991 discovered, 825 executed, 166 explicitly disabled external cases; zero failures/errors; BUILD SUCCESS |
| JavaScript `.js` unit regression | 60 passed, zero failures/skips |
| JavaScript/guard `.cjs` unit regression | 72 passed, 8 unavailable-platform checks skipped, zero failures |
| Workflow and source checks | actionlint, JavaScript syntax and Git whitespace checks pass |

SQL cases cover start-time denial, immediate hold release, replay/payload conflict,
adoption, zero refund, USED exception, revoked ADMIN/authVersion after lock waits,
two competing cancellation requests, concurrent workers, outer rollback, an
injected error after adoption but before cursor update, partial-batch worker
restart, least privilege, UNKNOWN preservation and late capture compensation.

All mutating tests used owned loopback fixtures: `TicketCenter_Khanh02_Test` and
the newly created `TC_Khanh06_Test_Canonical`. Existing databases/logins were not
replaced. Native acceptance logins and its Tomcat processes are removed afterward.
AUTH/BUYER/MANAGER/CHECK_IN use fixture-owner credentials only for bootstrap in
the WAR harness; native least-privilege evidence here concerns ADMIN/WORKER.
Transfers use the required simulator, not real banking or VNPAY Refund API.

Ignored artifacts: `target/khanh06-final-focused.log`,
`target/khanh06-final-web.log`, `target/khanh06-web-acceptance.json`,
`target/khanh06-browser-acceptance.json`, `target/khanh06-review-*.png`,
`target/khanh06-regression.log`, `target/khanh06-js*.log`.
Some generic HTTP smoke files were initially invoked without their explicit
fixture; their guard failures are excluded from acceptance. The correctly scoped
unit suites and the complete cancellation WAR harness are the results above.

## Shared project database and merge gates

Read-only project preflight verifies immutable 046/048/050 checksums and canonical
tables. The initial installation request was rejected by automatic approval review;
it did not execute. The user then explicitly approved **“Cho phép cài migration
058”**. The exact reviewed script installed 058 atomically on the configured
TicketsCenter project database on 2026-10-13. A separate read-only Verify run
passes the checksum prerequisites, runtime object checks, ADMIN/WORKER procedure
grants and all 24 direct-access queue denials. No cancellation API/SP or shared
business-data test was executed, and existing business rows/passwords were not
updated by this schema-only installation.

`src/test/resources/cancellation/project-contract.ps1` provides guarded Preflight,
Install and Verify modes using ignored private configuration. Installation is
schema-only and atomic, preserves prior migration history and saves the former
SP13 definition privately. No online application deployment or merge is claimed.

`.github/workflows/khanh06-event-cancellation.yml` recreates SQL Server/Redis,
requires all focused suites with zero skips, and checks the complete WAR/browser
and real restart for both legacy-adapter and canonical-050 schemas. Merge to `dev`
still requires the project's green checks and an independent approving review.

## Reproduction

```powershell
. ./src/test/resources/checkin/khanh02/load-local.ps1
$env:TC_CANCELLATION_SQL_TEST='true'
$env:TC_CANCELLATION_REDIS_TEST='true'
mvn -B -DskipTests test-compile dependency:build-classpath '-Dmdep.outputFile=target/khanh06-classpath.txt' '-Dmdep.includeScope=test'
$cp='target/test-classes;target/classes;'+(Get-Content target/khanh06-classpath.txt -Raw).Trim()
java -cp $cp vn.ticketscenter.database.EventCancellationCiSetup --existing
mvn -B test '-Djunit.jupiter.extensions.autodetection.enabled=false' '-Dtest=EventCancellationSqlServerTest,EventCancellationServiceTest,EventCancellationRequestTest,CancellationProgressRedisTest,FinanceOpenApiContractTest'
mvn -B -DskipTests package
$env:TC_KHANH06_TOMCAT_HOME=(Resolve-Path target/khanh01-runtime/apache-tomcat-11.0.25).Path
python src/test/resources/cancellation/web-acceptance.py
```

Set `TC_KHANH06_BROWSER_SCRIPT=src/test/resources/cancellation/browser-acceptance.cjs`
and a pinned Playwright runtime to include browser acceptance. CI installs it.
For the separate new local canonical fixture, run the guarded creation script
once, use the fixed canonical URL with `TC_CANCELLATION_CANONICAL_TEST=true`, then
`EventCancellationCiSetup --local-canonical`. It refuses nonempty/existing targets;
do not use fresh-schema installers against the shared database.
