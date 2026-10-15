/* Local proposal only. Requires 058; requesting/rejecting never cancels an event or moves money.
   Approval invokes SP13 in the SAME transaction. COMPLETED is order adoption, not payout. */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
IF OBJECT_ID('dbo.sp_event_cancel','P') IS NULL THROW 54300,'CANCELLATION_058_REQUIRED',1;
IF OBJECT_ID('dbo.tc_event_cancellation_requests','U') IS NULL
BEGIN
 CREATE TABLE dbo.tc_event_cancellation_requests(
  event_id uniqueidentifier NOT NULL PRIMARY KEY REFERENCES dbo.tc_events(id),
  requested_by uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),
  reason nvarchar(500) NOT NULL,
  status varchar(20) NOT NULL CHECK(status IN('PENDING','APPROVED','REJECTED')),
  requested_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  reviewer_id uniqueidentifier NULL REFERENCES dbo.tc_users(id),
  decided_at datetime2(3) NULL,
  rejection_reason nvarchar(500) NULL
 );
 CREATE TABLE dbo.tc_event_cancellation_review_receipts(
  actor_id uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),
  idempotency_key uniqueidentifier NOT NULL,
  fingerprint varchar(64) NOT NULL,
  response_json nvarchar(max) NOT NULL,
  PRIMARY KEY(actor_id,idempotency_key)
 );
END;
GO
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
GRANT EXECUTE ON dbo.sp_event_cancellation_review TO tc_manager,tc_platform_admin;
GRANT EXECUTE ON dbo.sp_event_cancellation_queue TO tc_platform_admin;
DENY SELECT,INSERT,UPDATE,DELETE ON dbo.tc_event_cancellation_requests TO tc_auth,tc_buyer,tc_manager,tc_checkin,tc_platform_admin,tc_worker;
DENY SELECT,INSERT,UPDATE,DELETE ON dbo.tc_event_cancellation_review_receipts TO tc_auth,tc_buyer,tc_manager,tc_checkin,tc_platform_admin,tc_worker;
GO
