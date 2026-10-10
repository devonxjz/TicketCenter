# Event SQL contract coordination

Tracking [issue #45](https://github.com/devonxjz/TicketCenter/issues/45), initially for DONG-01 public Seat access; now the concrete SQL dependency for [DONG-EPIC #13](https://github.com/devonxjz/TicketCenter/issues/13).

The baseline buyer role has no Seat projection, and baseline migrations contain canonical publish but no draft/zone/reject SPs or durable Event command receipt. [DONG-DB-CONTRACT.sql](DONG-DB-CONTRACT.sql) supplies a reviewable proposal rather than editing locked shared migrations:

- PUBLISHED-only `vw_PublicEventSeats` and buyer grant; no hold/order/customer identifiers.
- `usp_EventAuthorize`, `usp_EventRead`, `usp_EventReceipt`, `usp_EventCommand`: current authVersion/ACTIVE user, current manager membership/approved org or current platform admin, parent ownership, status/structure/version locks, atomic mutation+receipt and audit.
- Technical receipt table (not a new business model); BIN2 idempotency key comparison matches Redis case sensitivity. Retention/deployment policy requires owner agreement.
- Canonical `usp_PublishEvent` retained; rule must also cover Event start. Review the current `tc_users.platform_role` versus canonical publish's legacy `tc_user_platform_roles` synchronization; acceptance fixture seeds both deliberately, but role-row fallback never grants Event admin authorization.

Proposal applied only to `TicketCenter_Dong_Epic_Test_20261011`: 12 SQL/Redis +1 real Cloudinary +2 actual WAR HTTP tests pass after integration with dev 28ce056, restricted database execution contexts included. This is not proof that the shared migration/logins are deployed. Owner should adopt a new numbered migration, review grants/lock order/role contracts and rerun acceptance with actual runtime logins. Shared POM/config/migrations/models/OpenAPI are unchanged. Tomcat JSTL deployment prerequisites also need common-owner integration. [Runbook](RUNBOOK.md) documents setup and limits.