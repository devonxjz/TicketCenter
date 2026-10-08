-- SQL Server 2017+. Install after functions.sql.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER VIEW dbo.v_numbers AS
SELECT a.n+10*b.n+100*c.n+1000*d.n+1 AS n
FROM (VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) a(n)
CROSS JOIN (VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) b(n)
CROSS JOIN (VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) c(n)
CROSS JOIN (VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) d(n);
GO
CREATE OR ALTER VIEW dbo.v_users AS
SELECT u.*, dbo.fn_user_is_eligible_buyer(u.id) AS is_eligible_buyer,
    (SELECT COUNT_BIG(*) FROM dbo.user_organization_roles r WHERE r.user_id=u.id) AS organization_count
FROM dbo.users u;
GO
CREATE OR ALTER VIEW dbo.v_user_organization_roles AS
SELECT r.*, u.full_name, u.email, o.name AS organization_name
FROM dbo.user_organization_roles r JOIN dbo.users u ON u.id=r.user_id JOIN dbo.organizations o ON o.id=r.organization_id;
GO
CREATE OR ALTER VIEW dbo.v_organizations AS
SELECT o.*, u.full_name AS requester_name,
    (SELECT COUNT_BIG(*) FROM dbo.events e WHERE e.organization_id=o.id) AS event_count
FROM dbo.organizations o LEFT JOIN dbo.users u ON u.id=o.requester_id;
GO
CREATE OR ALTER VIEW dbo.v_coupons AS
SELECT c.*, o.name AS organization_name,
    dbo.fn_coupon_is_active_at(c.id,SYSUTCDATETIME()) AS is_active_now,
    (SELECT COUNT_BIG(*) FROM dbo.orders x JOIN dbo.ticket_holds h ON h.id=x.hold_id
        WHERE x.coupon_id=c.id AND (x.status=N'PAID' OR (x.status=N'AWAITING_PAYMENT' AND h.status=N'ACTIVE' AND h.expires_at>SYSUTCDATETIME()))) AS allocated_uses
FROM dbo.coupons c JOIN dbo.organizations o ON o.id=c.organization_id;
GO
CREATE OR ALTER VIEW dbo.v_commission_rules AS
SELECT c.*, o.name AS organization_name, dbo.fn_commission_rule_is_effective_at(c.id,SYSUTCDATETIME()) AS is_effective_now
FROM dbo.commission_rules c JOIN dbo.organizations o ON o.id=c.organization_id;
GO
CREATE OR ALTER VIEW dbo.v_events AS
SELECT e.*, o.name AS organization_name,
    dbo.fn_event_is_sale_active(e.id,SYSUTCDATETIME()) AS is_sale_active,
    dbo.fn_event_is_check_in_open(e.id,SYSUTCDATETIME()) AS is_check_in_open,
    (SELECT COUNT_BIG(*) FROM dbo.zones z WHERE z.event_id=e.id) AS zone_count
FROM dbo.events e JOIN dbo.organizations o ON o.id=e.organization_id;
GO
CREATE OR ALTER VIEW dbo.v_zones AS
SELECT z.*, e.title AS event_title, dbo.fn_zone_get_capacity(z.id) AS capacity,
    dbo.fn_zone_get_available(z.id) AS available
FROM dbo.zones z JOIN dbo.events e ON e.id=z.event_id;
GO
CREATE OR ALTER VIEW dbo.v_seats AS
SELECT s.*, z.name AS zone_name, z.event_id, CONCAT(s.row_name,N'-',s.seat_number) AS seat_label
FROM dbo.seats s JOIN dbo.zones z ON z.id=s.zone_id;
GO
CREATE OR ALTER VIEW dbo.v_settlements AS
SELECT s.*, e.title AS event_title, e.organization_id,
    (SELECT COUNT_BIG(*) FROM dbo.settlement_payouts p WHERE p.settlement_id=s.id) AS payout_count
FROM dbo.settlements s JOIN dbo.events e ON e.id=s.event_id;
GO
CREATE OR ALTER VIEW dbo.v_ticket_holds AS
SELECT h.*, u.full_name AS buyer_name, e.title AS event_title,
    dbo.fn_ticket_hold_is_active(h.id,SYSUTCDATETIME()) AS is_active,
    dbo.fn_ticket_hold_is_expired(h.id,SYSUTCDATETIME()) AS is_expired,
    COALESCE((SELECT SUM(i.quantity*i.unit_price) FROM dbo.ticket_hold_items i WHERE i.ticket_hold_id=h.id),0) AS subtotal_amount
FROM dbo.ticket_holds h JOIN dbo.users u ON u.id=h.buyer_id JOIN dbo.events e ON e.id=h.event_id;
GO
CREATE OR ALTER VIEW dbo.v_ticket_hold_items AS
SELECT i.*, z.name AS zone_name, CONCAT(s.row_name,N'-',s.seat_number) AS seat_label,
    CAST(i.quantity*i.unit_price AS DECIMAL(19,2)) AS line_total
FROM dbo.ticket_hold_items i JOIN dbo.zones z ON z.id=i.zone_id LEFT JOIN dbo.seats s ON s.id=i.seat_id;
GO
CREATE OR ALTER VIEW dbo.order_totals AS
SELECT o.*, CAST(COALESCE(t.subtotal_amount,0) AS DECIMAL(19,2)) AS subtotal_amount,
    CAST(COALESCE(t.subtotal_amount,0)-o.discount_amount AS DECIMAL(19,2)) AS total_amount
FROM dbo.orders o LEFT JOIN (SELECT order_id,SUM(quantity*unit_price) AS subtotal_amount FROM dbo.order_items GROUP BY order_id) t ON t.order_id=o.id;
GO
CREATE OR ALTER VIEW dbo.v_orders AS
SELECT o.*, u.full_name AS buyer_name, e.title AS event_title, c.code AS coupon_code,
    (SELECT COUNT_BIG(*) FROM dbo.tickets t JOIN dbo.order_items i ON i.id=t.order_item_id WHERE i.order_id=o.id) AS ticket_count
FROM dbo.order_totals o JOIN dbo.users u ON u.id=o.buyer_id JOIN dbo.events e ON e.id=o.event_id LEFT JOIN dbo.coupons c ON c.id=o.coupon_id;
GO
CREATE OR ALTER VIEW dbo.v_order_items AS
SELECT i.*, CAST(i.quantity*i.unit_price AS DECIMAL(19,2)) AS line_total,
    (SELECT COUNT_BIG(*) FROM dbo.tickets t WHERE t.order_item_id=i.id) AS ticket_count
FROM dbo.order_items i;
GO
CREATE OR ALTER VIEW dbo.ticket_locations AS
SELECT t.*, i.zone_id, i.seat_id FROM dbo.tickets t JOIN dbo.order_items i ON i.id=t.order_item_id;
GO
CREATE OR ALTER VIEW dbo.v_tickets AS
SELECT t.*, i.order_id, i.zone_name_snapshot, i.seat_label_snapshot, o.event_id, o.buyer_id,
    e.title AS event_title, e.start_time, e.end_time
FROM dbo.ticket_locations t JOIN dbo.order_items i ON i.id=t.order_item_id JOIN dbo.orders o ON o.id=i.order_id JOIN dbo.events e ON e.id=o.event_id;
GO
CREATE OR ALTER VIEW dbo.v_payments AS
SELECT p.*, o.order_code, o.event_id, o.buyer_id,
    CAST(CASE WHEN o.accepted_payment_id=p.id THEN 1 ELSE 0 END AS BIT) AS is_accepted_payment
FROM dbo.payments p JOIN dbo.orders o ON o.id=p.order_id;
GO
CREATE OR ALTER VIEW dbo.v_refunds AS
SELECT r.*, o.order_code, o.event_id, o.buyer_id,
    (SELECT COUNT_BIG(*) FROM dbo.refund_tickets rt WHERE rt.refund_id=r.id) AS ticket_count
FROM dbo.refunds r JOIN dbo.orders o ON o.id=r.order_id;
GO
CREATE OR ALTER VIEW dbo.v_refund_tickets AS
SELECT rt.refund_id, t.* FROM dbo.refund_tickets rt JOIN dbo.v_tickets t ON t.id=rt.ticket_id;
GO
CREATE OR ALTER VIEW dbo.v_settlement_payouts AS
SELECT p.*, s.event_id, e.organization_id FROM dbo.settlement_payouts p JOIN dbo.settlements s ON s.id=p.settlement_id JOIN dbo.events e ON e.id=s.event_id;
GO
CREATE OR ALTER VIEW dbo.v_refund_attempts AS
SELECT a.*, r.order_id, r.payment_id FROM dbo.refund_attempts a JOIN dbo.refunds r ON r.id=a.refund_id;
GO
-- Compensation returns an unaccepted capture; it is excluded from sales refunds.
-- Commission is calculated once per order on its remaining ticket revenue.
CREATE OR ALTER VIEW dbo.v_event_financials AS
SELECT e.id AS event_id,
    CAST(COALESCE(SUM(o.total_amount),0) AS DECIMAL(19,2)) AS gross_revenue,
    CAST(COALESCE(SUM(r.total_refund),0) AS DECIMAL(19,2)) AS total_refund,
    CAST(COALESCE(SUM(dbo.fn_commission_rule_calculate_fee(e.commission_rule_id,o.total_amount-COALESCE(r.total_refund,0))),0) AS DECIMAL(19,2)) AS total_commission
FROM dbo.events e LEFT JOIN dbo.order_totals o ON o.event_id=e.id AND o.status=N'PAID'
LEFT JOIN (SELECT order_id,SUM(amount) AS total_refund FROM dbo.refunds WHERE purpose=N'TICKET_REFUND' AND status=N'COMPLETED' GROUP BY order_id) r ON r.order_id=o.id
GROUP BY e.id;
GO
