# KHANH-01 — Check-in Event selection and time window cache

Implementation date: 2026-10-09 (Asia/Saigon).

Scope: [issue #21](https://github.com/devonxjz/TicketCenter/issues/21), with ownership from [epic #27](https://github.com/devonxjz/TicketCenter/issues/27). Branch: `feat/khanh/khanh-01-checkin-events`, based on `origin/dev` at `cfd77c6`. The live issue's explicit `dev` / `feat/khanh/...` convention takes precedence over the older `develop` / `feature/...` examples in the reference documents.

Status: Java/JSP implementation, isolated tests and WAR packaging PASS. SQL Server integration, live Redis, Tomcat 11 deployment, GitHub Actions and reviewer acceptance remain UNVERIFIED/BLOCKED. This document does not mark the issue or its merge gate complete.

## Delivered behavior

- `GET /api/check-ins/events`: `{ "data": [...] }` with UUID strings, UTC timestamps, names, venue, window state and ticket counts. Optional `organizationId` filters within the actor's current DB memberships; without it, only their authorized organizations are included. Includes PUBLISHED/CANCELLED events ending today (UTC) or later; ordering is start time then ID. No financial fields are selected or returned.
- `GET /api/events/{uuid}/check-in-window`: fresh server UTC time; `[startTime - 60 minutes, endTime)`, PUBLISHED only. CANCELLED returns 400 `EVENT_CANCELLED`; missing Event 404; missing membership 403; missing/expired session 401. A disagreement with the canonical UDF returns 503 `CHECK_IN_CONTRACT_MISMATCH` and cannot open the scanner.
- `GET /check-ins`: UI-15 JSP; mobile layout, green/yellow/red state badges, loading/empty/error states, counts, refresh, 30-second foreground polling. All event text is inserted with `textContent`. Only an open Event enables the selection button; the button rechecks the window before navigating to `/check-ins/scanner?eventId=...` (UI-16, supplied by dependent KHANH-02).
- DB truth: `tc_organization_memberships.active=1`, role MANAGER/CHECK_IN_STAFF, active User. Actor comes from `UserPrincipal`, never client userId/role. HTML and the named nested-route dispatcher independently validate authVersion through the existing AuthService, irrespective of annotation filter order.
- Redis keys: `tc:checkin:events:v1:<actorId>:<orgId>` (60 seconds), `tc:checkin:window:v1:<eventId>` (30 seconds). Lists cache ticket counters. Window cache stores schedule metadata, not a clock-frozen `isOpen`. Current membership/schedule/status are re-read even on hits; UDF and boundary evaluation use the request's server clock. This deliberately favors immediate revocation/cancellation over caching authorization. Redis failure/corrupt cache falls back to DB.
- `CheckInEventService.invalidateEvent(eventId, organizationId)` is the post-commit integration hook for schedule/cancellation owners. It deletes the window and uses Redis SCAN to invalidate this organization's actor list keys. No global Event servlet wildcard is claimed: a feature filter dispatches only the exact `/api/events/{id}/check-in-window` route; unrelated Event routes continue down the existing chain.
- Changes stay within `fulfillment/checkin/**`, UI-15, corresponding tests and this evidence. No model, config, POM, database, persistence.xml, OpenAPI, CI or another member's feature is changed.

## Verification

JDK 25.0.4.1, existing Maven/dependencies; no added dependency.

```powershell
mvn -B '-Dtest=CheckInEventServiceTest,RedisCheckInEventCacheTest,CheckInServletsTest,CheckInEventRepositoryTest,FeaturePackageStructureTest,AuthOtpServletsTest,IdentityWebFlowTest,SecurityContextTest,UserPrincipalTest,OtpCryptoHelperTest' package
```

Exit 0, BUILD SUCCESS: **76 tests, 0 failures, 0 errors, 0 skipped**. Of these, 40 are new KHANH-01 checks: service 17, Redis adapter 4, controller/routes 13, repository bindings 6. Log: `target/khanh-01-build.log`; XML reports: `target/surefire-reports/`; output: `target/ticketscenter.war`.

Covered: CHK-01/02 actor scope; CHK-03/04 at 61/60 minutes; exclusive end time; cancellation; unpublished/missing Event; revoked cached membership; changed cached schedule; cached opening boundary/server clock; cache counter reuse without extending hit TTL; explicit invalidation; cache corruption/outage/actor isolation; SQL UUID and UTC bindings, timeout and stable ordering; no financial columns; session expiration; API error mapping/redaction; HTML/JSON representation; coexistence with Event routes; GET-only window.

UI fixture verification (not an API-to-database acceptance test): browser rendered the actual JSP's static HTML/JS with four fixture Events (OPEN, NOT_YET_OPEN, CANCELLED, ENDED). At a 390px viewport the document content width and scroll width were both 375px, with no horizontal overflow. Buttons were enabled only for OPEN. A literal `<script>` Event title rendered as text; zero injected script nodes appeared in cards. Production endpoints do not contain fixture data.

## Outstanding integration gate and schema coordination

The configured Azure SQL endpoint rejected the machine with SQL Server error 40615 (client IP not allowed). Full `mvn -B test` reached existing DB-dependent identity tests and could not finish in this environment; the independent suite above is not claimed as a full-suite pass. No firewall, SQL credentials or shared data were changed.

Explicit read-only SQL acceptance was attempted:

```powershell
mvn -B -Psqlserver-it '-Dtest=CheckInEventServiceTest,RedisCheckInEventCacheTest,CheckInServletsTest' '-Dit.test=CheckInEventSqlServerIT' verify
```

Exit 1: `CheckInEventSqlServerIT` failed (not skipped) because a dedicated `TC_SQL_TEST_LOGIN` was not supplied. Log: `target/khanh-01-integration.log`; report: `target/failsafe-reports/`. Required settings: `TC_SQL_TEST_DB`, `TC_SQL_TEST_LOGIN`, `TC_SQL_TEST_PASSWORD`, `TC_CHECKIN_TEST_EVENT_ID`; the Event fixture must be PUBLISHED in the dedicated test database. Secrets must be provided through environment/configuration, not committed. The test selects metadata/Event/function only; it writes no rows and checks the UUID parameter plus the four time boundaries.

The checked-in SQL currently needs coordination with its owner before acceptance:

1. `database/functions.sql` defines `fn_event_is_check_in_open(@event_id BIGINT, @now DATETIME2)` over legacy `dbo.events` and opens at `start_time`. Issue #21 and existing locked JPA Event require UUID `dbo.tc_events` and opening at `start_time - 60 minutes`. The Java repository intentionally calls the issue's canonical function and does not silently switch to a legacy function or bypass it.
2. `database/schema.sql` creates legacy BIGINT tables, whereas JPA and migration 026 use UUID `tc_*` tables; the live catalog could not be verified because of the Azure firewall. Required projections use existing model names (`active`, `venue_name`, `start_time`, `end_time`, `order_item_id`, `event_id`); no nonexistent `is_active` alias is invented.
3. The CHECK_IN SQL principal must have the minimal identifier/status read permissions needed for membership/Event projections and ticket count joins, plus SELECT on the canonical UDF, without access to financial columns. Actual grants need verification by the schema owner on the test database.
4. The Event/cancellation owner should invoke `invalidateEvent` after a committed schedule/cancellation change; current requests also check DB state, so missing notification cannot authorize a cancelled Event.

After SQL coordination and a reachable dedicated database, rerun the SQL IT and full suite, verify with real Redis, deploy on Tomcat 11, and obtain green Actions plus another person's review before merging to `dev`. UI-16 navigation is completed by KHANH-02; QR decoding/atomic check-in are outside this branch.
