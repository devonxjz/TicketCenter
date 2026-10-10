# VUONG implementation and handoff — 2026-10-10

Branch `feat/vuong/vuong-epic-finance-integration`; PR target `dev`. Original base `8d5828a`, groundwork `36e5239`, read-side commits `8e3b85a`/`03cc139`. Final revision is the PR head; worktree is preserved for iteration.

Handoff: draft [PR #42](https://github.com/devonxjz/TicketCenter/pull/42), attached to the Codex task. Implementation `4f8cf5d`; latest teammate `dev`63643d5 merged into feature at `5fa7d74` with only the three Docker files changed and no conflicts. Post-merge Maven tests/package passed. Final WAR SHA256 `F4F0992820631974792EE13938BFC61244DA4010BA4610D38889B34AD5828D7E`. GitHub Actions runs/checks on the PR provide separate remote evidence; no reviewer reply is awaited, and the PR remains unmerged.

The user approved UUID/tc_* and whole VND, isolated test schema/additive migration, continuing without peer-review waits, and local SQL Server TCP enable/restart. Team merge gates and missing other-owner acceptance remain separate.

## Delivered owned scope

| Issue | Delivered | External acceptance still required |
|---|---|---|
| #28 | Actual/planned/legacy OpenAPI catalog, finance DTOs, session/CSRF/idempotency, safe tests/CI, actual Redis registry | Other owners' absent endpoints/contracts and full catalog/E2E |
| #29 | Commission list/create/edit, durable replay/conflict, event-linked immutability, ADMIN authorization, escaped real JSP selectors, TTL300 cache | InitialPolicy after-commit invalidation |
| #30 | Exact accepted-payment/order snapshot, blockers/recalc/UTC/zero cases, summary/workbench, TTL60/30 caches | Payment/refund/cancellation operational integration and invalidation |
| #31 | Confirmation freeze, partial payout, durable reservation/fingerprint/replay, UNKNOWN/crash recovery, history reconciliation with `{}`, explicit simulated provider outside transaction | Production bank adapter/actual bank acceptance; default fails closed |
| #32 | Four report metrics, exact manager scope, shared UTC filters, streaming CSV/cursor cleanup, BOM/approved quoted TAB formula defense, scoped TTL60 cache | Native Excel open-save-reopen and owner invalidation |
| #33 | Actual admin/manager overview, users/APPROVED organizations, exact seven UTC days and separate revenue/cash flow; authority before TTL60 admin cache; audit actor/table/org/event filters, safe metadata, append-only protection | Full register→event→hold→pay→check-in→settlement E2E and team CI |

#34 tracks the epic; these results do not establish completion of every owner's 34 issues. No issue is automatically closed. Draft PRs #37/#40 are preserved; their groundwork is included here.

## Actual final checks

Java25/Maven3.9.16, SQL Server17.0.1135.8, Tomcat11.0.25, standalone Redis7.4.2. Results below come from executed checks.

| Check | Result |
|---|---|
| Bare Maven tests | 286 discovered; **243 executed**, 43 guarded skips, zero failures/errors |
| Actual JDBC selection | **19 passed, zero skips**: finance7, report/dashboard8, audit scope4 |
| Node | **29 passed**: Swagger initializer8, report summary6, read-side9, mutation UI6 |
| SQL assertions | **Five suites passed**: contract, guardrails, evidence guardrails, temporal, permissions |
| SQL concurrency | **Six real two-session races passed**, reservation/replay, refund/evidence/confirmation, rule attachment/edit |
| Actual Redis | **One passed, zero skips**: hit, TTL300/60/30, generation invalidation, overview deletion, fallback after actual server shutdown |
| Finance HTTP/JSP | **14 passed**: real JDBC/session/CSRF/RBAC, writes/recovery, four metrics, CSV bytes, audit filtering/redaction, daily overview and five owned pages |
| Swagger HTTP | **10 passed**, ROOT and /ticketscenter, actual public login unavailable-DB and protected CSRF behavior |
| WAR/static | Package succeeds; test session bridge/private fixture files excluded; workflow actionlint and whitespace checks pass |

Default skips: 23 legacy DB/SMTP +19 actual owned DB +1 explicit Redis. Enabled results do not count skipped legacy tests as successful acceptance. Default tests start no workers/SMTP and send no mail. DB/Redis CI jobs require marked disposable fixtures, actual suite execution and zero skips; local evidence does not prove a GitHub Actions run. Locked pom/model/persistence/026 are unchanged.

Selective HTTP deployment uses real production API/JSP classes and a controlled test-only session bridge. It excludes persistence listeners/seeders/workers/SMTP, so it proves owned HTTP behavior rather than full startup or another owner's authentication flow.

## Repaired behavioral regressions

- Procedure result consumption drains through COMMIT/late errors. Public errors use fixed codes, never arbitrary SQL/bank/credential text.
- Every durable PENDING reservation blocks a new payout ID. A crash after provider success cannot create a second transfer; reconciliation uses the exact stored settlement/payout identity and immutable terms, and only looks up the result.
- Confirmation freezes current customer-refund attempt and transfer evidence. Both evidence/confirmation lock directions are tested; nonaccepted captures/compensation remain valid owner paths.
- UUID projection normalizes only UUID identifiers; provider references retain case. Money stays BigDecimal/integer strings/browser BigInt.
- UI locks event/forms until mutation and refresh finish; PENDING history provides recovery. JSP selectors/text escape without adding absent JSTL.
- Bound audit joins recover settlement/transfer ownership. Event intersection excludes organization-wide rule history. actorId is UUID; targetTable is an allowlisted alias, never dynamic SQL.
- Current DB authority precedes every cache hit. Mutations invalidate only after commit. Today's cohort gross revenue/commission are distinct from cash flow; seven real UTC days include zero days.

Observed RED/GREEN includes payout interruption, five SQL evidence guards, confirmation/evidence race, UUID case, JSP/UI behavior, audit scope, and today's gross KPI (100 expected versus missing before repair). Doubles prove orchestration; actual JDBC/SQL/HTTP/Redis prove the corresponding runtime behavior.

## Environment and retained evidence

Only dedicated TC_Vuong_Test_* databases with vuong-finance-v1 marker were used. Production027 validates prerequisites; fixture001 is test-only. No existing/shared database was replaced/dropped. Run SQL races, JDBC and HTTP sequentially against one fixture: an overlapping HTTP seed became an expected deadlock victim of intentional race locks; its subsequent isolated rerun passed.

User-authorized TCP uses ListenOnAllIPs=0, 127.0.0.1/::1 only on1433, running service, no firewall changes; listener inspection reconfirmed. An initial settings backup under ignored target was subsequently cleared by Maven clean; do not claim it remains. TCP SSPI failed; shared-memory integrated sqlcmd works. Java uses private fixture-only SQL credentials with no server role.

Private config/logs now live outside build outputs under %LOCALAPPDATA%/TicketCenter/fixtures and /verification. No secrets are committed/printed. A clean run whose log was under target failed on Windows after clearing scratch data; configuration was privately recovered and final checks rerun. Keep configuration/logs outside target.

Final logs: finance-final-default/db/node/redis/http/swagger-http/package.log, five finance_*.log SQL transcripts and finance-final-races.log. Race directory is reported in the last log. RED logs are retained separately. Credential-bearing Surefire properties remain ignored. Redis7.4.2 official source was compiled in an isolated WSL cache, loopback16379/no persistence/no autostart; the marked test shut that standalone server down. No shared Redis service, Docker daemon or real bank transfer is assumed.

## Contract changes and remaining gates

Approved UUID/integer-VND/UTC millisecond [from,to), canonical states and envelope DTOs supersede inconsistent numeric-ID/decimal-cent issue examples. activeOrganizations counts APPROVED organizations. Cohort revenue and cash flow use explicit independent fields/time bases; no synthetic HEALTHY status is returned. Audit output uses canonical entityType/entityId and safe structured metadata; targetTable aliases are accepted for filtering. Exact routes/DTOs are in OpenAPI/runbook.

CSV deliberately uses the approved Excel-first TAB inside a quoted cell rather than apostrophe-only defense. BOM/quoted bytes were tested; native Excel persistence acceptance is still separate.

Redis fallback and failed best-effort invalidation can leave an authorized read stale until its bounded TTL. Other owners need after-commit hooks. Financial writes/current permission checks never depend on cached money/authority.

Handoff is a PR into dev without reviewer-response waits. Team review/green Actions remain the merge gate. No merge/deploy/publication, external-gated issue closure, mail or actual bank transfer is performed.
