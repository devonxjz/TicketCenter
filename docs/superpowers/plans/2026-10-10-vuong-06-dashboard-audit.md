# VUONG-06 Dashboard and Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver the ADMIN overview and audit APIs/JSPs from issue #33 on verified dev65645f1, without claiming the separate whole-platform integration gate.
**Architecture:** Reuse the current authorized JDBC services/projections and optional Redis cache; extract only audit/dashboard controllers from integration c09c3a3. Keep report routes/UI intact and add an ADMIN-only page servlet and dedicated audit/dashboard script.
**Tech Stack:** Java25, Tomcat11, Servlet6.1, SQL Server, existing Node tests; no new dependencies.
**Spec:** docs/superpowers/specs/2026-10-10-vuong-finance-integration-design.md; issue #33. Later human authorization permits completed-scope merge into dev and branch cleanup.

## Global Constraints

- UUID/tc_*, integer VND strings, UTC; no Spring or locked model/pom/persistence changes.
- Current ACTIVE/authVersion/ADMIN checked against SQL before every page/API/cache read; no manager access to admin overview.
- Audit read-only; metadata allowlist only, no raw JSON or secrets; stable pagination.
- Tests use dedicated marked loopback fixtures, no shared DB/SMTP/bank calls.
- #28 and the cross-owner E2E part of #33 stay pending until actually proven. Existing owner contracts and unimplemented organization overview stay planned.
- Feature branch feat/vuong/vuong-06-admin-audit-integration; exact-head and post-merge CI required; no deploy.

## Review Focus

- Cached overview must not bypass current role/version checks.
- Unknown or malformed detail IDs and duplicate filters fail before exposing data.
- Changing audit filters or selecting a second detail must discard pending obsolete responses.
- UTC day boundaries and signed cash flow must preserve exact VND beyond Number precision.
- Team audit BIGINT projection and constrained-reader grants must execute on actual supported schema.

### Task 1: Authorized API and page delivery

Files: new audit/controller/AuditServlet.java, dashboard/controller/DashboardServlet.java, dashboard/controller/AdminReadsidePageServlet.java; new dashboard/controller/AdminReadsidePageServletTest.java; swagger/FinanceOpenApiContractTest.java; openapi.json.
Interfaces: consumes FinanceRuntime.database, FinanceAuthorization.requireAdmin, DashboardService.admin, AuditService.list/detail; produces GET /api/admin/overview, /api/admin/audit-logs[/UUID], /finance/overview and /finance/audit.

- [ ] Add page tests for ADMIN forwarding, manager/staff denial, stale session denial and unknown route rejection; update contract test to require only three admin operations implemented. Run selected Maven tests and observe missing controller/contract RED.
- [ ] Extract two API controllers and implement a separate ADMIN-only page controller; update only delivered OpenAPI operations/schema projections and keep organization overview planned. Run tests GREEN.
- [ ] Extend actual HTTP fixture wiring and tests for API permission/redaction/filter/detail/page behavior. Build and run real HTTP/JDBC/JSP; expected zero failures/skips.

### Task 2: Audit/dashboard UI and actual-schema acceptance

Files: audit/audit.jsp, dashboard/overview.jsp, shared report header/footer navigation, assets/js/finance-admin-readside.js; test/js/finance-admin-readside.test.cjs; existing canonical/team JDBC tests where needed.
Interfaces: consumes Task1 APIs and current report options for ADMIN audit filters; produces safe text-only audit explorer, exact-money seven-day chart and current KPI display.

- [ ] Write Node helper/async DOM tests for exact filters, money/chart, stale list/detail/filter races and safe error display; observe missing script RED.
- [ ] Extract/adapt the existing admin UI only, remove inactive organization overview and export/report behavior; update shared footer to choose admin script without changing report script.
- [ ] Run Node GREEN and real canonical/team JDBC tests including audit detail/redaction/order and overview totals/cache authority. Existing implementation regressions are not new TDD work; each new fix requires RED→GREEN.
- [ ] Record actual SQL audit append-only/permission and Redis outage/cache results, labeled fixture scope.

### Task 3: Verify, review and integrate scoped delivery

Files: docs/evidence/vuong/VUONG-06-ACCEPTANCE.md, docs/backend/swagger-runbook.md, PR metadata and issue #33 progress.
Interfaces: consumes Tasks1/2 routes/tests; produces verified scoped PR and truthful pending whole-platform gates.

- [ ] Fresh clean package/default Java/Node, explicit canonical/team JDBC, HTTP and required Redis/SQL race checks; expected zero failures, with guarded skips labeled.
- [ ] One fresh whole-branch review per executing-plans; fix important findings in one RED→GREEN pass.
- [ ] Push scoped PR, attach to chat, pass exact feature CI, merge and pass exact dev CI; update local dev and delete only merged feature branch.
- [ ] Update epic with Dashboard/Audit progress, retaining #33 unchecked until full cross-owner E2E and #28 remain genuinely complete.
