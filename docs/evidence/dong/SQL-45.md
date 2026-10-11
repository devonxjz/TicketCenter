# Event SQL coordination — issue45 / PR56

Updated12 October2026 (UTC+7). Scope: Event command/public Seat contract for
DONG-01–05. Feature delivery is PR51; database changes are kept in this separate
PR. Both now integrate dev af2ded209122d8c5604ae4ccee2e765fefde190e.

## Current additive migration

The unmerged/unapplied Event file was renumbered from030 to
**043_event_commands_and_public_seats.sql**. dev already contains029_hold_runtime
and030_order_runtime; pending PR65/67 reserve031–042. No applied migration was
renumbered or edited. This PR does not adopt those other pending changes.

043 requires canonical baseline/roles, Event image columns from028 and existing
Zone/public views/procedures. It provides:

- PUBLISHED-only vw_PublicEventSeats; buyer has projection access but no private
  Seat/Event/receipt grant or hold/order/customer data in the output.
- Scoped current-user/authVersion/organization authorization, private reads,
  durable command receipts and atomic draft/Zone/Seat/review mutations.
- Canonical SP12 still owns publish; current ACTIVE platform_role is authoritative,
  including revocation with a stale legacy ADMIN row. Rule must cover Event start.
- Private cleanup_json commits with draft deletion/receipt for postcommit journal
  and authorized replay repair; it is never exposed in the HTTP JSON envelope.
- Command/SP12 restore the prior actor context rather than clearing a caller's
  context. Direct SP12 sets audit actor and refreshes time after its Event lock.

Pair with PR51's updated EventManagementRepository: scope each JDBC work item to
the authenticated actor and restore previous SESSION_CONTEXT on every exit.
No user roles/credentials are synchronized or granted by this migration.

## Verified12 October

- Exact043 full-artifact migration test **1/1**,0 failures/errors/skips. Applies
  and reapplies in outer transactions to TicketCenter_Dong_Epic_Test_20261011.
  Receipt count and cleanup-data SHA256 fingerprint are preserved. Restricted
  buyer reads projection; private Event and receipt access is denied.
- Event source on current dev: **16 real SQL/Redis +1 Cloudinary +3 actual WAR
  HTTP =20**,0 failures/errors/skips. Includes restored actor after read/edit/403/
  direct SQL, rollback/races/rights, provider replay, image/delete recovery and
  public Hold handoff alongside Report/overview.
- Safe package: **659 executed pass/183 external guard-skipped** (842 total),
  one unguarded external organization method explicitly excluded; **116 JS pass**.
  These exclusions/skips are not SQL acceptance and common tests were not edited.

Normalized SHA256 (UTF-8, CRLF→LF), exact043 file:

17CBDA78F6DB6C169934ECDA9E6C20D32926AC86FD4F5ED4A5D76E0838110550

The initial helper used a wrong relative path and left only a temporary prefix;
its smoke result is not acceptance. Complete artifact was restored, helper made
fail-fast and smoke strengthened to reject a missing command body; fresh compile/
full-artifact apply/reapply passed. No temporary file was deployed to shared SQL.
Private logs: target/dong-sql-20261012-migration-final.log in this worktree;
Event branch logs/evidence: docs/evidence/dong/DONG-PROGRESS-20261012.md.

## Open adoption gates

1. Independent database-owner review, final migration-number/deploy-order adoption.
2. Combined compatibility with PR65/67 (031–042/RLS/image/actor/routing); current
   tests use the accepted dev baseline and do not prove the entire pending stack.
3. Actual deployed runtime-login/grants and HTTP acceptance. Tests impersonate
   restricted database users over Windows-auth owner transport, not SQL logins.
4. Shared metadata probe12 October returns40615/S0001; shared schema remains
   unverified. No firewall, credentials/grants or shared business data changed.
5. Merge and CI/owned UI acceptance on final dev before closing epic/coordination.

Earlier030 checkpoints remain in Git history; current file/hash/results above
supersede them. Runtime image/JSTL/Cloudinary/Redis prerequisites and reference-safe
provider cleanup remain in PR51's DONG-INTEGRATION.md and RUNBOOK.md. Other domains'
postcommit inventory/cancellation cache hooks are separate owner obligations.

## Reproduce (dedicated fixture only)

Use TC_DONG_ACCEPTANCE=true and the strict loopback Dong Windows-auth URL in
PR51's runbook; test checks exact DB_NAME before mutations and rejects JDBC overrides.

    mvn test-compile -Djunit.jupiter.extensions.autodetection.enabled=false -Dit.test=EventSqlMigrationIT -DargLine="--enable-native-access=ALL-UNNAMED -Djava.library.path=<verified-native-directory>" failsafe:integration-test failsafe:verify

Default green CI does not run this opted-in test. Disable automatic discovery
only for the explicitly listed Dong suites with their own fixture guards.
