# DONG-05 — Admin review

Verified locally on 11 October 2026 (UTC+7). Issue #12.

Current platform admin list/detail/full schedule/layout/effective rule read, canonical publish and reason-required reject, SQL status audit, post-commit public cache refresh.

Acceptance: SQL publish/reject/resubmit/rule scope/public visibility, publish vs deletion race and status audit; HTTP non-admin403, publish and public/image read; browser review/missing-cover400/reason persistence/disabled second decision.

Pre-integration checkpoint: 143 selected unit tests + WAR; 12 SQL/Redis +1 Cloudinary +1 actual WAR HTTP tests pass without skips. See [DONG-EPIC.md](DONG-EPIC.md) for contracts, actual/fake provider distinctions and limits, and [RUNBOOK.md](RUNBOOK.md) for reproduction.

Shared SQL proposal (#45), exact push approval, CI and independent review remain delivery gates. No shared models/migrations/POM/config or another domain were edited. This issue is not marked merged/closed.


Final integration update: dev 28ce056 was merged locally, retaining its durable image workflow/migration tooling. Safe default build: 375 tests executed successfully, 59 external tests explicitly skipped; 56 Node tests passed; 12 SQL/Redis +1 provider +2 actual WAR HTTP checks passed without skips. Earlier 143-test numbers above describe the pre-integration checkpoint. See DONG-EPIC and RUNBOOK for the updated routing/contract details.
