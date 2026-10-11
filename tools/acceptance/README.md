# Independent Native acceptance

PowerShell 7.4+, Node22+, local SQL Server TCP1433 and Windows SQL administration
are required. Use the generated **private, git-ignored** JSON from
`tools/local/setup.ps1`. These tools accept only the exact canonical loopback URL
and both local fixture markers. Never use a shared database or provider secrets.

```powershell
# NEW isolated fixture; setup never overwrites an existing database/config.
pwsh -File tools/local/setup.ps1 -Database TC_Vuong_Test_Local_AcceptanceNew -ConfigPath .local/acceptance-new.json
# Start full WAR with tools/local/run.ps1. After private accounts exist:
pwsh -File tools/acceptance/seed-browser.ps1 -ConfigPath .local/acceptance-new.json
# Seed is synthetic25 events/orders/payment rows, not purchase/provider E2E.
# It refuses a second seed. CSV title fixture intentionally starts with '='.
pwsh -File tools/acceptance/sql-inventory.ps1 -ConfigPath .local/acceptance-new.json -OutputDirectory .local/inventory-new
pwsh -File tools/acceptance/verify-artifacts.ps1 -Kind inventory -OutputDirectory .local/inventory-new
```

Stop the dedicated Serve runtime before the restore drill and keep the source
quiescent until comparison finishes. Other writers can make comparison fail.
Choose a **NEW** restore DB and **NEW** output directory on every run:

```powershell
pwsh -File tools/acceptance/backup-restore.ps1 -ConfigPath .local/acceptance-new.json -RestoredDatabase TC_Vuong_Test_Local_RestoredNew -OutputDirectory .local/restore-new
pwsh -File tools/acceptance/verify-artifacts.ps1 -Kind restore -ConfigPath .local/acceptance-new.json -OutputDirectory .local/restore-new
# Use .local/restore-new/restored-database.json with tools/local/run.ps1 -Smoke.
```

Backup uses SQL's existing default backup folder, COPY_ONLY/CHECKSUM, media
verification and NEW data/log files. No INIT, FORMAT, REPLACE or DROP. CHECKDB,
PK-ordered JSON SHA256 and actual COUNT_BIG for every table, object/column/index
signatures are compared. Empty table JSON is canonical `[]`. Keep DBs, backups
and evidence for inspection. SQL's Windows folder ACL stays protected; retained
backup verification runs through SQL. Restore JSON contains credentials and
must stay private. Runtime uses fixture db_owner, which does not prove
production least privilege.

```powershell
pwsh -File tools/local/setup.ps1 -Database TC_Vuong_Test_Local_Bench_New -ConfigPath .local/bench-new.json
pwsh -File tools/acceptance/finance-benchmark.ps1 -ConfigPath .local/bench-new.json -OutputDirectory .local/bench-results-new
pwsh -File tools/acceptance/verify-artifacts.ps1 -Kind benchmark -OutputDirectory .local/bench-results-new
```

Benchmark requires an empty marked Bench_* fixture. It inserts20,000 synthetic
audit rows and measures date/action/actor-action list profiles through the
actual adapter view, three repeats before/after a candidate index. Results
must have identical hashes. Plans and STATISTICS IO/TIME are retained privately.
No index is removed; candidate remains in the private fixture only. A failed
partial seed/run requires another NEW fixture, not deleting history to retry.
The candidate covers detail and consumes extra storage/write work; this is an
experiment, not a proposed production migration. Org/event EXISTS filtering,
realistic skew, parameter sniffing, cold cache and write throughput are outside
this bounded benchmark.

The Windows CI job installs checksum-pinned Tomcat11 and executes18 existing
Native guard nodes plus13 SQL guard nodes. Its JUnit gate requires28 leaf cases,
three parent groups and no skipped/failing cases. SQL is not installed or
accessed by the guard job. Full opt-in SQL/Redis/HTTP tests remain separate.
