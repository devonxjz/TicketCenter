# Event SQL contract coordination

Tracking [issue #45](https://github.com/devonxjz/TicketCenter/issues/45), initially for DONG-01 public Seat access; now the concrete SQL dependency for [DONG-EPIC #13](https://github.com/devonxjz/TicketCenter/issues/13).

The baseline buyer role has no Seat projection, and baseline migrations contain canonical publish but no draft/zone/reject SPs or durable Event command receipt. [DONG-DB-CONTRACT.sql](DONG-DB-CONTRACT.sql) supplies a reviewable proposal rather than editing locked shared migrations:

- PUBLISHED-only `vw_PublicEventSeats` and buyer grant; no hold/order/customer identifiers.
- `usp_EventAuthorize`, `usp_EventRead`, `usp_EventReceipt`, `usp_EventCommand`: current authVersion/ACTIVE user, current manager membership/approved org or current platform admin, parent ownership, status/structure/version locks, atomic mutation+receipt and audit.
- Technical receipt table (not a new business model); BIN2 idempotency key comparison matches Redis case sensitivity. Retention/deployment policy requires owner agreement.
- Canonical `usp_PublishEvent` remains the sole publication path; proposal aligns its check with current ACTIVE `tc_users.platform_role`, approved organization and rule validity at Event start. No roles are synchronized or granted. Latest acceptance creates ADMIN without a legacy role row and proves stale legacy ADMIN cannot publish after current-role revocation.

Proposal applied only to `TicketCenter_Dong_Epic_Test_20261011`: 12 SQL/Redis +1 real Cloudinary +2 actual WAR HTTP tests pass after integration with dev 28ce056, restricted database execution contexts included. This is not proof that the shared migration/logins are deployed. Owner should adopt a new numbered migration, review grants/lock order/role contracts and rerun acceptance with actual runtime logins. Shared POM/config/migrations/models/OpenAPI are unchanged. Tomcat JSTL deployment prerequisites also need common-owner integration. [Runbook](RUNBOOK.md) documents setup and limits.

Latest checkpoint integrates dev7541a65:13 SQL/Redis +1 real provider +2 full-WAR
HTTP tests pass without skips;383 executed Java/60 guarded skips,56 Node plus7 URL
guard checks pass. New migration030 is prepared separately under#45 (029 is in
refund PR50), applied/reapplied atomically only in the dedicated fixture, with
receipt history preserved. Event feature PR51 does not contain database changes.
[DONG-INTEGRATION.md](DONG-INTEGRATION.md) supplies exact routes, Redis TTLs,
invalidation hooks, role authority and rollout order for integration review.
