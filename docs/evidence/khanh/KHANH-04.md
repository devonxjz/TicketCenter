# KHANH-04 — Admin refund decisions and explicit retry

Issue: [#24](https://github.com/devonxjz/TicketCenter/issues/24). Branch: `feat/khanh/khanh-04-admin-refund-decision`, based on updated `dev` (`c1d7e0a`). PR targets `dev`.

## Delivered behavior

UI-21 is available at `/admin/refund-requests` and `/admin/refund-requests/{refundId}`. Existing admin refund navigation redirects to this portal. List filtering, stable paging, empty/error/loading states, buyer email, order/event/reason, ticket amounts/statuses, approval notes, rejection reasons, reviewer/time and transfer history are included. Confirmations require a rejection reason, disable controls while sending and retain the same UUID key for repeated payloads after a lost response. User-provided text is rendered as text, including stored HTML-looking buyer names. VND formatting uses integer strings/BigInt; displayed dates use `Asia/Ho_Chi_Minh`.

Implemented API endpoints, documented with typed envelopes in OpenAPI:

- `GET /api/admin/refund-requests?status=&page=&pageSize=`
- `GET /api/admin/refund-requests/{refundId}`
- `POST /api/admin/refund-requests/{refundId}/decision`
- `POST /api/admin/refund-requests/{refundId}/retry`

Only an authenticated, currently active ADMIN with the current authVersion can read or mutate. Mutations require CSRF, JSON and a UUID Idempotency-Key. Caller-selected actor/attempt fields, duplicate JSON keys, unexpected fields and malformed inputs are rejected. The internal named servlet entry points cannot be called as public mutation routes.

## Canonical contract and transaction behavior

Migration `043_khanh04_admin_refund.sql` adds admin-only projections and `sp_refund_approve`, `sp_refund_reject`, `sp_refund_retry`. It wraps the existing financial procedure, preserving the accepted payment, reservation limits, canonical obligation, technical attempt mirror, inventory updates and audit ownership. Existing migrations and the Order/Ticket/Refund models are unchanged. The admin event/organization/coupon/finance implementations are unchanged; the shared AdminServlet change redirects only its refund page.

The issue examples use old numeric IDs, fractional money and ticket `ISSUED`. SPEC takes precedence: all IDs are UUID strings, money is a whole-VND string, restored tickets are `ACTIVE`. A successful approval returns request `status=APPROVED`; `canonicalStatus=PROCESSING` reflects the durable `PENDING` transfer attempt created atomically by SP10 and its canonical mirror. Tickets stay `REFUND_PENDING`. Zero-value refunds finish immediately without creating a fictitious transfer. The actual transfer worker/reconciliation is KHANH-05; this task creates the durable queued intent and never calls a money gateway from the admin request.

Explicit retry requires canonical `RETRYABLE`, a current attempt confirmed `FAILED`, no pending/unknown/successful competing attempt and no unresolved payment receipt. It retains the refund ID and creates a new attempt ID. `UNKNOWN`/`NEEDS_RECONCILIATION` cannot be retried. The issue's filter aliases `FAILED` and `UNKNOWN` map to these canonical states. Rejection restores tickets only while the event remains valid; a cancelled event leaves them `INVALIDATED`.

SQL serializes competing decisions, follows Event → User → Order → Payment → Request → obligation/attempt locks and checks authority again after waiting. Decision, attempt, audit and durable idempotency payload commit together. Outer transaction rollback and injected failure after attempt creation undo all of them. A changed payload using the same actor/key returns 409. Redis is updated only after commit; Redis failure cannot turn a committed decision into an unsuccessful response. Cache invalidation covers the refund, pending count, affected buyer's ticket lists and affected ticket details/QRs; the optional response cache TTL is 86400 seconds. Reads and replay authority always come from SQL.

## Verification

Local verification on 2026-10-12:

| Check | Result |
|---|---|
| Focused Java acceptance | 39 cases: 15 real SQL Server, 1 real Redis, 21 parser/service, 2 OpenAPI; no failures/errors/skips |
| Project Java regression | 810 cases, 690 executed, 120 other opt-in infrastructure cases skipped; no failures/errors |
| JavaScript unit regression | 66 passed, 9 platform-specific native guards skipped on Windows; no failures |
| Packaged complete WAR on Tomcat 11 | 40 HTTP/JSP checks passed using actual login and a restricted ADMIN SQL login; retry supports an empty POST body |
| Browser | 12 checks passed, desktop/mobile screenshots, no JavaScript errors |
| Azure contract | 040–043 installed atomically; all SHA-256 history verified; existing restricted ADMIN projections/procedure permissions and direct-write denials verified read-only |

DEC-01 through DEC-05 are covered. Additional SQL cases prove a two-admin approve/reject race, two simultaneous retries, identical-key replay, changed-payload conflict, zero-value completion, cancelled-event rejection, current role/authVersion revocation, revocation during an Event lock wait, outer rollback and injected failure after attempt insertion. Browser acceptance loses the first committed approval response, resends the same payload/key and verifies exactly one attempt. It also performs reject/restoration and failed retry, and confirms UNKNOWN has no retry control.

No mutating test runs against shared Azure. The project installation adds only schema/runtime contracts and migration history. The private pre-install definition snapshot is Git-ignored under `docs/handoff/`. Azure verification uses database impersonation of the existing restricted ADMIN user; it does not reset credentials or prove separately configured production role-login secrets. The complete WAR acceptance uses a real restricted ADMIN connection in the dedicated loopback database.

GitHub Actions results for the current head are available on [PR #68 checks](https://github.com/devonxjz/TicketCenter/pull/68/checks). The dedicated KHANH-04 job requires real SQL/Redis execution without skips and full-WAR HTTP/browser acceptance; the shared project pipeline, customer-refund and SQL financial regressions also run on this PR. Merge remains subject to green checks and the project's review gate.

## Reproduction and artifacts

The dedicated workflow `.github/workflows/khanh04-admin-refund.yml` creates a disposable SQL Server 2022 database and Redis service, installs all prerequisite contracts, runs actual SQL/Redis cases with a zero-skip gate, packages the whole production WAR, runs HTTP/JSP checks on checksum-verified Tomcat 11 and performs browser acceptance with pinned Playwright. It never deploys the application or changes Azure data.

Local fixture setup requires the existing guarded KHANH-02 database credentials. Run `AdminRefundCiSetup --existing` only for that dedicated loopback fixture; its owner connector refuses any shared target. Enable `TC_ADMIN_REFUND_TEST=true`, supply loopback Redis settings and run:

```text
mvn test -Djunit.jupiter.extensions.autodetection.enabled=false -Dtest=AdminRefundDecisionSqlServerTest,RedisAdminRefundCacheTest,AdminRefundJsonTest,AdminRefundDecisionServiceTest,AdminRefundOpenApiContractTest
```

`src/test/resources/refund/khanh04/web-acceptance.py` uses the complete packaged WAR, a fresh isolated Tomcat base and fixed loopback database. Provide disposable OWNER/ADMIN login settings and `TC_ADMIN_REFUND_BROWSER_SCRIPT=src/test/resources/refund/khanh04/browser-acceptance.cjs` to include UI acceptance. The script closes its own Tomcat process when finished. Generated reports and screenshots are under ignored `target/khanh04-*`; CI uploads those reports/screenshots and excludes credentials, fixture identity files and the WAR.

The Azure installer/verification scripts in the same directory enforce the exact project host/database, require matching 031–039 checksums, refuse altered installed migration history and make no business-data updates. Hash validation accepts equivalent Git LF/CRLF checkouts without rewriting recorded history; other content edits still fail. Migrations 040–042 are already committed on `dev` and provide the shared runtime projection and Event → User lock order required by the new contract.
