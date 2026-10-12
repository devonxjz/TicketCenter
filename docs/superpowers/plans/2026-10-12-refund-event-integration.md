# Refund and Event integration implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Native execution is already authorized; complete one fresh whole-branch review before merging.

**Goal:** Integrate ready Admin Refund and Event SQL contracts with current dev and prove the supported cross-domain flows without claiming final-product acceptance.

**Architecture:** Preserve the existing Servlet/Service/Repository boundaries and SQL-owned transactions. Work from dev80079d11504f136b32c8333a65aac74707e9b992; incorporate PR56 and PR68 commits with additive compatibility changes. Use explicit migration identifiers/order and isolated databases; never reapply older definitions over later contracts.

**Tech Stack:** Java25, Tomcat11, Jakarta Servlet6.1, Hibernate/JDBC, SQL Server, Redis, Maven, Node.

**Spec:** `docs/backend/2026-10-12-cross-domain-runtime-contract.md`; assigned issues #28/#33; PR51/#56 Event handoff and PR68 Admin Refund contract. Human approval: execute the previously proposed integration work, with existing authorization to merge only verified work and update scoped epic checklists.

## Global constraints

- UUID identifiers, `tc_*` schema, integer VND, explicit UTC timestamps.
- Preserve installed migration bytes/identifiers and role boundaries. No shared SQL/provider business mutations during acceptance.
- SQL is authoritative; actor/current role/authVersion validation precedes reads/replay and committed changes precede cache invalidation.
- Skipped infrastructure cases are reported separately and never counted as passed acceptance.
- Keep #28/#33 open unless their complete remaining gates are actually verified; whole-task progress remains4/6 meanwhile.
- Reuse the attached worktree and preserve ignored protected configurations/evidence. Delete only the owned merged branch.

## Review focus

- Two historical043 identifiers and a pending Event043: installation order and checksums must remain explicit; migration prefix alone is not the ledger key.
- Event publication, buyer checkout/payment/tickets and Admin Refund must coexist under their restricted principals, including revoked/cross-owner access.
- Event/refund changes must not overwrite finance projections or restore invalid tickets after cancellation.
- Actor context on forwards/JDBC replay, Event-before-User locks and actual UTC values must survive combined changes.
- New dev changes require fresh combined SQL/Redis/HTTP validation; individual PR CI and owner evidence are insufficient for the merged revision.

## Task1: Adopt ready contracts without changing installed history

**Files:** `database/migrations/043_khanh04_admin_refund.sql`, pending Event migration adopted as `database/migrations/045_event_commands_and_public_seats.sql`, existing Event migration test/setup references, `src/test/java/vn/ticketscenter/database/SqlReportCiSetup.java`, Admin Refund fixture/setup and deployment documentation.

- [x] Verify clean latest-dev baseline and freeze PR56/68 heads.
- [x] Merge source commits into the owned integration branch; resolve only actual compatibility conflicts.
- [x] Preserve merged043_coupon_admin and044_buyer_order_history_views and already installed043_khanh04_admin_refund. Renumber only the pending Event artifact to045; retain original history in the merge ancestry.
- [x] Specify install order by complete migration identifiers; add Event/refund to the combined isolated fixture after040–044. Check actual ledger uses complete basename, not the numeric prefix.
- [x] Run actual apply/reapply checks and assert previously installed projections/receipts and role denials remain intact. Treat fixture connectivity/config failures separately from application failures.
- [ ] Commit adoption and record result/deviations in the evidence ledger.

## Task2: Prove combined restricted SQL and cache behavior

**Files:** combined database integration tests, Event SQL fixtures, `.github/workflows/sql-report-safety.yml`, `.github/workflows/khanh04-admin-refund.yml`, additive integration workflow if needed.

- [x] Read existing hold/order/payment/ticket and Admin Refund tests and extend a meaningful actual linked regression to cover their shared data/state and settlement blockers where the retained contract supports it.
- [x] Watch any added regression fail for the actual missing contract/behavior before implementing the minimal correction.
- [x] Execute combined payment/role/check-in SQL cases, actual Event SQL cases and Admin Refund races/replay/rollback cases in explicitly owned isolated fixtures.
- [x] Execute real Redis cases using a dedicated disposable process; no shared FLUSH, unknown-process termination or provider transfers.
- [ ] Require the actual opted-in suites with zero skips in CI; default guarded suites remain separately reported.
- [ ] Commit any demonstrated compatibility fixes and ledger RED→GREEN evidence.

## Task3: Reconcile delivered API contracts and full application acceptance

**Files:** `src/main/resources/openapi.json`, Redis registry/runtime contract docs, Native and owner HTTP acceptance harnesses, `docs/evidence/vuong/VUONG-REFUND-EVENT-INTEGRATION.md`.

- [ ] Compare delivered Event/Admin Refund response/route/authority contracts against existing OpenAPI; preserve planned operations unless implemented and tested.
- [ ] Build/test the complete combined WAR; run Native guards and real-login finance HTTP plus restricted Admin Refund HTTP/browser checks.
- [ ] Exercise public Event/checkout entry points alongside report/overview where runtime configuration permits; distinguish isolated restricted-user impersonation from actual runtime logins.
- [ ] If Cloudinary/provider/runtime credentials are unavailable, record that exact remaining gate without inventing a pass or weakening existing tests; finish all independent SQL/application checks.
- [ ] Document exact migration order, baseline/heads, actual counts, role/login scope and remaining #28/#33 gates.
- [ ] Commit scoped evidence and completion checklist.

## Task4: Review, exact-head CI, merge and postmerge checks

- [ ] Dispatch one fresh whole-branch review per executing-plans/requesting-code-review; fix Critical/Important findings with reproduced regression evidence and record any rulings/minors.
- [ ] Create/attach the owned integration PR. Run combined mandatory acceptance and expanded SQL/Redis/Native/HTTP/build/security CI on its exact head, with publish/deploy disabled.
- [ ] If dev advances, integrate it and repeat only affected checks before merging.
- [ ] Merge only the reviewed green revision; update local dev and run required postmerge acceptance on the exact merge SHA.
- [ ] Append only accepted slices to owned #28/#33/#34, preserving open state and4/6 whole-task completion.
- [ ] Delete the owned merged branch; preserve worktree/private fixture data. Report material remaining gates and ledgered rulings.

## Execution ledger

- Setup: reused attached `vuong-finance-ready` worktree on `codex/vuong-refund-event-acceptance`; main dev fast-forwarded to80079d1. PR51 already merged by the team, so it is baseline rather than a duplicate merge task. PR68 current head7d5358bbd9dba63f0fdd0fb07815b032ecd0104b; PR56 head5ece780ba4d0df12252af8abe958d529ec634f05.
- Ruling: preserve both already adopted043 domain identifiers; ledger keys are full basenames and checksum assertions. Renumber only pending Event to045. Cost if wrong: rollout-order reconciliation is required, never rewriting installed history.
- Task1/2 evidence: clean baseline823/718 executed/105 guarded; linked SQL1 RED (missing EventRead)/GREEN, connection guards5 RED compilation/GREEN, Event18/18 and combined SQL/Redis58/58 without skips. Own fixtures and DPAPI-protected credentials recorded outside Git. WSL Redis is owned PID423 on16379, private pidfile `/tmp/tc-vuong-refund-event-20261012-a/redis.pid`.
- Task3 evidence: Event Swagger3 cases RED,8 combined Swagger cases GREEN; complete package871/749 executed/122 guarded with0 failures/errors; full Admin/public Event/actual BUYER WAR47 HTTP +12 browser passed. Native clean removed ignored browser artifacts, so preserve regenerated screenshots/JSON outside target before further cleans. Native financial smoke exposed the obsolete Event POST415 expectation; current JSON validation is400/INVALID_EVENT_INPUT, unsupported media415/CONTENT_TYPE_INVALID at the shared media gate.
- Fixture corrections: SEATED capacity NULL and required seat label fixed after constraint inspection; Windows TCP SSPI fails, so SQL transport is explicit/guarded. Local Tomcat conf initially imported unrelated exercise descriptors; its owned JVM stopped, fixture now excludes them and emits the same minimal NIO2 host as established Native smoke. IPv4 flag alone did not resolve the NIO selector-pipe failure; no production network policy changed.
- Native isolated financial WAR17/17 passes after matching the delivered JSON400/INVALID_EVENT_INPUT and shared media415/CONTENT_TYPE_INVALID contract. Separate Event Redis PID672 on16479 (pidfile `/tmp/tc-vuong-event-20261012-b/redis.pid`); Scan/Admin PID423 on16379; Native financial smoke uses closed16480 for SQL fallback. Default Native serve configuration is preserved; this does not prove production seed/cache invalidation.
