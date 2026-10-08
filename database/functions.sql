-- SQL Server 2017+. Install after schema.sql.
-- The diagram does not enumerate states. The values below define this SQL API.
-- Roles and event/refund categories remain application-defined strings.
-- Time windows are half-open [start, end); every time parameter is UTC.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER FUNCTION dbo.fn_state_is_valid (@entity NVARCHAR(32), @state NVARCHAR(64))
RETURNS BIT
AS
BEGIN
    RETURN CASE WHEN EXISTS (
        SELECT 1 FROM (VALUES
        (N'User',N'ACTIVE'),(N'User',N'BLOCKED'),(N'User',N'INACTIVE'),
        (N'Organization',N'DRAFT'),(N'Organization',N'PENDING_APPROVAL'),(N'Organization',N'APPROVED'),(N'Organization',N'REJECTED'),
        (N'Event',N'DRAFT'),(N'Event',N'PENDING_APPROVAL'),(N'Event',N'PUBLISHED'),(N'Event',N'REJECTED'),(N'Event',N'CANCELLED'),
        (N'Seat',N'AVAILABLE'),(N'Seat',N'HELD'),(N'Seat',N'SOLD'),
        (N'Settlement',N'DRAFT'),(N'Settlement',N'CONFIRMED'),
        (N'Hold',N'ACTIVE'),(N'Hold',N'RELEASED'),(N'Hold',N'CONSUMED'),
        (N'Order',N'AWAITING_PAYMENT'),(N'Order',N'PAID'),(N'Order',N'CANCELLED'),(N'Order',N'EXPIRED'),
        (N'Ticket',N'VALID'),(N'Ticket',N'CHECKED_IN'),(N'Ticket',N'REFUND_PENDING'),(N'Ticket',N'REFUNDED'),
        (N'Payment',N'PENDING'),(N'Payment',N'UNKNOWN'),(N'Payment',N'CAPTURED'),(N'Payment',N'FAILED'),
        (N'Refund',N'REQUESTED'),(N'Refund',N'APPROVED'),(N'Refund',N'REJECTED'),(N'Refund',N'PROCESSING'),
        (N'Refund',N'UNKNOWN'),(N'Refund',N'FAILED'),(N'Refund',N'COMPLETED')
        ) AS s(entity, state) WHERE s.entity = @entity AND s.state = @state
    ) THEN 1 ELSE 0 END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_can_transition (@entity NVARCHAR(32), @old NVARCHAR(64), @new NVARCHAR(64))
RETURNS BIT
AS
BEGIN
    IF @old = @new AND dbo.fn_state_is_valid(@entity,@new) = 1 RETURN 1;
    RETURN CASE WHEN EXISTS (
        SELECT 1 FROM (VALUES
        (N'User',N'ACTIVE',N'BLOCKED'),(N'User',N'ACTIVE',N'INACTIVE'),(N'User',N'BLOCKED',N'ACTIVE'),(N'User',N'INACTIVE',N'ACTIVE'),
        (N'Organization',N'DRAFT',N'PENDING_APPROVAL'),(N'Organization',N'REJECTED',N'PENDING_APPROVAL'),
        (N'Organization',N'PENDING_APPROVAL',N'APPROVED'),(N'Organization',N'PENDING_APPROVAL',N'REJECTED'),
        (N'Event',N'DRAFT',N'PENDING_APPROVAL'),(N'Event',N'REJECTED',N'PENDING_APPROVAL'),
        (N'Event',N'PENDING_APPROVAL',N'PUBLISHED'),(N'Event',N'PENDING_APPROVAL',N'REJECTED'),
        (N'Event',N'DRAFT',N'CANCELLED'),(N'Event',N'REJECTED',N'CANCELLED'),
        (N'Event',N'PENDING_APPROVAL',N'CANCELLED'),(N'Event',N'PUBLISHED',N'CANCELLED'),
        (N'Seat',N'AVAILABLE',N'HELD'),(N'Seat',N'HELD',N'AVAILABLE'),(N'Seat',N'HELD',N'SOLD'),(N'Seat',N'SOLD',N'AVAILABLE'),
        (N'Settlement',N'DRAFT',N'CONFIRMED'),
        (N'Hold',N'ACTIVE',N'RELEASED'),(N'Hold',N'ACTIVE',N'CONSUMED'),
        (N'Order',N'AWAITING_PAYMENT',N'PAID'),(N'Order',N'AWAITING_PAYMENT',N'CANCELLED'),(N'Order',N'AWAITING_PAYMENT',N'EXPIRED'),
        (N'Ticket',N'VALID',N'CHECKED_IN'),(N'Ticket',N'VALID',N'REFUND_PENDING'),
        (N'Ticket',N'REFUND_PENDING',N'VALID'),(N'Ticket',N'REFUND_PENDING',N'REFUNDED'),
        (N'Payment',N'PENDING',N'CAPTURED'),(N'Payment',N'PENDING',N'UNKNOWN'),(N'Payment',N'PENDING',N'FAILED'),
        (N'Payment',N'UNKNOWN',N'CAPTURED'),(N'Payment',N'UNKNOWN',N'FAILED'),(N'Payment',N'FAILED',N'CAPTURED'),
        (N'Refund',N'REQUESTED',N'APPROVED'),(N'Refund',N'REQUESTED',N'REJECTED'),
        (N'Refund',N'APPROVED',N'REJECTED'),(N'Refund',N'APPROVED',N'PROCESSING'),(N'Refund',N'APPROVED',N'COMPLETED'),
        (N'Refund',N'PROCESSING',N'UNKNOWN'),(N'Refund',N'PROCESSING',N'FAILED'),(N'Refund',N'PROCESSING',N'COMPLETED'),
        (N'Refund',N'UNKNOWN',N'FAILED'),(N'Refund',N'UNKNOWN',N'COMPLETED'),(N'Refund',N'FAILED',N'PROCESSING')
        ) AS s(entity, old_state, new_state)
        WHERE s.entity = @entity AND s.old_state = @old AND s.new_state = @new
    ) THEN 1 ELSE 0 END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_user_is_eligible_buyer (@user_id BIGINT)
RETURNS BIT
AS
BEGIN
    RETURN CASE WHEN EXISTS (SELECT 1 FROM dbo.users WHERE id=@user_id AND status=N'ACTIVE' AND email_verified=1) THEN 1 ELSE 0 END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_coupon_is_active_at (@coupon_id BIGINT, @now DATETIME2(6))
RETURNS BIT
AS
BEGIN
    RETURN CASE WHEN EXISTS (
        SELECT 1 FROM dbo.coupons c WHERE c.id=@coupon_id AND c.is_active=1
        AND c.valid_from<=@now AND @now<c.valid_to
        AND c.max_uses > (SELECT COUNT_BIG(*) FROM dbo.orders o JOIN dbo.ticket_holds h ON h.id=o.hold_id
            WHERE o.coupon_id=c.id AND (o.status=N'PAID' OR (o.status=N'AWAITING_PAYMENT' AND h.status=N'ACTIVE' AND h.expires_at>@now)))
    ) THEN 1 ELSE 0 END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_coupon_calculate_discount (@coupon_id BIGINT, @subtotal DECIMAL(19,2))
RETURNS DECIMAL(19,2)
AS
BEGIN
    DECLARE @discount DECIMAL(19,2);
    IF @subtotal IS NULL OR @subtotal<0 RETURN NULL;
    SELECT @discount=CASE discount_type WHEN N'FIXED' THEN fixed_amount WHEN N'PERCENTAGE' THEN ROUND(@subtotal*percentage/100,2) END
    FROM dbo.coupons WHERE id=@coupon_id;
    RETURN CASE WHEN @discount>@subtotal THEN @subtotal ELSE @discount END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_commission_rule_is_effective_at (@rule_id BIGINT, @now DATETIME2(6))
RETURNS BIT
AS
BEGIN
    RETURN CASE WHEN EXISTS(SELECT 1 FROM dbo.commission_rules WHERE id=@rule_id AND effective_from<=@now AND @now<effective_to) THEN 1 ELSE 0 END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_commission_rule_calculate_fee (@rule_id BIGINT, @remaining DECIMAL(19,2))
RETURNS DECIMAL(19,2)
AS
BEGIN
    DECLARE @fee DECIMAL(19,2);
    IF @remaining IS NULL OR @remaining<0 RETURN NULL;
    IF @remaining=0 RETURN 0;
    SELECT @fee=ROUND(@remaining*rate_percent/100,2)+fixed_fee FROM dbo.commission_rules WHERE id=@rule_id;
    RETURN CASE WHEN @fee>@remaining THEN @remaining ELSE @fee END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_event_is_sale_active (@event_id BIGINT, @now DATETIME2(6))
RETURNS BIT
AS
BEGIN
    RETURN CASE WHEN EXISTS(SELECT 1 FROM dbo.events e JOIN dbo.organizations o ON o.id=e.organization_id
        WHERE e.id=@event_id AND e.status=N'PUBLISHED' AND o.status=N'APPROVED'
        AND e.sale_start<=@now AND @now<e.sale_end AND @now<e.start_time) THEN 1 ELSE 0 END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_event_is_check_in_open (@event_id BIGINT, @now DATETIME2(6))
RETURNS BIT
AS
BEGIN
    RETURN CASE WHEN EXISTS(SELECT 1 FROM dbo.events WHERE id=@event_id AND status=N'PUBLISHED' AND start_time<=@now AND @now<end_time) THEN 1 ELSE 0 END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_zone_get_capacity (@zone_id BIGINT)
RETURNS INT
AS
BEGIN
    DECLARE @value INT;
    SELECT @value=CASE WHEN z.type=N'STANDING' THEN z.standing_capacity ELSE (SELECT COUNT(*) FROM dbo.seats WHERE zone_id=z.id) END
    FROM dbo.zones z WHERE z.id=@zone_id;
    RETURN @value;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_zone_get_available (@zone_id BIGINT)
RETURNS INT
AS
BEGIN
    DECLARE @value INT;
    SELECT @value=CASE WHEN z.type=N'STANDING' THEN z.standing_capacity-z.standing_held-z.standing_sold
        ELSE (SELECT COUNT(*) FROM dbo.seats WHERE zone_id=z.id AND status=N'AVAILABLE') END
    FROM dbo.zones z WHERE z.id=@zone_id;
    RETURN @value;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_ticket_hold_is_expired (@hold_id BIGINT, @now DATETIME2(6))
RETURNS BIT
AS
BEGIN
    RETURN CASE WHEN EXISTS(SELECT 1 FROM dbo.ticket_holds WHERE id=@hold_id AND expires_at<=@now) THEN 1 ELSE 0 END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_ticket_hold_is_active (@hold_id BIGINT, @now DATETIME2(6))
RETURNS BIT
AS
BEGIN
    RETURN CASE WHEN EXISTS(SELECT 1 FROM dbo.ticket_holds WHERE id=@hold_id AND status=N'ACTIVE' AND expires_at>@now) THEN 1 ELSE 0 END;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_ticket_hold_item_line_total (@item_id BIGINT)
RETURNS DECIMAL(19,2)
AS
BEGIN
    RETURN (SELECT CAST(quantity*unit_price AS DECIMAL(19,2)) FROM dbo.ticket_hold_items WHERE id=@item_id);
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_order_item_line_total (@item_id BIGINT)
RETURNS DECIMAL(19,2)
AS
BEGIN
    RETURN (SELECT CAST(quantity*unit_price AS DECIMAL(19,2)) FROM dbo.order_items WHERE id=@item_id);
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_order_subtotal (@order_id BIGINT)
RETURNS DECIMAL(19,2)
AS
BEGIN
    RETURN (SELECT CAST(COALESCE((SELECT SUM(quantity*unit_price) FROM dbo.order_items WHERE order_id=o.id),0) AS DECIMAL(19,2)) FROM dbo.orders o WHERE id=@order_id);
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_order_total (@order_id BIGINT)
RETURNS DECIMAL(19,2)
AS
BEGIN
    RETURN (SELECT dbo.fn_order_subtotal(id)-discount_amount FROM dbo.orders WHERE id=@order_id);
END;
GO
