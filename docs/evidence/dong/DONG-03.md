# DONG-03 — Zone and seat inventory

Verified locally on 11 October 2026 (UTC+7). Issue #10.

SEATED row matrix, STANDING capacity, integer VND price, safe deletion/regeneration, held+sold quota floor, immutable published layout.

Acceptance: SQL AA2 labels, quota floor, duplicate Zone race, submit/delete and publish/delete races; browser seated2x3 and standing10, availability totals and disabled pending controls.

Current combined evidence: 143 selected unit tests + WAR; 12 SQL/Redis +1 Cloudinary +1 actual WAR HTTP tests pass without skips. See [DONG-EPIC.md](DONG-EPIC.md) for contracts, actual/fake provider distinctions and limits, and [RUNBOOK.md](RUNBOOK.md) for reproduction.

Shared SQL proposal (#45), exact push approval, CI and independent review remain delivery gates. No shared models/migrations/POM/config or another domain were edited. This issue is not marked merged/closed.
