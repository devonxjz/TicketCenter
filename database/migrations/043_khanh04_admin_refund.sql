-- KHANH-04: checked admin decisions over the existing canonical obligation/attempt contract.
-- No model changes or historical migration edits. All IDs remain uniqueidentifier.
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER VIEW dbo.v_admin_refund_actor AS
SELECT u.id,u.status,u.auth_version,
 CAST(CASE WHEN EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles p WHERE p.user_id=u.id AND p.role='ADMIN') THEN 1 ELSE 0 END AS bit) is_admin
FROM dbo.tc_users u;
GO
CREATE OR ALTER VIEW dbo.v_admin_refund_requests AS
SELECT r.id refund_id,r.order_id,o.user_id buyer_id,u.full_name buyer_name,u.email buyer_email,
 e.title event_name,r.amount,r.reason,r.status,r.created_at,r.rejection_reason,r.current_attempt_id,
 (SELECT COUNT(*) FROM dbo.tc_refund_tickets t WHERE t.refund_id=r.id) ticket_count,
 q.reviewer_id,q.decided_at,
 CAST(CASE WHEN r.status='REQUESTED' AND q.status='PENDING' AND r.current_attempt_id IS NULL THEN 1 ELSE 0 END AS bit) can_decide,
 CAST(CASE WHEN r.status='RETRYABLE' AND a.status='FAILED'
  AND NOT EXISTS(SELECT 1 FROM dbo.tc_refunds f WHERE f.refund_request_id=q.id AND f.status IN('PENDING','UNKNOWN','SUCCEEDED'))
  AND NOT EXISTS(SELECT 1 FROM dbo.tc_gateway_refund_receipts x WHERE x.payment_id=r.payment_id AND x.resolution='NEEDS_RECONCILIATION')
  THEN 1 ELSE 0 END AS bit) can_retry
FROM dbo.tc_refund_obligations r JOIN dbo.tc_refund_requests q ON q.id=r.legacy_request_id
JOIN dbo.tc_orders o ON o.id=r.order_id JOIN dbo.tc_events e ON e.id=o.event_id
JOIN dbo.tc_users u ON u.id=o.user_id
LEFT JOIN dbo.tc_refund_transfer_logs a ON a.id=r.current_attempt_id AND a.refund_id=r.id
WHERE r.purpose='CUSTOMER_REFUND';
GO
CREATE OR ALTER VIEW dbo.v_admin_refund_attempts AS
SELECT refund_id,id attempt_id,attempt_no,status,provider_reference,created_at,processed_at
FROM dbo.tc_refund_transfer_logs;
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
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id=@admin_user_id AND role='ADMIN')
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
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles WITH(HOLDLOCK) WHERE user_id=@admin_user_id AND role='ADMIN')
     THROW 54103,'ADMIN_REQUIRED',1;
   SELECT @buyer=user_id,@payment=accepted_payment_id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
   SELECT @unused=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
   SELECT @request_state=status FROM dbo.tc_refund_requests WITH(UPDLOCK,HOLDLOCK) WHERE id=@request;
   SELECT @state=status,@attempt=current_attempt_id FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id;
   SELECT @attempt_state=status FROM dbo.tc_refund_transfer_logs WITH(UPDLOCK,HOLDLOCK) WHERE id=@attempt AND refund_id=@refund_id;
   -- Recheck current authority at the mutation boundary, after lock waits.
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version) THROW 54101,'SESSION_EXPIRED',1;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id=@admin_user_id AND role='ADMIN') THROW 54103,'ADMIN_REQUIRED',1;
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
  END ELSE IF @old<>@fingerprint THROW 54109,'IDEMPOTENCY_CONFLICT',1;
  IF @owns=1 COMMIT;
  SELECT * FROM OPENJSON(@payload) WITH(refund_id varchar(36),status varchar(20),canonical_status varchar(40),attempt_id varchar(36),buyer_id varchar(36),ticket_ids varchar(400));
 END TRY BEGIN CATCH
  IF @owns=1 AND XACT_STATE()<>0 ROLLBACK;
  ELSE IF @owns=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION admin_refund_decision;
  THROW;
 END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.sp_refund_approve @refund_id uniqueidentifier,@admin_user_id uniqueidentifier,
 @admin_note nvarchar(500)=NULL,@idempotency_key uniqueidentifier,@expected_auth_version int
AS BEGIN SET NOCOUNT ON;
 EXEC dbo.sp_admin_refund_decide @refund_id,@admin_user_id,@expected_auth_version,'APPROVE',@admin_note,NULL,@idempotency_key;
END;
GO
CREATE OR ALTER PROCEDURE dbo.sp_refund_reject @refund_id uniqueidentifier,@admin_user_id uniqueidentifier,
 @reject_reason nvarchar(1000),@idempotency_key uniqueidentifier,@expected_auth_version int
AS BEGIN SET NOCOUNT ON;
 EXEC dbo.sp_admin_refund_decide @refund_id,@admin_user_id,@expected_auth_version,'REJECT',NULL,@reject_reason,@idempotency_key;
END;
GO
CREATE OR ALTER PROCEDURE dbo.sp_refund_retry @refund_id uniqueidentifier,@admin_user_id uniqueidentifier,
 @idempotency_key uniqueidentifier,@expected_auth_version int
AS BEGIN SET NOCOUNT ON;
 EXEC dbo.sp_admin_refund_decide @refund_id,@admin_user_id,@expected_auth_version,'RETRY',NULL,NULL,@idempotency_key;
END;
GO
GRANT SELECT ON dbo.v_admin_refund_requests TO tc_platform_admin;
GRANT SELECT ON dbo.v_admin_refund_actor TO tc_platform_admin;
GRANT SELECT ON dbo.v_admin_refund_attempts TO tc_platform_admin;
GRANT SELECT ON dbo.v_refund_tickets TO tc_platform_admin;
GRANT EXECUTE ON dbo.sp_refund_approve TO tc_platform_admin;
GRANT EXECUTE ON dbo.sp_refund_reject TO tc_platform_admin;
GRANT EXECUTE ON dbo.sp_refund_retry TO tc_platform_admin;
-- Internal dispatcher has no standalone runtime grant; wrappers share dbo ownership.
GO
