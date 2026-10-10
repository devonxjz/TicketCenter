# DONG-EPIC — Event integration handoff

Verified locally on 11 October 2026 (UTC+7). Scope is the live epic [#13](https://github.com/devonxjz/TicketCenter/issues/13), DONG-01–05 (#8–12), not the older thirteen-task organization assignment in the reference documents.

Implementation and local acceptance are complete. Remote delivery and shared SQL integration remain open gates; this is not a merged release or a claim that all project tests/CI passed.

## Traceability

| Task | Delivered behavior | Evidence |
|---|---|---|
| DONG-01 / #8 | Public catalog/category/detail/Zone/Seat, filters/pagination, public JSPs, Redis fallback and post-commit invalidation | 126-test initial milestone; current 143-test package; SQL public projection after publish; actual HTTP and browser public detail/seat matrix |
| DONG-02 / #9 | Organization-scoped draft CRUD/submit, current membership/authVersion, chronological validation, lifecycle locks, CSRF/idempotency | SQL replay/conflict/cross-org/revocation/FK rollback/concurrency; actual HTTP 201 replay/401/403; browser create/submit/locked/rejected |
| DONG-03 / #10 | SEATED matrix A…Z, AA…ALL; STANDING capacity; price edits; inventory-safe update/delete; immutable published layout | SQL 27×2 with AA2, held+sold floor, duplicate Zone race, submit/delete and publish/delete races; browser 2×3 plus standing capacity10 |
| DONG-04 / #11 | Multipart PNG/JPEG/WEBP validation, provider upload outside SQL transaction, optimistic save, durable SQL replay, old/unused asset journal | Real Cloudinary upload→SQL URL→public detail, verified provider cleanup; forced rollback/provider failure/cross-org/stale upload tests; actual multipart through WAR |
| DONG-05 / #12 | Admin list/detail/layout/rule selector, canonical publish, reason-required reject, current admin role, status audit and broad cache invalidation | Real SQL publish/reject/resubmit and race; actual WAR non-admin403/publish/public visibility; browser missing-cover error and rejection reason |

## Verification results

| Check | Result | Local output (ignored by Git) |
|---|---|---|
| Selected unit/regression tests + WAR packaging | **143 tests, 0 failures/errors/skips; BUILD SUCCESS** | `target/dong-epic-unit-package.log`, `target/surefire-reports`, `target/ticketscenter.war` |
| Real SQL Server/Redis acceptance | **12 tests, 0 failures/errors/skips** | `EventEpicSqlServerIT`, `target/dong-epic-final-it.log` |
| Real Cloudinary + SQL/public read | **1 test, 0 failures/errors/skips** | `EventCloudinarySqlServerIT`, same final log |
| Actual Tomcat WAR HTTP/auth/session/CSRF/multipart/JSP | **1 test, 0 failures/errors/skips** | `EventHttpWarIT`, same final log |
| Retained HTTP provider fixture cleanup after browser acceptance | **1 cleanup test passed** | `EventHttpFixtureCleanupIT`, `target/dong-epic-cleanup-it.log`; only its unreferenced assets removed |
| Public DOM fixture checks and owned JS syntax | PASS | `src/test/resources/event/dong-01-ui-check.cjs`; `node --check` |
| Actual in-app browser, narrow viewport | PASS: manager list/create/editor, seated/standing forms, submit lock, published description→immediate public update, public seat rows, admin detail/full schedule/inventory, missing-cover rejection and reason persistence | `target/dong-runtime/*-browser.png`; generated fixture accounts only |

The 14 integration tests are real SQL/Redis/HTTP checks. Fault-only storage scenarios use the explicitly named `DongSqlFixture.Storage` fake; the separate provider test and HTTP multipart use real Cloudinary. Redis outage is injected via a failing connection supplier, not a claim that an actual Redis server was stopped. The generation/corrupt-entry tests use real Redis.

SQL Server is 17.0.1000.7 SQLEXPRESS; only `TicketCenter_Dong_Epic_Test_20261011` was provisioned. Transport uses Windows authentication with a temporary loopback named-pipe bridge because server TCP is disabled. Tests execute as dedicated database users `dong_buyer`, `dong_manager`, `dong_admin` in the respective restricted roles; this verifies restricted execution contexts, **not separate SQL-auth logins**. Actual WAR additionally exercises existing JPA pools/auth filters. Redis 8.10.2 runs separately on localhost:16479 without persistence. Tomcat 11.0.25 runs separately on localhost:18082. No existing database or SQL Server service configuration was changed.

The default Maven test suite contains database seeding and real SMTP tests. These were excluded; the selected package is not equivalent to the full default suite or GitHub Actions. No SMTP messages were sent intentionally. [RUNBOOK.md](RUNBOOK.md) provides the exact selection and acceptance setup.

## Implementation decisions and contracts

- Java25/Servlet6.1/JPA resource-local/JDBC SPs, existing `SecurityContext`, `PersistenceRegistry`, `StorageService` and Redis provider. No new framework, dependency or business model.
- UUID strings, integer VND strings, UTC ISO dates and `data` envelope follow CONVENTIONS. `venueName`/`venueAddress`, `rows`/`seatsPerRow` follow current API-MAP/schema. Examples with numeric IDs, `venue`, `rowCount`, invented `sp_event_*` names in the live issues are illustrative and conflict with these canonical contracts.
- One typed `EventCommand` with operation-specific validation and one repository/service pair share the short SP/receipt boundary. Grouping prevents drift between five coupled tasks. Optional `expectedVersion` rejects stale edits; cover always captures/rechecks the current Event version.
- The baseline does not contain draft/zone/reject SPs or durable Event command receipts. [DONG-DB-CONTRACT.sql](DONG-DB-CONTRACT.sql) is a concrete coordination proposal, applied **only to the isolated test DB**, not a migration silently inserted into the shared suite. It adds the missing SPs, technical receipt table and PUBLISHED-only Seat view. Existing `usp_PublishEvent` is still the sole publication path. Status changes use the existing trigger audit; optional admin notes have a separate audit note.
- Canonical publication uses `tc_user_platform_roles`, while current auth uses `tc_users.platform_role`; acceptance fixtures deliberately seed both. Shared role synchronization requires owner review. Event authorization never grants admin based only on the legacy role row.
- Idempotency hashes normalized payload + operation/resources + actor/scope. Redis NX120s gate and compare-token completion24h coordinate requests; SQL receipt and mutation commit atomically. Replays revalidate current permissions, including after delete or Redis eviction. Redis completion failure cannot turn a committed write into an HTTP failure. New writes fail503 before mutation when mandatory Redis lease acquisition is unavailable. Durable receipts currently have no automatic retention job; agree retention before production rollout.
- Invalidation occurs after committed repository return. Generation comparison prevents an in-flight old read from refilling cache. Rollback does not invalidate. A shared cache instance bypasses cache while retrying failed invalidation; a hard crash or multiple nodes still require bounded TTL/reconciliation. Hold/refund/cancellation owners must invalidate these namespaces when integrating; this change does not claim their paths are connected.
- Private org/admin reads are scoped SQL reads, not cached copies. Filter dispatch claims only Event-owned actions, leaving other domain routes to their existing controllers.
- PNG/JPEG are fully decoded locally. WEBP is structurally validated (RIFF chunks/non-animation/dimensions/size), then decoded by Cloudinary. No Java VP8 decoder was added to the locked dependency file. Invalid provider data/errors are sanitized; URL/id metadata never come from client JSON.
- Provider I/O occurs before a short SQL write. Current permission/status/version are checked again by SQL. A lost commit acknowledgement is ambiguous: the code records cleanup metadata rather than blindly deleting a possibly referenced asset. Replaced old covers are also journaled. Reconciliation must check all Event references before provider deletion; no automatic deletion worker is claimed. Journal outage logs a sanitized manual-reconciliation warning. Runtime must supply a persistent writable journal directory.

## Delivery gates

1. [Coordination #45](https://github.com/devonxjz/TicketCenter/issues/45): database owner reviews/adopts the new SQL contract as a **new migration**, grants/views, role synchronization and deploy order. Current baseline alone cannot run the new writes/public seats. Models, `database/**`, `pom.xml`, shared config/persistence/OpenAPI and other domains remain untouched.
2. Tomcat acceptance needed existing JSTL API3.0.0 + implementation3.0.1 in the dedicated server library. This runtime prerequisite must be supplied by deployment/common build owner; no locked POM edit was made. Other owners' dashboard prototype data remain outside this epic.
3. Cancellation/progress controls call the other owner's API and were not counted as completed backend functionality. Hold/payment/refund/coupon flows are outside Đông's five-issue scope.
4. Local branch `feat/dong/dong-epic-event-integration` is based on the DONG-01 commit atop `origin/dev` `71e02ad`. Live issues explicitly target `dev`/`feat/dong/...`, while the older reference workflow says `develop`/`feature/...`; this handoff follows the actual issue/base and records that discrepancy. Coupled subissues are delivered together on the epic integration branch with issue references.
5. Push of the earlier DONG-01 branch was rejected by automatic approval review (private repository payload to an external destination without exact approval). No bypass occurred. Commit/push/PR status must be reported honestly; after explicit destination/payload approval, publish this concrete branch and open PR against `dev`, then obtain required CI and independent review. Issues remain open until shared integration/merge gates are satisfied.
