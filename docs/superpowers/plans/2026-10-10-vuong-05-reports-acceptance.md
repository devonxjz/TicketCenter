# VUONG-05 reports acceptance implementation plan

> For agentic workers: use superpowers:executing-plans inline, as already selected by the user. Track steps in this file and the plan ledger.

Goal: finish issue#32 reports/CSV independently on dev57afef0, then merge only after verified acceptance.
Architecture: retain the financial read queries, DTO/filter/cache/CSV utilities already merged by PR46. Extract report controllers/JSP from integration c09c3a3; exclude audit/overview routes. Use one validated UTC/UUID/VND filter for paged JSON and unpaged streaming CSV, authorized from current SQL roles before output.
Tech Stack: JDK25, Servlet6.1, Tomcat11, SQL Server, Redis, existing JDK CSV utilities; no new dependency or locked model/pom changes.
Spec: ../specs/2026-10-10-vuong-finance-integration-design.md and ../specs/2026-10-09-vuong-05-safe-csv-design.md. The user approved Native execution, integer-VND UUID/tc_* schema, Excel-tab protection and completed-task merges; this plan resumes that approved work.

## Global Constraints
- Team branch feat/vuong/vuong-05-reports-acceptance from dev; conventional commits and PR to dev.
- Current manager membership/admin version is rechecked before cache/CSV; no cross-organization data.
- UTC [from,to), integer money strings, exact CSV BOM/quotes/CRLF; tab inside quoted dangerous cells per human contract decision.
- Only marked isolated loopback DB fixtures; no shared DB/SMTP/bank writes, publish or deployment.
- Existing helpers are already tested implementation; extraction is integration work, not a rewrite. New behavior/fixes use RED→GREEN.

## Review Focus
- Route/scope mismatch or forged organization: reject before CSV bytes.
- Money/check-in aggregation multiplied by joins: independently seeded SQL expected values, including empty events and confirmed snapshots.
- UI filters change during a request/export: current scope and exact shared filter, bounded server streaming.
- Redis stale authorization or missing transaction invalidation: current DB authorization and source freshness, SQL fallback when Redis unavailable.
- Excel save/reopen and large money auto-conversion: actual harmless formula/Vietnamese tests; explicit Text import instructions for money/IDs; no unsafe formula payload.

### Task 1: expose report routes/pages with one filter contract
Files: report/controller/{ReadsideHttp,ReportServlet,FinanceReadsidePageServlet}.java; report JSP partials/reports.jsp; assets/js/finance-reports.js; report controller tests, report JS tests, OpenAPI and report smoke mappings.
Interfaces: consumes ReportService.json/jsonAdmin/export/options and ReportFilter.parse; exposes three specified report APIs plus options and /finance/reports; other readside operations stay planned.
- [ ] Copy/add controller and contract tests first; run selected tests, observe missing report controller failure.
- [ ] Extract controllers/pages only, restrict organization route to reports and page to /finance/reports; scoped navigation, safe DOM text, validated export scope/format.
- [ ] Prove managerA view/export ofB rejected, admin/scoped reads, filter/metric parity, stale versions, cursor closure on disconnect; keep report public DTO shape in OpenAPI.
- [ ] Run selected Java/Node and commit validated route/UI integration.

### Task 2: close metric, large-data and native Excel acceptance
Files: report/repository/service/DTOs as necessary; report acceptance JDBC test; report smoke script/test; native Excel fixtures/evidence/runbook.
Interfaces: consumes same financial views/UDF and source role checks; produces exact scoped summary/event metrics and unpaged cursor export.
- [ ] Seed independent SQL expectations for summary/tickets/check-in/top revenue or document current metric projection; add failing tests for missing required behavior before code changes.
- [ ] Test10,000orders in128MiB (database aggregate should produce1event row) and10,000event rows streamed without collecting; HTTP download uses real JDBC/servlets/JSP.
- [ ] Export harmless formula and Vietnamese fixtures using actual exporter; open→save→reopen in installed Excel via computer-use, inspect cell formula/value/encoding and document version.
- [ ] If default Excel numeric conversion loses18digit VND, keep exact CSV bytes and verify Text import, documenting the user-facing import instruction.
- [ ] Run regression/actualSQL/HTTP/Redis appropriate to changes and commit evidence.

### Task 3: integrate verified task
Files: docs/evidence/vuong/VUONG-05-ACCEPTANCE.md, scoped CI configuration and PR metadata.
- [ ] Independent whole-branch review per executing-plans; fix important findings via reproducing tests.
- [ ] Fresh clean package/default Java/Node, explicit canonical/team SQL tests and required SQL races, actual Redis and isolated HTTP; record skips accurately.
- [ ] Push PR to dev and run expanded CI on exact head, with publish/deploy off; merge only if all enabled jobs pass and actual GitHub protections permit.
- [ ] Run expanded CI on exact merged dev, update main checkout clean dev, record evidence; retain #28/#33 pending scope separately.
