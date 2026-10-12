# KHANH-05 — Refund worker, attempts and reconciliation

Issue: [#25](https://github.com/devonxjz/TicketCenter/issues/25). Branch: `feat/khanh/khanh-05-refund-worker`, created from updated `dev` (`80079d1`) on 2026-10-12. PR target: `dev`. Merge still requires green Actions and an approving review.

## Implemented contract

- `RefundJob` remains the application scheduler entry point used by the existing `PersistenceListener`. It runs `RefundProcessingWorker` and `RefundReconciliationJob`; the existing five-second scheduler scans bounded batches. SQL persists the fifteen-minute reconciliation due time across processes.
- A first dispatch claims the existing approved transfer attempt and **commits before gateway I/O**. Its dispatch marker is never cleared. A restarted worker, an expired lease, or a lost result uses **query with the same attempt ID**, never a second submit. SQL transactions use WORKER; connection and entity manager are closed before network calls.
- Redis `tc:lease:refund:<refundId>` uses `SET NX EX 120` and token-matched deletion. Redis outage falls back to the durable SQL claim. SQL also leases for 120 seconds, fences stale result tokens and validates captured payment/reserved amounts before dispatch.
- `SUCCEEDED` goes through existing hardened SP11 and atomically completes the obligation/tickets, returns the allocated seat/standing inventory once and creates the success outbox once. `FAILED` produces canonical `RETRYABLE`, keeping tickets pending; only explicit administrative retry through SP10 allocates a new attempt on the same customer obligation. `UNKNOWN` produces `NEEDS_RECONCILIATION` and waits fifteen minutes; no invented provider reference or unverified failure.
- A crash before submission with no provider receipt remains unresolved. Provider absence alone is not proof of a rejected transfer. The adapter must confirm final truth before retry becomes eligible.
- Zero refunds finish in the existing **approval transaction** through SP10. No worker gateway call, captured Payment, dispatch or transfer attempt is fabricated. The compatibility zero-completion procedure only acknowledges that already committed result.
- DTOs `RefundTransferRequest`/`RefundTransferResult` validate UUIDs, positive integer VND and final provider references. `PENDING` from the older adapter contract is normalized to UNKNOWN.
- `SimulatedRefundGateway` is explicitly a **local/test simulator**, as specified by SPEC and BACKEND-21-DAYS. Atomic filesystem receipts and process/file locking preserve attempt outcome and immutable amount across threads and JVMs. Timeout-after-side-effect saves the real result before throwing. A new simulator configured FAILED still reads an earlier SUCCEEDED receipt. No VNPAY payment-initiation code or real-bank refund adapter is changed.

## Canonical schema compatibility

The issue's illustrative `tc_refunds`/`tc_refund_attempts` model differs from the committed canonical schema. SPEC, CONVENTIONS, API-MAP and migrations 031–044 remain authoritative:

| Canonical concept | Existing physical contract |
|---|---|
| Public refund obligation and current attempt | `tc_refund_obligations` |
| Durable transfer attempt | Legacy `tc_refunds` and mirrored `tc_refund_transfer_logs` |
| Technical attempt projection required by the issue | `tc_refund_attempts` view over existing logs and dispatch diagnostics |
| First dispatch, lease and due time | Additive `tc_refund_dispatches` technical table |
| Approval / attempt allocation / zero completion | Existing SP10 `usp_ReviewRefundRequest` |
| Transfer result, financial limits and inventory | Existing SP11 `usp_ApplyRefundResult` |

Migration `048_khanh05_refund_worker.sql` adds the ledger, projection, candidate view and compatibility worker procedures. It creates no duplicate obligations or parallel business models and rewrites no historical migration. Worker procedures use short transactions/savepoints, established Event → Order → Payment → Request → Attempt → dispatch lock order, role checks and token fencing. Only WORKER receives dispatch/result execution; direct ledger writes are denied to runtime roles.

## Acceptance evidence

Local SQL Server 2022, Redis on loopback, JDK 25 and Tomcat 11.0.25. Disposable named fixture: `TicketCenter_Khanh02_Test`; the fixture guard refuses remote targets. Integration tests use restricted SQL database users rather than owner rights. Full WAR acceptance uses a separate native WORKER login in `tc_worker`; the other runtime roles use fixture owner credentials for bootstrap, so this is worker-specific privilege evidence, not a claim of production role credential provisioning.

| Requirement | Evidence |
|---|---|
| WRK-01 successful standing/seated refund | SQL tests verify COMPLETED, REFUNDED, correct inventory and one attempt/outbox |
| WRK-02 definitive failure and explicit retry | Same public obligation, old FAILED preserved, new attempt only after admin retry |
| WRK-03 timeout/UNKNOWN | No provider reference invented, inventory retained, fifteen-minute due gate, same-attempt query |
| WRK-04 zero refund | Completed by actual approval SP, no captured payment/attempt/gateway call |
| WRK-05 crash/restart | Claim committed, gateway effect saved, missing DB result recovered by query; pre-call crash remains unresolved |
| Concurrent workers / Redis unavailable | Two genuine SQL connections and synchronized workers produce one dispatch/gateway submit |
| Rollback and stale worker | Outer rollback and injected failure after inventory mutation undo financial effects; expired old token cannot finalize |
| Extra-capture compensation | Separate compensation transfer succeeds without changing valid order tickets/inventory |
| Network transaction boundary | Gateway observes the committed dispatch with zero open worker connections; JPA path also executes actual SPs |
| Durable simulator | New objects and separate JVMs recover the old receipt; amount cannot change; concurrent submissions share one receipt |
| Redis lease ownership | Real TTL 120, exclusive claim and successor-token preservation |
| Whole application lifecycle | Unmodified complete WAR starts on isolated port 18085; timeout-after-transfer yields NEEDS_RECONCILIATION, real Tomcat restart queries the persisted receipt and yields COMPLETED with one attempt/outbox and one inventory return |

Focused acceptance: **33 tests: 18 real SQL/JPA, 2 real Redis, 7 worker unit and 6 simulator/crash/JVM tests**, no failures/errors/skips. Additional payment, financial safety, QR and check-in regression is run through the shared SQL pipeline. `mvn verify` default run: 856 tests, zero failures/errors, 125 infrastructure cases skipped when their explicit switches are disabled; it does not replace the required opt-in acceptance. Node regression: 72 pass, 9 Windows-specific guards skipped, zero failures.

Azure project installation: migration 045 installed atomically with canonical LF SHA-256 in `tc_schema_migrations`; prerequisite immutable hashes checked (equivalent checkout LF/CRLF allowed). Read-only verification checks WORKER projections/SP permissions, direct-write denial and BUYER dispatch denial using existing database-user impersonation. Counts of all pre-existing business tables are unchanged. No project orders, users, tickets, attempts, passwords or gateway effects were changed by installation. Production runtime credential setup is not claimed from this impersonation check.

The initial full-WAR compatibility patch updated only the KHANH-03 test fixture and organization-event route assertion. On 2026-10-13 this branch merged current `dev` (`82c5ae7`), which now contains approved KHANH-04 and canonical Event/authority migrations through 047. Test schema setup consumes the same contracts as that updated dev. No Event/report implementation is changed by KHANH-05.

### Database and privacy update — 2026-10-13

The user moved the project to database `TicketsCenter` on a new Azure SQL server. All four prerequisite checksums match; its imported ledger uses `.sql` filenames, which the installer now recognizes without rewriting history. The KHANH-05 contract was installed atomically on the new database. A new contained WORKER user received only `tc_worker` and metadata visibility, with a generated password stored solely in the ignored private configuration. Native WORKER connection/read access, SP grants and direct-write denial were verified; existing users/passwords and all pre-existing business table counts are unchanged.

The user's credential concern prompted a separate configuration correction: the formerly tracked live `application.properties` is removed from Git and excluded from resources/WAR; private configuration is explicitly loaded via `TC_CONFIG_FILE`. Public template defaults, admin fallback identities, mail test recipients and the KHANH-01 workflow no longer contain the previous personal accounts/passwords. CI has a fictitious admin and a generated masked admin password; SQL service credentials are specific to the disposable run. The literal ROOT route and XML root terms were not SQL accounts. Exact-value scanning of the tracked tree finds zero matches of the prior personal credential values, and actual WAR inspection verifies no private configuration is packaged. Git history was not rewritten; previously published credentials require owner rotation.

Private-file behavior has two separate-JVM tests for selection/override and fail-closed behavior. Combined local Java verification on updated dev ran 952 tests, zero failures/errors, 147 explicitly disabled infrastructure tests; opt-in SQL/Redis acceptance runs separately. The KHANH-05 CI job now requires **35** tests (the original 33 plus the two private-configuration tests), all without skips, before complete-WAR timeout/restart acceptance.

The updated combined opt-in run passed **77 tests with no failures/errors/skips**: the 35 above plus 16 financial SQL cases, 9 payment cross-domain cases, 14 check-in SQL cases and 3 QR interoperability cases. Existing local fixtures were synchronized with current dev's contracts and their accumulated expired holds processed through the real bounded expiry SP; no shared database was used for these test mutations.

## Reproduction and runbook

Fresh CI: `.github/workflows/khanh05-refund-worker.yml` creates SQL Server/Redis services and masked disposable credentials, installs the full schema, requires all four acceptance suites with zero skipped tests, packages the complete WAR and runs real Tomcat restart acceptance. Artifacts contain sanitized reports/Tomcat logs and acceptance summary, excluding credentials and fixture files.

Local with the existing private secret loader:

```powershell
. ./src/test/resources/checkin/khanh02/load-local.ps1
$env:TC_REFUND_WORKER_SQL_TEST='true'
$env:TC_REFUND_WORKER_REDIS_TEST='true'
mvn -B -DskipTests test-compile dependency:build-classpath '-Dmdep.outputFile=target/khanh05-classpath.txt' '-Dmdep.includeScope=test'
$cp='target/test-classes;target/classes;'+(Get-Content target/khanh05-classpath.txt -Raw).Trim()
java -cp $cp vn.ticketscenter.database.RefundWorkerCiSetup --existing
mvn -B test '-Djunit.jupiter.extensions.autodetection.enabled=false' '-Dtest=RefundWorkerTest,WorkerCrashRecoveryTest,RefundWorkerSqlServerTest,RefundWorkerRedisTest'
mvn -B -DskipTests package
$env:TC_KHANH05_TOMCAT_HOME=(Resolve-Path target/khanh01-runtime/apache-tomcat-11.0.25).Path
python src/test/resources/refund/khanh05/web-acceptance.py
```

The existing-fixture installer restores only the exact committed SP07 helper used by fresh CI if older local suites had overwritten it; it never touches a remote project. Tomcat acceptance creates a fresh runtime base, preserves the user's server on 18081 and removes its own process/native test login when finished. It advances reconciliation due time **only in the guarded fixture**, not via any HTTP parameter.

Operational configuration remains `refund.simulated.state.directory`, `refund.simulated.outcome` and `refund.simulated.timeoutAfterSideEffect` (AppConfig environment equivalents supported). Keep the simulator directory on persistent storage across restarts. Investigate unresolved provider observations using attempt ID and receipt history; do not delete dispatch markers or blindly resubmit. FAILED retries belong to the existing admin decision flow. Reconciliation with a real provider requires an adapter that authenticates the provider result and supports attempt-ID lookup; real VNPAY Refund API integration is optional in this issue and has not been claimed.
