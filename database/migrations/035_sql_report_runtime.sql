-- Retained runtime corrections; canonical finance projections are preserved.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
GO
CREATE OR ALTER FUNCTION dbo.fn_CalculateCouponDiscountCapped(@subtotal decimal(19,0),@coupon_id uniqueidentifier)
RETURNS decimal(19,0) AS BEGIN
 DECLARE @discount decimal(19,0),@cap decimal(19,0);
 SELECT @discount=dbo.fn_CalculateCouponDiscount(@subtotal,discount_type,percentage_value,fixed_amount),@cap=max_discount_amount FROM dbo.tc_coupons WHERE id=@coupon_id;
 RETURN CASE WHEN @cap IS NOT NULL AND @cap<@discount THEN @cap ELSE @discount END;
END;
GO
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
/* Stored Procedure: usp_CreateTicketHold */
CREATE OR ALTER PROCEDURE dbo.usp_CreateTicketHold
    @user_id uniqueidentifier, @event_id uniqueidentifier, @selections nvarchar(max)
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
        IF @old_hold IS NOT NULL EXEC dbo.usp_ReleaseTicketHold @old_hold, NULL, 'EXPIRED';
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
        SELECT @hold_id hold_id, @expires expires_at;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp02;
        THROW;
    END CATCH
END;
GO
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
/* Stored Procedure: usp_ProcessCancelledOrder */
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

        UPDATE rr SET reason_type = 'EVENT_CANCELLATION', version = version + 1
        FROM dbo.tc_refund_requests rr
        WHERE rr.order_id = @order_id AND rr.status = 'PENDING'
          AND rr.reason_type = 'CUSTOMER_REQUEST';

        UPDATE rr SET status = 'COMPLETED', decided_at = COALESCE(decided_at, SYSUTCDATETIME()),
                      version = version + 1
        FROM dbo.tc_refund_requests rr
        WHERE rr.order_id = @order_id AND rr.status = 'PENDING'
          AND rr.reason_type = 'EVENT_CANCELLATION'
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.refund_request_id = rr.id);

        DECLARE @pending TABLE(id uniqueidentifier PRIMARY KEY);
        INSERT @pending
        SELECT id FROM dbo.tc_refund_requests
        WHERE order_id = @order_id AND status = 'PENDING' AND reason_type = 'EVENT_CANCELLATION';
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
            INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
            VALUES (@request_id, @order_id, @owner_id, N'Event cancelled', 'EVENT_CANCELLATION');
            INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id)
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
/* Stored Procedure: usp_CreateRefundRequest */
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
               OR EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.ticket_id = x.id AND rt.is_open = 1)
        ) THROW 51255, 'One or more tickets are not refundable', 1;
        INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
        VALUES (@request_id, @order_id, @actor_id, @reason, 'CUSTOMER_REQUEST');
        INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id) SELECT @request_id, id FROM @tickets;
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
IF OBJECT_ID('dbo.usp_VuongRequireAdmin') IS NULL EXEC(N'/* Stored Procedure: usp_PublishEvent */
CREATE OR ALTER PROCEDURE dbo.usp_PublishEvent
    @event_id uniqueidentifier, @actor_id uniqueidentifier, @commission_rule_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp12;
    BEGIN TRY
        DECLARE @old_actor sql_variant=SESSION_CONTEXT(N''actor_id'');
        EXEC sys.sp_set_session_context @key=N''actor_id'',@value=@actor_id;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51520, ''Administrator role required'', 1;
        DECLARE @organization_id uniqueidentifier, @status varchar(30), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @organization_id = organization_id, @status = status FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @status IS NULL THROW 51521, ''Event not found'', 1;
        IF @status = ''PUBLISHED'' BEGIN
            IF (SELECT commission_rule_id FROM dbo.tc_events WHERE id = @event_id) <> @commission_rule_id
                THROW 51522, ''Published event commission rule is immutable'', 1;
            SELECT @event_id event_id, @status status;
            IF @started = 1 COMMIT TRANSACTION;
        EXEC sys.sp_set_session_context @key=N''actor_id'',@value=@old_actor; RETURN;
        END;
        IF @status <> ''PENDING_APPROVAL'' THROW 51523, ''Event is not pending approval'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_commission_rules WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @commission_rule_id AND organization_id = @organization_id
                         AND effective_from <= @now AND effective_to > @now)
            THROW 51524, ''Commission rule is not valid for event organization'', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND (cover_image_url IS NULL OR sale_start >= sale_end OR sale_end > start_time OR start_time >= end_time))
            THROW 51525, ''Event content or schedule is incomplete'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_zones WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id)
            THROW 51526, ''Event requires at least one zone'', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_zones z WHERE z.event_id = @event_id AND z.type = ''SEATED''
                   AND NOT EXISTS (SELECT 1 FROM dbo.tc_seats s WHERE s.zone_id = z.id))
            THROW 51527, ''Each seated zone requires seats'', 1;
        UPDATE dbo.tc_events SET commission_rule_id = @commission_rule_id, status = ''PUBLISHED'', version = version + 1 WHERE id = @event_id;
        IF @started = 1 COMMIT TRANSACTION;
        EXEC sys.sp_set_session_context @key=N''actor_id'',@value=@old_actor;
        SELECT @event_id event_id, CAST(''PUBLISHED'' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp12;
        EXEC sys.sp_set_session_context @key=N''actor_id'',@value=@old_actor;
        THROW;
    END CATCH
END;');
GO
/* Stored Procedure: usp_CancelEvent */
CREATE OR ALTER PROCEDURE dbo.usp_CancelEvent @event_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp13;
    BEGIN TRY
        DECLARE @old_actor sql_variant=SESSION_CONTEXT(N'actor_id');
        EXEC sys.sp_set_session_context @key=N'actor_id',@value=@actor_id;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51530, 'Administrator role required', 1;
        DECLARE @status varchar(30), @start datetime2(3);
        SELECT @status = status, @start = start_time
        FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @status IS NULL THROW 51531, 'Event not found', 1;
        IF @status = 'CANCELLED' BEGIN
            SELECT @event_id event_id, @status status;
            IF @started = 1 COMMIT TRANSACTION;
        EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor; RETURN;
        END;
        IF @status <> 'PUBLISHED' OR SYSUTCDATETIME() >= @start
            THROW 51532, 'Only a future published event can be cancelled', 1;
        UPDATE dbo.tc_events SET status = 'CANCELLED', version = version + 1 WHERE id = @event_id;
        INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('EVENT_CANCELLED', 'EVENT', @event_id,
                CONCAT(N'{"eventId":"', @event_id, N'"}'), CONCAT('event-cancelled:', @event_id));
        IF @started = 1 COMMIT TRANSACTION;
        EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor;
        SELECT @event_id event_id, CAST('CANCELLED' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp13;
        EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor;
        THROW;
    END CATCH
END;
GO
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
