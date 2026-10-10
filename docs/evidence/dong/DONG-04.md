# DONG-04 — Event cover

Verified locally on 11 October 2026 (UTC+7). Issue #11.

Actual multipart content validation and Cloudinary, authorization before provider, upload outside SQL transaction, version-aware save, durable replay, old/unused cover journal and sanitized errors.

Acceptance: PNG/JPEG/WEBP unit validation; SQL rollback/provider502/cross-org/replay/eviction/stale upload; real Cloudinary SQL/public read and verified cleanup; real HTTP multipart. WEBP is structural locally, full decode at provider.

Pre-integration checkpoint: 143 selected unit tests + WAR; 12 SQL/Redis +1 Cloudinary +1 actual WAR HTTP tests pass without skips. See [DONG-EPIC.md](DONG-EPIC.md) for contracts, actual/fake provider distinctions and limits, and [RUNBOOK.md](RUNBOOK.md) for reproduction.

Shared SQL proposal (#45), exact push approval, CI and independent review remain delivery gates. No shared models/migrations/POM/config or another domain were edited. This issue is not marked merged/closed.


Final integration update: dev 28ce056 was merged locally, retaining its durable image workflow/migration tooling. Safe default build: 375 tests executed successfully, 59 external tests explicitly skipped; 56 Node tests passed; 12 SQL/Redis +1 provider +2 actual WAR HTTP checks passed without skips. Earlier 143-test numbers above describe the pre-integration checkpoint. See DONG-EPIC and RUNBOOK for the updated routing/contract details.
