/*
 * 005_create_views
 *
 * Creates or alters all 16 business views.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* View: vw_CheckInHistory */
CREATE OR ALTER VIEW dbo.vw_CheckInHistory
AS
SELECT ci.id check_in_id, ci.event_id, e.organization_id, ci.actor_id, ci.scanned_at,
       ci.result, ci.ticket_id, oi.zone_name_snapshot, oi.seat_label_snapshot
FROM dbo.tc_check_ins ci JOIN dbo.tc_events e ON e.id = ci.event_id
LEFT JOIN dbo.tc_tickets t ON t.id = ci.ticket_id
LEFT JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id;

GO

/* View: vw_CouponUsage */
CREATE OR ALTER VIEW dbo.vw_CouponUsage
AS
WITH u AS (
    SELECT coupon_id,
           SUM(CASE WHEN status = 'RESERVED' THEN 1 ELSE 0 END) reserved_uses,
           SUM(CASE WHEN status = 'CONSUMED' THEN 1 ELSE 0 END) consumed_uses,
           SUM(CASE WHEN status = 'RELEASED' THEN 1 ELSE 0 END) released_uses
    FROM dbo.tc_coupon_redemptions GROUP BY coupon_id
)
SELECT c.id coupon_id, c.organization_id, c.code, c.active, c.valid_from, c.valid_to, c.max_uses,
       COALESCE(u.reserved_uses, 0) reserved_uses, COALESCE(u.consumed_uses, 0) consumed_uses,
       COALESCE(u.released_uses, 0) released_uses,
       CASE WHEN c.max_uses > COALESCE(u.reserved_uses, 0) + COALESCE(u.consumed_uses, 0)
            THEN c.max_uses - COALESCE(u.reserved_uses, 0) - COALESCE(u.consumed_uses, 0) ELSE 0 END available_uses
FROM dbo.tc_coupons c LEFT JOIN u ON u.coupon_id = c.id;

GO

/* View: vw_EventCancellationExceptions */
CREATE OR ALTER VIEW dbo.vw_EventCancellationExceptions
AS
    SELECT o.event_id, CAST('USED_TICKET' AS varchar(40)) exception_type,
           t.id exception_id, o.id order_id, t.status
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED' AND t.status = 'USED'
    UNION ALL
    SELECT o.event_id, CAST('REFUND_RESULT' AS varchar(40)),
           r.id, o.id, r.status
    FROM dbo.tc_refunds r
    JOIN dbo.tc_refund_requests rr ON rr.id = r.refund_request_id
    JOIN dbo.tc_orders o ON o.id = rr.order_id
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED' AND rr.reason_type = 'EVENT_CANCELLATION'
      AND r.status IN ('FAILED', 'UNKNOWN');

GO

/* View: vw_EventCancellationProgress */
CREATE OR ALTER VIEW dbo.vw_EventCancellationProgress
AS
    SELECT e.id event_id, e.status event_status,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o WHERE o.event_id = e.id) total_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id AND (
                EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
                OR EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                           WHERE oi.order_id = o.id AND t.status = 'ACTIVE')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_requests rr WHERE rr.order_id = o.id AND rr.status = 'PENDING')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_requests rr
                           LEFT JOIN dbo.tc_refunds r ON r.refund_request_id = rr.id
                           WHERE rr.order_id = o.id AND rr.reason_type = 'EVENT_CANCELLATION'
                             AND rr.status = 'APPROVED' AND (r.id IS NULL OR r.status = 'PENDING')))) pending_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                              WHERE oi.order_id = o.id AND t.status NOT IN ('REFUNDED', 'INVALIDATED'))
              AND NOT EXISTS (SELECT 1 FROM dbo.vw_EventCancellationExceptions x WHERE x.order_id = o.id)) completed_orders,
           (SELECT COUNT_BIG(*) FROM dbo.vw_EventCancellationExceptions x WHERE x.event_id = e.id) exception_count,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refund_requests rr JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION') requests_created,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refunds r JOIN dbo.tc_refund_requests rr ON rr.id = r.refund_request_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION' AND r.status = 'SUCCEEDED') refunds_succeeded,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refunds r JOIN dbo.tc_refund_requests rr ON rr.id = r.refund_request_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION' AND r.status = 'PENDING') refunds_pending
    FROM dbo.tc_events e;

GO

/* View: vw_EventCancellationWork */
CREATE OR ALTER VIEW dbo.vw_EventCancellationWork
AS
    SELECT h.event_id, CAST('HOLD' AS varchar(10)) work_type, h.id work_id
    FROM dbo.tc_ticket_holds h
    JOIN dbo.tc_events e ON e.id = h.event_id
    WHERE e.status = 'CANCELLED' AND h.status = 'ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_orders o WHERE o.hold_id = h.id)
      AND EXISTS (
          SELECT 1 FROM dbo.tc_outbox x
          WHERE x.event_type = 'EVENT_CANCELLED' AND x.aggregate_id = h.event_id
            AND x.status <> 'PUBLISHED')
    UNION ALL
    SELECT o.event_id, CAST('ORDER' AS varchar(10)), o.id
    FROM dbo.tc_orders o
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED'
      AND EXISTS (
          SELECT 1 FROM dbo.tc_outbox x
          WHERE x.event_type = 'EVENT_CANCELLED' AND x.aggregate_id = o.event_id
            AND x.status <> 'PUBLISHED')
      AND (
          EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
          OR EXISTS (
              SELECT 1 FROM dbo.tc_tickets t
              JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
              WHERE oi.order_id = o.id AND t.status = 'ACTIVE')
          OR EXISTS (
              SELECT 1 FROM dbo.tc_refund_requests rr
              WHERE rr.order_id = o.id AND rr.status = 'PENDING'));

GO

/* View: vw_EventSalesReport */
CREATE OR ALTER VIEW dbo.vw_EventSalesReport
AS
WITH orders AS (
    SELECT s.event_id, COUNT_BIG(*) paid_order_count,
           SUM(s.ticket_gross_amount) gross_revenue,
           SUM(s.successful_ticket_refund_amount) total_refund,
           SUM(dbo.fn_CalculateCommission(s.remaining_ticket_amount, r.rate_percent, r.fixed_fee)) projected_commission,
           SUM(s.remaining_ticket_amount - dbo.fn_CalculateCommission(s.remaining_ticket_amount, r.rate_percent, r.fixed_fee)) projected_net
    FROM dbo.vw_OrderFinancialSummary s
    JOIN dbo.tc_events e ON e.id = s.event_id
    JOIN dbo.tc_commission_rules r ON r.id = e.commission_rule_id
    WHERE s.status = 'PAID'
    GROUP BY s.event_id
), tickets AS (
    SELECT o.event_id,
           SUM(CASE WHEN t.status = 'ACTIVE' THEN 1 ELSE 0 END) active_tickets,
           SUM(CASE WHEN t.status = 'USED' THEN 1 ELSE 0 END) used_tickets,
           SUM(CASE WHEN t.status IN ('REFUNDED', 'INVALIDATED') THEN 1 ELSE 0 END) inactive_tickets
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id
    GROUP BY o.event_id
)
SELECT e.id event_id, e.organization_id, e.title, e.status,
       COALESCE(o.paid_order_count, 0) paid_order_count,
       COALESCE(t.active_tickets, 0) active_tickets,
       COALESCE(t.used_tickets, 0) used_tickets,
       COALESCE(t.inactive_tickets, 0) inactive_tickets,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN s.gross_revenue ELSE COALESCE(o.gross_revenue, 0) END gross_revenue,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN s.total_refund ELSE COALESCE(o.total_refund, 0) END total_refund,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN s.total_commission ELSE COALESCE(o.projected_commission, 0) END total_commission,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN s.net_payable ELSE COALESCE(o.projected_net, 0) END net_payable,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_settlement_snapshot
FROM dbo.tc_events e
LEFT JOIN orders o ON o.event_id = e.id
LEFT JOIN tickets t ON t.event_id = e.id
LEFT JOIN dbo.tc_settlements s ON s.event_id = e.id;

GO

/* View: vw_FailedCompensationAttempts */
CREATE OR ALTER VIEW dbo.vw_FailedCompensationAttempts
AS
    SELECT DISTINCT r.payment_id, p.order_id
    FROM dbo.tc_refunds r
    JOIN dbo.tc_payments p ON p.id = r.payment_id
    WHERE r.purpose = 'PAYMENT_COMPENSATION' AND r.status = 'FAILED'
      AND NOT EXISTS (
          SELECT 1 FROM dbo.tc_refunds active
          WHERE active.payment_id = r.payment_id AND active.purpose = 'PAYMENT_COMPENSATION'
            AND active.status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED'));

GO

/* View: vw_OrderFinancialSummary */
CREATE OR ALTER VIEW dbo.vw_OrderFinancialSummary
AS
WITH captured AS (
    SELECT order_id, SUM(amount) captured_amount
    FROM dbo.tc_payments
    WHERE status = 'CAPTURED'
    GROUP BY order_id
), refunds AS (
    SELECT p.order_id,
           SUM(CASE WHEN r.purpose = 'CUSTOMER_REFUND' THEN r.amount ELSE 0 END) ticket_refund_amount,
           SUM(CASE WHEN r.purpose = 'PAYMENT_COMPENSATION' THEN r.amount ELSE 0 END) compensation_refund_amount
    FROM dbo.tc_refunds r
    JOIN dbo.tc_payments p ON p.id = r.payment_id
    WHERE r.status = 'SUCCEEDED'
    GROUP BY p.order_id
), tickets AS (
    SELECT oi.order_id, SUM(t.paid_amount) ticket_gross_amount
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    GROUP BY oi.order_id
)
SELECT o.id order_id, o.user_id owner_id, o.event_id, e.organization_id, o.status,
       o.subtotal_amount, o.discount_amount, o.total_amount, o.paid_at,
       COALESCE(c.captured_amount, 0) captured_amount,
       COALESCE(t.ticket_gross_amount, 0) issued_ticket_amount,
       COALESCE(t.ticket_gross_amount, 0) ticket_gross_amount,
       COALESCE(r.ticket_refund_amount, 0) ticket_refund_amount,
       COALESCE(r.ticket_refund_amount, 0) successful_ticket_refund_amount,
       COALESCE(r.compensation_refund_amount, 0) compensation_refund_amount,
       CASE WHEN COALESCE(t.ticket_gross_amount, 0) > COALESCE(r.ticket_refund_amount, 0)
            THEN COALESCE(t.ticket_gross_amount, 0) - COALESCE(r.ticket_refund_amount, 0)
            ELSE 0 END remaining_ticket_amount
FROM dbo.tc_orders o
JOIN dbo.tc_events e ON e.id = o.event_id
LEFT JOIN captured c ON c.order_id = o.id
LEFT JOIN refunds r ON r.order_id = o.id
LEFT JOIN tickets t ON t.order_id = o.id;

GO

/* View: vw_OrganizationMembers */
CREATE OR ALTER VIEW dbo.vw_OrganizationMembers
AS
SELECT m.id membership_id, m.organization_id, m.user_id, u.email, u.full_name,
       m.role, m.active, u.status user_status, m.joined_at
FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id = m.user_id;

GO

/* View: vw_PublicEvents */
CREATE OR ALTER VIEW dbo.vw_PublicEvents
AS
SELECT e.id event_id, e.organization_id, e.category_id, e.title, e.description,
       COALESCE(e.event_image, CONVERT(varchar(2048), e.cover_image_url)) cover_image_url,
       e.venue_name, e.venue_address, e.sale_start, e.sale_end,
       e.start_time, e.end_time, MIN(z.price) minimum_price
FROM dbo.tc_events e
LEFT JOIN dbo.tc_zones z ON z.event_id = e.id
WHERE e.status = 'PUBLISHED'
GROUP BY e.id, e.organization_id, e.category_id, e.title, e.description,
         e.event_image, e.cover_image_url, e.venue_name, e.venue_address,
         e.sale_start, e.sale_end, e.start_time, e.end_time;

GO

/* View: vw_RefundOverview */
/* Canonical view name. Legacy view remains available during transition. */
CREATE OR ALTER VIEW dbo.vw_RefundOverview
AS
WITH ticket_totals AS (
    SELECT refund_id, COUNT_BIG(*) ticket_count,
           SUM(CASE WHEN is_open = 1 THEN 1 ELSE 0 END) open_ticket_count
    FROM dbo.tc_refund_tickets GROUP BY refund_id
), attempt_totals AS (
    SELECT refund_id, COUNT_BIG(*) attempt_count,
           SUM(CASE WHEN status = 'SUCCEEDED' THEN 1 ELSE 0 END) succeeded_attempt_count,
           MAX(created_at) latest_attempt_at
    FROM dbo.tc_refund_transfer_logs GROUP BY refund_id
)
SELECT r.id refund_id, r.order_id, o.event_id, e.organization_id, r.payment_id,
       r.purpose, r.reason_type, r.reason, r.rejection_reason, r.amount, r.status,
       r.current_attempt_id, r.provider_reference, r.processed_at, r.created_at,
       COALESCE(t.ticket_count, 0) ticket_count,
       COALESCE(t.open_ticket_count, 0) open_ticket_count,
       COALESCE(a.attempt_count, 0) attempt_count,
       COALESCE(a.succeeded_attempt_count, 0) succeeded_attempt_count,
       a.latest_attempt_at
FROM dbo.tc_refund_obligations r
LEFT JOIN dbo.tc_orders o ON o.id = r.order_id
LEFT JOIN dbo.tc_events e ON e.id = o.event_id
LEFT JOIN ticket_totals t ON t.refund_id = r.id
LEFT JOIN attempt_totals a ON a.refund_id = r.id;

GO

/* View: vw_RefundRequestOverview */
CREATE OR ALTER VIEW dbo.vw_RefundRequestOverview
AS
WITH requested AS (
    SELECT rt.refund_request_id, COUNT_BIG(*) ticket_count, SUM(t.paid_amount) requested_amount
    FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
    GROUP BY rt.refund_request_id
), attempts AS (
    SELECT refund_request_id, SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END) refunded_amount,
           SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) pending_attempts,
           SUM(CASE WHEN status = 'UNKNOWN' THEN 1 ELSE 0 END) unknown_attempts,
           SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) failed_attempts
    FROM dbo.tc_refunds WHERE refund_request_id IS NOT NULL GROUP BY refund_request_id
)
SELECT rr.id refund_request_id, o.user_id owner_id, o.event_id, e.organization_id,
       rr.reason_type, rr.reason, rr.status, rr.requested_at, rr.decided_at,
       COALESCE(q.ticket_count, 0) ticket_count, COALESCE(q.requested_amount, 0) requested_amount,
       COALESCE(a.refunded_amount, 0) refunded_amount, COALESCE(a.pending_attempts, 0) pending_attempts,
       COALESCE(a.unknown_attempts, 0) unknown_attempts, COALESCE(a.failed_attempts, 0) failed_attempts
FROM dbo.tc_refund_requests rr JOIN dbo.tc_orders o ON o.id = rr.order_id
JOIN dbo.tc_events e ON e.id = o.event_id LEFT JOIN requested q ON q.refund_request_id = rr.id
LEFT JOIN attempts a ON a.refund_request_id = rr.id;

GO

/* View: vw_RefundWork */
CREATE OR ALTER VIEW dbo.vw_RefundWork
AS
    SELECT id, amount, status, provider_reference, created_at
    FROM dbo.tc_refunds
    WHERE status IN ('PENDING', 'UNKNOWN');

GO

/* View: vw_SettlementPayoutBalance */
CREATE OR ALTER VIEW dbo.vw_SettlementPayoutBalance
AS
WITH payouts AS (
    SELECT settlement_id,
           SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END) paid_amount,
           SUM(CASE WHEN status = 'PENDING' THEN amount ELSE 0 END) pending_amount
    FROM dbo.tc_payouts
    GROUP BY settlement_id
)
SELECT s.id settlement_id, s.event_id, s.gross_revenue, s.total_refund, s.total_commission,
       s.net_payable, s.status, s.confirmed_at,
       COALESCE(p.paid_amount, 0) paid_amount,
       COALESCE(p.pending_amount, 0) pending_amount,
       CASE WHEN s.net_payable > COALESCE(p.paid_amount, 0)
            THEN s.net_payable - COALESCE(p.paid_amount, 0) ELSE 0 END remaining_amount,
       CASE WHEN s.status = 'CONFIRMED'
                 AND s.net_payable > COALESCE(p.paid_amount, 0) + COALESCE(p.pending_amount, 0)
            THEN s.net_payable - COALESCE(p.paid_amount, 0) - COALESCE(p.pending_amount, 0)
            ELSE 0 END available_amount
FROM dbo.tc_settlements s
LEFT JOIN payouts p ON p.settlement_id = s.id;

GO

/* View: vw_TicketDetails */
CREATE OR ALTER VIEW dbo.vw_TicketDetails
AS
SELECT t.id ticket_id, o.user_id owner_id, o.id order_id, o.event_id, e.organization_id,
       e.title event_title, e.venue_name, e.venue_address, e.start_time, e.end_time,
       oi.zone_name_snapshot, oi.seat_label_snapshot, t.paid_amount, t.status, t.issued_at
FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
JOIN dbo.tc_orders o ON o.id = oi.order_id JOIN dbo.tc_events e ON e.id = o.event_id;

GO

/* View: vw_ZoneInventory */
CREATE OR ALTER VIEW dbo.vw_ZoneInventory
AS
WITH seated AS (
    SELECT zone_id, COUNT_BIG(*) capacity,
           SUM(CASE WHEN status = 'HELD' THEN 1 ELSE 0 END) held,
           SUM(CASE WHEN status = 'SOLD' THEN 1 ELSE 0 END) sold,
           SUM(CASE WHEN status = 'AVAILABLE' THEN 1 ELSE 0 END) available
    FROM dbo.tc_seats GROUP BY zone_id
)
SELECT z.id zone_id, z.event_id, e.organization_id, z.name, z.type, z.price,
       CASE WHEN z.type = 'SEATED' THEN COALESCE(s.capacity, 0) ELSE z.capacity END capacity,
       CASE WHEN z.type = 'SEATED' THEN COALESCE(s.held, 0) ELSE z.held_quantity END held_quantity,
       CASE WHEN z.type = 'SEATED' THEN COALESCE(s.sold, 0) ELSE z.sold_quantity END sold_quantity,
       CASE WHEN z.type = 'SEATED' THEN COALESCE(s.available, 0)
            ELSE z.capacity - z.held_quantity - z.sold_quantity END available_quantity
FROM dbo.tc_zones z
JOIN dbo.tc_events e ON e.id = z.event_id
LEFT JOIN seated s ON s.zone_id = z.id;

GO
