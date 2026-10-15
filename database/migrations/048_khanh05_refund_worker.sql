-- KHANH-05: use canonical obligations and existing transfer logs, never a second refund model.
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
IF OBJECT_ID('dbo.tc_refund_dispatches','U') IS NULL CREATE TABLE dbo.tc_refund_dispatches (
 attempt_id uniqueidentifier NOT NULL PRIMARY KEY REFERENCES dbo.tc_refunds(id),
 lease_token uniqueidentifier NULL, lease_until datetime2(3) NULL,
 dispatch_started_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 next_query_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 last_error nvarchar(500) NULL
);
GO
CREATE OR ALTER VIEW dbo.tc_refund_attempts AS
 SELECT l.id attempt_id,l.refund_id,l.attempt_no attempt_number,l.status,l.provider_reference provider_ref,l.result,l.created_at,l.processed_at,
 COALESCE(l.failure_reason,d.last_error) error_message
 FROM dbo.tc_refund_transfer_logs l LEFT JOIN dbo.tc_refund_dispatches d ON d.attempt_id=l.id;
GO
CREATE OR ALTER VIEW dbo.vw_RefundWorkerCandidates AS
 SELECT r.id attempt_id,l.refund_id,r.amount,r.provider_reference,
 CASE WHEN d.attempt_id IS NULL AND r.status='PENDING' AND r.provider_reference IS NULL THEN 'EXECUTE' ELSE 'QUERY' END work_kind,
 r.created_at
 FROM dbo.tc_refunds r JOIN dbo.tc_refund_transfer_logs l ON l.id=r.id
 JOIN dbo.tc_refund_obligations o ON o.id=l.refund_id AND o.current_attempt_id=r.id
 LEFT JOIN dbo.tc_refund_dispatches d ON d.attempt_id=r.id
 WHERE r.status IN('PENDING','UNKNOWN') AND r.amount>0
 AND (d.lease_until IS NULL OR d.lease_until<=SYSUTCDATETIME())
 AND (d.attempt_id IS NULL OR d.next_query_at<=SYSUTCDATETIME());
GO
-- SP10/SP09 already allocate the durable attempt. Claim it; do not allocate an automatic retry.
CREATE OR ALTER PROCEDURE dbo.sp_refund_begin_attempt @refund_id uniqueidentifier,@attempt_id uniqueidentifier,@lease_token uniqueidentifier
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @started=1 BEGIN TRAN; ELSE SAVE TRAN khanh05_claim;
 BEGIN TRY
  IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 THROW 53500,'Worker principal required',1;
  IF @lease_token IS NULL THROW 53501,'Lease token required',1;
  -- Use the same Event -> Order -> Payment -> Request -> Attempt order as the financial SPs.
  DECLARE @status varchar(20),@amount decimal(19,0),@ref nvarchar(200),@kind varchar(10),@now datetime2(3)=SYSUTCDATETIME();
  DECLARE @payment uniqueidentifier,@request uniqueidentifier,@order uniqueidentifier,@event uniqueidentifier,@count bigint,@captured decimal(19,0),@payment_status varchar(20),@purpose varchar(30);
  SELECT @payment=payment_id,@request=refund_request_id FROM dbo.tc_refunds WHERE id=@attempt_id;
  SELECT @order=order_id FROM dbo.tc_payments WHERE id=@payment;
  SELECT @event=event_id FROM dbo.tc_orders WHERE id=@order;
  SELECT @count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
  SELECT @count=COUNT_BIG(*) FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
  SELECT @captured=amount,@payment_status=status FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
  IF @request IS NOT NULL SELECT @count=COUNT_BIG(*) FROM dbo.tc_refund_requests WITH(UPDLOCK,HOLDLOCK) WHERE id=@request;
  SELECT @status=status,@amount=amount,@ref=provider_reference FROM dbo.tc_refunds WITH(UPDLOCK,HOLDLOCK) WHERE id=@attempt_id;
  IF @status NOT IN('PENDING','UNKNOWN') OR @status IS NULL OR @amount<=0 OR NOT EXISTS(
   SELECT 1 FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refund_obligations o ON o.id=l.refund_id
   WHERE l.id=@attempt_id AND o.id=@refund_id AND o.current_attempt_id=@attempt_id) BEGIN
    IF @started=1 COMMIT; RETURN;
  END;
  SELECT @purpose=purpose FROM dbo.tc_refunds WHERE id=@attempt_id;
  IF @payment_status<>'CAPTURED' OR @payment_status IS NULL OR @amount>@captured
    OR (SELECT COALESCE(SUM(amount),0) FROM dbo.tc_refunds WHERE payment_id=@payment AND status IN('PENDING','UNKNOWN','SUCCEEDED'))>@captured
    OR (@purpose='CUSTOMER_REFUND' AND NOT EXISTS(SELECT 1 FROM dbo.tc_orders WHERE id=@order AND accepted_payment_id=@payment))
   THROW 53505,'Refund is not backed by a reserved captured payment',1;
  IF EXISTS(SELECT 1 FROM dbo.tc_refund_dispatches WITH(UPDLOCK,HOLDLOCK) WHERE attempt_id=@attempt_id AND
    (lease_until>@now OR next_query_at>@now)) BEGIN IF @started=1 COMMIT; RETURN; END;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_refund_dispatches WHERE attempt_id=@attempt_id) BEGIN
   SET @kind=CASE WHEN @status='PENDING' AND @ref IS NULL THEN 'EXECUTE' ELSE 'QUERY' END;
   INSERT dbo.tc_refund_dispatches(attempt_id,lease_token,lease_until,next_query_at) VALUES(@attempt_id,@lease_token,DATEADD(second,120,@now),@now);
  END ELSE BEGIN
   SET @kind='QUERY';
   UPDATE dbo.tc_refund_dispatches SET lease_token=@lease_token,lease_until=DATEADD(second,120,@now) WHERE attempt_id=@attempt_id;
  END;
  IF @started=1 COMMIT;
  SELECT @refund_id refund_id,@attempt_id attempt_id,@amount amount,@ref provider_reference,@kind work_kind,@lease_token lease_token;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK; ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRAN khanh05_claim;
  THROW;
 END CATCH
END;
GO
CREATE OR ALTER PROCEDURE dbo.sp_refund_record_attempt_result
 @attempt_id uniqueidentifier,@lease_token uniqueidentifier,@status varchar(20),@provider_reference nvarchar(200)=NULL,@error_message nvarchar(500)=NULL
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @started=1 BEGIN TRAN; ELSE SAVE TRAN khanh05_result;
 BEGIN TRY
  IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 THROW 53500,'Worker principal required',1;
  IF @status IS NULL OR @status NOT IN('SUCCEEDED','FAILED','UNKNOWN') THROW 53502,'Invalid gateway result',1;
  -- Token check is optimistic; the financial SP owns the established lock order. Ledger is
  -- fenced again after that call. A competing query can only observe the same durable attempt.
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_refund_dispatches WHERE attempt_id=@attempt_id AND lease_token=@lease_token)
   THROW 53503,'Dispatch lease superseded',1;
  IF @status='UNKNOWN' AND NULLIF(@provider_reference,N'') IS NULL BEGIN
   DECLARE @payment uniqueidentifier,@request uniqueidentifier,@order uniqueidentifier,@event uniqueidentifier,@count bigint;
   SELECT @payment=payment_id,@request=refund_request_id FROM dbo.tc_refunds WHERE id=@attempt_id;
   SELECT @order=order_id FROM dbo.tc_payments WHERE id=@payment;
   SELECT @event=event_id FROM dbo.tc_orders WHERE id=@order;
   SELECT @count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
   SELECT @count=COUNT_BIG(*) FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
   SELECT @count=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
   IF @request IS NOT NULL SELECT @count=COUNT_BIG(*) FROM dbo.tc_refund_requests WITH(UPDLOCK,HOLDLOCK) WHERE id=@request;
   UPDATE dbo.tc_refunds SET status='UNKNOWN',version=version+1 WHERE id=@attempt_id AND status IN('PENDING','UNKNOWN');
  END ELSE BEGIN
   DECLARE @payload nvarchar(max)=(SELECT @status status,@provider_reference providerReference FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
   EXEC dbo.usp_ApplyRefundResult @attempt_id,@payload;
  END;
  UPDATE dbo.tc_refund_dispatches SET lease_token=NULL,lease_until=NULL,next_query_at=DATEADD(minute,15,SYSUTCDATETIME()),last_error=@error_message
   WHERE attempt_id=@attempt_id AND lease_token=@lease_token;
  IF @@ROWCOUNT<>1 THROW 53503,'Dispatch lease superseded',1;
  IF @started=1 COMMIT;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK; ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRAN khanh05_result;
  THROW;
 END CATCH
END;
GO
-- Valid zero refunds are completed by SP10 in its approval transaction, without any attempt.
CREATE OR ALTER PROCEDURE dbo.sp_refund_complete_without_transfer @refund_id uniqueidentifier AS
BEGIN
 SET NOCOUNT ON;
 IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1 THROW 53500,'Worker principal required',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_refund_obligations WHERE id=@refund_id AND amount=0 AND status='COMPLETED' AND current_attempt_id IS NULL)
  THROW 53504,'Zero refund must complete atomically through approval',1;
 SELECT @refund_id refund_id,CAST('COMPLETED' AS varchar(30)) status;
END;
GO
GRANT SELECT ON dbo.vw_RefundWorkerCandidates TO tc_worker;
GRANT SELECT ON dbo.tc_refund_attempts TO tc_worker,tc_platform_admin;
GRANT EXECUTE ON dbo.sp_refund_begin_attempt TO tc_worker;
GRANT EXECUTE ON dbo.sp_refund_record_attempt_result TO tc_worker;
GRANT EXECUTE ON dbo.sp_refund_complete_without_transfer TO tc_worker;
DENY INSERT,UPDATE,DELETE ON dbo.tc_refund_dispatches TO tc_worker,tc_buyer,tc_manager,tc_checkin,tc_platform_admin;
