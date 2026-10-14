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
GRANT EXECUTE ON dbo.usp_ListCheckoutCoupons TO tc_buyer;
GO
