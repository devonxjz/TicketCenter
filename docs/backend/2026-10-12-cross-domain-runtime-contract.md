# Cross-domain runtime reconciliation — VUONG-01/06

Integration base: `af2ded209122d8c5604ae4ccee2e765fefde190e` (`dev`).
Inspected producers: PR50 `908c284`, PR61 `8cd7322`, PR65 `43ac0ae`.
This is a tested interoperability slice of #28/#33, not whole-product acceptance.

## Database and principals

Use canonical UUID identifiers, `tc_*` tables and integer VND. Install canonical
001–008, retained 029 hold/refund and 030 order/signed check-in migrations before
031–039, then additive 040–042. Do not reinstall historical procedures over
the later migrations. The isolated CI installer follows this order.

040 exposes payment/ticket projections through the order ownership root; BUYER
requires the authenticated session actor. It does not grant direct financial
table access. Anonymous, signature-verified VNPay callback lookup, ticket identity
generation and result application use WORKER; checkout reads and free completion
use BUYER. Configure both `db.buyer.*` and `db.worker.*` credentials.

Payment capture writes `accepted_payment_id` and issues tickets atomically through
031. Refund requests use that actual accepted payment, never a fixture-added link.
Free completion calls `usp_CompleteFreeOrder` with nonempty ticket identities and
does not create a captured payment. Callback replays issue once.

041 preserves `usp_CreateTicketHold`'s optional output ID and suppressed result
contract used by the retained hold facade. Expired-hold release suppresses its
nested result. Same-buyer hold/refund mutations acquire the transaction-owned
`hold-user:<uuid>` application lock before durable idempotency keys, then lock
Event before User. Refund performs an unlocked initial session check and repeats
it under User lock after Event. The deadline is checked after lock waits.
042 aligns check-in's Event → User order as well, including users who are both
buyers and staff; current active membership is still validated before replay.

JDBC reads SQL UTC timestamps with an explicit UTC calendar. The cross-domain
test suite forces Asia/Bangkok even on UTC CI hosts.

## Ticket signing and routing

Both issuer and scanner resolve the same UTF-8 key: `TC_TICKET_SIGNING_SECRET`,
then `ticket.qr.secret`, then `app.secret.key`; minimum 32 UTF-8 bytes. A weak
explicit key fails closed. There is no development default. Existing deployment
keys must be preserved when deploying; changing a key invalidates existing QR.

Envelope: unpadded base64url JSON + dot + unpadded HMAC-SHA256 signature. Exactly
`id` and `name` string fields. Bounds: 4,096 envelope characters, 3,000 payload
bytes, 373 name characters (250 title + 3 separator + 120 zone). SQL decides
admission; the signed name is display metadata. Existing SQL display columns
retain a 200-character limit, so longer display labels can be truncated.

Named report/refund forwards establish and restore the authenticated SQL actor,
including failure paths, independently of annotation filter ordering. Report
forwarding also preserves the canonical path attribute; query parameters cannot
replace the organization in the path.

## Redis reconciliation

| Operation | Namespace | Retention / lock | Authority |
| --- | --- | --- | --- |
| Payment init | `tc:idem:vnpay:init:<actor>:<key>` | default 10 minutes; supplied hold retention; pending/completed claim | SQL payment state; Redis claim required by payment-init service |
| Check-in | `tc:idem:checkin:<actor>:<key>` | 3,600 seconds | Durable SQL replay |
| Check-in lock | `tc:lock:ticket-checkin:<ticket>` | 5 seconds; token-checked release | SQL ticket lock |
| Refund request | `tc:idem:refund-request:<actor>:<key>` | 86,400 seconds | Durable SQL replay |
| Refund ticket-set lock | `tc:lock:refund-tickets:<sorted-ticket-ids>` | 10 seconds; token-checked release | SQL ticket locks |

Check-in/refund caches tolerate Redis failure. They invalidate affected ticket
detail/QR caches after committed mutations; refund also invalidates buyer ticket
lists and the admin pending count. Cache publication failure cannot undo SQL.
This table reconciles inspected source; it does not claim live Redis or all
cross-module invalidation paths were accepted in this slice.

## Accepted verification and remaining gates

See [integration evidence](../evidence/vuong/VUONG-CROSS-DOMAIN-INTEGRATION.md).
The executable SQL suite includes actual hold facade → order facade → payment
init → capture → ticket reads, capture → refund, signed issuer → actual scanner,
free issuance, callback replay, cross-owner denial and synchronized lock ordering.

Remaining #28/#33 gates include final response-schema reconciliation in OpenAPI,
all owners' remaining business contracts, restricted production-principal full
HTTP flows, provider callbacks/reconciliation workers, refund approval/transfer,
cancellation/settlement completion and full product/browser acceptance. Existing
OpenAPI placeholders are not declared complete by this integration note.
