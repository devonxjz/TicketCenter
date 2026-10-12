SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

CREATE OR ALTER VIEW dbo.vw_BuyerOrderDetails AS
SELECT o.id, o.user_id AS buyer_id, o.order_code, o.event_id,
       e.title AS event_title, e.venue_name, e.venue_address, e.start_time, e.end_time,
       o.subtotal_amount, o.discount_amount, o.total_amount, o.status,
       o.created_at, o.paid_at, c.code AS coupon_code,
       p.status AS payment_status, p.txn_ref, p.provider_reference, p.captured_at,
       (SELECT COUNT_BIG(*)
        FROM dbo.tc_tickets t
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = o.id) AS ticket_count
FROM dbo.tc_orders o
JOIN dbo.tc_events e ON e.id = o.event_id
LEFT JOIN dbo.tc_coupons c ON c.id = o.coupon_id
LEFT JOIN dbo.tc_payments p ON p.id = o.accepted_payment_id;
GO

CREATE OR ALTER VIEW dbo.vw_BuyerOrderItems AS
SELECT oi.order_id, oi.id, oi.zone_name_snapshot, oi.seat_label_snapshot,
       oi.quantity, oi.unit_price, oi.quantity * oi.unit_price AS line_total
FROM dbo.tc_order_items oi
JOIN dbo.tc_orders o ON o.id = oi.order_id;
GO

GRANT SELECT ON dbo.vw_BuyerOrderDetails TO tc_buyer;
GRANT SELECT ON dbo.vw_BuyerOrderItems TO tc_buyer;
GO
