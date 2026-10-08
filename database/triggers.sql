-- SQL Server 2017+. Install after procedures.sql.
-- Triggers validate every row in inserted/deleted, including multi-row DML.
-- Hold item insertion reserves stock; hold state changes release/sell stock.
-- A ticket becoming REFUNDED returns stock exactly once. Payout ledger changes
-- recalculate settlement balances. Other triggers enforce domain relationships.
-- Base tables and transfer ledgers should not be writable by end users.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER TRIGGER dbo.tr_users_validate ON dbo.users
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'User',status)=0)
    THROW 51000,N'Invalid User status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'User',d.status,i.status)=0)
    THROW 51000,N'Invalid User state transition.',1;


IF EXISTS(SELECT 1 FROM inserted WHERE NULLIF(LTRIM(RTRIM(full_name)),N'') IS NULL OR NULLIF(LTRIM(RTRIM(email)),N'') IS NULL OR NULLIF(LTRIM(RTRIM(user_name)),N'') IS NULL OR NULLIF(LTRIM(RTRIM(platform_role)),N'') IS NULL)
    THROW 51000,N'User identity fields must not be empty.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_organizations_validate ON dbo.organizations
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'Organization',status)=0)
    THROW 51000,N'Invalid Organization status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'Organization',d.status,i.status)=0)
    THROW 51000,N'Invalid Organization state transition.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status NOT IN(N'DRAFT')) THROW 51000,N'Invalid initial Organization state.',1;

IF EXISTS(SELECT 1 FROM inserted WHERE NULLIF(LTRIM(RTRIM(name)),N'') IS NULL OR NULLIF(LTRIM(RTRIM(contact_email)),N'') IS NULL
    OR (status=N'REJECTED' AND NULLIF(LTRIM(RTRIM(rejection_reason)),N'') IS NULL)) THROW 51000,N'Organization fields or rejection reason are missing.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_user_organization_roles_validate ON dbo.user_organization_roles
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE NULLIF(LTRIM(RTRIM(role)),N'') IS NULL) THROW 51000,N'Organization role cannot be empty.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_coupons_validate ON dbo.coupons
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE NOT (
    (discount_type=N'FIXED' AND fixed_amount IS NOT NULL AND percentage IS NULL) OR
    (discount_type=N'PERCENTAGE' AND percentage IS NOT NULL AND fixed_amount IS NULL))
    OR valid_from>=valid_to OR NULLIF(LTRIM(RTRIM(code)),N'') IS NULL) THROW 51000,N'Coupon terms are invalid.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(
    SELECT i.organization_id,i.code,i.discount_type,i.fixed_amount,i.percentage,i.valid_from,i.valid_to
    EXCEPT SELECT d.organization_id,d.code,d.discount_type,d.fixed_amount,d.percentage,d.valid_from,d.valid_to)
    AND EXISTS(SELECT 1 FROM dbo.orders o JOIN dbo.ticket_holds h ON h.id=o.hold_id WHERE o.coupon_id=i.id
        AND (o.status=N'PAID' OR (o.status=N'AWAITING_PAYMENT' AND h.status=N'ACTIVE' AND h.expires_at>SYSUTCDATETIME()))))
    THROW 51000,N'Allocated coupon terms are immutable.',1;
IF EXISTS(SELECT 1 FROM inserted i WHERE i.max_uses<(SELECT COUNT_BIG(*) FROM dbo.orders o JOIN dbo.ticket_holds h ON h.id=o.hold_id
    WHERE o.coupon_id=i.id AND (o.status=N'PAID' OR (o.status=N'AWAITING_PAYMENT' AND h.status=N'ACTIVE' AND h.expires_at>SYSUTCDATETIME()))))
    THROW 51000,N'Coupon limit is below allocated uses.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_commission_rules_validate ON dbo.commission_rules
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE effective_from>=effective_to) THROW 51000,N'Commission interval is empty.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(
    SELECT i.organization_id,i.rate_percent,i.fixed_fee,i.effective_from,i.effective_to
    EXCEPT SELECT d.organization_id,d.rate_percent,d.fixed_fee,d.effective_from,d.effective_to)
    AND EXISTS(SELECT 1 FROM dbo.events WHERE commission_rule_id=i.id)) THROW 51000,N'An assigned commission rule is immutable.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_events_validate ON dbo.events
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'Event',status)=0)
    THROW 51000,N'Invalid Event status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'Event',d.status,i.status)=0)
    THROW 51000,N'Invalid Event state transition.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status NOT IN(N'DRAFT')) THROW 51000,N'Invalid initial Event state.',1;

IF EXISTS(SELECT 1 FROM inserted WHERE sale_start>=sale_end OR sale_end>start_time OR start_time>=end_time
    OR NULLIF(LTRIM(RTRIM(title)),N'') IS NULL OR NULLIF(LTRIM(RTRIM(category)),N'') IS NULL
    OR NULLIF(LTRIM(RTRIM(venue_name)),N'') IS NULL OR NULLIF(LTRIM(RTRIM(venue_address)),N'') IS NULL
    OR (status=N'REJECTED' AND NULLIF(LTRIM(RTRIM(rejection_reason)),N'') IS NULL)) THROW 51000,N'Event details or schedule are invalid.',1;
IF EXISTS(SELECT 1 FROM inserted e LEFT JOIN dbo.commission_rules r ON r.id=e.commission_rule_id
    WHERE (e.commission_rule_id IS NOT NULL AND r.organization_id<>e.organization_id) OR (e.status=N'PUBLISHED' AND e.commission_rule_id IS NULL))
    THROW 51000,N'Event commission rule belongs to another organization or is missing.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE d.status NOT IN(N'DRAFT',N'REJECTED') AND EXISTS(
    SELECT i.organization_id,i.title,i.description,i.category,i.venue_name,i.venue_address,i.cover_image_url,i.sale_start,i.sale_end,i.start_time,i.end_time
    EXCEPT SELECT d.organization_id,d.title,d.description,d.category,d.venue_name,d.venue_address,d.cover_image_url,d.sale_start,d.sale_end,d.start_time,d.end_time))
    THROW 51000,N'Submitted event details are immutable.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE d.status=N'PUBLISHED' AND EXISTS(SELECT i.commission_rule_id EXCEPT SELECT d.commission_rule_id))
    THROW 51000,N'Published event commission rule is immutable.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_zones_validate ON dbo.zones
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE type NOT IN(N'SEATED',N'STANDING') OR NULLIF(LTRIM(RTRIM(name)),N'') IS NULL
    OR (type=N'SEATED' AND (standing_capacity IS NOT NULL OR standing_held IS NOT NULL OR standing_sold IS NOT NULL))
    OR (type=N'STANDING' AND (standing_capacity IS NULL OR standing_held IS NULL OR standing_sold IS NULL OR standing_held+standing_sold>standing_capacity)))
    THROW 51000,N'Zone capacity or type is invalid.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE (i.event_id<>d.event_id OR i.type<>d.type)
    AND (EXISTS(SELECT 1 FROM dbo.seats WHERE zone_id=i.id) OR EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE zone_id=i.id)))
    THROW 51000,N'Zone ownership or type cannot change after inventory exists.',1;
IF EXISTS(SELECT 1 FROM inserted z WHERE z.type=N'STANDING' AND (
    z.standing_held<>COALESCE((SELECT SUM(quantity) FROM dbo.ticket_hold_items WHERE zone_id=z.id AND inventory_state=N'HELD'),0)
    OR z.standing_sold<>COALESCE((SELECT SUM(quantity) FROM dbo.ticket_hold_items WHERE zone_id=z.id AND inventory_state=N'SOLD'),0)
        -(SELECT COUNT(*) FROM dbo.tickets t JOIN dbo.order_items oi ON oi.id=t.order_item_id WHERE oi.zone_id=z.id AND oi.seat_id IS NULL AND t.status=N'REFUNDED')))
    THROW 51000,N'Standing counters must match reservation and ticket inventory.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_seats_validate ON dbo.seats
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'Seat',status)=0)
    THROW 51000,N'Invalid Seat status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'Seat',d.status,i.status)=0)
    THROW 51000,N'Invalid Seat state transition.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status NOT IN(N'AVAILABLE')) THROW 51000,N'Invalid initial Seat state.',1;

IF EXISTS(SELECT 1 FROM inserted s JOIN dbo.zones z ON z.id=s.zone_id WHERE z.type<>N'SEATED') THROW 51000,N'Seats require a seated zone.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(SELECT i.zone_id,i.row_name,i.seat_number EXCEPT SELECT d.zone_id,d.row_name,d.seat_number)
    AND EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE seat_id=i.id)) THROW 51000,N'Referenced seat positions are immutable.',1;
IF EXISTS(SELECT 1 FROM inserted s WHERE
    (s.status=N'HELD' AND NOT EXISTS(SELECT 1 FROM dbo.ticket_hold_items i WHERE i.seat_id=s.id AND i.inventory_state=N'HELD'))
    OR (s.status=N'SOLD' AND NOT EXISTS(SELECT 1 FROM dbo.ticket_hold_items i WHERE i.seat_id=s.id AND i.inventory_state=N'SOLD'
        AND NOT EXISTS(SELECT 1 FROM dbo.order_items oi JOIN dbo.tickets t ON t.order_item_id=oi.id WHERE oi.hold_item_id=i.id AND t.status=N'REFUNDED')))
    OR (s.status=N'AVAILABLE' AND EXISTS(SELECT 1 FROM dbo.ticket_hold_items i WHERE i.seat_id=s.id AND (i.inventory_state=N'HELD'
        OR (i.inventory_state=N'SOLD' AND NOT EXISTS(SELECT 1 FROM dbo.order_items oi JOIN dbo.tickets t ON t.order_item_id=oi.id WHERE oi.hold_item_id=i.id AND t.status=N'REFUNDED'))))))
    THROW 51000,N'Seat status does not match reservation inventory.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_ticket_holds_validate ON dbo.ticket_holds
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'Hold',status)=0)
    THROW 51000,N'Invalid Hold status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'Hold',d.status,i.status)=0)
    THROW 51000,N'Invalid Hold state transition.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status NOT IN(N'ACTIVE')) THROW 51000,N'Invalid initial Hold state.',1;

IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id JOIN dbo.events e ON e.id=i.event_id WHERE d.id IS NULL
    AND (i.expires_at<=SYSUTCDATETIME() OR i.expires_at>e.sale_end OR dbo.fn_event_is_sale_active(i.event_id,SYSUTCDATETIME())=0 OR dbo.fn_user_is_eligible_buyer(i.buyer_id)=0))
    THROW 51000,N'New holds require an eligible buyer, active sale and valid expiry.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(SELECT i.buyer_id,i.event_id,i.expires_at EXCEPT SELECT d.buyer_id,d.event_id,d.expires_at))
    THROW 51000,N'Hold identity and expiry are immutable.',1;
IF EXISTS(SELECT 1 FROM inserted h WHERE h.status=N'CONSUMED' AND NOT EXISTS(SELECT 1 FROM dbo.orders o WHERE o.hold_id=h.id
    AND (o.status=N'PAID' OR (o.status=N'AWAITING_PAYMENT' AND (dbo.fn_order_total(o.id)=0 OR EXISTS(SELECT 1 FROM dbo.payments p WHERE p.order_id=o.id AND p.status=N'CAPTURED' AND p.amount=dbo.fn_order_total(o.id)))))))
    THROW 51000,N'Only paid or captured orders can consume a hold.',1;
IF EXISTS(SELECT 1 FROM inserted h JOIN deleted d ON d.id=h.id WHERE d.status=N'ACTIVE' AND h.status=N'RELEASED'
    AND EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE ticket_hold_id=h.id AND inventory_state=N'SOLD'))
    THROW 51000,N'Sold inventory cannot be released as a hold.',1;
IF EXISTS(SELECT 1 FROM inserted h JOIN deleted d ON d.id=h.id WHERE d.status=N'ACTIVE' AND h.status=N'CONSUMED'
    AND (NOT EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE ticket_hold_id=h.id) OR EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE ticket_hold_id=h.id AND inventory_state<>N'HELD')))
    THROW 51000,N'Consumed holds must contain reserved items.',1;
UPDATE i SET inventory_state=CASE h.status WHEN N'RELEASED' THEN N'RELEASED' ELSE N'SOLD' END
FROM dbo.ticket_hold_items i JOIN inserted h ON h.id=i.ticket_hold_id JOIN deleted d ON d.id=h.id
WHERE d.status=N'ACTIVE' AND h.status IN(N'RELEASED',N'CONSUMED') AND i.inventory_state=N'HELD';
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_ticket_hold_items_inventory ON dbo.ticket_hold_items
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id WHERE i.id IS NULL AND d.inventory_state<>N'RELEASED')
    THROW 51000,N'Release held inventory before deleting an item.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(
    SELECT i.ticket_hold_id,i.zone_id,i.seat_id,i.quantity,i.unit_price EXCEPT SELECT d.ticket_hold_id,d.zone_id,d.seat_id,d.quantity,d.unit_price)
    OR (i.inventory_state<>d.inventory_state AND NOT(d.inventory_state=N'HELD' AND i.inventory_state IN(N'SOLD',N'RELEASED'))))
    THROW 51000,N'Hold item identity is immutable or inventory transition is invalid.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.ticket_holds h ON h.id=i.ticket_hold_id JOIN dbo.zones z ON z.id=i.zone_id
    LEFT JOIN dbo.seats s ON s.id=i.seat_id LEFT JOIN deleted d ON d.id=i.id
    WHERE z.event_id<>h.event_id OR (i.seat_id IS NOT NULL AND (s.zone_id<>z.id OR z.type<>N'SEATED' OR i.quantity<>1))
    OR (i.seat_id IS NULL AND z.type<>N'STANDING') OR i.quantity>10000
    OR (d.id IS NULL AND (i.inventory_state<>N'HELD' OR h.status<>N'ACTIVE' OR h.expires_at<=SYSUTCDATETIME()
        OR dbo.fn_event_is_sale_active(h.event_id,SYSUTCDATETIME())=0 OR EXISTS(SELECT 1 FROM dbo.orders WHERE hold_id=h.id)))
    OR (d.id IS NOT NULL AND i.inventory_state=N'RELEASED' AND d.inventory_state=N'HELD' AND EXISTS(SELECT 1 FROM dbo.orders WHERE hold_id=h.id AND status IN(N'AWAITING_PAYMENT',N'PAID')))
    OR (d.id IS NOT NULL AND i.inventory_state=N'SOLD' AND d.inventory_state=N'HELD' AND h.status<>N'CONSUMED'))
    THROW 51000,N'Hold item does not match its hold, zone, seat or lifecycle.',1;
IF EXISTS(SELECT seat_id FROM inserted WHERE seat_id IS NOT NULL AND inventory_state=N'HELD' GROUP BY seat_id HAVING COUNT(*)>1)
    THROW 51000,N'A batch cannot reserve the same seat twice.',1;
DECLARE @expected INT=(SELECT COUNT(*) FROM inserted i LEFT JOIN deleted d ON d.id=i.id
    WHERE i.seat_id IS NOT NULL AND (d.id IS NULL OR i.inventory_state<>d.inventory_state));
UPDATE s SET status=CASE i.inventory_state WHEN N'HELD' THEN N'HELD' WHEN N'SOLD' THEN N'SOLD' ELSE N'AVAILABLE' END
FROM dbo.seats s JOIN inserted i ON i.seat_id=s.id LEFT JOIN deleted d ON d.id=i.id
WHERE (d.id IS NULL AND s.status=N'AVAILABLE') OR (d.inventory_state=N'HELD' AND i.inventory_state<>d.inventory_state AND s.status=N'HELD');
IF @@ROWCOUNT<>@expected THROW 51000,N'Seat is no longer available or inventory state is inconsistent.',1;
;WITH delta AS (
    SELECT zone_id,SUM(held) AS held,SUM(sold) AS sold FROM (
        SELECT zone_id,CASE WHEN inventory_state=N'HELD' THEN quantity ELSE 0 END AS held,CASE WHEN inventory_state=N'SOLD' THEN quantity ELSE 0 END AS sold FROM inserted WHERE seat_id IS NULL
        UNION ALL SELECT zone_id,-CASE WHEN inventory_state=N'HELD' THEN quantity ELSE 0 END,-CASE WHEN inventory_state=N'SOLD' THEN quantity ELSE 0 END FROM deleted WHERE seat_id IS NULL
    ) x GROUP BY zone_id
)
UPDATE z SET standing_held=z.standing_held+d.held,standing_sold=z.standing_sold+d.sold FROM dbo.zones z JOIN delta d ON d.zone_id=z.id WHERE d.held<>0 OR d.sold<>0;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_orders_validate ON dbo.orders
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'Order',status)=0)
    THROW 51000,N'Invalid Order status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'Order',d.status,i.status)=0)
    THROW 51000,N'Invalid Order state transition.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status NOT IN(N'AWAITING_PAYMENT')) THROW 51000,N'Invalid initial Order state.',1;

IF EXISTS(SELECT 1 FROM inserted o JOIN dbo.ticket_holds h ON h.id=o.hold_id WHERE o.buyer_id<>h.buyer_id OR o.event_id<>h.event_id)
    THROW 51000,N'Order must match the hold buyer and event.',1;
IF EXISTS(SELECT 1 FROM inserted o JOIN dbo.events e ON e.id=o.event_id JOIN dbo.coupons c ON c.id=o.coupon_id WHERE c.organization_id<>e.organization_id)
    THROW 51000,N'Order coupon belongs to another organization.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(SELECT i.buyer_id,i.event_id,i.hold_id,i.order_code EXCEPT SELECT d.buyer_id,d.event_id,d.hold_id,d.order_code))
    THROW 51000,N'Order identity is immutable.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(SELECT i.coupon_id,i.discount_amount EXCEPT SELECT d.coupon_id,d.discount_amount)
    AND (d.status<>N'AWAITING_PAYMENT' OR EXISTS(SELECT 1 FROM dbo.payments WHERE order_id=i.id AND status IN(N'PENDING',N'UNKNOWN',N'CAPTURED'))))
    THROW 51000,N'Payment attempts freeze order totals.',1;
IF EXISTS(SELECT 1 FROM inserted WHERE status<>N'AWAITING_PAYMENT' AND discount_amount>dbo.fn_order_subtotal(id))
    THROW 51000,N'Order discount exceeds subtotal.',1;
IF EXISTS(SELECT 1 FROM inserted o JOIN dbo.ticket_holds h ON h.id=o.hold_id WHERE o.status=N'PAID'
    AND (h.status<>N'CONSUMED' OR NOT EXISTS(SELECT 1 FROM dbo.order_items WHERE order_id=o.id)
        OR (o.accepted_payment_id IS NULL AND dbo.fn_order_total(o.id)<>0)
        OR (o.accepted_payment_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.payments p WHERE p.id=o.accepted_payment_id AND p.order_id=o.id AND p.status=N'CAPTURED' AND p.amount=dbo.fn_order_total(o.id)))))
    THROW 51000,N'Paid order requires consumed inventory and an exact capture.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE d.status=N'PAID' AND EXISTS(SELECT i.accepted_payment_id EXCEPT SELECT d.accepted_payment_id))
    THROW 51000,N'Accepted payment is immutable.',1;
IF EXISTS(SELECT 1 FROM inserted o WHERE o.status<>N'PAID' AND o.accepted_payment_id IS NOT NULL) THROW 51000,N'Only paid orders accept payments.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_order_items_validate ON dbo.order_items
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM deleted) THROW 51000,N'Order item snapshots are immutable.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.orders o ON o.id=i.order_id JOIN dbo.ticket_hold_items h ON h.id=i.hold_item_id
    WHERE o.status<>N'AWAITING_PAYMENT' OR h.ticket_hold_id<>o.hold_id OR h.inventory_state<>N'HELD' OR EXISTS(
        SELECT i.zone_id,i.seat_id,i.quantity,i.unit_price EXCEPT SELECT h.zone_id,h.seat_id,h.quantity,h.unit_price)
    OR EXISTS(SELECT 1 FROM dbo.payments WHERE order_id=o.id)) THROW 51000,N'Order items must copy reserved hold items before payment starts.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_tickets_validate ON dbo.tickets
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'Ticket',status)=0)
    THROW 51000,N'Invalid Ticket status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'Ticket',d.status,i.status)=0)
    THROW 51000,N'Invalid Ticket state transition.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status NOT IN(N'VALID')) THROW 51000,N'Invalid initial Ticket state.',1;

IF EXISTS(SELECT 1 FROM inserted t JOIN dbo.order_items i ON i.id=t.order_item_id JOIN dbo.orders o ON o.id=i.order_id JOIN dbo.ticket_hold_items h ON h.id=i.hold_item_id
    WHERE o.status<>N'PAID' OR h.inventory_state<>N'SOLD') THROW 51000,N'Tickets require paid orders and sold inventory.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(SELECT i.order_item_id,i.ticket_code,i.paid_amount EXCEPT SELECT d.order_item_id,d.ticket_code,d.paid_amount))
    THROW 51000,N'Ticket identity and paid amount are immutable.',1;
IF EXISTS(SELECT 1 FROM dbo.order_items oi JOIN (SELECT DISTINCT order_item_id FROM inserted) x ON x.order_item_id=oi.id
    WHERE (SELECT COUNT_BIG(*) FROM dbo.tickets WHERE order_item_id=oi.id)>oi.quantity) THROW 51000,N'Ticket count exceeds ordered quantity.',1;
IF EXISTS(SELECT 1 FROM inserted t WHERE t.status=N'REFUND_PENDING' AND NOT EXISTS(SELECT 1 FROM dbo.refund_tickets rt JOIN dbo.refunds r ON r.id=rt.refund_id WHERE rt.ticket_id=t.id AND r.status NOT IN(N'REJECTED',N'COMPLETED')))
    THROW 51000,N'Refund-pending tickets require an active request.',1;
IF EXISTS(SELECT 1 FROM inserted t WHERE t.status=N'REFUNDED' AND NOT EXISTS(SELECT 1 FROM dbo.refund_tickets rt JOIN dbo.refunds r ON r.id=rt.refund_id WHERE rt.ticket_id=t.id AND r.status=N'COMPLETED'))
    THROW 51000,N'Refunded tickets require a completed refund.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE d.status=N'REFUND_PENDING' AND i.status=N'VALID'
    AND EXISTS(SELECT 1 FROM dbo.refund_tickets rt JOIN dbo.refunds r ON r.id=rt.refund_id WHERE rt.ticket_id=i.id AND r.status<>N'REJECTED'))
    THROW 51000,N'An active refund still owns this ticket.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_tickets_inventory ON dbo.tickets
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
UPDATE s SET status=N'AVAILABLE' FROM dbo.seats s JOIN dbo.order_items oi ON oi.seat_id=s.id JOIN inserted i ON i.order_item_id=oi.id
JOIN deleted d ON d.id=i.id WHERE i.status=N'REFUNDED' AND d.status<>N'REFUNDED';
;WITH returned AS (
    SELECT oi.zone_id,COUNT(*) AS quantity FROM inserted i JOIN deleted d ON d.id=i.id JOIN dbo.order_items oi ON oi.id=i.order_item_id
    WHERE i.status=N'REFUNDED' AND d.status<>N'REFUNDED' AND oi.seat_id IS NULL GROUP BY oi.zone_id
)
UPDATE z SET standing_sold=z.standing_sold-r.quantity FROM dbo.zones z JOIN returned r ON r.zone_id=z.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_payments_validate ON dbo.payments
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'Payment',status)=0)
    THROW 51000,N'Invalid Payment status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'Payment',d.status,i.status)=0)
    THROW 51000,N'Invalid Payment state transition.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status NOT IN(N'PENDING')) THROW 51000,N'Invalid initial Payment state.',1;

IF EXISTS(SELECT 1 FROM inserted WHERE amount<=0 OR NULLIF(LTRIM(RTRIM(txn_ref)),N'') IS NULL
    OR (status=N'CAPTURED' AND NULLIF(LTRIM(RTRIM(transaction_no)),N'') IS NULL)) THROW 51000,N'Payment amount or reference is invalid.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(SELECT i.order_id,i.amount,i.txn_ref EXCEPT SELECT d.order_id,d.amount,d.txn_ref)
    OR (d.status=N'CAPTURED' AND EXISTS(SELECT i.transaction_no EXCEPT SELECT d.transaction_no))) THROW 51000,N'Payment terms and capture reference are immutable.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_refunds_validate ON dbo.refunds
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'Refund',status)=0)
    THROW 51000,N'Invalid Refund status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'Refund',d.status,i.status)=0)
    THROW 51000,N'Invalid Refund state transition.',1;


IF EXISTS(SELECT 1 FROM inserted WHERE purpose NOT IN(N'TICKET_REFUND',N'COMPENSATION')
    OR (status=N'REJECTED' AND NULLIF(LTRIM(RTRIM(rejection_reason)),N'') IS NULL)) THROW 51000,N'Refund purpose or rejection reason is invalid.',1;
IF EXISTS(SELECT 1 FROM inserted r JOIN dbo.orders o ON o.id=r.order_id JOIN dbo.events e ON e.id=o.event_id
    WHERE r.purpose=N'TICKET_REFUND' AND r.status=N'REJECTED' AND e.status=N'CANCELLED') THROW 51000,N'Cancellation refunds cannot be rejected.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND
    NOT((i.purpose=N'TICKET_REFUND' AND i.status=N'REQUESTED') OR (i.purpose=N'COMPENSATION' AND i.status=N'APPROVED')))
    THROW 51000,N'Invalid initial refund state.',1;
IF EXISTS(SELECT 1 FROM inserted r JOIN dbo.orders o ON o.id=r.order_id LEFT JOIN dbo.payments p ON p.id=r.payment_id WHERE
    (r.purpose=N'TICKET_REFUND' AND (o.status<>N'PAID' OR EXISTS(SELECT r.payment_id EXCEPT SELECT o.accepted_payment_id)))
    OR (r.purpose=N'COMPENSATION' AND (p.id IS NULL OR p.status<>N'CAPTURED' OR r.amount<>p.amount OR o.accepted_payment_id=p.id))
    OR (r.amount>0 AND (p.id IS NULL OR p.status<>N'CAPTURED'))) THROW 51000,N'Refund does not match captured payment ownership.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(SELECT i.order_id,i.payment_id,i.amount,i.purpose EXCEPT SELECT d.order_id,d.payment_id,d.amount,d.purpose))
    THROW 51000,N'Refund ownership, purpose and amount are immutable.',1;
IF EXISTS(SELECT 1 FROM inserted r WHERE r.amount>0 AND r.status=N'COMPLETED' AND NOT EXISTS(SELECT 1 FROM dbo.refund_attempts a WHERE a.attempt_id=r.current_attempt_id AND a.refund_id=r.id AND a.status=N'SUCCEEDED'))
    THROW 51000,N'A successful attempt is required to complete a nonzero refund.',1;
IF EXISTS(SELECT 1 FROM inserted r JOIN dbo.payments p ON p.id=r.payment_id WHERE
    (SELECT SUM(amount) FROM dbo.refunds WHERE payment_id=p.id AND status<>N'REJECTED')>p.amount)
    THROW 51000,N'Refund reservations exceed the captured amount.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_refund_tickets_validate ON dbo.refund_tickets
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM deleted) THROW 51000,N'Refund ticket selections are immutable.',1;
IF EXISTS(SELECT 1 FROM inserted rt JOIN dbo.refunds r ON r.id=rt.refund_id JOIN dbo.tickets t ON t.id=rt.ticket_id JOIN dbo.order_items i ON i.id=t.order_item_id
    WHERE i.order_id<>r.order_id OR r.purpose<>N'TICKET_REFUND' OR r.status NOT IN(N'REQUESTED',N'APPROVED') OR t.status<>N'VALID')
    THROW 51000,N'Refund selections must reference valid tickets from their order.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.refund_tickets other ON other.ticket_id=i.ticket_id AND other.refund_id<>i.refund_id JOIN dbo.refunds r ON r.id=other.refund_id WHERE r.status<>N'REJECTED')
    THROW 51000,N'Ticket already belongs to an active or completed refund.',1;
IF EXISTS(SELECT 1 FROM dbo.refunds r JOIN (SELECT DISTINCT refund_id FROM inserted) x ON x.refund_id=r.id
    WHERE r.amount<>(SELECT SUM(t.paid_amount) FROM dbo.refund_tickets rt JOIN dbo.tickets t ON t.id=rt.ticket_id WHERE rt.refund_id=r.id))
    THROW 51000,N'Refund amount must equal its selected tickets.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_settlements_validate ON dbo.settlements
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM inserted WHERE dbo.fn_state_is_valid(N'Settlement',status)=0)
    THROW 51000,N'Invalid Settlement status.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE dbo.fn_can_transition(N'Settlement',d.status,i.status)=0)
    THROW 51000,N'Invalid Settlement state transition.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status NOT IN(N'DRAFT')) THROW 51000,N'Invalid initial Settlement state.',1;

IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE i.event_id<>d.event_id OR (d.status=N'CONFIRMED' AND EXISTS(
    SELECT i.gross_revenue,i.total_refund,i.total_commission EXCEPT SELECT d.gross_revenue,d.total_refund,d.total_commission)))
    THROW 51000,N'Confirmed settlement accounting is immutable.',1;
IF EXISTS(SELECT 1 FROM inserted WHERE status=N'CONFIRMED' AND available_to_pay<0) THROW 51000,N'Settlement is overpaid or over-reserved.',1;
IF EXISTS(SELECT 1 FROM inserted s WHERE
    s.paid_amount<>COALESCE((SELECT SUM(amount) FROM dbo.settlement_payouts WHERE settlement_id=s.id AND status=N'SUCCEEDED'),0)
    OR s.pending_amount<>COALESCE((SELECT SUM(amount) FROM dbo.settlement_payouts WHERE settlement_id=s.id AND status IN(N'PROCESSING',N'UNKNOWN')),0))
    THROW 51000,N'Settlement balances must match the payout ledger.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_refund_attempts_validate ON dbo.refund_attempts
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.attempt_id=d.attempt_id WHERE i.attempt_id IS NULL) THROW 51000,N'Transfer history cannot be deleted.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.attempt_id=i.attempt_id WHERE d.attempt_id IS NULL AND i.status<>N'PROCESSING') THROW 51000,N'Transfers must start as processing.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.attempt_id=i.attempt_id WHERE i.refund_id<>d.refund_id OR i.amount<>d.amount
    OR (d.status IN(N'SUCCEEDED',N'FAILED') AND EXISTS(SELECT i.status,i.provider_reference,i.processed_at EXCEPT SELECT d.status,d.provider_reference,d.processed_at))
    OR (d.status=N'UNKNOWN' AND i.status=N'PROCESSING')) THROW 51000,N'Transfer identity or terminal result is immutable.',1;

IF EXISTS(SELECT 1 FROM inserted a JOIN dbo.refunds r ON r.id=a.refund_id WHERE a.amount<>r.amount) THROW 51000,N'Attempt amount must match refund.',1;
END;
GO

CREATE OR ALTER TRIGGER dbo.tr_settlement_payouts_validate ON dbo.settlement_payouts
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM inserted) AND NOT EXISTS(SELECT 1 FROM deleted) RETURN;
IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.payout_id=d.payout_id WHERE i.payout_id IS NULL) THROW 51000,N'Transfer history cannot be deleted.',1;
IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.payout_id=i.payout_id WHERE d.payout_id IS NULL AND i.status<>N'PROCESSING') THROW 51000,N'Transfers must start as processing.',1;
IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.payout_id=i.payout_id WHERE i.settlement_id<>d.settlement_id OR i.amount<>d.amount
    OR (d.status IN(N'SUCCEEDED',N'FAILED') AND EXISTS(SELECT i.status,i.provider_reference,i.processed_at EXCEPT SELECT d.status,d.provider_reference,d.processed_at))
    OR (d.status=N'UNKNOWN' AND i.status=N'PROCESSING')) THROW 51000,N'Transfer identity or terminal result is immutable.',1;

IF EXISTS(SELECT 1 FROM inserted p JOIN dbo.settlements s ON s.id=p.settlement_id WHERE s.status<>N'CONFIRMED') THROW 51000,N'Payouts require a confirmed settlement.',1;
;WITH touched AS(SELECT settlement_id FROM inserted UNION SELECT settlement_id FROM deleted), totals AS(
    SELECT s.settlement_id,COALESCE(SUM(CASE WHEN p.status=N'SUCCEEDED' THEN p.amount ELSE 0 END),0) AS paid,
        COALESCE(SUM(CASE WHEN p.status IN(N'PROCESSING',N'UNKNOWN') THEN p.amount ELSE 0 END),0) AS pending
    FROM touched s LEFT JOIN dbo.settlement_payouts p ON p.settlement_id=s.settlement_id GROUP BY s.settlement_id
)
UPDATE s SET paid_amount=t.paid,pending_amount=t.pending FROM dbo.settlements s JOIN totals t ON t.settlement_id=s.id;
END;
GO
