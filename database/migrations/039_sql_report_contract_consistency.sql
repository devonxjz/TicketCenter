-- Follow-up after 031..038: retained contracts, no history rewrite.
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER FUNCTION dbo.fn_GetEventCheckInWindow(@event_id uniqueidentifier,@now_utc datetime2(3))
RETURNS TABLE AS RETURN (
 SELECT id event_id,DATEADD(minute,-60,start_time) opens_at,end_time closes_at,
 CASE WHEN status='CANCELLED' THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_cancelled,
 CASE WHEN status='PUBLISHED' AND @now_utc>=DATEADD(minute,-60,start_time) AND @now_utc<end_time THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_open
 FROM dbo.tc_events WHERE id=@event_id);
GO
-- Review and callback both take Payment -> Request -> Attempts; avoid their inverse lock order.
DECLARE @sql nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('dbo.usp_ApplyRefundResult'));
IF CHARINDEX(N'SELECT @payment_id = payment_id FROM dbo.tc_refunds WHERE id = @refund_id;',@sql)=0 THROW 51891,'Unexpected refund callback contract',1;
SET @sql=REPLACE(@sql,N'SELECT @payment_id = payment_id FROM dbo.tc_refunds WHERE id = @refund_id;',N'SELECT @payment_id=payment_id,@request_id=refund_request_id FROM dbo.tc_refunds WHERE id=@refund_id;');
SET @sql=REPLACE(@sql,N'        IF @request_id IS NOT NULL
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_refund_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;',N'');
SET @sql=REPLACE(@sql,N'        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_refunds WITH (UPDLOCK, HOLDLOCK) WHERE payment_id = @payment_id;',N'        IF @request_id IS NOT NULL SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_refund_requests WITH(UPDLOCK,HOLDLOCK) WHERE id=@request_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_refunds WITH (UPDLOCK, HOLDLOCK) WHERE payment_id = @payment_id;');
SET @sql=REPLACE(@sql,N'CREATE OR ALTER PROCEDURE',N'ALTER PROCEDURE');
IF CHARINDEX(N'CREATE',@sql)>0 SET @sql=STUFF(@sql,CHARINDEX(N'CREATE',@sql),6,N'ALTER');
EXEC(@sql);
GO
-- Newer checkout code, if installed, must use the same configured coupon ceiling as the retained path.
DECLARE @sql nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('dbo.usp_OrderCouponDecision'));
IF @sql IS NOT NULL BEGIN
 SET @sql=REPLACE(@sql,N'dbo.fn_CalculateCouponDiscount(@subtotal,discount_type,percentage_value,fixed_amount)',N'dbo.fn_CalculateCouponDiscountCapped(@subtotal,id)');
 SET @sql=REPLACE(@sql,N'CREATE OR ALTER PROCEDURE',N'ALTER PROCEDURE');
 IF CHARINDEX(N'CREATE',@sql)>0 SET @sql=STUFF(@sql,CHARINDEX(N'CREATE',@sql),6,N'ALTER');
 EXEC(@sql);
END;
GO
-- Both known triggers had the same last-manager guard. Preserve the canonical one.
IF OBJECT_ID('dbo.trg_Membership_ProtectLastManager','TR') IS NOT NULL
 DROP TRIGGER IF EXISTS dbo.trg_UserOrganizationRole_ProtectLastManager;
GO
