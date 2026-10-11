# Backend acceptance — local handoff scope

This record supplements the per-owner evidence. It is not whole-project acceptance.
Base: dev7541a65. Work: VUONG-06 local handoff, #33/#28; Native Windows.

## Scope and verification ledger

- JDK25 safe-default baseline package:335 executed,60 opt-in external skips;
  no failure/error. Shell default Java17 failed release25 before selecting JDK25;
  runner now rejects a wrong/missing JDK before build.
- New guard tests RED because setup/run did not exist; GREEN local setup name,
  preservation, Git-ignore and missing-JDK checks plus prior JDBC override tests:
  16/16, zero skips. Windows PowerShell tests explicitly enabled.
- Fresh checkout + new empty SQL fixture + full WAR smoke: pending this delivery's
  run; results below will be completed only after running the documented steps.
- Setup uses schema-only team snapshot and explicit5-file migration order; no
  shared/Azure mutation or previous migration edit. JSON has current-user/SYSTEM
  ACL. Credential directory and .env.runtime are ignored by Git.
- Runtime supplies checksum-verified JSTL3.0.1 in a private Tomcat base; no POM,
  locked model/persistence or installed Tomcat changes.

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
