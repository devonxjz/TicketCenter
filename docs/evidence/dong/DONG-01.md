# DONG-01 — Public catalog/detail and Redis

- [x] Implementation and dedicated local acceptance complete. Current evidence:
  [DONG-ACCEPTANCE-20261011.md](DONG-ACCEPTANCE-20261011.md).
- [ ] Shared SQL/runtime acceptance, independent review, merge and final dev UI
  acceptance. Issue stays open. Push approval and remote publication are complete;
  older checkpoint statements below retain their historical status.

Updated 11 October 2026 (UTC+7). [Issue #8](https://github.com/devonxjz/TicketCenter/issues/8). Initial local commit `d84c4eff3055312eda2c28706c131d7699369400` on `feat/dong/dong-01-event-catalog-cache`, based on `origin/dev` `71e02ad`. Final integration is on `feat/dong/dong-epic-event-integration`; see [DONG-EPIC.md](DONG-EPIC.md) and [RUNBOOK.md](RUNBOOK.md).

The initial 126-test milestone lacked SQL/browser acceptance. Those gaps have now been exercised locally on the epic integration: **143 selected unit tests + WAR package**, **12 real SQL/Redis tests**, **1 real Cloudinary test**, **1 actual WAR HTTP test**, all pass without skips. Public detail/seat rows and immediate description refresh were also verified in the actual browser. The older fixture-variable-dependent `EventCatalogSqlServerIT` was not rerun; the new epic suite verifies catalog visibility on restricted SQL execution contexts after canonical publication. No claim is made for full default suite, separate SQL-auth logins, CI or merge.

- Five public GET endpoints: `/api/events`, `/api/events/{eventId}`, `/api/events/{eventId}/zones`, `/api/zones/{zoneId}/seats`, `/api/event-categories`.
- Canonical `vw_PublicEvents`, `vw_ZoneInventory`, proposed `vw_PublicEventSeats` expose only published Event data; hidden/missing parents404, existing empty children[]; no fake fallback data.
- Keyword≤100, bound and LIKE-escaped title/venue search; page1/pageSize12 default/max50; allowlisted start/price sorting with ID tie-break; from-inclusive/to-exclusive UTC; offset≤1,000,000; strict UUID/date validation.
- DTO names category/org, schedule/sale-active, integer-string min/max prices; Zone type/capacity/availability, Seat row/number/label/status. UUID/VND-string/UTC/`data` conventions take precedence over inconsistent example responses.
- List keys use canonical filter hash; list/Zone/Seat TTL30s, detail120s, category300s. Corrupt entries miss; Redis GET outage falls back to SQL; DB failures503 are not cached; logs are bounded/sanitized. A post-commit generation prevents stale in-flight refill. Other owners' inventory/cancellation mutation hooks remain an integration requirement.
- Public JSPs use API loading/empty/error/filter/pagination, safe text/URL rendering and actual seat-status rows; detail is accessible without login.

Shared rollout still requires [coordination #45](https://github.com/devonxjz/TicketCenter/issues/45): adopt [SQL proposal](DONG-DB-CONTRACT.sql) as a new reviewed migration and verify actual runtime principal logins. The proposal was applied only to the dedicated test DB. Locked models/POM/config/shared migrations/OpenAPI were not edited. Remote push awaits exact approval following the earlier automatic review rejection; issue remains open pending integration/CI/review.

Final integration update: dev 28ce056 was merged locally, retaining its durable image workflow/migration tooling. Safe default build: 375 tests executed successfully, 59 external tests explicitly skipped; 56 Node tests passed; 12 SQL/Redis +1 provider +2 actual WAR HTTP checks passed without skips. Earlier 143-test numbers above describe the pre-integration checkpoint. See DONG-EPIC and RUNBOOK for the updated routing/contract details.
