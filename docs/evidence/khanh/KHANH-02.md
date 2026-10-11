# KHANH-02 acceptance — 2026-10-11

Issue [#22](https://github.com/devonxjz/TicketCenter/issues/22).
Branch `feat/khanh/khanh-02-qr-checkin` starts from `origin/dev` at `cdaa0b4`;
PR targets `dev`. Runbook: `src/test/resources/checkin/khanh02/README.md`.

## Delivered behavior

Signed HMAC-SHA256 QR decoder, the three requested DTOs, atomic repository/SP,
service, POST scan and GET recent-history servlets, Redis lock/cache invalidation
and responsive UI-16 with camera, local-image decoder, manual code, Vietnamese
feedback/beep and safe replay after an uncertain network result.

All IDs are UUIDs. SPEC's canonical ACTIVE/USED states and
tc_ticket_check_in_logs are retained instead of changing the Ticket model to
match illustrative issue names. Writes use SQL through the CHECK_IN JPA
transaction principal; no JPA dirty-write or issuance code is added. Current
user/organization membership, event ownership and SQL server time remain
authoritative even on a durable replay or Redis outage.

| Acceptance | Evidence |
|---|---|
| SCN-01 valid signed QR | Real JPA/SQL service test and full WAR HTTP scan; ticket USED, one SUCCESS log and audit |
| SCN-02 one-bit signature change | SignedQrDecoderTest; fake scan logs INVALID_CODE with null ticket identity |
| SCN-03 used ticket | Original entry time returned with 409; repeated idempotency key replays the original 200 without another log |
| SCN-04 wrong event | SQL tests return WRONG_EVENT and strip foreign ticket/name/zone; no admission |
| SCN-05 simultaneous scans | Two actors, two threads, barrier and real SQL connections: one success and one already-used; identical-key contention produces one mutation |
| Rollback | Fault-injecting log trigger proves ticket, audit, log and request receipt all roll back |
| Security | CSRF, expired/anonymous sessions, outsiders, disabled user, revoked membership even on replay, input limits, duplicate JSON fields and least-privilege SQL denial |
| Event/ticket boundaries | Closed/early/ended/cancelled event; REFUND_PENDING/REFUNDED/INVALIDATED tickets denied |
| Redis | Real TTL 5s/3,600s, token-owner unlock, QR/detail/counter invalidation and outage fallback |
| Browser logic | Uncertain response retains same key/body and blocks another scan; 503 replay, red duplicate result, session expiry, local-file decoding controls and textContent rendering |

## Local verification

- **46 new Java tests + 14 existing check-in servlet tests passed**, zero
  failures/errors/skips with dedicated SQL Server and Redis; log
  `target/khanh02-feature-final.log`. The 46 include 14 SQL integration cases,
  two real concurrency cases and three Redis cases.
- Full default regression: **514 tests discovered, 435 executed, 79 skipped**,
  zero failures/errors, BUILD SUCCESS; `target/khanh02-regression.log`.
  Skips are external-service opt-ins, including the feature tests separately
  executed above. Skipped tests are not counted as passed.
- Node regression: **63 passed, eight existing opt-in safety cases skipped**,
  zero failures. All **seven scanner UI tests ran and passed**.
- Full production WAR on Tomcat 11.0.25: **24 HTTP/JSP checks passed**, covering
  real auth/login JSP, current membership, scanner routes/assets, signed QR,
  durable replay, duplicate original time, fake signature, CSRF and private
  history; sanitized `target/khanh02-http-result.json`.
- Actual browser acceptance passed: a signed PNG was decoded by the vendored
  library and admitted through the full WAR; SQL confirmed a second SUCCESS
  alongside the earlier HTTP ticket. Manual re-scan displayed red feedback and
  the original time. UI initialized correctly at narrow and desktop widths;
  result text and history rendered in Vietnamese. Private proof:
  `docs/handoff/khanh02-ui-success.png`. Physical camera hardware was not tested;
  its adapter is implemented with html5-qrcode and file decoding was exercised.

## Azure contract

Migration `030_khanh02_signed_checkin` installed atomically on the existing
project database on 2026-10-11. Recorded SHA-256:
`18a208c9f431e7037b10ce21ca89682b33ec62ef5457fb0a83d14057cc1fc3cc`.
Read-only verification confirmed UUID ticket IDs, both procedures, durable
receipt table, zero duplicate successful-ticket logs and EXECUTE grants to the
existing check-in role. Existing business rows, logins and earlier migrations
were not changed; synthetic ticket mutations are isolated to loopback test DB.

## CI / review

The dedicated KHANH-02 workflow recreates real SQL/Redis acceptance and runs
full-WAR HTTP verification without Azure secrets. It explicitly requires zero
skips for SQL, concurrency and Redis suites. CI run links and the PR are recorded
after GitHub completes. Human approving review remains required before merge.
