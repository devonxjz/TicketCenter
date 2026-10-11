# SQL45 — Event command and public Seat integration

Refs #45 and Event feature PR51. This is a separate schema coordination change,
not part of the Event feature PR. Existing migrations, locked models, POM/config,
OpenAPI and other owners' business code are unchanged.

## Result

New migration030 adds a PUBLISHED-only Seat projection for tc_buyer, scoped Event
read/authorize/command/receipt procedures and a durable atomic command-receipt
store. Manager/admin execute rights are limited to those procedures; ownership
chaining supplies their table access. Idempotency keys use BIN2, matching Redis
case sensitivity. Retention policy requires rollout agreement; no purge job is
claimed. The migration does not change user roles or seed production users.

Canonical usp_PublishEvent remains the only publication path. Its authority now
matches the current ACTIVE tc_users.platform_role used by login/Event access,
rather than legacy tc_user_platform_roles. It checks approved organization and
same-org commission validity both now and at Event start. Existing lifecycle,
short transaction/savepoint behavior, seat prerequisites and status audit remain.
No other legacy procedure is rewritten.

## Order and rollout

1. Verify canonical baseline001–008 and existing027/028 adapters/image workflow.
   Migration030 preflight rejects a missing Event image column/view/procedure or
   runtime role before the Event additions.029 is reserved by customer refund PR50.
2. Database/platform owner reviews this PR and the role-authority change. Execute
   GO-separated batches using a fail-fast migration runner within an outer
   transaction; rollback the entire run on any error. Never rewrite older files.
3. Verify buyer public Seat access and manager/admin procedure execution using
   the actual deployment logins, current role revocation and tenant boundaries.
4. Deploy Event PR51 only after migration030. Adopt its DONG-INTEGRATION.md API,
   Redis and postcommit owner hooks, then run acceptance on the integrated head.

No shared/production DB migration or role grants have been executed by this task.
Shared deploy/merge still needs approval and independent review. Baseline and
schema-only team layouts differ in audit columns: this version targets the
canonical baseline validated by Dong, not an untested legacy physical layout.
Run the guarded preflight and schema review before adoption; do not claim legacy
team compatibility merely because the finance adapter has been installed.

## Verification — 11 October 2026, UTC+7

- Applied migration030 and reapplied it atomically only to
  TicketCenter_Dong_Epic_Test_20261011 on local SQLEXPRESS. Existing durable receipt
  count was preserved; restricted dong_buyer could select vw_PublicEventSeats.
- Same SQL proposal exercised through Event PR51/current dev7541a65 integration:
  13 real SQL/Redis,1 real Cloudinary and2 full-WAR HTTP tests passed, no skips.
  Includes rollback/concurrency/current permissions, cross-org access, seat
  inventory, publish/reject, public cache refresh and multipart/overview coexistence.
- Current ADMIN fixture has no legacy role row. New regression publishes with
  current role only, then revokes it while retaining a stale legacy ADMIN row:
  Event service403 and direct canonical SP51520, no authorization by legacy row.
- Safe regression package:383 Java executed pass/60 guarded external skips,
  56 Node tests pass plus7 URL guard tests pass. No SMTP messages sent.
- SQL tests use restricted database-user impersonation over Windows-auth owner
  transport; these are not separate production SQL-login grant tests.

Event reproduction: docs/evidence/dong/RUNBOOK.md in PR51. Tests are opted in via
TC_DONG_ACCEPTANCE, dedicated loopback DB-name guard and Cloudinary acceptance.
Fault-only storage cases are labeled fake; provider/HTTP cases use real Cloudinary.
The initial retest attempt failed from launching the SQL bridge with PowerShell5;
correcting the helper to PowerShell7 and restarting the dedicated WAR resolved
fixture startup. No acceptance failure was silently skipped.

Migration030 normalized SHA256 (UTF-8, CRLF normalized to LF):
4E9F9997C26C9D59A0144522DDBC4B1703D8726DDAD6D72431F3F5CB2DA37C4D

A read-only metadata probe of the configured shared database returned SQL40613
(database unavailable); no shared compatibility/grant acceptance is claimed.

Separate branch test: EventSqlMigrationIT passed1/1 with no skips, applying and
reapplying the exact file in outer transactions, preserving receipts, allowing
buyer projection reads but denying private Event tables/receipts, and checking
that all projected inventory belongs to PUBLISHED Events. The initial test
incorrectly attempted its owner integrity join as buyer and received SQL229;
the corrected test preserves that denial and checks integrity after REVERT.

Reproduce after the dedicated fixture/users are provisioned from PR51:

    mvn test-compile -Djunit.jupiter.extensions.autodetection.enabled=false -Dit.test=EventSqlMigrationIT -DargLine="--enable-native-access=ALL-UNNAMED -Djava.library.path=<verified-native-directory>" failsafe:integration-test failsafe:verify

Set TC_DONG_ACCEPTANCE=true and TC_DONG_SQL_URL to the explicit loopback Dong
Windows-auth URL documented by PR51. This test rejects JDBC redirection/extra
properties and verifies actual DB_NAME before mutations. Ordinary CI does not
run this opted-in test automatically; default green jobs are not SQL acceptance.

## Thorough review update — 11 October 2026

Integrated dev3e380bf (PR55). Reviewed migration030 now stores private cleanup_json
atomically with draft deletion and receipt, so postcommit journaling can be
repaired by authorized replay without deleting a referenced asset or exposing
cleanup metadata in HTTP JSON. Existing command receipts get a nullable column;
image-workflow receipts and existing migrations are unchanged. Pair SQL's fifth
internal result column with updated Event PR51 caller during adoption.

Two additional real SQL regressions reproduced unknown category409 and malformed
DETAIL JSON under list page/status filters. Category existence validation now
returns400 with no receipt/change; detail ignores list filters and returns404 if
its target disappears. Canonical SP12 remains current ACTIVE role based.

Final acceptance on Event PR51 tree:385 Java executed/60 guarded skips;59 Node,
18 URL/native handoff guard checks;15 SQL/Redis+1 Cloudinary+3 full-WAR HTTP=19,
all executed checks pass. The19 run supersedes earlier16/17 checkpoints. Focused
migration test1/1 preserves both receipt count and a SHA256 fingerprint of the
private cleanup metadata across atomic reapplication, and verifies buyer public
projection/private table denial. Fixture cleanup1/1 passes after reference checks.
Initial RED outputs and fixture limitations remain documented in Event PR51's
DONG-REVIEW-20261011.md; no acceptance failures were silently skipped.

Shared metadata read now returns SQL40615; actual deployed schema/runtime logins
remain unverified. No shared firewall, credentials, grants or database were
changed. Independent review and actual-schema adoption still gate shared rollout.
