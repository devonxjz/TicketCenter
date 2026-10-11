# Backend acceptance — local handoff scope

This record supplements the per-owner evidence. It is not whole-project acceptance.
Base: dev7541a65. Work: VUONG-06 local handoff, #33/#28; Native Windows.
Tested implementation: a3a092d8e2050a7a0c8c2f3e1cad160ebea2ae6d;
test isolation fix: c5dfdc39674eebf402b1f015f30022d45f552368.
Date:11/10/2026. Environment: Windows, PowerShell7.6.5, OpenJDK25,
Maven3.9.16, Tomcat11.0.25, local SQL Server TCP1433.

## Scope and verification ledger

- JDK25 safe-default baseline package:335 executed,60 opt-in external skips;
  no failure/error. Shell default Java17 failed release25 before selecting JDK25;
  runner now rejects a wrong/missing JDK before build.
- New guard tests RED because setup/run did not exist; GREEN local setup name,
  preservation, Git-ignore and missing-JDK checks plus prior JDBC override tests:
  18/18, zero skips. Includes tampered JSTL cache and inherited MAVEN_ARGS/OPTS;
  fake-Maven regression first failed with inherited settings, then passed after
  clearing/restoring them and forcing safe-test flags. A temporary port also
  allows guards to pass while the app is serving on18082.
  Enabled TC_TEST_POWERSHELL, TC_TEST_JAVA_HOME, TC_TEST_TOMCAT_HOME explicitly.
- Existing JavaScript contract/UI/report-summary suites:56/56, zero skips.
- Separate clean clone with no target/config + NEW empty DB
  `TC_Vuong_Test_Local_Handoff_1791693615653`: documented setup installed all5
  files and protected the generated JSON. Initial setup at b3817e7; first runner
  found unavailable remote JSTL.sha512 files. Fixed with pinned hashes in702d17a,
  pulled into the clone, then Smoke passed16/16 and stopped (exit0).
- Pulled a3a092d into that clone; documented Serve command rebuilt successfully
  (335 executed/60 explicit external skips), started full annotation WAR,
  Hibernate validate/readiness and real login/session/CSRF/finance/JSP smoke
  passed16/16 with no skips. LOCAL_APP_READY on loopback18082.
- Ctrl+C then closed18082, retained stdout/stderr, removed auth.json,
  accounts.json and both runtime properties copies. Runtime directory ACL
  remains protected with only current-user/SYSTEM. Existing8080 PID28036 was
  preserved; persistent private database config was retained for reopening.
- Read-only SQL verified both fixture markers and all5 installed SHA256 hashes.
  Re-running setup with the existing DB/new config path was rejected before
  overwrite and produced no new config. No shared DB was used.
- Setup uses schema-only team snapshot and explicit5-file migration order; no
  shared/Azure mutation or previous migration edit. JSON has current-user/SYSTEM
  ACL. Credential directory and .env.runtime are ignored by Git.
- Runtime supplies checksum-verified JSTL3.0.1 in a private Tomcat base; no POM,
  locked model/persistence or installed Tomcat changes.

## Independent finance acceptance — 11/10/2026

[Five bounded verification slices](vuong/VUONG-06-INDEPENDENT-ACCEPTANCE.md):
16 real browser results; UTF-8 JSP regression fixed; Windows CI Native guards;
15-class/62 SQL-name existence mapping; NEW backup/restore with42 data hashes,
CHECKDB and full WAR16/16;18 actual audit query benchmark runs on20,000 rows.
No shared/provider mutation or production index. Counts and scoped finance
acceptance do not close24UI/business E2E or #28/#33; whole progress stays4/6.

## Gates that remain open

| Gate | Required evidence / owner |
|---|---|
| Whole #28 | Route/DTO/Redis contracts for remaining planned APIs; auth TTL, QR/IPN and cross-domain invalidation handoff |
| Whole #33 | Browser24UI and connected purchase/payment/ticket/check-in/refund/cancellation/settlement acceptance on final dev |
| Database least privilege | Six real restricted runtime identities, #45 Seat projection, current-role synchronization; fixture db_owner is not this evidence |
| External integrations | SMTP/Cloudinary/VNPAY Sandbox merchant and HTTPS callback, with actual configured provider acceptance |
| Workers/recovery | Hold expiration, payment reconciliation, ticket/refund email, refund attempts and cancellation restart scenarios |
| Online/container | Docker cold-start/restart, Azure schema/grants, quota/memory; current rollout workflow is simulation |
| Full SPEC/DBMS |15 class mapping, SQL behavior/concurrency,15 index benchmarks, backup/restore and course reports/slides |

Existing finance acceptance: [VUONG-READY-MERGE](vuong/VUONG-READY-MERGE.md),
[CSV](vuong/VUONG-05-ACCEPTANCE.md), [full WAR](vuong/VUONG-06-FULL-WAR.md).
Do not count skipped/disabled jobs as PASS, or a local fixture as shared deployment.
Whole task total remains4/6 until #28/#33 meet their final gates.
