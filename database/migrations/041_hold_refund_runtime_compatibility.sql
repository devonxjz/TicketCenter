-- Preserve facade outputs and serialize same-buyer mutations before durable keys.
-- Lock order: buyer application lock -> idempotency key -> Event -> User -> Order/Ticket.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
GO
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
  INSERT dbo.tc_refund_requests(id,order_id,requester_id,reason,reason_type,requested_at)
    VALUES(@new_refund_id,@order_id,@buyer_user_id,@reason,'CUSTOMER_REQUEST',@now);
  INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id) SELECT @new_refund_id,id FROM @ids;
  -- Existing compatibility triggers mirror the request and links into the canonical tables.
  UPDATE dbo.tc_refund_obligations SET payment_id=@payment,amount=@amount WHERE id=@new_refund_id;
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
