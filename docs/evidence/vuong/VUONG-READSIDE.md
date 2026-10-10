# Vương finance read-side evidence — Task 4

Plan: `docs/superpowers/plans/2026-10-10-vuong-finance-integration.md`, Task 4. Base a7710e1. User approved UUID/tc_* integer VND and isolated SQL fixture; no peer-review wait.

## Progress ledger

- Filter/audit metadata tests written before implementation. First build stopped because default JDK cannot compile release25; corrected only command environment to JDK25. Actual RED: `mvn -B -Dtest=ReportFilterTest,AuditMetadataTest test` failed missing ReportFilter/AuditMetadata. GREEN: 7 tests, zero failures/errors/skips.
- Service tests written before implementation. RED: selected ReportServiceTest compilation failed missing ReportService/ReportCache. GREEN combined suite: 11 tests, zero failures/errors/skips (2026-10-10 09:03 Asia/Bangkok).
- JDBC doubles test resource ownership/authority ordering only. They are not database acceptance. The actual isolated SQL verification below supersedes the earlier pending gate.

## Decisions and shared interface

- Ruling: EVENT_SALES and SETTLEMENT_BALANCE dates filter Event.end_time, not payment dates — current/confirmed aggregate views cannot represent transactional history. Response `timeBasis` states this explicitly; COHORT_REVENUE/CASH_FLOW use the frozen F05/F10 UTC definitions.
- Ruling: metadata projection only exposes allowlisted UUID, nonnegative integer money, known state and timestamp fields. Unknown/nested/secret fields are discarded; malformed/duplicate-key/oversize JSON fails closed to an empty projection.
- Root mutation integration calls `ReportCache.invalidateAfterCommit(UUID organizationId)` after commit. Keys: `tc:finance:report:g:global`, `tc:finance:report:g:org:{UUID}`; TTL60 payload `tc:finance:report:v1:{scope}:{generation}:{SHA256(actorId|authVersion|all filters)}`. Current authority/event scope is rechecked before every cache read. Redis errors fall back to database; CSV never uses cache.

No full WAR, shared DB, SMTP, remote push or PR executed by this task. CSV Excel/Sheets acceptance and owner full end-to-end flows remain separate gates.

## Completed domain pages and HTTP contracts

Read-side core is commit `3cac455`; shared finance helpers `a7710e1`; platform opt-in guard `f999c42`. SQL projection names/columns match the frozen contract `24064d8` in `docs/backend/vuong-finance-sql-contract.md` (SQL owner's worktree).

All operations below are GET, require a session actor and reload current ACTIVE/authVersion authority in SQL. JSON uses `{data: ...}`; monetary values are whole-VND strings, UUIDs are canonical strings and timestamps are UTC. JSON/pages have `Cache-Control: no-store`. Errors contain a fixed code/message and generated correlation UUID, never SQL exception details.

| Route | Authority and response |
| --- | --- |
| `/api/admin/reports` | Current ADMIN, optionally one organization/event; paginated report. |
| `/api/organizations/{organizationId}/reports` | Current ADMIN or active MANAGER membership for exactly that organization; paginated report. |
| `/api/reports/export` | Current ADMIN for all organizations or current exact-org ADMIN/MANAGER; sequential live ResultSet CSV, UTF-8 BOM, CRLF, sanitized cells, no report cache. Authorization/event scope precede CSV bytes. |
| `/api/reports/options` | Current ADMIN sees all organizations; other actors see active MANAGER memberships only. Optional `organizationId` separately authorized before returning that organization's events. `{data:{isAdmin,organizations:[{id,name}],events:[{id,title}]}}`. |
| `/api/admin/audit-logs` | Current ADMIN; stable `created_at DESC,id DESC` pagination of redacted records. |
| `/api/admin/audit-logs/{id}` | Current ADMIN; one audit record or NOT_FOUND. No raw metadataJson. |
| `/api/admin/overview` | Current ADMIN; actual pending organization/event/refund and draft settlement counts; today's UTC cash flow and cohort commission. |
| `/api/organizations/{organizationId}/overview` | Current ADMIN or exact-org active MANAGER; actual event/pending-event/paid-order counts; today's organization cash flow and cohort commission. |
| `/finance/reports`, `/finance/overview` | Current ADMIN or exact-org active MANAGER. With no organization selection, a manager starts at the first current active manager membership; no membership returns 403. |
| `/finance/audit` | Current ADMIN; escaped JSP with list, filters and safe detail panel. |

Report query: required `from`/`to` ISO Instants, `from < to`, millisecond precision, UTC half-open `[from,to)`; optional `metric` enum `COHORT_REVENUE` (default), `CASH_FLOW`, `EVENT_SALES`, `SETTLEMENT_BALANCE`; canonical UUID `organizationId`, `eventId`; `page` default1, `pageSize` default20/range1–100 and int-safe offset. A route organization conflicting with the query organization fails400; an event outside the authorized organization fails404. CSV accepts the same filter contract and returns all matching rows regardless of page.

Report data: `metric,timeBasis,timezone,from,to,organizationId,eventId,rows,page,pageSize,total`. Row columns are the explicit camelCase frozen SQL projection; all event metrics include zero-order events. EVENT_SALES rows label `isConfirmed`; dates for EVENT_SALES/SETTLEMENT_BALANCE use Event.end_time, as stated in the ruling above.

Audit query: required `from,to`; optional `action` uppercase token (100 chars), `entityType` identifier (100 chars), `entityId` identifier (100 chars), `organizationId,eventId` canonical UUIDs, `page,pageSize` with shared bounds. Audit records expose `id,actorId,action,entityType,entityId,createdAt,metadata`; `metadata` is the bounded allowlisted typed projection. Duplicate query keys or values over200 chars fail400.

Overview data: `asOf,timezone,from,to,organizationId`, scope-specific counts above, `todayCashFlow:{ticketCaptured,compensationCaptured,ticketRefund,compensationRefund,netCashFlow}`, `todayCohortCommission`, `commissionTimeBasis`.

Domain JSPs use plain JSP and `ReadsideHttp.escapeHtml`; no new JSTL dependency. Select options and data render through text-only DOM APIs. The page script shares the JSON/CSV filter builder, clearly labels UTC and time basis, preserves large VND using BigInt, validates CSV MIME before saving, and shows fixed errors/correlation IDs. Report cache defaults enabled; `finance.redis.enabled=false` / `FINANCE_REDIS_ENABLED=false` bypasses Redis for isolated HTTP fixtures, consistent with root finance controllers. Redis failure still falls back to authorized SQL.

## Fresh verification — 2026-10-10, JDK25 / Maven3.9.16

- RED page guard: `FinanceReadsidePageServletTest.actorWithNoManagerMembershipGetsForbiddenInsteadOfMalformedFilter` failed expected403/actual400. GREEN after the explicit no-membership guard; all three page tests pass. They cover forwarding/scoping and stale authority with JDBC doubles, not network HTTP.
- RED page script: Node test run failed because `finance-readside.js` was absent. GREEN: seven behavioral tests pass (UTC/filter contract, invalid date/scope, audit query, >MAX_SAFE_INTEGER VND, safe503 error, failed HTML/CSV download, same-origin successful download response).
- Selected Java suite: 75 executed, 0 failures/errors/skips (`ReportFilterTest,ReportServiceTest,AuditMetadataTest,AuditFilterTest,DashboardServiceTest,ReadsideHttpTest,FinanceReadsidePageServletTest,CsvSanitizerTest,StreamingCsvExporterTest`).
- Actual `ReadsideDatabaseIntegrationTest`: six executed, 0 failures/errors/skips against the private `TC_Vuong_Test_20261010_Finance` fixture, marker `vuong-finance-v1`. Each case inserts unique UUID rows inside a rollback transaction. Test selection validates the explicit `tc.tests.db.name` and actual catalog, so CI's isolated `TC_Vuong_Test_CI_*` database is supported. Config secrets were bound only through environment variables, never CLI parameters/log output.
- SQL cases: half-open cohort/cash-flow/CSV definitions and compensation separation; tenant A/B/staff-only and stale actor denial before bytes; stable audit list/detail and redaction; actual admin/org overview and all-org report; EVENT_SALES/SETTLEMENT_BALANCE against frozen columns; selector scope/admin endpoint/membership revocation.
- Bare safe `mvn -B test`: 240 discovered, 211 executed, 29 opt-in external skips, 0 failures/errors. This is the own-worktree suite; root integration adds other owned tests separately.
- Combined Node suite: 20 executed, 0 failures/skips (seven readside + eight Swagger initializer + five truthful summary tests). `git diff --check` clean.
- Logs preserved under this worktree's ignored `target/readside-final-{unit,db,safe-suite,node}.log`; earlier RED/GREEN logs moved into ignored target without deletion.

Root performs actual HTTP/JSP/browser integration separately after cherry-pick. This task does not claim network HTTP, full application WAR/E2E, real SMTP, remote publication, or Excel/Sheets CSV acceptance.

## #33 completion follow-up — admin overview cache, KPIs and chart

Base `4b6afdb`; same existing readside worktree, no root/SQL/model/pom/platform edits.

- `DashboardCache` owns the exact global admin payload key `tc:admin:overview:v1`, TTL60, optional Redis and fail-open cache access/write/deletion. `DashboardService.admin` opens SQL and calls current `FinanceAuthorization.requireAdmin` **before** every cache read, including hits. Organization overview bypasses the global cache and uses authorized scoped SQL. Both `DashboardServlet` and `ReportServlet` inject `DashboardCache.configured()`, honoring `finance.redis.enabled=false` / `FINANCE_REDIS_ENABLED=false`.
- Root integration contract: call `vn.ticketscenter.dashboard.service.DashboardCache.invalidateAdminAfterCommit()` only after committed finance mutations. It deletes the admin projection and honors disabled Redis. Other owners' approval/identity mutations expire naturally within60 seconds unless their owners wire the same after-commit invalidation; this task does not alter their logic.
- New admin fields: `totalUsers` counts all rows in `dbo.tc_users`, including inactive users; `activeOrganizations` counts `dbo.tc_organizations.status='APPROVED'`. Ruling: APPROVED is the operational organization state because canonical `OrganizationStatus` has no ACTIVE state — inventing ACTIVE would return a false zero. Cost if future schema changes: update this predicate and its SQL fixture test with the organization owner.
- All overview scopes now include exactly seven zero-inclusive UTC days, ending with the current UTC date: `dailyMetrics:[{date,from,to,cohortGrossRevenue,netCashFlow}]`. Dates are UTC `[from,to)` windows; money is SQL DECIMAL summed and serialized as integer strings. `dailyMetricTimeBasis:{cohortGrossRevenue:'ORDER_PAID_AT',netCashFlow:'PAYMENT_PAID_AT_AND_REFUND_PROCESSED_AT'}` states the distinct definitions. Fixed seven-day bound requires14 aggregate queries, with today's existing cash/commission results reused, rather than an unbounded date loop.
- Overview JSP/script render an accessible standalone SVG, signed cash bars around zero, two labelled series, and a corresponding exact-VND data table. Scaling uses BigInt rational arithmetic; only bounded pixel heights convert to Number. Admin global pending counts link to existing verified routes `/admin/org-approval`, `/admin/event-approval`, `/admin/refund-approval`, `/finance/settlements`. Current timestamps/freshness are visible; no prototype values.
- At root's request, the audit page/query also includes canonical optional `actorId` UUID, with local malformed-ID validation. The backend actor filter is implemented by root's audit reviewer and must be integrated with this UI; no audit repository/filter ownership overlap occurred here.

Fresh TDD/verification (2026-10-10, JDK25/Maven3.9.16):

- Cache RED: missing DashboardCache; then two behavioral failures for absent KPI/chart projection. GREEN: six cache tests cover miss/store TTL60, hit with renewed DB authorization, stale actor, demoted admin, Redis outage SQL fallback, invalidation and organization bypass (the first test covers both miss and hit).
- Actual SQL RED: missing `totalUsers` and `dailyMetrics`. GREEN: eight `ReadsideDatabaseIntegrationTest` cases, 0 failures/errors/skips. New cases verify UTC seven-day boundaries/zero days, cohort gross100 versus cash90, exact `9007199254740993` VND strings, user-count delta including disabled user and APPROVED organization-count delta. Every test rolls back unique fixture rows. Private credentials loaded only from the authorized external fixture config and bound via environment; no secrets printed.
- Chart JS RED: missing chartBars. GREEN: nine readside Node tests, 0 failures/skips; signed chart ratio, zero totals, >MAX_SAFE_INTEGER money and actor UUID validation included.
- Selected Java readside suite:81 executed, 0 failures/errors/skips. Bare safe Maven suite:248 discovered,217 executed,31 guarded external skips,0 failures/errors. Combined Node suite:22 passed (nine readside, eight initializer, five summary),0 failures/skips. Whitespace checks clean.
- Logs retained in ignored target: `dashboard-cache-{red,repository-red,green}.log`, `dashboard-db-{red,green}.log`, `dashboard-chart-js-{red,green}.log`, `dashboard-final-{unit,safe-suite,node}.log`. Root owns the subsequent HTTP/JSP integration and after-commit wiring; no actual Redis server/HTTP/browser acceptance claimed by these test doubles.
