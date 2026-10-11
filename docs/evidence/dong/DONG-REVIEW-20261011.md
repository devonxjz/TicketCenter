# Dong thorough review — 11 October 2026 (UTC+7)

Reviewed Event PR51 and SQL coordination PR56: caller/receipt contract, current
privileges, lifecycle/rollback, cache invalidation, multipart routing and admin
UI response ordering. Both integrate dev3e380bf (PR55). No manual changes to other
domains, locked models/POM/config/OpenAPI or applied shared migrations. Migration030
remains a draft coordination change, not a shared deployment.

## Reproduced findings and corrections

| Finding | Before fix | Verified correction |
|---|---|---|
| Concurrent cache retry loss | An older successful scan cleared the flag after a newer failure; stale JSON returned. | Requested/completed epochs acknowledge only successful work. Controlled two-flight regression keeps SQL fallback after the newer failure. |
| Stale admin review selection/policy | Slow EventA detail or layout/rules overwrote EventB; decisions stayed enabled while loading.3 DOM tests failed. | Revision guard publishes the active detail/layout/rules together; decisions lock during loading and retain the clicked Event through mutation.3 regressions pass in the existing Event CI entrypoint. |
| Missing deletion cleanup | Real SQL deletion/replay produced no compatibility-cover orphan record: expected1, got0. | Private cleanup_json commits with the deletion/receipt, never in HTTP JSON. Postcommit/replay journals it; rollback never journals referenced assets. Journal failure preserves success and SQL recovery data. |
| Wrong upload-limit status | Actual old WAR returned503 for a5MiB+1 versioned upload while parsing expectedVersion. |413 before image intent/provider work; actual WAR asserts status and zero intent. |
| Unknown category becomes conflict | Real SQL returned409 from a foreign-key error. | Explicit create/edit category check returns400; no receipt, existing category preserved. |
| Malformed private detail JSON | DETAIL with page2/statusPUBLISHED for an owned draft returned an invalid data wrapper. | Single-resource read ignores list filters; concurrent disappearance returns404. Real SQL returns the correct draft object. |

SQL now returns a fifth internal cleanup column; JDBC consumes it while the HTTP
JSON envelope stays unchanged. Existing image request receipts are unchanged.
Old command receipts gain a nullable column without changing payloads; new
deletions preserve recovery metadata. Pair migration030 with the updated caller.

## Results

- Java safe-default package: **385 executed passed,60 guarded external skips
  (445 total)**; WAR BUILD SUCCESS.
- Existing Node entrypoint with3 new admin UI regressions: **59 passed, no skips**.
  Owned JS syntax/public DOM fixture pass.
- Incoming platform URL/local-handoff safety checks: **18 passed, no skips** with
  a verified ASCII Tomcat guard fixture. Initial missing native variables caused
  1skip; enabling them exposed the Unicode Windows Java classpath issue in the
  platform version probe. ASCII guard files resolved that fixture limitation;
  the platform owner's source was preserved.
- Real acceptance: **15 SQL/Redis +1 Cloudinary +3 full-WAR HTTP =19 passed, no
  skips**. Covers rights/revocation, concurrency/rollback, lifecycle/locks,
  durable deletion metadata, real journal/replay repair, multipart413,
  default/versioned images, public cache and Report/overview coexistence.
- Separate migration test: **1 passed, no skips**; atomic reapplication preserves
  receipt count and cleanup-data fingerprint. Restricted buyer reads public seats
  but cannot read private Event/receipt tables.
- Provider fixture cleanup: **1 passed**, only after SQL-reference checks. New
  deletion HTTP test also removes only its own generated unreferenced asset.

Ignored logs: target/dong-review-*-red.log, dong-review-cache-green.log,
dong-review-ui-green.log, dong-review-package.log, dong-review-node.log,
dong-review-safety-complete-retry.log, dong-review-it-final.log,
dong-review-cleanup.log. SQL worktree: target/dong-sql-review-final.log. Initial
failures are retained, not disguised as skips;19 supersedes the17-test checkpoint.

## External gates

Acceptance-checklist update: [DONG-ACCEPTANCE-20261011.md](DONG-ACCEPTANCE-20261011.md).
Source heads d43250e (Event) and68cc3e9 (SQL) each have12 successful and10 skipped
CI checks; neither has an independent review and both remain draft. New shared
metadata retry around19:01 returned40613/S0001; the40615 result below belongs to
the earlier review. Shared schema/login acceptance is still unproven.

Read-only shared metadata probe returned SQL40615 (previous checkpoint40613).
No shared firewall, credentials, grants or data were changed; actual schema/login
compatibility is not proven. Tests use restricted database-user impersonation
over Windows transport, not production SQL logins. Fault-only storage/outage
cases are labeled injections; separate provider/HTTP cases use real Cloudinary.
Independent approval, current-head CI, SQL-owner adoption and final dev acceptance
remain gates. No self-approval/issue closure. DONG-INTEGRATION.md and RUNBOOK.md
provide rollout and cross-owner cache hooks.
