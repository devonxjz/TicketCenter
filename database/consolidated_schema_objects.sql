/*
========================================================================================
TICKETSCENTER - CONSOLIDATED DATABASE PROGRAMMABLE OBJECTS
Total Active Objects: 207
  - Functions: 21
  - Views: 67
  - Stored Procedures: 93
  - Triggers: 26
Generated from migrations 001 through 068 following APPLY_ORDER.md.
========================================================================================
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/*
========================================================================================
SECTION: FUNCTIONs (Total: 21)
========================================================================================
*/

-- Object: [FUNCTION] dbo.fn_AllocateTicketPaidAmounts
-- Latest Source Migration: 004_create_functions.sql
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

-- Object: [FUNCTION] dbo.fn_AuditEventMatch
-- Latest Source Migration: 057_application_read_models.sql
/* ---- audit scope: does an audit row concern this event / this organization? One row when it does. ---- */
CREATE OR ALTER FUNCTION dbo.fn_AuditEventMatch(@event_id uniqueidentifier,@entity_type varchar(100),@entity_id varchar(100),@metadata_json nvarchar(max))
RETURNS TABLE AS RETURN
 SELECT 1 matched FROM dbo.tc_events e
 WHERE e.id=@event_id AND (
   (@entity_type='Event' AND TRY_CONVERT(uniqueidentifier,@entity_id)=e.id)
   OR TRY_CONVERT(uniqueidentifier,JSON_VALUE(CASE WHEN ISJSON(@metadata_json)=1 THEN @metadata_json ELSE N'{}' END,'$.eventId'))=e.id
   OR EXISTS(SELECT 1 FROM dbo.tc_settlements s WHERE s.event_id=e.id AND (
        (@entity_type='Settlement' AND TRY_CONVERT(uniqueidentifier,@entity_id)=s.id)
        OR TRY_CONVERT(uniqueidentifier,JSON_VALUE(CASE WHEN ISJSON(@metadata_json)=1 THEN @metadata_json ELSE N'{}' END,'$.settlementId'))=s.id
        OR EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs l WHERE l.settlement_id=s.id
                  AND @entity_type='SettlementTransferLog' AND TRY_CONVERT(uniqueidentifier,@entity_id)=l.id))));
GO

-- Object: [FUNCTION] dbo.fn_AuditOrganizationMatch
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER FUNCTION dbo.fn_AuditOrganizationMatch(@organization_id uniqueidentifier,@entity_type varchar(100),@entity_id varchar(100),@metadata_json nvarchar(max))
RETURNS TABLE AS RETURN
 SELECT 1 matched
 WHERE (@entity_type='Organization' AND TRY_CONVERT(uniqueidentifier,@entity_id)=@organization_id)
    OR TRY_CONVERT(uniqueidentifier,JSON_VALUE(CASE WHEN ISJSON(@metadata_json)=1 THEN @metadata_json ELSE N'{}' END,'$.organizationId'))=@organization_id
    OR EXISTS(SELECT 1 FROM dbo.tc_events e WHERE e.organization_id=@organization_id
              AND EXISTS(SELECT 1 FROM dbo.fn_AuditEventMatch(e.id,@entity_type,@entity_id,@metadata_json)))
    OR EXISTS(SELECT 1 FROM dbo.tc_commission_rules c WHERE c.organization_id=@organization_id
              AND @entity_type='CommissionRule' AND TRY_CONVERT(uniqueidentifier,@entity_id)=c.id);
GO

-- Object: [FUNCTION] dbo.fn_CalculateCommission
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER FUNCTION dbo.fn_CalculateCommission(@remaining_amount decimal(19,0),@rate_percent decimal(12,4),@fixed_fee decimal(19,0))
RETURNS decimal(19,0) AS
BEGIN
 IF @remaining_amount IS NULL OR @rate_percent IS NULL OR @fixed_fee IS NULL OR @remaining_amount<0 OR @rate_percent<0 OR @rate_percent>100 OR @fixed_fee<0 RETURN NULL;
 IF @remaining_amount=0 RETURN 0;
 DECLARE @fee decimal(38,4)=ROUND(CAST(@remaining_amount AS decimal(23,0))*@rate_percent/100,0)+@fixed_fee;
 RETURN CONVERT(decimal(19,0),CASE WHEN @fee>@remaining_amount THEN @remaining_amount ELSE @fee END);
END;
GO

-- Object: [FUNCTION] dbo.fn_CalculateCouponDiscount
-- Latest Source Migration: 004_create_functions.sql
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

-- Object: [FUNCTION] dbo.fn_CalculateCouponDiscountCapped
-- Latest Source Migration: 035_sql_report_runtime.sql
CREATE OR ALTER FUNCTION dbo.fn_CalculateCouponDiscountCapped(@subtotal decimal(19,0),@coupon_id uniqueidentifier)
RETURNS decimal(19,0) AS BEGIN
 DECLARE @discount decimal(19,0),@cap decimal(19,0);
 SELECT @discount=dbo.fn_CalculateCouponDiscount(@subtotal,discount_type,percentage_value,fixed_amount),@cap=max_discount_amount FROM dbo.tc_coupons WHERE id=@coupon_id;
 RETURN CASE WHEN @cap IS NOT NULL AND @cap<@discount THEN @cap ELSE @discount END;
END;
GO

-- Object: [FUNCTION] dbo.fn_CheckInEventTicketCounts
-- Latest Source Migration: 057_application_read_models.sql
/* Issued tickets of an event (including later used/refunded ones) and how many were checked in; never prices. */
CREATE OR ALTER FUNCTION dbo.fn_CheckInEventTicketCounts(@event_id uniqueidentifier)
RETURNS TABLE AS RETURN
 SELECT COUNT_BIG(*) issued_count,
        COALESCE(SUM(CASE WHEN t.status IN('CHECKED_IN','USED') THEN CAST(1 AS bigint) ELSE CAST(0 AS bigint) END),0) checked_in_count
 FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id
 JOIN dbo.tc_orders o ON o.id=i.order_id WHERE o.event_id=@event_id;
GO

-- Object: [FUNCTION] dbo.fn_DashboardOrganizationCounts
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER FUNCTION dbo.fn_DashboardOrganizationCounts(@organization_id uniqueidentifier)
RETURNS TABLE AS RETURN
 SELECT (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE organization_id=@organization_id) event_count,
        (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE organization_id=@organization_id AND status='PENDING_APPROVAL') pending_events,
        (SELECT COUNT_BIG(*) FROM dbo.vw_VuongOrders o JOIN dbo.tc_events e ON e.id=o.event_id
         WHERE e.organization_id=@organization_id AND o.status='PAID') paid_orders;
GO

-- Object: [FUNCTION] dbo.fn_GetCouponEligibility
-- Latest Source Migration: 004_create_functions.sql
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

-- Object: [FUNCTION] dbo.fn_GetEventCheckInWindow
-- Latest Source Migration: 039_sql_report_contract_consistency.sql
CREATE OR ALTER FUNCTION dbo.fn_GetEventCheckInWindow(@event_id uniqueidentifier,@now_utc datetime2(3))
RETURNS TABLE AS RETURN (
 SELECT id event_id,DATEADD(minute,-60,start_time) opens_at,end_time closes_at,
 CASE WHEN status='CANCELLED' THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_cancelled,
 CASE WHEN status='PUBLISHED' AND @now_utc>=DATEADD(minute,-60,start_time) AND @now_utc<end_time THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_open
 FROM dbo.tc_events WHERE id=@event_id);
GO

-- Object: [FUNCTION] dbo.fn_GetOrganizationCashFlow
-- Latest Source Migration: 035_sql_report_runtime.sql
IF OBJECT_ID('dbo.vw_VuongPayments') IS NULL EXEC(N'/* Function: fn_GetOrganizationCashFlow */
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationCashFlow(
    @organization_id uniqueidentifier, @from_utc datetime2(3), @to_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    WITH flows AS (
        SELECT CASE WHEN EXISTS (
                        SELECT 1 FROM dbo.tc_refunds r
                        WHERE r.payment_id = p.id AND r.purpose = ''PAYMENT_COMPENSATION''
                    ) THEN ''COMPENSATION_CAPTURE'' ELSE ''TICKET_CAPTURE'' END flow_type,
               p.amount inflow, CAST(0 AS decimal(19,0)) outflow
        FROM dbo.tc_payments p
        JOIN dbo.tc_orders o ON o.id = p.order_id
        JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND p.status = ''CAPTURED''
          AND p.captured_at >= @from_utc AND p.captured_at < @to_utc
        UNION ALL
        SELECT CASE WHEN r.purpose = ''PAYMENT_COMPENSATION'' THEN ''COMPENSATION_REFUND'' ELSE ''TICKET_REFUND'' END,
               CAST(0 AS decimal(19,0)), r.amount
        FROM dbo.tc_refunds r
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id
        JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND r.status = ''SUCCEEDED''
          AND r.processed_at >= @from_utc AND r.processed_at < @to_utc
    )
    SELECT flow_type, SUM(inflow) inflow_amount, SUM(outflow) outflow_amount,
           SUM(inflow - outflow) net_amount
    FROM flows GROUP BY flow_type
);');
GO

-- Object: [FUNCTION] dbo.fn_GetOrganizationRevenue
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationRevenue(@organization_id uniqueidentifier,@from_utc datetime2(3),@to_utc datetime2(3))
RETURNS TABLE AS RETURN
SELECT e.id event_id,e.organization_id,COALESCE(f.paid_order_count,0) paid_order_count,
 CAST(COALESCE(f.gross_revenue,0) AS decimal(19,0)) gross_revenue,CAST(COALESCE(f.total_refund,0) AS decimal(19,0)) total_refund,
 CAST(COALESCE(f.gross_revenue-f.total_refund,0) AS decimal(19,0)) remaining_amount,CAST(COALESCE(f.total_commission,0) AS decimal(19,0)) total_commission,
 CAST(COALESCE(f.gross_revenue-f.total_refund-f.total_commission,0) AS decimal(19,0)) net_payable
FROM dbo.tc_events e OUTER APPLY (
 SELECT COUNT_BIG(*) paid_order_count,SUM(o.total_amount) gross_revenue,SUM(COALESCE(r.amount,0)) total_refund,
 SUM(dbo.fn_CalculateCommission(o.total_amount-COALESCE(r.amount,0),c.rate_percent,c.fixed_fee)) total_commission
 FROM dbo.vw_VuongOrders o LEFT JOIN dbo.vw_VuongPayments p ON p.id=o.accepted_payment_id LEFT JOIN dbo.tc_commission_rules c ON c.id=e.commission_rule_id
 OUTER APPLY(SELECT SUM(r.amount) amount FROM dbo.vw_VuongRefunds r WHERE r.order_id=o.id AND r.purpose='CUSTOMER_REFUND' AND EXISTS
 (SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=r.id AND l.result='SUCCEEDED' AND l.processed_at<@to_utc)) r
 WHERE o.event_id=e.id AND o.status='PAID' AND o.paid_at>=@from_utc AND o.paid_at<@to_utc AND
 (o.total_amount=0 OR (p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount))) f
WHERE (@organization_id IS NULL OR e.organization_id=@organization_id) AND @from_utc<@to_utc;
GO

-- Object: [FUNCTION] dbo.fn_GetRefundableTickets
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER FUNCTION dbo.fn_GetRefundableTickets(@order_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT t.id ticket_id, t.paid_amount, oi.zone_name_snapshot, oi.seat_label_snapshot
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE o.id = @order_id AND @now_utc < e.start_time AND t.status = 'ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_tickets rt WHERE rt.ticket_id = t.id AND rt.is_open = 1)
);
GO

-- Object: [FUNCTION] dbo.fn_GetSettlementBlockers
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER FUNCTION dbo.fn_GetSettlementBlockers(@event_id uniqueidentifier,@now_utc datetime2(3)) RETURNS TABLE AS RETURN
SELECT CAST('EVENT_NOT_ENDED' AS varchar(40)) blocker_type,e.id object_id,CAST('Scheduled event end has not passed' AS nvarchar(200)) detail FROM dbo.tc_events e WHERE e.id=@event_id AND e.end_time>@now_utc
UNION ALL SELECT 'ORDER_PAYMENT',id,'Order payment is unresolved' FROM dbo.vw_VuongOrders WHERE event_id=@event_id AND status='PENDING_PAYMENT'
UNION ALL SELECT 'PAYMENT',p.id,'Capture is pending or unknown' FROM dbo.vw_VuongPayments p JOIN dbo.vw_VuongOrders o ON o.id=p.order_id WHERE o.event_id=@event_id AND p.status IN('PENDING','UNKNOWN')
UNION ALL SELECT 'REFUND',r.id,'Refund obligation is unresolved' FROM dbo.vw_VuongRefunds r JOIN dbo.vw_VuongOrders o ON o.id=r.order_id WHERE o.event_id=@event_id AND r.status NOT IN('COMPLETED','REJECTED')
UNION ALL SELECT 'REFUND_VERIFICATION',r.id,'Completed positive refund lacks a verified successful transfer' FROM dbo.vw_VuongRefunds r JOIN dbo.vw_VuongOrders o ON o.id=r.order_id WHERE o.event_id=@event_id AND r.status='COMPLETED' AND r.amount>0 AND
 (NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=r.id AND l.result='SUCCEEDED' AND l.processed_at IS NOT NULL) OR
 (r.current_attempt_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.id=r.current_attempt_id AND l.refund_id=r.id AND l.result='SUCCEEDED' AND l.processed_at IS NOT NULL)))
UNION ALL SELECT 'CANCELLATION_WORK',x.id,'Cancellation outbox work is unfinished' FROM dbo.tc_outbox x WHERE x.event_type='EVENT_CANCELLED' AND x.status<>'PUBLISHED' AND TRY_CONVERT(uniqueidentifier,JSON_VALUE(CASE WHEN ISJSON(x.payload)=1 THEN x.payload ELSE '{}' END,'$.eventId'))=@event_id
UNION ALL SELECT 'CANCELLED_REVENUE',f.order_id,'Cancelled order still has unrefunded ticket revenue' FROM dbo.vw_OrderFinancialSummary f JOIN dbo.tc_events e ON e.id=f.event_id WHERE e.id=@event_id AND e.status='CANCELLED' AND f.remaining_amount>0
UNION ALL SELECT 'INVALID_FINANCIALS',f.order_id,'Accepted order or commission financial data is invalid' FROM dbo.vw_OrderFinancialSummary f WHERE f.event_id=@event_id AND f.status='PAID' AND (f.commission_amount IS NULL OR f.remaining_amount<0 OR (f.total_amount>0 AND f.ticket_revenue=0));
GO

-- Object: [FUNCTION] dbo.fn_GetZoneAvailability
-- Latest Source Migration: 004_create_functions.sql
/* Function: fn_GetZoneAvailability */
CREATE OR ALTER FUNCTION dbo.fn_GetZoneAvailability(@zone_id uniqueidentifier)
RETURNS TABLE
AS RETURN (SELECT * FROM dbo.vw_ZoneInventory WHERE zone_id = @zone_id);
GO

-- Object: [FUNCTION] dbo.fn_IsLocalEventImageUrl
-- Latest Source Migration: 061_persistent_local_event_images.sql
/* Only immutable, same-origin image paths are accepted in addition to existing provider URLs. */
CREATE OR ALTER FUNCTION dbo.fn_IsLocalEventImageUrl(@url varchar(4000))
RETURNS bit
AS
BEGIN
 DECLARE @route varchar(30)='/assets/event-images/',@position int=CHARINDEX('/assets/event-images/',@url COLLATE Latin1_General_BIN2);
 IF @url IS NULL OR @position=0 OR DATALENGTH(@url)<>LEN(@url) RETURN 0;
 DECLARE @context varchar(4000)=LEFT(@url,@position-1),@filename varchar(4000)=SUBSTRING(@url,@position+LEN(@route),4000);
 IF @context<>'' AND (LEFT(@context,1)<>'/' OR LEN(@context)<2 OR SUBSTRING(@context,2,4000) COLLATE Latin1_General_BIN2 LIKE '%[^A-Za-z0-9_-]%') RETURN 0;
 IF LEN(@filename)<>68 OR RIGHT(@filename,4) COLLATE Latin1_General_BIN2<>'.img'
    OR LEFT(@filename,64) COLLATE Latin1_General_BIN2 LIKE '%[^0-9a-f]%' RETURN 0;
 RETURN 1;
END;
GO

-- Object: [FUNCTION] dbo.fn_ReportCouponVisibility
-- Latest Source Migration: 034_sql_report_read_scope.sql
-- The payment/order policy scopes financial projections. Coupons have an independent organization root.
CREATE OR ALTER FUNCTION dbo.fn_ReportCouponVisibility(@organization uniqueidentifier)
RETURNS TABLE WITH SCHEMABINDING AS RETURN
 SELECT 1 permitted WHERE USER_NAME()=N'dbo' OR COALESCE(IS_MEMBER('db_owner'),0)=1
 OR COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_worker'),0)=1
 -- Buyer has only eligibility/procedure access; code-containing aggregates are denied above.
 OR COALESCE(IS_ROLEMEMBER('tc_buyer'),0)=1
 OR EXISTS(SELECT 1 FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id=m.user_id
 WHERE m.organization_id=@organization AND m.user_id=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id')) AND m.role='MANAGER' AND m.active=1 AND u.status='ACTIVE');
GO

-- Object: [FUNCTION] dbo.fn_ReportOrderVisibility
-- Latest Source Migration: 034_sql_report_read_scope.sql
CREATE OR ALTER FUNCTION dbo.fn_ReportOrderVisibility(@owner uniqueidentifier,@event uniqueidentifier)
RETURNS TABLE WITH SCHEMABINDING AS RETURN
 SELECT 1 permitted WHERE
 COALESCE(IS_MEMBER('db_owner'),0)=1 OR USER_NAME()=N'dbo'
 OR COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_worker'),0)=1
 OR COALESCE(IS_ROLEMEMBER('tc_checkin'),0)=1
 OR @owner=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id'))
 OR (COALESCE(IS_ROLEMEMBER('tc_manager'),0)=1 AND EXISTS(
 SELECT 1 FROM dbo.tc_events e JOIN dbo.tc_organization_memberships m ON m.organization_id=e.organization_id
 JOIN dbo.tc_users u ON u.id=m.user_id
 WHERE e.id=@event AND m.user_id=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id')) AND m.role='MANAGER' AND m.active=1 AND u.status='ACTIVE'));
GO

-- Object: [FUNCTION] dbo.fn_ReportOrganizationRequestVisibility
-- Latest Source Migration: 034_sql_report_read_scope.sql
CREATE OR ALTER FUNCTION dbo.fn_ReportOrganizationRequestVisibility(@applicant uniqueidentifier)
RETURNS TABLE WITH SCHEMABINDING AS RETURN
 SELECT 1 permitted WHERE USER_NAME()=N'dbo' OR COALESCE(IS_MEMBER('db_owner'),0)=1
 OR COALESCE(IS_ROLEMEMBER('tc_auth'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)=1
 OR @applicant=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id'));
GO

-- Object: [FUNCTION] dbo.fn_ReportUserVisibility
-- Latest Source Migration: 034_sql_report_read_scope.sql
CREATE OR ALTER FUNCTION dbo.fn_ReportUserVisibility(@user uniqueidentifier)
RETURNS TABLE WITH SCHEMABINDING AS RETURN
 SELECT 1 permitted WHERE
 COALESCE(IS_MEMBER('db_owner'),0)=1 OR USER_NAME()=N'dbo'
 OR COALESCE(IS_ROLEMEMBER('tc_auth'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)=1
 OR COALESCE(IS_ROLEMEMBER('tc_worker'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_checkin'),0)=1
 OR @user=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id'))
 OR (COALESCE(IS_ROLEMEMBER('tc_manager'),0)=1 AND EXISTS(
 SELECT 1 FROM dbo.tc_organization_memberships member JOIN dbo.tc_organization_memberships manager ON manager.organization_id=member.organization_id
 WHERE member.user_id=@user AND member.active=1 AND manager.user_id=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id')) AND manager.role='MANAGER' AND manager.active=1));
GO

-- Object: [FUNCTION] dbo.fn_event_is_check_in_open
-- Latest Source Migration: 030_khanh02_signed_checkin.sql
-- The KHANH-01 deployment may already provide this helper. Fresh schemas need it too.
IF OBJECT_ID('dbo.fn_event_is_check_in_open') IS NULL
 EXEC(N'CREATE FUNCTION dbo.fn_event_is_check_in_open(@event_id uniqueidentifier,@now datetime2(7)) RETURNS bit AS BEGIN DECLARE @open bit=0; SELECT @open=1 FROM dbo.tc_events WHERE id=@event_id AND status=''PUBLISHED'' AND @now>=DATEADD(minute,-60,start_time) AND @now<end_time; RETURN @open; END;');
GO

/*
========================================================================================
SECTION: VIEWs (Total: 67)
========================================================================================
*/

-- Object: [VIEW] dbo.tc_refund_attempts
-- Latest Source Migration: 048_khanh05_refund_worker.sql
CREATE OR ALTER VIEW dbo.tc_refund_attempts AS
 SELECT l.id attempt_id,l.refund_id,l.attempt_no attempt_number,l.status,l.provider_reference provider_ref,l.result,l.created_at,l.processed_at,
 COALESCE(l.failure_reason,d.last_error) error_message
 FROM dbo.tc_refund_transfer_logs l LEFT JOIN dbo.tc_refund_dispatches d ON d.attempt_id=l.id;
GO

-- Object: [VIEW] dbo.v_admin_cancellation_progress
-- Latest Source Migration: 058_khanh06_event_cancellation.sql
CREATE OR ALTER VIEW dbo.v_admin_cancellation_progress AS
 SELECT j.event_id,e.organization_id,j.status,j.reason,j.cancelled_at,j.total_orders,j.processed_orders,
  j.last_processed_order_id,j.updated_at,
  (SELECT COUNT_BIG(*) FROM dbo.tc_refund_obligations r JOIN dbo.tc_cancellation_orders q ON q.order_id=r.order_id
   WHERE q.event_id=j.event_id AND r.purpose='CUSTOMER_REFUND' AND r.status='COMPLETED') successful_refunds,
  (SELECT COUNT_BIG(*) FROM dbo.tc_refund_obligations r JOIN dbo.tc_cancellation_orders q ON q.order_id=r.order_id
   WHERE q.event_id=j.event_id AND r.purpose='CUSTOMER_REFUND' AND r.status='RETRYABLE') failed_refunds,
  (SELECT COUNT_BIG(*) FROM dbo.tc_refund_obligations r JOIN dbo.tc_cancellation_orders q ON q.order_id=r.order_id
   WHERE q.event_id=j.event_id AND r.purpose='CUSTOMER_REFUND' AND r.status='NEEDS_RECONCILIATION') unknown_refunds,
  (SELECT COUNT_BIG(*) FROM dbo.tc_refund_obligations r JOIN dbo.tc_cancellation_orders q ON q.order_id=r.order_id
   WHERE q.event_id=j.event_id AND r.purpose='CUSTOMER_REFUND' AND r.status IN('REQUESTED','APPROVED','PROCESSING')) pending_refunds,
  (SELECT COUNT_BIG(*) FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id=t.order_item_id
   JOIN dbo.tc_cancellation_orders q ON q.order_id=oi.order_id WHERE q.event_id=j.event_id AND t.status='USED') used_ticket_exceptions
 FROM dbo.tc_cancellation_jobs j JOIN dbo.tc_events e ON e.id=j.event_id;
GO

-- Object: [VIEW] dbo.v_admin_refund_actor
-- Latest Source Migration: 043_khanh04_admin_refund.sql
CREATE OR ALTER VIEW dbo.v_admin_refund_actor AS
SELECT u.id,u.status,u.auth_version,
 CAST(CASE WHEN EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles p WHERE p.user_id=u.id AND p.role='ADMIN') THEN 1 ELSE 0 END AS bit) is_admin
FROM dbo.tc_users u;
GO

-- Object: [VIEW] dbo.v_admin_refund_attempts
-- Latest Source Migration: 043_khanh04_admin_refund.sql
CREATE OR ALTER VIEW dbo.v_admin_refund_attempts AS
SELECT refund_id,id attempt_id,attempt_no,status,provider_reference,created_at,processed_at
FROM dbo.tc_refund_transfer_logs;
GO

-- Object: [VIEW] dbo.v_admin_refund_requests
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER VIEW dbo.v_admin_refund_requests AS
SELECT r.id refund_id,r.order_id,o.user_id buyer_id,u.full_name buyer_name,u.email buyer_email,
 e.title event_name,r.amount,r.reason,r.status,r.created_at,r.rejection_reason,r.current_attempt_id,
 (SELECT COUNT(*) FROM dbo.tc_refund_tickets t WHERE t.refund_id=r.id) ticket_count,
 r.reviewer_id,r.decided_at,
 CAST(CASE WHEN r.status='REQUESTED' AND r.current_attempt_id IS NULL THEN 1 ELSE 0 END AS bit) can_decide,
 CAST(CASE WHEN r.status='RETRYABLE' AND a.status='FAILED'
  AND NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs f WHERE f.refund_id=r.id AND f.status IN('PENDING','UNKNOWN','SUCCEEDED'))
  AND NOT EXISTS(SELECT 1 FROM dbo.tc_gateway_refund_receipts x WHERE x.payment_id=r.payment_id AND x.resolution='NEEDS_RECONCILIATION')
  THEN 1 ELSE 0 END AS bit) can_retry
FROM dbo.tc_refund_obligations r
JOIN dbo.tc_orders o ON o.id=r.order_id JOIN dbo.tc_events e ON e.id=o.event_id
JOIN dbo.tc_users u ON u.id=o.user_id
LEFT JOIN dbo.tc_refund_transfer_logs a ON a.id=r.current_attempt_id AND a.refund_id=r.id
WHERE r.purpose='CUSTOMER_REFUND';
GO

-- Object: [VIEW] dbo.v_refund_attempts
-- Latest Source Migration: 029_khanh03_refund_request.sql
CREATE OR ALTER VIEW dbo.v_refund_attempts AS
SELECT refund_id,id attempt_id,attempt_no,status,created_at,processed_at
FROM dbo.tc_refund_transfer_logs;
GO

-- Object: [VIEW] dbo.v_refund_tickets
-- Latest Source Migration: 029_khanh03_refund_request.sql
CREATE OR ALTER VIEW dbo.v_refund_tickets AS
SELECT rt.refund_id,t.id ticket_id,t.ticket_code,i.zone_name_snapshot,
       i.seat_label_snapshot,t.paid_amount,t.status
FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id=rt.ticket_id
JOIN dbo.tc_order_items i ON i.id=t.order_item_id;
GO

-- Object: [VIEW] dbo.v_refundable_orders
-- Latest Source Migration: 029_khanh03_refund_request.sql
CREATE OR ALTER VIEW dbo.v_refundable_orders AS
SELECT o.id order_id,o.order_code,o.user_id owner_id,o.status order_status,e.title event_name,
       e.status event_status,e.start_time
FROM dbo.tc_orders o JOIN dbo.tc_events e ON e.id=o.event_id;
GO

-- Object: [VIEW] dbo.v_refundable_ticket_options
-- Latest Source Migration: 029_khanh03_refund_request.sql
CREATE OR ALTER VIEW dbo.v_refundable_ticket_options AS
SELECT o.id order_id,o.user_id owner_id,t.id ticket_id,t.ticket_code,i.zone_name_snapshot,
       i.seat_label_snapshot,t.paid_amount,t.status,
       CAST(CASE WHEN EXISTS(SELECT 1 FROM dbo.tc_refund_tickets rt WHERE rt.ticket_id=t.id AND rt.is_open=1)
         THEN 1 ELSE 0 END AS bit) has_open_refund
FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id
JOIN dbo.tc_orders o ON o.id=i.order_id;
GO

-- Object: [VIEW] dbo.v_refunds
-- Latest Source Migration: 029_khanh03_refund_request.sql
CREATE OR ALTER VIEW dbo.v_refunds AS
SELECT r.id refund_id,r.order_id,o.user_id owner_id,e.title event_name,r.amount,r.status,
       r.reason,r.rejection_reason,r.created_at,r.processed_at,r.current_attempt_id,
       (SELECT COUNT(*) FROM dbo.tc_refund_tickets rt WHERE rt.refund_id=r.id) ticket_count
FROM dbo.tc_refund_obligations r JOIN dbo.tc_orders o ON o.id=r.order_id
JOIN dbo.tc_events e ON e.id=o.event_id WHERE r.purpose='CUSTOMER_REFUND';
GO

-- Object: [VIEW] dbo.v_worker_cancellation_orders
-- Latest Source Migration: 058_khanh06_event_cancellation.sql
CREATE OR ALTER VIEW dbo.v_worker_cancellation_orders AS
 SELECT q.event_id,q.order_id,j.cancelled_at FROM dbo.tc_cancellation_orders q
 JOIN dbo.tc_cancellation_jobs j ON j.event_id=q.event_id WHERE q.processed_at IS NULL;
GO

-- Object: [VIEW] dbo.vw_ActiveManagerMemberships
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER VIEW dbo.vw_ActiveManagerMemberships AS
 SELECT user_id,organization_id FROM dbo.tc_organization_memberships WHERE active=1 AND role='MANAGER';
GO

-- Object: [VIEW] dbo.vw_ActorAuthority
-- Latest Source Migration: 057_application_read_models.sql
/* ---- authority ---- */
CREATE OR ALTER VIEW dbo.vw_ActorAuthority AS
 SELECT id,status,auth_version,platform_role FROM dbo.tc_users;
GO

-- Object: [VIEW] dbo.vw_AdminRefundApprovalNotes
-- Latest Source Migration: 057_application_read_models.sql
/* ---- refunds ---- */
CREATE OR ALTER VIEW dbo.vw_AdminRefundApprovalNotes AS
 SELECT aggregate_id refund_id,JSON_VALUE(detail,'$.note') note,created_at,id
 FROM dbo.tc_audit_logs WHERE action='ADMIN_REFUND_APPROVE' AND ISJSON(detail)=1;
GO

-- Object: [VIEW] dbo.vw_AuditDisplayNames
-- Latest Source Migration: 062_audit_display_names.sql
CREATE OR ALTER VIEW dbo.vw_AuditDisplayNames AS
SELECT a.id,a.actor_id,a.action,a.entity_type,a.entity_id,a.metadata_json,a.created_at,
 COALESCE(NULLIF(u.full_name,N''),NULLIF(u.user_name,N''),N'Hệ thống') actor_name,
 COALESCE(e.title,o.name,
  CASE WHEN se.id IS NOT NULL THEN CONCAT(N'Quyết toán · ',se.title) END,
  CASE WHEN re.id IS NOT NULL THEN CONCAT(N'Hoàn vé · ',re.title) END,
  CASE WHEN co.id IS NOT NULL THEN CONCAT(N'Hoa hồng · ',co.name) END,
  CASE WHEN pe.id IS NOT NULL THEN CONCAT(N'Chi trả · ',pe.title) END,
  CASE WHEN oe.id IS NOT NULL THEN CONCAT(N'Đơn vé · ',oe.title) END) entity_name
FROM dbo.vw_AuditLogs a
LEFT JOIN dbo.tc_users u ON u.id=a.actor_id
LEFT JOIN dbo.tc_events e ON e.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Event','tc_events','dbo.tc_events')
LEFT JOIN dbo.tc_organizations o ON o.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Organization','tc_organizations','dbo.tc_organizations')
LEFT JOIN dbo.tc_settlements s ON s.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Settlement','tc_settlements','dbo.tc_settlements')
LEFT JOIN dbo.tc_events se ON se.id=s.event_id
LEFT JOIN dbo.tc_refund_obligations r ON r.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Refund','RefundRequest','tc_refund_obligations','dbo.tc_refund_obligations','tc_refund_requests','dbo.tc_refund_requests')
LEFT JOIN dbo.tc_orders ro ON ro.id=r.order_id
LEFT JOIN dbo.tc_events re ON re.id=ro.event_id
LEFT JOIN dbo.tc_commission_rules c ON c.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('CommissionRule','tc_commission_rules','dbo.tc_commission_rules')
LEFT JOIN dbo.tc_organizations co ON co.id=c.organization_id
LEFT JOIN dbo.tc_settlement_transfer_logs p ON p.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('SettlementTransferLog','Payout','tc_settlement_transfer_logs','dbo.tc_settlement_transfer_logs')
LEFT JOIN dbo.tc_settlements ps ON ps.id=p.settlement_id
LEFT JOIN dbo.tc_events pe ON pe.id=ps.event_id
LEFT JOIN dbo.tc_orders ord ON ord.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Order','tc_orders','dbo.tc_orders')
LEFT JOIN dbo.tc_events oe ON oe.id=ord.event_id;
GO

-- Object: [VIEW] dbo.vw_AuditLogs
-- Latest Source Migration: 060_compensation_retry_and_neutral_views.sql
CREATE OR ALTER VIEW dbo.vw_AuditLogs AS
 SELECT CONVERT(uniqueidentifier,CONVERT(binary(16),id)) id,actor_id,action,aggregate_type entity_type,
        CONVERT(varchar(100),aggregate_id) entity_id,detail metadata_json,created_at,created_at updated_at
 FROM dbo.tc_audit_logs;
GO

-- Object: [VIEW] dbo.vw_BuyerOrderDetails
-- Latest Source Migration: 044_buyer_order_history_views.sql
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

-- Object: [VIEW] dbo.vw_BuyerOrderItems
-- Latest Source Migration: 044_buyer_order_history_views.sql
CREATE OR ALTER VIEW dbo.vw_BuyerOrderItems AS
SELECT oi.order_id, oi.id, oi.zone_name_snapshot, oi.seat_label_snapshot,
       oi.quantity, oi.unit_price, oi.quantity * oi.unit_price AS line_total
FROM dbo.tc_order_items oi
JOIN dbo.tc_orders o ON o.id = oi.order_id;
GO

-- Object: [VIEW] dbo.vw_CheckInEvents
-- Latest Source Migration: 066_checkin_event_archive_visibility.sql
CREATE OR ALTER VIEW dbo.vw_CheckInEvents AS
SELECT e.id,e.organization_id,e.title,e.venue_name,e.start_time,e.end_time,e.status
FROM dbo.tc_events e WHERE e.archived_at IS NULL;
GO

-- Object: [VIEW] dbo.vw_CheckInHistory
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER VIEW dbo.vw_CheckInHistory
AS
SELECT ci.id check_in_id, ci.event_id, e.organization_id, ci.actor_id, ci.scanned_at,
       ci.result, ci.ticket_id, oi.zone_name_snapshot, oi.seat_label_snapshot
FROM dbo.tc_ticket_check_in_logs ci JOIN dbo.tc_events e ON e.id = ci.event_id
LEFT JOIN dbo.tc_tickets t ON t.id = ci.ticket_id
LEFT JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id;
GO

-- Object: [VIEW] dbo.vw_CheckInStaffOrganizations
-- Latest Source Migration: 057_application_read_models.sql
/* ---- check-in (no financial columns) ---- */
CREATE OR ALTER VIEW dbo.vw_CheckInStaffOrganizations AS
 SELECT m.user_id,m.organization_id
 FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id=m.user_id
 WHERE m.active=1 AND u.status='ACTIVE' AND m.role IN('MANAGER','CHECK_IN_STAFF');
GO

-- Object: [VIEW] dbo.vw_CommissionRuleUsage
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER VIEW dbo.vw_CommissionRuleUsage AS
 SELECT c.id rule_id,c.organization_id,c.rate_percent,c.fixed_fee,c.effective_from,c.effective_to,
        (SELECT COUNT_BIG(*) FROM dbo.tc_events e WHERE e.commission_rule_id=c.id) associated_events_count
 FROM dbo.tc_commission_rules c;
GO

-- Object: [VIEW] dbo.vw_CouponUsage
-- Latest Source Migration: 005_create_views.sql
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

-- Object: [VIEW] dbo.vw_DashboardPlatformCounts
-- Latest Source Migration: 057_application_read_models.sql
/* ---- dashboard ---- */
CREATE OR ALTER VIEW dbo.vw_DashboardPlatformCounts AS
 SELECT (SELECT COUNT_BIG(*) FROM dbo.tc_users) total_users,
        (SELECT COUNT_BIG(*) FROM dbo.tc_organizations WHERE status='APPROVED') active_organizations,
        (SELECT COUNT_BIG(*) FROM dbo.tc_organizations WHERE status='PENDING_APPROVAL') pending_organizations,
        (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE status='PENDING_APPROVAL') pending_events,
        (SELECT COUNT_BIG(*) FROM dbo.vw_VuongRefunds WHERE status='REQUESTED') pending_refunds,
        (SELECT COUNT_BIG(*) FROM dbo.tc_settlements WHERE status='DRAFT') draft_settlements;
GO

-- Object: [VIEW] dbo.vw_EventCancellationExceptions
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
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
    FROM dbo.tc_refund_transfer_logs r
    JOIN dbo.tc_refund_obligations rr ON rr.id = r.refund_id
    JOIN dbo.tc_orders o ON o.id = rr.order_id
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED' AND rr.reason_type = 'EVENT_CANCELLATION'
      AND r.status IN ('FAILED', 'UNKNOWN');
GO

-- Object: [VIEW] dbo.vw_EventCancellationProgress
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER VIEW dbo.vw_EventCancellationProgress
AS
    SELECT e.id event_id, e.status event_status,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o WHERE o.event_id = e.id) total_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id AND (
                EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
                OR EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                           WHERE oi.order_id = o.id AND t.status = 'ACTIVE')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_obligations rr WHERE rr.order_id = o.id AND rr.purpose = 'CUSTOMER_REFUND' AND rr.status = 'REQUESTED')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_obligations rr
                           LEFT JOIN dbo.tc_refund_transfer_logs r ON r.refund_id = rr.id
                           WHERE rr.order_id = o.id AND rr.reason_type = 'EVENT_CANCELLATION'
                             AND rr.request_status = 'APPROVED' AND (r.id IS NULL OR r.status = 'PENDING')))) pending_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                              WHERE oi.order_id = o.id AND t.status NOT IN ('REFUNDED', 'INVALIDATED'))
              AND NOT EXISTS (SELECT 1 FROM dbo.vw_EventCancellationExceptions x WHERE x.order_id = o.id)) completed_orders,
           (SELECT COUNT_BIG(*) FROM dbo.vw_EventCancellationExceptions x WHERE x.event_id = e.id) exception_count,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refund_obligations rr JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION') requests_created,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refund_transfer_logs r JOIN dbo.tc_refund_obligations rr ON rr.id = r.refund_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION' AND r.status = 'SUCCEEDED') refunds_succeeded,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refund_transfer_logs r JOIN dbo.tc_refund_obligations rr ON rr.id = r.refund_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION' AND r.status = 'PENDING') refunds_pending
    FROM dbo.tc_events e;
GO

-- Object: [VIEW] dbo.vw_EventCancellationWork
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
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
              SELECT 1 FROM dbo.tc_refund_obligations rr
              WHERE rr.order_id = o.id AND rr.purpose = 'CUSTOMER_REFUND' AND rr.status = 'REQUESTED'));
GO

-- Object: [VIEW] dbo.vw_EventCategories
-- Latest Source Migration: 057_application_read_models.sql
/* ---- public catalog ---- */
CREATE OR ALTER VIEW dbo.vw_EventCategories AS
 SELECT id,name,slug FROM dbo.tc_event_categories;
GO

-- Object: [VIEW] dbo.vw_EventCheckinCounts
-- Latest Source Migration: 060_compensation_retry_and_neutral_views.sql
CREATE OR ALTER VIEW dbo.vw_EventCheckinCounts AS
 SELECT o.event_id,COUNT_BIG(DISTINCT t.id) tickets_checked_in_count
 FROM dbo.tc_ticket_check_in_logs l JOIN dbo.tc_tickets t ON t.id=l.ticket_id
 JOIN dbo.tc_order_items i ON i.id=t.order_item_id JOIN dbo.vw_VuongOrders o ON o.id=i.order_id
 WHERE l.result='SUCCESS' AND l.event_id=o.event_id GROUP BY o.event_id;
GO

-- Object: [VIEW] dbo.vw_EventSalesReport
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER VIEW dbo.vw_EventSalesReport AS
SELECT e.id event_id,e.organization_id,e.title event_title,e.status event_status,e.end_time,
 COALESCE(f.paid_order_count,0) paid_order_count,COALESCE(t.ticket_count,0) ticket_count,COALESCE(t.valid_ticket_count,0) valid_ticket_count,COALESCE(t.refunded_ticket_count,0) refunded_ticket_count,
 CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN s.gross_revenue ELSE COALESCE(f.gross_revenue,0) END AS decimal(19,0)) gross_revenue,
 CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN s.total_refund ELSE COALESCE(f.total_refund,0) END AS decimal(19,0)) total_refund,
 CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN s.total_commission ELSE COALESCE(f.total_commission,0) END AS decimal(19,0)) total_commission,
 CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN s.net_payable ELSE COALESCE(f.net_payable,0) END AS decimal(19,0)) net_payable,
 s.id settlement_id,s.status settlement_status,CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN 1 ELSE 0 END AS bit) is_confirmed
FROM dbo.tc_events e LEFT JOIN dbo.tc_settlements s ON s.event_id=e.id
OUTER APPLY(SELECT COUNT_BIG(*) paid_order_count,SUM(ticket_revenue) gross_revenue,SUM(ticket_refund) total_refund,SUM(commission_amount) total_commission,SUM(net_amount) net_payable FROM dbo.vw_OrderFinancialSummary WHERE event_id=e.id AND status='PAID') f
OUTER APPLY(SELECT COUNT_BIG(*) ticket_count,SUM(CASE WHEN t.status IN('ACTIVE','USED') THEN CONVERT(bigint,1) ELSE 0 END) valid_ticket_count,SUM(CASE WHEN t.status='REFUNDED' THEN CONVERT(bigint,1) ELSE 0 END) refunded_ticket_count FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id JOIN dbo.vw_VuongOrders o ON o.id=i.order_id WHERE o.event_id=e.id) t;
GO

-- Object: [VIEW] dbo.vw_ExpiredHolds
-- Latest Source Migration: 036_sql_report_worker_reads.sql
CREATE OR ALTER VIEW dbo.vw_ExpiredHolds AS
 SELECT id,user_id,event_id,expires_at FROM dbo.tc_ticket_holds WHERE status='ACTIVE' AND expires_at<=SYSUTCDATETIME();
GO

-- Object: [VIEW] dbo.vw_FailedCompensationAttempts
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER VIEW dbo.vw_FailedCompensationAttempts
AS
    SELECT DISTINCT r.payment_id, r.order_id
    FROM dbo.vw_RefundAttemptLedger r
    WHERE r.purpose = 'PAYMENT_COMPENSATION' AND r.status = 'FAILED'
      AND NOT EXISTS (
          SELECT 1 FROM dbo.vw_RefundAttemptLedger active
          WHERE active.payment_id = r.payment_id AND active.purpose = 'PAYMENT_COMPENSATION'
            AND active.status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED'));
GO

-- Object: [VIEW] dbo.vw_FinanceEvents
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER VIEW dbo.vw_FinanceEvents AS
 SELECT e.id,e.title,e.organization_id,e.created_at,e.end_time,s.id settlement_id,s.status settlement_status
 FROM dbo.tc_events e LEFT JOIN dbo.tc_settlements s ON s.event_id=e.id;
GO

-- Object: [VIEW] dbo.vw_FinanceOrganizations
-- Latest Source Migration: 057_application_read_models.sql
/* ---- finance ---- */
CREATE OR ALTER VIEW dbo.vw_FinanceOrganizations AS
 SELECT id,name FROM dbo.tc_organizations;
GO

-- Object: [VIEW] dbo.vw_FinanceSettlements
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER VIEW dbo.vw_FinanceSettlements AS
 SELECT s.id,s.event_id,e.organization_id
 FROM dbo.tc_settlements s JOIN dbo.tc_events e ON e.id=s.event_id;
GO

-- Object: [VIEW] dbo.vw_GatewayRefundReconciliation
-- Latest Source Migration: 036_sql_report_worker_reads.sql
CREATE OR ALTER VIEW dbo.vw_GatewayRefundReconciliation AS
 SELECT id,refund_id,payment_id,observed_status,provider_reference,reason,received_at
 FROM dbo.tc_gateway_refund_receipts WHERE resolution='NEEDS_RECONCILIATION';
GO

-- Object: [VIEW] dbo.vw_ManagedEvents
-- Latest Source Migration: 065_managed_event_archive_visibility.sql
CREATE OR ALTER VIEW dbo.vw_ManagedEvents AS
SELECT e.id,e.organization_id,e.category_id,e.title,e.description,e.venue_name,e.venue_address,
 e.sale_start,e.sale_end,e.start_time,e.end_time,e.status,e.version,NULLIF(e.event_image,'') image_url,
 e.event_image_public_id,cat.slug category_slug,e.event_image
FROM dbo.tc_events e JOIN dbo.tc_event_categories cat ON cat.id=e.category_id
WHERE e.archived_at IS NULL;
GO

-- Object: [VIEW] dbo.vw_ManagerOrganizations
-- Latest Source Migration: 057_application_read_models.sql
/* Organizations a manager may create events for: approved, active MANAGER membership. */
CREATE OR ALTER VIEW dbo.vw_ManagerOrganizations AS
 SELECT m.user_id,o.id,o.name
 FROM dbo.tc_organizations o JOIN dbo.tc_organization_memberships m ON m.organization_id=o.id
 WHERE m.active=1 AND m.role='MANAGER' AND o.status='APPROVED';
GO

-- Object: [VIEW] dbo.vw_OrderFinancialSummary
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER VIEW dbo.vw_OrderFinancialSummary AS
WITH financial AS (
SELECT o.id order_id,o.buyer_id owner_id,o.event_id,e.organization_id,o.status,o.paid_at,o.subtotal_amount,o.discount_amount,o.total_amount,
 CAST(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.id IS NOT NULL AND p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount ELSE 0 END AS decimal(19,0)) ticket_revenue,
 CAST(CASE WHEN o.status='PAID' THEN COALESCE(r.ticket_refund,0) ELSE 0 END AS decimal(19,0)) ticket_refund,
 CAST(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.id IS NOT NULL AND p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount-COALESCE(r.ticket_refund,0) ELSE 0 END AS decimal(19,0)) remaining_amount,
 dbo.fn_CalculateCommission(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount-COALESCE(r.ticket_refund,0) ELSE 0 END,c.rate_percent,c.fixed_fee) commission_amount,
 CAST(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount-COALESCE(r.ticket_refund,0) ELSE 0 END-
 dbo.fn_CalculateCommission(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount-COALESCE(r.ticket_refund,0) ELSE 0 END,c.rate_percent,c.fixed_fee) AS decimal(19,0)) net_amount,
 CAST(COALESCE(pc.amount,0) AS decimal(19,0)) compensation_captured,CAST(COALESCE(r.compensation_refund,0) AS decimal(19,0)) compensation_refund
FROM dbo.vw_VuongOrders o JOIN dbo.tc_events e ON e.id=o.event_id LEFT JOIN dbo.vw_VuongPayments p ON p.id=o.accepted_payment_id
LEFT JOIN dbo.tc_commission_rules c ON c.id=e.commission_rule_id
OUTER APPLY(SELECT SUM(CASE WHEN purpose='CUSTOMER_REFUND' THEN amount ELSE 0 END) ticket_refund,SUM(CASE WHEN purpose='PAYMENT_COMPENSATION' THEN amount ELSE 0 END) compensation_refund FROM dbo.vw_VuongRefunds WHERE order_id=o.id AND status='COMPLETED') r
OUTER APPLY(SELECT SUM(amount) amount FROM dbo.vw_VuongPayments WHERE order_id=o.id AND status='CAPTURED' AND (o.accepted_payment_id IS NULL OR id<>o.accepted_payment_id)) pc
)
/* Preserve retained owner column names with the approved accepted-payment and
   obligation semantics. This does not authorize the old mutation procedures. */
SELECT f.*,ticket_revenue ticket_gross_amount,ticket_refund ticket_refund_amount,
 ticket_refund successful_ticket_refund_amount,remaining_amount remaining_ticket_amount,
 compensation_refund compensation_refund_amount,ticket_revenue+compensation_captured captured_amount,
 COALESCE(t.issued_ticket_amount,0) issued_ticket_amount
FROM financial f OUTER APPLY(SELECT SUM(t.paid_amount) issued_ticket_amount FROM dbo.tc_tickets t
 JOIN dbo.tc_order_items i ON i.id=t.order_item_id WHERE i.order_id=f.order_id) t;
GO

-- Object: [VIEW] dbo.vw_OrderTicketQuantity
-- Latest Source Migration: 040_payment_ticket_runtime_reads.sql
CREATE OR ALTER VIEW dbo.vw_OrderTicketQuantity AS
 SELECT i.order_id,COALESCE(SUM(i.quantity),0) quantity
 FROM dbo.tc_order_items i JOIN dbo.tc_orders o ON o.id=i.order_id GROUP BY i.order_id;
GO

-- Object: [VIEW] dbo.vw_OrganizationMembers
-- Latest Source Migration: 005_create_views.sql
/* View: vw_OrganizationMembers */
CREATE OR ALTER VIEW dbo.vw_OrganizationMembers
AS
SELECT m.id membership_id, m.organization_id, m.user_id, u.email, u.full_name,
       m.role, m.active, u.status user_status, m.joined_at
FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id = m.user_id;
GO

-- Object: [VIEW] dbo.vw_OutboxEmailContext
-- Latest Source Migration: 049_outbox_email_runtime.sql
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
 JOIN dbo.tc_refund_transfer_logs l ON l.id=x.aggregate_id
 JOIN dbo.tc_refund_obligations r ON r.id=l.refund_id
 JOIN dbo.tc_orders o ON o.id=r.order_id
 JOIN dbo.tc_users u ON u.id=o.user_id
 JOIN dbo.tc_events e ON e.id=o.event_id
 WHERE x.event_type='REFUND_SUCCEEDED' AND x.aggregate_type='REFUND';
GO

-- Object: [VIEW] dbo.vw_PaymentOrderContext
-- Latest Source Migration: 040_payment_ticket_runtime_reads.sql
CREATE OR ALTER VIEW dbo.vw_PaymentOrderContext AS
 SELECT o.id order_id,o.user_id,o.order_code,o.total_amount,o.status order_status,
        h.expires_at,h.status hold_status,e.status event_status
 FROM dbo.tc_orders o JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id
 JOIN dbo.tc_events e ON e.id=o.event_id;
GO

-- Object: [VIEW] dbo.vw_PaymentRuntimeDetails
-- Latest Source Migration: 040_payment_ticket_runtime_reads.sql
CREATE OR ALTER VIEW dbo.vw_PaymentRuntimeDetails AS
 SELECT p.id payment_id,p.order_id,o.user_id,p.txn_ref,p.amount,p.currency,
        p.status payment_status,o.status order_status,o.total_amount,p.captured_at AS paid_at,
        p.created_at,o.order_code,h.expires_at
 FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id
 JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id;
GO

-- Object: [VIEW] dbo.vw_PaymentsToReconcile
-- Latest Source Migration: 036_sql_report_worker_reads.sql
CREATE OR ALTER VIEW dbo.vw_PaymentsToReconcile AS
 SELECT p.id,p.order_id,p.txn_ref,p.amount,p.status,p.provider_reference,p.created_at,p.captured_at,o.event_id,h.expires_at hold_expires_at
 FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id
 WHERE p.status IN('PENDING','UNKNOWN') AND p.created_at<DATEADD(minute,-5,SYSUTCDATETIME());
GO

-- Object: [VIEW] dbo.vw_PublicEventCatalog
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER VIEW dbo.vw_PublicEventCatalog AS
 SELECT e.event_id,e.organization_id,e.category_id,e.title,e.description,e.cover_image_url,e.venue_name,e.venue_address,
        e.sale_start,e.sale_end,e.start_time,e.end_time,e.minimum_price,
        c.name category_name,o.name organizer_name,prices.maximum_price,
        CASE WHEN SYSUTCDATETIME()>=e.sale_start AND SYSUTCDATETIME()<e.sale_end THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_on_sale
 FROM dbo.vw_PublicEvents e
 JOIN dbo.tc_event_categories c ON c.id=e.category_id
 JOIN dbo.tc_organizations o ON o.id=e.organization_id
 OUTER APPLY (SELECT MAX(zi.price) maximum_price FROM dbo.vw_ZoneInventory zi WHERE zi.event_id=e.event_id) prices;
GO

-- Object: [VIEW] dbo.vw_PublicEventSeats
-- Latest Source Migration: 045_event_commands_and_public_seats.sql
CREATE OR ALTER VIEW dbo.vw_PublicEventSeats AS
 SELECT s.id,s.zone_id,s.row_name,s.seat_number,s.label,s.status
 FROM dbo.tc_seats s JOIN dbo.tc_zones z ON z.id=s.zone_id
 JOIN dbo.tc_events e ON e.id=z.event_id WHERE e.status='PUBLISHED';
GO

-- Object: [VIEW] dbo.vw_PublicEvents
-- Latest Source Migration: 064_event_publication_archive.sql
CREATE OR ALTER VIEW dbo.vw_PublicEvents AS
SELECT e.id event_id,e.organization_id,e.category_id,e.title,e.description,
 e.event_image cover_image_url,e.venue_name,e.venue_address,e.sale_start,e.sale_end,
 e.start_time,e.end_time,MIN(z.price) minimum_price
FROM dbo.tc_events e LEFT JOIN dbo.tc_zones z ON z.event_id=e.id
WHERE e.status='PUBLISHED' AND e.archived_at IS NULL
GROUP BY e.id,e.organization_id,e.category_id,e.title,e.description,e.event_image,
 e.venue_name,e.venue_address,e.sale_start,e.sale_end,e.start_time,e.end_time;
GO

-- Object: [VIEW] dbo.vw_RefundAttemptLedger
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
/* ---- C. rewritten views / functions / procedures (write canonical tables directly) ---- */
CREATE OR ALTER VIEW dbo.vw_RefundAttemptLedger AS
/* One row per transfer attempt with the financial terms of its obligation.
   Amount, payment and purpose live on the obligation; the attempt only records the transfer. */
SELECT l.id attempt_id,l.refund_id,o.order_id,o.payment_id,o.purpose,o.amount,l.attempt_no,
       l.status,l.provider_reference,l.created_at,l.processed_at
FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refund_obligations o ON o.id=l.refund_id;
GO

-- Object: [VIEW] dbo.vw_RefundOverview
-- Latest Source Migration: 005_create_views.sql
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

-- Object: [VIEW] dbo.vw_RefundRequestOverview
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER VIEW dbo.vw_RefundRequestOverview
AS
WITH requested AS (
    SELECT rt.refund_id, COUNT_BIG(*) ticket_count, SUM(t.paid_amount) requested_amount
    FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
    GROUP BY rt.refund_id
), attempts AS (
    SELECT refund_id, SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END) refunded_amount,
           SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) pending_attempts,
           SUM(CASE WHEN status = 'UNKNOWN' THEN 1 ELSE 0 END) unknown_attempts,
           SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) failed_attempts
    FROM dbo.vw_RefundAttemptLedger WHERE purpose = 'CUSTOMER_REFUND' GROUP BY refund_id
)
SELECT rr.id refund_request_id, o.user_id owner_id, o.event_id, e.organization_id,
       rr.reason_type, rr.reason, rr.request_status status, rr.created_at requested_at, rr.decided_at,
       COALESCE(q.ticket_count, 0) ticket_count, COALESCE(q.requested_amount, 0) requested_amount,
       COALESCE(a.refunded_amount, 0) refunded_amount, COALESCE(a.pending_attempts, 0) pending_attempts,
       COALESCE(a.unknown_attempts, 0) unknown_attempts, COALESCE(a.failed_attempts, 0) failed_attempts
FROM dbo.tc_refund_obligations rr JOIN dbo.tc_orders o ON o.id = rr.order_id
JOIN dbo.tc_events e ON e.id = o.event_id LEFT JOIN requested q ON q.refund_id = rr.id
LEFT JOIN attempts a ON a.refund_id = rr.id
WHERE rr.purpose = 'CUSTOMER_REFUND';
GO

-- Object: [VIEW] dbo.vw_RefundWork
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER VIEW dbo.vw_RefundWork
AS
    SELECT attempt_id id, amount, status, provider_reference, created_at
    FROM dbo.vw_RefundAttemptLedger
    WHERE status IN ('PENDING', 'UNKNOWN');
GO

-- Object: [VIEW] dbo.vw_RefundWorkerCandidates
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER VIEW dbo.vw_RefundWorkerCandidates AS
 SELECT l.id attempt_id,l.refund_id,o.amount,l.provider_reference,
 CASE WHEN d.attempt_id IS NULL AND l.status='PENDING' AND l.provider_reference IS NULL THEN 'EXECUTE' ELSE 'QUERY' END work_kind,
 l.created_at
 FROM dbo.tc_refund_transfer_logs l
 JOIN dbo.tc_refund_obligations o ON o.id=l.refund_id AND o.current_attempt_id=l.id
 LEFT JOIN dbo.tc_refund_dispatches d ON d.attempt_id=l.id
 WHERE l.status IN('PENDING','UNKNOWN') AND o.amount>0
 AND (d.lease_until IS NULL OR d.lease_until<=SYSUTCDATETIME())
 AND (d.attempt_id IS NULL OR d.next_query_at<=SYSUTCDATETIME());
GO

-- Object: [VIEW] dbo.vw_SettlementPayoutBalance
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER VIEW dbo.vw_SettlementPayoutBalance AS
SELECT s.id settlement_id,s.event_id,e.organization_id,s.status,s.gross_revenue,s.total_refund,s.total_commission,
 s.net_payable,s.paid_amount,s.pending_amount,CAST(s.net_payable-s.paid_amount-s.pending_amount AS decimal(19,0)) available_to_pay,s.confirmed_at
FROM dbo.tc_settlements s JOIN dbo.tc_events e ON e.id=s.event_id;
GO

-- Object: [VIEW] dbo.vw_SettlementPayouts
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER VIEW dbo.vw_SettlementPayouts AS
 SELECT l.id payout_id,l.settlement_id,l.amount,l.status,l.verification_state,l.provider_reference,l.processed_at,
        l.created_at,l.request_fingerprint,s.status settlement_status,s.net_payable,s.paid_amount,s.pending_amount,
        CAST(s.net_payable-s.paid_amount-s.pending_amount AS decimal(19,0)) available_to_pay
 FROM dbo.tc_settlement_transfer_logs l JOIN dbo.tc_settlements s ON s.id=l.settlement_id;
GO

-- Object: [VIEW] dbo.vw_SettlementSummary
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER VIEW dbo.vw_SettlementSummary AS
 SELECT b.settlement_id,b.event_id,b.organization_id,b.status,b.gross_revenue,b.total_refund,b.total_commission,
        b.net_payable,b.paid_amount,b.pending_amount,b.available_to_pay,b.confirmed_at,e.end_time
 FROM dbo.vw_SettlementPayoutBalance b JOIN dbo.tc_events e ON e.id=b.event_id;
GO

-- Object: [VIEW] dbo.vw_TicketDetails
-- Latest Source Migration: 005_create_views.sql
/* View: vw_TicketDetails */
CREATE OR ALTER VIEW dbo.vw_TicketDetails
AS
SELECT t.id ticket_id, o.user_id owner_id, o.id order_id, o.event_id, e.organization_id,
       e.title event_title, e.venue_name, e.venue_address, e.start_time, e.end_time,
       oi.zone_name_snapshot, oi.seat_label_snapshot, t.paid_amount, t.status, t.issued_at
FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
JOIN dbo.tc_orders o ON o.id = oi.order_id JOIN dbo.tc_events e ON e.id = o.event_id;
GO

-- Object: [VIEW] dbo.vw_TicketRuntimeDetails
-- Latest Source Migration: 040_payment_ticket_runtime_reads.sql
CREATE OR ALTER VIEW dbo.vw_TicketRuntimeDetails AS
 SELECT t.id,t.ticket_code,t.paid_amount,t.status,t.issued_at,t.version,
        i.order_id,i.zone_name_snapshot,i.seat_label_snapshot,
        o.order_code,o.user_id buyer_id,e.id event_id,e.title event_title,
        e.venue_name,e.venue_address,e.start_time,e.end_time
 FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id
 JOIN dbo.tc_orders o ON o.id=i.order_id JOIN dbo.tc_events e ON e.id=o.event_id;
GO

-- Object: [VIEW] dbo.vw_VnpayBuyerPaymentProvenance
-- Latest Source Migration: 063_vnpay_sandbox_refund_provenance.sql
CREATE OR ALTER VIEW dbo.vw_VnpayBuyerPaymentProvenance AS
 SELECT p.id payment_id,o.user_id,v.tmn_code,v.transaction_date
 FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id
 JOIN dbo.tc_vnpay_payment_provenance v ON v.payment_id=p.id
 WHERE p.status IN('PENDING','UNKNOWN') AND v.environment='SANDBOX';
GO

-- Object: [VIEW] dbo.vw_VnpayRefundContext
-- Latest Source Migration: 063_vnpay_sandbox_refund_provenance.sql
CREATE OR ALTER VIEW dbo.vw_VnpayRefundContext AS
 SELECT a.refund_id,a.attempt_id,a.amount refund_amount,p.amount payment_amount,p.currency,p.status payment_status,
 p.txn_ref,p.provider_reference payment_reference,v.tmn_code,v.environment,v.transaction_date,a.created_at attempt_created_at
 FROM dbo.vw_RefundAttemptLedger a JOIN dbo.tc_payments p ON p.id=a.payment_id
 JOIN dbo.tc_vnpay_payment_provenance v ON v.payment_id=p.id;
GO

-- Object: [VIEW] dbo.vw_VnpaySandboxRefundCandidates
-- Latest Source Migration: 063_vnpay_sandbox_refund_provenance.sql
-- Filter BEFORE sp_refund_begin_attempt creates a durable dispatch. Never claim legacy/demo money.
CREATE OR ALTER VIEW dbo.vw_VnpaySandboxRefundCandidates AS
 SELECT q.refund_id,q.attempt_id,q.work_kind,q.created_at,c.tmn_code
 FROM dbo.vw_RefundWorkerCandidates q JOIN dbo.vw_VnpayRefundContext c ON c.attempt_id=q.attempt_id
 WHERE c.environment='SANDBOX' AND c.payment_status='CAPTURED' AND c.currency='VND'
 AND c.refund_amount>0 AND c.refund_amount<=c.payment_amount AND c.refund_amount<=9999999999
 AND LEN(c.tmn_code)=8 AND c.tmn_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Za-z0-9]%'
 AND LEN(c.txn_ref) BETWEEN 1 AND 100 AND c.txn_ref COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Za-z0-9-]%'
 AND LEN(c.payment_reference) BETWEEN 1 AND 15 AND c.payment_reference COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9]%'
 AND LEN(c.transaction_date)=14 AND c.transaction_date COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9]%'
 AND TRY_CONVERT(datetime2(0),CONCAT(SUBSTRING(c.transaction_date,1,4),'-',SUBSTRING(c.transaction_date,5,2),'-',
  SUBSTRING(c.transaction_date,7,2),'T',SUBSTRING(c.transaction_date,9,2),':',SUBSTRING(c.transaction_date,11,2),':',
  SUBSTRING(c.transaction_date,13,2)),126) IS NOT NULL;
GO

-- Object: [VIEW] dbo.vw_VuongAuditLogs
-- Latest Source Migration: 027_team_schema_adapter.sql
DECLARE @buyer sysname=CASE WHEN COL_LENGTH('dbo.tc_orders','buyer_id') IS NOT NULL THEN 'buyer_id' ELSE 'user_id' END;
DECLARE @projection nvarchar(max)=N'CREATE OR ALTER VIEW dbo.vw_VuongOrders AS SELECT id,'+QUOTENAME(@buyer)+N' buyer_id,event_id,status,accepted_payment_id,paid_at,subtotal_amount,discount_amount,total_amount FROM dbo.tc_orders;';
EXEC(@projection);
DECLARE @captured sysname=CASE WHEN COL_LENGTH('dbo.tc_payments','paid_at') IS NOT NULL THEN 'paid_at' ELSE 'captured_at' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongPayments AS SELECT id,order_id,status,amount,'+QUOTENAME(@captured)+N' paid_at FROM dbo.tc_payments;';
EXEC(@projection);
DECLARE @refunds sysname=CASE WHEN COL_LENGTH('dbo.tc_refunds','order_id') IS NOT NULL THEN 'tc_refunds' ELSE 'tc_refund_obligations' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS SELECT id,order_id,amount,purpose,status,current_attempt_id,processed_at FROM dbo.'+QUOTENAME(@refunds)+N';';
IF @refunds='tc_refund_obligations'
 /* The owner's compensation mirror creates one obligation per legacy attempt.
    Hide only a failed mirror after the SAME capture is fully compensated by
    timestamped, verified successes. Partial/unknown/manual debts stay visible;
    no history, status or transfer evidence is rewritten. */
 SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS
 SELECT r.id,r.order_id,r.amount,r.purpose,r.status,r.current_attempt_id,r.processed_at
 FROM dbo.tc_refund_obligations r WHERE NOT (
 r.purpose=''PAYMENT_COMPENSATION'' AND r.status=''RETRYABLE'' AND EXISTS (
 SELECT 1 FROM dbo.tc_refunds failed JOIN dbo.tc_payments p ON p.id=failed.payment_id
 WHERE failed.id=r.id AND failed.refund_request_id IS NULL AND failed.status=''FAILED''
 AND failed.purpose=''PAYMENT_COMPENSATION'' AND failed.payment_id=r.payment_id
 AND p.order_id=r.order_id AND p.status=''CAPTURED'' AND r.amount<=p.amount
 AND p.amount>0 AND p.amount<=(SELECT COALESCE(SUM(done.amount),0)
 FROM dbo.tc_refund_obligations done WHERE done.payment_id=p.id AND done.order_id=r.order_id
 AND done.purpose=''PAYMENT_COMPENSATION'' AND done.status=''COMPLETED'' AND done.processed_at IS NOT NULL
 AND EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=done.id
 AND l.id=done.current_attempt_id AND l.result=''SUCCEEDED'' AND l.processed_at IS NOT NULL))));';
EXEC(@projection);
IF COL_LENGTH('dbo.tc_audit_logs','entity_type') IS NOT NULL
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT id,actor_id,action,entity_type,entity_id,metadata_json,created_at,updated_at FROM dbo.tc_audit_logs;');
ELSE
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_audit_logs') AND name='id' AND TYPE_NAME(user_type_id)='bigint' AND is_identity=1)
  THROW 52700,'Legacy audit requires an identity BIGINT key',1;
 /* The zero-padded 128-bit legacy key is an injective UUID projection. It is
    stable across reads/restarts without rekeying or updating historical rows. */
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT CONVERT(uniqueidentifier,CONVERT(binary(16),id)) id,actor_id,action,aggregate_type entity_type,CONVERT(varchar(100),aggregate_id) entity_id,detail metadata_json,created_at,created_at updated_at FROM dbo.tc_audit_logs;');
 EXEC(N'CREATE OR ALTER TRIGGER dbo.trg_VuongLegacyAuditInsert ON dbo.vw_VuongAuditLogs INSTEAD OF INSERT AS
 BEGIN
  SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted WHERE TRY_CONVERT(uniqueidentifier,entity_id) IS NULL) THROW 52702,''Invalid finance audit entity identifier'',1;
  /* Internal NEWID supplied by finance procedures is not a public audit ID.
     The legacy store allocates its own identity; reads return its stable UUID. */
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail,created_at)
  SELECT actor_id,action,entity_type,CONVERT(uniqueidentifier,entity_id),metadata_json,created_at FROM inserted;
 END;');
END;
GO

-- Object: [VIEW] dbo.vw_VuongEventCheckinCounts
-- Latest Source Migration: 028_vuong_report_checkins.sql
/* Apply after 027_team_schema_adapter.sql and 027_vuong_finance.sql.
   Read-only compatibility projection; no owner table, history or status changes. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
IF OBJECT_ID('dbo.vw_VuongOrders','V') IS NULL OR OBJECT_ID('dbo.vw_EventSalesReport','V') IS NULL
 THROW 52800,'Finance report prerequisites must be installed first',1;
IF OBJECT_ID('dbo.tc_ticket_check_in_logs','U') IS NOT NULL
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongEventCheckinCounts AS
 SELECT o.event_id,COUNT_BIG(DISTINCT t.id) tickets_checked_in_count
 FROM dbo.tc_ticket_check_in_logs l JOIN dbo.tc_tickets t ON t.id=l.ticket_id
 JOIN dbo.tc_order_items i ON i.id=t.order_item_id JOIN dbo.vw_VuongOrders o ON o.id=i.order_id
 WHERE l.result=''SUCCESS'' AND l.event_id=o.event_id GROUP BY o.event_id;');
ELSE IF COL_LENGTH('dbo.tc_tickets','used_at') IS NOT NULL
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongEventCheckinCounts AS
 SELECT o.event_id,COUNT_BIG(*) tickets_checked_in_count
 FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id
 JOIN dbo.vw_VuongOrders o ON o.id=i.order_id
 WHERE t.used_at IS NOT NULL GROUP BY o.event_id;');
ELSE THROW 52800,'Supported successful check-in evidence is required',1;
GO

-- Object: [VIEW] dbo.vw_VuongOrders
-- Latest Source Migration: 027_team_schema_adapter.sql
DECLARE @buyer sysname=CASE WHEN COL_LENGTH('dbo.tc_orders','buyer_id') IS NOT NULL THEN 'buyer_id' ELSE 'user_id' END;
DECLARE @projection nvarchar(max)=N'CREATE OR ALTER VIEW dbo.vw_VuongOrders AS SELECT id,'+QUOTENAME(@buyer)+N' buyer_id,event_id,status,accepted_payment_id,paid_at,subtotal_amount,discount_amount,total_amount FROM dbo.tc_orders;';
EXEC(@projection);
DECLARE @captured sysname=CASE WHEN COL_LENGTH('dbo.tc_payments','paid_at') IS NOT NULL THEN 'paid_at' ELSE 'captured_at' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongPayments AS SELECT id,order_id,status,amount,'+QUOTENAME(@captured)+N' paid_at FROM dbo.tc_payments;';
EXEC(@projection);
DECLARE @refunds sysname=CASE WHEN COL_LENGTH('dbo.tc_refunds','order_id') IS NOT NULL THEN 'tc_refunds' ELSE 'tc_refund_obligations' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS SELECT id,order_id,amount,purpose,status,current_attempt_id,processed_at FROM dbo.'+QUOTENAME(@refunds)+N';';
IF @refunds='tc_refund_obligations'
 /* The owner's compensation mirror creates one obligation per legacy attempt.
    Hide only a failed mirror after the SAME capture is fully compensated by
    timestamped, verified successes. Partial/unknown/manual debts stay visible;
    no history, status or transfer evidence is rewritten. */
 SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS
 SELECT r.id,r.order_id,r.amount,r.purpose,r.status,r.current_attempt_id,r.processed_at
 FROM dbo.tc_refund_obligations r WHERE NOT (
 r.purpose=''PAYMENT_COMPENSATION'' AND r.status=''RETRYABLE'' AND EXISTS (
 SELECT 1 FROM dbo.tc_refunds failed JOIN dbo.tc_payments p ON p.id=failed.payment_id
 WHERE failed.id=r.id AND failed.refund_request_id IS NULL AND failed.status=''FAILED''
 AND failed.purpose=''PAYMENT_COMPENSATION'' AND failed.payment_id=r.payment_id
 AND p.order_id=r.order_id AND p.status=''CAPTURED'' AND r.amount<=p.amount
 AND p.amount>0 AND p.amount<=(SELECT COALESCE(SUM(done.amount),0)
 FROM dbo.tc_refund_obligations done WHERE done.payment_id=p.id AND done.order_id=r.order_id
 AND done.purpose=''PAYMENT_COMPENSATION'' AND done.status=''COMPLETED'' AND done.processed_at IS NOT NULL
 AND EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=done.id
 AND l.id=done.current_attempt_id AND l.result=''SUCCEEDED'' AND l.processed_at IS NOT NULL))));';
EXEC(@projection);
IF COL_LENGTH('dbo.tc_audit_logs','entity_type') IS NOT NULL
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT id,actor_id,action,entity_type,entity_id,metadata_json,created_at,updated_at FROM dbo.tc_audit_logs;');
ELSE
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_audit_logs') AND name='id' AND TYPE_NAME(user_type_id)='bigint' AND is_identity=1)
  THROW 52700,'Legacy audit requires an identity BIGINT key',1;
 /* The zero-padded 128-bit legacy key is an injective UUID projection. It is
    stable across reads/restarts without rekeying or updating historical rows. */
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT CONVERT(uniqueidentifier,CONVERT(binary(16),id)) id,actor_id,action,aggregate_type entity_type,CONVERT(varchar(100),aggregate_id) entity_id,detail metadata_json,created_at,created_at updated_at FROM dbo.tc_audit_logs;');
 EXEC(N'CREATE OR ALTER TRIGGER dbo.trg_VuongLegacyAuditInsert ON dbo.vw_VuongAuditLogs INSTEAD OF INSERT AS
 BEGIN
  SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted WHERE TRY_CONVERT(uniqueidentifier,entity_id) IS NULL) THROW 52702,''Invalid finance audit entity identifier'',1;
  /* Internal NEWID supplied by finance procedures is not a public audit ID.
     The legacy store allocates its own identity; reads return its stable UUID. */
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail,created_at)
  SELECT actor_id,action,entity_type,CONVERT(uniqueidentifier,entity_id),metadata_json,created_at FROM inserted;
 END;');
END;
GO

-- Object: [VIEW] dbo.vw_VuongPayments
-- Latest Source Migration: 027_team_schema_adapter.sql
DECLARE @buyer sysname=CASE WHEN COL_LENGTH('dbo.tc_orders','buyer_id') IS NOT NULL THEN 'buyer_id' ELSE 'user_id' END;
DECLARE @projection nvarchar(max)=N'CREATE OR ALTER VIEW dbo.vw_VuongOrders AS SELECT id,'+QUOTENAME(@buyer)+N' buyer_id,event_id,status,accepted_payment_id,paid_at,subtotal_amount,discount_amount,total_amount FROM dbo.tc_orders;';
EXEC(@projection);
DECLARE @captured sysname=CASE WHEN COL_LENGTH('dbo.tc_payments','paid_at') IS NOT NULL THEN 'paid_at' ELSE 'captured_at' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongPayments AS SELECT id,order_id,status,amount,'+QUOTENAME(@captured)+N' paid_at FROM dbo.tc_payments;';
EXEC(@projection);
DECLARE @refunds sysname=CASE WHEN COL_LENGTH('dbo.tc_refunds','order_id') IS NOT NULL THEN 'tc_refunds' ELSE 'tc_refund_obligations' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS SELECT id,order_id,amount,purpose,status,current_attempt_id,processed_at FROM dbo.'+QUOTENAME(@refunds)+N';';
IF @refunds='tc_refund_obligations'
 /* The owner's compensation mirror creates one obligation per legacy attempt.
    Hide only a failed mirror after the SAME capture is fully compensated by
    timestamped, verified successes. Partial/unknown/manual debts stay visible;
    no history, status or transfer evidence is rewritten. */
 SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS
 SELECT r.id,r.order_id,r.amount,r.purpose,r.status,r.current_attempt_id,r.processed_at
 FROM dbo.tc_refund_obligations r WHERE NOT (
 r.purpose=''PAYMENT_COMPENSATION'' AND r.status=''RETRYABLE'' AND EXISTS (
 SELECT 1 FROM dbo.tc_refunds failed JOIN dbo.tc_payments p ON p.id=failed.payment_id
 WHERE failed.id=r.id AND failed.refund_request_id IS NULL AND failed.status=''FAILED''
 AND failed.purpose=''PAYMENT_COMPENSATION'' AND failed.payment_id=r.payment_id
 AND p.order_id=r.order_id AND p.status=''CAPTURED'' AND r.amount<=p.amount
 AND p.amount>0 AND p.amount<=(SELECT COALESCE(SUM(done.amount),0)
 FROM dbo.tc_refund_obligations done WHERE done.payment_id=p.id AND done.order_id=r.order_id
 AND done.purpose=''PAYMENT_COMPENSATION'' AND done.status=''COMPLETED'' AND done.processed_at IS NOT NULL
 AND EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=done.id
 AND l.id=done.current_attempt_id AND l.result=''SUCCEEDED'' AND l.processed_at IS NOT NULL))));';
EXEC(@projection);
IF COL_LENGTH('dbo.tc_audit_logs','entity_type') IS NOT NULL
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT id,actor_id,action,entity_type,entity_id,metadata_json,created_at,updated_at FROM dbo.tc_audit_logs;');
ELSE
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_audit_logs') AND name='id' AND TYPE_NAME(user_type_id)='bigint' AND is_identity=1)
  THROW 52700,'Legacy audit requires an identity BIGINT key',1;
 /* The zero-padded 128-bit legacy key is an injective UUID projection. It is
    stable across reads/restarts without rekeying or updating historical rows. */
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT CONVERT(uniqueidentifier,CONVERT(binary(16),id)) id,actor_id,action,aggregate_type entity_type,CONVERT(varchar(100),aggregate_id) entity_id,detail metadata_json,created_at,created_at updated_at FROM dbo.tc_audit_logs;');
 EXEC(N'CREATE OR ALTER TRIGGER dbo.trg_VuongLegacyAuditInsert ON dbo.vw_VuongAuditLogs INSTEAD OF INSERT AS
 BEGIN
  SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted WHERE TRY_CONVERT(uniqueidentifier,entity_id) IS NULL) THROW 52702,''Invalid finance audit entity identifier'',1;
  /* Internal NEWID supplied by finance procedures is not a public audit ID.
     The legacy store allocates its own identity; reads return its stable UUID. */
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail,created_at)
  SELECT actor_id,action,entity_type,CONVERT(uniqueidentifier,entity_id),metadata_json,created_at FROM inserted;
 END;');
END;
GO

-- Object: [VIEW] dbo.vw_VuongRefunds
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS
 SELECT r.id,r.order_id,r.amount,r.purpose,r.status,r.current_attempt_id,r.processed_at
 FROM dbo.tc_refund_obligations r WHERE NOT (
 r.purpose='PAYMENT_COMPENSATION' AND r.status='RETRYABLE' AND EXISTS (
 SELECT 1 FROM dbo.tc_refund_transfer_logs failed JOIN dbo.tc_payments p ON p.id=r.payment_id
 WHERE failed.id=r.id AND failed.refund_id=r.id AND failed.status='FAILED'
 AND p.order_id=r.order_id AND p.status='CAPTURED' AND r.amount<=p.amount
 AND p.amount>0 AND p.amount<=(SELECT COALESCE(SUM(done.amount),0)
 FROM dbo.tc_refund_obligations done WHERE done.payment_id=p.id AND done.order_id=r.order_id
 AND done.purpose='PAYMENT_COMPENSATION' AND done.status='COMPLETED' AND done.processed_at IS NOT NULL
 AND EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=done.id
 AND l.id=done.current_attempt_id AND l.result='SUCCEEDED' AND l.processed_at IS NOT NULL))));
GO

-- Object: [VIEW] dbo.vw_ZoneInventory
-- Latest Source Migration: 005_create_views.sql
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

/*
========================================================================================
SECTION: PROCEDUREs (Total: 93)
========================================================================================
*/

-- Object: [PROCEDURE] dbo.sp_admin_refund_decide
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.sp_admin_refund_decide
 @refund_id uniqueidentifier,@admin_user_id uniqueidentifier,@expected_auth_version int,
 @decision varchar(20),@admin_note nvarchar(500)=NULL,@reject_reason nvarchar(1000)=NULL,
 @idempotency_key uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @owns bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @owns=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION admin_refund_decision;
 BEGIN TRY
  IF COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 AND USER_NAME()<>N'dbo'
    THROW 54103,'ADMIN_PRINCIPAL_REQUIRED',1;
  IF @refund_id IS NULL OR @idempotency_key IS NULL OR @decision IS NULL OR @decision NOT IN('APPROVE','REJECT','RETRY')
    THROW 54100,'INVALID_DECISION',1;
  SET @admin_note=NULLIF(TRIM(@admin_note),N''); SET @reject_reason=NULLIF(TRIM(@reject_reason),N'');
  IF (@decision='REJECT' AND @reject_reason IS NULL) OR (@decision<>'REJECT' AND @reject_reason IS NOT NULL)
    THROW 54100,'REJECTION_REASON_REQUIRED',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version)
    THROW 54101,'SESSION_EXPIRED',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND platform_role='ADMIN')
    THROW 54103,'ADMIN_REQUIRED',1;
  -- Serialize durable keys for this actor; business locks follow Event -> User -> Order.
  DECLARE @lock int,@resource nvarchar(255)=CONCAT('admin-refund:',CONVERT(varchar(36),@admin_user_id));
  EXEC @lock=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
  IF @lock<0 THROW 54109,'DECISION_BUSY',1;
  DECLARE @input nvarchar(max),@fingerprint varchar(200),@old varchar(200),@payload nvarchar(max),
   @request uniqueidentifier,@order uniqueidentifier,@event uniqueidentifier,@payment uniqueidentifier,
   @state varchar(40),@request_state varchar(20),@attempt uniqueidentifier,@attempt_state varchar(30),
   @unused bigint,@buyer uniqueidentifier,@tickets varchar(400),@now datetime2(3);
  SET @input=(SELECT CONVERT(varchar(36),@refund_id) refundId,@decision decision,@admin_note note,@reject_reason rejectReason FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
  SET @fingerprint=CONVERT(varchar(64),HASHBYTES('SHA2_256',@input),2);
  SELECT @old=fingerprint,@payload=response_payload FROM dbo.tc_request_idempotency WITH(UPDLOCK,HOLDLOCK)
   WHERE actor_id=@admin_user_id AND operation='ADMIN_REFUND_DECISION' AND idempotency_key=CONVERT(varchar(36),@idempotency_key);
  IF @old IS NULL BEGIN
   SELECT @order=order_id,@request=id FROM dbo.tc_refund_obligations WHERE id=@refund_id AND purpose='CUSTOMER_REFUND';
   SELECT @event=event_id FROM dbo.tc_orders WHERE id=@order;
   IF @event IS NULL OR @request IS NULL THROW 54104,'REFUND_NOT_FOUND',1;
   SELECT @unused=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@admin_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version)
     THROW 54101,'SESSION_EXPIRED',1;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND platform_role='ADMIN')
     THROW 54103,'ADMIN_REQUIRED',1;
   SELECT @buyer=user_id,@payment=accepted_payment_id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
   SELECT @unused=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
   SELECT @state=status,@request_state=request_status,@attempt=current_attempt_id FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id;
   SELECT @attempt_state=status FROM dbo.tc_refund_transfer_logs WITH(UPDLOCK,HOLDLOCK) WHERE id=@attempt AND refund_id=@refund_id;
   -- Recheck current authority at the mutation boundary, after lock waits.
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version) THROW 54101,'SESSION_EXPIRED',1;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND platform_role='ADMIN') THROW 54103,'ADMIN_REQUIRED',1;
   IF @decision='RETRY' BEGIN
    IF @state<>'RETRYABLE' OR COALESCE(@attempt_state,'')<>'FAILED' OR @attempt IS NULL
      THROW 54109,'ONLY_VERIFIED_FAILED_ATTEMPT_CAN_RETRY',1;
    IF EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE refund_id=@refund_id AND status IN('PENDING','UNKNOWN','SUCCEEDED'))
      OR EXISTS(SELECT 1 FROM dbo.tc_gateway_refund_receipts WHERE payment_id=@payment AND resolution='NEEDS_RECONCILIATION')
      THROW 54109,'RECONCILIATION_REQUIRED',1;
   END ELSE IF @state<>'REQUESTED' OR @request_state<>'PENDING' OR @attempt IS NOT NULL
    THROW 54109,'REFUND_ALREADY_DECIDED',1;
   DECLARE @result TABLE(request_id uniqueidentifier,request_status varchar(20),refund_id uniqueidentifier);
   DECLARE @legacy_decision varchar(20)=CASE WHEN @decision='RETRY' THEN 'APPROVE' ELSE @decision END,
           @retry bit=CASE WHEN @decision='RETRY' THEN 1 ELSE 0 END;
   INSERT @result EXEC dbo.usp_ReviewRefundRequest @request_id=@request,@actor_id=@admin_user_id,
    @decision=@legacy_decision,@rejection_reason=@reject_reason,@retry_failed=@retry;
   SELECT @tickets=STRING_AGG(CONVERT(varchar(36),ticket_id),',') WITHIN GROUP(ORDER BY ticket_id)
    FROM dbo.tc_refund_tickets WHERE refund_id=@refund_id;
   SET @now=SYSUTCDATETIME();
   INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail)
    VALUES(@admin_user_id,CONCAT('ADMIN_REFUND_',@decision),'Refund',@refund_id,@input);
   SET @payload=(SELECT CONVERT(varchar(36),r.id) refund_id,r.request_status status,r.status canonical_status,
    CONVERT(varchar(36),r.current_attempt_id) attempt_id,CONVERT(varchar(36),@buyer) buyer_id,@tickets ticket_ids
    FROM dbo.tc_refund_obligations r WHERE r.id=@refund_id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
   INSERT dbo.tc_request_idempotency(actor_id,operation,idempotency_key,target_id,fingerprint,response_payload,status,expires_at)
    VALUES(@admin_user_id,'ADMIN_REFUND_DECISION',CONVERT(varchar(36),@idempotency_key),@refund_id,@fingerprint,@payload,'COMPLETED',DATEADD(day,1,@now));
  END ELSE BEGIN
   -- A durable receipt is not an authority grant. Recheck after actor/receipt waits.
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@admin_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version)
    THROW 54101,'SESSION_EXPIRED',1;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND platform_role='ADMIN')
    THROW 54103,'ADMIN_REQUIRED',1;
   IF @old<>@fingerprint THROW 54109,'IDEMPOTENCY_CONFLICT',1;
  END;
  IF @owns=1 COMMIT;
  SELECT * FROM OPENJSON(@payload) WITH(refund_id varchar(36),status varchar(20),canonical_status varchar(40),attempt_id varchar(36),buyer_id varchar(36),ticket_ids varchar(400));
 END TRY BEGIN CATCH
  IF @owns=1 AND XACT_STATE()<>0 ROLLBACK;
  ELSE IF @owns=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION admin_refund_decision;
  THROW;
 END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.sp_checkin_recent_logs
-- Latest Source Migration: 030_khanh02_signed_checkin.sql
CREATE OR ALTER PROCEDURE dbo.sp_checkin_recent_logs
 @event_id uniqueidentifier,@staff_user_id uniqueidentifier,@limit int=20
AS
BEGIN
 SET NOCOUNT ON;
 IF @limit<1 OR @limit>100 THROW 53204,'Invalid log limit',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id) THROW 53202,'Event not found',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_events e JOIN dbo.tc_organization_memberships m ON m.organization_id=e.organization_id
  JOIN dbo.tc_users u ON u.id=m.user_id WHERE e.id=@event_id AND m.user_id=@staff_user_id AND u.status='ACTIVE'
  AND m.active=1 AND m.role IN('MANAGER','CHECK_IN_STAFF')) THROW 53201,'Active event staff membership required',1;
 SELECT TOP(@limit) l.id,l.ticket_id,l.result,l.scanned_at,u.full_name staff_name,
  CASE WHEN o.event_id=l.event_id THEN i.zone_name_snapshot END zone_name,
  CASE WHEN o.event_id=l.event_id THEN i.seat_label_snapshot END seat_label
 FROM dbo.tc_ticket_check_in_logs l LEFT JOIN dbo.tc_users u ON u.id=l.actor_id
 LEFT JOIN dbo.tc_tickets t ON t.id=l.ticket_id LEFT JOIN dbo.tc_order_items i ON i.id=t.order_item_id
 LEFT JOIN dbo.tc_orders o ON o.id=i.order_id
 WHERE l.event_id=@event_id ORDER BY l.scanned_at DESC,l.id;
END;
GO

-- Object: [PROCEDURE] dbo.sp_event_cancel
-- Latest Source Migration: 058_khanh06_event_cancellation.sql
CREATE OR ALTER PROCEDURE dbo.sp_event_cancel
 @event_id uniqueidentifier,@admin_user_id uniqueidentifier,@cancel_reason nvarchar(500),
 @idempotency_key uniqueidentifier=NULL,@auth_version int=NULL,@canonical_replay bit=0,@suppress_result bit=0
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END,
  @old_actor sql_variant=SESSION_CONTEXT(N'actor_id');
 IF @started=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_cancel;
 BEGIN TRY
  IF COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 AND USER_NAME()<>'dbo' THROW 54203,'ADMIN_REQUIRED',1;
  IF @admin_user_id IS NULL OR (@old_actor IS NOT NULL AND TRY_CONVERT(uniqueidentifier,@old_actor)<>@admin_user_id)
   THROW 54203,'ADMIN_REQUIRED',1;
  SET @cancel_reason=LTRIM(RTRIM(@cancel_reason));
  IF @cancel_reason IS NULL OR LEN(@cancel_reason)<10 OR LEN(@cancel_reason)>500 THROW 54200,'CANCELLATION_INVALID',1;
  DECLARE @state varchar(30),@start datetime2(3),@current_version int,@role varchar(20),@user_state varchar(20);
  SELECT @state=status,@start=start_time FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
  IF @state IS NULL THROW 54204,'EVENT_NOT_FOUND',1;
  SELECT @current_version=auth_version,@role=platform_role,@user_state=status
   FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@admin_user_id;
  IF @user_state IS NULL OR @user_state<>'ACTIVE' OR (@auth_version IS NOT NULL AND @current_version<>@auth_version)
   THROW 54201,'SESSION_EXPIRED',1;
  IF @role<>'ADMIN' THROW 54203,'ADMIN_REQUIRED',1;
  -- The actor/key lock spans different Events. Recheck payload before any mutation.
  DECLARE @receipt_event uniqueidentifier,@receipt_reason nvarchar(500),@lock int,
   @resource nvarchar(255)=CONCAT(N'event-cancel:',@admin_user_id,N':',@idempotency_key);
  IF @idempotency_key IS NOT NULL BEGIN
   EXEC @lock=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
   IF @lock<0 THROW 54209,'CANCELLATION_BUSY',1;
   SELECT @receipt_event=event_id,@receipt_reason=reason FROM dbo.tc_cancellation_receipts
    WHERE actor_id=@admin_user_id AND idempotency_key=@idempotency_key;
   IF @receipt_event IS NOT NULL BEGIN
    IF @receipt_event<>@event_id OR @receipt_reason COLLATE Latin1_General_100_BIN2<>@cancel_reason COLLATE Latin1_General_100_BIN2
     THROW 54209,'IDEMPOTENCY_CONFLICT',1;
    IF @started=1 COMMIT TRANSACTION;
    IF @suppress_result=0 SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=@event_id; RETURN;
   END;
  END;
  IF @state='CANCELLED' BEGIN
   IF @canonical_replay=1 AND EXISTS(SELECT 1 FROM dbo.tc_cancellation_jobs WHERE event_id=@event_id) BEGIN
    IF @started=1 COMMIT TRANSACTION;
    IF @suppress_result=0 SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=@event_id; RETURN;
   END;
   THROW 54209,'EVENT_ALREADY_CANCELLED',1;
  END;
  IF @state<>'PUBLISHED' OR SYSUTCDATETIME()>=@start THROW 54200,'EVENT_CANNOT_BE_CANCELLED',1;
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@admin_user_id;
  UPDATE dbo.tc_events SET status='CANCELLED',version=version+1 WHERE id=@event_id;
  INSERT dbo.tc_cancellation_jobs(event_id,actor_id,reason) VALUES(@event_id,@admin_user_id,@cancel_reason);
  INSERT dbo.tc_cancellation_orders(event_id,order_id)
   SELECT @event_id,id FROM dbo.tc_orders WHERE event_id=@event_id AND status='PAID';
  UPDATE dbo.tc_cancellation_jobs SET total_orders=@@ROWCOUNT WHERE event_id=@event_id;
  -- SP07 owns inventory/coupon release. All writes roll back together.
  DECLARE @holds TABLE(id uniqueidentifier PRIMARY KEY); DECLARE @hold uniqueidentifier;
  INSERT @holds SELECT id FROM dbo.tc_ticket_holds WHERE event_id=@event_id AND status='ACTIVE';
  WHILE EXISTS(SELECT 1 FROM @holds) BEGIN
   SELECT TOP(1) @hold=id FROM @holds ORDER BY id;
   EXEC dbo.usp_ReleaseTicketHold @hold,NULL,'EVENT_CANCELLED',1;
   DELETE @holds WHERE id=@hold;
  END;
  INSERT dbo.tc_outbox(event_type,aggregate_type,aggregate_id,payload,idempotency_key)
   VALUES('EVENT_CANCELLED','EVENT',@event_id,
    (SELECT CONVERT(varchar(36),@event_id) eventId,@cancel_reason reason FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),
    CONCAT('event-cancelled:',@event_id));
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail)
   VALUES(@admin_user_id,'EVENT_CANCELLATION_REQUESTED','EVENT',@event_id,
    (SELECT @cancel_reason reason FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
  IF @idempotency_key IS NOT NULL INSERT dbo.tc_cancellation_receipts VALUES(@admin_user_id,@idempotency_key,@event_id,@cancel_reason);
  IF @started=1 COMMIT TRANSACTION;
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor;
  IF @suppress_result=0 SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=@event_id;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK TRANSACTION;
  ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_cancel;
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor; THROW;
 END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.sp_event_cancellation_finish
-- Latest Source Migration: 058_khanh06_event_cancellation.sql
CREATE OR ALTER PROCEDURE dbo.sp_event_cancellation_finish
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 AND USER_NAME()<>'dbo' THROW 54203,'WORKER_REQUIRED',1;
 UPDATE j SET status='COMPLETED',updated_at=SYSUTCDATETIME()
 FROM dbo.tc_cancellation_jobs j WHERE j.status<>'COMPLETED'
  AND j.processed_orders=j.total_orders
  AND NOT EXISTS(SELECT 1 FROM dbo.tc_cancellation_orders q WHERE q.event_id=j.event_id AND q.processed_at IS NULL);
END;
GO

-- Object: [PROCEDURE] dbo.sp_event_cancellation_queue
-- Latest Source Migration: 061_event_cancellation_review.sql
CREATE OR ALTER PROCEDURE dbo.sp_event_cancellation_queue
 @actor_id uniqueidentifier,@auth_version int,@page int=1,@page_size int=20
AS
BEGIN
 SET NOCOUNT ON;
 IF @page<1 OR @page_size<1 OR @page_size>100 OR @page>50000 THROW 54300,'INVALID_PAGE',1;
 IF COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 AND USER_NAME()<>'dbo' THROW 54303,'ADMIN_PRINCIPAL_REQUIRED',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@actor_id AND status='ACTIVE' AND auth_version=@auth_version) THROW 54301,'SESSION_EXPIRED',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@actor_id AND platform_role='ADMIN') THROW 54303,'ADMIN_REQUIRED',1;
 DECLARE @json nvarchar(max)=(SELECT
  (SELECT COUNT_BIG(*) FROM dbo.tc_event_cancellation_requests) total,@page page,@page_size pageSize,
  JSON_QUERY((SELECT CONVERT(varchar(36),e.id) eventId,e.title,r.status requestStatus,r.reason,r.requested_at requestedAt
   FROM dbo.tc_event_cancellation_requests r JOIN dbo.tc_events e ON e.id=r.event_id
   ORDER BY CASE WHEN r.status='PENDING' THEN 0 ELSE 1 END,r.requested_at DESC,e.id
   OFFSET (@page-1)*@page_size ROWS FETCH NEXT @page_size ROWS ONLY FOR JSON PATH)) items
  FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
 SELECT @json response_json;
END;
GO

-- Object: [PROCEDURE] dbo.sp_event_cancellation_review
-- Latest Source Migration: 061_event_cancellation_review.sql
CREATE OR ALTER PROCEDURE dbo.sp_event_cancellation_review
 @event_id uniqueidentifier,@actor_id uniqueidentifier,@auth_version int,
 @action varchar(20),@reason nvarchar(500)=NULL,@idempotency_key uniqueidentifier=NULL
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @owns bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @owns=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION cancellation_review;
 BEGIN TRY
  IF @action NOT IN('READ','REQUEST','APPROVE','REJECT') THROW 54300,'INVALID_ACTION',1;
  DECLARE @context_actor uniqueidentifier=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id'));
  IF @actor_id IS NULL OR (@context_actor IS NOT NULL AND @context_actor<>@actor_id) THROW 54303,'ACTOR_MISMATCH',1;
  DECLARE @org uniqueidentifier,@state varchar(30),@start datetime2(3),@admin bit=0,
   @request_state varchar(20),@request_reason nvarchar(500),@response nvarchar(max),
   @old varchar(64),@hash varchar(64),@lock int;
  -- Event -> actor -> request is also the cancellation procedure's lock order.
  SELECT @org=organization_id,@state=status,@start=start_time FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
  IF @org IS NULL THROW 54304,'EVENT_NOT_FOUND',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK)
    WHERE id=@actor_id AND status='ACTIVE' AND auth_version=@auth_version) THROW 54301,'SESSION_EXPIRED',1;
  IF EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@actor_id AND platform_role='ADMIN') SET @admin=1;
  IF @admin=1 BEGIN
   IF COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 AND USER_NAME()<>'dbo'
    THROW 54303,'ADMIN_PRINCIPAL_REQUIRED',1;
  END ELSE BEGIN
   IF @action IN('APPROVE','REJECT') THROW 54303,'ADMIN_REQUIRED',1;
   IF COALESCE(IS_ROLEMEMBER('tc_manager'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 AND USER_NAME()<>'dbo'
    THROW 54303,'MANAGER_PRINCIPAL_REQUIRED',1;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_organization_memberships m WITH(UPDLOCK,HOLDLOCK)
     JOIN dbo.tc_organizations o ON o.id=m.organization_id
     WHERE m.user_id=@actor_id AND m.organization_id=@org AND m.active=1 AND m.role='MANAGER' AND o.status='APPROVED')
    THROW 54303,'MANAGER_REQUIRED',1;
  END;
  SELECT @request_state=status,@request_reason=reason FROM dbo.tc_event_cancellation_requests WITH(UPDLOCK,HOLDLOCK) WHERE event_id=@event_id;
  SET @reason=NULLIF(LTRIM(RTRIM(@reason)),N'');
  IF @action<>'READ' BEGIN
   IF @idempotency_key IS NULL THROW 54300,'IDEMPOTENCY_KEY_REQUIRED',1;
   IF @action IN('REQUEST','REJECT') AND (@reason IS NULL OR LEN(@reason)<10 OR LEN(@reason)>500) THROW 54300,'REASON_REQUIRED',1;
   IF @action='APPROVE' AND @reason IS NOT NULL THROW 54300,'APPROVAL_REASON_UNEXPECTED',1;
   DECLARE @resource nvarchar(255)=CONCAT('cancel-review:',@actor_id,':',@idempotency_key);
   EXEC @lock=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
   IF @lock<0 THROW 54309,'REQUEST_BUSY',1;
   DECLARE @input nvarchar(max)=(SELECT CONVERT(varchar(36),@event_id) eventId,@action action,@reason reason FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
   SET @hash=CONVERT(varchar(64),HASHBYTES('SHA2_256',@input),2);
   SELECT @old=fingerprint,@response=response_json FROM dbo.tc_event_cancellation_review_receipts WHERE actor_id=@actor_id AND idempotency_key=@idempotency_key;
   IF @old IS NOT NULL BEGIN
    IF @old<>@hash THROW 54309,'IDEMPOTENCY_CONFLICT',1;
    IF @owns=1 COMMIT;
    SELECT @response response_json; RETURN;
   END;
   IF @action='REQUEST' BEGIN
    IF @state<>'PUBLISHED' OR SYSUTCDATETIME()>=@start THROW 54309,'EVENT_CANNOT_BE_CANCELLED',1;
    IF @request_state IN('PENDING','APPROVED') THROW 54309,'CANCELLATION_ALREADY_REQUESTED',1;
    IF @request_state IS NULL INSERT dbo.tc_event_cancellation_requests(event_id,requested_by,reason,status) VALUES(@event_id,@actor_id,@reason,'PENDING');
    ELSE UPDATE dbo.tc_event_cancellation_requests SET requested_by=@actor_id,reason=@reason,status='PENDING',
     requested_at=SYSUTCDATETIME(),reviewer_id=NULL,decided_at=NULL,rejection_reason=NULL WHERE event_id=@event_id;
   END ELSE BEGIN
    IF COALESCE(@request_state,'')<>'PENDING' THROW 54309,'NO_PENDING_REQUEST',1;
    IF @action='APPROVE' BEGIN
     EXEC dbo.sp_event_cancel @event_id=@event_id,@admin_user_id=@actor_id,@cancel_reason=@request_reason,
      @idempotency_key=@idempotency_key,@auth_version=@auth_version,@suppress_result=1;
    END;
    UPDATE dbo.tc_event_cancellation_requests SET status=CASE WHEN @action='APPROVE' THEN 'APPROVED' ELSE 'REJECTED' END,
     reviewer_id=@actor_id,decided_at=SYSUTCDATETIME(),rejection_reason=CASE WHEN @action='REJECT' THEN @reason ELSE NULL END WHERE event_id=@event_id;
   END;
   INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail)
    VALUES(@actor_id,CONCAT('EVENT_CANCELLATION_',@action),'EVENT',@event_id,@input);
  END;
  SET @response=(SELECT CONVERT(varchar(36),e.id) eventId,e.title,e.status eventStatus,
   COALESCE(r.status,'NONE') requestStatus,r.reason,r.requested_at requestedAt,r.decided_at decidedAt,r.rejection_reason rejectionReason,
   JSON_QUERY((SELECT p.status,p.total_orders totalOrders,p.processed_orders processedOrders,
    p.successful_refunds successfulRefunds,p.failed_refunds failedRefunds,p.unknown_refunds unknownRefunds,
    p.pending_refunds pendingRefunds,p.used_ticket_exceptions usedTicketExceptions,p.updated_at updatedAt
    FROM dbo.v_admin_cancellation_progress p WHERE p.event_id=e.id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)) progress
   FROM dbo.tc_events e LEFT JOIN dbo.tc_event_cancellation_requests r ON r.event_id=e.id
   WHERE e.id=@event_id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
  IF @action<>'READ' INSERT dbo.tc_event_cancellation_review_receipts VALUES(@actor_id,@idempotency_key,@hash,@response);
  IF @owns=1 COMMIT;
  SELECT @response response_json;
 END TRY BEGIN CATCH
  IF @owns=1 AND XACT_STATE()<>0 ROLLBACK;
  ELSE IF @owns=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION cancellation_review;
  THROW;
 END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.sp_organization_approve
-- Latest Source Migration: 054_organization_decision_procedures.sql
CREATE OR ALTER PROCEDURE dbo.sp_organization_approve
    @organization_id uniqueidentifier,
    @admin_user_id uniqueidentifier,
    @rate_percent decimal(7,4)=NULL,
    @fixed_fee decimal(19,0)=NULL,
    @effective_from datetime2(3)=NULL,
    @effective_to datetime2(3)=NULL,
    @event_title nvarchar(250)=NULL,
    @event_venue nvarchar(250)=NULL,
    @event_start datetime2(3)=NULL,
    @event_end datetime2(3)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @admin_user_id AND platform_role = 'ADMIN')
       AND NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @admin_user_id AND role = 'ADMIN')
        THROW 52610, 'Administrator role required', 1;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION sp_organization_approve_sp;

    BEGIN TRY
        DECLARE @status varchar(30), @requester_id uniqueidentifier, @description nvarchar(2000);
        DECLARE @rule_id uniqueidentifier, @event_id uniqueidentifier, @category_id uniqueidentifier;

        SELECT @status = status, @requester_id = requester_id, @description = description
        FROM dbo.tc_organizations WITH (UPDLOCK, ROWLOCK)
        WHERE id = @organization_id;

        IF @status IS NULL
            THROW 52611, 'Organization not found', 1;
        IF @status <> 'PENDING_APPROVAL'
            THROW 52612, 'ORGANIZATION_ALREADY_PROCESSED', 1;

        IF NOT (@rate_percent IS NULL AND @fixed_fee IS NULL AND @effective_from IS NULL AND @effective_to IS NULL)
        BEGIN
            IF @rate_percent IS NULL OR @rate_percent < 0 OR @rate_percent > 100
               OR @fixed_fee IS NULL OR @fixed_fee < 0
               OR @effective_from IS NULL OR @effective_to IS NULL OR @effective_from >= @effective_to
                THROW 52613, 'Invalid initial commission policy', 1;
            SET @rule_id = NEWID();
            INSERT INTO dbo.tc_commission_rules (id, organization_id, rate_percent, fixed_fee, effective_from, effective_to)
            VALUES (@rule_id, @organization_id, @rate_percent, @fixed_fee, @effective_from, @effective_to);
        END;

        IF @requester_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships
            WHERE user_id = @requester_id AND organization_id = @organization_id)
            INSERT INTO dbo.tc_organization_memberships (user_id, organization_id, role)
            VALUES (@requester_id, @organization_id, 'MANAGER');

        IF NULLIF(LTRIM(RTRIM(@event_title)), N'') IS NOT NULL
        BEGIN
            IF @event_start IS NULL OR @event_end IS NULL OR @event_start >= @event_end
               OR NULLIF(LTRIM(RTRIM(@event_venue)), N'') IS NULL
                THROW 52615, 'Invalid initial event', 1;
            SELECT TOP (1) @category_id = id FROM dbo.tc_event_categories WITH (UPDLOCK, HOLDLOCK)
            ORDER BY CASE WHEN slug = 'concert' THEN 0 ELSE 1 END, name, id;
            IF @category_id IS NULL
            BEGIN
                SET @category_id = NEWID();
                INSERT INTO dbo.tc_event_categories (id, name, slug) VALUES (@category_id, N'Hòa nhạc', 'concert');
            END;
            SET @event_id = NEWID();
            INSERT INTO dbo.tc_events (id, organization_id, category_id, commission_rule_id, title, description,
                                       venue_name, venue_address, sale_start, sale_end, start_time, end_time)
            VALUES (@event_id, @organization_id, @category_id, @rule_id, @event_title,
                    COALESCE(@description, N'Sự kiện được phê duyệt'), @event_venue, @event_venue,
                    DATEADD(day, -7, @event_start), @event_start, @event_start, @event_end);
        END;

        UPDATE dbo.tc_organizations
        SET status = 'APPROVED', decided_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME(), rejection_reason = NULL
        WHERE id = @organization_id;

        INSERT INTO dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id)
        VALUES (@admin_user_id, 'ORGANIZATION_APPROVED', 'ORGANIZATION', @organization_id);

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT @organization_id AS organization_id,
               CAST('APPROVED' AS varchar(30)) AS status,
               @requester_id AS assigned_manager_id,
               @rule_id AS commission_rule_id,
               @event_id AS event_id;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION sp_organization_approve_sp;
        THROW;
    END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.sp_organization_reject
-- Latest Source Migration: 054_organization_decision_procedures.sql
CREATE OR ALTER PROCEDURE dbo.sp_organization_reject
    @organization_id uniqueidentifier,
    @admin_user_id uniqueidentifier,
    @rejected_reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @admin_user_id AND platform_role = 'ADMIN')
       AND NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @admin_user_id AND role = 'ADMIN')
        THROW 52610, 'Administrator role required', 1;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION sp_organization_reject_sp;

    BEGIN TRY
        DECLARE @status varchar(30);

        SELECT @status = status
        FROM dbo.tc_organizations WITH (UPDLOCK, ROWLOCK)
        WHERE id = @organization_id;

        IF @status IS NULL
            THROW 52611, 'Organization not found', 1;
        IF @status <> 'PENDING_APPROVAL'
            THROW 52612, 'ORGANIZATION_ALREADY_PROCESSED', 1;
        IF @rejected_reason IS NULL OR LEN(LTRIM(RTRIM(@rejected_reason))) < 10 OR LEN(LTRIM(RTRIM(@rejected_reason))) > 500
            THROW 52614, 'Rejection reason must be between 10 and 500 characters', 1;

        UPDATE dbo.tc_organizations
        SET status = 'REJECTED', decided_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME(), rejection_reason = @rejected_reason
        WHERE id = @organization_id;

        INSERT INTO dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@admin_user_id, 'ORGANIZATION_REJECTED', 'ORGANIZATION', @organization_id, @rejected_reason);

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT @organization_id AS organization_id, CAST('REJECTED' AS varchar(30)) AS status;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION sp_organization_reject_sp;
        THROW;
    END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.sp_refund_adopt_event_cancellation
-- Latest Source Migration: 058_khanh06_event_cancellation.sql
CREATE OR ALTER PROCEDURE dbo.sp_refund_adopt_event_cancellation
 @event_id uniqueidentifier,@order_id uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @started=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_cancel_order;
 BEGIN TRY
  IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 AND USER_NAME()<>'dbo' THROW 54203,'WORKER_REQUIRED',1;
  DECLARE @count bigint,@next uniqueidentifier;
  SELECT @count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id AND status='CANCELLED';
  IF @count=0 THROW 54209,'EVENT_NOT_CANCELLED',1;
  SELECT TOP(1) @next=order_id FROM dbo.tc_cancellation_orders WHERE event_id=@event_id AND processed_at IS NULL ORDER BY order_id;
  -- Replayed/stale batches never advance a cursor past unprocessed work.
  IF EXISTS(SELECT 1 FROM dbo.tc_cancellation_orders WHERE event_id=@event_id AND order_id=@order_id AND processed_at IS NULL)
  BEGIN
   IF @next<>@order_id THROW 54209,'CANCELLATION_ORDER_CHANGED',1;
   EXEC dbo.usp_ProcessCancelledOrder @event_id,@order_id;
   UPDATE dbo.tc_cancellation_orders SET processed_at=SYSUTCDATETIME() WHERE event_id=@event_id AND order_id=@order_id;
   UPDATE dbo.tc_cancellation_jobs SET status='IN_PROGRESS',processed_orders=processed_orders+1,
    last_processed_order_id=@order_id,updated_at=SYSUTCDATETIME() WHERE event_id=@event_id;
   INSERT dbo.tc_audit_logs(action,aggregate_type,aggregate_id,detail)
    SELECT 'EVENT_CANCELLATION_ORDER_ADOPTED','ORDER',@order_id,
     (SELECT CONVERT(varchar(36),@event_id) eventId,reason cancellationReason FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)
    FROM dbo.tc_cancellation_jobs WHERE event_id=@event_id;
  END;
  IF @started=1 COMMIT TRANSACTION;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK TRANSACTION;
  ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_cancel_order;
  THROW;
 END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.sp_refund_approve
-- Latest Source Migration: 043_khanh04_admin_refund.sql
CREATE OR ALTER PROCEDURE dbo.sp_refund_approve @refund_id uniqueidentifier,@admin_user_id uniqueidentifier,
 @admin_note nvarchar(500)=NULL,@idempotency_key uniqueidentifier,@expected_auth_version int
AS BEGIN SET NOCOUNT ON;
 EXEC dbo.sp_admin_refund_decide @refund_id,@admin_user_id,@expected_auth_version,'APPROVE',@admin_note,NULL,@idempotency_key;
END;
GO

-- Object: [PROCEDURE] dbo.sp_refund_begin_attempt
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.sp_refund_begin_attempt @refund_id uniqueidentifier,@attempt_id uniqueidentifier,@lease_token uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @started=1 BEGIN TRAN; ELSE SAVE TRAN khanh05_claim;
 BEGIN TRY
  IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 THROW 53500,'Worker principal required',1;
  IF @lease_token IS NULL THROW 53501,'Lease token required',1;
  -- Use the same Event -> Order -> Payment -> Request -> Attempt order as the financial SPs.
  DECLARE @status varchar(20),@amount decimal(19,0),@ref nvarchar(200),@kind varchar(10),@now datetime2(3)=SYSUTCDATETIME();
  DECLARE @payment uniqueidentifier,@request uniqueidentifier,@order uniqueidentifier,@event uniqueidentifier,@count bigint,@captured decimal(19,0),@payment_status varchar(20),@purpose varchar(30);
  SELECT @payment=payment_id,@request=refund_id FROM dbo.vw_RefundAttemptLedger WHERE attempt_id=@attempt_id;
  SELECT @order=order_id FROM dbo.tc_payments WHERE id=@payment;
  SELECT @event=event_id FROM dbo.tc_orders WHERE id=@order;
  SELECT @count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
  SELECT @count=COUNT_BIG(*) FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
  SELECT @captured=amount,@payment_status=status FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
  IF @request IS NOT NULL SELECT @count=COUNT_BIG(*) FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@request;
  SELECT @status=l.status,@ref=l.provider_reference FROM dbo.tc_refund_transfer_logs l WITH(UPDLOCK,HOLDLOCK) WHERE l.id=@attempt_id;
  SELECT @amount=amount FROM dbo.tc_refund_obligations WHERE id=@request;
  IF @status NOT IN('PENDING','UNKNOWN') OR @status IS NULL OR @amount<=0 OR NOT EXISTS(
   SELECT 1 FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refund_obligations o ON o.id=l.refund_id
   WHERE l.id=@attempt_id AND o.id=@refund_id AND o.current_attempt_id=@attempt_id) BEGIN
    IF @started=1 COMMIT; RETURN;
  END;
  SELECT @purpose=purpose FROM dbo.tc_refund_obligations WHERE id=@request;
  IF @payment_status<>'CAPTURED' OR @payment_status IS NULL OR @amount>@captured
    OR (SELECT COALESCE(SUM(amount),0) FROM dbo.vw_RefundAttemptLedger WHERE payment_id=@payment AND status IN('PENDING','UNKNOWN','SUCCEEDED'))>@captured
    OR (@purpose='CUSTOMER_REFUND' AND NOT EXISTS(SELECT 1 FROM dbo.tc_orders WHERE id=@order AND accepted_payment_id=@payment))
   THROW 53505,'Refund is not backed by a reserved captured payment',1;
  IF EXISTS(SELECT 1 FROM dbo.tc_refund_dispatches WITH(UPDLOCK,HOLDLOCK) WHERE attempt_id=@attempt_id AND
    (lease_until>@now OR next_query_at>@now)) BEGIN IF @started=1 COMMIT; RETURN; END;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_refund_dispatches WHERE attempt_id=@attempt_id) BEGIN
   SET @kind=CASE WHEN @status='PENDING' AND @ref IS NULL THEN 'EXECUTE' ELSE 'QUERY' END;
   INSERT dbo.tc_refund_dispatches(attempt_id,lease_token,lease_until,next_query_at) VALUES(@attempt_id,@lease_token,DATEADD(second,120,@now),@now);
  END ELSE BEGIN
   SET @kind='QUERY';
   UPDATE dbo.tc_refund_dispatches SET lease_token=@lease_token,lease_until=DATEADD(second,120,@now) WHERE attempt_id=@attempt_id;
  END;
  IF @started=1 COMMIT;
  SELECT @refund_id refund_id,@attempt_id attempt_id,@amount amount,@ref provider_reference,@kind work_kind,@lease_token lease_token;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK; ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRAN khanh05_claim;
  THROW;
 END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.sp_refund_complete_without_transfer
-- Latest Source Migration: 048_khanh05_refund_worker.sql
-- Valid zero refunds are completed by SP10 in its approval transaction, without any attempt.
CREATE OR ALTER PROCEDURE dbo.sp_refund_complete_without_transfer @refund_id uniqueidentifier AS
BEGIN
 SET NOCOUNT ON;
 IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 THROW 53500,'Worker principal required',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_refund_obligations WHERE id=@refund_id AND amount=0 AND status='COMPLETED' AND current_attempt_id IS NULL)
  THROW 53504,'Zero refund must complete atomically through approval',1;
 SELECT @refund_id refund_id,CAST('COMPLETED' AS varchar(30)) status;
END;
GO

-- Object: [PROCEDURE] dbo.sp_refund_record_attempt_result
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.sp_refund_record_attempt_result
 @attempt_id uniqueidentifier,@lease_token uniqueidentifier,@status varchar(20),@provider_reference nvarchar(200)=NULL,@error_message nvarchar(500)=NULL
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @started=1 BEGIN TRAN; ELSE SAVE TRAN khanh05_result;
 BEGIN TRY
  IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 THROW 53500,'Worker principal required',1;
  IF @status IS NULL OR @status NOT IN('SUCCEEDED','FAILED','UNKNOWN') THROW 53502,'Invalid gateway result',1;
  -- Token check is optimistic; the financial SP owns the established lock order. Ledger is
  -- fenced again after that call. A competing query can only observe the same durable attempt.
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_refund_dispatches WHERE attempt_id=@attempt_id AND lease_token=@lease_token)
   THROW 53503,'Dispatch lease superseded',1;
  IF @status='UNKNOWN' AND NULLIF(@provider_reference,N'') IS NULL BEGIN
   DECLARE @payment uniqueidentifier,@request uniqueidentifier,@order uniqueidentifier,@event uniqueidentifier,@count bigint;
   SELECT @payment=payment_id,@request=refund_id FROM dbo.vw_RefundAttemptLedger WHERE attempt_id=@attempt_id;
   SELECT @order=order_id FROM dbo.tc_payments WHERE id=@payment;
   SELECT @event=event_id FROM dbo.tc_orders WHERE id=@order;
   SELECT @count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
   SELECT @count=COUNT_BIG(*) FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
   SELECT @count=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
   IF @request IS NOT NULL SELECT @count=COUNT_BIG(*) FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@request;
   UPDATE dbo.tc_refund_transfer_logs SET status='UNKNOWN',result='UNKNOWN' WHERE id=@attempt_id AND status IN('PENDING','UNKNOWN');
   IF @@ROWCOUNT=1 UPDATE dbo.tc_refund_obligations SET status='NEEDS_RECONCILIATION',current_attempt_id=@attempt_id,updated_at=SYSUTCDATETIME() WHERE id=@request;
  END ELSE BEGIN
   DECLARE @payload nvarchar(max)=(SELECT @status status,@provider_reference providerReference FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
   EXEC dbo.usp_ApplyRefundResult @attempt_id,@payload;
  END;
  UPDATE dbo.tc_refund_dispatches SET lease_token=NULL,lease_until=NULL,next_query_at=DATEADD(minute,15,SYSUTCDATETIME()),last_error=@error_message
   WHERE attempt_id=@attempt_id AND lease_token=@lease_token;
  IF @@ROWCOUNT<>1 THROW 53503,'Dispatch lease superseded',1;
  IF @started=1 COMMIT;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK; ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRAN khanh05_result;
  THROW;
 END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.sp_refund_reject
-- Latest Source Migration: 043_khanh04_admin_refund.sql
CREATE OR ALTER PROCEDURE dbo.sp_refund_reject @refund_id uniqueidentifier,@admin_user_id uniqueidentifier,
 @reject_reason nvarchar(1000),@idempotency_key uniqueidentifier,@expected_auth_version int
AS BEGIN SET NOCOUNT ON;
 EXEC dbo.sp_admin_refund_decide @refund_id,@admin_user_id,@expected_auth_version,'REJECT',NULL,@reject_reason,@idempotency_key;
END;
GO

-- Object: [PROCEDURE] dbo.sp_refund_request_for_tickets
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.sp_refund_request_for_tickets
 @order_id uniqueidentifier,@buyer_user_id uniqueidentifier,@ticket_ids_csv varchar(400),
 @reason nvarchar(500),@new_refund_id uniqueidentifier OUTPUT,
 @idempotency_key uniqueidentifier,@expected_auth_version int
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @owns bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @owns=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION refund_request_save;
 BEGIN TRY
  DECLARE @app_result int,@resource nvarchar(255)=CONCAT('hold-user:',CONVERT(varchar(36),@buyer_user_id));
  EXEC @app_result=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
  IF @app_result<0 THROW 51830,'Buyer mutation lock unavailable; retry',1;
  DECLARE @now datetime2(3)=SYSUTCDATETIME(),@fingerprint varchar(200),@old_fingerprint varchar(200),
          @event_id uniqueidentifier,@owner uniqueidentifier,@order_status varchar(30),
          @event_status varchar(30),@start datetime2(3),@payment uniqueidentifier,@amount decimal(19,0),
          @count int,@locked int,@canonical varchar(400);
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@buyer_user_id
          AND status='ACTIVE' AND auth_version=@expected_auth_version)
    THROW 53001,'SESSION_EXPIRED',1;
  IF @order_id IS NULL OR @idempotency_key IS NULL OR LEN(TRIM(@reason)) NOT BETWEEN 10 AND 500
     OR @reason IS NULL OR @ticket_ids_csv IS NULL OR LEN(@ticket_ids_csv)=0
     THROW 53000,'INVALID_REQUEST',1;
  DECLARE @ids TABLE(id uniqueidentifier PRIMARY KEY);
  IF EXISTS(SELECT 1 FROM STRING_SPLIT(@ticket_ids_csv,',') WHERE LEN(value)<>36 OR TRY_CONVERT(uniqueidentifier,value) IS NULL)
     THROW 53000,'INVALID_REQUEST',1;
  IF (SELECT COUNT(*) FROM STRING_SPLIT(@ticket_ids_csv,',')) NOT BETWEEN 1 AND 8
     OR (SELECT COUNT(*) FROM STRING_SPLIT(@ticket_ids_csv,','))<>(SELECT COUNT(DISTINCT value) FROM STRING_SPLIT(@ticket_ids_csv,','))
     THROW 53000,'INVALID_REQUEST',1;
  INSERT @ids SELECT CONVERT(uniqueidentifier,value) FROM STRING_SPLIT(@ticket_ids_csv,',');
  SELECT @count=COUNT(*) FROM @ids;
  SELECT @canonical=STRING_AGG(CONVERT(varchar(36),id),',') WITHIN GROUP(ORDER BY CONVERT(varchar(36),id)) FROM @ids;
  SET @reason=TRIM(@reason);
  SET @fingerprint=CONVERT(varchar(64),HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@order_id),'|',@canonical,'|',@reason)),2);
  DECLARE @payload nvarchar(max);
  SELECT @new_refund_id=target_id,@old_fingerprint=fingerprint,@payload=response_payload
    FROM dbo.tc_request_idempotency WITH(UPDLOCK,HOLDLOCK)
    WHERE actor_id=@buyer_user_id AND operation='CREATE_REFUND_REQUEST' AND idempotency_key=CONVERT(varchar(36),@idempotency_key);
  IF @old_fingerprint IS NOT NULL BEGIN
    IF @fingerprint<>@old_fingerprint THROW 53009,'IDEMPOTENCY_CONFLICT',1;
    IF @owns=1 COMMIT;
    SELECT * FROM OPENJSON(@payload) WITH(refund_id varchar(36) '$.refundId',order_id varchar(36) '$.orderId',
      amount varchar(30) '$.totalRefundAmount',status varchar(40) '$.status',ticket_count int '$.ticketCount',
      created_at datetimeoffset '$.createdAt',event_name nvarchar(250) '$.eventName',reason nvarchar(500) '$.reason',rejection_reason nvarchar(1000) '$.rejectionReason');
    RETURN;
  END;
  SELECT @event_id=event_id FROM dbo.tc_orders WHERE id=@order_id;
  IF @event_id IS NULL THROW 53004,'ORDER_NOT_FOUND',1;
  SELECT @event_status=status,@start=start_time FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@buyer_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version) THROW 53001,'SESSION_EXPIRED',1;
  SELECT @owner=user_id,@order_status=status,@payment=accepted_payment_id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
  SET @now=SYSUTCDATETIME(); -- Evaluate the deadline after lock waits, using the database clock.
  IF @owner<>@buyer_user_id THROW 53003,'ORDER_FORBIDDEN',1;
  IF @order_status<>'PAID' OR @event_status<>'PUBLISHED' OR @now>=@start THROW 53005,'REFUND_NOT_ELIGIBLE',1;
  DECLARE @ticket uniqueidentifier,@found uniqueidentifier;
  SET @locked=0;
  DECLARE ticket_locks CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM @ids ORDER BY id;
  OPEN ticket_locks; FETCH NEXT FROM ticket_locks INTO @ticket;
  WHILE @@FETCH_STATUS=0 BEGIN
    SET @found=NULL;
    SELECT @found=id FROM dbo.tc_tickets WITH(UPDLOCK,HOLDLOCK) WHERE id=@ticket;
    IF @found IS NOT NULL SET @locked=@locked+1;
    FETCH NEXT FROM ticket_locks INTO @ticket;
  END;
  CLOSE ticket_locks; DEALLOCATE ticket_locks;
  SET @now=SYSUTCDATETIME();
  IF @now>=@start THROW 53005,'REFUND_NOT_ELIGIBLE',1;
  IF @locked<>@count OR EXISTS(SELECT 1 FROM @ids x JOIN dbo.tc_tickets t ON t.id=x.id
      JOIN dbo.tc_order_items i ON i.id=t.order_item_id WHERE i.order_id<>@order_id)
    THROW 53000,'INVALID_TICKETS',1;
  IF EXISTS(SELECT 1 FROM @ids x JOIN dbo.tc_tickets t ON t.id=x.id WHERE t.status='REFUND_PENDING')
     OR EXISTS(SELECT 1 FROM @ids x JOIN dbo.tc_refund_tickets rt ON rt.ticket_id=x.id WHERE rt.is_open=1)
    THROW 53009,'REFUND_PENDING',1;
  IF EXISTS(SELECT 1 FROM @ids x JOIN dbo.tc_tickets t ON t.id=x.id WHERE t.status<>'ACTIVE')
    THROW 53005,'REFUND_NOT_ELIGIBLE',1;
  SELECT @amount=SUM(t.paid_amount) FROM @ids x JOIN dbo.tc_tickets t ON t.id=x.id;
  IF @amount>0 AND NOT EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment AND order_id=@order_id AND status='CAPTURED')
    THROW 53005,'PAYMENT_NOT_ACCEPTED',1;
  SET @new_refund_id=NEWID();
  INSERT dbo.tc_refund_obligations(id,order_id,payment_id,amount,purpose,reason_type,reason,status,requester_id,created_at,updated_at)
    VALUES(@new_refund_id,@order_id,@payment,@amount,'CUSTOMER_REFUND','CUSTOMER_REQUEST',@reason,'REQUESTED',@buyer_user_id,@now,@now);
  INSERT dbo.tc_refund_tickets(refund_id,ticket_id,created_at) SELECT @new_refund_id,id,@now FROM @ids;
  UPDATE t SET status='REFUND_PENDING',version=version+1 FROM dbo.tc_tickets t JOIN @ids x ON x.id=t.id;
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail)
    VALUES(@buyer_user_id,'REFUND_REQUESTED','Refund',@new_refund_id,N'Customer requested ticket refund');
  SET @payload=(SELECT CONVERT(varchar(36),@new_refund_id) refundId,
    CONVERT(varchar(36),@order_id) orderId,CONVERT(varchar(30),@amount) totalRefundAmount,'REQUESTED' status,
    @count ticketCount,CONVERT(varchar(24),@now,126)+'Z' createdAt,e.title eventName,@reason reason,
    N'Yêu cầu hoàn tiền đã được tiếp nhận và chuyển đến Ban quản trị xem xét.' message
    FROM dbo.tc_events e WHERE e.id=@event_id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
  INSERT dbo.tc_request_idempotency(actor_id,operation,idempotency_key,target_id,fingerprint,response_payload,status,expires_at)
    VALUES(@buyer_user_id,'CREATE_REFUND_REQUEST',CONVERT(varchar(36),@idempotency_key),@new_refund_id,@fingerprint,@payload,'COMPLETED',DATEADD(day,1,@now));
  IF @owns=1 COMMIT;
  SELECT * FROM OPENJSON(@payload) WITH(refund_id varchar(36) '$.refundId',order_id varchar(36) '$.orderId',
    amount varchar(30) '$.totalRefundAmount',status varchar(40) '$.status',ticket_count int '$.ticketCount',
    created_at datetimeoffset '$.createdAt',event_name nvarchar(250) '$.eventName',reason nvarchar(500) '$.reason',rejection_reason nvarchar(1000) '$.rejectionReason');
 END TRY
 BEGIN CATCH
  IF XACT_STATE()<>0 BEGIN
    IF @owns=1 OR XACT_STATE()=-1 ROLLBACK; ELSE ROLLBACK TRANSACTION refund_request_save;
  END;
  THROW;
 END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.sp_refund_retry
-- Latest Source Migration: 043_khanh04_admin_refund.sql
CREATE OR ALTER PROCEDURE dbo.sp_refund_retry @refund_id uniqueidentifier,@admin_user_id uniqueidentifier,
 @idempotency_key uniqueidentifier,@expected_auth_version int
AS BEGIN SET NOCOUNT ON;
 EXEC dbo.sp_admin_refund_decide @refund_id,@admin_user_id,@expected_auth_version,'RETRY',NULL,NULL,@idempotency_key;
END;
GO

-- Object: [PROCEDURE] dbo.sp_ticket_check_in
-- Latest Source Migration: 042_checkin_event_user_lock_order.sql
CREATE OR ALTER PROCEDURE dbo.sp_ticket_check_in
 @event_id uniqueidentifier,@staff_user_id uniqueidentifier,@ticket_uuid uniqueidentifier=NULL,
 @ticket_code varchar(120)=NULL,@customer_name nvarchar(200)=NULL,
 @request_key uniqueidentifier=NULL,@request_fingerprint char(64)=NULL,@invalid_signature bit=0
AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT ON;
 DECLARE @owns bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @owns=1 BEGIN TRANSACTION;ELSE SAVE TRANSACTION tc_k02_scan;
 BEGIN TRY
  IF @event_id IS NULL OR @staff_user_id IS NULL OR @request_key IS NULL
   OR @request_fingerprint IS NULL OR @request_fingerprint LIKE '%[^0-9a-f]%'
   OR LEN(@request_fingerprint)<>64
   OR (@invalid_signature=0 AND (@ticket_uuid IS NULL AND @ticket_code IS NULL OR @ticket_uuid IS NOT NULL AND @ticket_code IS NOT NULL))
   THROW 53204,'Invalid check-in arguments',1;
  DECLARE @org uniqueidentifier,@event_status varchar(30),@start datetime2(3),@end datetime2(3);
  SELECT @org=organization_id,@event_status=status,@start=start_time,@end=end_time
   FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
  IF @org IS NULL THROW 53202,'Event not found',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@staff_user_id AND status='ACTIVE')
   THROW 53201,'Active event staff membership required',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_organization_memberships WITH(UPDLOCK,HOLDLOCK)
   WHERE user_id=@staff_user_id AND organization_id=@org AND active=1 AND role IN('MANAGER','CHECK_IN_STAFF'))
   THROW 53201,'Active event staff membership required',1;
  DECLARE @fingerprint char(64),@result varchar(30),@ticket uniqueidentifier,@at datetime2(3),@checked datetime2(3);
  SELECT @fingerprint=fingerprint,@result=result,@ticket=ticket_id,@at=scanned_at,@checked=checked_in_at,@customer_name=customer_name
   FROM dbo.tc_checkin_scan_requests WITH(UPDLOCK,HOLDLOCK) WHERE actor_id=@staff_user_id AND request_key=@request_key;
  IF @fingerprint IS NOT NULL
  BEGIN
   IF @fingerprint<>@request_fingerprint OR NOT EXISTS(SELECT 1 FROM dbo.tc_checkin_scan_requests
     WHERE actor_id=@staff_user_id AND request_key=@request_key AND event_id=@event_id)
    THROW 53203,'Idempotency key belongs to a different scan',1;
  END
  ELSE
  BEGIN
   DECLARE @ticket_status varchar(30),@ticket_event uniqueidentifier,@display nvarchar(200);
   IF @invalid_signature=0
    SELECT @ticket=t.id,@ticket_status=t.status,@ticket_event=o.event_id,@display=u.full_name
    FROM dbo.tc_tickets t WITH(UPDLOCK,HOLDLOCK) JOIN dbo.tc_order_items i ON i.id=t.order_item_id
    JOIN dbo.tc_orders o ON o.id=i.order_id JOIN dbo.tc_users u ON u.id=o.user_id
    WHERE (@ticket_uuid IS NOT NULL AND t.id=@ticket_uuid) OR (@ticket_code IS NOT NULL AND t.ticket_code=@ticket_code);
   SET @at=SYSUTCDATETIME();
   SET @customer_name=CASE WHEN @ticket_event=@event_id THEN COALESCE(@customer_name,@display) ELSE NULL END;
   SET @result=CASE
    WHEN @invalid_signature=1 THEN 'INVALID_SIGNATURE'
    WHEN @ticket IS NULL THEN 'TICKET_NOT_FOUND'
    WHEN @ticket_event<>@event_id THEN 'WRONG_EVENT'
    WHEN @event_status='CANCELLED' THEN 'EVENT_CANCELLED'
    WHEN @event_status<>'PUBLISHED' OR @at<DATEADD(minute,-60,@start) OR @at>=@end THEN 'OUTSIDE_WINDOW'
    WHEN @ticket_status='USED' THEN 'ALREADY_CHECKED_IN'
    WHEN @ticket_status='REFUND_PENDING' THEN 'TICKET_REFUND_PENDING'
    WHEN @ticket_status='REFUNDED' THEN 'TICKET_REFUNDED'
    WHEN @ticket_status<>'ACTIVE' THEN 'TICKET_INVALID_STATUS'
    ELSE 'CHECKED_IN_SUCCESS' END;
   IF @result='CHECKED_IN_SUCCESS'
   BEGIN
    UPDATE dbo.tc_tickets SET status='USED',used_at=@at,version=version+1 WHERE id=@ticket AND status='ACTIVE';
    IF @@ROWCOUNT<>1 THROW 53205,'Ticket changed during scan',1;
    SET @checked=@at;
    INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail)
     VALUES(@staff_user_id,'TICKET_CHECKED_IN','TICKET',@ticket,N'{"source":"KHANH-02"}');
   END
   ELSE IF @result='ALREADY_CHECKED_IN'
    SELECT @checked=MAX(scanned_at) FROM dbo.tc_ticket_check_in_logs WHERE ticket_id=@ticket AND result='SUCCESS';
   -- Foreign/unknown identifiers cannot expose another organization's customer or ticket data.
   IF @ticket_event<>@event_id SET @ticket=NULL;
   INSERT dbo.tc_ticket_check_in_logs(event_id,ticket_id,actor_id,result,scanned_at)
    VALUES(@event_id,@ticket,@staff_user_id,
     CASE @result WHEN 'CHECKED_IN_SUCCESS' THEN 'SUCCESS' WHEN 'ALREADY_CHECKED_IN' THEN 'ALREADY_USED'
      WHEN 'WRONG_EVENT' THEN 'WRONG_EVENT' WHEN 'OUTSIDE_WINDOW' THEN 'OUTSIDE_WINDOW'
      WHEN 'INVALID_SIGNATURE' THEN 'INVALID_CODE' WHEN 'TICKET_NOT_FOUND' THEN 'INVALID_CODE' ELSE 'INACTIVE_TICKET' END,@at);
   INSERT dbo.tc_checkin_scan_requests(actor_id,request_key,fingerprint,event_id,ticket_id,result,customer_name,checked_in_at,scanned_at,expires_at)
    VALUES(@staff_user_id,@request_key,@request_fingerprint,@event_id,@ticket,@result,@customer_name,@checked,@at,DATEADD(hour,1,@at));
  END;
  -- Keep a durable tombstone beyond the Redis TTL: an old key cannot cause a second mutation.
  SELECT @result result,@ticket ticket_id,
   CASE WHEN @result IN('CHECKED_IN_SUCCESS','ALREADY_CHECKED_IN') THEN t.ticket_code ELSE NULL END ticket_code,
   @customer_name customer_name,i.zone_name_snapshot zone_name,i.seat_label_snapshot seat_label,
   @checked checked_in_at,@at scanned_at,@org organization_id
  FROM (SELECT 1 n) d LEFT JOIN dbo.tc_tickets t ON t.id=@ticket LEFT JOIN dbo.tc_order_items i ON i.id=t.order_item_id;
  IF @owns=1 COMMIT TRANSACTION;
 END TRY
 BEGIN CATCH
  IF @owns=1 AND XACT_STATE()<>0 ROLLBACK TRANSACTION;
  ELSE IF @owns=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_k02_scan;
  THROW;
 END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_AddMember
-- Latest Source Migration: 032_sql_report_permissions.sql
CREATE OR ALTER PROCEDURE dbo.usp_AddMember @organization_id uniqueidentifier,@actor_id uniqueidentifier,@user_id uniqueidentifier,@role varchar(30)
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @lock_count bigint;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_organizations WITH(UPDLOCK,HOLDLOCK) WHERE id=@organization_id;
 EXEC dbo.usp_RequireOrganizationManager @actor_id,@organization_id;
 IF @role IS NULL OR @role NOT IN('MANAGER','CHECK_IN_STAFF') THROW 51816,'Invalid organization role',1;
 EXEC dbo.usp_RequireActiveMember @user_id;
 INSERT dbo.tc_organization_memberships(user_id,organization_id,role,active) VALUES(@user_id,@organization_id,@role,1);
 INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail) VALUES(@actor_id,'MEMBER_ADDED','ORGANIZATION',@organization_id,CONVERT(varchar(36),@user_id));
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ApplyOrderCoupon
-- Latest Source Migration: 035_sql_report_runtime.sql
/* Stored Procedure: usp_ApplyOrderCoupon */
CREATE OR ALTER PROCEDURE dbo.usp_ApplyOrderCoupon
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @coupon_code varchar(80) = NULL
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp03;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @organization_id uniqueidentifier, @hold_id uniqueidentifier,
                @subtotal decimal(19,0), @old_coupon uniqueidentifier, @new_coupon uniqueidentifier,
                @discount decimal(19,0) = 0, @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id = o.event_id, @hold_id = o.hold_id, @subtotal = o.subtotal_amount, @old_coupon = o.coupon_id
        FROM dbo.tc_orders o JOIN dbo.tc_ticket_holds h ON h.id = o.hold_id
        WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = 'PENDING_PAYMENT' AND h.status = 'ACTIVE' AND h.expires_at > @now;
        IF @event_id IS NULL THROW 51230, 'Order is not coupon-eligible', 1;
        SELECT @organization_id = organization_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id AND status = 'PUBLISHED';
        IF @organization_id IS NULL THROW 51231, 'Event is unavailable', 1;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_ticket_holds WITH(UPDLOCK,HOLDLOCK) WHERE id=@hold_id;
        SET @now=SYSUTCDATETIME();
        SET @subtotal=NULL;
        SELECT @subtotal=o.subtotal_amount,@old_coupon=o.coupon_id FROM dbo.tc_orders o WITH(UPDLOCK,HOLDLOCK) JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id
        WHERE o.id=@order_id AND o.user_id=@actor_id AND o.status='PENDING_PAYMENT' AND h.status='ACTIVE' AND h.expires_at>@now;
        IF @subtotal IS NULL THROW 51230,'Order is not coupon-eligible',1;
        IF EXISTS (SELECT 1 FROM dbo.tc_payments WHERE order_id = @order_id AND status IN ('PENDING', 'UNKNOWN'))
            THROW 51232, 'Coupon cannot change while payment is unresolved', 1;
        IF @coupon_code IS NOT NULL
            SELECT @new_coupon = id FROM dbo.tc_coupons WITH (UPDLOCK, HOLDLOCK)
            WHERE organization_id = @organization_id AND code = @coupon_code;
        IF @coupon_code IS NOT NULL AND @new_coupon IS NULL THROW 51233, 'Coupon not found', 1;
        IF @old_coupon = @new_coupon OR (@old_coupon IS NULL AND @new_coupon IS NULL) BEGIN
            SELECT subtotal_amount, discount_amount, total_amount, coupon_id FROM dbo.tc_orders WHERE id = @order_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @new_coupon IS NOT NULL BEGIN
            IF NOT EXISTS (SELECT 1 FROM dbo.fn_GetCouponEligibility(@new_coupon, @organization_id, @subtotal, @now) WHERE is_eligible = 1)
                THROW 51234, 'Coupon is not eligible', 1;
            SELECT @discount = dbo.fn_CalculateCouponDiscountCapped(@subtotal,id)
            FROM dbo.tc_coupons WHERE id = @new_coupon;
            IF @discount IS NULL THROW 51235, 'Coupon discount is invalid', 1;
        END;
        IF EXISTS (SELECT 1 FROM dbo.tc_coupon_redemptions WHERE order_id = @order_id)
            UPDATE dbo.tc_coupon_redemptions
            SET coupon_id = COALESCE(@new_coupon, coupon_id),
                status = CASE WHEN @new_coupon IS NULL THEN 'RELEASED' ELSE 'RESERVED' END,
                reserved_at = CASE WHEN @new_coupon IS NULL THEN reserved_at ELSE @now END,
                expires_at = CASE WHEN @new_coupon IS NULL THEN expires_at ELSE (SELECT expires_at FROM dbo.tc_ticket_holds WHERE id = @hold_id) END,
                consumed_at = NULL, released_at = CASE WHEN @new_coupon IS NULL THEN @now ELSE NULL END
            WHERE order_id = @order_id;
        ELSE IF @new_coupon IS NOT NULL
            INSERT dbo.tc_coupon_redemptions(coupon_id, order_id, expires_at)
            VALUES (@new_coupon, @order_id, (SELECT expires_at FROM dbo.tc_ticket_holds WHERE id = @hold_id));
        UPDATE dbo.tc_orders SET coupon_id = @new_coupon, discount_amount = @discount,
               total_amount = @subtotal - @discount, version = version + 1 WHERE id = @order_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT subtotal_amount, discount_amount, total_amount, coupon_id FROM dbo.tc_orders WHERE id = @order_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp03;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_ApplyOrderCouponRequest
-- Latest Source Migration: 030_order_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_ApplyOrderCouponRequest
    @actor_id uniqueidentifier,@auth_version int,@key uniqueidentifier,@fingerprint varchar(64),@order_id uniqueidentifier,@coupon_code nvarchar(80)=NULL
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @@TRANCOUNT<>0 THROW 51410,'Facade owns transaction',1;
    BEGIN TRANSACTION;
    BEGIN TRY
        DECLARE @payload nvarchar(max),@saved_fingerprint varchar(200),@saved_target uniqueidentifier,@event_id uniqueidentifier,@hold_id uniqueidentifier,
            @owner_id uniqueidentifier,@old_coupon uniqueidentifier,@new_coupon uniqueidentifier,@locked_new uniqueidentifier,@org uniqueidentifier,
            @now datetime2(3),@expires datetime2(3),@reason varchar(30),@discount decimal(19,0)=0,@remaining int,@valid_until datetime2(3);
        SELECT @payload=response_payload,@saved_fingerprint=fingerprint,@saved_target=target_id FROM dbo.tc_request_idempotency WITH(UPDLOCK,HOLDLOCK)
            WHERE actor_id=@actor_id AND operation='ORDER_MUTATION' AND idempotency_key=CONVERT(varchar(36),@key);
        SELECT @event_id=event_id,@hold_id=hold_id,@owner_id=user_id FROM dbo.tc_orders WHERE id=@order_id;
        EXEC dbo.usp_HoldLockSelection @actor_id,@event_id,N'[]',@hold_id,@include_active=0;
        EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
        IF @owner_id IS NULL THROW 51404,'Order not found',1;
        IF @owner_id<>@actor_id THROW 51403,'Order owner required',1;
        IF @payload IS NOT NULL BEGIN
            IF @saved_fingerprint<>@fingerprint OR @saved_target<>@order_id THROW 51408,'Changed request for key',1;
            SELECT @expires=expires_at FROM dbo.tc_ticket_holds WHERE id=@hold_id; COMMIT; SELECT @payload response_payload,CAST(1 AS bit) replayed,CONVERT(varchar(36),@hold_id) hold_id,CONVERT(varchar(23),@expires,126)+'Z' expires_at; RETURN;
        END;
        SELECT @old_coupon=coupon_id FROM dbo.tc_orders WHERE id=@order_id;
        SELECT @org=organization_id FROM dbo.tc_events WHERE id=@event_id;
        SELECT @old_coupon=coupon_id FROM dbo.tc_orders WHERE id=@order_id;
        SELECT @new_coupon=id FROM dbo.tc_coupons WHERE organization_id=@org AND code=@coupon_code;
        SET @locked_new=@new_coupon;
        EXEC dbo.usp_OrderLockCoupons @old_coupon,@new_coupon;
        DECLARE @quota_dummy int;
        SELECT @quota_dummy=COUNT(*) FROM dbo.tc_coupon_redemptions WITH(UPDLOCK,HOLDLOCK) WHERE coupon_id=@new_coupon AND status IN('RESERVED','CONSUMED');
        IF @coupon_code IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.tc_coupons WHERE id=@locked_new AND organization_id=@org AND code=@coupon_code)
            BEGIN IF @locked_new IS NOT NULL THROW 51309,'Coupon preflight changed',1; END;
        IF EXISTS(SELECT 1 FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@order_id AND status IN('PENDING','UNKNOWN'))
            THROW 51407,'Payment unresolved',1;
        SET @now=SYSUTCDATETIME();
        SELECT @expires=expires_at FROM dbo.tc_ticket_holds WHERE id=@hold_id AND status='ACTIVE';
        IF @expires IS NULL OR @expires<=@now OR NOT EXISTS(SELECT 1 FROM dbo.tc_orders WHERE id=@order_id AND status='PENDING_PAYMENT')
            THROW 51406,'Order is terminal or expired',1;
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND status='PUBLISHED' AND @now>=sale_start AND @now<sale_end)
            THROW 51406,'Event is not on sale',1;
        IF @coupon_code IS NOT NULL BEGIN
            EXEC dbo.usp_OrderCouponDecision @order_id,@coupon_code,@now,@new_coupon OUTPUT,@reason OUTPUT,@discount OUTPUT,@remaining OUTPUT,@valid_until OUTPUT;
            IF ISNULL(@new_coupon,'00000000-0000-0000-0000-000000000000')<>ISNULL(@locked_new,'00000000-0000-0000-0000-000000000000') THROW 51309,'Coupon preflight changed',1;
            IF @reason<>'ELIGIBLE' BEGIN
                DECLARE @error nvarchar(2048)=CONCAT('COUPON_',@reason);
                THROW 51405,@error,1;
            END;
        END;
        IF @old_coupon<>@new_coupon OR @old_coupon IS NULL AND @new_coupon IS NOT NULL OR @old_coupon IS NOT NULL AND @new_coupon IS NULL BEGIN
            IF EXISTS(SELECT 1 FROM dbo.tc_coupon_redemptions WHERE order_id=@order_id)
                UPDATE dbo.tc_coupon_redemptions SET coupon_id=COALESCE(@new_coupon,coupon_id),status=CASE WHEN @new_coupon IS NULL THEN 'RELEASED' ELSE 'RESERVED' END,
                    reserved_at=CASE WHEN @new_coupon IS NULL THEN reserved_at ELSE @now END,expires_at=CASE WHEN @new_coupon IS NULL THEN expires_at ELSE @expires END,
                    consumed_at=NULL,released_at=CASE WHEN @new_coupon IS NULL THEN @now ELSE NULL END WHERE order_id=@order_id;
            ELSE IF @new_coupon IS NOT NULL INSERT dbo.tc_coupon_redemptions(coupon_id,order_id,reserved_at,expires_at) VALUES(@new_coupon,@order_id,@now,@expires);
            UPDATE dbo.tc_orders SET coupon_id=@new_coupon,discount_amount=@discount,total_amount=subtotal_amount-@discount,version=version+1 WHERE id=@order_id;
        END;
        SELECT @payload=(SELECT LOWER(CONVERT(char(36),o.id)) orderId,c.code appliedCouponCode,CONVERT(varchar(30),o.subtotal_amount) subtotal,
            CONVERT(varchar(30),o.discount_amount) discountAmount,CONVERT(varchar(30),o.total_amount) totalAmount,o.version
            FROM dbo.tc_orders o LEFT JOIN dbo.tc_coupons c ON c.id=o.coupon_id WHERE o.id=@order_id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
        INSERT dbo.tc_request_idempotency(actor_id,operation,idempotency_key,target_id,fingerprint,response_payload,expires_at)
            VALUES(@actor_id,'ORDER_MUTATION',CONVERT(varchar(36),@key),@order_id,@fingerprint,@payload,DATEADD(second,600,@now));
        COMMIT; SELECT @payload response_payload,CAST(0 AS bit) replayed,CONVERT(varchar(36),@hold_id) hold_id,CONVERT(varchar(23),@expires,126)+'Z' expires_at;
    END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ApplyPaymentResult
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.usp_ApplyPaymentResult
    @order_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @payment_id uniqueidentifier = NULL,
    @verified_result nvarchar(max), @ticket_codes nvarchar(max) = NULL, @retry_failed_compensation bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp09;
    BEGIN TRY
        IF ISJSON(@verified_result) <> 1 THROW 51390, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @event_id uniqueidentifier, @hold_id uniqueidentifier, @owner_id uniqueidentifier,
                @accepted uniqueidentifier, @sale_start datetime2(3), @sale_end datetime2(3),
                @order_status varchar(30), @hold_status varchar(20), @expires datetime2(3),
                @event_status varchar(30), @total decimal(19,0), @payment_status varchar(20),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result IS NULL OR @result NOT IN ('CAPTURED', 'FAILED', 'UNKNOWN') THROW 51391, 'Unsupported payment result', 1;
        SELECT @event_id = event_id, @hold_id = hold_id, @owner_id = user_id, @order_status = status, @total = total_amount
        FROM dbo.tc_orders WHERE id = @order_id;
        IF @event_id IS NULL THROW 51392, 'Order not found', 1;
        SELECT @event_status = status, @sale_start = sale_start, @sale_end = sale_end FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @hold_status = status, @expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;
        SELECT @order_status=status,@total=total_amount,@accepted=accepted_payment_id
        FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
        SET @now=SYSUTCDATETIME();
        IF @payment_id IS NULL BEGIN
            IF @actor_id IS NULL OR @actor_id <> @owner_id OR @total <> 0 OR @result <> 'CAPTURED'
                THROW 51393, 'Only the owner may complete a zero-value order', 1;
        END ELSE BEGIN
            IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1
                THROW 51389, 'Buyer principal cannot apply a paid provider result', 1;
            IF @result='CAPTURED' AND NULLIF(LTRIM(RTRIM(@provider_reference)),N'') IS NULL THROW 51801,'Captured result requires provider reference',1;
            SELECT @payment_status = status FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK)
            WHERE id = @payment_id AND order_id = @order_id AND amount = @total;
            IF @payment_status IS NULL THROW 51394, 'Payment does not match order amount', 1;
            IF EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND provider_reference IS NOT NULL AND @provider_reference IS NOT NULL AND provider_reference<>@provider_reference) THROW 51802,'Provider reference conflict',1;
            IF JSON_VALUE(@verified_result,'$.providerAmount') IS NOT NULL AND (TRY_CONVERT(decimal(19,0),JSON_VALUE(@verified_result,'$.providerAmount')) IS NULL OR TRY_CONVERT(decimal(19,0),JSON_VALUE(@verified_result,'$.providerAmount'))<>@total) THROW 51803,'Gateway amount mismatch',1;
            IF JSON_VALUE(@verified_result,'$.txnRef') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND txn_ref=JSON_VALUE(@verified_result,'$.txnRef')) THROW 51804,'Gateway transaction mismatch',1;
            IF @payment_status = 'CAPTURED' AND @order_status = 'PAID' AND @accepted=@payment_id BEGIN
                SELECT @order_status order_status, @payment_status payment_status;
                IF @started = 1 COMMIT TRANSACTION; RETURN;
            END;
            IF @payment_status = 'CAPTURED' AND @result <> 'CAPTURED' BEGIN
                SELECT @order_status order_status, @payment_status payment_status;
                IF @started = 1 COMMIT TRANSACTION; RETURN;
            END;
            UPDATE dbo.tc_payments SET status = @result,
                   provider_reference = COALESCE(@provider_reference, provider_reference),
                   captured_at = CASE WHEN @result = 'CAPTURED' THEN COALESCE(captured_at, @now) ELSE captured_at END,
                   version = version + 1 WHERE id = @payment_id;
        END;
        IF @result <> 'CAPTURED' BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_status order_status, @result payment_status; RETURN;
        END;
        IF @order_status = 'PAID' AND (@payment_id IS NULL OR @accepted=@payment_id) BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_status order_status, @result payment_status; RETURN;
        END;
        IF @order_status<>'PENDING_PAYMENT' OR @event_status <> 'PUBLISHED' OR @hold_status <> 'ACTIVE' OR @expires <= @now OR @now<@sale_start OR @now>=@sale_end BEGIN
            IF @payment_id IS NULL THROW 51395, 'Zero-value order expired before completion', 1;
            IF NOT EXISTS (SELECT 1 FROM dbo.vw_RefundAttemptLedger WHERE payment_id = @payment_id AND purpose = 'PAYMENT_COMPENSATION' AND status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED'))
            BEGIN
                IF @retry_failed_compensation = 0 AND EXISTS (SELECT 1 FROM dbo.vw_RefundAttemptLedger WHERE payment_id = @payment_id AND purpose = 'PAYMENT_COMPENSATION' AND status = 'FAILED')
                    THROW 51396, 'Compensation retry requires explicit authorization', 1;
                -- A compensation is one obligation with exactly one attempt that shares its id.
                DECLARE @compensation_id uniqueidentifier = NEWID();
                INSERT dbo.tc_refund_obligations(id, order_id, payment_id, amount, purpose, status, current_attempt_id)
                VALUES (@compensation_id, @order_id, @payment_id, @total, 'PAYMENT_COMPENSATION', 'PROCESSING', @compensation_id);
                INSERT dbo.tc_refund_transfer_logs(id, refund_id, attempt_no, status, result)
                VALUES (@compensation_id, @compensation_id, 1, 'PENDING', 'PENDING');
            END;
            IF @started = 1 COMMIT TRANSACTION;
            SELECT CAST('COMPENSATION_PENDING' AS varchar(30)) order_status, CAST('CAPTURED' AS varchar(20)) payment_status; RETURN;
        END;
        IF ISJSON(@ticket_codes) <> 1 THROW 51397, 'Ticket codes must be a JSON array', 1;
        DECLARE @issued TABLE(ordinal int PRIMARY KEY, ticket_code varchar(100), qr_hash varbinary(64));
        INSERT @issued
        SELECT CONVERT(int, [key]) + 1, JSON_VALUE(value, '$.ticketCode'),
               TRY_CONVERT(varbinary(64), JSON_VALUE(value, '$.qrSecretHashHex'), 2)
        FROM OPENJSON(@ticket_codes);
        DECLARE @expected int = (SELECT SUM(quantity) FROM dbo.tc_order_items WHERE order_id = @order_id);
        IF @expected IS NULL OR @expected <> (SELECT COUNT(*) FROM @issued)
           OR EXISTS (SELECT 1 FROM @issued WHERE ticket_code IS NULL OR qr_hash IS NULL)
           OR EXISTS (SELECT ticket_code FROM @issued GROUP BY ticket_code HAVING COUNT(*) > 1)
            THROW 51398, 'Ticket identities do not match order quantity', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.zone_id = z.id);
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.seat_id = s.id);
        IF EXISTS (SELECT 1 FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
                   WHERE oi.order_id = @order_id AND s.status <> 'HELD')
            THROW 51388, 'Held seat inventory is inconsistent', 1;
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (
                SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_order_items
                WHERE order_id = @order_id AND seat_id IS NULL GROUP BY zone_id
            ) q ON q.zone_id = z.id WHERE z.held_quantity < q.quantity
        ) THROW 51387, 'Held standing inventory is inconsistent', 1;
        UPDATE s SET status = 'SOLD', version = s.version + 1 FROM dbo.tc_seats s
        JOIN dbo.tc_order_items oi ON oi.seat_id = s.id WHERE oi.order_id = @order_id AND s.status = 'HELD';
        UPDATE z SET held_quantity = held_quantity - q.quantity, sold_quantity = sold_quantity + q.quantity, version = z.version + 1
        FROM dbo.tc_zones z JOIN (SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_order_items
                                  WHERE order_id = @order_id AND seat_id IS NULL GROUP BY zone_id) q ON q.zone_id = z.id;
        UPDATE dbo.tc_ticket_holds SET status = 'CONSUMED', version = version + 1 WHERE id = @hold_id AND status = 'ACTIVE';
        UPDATE dbo.tc_coupon_redemptions SET status = 'CONSUMED', consumed_at = @now WHERE order_id = @order_id AND status = 'RESERVED';
        UPDATE dbo.tc_orders SET status = 'PAID', accepted_payment_id=@payment_id, paid_at = @now, version = version + 1 WHERE id = @order_id;
        WITH allocations AS (
            SELECT *, ROW_NUMBER() OVER (ORDER BY order_item_id, ticket_ordinal) ordinal
            FROM dbo.fn_AllocateTicketPaidAmounts(@order_id)
        )
        INSERT dbo.tc_tickets(order_item_id, ticket_code, qr_secret_hash, paid_amount)
        SELECT a.order_item_id, i.ticket_code, i.qr_hash, a.paid_amount FROM allocations a JOIN @issued i ON i.ordinal = a.ordinal;
        IF (SELECT SUM(paid_amount) FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE oi.order_id = @order_id) <> @total
            THROW 51399, 'Ticket paid amount allocation mismatch', 1;
        INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('ORDER_PAID', 'ORDER', @order_id, CONCAT(N'{"orderId":"', @order_id, N'"}'), CONCAT('order-paid:', @order_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT CAST('PAID' AS varchar(30)) order_status, @result payment_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp09;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_ApplyRefundResult
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.usp_ApplyRefundResult @refund_id uniqueidentifier, @verified_result nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp11;
    BEGIN TRY
        IF COALESCE(IS_ROLEMEMBER('tc_worker'), 0) <> 1 AND COALESCE(IS_ROLEMEMBER('db_owner'), 0) <> 1
            THROW 51409, 'Worker principal required', 1;
        IF ISJSON(@verified_result) <> 1 THROW 51410, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @request_id uniqueidentifier, @obligation_id uniqueidentifier, @payment_id uniqueidentifier, @purpose varchar(30),
                @receipt uniqueidentifier, @fingerprint binary(32), @old_reference nvarchar(200), @problem varchar(60),
                @amount decimal(19,0), @current varchar(20), @order_id uniqueidentifier,
                @event_id uniqueidentifier, @payment_amount decimal(19,0),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result IS NULL OR @result NOT IN ('SUCCEEDED', 'FAILED', 'UNKNOWN') THROW 51411, 'Unsupported refund result', 1;
        IF @provider_reference IS NULL OR LEN(@provider_reference) = 0 THROW 51415, 'Provider reference is required', 1;

        SELECT @payment_id=payment_id,@obligation_id=refund_id FROM dbo.vw_RefundAttemptLedger WHERE attempt_id=@refund_id;
        IF @payment_id IS NULL THROW 51412, 'Refund not found', 1;
        SELECT @order_id = order_id FROM dbo.tc_payments WHERE id = @payment_id;
        SELECT @event_id = event_id FROM dbo.tc_orders WHERE id = @order_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        SELECT @payment_amount = amount FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK) WHERE id = @payment_id;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@obligation_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_refund_transfer_logs l WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_refund_obligations o WHERE o.id = l.refund_id AND o.payment_id = @payment_id);
        SELECT @request_id = CASE WHEN purpose = 'CUSTOMER_REFUND' THEN refund_id END, @purpose = purpose, @amount = amount,
               @current = status, @old_reference=provider_reference
        FROM dbo.vw_RefundAttemptLedger WHERE attempt_id = @refund_id AND payment_id = @payment_id;
        IF @current IS NULL THROW 51412, 'Refund not found', 1;


        SET @now=SYSUTCDATETIME();
        SET @fingerprint=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@refund_id),'|',@result,'|',@provider_reference));
        SELECT @receipt=id FROM dbo.tc_gateway_refund_receipts WITH(UPDLOCK,HOLDLOCK) WHERE fingerprint=@fingerprint;
        IF @receipt IS NULL BEGIN
            SET @receipt=NEWID();
            INSERT dbo.tc_gateway_refund_receipts(id,refund_id,payment_id,fingerprint,observed_status,provider_reference,verified_payload)
            VALUES(@receipt,@refund_id,@payment_id,@fingerprint,@result,@provider_reference,@verified_result);
        END;
        IF @old_reference IS NOT NULL AND @old_reference<>@provider_reference SET @problem='PROVIDER_REFERENCE_CONFLICT';
        IF @current = 'SUCCEEDED' AND @problem IS NULL BEGIN
            UPDATE dbo.tc_gateway_refund_receipts SET resolution='APPLIED' WHERE id=@receipt;
            SELECT @refund_id refund_id, @current refund_status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @current='FAILED' AND @result='UNKNOWN' AND @problem IS NULL BEGIN
            UPDATE dbo.tc_gateway_refund_receipts SET resolution='IGNORED' WHERE id=@receipt;
            IF @started=1 COMMIT;
            SELECT @refund_id refund_id,@current refund_status; RETURN;
        END;
        IF @current='FAILED' AND @result='SUCCEEDED' AND (@old_reference IS NULL OR @old_reference<>@provider_reference) SET @problem='LATE_SUCCESS_UNMATCHED_REFERENCE';
        IF @current='FAILED' AND @result='SUCCEEDED' AND EXISTS(SELECT 1 FROM dbo.vw_RefundAttemptLedger WHERE attempt_id<>@refund_id AND status IN('PENDING','UNKNOWN','SUCCEEDED') AND ((refund_id=@request_id AND @request_id IS NOT NULL) OR (@purpose='PAYMENT_COMPENSATION' AND payment_id=@payment_id AND purpose=@purpose))) SET @problem='LATE_SUCCESS_WITH_ANOTHER_ATTEMPT';
        IF @result = 'SUCCEEDED' AND
           (SELECT COALESCE(SUM(amount), 0) FROM dbo.vw_RefundAttemptLedger WHERE payment_id = @payment_id AND status = 'SUCCEEDED') + @amount > @payment_amount
            SET @problem='REFUND_EXCEEDS_CAPTURE';

        IF @result = 'SUCCEEDED' AND @purpose = 'CUSTOMER_REFUND' BEGIN
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id;
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_id = @request_id AND oi.zone_id = z.id);
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_id = @request_id AND oi.seat_id = s.id);
            IF EXISTS (
                SELECT 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_tickets rt
                    JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                    WHERE rt.refund_id = @request_id AND oi.seat_id IS NULL AND t.status = 'REFUND_PENDING'
                    GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id WHERE z.sold_quantity < q.quantity)
                SET @problem='INVENTORY_INCONSISTENT';
        END;

        IF @problem IS NOT NULL BEGIN
            UPDATE dbo.tc_gateway_refund_receipts SET resolution='NEEDS_RECONCILIATION',reason=@problem WHERE id=@receipt;
            UPDATE dbo.tc_refund_obligations SET status='NEEDS_RECONCILIATION',updated_at=@now WHERE id=@obligation_id;
            INSERT dbo.tc_audit_logs(action,aggregate_type,aggregate_id,detail) VALUES('REFUND_RECONCILIATION_REQUIRED','REFUND',@refund_id,@problem);
            IF @started=1 COMMIT;
            SELECT @refund_id refund_id,CAST('NEEDS_RECONCILIATION' AS varchar(30)) refund_status; RETURN;
        END;
        UPDATE dbo.tc_gateway_refund_receipts SET resolution='APPLIED' WHERE id=@receipt;
        UPDATE dbo.tc_refund_transfer_logs SET status = @result, result = @result, provider_reference = @provider_reference,
               processed_at = CASE WHEN @result IN ('SUCCEEDED', 'FAILED') THEN @now ELSE processed_at END
        WHERE id = @refund_id;
        UPDATE o SET status = CASE @result WHEN 'SUCCEEDED' THEN 'COMPLETED' WHEN 'FAILED' THEN 'RETRYABLE' ELSE 'NEEDS_RECONCILIATION' END,
               current_attempt_id = l.id, provider_reference = l.provider_reference, processed_at = l.processed_at, updated_at = @now
        FROM dbo.tc_refund_obligations o JOIN dbo.tc_refund_transfer_logs l ON l.refund_id = o.id
        WHERE o.id = @obligation_id AND l.id = @refund_id;

        IF @result = 'SUCCEEDED' AND @purpose = 'CUSTOMER_REFUND' BEGIN
            UPDATE s SET status = 'AVAILABLE', version = s.version + 1
            FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
            JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id AND t.status = 'REFUND_PENDING' AND s.status = 'SOLD';
            UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1
            FROM dbo.tc_zones z JOIN (
                SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_tickets rt
                JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_id = @request_id AND oi.seat_id IS NULL AND t.status = 'REFUND_PENDING'
                GROUP BY oi.zone_id
            ) q ON q.zone_id = z.id;
            UPDATE t SET status = 'REFUNDED', version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id AND t.status = 'REFUND_PENDING';
            UPDATE dbo.tc_refund_tickets SET is_open = 0 WHERE refund_id = @request_id;
            UPDATE dbo.tc_refund_obligations SET version = version + 1 WHERE id = @request_id;
        END;

        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        VALUES (CONCAT('REFUND_', @result), 'REFUND', @refund_id, @provider_reference);
        IF @result = 'SUCCEEDED' INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('REFUND_SUCCEEDED', 'REFUND', @refund_id,
                CONCAT(N'{"refundId":"', @refund_id, N'"}'), CONCAT('refund-succeeded:', @refund_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @refund_id refund_id, @result refund_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp11;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_ApproveOrganization
-- Latest Source Migration: 006_create_procedures.sql
/* Stored Procedure: usp_ApproveOrganization */
/* Canonical names delegate to the proven legacy transaction paths. */
CREATE OR ALTER PROCEDURE dbo.usp_ApproveOrganization
    @organization_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @initial_commission_policy nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM dbo.tc_organization_requests WHERE id = @organization_id)
    BEGIN
        EXEC dbo.usp_ApproveOrganizationRequest
            @request_id = @organization_id,
            @actor_id = @actor_id,
            @initial_commission_policy = @initial_commission_policy;
        RETURN;
    END;

    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 52610, 'Administrator role required', 1;

        DECLARE @status varchar(30), @requester_id uniqueidentifier,
                @rate decimal(7,4), @fixed decimal(19,0),
                @effective_from datetime2(3), @effective_to datetime2(3);
        SELECT @status = status, @requester_id = requester_id
        FROM dbo.tc_organizations WITH (UPDLOCK, HOLDLOCK) WHERE id = @organization_id;
        IF @status IS NULL THROW 52611, 'Organization not found', 1;
        IF @status = 'APPROVED' BEGIN COMMIT; SELECT @organization_id organization_id, @status status; RETURN; END;
        IF @status <> 'PENDING_APPROVAL' THROW 52612, 'Organization is not pending approval', 1;

        SELECT @rate = TRY_CONVERT(decimal(7,4), JSON_VALUE(@initial_commission_policy, '$.ratePercent')),
               @fixed = TRY_CONVERT(decimal(19,0), JSON_VALUE(@initial_commission_policy, '$.fixedFee')),
               @effective_from = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveFrom')),
               @effective_to = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveTo'));
        IF ISJSON(@initial_commission_policy) <> 1 OR @rate IS NULL OR @fixed IS NULL
           OR @effective_from IS NULL OR @effective_to IS NULL
            THROW 52613, 'Invalid initial commission policy', 1;

        INSERT dbo.tc_commission_rules(organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES (@organization_id, @rate, @fixed, @effective_from, @effective_to);
        IF @requester_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships
            WHERE user_id = @requester_id AND organization_id = @organization_id
        )
            INSERT dbo.tc_organization_memberships(user_id, organization_id, role)
            VALUES (@requester_id, @organization_id, 'MANAGER');
        UPDATE dbo.tc_organizations
        SET status = 'APPROVED', decided_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME(), rejection_reason = NULL
        WHERE id = @organization_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'ORGANIZATION_APPROVED', 'ORGANIZATION', @organization_id);
        COMMIT;
        SELECT @organization_id organization_id, CAST('APPROVED' AS varchar(30)) status;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ApproveOrganizationRequest
-- Latest Source Migration: 006_create_procedures.sql
/* Stored Procedure: usp_ApproveOrganizationRequest */
CREATE OR ALTER PROCEDURE dbo.usp_ApproveOrganizationRequest
    @request_id uniqueidentifier, @actor_id uniqueidentifier, @initial_commission_policy nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp01;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51201, 'Administrator role required', 1;
        DECLARE @status varchar(30), @organization_id uniqueidentifier, @applicant_id uniqueidentifier,
                @name nvarchar(200), @email nvarchar(320), @phone nvarchar(30), @description nvarchar(2000);
        SELECT @status = status, @organization_id = organization_id, @applicant_id = applicant_id,
               @name = name, @email = contact_email, @phone = contact_phone, @description = description
        FROM dbo.tc_organization_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;
        IF @status IS NULL THROW 51202, 'Organization request not found', 1;
        IF @status = 'APPROVED' BEGIN
            SELECT @organization_id organization_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> 'PENDING' THROW 51203, 'Organization request is already rejected', 1;
        DECLARE @rate decimal(7,4) = TRY_CONVERT(decimal(7,4), JSON_VALUE(@initial_commission_policy, '$.ratePercent')),
                @fixed decimal(19,0) = TRY_CONVERT(decimal(19,0), JSON_VALUE(@initial_commission_policy, '$.fixedFee')),
                @from datetime2(3) = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveFrom')),
                @to datetime2(3) = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveTo'));
        IF ISJSON(@initial_commission_policy) <> 1 OR @rate IS NULL OR @fixed IS NULL OR @from IS NULL OR @to IS NULL
            THROW 51204, 'Invalid initial commission policy', 1;
        SET @organization_id = NEWID();
        INSERT dbo.tc_organizations(id, name, contact_email, contact_phone, description)
        VALUES (@organization_id, @name, @email, @phone, @description);
        INSERT dbo.tc_commission_rules(organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES (@organization_id, @rate, @fixed, @from, @to);
        INSERT dbo.tc_organization_memberships(user_id, organization_id, role) VALUES (@applicant_id, @organization_id, 'MANAGER');
        UPDATE dbo.tc_organization_requests SET reviewer_id = @actor_id, organization_id = @organization_id,
               status = 'APPROVED', decided_at = SYSUTCDATETIME(), rejection_reason = NULL, version = version + 1
        WHERE id = @request_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'ORGANIZATION_REQUEST_APPROVED', 'ORGANIZATION_REQUEST', @request_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @organization_id organization_id, CAST('APPROVED' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp01;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_AssertOrderAccess
-- Latest Source Migration: 045_order_ticket_access_assertions.sql
CREATE OR ALTER PROCEDURE dbo.usp_AssertOrderAccess
    @order_id uniqueidentifier
WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @session_actor uniqueidentifier = TRY_CONVERT(uniqueidentifier, SESSION_CONTEXT(N'actor_id'));
    IF @session_actor IS NULL
        THROW 51403, 'A current verified owner is required.', 1;

    DECLARE @owner_id uniqueidentifier;
    SELECT @owner_id = user_id FROM dbo.tc_orders WHERE id = @order_id;
    IF @owner_id IS NULL
        THROW 51404, 'Order was not found.', 1;
    IF @owner_id <> @session_actor
        THROW 51403, 'A current verified owner is required.', 1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_AssertTicketAccess
-- Latest Source Migration: 045_order_ticket_access_assertions.sql
CREATE OR ALTER PROCEDURE dbo.usp_AssertTicketAccess
    @ticket_id uniqueidentifier
WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @session_actor uniqueidentifier = TRY_CONVERT(uniqueidentifier, SESSION_CONTEXT(N'actor_id'));
    IF @session_actor IS NULL
        THROW 51403, 'Ticket belongs to another user.', 1;

    DECLARE @owner_id uniqueidentifier;
    SELECT @owner_id = o.user_id
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id
    WHERE t.id = @ticket_id;

    IF @owner_id IS NULL
        THROW 51404, 'Ticket was not found.', 1;
    IF @owner_id <> @session_actor
        THROW 51403, 'Ticket belongs to another user.', 1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_BeginOrderPayment
-- Latest Source Migration: 031_sql_report_financial_safety.sql
/* Stored Procedure: usp_BeginOrderPayment */
CREATE OR ALTER PROCEDURE dbo.usp_BeginOrderPayment @order_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp08;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @hold_id uniqueidentifier, @amount decimal(19,0), @payment_id uniqueidentifier,
                @txn_ref varchar(100), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id=event_id,@hold_id=hold_id FROM dbo.tc_orders WHERE id=@order_id AND user_id=@actor_id;
        IF @event_id IS NULL THROW 51280,'Order cannot begin payment',1;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_ticket_holds WITH(UPDLOCK,HOLDLOCK) WHERE id=@hold_id;
        SET @now=SYSUTCDATETIME();
        SELECT @amount=o.total_amount FROM dbo.tc_orders o WITH(UPDLOCK,HOLDLOCK) JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id
        WHERE o.id=@order_id AND o.user_id=@actor_id AND o.status='PENDING_PAYMENT' AND h.status='ACTIVE' AND h.expires_at>@now;
        IF @amount IS NULL THROW 51280,'Order cannot begin payment',1;
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND status='PUBLISHED' AND @now>=sale_start AND @now<sale_end) THROW 51281,'Event is unavailable for payment',1;
        IF @amount = 0 THROW 51282, 'Zero-value order must use the free completion flow', 1;
        SELECT TOP (1) @payment_id = id, @txn_ref = txn_ref FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK)
        WHERE order_id = @order_id AND status IN ('PENDING', 'UNKNOWN') ORDER BY created_at DESC;
        IF @payment_id IS NULL BEGIN
            SET @payment_id = NEWID(); SET @txn_ref = CONCAT('PAY-', REPLACE(CONVERT(varchar(36), @payment_id), '-', ''));
            INSERT dbo.tc_payments(id, order_id, txn_ref, amount) VALUES (@payment_id, @order_id, @txn_ref, @amount);
        END;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @payment_id payment_id, @txn_ref txn_ref, @amount amount, CAST('VND' AS char(3)) currency;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp08;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_CanManageCouponOrganization
-- Latest Source Migration: 043_coupon_admin.sql
CREATE OR ALTER PROCEDURE dbo.usp_CanManageCouponOrganization
    @actor_id uniqueidentifier, @auth_version int, @organization_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;
    SELECT CAST(CASE WHEN EXISTS(SELECT 1 FROM dbo.tc_users
                                 WHERE id=@actor_id AND auth_version=@auth_version AND status='ACTIVE')
        AND ((ISNULL(IS_ROLEMEMBER('tc_platform_admin'),0)=1 OR ISNULL(IS_ROLEMEMBER('db_owner'),0)=1 OR USER_NAME()='dbo')
             AND EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles
                        WHERE user_id=@actor_id AND role='ADMIN')
             OR @organization_id IS NOT NULL AND (ISNULL(IS_ROLEMEMBER('tc_manager'),0)=1 OR ISNULL(IS_ROLEMEMBER('db_owner'),0)=1 OR USER_NAME()='dbo')
             AND EXISTS(SELECT 1 FROM dbo.tc_organization_memberships
                        WHERE user_id=@actor_id AND organization_id=@organization_id
                          AND role='MANAGER' AND active=1))
        THEN 1 ELSE 0 END AS bit) AS allowed;
END;
GO

-- Object: [PROCEDURE] dbo.usp_CancelEvent
-- Latest Source Migration: 058_khanh06_event_cancellation.sql
/* Preserve canonical entry point, including idempotent repeated cancellation. */
CREATE OR ALTER PROCEDURE dbo.usp_CancelEvent @event_id uniqueidentifier,@actor_id uniqueidentifier
AS BEGIN
 SET NOCOUNT ON;
 EXEC dbo.sp_event_cancel @event_id,@actor_id,N'Event cancelled by administrator',NULL,NULL,1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_CancelHoldRequest
-- Latest Source Migration: 029_hold_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_CancelHoldRequest
    @actor_id uniqueidentifier,@auth_version int,@request_key uniqueidentifier,@fingerprint varchar(64),@hold_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldMutation @actor_id,@auth_version,@request_key,@fingerprint,'CANCEL',@hold_id=@hold_id;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ChangeMemberRole
-- Latest Source Migration: 032_sql_report_permissions.sql
CREATE OR ALTER PROCEDURE dbo.usp_ChangeMemberRole @organization_id uniqueidentifier,@actor_id uniqueidentifier,@user_id uniqueidentifier,@role varchar(30),@active bit=1
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @lock_count bigint;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_organizations WITH(UPDLOCK,HOLDLOCK) WHERE id=@organization_id;
 EXEC dbo.usp_RequireOrganizationManager @actor_id,@organization_id;
 IF @role IS NULL OR @role NOT IN('MANAGER','CHECK_IN_STAFF') OR @active IS NULL THROW 51816,'Invalid organization role',1;
 UPDATE dbo.tc_organization_memberships SET role=@role,active=@active WHERE user_id=@user_id AND organization_id=@organization_id;
 IF @@ROWCOUNT<>1 THROW 51817,'Member not found',1;
 INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail) VALUES(@actor_id,'MEMBER_ROLE_CHANGED','ORGANIZATION',@organization_id,CONVERT(varchar(36),@user_id));
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_CompleteFreeOrder
-- Latest Source Migration: 031_sql_report_financial_safety.sql
CREATE OR ALTER PROCEDURE dbo.usp_CompleteFreeOrder @order_id uniqueidentifier,@actor_id uniqueidentifier,@ticket_codes nvarchar(max)
AS BEGIN SET NOCOUNT ON;
 EXEC dbo.usp_ApplyPaymentResult @order_id=@order_id,@actor_id=@actor_id,@payment_id=NULL,@verified_result=N'{"status":"CAPTURED"}',@ticket_codes=@ticket_codes;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ConfirmSettlement
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER PROCEDURE dbo.usp_ConfirmSettlement @settlement_id uniqueidentifier,@actor_id uniqueidentifier,@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 DECLARE @event_id uniqueidentifier,@status varchar(20),@replay bit=0,@lock_count bigint;
 SELECT @event_id=event_id FROM dbo.tc_settlements WHERE id=@settlement_id;
 IF @event_id IS NULL THROW 52704,'SETTLEMENT_NOT_FOUND',1;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
 SELECT @status=status FROM dbo.tc_settlements WITH(UPDLOCK,HOLDLOCK) WHERE id=@settlement_id;
 IF @status IN('CONFIRMED','PAID') SET @replay=1;
 ELSE BEGIN
 EXEC dbo.usp_RecalculateSettlement @event_id,@actor_id,@auth_version,0;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_outbox WITH(UPDLOCK,HOLDLOCK) WHERE event_type='EVENT_CANCELLED';
 IF EXISTS(SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event_id,SYSUTCDATETIME())) THROW 52709,'SETTLEMENT_BLOCKED',1;
 UPDATE dbo.tc_settlements SET status=CASE WHEN net_payable=0 THEN 'PAID' ELSE 'CONFIRMED' END,confirmed_at=SYSUTCDATETIME(),updated_at=SYSUTCDATETIME() WHERE id=@settlement_id;
 INSERT dbo.vw_VuongAuditLogs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'SETTLEMENT_CONFIRMED','Settlement',CONVERT(varchar(36),@settlement_id),(SELECT @event_id eventId FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 SELECT settlement_id,event_id,status,gross_revenue,total_refund,total_commission,net_payable,paid_amount,pending_amount,available_to_pay,confirmed_at,@replay is_replay FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id=@settlement_id;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_CouponAdminAuthorize
-- Latest Source Migration: 043_coupon_admin.sql
CREATE OR ALTER PROCEDURE dbo.usp_CouponAdminAuthorize
    @actor_id uniqueidentifier, @auth_version int, @organization_id uniqueidentifier=NULL
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM dbo.tc_users
                  WHERE id=@actor_id AND auth_version=@auth_version AND status='ACTIVE')
        THROW 51520,'Current session required',1;
    IF (ISNULL(IS_ROLEMEMBER('tc_platform_admin'),0)=1 OR ISNULL(IS_ROLEMEMBER('db_owner'),0)=1 OR USER_NAME()='dbo')
       AND EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles
                  WHERE user_id=@actor_id AND role='ADMIN') RETURN;
    IF @organization_id IS NOT NULL AND (ISNULL(IS_ROLEMEMBER('tc_manager'),0)=1 OR ISNULL(IS_ROLEMEMBER('db_owner'),0)=1 OR USER_NAME()='dbo')
       AND EXISTS(SELECT 1 FROM dbo.tc_organization_memberships WITH (HOLDLOCK)
                  WHERE user_id=@actor_id AND organization_id=@organization_id
                    AND role='MANAGER' AND active=1) RETURN;
    THROW 51521,'Organization manager or platform admin required',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_CreateCommissionRule
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER PROCEDURE dbo.usp_CreateCommissionRule
 @organization_id uniqueidentifier,@actor_id uniqueidentifier,@idempotency_key uniqueidentifier,@rate_percent decimal(12,4),@fixed_fee decimal(19,0),@effective_from datetime2(3),@effective_to datetime2(3),@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 IF @idempotency_key IS NULL OR @rate_percent IS NULL OR @rate_percent<0 OR @rate_percent>100 OR @fixed_fee IS NULL OR @fixed_fee<0 OR @effective_from IS NULL OR @effective_to IS NULL OR @effective_from>=@effective_to THROW 52702,'COMMISSION_RULE_INVALID',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_organizations WITH(UPDLOCK,HOLDLOCK) WHERE id=@organization_id) THROW 52704,'ORGANIZATION_NOT_FOUND',1;
 DECLARE @fingerprint binary(32)=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@organization_id),'|',@rate_percent,'|',@fixed_fee,'|',CONVERT(varchar(30),@effective_from,126),'|',CONVERT(varchar(30),@effective_to,126))),@rule_id uniqueidentifier,@replay bit=0;
 SELECT @rule_id=resource_id FROM dbo.tc_finance_mutations WITH(UPDLOCK,HOLDLOCK) WHERE idempotency_key=@idempotency_key;
 IF @rule_id IS NOT NULL BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_finance_mutations WHERE idempotency_key=@idempotency_key AND actor_id=@actor_id AND operation='COMMISSION_CREATE' AND fingerprint=@fingerprint) THROW 52709,'IDEMPOTENCY_CONFLICT',1;
 SET @replay=1;
 END ELSE BEGIN
 SET @rule_id=NEWID();
 INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to,created_at,updated_at) VALUES(@rule_id,@organization_id,@rate_percent,@fixed_fee,@effective_from,@effective_to,SYSUTCDATETIME(),SYSUTCDATETIME());
 INSERT dbo.tc_finance_mutations(idempotency_key,actor_id,operation,fingerprint,resource_id,response_json) VALUES(@idempotency_key,@actor_id,'COMMISSION_CREATE',@fingerprint,@rule_id,
 (SELECT @organization_id organizationId,@rate_percent ratePercent,@fixed_fee fixedFee,@effective_from effectiveFrom,@effective_to effectiveTo FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 INSERT dbo.vw_VuongAuditLogs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'COMMISSION_CREATED','CommissionRule',CONVERT(varchar(36),@rule_id),(SELECT @organization_id organizationId,@rate_percent ratePercent,@fixed_fee fixedFee FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 SELECT c.id rule_id,COALESCE(TRY_CONVERT(uniqueidentifier,JSON_VALUE(m.response_json,'$.organizationId')),c.organization_id) organization_id,
 COALESCE(TRY_CONVERT(decimal(12,4),JSON_VALUE(m.response_json,'$.ratePercent')),c.rate_percent) rate_percent,
 COALESCE(TRY_CONVERT(decimal(19,0),JSON_VALUE(m.response_json,'$.fixedFee')),c.fixed_fee) fixed_fee,
 COALESCE(TRY_CONVERT(datetime2(3),JSON_VALUE(m.response_json,'$.effectiveFrom')),c.effective_from) effective_from,
 COALESCE(TRY_CONVERT(datetime2(3),JSON_VALUE(m.response_json,'$.effectiveTo')),c.effective_to) effective_to,
 (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE commission_rule_id=c.id) associated_events_count,@replay is_replay FROM dbo.tc_commission_rules c JOIN dbo.tc_finance_mutations m ON m.resource_id=c.id AND m.idempotency_key=@idempotency_key WHERE c.id=@rule_id;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_CreateHoldRequest
-- Latest Source Migration: 029_hold_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_CreateHoldRequest
    @actor_id uniqueidentifier,@auth_version int,@request_key uniqueidentifier,@fingerprint varchar(64),
    @event_id uniqueidentifier,@selections nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldMutation @actor_id,@auth_version,@request_key,@fingerprint,'CREATE',@event_id,@selections;
END;
GO

-- Object: [PROCEDURE] dbo.usp_CreateManagedCoupon
-- Latest Source Migration: 043_coupon_admin.sql
CREATE OR ALTER PROCEDURE dbo.usp_CreateManagedCoupon
    @organization_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int,
    @code varchar(80), @discount_type varchar(30), @percentage_value decimal(5,2)=NULL,
    @fixed_amount decimal(19,0)=NULL, @max_discount_amount decimal(19,0)=NULL,
    @max_uses int, @valid_from datetime2(3), @valid_to datetime2(3)
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    DECLARE @id uniqueidentifier=NEWID();
    INSERT dbo.tc_coupons(id,organization_id,code,discount_type,percentage_value,fixed_amount,
                          max_discount_amount,max_uses,valid_from,valid_to,active,version)
    VALUES(@id,@organization_id,@code,@discount_type,@percentage_value,@fixed_amount,
           @max_discount_amount,@max_uses,@valid_from,@valid_to,1,0);
    SELECT @id AS id;
END;
GO

-- Object: [PROCEDURE] dbo.usp_CreateOrderFromHold
-- Latest Source Migration: 006_create_procedures.sql
/* Stored Procedure: usp_CreateOrderFromHold */
CREATE OR ALTER PROCEDURE dbo.usp_CreateOrderFromHold @hold_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp06;
    BEGIN TRY
        DECLARE @order_id uniqueidentifier, @event_id uniqueidentifier, @subtotal decimal(19,0), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @order_id = id FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE hold_id = @hold_id;
        IF @order_id IS NOT NULL BEGIN
            SELECT id order_id, order_code, subtotal_amount, discount_amount, total_amount FROM dbo.tc_orders WHERE id = @order_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT @event_id = event_id FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @hold_id AND user_id = @actor_id AND status = 'ACTIVE' AND expires_at > @now;
        IF @event_id IS NULL THROW 51260, 'Active unexpired hold owned by actor required', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id AND status = 'PUBLISHED')
            THROW 51261, 'Event is unavailable', 1;
        SELECT @subtotal = SUM(unit_price * quantity) FROM dbo.tc_ticket_hold_items WHERE hold_id = @hold_id;
        IF @subtotal IS NULL THROW 51262, 'Hold has no items', 1;
        SET @order_id = NEWID();
        INSERT dbo.tc_orders(id, user_id, event_id, hold_id, order_code, subtotal_amount, discount_amount, total_amount)
        VALUES (@order_id, @actor_id, @event_id, @hold_id, CONCAT('ORD-', REPLACE(CONVERT(varchar(36), @order_id), '-', '')), @subtotal, 0, @subtotal);
        INSERT dbo.tc_order_items(order_id, zone_id, seat_id, quantity, unit_price, zone_name_snapshot, seat_label_snapshot)
        SELECT @order_id, hi.zone_id, hi.seat_id, hi.quantity, hi.unit_price, z.name, s.label
        FROM dbo.tc_ticket_hold_items hi JOIN dbo.tc_zones z ON z.id = hi.zone_id LEFT JOIN dbo.tc_seats s ON s.id = hi.seat_id
        WHERE hi.hold_id = @hold_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT id order_id, order_code, subtotal_amount, discount_amount, total_amount FROM dbo.tc_orders WHERE id = @order_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp06;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_CreateOrderRequest
-- Latest Source Migration: 030_order_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_CreateOrderRequest
    @actor_id uniqueidentifier,@auth_version int,@key uniqueidentifier,@fingerprint varchar(64),@hold_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @@TRANCOUNT<>0 THROW 51410,'Facade owns transaction',1;
    BEGIN TRANSACTION;
    BEGIN TRY
        DECLARE @payload nvarchar(max),@saved_fingerprint varchar(200),@saved_target uniqueidentifier,
            @event_id uniqueidentifier,@owner_id uniqueidentifier,@order_id uniqueidentifier,@now datetime2(3),@expires datetime2(3),@subtotal decimal(19,0);
        SELECT @payload=response_payload,@saved_fingerprint=fingerprint,@saved_target=target_id FROM dbo.tc_request_idempotency WITH(UPDLOCK,HOLDLOCK)
            WHERE actor_id=@actor_id AND operation='ORDER_MUTATION' AND idempotency_key=CONVERT(varchar(36),@key);
        SELECT @event_id=event_id,@owner_id=user_id FROM dbo.tc_ticket_holds WHERE id=@hold_id;
        EXEC dbo.usp_HoldLockSelection @actor_id,@event_id,N'[]',@hold_id,@include_active=0;
        EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
        IF @owner_id IS NOT NULL AND @owner_id<>@actor_id THROW 51403,'Hold owner required',1;
        IF @owner_id IS NULL THROW 51400,'Hold not found',1;
        IF EXISTS(SELECT 1 FROM dbo.tc_orders WHERE hold_id=@hold_id AND user_id<>@actor_id) THROW 51403,'Order owner required',1;
        IF @payload IS NOT NULL BEGIN
            IF @saved_fingerprint<>@fingerprint OR @saved_target<>@hold_id THROW 51408,'Changed request for key',1;
            SELECT @expires=expires_at FROM dbo.tc_ticket_holds WHERE id=@hold_id;
            COMMIT;
            SELECT @payload response_payload,CAST(1 AS bit) replayed,CONVERT(varchar(36),@hold_id) hold_id,CONVERT(varchar(23),@expires,126)+'Z' expires_at;
            RETURN;
        END;
        SELECT @order_id=id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE hold_id=@hold_id;
        DECLARE @existing_coupon uniqueidentifier,@payment_dummy int;
        SELECT @existing_coupon=coupon_id FROM dbo.tc_orders WHERE id=@order_id;
        EXEC dbo.usp_OrderLockCoupons @existing_coupon,NULL;
        SELECT @payment_dummy=1 FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@order_id;
        IF EXISTS(SELECT 1 FROM dbo.tc_payments WHERE order_id=@order_id AND status IN('PENDING','UNKNOWN')) THROW 51407,'Payment unresolved',1;
        SET @now=SYSUTCDATETIME();
        SELECT @expires=expires_at FROM dbo.tc_ticket_holds WHERE id=@hold_id AND status='ACTIVE';
        IF @expires IS NULL OR @expires<=@now THROW 51400,'Active unexpired Hold required',1;
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND status='PUBLISHED' AND @now>=sale_start AND @now<sale_end)
            THROW 51406,'Event is not on sale',1;
        SELECT @order_id=id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE hold_id=@hold_id;
        IF @order_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.tc_orders WHERE id=@order_id AND user_id=@actor_id AND status='PENDING_PAYMENT')
            THROW 51406,'Order is terminal',1;
        IF @order_id IS NULL BEGIN
            SELECT @subtotal=SUM(unit_price*quantity) FROM dbo.tc_ticket_hold_items WHERE hold_id=@hold_id;
            IF @subtotal IS NULL THROW 51400,'Hold has no items',1;
            SET @order_id=NEWID();
            INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount)
                VALUES(@order_id,@actor_id,@event_id,@hold_id,CONCAT('ORD-',REPLACE(CONVERT(varchar(36),@order_id),'-','')),@subtotal,0,@subtotal);
            INSERT dbo.tc_order_items(order_id,zone_id,seat_id,quantity,unit_price,zone_name_snapshot,seat_label_snapshot)
                SELECT @order_id,i.zone_id,i.seat_id,i.quantity,i.unit_price,z.name,s.label
                FROM dbo.tc_ticket_hold_items i JOIN dbo.tc_zones z ON z.id=i.zone_id LEFT JOIN dbo.tc_seats s ON s.id=i.seat_id WHERE i.hold_id=@hold_id;
        END;
        EXEC dbo.usp_OrderSnapshot @order_id,@payload=@payload OUTPUT;
        INSERT dbo.tc_request_idempotency(actor_id,operation,idempotency_key,target_id,fingerprint,response_payload,expires_at)
            VALUES(@actor_id,'ORDER_MUTATION',CONVERT(varchar(36),@key),@hold_id,@fingerprint,@payload,DATEADD(second,600,@now));
        COMMIT;
        SELECT @payload response_payload,CAST(0 AS bit) replayed,CONVERT(varchar(36),@hold_id) hold_id,CONVERT(varchar(23),@expires,126)+'Z' expires_at;
    END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_CreateRefundRequest
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.usp_CreateRefundRequest
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @ticket_ids nvarchar(max), @reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp05;
    BEGIN TRY
        IF ISJSON(@ticket_ids) <> 1 OR NULLIF(LTRIM(RTRIM(@reason)), N'') IS NULL THROW 51250, 'Ticket list and reason are required', 1;
        DECLARE @tickets TABLE(id uniqueidentifier NOT NULL PRIMARY KEY);
        INSERT @tickets SELECT TRY_CONVERT(uniqueidentifier, value) FROM OPENJSON(@ticket_ids);
        IF NOT EXISTS (SELECT 1 FROM @tickets) OR EXISTS (SELECT 1 FROM @tickets WHERE id IS NULL) THROW 51251, 'Invalid ticket list', 1;
        IF (SELECT COUNT(*) FROM OPENJSON(@ticket_ids)) <> (SELECT COUNT(*) FROM @tickets) THROW 51252, 'Duplicate ticket id', 1;
        DECLARE @event_id uniqueidentifier, @start datetime2(3), @request_id uniqueidentifier = NEWID(), @amount decimal(19,0);
        SELECT @event_id = o.event_id, @start = e.start_time FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_events e WITH (UPDLOCK, HOLDLOCK) ON e.id = o.event_id WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = 'PAID';
        IF @event_id IS NULL THROW 51253, 'Paid order owned by actor required', 1;
        IF SYSUTCDATETIME() >= @start THROW 51254, 'Refund requests close at event start', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN @tickets x ON x.id = t.id;
        IF EXISTS (
            SELECT 1 FROM @tickets x LEFT JOIN dbo.tc_tickets t ON t.id = x.id
            LEFT JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE t.id IS NULL OR oi.order_id <> @order_id OR t.status <> 'ACTIVE'
               OR EXISTS (SELECT 1 FROM dbo.tc_refund_tickets rt WHERE rt.ticket_id = x.id AND rt.is_open = 1)
        ) THROW 51255, 'One or more tickets are not refundable', 1;
        SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        INSERT dbo.tc_refund_obligations(id, order_id, payment_id, amount, purpose, reason_type, reason, status, requester_id)
        SELECT @request_id, o.id, (SELECT p.id FROM dbo.tc_payments p WHERE p.id = o.accepted_payment_id AND p.status = 'CAPTURED'),
               @amount, 'CUSTOMER_REFUND', 'CUSTOMER_REQUEST', @reason, 'REQUESTED', @actor_id
        FROM dbo.tc_orders o WHERE o.id = @order_id;
        INSERT dbo.tc_refund_tickets(refund_id, ticket_id) SELECT @request_id, id FROM @tickets;
        UPDATE t SET status = 'REFUND_PENDING', version = version + 1 FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'REFUND_REQUEST_CREATED', 'REFUND_REQUEST', @request_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @request_id request_id, @amount requested_amount;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp05;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_CreateTicketHold
-- Latest Source Migration: 041_hold_refund_runtime_compatibility.sql
CREATE OR ALTER PROCEDURE dbo.usp_CreateTicketHold
    @user_id uniqueidentifier, @event_id uniqueidentifier, @selections nvarchar(max), @result_hold uniqueidentifier=NULL OUTPUT, @suppress_result bit=0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp02;
    BEGIN TRY
        DECLARE @now datetime2(3) = SYSUTCDATETIME(), @hold_id uniqueidentifier = NEWID(), @expires datetime2(3);
        IF ISJSON(@selections) <> 1 THROW 51220, 'Selections must be a JSON array', 1;
        DECLARE @app_result int,@resource nvarchar(255)=CONCAT('hold-user:',CONVERT(varchar(36),@user_id)),@lock_event uniqueidentifier;
        EXEC @app_result=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
        IF @app_result<0 THROW 51830,'Hold user lock unavailable; retry',1;
        DECLARE event_locks CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM dbo.tc_events WHERE id=@event_id OR id IN(SELECT event_id FROM dbo.tc_ticket_holds WHERE user_id=@user_id AND status='ACTIVE') ORDER BY id;
        OPEN event_locks; FETCH NEXT FROM event_locks INTO @lock_event;
        WHILE @@FETCH_STATUS=0 BEGIN
            SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@lock_event;
            FETCH NEXT FROM event_locks INTO @lock_event;
        END;
        CLOSE event_locks; DEALLOCATE event_locks;
        SET @now=SYSUTCDATETIME();
        DECLARE @items TABLE(row_no int NOT NULL, zone_id uniqueidentifier NULL, seat_id uniqueidentifier NULL, quantity int NULL);
        INSERT @items SELECT CONVERT(int, [key]), TRY_CONVERT(uniqueidentifier, JSON_VALUE(value, '$.zoneId')),
                             TRY_CONVERT(uniqueidentifier, JSON_VALUE(value, '$.seatId')),
                             TRY_CONVERT(int, JSON_VALUE(value, '$.quantity')) FROM OPENJSON(@selections);
        IF NOT EXISTS (SELECT 1 FROM @items) OR (SELECT SUM(quantity) FROM @items) NOT BETWEEN 1 AND 8
            THROW 51221, 'A hold must contain between 1 and 8 tickets', 1;
        IF EXISTS (SELECT 1 FROM @items WHERE zone_id IS NULL OR quantity IS NULL OR quantity <= 0)
            THROW 51222, 'Invalid hold selection', 1;
        IF EXISTS (SELECT seat_id FROM @items WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING COUNT(*) > 1)
            THROW 51223, 'Duplicate seat selection', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @user_id AND status = 'ACTIVE' AND email_verified_at IS NOT NULL)
            THROW 51224, 'Active verified user required', 1;
        DECLARE @old_hold uniqueidentifier, @old_expires datetime2(3);
        SELECT @old_hold = id, @old_expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK)
        WHERE user_id = @user_id AND status = 'ACTIVE';
        IF @old_hold IS NOT NULL AND @old_expires > @now THROW 51225, 'User already has an active hold', 1;
        IF @old_hold IS NOT NULL EXEC dbo.usp_ReleaseTicketHold @old_hold, NULL, 'EXPIRED', @suppress_result=1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = 'PUBLISHED' AND @now >= sale_start AND @now < sale_end)
            THROW 51226, 'Event is not on sale', 1;
        IF EXISTS (
            SELECT 1 FROM @items i LEFT JOIN dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) ON z.id = i.zone_id
            WHERE z.id IS NULL OR z.event_id <> @event_id OR (z.type = 'SEATED' AND (i.seat_id IS NULL OR i.quantity <> 1))
               OR (z.type = 'STANDING' AND i.seat_id IS NOT NULL)
        ) THROW 51227, 'Selection does not match event zone type', 1;
        IF EXISTS (
            SELECT 1 FROM @items i JOIN dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) ON s.id = i.seat_id
            WHERE i.seat_id IS NOT NULL AND (s.zone_id <> i.zone_id OR s.status <> 'AVAILABLE')
        ) OR EXISTS (SELECT 1 FROM @items i WHERE i.seat_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.tc_seats WHERE id = i.seat_id))
            THROW 51228, 'Seat is unavailable', 1;
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (SELECT zone_id, SUM(quantity) q FROM @items WHERE seat_id IS NULL GROUP BY zone_id) i ON i.zone_id = z.id
            WHERE z.capacity - z.held_quantity - z.sold_quantity < i.q
        ) THROW 51229, 'Zone capacity is unavailable', 1;
        SET @expires = DATEADD(minute, 10, @now);
        INSERT dbo.tc_ticket_holds(id, user_id, event_id, created_at, expires_at) VALUES (@hold_id, @user_id, @event_id, @now, @expires);
        INSERT dbo.tc_ticket_hold_items(hold_id, zone_id, seat_id, quantity, unit_price)
        SELECT @hold_id, i.zone_id, i.seat_id, i.quantity, z.price FROM @items i JOIN dbo.tc_zones z ON z.id = i.zone_id;
        UPDATE s SET status = 'HELD', version = version + 1 FROM dbo.tc_seats s JOIN @items i ON i.seat_id = s.id;
        UPDATE z SET held_quantity = held_quantity + i.q, version = version + 1 FROM dbo.tc_zones z
        JOIN (SELECT zone_id, SUM(quantity) q FROM @items WHERE seat_id IS NULL GROUP BY zone_id) i ON i.zone_id = z.id;
        IF @started = 1 COMMIT TRANSACTION;
        SET @result_hold=@hold_id; IF @suppress_result=0 SELECT @hold_id hold_id, @expires expires_at;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp02;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_DecideRefund
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.usp_DecideRefund
    @refund_id uniqueidentifier,
    @actor_id uniqueidentifier = NULL,
    @decision varchar(20),
    @rejection_reason nvarchar(2000) = NULL,
    @attempt_id uniqueidentifier = NULL,
    @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @request_id uniqueidentifier =
        (SELECT id FROM dbo.tc_refund_obligations WHERE id = @refund_id AND purpose = 'CUSTOMER_REFUND');
    IF @request_id IS NULL THROW 52630, 'Refund obligation not found', 1;
    EXEC dbo.usp_DecideRefundRequest
        @request_id = @request_id,
        @actor_id = @actor_id,
        @decision = @decision,
        @rejection_reason = @rejection_reason,
        @retry_failed = @retry_failed;
END;
GO

-- Object: [PROCEDURE] dbo.usp_DecideRefundRequest
-- Latest Source Migration: 006_create_procedures.sql
/* Stored Procedure: usp_DecideRefundRequest */
CREATE OR ALTER PROCEDURE dbo.usp_DecideRefundRequest
    @request_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @decision varchar(20),
    @rejection_reason nvarchar(1000) = NULL,
    @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_ReviewRefundRequest @request_id, @actor_id, @decision, @rejection_reason, @retry_failed;
END;
GO

-- Object: [PROCEDURE] dbo.usp_DeleteManagedCoupon
-- Latest Source Migration: 043_coupon_admin.sql
CREATE OR ALTER PROCEDURE dbo.usp_DeleteManagedCoupon
    @coupon_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    BEGIN TRANSACTION;
    BEGIN TRY
        DECLARE @organization_id uniqueidentifier;
        SELECT @organization_id=organization_id FROM dbo.tc_coupons WITH (UPDLOCK,HOLDLOCK) WHERE id=@coupon_id;
        IF @organization_id IS NULL THROW 51523,'Coupon not found',1;
        EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
        IF EXISTS(SELECT 1 FROM dbo.tc_coupon_redemptions WITH (UPDLOCK,HOLDLOCK) WHERE coupon_id=@coupon_id)
           OR EXISTS(SELECT 1 FROM dbo.tc_orders WITH (UPDLOCK,HOLDLOCK) WHERE coupon_id=@coupon_id)
            THROW 51524,'Coupon is in use',1;
        DELETE dbo.tc_coupons WHERE id=@coupon_id AND organization_id=@organization_id;
        COMMIT TRANSACTION;
    END TRY BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventAuthorize
-- Latest Source Migration: 047_event_authority_lock_order.sql
CREATE OR ALTER PROCEDURE dbo.usp_EventAuthorize
 @actor_id uniqueidentifier,@actor_version int,@admin bit,
 @organization_id uniqueidentifier OUTPUT,@event_id uniqueidentifier OUTPUT,@zone_id uniqueidentifier=NULL,@lock_authority bit=1
AS
BEGIN
 SET NOCOUNT ON;
 IF @zone_id IS NOT NULL BEGIN
  DECLARE @zone_event uniqueidentifier;
  SELECT @zone_event=event_id FROM dbo.tc_zones WHERE id=@zone_id;
  IF @zone_event IS NULL THROW 52004,'Zone not found',1;
  IF @event_id IS NOT NULL AND @event_id<>@zone_event THROW 52003,'Wrong event scope',1;
  SET @event_id=@zone_event;
 END;
 IF @event_id IS NOT NULL BEGIN
  DECLARE @owner uniqueidentifier;
  IF @lock_authority=1 SELECT @owner=organization_id FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
  ELSE SELECT @owner=organization_id FROM dbo.tc_events WITH(READCOMMITTEDLOCK) WHERE id=@event_id;
  IF @owner IS NULL THROW 52004,'Event not found',1;
  IF @organization_id IS NOT NULL AND @organization_id<>@owner THROW 52003,'Wrong organization scope',1;
  SET @organization_id=@owner;
 END;
 -- An Event-scoped operation locks Event before retaining current authority.
 -- Unscoped/preflight checks retain no User lock before later Event discovery.
 IF @lock_authority=1 AND @event_id IS NOT NULL BEGIN
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND auth_version=@actor_version)
   THROW 52001,'Current session is not valid',1;
 END ELSE IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(READCOMMITTEDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND auth_version=@actor_version)
  THROW 52001,'Current session is not valid',1;
 IF @admin=1 BEGIN
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(READCOMMITTEDLOCK) WHERE id=@actor_id AND platform_role='ADMIN')
   THROW 52003,'Administrator role required',1;
 END ELSE IF @organization_id IS NOT NULL BEGIN
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_organization_memberships m WITH(HOLDLOCK)
   JOIN dbo.tc_organizations o ON o.id=m.organization_id
   WHERE m.user_id=@actor_id AND m.organization_id=@organization_id AND m.role='MANAGER' AND m.active=1 AND o.status='APPROVED')
   THROW 52003,'Current manager membership required',1;
 END;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventCommand
-- Latest Source Migration: 051_event_single_image_url.sql
CREATE OR ALTER PROCEDURE dbo.usp_EventCommand
 @actor_id uniqueidentifier,@actor_version int,@scope varchar(30),@idempotency_key varchar(128),@request_hash char(64),
 @operation varchar(30),@organization_id uniqueidentifier=NULL,@event_id uniqueidentifier=NULL,@zone_id uniqueidentifier=NULL,
 @payload nvarchar(max)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END,@old_actor sql_variant=SESSION_CONTEXT(N'actor_id');
 IF @started=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_event_command;
 BEGIN TRY
  IF @payload IS NULL OR ISJSON(@payload)<>1 OR @idempotency_key IS NULL OR LEN(@idempotency_key) NOT BETWEEN 1 AND 128 OR @scope IS NULL OR @scope NOT IN('EVENT','EVENT_REVIEW') OR @request_hash IS NULL THROW 52000,'Invalid command',1;
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@actor_id;
  DECLARE @admin bit=CASE WHEN @scope='EVENT_REVIEW' THEN 1 ELSE 0 END,@stored_org uniqueidentifier,@stored_event uniqueidentifier;
  EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@stored_org OUTPUT,@stored_event OUTPUT,@lock_authority=0;
  DECLARE @lock_result int,@resource nvarchar(255)=CONCAT('tc:event:command:',@actor_id,':',@scope,':',CONVERT(varchar(64),HASHBYTES('SHA2_256',@idempotency_key),2));
  EXEC @lock_result=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
  IF @lock_result<0 THROW 52009,'Command already processing',1;
  SELECT @stored_org=organization_id FROM dbo.tc_event_command_receipts WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key;
  IF @stored_org IS NOT NULL BEGIN
   SET @stored_event=NULL;
   EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@stored_org OUTPUT,@stored_event OUTPUT;
   IF EXISTS(SELECT 1 FROM dbo.tc_event_command_receipts WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key AND request_hash<>@request_hash)
    THROW 52009,'Idempotency payload conflict',1;
   EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor;
   IF @started=1 COMMIT;
   SELECT status_code,response_json,organization_id,event_id,cleanup_json FROM dbo.tc_event_command_receipts WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key; RETURN;
  END;
  EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@organization_id OUTPUT,@event_id OUTPUT,@zone_id;
  IF @organization_id IS NULL THROW 52003,'Organization scope required',1;
  IF (@admin=1 AND @operation NOT IN('PUBLISH','REJECT')) OR (@admin=0 AND @operation IN('PUBLISH','REJECT')) THROW 52003,'Wrong command scope',1;
  DECLARE @status varchar(30),@version bigint,@now datetime2(3)=SYSUTCDATETIME();
  IF @event_id IS NOT NULL SELECT @status=status,@version=version FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
  IF @event_id IS NOT NULL AND @status IS NULL THROW 52004,'Event was deleted',1;
  DECLARE @expected bigint=TRY_CONVERT(bigint,JSON_VALUE(@payload,'$.expectedVersion'));
  IF @expected IS NOT NULL AND @expected<>@version THROW 52009,'Event changed since it was read',1;
  DECLARE @title nvarchar(250)=JSON_VALUE(@payload,'$.title'),@description nvarchar(max)=(SELECT [value] FROM OPENJSON(@payload) WHERE [key]='description'),
   @venue nvarchar(250)=JSON_VALUE(@payload,'$.venueName'),@address nvarchar(500)=JSON_VALUE(@payload,'$.venueAddress'),
   @category uniqueidentifier=TRY_CONVERT(uniqueidentifier,JSON_VALUE(@payload,'$.categoryId')),
   @sale_start datetime2(3)=TRY_CONVERT(datetime2(3),JSON_VALUE(@payload,'$.saleStartTime'),127),
   @sale_end datetime2(3)=TRY_CONVERT(datetime2(3),JSON_VALUE(@payload,'$.saleEndTime'),127),
   @start datetime2(3)=TRY_CONVERT(datetime2(3),JSON_VALUE(@payload,'$.startTime'),127),
   @end datetime2(3)=TRY_CONVERT(datetime2(3),JSON_VALUE(@payload,'$.endTime'),127),
   @response nvarchar(max),@cleanup_json nvarchar(max)=NULL,@code int=200,@cover nvarchar(2048)=JSON_VALUE(@payload,'$.coverImageUrl');
  IF @operation IN('CREATE','EDIT') AND @category IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.tc_event_categories WHERE id=@category)
   THROW 52000,'Unknown event category',1;
  IF @operation='CREATE' BEGIN
   IF @admin=1 OR @event_id IS NOT NULL OR @sale_start IS NULL OR @sale_end IS NULL OR @start IS NULL OR @end IS NULL OR @sale_start<=@now OR @title IS NULL OR LEN(@title) NOT BETWEEN 5 AND 200 THROW 52000,'Invalid draft',1;
   IF NOT(@sale_start<@sale_end AND @sale_end<=@start AND @start<@end) OR @category IS NULL OR @description IS NULL OR LEN(@description) NOT BETWEEN 1 AND 5000 OR @venue IS NULL OR LEN(@venue)=0 OR @address IS NULL OR LEN(@address)=0 THROW 52000,'Invalid event fields or schedule',1;
   SET @event_id=NEWID(); SET @status='DRAFT'; SET @code=201;
   INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status)
    VALUES(@event_id,@organization_id,@category,@title,@description,@venue,@address,@sale_start,@sale_end,@start,@end,@status);
  END ELSE IF @operation='EDIT' BEGIN
   IF @status NOT IN('DRAFT','REJECTED','PUBLISHED') THROW 52009,'Event is locked',1;
   IF @status='PUBLISHED' AND EXISTS(SELECT 1 FROM OPENJSON(@payload) WHERE [key] NOT IN('description','expectedVersion')) THROW 52009,'Only description may change after publication',1;
   IF (@description IS NOT NULL AND LEN(@description) NOT BETWEEN 1 AND 5000) OR (@title IS NOT NULL AND LEN(@title) NOT BETWEEN 5 AND 200) THROW 52000,'Invalid event text',1;
   UPDATE dbo.tc_events SET title=COALESCE(@title,title),description=COALESCE(@description,description),category_id=COALESCE(@category,category_id),
    venue_name=COALESCE(@venue,venue_name),venue_address=COALESCE(@address,venue_address),sale_start=COALESCE(@sale_start,sale_start),
    sale_end=COALESCE(@sale_end,sale_end),start_time=COALESCE(@start,start_time),end_time=COALESCE(@end,end_time),version=version+1 WHERE id=@event_id;
  END ELSE IF @operation='DELETE' BEGIN
   IF @status<>'DRAFT' OR EXISTS(SELECT 1 FROM dbo.tc_orders WHERE event_id=@event_id) OR EXISTS(SELECT 1 FROM dbo.tc_ticket_holds WHERE event_id=@event_id) THROW 52009,'Draft cannot be deleted',1;
   SET @cleanup_json=NULLIF((SELECT event_image_public_id publicId,event_image coverImageUrl FROM dbo.tc_events
    WHERE id=@event_id AND event_image_public_id IS NOT NULL FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),'');
   DELETE s FROM dbo.tc_seats s JOIN dbo.tc_zones z ON z.id=s.zone_id WHERE z.event_id=@event_id;
   DELETE dbo.tc_zones WHERE event_id=@event_id; DELETE dbo.tc_events WHERE id=@event_id; SET @status='DELETED';
  END ELSE IF @operation='SUBMIT' BEGIN
   IF @status='PENDING_APPROVAL' BEGIN SET @status='PENDING_APPROVAL'; END
   ELSE BEGIN
    IF @status NOT IN('DRAFT','REJECTED') THROW 52009,'Event cannot be submitted',1;
    IF NOT EXISTS(SELECT 1 FROM dbo.vw_ZoneInventory WHERE event_id=@event_id AND capacity>0) THROW 52000,'Event requires inventory',1;
    IF EXISTS(SELECT 1 FROM dbo.vw_ZoneInventory WHERE event_id=@event_id AND capacity<=0) THROW 52000,'All zones require inventory',1;
    IF EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND start_time<=@now) THROW 52000,'Event schedule has expired',1;
    UPDATE dbo.tc_events SET status='PENDING_APPROVAL',rejection_reason=NULL,version=version+1 WHERE id=@event_id; SET @status='PENDING_APPROVAL';
   END;
  END ELSE IF @operation IN('ZONE_CREATE','ZONE_EDIT','ZONE_DELETE') BEGIN
   IF @status NOT IN('DRAFT','REJECTED','PUBLISHED') THROW 52009,'Event inventory is locked',1;
   IF @status='PUBLISHED' AND (@operation<>'ZONE_EDIT' OR EXISTS(SELECT 1 FROM OPENJSON(@payload) WHERE [key] NOT IN('basePrice','expectedVersion'))) THROW 52009,'Published layout is immutable',1;
   DECLARE @type varchar(20)=JSON_VALUE(@payload,'$.zoneType'),@name nvarchar(120)=JSON_VALUE(@payload,'$.name'),
    @price decimal(19,0)=TRY_CONVERT(decimal(19,0),JSON_VALUE(@payload,'$.basePrice')),
    @capacity int=TRY_CONVERT(int,JSON_VALUE(@payload,'$.capacity')),@rows int=TRY_CONVERT(int,JSON_VALUE(@payload,'$.rows')),
    @per int=TRY_CONVERT(int,JSON_VALUE(@payload,'$.seatsPerRow')),@old_type varchar(20),@occupied bigint;
   IF @zone_id IS NOT NULL SELECT @old_type=type,@occupied=held_quantity+sold_quantity FROM dbo.tc_zones WITH(UPDLOCK,HOLDLOCK) WHERE id=@zone_id AND event_id=@event_id;
   IF @operation<>'ZONE_CREATE' AND @old_type IS NULL THROW 52004,'Zone not found',1;
   IF @operation='ZONE_DELETE' BEGIN
    IF @occupied>0 OR EXISTS(SELECT 1 FROM dbo.tc_seats WHERE zone_id=@zone_id AND status<>'AVAILABLE') THROW 52009,'Zone has allocated inventory',1;
    DELETE dbo.tc_seats WHERE zone_id=@zone_id; DELETE dbo.tc_zones WHERE id=@zone_id;
   END ELSE BEGIN
    IF @price IS NULL OR @price<0 THROW 52000,'Invalid price',1;
    IF @operation='ZONE_CREATE' BEGIN
     IF @name IS NULL OR LEN(@name) NOT BETWEEN 1 AND 120 OR @type IS NULL OR @type NOT IN('SEATED','STANDING') THROW 52000,'Invalid Zone',1;
     IF (@type='SEATED' AND (@rows IS NULL OR @per IS NULL OR @capacity IS NOT NULL))
       OR (@type='STANDING' AND (@capacity IS NULL OR @capacity NOT BETWEEN 1 AND 10000000 OR @rows IS NOT NULL OR @per IS NOT NULL)) THROW 52000,'Zone fields do not match type',1;
     SET @zone_id=NEWID(); SET @code=201;
     INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES(@zone_id,@event_id,@name,@type,@price,CASE WHEN @type='STANDING' THEN @capacity END);
    END ELSE BEGIN
     IF @type IS NOT NULL AND @type<>@old_type THROW 52009,'Zone type cannot change',1;
     SET @type=@old_type;
     IF @capacity IS NOT NULL AND (@type<>'STANDING' OR @capacity NOT BETWEEN 1 AND 10000000 OR @capacity<@occupied) THROW 52009,'Capacity below allocated inventory',1;
     UPDATE dbo.tc_zones SET name=COALESCE(@name,name),price=@price,capacity=COALESCE(@capacity,capacity),version=version+1 WHERE id=@zone_id;
    END;
    IF @rows IS NOT NULL OR @per IS NOT NULL BEGIN
     IF @type<>'SEATED' OR @rows IS NULL OR @per IS NULL OR @rows NOT BETWEEN 1 AND 1000 OR @per NOT BETWEEN 1 AND 1000 OR CONVERT(bigint,@rows)*@per>10000 THROW 52000,'Invalid seat matrix',1;
     IF EXISTS(SELECT 1 FROM dbo.tc_seats WHERE zone_id=@zone_id AND status<>'AVAILABLE') THROW 52009,'Allocated seat layout is immutable',1;
     DELETE dbo.tc_seats WHERE zone_id=@zone_id;
     DECLARE @row int=1,@seat int,@label nvarchar(20),@n int;
     WHILE @row<=@rows BEGIN
      SET @n=@row; SET @label=''; WHILE @n>0 BEGIN SET @label=CHAR(65+(@n-1)%26)+@label; SET @n=(@n-1)/26; END;
      SET @seat=1; WHILE @seat<=@per BEGIN
       INSERT dbo.tc_seats(zone_id,row_name,seat_number,label) VALUES(@zone_id,@label,@seat,CONCAT(@label,@seat)); SET @seat+=1;
      END; SET @row+=1;
     END;
    END;
   END;
   UPDATE dbo.tc_events SET version=version+1 WHERE id=@event_id;
  END ELSE IF @operation='COVER' BEGIN
   IF @status NOT IN('DRAFT','REJECTED') THROW 52009,'Event media is locked',1;
   IF @cover IS NULL OR @cover NOT LIKE 'https://%' OR JSON_VALUE(@payload,'$.publicId') IS NULL THROW 52000,'Invalid storage metadata',1;
   UPDATE dbo.tc_events SET event_image=@cover,event_image_public_id=JSON_VALUE(@payload,'$.publicId'),version=version+1 WHERE id=@event_id;
  END ELSE IF @operation='PUBLISH' BEGIN
   DECLARE @rule uniqueidentifier=TRY_CONVERT(uniqueidentifier,JSON_VALUE(@payload,'$.commissionRuleId'));
   IF @rule IS NULL THROW 52000,'Commission rule required',1;
   IF @status<>'PUBLISHED' AND NOT EXISTS(SELECT 1 FROM dbo.tc_commission_rules r JOIN dbo.tc_events e ON e.organization_id=r.organization_id
    WHERE e.id=@event_id AND r.id=@rule AND r.effective_from<=e.start_time AND r.effective_to>e.start_time) THROW 52000,'Rule does not cover event date',1;
   EXEC dbo.usp_PublishEvent @event_id,@actor_id,@rule; SET @status='PUBLISHED';
   IF NULLIF(JSON_VALUE(@payload,'$.adminNote'),'') IS NOT NULL
    INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail) VALUES(@actor_id,'EVENT_REVIEW_NOTE','EVENT',@event_id,JSON_VALUE(@payload,'$.adminNote'));
  END ELSE IF @operation='REJECT' BEGIN
   DECLARE @reason nvarchar(500)=JSON_VALUE(@payload,'$.reason');
   IF LEN(LTRIM(RTRIM(@reason))) NOT BETWEEN 10 AND 500 OR @reason IS NULL THROW 52000,'Rejection reason required',1;
   IF @status='REJECTED' AND EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND rejection_reason=@reason) SET @status='REJECTED';
   ELSE BEGIN
    IF @status<>'PENDING_APPROVAL' THROW 52009,'Event is not pending approval',1;
    UPDATE dbo.tc_events SET status='REJECTED',rejection_reason=@reason,version=version+1 WHERE id=@event_id; SET @status='REJECTED';
   END;
  END ELSE THROW 52000,'Unsupported operation',1;
  IF @operation NOT IN('SUBMIT','PUBLISH','REJECT')
   INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail) VALUES(@actor_id,CONCAT('EVENT_',@operation),'EVENT',@event_id,NULL);
  SET @response=CONCAT('{"data":',(SELECT LOWER(CONVERT(varchar(36),@event_id)) eventId,LOWER(CONVERT(varchar(36),@organization_id)) organizationId,LOWER(CONVERT(varchar(36),@zone_id)) zoneId,@status status,@cover coverImageUrl FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES),'}');
  INSERT dbo.tc_event_command_receipts(actor_id,scope,idempotency_key,request_hash,organization_id,event_id,status_code,response_json,cleanup_json)
   VALUES(@actor_id,@scope,@idempotency_key,@request_hash,@organization_id,@event_id,@code,@response,@cleanup_json);
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor;
  IF @started=1 COMMIT;
  SELECT @code status_code,@response response_json,@organization_id organization_id,@event_id event_id,@cleanup_json cleanup_json;
 END TRY BEGIN CATCH
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor;
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK;
  ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_event_command;
  THROW;
 END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventImageAuthorize
-- Latest Source Migration: 055_event_image_procedures.sql
CREATE OR ALTER PROCEDURE dbo.usp_EventImageAuthorize
 @actor_id uniqueidentifier,@auth_version int,@organization_id uniqueidentifier,@maintenance bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @maintenance=1 BEGIN
  IF NOT(USER_NAME()='dbo' OR IS_ROLEMEMBER('db_owner')=1 OR IS_SRVROLEMEMBER('sysadmin')=1)
   THROW 52706,'Database owner connection is required for maintenance',1;
  RETURN;
 END;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND auth_version=@auth_version)
  THROW 52703,'Current session is no longer valid',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_organization_memberships WITH(UPDLOCK,HOLDLOCK)
               WHERE user_id=@actor_id AND organization_id=@organization_id AND active=1 AND role='MANAGER')
  THROW 52704,'Current Manager membership is required',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_organizations WITH(HOLDLOCK) WHERE id=@organization_id AND status='APPROVED')
  THROW 52705,'Organization is not approved',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventImageCheckTarget
-- Latest Source Migration: 055_event_image_procedures.sql
CREATE OR ALTER PROCEDURE dbo.usp_EventImageCheckTarget
 @operation varchar(10),@event_id uniqueidentifier,@expected_version bigint,@has_fields bit,@category_id uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @has_fields=1 AND NOT EXISTS(SELECT 1 FROM dbo.tc_event_categories WHERE id=@category_id)
  THROW 52710,'Unknown category',1;
 IF @operation='CREATE' RETURN;
 DECLARE @status varchar(30),@version bigint;
 SELECT @status=status,@version=version FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
 IF @status IS NULL THROW 52701,'Event was not found',1;
 IF @version<>@expected_version THROW 52711,'Event has changed; reload before saving',1;
 IF @operation='EDIT' AND @status NOT IN('DRAFT','REJECTED') THROW 52712,'Only draft or rejected Events can be edited',1;
 IF @operation='COVER' AND @status NOT IN('DRAFT','REJECTED','PUBLISHED') THROW 52713,'Event cover cannot be changed in this state',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventImageClaim
-- Latest Source Migration: 055_event_image_procedures.sql
/* Claims (or re-claims, or replays) one idempotent save request. Returns one row; result_json is set only for a replay. */
CREATE OR ALTER PROCEDURE dbo.usp_EventImageClaim
 @actor_id uniqueidentifier,@auth_version int,@maintenance bit,@request_id uniqueidentifier,@fingerprint char(64),
 @operation varchar(10),@target_id uniqueidentifier,@expected_version bigint,@has_fields bit,@category_id uniqueidentifier,@has_image bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @row_actor uniqueidentifier=CASE WHEN @maintenance=1 THEN CAST('00000000-0000-0000-0000-000000000000' AS uniqueidentifier) ELSE @actor_id END;
 DECLARE @event_id uniqueidentifier,@organization_id uniqueidentifier,@token uniqueidentifier,@public_id varchar(255);
 IF @operation='CREATE' SELECT @event_id=NEWID(),@organization_id=@target_id;
 ELSE BEGIN
  SET @event_id=@target_id;
  SELECT @organization_id=organization_id FROM dbo.tc_events WHERE id=@target_id;
  IF @organization_id IS NULL THROW 52701,'Event was not found',1;
 END;
 EXEC dbo.usp_EventImageAuthorize @actor_id,@auth_version,@organization_id,@maintenance;

 DECLARE @r_fingerprint char(64),@r_event uniqueidentifier,@r_org uniqueidentifier,@r_public varchar(255),
         @r_state varchar(12),@r_until datetime2(7),@r_json nvarchar(max);
 SELECT @r_fingerprint=fingerprint,@r_event=event_id,@r_org=organization_id,@r_public=public_id,
        @r_state=state,@r_until=lease_until,@r_json=result_json
 FROM dbo.tc_event_image_requests WITH(UPDLOCK,HOLDLOCK) WHERE actor_id=@row_actor AND request_id=@request_id;

 IF @r_state IS NOT NULL BEGIN
  IF @r_fingerprint<>@fingerprint COLLATE Latin1_General_BIN2 THROW 52707,'Request key was already used for different input',1;
  SELECT @event_id=@r_event,@organization_id=@r_org;
  EXEC dbo.usp_EventImageAuthorize @actor_id,@auth_version,@organization_id,@maintenance;
  IF @r_state='COMMITTED' BEGIN
   SELECT @row_actor actor_id,@event_id event_id,@organization_id organization_id,CAST(NULL AS uniqueidentifier) lease_token,@r_public public_id,@r_json result_json;
   RETURN;
  END;
  IF @r_state='ABORTED' THROW 52708,'Save has been cancelled; use a new request key',1;
  IF @r_until>SYSUTCDATETIME() THROW 52709,'Save is still running; retry shortly',1;
  EXEC dbo.usp_EventImageCheckTarget @operation,@event_id,@expected_version,@has_fields,@category_id;
  SET @token=NEWID();
  UPDATE dbo.tc_event_image_requests SET lease_token=@token,lease_until=DATEADD(minute,10,SYSUTCDATETIME()),updated_at=SYSUTCDATETIME()
  WHERE actor_id=@row_actor AND request_id=@request_id;
  SELECT @row_actor actor_id,@event_id event_id,@organization_id organization_id,@token lease_token,@r_public public_id,CAST(NULL AS nvarchar(max)) result_json;
  RETURN;
 END;

 EXEC dbo.usp_EventImageCheckTarget @operation,@event_id,@expected_version,@has_fields,@category_id;
 SET @token=NEWID();
 IF @has_image=1
  SET @public_id='ticketcenter/events/'+LOWER(CONVERT(varchar(36),@event_id))+'/'+LOWER(CONVERT(varchar(36),@request_id));
 INSERT dbo.tc_event_image_requests(actor_id,request_id,fingerprint,operation,event_id,organization_id,expected_version,public_id,state,lease_token,lease_until)
 VALUES(@row_actor,@request_id,@fingerprint,@operation,@event_id,@organization_id,@expected_version,@public_id,'PENDING',@token,DATEADD(minute,10,SYSUTCDATETIME()));
 IF @public_id IS NOT NULL
  INSERT dbo.tc_event_image_assets(public_id,event_id,state) VALUES(@public_id,@event_id,'AVAILABLE');
 SELECT @row_actor actor_id,@event_id event_id,@organization_id organization_id,@token lease_token,@public_id public_id,CAST(NULL AS nvarchar(max)) result_json;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventImageClaimCleanup
-- Latest Source Migration: 055_event_image_procedures.sql
/* Expires abandoned requests, then leases unreferenced images for deletion at the storage provider. */
CREATE OR ALTER PROCEDURE dbo.usp_EventImageClaimCleanup
 @limit int
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @limit IS NULL OR @limit NOT BETWEEN 1 AND 100 THROW 52718,'Invalid cleanup limit',1;
 UPDATE dbo.tc_event_image_requests SET state='ABORTED',updated_at=SYSUTCDATETIME()
 WHERE state='PENDING' AND lease_until<SYSUTCDATETIME() AND updated_at<DATEADD(day,-1,SYSUTCDATETIME());

 DECLARE @uuid varchar(500)=REPLICATE('[0-9a-fA-F-]',36);
 DECLARE @leased TABLE(public_id varchar(255) PRIMARY KEY,lease_token uniqueidentifier,available_at datetime2(7));
 WITH due AS (
  SELECT TOP(@limit) a.public_id,a.state,a.lease_token,a.lease_until,a.attempts,a.available_at
  FROM dbo.tc_event_image_assets a WITH(UPDLOCK,HOLDLOCK)
  WHERE a.state IN('AVAILABLE','DELETE_PENDING') AND a.available_at<=SYSUTCDATETIME()
    AND (a.lease_until IS NULL OR a.lease_until<SYSUTCDATETIME())
    AND a.public_id LIKE 'ticketcenter/events/'+@uuid+'/'+@uuid
    AND NOT EXISTS(SELECT 1 FROM dbo.tc_events e WHERE e.event_image_public_id=a.public_id
                   OR (a.secure_url IS NOT NULL AND e.event_image=a.secure_url))
    AND NOT EXISTS(SELECT 1 FROM dbo.tc_users u WHERE u.avatar_public_id=a.public_id
                   OR (a.secure_url IS NOT NULL AND u.avatar=a.secure_url))
    AND NOT EXISTS(SELECT 1 FROM dbo.tc_event_image_requests r WITH(UPDLOCK,HOLDLOCK)
                   WHERE r.public_id=a.public_id AND r.state='PENDING')
  ORDER BY a.available_at,a.public_id)
 UPDATE due SET state='DELETE_PENDING',lease_token=NEWID(),lease_until=DATEADD(minute,10,SYSUTCDATETIME()),attempts=attempts+1
 OUTPUT inserted.public_id,inserted.lease_token,deleted.available_at INTO @leased;
 SELECT public_id,lease_token FROM @leased ORDER BY available_at,public_id;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventImageCleanupFinished
-- Latest Source Migration: 055_event_image_procedures.sql
CREATE OR ALTER PROCEDURE dbo.usp_EventImageCleanupFinished
 @public_id varchar(255),@lease_token uniqueidentifier,@success bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 UPDATE dbo.tc_event_image_assets
 SET state=CASE WHEN @success=1 THEN 'DELETED' ELSE 'DELETE_PENDING' END,lease_until=NULL,lease_token=NULL,
     available_at=DATEADD(minute,CASE WHEN @success=1 THEN 1440 ELSE 5 END,SYSUTCDATETIME())
 WHERE public_id=@public_id AND lease_token=@lease_token AND state='DELETE_PENDING';
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventImageCommit
-- Latest Source Migration: 055_event_image_procedures.sql
/* Records the saved event snapshot on the request and schedules the replaced image for cleanup. */
CREATE OR ALTER PROCEDURE dbo.usp_EventImageCommit
 @actor_id uniqueidentifier,@request_id uniqueidentifier,@lease_token uniqueidentifier,@result_json nvarchar(max),
 @old_public_id varchar(255)=NULL,@new_public_id varchar(255)=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 UPDATE dbo.tc_event_image_requests SET state='COMMITTED',result_json=@result_json,updated_at=SYSUTCDATETIME()
 WHERE actor_id=@actor_id AND request_id=@request_id AND state='PENDING' AND lease_token=@lease_token;
 IF @@ROWCOUNT<>1 THROW 52715,'Save lease has changed; retry',1;
 IF @new_public_id IS NOT NULL AND @old_public_id IS NOT NULL AND @old_public_id<>@new_public_id COLLATE Latin1_General_BIN2
  UPDATE dbo.tc_event_image_assets SET available_at=SYSUTCDATETIME() WHERE public_id=@old_public_id AND state='AVAILABLE';
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventImageRelease
-- Latest Source Migration: 055_event_image_procedures.sql
CREATE OR ALTER PROCEDURE dbo.usp_EventImageRelease
 @actor_id uniqueidentifier,@request_id uniqueidentifier,@lease_token uniqueidentifier,@abort bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 UPDATE dbo.tc_event_image_requests
 SET state=CASE WHEN @abort=1 THEN 'ABORTED' ELSE 'PENDING' END,lease_until=SYSUTCDATETIME(),updated_at=SYSUTCDATETIME()
 WHERE actor_id=@actor_id AND request_id=@request_id AND lease_token=@lease_token AND state='PENDING';
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventImageSave
-- Latest Source Migration: 055_event_image_procedures.sql
/* Validates the lease, attaches the uploaded image and writes the event. Returns one row:
   committed=1 with result_json when the request was already committed (replay), otherwise committed=0 and the
   previous image key; the caller then snapshots the event and calls usp_EventImageCommit in the same transaction. */
CREATE OR ALTER PROCEDURE dbo.usp_EventImageSave
 @actor_id uniqueidentifier,@auth_version int,@maintenance bit,@request_id uniqueidentifier,@lease_token uniqueidentifier,
 @event_id uniqueidentifier,@organization_id uniqueidentifier,@operation varchar(10),@expected_version bigint,
 @has_fields bit,@category_id uniqueidentifier=NULL,@title nvarchar(250)=NULL,@description nvarchar(max)=NULL,
 @venue_name nvarchar(250)=NULL,@venue_address nvarchar(500)=NULL,@sale_start datetime2(3)=NULL,@sale_end datetime2(3)=NULL,
 @start_time datetime2(3)=NULL,@end_time datetime2(3)=NULL,@asset_public_id varchar(255)=NULL,@asset_secure_url varchar(4000)=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @row_actor uniqueidentifier=CASE WHEN @maintenance=1 THEN CAST('00000000-0000-0000-0000-000000000000' AS uniqueidentifier) ELSE @actor_id END;
 EXEC dbo.usp_EventImageAuthorize @actor_id,@auth_version,@organization_id,@maintenance;

 DECLARE @r_state varchar(12),@r_token uniqueidentifier,@r_json nvarchar(max),@r_public varchar(255);
 SELECT @r_state=state,@r_token=lease_token,@r_json=result_json,@r_public=public_id
 FROM dbo.tc_event_image_requests WITH(UPDLOCK,HOLDLOCK) WHERE actor_id=@row_actor AND request_id=@request_id;
 IF @r_state IS NULL THROW 52714,'Save intent is missing',1;
 IF @r_state='COMMITTED' BEGIN
  SELECT CAST(1 AS bit) committed,@r_json result_json,CAST(NULL AS varchar(255)) old_public_id;
  RETURN;
 END;
 IF @r_state<>'PENDING' OR @lease_token IS NULL OR @r_token<>@lease_token THROW 52715,'Save lease has changed; retry',1;

 EXEC dbo.usp_EventImageCheckTarget @operation,@event_id,@expected_version,@has_fields,@category_id;

 IF @asset_public_id IS NOT NULL BEGIN
  IF @r_public IS NULL OR @r_public<>@asset_public_id COLLATE Latin1_General_BIN2
     OR @asset_secure_url IS NULL OR LEN(@asset_secure_url)>2048
     OR LEFT(@asset_secure_url,27)<>'https://res.cloudinary.com/' COLLATE Latin1_General_BIN2
   THROW 52716,'Image provider returned invalid metadata',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_event_image_assets WITH(UPDLOCK,HOLDLOCK) WHERE public_id=@asset_public_id AND state='AVAILABLE')
   THROW 52717,'Image is scheduled for cleanup',1;
  UPDATE dbo.tc_event_image_assets SET secure_url=@asset_secure_url WHERE public_id=@asset_public_id;
 END;

 DECLARE @old_public_id varchar(255),@found bit=0;
 IF @operation<>'CREATE' BEGIN
  SELECT @old_public_id=event_image_public_id,@found=1 FROM dbo.tc_events WHERE id=@event_id;
  IF @found=0 THROW 52701,'Event was not found',1;
 END;

 IF @maintenance=0 BEGIN
  DECLARE @create bit=CASE WHEN @operation='CREATE' THEN 1 ELSE 0 END,@cover_only bit=CASE WHEN @operation='COVER' THEN 1 ELSE 0 END;
  EXEC dbo.usp_SaveEventDraft @event_id,@organization_id,@actor_id,@create,@expected_version,@category_id,@title,@description,
       @venue_name,@venue_address,@sale_start,@sale_end,@start_time,@end_time,@asset_secure_url,@asset_public_id,@cover_only;
 END
 ELSE IF @operation='CREATE'
  INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status,version,event_image,event_image_public_id)
  VALUES(@event_id,@organization_id,@category_id,@title,@description,@venue_name,@venue_address,@sale_start,@sale_end,@start_time,@end_time,'DRAFT',0,@asset_secure_url,@asset_public_id);
 ELSE BEGIN
  UPDATE dbo.tc_events SET version=version+1,
   category_id=CASE WHEN @operation='EDIT' THEN @category_id ELSE category_id END,
   title=CASE WHEN @operation='EDIT' THEN @title ELSE title END,
   description=CASE WHEN @operation='EDIT' THEN @description ELSE description END,
   venue_name=CASE WHEN @operation='EDIT' THEN @venue_name ELSE venue_name END,
   venue_address=CASE WHEN @operation='EDIT' THEN @venue_address ELSE venue_address END,
   sale_start=CASE WHEN @operation='EDIT' THEN @sale_start ELSE sale_start END,
   sale_end=CASE WHEN @operation='EDIT' THEN @sale_end ELSE sale_end END,
   start_time=CASE WHEN @operation='EDIT' THEN @start_time ELSE start_time END,
   end_time=CASE WHEN @operation='EDIT' THEN @end_time ELSE end_time END,
   event_image=CASE WHEN @asset_public_id IS NOT NULL THEN @asset_secure_url ELSE event_image END,
   event_image_public_id=CASE WHEN @asset_public_id IS NOT NULL THEN @asset_public_id ELSE event_image_public_id END
  WHERE id=@event_id AND version=@expected_version;
  IF @@ROWCOUNT<>1 THROW 52711,'Event has changed; reload before saving',1;
 END;
 SELECT CAST(0 AS bit) committed,CAST(NULL AS nvarchar(max)) result_json,@old_public_id old_public_id;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventRead
-- Latest Source Migration: 051_event_single_image_url.sql
CREATE OR ALTER PROCEDURE dbo.usp_EventRead
 @actor_id uniqueidentifier,@actor_version int,@admin bit=0,@kind varchar(30),
 @organization_id uniqueidentifier=NULL,@event_id uniqueidentifier=NULL,@zone_id uniqueidentifier=NULL,
 @status varchar(30)=NULL,@page int=1,@page_size int=20
AS
BEGIN
 SET NOCOUNT ON;
 IF @page<1 OR @page_size<1 OR @page_size>100 OR CONVERT(bigint,@page-1)*@page_size>1000000 THROW 52000,'Invalid page',1;
 IF @status IS NOT NULL AND @status NOT IN('DRAFT','PENDING_APPROVAL','REJECTED','PUBLISHED','CANCELLED') THROW 52000,'Invalid status',1;
 EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@organization_id OUTPUT,@event_id OUTPUT,@zone_id;
 IF @kind='DETAIL' BEGIN SET @status=NULL;SET @page=1;SET @page_size=1;END;
 IF @kind='AUTHORIZE' BEGIN
  SELECT @organization_id organization_id,@event_id event_id; RETURN;
 END;
 IF @kind='COVER_CONTEXT' BEGIN
  SELECT organization_id,status,version,event_image_public_id,event_image FROM dbo.tc_events WHERE id=@event_id; RETURN;
 END;
 IF @kind='ORGANIZATIONS' BEGIN
  SELECT CONCAT('{"data":',COALESCE((SELECT LOWER(CONVERT(varchar(36),o.id)) organizationId,o.name FROM dbo.tc_organizations o
   JOIN dbo.tc_organization_memberships m ON m.organization_id=o.id
   WHERE m.user_id=@actor_id AND m.active=1 AND m.role='MANAGER' AND o.status='APPROVED'
   ORDER BY o.name,o.id FOR JSON PATH),'[]'),'}') json; RETURN;
 END;
 IF @kind='RULES' BEGIN
  IF @admin<>1 OR @event_id IS NULL THROW 52003,'Admin event scope required',1;
  SELECT CONCAT('{"data":',COALESCE((SELECT LOWER(CONVERT(varchar(36),r.id)) commissionRuleId,CONVERT(varchar(30),r.rate_percent) ratePercent,
   CONVERT(varchar(40),r.fixed_fee) fixedFee,CONVERT(varchar(30),r.effective_from,126)+'Z' effectiveFrom,
   CONVERT(varchar(30),r.effective_to,126)+'Z' effectiveTo FROM dbo.tc_commission_rules r
   JOIN dbo.tc_events e ON e.organization_id=r.organization_id
   WHERE e.id=@event_id AND r.effective_from<=SYSUTCDATETIME() AND r.effective_to>SYSUTCDATETIME()
   AND r.effective_from<=e.start_time AND r.effective_to>e.start_time ORDER BY r.effective_from,r.id FOR JSON PATH),'[]'),'}') json; RETURN;
 END;
 IF @kind='LAYOUT' BEGIN
  IF @event_id IS NULL THROW 52000,'Event required',1;
  SELECT CONCAT('{"data":',COALESCE((SELECT LOWER(CONVERT(varchar(36),z.zone_id)) zoneId,LOWER(CONVERT(varchar(36),z.event_id)) eventId,z.name,z.type zoneType,
   CONVERT(varchar(40),z.price) basePrice,z.capacity totalCapacity,z.held_quantity heldQuantity,z.sold_quantity soldQuantity,
   z.available_quantity availableSeats,JSON_QUERY((SELECT LOWER(CONVERT(varchar(36),s.id)) seatId,s.row_name rowName,s.seat_number seatNumber,
    s.label seatLabel,s.status FROM dbo.tc_seats s WHERE s.zone_id=z.zone_id ORDER BY LEN(s.row_name),s.row_name,s.seat_number,s.id FOR JSON PATH)) seats
   FROM dbo.vw_ZoneInventory z WHERE z.event_id=@event_id ORDER BY z.name,z.zone_id FOR JSON PATH),'[]'),'}') json; RETURN;
 END;
 IF @kind NOT IN('LIST','DETAIL') THROW 52000,'Invalid read operation',1;
 IF @admin=0 AND @organization_id IS NULL THROW 52003,'Organization scope required',1;
 DECLARE @total bigint=(SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE (@admin=1 OR organization_id=@organization_id)
   AND (@status IS NULL OR status=@status) AND (@event_id IS NULL OR id=@event_id));
 IF @kind='DETAIL' AND @event_id IS NULL THROW 52000,'Event required',1;
 DECLARE @data nvarchar(max)=(SELECT LOWER(CONVERT(varchar(36),e.id)) eventId,LOWER(CONVERT(varchar(36),e.organization_id)) organizationId,o.name organizationName,
  LOWER(CONVERT(varchar(36),e.category_id)) categoryId,c.name category,e.title,e.description,e.venue_name venueName,e.venue_address venueAddress,
  e.event_image coverImageUrl,e.status,e.rejection_reason rejectionReason,e.version,
  CONVERT(varchar(30),e.sale_start,126)+'Z' saleStartTime,CONVERT(varchar(30),e.sale_end,126)+'Z' saleEndTime,
  CONVERT(varchar(30),e.start_time,126)+'Z' startTime,CONVERT(varchar(30),e.end_time,126)+'Z' endTime,
  CONVERT(varchar(30),e.created_at,126)+'Z' createdAt,
  (SELECT COUNT_BIG(*) FROM dbo.tc_zones z WHERE z.event_id=e.id) totalZones,
  COALESCE((SELECT SUM(CONVERT(bigint,capacity)) FROM dbo.vw_ZoneInventory z WHERE z.event_id=e.id),0) totalCapacity
  FROM dbo.tc_events e JOIN dbo.tc_organizations o ON o.id=e.organization_id JOIN dbo.tc_event_categories c ON c.id=e.category_id
  WHERE (@admin=1 OR e.organization_id=@organization_id) AND (@status IS NULL OR e.status=@status)
  AND (@event_id IS NULL OR e.id=@event_id) ORDER BY e.created_at DESC,e.id
  OFFSET (@page-1)*@page_size ROWS FETCH NEXT @page_size ROWS ONLY FOR JSON PATH,INCLUDE_NULL_VALUES);
 IF @kind='DETAIL' BEGIN
  IF JSON_QUERY(@data,'$[0]') IS NULL THROW 52004,'Event no longer exists',1;
  SELECT CONCAT('{"data":',JSON_QUERY(@data,'$[0]'),'}') json;
 END;
 ELSE SELECT CONCAT('{"data":',COALESCE(@data,'[]'),',"page":',@page,',"pageSize":',@page_size,',"total":',@total,
  ',"totalPages":',@total/@page_size+CASE WHEN @total%@page_size=0 THEN 0 ELSE 1 END,'}') json;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventReceipt
-- Latest Source Migration: 045_event_commands_and_public_seats.sql
CREATE OR ALTER PROCEDURE dbo.usp_EventReceipt
 @actor_id uniqueidentifier,@actor_version int,@scope varchar(30),@idempotency_key varchar(128),@request_hash char(64)
AS
BEGIN
 SET NOCOUNT ON;
 DECLARE @organization_id uniqueidentifier,@event_id uniqueidentifier=NULL,@admin bit=CASE WHEN @scope='EVENT_REVIEW' THEN 1 ELSE 0 END;
 SELECT @organization_id=organization_id FROM dbo.tc_event_command_receipts
  WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key;
 EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@organization_id OUTPUT,@event_id OUTPUT;
 IF EXISTS(SELECT 1 FROM dbo.tc_event_command_receipts WHERE actor_id=@actor_id AND scope=@scope
  AND idempotency_key=@idempotency_key AND request_hash<>@request_hash) THROW 52009,'Idempotency payload conflict',1;
 SELECT status_code,response_json,organization_id,event_id,cleanup_json FROM dbo.tc_event_command_receipts
  WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key;
END;
GO

-- Object: [PROCEDURE] dbo.usp_EventRequireSession
-- Latest Source Migration: 057_application_read_models.sql
CREATE OR ALTER PROCEDURE dbo.usp_EventRequireSession
 @actor_id uniqueidentifier,@auth_version int
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND auth_version=@auth_version)
  THROW 52703,'Current session is no longer valid',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ExpireHolds
-- Latest Source Migration: 036_sql_report_worker_reads.sql
-- Keep the LIEM runtime facade where present; the fallback has the same changes_payload contract.
IF OBJECT_ID('dbo.usp_ExpireHolds','P') IS NULL EXEC(N'
CREATE PROCEDURE dbo.usp_ExpireHolds @batch_size int=50 AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF COALESCE(IS_ROLEMEMBER(''tc_worker''),0)<>1 AND COALESCE(IS_MEMBER(''db_owner''),0)<>1 THROW 51831,''Worker principal required'',1;
 IF @batch_size IS NULL OR @batch_size NOT BETWEEN 1 AND 100 THROW 51832,''Batch size must be 1..100'',1;
 IF @@TRANCOUNT<>0 THROW 51833,''Expiry facade requires autocommit'',1;
 DECLARE @id uniqueidentifier;
 DECLARE @changes TABLE(actorId uniqueidentifier,eventId uniqueidentifier,holdId uniqueidentifier,version bigint);
 DECLARE holds CURSOR LOCAL FAST_FORWARD FOR SELECT TOP(@batch_size) id FROM dbo.tc_ticket_holds WHERE status=''ACTIVE'' AND expires_at<=SYSUTCDATETIME() ORDER BY event_id,id;
 OPEN holds; FETCH NEXT FROM holds INTO @id;
 WHILE @@FETCH_STATUS=0 BEGIN
  BEGIN TRY
   EXEC dbo.usp_ReleaseTicketHold @hold_id=@id,@actor_id=NULL,@reason=''EXPIRED'',@suppress_result=1;
   INSERT @changes SELECT user_id,event_id,id,version FROM dbo.tc_ticket_holds WHERE id=@id AND status=''RELEASED'';
  END TRY BEGIN CATCH IF ERROR_NUMBER() NOT IN(1205,1222,51273) THROW; END CATCH;
  FETCH NEXT FROM holds INTO @id;
 END;
 CLOSE holds; DEALLOCATE holds;
 SELECT COALESCE((SELECT LOWER(CONVERT(char(36),actorId)) actorId,LOWER(CONVERT(char(36),eventId)) eventId,LOWER(CONVERT(char(36),holdId)) holdId,version FROM @changes FOR JSON PATH),N''[]'') changes_payload;
END;');
GO

-- Object: [PROCEDURE] dbo.usp_HoldAuthorize
-- Latest Source Migration: 029_hold_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_HoldAuthorize @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@actor_id AND auth_version=@auth_version)
        THROW 51301,'Current session required',1;
    IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@actor_id AND status='ACTIVE' AND email_verified_at IS NOT NULL)
        THROW 51303,'Active verified user required',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_HoldLockSelection
-- Latest Source Migration: 029_hold_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_HoldLockSelection
    @actor_id uniqueidentifier, @event_id uniqueidentifier, @selections nvarchar(max), @target_hold uniqueidentifier=NULL,
    @include_active bit=1
AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 THROW 51310,'Transaction required',1;
    DECLARE @events TABLE(id uniqueidentifier PRIMARY KEY);
    INSERT @events SELECT @event_id WHERE @event_id IS NOT NULL;
    INSERT @events SELECT event_id FROM dbo.tc_ticket_holds
        WHERE (@include_active=1 AND user_id=@actor_id AND status='ACTIVE' OR id=@target_hold)
          AND event_id NOT IN(SELECT id FROM @events) GROUP BY event_id;
    DECLARE @id uniqueidentifier,@dummy int;
    DECLARE event_locks CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM @events ORDER BY id;
    OPEN event_locks; FETCH NEXT FROM event_locks INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN
        SELECT @dummy=1 FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@id;
        FETCH NEXT FROM event_locks INTO @id;
    END;
    CLOSE event_locks; DEALLOCATE event_locks;
    SELECT @dummy=1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@actor_id;
    IF @include_active=1 AND EXISTS(SELECT 1 FROM dbo.tc_ticket_holds WHERE user_id=@actor_id AND status='ACTIVE'
        AND event_id NOT IN(SELECT id FROM @events)) THROW 51309,'Hold preflight changed; retry transaction',1;
    DECLARE @holds TABLE(id uniqueidentifier PRIMARY KEY);
    INSERT @holds SELECT id FROM dbo.tc_ticket_holds WHERE @include_active=1 AND user_id=@actor_id AND status='ACTIVE' OR id=@target_hold;
    DECLARE hold_locks CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM @holds ORDER BY id;
    OPEN hold_locks; FETCH NEXT FROM hold_locks INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN
        SELECT @dummy=1 FROM dbo.tc_ticket_holds WITH(UPDLOCK,HOLDLOCK) WHERE id=@id;
        FETCH NEXT FROM hold_locks INTO @id;
    END;
    CLOSE hold_locks; DEALLOCATE hold_locks;
    DECLARE order_locks CURSOR LOCAL FAST_FORWARD FOR
        SELECT id FROM dbo.tc_orders WHERE hold_id IN(SELECT id FROM @holds) ORDER BY id;
    OPEN order_locks; FETCH NEXT FROM order_locks INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN
        SELECT @dummy=1 FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@id;
        FETCH NEXT FROM order_locks INTO @id;
    END;
    CLOSE order_locks; DEALLOCATE order_locks;
    DECLARE @zones TABLE(id uniqueidentifier PRIMARY KEY);
    DECLARE @seats TABLE(id uniqueidentifier PRIMARY KEY);
    INSERT @zones SELECT zone_id FROM dbo.tc_ticket_hold_items WHERE hold_id IN(SELECT id FROM @holds)
        UNION SELECT TRY_CONVERT(uniqueidentifier,JSON_VALUE(value,'$.zoneId')) FROM OPENJSON(@selections)
        WHERE TRY_CONVERT(uniqueidentifier,JSON_VALUE(value,'$.zoneId')) IS NOT NULL;
    INSERT @seats SELECT seat_id FROM dbo.tc_ticket_hold_items WHERE hold_id IN(SELECT id FROM @holds) AND seat_id IS NOT NULL
        UNION SELECT TRY_CONVERT(uniqueidentifier,JSON_VALUE(value,'$.seatId')) FROM OPENJSON(@selections)
        WHERE TRY_CONVERT(uniqueidentifier,JSON_VALUE(value,'$.seatId')) IS NOT NULL;
    DECLARE zone_locks CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM @zones ORDER BY id;
    OPEN zone_locks; FETCH NEXT FROM zone_locks INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN
        SELECT @dummy=1 FROM dbo.tc_zones WITH(UPDLOCK,HOLDLOCK) WHERE id=@id;
        FETCH NEXT FROM zone_locks INTO @id;
    END;
    CLOSE zone_locks; DEALLOCATE zone_locks;
    DECLARE seat_locks CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM @seats ORDER BY id;
    OPEN seat_locks; FETCH NEXT FROM seat_locks INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN
        SELECT @dummy=1 FROM dbo.tc_seats WITH(UPDLOCK,HOLDLOCK) WHERE id=@id;
        FETCH NEXT FROM seat_locks INTO @id;
    END;
    CLOSE seat_locks; DEALLOCATE seat_locks;
END;
GO

-- Object: [PROCEDURE] dbo.usp_HoldMutation
-- Latest Source Migration: 041_hold_refund_runtime_compatibility.sql
CREATE OR ALTER PROCEDURE dbo.usp_HoldMutation
    @actor_id uniqueidentifier,@auth_version int,@request_key uniqueidentifier,@fingerprint varchar(64),
    @kind varchar(10),@event_id uniqueidentifier=NULL,@selections nvarchar(max)=N'[]',@hold_id uniqueidentifier=NULL
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 10000;
    IF @@TRANCOUNT<>0 THROW 51310,'Facade requires autocommit',1;
    IF @request_key IS NULL OR @request_key='00000000-0000-0000-0000-000000000000'
        OR LEN(@fingerprint)<>64 OR @fingerprint LIKE '%[^0-9a-f]%' THROW 51300,'Invalid request',1;
    EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @app_result int,@resource nvarchar(255)=CONCAT('hold-user:',CONVERT(varchar(36),@actor_id));
        EXEC @app_result=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
        IF @app_result<0 THROW 51830,'Hold user lock unavailable; retry',1;
        DECLARE @payload nvarchar(max),@prior_fingerprint varchar(200),@prior_target uniqueidentifier,
            @key varchar(100)=LOWER(CONVERT(char(36),@request_key)),@changes nvarchar(max),@old_hold uniqueidentifier;
        SELECT @payload=response_payload,@prior_fingerprint=fingerprint,@prior_target=target_id
        FROM dbo.tc_request_idempotency WITH(UPDLOCK,HOLDLOCK)
        WHERE actor_id=@actor_id AND operation='HOLD_MUTATION' AND idempotency_key=@key;
        IF @kind='CANCEL' BEGIN
            SELECT @event_id=event_id FROM dbo.tc_ticket_holds WHERE id=@hold_id AND user_id=@actor_id;
            IF @event_id IS NULL THROW 51304,'Hold not found',1;
        END;
        IF @kind NOT IN('CREATE','CANCEL') THROW 51300,'Invalid mutation kind',1;
        DECLARE @include_active bit=CASE WHEN @kind='CREATE' THEN 1 ELSE 0 END;
        EXEC dbo.usp_HoldLockSelection @actor_id,@event_id,@selections,@hold_id,@include_active;
        EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_events e JOIN dbo.tc_organizations o WITH(HOLDLOCK) ON o.id=e.organization_id
            WHERE e.id=@event_id AND o.status='APPROVED') THROW 51303,'Event unavailable',1;
        IF @payload IS NOT NULL BEGIN
            IF @prior_fingerprint<>@fingerprint OR (@kind='CANCEL' AND @prior_target<>@hold_id)
                THROW 51308,'Idempotency key conflict',1;
            IF NOT EXISTS(SELECT 1 FROM dbo.tc_ticket_holds WHERE id=@prior_target AND user_id=@actor_id)
                THROW 51304,'Hold not found',1;
            COMMIT;
            SELECT CAST(1 AS bit) replayed,@payload response_payload,N'[]' changes_payload;
            RETURN;
        END;
        DECLARE @affected TABLE(hold_id uniqueidentifier PRIMARY KEY);
        IF @kind='CREATE' BEGIN
            SELECT @old_hold=id FROM dbo.tc_ticket_holds WHERE user_id=@actor_id AND status='ACTIVE';
            EXEC dbo.usp_CreateTicketHold @actor_id,@event_id,@selections,@hold_id OUTPUT,1;
            IF @old_hold IS NOT NULL INSERT @affected VALUES(@old_hold);
        END ELSE IF @kind='CANCEL' BEGIN
            IF EXISTS(SELECT 1 FROM dbo.tc_ticket_holds WHERE id=@hold_id AND status='CONSUMED')
                THROW 51306,'Consumed hold cannot be cancelled',1;
            IF EXISTS(SELECT 1 FROM dbo.tc_ticket_holds WHERE id=@hold_id AND status='ACTIVE')
                INSERT @affected VALUES(@hold_id);
            EXEC dbo.usp_ReleaseTicketHold @hold_id,@actor_id,'USER_CANCEL',@suppress_result=1;
        END ELSE THROW 51300,'Invalid mutation kind',1;
        IF @kind='CREATE' INSERT @affected VALUES(@hold_id);
        DECLARE @outcome varchar(20)=CASE WHEN @kind='CREATE' THEN 'CREATED' ELSE 'CANCELLED' END;
        EXEC dbo.usp_HoldSnapshot @hold_id,@outcome,@payload OUTPUT;
        INSERT dbo.tc_request_idempotency(actor_id,operation,idempotency_key,target_id,fingerprint,response_payload,expires_at)
        VALUES(@actor_id,'HOLD_MUTATION',@key,@hold_id,@fingerprint,@payload,CONVERT(datetime2(3),'9999-12-31'));
        SELECT @changes=(SELECT LOWER(CONVERT(char(36),h.user_id)) actorId,LOWER(CONVERT(char(36),h.event_id)) eventId,
            LOWER(CONVERT(char(36),h.id)) holdId,h.version FROM dbo.tc_ticket_holds h JOIN @affected a ON a.hold_id=h.id FOR JSON PATH);
        COMMIT;
        SELECT CAST(0 AS bit) replayed,@payload response_payload,COALESCE(@changes,N'[]') changes_payload;
    END TRY BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_HoldSnapshot
-- Latest Source Migration: 029_hold_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_HoldSnapshot
    @hold_id uniqueidentifier,@outcome varchar(20),@payload nvarchar(max) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SELECT @payload=(SELECT LOWER(CONVERT(char(36),h.id)) id, LOWER(CONVERT(char(36),h.event_id)) eventId,e.title eventTitle,
        CONVERT(varchar(23),h.created_at,126)+'Z' createdAt,CONVERT(varchar(23),h.expires_at,126)+'Z' expiresAt,
        @outcome outcome,JSON_QUERY((SELECT LOWER(CONVERT(char(36),i.zone_id)) zoneId,
            LOWER(CONVERT(char(36),i.seat_id)) seatId,z.name zoneName,s.label seatLabel,i.quantity,CONVERT(varchar(30),i.unit_price) unitPrice
            FROM dbo.tc_ticket_hold_items i JOIN dbo.tc_zones z ON z.id=i.zone_id LEFT JOIN dbo.tc_seats s ON s.id=i.seat_id WHERE i.hold_id=h.id ORDER BY i.zone_id,i.seat_id
            FOR JSON PATH,INCLUDE_NULL_VALUES)) items
        FROM dbo.tc_ticket_holds h JOIN dbo.tc_events e ON e.id=h.event_id WHERE h.id=@hold_id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
END;
GO

-- Object: [PROCEDURE] dbo.usp_ListCheckoutCoupons
-- Latest Source Migration: 068_checkout_eligible_coupons.sql
/* Read-only coupon discovery. Applying a selection still uses the locked order command. */
CREATE OR ALTER PROCEDURE dbo.usp_ListCheckoutCoupons
    @actor_id uniqueidentifier,@auth_version int,@hold_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @@TRANCOUNT<>0 THROW 51410,'Facade owns transaction',1;
    BEGIN TRANSACTION;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier,@owner_id uniqueidentifier,@org uniqueidentifier,
            @order_id uniqueidentifier,@subtotal decimal(19,0),@now datetime2(3),@expires datetime2(3),@payload nvarchar(max);
        SELECT @event_id=event_id,@owner_id=user_id FROM dbo.tc_ticket_holds WHERE id=@hold_id;
        EXEC dbo.usp_HoldLockSelection @actor_id,@event_id,N'[]',@hold_id,@include_active=0;
        EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
        IF @owner_id IS NULL THROW 51404,'Hold not found',1;
        IF @owner_id<>@actor_id THROW 51403,'Hold owner required',1;
        SET @now=SYSUTCDATETIME();
        SELECT @expires=expires_at FROM dbo.tc_ticket_holds WHERE id=@hold_id AND status='ACTIVE' AND expires_at>@now;
        IF @expires IS NULL THROW 51406,'Hold is terminal or expired',1;
        SELECT @org=organization_id FROM dbo.tc_events WHERE id=@event_id AND archived_at IS NULL AND status='PUBLISHED' AND @now>=sale_start AND @now<sale_end;
        IF @org IS NULL THROW 51406,'Event is not on sale',1;
        IF EXISTS(SELECT 1 FROM dbo.tc_orders WHERE hold_id=@hold_id AND user_id<>@actor_id) THROW 51403,'Order owner required',1;
        SELECT @order_id=id,@subtotal=subtotal_amount FROM dbo.tc_orders WHERE hold_id=@hold_id AND user_id=@actor_id AND status='PENDING_PAYMENT';
        IF EXISTS(SELECT 1 FROM dbo.tc_payments WHERE order_id=@order_id AND status IN('PENDING','UNKNOWN')) THROW 51407,'Payment unresolved',1;
        IF @subtotal IS NULL
            SELECT @subtotal=SUM(unit_price*quantity) FROM dbo.tc_ticket_hold_items WHERE hold_id=@hold_id;
        SELECT @payload=(SELECT CONVERT(varchar(23),@now,126)+'Z' serverTime,LOWER(CONVERT(char(36),@hold_id)) holdId,
            JSON_QUERY((SELECT c.code couponCode,CAST(1 AS bit) eligible,'ELIGIBLE' reason,
                CASE WHEN c.max_uses>u.used THEN c.max_uses-u.used ELSE 0 END remainingUses,
                CONVERT(varchar(30),d.amount) discountAmount,CONVERT(varchar(30),@subtotal-d.amount) totalAmount,
                CONVERT(varchar(23),CASE WHEN c.valid_to<@expires AND own.reserved=0 THEN c.valid_to ELSE @expires END,126)+'Z' validUntil,
                CONVERT(varchar(23),@now,126)+'Z' serverTime,LOWER(CONVERT(char(36),@order_id)) orderId
              FROM dbo.tc_coupons c
              CROSS APPLY (SELECT COUNT(*) used FROM dbo.tc_coupon_redemptions WHERE coupon_id=c.id AND status IN('RESERVED','CONSUMED')) u
              CROSS APPLY (SELECT COUNT(*) reserved FROM dbo.tc_coupon_redemptions WHERE coupon_id=c.id AND order_id=@order_id AND status='RESERVED') own
              CROSS APPLY (SELECT CASE WHEN own.reserved>0 THEN (SELECT discount_amount FROM dbo.tc_orders WHERE id=@order_id)
                  ELSE dbo.fn_CalculateCouponDiscountCapped(@subtotal,c.id) END amount) d
              WHERE c.organization_id=@org AND d.amount IS NOT NULL AND
                (own.reserved>0 OR (c.active=1 AND @now>=c.valid_from AND @now<c.valid_to AND c.max_uses>u.used))
              ORDER BY d.amount DESC,c.code FOR JSON PATH,INCLUDE_NULL_VALUES)) coupons
            FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
        COMMIT; SELECT @payload response_payload;
    END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ListManagedCoupons
-- Latest Source Migration: 043_coupon_admin.sql
CREATE OR ALTER PROCEDURE dbo.usp_ListManagedCoupons
    @actor_id uniqueidentifier, @auth_version int, @organization_id uniqueidentifier=NULL,
    @page int=1, @size int=20
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    IF @page IS NULL OR @size IS NULL OR @page<1 OR @size<1 OR @size>100
       OR @page>1+2147483647/@size THROW 51522,'Invalid page',1;
    DECLARE @offset int=(@page-1)*@size;
    SELECT c.id,c.organization_id,o.name AS org_name,c.code,c.discount_type,
           c.percentage_value,c.fixed_amount,c.max_discount_amount,c.max_uses,
           u.reserved_uses,u.consumed_uses,u.available_uses,
           c.valid_from,c.valid_to,c.active,c.version
    FROM dbo.tc_coupons c JOIN dbo.vw_CouponUsage u ON u.coupon_id=c.id
    JOIN dbo.tc_organizations o ON o.id=c.organization_id
    WHERE @organization_id IS NULL OR c.organization_id=@organization_id
    ORDER BY c.valid_from DESC,c.id DESC OFFSET @offset ROWS FETCH NEXT @size ROWS ONLY;
END;
GO

-- Object: [PROCEDURE] dbo.usp_OrderCouponDecision
-- Latest Source Migration: 030_order_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_OrderCouponDecision
    @order_id uniqueidentifier,@coupon_code nvarchar(80),@now datetime2(3),@new_coupon uniqueidentifier OUTPUT,
    @reason varchar(30) OUTPUT,@discount decimal(19,0) OUTPUT,@remaining int OUTPUT,@valid_until datetime2(3) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @org uniqueidentifier,@subtotal decimal(19,0),@max int,@used int,@active bit,@from datetime2(3),@to datetime2(3),@own bit=0;
    SELECT @org=e.organization_id,@subtotal=o.subtotal_amount,@valid_until=h.expires_at
        FROM dbo.tc_orders o JOIN dbo.tc_events e ON e.id=o.event_id JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id WHERE o.id=@order_id;
    SELECT @new_coupon=id,@active=active,@from=valid_from,@to=valid_to,@max=max_uses,
        @discount=dbo.fn_CalculateCouponDiscount(@subtotal,discount_type,percentage_value,fixed_amount)
        FROM dbo.tc_coupons WHERE organization_id=@org AND code=@coupon_code;
    IF @new_coupon IS NULL BEGIN SET @reason=CASE WHEN EXISTS(SELECT 1 FROM dbo.tc_coupons WHERE code=@coupon_code AND organization_id<>@org) THEN 'WRONG_ORGANIZATION' ELSE 'NOT_FOUND' END; SET @remaining=0; RETURN; END;
    SELECT @used=COUNT(*) FROM dbo.tc_coupon_redemptions WHERE coupon_id=@new_coupon AND status IN('RESERVED','CONSUMED');
    IF EXISTS(SELECT 1 FROM dbo.tc_coupon_redemptions WHERE order_id=@order_id AND coupon_id=@new_coupon AND status='RESERVED') SET @own=1;
    SET @remaining=CASE WHEN @max>@used THEN @max-@used ELSE 0 END;
    IF @own=1 BEGIN
        SELECT @discount=discount_amount FROM dbo.tc_orders WHERE id=@order_id;
        SET @reason='ELIGIBLE'; RETURN;
    END;
    SET @valid_until=CASE WHEN @to<@valid_until THEN @to ELSE @valid_until END;
    SET @reason=CASE WHEN @active=0 THEN 'INACTIVE' WHEN @now<@from THEN 'NOT_STARTED' WHEN @now>=@to THEN 'EXPIRED'
        WHEN @discount IS NULL THEN 'INVALID_CONFIGURATION' WHEN @remaining=0 AND @own=0 THEN 'QUOTA_EXHAUSTED' ELSE 'ELIGIBLE' END;
END;
GO

-- Object: [PROCEDURE] dbo.usp_OrderLockCoupons
-- Latest Source Migration: 030_order_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_OrderLockCoupons @old_id uniqueidentifier=NULL,@new_id uniqueidentifier=NULL
AS
BEGIN
    SET NOCOUNT ON;
    IF @@TRANCOUNT=0 THROW 51410,'Transaction required',1;
    DECLARE @id uniqueidentifier,@dummy int;
    DECLARE coupon_locks CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM dbo.tc_coupons WHERE id IN(@old_id,@new_id) ORDER BY id;
    OPEN coupon_locks; FETCH NEXT FROM coupon_locks INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN
        SELECT @dummy=1 FROM dbo.tc_coupons WITH(UPDLOCK,HOLDLOCK) WHERE id=@id;
        FETCH NEXT FROM coupon_locks INTO @id;
    END;
    CLOSE coupon_locks; DEALLOCATE coupon_locks;
END;
GO

-- Object: [PROCEDURE] dbo.usp_OrderSnapshot
-- Latest Source Migration: 030_order_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_OrderSnapshot @order_id uniqueidentifier,@checkout bit=0,@now datetime2(3)=NULL,@payload nvarchar(max) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SELECT @payload=(SELECT LOWER(CONVERT(char(36),o.id)) orderId,o.order_code orderCode,
        LOWER(CONVERT(char(36),o.user_id)) userId,LOWER(CONVERT(char(36),o.event_id)) eventId,
        LOWER(CONVERT(char(36),o.hold_id)) holdId,o.status,CONVERT(varchar(23),h.expires_at,126)+'Z' expiresAt,
        CONVERT(varchar(30),o.subtotal_amount) subtotal,CONVERT(varchar(30),o.discount_amount) discountAmount,
        CONVERT(varchar(30),o.total_amount) totalAmount,c.code appliedCouponCode,o.version,
        JSON_QUERY((SELECT LOWER(CONVERT(char(36),i.id)) orderItemId,LOWER(CONVERT(char(36),i.zone_id)) zoneId,
            LOWER(CONVERT(char(36),i.seat_id)) seatId,i.zone_name_snapshot zoneName,i.seat_label_snapshot seatLabel,
            i.quantity,CONVERT(varchar(30),i.unit_price) unitPrice,CONVERT(varchar(30),i.unit_price*i.quantity) lineTotal
            FROM dbo.tc_order_items i WHERE i.order_id=o.id ORDER BY i.id FOR JSON PATH,INCLUDE_NULL_VALUES)) items,
        CASE WHEN @checkout=1 THEN h.status END holdStatus,
        CASE WHEN @checkout=1 THEN CASE WHEN EXISTS(SELECT 1 FROM dbo.tc_payments WHERE order_id=o.id AND status='UNKNOWN') THEN 'UNKNOWN'
            WHEN EXISTS(SELECT 1 FROM dbo.tc_payments WHERE order_id=o.id AND status='PENDING') THEN 'PAYMENT_PENDING' ELSE 'CLEAR' END END paymentResolution,
        CASE WHEN @checkout=1 THEN CAST(CASE WHEN o.status='PENDING_PAYMENT' AND h.status='ACTIVE' AND h.expires_at>@now
            AND e.status='PUBLISHED' AND @now>=e.sale_start AND @now<e.sale_end
            AND NOT EXISTS(SELECT 1 FROM dbo.tc_payments WHERE order_id=o.id AND status IN('PENDING','UNKNOWN')) THEN 1 ELSE 0 END AS bit) END canEditCoupon
        FROM dbo.tc_orders o JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id JOIN dbo.tc_events e ON e.id=o.event_id
        LEFT JOIN dbo.tc_coupons c ON c.id=o.coupon_id WHERE o.id=@order_id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF @checkout=0 SET @payload=JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(@payload,'$.holdStatus',NULL),'$.paymentResolution',NULL),'$.canEditCoupon',NULL);
END;
GO

-- Object: [PROCEDURE] dbo.usp_OrganizationSubmit
-- Latest Source Migration: 056_organization_submit_and_grant_cleanup.sql
CREATE OR ALTER PROCEDURE dbo.usp_OrganizationSubmit
    @requester_id uniqueidentifier,
    @name nvarchar(200),
    @contact_email nvarchar(320),
    @contact_phone nvarchar(30)=NULL,
    @description nvarchar(2000)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION usp_OrganizationSubmit_sp;

    BEGIN TRY
        DECLARE @user_status varchar(20), @organization_id uniqueidentifier = NEWID(), @now datetime2(7) = SYSUTCDATETIME();

        SELECT @user_status = status FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK) WHERE id = @requester_id;
        IF @user_status IS NULL
            THROW 52620, 'User not found', 1;
        IF @user_status <> 'ACTIVE'
            THROW 52621, 'User is not active', 1;
        IF NULLIF(LTRIM(RTRIM(@name)), N'') IS NULL OR NULLIF(LTRIM(RTRIM(@contact_email)), N'') IS NULL
            THROW 52623, 'Organization name and contact email are required', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_organizations WHERE requester_id = @requester_id AND status = 'PENDING_APPROVAL')
            THROW 52622, 'A pending organization request already exists', 1;

        INSERT INTO dbo.tc_organizations (id, name, contact_email, contact_phone, description, requester_id, status, submitted_at, created_at, updated_at)
        VALUES (@organization_id, @name, @contact_email, @contact_phone, @description, @requester_id, 'PENDING_APPROVAL', @now, @now, @now);

        INSERT INTO dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id)
        VALUES (@requester_id, 'ORGANIZATION_SUBMITTED', 'ORGANIZATION', @organization_id);

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT @organization_id AS organization_id, CAST('PENDING_APPROVAL' AS varchar(30)) AS status, @now AS submitted_at;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION usp_OrganizationSubmit_sp;
        THROW;
    END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_OutboxComplete
-- Latest Source Migration: 053_outbox_worker_procedures.sql
CREATE OR ALTER PROCEDURE dbo.usp_OutboxComplete
 @id uniqueidentifier,@owner varchar(120),@sent bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 /* Only the current lease holder may finish a row; a lost lease is a silent no-op, as before. */
 UPDATE dbo.tc_outbox SET
  status=CASE WHEN @sent=1 THEN 'PUBLISHED' ELSE 'FAILED' END,
  published_at=CASE WHEN @sent=1 THEN SYSUTCDATETIME() ELSE published_at END,
  available_at=CASE WHEN @sent=1 THEN available_at ELSE DATEADD(minute,5,SYSUTCDATETIME()) END,
  lease_owner=NULL,lease_until=NULL
 WHERE id=@id AND lease_owner=@owner AND status='PROCESSING';
 SELECT @@ROWCOUNT completed;
END;
GO

-- Object: [PROCEDURE] dbo.usp_OutboxLease
-- Latest Source Migration: 053_outbox_worker_procedures.sql
CREATE OR ALTER PROCEDURE dbo.usp_OutboxLease
 @owner varchar(120),@max_attempts int,@batch int=50
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF NULLIF(LTRIM(RTRIM(@owner)),'') IS NULL OR @max_attempts<1 OR @batch NOT BETWEEN 1 AND 500 THROW 52080,'Invalid outbox lease request',1;
 DECLARE @leased TABLE(id uniqueidentifier PRIMARY KEY,event_type varchar(120),payload nvarchar(max),created_at datetime2(7));
 WITH due AS (
  SELECT TOP(@batch) id,event_type,payload,created_at,status,lease_owner,lease_until,attempts
  FROM dbo.tc_outbox WITH(UPDLOCK,READPAST,ROWLOCK)
  WHERE (status IN('PENDING','FAILED') OR (status='PROCESSING' AND lease_until<=SYSUTCDATETIME()))
    AND attempts<@max_attempts AND event_type<>'EVENT_CANCELLED' AND available_at<=SYSUTCDATETIME()
  ORDER BY created_at)
 UPDATE due SET status='PROCESSING',lease_owner=@owner,lease_until=DATEADD(minute,2,SYSUTCDATETIME()),attempts=attempts+1
 OUTPUT inserted.id,inserted.event_type,inserted.payload,inserted.created_at INTO @leased;
 SELECT id,event_type,payload FROM @leased ORDER BY created_at,id;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ProcessCancelledOrder
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.usp_ProcessCancelledOrder @event_id uniqueidentifier, @order_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp17;
    BEGIN TRY
        IF IS_ROLEMEMBER('tc_worker') <> 1 AND IS_ROLEMEMBER('db_owner') <> 1
            THROW 51569, 'Worker principal required', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = 'CANCELLED')
            THROW 51570, 'Event is not cancelled', 1;
        DECLARE @hold_id uniqueidentifier, @order_status varchar(30), @owner_id uniqueidentifier,
                @request_id uniqueidentifier;
        SELECT @hold_id = hold_id, @order_status = status, @owner_id = user_id
        FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @order_id AND event_id = @event_id;
        IF @hold_id IS NULL THROW 51571, 'Order does not belong to cancelled event', 1;
        IF @order_status IN ('PENDING_PAYMENT', 'EXPIRED', 'CANCELLED') BEGIN
            IF EXISTS (SELECT 1 FROM dbo.tc_ticket_holds WHERE id = @hold_id AND status = 'ACTIVE')
                EXEC dbo.usp_ReleaseTicketHold @hold_id, NULL, 'EVENT_CANCELLED';
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_id order_id, CAST('RELEASED' AS varchar(30)) result; RETURN;
        END;

        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id;

        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        SELECT 'CANCELLED_EVENT_USED_TICKET', 'TICKET', t.id,
               CONCAT(N'{"orderId":"', @order_id, N'"}')
        FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id AND t.status = 'USED'
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_audit_logs a
                          WHERE a.action = 'CANCELLED_EVENT_USED_TICKET' AND a.aggregate_id = t.id);

        UPDATE rr SET reason_type = 'EVENT_CANCELLATION', version = version + 1, updated_at = SYSUTCDATETIME()
        FROM dbo.tc_refund_obligations rr
        WHERE rr.order_id = @order_id AND rr.purpose = 'CUSTOMER_REFUND' AND rr.status = 'REQUESTED'
          AND rr.reason_type = 'CUSTOMER_REQUEST';

        UPDATE rr SET status = 'COMPLETED', decided_at = COALESCE(decided_at, SYSUTCDATETIME()),
                      version = version + 1, updated_at = SYSUTCDATETIME()
        FROM dbo.tc_refund_obligations rr
        WHERE rr.order_id = @order_id AND rr.purpose = 'CUSTOMER_REFUND' AND rr.status = 'REQUESTED'
          AND rr.reason_type = 'EVENT_CANCELLATION'
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_tickets rt WHERE rt.refund_id = rr.id);

        DECLARE @pending TABLE(id uniqueidentifier PRIMARY KEY);
        INSERT @pending
        SELECT id FROM dbo.tc_refund_obligations
        WHERE order_id = @order_id AND purpose = 'CUSTOMER_REFUND' AND status = 'REQUESTED' AND reason_type = 'EVENT_CANCELLATION';
        WHILE EXISTS (SELECT 1 FROM @pending) BEGIN
            SELECT TOP (1) @request_id = id FROM @pending ORDER BY id;
            EXEC dbo.usp_ReviewRefundRequest @request_id, NULL, 'APPROVE', NULL, 0;
            DELETE FROM @pending WHERE id = @request_id;
        END;

        IF EXISTS (
            SELECT 1 FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = 'ACTIVE')
        BEGIN
            SET @request_id = NEWID();
            INSERT dbo.tc_refund_obligations(id, order_id, payment_id, amount, purpose, reason_type, reason, status, requester_id)
            SELECT @request_id, o.id, (SELECT p.id FROM dbo.tc_payments p WHERE p.id = o.accepted_payment_id AND p.status = 'CAPTURED'),
                   (SELECT COALESCE(SUM(t.paid_amount), 0) FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                    WHERE oi.order_id = o.id AND t.status = 'ACTIVE'),
                   'CUSTOMER_REFUND', 'EVENT_CANCELLATION', N'Event cancelled', 'REQUESTED', @owner_id
            FROM dbo.tc_orders o WHERE o.id = @order_id;
            INSERT dbo.tc_refund_tickets(refund_id, ticket_id)
            SELECT @request_id, t.id FROM dbo.tc_tickets t
            JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = 'ACTIVE';
            UPDATE t SET status = 'REFUND_PENDING', version = version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = 'ACTIVE';
            EXEC dbo.usp_ReviewRefundRequest @request_id, NULL, 'APPROVE', NULL, 0;
        END;

        IF @started = 1 COMMIT TRANSACTION;
        SELECT @order_id order_id, @request_id refund_request_id,
               CAST('PROCESSED' AS varchar(30)) result;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp17;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_PublishEvent
-- Latest Source Migration: 051_event_single_image_url.sql
CREATE OR ALTER PROCEDURE dbo.usp_PublishEvent
 @event_id uniqueidentifier,@actor_id uniqueidentifier,@commission_rule_id uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END,@old_actor sql_variant=SESSION_CONTEXT(N'actor_id');
 IF @started=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp12;
 BEGIN TRY
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@actor_id;
  DECLARE @organization_id uniqueidentifier,@status varchar(30),@now datetime2(3)=SYSUTCDATETIME();
  SELECT @organization_id=organization_id,@status=status FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(HOLDLOCK)
   WHERE id=@actor_id AND status='ACTIVE' AND platform_role='ADMIN')
   THROW 51520,'Administrator role required',1;
  SET @now=SYSUTCDATETIME();
  IF @status IS NULL THROW 51521,'Event not found',1;
  IF @status='PUBLISHED' BEGIN
   IF @commission_rule_id IS NULL OR (SELECT commission_rule_id FROM dbo.tc_events WHERE id=@event_id)<>@commission_rule_id
    THROW 51522,'Published event commission rule is immutable',1;
   IF @started=1 COMMIT;
   EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor;
   SELECT @event_id event_id,@status status; RETURN;
  END;
  IF @status<>'PENDING_APPROVAL' THROW 51523,'Event is not pending approval',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_organizations WITH(HOLDLOCK) WHERE id=@organization_id AND status='APPROVED')
   THROW 51524,'Event organization is not approved',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_commission_rules r WITH(UPDLOCK,HOLDLOCK)
   JOIN dbo.tc_events e ON e.organization_id=r.organization_id
   WHERE e.id=@event_id AND r.id=@commission_rule_id AND r.effective_from<=@now AND r.effective_to>@now
   AND r.effective_from<=e.start_time AND r.effective_to>e.start_time)
   THROW 51524,'Commission rule is not valid for event organization and date',1;
  IF EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND
   (NULLIF(LTRIM(RTRIM(event_image)),'') IS NULL OR sale_start>=sale_end OR sale_end>start_time OR start_time>=end_time))
   THROW 51525,'Event content or schedule is incomplete',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_zones WITH(UPDLOCK,HOLDLOCK) WHERE event_id=@event_id)
   THROW 51526,'Event requires at least one zone',1;
  IF EXISTS(SELECT 1 FROM dbo.tc_zones z WHERE z.event_id=@event_id AND z.type='SEATED'
   AND NOT EXISTS(SELECT 1 FROM dbo.tc_seats s WHERE s.zone_id=z.id))
   THROW 51527,'Each seated zone requires seats',1;
  UPDATE dbo.tc_events SET commission_rule_id=@commission_rule_id,status='PUBLISHED',version=version+1 WHERE id=@event_id;
  IF @started=1 COMMIT;
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor;
  SELECT @event_id event_id,CAST('PUBLISHED' AS varchar(30)) status;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK;
  ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_sp12;
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor;
  THROW;
 END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ReadActiveHold
-- Latest Source Migration: 029_hold_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_ReadActiveHold @actor_id uniqueidentifier,@auth_version int
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 10000;
    IF @@TRANCOUNT<>0 THROW 51310,'Facade requires autocommit',1;
    EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
    BEGIN TRY
        BEGIN TRANSACTION;
        EXEC dbo.usp_HoldLockSelection @actor_id,NULL,N'[]';
        EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
        DECLARE @hold uniqueidentifier,@payload nvarchar(max),@now datetime2(3)=SYSUTCDATETIME();
        SELECT @hold=h.id FROM dbo.tc_ticket_holds h JOIN dbo.tc_events e ON e.id=h.event_id
            JOIN dbo.tc_organizations o WITH(HOLDLOCK) ON o.id=e.organization_id
            WHERE h.user_id=@actor_id AND h.status='ACTIVE' AND h.expires_at>@now AND e.status='PUBLISHED' AND o.status='APPROVED';
        IF @hold IS NOT NULL BEGIN
            EXEC dbo.usp_HoldSnapshot @hold,'ACTIVE',@payload OUTPUT;
            SET @payload=JSON_MODIFY(JSON_MODIFY(@payload,'$.outcome',NULL),'$.status','ACTIVE');
        END;
        SELECT @payload=(SELECT CONVERT(varchar(23),@now,126)+'Z' serverTime,JSON_QUERY(@payload) hold
            FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
        COMMIT;
        SELECT @payload response_payload;
    END TRY BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ReadCheckout
-- Latest Source Migration: 030_order_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_ReadCheckout @actor_id uniqueidentifier,@auth_version int,@hold_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @@TRANCOUNT<>0 THROW 51410,'Facade owns transaction',1;
    BEGIN TRANSACTION;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier,@owner_id uniqueidentifier,@order_id uniqueidentifier,@now datetime2(3),@order nvarchar(max),@payload nvarchar(max);
        SELECT @event_id=event_id,@owner_id=user_id FROM dbo.tc_ticket_holds WHERE id=@hold_id;
        EXEC dbo.usp_HoldLockSelection @actor_id,@event_id,N'[]',@hold_id,@include_active=0;
        EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
        IF @owner_id IS NOT NULL AND @owner_id<>@actor_id THROW 51403,'Hold owner required',1;
        IF @owner_id IS NULL THROW 51400,'Hold not found',1;
        SELECT @order_id=id FROM dbo.tc_orders WHERE hold_id=@hold_id;
        IF EXISTS(SELECT 1 FROM dbo.tc_orders WHERE id=@order_id AND user_id<>@actor_id) THROW 51403,'Order owner required',1;
        DECLARE @coupon uniqueidentifier,@payment_dummy int;
        SELECT @coupon=coupon_id FROM dbo.tc_orders WHERE id=@order_id;
        EXEC dbo.usp_OrderLockCoupons @coupon,NULL;
        SELECT @payment_dummy=1 FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@order_id;
        SELECT @now=SYSUTCDATETIME();
        IF @order_id IS NOT NULL EXEC dbo.usp_OrderSnapshot @order_id,1,@now,@order OUTPUT;
        SELECT @payload=(SELECT CONVERT(varchar(23),@now,126)+'Z' serverTime,JSON_QUERY(@order) [order] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
        COMMIT; SELECT @payload response_payload;
    END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ReadHoldOptions
-- Latest Source Migration: 029_hold_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_ReadHoldOptions @actor_id uniqueidentifier,@auth_version int,@event_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 10000;
    IF @@TRANCOUNT<>0 THROW 51310,'Facade requires autocommit',1;
    EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @dummy int;
        SELECT @dummy=1 FROM dbo.tc_events WITH(HOLDLOCK) WHERE id=@event_id;
        SELECT @dummy=1 FROM dbo.tc_users WITH(HOLDLOCK) WHERE id=@actor_id;
        EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
        DECLARE @now datetime2(3)=SYSUTCDATETIME(),@payload nvarchar(max);
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_events e JOIN dbo.tc_organizations o WITH(HOLDLOCK) ON o.id=e.organization_id
            WHERE e.id=@event_id AND e.status='PUBLISHED' AND o.status='APPROVED' AND @now>=e.sale_start AND @now<e.sale_end)
            THROW 51304,'Event not found or not on sale',1;
        SELECT @payload=(SELECT LOWER(CONVERT(char(36),e.id)) eventId,e.title,CONVERT(varchar(23),e.sale_end,126)+'Z' saleEnd,
            CONVERT(varchar(23),@now,126)+'Z' serverTime,JSON_QUERY(COALESCE((SELECT LOWER(CONVERT(char(36),z.id)) id,z.name,z.type,
                CONVERT(varchar(30),z.price) unitPrice,CASE WHEN z.type='STANDING' THEN z.capacity-z.held_quantity-z.sold_quantity END availableStanding,
                JSON_QUERY(COALESCE((SELECT LOWER(CONVERT(char(36),s.id)) id,s.label FROM dbo.tc_seats s
                    WHERE s.zone_id=z.id AND s.status='AVAILABLE' ORDER BY s.row_name,s.seat_number FOR JSON PATH),N'[]')) availableSeats
                FROM dbo.tc_zones z WHERE z.event_id=e.id ORDER BY z.id FOR JSON PATH,INCLUDE_NULL_VALUES),N'[]')) zones
            FROM dbo.tc_events e WHERE e.id=@event_id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
        COMMIT;
        SELECT @payload response_payload;
    END TRY BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ReadManagedCoupon
-- Latest Source Migration: 043_coupon_admin.sql
CREATE OR ALTER PROCEDURE dbo.usp_ReadManagedCoupon
    @coupon_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @organization_id uniqueidentifier;
    SELECT @organization_id=organization_id FROM dbo.tc_coupons WHERE id=@coupon_id;
    IF @organization_id IS NULL THROW 51523,'Coupon not found',1;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    SELECT c.id,c.organization_id,o.name AS org_name,c.code,c.discount_type,
           c.percentage_value,c.fixed_amount,c.max_discount_amount,c.max_uses,
           u.reserved_uses,u.consumed_uses,u.available_uses,
           c.valid_from,c.valid_to,c.active,c.version
    FROM dbo.tc_coupons c JOIN dbo.vw_CouponUsage u ON u.coupon_id=c.id
    JOIN dbo.tc_organizations o ON o.id=c.organization_id WHERE c.id=@coupon_id;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ReadOrderCouponEligibility
-- Latest Source Migration: 030_order_runtime.sql
CREATE OR ALTER PROCEDURE dbo.usp_ReadOrderCouponEligibility @actor_id uniqueidentifier,@auth_version int,@order_id uniqueidentifier,@coupon_code nvarchar(80)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @@TRANCOUNT<>0 THROW 51410,'Facade owns transaction',1;
    BEGIN TRANSACTION;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier,@hold_id uniqueidentifier,@owner_id uniqueidentifier,@old_coupon uniqueidentifier,@new_coupon uniqueidentifier,
            @locked_new uniqueidentifier,@org uniqueidentifier,@now datetime2(3),@reason varchar(30),@discount decimal(19,0),@remaining int,@valid_until datetime2(3),@payload nvarchar(max);
        SELECT @event_id=event_id,@hold_id=hold_id,@owner_id=user_id,@old_coupon=coupon_id FROM dbo.tc_orders WHERE id=@order_id;
        EXEC dbo.usp_HoldLockSelection @actor_id,@event_id,N'[]',@hold_id,@include_active=0;
        EXEC dbo.usp_HoldAuthorize @actor_id,@auth_version;
        IF @owner_id IS NULL THROW 51404,'Order not found',1;
        IF @owner_id<>@actor_id THROW 51403,'Order owner required',1;
        SELECT @org=organization_id FROM dbo.tc_events WHERE id=@event_id;
        SELECT @old_coupon=coupon_id FROM dbo.tc_orders WHERE id=@order_id;
        SELECT @new_coupon=id FROM dbo.tc_coupons WHERE organization_id=@org AND code=@coupon_code;
        SET @locked_new=@new_coupon;
        EXEC dbo.usp_OrderLockCoupons @old_coupon,@new_coupon;
        DECLARE @quota_dummy int;
        SELECT @quota_dummy=COUNT(*) FROM dbo.tc_coupon_redemptions WITH(UPDLOCK,HOLDLOCK) WHERE coupon_id=@new_coupon AND status IN('RESERVED','CONSUMED');
        IF EXISTS(SELECT 1 FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE order_id=@order_id AND status IN('PENDING','UNKNOWN')) THROW 51407,'Payment unresolved',1;
        SET @now=SYSUTCDATETIME();
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_orders o JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id WHERE o.id=@order_id AND o.status='PENDING_PAYMENT' AND h.status='ACTIVE' AND h.expires_at>@now)
            THROW 51406,'Order is terminal or expired',1;
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND status='PUBLISHED' AND @now>=sale_start AND @now<sale_end) THROW 51406,'Event is not on sale',1;
        EXEC dbo.usp_OrderCouponDecision @order_id,@coupon_code,@now,@new_coupon OUTPUT,@reason OUTPUT,@discount OUTPUT,@remaining OUTPUT,@valid_until OUTPUT;
        IF ISNULL(@new_coupon,'00000000-0000-0000-0000-000000000000')<>ISNULL(@locked_new,'00000000-0000-0000-0000-000000000000') THROW 51309,'Coupon preflight changed',1;
        SELECT @payload=(SELECT CONVERT(varchar(23),@now,126)+'Z' serverTime,LOWER(CONVERT(char(36),@order_id)) orderId,@coupon_code couponCode,
            CAST(CASE WHEN @reason='ELIGIBLE' THEN 1 ELSE 0 END AS bit) eligible,@reason reason,@remaining remainingUses,
            CASE WHEN @reason='ELIGIBLE' THEN CONVERT(varchar(30),@discount) END discountAmount,
            CASE WHEN @reason='ELIGIBLE' THEN (SELECT CONVERT(varchar(30),subtotal_amount-@discount) FROM dbo.tc_orders WHERE id=@order_id) END totalAmount,
            CASE WHEN @reason='ELIGIBLE' THEN CONVERT(varchar(23),@valid_until,126)+'Z' END validUntil FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
        COMMIT; SELECT @payload response_payload;
    END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_RecalculateSettlement
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER PROCEDURE dbo.usp_RecalculateSettlement @event_id uniqueidentifier,@actor_id uniqueidentifier,@auth_version int=NULL,@emit_result bit=1
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id) THROW 52704,'EVENT_NOT_FOUND',1;
 DECLARE @settlement_id uniqueidentifier,@status varchar(20),@lock_count bigint;
 SELECT @settlement_id=id,@status=status FROM dbo.tc_settlements WITH(UPDLOCK,HOLDLOCK) WHERE event_id=@event_id;
 IF @status IS NOT NULL AND @status<>'DRAFT' THROW 52709,'SETTLEMENT_NOT_DRAFT',1;
 -- Lock relevant ranges so ordinary owner writes cannot race confirmation, even if their SP omits the event lock.
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.vw_VuongOrders WITH(UPDLOCK,HOLDLOCK) WHERE event_id=@event_id;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.vw_VuongPayments p WITH(UPDLOCK,HOLDLOCK) JOIN dbo.vw_VuongOrders o ON o.id=p.order_id WHERE o.event_id=@event_id;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.vw_VuongRefunds r WITH(UPDLOCK,HOLDLOCK) JOIN dbo.vw_VuongOrders o ON o.id=r.order_id WHERE o.event_id=@event_id;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_refund_transfer_logs l WITH(UPDLOCK,HOLDLOCK) JOIN dbo.vw_VuongRefunds r ON r.id=l.refund_id JOIN dbo.vw_VuongOrders o ON o.id=r.order_id WHERE o.event_id=@event_id;
 IF EXISTS(SELECT 1 FROM dbo.vw_OrderFinancialSummary WHERE event_id=@event_id AND status='PAID' AND (commission_amount IS NULL OR remaining_amount<0 OR (total_amount>0 AND ticket_revenue=0))) THROW 52702,'INVALID_FINANCIALS',1;
 IF EXISTS(SELECT 1 FROM dbo.vw_OrderFinancialSummary WHERE event_id=@event_id AND status='PAID') BEGIN
 IF @settlement_id IS NULL BEGIN SET @settlement_id=NEWID(); INSERT dbo.tc_settlements(id,event_id,status,gross_revenue,total_refund,total_commission,paid_amount,pending_amount,created_at,updated_at) VALUES(@settlement_id,@event_id,'DRAFT',0,0,0,0,0,SYSUTCDATETIME(),SYSUTCDATETIME()); END;
 DELETE dbo.tc_settlement_order_snapshots WHERE settlement_id=@settlement_id;
 INSERT dbo.tc_settlement_order_snapshots(id,settlement_id,order_id,gross_amount,refund_amount,commission_amount,net_amount,created_at,updated_at)
 SELECT NEWID(),@settlement_id,order_id,ticket_revenue,ticket_refund,commission_amount,net_amount,SYSUTCDATETIME(),SYSUTCDATETIME() FROM dbo.vw_OrderFinancialSummary WHERE event_id=@event_id AND status='PAID';
 EXEC dbo.usp_VuongUpdateSettlementTotals @settlement_id;
 INSERT dbo.vw_VuongAuditLogs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'SETTLEMENT_RECALCULATED','Settlement',CONVERT(varchar(36),@settlement_id),(SELECT @event_id eventId FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END ELSE IF @settlement_id IS NOT NULL THROW 52709,'SETTLEMENT_SOURCE_MISSING',1;
 IF @emit_result=1 BEGIN
 IF @settlement_id IS NULL SELECT CAST(NULL AS uniqueidentifier) settlement_id,@event_id event_id,CAST('DRAFT' AS varchar(20)) status,CAST(0 AS decimal(19,0)) gross_revenue,CAST(0 AS decimal(19,0)) total_refund,CAST(0 AS decimal(19,0)) total_commission,CAST(0 AS decimal(19,0)) net_payable,CAST(0 AS decimal(19,0)) paid_amount,CAST(0 AS decimal(19,0)) pending_amount,CAST(0 AS decimal(19,0)) available_to_pay,CAST(NULL AS datetime2(3)) confirmed_at,CAST(0 AS bit) is_replay;
 ELSE SELECT settlement_id,event_id,status,gross_revenue,total_refund,total_commission,net_payable,paid_amount,pending_amount,available_to_pay,confirmed_at,CAST(0 AS bit) is_replay FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id=@settlement_id;
 END;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_RecordPayout
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER PROCEDURE dbo.usp_RecordPayout
 @settlement_id uniqueidentifier,@payout_id uniqueidentifier,@actor_id uniqueidentifier,@amount decimal(19,0),@reference nvarchar(200)=NULL,@verified_result varchar(20)=NULL,@fingerprint varbinary(32)=NULL,@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 IF @payout_id IS NULL OR @amount IS NULL OR @amount<=0 OR (@verified_result IS NOT NULL AND @verified_result NOT IN('SUCCEEDED','FAILED','UNKNOWN')) THROW 52702,'PAYOUT_INVALID',1;
 IF @fingerprint IS NULL SET @fingerprint=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@settlement_id),'|',@amount,'|',COALESCE(@reference,'')));
 IF DATALENGTH(@fingerprint)<>32 THROW 52702,'PAYOUT_INVALID',1;
 DECLARE @old varchar(30),@replay bit=0,@state varchar(20),@net decimal(19,0),@paid decimal(19,0),@pending decimal(19,0);
 SELECT @state=status,@net=net_payable,@paid=paid_amount,@pending=pending_amount FROM dbo.tc_settlements WITH(UPDLOCK,HOLDLOCK) WHERE id=@settlement_id;
 IF @state IS NULL THROW 52704,'SETTLEMENT_NOT_FOUND',1;
 SELECT @old=status FROM dbo.tc_settlement_transfer_logs WITH(UPDLOCK,HOLDLOCK) WHERE id=@payout_id;
 IF @old IS NOT NULL BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs WHERE id=@payout_id AND settlement_id=@settlement_id AND amount=@amount AND request_fingerprint=@fingerprint AND (@verified_result IS NOT NULL OR requested_reference=@reference OR (requested_reference IS NULL AND @reference IS NULL))) THROW 52709,'PAYOUT_TERMS_CONFLICT',1;
 IF @old=@verified_result AND NOT EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs WHERE id=@payout_id AND (provider_reference=@reference OR (provider_reference IS NULL AND @reference IS NULL))) THROW 52709,'PAYOUT_RESULT_CONFLICT',1;
 IF @verified_result IS NULL OR (@old=@verified_result) OR (@verified_result='UNKNOWN' AND @old='PENDING' AND EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs WHERE id=@payout_id AND verification_state='UNKNOWN')) SET @replay=1;
 ELSE IF @old IN('SUCCEEDED','FAILED') THROW 52709,'PAYOUT_RESULT_CONFLICT',1;
 ELSE BEGIN
 UPDATE dbo.tc_settlement_transfer_logs SET status=CASE WHEN @verified_result='UNKNOWN' THEN 'PENDING' ELSE @verified_result END,result=CASE WHEN @verified_result='UNKNOWN' THEN 'PENDING' ELSE @verified_result END,
 verification_state=@verified_result,provider_reference=CASE WHEN @verified_result='UNKNOWN' THEN provider_reference ELSE @reference END,processed_at=CASE WHEN @verified_result='UNKNOWN' THEN NULL ELSE SYSUTCDATETIME() END WHERE id=@payout_id;
 INSERT dbo.vw_VuongAuditLogs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'PAYOUT_RESULT','SettlementTransferLog',CONVERT(varchar(36),@payout_id),(SELECT @settlement_id settlementId,@amount amount,@verified_result result FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 END ELSE BEGIN
 IF @verified_result IS NOT NULL THROW 52709,'PAYOUT_RESERVATION_REQUIRED',1;
 IF @state<>'CONFIRMED' THROW 52709,'SETTLEMENT_NOT_CONFIRMED',1;
 -- PENDING can mean the provider already transferred money but its result was not committed.
 -- Reconcile/replay that same ID before allowing a second provider attempt.
 IF EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=@settlement_id AND status='PENDING') THROW 52709,'PAYOUT_RECONCILIATION_REQUIRED',1;
 IF @net-@paid-@pending<@amount THROW 52709,'PAYOUT_EXCEEDS_BALANCE',1;
 INSERT dbo.tc_settlement_transfer_logs(id,settlement_id,amount,status,result,request_fingerprint,requested_reference,verification_state,created_at)
 VALUES(@payout_id,@settlement_id,@amount,'PENDING','PENDING',@fingerprint,@reference,'PENDING',SYSUTCDATETIME());
 INSERT dbo.vw_VuongAuditLogs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'PAYOUT_RESERVED','SettlementTransferLog',CONVERT(varchar(36),@payout_id),(SELECT @settlement_id settlementId,@amount amount FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 SELECT l.id payout_id,l.settlement_id,l.amount,l.status,l.verification_state,l.provider_reference,l.processed_at,@replay is_replay,
 s.status settlement_status,s.net_payable,s.paid_amount,s.pending_amount,CAST(s.net_payable-s.paid_amount-s.pending_amount AS decimal(19,0)) available_to_pay
 FROM dbo.tc_settlement_transfer_logs l JOIN dbo.tc_settlements s ON s.id=l.settlement_id WHERE l.id=@payout_id;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_RegisterVnpayPaymentProvenance
-- Latest Source Migration: 063_vnpay_sandbox_refund_provenance.sql
CREATE OR ALTER PROCEDURE dbo.usp_RegisterVnpayPaymentProvenance
 @payment_id uniqueidentifier,@actor_id uniqueidentifier,@tmn_code varchar(8),@transaction_date char(14)
WITH EXECUTE AS OWNER
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF LEN(@tmn_code)<>8 OR @tmn_code LIKE '%[^a-zA-Z0-9]%' OR @transaction_date LIKE '%[^0-9]%' OR LEN(@transaction_date)<>14
  THROW 53600,'Invalid sandbox payment provenance',1;
 BEGIN TRAN;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_payments p WITH(UPDLOCK,HOLDLOCK)
  JOIN dbo.tc_orders o ON o.id=p.order_id WHERE p.id=@payment_id AND o.user_id=@actor_id
  AND p.status IN('PENDING','UNKNOWN') AND p.amount>0 AND p.currency='VND')
 BEGIN ROLLBACK; THROW 53601,'Sandbox payment provenance requires an owned pending payment',1; END;
 IF EXISTS(SELECT 1 FROM dbo.tc_vnpay_payment_provenance WHERE payment_id=@payment_id
  AND (tmn_code<>@tmn_code OR transaction_date<>@transaction_date))
 BEGIN ROLLBACK; THROW 53602,'Payment provenance is immutable',1; END;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_vnpay_payment_provenance WHERE payment_id=@payment_id)
  INSERT dbo.tc_vnpay_payment_provenance(payment_id,tmn_code,environment,transaction_date)
   VALUES(@payment_id,@tmn_code,'SANDBOX',@transaction_date);
 COMMIT;
END;
GO

-- Object: [PROCEDURE] dbo.usp_ReleaseTicketHold
-- Latest Source Migration: 035_sql_report_runtime.sql
/* Stored Procedure: usp_ReleaseTicketHold */
CREATE OR ALTER PROCEDURE dbo.usp_ReleaseTicketHold
    @hold_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @reason varchar(30), @suppress_result bit=0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp07;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @user_id uniqueidentifier, @status varchar(20), @expires datetime2(3), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id = h.event_id, @user_id = h.user_id FROM dbo.tc_ticket_holds h WHERE h.id = @hold_id;
        IF @event_id IS NULL THROW 51270, 'Ticket hold not found', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @status = status, @expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;
        IF @status <> 'ACTIVE' BEGIN
            IF @suppress_result=0 SELECT @hold_id hold_id, @status hold_status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @reason NOT IN ('USER_CANCEL', 'EXPIRED', 'EVENT_CANCELLED') THROW 51271, 'Invalid release reason', 1;
        IF @reason = 'USER_CANCEL' AND (@actor_id IS NULL OR @actor_id <> @user_id) THROW 51272, 'Hold owner required', 1;
        IF @reason = 'EXPIRED' AND @now < @expires THROW 51273, 'Hold has not expired', 1;
        IF @reason = 'EVENT_CANCELLED' AND NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND status = 'CANCELLED')
            THROW 51274, 'Event is not cancelled', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_ticket_hold_items hi WHERE hi.hold_id = @hold_id AND hi.zone_id = z.id);
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_ticket_hold_items hi WHERE hi.hold_id = @hold_id AND hi.seat_id = s.id);
        UPDATE s SET status = 'AVAILABLE', version = version + 1 FROM dbo.tc_seats s
        JOIN dbo.tc_ticket_hold_items hi ON hi.seat_id = s.id WHERE hi.hold_id = @hold_id AND s.status = 'HELD';
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (
                SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_ticket_hold_items
                WHERE hold_id = @hold_id AND seat_id IS NULL GROUP BY zone_id
            ) hi ON hi.zone_id = z.id WHERE z.held_quantity < hi.quantity
        ) THROW 51275, 'Held standing inventory is inconsistent', 1;
        UPDATE z SET held_quantity = held_quantity - hi.quantity, version = version + 1
        FROM dbo.tc_zones z JOIN (
            SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_ticket_hold_items WHERE hold_id = @hold_id AND seat_id IS NULL GROUP BY zone_id
        ) hi ON hi.zone_id = z.id WHERE z.held_quantity >= hi.quantity;
        UPDATE dbo.tc_coupon_redemptions SET status = 'RELEASED', released_at = @now
        WHERE order_id IN (SELECT id FROM dbo.tc_orders WHERE hold_id = @hold_id) AND status = 'RESERVED';
        UPDATE dbo.tc_orders SET status = CASE WHEN @reason = 'EXPIRED' THEN 'EXPIRED' ELSE 'CANCELLED' END, version = version + 1
        WHERE hold_id = @hold_id AND status = 'PENDING_PAYMENT';
        UPDATE dbo.tc_ticket_holds SET status = 'RELEASED', version = version + 1 WHERE id = @hold_id AND status = 'ACTIVE';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, 'TICKET_HOLD_RELEASED', 'TICKET_HOLD', @hold_id, @reason);
        IF @started = 1 COMMIT TRANSACTION;
        IF @suppress_result=0 SELECT @hold_id hold_id, CAST('RELEASED' AS varchar(20)) hold_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp07;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_RequestTicketRefund
-- Latest Source Migration: 006_create_procedures.sql
/* Stored Procedure: usp_RequestTicketRefund */
CREATE OR ALTER PROCEDURE dbo.usp_RequestTicketRefund
    @order_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @ticket_ids nvarchar(max),
    @reason_type varchar(30),
    @reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON;
    IF @reason_type <> 'CUSTOMER_REQUEST'
        THROW 52620, 'Only CUSTOMER_REQUEST is accepted from a buyer', 1;
    EXEC dbo.usp_CreateRefundRequest
        @order_id = @order_id, @actor_id = @actor_id, @ticket_ids = @ticket_ids, @reason = @reason;
END;
GO

-- Object: [PROCEDURE] dbo.usp_RequireActiveMember
-- Latest Source Migration: 032_sql_report_permissions.sql
-- Minimal internal lookup permits inviting a user who is not a member yet;
-- it returns no personal data and has no direct runtime EXECUTE grant.
CREATE OR ALTER PROCEDURE dbo.usp_RequireActiveMember @user_id uniqueidentifier WITH EXECUTE AS OWNER
AS BEGIN SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@user_id AND status='ACTIVE') THROW 51817,'Active member required',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_RequireOrganizationManager
-- Latest Source Migration: 032_sql_report_permissions.sql
CREATE OR ALTER PROCEDURE dbo.usp_RequireOrganizationManager @actor_id uniqueidentifier,@organization_id uniqueidentifier
AS BEGIN SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id=m.user_id
 WHERE m.user_id=@actor_id AND m.organization_id=@organization_id AND m.role='MANAGER' AND m.active=1 AND u.status='ACTIVE')
 THROW 51810,'Active manager of this organization required',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_RetryPaymentCompensation
-- Latest Source Migration: 060_compensation_retry_and_neutral_views.sql
CREATE OR ALTER PROCEDURE dbo.usp_RetryPaymentCompensation
 @payment_id uniqueidentifier,@actor_id uniqueidentifier,@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @started=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp_comp_retry;
 BEGIN TRY
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND platform_role='ADMIN'
                AND (@auth_version IS NULL OR auth_version=@auth_version)) THROW 52810,'ADMIN_REQUIRED',1;
  DECLARE @order_id uniqueidentifier,@amount decimal(19,0),@id uniqueidentifier=NEWID();
  SELECT @order_id=order_id,@amount=amount FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment_id AND status='CAPTURED';
  IF @order_id IS NULL THROW 52811,'PAYMENT_NOT_FOUND',1;
  /* Only a payment whose every compensation attempt failed qualifies; an open or successful one blocks the retry. */
  IF NOT EXISTS(SELECT 1 FROM dbo.vw_FailedCompensationAttempts WHERE payment_id=@payment_id) THROW 52812,'COMPENSATION_NOT_RETRYABLE',1;
  INSERT dbo.tc_refund_obligations(id,order_id,payment_id,amount,purpose,status,current_attempt_id)
  VALUES(@id,@order_id,@payment_id,@amount,'PAYMENT_COMPENSATION','PROCESSING',@id);
  INSERT dbo.tc_refund_transfer_logs(id,refund_id,attempt_no,status,result) VALUES(@id,@id,1,'PENDING','PENDING');
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail)
  VALUES(@actor_id,'PAYMENT_COMPENSATION_RETRY','PAYMENT',@payment_id,CONCAT(N'{"compensationId":"',@id,N'"}'));
  IF @started=1 COMMIT TRANSACTION;
  SELECT @id compensation_id,@payment_id payment_id,CAST('PENDING' AS varchar(20)) status;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK TRANSACTION;
  ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_sp_comp_retry;
  THROW;
 END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_ReviewRefundRequest
-- Latest Source Migration: 050_consolidate_canonical_tables.sql
CREATE OR ALTER PROCEDURE dbo.usp_ReviewRefundRequest
    @request_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @decision varchar(20),
    @rejection_reason nvarchar(1000) = NULL, @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp10;
    BEGIN TRY
        IF @decision NOT IN ('APPROVE', 'REJECT') THROW 51400, 'Invalid refund decision', 1;
        DECLARE @order_id uniqueidentifier, @event_id uniqueidentifier, @reason_type varchar(30), @status varchar(20),
                @event_status varchar(30), @amount decimal(19,0), @refund_id uniqueidentifier, @payment_id uniqueidentifier,
                @now datetime2(3) = SYSUTCDATETIME();
        SELECT @order_id=order_id FROM dbo.tc_refund_obligations WHERE id=@request_id AND purpose='CUSTOMER_REFUND';
        SELECT @event_id=event_id FROM dbo.tc_orders WHERE id=@order_id;
        IF @event_id IS NULL THROW 51401,'Refund request not found',1;
        SELECT @event_status=status FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
        IF @actor_id IS NOT NULL SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@actor_id;
        SELECT @payment_id=accepted_payment_id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment_id;
        SELECT @reason_type=reason_type,@status=request_status FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@request_id;
        SET @now=SYSUTCDATETIME();
        IF @actor_id IS NULL BEGIN
            IF @decision<>'APPROVE' OR @reason_type<>'EVENT_CANCELLATION' OR @event_status<>'CANCELLED'
               OR (COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1) THROW 51402,'System approval only for cancelled event',1;
        END ELSE IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@actor_id AND platform_role='ADMIN' AND status='ACTIVE') THROW 51402,'Administrator role required',1;
        IF @reason_type = 'EVENT_CANCELLATION' AND @event_status <> 'CANCELLED' THROW 51403, 'Cancellation request requires cancelled event', 1;
        IF @status IN ('REJECTED', 'COMPLETED') BEGIN
            IF (@status = 'REJECTED' AND @decision <> 'REJECT') OR (@status = 'COMPLETED' AND @decision <> 'APPROVE')
                THROW 51404, 'Refund request already has the opposite final decision', 1;
            SELECT @request_id request_id, @status request_status, CAST(NULL AS uniqueidentifier) refund_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
        WHERE rt.refund_id = @request_id;
        IF @decision = 'REJECT' BEGIN
            IF NULLIF(LTRIM(RTRIM(@rejection_reason)), N'') IS NULL THROW 51405, 'Rejection reason is required', 1;
            UPDATE t SET status = CASE WHEN @event_status = 'CANCELLED' THEN 'INVALIDATED' ELSE 'ACTIVE' END, version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id AND t.status = 'REFUND_PENDING';
            UPDATE dbo.tc_refund_tickets SET is_open = 0 WHERE refund_id = @request_id;
            UPDATE dbo.tc_refund_obligations SET status = 'REJECTED', reviewer_id = @actor_id, decided_at = @now,
                   rejection_reason = @rejection_reason, version = version + 1, updated_at = @now WHERE id = @request_id;
        END ELSE BEGIN
            SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id;
            IF @amount = 0 BEGIN
                SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                    SELECT 1 FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_id = @request_id AND oi.zone_id = z.id);
                UPDATE s SET status = 'AVAILABLE', version = s.version + 1 FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
                JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
                WHERE rt.refund_id = @request_id AND s.status = 'SOLD';
                UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_id = @request_id AND oi.seat_id IS NULL GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id;
                UPDATE t SET status = 'REFUNDED', version = t.version + 1 FROM dbo.tc_tickets t
                JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id WHERE rt.refund_id = @request_id;
                UPDATE dbo.tc_refund_tickets SET is_open = 0 WHERE refund_id = @request_id;
                UPDATE dbo.tc_refund_obligations SET status = 'COMPLETED', reviewer_id = @actor_id, decided_at = @now, version = version + 1, updated_at = @now WHERE id = @request_id;
            END ELSE BEGIN
                IF EXISTS (SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE refund_id = @request_id AND status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED')) BEGIN
                    SELECT TOP (1) @refund_id = id FROM dbo.tc_refund_transfer_logs WHERE refund_id = @request_id ORDER BY attempt_no DESC;
                END ELSE BEGIN
                    IF @retry_failed = 0 AND EXISTS (SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE refund_id = @request_id AND status = 'FAILED')
                        THROW 51406, 'Refund retry requires explicit authorization', 1;
                    SELECT @payment_id=accepted_payment_id FROM dbo.tc_orders WHERE id=@order_id;
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND order_id=@order_id AND status='CAPTURED') SET @payment_id=NULL;
                    IF @payment_id IS NULL THROW 51407, 'Captured payment not found', 1;
                    IF (SELECT COALESCE(SUM(amount),0) FROM dbo.vw_RefundAttemptLedger WHERE payment_id=@payment_id AND status IN('PENDING','UNKNOWN','SUCCEEDED'))+@amount>(SELECT amount FROM dbo.tc_payments WHERE id=@payment_id) THROW 51805,'Refund reservation exceeds captured amount',1;
                    IF EXISTS(SELECT 1 FROM dbo.tc_gateway_refund_receipts WHERE payment_id=@payment_id AND resolution='NEEDS_RECONCILIATION') THROW 51806,'Gateway reconciliation is required before retry',1;
                    SET @refund_id = NEWID();
                    INSERT dbo.tc_refund_transfer_logs(id, refund_id, attempt_no, status, result, created_at)
                    SELECT @refund_id, @request_id, COALESCE(MAX(attempt_no), 0) + 1, 'PENDING', 'PENDING', @now
                    FROM dbo.tc_refund_transfer_logs WHERE refund_id = @request_id;
                    UPDATE dbo.tc_refund_obligations SET payment_id = @payment_id, amount = @amount, status = 'PROCESSING',
                           current_attempt_id = @refund_id, provider_reference = NULL, processed_at = NULL, updated_at = @now
                    WHERE id = @request_id;
                END;
                UPDATE dbo.tc_refund_obligations SET reviewer_id = @actor_id,
                       decided_at = COALESCE(decided_at, @now), version = version + 1, updated_at = @now WHERE id = @request_id;
            END;
        END;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT('REFUND_REQUEST_', @decision), 'REFUND_REQUEST', @request_id, @rejection_reason);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @request_id request_id, request_status, @refund_id refund_id FROM dbo.tc_refund_obligations WHERE id = @request_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp10;
        THROW;
    END CATCH
END;
GO

-- Object: [PROCEDURE] dbo.usp_SaveEventDraft
-- Latest Source Migration: 051_event_single_image_url.sql
CREATE OR ALTER PROCEDURE dbo.usp_SaveEventDraft
 @event_id uniqueidentifier,@organization_id uniqueidentifier,@actor_id uniqueidentifier,@create bit,@expected_version bigint,
 @category_id uniqueidentifier=NULL,@title nvarchar(200)=NULL,@description nvarchar(max)=NULL,
 @venue_name nvarchar(200)=NULL,@venue_address nvarchar(500)=NULL,@sale_start datetime2(3)=NULL,@sale_end datetime2(3)=NULL,
 @start_time datetime2(3)=NULL,@end_time datetime2(3)=NULL,@image_url varchar(2048)=NULL,@image_public_id varchar(255)=NULL,@cover_only bit=0
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END,@status varchar(30),@version bigint;
 IF @started=1 BEGIN TRANSACTION;
 BEGIN TRY
  EXEC dbo.usp_RequireOrganizationManager @actor_id,@organization_id;
  IF @create=1 BEGIN
   IF @cover_only=1 THROW 51811,'Draft fields required',1;
   INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status,version,event_image,event_image_public_id)
   VALUES(@event_id,@organization_id,@category_id,@title,@description,@venue_name,@venue_address,@sale_start,@sale_end,@start_time,@end_time,'DRAFT',0,@image_url,@image_public_id);
  END ELSE BEGIN
   SELECT @status=status,@version=version FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id AND organization_id=@organization_id;
   IF @status IS NULL OR @status NOT IN('DRAFT','REJECTED','PUBLISHED') OR (@status='PUBLISHED' AND @cover_only<>1) THROW 51812,'Only editable drafts or the cover of a published event may be changed',1;
   IF @version<>@expected_version THROW 51813,'Event version conflict',1;
   UPDATE dbo.tc_events SET
    category_id=CASE WHEN @cover_only=1 THEN category_id ELSE @category_id END,
    title=CASE WHEN @cover_only=1 THEN title ELSE @title END,description=CASE WHEN @cover_only=1 THEN description ELSE @description END,
    venue_name=CASE WHEN @cover_only=1 THEN venue_name ELSE @venue_name END,venue_address=CASE WHEN @cover_only=1 THEN venue_address ELSE @venue_address END,
    sale_start=CASE WHEN @cover_only=1 THEN sale_start ELSE @sale_start END,sale_end=CASE WHEN @cover_only=1 THEN sale_end ELSE @sale_end END,
    start_time=CASE WHEN @cover_only=1 THEN start_time ELSE @start_time END,end_time=CASE WHEN @cover_only=1 THEN end_time ELSE @end_time END,
    event_image=COALESCE(@image_url,event_image),event_image_public_id=COALESCE(@image_public_id,event_image_public_id),version=version+1
   WHERE id=@event_id;
  END;
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id) VALUES(@actor_id,CASE WHEN @create=1 THEN 'EVENT_DRAFT_CREATED' ELSE 'EVENT_DRAFT_UPDATED' END,'EVENT',@event_id);
  IF @started=1 COMMIT;
 END TRY BEGIN CATCH IF @started=1 AND XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_SetManagedCouponActive
-- Latest Source Migration: 043_coupon_admin.sql
CREATE OR ALTER PROCEDURE dbo.usp_SetManagedCouponActive
    @coupon_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int, @active bit
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @organization_id uniqueidentifier;
    SELECT @organization_id=organization_id FROM dbo.tc_coupons WHERE id=@coupon_id;
    IF @organization_id IS NULL THROW 51523,'Coupon not found',1;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    UPDATE dbo.tc_coupons SET active=@active,version=version+1
    WHERE id=@coupon_id AND organization_id=@organization_id;
    IF @@ROWCOUNT<>1 THROW 51523,'Coupon not found',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_UpdateCommissionRule
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER PROCEDURE dbo.usp_UpdateCommissionRule
 @rule_id uniqueidentifier,@organization_id uniqueidentifier,@actor_id uniqueidentifier,@idempotency_key uniqueidentifier,@rate_percent decimal(12,4),@fixed_fee decimal(19,0),@effective_from datetime2(3),@effective_to datetime2(3),@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 IF @idempotency_key IS NULL OR @rate_percent IS NULL OR @rate_percent<0 OR @rate_percent>100 OR @fixed_fee IS NULL OR @fixed_fee<0 OR @effective_from IS NULL OR @effective_to IS NULL OR @effective_from>=@effective_to THROW 52702,'COMMISSION_RULE_INVALID',1;
 DECLARE @fingerprint binary(32)=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@rule_id),'|',CONVERT(varchar(36),@organization_id),'|',@rate_percent,'|',@fixed_fee,'|',CONVERT(varchar(30),@effective_from,126),'|',CONVERT(varchar(30),@effective_to,126))),@replay bit=0;
 IF EXISTS(SELECT 1 FROM dbo.tc_finance_mutations WITH(UPDLOCK,HOLDLOCK) WHERE idempotency_key=@idempotency_key) BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_finance_mutations WHERE idempotency_key=@idempotency_key AND actor_id=@actor_id AND operation='COMMISSION_UPDATE' AND fingerprint=@fingerprint AND resource_id=@rule_id) THROW 52709,'IDEMPOTENCY_CONFLICT',1;
 SET @replay=1;
 END ELSE BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_commission_rules WITH(UPDLOCK,HOLDLOCK) WHERE id=@rule_id AND organization_id=@organization_id) THROW 52704,'COMMISSION_RULE_NOT_FOUND',1;
 IF EXISTS(SELECT 1 FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE commission_rule_id=@rule_id) THROW 52709,'RULE_ALREADY_ATTACHED_TO_EVENTS',1;
 UPDATE dbo.tc_commission_rules SET rate_percent=@rate_percent,fixed_fee=@fixed_fee,effective_from=@effective_from,effective_to=@effective_to,updated_at=SYSUTCDATETIME() WHERE id=@rule_id;
 INSERT dbo.tc_finance_mutations(idempotency_key,actor_id,operation,fingerprint,resource_id,response_json) VALUES(@idempotency_key,@actor_id,'COMMISSION_UPDATE',@fingerprint,@rule_id,
 (SELECT @organization_id organizationId,@rate_percent ratePercent,@fixed_fee fixedFee,@effective_from effectiveFrom,@effective_to effectiveTo FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 INSERT dbo.vw_VuongAuditLogs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'COMMISSION_UPDATED','CommissionRule',CONVERT(varchar(36),@rule_id),(SELECT @organization_id organizationId,@rate_percent ratePercent,@fixed_fee fixedFee FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 SELECT c.id rule_id,COALESCE(TRY_CONVERT(uniqueidentifier,JSON_VALUE(m.response_json,'$.organizationId')),c.organization_id) organization_id,
 COALESCE(TRY_CONVERT(decimal(12,4),JSON_VALUE(m.response_json,'$.ratePercent')),c.rate_percent) rate_percent,
 COALESCE(TRY_CONVERT(decimal(19,0),JSON_VALUE(m.response_json,'$.fixedFee')),c.fixed_fee) fixed_fee,
 COALESCE(TRY_CONVERT(datetime2(3),JSON_VALUE(m.response_json,'$.effectiveFrom')),c.effective_from) effective_from,
 COALESCE(TRY_CONVERT(datetime2(3),JSON_VALUE(m.response_json,'$.effectiveTo')),c.effective_to) effective_to,
 (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE commission_rule_id=c.id) associated_events_count,@replay is_replay FROM dbo.tc_commission_rules c JOIN dbo.tc_finance_mutations m ON m.resource_id=c.id AND m.idempotency_key=@idempotency_key WHERE c.id=@rule_id;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO

-- Object: [PROCEDURE] dbo.usp_UpdateManagedCoupon
-- Latest Source Migration: 043_coupon_admin.sql
CREATE OR ALTER PROCEDURE dbo.usp_UpdateManagedCoupon
    @coupon_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int,
    @max_uses int=NULL, @valid_to datetime2(3)=NULL, @max_discount_amount decimal(19,0)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @organization_id uniqueidentifier;
    SELECT @organization_id=organization_id FROM dbo.tc_coupons WHERE id=@coupon_id;
    IF @organization_id IS NULL THROW 51523,'Coupon not found',1;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    UPDATE dbo.tc_coupons SET max_uses=COALESCE(@max_uses,max_uses),
           valid_to=COALESCE(@valid_to,valid_to),
           max_discount_amount=COALESCE(@max_discount_amount,max_discount_amount),version=version+1
    WHERE id=@coupon_id AND organization_id=@organization_id;
    IF @@ROWCOUNT<>1 THROW 51523,'Coupon not found',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_UpdateMyProfile
-- Latest Source Migration: 059_profile_update.sql
CREATE OR ALTER PROCEDURE dbo.usp_UpdateMyProfile
 @actor_id uniqueidentifier,@full_name nvarchar(200),@phone nvarchar(30)=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @name nvarchar(200)=NULLIF(LTRIM(RTRIM(@full_name)),N''),@number nvarchar(30)=NULLIF(LTRIM(RTRIM(@phone)),N'');
 IF @name IS NULL OR LEN(@name)<2 THROW 52800,'Full name is required',1;
 IF @number IS NOT NULL AND (LEN(@number)<8 OR @number LIKE N'%[^0-9+ ]%') THROW 52800,'Phone number is invalid',1;
 UPDATE dbo.tc_users SET full_name=@name,phone=@number,version=version+1,updated_at=SYSUTCDATETIME()
 WHERE id=@actor_id AND status='ACTIVE';
 IF @@ROWCOUNT=0 THROW 52801,'Active user not found',1;
 SELECT id,email,user_name,full_name,phone FROM dbo.tc_users WHERE id=@actor_id;
END;
GO

-- Object: [PROCEDURE] dbo.usp_VuongRequireAdmin
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER PROCEDURE dbo.usp_VuongRequireAdmin @actor_id uniqueidentifier,@auth_version int=NULL AS
BEGIN
 SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND platform_role='ADMIN' AND (@auth_version IS NULL OR auth_version=@auth_version))
 THROW 52701,'ADMIN_REQUIRED',1;
END;
GO

-- Object: [PROCEDURE] dbo.usp_VuongUpdateSettlementTotals
-- Latest Source Migration: 027_vuong_finance.sql
/* Compile the private helper once for the physical layout. Runtime statements
   remain static and retain the ownership chain through denied base-table DML. */
DECLARE @totals_definition nvarchar(max)=N'CREATE OR ALTER PROCEDURE dbo.usp_VuongUpdateSettlementTotals @sid uniqueidentifier AS BEGIN SET NOCOUNT ON; UPDATE s SET gross_revenue=t.gross,total_refund=t.refund,total_commission=t.commission,';
IF COLUMNPROPERTY(OBJECT_ID('dbo.tc_settlements'),'net_payable','IsComputed')=0
 SET @totals_definition+=N'net_payable=t.gross-t.refund-t.commission,';
SET @totals_definition+=N'updated_at=SYSUTCDATETIME() FROM dbo.tc_settlements s CROSS APPLY(SELECT SUM(gross_amount) gross,SUM(refund_amount) refund,SUM(commission_amount) commission FROM dbo.tc_settlement_order_snapshots WHERE settlement_id=s.id) t WHERE s.id=@sid; END;';
EXEC(@totals_definition);
GO

/*
========================================================================================
SECTION: TRIGGERs (Total: 26)
========================================================================================
*/

-- Object: [TRIGGER] dbo.trg_AuditLog_AppendOnly
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_AuditLog_AppendOnly */
CREATE OR ALTER TRIGGER dbo.trg_AuditLog_AppendOnly ON dbo.tc_audit_logs
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted) THROW 51110, 'Audit log is append-only', 1;
END;
GO

-- Object: [TRIGGER] dbo.trg_CommissionRule_ProtectAppliedTerms
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_CommissionRule_ProtectAppliedTerms */
CREATE OR ALTER TRIGGER dbo.trg_CommissionRule_ProtectAppliedTerms ON dbo.tc_commission_rules
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        WHERE EXISTS (SELECT 1 FROM dbo.tc_events e WHERE e.commission_rule_id = d.id)
          AND (i.id IS NULL OR i.organization_id <> d.organization_id
               OR i.rate_percent <> d.rate_percent OR i.fixed_fee <> d.fixed_fee)
    ) THROW 51103, 'Applied commission terms are immutable', 1;
END;
GO

-- Object: [TRIGGER] dbo.trg_Coupon_ProtectUsageLimit
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_Coupon_ProtectUsageLimit */
CREATE OR ALTER TRIGGER dbo.trg_Coupon_ProtectUsageLimit ON dbo.tc_coupons
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        WHERE i.max_uses < (SELECT COUNT_BIG(*) FROM dbo.tc_coupon_redemptions r WITH (UPDLOCK, HOLDLOCK)
                            WHERE r.coupon_id = i.id AND r.status IN ('RESERVED', 'CONSUMED'))
    ) THROW 51107, 'Coupon max uses is below current usage', 1;
END;
GO

-- Object: [TRIGGER] dbo.trg_Event_AuditStatusChange
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_Event_AuditStatusChange */
CREATE OR ALTER TRIGGER dbo.trg_Event_AuditStatusChange ON dbo.tc_events
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
    SELECT TRY_CONVERT(uniqueidentifier, SESSION_CONTEXT(N'actor_id')), 'EVENT_STATUS_CHANGED', 'EVENT',
           i.id, CONCAT(N'{"from":"', d.status, N'","to":"', i.status, N'"}')
    FROM inserted i JOIN deleted d ON d.id = i.id WHERE i.status <> d.status;
END;
GO

-- Object: [TRIGGER] dbo.trg_Membership_ProtectLastManager
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_Membership_ProtectLastManager */
CREATE OR ALTER TRIGGER dbo.trg_Membership_ProtectLastManager ON dbo.tc_organization_memberships
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM (SELECT organization_id FROM deleted UNION SELECT organization_id FROM inserted) x
        WHERE NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships m WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_users u ON u.id = m.user_id
            WHERE m.organization_id = x.organization_id AND m.role = 'MANAGER'
              AND m.active = 1 AND u.status = 'ACTIVE'
        )
    ) THROW 51106, 'Organization must retain an active manager', 1;
END;
GO

-- Object: [TRIGGER] dbo.trg_OrderItem_ValidateEventSeat
-- Latest Source Migration: 038_sql_report_composite_foreign_keys.sql
CREATE OR ALTER TRIGGER dbo.trg_OrderItem_ValidateEventSeat ON dbo.tc_order_items AFTER INSERT,UPDATE AS
BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.tc_orders o ON o.id=i.order_id JOIN dbo.tc_zones z ON z.id=i.zone_id LEFT JOIN dbo.tc_seats s ON s.id=i.seat_id
 WHERE z.event_id<>o.event_id OR (i.event_id IS NOT NULL AND i.event_id<>o.event_id) OR (i.seat_id IS NOT NULL AND s.zone_id<>i.zone_id)) THROW 51820,'Order item zone or seat belongs to another event',1;
 UPDATE i SET event_id=o.event_id FROM dbo.tc_order_items i JOIN inserted n ON n.id=i.id JOIN dbo.tc_orders o ON o.id=i.order_id WHERE i.event_id IS NULL;
END;
GO

-- Object: [TRIGGER] dbo.trg_Seat_ProtectPublishedLayout
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_Seat_ProtectPublishedLayout */
CREATE OR ALTER TRIGGER dbo.trg_Seat_ProtectPublishedLayout ON dbo.tc_seats
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.tc_zones z ON z.id = i.zone_id JOIN dbo.tc_events e ON e.id = z.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED')
          AND (d.id IS NULL OR d.zone_id <> i.zone_id OR d.row_name <> i.row_name
               OR d.seat_number <> i.seat_number OR d.label <> i.label)
    ) OR EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        JOIN dbo.tc_zones z ON z.id = d.zone_id JOIN dbo.tc_events e ON e.id = z.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED') AND (i.id IS NULL OR i.zone_id <> d.zone_id)
    ) THROW 51102, 'Published event seat layout is immutable', 1;
END;
GO

-- Object: [TRIGGER] dbo.trg_SettlementOrderSnapshot_ProtectConfirmedAmounts
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_SettlementOrderSnapshot_ProtectConfirmedAmounts */
CREATE OR ALTER TRIGGER dbo.trg_SettlementOrderSnapshot_ProtectConfirmedAmounts
ON dbo.tc_settlement_order_snapshots AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT settlement_id FROM inserted
            UNION
            SELECT settlement_id FROM deleted
        ) affected
        JOIN dbo.tc_settlements s WITH (UPDLOCK, HOLDLOCK) ON s.id = affected.settlement_id
        WHERE s.status IN ('CONFIRMED', 'PAID')
    ) THROW 52640, 'Confirmed settlement order snapshots are immutable', 1;
END;
GO

-- Object: [TRIGGER] dbo.trg_Settlement_ProtectConfirmedSnapshot
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_Settlement_ProtectConfirmedSnapshot */
CREATE OR ALTER TRIGGER dbo.trg_Settlement_ProtectConfirmedSnapshot ON dbo.tc_settlements
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        WHERE d.status IN ('CONFIRMED', 'PAID')
          AND (i.id IS NULL OR i.event_id <> d.event_id OR i.created_at <> d.created_at
               OR ISNULL(i.confirmed_at, '19000101') <> ISNULL(d.confirmed_at, '19000101')
               OR i.gross_revenue <> d.gross_revenue OR i.total_refund <> d.total_refund
               OR i.total_commission <> d.total_commission OR i.net_payable <> d.net_payable
               OR NOT (d.status = 'CONFIRMED' AND i.status IN ('CONFIRMED', 'PAID') OR d.status = 'PAID' AND i.status = 'PAID'))
    ) THROW 51109, 'Confirmed settlement snapshot is immutable', 1;
END;
GO

-- Object: [TRIGGER] dbo.trg_Ticket_AssignSeat
-- Latest Source Migration: 033_sql_report_indexes.sql
CREATE OR ALTER TRIGGER dbo.trg_Ticket_AssignSeat ON dbo.tc_tickets AFTER INSERT,UPDATE AS
BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted t JOIN dbo.tc_order_items i ON i.id=t.order_item_id WHERE t.seat_id IS NOT NULL AND (i.seat_id IS NULL OR t.seat_id<>i.seat_id)) THROW 51821,'Ticket seat must match issued order item',1;
 UPDATE t SET seat_id=i.seat_id FROM dbo.tc_tickets t JOIN inserted n ON n.id=t.id JOIN dbo.tc_order_items i ON i.id=t.order_item_id WHERE t.seat_id IS NULL AND i.seat_id IS NOT NULL;
END;
GO

-- Object: [TRIGGER] dbo.trg_Ticket_ProtectIssuedSnapshot
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_Ticket_ProtectIssuedSnapshot */
CREATE OR ALTER TRIGGER dbo.trg_Ticket_ProtectIssuedSnapshot ON dbo.tc_tickets
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.id = i.id
        WHERE i.order_item_id <> d.order_item_id OR i.ticket_code <> d.ticket_code
           OR i.qr_secret_hash <> d.qr_secret_hash OR i.paid_amount <> d.paid_amount
           OR i.issued_at <> d.issued_at
    ) THROW 51108, 'Issued ticket snapshot is immutable', 1;
END;
GO

-- Object: [TRIGGER] dbo.trg_User_AuthRoleGuard
-- Latest Source Migration: 032_sql_report_permissions.sql
-- AUTH may register customers, never manufacture an administrator through the denormalized role column.
CREATE OR ALTER TRIGGER dbo.trg_User_AuthRoleGuard ON dbo.tc_users AFTER INSERT,UPDATE AS
BEGIN SET NOCOUNT ON;
 IF COALESCE(IS_ROLEMEMBER('tc_auth'),0)=1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1
 AND EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE (d.id IS NULL AND i.platform_role<>'CUSTOMER') OR (d.id IS NOT NULL AND i.platform_role<>d.platform_role))
 THROW 51818,'Authentication principal cannot assign platform roles',1;
END;
GO

-- Object: [TRIGGER] dbo.trg_VuongAuditAppendOnly
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER TRIGGER dbo.trg_VuongAuditAppendOnly ON dbo.tc_audit_logs AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 52709,'AUDIT_APPEND_ONLY',1; END;
GO

-- Object: [TRIGGER] dbo.trg_VuongCommissionFreeze
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER TRIGGER dbo.trg_VuongCommissionFreeze ON dbo.tc_commission_rules AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id JOIN dbo.tc_events e WITH(UPDLOCK,HOLDLOCK) ON e.commission_rule_id=d.id WHERE i.id IS NULL OR EXISTS(SELECT i.organization_id,i.rate_percent,i.fixed_fee,i.effective_from,i.effective_to EXCEPT SELECT d.organization_id,d.rate_percent,d.fixed_fee,d.effective_from,d.effective_to)) THROW 52709,'RULE_ALREADY_ATTACHED_TO_EVENTS',1;
END;
GO

-- Object: [TRIGGER] dbo.trg_VuongLegacyAuditInsert
-- Latest Source Migration: 027_team_schema_adapter.sql
DECLARE @buyer sysname=CASE WHEN COL_LENGTH('dbo.tc_orders','buyer_id') IS NOT NULL THEN 'buyer_id' ELSE 'user_id' END;
DECLARE @projection nvarchar(max)=N'CREATE OR ALTER VIEW dbo.vw_VuongOrders AS SELECT id,'+QUOTENAME(@buyer)+N' buyer_id,event_id,status,accepted_payment_id,paid_at,subtotal_amount,discount_amount,total_amount FROM dbo.tc_orders;';
EXEC(@projection);
DECLARE @captured sysname=CASE WHEN COL_LENGTH('dbo.tc_payments','paid_at') IS NOT NULL THEN 'paid_at' ELSE 'captured_at' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongPayments AS SELECT id,order_id,status,amount,'+QUOTENAME(@captured)+N' paid_at FROM dbo.tc_payments;';
EXEC(@projection);
DECLARE @refunds sysname=CASE WHEN COL_LENGTH('dbo.tc_refunds','order_id') IS NOT NULL THEN 'tc_refunds' ELSE 'tc_refund_obligations' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS SELECT id,order_id,amount,purpose,status,current_attempt_id,processed_at FROM dbo.'+QUOTENAME(@refunds)+N';';
IF @refunds='tc_refund_obligations'
 /* The owner's compensation mirror creates one obligation per legacy attempt.
    Hide only a failed mirror after the SAME capture is fully compensated by
    timestamped, verified successes. Partial/unknown/manual debts stay visible;
    no history, status or transfer evidence is rewritten. */
 SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS
 SELECT r.id,r.order_id,r.amount,r.purpose,r.status,r.current_attempt_id,r.processed_at
 FROM dbo.tc_refund_obligations r WHERE NOT (
 r.purpose=''PAYMENT_COMPENSATION'' AND r.status=''RETRYABLE'' AND EXISTS (
 SELECT 1 FROM dbo.tc_refunds failed JOIN dbo.tc_payments p ON p.id=failed.payment_id
 WHERE failed.id=r.id AND failed.refund_request_id IS NULL AND failed.status=''FAILED''
 AND failed.purpose=''PAYMENT_COMPENSATION'' AND failed.payment_id=r.payment_id
 AND p.order_id=r.order_id AND p.status=''CAPTURED'' AND r.amount<=p.amount
 AND p.amount>0 AND p.amount<=(SELECT COALESCE(SUM(done.amount),0)
 FROM dbo.tc_refund_obligations done WHERE done.payment_id=p.id AND done.order_id=r.order_id
 AND done.purpose=''PAYMENT_COMPENSATION'' AND done.status=''COMPLETED'' AND done.processed_at IS NOT NULL
 AND EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=done.id
 AND l.id=done.current_attempt_id AND l.result=''SUCCEEDED'' AND l.processed_at IS NOT NULL))));';
EXEC(@projection);
IF COL_LENGTH('dbo.tc_audit_logs','entity_type') IS NOT NULL
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT id,actor_id,action,entity_type,entity_id,metadata_json,created_at,updated_at FROM dbo.tc_audit_logs;');
ELSE
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_audit_logs') AND name='id' AND TYPE_NAME(user_type_id)='bigint' AND is_identity=1)
  THROW 52700,'Legacy audit requires an identity BIGINT key',1;
 /* The zero-padded 128-bit legacy key is an injective UUID projection. It is
    stable across reads/restarts without rekeying or updating historical rows. */
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT CONVERT(uniqueidentifier,CONVERT(binary(16),id)) id,actor_id,action,aggregate_type entity_type,CONVERT(varchar(100),aggregate_id) entity_id,detail metadata_json,created_at,created_at updated_at FROM dbo.tc_audit_logs;');
 EXEC(N'CREATE OR ALTER TRIGGER dbo.trg_VuongLegacyAuditInsert ON dbo.vw_VuongAuditLogs INSTEAD OF INSERT AS
 BEGIN
  SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted WHERE TRY_CONVERT(uniqueidentifier,entity_id) IS NULL) THROW 52702,''Invalid finance audit entity identifier'',1;
  /* Internal NEWID supplied by finance procedures is not a public audit ID.
     The legacy store allocates its own identity; reads return its stable UUID. */
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail,created_at)
  SELECT actor_id,action,entity_type,CONVERT(uniqueidentifier,entity_id),metadata_json,created_at FROM inserted;
 END;');
END;
GO

-- Object: [TRIGGER] dbo.trg_VuongMutationAppendOnly
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER TRIGGER dbo.trg_VuongMutationAppendOnly ON dbo.tc_finance_mutations AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 52709,'MUTATION_HISTORY_IMMUTABLE',1; END;
GO

-- Object: [TRIGGER] dbo.trg_VuongOrderFinancialFreeze
-- Latest Source Migration: 027_vuong_finance.sql
DECLARE @source_trigger nvarchar(max)=N'
-- Reject later owner writes that would invalidate a confirmed financial source. These triggers
-- also acquire the Event lock, so conflicts with confirmation abort instead of accepting a stale snapshot.
CREATE OR ALTER TRIGGER dbo.trg_VuongOrderFinancialFreeze ON dbo.tc_orders AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i FULL JOIN deleted d ON d.id=i.id
 CROSS APPLY(SELECT i.event_id event_id UNION SELECT d.event_id) a JOIN dbo.tc_events e WITH(UPDLOCK,HOLDLOCK) ON e.id=a.event_id JOIN dbo.tc_settlements s ON s.event_id=e.id
 WHERE s.status IN(''CONFIRMED'',''PAID'') AND (i.id IS NULL OR d.id IS NULL OR EXISTS(SELECT i.buyer_id,i.event_id,i.status,i.accepted_payment_id,i.paid_at,i.total_amount,i.subtotal_amount,i.discount_amount EXCEPT SELECT d.buyer_id,d.event_id,d.status,d.accepted_payment_id,d.paid_at,d.total_amount,d.subtotal_amount,d.discount_amount)))
 THROW 52709,''CONFIRMED_FINANCIAL_SOURCE_IMMUTABLE'',1;
END;
';
IF COL_LENGTH('dbo.tc_orders','buyer_id') IS NULL SET @source_trigger=REPLACE(@source_trigger,N'.buyer_id',N'.user_id');
EXEC(@source_trigger);
GO

-- Object: [TRIGGER] dbo.trg_VuongPaymentFinancialFreeze
-- Latest Source Migration: 027_vuong_finance.sql
DECLARE @source_trigger nvarchar(max)=N'
CREATE OR ALTER TRIGGER dbo.trg_VuongPaymentFinancialFreeze ON dbo.tc_payments AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i FULL JOIN deleted d ON d.id=i.id
 CROSS APPLY(SELECT i.order_id order_id UNION SELECT d.order_id) a JOIN dbo.vw_VuongOrders o ON o.id=a.order_id JOIN dbo.tc_events e WITH(UPDLOCK,HOLDLOCK) ON e.id=o.event_id JOIN dbo.tc_settlements s ON s.event_id=e.id
 WHERE s.status IN(''CONFIRMED'',''PAID'') AND (o.accepted_payment_id=COALESCE(i.id,d.id) OR i.status IN(''PENDING'',''UNKNOWN''))
 AND (i.id IS NULL OR d.id IS NULL OR EXISTS(SELECT i.order_id,i.amount,i.status,i.paid_at EXCEPT SELECT d.order_id,d.amount,d.status,d.paid_at)))
 THROW 52709,''CONFIRMED_FINANCIAL_SOURCE_IMMUTABLE'',1;
END;
';
IF COL_LENGTH('dbo.tc_payments','paid_at') IS NULL SET @source_trigger=REPLACE(@source_trigger,N'.paid_at',N'.captured_at');
EXEC(@source_trigger);
GO

-- Object: [TRIGGER] dbo.trg_VuongRefundEvidenceFreeze
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER TRIGGER dbo.trg_VuongRefundEvidenceFreeze ON dbo.tc_refund_transfer_logs AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i FULL JOIN deleted d ON d.id=i.id
 CROSS APPLY(SELECT i.refund_id refund_id UNION SELECT d.refund_id) a JOIN dbo.vw_VuongRefunds r ON r.id=a.refund_id
 JOIN dbo.vw_VuongOrders o ON o.id=r.order_id JOIN dbo.tc_events e WITH(UPDLOCK,HOLDLOCK) ON e.id=o.event_id JOIN dbo.tc_settlements s ON s.event_id=e.id
 WHERE s.status IN('CONFIRMED','PAID') AND r.purpose='CUSTOMER_REFUND')
 THROW 52709,'CONFIRMED_FINANCIAL_SOURCE_IMMUTABLE',1;
END;
GO

-- Object: [TRIGGER] dbo.trg_VuongRefundFinancialFreeze
-- Latest Source Migration: 027_vuong_finance.sql
DECLARE @source_trigger nvarchar(max)=N'
CREATE OR ALTER TRIGGER dbo.trg_VuongRefundFinancialFreeze ON dbo.tc_refunds AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i FULL JOIN deleted d ON d.id=i.id
 CROSS APPLY(SELECT i.order_id order_id UNION SELECT d.order_id) a JOIN dbo.vw_VuongOrders o ON o.id=a.order_id JOIN dbo.tc_events e WITH(UPDLOCK,HOLDLOCK) ON e.id=o.event_id JOIN dbo.tc_settlements s ON s.event_id=e.id
 WHERE s.status IN(''CONFIRMED'',''PAID'') AND (i.purpose=''CUSTOMER_REFUND'' OR d.purpose=''CUSTOMER_REFUND'')
 AND (i.id IS NULL OR d.id IS NULL OR EXISTS(SELECT i.order_id,i.amount,i.purpose,i.status,i.current_attempt_id,i.processed_at EXCEPT SELECT d.order_id,d.amount,d.purpose,d.status,d.current_attempt_id,d.processed_at)))
 THROW 52709,''CONFIRMED_FINANCIAL_SOURCE_IMMUTABLE'',1;
END;
';
IF COL_LENGTH('dbo.tc_refunds','order_id') IS NULL SET @source_trigger=REPLACE(@source_trigger,N'ON dbo.tc_refunds ',N'ON dbo.tc_refund_obligations ');
EXEC(@source_trigger);
GO

-- Object: [TRIGGER] dbo.trg_VuongSettlementValidate
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER TRIGGER dbo.trg_VuongSettlementValidate ON dbo.tc_settlements AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id WHERE i.id IS NULL AND d.status<>'DRAFT') THROW 52709,'SETTLEMENT_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted WHERE status NOT IN('DRAFT','CONFIRMED','PAID') OR gross_revenue<0 OR total_refund<0 OR total_commission<0 OR total_refund+total_commission>gross_revenue OR paid_amount<0 OR pending_amount<0 OR paid_amount+pending_amount>net_payable OR (status='PAID' AND (paid_amount<>net_payable OR pending_amount<>0))) THROW 52702,'SETTLEMENT_BALANCE_INVALID',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE i.event_id<>d.event_id OR (d.status IN('CONFIRMED','PAID') AND EXISTS(SELECT i.gross_revenue,i.total_refund,i.total_commission,i.confirmed_at EXCEPT SELECT d.gross_revenue,d.total_refund,d.total_commission,d.confirmed_at)) OR (d.status='PAID' AND i.status<>'PAID') OR (d.status='CONFIRMED' AND i.status='DRAFT')) THROW 52709,'SETTLEMENT_SNAPSHOT_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted s WHERE s.paid_amount<>COALESCE((SELECT SUM(amount) FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=s.id AND status='SUCCEEDED'),0) OR s.pending_amount<>COALESCE((SELECT SUM(amount) FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=s.id AND status='PENDING'),0)) THROW 52702,'PAYOUT_LEDGER_MISMATCH',1;
 IF EXISTS(SELECT 1 FROM inserted s WHERE s.gross_revenue<>COALESCE((SELECT SUM(gross_amount) FROM dbo.tc_settlement_order_snapshots WHERE settlement_id=s.id),0) OR s.total_refund<>COALESCE((SELECT SUM(refund_amount) FROM dbo.tc_settlement_order_snapshots WHERE settlement_id=s.id),0) OR s.total_commission<>COALESCE((SELECT SUM(commission_amount) FROM dbo.tc_settlement_order_snapshots WHERE settlement_id=s.id),0)) THROW 52702,'SETTLEMENT_SNAPSHOT_MISMATCH',1;
END;
GO

-- Object: [TRIGGER] dbo.trg_VuongSimulatedTransferAppendOnly
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER TRIGGER dbo.trg_VuongSimulatedTransferAppendOnly ON dbo.tc_vuong_simulated_transfers AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 52709,'SIMULATED_TRANSFER_HISTORY_IMMUTABLE',1; END;
GO

-- Object: [TRIGGER] dbo.trg_VuongSnapshotFreeze
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER TRIGGER dbo.trg_VuongSnapshotFreeze ON dbo.tc_settlement_order_snapshots AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM (SELECT settlement_id FROM inserted UNION SELECT settlement_id FROM deleted) a JOIN dbo.tc_settlements s WITH(UPDLOCK,HOLDLOCK) ON s.id=a.settlement_id WHERE s.status IN('CONFIRMED','PAID')) THROW 52709,'SETTLEMENT_SNAPSHOT_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted WHERE gross_amount<0 OR refund_amount<0 OR commission_amount<0 OR net_amount<0 OR refund_amount+commission_amount>gross_amount OR net_amount<>gross_amount-refund_amount-commission_amount) THROW 52702,'INVALID_FINANCIALS',1;
END;
GO

-- Object: [TRIGGER] dbo.trg_VuongTransferLedger
-- Latest Source Migration: 027_vuong_finance.sql
CREATE OR ALTER TRIGGER dbo.trg_VuongTransferLedger ON dbo.tc_settlement_transfer_logs AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id WHERE i.id IS NULL) THROW 52709,'PAYOUT_HISTORY_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status<>'PENDING') THROW 52709,'PAYOUT_RESERVATION_REQUIRED',1;
 IF EXISTS(SELECT 1 FROM inserted WHERE amount<=0 OR status NOT IN('PENDING','SUCCEEDED','FAILED') OR result<>status OR request_fingerprint IS NULL OR verification_state NOT IN('PENDING','UNKNOWN','SUCCEEDED','FAILED') OR (status='PENDING' AND (processed_at IS NOT NULL OR verification_state NOT IN('PENDING','UNKNOWN'))) OR (status IN('SUCCEEDED','FAILED') AND (processed_at IS NULL OR verification_state<>status))) THROW 52702,'PAYOUT_INVALID',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(SELECT i.settlement_id,i.amount,i.request_fingerprint,i.requested_reference EXCEPT SELECT d.settlement_id,d.amount,d.request_fingerprint,d.requested_reference) OR (d.status IN('SUCCEEDED','FAILED') AND EXISTS(SELECT i.status,i.result,i.provider_reference,i.processed_at,i.verification_state EXCEPT SELECT d.status,d.result,d.provider_reference,d.processed_at,d.verification_state)) OR (d.verification_state='UNKNOWN' AND i.verification_state='PENDING')) THROW 52709,'PAYOUT_HISTORY_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.tc_settlements s WITH(UPDLOCK,HOLDLOCK) ON s.id=i.settlement_id WHERE s.status<>'CONFIRMED') THROW 52709,'SETTLEMENT_NOT_CONFIRMED',1;
 UPDATE s SET paid_amount=t.paid,pending_amount=t.pending,status=CASE WHEN t.paid=s.net_payable AND t.pending=0 THEN 'PAID' ELSE 'CONFIRMED' END,updated_at=SYSUTCDATETIME()
 FROM dbo.tc_settlements s JOIN(SELECT DISTINCT settlement_id FROM inserted) a ON a.settlement_id=s.id CROSS APPLY(SELECT COALESCE(SUM(CASE WHEN status='SUCCEEDED' THEN amount ELSE 0 END),0) paid,COALESCE(SUM(CASE WHEN status='PENDING' THEN amount ELSE 0 END),0) pending FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=s.id) t;
END;
GO

-- Object: [TRIGGER] dbo.trg_Zone_ProtectPublishedLayout
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_Zone_ProtectPublishedLayout */
CREATE OR ALTER TRIGGER dbo.trg_Zone_ProtectPublishedLayout ON dbo.tc_zones
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.tc_events e ON e.id = i.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED')
          AND (d.id IS NULL OR d.event_id <> i.event_id OR d.name <> i.name OR d.type <> i.type
               OR ISNULL(d.capacity, -1) <> ISNULL(i.capacity, -1))
    ) OR EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        JOIN dbo.tc_events e ON e.id = d.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED')
          AND (i.id IS NULL OR i.event_id <> d.event_id)
    ) THROW 51101, 'Published event zone layout is immutable', 1;
END;
GO

-- Object: [TRIGGER] dbo.trg_tc_users_normalize
-- Latest Source Migration: 007_create_triggers.sql
/* Trigger: trg_tc_users_normalize */
CREATE OR ALTER TRIGGER dbo.trg_tc_users_normalize
    ON dbo.tc_users AFTER INSERT, UPDATE
    AS
    BEGIN
        SET NOCOUNT ON;
        UPDATE u
        SET normalized_email = LOWER(u.email)
        FROM dbo.tc_users u
        JOIN inserted i ON u.id = i.id
        WHERE u.normalized_email IS NULL OR u.normalized_email <> LOWER(u.email);
    END
GO

