# Vương finance read-side evidence — Task 4

Plan: `docs/superpowers/plans/2026-10-10-vuong-finance-integration.md`, Task 4. Base a7710e1. User approved UUID/tc_* integer VND and isolated SQL fixture; no peer-review wait.

## Progress ledger

- Filter/audit metadata tests written before implementation. First build stopped because default JDK cannot compile release25; corrected only command environment to JDK25. Actual RED: `mvn -B -Dtest=ReportFilterTest,AuditMetadataTest test` failed missing ReportFilter/AuditMetadata. GREEN: 7 tests, zero failures/errors/skips.
- Service tests written before implementation. RED: selected ReportServiceTest compilation failed missing ReportService/ReportCache. GREEN combined suite: 11 tests, zero failures/errors/skips (2026-10-10 09:03 Asia/Bangkok).
- JDBC doubles test resource ownership/authority ordering only. They are not database acceptance. Dedicated SQL Server integration is pending the opt-in platform gate.

## Decisions and shared interface

- Ruling: EVENT_SALES and SETTLEMENT_BALANCE dates filter Event.end_time, not payment dates — current/confirmed aggregate views cannot represent transactional history. Response `timeBasis` states this explicitly; COHORT_REVENUE/CASH_FLOW use the frozen F05/F10 UTC definitions.
- Ruling: metadata projection only exposes allowlisted UUID, nonnegative integer money, known state and timestamp fields. Unknown/nested/secret fields are discarded; malformed/duplicate-key/oversize JSON fails closed to an empty projection.
- Root mutation integration calls `ReportCache.invalidateAfterCommit(UUID organizationId)` after commit. Keys: `tc:finance:report:g:global`, `tc:finance:report:g:org:{UUID}`; TTL60 payload `tc:finance:report:v1:{scope}:{generation}:{SHA256(actorId|authVersion|all filters)}`. Current authority/event scope is rechecked before every cache read. Redis errors fall back to database; CSV never uses cache.

No full WAR, shared DB, SMTP, remote push or PR executed by this task. CSV Excel/Sheets acceptance and owner full end-to-end flows remain separate gates.
