# DONG-EPIC — Event integration handoff

Latest: [DONG-PROGRESS-20261012.md](DONG-PROGRESS-20261012.md). Both branches now
integrate dev c1d7e0a (PR67). Current proposal is migration043;030 below is historical.
Current validation:717 Java executed/104 guarded skips,116 JS,20 real Event tests
with schema031–042 and migration043 test1/1. Review,
combined SQL/runtime rollout and final dev acceptance remain open.

Verified locally on 11 October 2026 (UTC+7). Current source integrates `origin/dev` at `3e380bf`. Scope is the live epic [#13](https://github.com/devonxjz/TicketCenter/issues/13), DONG-01–05 (#8–12), not the older thirteen-task organization assignment in the reference documents.

[DONG-ACCEPTANCE-20261011.md](DONG-ACCEPTANCE-20261011.md) is the current task
checklist: all five implementation/local-acceptance tasks are checked. Issues
stay open for independent review, shared SQL/runtime adoption, merge and final
dev acceptance. At the recorded Event/SQL source heads, each PR has12 successful
CI checks and10 skipped checks. The newest read-only shared probe returned40613;
earlier40615 and counts/routing below are historical checkpoints.

Implementation and local acceptance are complete; remote delivery is [PR #51](https://github.com/devonxjz/TicketCenter/pull/51). Shared SQL integration and merge remain open gates; this is not a merged release or a claim that all project tests/CI passed.

## Thorough review checkpoint — 11 October 2026

[DONG-REVIEW-20261011.md](DONG-REVIEW-20261011.md) records six reproduced/fixed
defects after dev3e380bf integration: cache retry loss, stale admin review,
deletion cleanup loss, oversized upload503, unknown category409 and malformed
detail JSON under list filters. Local verification:385 Java executed/60 guarded
skips;59 Node;18 platform guard;19 real SQL/Redis/Cloudinary/WAR tests; migration
and fixture cleanup1/1 each. All executed checks pass. Shared metadata read now
returns SQL40615; schema/logins and independent review remain external gates.
SQL is separately published as draft [PR56](https://github.com/devonxjz/TicketCenter/pull/56).

## Previous integration checkpoint — 11 October 2026

Integrated newer `origin/dev`7541a65 (PR52/53/54). Its Report named dispatcher now
owns only reports/overview, so EventApiServlet again owns the organization
wildcard with its own multipart configuration. Removed the obsolete Event
listener that changed Report's multipart metadata. Actual full-WAR acceptance
checks Event multipart create/upload, Report pass-through and authorized
organization overview200 in the same deployment.

Current safe-default build: **383 executed Java tests passed,60 guarded external
tests skipped (443 total)**; **56 Node tests passed**, plus **7 full-WAR URL guard
tests passed**. Dedicated acceptance: **13 SQL/Redis +1 real Cloudinary +2 actual
WAR HTTP =16 passed, no skips**. SQL acceptance now creates current ADMIN without
any legacy role row. The canonical publication proposal uses current ACTIVE
`tc_users.platform_role`; a separate regression proves a revoked ADMIN with a
stale legacy ADMIN row cannot publish, even by directly invoking SP12. No user
role synchronization or role grants are performed.

The first attempt failed because the SQL bridge was mistakenly started under
Windows PowerShell5 rather than its required PowerShell7 runtime. After correcting
the test helper and restarting the dedicated WAR, the unchanged test suite
passed. This was fixture startup failure, not a silently skipped acceptance.
Private outputs: `target/dong-current-dev-package.log`,
`dong-current-dev-node.log`, `dong-current-dev-safety.log`,
`dong-current-dev-it.log` (failed setup) and `dong-current-dev-it-retry.log`.

Concrete HTTP/routing, Redis TTL/namespace and other-owner postcommit handoff are
in [DONG-INTEGRATION.md](DONG-INTEGRATION.md). SQL is prepared separately under
coordination#45 as new migration030; proposed029 belongs to refund PR50. Existing
migrations are not rewritten and no shared database is changed. Migration030 was
applied/reapplied atomically in the dedicated Dong fixture with durable receipt
history preserved and restricted buyer Seat access verified. This makes adoption
reviewable; it does not count as shared rollout approval.

A read-only schema metadata probe against the configured shared database returned
SQL40613 (database unavailable). No shared-schema or actual-login acceptance is
claimed, and no shared SQL writes were attempted.

The following tables retain the earlier28ce056 checkpoint and its historical
counts/routing decisions. The current results above supersede them.

## Integration with current dev

The initial checkpoint commits are `d84c4ef` (catalog), `d003350` (SQL proposal), `9d8b4ec` (five-issue integration). A final fetch found newer dev `28ce056`, including PR48 Event images and PR49 dashboards/reports. The local integration preserves their source/history; no shared-domain business code was rewritten.

- Removed Event's overlapping public/organization wildcard mappings. Catalog serves public reads; the scoped Event filter routes only its actions. Report keeps its organization wildcard; an Event-owned startup listener supplies multipart metadata on that registration, and named dispatch enters the existing image servlet. Actual WAR tests prove deployment and Report pass-through (its own missing-filter400), rather than assuming annotation compilation proves deployment.
- Preserved the newer immutable Cloudinary intent/assets workflow, default category artwork, migration CLI and cleanup worker. Organizer creation uses its multipart contract; versioned cover uploads use it, including its existing published-cover exception. JSON lifecycle/partial description edits and unversioned compatibility cover requests keep the scoped SP/receipt path. Separate `EVENT_IMAGE` Redis keys coordinate the image workflow; its durable SQL receipt remains authoritative. Provider calls stay outside transactions; completion failure preserves successful writes.
- Added post-commit cache invalidation to existing image saves/replays; preserved `id`/`saleStart`/`saleEnd` public aliases and old `?id=` links while retaining new `eventId`/paging/filters/Seat APIs. Both write formats apply title/schedule validation. UI retains cover thumbnails, millisecond schedule precision, previews/progress and default artwork; aborted image intents rotate keys while transient failures retain them.
- Final safe-default build: **375 executed tests passed, 59 external DB/SMTP/Redis tests explicitly skipped (434 total)**. **56 Node tests passed**. Dong's opted-in acceptance runs **12 SQL/Redis +1 real provider +2 actual WAR HTTP tests without skips**. The extra HTTP flow covers default artwork creation/replay, invalid title/past sale opening, published versioned cover replacement/replay and immediate public cache refresh. These results supersede the 143-test checkpoint, not the unrelated skipped external suites.
- The new globally discovered test extension only accepts Vương's named fixture/marker. Direct runs of exactly Dong's listed ITs turn off its auto-discovery and use Dong's own explicit `TC_DONG_ACCEPTANCE=true`, loopback URL/database-name guard and provider opt-in. Never use that invocation to run unrelated DB/SMTP suites. No application/runtime security setting or shared test-support source was changed.

## Traceability

| Task | Delivered behavior | Evidence |
|---|---|---|
| DONG-01 / #8 | Public catalog/category/detail/Zone/Seat, filters/pagination, public JSPs, Redis fallback and post-commit invalidation | 126-test initial milestone; final 375-test safe build; SQL public projection after publish; actual HTTP and browser public detail/seat matrix |
| DONG-02 / #9 | Organization-scoped draft CRUD/submit, current membership/authVersion, chronological validation, lifecycle locks, CSRF/idempotency | SQL replay/conflict/cross-org/revocation/FK rollback/concurrency; actual HTTP 201 replay/401/403; browser create/submit/locked/rejected |
| DONG-03 / #10 | SEATED matrix A…Z, AA…ALL; STANDING capacity; price edits; inventory-safe update/delete; immutable published layout | SQL 27×2 with AA2, held+sold floor, duplicate Zone race, submit/delete and publish/delete races; browser 2×3 plus standing capacity10 |
| DONG-04 / #11 | Multipart PNG/JPEG/WEBP validation, provider upload outside SQL transaction, optimistic save, durable SQL replay, old/unused asset journal | Real Cloudinary upload→SQL URL→public detail, verified provider cleanup; forced rollback/provider failure/cross-org/stale upload tests; actual multipart through WAR |
| DONG-05 / #12 | Admin list/detail/layout/rule selector, canonical publish, reason-required reject, current admin role, status audit and broad cache invalidation | Real SQL publish/reject/resubmit and race; actual WAR non-admin403/publish/public visibility; browser missing-cover error and rejection reason |

## Verification results

| Check | Result | Local output (ignored by Git) |
|---|---|---|
| Safe-default unit/regression tests + WAR packaging | **375 executed tests pass, 59 external tests skipped by the new safe-default guard (434 total); BUILD SUCCESS** | `target/dong-merged-unit-package.log`, `target/surefire-reports`, `target/ticketscenter.war` |
| Real SQL Server/Redis acceptance | **12 tests, 0 failures/errors/skips** | `EventEpicSqlServerIT`, `target/dong-merged-it.log` |
| Real Cloudinary + SQL/public read | **1 test, 0 failures/errors/skips** | `EventCloudinarySqlServerIT`, same final log |
| Actual Tomcat WAR HTTP/auth/session/CSRF/multipart/JSP | **2 tests, 0 failures/errors/skips** | `EventHttpWarIT`, same final log |
| Retained HTTP provider fixture cleanup after browser acceptance | **1 cleanup test passed** | `EventHttpFixtureCleanupIT`, `target/dong-epic-cleanup-it.log`; only its unreferenced assets removed |
| Public DOM fixture checks and owned JS syntax | PASS | `src/test/resources/event/dong-01-ui-check.cjs`; `node --check` |
| Actual in-app browser, narrow viewport | PASS: manager list/create/editor, seated/standing forms, submit lock, published description→immediate public update, public seat rows, admin detail/full schedule/inventory, missing-cover rejection and reason persistence | `target/dong-runtime/*-browser.png`; generated fixture accounts only |

The 15 integration tests are real SQL/Redis/HTTP checks. Fault-only storage scenarios use the explicitly named `DongSqlFixture.Storage` fake; the separate provider test and HTTP multipart use real Cloudinary. Redis outage is injected via a failing connection supplier, not a claim that an actual Redis server was stopped. The generation/corrupt-entry tests use real Redis.

SQL Server is 17.0.1000.7 SQLEXPRESS; only `TicketCenter_Dong_Epic_Test_20261011` was provisioned. Transport uses Windows authentication with a temporary loopback named-pipe bridge because server TCP is disabled. Tests execute as dedicated database users `dong_buyer`, `dong_manager`, `dong_admin` in the respective restricted roles; this verifies restricted execution contexts, **not separate SQL-auth logins**. Actual WAR additionally exercises existing JPA pools/auth filters. Redis 8.10.2 runs separately on localhost:16479 without persistence. Tomcat 11.0.25 runs separately on localhost:18082. No existing database or SQL Server service configuration was changed.

The default Maven test suite contains database seeding and real SMTP tests. These were excluded; the selected package is not equivalent to the full default suite or GitHub Actions. No SMTP messages were sent intentionally. [RUNBOOK.md](RUNBOOK.md) provides the exact selection and acceptance setup.

## Implementation decisions and contracts

- Java25/Servlet6.1/JPA resource-local/JDBC SPs, existing `SecurityContext`, `PersistenceRegistry`, `StorageService` and Redis provider. No new framework, dependency or business model.
- UUID strings, integer VND strings, UTC ISO dates and `data` envelope follow CONVENTIONS. `venueName`/`venueAddress`, `rows`/`seatsPerRow` follow current API-MAP/schema. Examples with numeric IDs, `venue`, `rowCount`, invented `sp_event_*` names in the live issues are illustrative and conflict with these canonical contracts.
- One typed `EventCommand` with operation-specific validation and one repository/service pair share the short SP/receipt boundary. Grouping prevents drift between five coupled tasks. Optional `expectedVersion` rejects stale edits; cover always captures/rechecks the current Event version.
- The baseline does not contain draft/zone/reject SPs or durable Event command receipts. [DONG-DB-CONTRACT.sql](DONG-DB-CONTRACT.sql) is a concrete coordination proposal, applied **only to the isolated test DB**, not a migration silently inserted into the shared suite. It adds the missing SPs, technical receipt table and PUBLISHED-only Seat view. Existing `usp_PublishEvent` is still the sole publication path. Status changes use the existing trigger audit; optional admin notes have a separate audit note.
- The earlier canonical publication depended on `tc_user_platform_roles` and initial acceptance seeded both role representations. The current coordination proposal replaces only SP12's authority check with current ACTIVE `tc_users.platform_role`, matching Event authorization; latest acceptance has no legacy ADMIN seed and proves a stale legacy row cannot authorize publication. Other legacy procedures remain outside this change.
- Idempotency hashes normalized payload + operation/resources + actor/scope. Redis NX120s gate and compare-token completion24h coordinate requests; SQL receipt and mutation commit atomically. Replays revalidate current permissions, including after delete or Redis eviction. Redis completion failure cannot turn a committed write into an HTTP failure. New writes fail503 before mutation when mandatory Redis lease acquisition is unavailable. Durable receipts currently have no automatic retention job; agree retention before production rollout.
- Invalidation occurs after committed repository return. Generation comparison prevents an in-flight old read from refilling cache. Rollback does not invalidate. A shared cache instance bypasses cache while retrying failed invalidation; a hard crash or multiple nodes still require bounded TTL/reconciliation. Hold/refund/cancellation owners must invalidate these namespaces when integrating; this change does not claim their paths are connected.
- Private org/admin reads are scoped SQL reads, not cached copies. Filter dispatch claims only Event-owned actions, leaving other domain routes to their existing controllers.
- PNG/JPEG are fully decoded locally. WEBP is structurally validated (RIFF chunks/non-animation/dimensions/size), then decoded by Cloudinary. No Java VP8 decoder was added to the locked dependency file. Invalid provider data/errors are sanitized; URL/id metadata never come from client JSON.
- Provider I/O occurs before a short SQL write. Current permission/status/version are checked again by SQL. A lost commit acknowledgement is ambiguous: the code records cleanup metadata rather than blindly deleting a possibly referenced asset. Replaced old covers are also journaled. Reconciliation must check all Event references before provider deletion; no automatic deletion worker is claimed. Journal outage logs a sanitized manual-reconciliation warning. Runtime must supply a persistent writable journal directory.

## Delivery gates

1. [Coordination #45](https://github.com/devonxjz/TicketCenter/issues/45): database owner reviews/adopts the new SQL contract as a **new migration**, grants/views, role synchronization and deploy order. Current baseline alone cannot run the new writes/public seats. Models, `database/**`, `pom.xml`, shared config/persistence/OpenAPI and other domains remain untouched.
2. Tomcat acceptance needed existing JSTL API3.0.0 + implementation3.0.1 in the dedicated server library. This runtime prerequisite must be supplied by deployment/common build owner; no locked POM edit was made. Other owners' dashboard prototype data remain outside this epic.
3. Cancellation/progress controls call the other owner's API and were not counted as completed backend functionality. Hold/payment/refund/coupon flows are outside Đông's five-issue scope.
4. Local branch `feat/dong/dong-epic-event-integration` began atop `origin/dev` `71e02ad` and incorporates current dev `28ce056`. Live issues explicitly target `dev`/`feat/dong/...`, while the older reference workflow says `develop`/`feature/...`; this handoff follows the actual issue/base and records that discrepancy. Coupled subissues are delivered together on the epic integration branch with issue references.
5. The earlier DONG-01 push was rejected by automatic approval review. After explicit user approval, the reviewed integration branch was published as PR51 targeting `dev`. No bypass occurred. Obtain current-head CI, independent review and shared SQL integration before merge. Issues remain open until these gates are satisfied.
