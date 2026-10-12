/* Additive repair after installed043. Login's current tc_users role is authoritative.
   Preserve prior migration bytes, financial algorithms, worker cancellation grants and receipts. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF OBJECT_ID('dbo.sp_admin_refund_decide','P') IS NULL OR OBJECT_ID('dbo.usp_ReviewRefundRequest','P') IS NULL
 THROW 54100,'Refund authority migration requires financial031 and refund043',1;
GO
CREATE OR ALTER VIEW dbo.v_admin_refund_actor AS
SELECT u.id,u.status,u.auth_version,
 CAST(CASE WHEN u.platform_role='ADMIN' THEN 1 ELSE 0 END AS bit) is_admin
FROM dbo.tc_users u;
GO
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
   SELECT @order=order_id,@request=legacy_request_id FROM dbo.tc_refund_obligations WHERE id=@refund_id AND purpose='CUSTOMER_REFUND';
   SELECT @event=event_id FROM dbo.tc_orders WHERE id=@order;
   IF @event IS NULL OR @request IS NULL THROW 54104,'REFUND_NOT_FOUND',1;
   SELECT @unused=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@admin_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version)
     THROW 54101,'SESSION_EXPIRED',1;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND platform_role='ADMIN')
     THROW 54103,'ADMIN_REQUIRED',1;
   SELECT @buyer=user_id,@payment=accepted_payment_id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
   SELECT @unused=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
   SELECT @request_state=status FROM dbo.tc_refund_requests WITH(UPDLOCK,HOLDLOCK) WHERE id=@request;
   SELECT @state=status,@attempt=current_attempt_id FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id;
   SELECT @attempt_state=status FROM dbo.tc_refund_transfer_logs WITH(UPDLOCK,HOLDLOCK) WHERE id=@attempt AND refund_id=@refund_id;
   -- Recheck current authority at the mutation boundary, after lock waits.
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version) THROW 54101,'SESSION_EXPIRED',1;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND platform_role='ADMIN') THROW 54103,'ADMIN_REQUIRED',1;
   IF @decision='RETRY' BEGIN
    IF @state<>'RETRYABLE' OR COALESCE(@attempt_state,'')<>'FAILED' OR @attempt IS NULL
      THROW 54109,'ONLY_VERIFIED_FAILED_ATTEMPT_CAN_RETRY',1;
    IF EXISTS(SELECT 1 FROM dbo.tc_refunds WHERE refund_request_id=@request AND status IN('PENDING','UNKNOWN','SUCCEEDED'))
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
   SET @payload=(SELECT CONVERT(varchar(36),r.id) refund_id,q.status status,r.status canonical_status,
    CONVERT(varchar(36),r.current_attempt_id) attempt_id,CONVERT(varchar(36),@buyer) buyer_id,@tickets ticket_ids
    FROM dbo.tc_refund_obligations r JOIN dbo.tc_refund_requests q ON q.id=r.legacy_request_id WHERE r.id=@refund_id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
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
        SELECT @order_id=order_id FROM dbo.tc_refund_requests WHERE id=@request_id;
        SELECT @event_id=event_id FROM dbo.tc_orders WHERE id=@order_id;
        IF @event_id IS NULL THROW 51401,'Refund request not found',1;
        SELECT @event_status=status FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
        IF @actor_id IS NOT NULL SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@actor_id;
        SELECT @payment_id=accepted_payment_id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment_id;
        SELECT @reason_type=reason_type,@status=status FROM dbo.tc_refund_requests WITH(UPDLOCK,HOLDLOCK) WHERE id=@request_id;
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
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
        WHERE rt.refund_request_id = @request_id;
        IF @decision = 'REJECT' BEGIN
            IF NULLIF(LTRIM(RTRIM(@rejection_reason)), N'') IS NULL THROW 51405, 'Rejection reason is required', 1;
            UPDATE t SET status = CASE WHEN @event_status = 'CANCELLED' THEN 'INVALIDATED' ELSE 'ACTIVE' END, version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id AND t.status = 'REFUND_PENDING';
            UPDATE dbo.tc_refund_request_tickets SET is_open = 0 WHERE refund_request_id = @request_id;
            UPDATE dbo.tc_refund_requests SET status = 'REJECTED', reviewer_id = @actor_id, decided_at = @now,
                   rejection_reason = @rejection_reason, version = version + 1 WHERE id = @request_id;
        END ELSE BEGIN
            SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id;
            IF @amount = 0 BEGIN
                SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                    SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_request_id = @request_id AND oi.zone_id = z.id);
                UPDATE s SET status = 'AVAILABLE', version = s.version + 1 FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
                JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
                WHERE rt.refund_request_id = @request_id AND s.status = 'SOLD';
                UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id;
                UPDATE t SET status = 'REFUNDED', version = t.version + 1 FROM dbo.tc_tickets t
                JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id WHERE rt.refund_request_id = @request_id;
                UPDATE dbo.tc_refund_request_tickets SET is_open = 0 WHERE refund_request_id = @request_id;
                UPDATE dbo.tc_refund_requests SET status = 'COMPLETED', reviewer_id = @actor_id, decided_at = @now, version = version + 1 WHERE id = @request_id;
            END ELSE BEGIN
                IF EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE refund_request_id = @request_id AND status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED')) BEGIN
                    SELECT TOP (1) @refund_id = id FROM dbo.tc_refunds WHERE refund_request_id = @request_id ORDER BY created_at DESC;
                END ELSE BEGIN
                    IF @retry_failed = 0 AND EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE refund_request_id = @request_id AND status = 'FAILED')
                        THROW 51406, 'Refund retry requires explicit authorization', 1;
                    SELECT @payment_id=accepted_payment_id FROM dbo.tc_orders WHERE id=@order_id;
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND order_id=@order_id AND status='CAPTURED') SET @payment_id=NULL;
                    IF @payment_id IS NULL THROW 51407, 'Captured payment not found', 1;
                    IF (SELECT COALESCE(SUM(amount),0) FROM dbo.tc_refunds WHERE payment_id=@payment_id AND status IN('PENDING','UNKNOWN','SUCCEEDED'))+@amount>(SELECT amount FROM dbo.tc_payments WHERE id=@payment_id) THROW 51805,'Refund reservation exceeds captured amount',1;
                    IF EXISTS(SELECT 1 FROM dbo.tc_gateway_refund_receipts WHERE payment_id=@payment_id AND resolution='NEEDS_RECONCILIATION') THROW 51806,'Gateway reconciliation is required before retry',1;
                    SET @refund_id = NEWID();
                    INSERT dbo.tc_refunds(id, refund_request_id, payment_id, purpose, amount)
                    VALUES (@refund_id, @request_id, @payment_id, 'CUSTOMER_REFUND', @amount);
                END;
                UPDATE dbo.tc_refund_requests SET status = 'APPROVED', reviewer_id = @actor_id,
                       decided_at = COALESCE(decided_at, @now), version = version + 1 WHERE id = @request_id;
            END;
        END;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT('REFUND_REQUEST_', @decision), 'REFUND_REQUEST', @request_id, @rejection_reason);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @request_id request_id, status request_status, @refund_id refund_id FROM dbo.tc_refund_requests WHERE id = @request_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp10;
        THROW;
    END CATCH
END;
GO
