# DONG-05 — Admin review

Verified locally on 11 October 2026 (UTC+7). Issue #12.

Current platform admin list/detail/full schedule/layout/effective rule read, canonical publish and reason-required reject, SQL status audit, post-commit public cache refresh.

Acceptance: SQL publish/reject/resubmit/rule scope/public visibility, publish vs deletion race and status audit; HTTP non-admin403, publish and public/image read; browser review/missing-cover400/reason persistence/disabled second decision.

Current combined evidence: 143 selected unit tests + WAR; 12 SQL/Redis +1 Cloudinary +1 actual WAR HTTP tests pass without skips. See [DONG-EPIC.md](DONG-EPIC.md) for contracts, actual/fake provider distinctions and limits, and [RUNBOOK.md](RUNBOOK.md) for reproduction.

Shared SQL proposal (#45), exact push approval, CI and independent review remain delivery gates. No shared models/migrations/POM/config or another domain were edited. This issue is not marked merged/closed.
