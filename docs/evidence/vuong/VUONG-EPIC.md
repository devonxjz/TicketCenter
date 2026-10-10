# VUONG epic implementation ledger

Plan: `docs/superpowers/plans/2026-10-10-vuong-finance-integration.md`.
Integration base: origin/dev `8d5828a`; groundwork merged at `36e5239`; approved plan `0f32844`.
User approved UUID/tc_* whole VND, dedicated test schema/new financial migration, continued work without waiting peer review. On 2026-10-10 user additionally approved local SQL Server TCP enable/restart; initial UAC cancelled accidentally, retry explicitly authorized.

## Task 1: shared infrastructure

Commit `a7710e1`: explicit map/list/scalar serializer, exact money/UUID/UTC JSON, rejection of entities/floating values/invalid Unicode/NUL; current ACTIVE/authVersion/platformRole and exact active organization manager authority.
Observed missing-class RED then GREEN: FinanceJsonTest five tests, FinanceAuthorizationTest five tests, zero failures/errors/skips. JDBC doubles cover authorization bindings only; later real JDBC tests prove actual stale-session refusal.
Connection factory is lazy DriverManager, no startup connections/workers and no retained connections requiring lifecycle close.

## Task 2–4: independent implementation

Canonical SQL, isolated test/CI policy and report/audit/dashboard use independent attached worktrees with disjoint owned files. Integration commits/results recorded when delivered. No remote deploy, merge or peer-review wait.

## Task 5: mutations and UI, in progress

Observed RED→GREEN for payout orchestration (six tests including provider-unavailable stored replay), JDBC public error projection (two), request validation (four), unauthenticated/CSRF/unknown servlet routes (three). Current selected pure group: 25 passed, zero failures/errors/skips.
Real JDBC FinanceRepositoryIntegrationTest: five passed, zero failures/errors/skips. Covers durable commission replay and event-linked edit lock, exact recalc (100 revenue,7 fee,93 net), confirmation replay, partial payout40/remaining53, one durable simulated provider record/call_count1, timeout after durable provider effect retains UNKNOWN/pending40, stale authVersion refusal, no-paid-order no header, DB-backed UI selectors and UTC event end.
These tests create randomized records only in `TC_Vuong_Test_20261010_Finance`; retained fixture data is not production data. Test-only provider results never prove an actual bank transfer.
JDBC result draining goes through the procedure COMMIT and late SQL errors. Public errors never copy arbitrary SQL messages, bank payload or credentials.
Servlet routes use session, fresh DB authority, POST CSRF, canonical UUID Idempotency-Key, bounded JSON including unknown-length bodies, strict whole-VND strings and millisecond UTC dates. Payout provider unavailable by default; explicit fixture simulation enforces loopback DB URL and fixture marker. Admin reconciliation queries stored simulator result and never invokes transfer again.
Pages `/finance/commission` and `/finance/settlements` use actual DB selectors, escaped JSP text, external script/local style, meaningful blockers/amount/history. API/JSP/network smoke and cache integration remain verification work.

## Local SQL/JDBC environment evidence

SQL Server network settings backup retained in ignored `target/sql-tcp-settings-before.json`. Authorized elevated script enabled TCP with ListenOnAllIPs=0 and loopback IPv4/IPv6 only on1433; service restarted, no firewall changes. Listener inspection confirmed `127.0.0.1` and `::1` only.
Native Windows authentication over TCP failed SSPI; shared-memory sqlcmd remains available. Dedicated randomly generated fixture SQL login has db_owner only in the isolated fixture and no server role. Secrets retained in ignored local target configuration, never committed/reported. JDBC connected and read `vuong-finance-v1` marker; no server-level performance permission was granted.

## Remaining acceptance gates

Full default safe suite, SQL permissions/concurrency, real servlet/network smoke, report/audit integration, Redis fallback/invalidation, OpenAPI reconciliation and WAR package evidence remain in progress. Other owners' absent business endpoints, actual bank provider and native Excel open-save-reopen acceptance remain explicit external gates. Do not close all issues or declare cross-owner E2E complete from these results.
