# KHANH-03 acceptance and handoff

Issue: https://github.com/devonxjz/TicketCenter/issues/23
Branch: `feat/khanh/khanh-03-refund-request`, from `dev`.

Implemented: owner-only PAID order selection; all tickets returned with eligibility flags; server-side integer VND amounts; pre-start database clock checks after lock waits; atomic request/link/ticket/audit/idempotency writes; UUID headers/IDs; 10–500 character reason; CSRF and live session checks; tracking, detail and attempt history; UI-08 confirmation and status badges.

Canonical contract: Ticket ACTIVE/USED, Refund REQUESTED/APPROVED/PROCESSING/COMPLETED/RETRYABLE/NEEDS_RECONCILIATION/REJECTED. The issue's old numeric IDs and ISSUED/CHECKED_IN examples are interpreted according to SPEC. `tc_refund_obligations` owns the canonical Refund; `tc_refunds` contains transfer attempts. Existing mirror triggers maintain the legacy request and canonical projections. The accepted captured payment is used, and zero-value requests create no fake Payment or transfer.

Writes use the shared JPA resource-local TransactionManager and JDBC CallableStatement; no Order/Ticket entity edits or historical migration rewrites. SQL retains the original response and fingerprint when Redis is unavailable or evicted. Redis locks use sorted ticket sets, compare-token release, 10 second TTL and 86400 second result TTL. Invalidation occurs after commit only.

Approved additions: migration `029_khanh03_refund_request.sql`, standalone `.github/workflows/khanh03-refund.yml`, and installation on the configured project database. Installation is atomic, records a SHA-256 checksum, refuses conflicting migration history and makes no business-data changes. Runtime BUYER receives only projection reads and procedure execution for the new contract.

Local verification (2026-10-11): 42 focused Java cases passed without skips (9 service, 12 HTTP/controller, 4 shared routing, 14 actual SQL/JPA, 3 actual Redis). SQL coverage includes every RFD acceptance case, multi-ticket rollback, free tickets, stale sessions, cross-order input, two-connection races with identical/different keys, direct-write permission denial, injected failure after ticket mutation and lock waits across the event deadline. Typed JSON validation rejects array/string coercion. Full regression: 428 Java cases, 369 executed successfully, 59 other modules' opt-in infrastructure cases skipped, zero failures/errors; 56 Node cases passed. All 20 HTTP checks passed on real Tomcat with the complete production WAR, including the shared login JSP with JSTL, both refund JSPs, owner/CSRF/idempotency behavior and organization route dispatch. A browser walkthrough also verified selecting two tickets, the 320,002 VND confirmation, submission and tracking with both tickets frozen. Project BUYER projections/grants and the migration checksum are separately checked read-only through database impersonation; shared Azure business data is never used as a mutating test fixture.

## Full-project routing integration

Dev originally cannot start because EventApiServlet and FinanceReportServlet both map `/api/organizations/*`. Shared `WEB-INF/web.xml` uses servlet descriptor precedence to preserve the report servlet's three exact mappings and leave the wildcard with EventApiServlet. `OrganizationApiRoutesFilter` forwards only the GET reports subresource, preserves the report servlet's existing path contract and checks the current session before named dispatch. The event and report module implementations remain unchanged. Annotation discovery stays enabled for the full application. The common login JSP also requires the Jakarta Tags API and implementation now packaged as runtime dependencies; authentication implementations are unchanged. Local and CI acceptance deploy the packaged production WAR with no descriptor overlays or omitted modules.

## Reproduce locally

1. On a disposable local SQL Server, run `setup-local.ps1` once; it refuses existing database/credentials. Historical view/function dependencies are resolved without editing old migrations.
2. Load `load-local.ps1` and run `mvn -B test -Dtest=RefundApplicationRoutingTest,RefundRequestServiceTest,RefundRequestServletsTest,RefundRequestSqlServerTest,RefundRequestRedisTest` with dedicated Redis on loopback 16379.
3. Package the WAR, provision `RefundWebFixture`, run `start-local-web.ps1`, then `verify-local-web.ps1`. This requires the checksum-verified Tomcat 11 runtime from the check-in test resources. Fixture-only account passwords are confined to the disposable database.

Database suites have their own explicit environment opt-in and strict loopback/exact-database preflight. They use `*Test` because the existing shared `*IT` gate is hard-coded to a different member's finance fixture. No global guard is disabled. CI creates SQL Server and Redis containers, uses restricted BUYER credentials for the repository, and requires the SQL/Redis suites with zero skips. `ci-web-acceptance.py` verifies the official Tomcat SHA-512, boots the full WAR, runs the HTTP/JSP acceptance and shuts down its own process. No Azure access, outgoing email or deployment occurs in CI.

Project runtime configuration must supply `DB_BUYER_USER` and `DB_BUYER_PASSWORD` for an existing contained user in `tc_buyer`; do not rely on the common administrator fallback. This task installs the contract/grants and verifies the existing `tc_buyer_user` authority by impersonation. It does not create or rotate shared Azure login credentials. Local and CI application tests use a real restricted BUYER login.

PR merge remains subject to green GitHub Actions and a human approving review.
