# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

TicketsCenter: a Java 25 / Servlet 6.1 (`jakarta.*`) / JSP+JSTL WAR for Tomcat 11, with JPA/Hibernate + HikariCP on SQL Server (Azure SQL for the shared DB), Redis (Jedis) for cache/idempotency, payOS (`vn.payos:payos-java`, payment links + signed webhook) for payments (refunds/payouts are simulated). No Spring. It is a DBMS course deliverable; docs are mostly Vietnamese. Business references: `docs/references/` (`COVERAGE.md`, `CONVENTIONS.md`, `API-MAP.md`).

## Commands

- Build + unit tests: `mvn -B test` (surefire runs `*Test.java` plus `Day01IT`, `Day05IT`, `PasswordResetIT`). Single test: `mvn -B test -Dtest=ClassName#method`.
- Real SQL Server integration tests: `mvn -B -Psqlserver-it -Dit.test=SomeIT verify` (needs a loopback `TC_Vuong_Test_*` fixture DB; external-service ITs are skipped by default, and a skip is not acceptance).
- Package: `mvn -B package` (WAR; `application.properties` is excluded from the artifact). Quick dev server: `mvn jetty-ee11:run`.
- Config: copy `src/main/resources/application.properties.example` to the git-ignored `application.properties`. Java does not read `.env`.

## Architecture

- Package-by-feature under `src/main/java/vn/ticketscenter/`: `identity`, `event`, `ticketing`, `order`, `payment`, `fulfillment` (ticket/checkin/refund), `settlement`, `organizer`, `admin`, `audit`, `report`, `dashboard`, `storage`, `config`, `persistence`. Within a feature, layers are `controller`/`service`/`repository`/`model`/`dto`. `FeaturePackageStructureTest` enforces the top-level packages.
- Request flow: Filter → Servlet → Service → Repository → SQL. Pages return JSP from `WEB-INF/views/<feature>/`; `Accept: application/json` returns DTOs (envelope `{"data":…}` / `{"error":{code,message,correlationId}}`). Routes are registered via servlets and `*RoutesFilter`s (see `config/web`). `src/main/resources/openapi.json` backs the Swagger page.
- **SQL Server owns business rules and permissions.** Write paths go through stored procedures (`usp_*`) called via `StoredProcedureQuery`; reads go through views/UDFs with bound parameters. Don't dirty-write entities then call a procedure, don't add inline INSERT/UPDATE, and clear/refresh the persistence context after a procedure call. Money is `decimal(19,0)` VND (`BigDecimal`, JSON string), times are UTC `Instant`.
- DB objects named in COVERAGE (views, procedures, functions, triggers, roles) are mandatory even if no Java calls them — check before dropping anything "unused".
- Six `tc_*` runtime roles/persistence principals (migration 008 onward); grant EXECUTE on new procedures and revoke direct table writes in the same migration.
- Transactions: procedures use `SET NOCOUNT ON`, `XACT_ABORT ON`, THROW from CATCH, and join a caller's transaction (savepoints). Never hold a transaction across payOS/email/storage calls; use the outbox with leases. Idempotency keys are business IDs (holdId, orderId, txnRef, refundId+attemptId, payoutId). Lock order is documented in `docs/backend/` (see the cross-domain contract) — follow it.
- Tests that need locks/permissions must use real SQL Server; H2/mocks don't prove them. SQL tests live in `database/tests/` and must fail with THROW, not PRINT.

## Migrations (`database/migrations/`)

- Never rename or edit an applied migration. The shared Azure DB records applied files by **name** in `dbo.tc_schema_migrations(version, checksum_sha256)` (checksum = SHA-256 with LF-normalised line endings). Duplicate numbers (027, 028, 029, 030, 043, 045) are intentional; `APPLY_ORDER.md` gives the required order for a fresh DB. From 046 on, one file per number, applied numerically.
- New migration: next unused number (check unmerged branches too), one file per number. Runbook: `docs/backend/database-runbook.md`.
- This Linux machine has no `sqlcmd`/`pwsh`/Docker; migrations have been applied with a small JDBC runner (split on `GO`, one transaction, dry-run then commit). Ask before applying anything to the shared Azure DB in a new session.

## Workflow

GitFlow-lite: `main`, `develop`, and `feature/<name>/<task-id>-<desc>`; PRs target `develop` (acceptance via `develop → main` + tag). Commits use conventional prefixes (`feat(scope):`, `fix(scope):`, `refactor(db):`). No direct commits to `main`/`develop`; no push/merge/deploy unless asked. Evidence for tasks goes in `docs/evidence/<owner>/<TASK-ID>.md` — never include secrets, cookies, OTPs, QR payloads, or connection strings with passwords.
