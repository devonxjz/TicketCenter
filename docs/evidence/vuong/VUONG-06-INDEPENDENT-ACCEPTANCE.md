# VUONG-06 independent acceptance — 11/10/2026

Scope: five independent verification slices authorized by Vuong. Base dev
`3e380bf24ba0e8d25609614655f0f24e738c290d`. No POM/model/persistence or teammate
business implementation changed; no shared/Azure mutation or real provider.
These slices do not close whole #28/#33: overall progress remains4/6 tasks.

## Browser and regression

Real Tomcat11 WAR, SQL fixture `TC_Vuong_Test_Local_Independent_1011`, real
identity/session/CSRF. Synthetic25 event/order/capture rows and25 tagged audit
rows were inserted explicitly, not via purchase/provider workflows.
[16 recorded browser results](independent/browser-cases.json):

- ADMIN overview reads4 users/2 approved organizations and seven UTC day rows.
- Audit action/date filtering:25 rows, pages20/5; detail preserves eventId and
  hides the synthetic passwordHash sentinel.
- Reports: organization/date filters, pages20/5, global25-event totals remain
  exact. Gross9007199254743393 VND and first order9007199254740993 VND preserve
  every digit beyond JavaScript Number's safe integer range.
- CSV from page2 has header+25 data rows; exact money survives. EVENT_SALES
  export quotes the formula-like title with an internal tab.
- Commission5%+2VND linked to25 events has no edit button.
- Settlement preview100 gross/7 commission/93 net; recalculation persisted
  DRAFT, confirmation persisted CONFIRMED and disabled recalculation. Payout
  remained unavailable/disabled; no money transfer.
- MANAGER login exposes Reports only and scoped organizations, with the same
  exact fixture totals. STAFF login goes to check-in selection. IAB blocks
  rendering JSON403 documents; the HTTP suite separately asserts403 for all
  STAFF finance pages, and Manager ADMIN overview/audit.

Browser found a real defect: shared `_scope.jsp` labels rendered as mojibake
because the included fragment had no pageEncoding. An actual rendered-JSP HTTP
assertion failed before the fix; explicit UTF-8 passed16/16 and browser labels
became readable. The same assertion now runs in full-WAR acceptance.
Viewport screenshots are retained: [report](independent/report.png),
[confirmed settlement](independent/settlement.png). Full-page capture was
unavailable, so the documented screenshot fallback was used.

Owner admin/organizer dashboards still announce static prototypes; the public
event list reported service temporarily unavailable in this fixture. This
finance acceptance is not evidence that all24 UI flows or the entire product
are complete. The original temporary Serve process was absent at the final
HTTP rerun; a fresh packaged WAR rerun on the preserved source passed16/16.

## Windows CI and baseline

- Clean Native Java25 package:395 discovered,335 executed,60 explicit guarded
  external skips; zero failure/error.56 safe JS cases passed without skips.
- Native guards:18 existing +13 SQL =31 Node test nodes (28 leaves+3 groups),
  zero skips/failures. SQL rejects shared databases, remote/overridden URLs;
  browser seed is covered by the same rejection suite.
- `windows-native-guards` in workflow02 uses Java25/Node22 and official
  Tomcat11.0.25 ZIP with pinned SHA512, retains JUnit, and fails on wrong
  counts or any skip/failure. It exercises the real JDK/Tomcat preflight and
  fake-Maven probe without SQL. Actual head/postmerge CI results are the PR's
  merge gates; a declared job is not counted as passing evidence.

## SQL inventory

[Read-only inventory and all mappings](independent/sql-inventory.md):42 tables,
21 views,28 procedures,3 scalar+8 inline functions,31 triggers,67 CHECK,
62 FK,42 PK,21 unique constraints.59 non-PK indexes include unique constraints;
30 are non-PK/non-unique. All15 class mappings and62 exact SPEC SQL names exist
in the schema fixture. Runtime role permissions/memberships and column/index
definitions were exported privately. **Existence is not behavioral acceptance**:
transactions, concurrency, current-role synchronization, provider integration,
restricted runtime identities and final shared schema still need their gates.

## Backup/restore

Source was quiescent. COPY_ONLY/CHECKSUM backup retained under SQL's protected
default backup directory; RESTORE VERIFYONLY succeeded. Restored to NEW
`TC_Vuong_Test_Local_Restore_1011B` using NEW physical data/log files, no
REPLACE/INIT/FORMAT/DROP. CHECKDB passed. All42 tables matched actual row counts
and PK-ordered JSON SHA256 including empty `[]`;457 object,633 column and223
index-column signatures matched. Generated login remapped only in the restored
fixture; private JSON is git-ignored with current-user/SYSTEM ACL.

Full WAR on restored fixture passed16/16 (no skips), including Hibernate
validation/readiness and real identity/finance JSP assertions. Existing target
restore was explicitly refused before any output directory. Initial drill and
partial benchmark fixtures remain retained; no destructive retry. The initial
empty-table hash gap was detected by artifact verification and corrected in
the second drill. Windows backup-folder ACL was not widened; retained media is
verified through SQL. This is local recovery evidence, not production RPO/RTO
or the full disaster-recovery worker/provider scenario.

## Audit query benchmark

NEW fixture `TC_Vuong_Test_Local_Bench_1011B`:20,000 deterministic synthetic
audit rows. Three AuditRepository date/action/actor-action pagination profiles
through `vw_VuongAuditLogs`; three repeats per phase,18 actual XML plans and
STATISTICS IO/TIME outputs. All result hashes and20-row outputs were identical.
[Raw measurement summary](independent/benchmark.json).

| Profile | Reads before → after, each of3 runs | Median client ms before → after |
|---|---:|---:|
| Date page |603 →593 |44.806 →26.694 |
| Action page |576 →63 |19.024 →13.446 |
| Actor+action page |576 →63 |18.739 →14.990 |

Candidate `IX_Acceptance_Audit_Action_Date` used4,512 KiB versus4,608 KiB for
the base clustered index. Action profiles reduced reads about89%; date-only
reads barely changed. Timings include warm-cache/client/XML capture overhead;
they are observations, not an SLA or universal speedup. Additional storage and
every audit INSERT's index maintenance are costs; write throughput was not
measured. The candidate covers detail for this private synthetic experiment
and is **not** a production index/grant proposal. No production migration,
forced plan, global cache clear or index removal. This is3 query profiles,
not the SPEC's15 required index benchmarks or scoped org/event EXISTS plans.

Reproduction and retention: [Native acceptance tools](../../../tools/acceptance/README.md).
Private `.local/inventory-final`, `.local/benchmark-1011b` and
`.local/restore-1011b` retain catalogs, actual plans/statistics, backup metadata
and restricted restore credentials. Never publish the credential JSON.
