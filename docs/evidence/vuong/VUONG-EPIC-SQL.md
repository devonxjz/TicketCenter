# VUONG-EPIC Task 2 SQL evidence

Task base: `0f32844cce0419f62d456e6dd5e5b2cad249862a`. Dedicated managed worktree `vuong-canonical-sql`, local branch `feat/vuong/vuong-epic-canonical-sql`. Approved UUID/tc_* schema and Native execution are recorded in the 10/10 design/plan. No locked model, 026 migration or legacy BIGINT SQL was changed.

## Decisions

- Canonical snapshots and transfer logs own the financial write path. Procedures bind UUID/decimal integer VND, current actor and optional authVersion (Java must bind version).
- Audit aliases follow locked `entity_type/entity_id`; all SP inserts supply UUID/timestamps explicitly instead of assuming base-table defaults.
- A fresh test-only finance catalog supplies owner prerequisite columns. Secondary domain relations are fixture conveniences, not evidence of the owners' complete fresh installation.
- Durable commission replay preserves original saved terms after a subsequent edit; current attached-event count is informational. UNKNOWN preserves reservation and forbids a new payout ID until reconciliation. Provider result reference is distinct from requested reference, with the same explicit immutable fingerprint across phases.
- Source freeze blocks postconfirmation accepted order/payment/customer-refund monetary changes; nonaccepted captures/compensation remain possible. Existing owner row-first writes can deadlock against Event-first confirmation; safe abort/retry remains necessary.
- Report NULL organization means all organizations only after platform-admin authorization. F05 historical refund cutoff and F10 transfer date stay separate.

## RED and GREEN

The first fixture attempt exposed SQL Server nvarchar size/QUOTED_IDENTIFIER requirements; corrected fixture uses nvarchar(max), SET QUOTED_IDENTIFIER and an atomic transaction. Initial test database was completed additively, never dropped. A second truly empty database later ran the complete corrected fixture successfully.

`finance_contract.sql` RED: sqlcmd returned missing canonical implementation 52792 before mutation. Implemented 027; GREEN exercised actual procedures/UDF/views, accepted payment versus duplicate capture/compensation, snapshot fee7/net93, confirmation replay, pending/success/failure/UNKNOWN/replay and cohort/cashflow.

`finance_guardrails.sql` RED: original mutation replay returned edited 10%/3 instead of original 5%/2. Added durable response terms; GREEN additionally verified conflicting key, linked-rule edit rejection, nonadmin/stale-version denial, header/snapshot/audit immutability, postconfirm customer refund rejection, UNKNOWN/new-ID rejection, changed amount and final provider-reference conflicts.

`finance_temporal.sql` GREEN: paidAt half-open boundary; refund processed exactly at toUtc excluded; historical current refund not substituted into an earlier period; per-order rounding; cashflow refund of earlier cohort; completed refund ignores FAILED history; zero-event row/no empty header; zero-net PAID without transfer; CANCELLED scheduled end, uncreated refund obligations and unfinished outbox, full-refund zero-fee completion.

`finance_permissions.sql` GREEN with actual SQL Server EXECUTE AS USER: restricted admin SP mutation succeeds through ownership chain while direct DML is denied; manager report grant/no payout execution; check-in SELECT returns SQL229; independent GRANT then REVOKE loses permission. No SQL login/password was created by this task.

`run-finance-concurrency.ps1` GREEN, two independent sqlcmd sessions per race, transaction A confirms its lock before B starts:

1. 80+80 against net100: one reservation, pending80, second rejected.
2. Same payout ID40 twice: one row, pending40, second replay.
3. New unresolved customer refund commits while confirm waits: confirm rejected and settlement remains DRAFT.
4. Event attaches a rule while edit waits: edit rejected and attached rule terms remain unchanged.

Dedicated local databases created: `TC_Vuong_Test_20261010_Finance` and `TC_Vuong_Test_20261010_Finance_Fresh`. Before fresh fixture installation, 027 on the empty second database returned 52700/exit1 and left **zero user tables**. Then fixture+027 and all four SQL files passed exit0. The four races also passed there. Neither database was dropped; no existing/shared database was modified.

Fresh logs: Windows temporary directory `vuong-finance_contract-fresh.txt`, `vuong-finance_guardrails-fresh.txt`, `vuong-finance_temporal-fresh.txt`, `vuong-finance_permissions-fresh.txt`; race logs `vuong-finance-races-*`. These contain synthetic fixture data only and are not committed.

SQL Server local version 17.0.1135.8; sqlcmd Windows-integrated access used. Root owns the separately user-approved TCP configuration/restart and dedicated JDBC test login, and owns Java/JDBC/HTTP verification. This SQL evidence does not claim those root tests or SMTP/owner/provider E2E acceptance.

Run recipes and exact result columns are in `docs/backend/vuong-finance-sql-contract.md`. Execute with stop-on-error (`-b`) and explicit guarded database. No external messaging, remote push/PR/merge, real SMTP or real bank transfer was performed.
