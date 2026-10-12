/* Read-only email context for the outbox worker (ORDER_PAID, REFUND_SUCCEEDED).
   tc_worker reads this view instead of joining users/orders/payments directly. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER VIEW dbo.vw_OutboxEmailContext AS
 SELECT x.id outbox_id,x.event_type,u.email recipient_email,u.full_name recipient_name,
        o.order_code,e.title event_title,o.total_amount amount
 FROM dbo.tc_outbox x
 JOIN dbo.tc_orders o ON o.id=x.aggregate_id
 JOIN dbo.tc_users u ON u.id=o.user_id
 JOIN dbo.tc_events e ON e.id=o.event_id
 WHERE x.event_type='ORDER_PAID' AND x.aggregate_type='ORDER'
UNION ALL
 SELECT x.id,x.event_type,u.email,u.full_name,o.order_code,e.title,r.amount
 FROM dbo.tc_outbox x
 JOIN dbo.tc_refunds r ON r.id=x.aggregate_id
 JOIN dbo.tc_payments p ON p.id=r.payment_id
 JOIN dbo.tc_orders o ON o.id=p.order_id
 JOIN dbo.tc_users u ON u.id=o.user_id
 JOIN dbo.tc_events e ON e.id=o.event_id
 WHERE x.event_type='REFUND_SUCCEEDED' AND x.aggregate_type='REFUND';
GO
GRANT SELECT ON dbo.vw_OutboxEmailContext TO tc_worker;
GO
