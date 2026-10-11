# SQL catalog inventory — 2026-10-11

Source: NEW marked local schema fixture; no shared/Azure database. Existence is not behavior acceptance. The fixture includes adapters and verification-only tables.

| Kind | Present |
|---|---:|
| C | 67 |
| D | 132 |
| F | 62 |
| FN | 3 |
| IF | 8 |
| P | 28 |
| PK | 42 |
| TR | 31 |
| U | 42 |
| UQ | 21 |
| V | 21 |
| Non-PK indexes (includes unique constraints) | 59 |
| Non-PK, non-unique indexes | 30 |

## 15-class persistence mapping

| Class | Physical mapping | Existence only |
|---|---|---|
| User | tc_users | PRESENT |
| Organization | tc_organizations | PRESENT |
| Event | tc_events | PRESENT |
| Zone | tc_zones | PRESENT |
| Seat | tc_seats | PRESENT |
| TicketHold | tc_ticket_holds | PRESENT |
| TicketHoldItem | tc_ticket_hold_items | PRESENT |
| Order | tc_orders | PRESENT |
| OrderItem | tc_order_items | PRESENT |
| Ticket | tc_tickets | PRESENT |
| Payment | tc_payments | PRESENT |
| Coupon | tc_coupons | PRESENT |
| CommissionRule | tc_commission_rules | PRESENT |
| Refund | tc_refund_obligations | PRESENT |
| Settlement | tc_settlements | PRESENT |

Refund maps to the finance obligation ledger; legacy tc_refunds represents owner attempts and is normalized through adapters. Organization lifecycle, role synchronization, Email/QR/payment workers and connected E2E still require owner behavior evidence. Technical tables do not add domain classes.

## SPEC SQL names

PRESENT means the exact named object exists, not that its parameters, grants or behavior satisfy SPEC. MISSING may have a legacy equivalent requiring owner confirmation.

| SPEC ID | Required name | Exact name |
|---|---|---|
| V01 | vw_PublicEvents | PRESENT |
| V02 | vw_ZoneInventory | PRESENT |
| V03 | vw_OrderFinancialSummary | PRESENT |
| V04 | vw_EventSalesReport | PRESENT |
| V05 | vw_CheckInHistory | PRESENT |
| V06 | vw_TicketDetails | PRESENT |
| V07 | vw_RefundOverview | PRESENT |
| V08 | vw_SettlementPayoutBalance | PRESENT |
| V09 | vw_CouponUsage | PRESENT |
| V10 | vw_OrganizationMembers | PRESENT |
| SP01 | usp_ApproveOrganization | PRESENT |
| SP02 | usp_CreateTicketHold | PRESENT |
| SP03 | usp_ApplyOrderCoupon | PRESENT |
| SP04 | usp_CheckInTicket | PRESENT |
| SP05 | usp_RequestTicketRefund | PRESENT |
| SP06 | usp_CreateOrderFromHold | PRESENT |
| SP07 | usp_ReleaseTicketHold | PRESENT |
| SP08 | usp_BeginOrderPayment | PRESENT |
| SP09 | usp_ApplyPaymentResult | PRESENT |
| SP10 | usp_DecideRefund | PRESENT |
| SP11 | usp_ApplyRefundResult | PRESENT |
| SP12 | usp_PublishEvent | PRESENT |
| SP13 | usp_CancelEvent | PRESENT |
| SP14 | usp_RecalculateSettlement | PRESENT |
| SP15 | usp_ConfirmSettlement | PRESENT |
| SP16 | usp_RecordPayout | PRESENT |
| SP17 | usp_ProcessCancelledOrder | PRESENT |
| F01 | fn_CalculateCouponDiscount | PRESENT |
| F02 | fn_CalculateCommission | PRESENT |
| F03 | fn_GetZoneAvailability | PRESENT |
| F04 | fn_GetRefundableTickets | PRESENT |
| F05 | fn_GetOrganizationRevenue | PRESENT |
| F06 | fn_AllocateTicketPaidAmounts | PRESENT |
| F07 | fn_GetEventCheckInWindow | PRESENT |
| F08 | fn_GetCouponEligibility | PRESENT |
| F09 | fn_GetSettlementBlockers | PRESENT |
| F10 | fn_GetOrganizationCashFlow | PRESENT |
| TR01 | trg_Zone_ProtectPublishedLayout | PRESENT |
| TR02 | trg_Seat_ProtectPublishedLayout | PRESENT |
| TR03 | trg_CommissionRule_ProtectAppliedTerms | PRESENT |
| TR04 | trg_SettlementOrderSnapshot_ProtectConfirmedAmounts | PRESENT |
| TR05 | trg_Event_AuditStatusChange | PRESENT |
| TR06 | trg_UserOrganizationRole_ProtectLastManager | PRESENT |
| TR07 | trg_Coupon_ProtectUsageLimit | PRESENT |
| TR08 | trg_Ticket_ProtectIssuedSnapshot | PRESENT |
| TR09 | trg_Settlement_ProtectConfirmedSnapshot | PRESENT |
| TR10 | trg_AuditLog_AppendOnly | PRESENT |
| IX01 | IX_Event_Status_StartTime | PRESENT |
| IX02 | IX_Seat_Zone_Status | PRESENT |
| IX03 | IX_Order_User_CreatedAt | PRESENT |
| IX04 | IX_Payment_Status_CreatedAt | PRESENT |
| IX05 | IX_TicketCheckInLog_Event_ScannedAt | PRESENT |
| IX06 | IX_TicketHold_Status_ExpiresAt | PRESENT |
| IX07 | IX_Order_Event_Status_PaidAt | PRESENT |
| IX08 | IX_RefundTransferLog_Status_CreatedAt | PRESENT |
| IX09 | IX_CouponRedemption_Coupon_Status | PRESENT |
| IX10 | IX_Refund_Status_CreatedAt | PRESENT |
| IX11 | IX_Ticket_OrderItem_Status | PRESENT |
| IX12 | IX_Payout_Settlement_Status | PRESENT |
| IX13 | IX_AuditLog_Aggregate_CreatedAt | PRESENT |
| IX14 | IX_CommissionRule_Organization_EffectiveFrom | PRESENT |
| IX15 | IX_Event_Organization_Status_StartTime | PRESENT |

Exact named objects: 62/62. Transactions and constraint groups require separate behavioral evidence; object totals cannot close #33.
