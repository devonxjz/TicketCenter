# Cross-domain acceptance ledger

Date: 2026-10-12, Asia/Bangkok. Base `af2ded209122d8c5604ae4ccee2e765fefde190e`.
PR50/61/65 are integrated locally with additive runtime compatibility fixes.

## Reproductions and corrections

- QR key/metadata and report route regression: 12 tests initially produced one
  failure and two errors; the first correction run passed 24/24.
- Restricted payment callback/free checkout: initial SQL run had two errors;
  corrected repository/principal/projection run passed 5/5 including PNG/config.
- Forwarded actor propagation: 5 route tests initially had two assertion failures;
  scoped actor restoration plus QR/payment regression passed 29/29.
- Actual hold facade creation failed before 041 (three-vs-five parameter contract).
  Expired-hold replacement also requires nested-result suppression.
- Synchronized Event/User locking initially produced a real SQL deadlock victim;
  corrected 041 lock ordering passed the same two-connection reproduction.
- Full facade test exposed UTC expiry interpretation on a UTC+7 runtime;
  explicit UTC JDBC timestamp reads made checkout/payment succeed.
- Fresh whole-branch review found scanner User → Event inversion against buyer
  Event → User. A real restricted CHECK_IN two-connection reproduction deadlocked
  before additive042; the corrected suite passed afterward.
- First CI compile rejected duplicate JSTL dependencies inherited from combined
  dev changes. Keep one runtime API3.0.1 and implementation3.0.1 declaration,
  matching the effective Native-tested dependency and pinned Tomcat helpers.

## Fresh local results

- 40 SQL/QR tests executed, zero skips/failures/errors: PaymentCrossDomainSqlTest7,
  SqlReportSafetyIT16, TicketCheckInSqlServerTest14, TicketQrInteroperabilityTest3.
  Includes 1,000 hold attempts, concurrency, replay, refund and principal boundaries.
- 157 JavaScript/Native guard tests executed, zero skips/failures.
- Full package: 771 discovered, 667 executed, 104 guarded external skips, zero
  failures/errors. Skips are not counted as passes; dedicated SQL acceptance ran
  separately as above.
- Full annotation WAR on Native Tomcat11/JDK25: 17 HTTP checks executed, zero
  skips/failures. Real identity/session/CSRF and retained finance pages passed;
  that smoke fixture uses its owner login and does not prove restricted full HTTP.

Native SQL uses only `TicketCenter_Khanh02_Test` on loopback1433. Buyer/worker
boundaries are tested with database-user impersonation over owner transport;
check-in JPA uses its restricted SQL login. These are not proof of every deployed
login configuration or full HTTP callback authentication. No shared/Azure DB or
real provider is mutated.

Private logs live under the user's local TicketCenter verification directory;
protected credentials and raw private fixture files are not committed.

## Review rulings and limits

- Retain multiple real CAPTURED receipts and compensation: forbidding a second
  capture would lose evidence of real money; risk if wrong is missed compensation.
- UNKNOWN stays unresolved until provider evidence: inventing FAILED after aging
  could allow unsafe retry; risk if wrong is longer reconciliation wait.
- Preserve existing finance adapter projections: replacing them would break
  accepted owned finance screens; full financial acceptance still needs provider
  and teammates' final business flows.
- 041 adds an actor application lock before idempotency keys to align direct hold,
  retained hold facade and refund paths. It serializes same-buyer mutations; cost
  is reduced concurrency for one buyer, without serializing unrelated buyers.
- Deduplicate JSTL at3.0.1 instead of retaining conflicting3.0.2/3.0.1 declarations;
  cost if wrong is a missing newer JSTL behavior, covered by full WAR JSP checks.
- Defer retained finance ADMIN/raw connections and unscoped refund child SQL views.
  HTTP refund detail checks ownership; do not claim independently scoped child
  projections or a global physical connection cap from checkout budget tests.
- Defer SQL display labels beyond 200 characters; admission accepts maximum valid
  issuer metadata but display text may truncate.
- Physical camera, real gateway, workers, Khánh04–06, complete OpenAPI and all24
  product screens are not accepted here. Whole #28/#33 and the epic remain open.

## Delivery

Fresh scan/refund/SQL reviews and a whole-branch review completed. Important
findings were corrected with observed RED→GREEN and passing SQL/Native suites.
Exact-head CI, guarded merge and postmerge checks are recorded by the PR and
the scoped epic update; no final module count increase is implied here.
