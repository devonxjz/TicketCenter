# KHANH-01 acceptance — 2026-10-10

Issue #21; branch `feat/khanh/khanh-01-checkin-events`; [PR #38](https://github.com/devonxjz/TicketCenter/pull/38) targets `dev`. Updated with dev `63643d5`. KHANH-02 scanning/QR writes are outside this delivery.

## Delivered

Both GET endpoints, DTOs, CHECK_IN repository/service and mobile UI-15. DB truth requires ACTIVE user and active owning-organization MANAGER/CHECK_IN_STAFF membership. Organization parameter is only a filter. UUID IDs, UTC timestamps, PUBLISHED only and `[start-60 minutes,end)`. Cancelled 400, foreign actor 403, missing event 404, anonymous 401.

Redis keys/TTLs are 60/30 seconds; projections exclude financial fields; live authorization/schedule checks, current server clock, corruption/outage fallback and invalidation hook. Counters reuse cache without extending its TTL. A warm window cache reuses metadata after one indexed membership/User/schedule guard instead of two cold Event/membership queries; canonical UDF still runs each request. Revocation and schedule/cancellation changes remain checked immediately against SQL. Organizer check-in navigation reaches the real UI-15; the open button rechecks the window and passes eventId to existing `/organizer/checkin-scanner`. The scanner backend is KHANH-02.

## Azure database

Azure connection now works. Applied `src/test/resources/checkin/azure-contract.sql` atomically to `devonxjz.database.windows.net / TicketCenterticke`: created missing `dbo.fn_event_is_check_in_open(uniqueidentifier,datetime2(7))` plus minimum column SELECT/UDF EXECUTE grants to existing `tc_checkin` role. No shared table, business data, password, login or JPA mapping changed. Provisioning refuses to overwrite an existing function and rolls back all batches on error. The user's explicit database request authorizes this exception to issue ownership; shared database scripts remain unchanged.

**46 feature unit tests + 2 read-only Azure integration tests PASS**, no failures/errors/skips. Actual production persistence unit, repository/service and existing published Event/membership exercised under `EXECUTE AS USER='tc_checkin_user'`, without changing schema-validation settings or creating shared fixtures. Verified role access, 61/60 minute/end boundaries, real ticket counters, outsider 403 and missing 404. Financial-column SELECT denied; UDF EXECUTE and Event time SELECT permissions equal 1. Impersonation is a test technique, not a replacement for an actual CHECK_IN login in deployment.

Run `src/test/resources/checkin/verify-azure-contract.ps1`; log `target/khanh01-azure-integration.log`. Some other domains' JPA mappings differ from Azure schema and remain unchanged. KHANH-01 projection queries passed against the existing schema.

## Local acceptance

**Full Maven regression: 128 tests PASS**, no failures/errors/skips. Both existing SMTP tests executed through unmodified SmtpEmailSender using loopback STARTTLS capture validating authentication and MIME data; real OTP persisted in isolated SQL. Exactly two messages captured in the final run; no external mail relay. Existing tests print Gmail success text, which does not establish Gmail delivery.

**46 feature unit tests + 11 real SQL Server/Redis integration tests PASS**, no skips. Synthetic manager/staff/outsider, two organizations, five event states and 3 issued / 1 USED tickets. Warm-cache membership revocation, disabled user, cancellation/rescheduling, UUID UDF boundaries, TTL/expiry, corrupt-cache fallback and financial-read/write denial passed.

Run `verify-full-local.ps1` from checkin test resources; it privately loads Windows DPAPI credentials and starts local SMTP when needed. Logs: `target/khanh01-full-regression.log`, `target/khanh01-local-integration.log`. Credentials, fixture IDs, certificates and runtimes are ignored. `LocalCheckInFixture refresh` updates only named synthetic Events in the dedicated loopback test DB.

Actual Tomcat 11 acceptance: **14 HTTP checks PASS with Redis running and the same 14 PASS during a Redis outage; cache restarted afterward** (login, manager/staff, outsider/foreign scope, 401/400/403/404, invalid UUID, open window, real counters, compiled UI-15 JSP and organizer navigation). Log `target/khanh01-web-acceptance.json`. Deploys the WAR exploded to avoid packed-WAR URL/extraction errors on this Unicode OneDrive path; prior local deployments are preserved outside webapps.

## CI and review

User explicitly authorized CI changes. New `khanh01-acceptance.yml` reproduces full Maven regression, WAR packaging and all 11 feature SQL/Redis tests with disposable SQL Server/Redis and local SMTP. No shared Azure credentials/external mail are used. Master workflow's manual `khanh01_acceptance=true` option calls this job; normal pipeline behavior is unchanged. Manual acceptance does not deploy or attest other domains' production/security stages. Actionlint validated both workflows. CI results are linked in PR #38 after GitHub reports completion. The job asserts zero skipped tests and all 11 real integration checks; no other pipeline stage is represented as tested by this acceptance mode.

Merge requires another person's approving review; agent does not approve or merge its own PR. Event owner should call `CheckInEventService.invalidateEvent(eventId,organizationId)` after committed cancellation/schedule changes. It deletes window and actor-list keys via bounded SCAN. Requests independently check current DB metadata and authorization, so stale cache cannot grant revoked access or open cancelled/rescheduled Events. Event-owner source remains unchanged under ownership.

