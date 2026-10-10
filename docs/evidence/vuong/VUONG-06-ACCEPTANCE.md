# VUONG-06 Dashboard/Audit acceptance — scoped delivery

Issue [#33](https://github.com/devonxjz/TicketCenter/issues/33); base verified dev65645f1. This delivers ADMIN dashboard and audit, not the final cross-owner E2E gate. #33/#28 remain unchecked on the epic until the remaining gates pass. Human authorization permits completed-scope merge without waiting for peer review, subject to actual GitHub protections, and merged branch cleanup. No deployment/shared DB/bank/SMTP action.

## Delivered behavior

- `/finance/overview` and `/api/admin/overview`: actual database KPI counts, today UTC cohort revenue/commission and separate cash flow, seven UTC chart days including zeros. Exact money strings/BigInt; negative cash flow below zero. Optional Redis TTL60/fallback; current ACTIVE/authVersion/ADMIN checks precede cache reads. Owner cache hooks still require integration; no whole-owner invalidation claim.
- `/finance/audit`, `/api/admin/audit-logs` and detail `/{auditId}`: ADMIN only, required UTC `[from,to)`, pagination and action/actor/targetTable/entity/organization/event filters. Stable createdAt DESC/id DESC. Fixed table aliases and typed scalar metadata allowlist, no raw/nested/secret JSON. Commission percentages retain four decimals separately from integer VND. Team BIGINT audit projects to stable UUID without rekeying history.
- Pages independently check current ADMIN. Managers/staff retain their verified report scope; organization overview remains planned.
- Dedicated admin script preserves the report script. Filter edits discard pending rows/details; selecting a second detail rejects the first's late response. Organization selectors survive event bubbling. Text-node rendering and fixed errors prevent server HTML/SQL exposure. Navigation connects the delivered finance pages.
- Existing migrations027+028 are prerequisites. No owner model/pom/persistence/schema changes.

## Observed local verification — 10–11 October 2026

- Fresh clean package: Java359 discovered/300 executed/59 named guarded external skips, zero failures/errors. Skips are not acceptance passes.
- Full safe Node regression45/45, including13 admin UI tests. Controller activation, 404/planned contract, dropped fractional commission rates, text-input staleness and scope-event bubbling were observed RED before fixes.
- Actual canonical JDBC23/23, zero skips: current SQL authority before cached overview, exact UTC metrics, scoped/redacted audit.
- Actual schema-only team clone JDBC11/11, zero skips under128MiB: real team overview counts/commission, revoked ADMIN denial, BIGINT audit projection, constrained grants and existing report regressions.
- Actual isolated Tomcat/HTTP/JDBC/JSP18/18, zero skips: finance/report regression, overview JSON, audit list/detail/filter/redaction/invalid inputs, absent session and manager/staff denial, JSP rendering. Test-only session bridge; not owner login/full-WAR/worker acceptance.
- Actual UPDATE and DELETE rejected on both marked audit fixtures; history counts preserved. Canonical trigger52709 and existing team trigger51110 were observed. The team trigger was preserved; the private test expectation was corrected instead of changing production errors.

## Remaining acceptance

Real owner authentication, full owner schema/startup and registration → event → hold/order/payment → ticket/check-in/refund → settlement/report/audit E2E still need a dedicated environment and actual evidence. Reconcile OpenAPI/Redis producer contracts and owner invalidation hooks. Simulated payout/test sessions are not proof of those flows; disabled owner/deploy jobs are not passes.

One fresh whole-branch review, exact-head expanded CI (SQL suites/races/actual Redis) and post-merge expanded CI are required. Final commit/run links are recorded in PR metadata; prior PRs are not proof. No automatic issue closure.
