# VNPAY sandbox refunds

The worker sends refund requests only to `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`.
It has no bank payout adapter. Automatic simulated refunds are no longer connected to the application scheduler.

Apply migration `063_vnpay_sandbox_refund_provenance.sql` before starting checkout against the sandbox.
New sandbox payment redirects store their exact signed merchant and `vnp_CreateDate` before returning a redirect.
Historical payments and demo references are intentionally not backfilled. The refund gateway requires a captured VND
payment, numeric VNPAY transaction reference, matching sandbox merchant, and this explicit provenance.
The sandbox scheduler filters candidates through `vw_VnpaySandboxRefundCandidates` for its configured merchant
before claiming any attempt. It repeats this check before the dispatch procedure, so legacy/demo obligations and
payments belonging to another merchant do not receive dispatches or reconciliation mutations.

To enable the sandbox worker, use real data mode, configure the worker database principal, the existing sandbox
`vnpay.tmn.code` and `vnpay.hash.secret`, set `refund.sandbox.enabled=true`, and set
`refund.sandbox.server.ip` to the server's address accepted by VNPAY. The flag defaults to false.
Sandbox credentials and an active merchant API configuration must be supplied separately; no credentials are generated
or embedded by this change. The endpoint cannot be changed to production through configuration.

Each attempt uses its immutable database UUID without hyphens as `vnp_RequestId`, and its persisted creation time
as request date. Existing dispatch claims submit at most once; recovered claims only reconcile the same attempt.
A completed refund requires a valid HMAC SHA-512 response, matching merchant, original transaction reference,
amount, full/partial refund type, and an order description containing the exact attempt identifier.
Only response code `00` plus refund transaction status `00` completes the obligation; a signed status `09` can reject it.
Pending, malformed, unsigned, mismatched, timeout and ambiguous results stay `NEEDS_RECONCILIATION`.

VNPAY's public `querydr` contract identifies a payment transaction; it does not provide enough correlation to prove
completion of a specific refund attempt whose direct response was lost. This adapter deliberately does not mark a
refund successful from the original payment's captured status. Such cases require merchant/provider evidence and
an audited reconciliation process. Repeated checkout initiation after cache loss or with another idempotency key reuses
the original immutable sandbox creation date and its fixed fifteen-minute expiry, without extending the payment window.

Free orders do not enter this positive-amount refund adapter. Their cancellation completion must be handled within
the database's zero-value order invariants, without contacting a payment gateway.

Protocol reference: [VNPAY querydr and refund API](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).
