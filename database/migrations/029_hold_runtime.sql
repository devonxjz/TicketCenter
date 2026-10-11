SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
IF COL_LENGTH('dbo.tc_ticket_holds','updated_at') IS NULL
    ALTER TABLE dbo.tc_ticket_holds ADD updated_at datetime2(3) NOT NULL CONSTRAINT DF_HoldRuntime_Updated DEFAULT SYSUTCDATETIME();
IF COL_LENGTH('dbo.tc_ticket_hold_items','created_at') IS NULL
    ALTER TABLE dbo.tc_ticket_hold_items ADD created_at datetime2(3) NOT NULL CONSTRAINT DF_HoldRuntimeItem_Created DEFAULT SYSUTCDATETIME();
IF COL_LENGTH('dbo.tc_ticket_hold_items','updated_at') IS NULL
    ALTER TABLE dbo.tc_ticket_hold_items ADD updated_at datetime2(3) NOT NULL CONSTRAINT DF_HoldRuntimeItem_Updated DEFAULT SYSUTCDATETIME();
IF OBJECT_ID('dbo.tc_request_idempotency','U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_request_idempotency (
        id uniqueidentifier NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
        actor_id uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),
        operation varchar(50) NOT NULL, idempotency_key varchar(100) NOT NULL,
        target_id uniqueidentifier NOT NULL, fingerprint varchar(200) NOT NULL,
        response_payload nvarchar(max) NOT NULL, status varchar(20) NOT NULL DEFAULT 'COMPLETED',
        created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(), expires_at datetime2(3) NOT NULL,
        CONSTRAINT UQ_RequestIdempotency_Key UNIQUE(actor_id,operation,idempotency_key)
    );
END;
GO
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
CREATE OR ALTER PROCEDURE dbo.usp_ReleaseTicketHold
    @hold_id uniqueidentifier,
    @actor_id uniqueidentifier = NULL,
    @reason varchar(30),
    @suppress_result bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp07;
    BEGIN TRY
        IF @hold_id IS NULL THROW 51270, 'Ticket hold not found', 1;
        IF @reason IS NULL OR @reason NOT IN ('USER_CANCEL', 'EXPIRED', 'EVENT_CANCELLED')
            THROW 51271, 'Invalid release reason', 1;

        DECLARE @event_id uniqueidentifier, @user_id uniqueidentifier, @status varchar(20), @expires datetime2(3), @now datetime2(3);
        SELECT @event_id = h.event_id, @user_id = h.user_id FROM dbo.tc_ticket_holds h WHERE h.id = @hold_id;
        IF @event_id IS NULL THROW 51270, 'Ticket hold not found', 1;
        IF @reason = 'USER_CANCEL' BEGIN
            IF @actor_id IS NULL OR @actor_id <> @user_id
                THROW 51272, 'Hold owner required', 1;
        END ELSE IF @reason IN ('EXPIRED', 'EVENT_CANCELLED') BEGIN
            IF IS_ROLEMEMBER('tc_worker') <> 1 AND IS_ROLEMEMBER('tc_platform_admin') <> 1 AND IS_ROLEMEMBER('db_owner') <> 1
               AND NOT (@reason = 'EXPIRED' AND @actor_id IS NOT NULL AND @actor_id = @user_id)
                THROW 51276, 'System role required for expired or event cancelled release', 1;
        END;
        EXEC dbo.usp_HoldLockSelection @user_id,@event_id,N'[]',@hold_id,@include_active=0;
        SELECT @status=status,@expires=expires_at FROM dbo.tc_ticket_holds WHERE id=@hold_id;
        SET @now=SYSUTCDATETIME();
        IF @status <> 'ACTIVE' BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            IF @suppress_result = 0 SELECT @hold_id hold_id, @status hold_status;
            RETURN;
        END;
        IF @reason = 'EXPIRED' AND @now < @expires THROW 51273, 'Hold has not expired', 1;
        IF @reason = 'EVENT_CANCELLED' AND NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND status = 'CANCELLED')
            THROW 51274, 'Event is not cancelled', 1;

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

        UPDATE dbo.tc_ticket_holds SET status = 'RELEASED', version = version + 1, updated_at = @now WHERE id = @hold_id AND status = 'ACTIVE';

        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, 'TICKET_HOLD_RELEASED', 'TICKET_HOLD', @hold_id, @reason);

        IF @started = 1 COMMIT TRANSACTION;
        IF @suppress_result = 0 SELECT @hold_id hold_id, CAST('RELEASED' AS varchar(20)) hold_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp07;
        THROW;
    END CATCH
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_CreateTicketHold
    @user_id uniqueidentifier, @event_id uniqueidentifier, @selections nvarchar(max), @result_hold uniqueidentifier=NULL OUTPUT,
    @suppress_result bit=0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp02;
    BEGIN TRY
        DECLARE @now datetime2(3), @hold_id uniqueidentifier = NEWID(), @expires datetime2(3);
        IF ISJSON(@selections) <> 1 THROW 51220, 'Selections must be a JSON array', 1;
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
        EXEC dbo.usp_HoldLockSelection @user_id,@event_id,@selections;
        SET @now=SYSUTCDATETIME();
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @user_id AND status = 'ACTIVE' AND email_verified_at IS NOT NULL)
            THROW 51224, 'Active verified user required', 1;
        DECLARE @old_hold uniqueidentifier, @old_expires datetime2(3);
        SELECT @old_hold = id, @old_expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK)
        WHERE user_id = @user_id AND status = 'ACTIVE';
        IF @old_hold IS NOT NULL AND @old_expires > @now THROW 51225, 'User already has an active hold', 1;
        IF @old_hold IS NOT NULL EXEC dbo.usp_ReleaseTicketHold @old_hold, @user_id, 'EXPIRED', @suppress_result = 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = 'PUBLISHED' AND @now >= sale_start AND @now < sale_end
                       AND EXISTS(SELECT 1 FROM dbo.tc_organizations o WITH(HOLDLOCK) WHERE o.id=tc_events.organization_id AND o.status='APPROVED'))
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
        SET @now=SYSUTCDATETIME();
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND status='PUBLISHED' AND @now>=sale_start AND @now<sale_end)
            THROW 51226,'Event is not on sale',1;
        SET @expires = DATEADD(second, 600, @now);
        INSERT dbo.tc_ticket_holds(id, user_id, event_id, created_at, expires_at, updated_at) VALUES (@hold_id, @user_id, @event_id, @now, @expires, @now);
        INSERT dbo.tc_ticket_hold_items(hold_id, zone_id, seat_id, quantity, unit_price, created_at, updated_at)
        SELECT @hold_id, i.zone_id, i.seat_id, i.quantity, z.price, @now, @now FROM @items i JOIN dbo.tc_zones z ON z.id = i.zone_id;
        UPDATE s SET status = 'HELD', version = version + 1 FROM dbo.tc_seats s JOIN @items i ON i.seat_id = s.id;
        UPDATE z SET held_quantity = held_quantity + i.q, version = version + 1 FROM dbo.tc_zones z
        JOIN (SELECT zone_id, SUM(quantity) q FROM @items WHERE seat_id IS NULL GROUP BY zone_id) i ON i.zone_id = z.id;
        IF @started = 1 COMMIT TRANSACTION;
        SET @result_hold=@hold_id;
        IF @suppress_result=0 SELECT @hold_id hold_id, @expires expires_at;
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
CREATE OR ALTER PROCEDURE dbo.usp_CreateHoldRequest
    @actor_id uniqueidentifier,@auth_version int,@request_key uniqueidentifier,@fingerprint varchar(64),
    @event_id uniqueidentifier,@selections nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldMutation @actor_id,@auth_version,@request_key,@fingerprint,'CREATE',@event_id,@selections;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_CancelHoldRequest
    @actor_id uniqueidentifier,@auth_version int,@request_key uniqueidentifier,@fingerprint varchar(64),@hold_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldMutation @actor_id,@auth_version,@request_key,@fingerprint,'CANCEL',@hold_id=@hold_id;
END;
GO
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
CREATE OR ALTER PROCEDURE dbo.usp_ExpireHolds @batch_size int=50
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 5000;
    IF @@TRANCOUNT<>0 THROW 51310,'Facade requires autocommit',1;
    IF @batch_size NOT BETWEEN 1 AND 100 THROW 51300,'Invalid batch size',1;
    DECLARE @candidates TABLE(id uniqueidentifier PRIMARY KEY);
    INSERT @candidates SELECT TOP(@batch_size) id FROM dbo.tc_ticket_holds
        WHERE status='ACTIVE' AND expires_at<=SYSUTCDATETIME() ORDER BY expires_at,id;
    DECLARE @changes TABLE(actorId uniqueidentifier,eventId uniqueidentifier,holdId uniqueidentifier,version bigint);
    DECLARE @id uniqueidentifier,@actor uniqueidentifier,@event uniqueidentifier,@status varchar(20),@expires datetime2(3),@version bigint,@changed bit;
    DECLARE candidates CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM @candidates ORDER BY id;
    OPEN candidates; FETCH NEXT FROM candidates INTO @id;
    WHILE @@FETCH_STATUS=0 BEGIN
        BEGIN TRY
            SET @changed=0;
            SELECT @actor=user_id,@event=event_id FROM dbo.tc_ticket_holds WHERE id=@id;
            BEGIN TRANSACTION;
            EXEC dbo.usp_HoldLockSelection @actor,@event,N'[]',@id,@include_active=0;
            SELECT @status=status,@expires=expires_at FROM dbo.tc_ticket_holds WHERE id=@id;
            IF @status='ACTIVE' AND @expires<=SYSUTCDATETIME() BEGIN
                EXEC dbo.usp_ReleaseTicketHold @id,@actor,'EXPIRED',@suppress_result=1;
                SELECT @actor=user_id,@event=event_id,@version=version,@changed=1 FROM dbo.tc_ticket_holds WHERE id=@id AND status='RELEASED';
            END;
            COMMIT;
            IF @changed=1 INSERT @changes VALUES(@actor,@event,@id,@version);
        END TRY BEGIN CATCH
            IF XACT_STATE()<>0 ROLLBACK;
            IF ERROR_NUMBER() NOT IN(1205,1222,51309) THROW;
        END CATCH;
        FETCH NEXT FROM candidates INTO @id;
    END;
    CLOSE candidates; DEALLOCATE candidates;
    SELECT COALESCE((SELECT LOWER(CONVERT(char(36),actorId)) actorId,LOWER(CONVERT(char(36),eventId)) eventId,
        LOWER(CONVERT(char(36),holdId)) holdId,version FROM @changes FOR JSON PATH),N'[]') changes_payload;
END;
GO
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
GRANT EXECUTE ON dbo.usp_CreateHoldRequest TO tc_buyer;
GRANT EXECUTE ON dbo.usp_CancelHoldRequest TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReadActiveHold TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReadHoldOptions TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ExpireHolds TO tc_worker;
REVOKE EXECUTE ON dbo.usp_CreateTicketHold FROM tc_buyer;
REVOKE EXECUTE ON dbo.usp_ReleaseTicketHold FROM tc_buyer;
GO


