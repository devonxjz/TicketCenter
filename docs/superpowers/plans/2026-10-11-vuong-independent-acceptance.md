# VUONG-06 Independent Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans; implement each task in this session. Steps use checkbox syntax for tracking.

**Goal:** Complete the five independent acceptance slices approved by the user without implementing teammates' business features.

**Architecture:** Extend existing Native local fixtures and regression tests. Browser tests use real login and SQL-backed finance pages. SQL tools accept only marked local fixtures, use Windows authentication at literal loopback1433 and retain isolated artifacts.

**Tech Stack:** PowerShell7.4+, Java25/Tomcat11, Node22+, SQL Server, Codex browser.

**Spec:** docs/references/SPEC.md sections13–14; docs/evidence/backend-acceptance.md; approved five-task table in this conversation.

## Global Constraints

- No POM/model/persistence changes, teammate business implementation, shared/Azure mutations or real providers.
- No overwrite/drop of existing databases or backup files. Restore uses a NEW marked fixture name and NEW physical files.
- Benchmark uses a separate new fixture and synthetic data; no index removal from a running/demo/shared database.
- Counts/existence, mock DOM tests, disabled jobs and fixture db_owner do not prove final business acceptance.
- Existing authorization: Native execution, scoped PR to dev, exact-head and postmerge green checks, owned branch cleanup and scoped epic checkboxes.

## Review Focus

- Redirected JDBC config must fail before SQL access.
- Restoring an existing database or writing outside SQL's default backup/data directories must fail.
- Index comparisons keep data/query/results constant and retain all plans/reads; never promise improvements in advance.
- Browser filters/pagination must show actual fixture rows, large VND must remain exact, STAFF cannot read finance.
- Windows CI must execute18 guards without skips, including the real JDK/Tomcat preflight and fake-Maven probe.

## Task 1: Real browser finance acceptance

- [x] Create a NEW Native fixture and start private Serve runtime.
- [x] Seed explicitly synthetic finance rows using schema already exercised by FinanceTeamSchemaIntegrationTest.
- [x] Real browser ADMIN/MANAGER/STAFF login; inspect overview, audit filter/detail/pagination, reports/filter/CSV, commission and settlement selection/action state. Record each expected/actual result and screenshots; no actual payout.
- [x] Fix only owned defects with a failing regression first; retain browser evidence and rerun affected cases.

## Task 2: Windows CI guards

- [x] Add windows-latest job to02-unit-integration-tests.yml: Java25, Node22, checksum-verified Tomcat11 download, explicit test prerequisite env variables.
- [x] Run existing local-handoff and full-war safety suites, emit JUnit artifact; require18 executed, zero failures/skips.
- [x] Validate the same guard command locally. Actual GitHub exact-head/postmerge execution is enforced by the Delivery merge gates below.

## Task 3: Read-only SQL inventory

- [x] Add tools/acceptance/fixture.ps1 and sql-inventory.ps1; config identity/marker/output guards before query.
- [x] Add behavioral guard tests that fail before helpers exist and pass after implementation.
- [x] Export user object names/types, constraints, non-PK indexes, roles/permissions,15-class mapping; distinguish missing physical/logic mappings and count from behavior evidence.

## Task 4: Backup and restore drill

- [x] Add tools/acceptance/backup-restore.ps1; COPY_ONLY CHECKSUM backup to SQL default backup directory, verify media, restore to NEW fixture with MOVE/NEW physical files and no REPLACE.
- [x] Compare user-table row counts and schema signatures, DBCC CHECKDB on restored fixture, create private runtime JSON preserving dedicated login mapping.
- [x] Run full WAR Smoke against restored fixture, retain backup and SQL evidence, never restore over source.

## Task 5: Finance query benchmark

- [x] Add tools/acceptance/finance-benchmark.ps1 and deterministic synthetic benchmark SQL in database/tests/vuong.
- [x] Seed at least20,000 audit rows in a separate benchmark fixture; compare three finance audit queries before/after a candidate non-PK index, same parameters and results, three runs each.
- [x] Record actual plans, STATISTICS IO/TIME, row counts, index size and write considerations; leave benchmark candidate only in private fixture.

## Delivery

- [ ] Run full package,56 JS tests, all Native guards and full WAR browser/startup evidence; fresh whole-branch review.
- [ ] Commit evidence with tested revisions, publish scoped PR, attach it, expanded CI exact head, merge, Native/CI postmerge recheck.
- [ ] Update only accepted slices in #33/#34; whole total4/6 unchanged. Delete only this merged feature branch, preserve managed worktree and private evidence.
