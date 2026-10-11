-- Application projections retain tenant RLS without granting direct financial table access.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
GO
CREATE OR ALTER VIEW dbo.vw_PaymentOrderContext AS
 SELECT o.id order_id,o.user_id,o.order_code,o.total_amount,o.status order_status,
        h.expires_at,h.status hold_status,e.status event_status
 FROM dbo.tc_orders o JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id
 JOIN dbo.tc_events e ON e.id=o.event_id;
GO
CREATE OR ALTER VIEW dbo.vw_PaymentRuntimeDetails AS
 SELECT p.id payment_id,p.order_id,o.user_id,p.txn_ref,p.amount,p.currency,
        p.status payment_status,o.status order_status,o.total_amount,p.captured_at AS paid_at,
        p.created_at,o.order_code,h.expires_at
 FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id
 JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id;
GO
CREATE OR ALTER VIEW dbo.vw_OrderTicketQuantity AS
 SELECT i.order_id,COALESCE(SUM(i.quantity),0) quantity
 FROM dbo.tc_order_items i JOIN dbo.tc_orders o ON o.id=i.order_id GROUP BY i.order_id;
GO
CREATE OR ALTER VIEW dbo.vw_TicketRuntimeDetails AS
 SELECT t.id,t.ticket_code,t.paid_amount,t.status,t.issued_at,t.version,
        i.order_id,i.zone_name_snapshot,i.seat_label_snapshot,
        o.order_code,o.user_id buyer_id,e.id event_id,e.title event_title,
        e.venue_name,e.venue_address,e.start_time,e.end_time
 FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id
 JOIN dbo.tc_orders o ON o.id=i.order_id JOIN dbo.tc_events e ON e.id=o.event_id;
GO
GRANT SELECT ON dbo.vw_PaymentOrderContext TO tc_buyer;
GRANT SELECT ON dbo.vw_PaymentRuntimeDetails TO tc_buyer;
GRANT SELECT ON dbo.vw_OrderTicketQuantity TO tc_buyer;
GRANT SELECT ON dbo.vw_TicketRuntimeDetails TO tc_buyer;
GRANT SELECT ON dbo.vw_PaymentRuntimeDetails TO tc_worker;
GRANT SELECT ON dbo.vw_OrderTicketQuantity TO tc_worker;
GO
