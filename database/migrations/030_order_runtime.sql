SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
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
GRANT EXECUTE ON dbo.usp_CreateOrderRequest TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ApplyOrderCouponRequest TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReadCheckout TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReadOrderCouponEligibility TO tc_buyer;
REVOKE EXECUTE ON dbo.usp_CreateOrderFromHold FROM tc_buyer;
REVOKE EXECUTE ON dbo.usp_ApplyOrderCoupon FROM tc_buyer;
GO
