# VUONG Finance Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai và kiểm chứng toàn bộ phần Vương của #28–#33 trên UUID/tc_*; không chờ review PR.

**Architecture:** SQL owns transactional writes; JDBC repository + current-user authorization + servlet/JSP DTO. Shared finance helpers provide Connection ownership and safe JSON/errors. DB migration, platform CI và read-side report/audit là các phần song song có file ownership riêng; root triển khai commission/settlement/payout và tích hợp.

**Tech Stack:** Java25, Maven3.9.16, JUnit5.12, Servlet6.1, SQL Server, Jedis hiện có; không dependency mới.

**Spec:** ../specs/2026-10-10-vuong-finance-integration-design.md; schema decision ../.. /backend/2026-10-10-vuong-schema-decision.md (đường thực: docs/backend/2026-10-10-vuong-schema-decision.md). User đã chốt schema proposal và yêu cầu không chờ review; Native tiếp tục trực tiếp, peer review không là gate bắt đầu task.

## Global Constraints

- UUID/tc_*, money integer-VND String/DECIMAL(19,0), UTC [from,to); không legacy BIGINT/decimal cents.
- Locked model/pom/persistence.xml/026 giữ nguyên; thêm migration mới, test schema biệt lập, không email/transfer thật.
- Repo JDBC owns Connection/cursor cleanup; SQL owns atomic write, auth checks and durable replay; Redis chỉ acceleration.
- Các helper exact API: `vn.ticketscenter.settlement.infrastructure.FinanceDatabase` functional interface `Connection open() throws SQLException`; `FinanceAuthorization.requireAdmin(Connection,UserPrincipal)` và `requireManager(Connection,UserPrincipal,UUID)`; `FinanceException(String code,int status)`; `FinanceJson.write(Object)` chỉ map/list/scalar an toàn; `FinanceRuntime.database(ServletContext)` trả FinanceDatabase.
- Mọi servlet xác thực session, current DB status/authVersion; POST CSRF+UUID Idempotency-Key; không echo lỗi SQL/bank payload.
- Không chờ reviewer, không tự merge/publish/deploy/đóng issue; evidence separates implementation from missing owner E2E/Excel acceptance.

## Review Focus

1. Concurrent refund/confirm and double payout: DB locks, exact stored amounts and one final result.
2. Stale session or manager A requesting B/cache: current DB authority before bytes/cache.
3. Same-key different body, crash/timeout: durable fingerprints, UNKNOWN keeps pending, no second provider attempt.
4. Date boundaries, large values, compensation: [from,to), decimal aggregate, HALF_UP per Order, no metric substitution.
5. Missing config/IO fail: fail closed without external SMTP/DB shared effects, truthful test totals and cursor cleanup.

## Task 1: Shared finance infrastructure (root)

Files src/main/java/vn/ticketscenter/settlement/infrastructure/{FinanceDatabase,FinanceException,FinanceJson,FinanceAuthorization,FinanceRuntime}.java; tests same package FinanceJsonTest/FinanceAuthorizationTest.

- [x] Write failing tests for scalar/map/list money String/UUID/Instant escaping, unsupported entity rejected; authorization active/admin/authVersion and exact manager scope.
- [x] Run selected tests RED; implement helpers with bound SQL and fixed errors, production lazy datasource from DatabaseConfig, lifecycle cleanup.
- [x] Run GREEN and safe regression; commit helpers so independent read-side work consumes exact interface.

## Task 2: Canonical SQL and isolated fixture (SQL implementer)

Files database/tests/vuong/001_uuid_test_schema.sql, database/migrations/027_vuong_finance.sql, database/tests/vuong/{finance_contract,finance_concurrency,finance_permissions}.sql, docs/backend/vuong-finance-sql-contract.md.

- [x] Read locked mappings/spec and define exact columns/SP result sets; create test-only base with guarded TC_Vuong_Test_* DB, no existing DB edits.
- [x] Write failing assertions for fee rounding/cap/zero, accepted-order snapshot/compensation, blocker/confirm/immutable, payout sameID/differentterms/final replay/limits and audit append-only. Run on dedicated SQL Server via integrated sqlcmd RED.
- [x] Implement new migration with base preflight; SP14/15/16 atomic transaction/locks/current ADMIN, durable idempotency/fingerprint tables; V03/V04/V08/F02/F05/F09/F10; commission create/update SP and safe audit/grants.
- [x] SQL assertions GREEN and permission/rollback/concurrency evidence; publish contract names/columns for Java consumers. Never modify 026/model or claim JDBC tests ran from sqlcmd.

## Task 3: Test/CI isolation and truthful gates (platform implementer)

Files owned .github/workflows/*, src/test/scripts/platform-safe-tests.ps1, src/test/java/vn/ticketscenter/platform/testsupport/* and resources/JUnit config or minimal condition annotations if needed; docs/backend/platform-test-isolation.md.

- [x] Test defaults prevent known DB/SMTP setup before execution while pure methods still run; explicit flags need controlled test config. Demonstrate RED before gate.
- [x] Implement JUnit conditions/tags or auto-discovered condition without changing pom/business logic; bare mvn test safe, all skipped external cases named and explained. Opt-in suite must fail when target missing, not skip silently.
- [x] CI runs safe unit/Node checks; DB stage real existing suite only with disposable schema; no unconditional PASS, feature push cannot publish latest/deploy/start shared worker WAR. Keep migration/E2E gate explicit until schema fixture operational.
- [x] GREEN selected/default suite and static workflow validation; commit own files, no secret logging/config reads.

## Task 4: Report/audit/dashboard (read-side implementer)

Files report/{dto,repository,service,controller}, audit/{dto,repository,service,controller} excluding locked model, dashboard/**, new JSPs WEB-INF/views/{report,audit,dashboard}/, tests same domains.

- [x] TDD filter/page/UTC/metric/revenue money serialization and safe audit metadata allowlist; manager A/staffB/staleactor denied before export. Use shared helper APIs above, no pretend repository DB acceptance.
- [x] Implement bound SQL using Task2 contract, tenant-aware JSON/CSV one filter definition, live ResultSet export with cleanup, Redis fallback/TTL/filter scope.
- [x] Implement actual JSON servlets and domain-owned JSP endpoints without replacing owner's AdminServlet/OrganizerServlet; current DB authority before cache/CSV; audit no raw metadata.
- [x] Unit GREEN, SQL-backed/HTTP assertions using dedicated fixture when available; record pending owner E2E/Excel distinctly.

## Task 5: Commission and settlement mutation APIs (root)

Files settlement/commission/{repository,service,controller}, settlement/{dto,repository,service,controller,payout}, JSP WEB-INF/views/settlement/ and admin/commission/, tests same domains.

- [x] TDD real behavior validation, stored replay/different body, event-linked edit rejection, blocker/confirm state, reserve→provider→final/no double call/UNKNOWN pending, fail-closed production provider. Test doubles cover orchestration only, SQL suite covers DB truth.
- [x] Implement SP-backed commission CRUD, recalculation/blockers/confirm, payout history and explicit simulated provider durable sameID semantics outside transaction. Do not use network success simulation in default production.
- [x] Session/current actor/admin/CSRF/idempotency on servlet routes; escaped JSP/hidden tokens/status messages with meaningful failure states.
- [x] Unit/DB/HTTP GREEN, zero/rounding/concurrent reservation/replay/conflict cases, update evidence per issue.

## Task 6: Swagger and whole owned-scope verification (root)

- [x] Reconcile actual implemented route/DTO schemas to OpenAPI, keep absent owner APIs planned; Redis registry actual producers/invalidation, no guessed QR/IPN owner policy.
- [x] Run bare safe unit suite, Node, SQL fixture/procedure permission/concurrency, isolated WAR/HTTP own routes, package/diff-check. Test failures require root cause fix and rerun affected checks; no shared DB/SMTP.
- [x] Commit evidence with exact revisions/case counts and explicit remaining owner/Excel/provider gates. Keep worktree/artifacts; update relevant PRs only as authorized task handoff, no merge without actual team CI gate.

## Execution record

Baseline at integration merge 36e5239: 156 selected Java tests (0 failures/errors/skips), eight Node tests passed. Schema approved by user after RFC; local SQL Server 17.0.1135.8 available through Windows integrated sqlcmd. No database created/modified yet. Peer review is explicitly not awaited, required data contract decision was obtained. Each task gets RED/GREEN and commit evidence; progress tracked in docs/evidence/vuong/VUONG-EPIC.md.
