# Canonical Vương financial SQL binding

Approved schema decision: `2026-10-10-vuong-schema-decision.md`. Migration `database/migrations/027_vuong_finance.sql` uses UUID/tc_* and integer VND. It never creates the owners' base schema, selects a database, creates a login, or writes legacy BIGINT tables. Prerequisite column/type checks fail with 52700. Execute with a GO-aware, stop-on-error migration runner (`sqlcmd -b -I`); supply its target explicitly. Models and migration 026 remain unchanged.

The test fixture `database/tests/vuong/001_uuid_test_schema.sql` requires an empty current database named `TC_Vuong_Test_*`, creates all financial prerequisites in one transaction and marks `tc_vuong_test_fixture.fixture_key='vuong-finance-v1'`. It is an isolated financial catalog, not a fresh installation of the other owners' full application. Secondary hold/zone/seat/coupon associations are optional fixture conveniences; their owners' full schema remains a separate installation contract.

## Types and authorization

All identifiers are `uniqueidentifier`; money is `decimal(19,0)` and HTTP decimal strings; rate is `decimal(12,4)` in [0,100]. Timestamps are UTC `datetime2(3)`. Callers validate exact money/rate inputs and must reject or explicitly normalize sub-millisecond Instants before establishing a request fingerprint. SQL parameter conversion is not an HTTP validation layer.

Each write checks `tc_users.id/status='ACTIVE'/platform_role='ADMIN'`, plus `auth_version` when supplied. Java must always bind the authenticated session version and check current user before queries/cache/CSV. SQL roles cannot distinguish end users sharing a connection; manager tenant scope remains an application check on current active MANAGER membership. NULL organization scope on report functions is for a previously authorized platform admin only.

## Commission mutations

```sql
dbo.usp_CreateCommissionRule(
 @organization_id uniqueidentifier, @actor_id uniqueidentifier,
 @idempotency_key uniqueidentifier, @rate_percent decimal(12,4),
 @fixed_fee decimal(19,0), @effective_from datetime2(3),
 @effective_to datetime2(3), @auth_version int=NULL)
dbo.usp_UpdateCommissionRule(
 @rule_id uniqueidentifier, @organization_id uniqueidentifier,
 @actor_id uniqueidentifier, @idempotency_key uniqueidentifier,
 @rate_percent decimal(12,4), @fixed_fee decimal(19,0),
 @effective_from datetime2(3), @effective_to datetime2(3),
 @auth_version int=NULL)
```

One result row: `rule_id, organization_id, rate_percent, fixed_fee, effective_from, effective_to, associated_events_count (bigint), is_replay (bit)`.

`tc_finance_mutations` globally owns each idempotency UUID with actor, operation, SHA256 normalized terms, resource UUID and the original response terms. Same key/different actor, operation or terms conflicts. Replay returns original saved terms even after a later edit; associated event count is a current informational count. Successful rule mutation, replay record and audit commit together. Once an Event references a rule, every terms edit and deletion is forbidden by procedure and trigger, including effective-date edits, following the approved #29 decision. No delete route is defined.

## Settlement and payout mutations

```sql
dbo.usp_RecalculateSettlement(@event_id uniqueidentifier,
 @actor_id uniqueidentifier, @auth_version int=NULL, @emit_result bit=1)
dbo.usp_ConfirmSettlement(@settlement_id uniqueidentifier,
 @actor_id uniqueidentifier, @auth_version int=NULL)
dbo.usp_RecordPayout(@settlement_id uniqueidentifier,
 @payout_id uniqueidentifier, @actor_id uniqueidentifier,
 @amount decimal(19,0), @reference nvarchar(200)=NULL,
 @verified_result varchar(20)=NULL, @fingerprint varbinary(32)=NULL,
 @auth_version int=NULL)
```

SP14/15 return one row: `settlement_id, event_id, status, gross_revenue, total_refund, total_commission, net_payable, paid_amount, pending_amount, available_to_pay, confirmed_at, is_replay`.

No eligible paid orders: SP14 returns NULL `settlement_id`, DRAFT/all-zero amounts and creates no header. Eligible zero-value orders still have snapshots. Recalculate rejects CONFIRMED/PAID. Confirm locks the Event, header, relevant order/payment/refund/refund-transfer-evidence ranges and cancellation work; recalculates under the same transaction and checks blockers. Already CONFIRMED/PAID returns the frozen snapshot. A zero payable is PAID without a transfer.

SP16 returns `payout_id, settlement_id, amount, status, verification_state, provider_reference, processed_at, is_replay, settlement_status, net_payable, paid_amount, pending_amount, available_to_pay`.

NULL `verified_result` reserves a new PENDING transfer. `UNKNOWN` keeps PENDING/result PENDING and sets `verification_state=UNKNOWN`, leaving the full reservation. SUCCEEDED/FAILED are final and update the ledger once. All new payout IDs are rejected while a settlement has any PENDING transfer (`PAYOUT_RECONCILIATION_REQUIRED`); same-ID replay/reconciliation remain valid. This includes reservation-only `verification_state=PENDING`: a provider may have transferred money before a crash or failed final-result commit, so absence of a stored UNKNOWN result does not authorize a second transfer. FAILED releases pending but retains history. A successful full payment becomes PAID. Multiple partial payouts remain supported sequentially after each prior result is final.

On reserve, `reference` is the requested reference and is stored as `requested_reference`; on verification, it is the provider result reference and may differ. A final replay requires the same provider reference. Supply the same explicit 32-byte fingerprint across reserve/result/reconcile when provider references differ. Default fingerprint hashes settlement/amount/reference and is suitable only when that reference stays the same. Backend fingerprint includes immutable destination terms if a real bank adapter is added; current Organization model has no bank columns, so this migration does not invent raw bank storage.

Canonical `tc_settlement_transfer_logs` is the only payout source. Additional columns are `request_fingerprint binary(32), requested_reference nvarchar(200), verification_state varchar(20)`. No `tc_payouts` write, dual-ledger sum, or legacy alias is used. Canonical snapshot/header and log totals are checked by triggers; snapshot moves between parents inspect both parents. Changes to accepted ticket payment/order financial data and customer refunds after confirmation are forbidden, including the customer's `current_attempt_id` and any INSERT/UPDATE/DELETE of its transfer evidence. Evidence moves inspect both old and new refund parents. Later nonaccepted captures and compensation refunds/transfer evidence remain possible because they do not change the ticket snapshot.

The explicit test adapter may use `tc_vuong_simulated_transfers(payout_id UUID PK, fingerprint binary(32), result varchar(20) SUCCEEDED/FAILED/UNKNOWN, provider_reference varchar(100) NULL, created_at datetime2(3), call_count int DEFAULT1 CHECK=1)`. Rows are append-only. SELECT/INSERT grants are supplied only in `TC_Vuong_Test_*`. Java must also verify the local dedicated fixture/marker; this is not a production payment provider.

Read every JDBC result/update count through COMMIT before reporting success. Each SP uses XACT_ABORT and TRY/CATCH rollback; do not put unrelated owner changes into an outer JDBC transaction around it. A competing caller may deadlock when an owner write takes rows before Event; abort is safe, and a bounded retry must reread authoritative state.

## Read projections and functions

`fn_CalculateCommission(@remaining_amount decimal(19,0), @rate_percent decimal(12,4), @fixed_fee decimal(19,0)) -> decimal(19,0)`: missing/invalid inputs return NULL, zero remaining returns zero, otherwise HALF_UP to VND and cap at remaining. Fees are computed per accepted paid Order. A positive paid Order must reference its own CAPTURED payment with matching total. Compensation capture/refunds do not become ticket revenue.

| Object | Columns |
|---|---|
| `vw_OrderFinancialSummary` | order_id, owner_id, event_id, organization_id, status, paid_at, subtotal_amount, discount_amount, total_amount, ticket_revenue, ticket_refund, remaining_amount, commission_amount, net_amount, compensation_captured, compensation_refund |
| `vw_EventSalesReport` | event_id, organization_id, event_title, event_status, end_time, paid_order_count, ticket_count, valid_ticket_count, refunded_ticket_count, gross_revenue, total_refund, total_commission, net_payable, settlement_id, settlement_status, is_confirmed |
| `vw_SettlementPayoutBalance` | settlement_id, event_id, organization_id, status, gross_revenue, total_refund, total_commission, net_payable, paid_amount, pending_amount, available_to_pay, confirmed_at |

`fn_GetOrganizationRevenue(@organization_id UUID, @from_utc datetime2(3), @to_utc datetime2(3))`: one row/Event including zeros; columns `event_id, organization_id, paid_order_count, gross_revenue, total_refund, remaining_amount, total_commission, net_payable`. Cohort is PAID `Order.paid_at` in [from,to); related successful customer refund history has `processed_at<to`. It does not use today's refunded total for an earlier cutoff.

`fn_GetOrganizationCashFlow` has the same inputs and row grain; columns `event_id, organization_id, ticket_captured, compensation_captured, ticket_refund, compensation_refund, net_cash_flow`. Capture uses Payment.paid_at and refund uses successful transfer processed_at, both [from,to). The refund parent amount is counted once via EXISTS, so failed/multiple history does not multiply money. A refund outside its order's payment cohort still counts in cashflow. Empty organization returns no Event rows; repository totals must coalesce to zero. Both functions allow NULL organization for authorized all-org administration and return no rows for an invalid interval.

`fn_GetSettlementBlockers(@event_id UUID,@now_utc datetime2(3))` returns `blocker_type varchar(40),object_id UUID,detail nvarchar(200)`. Types: EVENT_NOT_ENDED, ORDER_PAYMENT, PAYMENT, REFUND, REFUND_VERIFICATION, CANCELLATION_WORK, CANCELLED_REVENUE, INVALID_FINANCIALS. CANCELLED still awaits scheduled endTime. Unfinished `tc_outbox` EVENT_CANCELLED work is matched by valid JSON `$.eventId`; cancelled accepted orders with remaining revenue also block, covering obligations the worker has not created. COMPLETED positive refunds require successful timestamped transfer history and, when currentAttemptId is supplied, that current attempt must be verified SUCCEEDED. Older FAILED attempts do not block a resolved refund. Owner cancellation payload integration still needs the documented eventId contract.

Audit columns are the locked model aliases: `id, actor_id, action, entity_type, entity_id, metadata_json, created_at, updated_at`. Procedure audit payloads only contain typed ID/state/money, never request/bank/credential data. Application projections must allowlist metadata rather than return raw JSON. UPDATE/DELETE is blocked.

## Errors and permissions

| SQL error number | Application interpretation |
|---|---|
| 52700 | Migration prerequisite/configuration failure |
| 52701 | Current ADMIN/version authorization failed; Java distinguishes unauthenticated/stale/forbidden before calling |
| 52702 | Input/financial invariant invalid; message is a stable internal code |
| 52704 | Missing organization/rule/event/settlement; message identifies the resource code |
| 52709 | Conflict; message is the stable code such as IDEMPOTENCY_CONFLICT, RULE_ALREADY_ATTACHED_TO_EVENTS, SETTLEMENT_NOT_DRAFT, SETTLEMENT_BLOCKED, PAYOUT_TERMS_CONFLICT, PAYOUT_RESULT_CONFLICT, PAYOUT_EXCEEDS_BALANCE, PAYOUT_RECONCILIATION_REQUIRED |

Do not expose arbitrary SQL exception text. Map only the approved number/code allowlist to fixed safe HTTP errors.

Roles `tc_platform_admin,tc_manager,tc_checkin,tc_buyer` are created if absent, with no login/password. Admin can execute financial mutation SPs/read financial projections/audit, while direct financial DML is denied. Manager can read report views/functions but cannot execute payout/confirmation. Check-in/buyer are denied financial projections. Current authorization can read only `tc_users(id,status,platform_role,auth_version,full_name)` under admin/manager roles; password fields are not granted. The application still enforces each end-user's tenant scope.

## Reproduction

Create a fresh local `TC_Vuong_Test_*` database explicitly, then apply 001 fixture and 027 with `sqlcmd -S localhost -E -C -b -I -d <name> -i <file>`. Run `finance_contract.sql`, `finance_guardrails.sql`, `finance_temporal.sql`, `finance_permissions.sql` against that target. `finance_evidence_guardrails.sql -v Case=ALL` additionally requires the dedicated `TC_Vuong_Test_20261010_Finance` fixture/marker and exercises reservation-only crash prevention, same-ID reconciliation, current-attempt/transfer-evidence freezes and allowed late compensation. `run-finance-concurrency.ps1 -DatabaseName <name>` drives six independent two-session races, including refund-evidence/confirmation in both write orders, and asserts ledger/source invariants. It guards local server/prefix and retains logs in a unique temporary directory.

Tests use no SMTP or actual bank provider. Happy-path and temporal suites roll their data back; negative/concurrency/permission cases retain only isolated test data. No script drops a database or table. Production owner migration installation and full owner E2E remain separate gates.
