# DONG-02 — Draft lifecycle

- [x] Implementation and dedicated local acceptance complete. Current evidence:
  [DONG-ACCEPTANCE-20261011.md](DONG-ACCEPTANCE-20261011.md).
- [ ] Shared SQL/runtime acceptance, independent review, merge and final dev UI
  acceptance. Issue stays open. Push approval and remote publication are complete;
  older checkpoint statements below retain their historical status.

Verified locally on 11 October 2026 (UTC+7). Issue #9.

Organization-scoped list/detail/create/edit/delete/submit; current membership and authVersion; CSRF; chronological validation; pending/published locks; Redis lease plus atomic SQL receipts.

Acceptance: SQL replay/conflict/cross-org/current-role, duplicate/concurrent create, delete FK rollback and replay; HTTP create201/replay/401/403/UTF8; browser create, submit lock and rejected state.

Pre-integration checkpoint: 143 selected unit tests + WAR; 12 SQL/Redis +1 Cloudinary +1 actual WAR HTTP tests pass without skips. See [DONG-EPIC.md](DONG-EPIC.md) for contracts, actual/fake provider distinctions and limits, and [RUNBOOK.md](RUNBOOK.md) for reproduction.

Shared SQL proposal (#45), exact push approval, CI and independent review remain delivery gates. No shared models/migrations/POM/config or another domain were edited. This issue is not marked merged/closed.


Final integration update: dev 28ce056 was merged locally, retaining its durable image workflow/migration tooling. Safe default build: 375 tests executed successfully, 59 external tests explicitly skipped; 56 Node tests passed; 12 SQL/Redis +1 provider +2 actual WAR HTTP checks passed without skips. Earlier 143-test numbers above describe the pre-integration checkpoint. See DONG-EPIC and RUNBOOK for the updated routing/contract details.
