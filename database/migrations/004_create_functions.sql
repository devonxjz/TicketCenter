/*
 * 004_create_functions
 *
 * Creates or alters all 11 user-defined scalar and inline table-valued functions.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* Function: fn_CalculateCommission */
CREATE OR ALTER FUNCTION dbo.fn_CalculateCommission(
    @remaining_amount decimal(19,0), @rate_percent decimal(7,4), @fixed_fee decimal(19,0)
)
RETURNS decimal(19,0)
AS
BEGIN
    IF @remaining_amount IS NULL OR @rate_percent IS NULL OR @fixed_fee IS NULL
       OR @remaining_amount < 0 OR @rate_percent < 0 OR @fixed_fee < 0 RETURN NULL;
    IF @remaining_amount = 0 RETURN 0;
    DECLARE @fee decimal(19,0) = ROUND(@remaining_amount * @rate_percent / 100.0 + @fixed_fee, 0);
    RETURN CASE WHEN @fee < @remaining_amount THEN @fee ELSE @remaining_amount END;
END;

GO

/* Function: fn_CalculateCouponDiscount */
CREATE OR ALTER FUNCTION dbo.fn_CalculateCouponDiscount(
    @subtotal decimal(19,0), @discount_type varchar(30),
    @percentage decimal(5,2), @fixed_amount decimal(19,0)
)
RETURNS decimal(19,0)
AS
BEGIN
    IF @subtotal IS NULL OR @subtotal < 0 RETURN NULL;
    DECLARE @cap decimal(19,0) = FLOOR(@subtotal * 0.30);
    IF @discount_type = 'PERCENTAGE' AND @percentage > 0 AND @percentage <= 30 AND @fixed_amount IS NULL
        RETURN FLOOR(@subtotal * @percentage / 100.0);
    IF @discount_type = 'FIXED_AMOUNT' AND @fixed_amount > 0 AND @percentage IS NULL
        RETURN CASE WHEN @fixed_amount < @cap THEN @fixed_amount ELSE @cap END;
    RETURN NULL;
END;

GO

/* Function: fn_event_is_check_in_open */
CREATE OR ALTER FUNCTION dbo.fn_event_is_check_in_open
(@event_id uniqueidentifier,@now datetime2(7))
RETURNS bit
AS
BEGIN
    DECLARE @open bit=0;
    SELECT @open=1 FROM dbo.tc_events
    WHERE id=@event_id AND status='PUBLISHED'
      AND @now>=DATEADD(minute,-60,start_time) AND @now<end_time;
    RETURN @open;
END;

GO

/* Function: fn_GetEventCheckInWindow */
CREATE OR ALTER FUNCTION dbo.fn_GetEventCheckInWindow(@event_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT id event_id, DATEADD(minute, -60, start_time) opens_at, end_time closes_at,
           CASE WHEN status = 'CANCELLED' THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_cancelled,
           CASE WHEN status <> 'CANCELLED' AND @now_utc >= DATEADD(minute, -60, start_time)
                     AND @now_utc < end_time THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_open
    FROM dbo.tc_events WHERE id = @event_id
);

GO

/* Function: fn_GetZoneAvailability */
CREATE OR ALTER FUNCTION dbo.fn_GetZoneAvailability(@zone_id uniqueidentifier)
RETURNS TABLE
AS RETURN (SELECT * FROM dbo.vw_ZoneInventory WHERE zone_id = @zone_id);

GO

/* Function: fn_GetCouponEligibility */
CREATE OR ALTER FUNCTION dbo.fn_GetCouponEligibility(
    @coupon_id uniqueidentifier, @organization_id uniqueidentifier,
    @subtotal decimal(19,0), @now_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    SELECT c.id coupon_id,
       CASE WHEN c.organization_id <> @organization_id THEN CAST(0 AS bit)
            WHEN c.active = 0 THEN CAST(0 AS bit)
            WHEN @now_utc < c.valid_from OR @now_utc >= c.valid_to THEN CAST(0 AS bit)
            WHEN u.available_uses <= 0 THEN CAST(0 AS bit) ELSE CAST(1 AS bit) END is_eligible,
       CASE WHEN c.organization_id <> @organization_id THEN 'WRONG_ORGANIZATION'
            WHEN c.active = 0 THEN 'INACTIVE'
            WHEN @now_utc < c.valid_from THEN 'NOT_STARTED' WHEN @now_utc >= c.valid_to THEN 'EXPIRED'
            WHEN u.available_uses <= 0 THEN 'QUOTA_EXHAUSTED' ELSE 'ELIGIBLE' END reason,
       u.available_uses,
       dbo.fn_CalculateCouponDiscount(@subtotal, c.discount_type, c.percentage_value, c.fixed_amount) preview_discount
    FROM dbo.tc_coupons c JOIN dbo.vw_CouponUsage u ON u.coupon_id = c.id WHERE c.id = @coupon_id
);

GO

/* Function: fn_GetRefundableTickets */
CREATE OR ALTER FUNCTION dbo.fn_GetRefundableTickets(@order_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT t.id ticket_id, t.paid_amount, oi.zone_name_snapshot, oi.seat_label_snapshot
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE o.id = @order_id AND @now_utc < e.start_time AND t.status = 'ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.ticket_id = t.id AND rt.is_open = 1)
);

GO

/* Function: fn_AllocateTicketPaidAmounts */
CREATE OR ALTER FUNCTION dbo.fn_AllocateTicketPaidAmounts(@order_id uniqueidentifier)
RETURNS TABLE
AS RETURN (
    WITH n AS (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
               UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8),
    units AS (
        SELECT oi.id order_item_id, n.n ticket_ordinal, oi.unit_price,
               o.subtotal_amount, o.discount_amount, o.total_amount,
               CAST(CASE WHEN o.subtotal_amount = 0 THEN 0
                    ELSE o.discount_amount * oi.unit_price / o.subtotal_amount END AS decimal(38,12)) raw_discount
        FROM dbo.tc_order_items oi JOIN dbo.tc_orders o ON o.id = oi.order_id
        JOIN n ON n.n <= oi.quantity WHERE o.id = @order_id
    ), base AS (
        SELECT *, FLOOR(raw_discount) base_discount, raw_discount - FLOOR(raw_discount) remainder,
               SUM(FLOOR(raw_discount)) OVER () allocated_base
        FROM units
    ), ranked AS (
        SELECT *, ROW_NUMBER() OVER (ORDER BY remainder DESC, order_item_id, ticket_ordinal) remainder_rank
        FROM base
    )
    SELECT order_item_id, ticket_ordinal, unit_price original_amount,
           CAST(base_discount + CASE WHEN remainder_rank <= discount_amount - allocated_base THEN 1 ELSE 0 END AS decimal(19,0)) discount_amount,
           CAST(unit_price - base_discount - CASE WHEN remainder_rank <= discount_amount - allocated_base THEN 1 ELSE 0 END AS decimal(19,0)) paid_amount
    FROM ranked
);

GO

/* Function: fn_GetOrganizationRevenue */
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationRevenue(
    @organization_id uniqueidentifier, @from_utc datetime2(3), @to_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    SELECT COUNT_BIG(*) paid_order_count,
           COALESCE(SUM(s.ticket_gross_amount), 0) gross_revenue,
           COALESCE(SUM(COALESCE(r.refunded_amount, 0)), 0) refunded_amount,
           COALESCE(SUM(s.ticket_gross_amount - COALESCE(r.refunded_amount, 0)), 0) net_revenue
    FROM dbo.vw_OrderFinancialSummary s
    OUTER APPLY (
        SELECT SUM(x.amount) refunded_amount
        FROM dbo.tc_refunds x
        JOIN dbo.tc_payments p ON p.id = x.payment_id
        WHERE p.order_id = s.order_id AND x.purpose = 'CUSTOMER_REFUND'
          AND x.status = 'SUCCEEDED' AND x.processed_at < @to_utc
    ) r
    WHERE s.organization_id = @organization_id AND s.status = 'PAID'
      AND s.paid_at >= @from_utc AND s.paid_at < @to_utc
);

GO

/* Function: fn_GetOrganizationCashFlow */
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationCashFlow(
    @organization_id uniqueidentifier, @from_utc datetime2(3), @to_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    WITH flows AS (
        SELECT CASE WHEN EXISTS (
                        SELECT 1 FROM dbo.tc_refunds r
                        WHERE r.payment_id = p.id AND r.purpose = 'PAYMENT_COMPENSATION'
                    ) THEN 'COMPENSATION_CAPTURE' ELSE 'TICKET_CAPTURE' END flow_type,
               p.amount inflow, CAST(0 AS decimal(19,0)) outflow
        FROM dbo.tc_payments p
        JOIN dbo.tc_orders o ON o.id = p.order_id
        JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND p.status = 'CAPTURED'
          AND o.paid_at >= @from_utc AND o.paid_at < @to_utc
        UNION ALL
        SELECT CASE WHEN r.purpose = 'PAYMENT_COMPENSATION' THEN 'COMPENSATION_REFUND' ELSE 'TICKET_REFUND' END,
               CAST(0 AS decimal(19,0)), r.amount
        FROM dbo.tc_refunds r
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id
        JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND r.status = 'SUCCEEDED'
          AND r.processed_at >= @from_utc AND r.processed_at < @to_utc
    )
    SELECT flow_type, SUM(inflow) inflow_amount, SUM(outflow) outflow_amount,
           SUM(inflow - outflow) net_amount
    FROM flows GROUP BY flow_type
);

GO

/* Function: fn_GetSettlementBlockers */
CREATE OR ALTER FUNCTION dbo.fn_GetSettlementBlockers(@event_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT CAST('EVENT_NOT_ENDED' AS varchar(40)) blocker_type, e.id blocker_id,
           CAST(NULL AS uniqueidentifier) related_id
    FROM dbo.tc_events e
    WHERE e.id = @event_id AND @now_utc < e.end_time

    UNION ALL
    SELECT 'PAYMENT_UNRESOLVED', p.id, p.order_id
    FROM dbo.tc_payments p
    JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND p.status IN ('PENDING', 'UNKNOWN')

    UNION ALL
    SELECT 'REFUND_REQUEST_OPEN', rr.id, rr.order_id
    FROM dbo.tc_refund_requests rr
    JOIN dbo.tc_orders o ON o.id = rr.order_id
    WHERE o.event_id = @event_id AND rr.status IN ('PENDING', 'APPROVED')

    UNION ALL
    SELECT 'REFUND_UNRESOLVED', r.id, p.order_id
    FROM dbo.tc_refunds r
    JOIN dbo.tc_payments p ON p.id = r.payment_id
    JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND r.status IN ('PENDING', 'UNKNOWN')

    UNION ALL
    SELECT 'COMPENSATION_UNRESOLVED', p.id, p.order_id
    FROM dbo.tc_payments p
    JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND p.status = 'CAPTURED'
      AND EXISTS (SELECT 1 FROM dbo.tc_refunds r WHERE r.payment_id = p.id AND r.purpose = 'PAYMENT_COMPENSATION')
      AND COALESCE((SELECT SUM(r.amount) FROM dbo.tc_refunds r
                    WHERE r.payment_id = p.id AND r.purpose = 'PAYMENT_COMPENSATION'
                      AND r.status = 'SUCCEEDED'), 0) < p.amount

    UNION ALL
    SELECT 'CANCELLATION_PENDING', x.id, x.aggregate_id
    FROM dbo.tc_outbox x
    WHERE x.aggregate_type = 'EVENT' AND x.aggregate_id = @event_id
      AND x.event_type = 'EVENT_CANCELLED' AND x.status <> 'PUBLISHED'
);

GO
