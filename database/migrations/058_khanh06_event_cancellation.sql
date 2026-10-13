/* KHANH-06. Additive runtime for canonical SP13/SP17; compatible before/after 050.
   The queue is technical progress, never a second Refund obligation. */
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO
IF OBJECT_ID('dbo.tc_cancellation_jobs','U') IS NULL
BEGIN
 CREATE TABLE dbo.tc_cancellation_jobs(
  event_id uniqueidentifier NOT NULL PRIMARY KEY REFERENCES dbo.tc_events(id),
  actor_id uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),
  reason nvarchar(500) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','IN_PROGRESS','COMPLETED')),
  total_orders bigint NOT NULL DEFAULT 0,
  processed_orders bigint NOT NULL DEFAULT 0,
  last_processed_order_id uniqueidentifier NULL,
  cancelled_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  CHECK(processed_orders>=0 AND processed_orders<=total_orders)
 );
 CREATE TABLE dbo.tc_cancellation_orders(
  event_id uniqueidentifier NOT NULL REFERENCES dbo.tc_cancellation_jobs(event_id),
  order_id uniqueidentifier NOT NULL REFERENCES dbo.tc_orders(id),
  processed_at datetime2(3) NULL,
  PRIMARY KEY(event_id,order_id)
 );
 CREATE INDEX IX_CancellationOrders_Pending ON dbo.tc_cancellation_orders(event_id,order_id)
  WHERE processed_at IS NULL;
 CREATE TABLE dbo.tc_cancellation_receipts(
  actor_id uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),
  idempotency_key uniqueidentifier NOT NULL,
  event_id uniqueidentifier NOT NULL REFERENCES dbo.tc_cancellation_jobs(event_id),
  reason nvarchar(500) NOT NULL,
  PRIMARY KEY(actor_id,idempotency_key)
 );
END;
GO
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
CREATE OR ALTER VIEW dbo.v_worker_cancellation_orders AS
 SELECT q.event_id,q.order_id,j.cancelled_at FROM dbo.tc_cancellation_orders q
 JOIN dbo.tc_cancellation_jobs j ON j.event_id=q.event_id WHERE q.processed_at IS NULL;
GO
CREATE OR ALTER PROCEDURE dbo.sp_event_cancel
 @event_id uniqueidentifier,@admin_user_id uniqueidentifier,@cancel_reason nvarchar(500),
 @idempotency_key uniqueidentifier=NULL,@auth_version int=NULL,@canonical_replay bit=0
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END,
  @old_actor sql_variant=SESSION_CONTEXT(N'actor_id');
 IF @started=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_cancel;
 BEGIN TRY
  IF IS_ROLEMEMBER('tc_platform_admin')<>1 AND IS_ROLEMEMBER('db_owner')<>1 THROW 54203,'ADMIN_REQUIRED',1;
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
    SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=@event_id; RETURN;
   END;
  END;
  IF @state='CANCELLED' BEGIN
   IF @canonical_replay=1 AND EXISTS(SELECT 1 FROM dbo.tc_cancellation_jobs WHERE event_id=@event_id) BEGIN
    IF @started=1 COMMIT TRANSACTION;
    SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=@event_id; RETURN;
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
  SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=@event_id;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK TRANSACTION;
  ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_cancel;
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@old_actor; THROW;
 END CATCH;
END;
GO
/* Preserve canonical entry point, including idempotent repeated cancellation. */
CREATE OR ALTER PROCEDURE dbo.usp_CancelEvent @event_id uniqueidentifier,@actor_id uniqueidentifier
AS BEGIN
 SET NOCOUNT ON;
 EXEC dbo.sp_event_cancel @event_id,@actor_id,N'Event cancelled by administrator',NULL,NULL,1;
END;
GO
CREATE OR ALTER PROCEDURE dbo.sp_refund_adopt_event_cancellation
 @event_id uniqueidentifier,@order_id uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @started=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_cancel_order;
 BEGIN TRY
  IF IS_ROLEMEMBER('tc_worker')<>1 AND IS_ROLEMEMBER('db_owner')<>1 THROW 54203,'WORKER_REQUIRED',1;
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
CREATE OR ALTER PROCEDURE dbo.sp_event_cancellation_finish
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF IS_ROLEMEMBER('tc_worker')<>1 AND IS_ROLEMEMBER('db_owner')<>1 THROW 54203,'WORKER_REQUIRED',1;
 UPDATE j SET status='COMPLETED',updated_at=SYSUTCDATETIME()
 FROM dbo.tc_cancellation_jobs j WHERE j.status<>'COMPLETED'
  AND j.processed_orders=j.total_orders
  AND NOT EXISTS(SELECT 1 FROM dbo.tc_cancellation_orders q WHERE q.event_id=j.event_id AND q.processed_at IS NULL);
END;
GO
GRANT SELECT ON dbo.v_admin_cancellation_progress TO tc_platform_admin;
GRANT SELECT ON dbo.v_worker_cancellation_orders TO tc_worker;
GRANT EXECUTE ON dbo.sp_event_cancel TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_CancelEvent TO tc_platform_admin;
GRANT EXECUTE ON dbo.sp_refund_adopt_event_cancellation TO tc_worker;
GRANT EXECUTE ON dbo.sp_event_cancellation_finish TO tc_worker;
DENY SELECT,INSERT,UPDATE,DELETE ON dbo.tc_cancellation_jobs TO tc_auth,tc_buyer,tc_manager,tc_checkin,tc_platform_admin,tc_worker;
DENY SELECT,INSERT,UPDATE,DELETE ON dbo.tc_cancellation_orders TO tc_auth,tc_buyer,tc_manager,tc_checkin,tc_platform_admin,tc_worker;
DENY SELECT,INSERT,UPDATE,DELETE ON dbo.tc_cancellation_receipts TO tc_auth,tc_buyer,tc_manager,tc_checkin,tc_platform_admin,tc_worker;
GO
