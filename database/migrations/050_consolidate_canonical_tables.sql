/* 050: one canonical table per concept.
   Refund    : tc_refund_obligations (+tc_refund_tickets, tc_refund_transfer_logs)  replaces tc_refund_requests, tc_refund_request_tickets, tc_refunds
   Check-in  : tc_ticket_check_in_logs                                              replaces tc_check_ins
   Payout    : tc_settlement_transfer_logs                                          replaces tc_payouts
   Settlement: tc_settlement_order_snapshots                                        replaces tc_settlement_items
   Legacy tables and their "mirror" triggers are dropped; procedures/views are rewritten to write the canonical tables directly.
   Requires 048_khanh05_refund_worker. Safe only while legacy tables hold no rows (verified below). */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
IF OBJECT_ID('dbo.tc_refund_dispatches','U') IS NULL OR OBJECT_ID('dbo.tc_refund_obligations','U') IS NULL
 THROW 52050,'050 requires the canonical refund tables and 048_khanh05_refund_worker',1;
IF EXISTS(SELECT 1 FROM dbo.tc_refund_requests) OR EXISTS(SELECT 1 FROM dbo.tc_refund_request_tickets) OR EXISTS(SELECT 1 FROM dbo.tc_refunds)
   OR EXISTS(SELECT 1 FROM dbo.tc_check_ins) OR EXISTS(SELECT 1 FROM dbo.tc_payouts) OR EXISTS(SELECT 1 FROM dbo.tc_settlement_items)
 THROW 52051,'050 refuses to drop legacy tables that still hold rows; back-fill the canonical tables first',1;
GO
/* ---- A. refund obligations absorb the request columns ---- */
IF COL_LENGTH('dbo.tc_refund_obligations','requester_id') IS NULL
 ALTER TABLE dbo.tc_refund_obligations ADD requester_id uniqueidentifier NULL, reviewer_id uniqueidentifier NULL,
   decided_at datetime2(3) NULL, version bigint NOT NULL CONSTRAINT DF_RefundObligations_Version DEFAULT(0);
GO
IF OBJECT_ID('FK_RefundObligations_Requester','F') IS NULL
 ALTER TABLE dbo.tc_refund_obligations ADD CONSTRAINT FK_RefundObligations_Requester FOREIGN KEY(requester_id) REFERENCES dbo.tc_users(id),
   CONSTRAINT FK_RefundObligations_Reviewer FOREIGN KEY(reviewer_id) REFERENCES dbo.tc_users(id),
   CONSTRAINT CK_RefundObligations_Requester CHECK(purpose<>'CUSTOMER_REFUND' OR requester_id IS NOT NULL);
GO
IF COL_LENGTH('dbo.tc_refund_obligations','request_status') IS NULL
 ALTER TABLE dbo.tc_refund_obligations ADD request_status AS (CAST(CASE WHEN purpose<>'CUSTOMER_REFUND' THEN NULL
   WHEN status='REQUESTED' THEN 'PENDING' WHEN status='REJECTED' THEN 'REJECTED' WHEN status='COMPLETED' THEN 'COMPLETED'
   ELSE 'APPROVED' END AS varchar(20)));
GO
/* ---- B. drop the legacy link and re-point the foreign keys at the attempt log ---- */
IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_RefundObligations_LegacyRequest') ALTER TABLE dbo.tc_refund_obligations DROP CONSTRAINT FK_RefundObligations_LegacyRequest;
IF EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_RefundObligations_LegacyRequest') DROP INDEX UX_RefundObligations_LegacyRequest ON dbo.tc_refund_obligations;
GO
DECLARE @sql nvarchar(max)=N'';
SELECT @sql+=N'ALTER TABLE '+QUOTENAME(OBJECT_SCHEMA_NAME(parent_object_id))+N'.'+QUOTENAME(OBJECT_NAME(parent_object_id))+N' DROP CONSTRAINT '+QUOTENAME(name)+N';'
FROM sys.foreign_keys WHERE referenced_object_id=OBJECT_ID('dbo.tc_refunds') AND parent_object_id IN(OBJECT_ID('dbo.tc_gateway_refund_receipts'),OBJECT_ID('dbo.tc_refund_dispatches'));
EXEC sys.sp_executesql @sql;
GO
IF OBJECT_ID('FK_GatewayRefundReceipts_Attempt','F') IS NULL ALTER TABLE dbo.tc_gateway_refund_receipts ADD CONSTRAINT FK_GatewayRefundReceipts_Attempt FOREIGN KEY(refund_id) REFERENCES dbo.tc_refund_transfer_logs(id);
IF OBJECT_ID('FK_RefundDispatches_Attempt','F') IS NULL ALTER TABLE dbo.tc_refund_dispatches ADD CONSTRAINT FK_RefundDispatches_Attempt FOREIGN KEY(attempt_id) REFERENCES dbo.tc_refund_transfer_logs(id);
GO
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_refund_obligations') AND name='legacy_request_id') ALTER TABLE dbo.tc_refund_obligations DROP COLUMN legacy_request_id;
GO
/* the invariants that tc_refunds enforced, restated on the canonical tables */
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_RefundTransferLogs_OneActive')
 CREATE UNIQUE INDEX UX_RefundTransferLogs_OneActive ON dbo.tc_refund_transfer_logs(refund_id) WHERE status IN('PENDING','UNKNOWN','SUCCEEDED');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_RefundObligations_OneCompensationPerPayment')
 CREATE UNIQUE INDEX UX_RefundObligations_OneCompensationPerPayment ON dbo.tc_refund_obligations(payment_id)
 WHERE purpose='PAYMENT_COMPENSATION' AND status IN('PROCESSING','NEEDS_RECONCILIATION','COMPLETED');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_RefundObligations_Payment')
 CREATE INDEX IX_RefundObligations_Payment ON dbo.tc_refund_obligations(payment_id,status) INCLUDE(amount,purpose);
GO
/* ---- C. rewritten views / functions / procedures (write canonical tables directly) ---- */
CREATE OR ALTER VIEW dbo.vw_RefundAttemptLedger AS
/* One row per transfer attempt with the financial terms of its obligation.
   Amount, payment and purpose live on the obligation; the attempt only records the transfer. */
SELECT l.id attempt_id,l.refund_id,o.order_id,o.payment_id,o.purpose,o.amount,l.attempt_no,
       l.status,l.provider_reference,l.created_at,l.processed_at
FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refund_obligations o ON o.id=l.refund_id;
GO
CREATE OR ALTER VIEW dbo.v_admin_refund_requests AS
SELECT r.id refund_id,r.order_id,o.user_id buyer_id,u.full_name buyer_name,u.email buyer_email,
 e.title event_name,r.amount,r.reason,r.status,r.created_at,r.rejection_reason,r.current_attempt_id,
 (SELECT COUNT(*) FROM dbo.tc_refund_tickets t WHERE t.refund_id=r.id) ticket_count,
 r.reviewer_id,r.decided_at,
 CAST(CASE WHEN r.status='REQUESTED' AND r.current_attempt_id IS NULL THEN 1 ELSE 0 END AS bit) can_decide,
 CAST(CASE WHEN r.status='RETRYABLE' AND a.status='FAILED'
  AND NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs f WHERE f.refund_id=r.id AND f.status IN('PENDING','UNKNOWN','SUCCEEDED'))
  AND NOT EXISTS(SELECT 1 FROM dbo.tc_gateway_refund_receipts x WHERE x.payment_id=r.payment_id AND x.resolution='NEEDS_RECONCILIATION')
  THEN 1 ELSE 0 END AS bit) can_retry
FROM dbo.tc_refund_obligations r
JOIN dbo.tc_orders o ON o.id=r.order_id JOIN dbo.tc_events e ON e.id=o.event_id
JOIN dbo.tc_users u ON u.id=o.user_id
LEFT JOIN dbo.tc_refund_transfer_logs a ON a.id=r.current_attempt_id AND a.refund_id=r.id
WHERE r.purpose='CUSTOMER_REFUND';
GO
CREATE OR ALTER VIEW dbo.vw_RefundRequestOverview
AS
WITH requested AS (
    SELECT rt.refund_id, COUNT_BIG(*) ticket_count, SUM(t.paid_amount) requested_amount
    FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
    GROUP BY rt.refund_id
), attempts AS (
    SELECT refund_id, SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END) refunded_amount,
           SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) pending_attempts,
           SUM(CASE WHEN status = 'UNKNOWN' THEN 1 ELSE 0 END) unknown_attempts,
           SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) failed_attempts
    FROM dbo.vw_RefundAttemptLedger WHERE purpose = 'CUSTOMER_REFUND' GROUP BY refund_id
)
SELECT rr.id refund_request_id, o.user_id owner_id, o.event_id, e.organization_id,
       rr.reason_type, rr.reason, rr.request_status status, rr.created_at requested_at, rr.decided_at,
       COALESCE(q.ticket_count, 0) ticket_count, COALESCE(q.requested_amount, 0) requested_amount,
       COALESCE(a.refunded_amount, 0) refunded_amount, COALESCE(a.pending_attempts, 0) pending_attempts,
       COALESCE(a.unknown_attempts, 0) unknown_attempts, COALESCE(a.failed_attempts, 0) failed_attempts
FROM dbo.tc_refund_obligations rr JOIN dbo.tc_orders o ON o.id = rr.order_id
JOIN dbo.tc_events e ON e.id = o.event_id LEFT JOIN requested q ON q.refund_id = rr.id
LEFT JOIN attempts a ON a.refund_id = rr.id
WHERE rr.purpose = 'CUSTOMER_REFUND';
GO
CREATE OR ALTER VIEW dbo.vw_RefundWork
AS
    SELECT attempt_id id, amount, status, provider_reference, created_at
    FROM dbo.vw_RefundAttemptLedger
    WHERE status IN ('PENDING', 'UNKNOWN');
GO
CREATE OR ALTER VIEW dbo.vw_FailedCompensationAttempts
AS
    SELECT DISTINCT r.payment_id, r.order_id
    FROM dbo.vw_RefundAttemptLedger r
    WHERE r.purpose = 'PAYMENT_COMPENSATION' AND r.status = 'FAILED'
      AND NOT EXISTS (
          SELECT 1 FROM dbo.vw_RefundAttemptLedger active
          WHERE active.payment_id = r.payment_id AND active.purpose = 'PAYMENT_COMPENSATION'
            AND active.status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED'));
GO
CREATE OR ALTER VIEW dbo.vw_RefundWorkerCandidates AS
 SELECT l.id attempt_id,l.refund_id,o.amount,l.provider_reference,
 CASE WHEN d.attempt_id IS NULL AND l.status='PENDING' AND l.provider_reference IS NULL THEN 'EXECUTE' ELSE 'QUERY' END work_kind,
 l.created_at
 FROM dbo.tc_refund_transfer_logs l
 JOIN dbo.tc_refund_obligations o ON o.id=l.refund_id AND o.current_attempt_id=l.id
 LEFT JOIN dbo.tc_refund_dispatches d ON d.attempt_id=l.id
 WHERE l.status IN('PENDING','UNKNOWN') AND o.amount>0
 AND (d.lease_until IS NULL OR d.lease_until<=SYSUTCDATETIME())
 AND (d.attempt_id IS NULL OR d.next_query_at<=SYSUTCDATETIME());
GO
CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS
 SELECT r.id,r.order_id,r.amount,r.purpose,r.status,r.current_attempt_id,r.processed_at
 FROM dbo.tc_refund_obligations r WHERE NOT (
 r.purpose='PAYMENT_COMPENSATION' AND r.status='RETRYABLE' AND EXISTS (
 SELECT 1 FROM dbo.tc_refund_transfer_logs failed JOIN dbo.tc_payments p ON p.id=r.payment_id
 WHERE failed.id=r.id AND failed.refund_id=r.id AND failed.status='FAILED'
 AND p.order_id=r.order_id AND p.status='CAPTURED' AND r.amount<=p.amount
 AND p.amount>0 AND p.amount<=(SELECT COALESCE(SUM(done.amount),0)
 FROM dbo.tc_refund_obligations done WHERE done.payment_id=p.id AND done.order_id=r.order_id
 AND done.purpose='PAYMENT_COMPENSATION' AND done.status='COMPLETED' AND done.processed_at IS NOT NULL
 AND EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=done.id
 AND l.id=done.current_attempt_id AND l.result='SUCCEEDED' AND l.processed_at IS NOT NULL))));
GO
CREATE OR ALTER VIEW dbo.vw_EventCancellationExceptions
AS
    SELECT o.event_id, CAST('USED_TICKET' AS varchar(40)) exception_type,
           t.id exception_id, o.id order_id, t.status
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED' AND t.status = 'USED'
    UNION ALL
    SELECT o.event_id, CAST('REFUND_RESULT' AS varchar(40)),
           r.id, o.id, r.status
    FROM dbo.tc_refund_transfer_logs r
    JOIN dbo.tc_refund_obligations rr ON rr.id = r.refund_id
    JOIN dbo.tc_orders o ON o.id = rr.order_id
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED' AND rr.reason_type = 'EVENT_CANCELLATION'
      AND r.status IN ('FAILED', 'UNKNOWN');
GO
CREATE OR ALTER VIEW dbo.vw_EventCancellationProgress
AS
    SELECT e.id event_id, e.status event_status,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o WHERE o.event_id = e.id) total_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id AND (
                EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
                OR EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                           WHERE oi.order_id = o.id AND t.status = 'ACTIVE')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_obligations rr WHERE rr.order_id = o.id AND rr.purpose = 'CUSTOMER_REFUND' AND rr.status = 'REQUESTED')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_obligations rr
                           LEFT JOIN dbo.tc_refund_transfer_logs r ON r.refund_id = rr.id
                           WHERE rr.order_id = o.id AND rr.reason_type = 'EVENT_CANCELLATION'
                             AND rr.request_status = 'APPROVED' AND (r.id IS NULL OR r.status = 'PENDING')))) pending_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                              WHERE oi.order_id = o.id AND t.status NOT IN ('REFUNDED', 'INVALIDATED'))
              AND NOT EXISTS (SELECT 1 FROM dbo.vw_EventCancellationExceptions x WHERE x.order_id = o.id)) completed_orders,
           (SELECT COUNT_BIG(*) FROM dbo.vw_EventCancellationExceptions x WHERE x.event_id = e.id) exception_count,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refund_obligations rr JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION') requests_created,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refund_transfer_logs r JOIN dbo.tc_refund_obligations rr ON rr.id = r.refund_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION' AND r.status = 'SUCCEEDED') refunds_succeeded,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refund_transfer_logs r JOIN dbo.tc_refund_obligations rr ON rr.id = r.refund_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION' AND r.status = 'PENDING') refunds_pending
    FROM dbo.tc_events e;
GO
CREATE OR ALTER VIEW dbo.vw_EventCancellationWork
AS
    SELECT h.event_id, CAST('HOLD' AS varchar(10)) work_type, h.id work_id
    FROM dbo.tc_ticket_holds h
    JOIN dbo.tc_events e ON e.id = h.event_id
    WHERE e.status = 'CANCELLED' AND h.status = 'ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_orders o WHERE o.hold_id = h.id)
      AND EXISTS (
          SELECT 1 FROM dbo.tc_outbox x
          WHERE x.event_type = 'EVENT_CANCELLED' AND x.aggregate_id = h.event_id
            AND x.status <> 'PUBLISHED')
    UNION ALL
    SELECT o.event_id, CAST('ORDER' AS varchar(10)), o.id
    FROM dbo.tc_orders o
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED'
      AND EXISTS (
          SELECT 1 FROM dbo.tc_outbox x
          WHERE x.event_type = 'EVENT_CANCELLED' AND x.aggregate_id = o.event_id
            AND x.status <> 'PUBLISHED')
      AND (
          EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
          OR EXISTS (
              SELECT 1 FROM dbo.tc_tickets t
              JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
              WHERE oi.order_id = o.id AND t.status = 'ACTIVE')
          OR EXISTS (
              SELECT 1 FROM dbo.tc_refund_obligations rr
              WHERE rr.order_id = o.id AND rr.purpose = 'CUSTOMER_REFUND' AND rr.status = 'REQUESTED'));
GO
CREATE OR ALTER FUNCTION dbo.fn_GetRefundableTickets(@order_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT t.id ticket_id, t.paid_amount, oi.zone_name_snapshot, oi.seat_label_snapshot
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE o.id = @order_id AND @now_utc < e.start_time AND t.status = 'ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_tickets rt WHERE rt.ticket_id = t.id AND rt.is_open = 1)
);
GO
CREATE OR ALTER VIEW dbo.vw_CheckInHistory
AS
SELECT ci.id check_in_id, ci.event_id, e.organization_id, ci.actor_id, ci.scanned_at,
       ci.result, ci.ticket_id, oi.zone_name_snapshot, oi.seat_label_snapshot
FROM dbo.tc_ticket_check_in_logs ci JOIN dbo.tc_events e ON e.id = ci.event_id
LEFT JOIN dbo.tc_tickets t ON t.id = ci.ticket_id
LEFT JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id;
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
  INSERT dbo.tc_refund_obligations(id,order_id,payment_id,amount,purpose,reason_type,reason,status,requester_id,created_at,updated_at)
    VALUES(@new_refund_id,@order_id,@payment,@amount,'CUSTOMER_REFUND','CUSTOMER_REQUEST',@reason,'REQUESTED',@buyer_user_id,@now,@now);
  INSERT dbo.tc_refund_tickets(refund_id,ticket_id,created_at) SELECT @new_refund_id,id,@now FROM @ids;
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
               OR EXISTS (SELECT 1 FROM dbo.tc_refund_tickets rt WHERE rt.ticket_id = x.id AND rt.is_open = 1)
        ) THROW 51255, 'One or more tickets are not refundable', 1;
        SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        INSERT dbo.tc_refund_obligations(id, order_id, payment_id, amount, purpose, reason_type, reason, status, requester_id)
        SELECT @request_id, o.id, (SELECT p.id FROM dbo.tc_payments p WHERE p.id = o.accepted_payment_id AND p.status = 'CAPTURED'),
               @amount, 'CUSTOMER_REFUND', 'CUSTOMER_REQUEST', @reason, 'REQUESTED', @actor_id
        FROM dbo.tc_orders o WHERE o.id = @order_id;
        INSERT dbo.tc_refund_tickets(refund_id, ticket_id) SELECT @request_id, id FROM @tickets;
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
        SELECT @order_id=order_id FROM dbo.tc_refund_obligations WHERE id=@request_id AND purpose='CUSTOMER_REFUND';
        SELECT @event_id=event_id FROM dbo.tc_orders WHERE id=@order_id;
        IF @event_id IS NULL THROW 51401,'Refund request not found',1;
        SELECT @event_status=status FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
        IF @actor_id IS NOT NULL SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@actor_id;
        SELECT @payment_id=accepted_payment_id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment_id;
        SELECT @reason_type=reason_type,@status=request_status FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@request_id;
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
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
        WHERE rt.refund_id = @request_id;
        IF @decision = 'REJECT' BEGIN
            IF NULLIF(LTRIM(RTRIM(@rejection_reason)), N'') IS NULL THROW 51405, 'Rejection reason is required', 1;
            UPDATE t SET status = CASE WHEN @event_status = 'CANCELLED' THEN 'INVALIDATED' ELSE 'ACTIVE' END, version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id AND t.status = 'REFUND_PENDING';
            UPDATE dbo.tc_refund_tickets SET is_open = 0 WHERE refund_id = @request_id;
            UPDATE dbo.tc_refund_obligations SET status = 'REJECTED', reviewer_id = @actor_id, decided_at = @now,
                   rejection_reason = @rejection_reason, version = version + 1, updated_at = @now WHERE id = @request_id;
        END ELSE BEGIN
            SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id;
            IF @amount = 0 BEGIN
                SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                    SELECT 1 FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_id = @request_id AND oi.zone_id = z.id);
                UPDATE s SET status = 'AVAILABLE', version = s.version + 1 FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
                JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
                WHERE rt.refund_id = @request_id AND s.status = 'SOLD';
                UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_id = @request_id AND oi.seat_id IS NULL GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id;
                UPDATE t SET status = 'REFUNDED', version = t.version + 1 FROM dbo.tc_tickets t
                JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id WHERE rt.refund_id = @request_id;
                UPDATE dbo.tc_refund_tickets SET is_open = 0 WHERE refund_id = @request_id;
                UPDATE dbo.tc_refund_obligations SET status = 'COMPLETED', reviewer_id = @actor_id, decided_at = @now, version = version + 1, updated_at = @now WHERE id = @request_id;
            END ELSE BEGIN
                IF EXISTS (SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE refund_id = @request_id AND status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED')) BEGIN
                    SELECT TOP (1) @refund_id = id FROM dbo.tc_refund_transfer_logs WHERE refund_id = @request_id ORDER BY attempt_no DESC;
                END ELSE BEGIN
                    IF @retry_failed = 0 AND EXISTS (SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE refund_id = @request_id AND status = 'FAILED')
                        THROW 51406, 'Refund retry requires explicit authorization', 1;
                    SELECT @payment_id=accepted_payment_id FROM dbo.tc_orders WHERE id=@order_id;
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND order_id=@order_id AND status='CAPTURED') SET @payment_id=NULL;
                    IF @payment_id IS NULL THROW 51407, 'Captured payment not found', 1;
                    IF (SELECT COALESCE(SUM(amount),0) FROM dbo.vw_RefundAttemptLedger WHERE payment_id=@payment_id AND status IN('PENDING','UNKNOWN','SUCCEEDED'))+@amount>(SELECT amount FROM dbo.tc_payments WHERE id=@payment_id) THROW 51805,'Refund reservation exceeds captured amount',1;
                    IF EXISTS(SELECT 1 FROM dbo.tc_gateway_refund_receipts WHERE payment_id=@payment_id AND resolution='NEEDS_RECONCILIATION') THROW 51806,'Gateway reconciliation is required before retry',1;
                    SET @refund_id = NEWID();
                    INSERT dbo.tc_refund_transfer_logs(id, refund_id, attempt_no, status, result, created_at)
                    SELECT @refund_id, @request_id, COALESCE(MAX(attempt_no), 0) + 1, 'PENDING', 'PENDING', @now
                    FROM dbo.tc_refund_transfer_logs WHERE refund_id = @request_id;
                    UPDATE dbo.tc_refund_obligations SET payment_id = @payment_id, amount = @amount, status = 'PROCESSING',
                           current_attempt_id = @refund_id, provider_reference = NULL, processed_at = NULL, updated_at = @now
                    WHERE id = @request_id;
                END;
                UPDATE dbo.tc_refund_obligations SET reviewer_id = @actor_id,
                       decided_at = COALESCE(decided_at, @now), version = version + 1, updated_at = @now WHERE id = @request_id;
            END;
        END;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT('REFUND_REQUEST_', @decision), 'REFUND_REQUEST', @request_id, @rejection_reason);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @request_id request_id, request_status, @refund_id refund_id FROM dbo.tc_refund_obligations WHERE id = @request_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp10;
        THROW;
    END CATCH
END;
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
   SELECT @order=order_id,@request=id FROM dbo.tc_refund_obligations WHERE id=@refund_id AND purpose='CUSTOMER_REFUND';
   SELECT @event=event_id FROM dbo.tc_orders WHERE id=@order;
   IF @event IS NULL OR @request IS NULL THROW 54104,'REFUND_NOT_FOUND',1;
   SELECT @unused=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@admin_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version)
     THROW 54101,'SESSION_EXPIRED',1;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND platform_role='ADMIN')
     THROW 54103,'ADMIN_REQUIRED',1;
   SELECT @buyer=user_id,@payment=accepted_payment_id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
   SELECT @unused=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
   SELECT @state=status,@request_state=request_status,@attempt=current_attempt_id FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id;
   SELECT @attempt_state=status FROM dbo.tc_refund_transfer_logs WITH(UPDLOCK,HOLDLOCK) WHERE id=@attempt AND refund_id=@refund_id;
   -- Recheck current authority at the mutation boundary, after lock waits.
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND status='ACTIVE' AND auth_version=@expected_auth_version) THROW 54101,'SESSION_EXPIRED',1;
   IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@admin_user_id AND platform_role='ADMIN') THROW 54103,'ADMIN_REQUIRED',1;
   IF @decision='RETRY' BEGIN
    IF @state<>'RETRYABLE' OR COALESCE(@attempt_state,'')<>'FAILED' OR @attempt IS NULL
      THROW 54109,'ONLY_VERIFIED_FAILED_ATTEMPT_CAN_RETRY',1;
    IF EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE refund_id=@refund_id AND status IN('PENDING','UNKNOWN','SUCCEEDED'))
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
   SET @payload=(SELECT CONVERT(varchar(36),r.id) refund_id,r.request_status status,r.status canonical_status,
    CONVERT(varchar(36),r.current_attempt_id) attempt_id,CONVERT(varchar(36),@buyer) buyer_id,@tickets ticket_ids
    FROM dbo.tc_refund_obligations r WHERE r.id=@refund_id FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
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
CREATE OR ALTER PROCEDURE dbo.usp_ApplyRefundResult @refund_id uniqueidentifier, @verified_result nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp11;
    BEGIN TRY
        IF COALESCE(IS_ROLEMEMBER('tc_worker'), 0) <> 1 AND COALESCE(IS_ROLEMEMBER('db_owner'), 0) <> 1
            THROW 51409, 'Worker principal required', 1;
        IF ISJSON(@verified_result) <> 1 THROW 51410, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @request_id uniqueidentifier, @obligation_id uniqueidentifier, @payment_id uniqueidentifier, @purpose varchar(30),
                @receipt uniqueidentifier, @fingerprint binary(32), @old_reference nvarchar(200), @problem varchar(60),
                @amount decimal(19,0), @current varchar(20), @order_id uniqueidentifier,
                @event_id uniqueidentifier, @payment_amount decimal(19,0),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result IS NULL OR @result NOT IN ('SUCCEEDED', 'FAILED', 'UNKNOWN') THROW 51411, 'Unsupported refund result', 1;
        IF @provider_reference IS NULL OR LEN(@provider_reference) = 0 THROW 51415, 'Provider reference is required', 1;

        SELECT @payment_id=payment_id,@obligation_id=refund_id FROM dbo.vw_RefundAttemptLedger WHERE attempt_id=@refund_id;
        IF @payment_id IS NULL THROW 51412, 'Refund not found', 1;
        SELECT @order_id = order_id FROM dbo.tc_payments WHERE id = @payment_id;
        SELECT @event_id = event_id FROM dbo.tc_orders WHERE id = @order_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        SELECT @payment_amount = amount FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK) WHERE id = @payment_id;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@obligation_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_refund_transfer_logs l WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_refund_obligations o WHERE o.id = l.refund_id AND o.payment_id = @payment_id);
        SELECT @request_id = CASE WHEN purpose = 'CUSTOMER_REFUND' THEN refund_id END, @purpose = purpose, @amount = amount,
               @current = status, @old_reference=provider_reference
        FROM dbo.vw_RefundAttemptLedger WHERE attempt_id = @refund_id AND payment_id = @payment_id;
        IF @current IS NULL THROW 51412, 'Refund not found', 1;


        SET @now=SYSUTCDATETIME();
        SET @fingerprint=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@refund_id),'|',@result,'|',@provider_reference));
        SELECT @receipt=id FROM dbo.tc_gateway_refund_receipts WITH(UPDLOCK,HOLDLOCK) WHERE fingerprint=@fingerprint;
        IF @receipt IS NULL BEGIN
            SET @receipt=NEWID();
            INSERT dbo.tc_gateway_refund_receipts(id,refund_id,payment_id,fingerprint,observed_status,provider_reference,verified_payload)
            VALUES(@receipt,@refund_id,@payment_id,@fingerprint,@result,@provider_reference,@verified_result);
        END;
        IF @old_reference IS NOT NULL AND @old_reference<>@provider_reference SET @problem='PROVIDER_REFERENCE_CONFLICT';
        IF @current = 'SUCCEEDED' AND @problem IS NULL BEGIN
            UPDATE dbo.tc_gateway_refund_receipts SET resolution='APPLIED' WHERE id=@receipt;
            SELECT @refund_id refund_id, @current refund_status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @current='FAILED' AND @result='UNKNOWN' AND @problem IS NULL BEGIN
            UPDATE dbo.tc_gateway_refund_receipts SET resolution='IGNORED' WHERE id=@receipt;
            IF @started=1 COMMIT;
            SELECT @refund_id refund_id,@current refund_status; RETURN;
        END;
        IF @current='FAILED' AND @result='SUCCEEDED' AND (@old_reference IS NULL OR @old_reference<>@provider_reference) SET @problem='LATE_SUCCESS_UNMATCHED_REFERENCE';
        IF @current='FAILED' AND @result='SUCCEEDED' AND EXISTS(SELECT 1 FROM dbo.vw_RefundAttemptLedger WHERE attempt_id<>@refund_id AND status IN('PENDING','UNKNOWN','SUCCEEDED') AND ((refund_id=@request_id AND @request_id IS NOT NULL) OR (@purpose='PAYMENT_COMPENSATION' AND payment_id=@payment_id AND purpose=@purpose))) SET @problem='LATE_SUCCESS_WITH_ANOTHER_ATTEMPT';
        IF @result = 'SUCCEEDED' AND
           (SELECT COALESCE(SUM(amount), 0) FROM dbo.vw_RefundAttemptLedger WHERE payment_id = @payment_id AND status = 'SUCCEEDED') + @amount > @payment_amount
            SET @problem='REFUND_EXCEEDS_CAPTURE';

        IF @result = 'SUCCEEDED' AND @purpose = 'CUSTOMER_REFUND' BEGIN
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id;
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_id = @request_id AND oi.zone_id = z.id);
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_id = @request_id AND oi.seat_id = s.id);
            IF EXISTS (
                SELECT 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_tickets rt
                    JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                    WHERE rt.refund_id = @request_id AND oi.seat_id IS NULL AND t.status = 'REFUND_PENDING'
                    GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id WHERE z.sold_quantity < q.quantity)
                SET @problem='INVENTORY_INCONSISTENT';
        END;

        IF @problem IS NOT NULL BEGIN
            UPDATE dbo.tc_gateway_refund_receipts SET resolution='NEEDS_RECONCILIATION',reason=@problem WHERE id=@receipt;
            UPDATE dbo.tc_refund_obligations SET status='NEEDS_RECONCILIATION',updated_at=@now WHERE id=@obligation_id;
            INSERT dbo.tc_audit_logs(action,aggregate_type,aggregate_id,detail) VALUES('REFUND_RECONCILIATION_REQUIRED','REFUND',@refund_id,@problem);
            IF @started=1 COMMIT;
            SELECT @refund_id refund_id,CAST('NEEDS_RECONCILIATION' AS varchar(30)) refund_status; RETURN;
        END;
        UPDATE dbo.tc_gateway_refund_receipts SET resolution='APPLIED' WHERE id=@receipt;
        UPDATE dbo.tc_refund_transfer_logs SET status = @result, result = @result, provider_reference = @provider_reference,
               processed_at = CASE WHEN @result IN ('SUCCEEDED', 'FAILED') THEN @now ELSE processed_at END
        WHERE id = @refund_id;
        UPDATE o SET status = CASE @result WHEN 'SUCCEEDED' THEN 'COMPLETED' WHEN 'FAILED' THEN 'RETRYABLE' ELSE 'NEEDS_RECONCILIATION' END,
               current_attempt_id = l.id, provider_reference = l.provider_reference, processed_at = l.processed_at, updated_at = @now
        FROM dbo.tc_refund_obligations o JOIN dbo.tc_refund_transfer_logs l ON l.refund_id = o.id
        WHERE o.id = @obligation_id AND l.id = @refund_id;

        IF @result = 'SUCCEEDED' AND @purpose = 'CUSTOMER_REFUND' BEGIN
            UPDATE s SET status = 'AVAILABLE', version = s.version + 1
            FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
            JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id AND t.status = 'REFUND_PENDING' AND s.status = 'SOLD';
            UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1
            FROM dbo.tc_zones z JOIN (
                SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_tickets rt
                JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_id = @request_id AND oi.seat_id IS NULL AND t.status = 'REFUND_PENDING'
                GROUP BY oi.zone_id
            ) q ON q.zone_id = z.id;
            UPDATE t SET status = 'REFUNDED', version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_id = @request_id AND t.status = 'REFUND_PENDING';
            UPDATE dbo.tc_refund_tickets SET is_open = 0 WHERE refund_id = @request_id;
            UPDATE dbo.tc_refund_obligations SET version = version + 1 WHERE id = @request_id;
        END;

        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        VALUES (CONCAT('REFUND_', @result), 'REFUND', @refund_id, @provider_reference);
        IF @result = 'SUCCEEDED' INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('REFUND_SUCCEEDED', 'REFUND', @refund_id,
                CONCAT(N'{"refundId":"', @refund_id, N'"}'), CONCAT('refund-succeeded:', @refund_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @refund_id refund_id, @result refund_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp11;
        THROW;
    END CATCH
END;
GO
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
  SELECT @payment=payment_id,@request=refund_id FROM dbo.vw_RefundAttemptLedger WHERE attempt_id=@attempt_id;
  SELECT @order=order_id FROM dbo.tc_payments WHERE id=@payment;
  SELECT @event=event_id FROM dbo.tc_orders WHERE id=@order;
  SELECT @count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
  SELECT @count=COUNT_BIG(*) FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
  SELECT @captured=amount,@payment_status=status FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
  IF @request IS NOT NULL SELECT @count=COUNT_BIG(*) FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@request;
  SELECT @status=l.status,@ref=l.provider_reference FROM dbo.tc_refund_transfer_logs l WITH(UPDLOCK,HOLDLOCK) WHERE l.id=@attempt_id;
  SELECT @amount=amount FROM dbo.tc_refund_obligations WHERE id=@request;
  IF @status NOT IN('PENDING','UNKNOWN') OR @status IS NULL OR @amount<=0 OR NOT EXISTS(
   SELECT 1 FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refund_obligations o ON o.id=l.refund_id
   WHERE l.id=@attempt_id AND o.id=@refund_id AND o.current_attempt_id=@attempt_id) BEGIN
    IF @started=1 COMMIT; RETURN;
  END;
  SELECT @purpose=purpose FROM dbo.tc_refund_obligations WHERE id=@request;
  IF @payment_status<>'CAPTURED' OR @payment_status IS NULL OR @amount>@captured
    OR (SELECT COALESCE(SUM(amount),0) FROM dbo.vw_RefundAttemptLedger WHERE payment_id=@payment AND status IN('PENDING','UNKNOWN','SUCCEEDED'))>@captured
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
   SELECT @payment=payment_id,@request=refund_id FROM dbo.vw_RefundAttemptLedger WHERE attempt_id=@attempt_id;
   SELECT @order=order_id FROM dbo.tc_payments WHERE id=@payment;
   SELECT @event=event_id FROM dbo.tc_orders WHERE id=@order;
   SELECT @count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
   SELECT @count=COUNT_BIG(*) FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
   SELECT @count=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment;
   IF @request IS NOT NULL SELECT @count=COUNT_BIG(*) FROM dbo.tc_refund_obligations WITH(UPDLOCK,HOLDLOCK) WHERE id=@request;
   UPDATE dbo.tc_refund_transfer_logs SET status='UNKNOWN',result='UNKNOWN' WHERE id=@attempt_id AND status IN('PENDING','UNKNOWN');
   IF @@ROWCOUNT=1 UPDATE dbo.tc_refund_obligations SET status='NEEDS_RECONCILIATION',current_attempt_id=@attempt_id,updated_at=SYSUTCDATETIME() WHERE id=@request;
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
CREATE OR ALTER PROCEDURE dbo.usp_DecideRefund
    @refund_id uniqueidentifier,
    @actor_id uniqueidentifier = NULL,
    @decision varchar(20),
    @rejection_reason nvarchar(2000) = NULL,
    @attempt_id uniqueidentifier = NULL,
    @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @request_id uniqueidentifier =
        (SELECT id FROM dbo.tc_refund_obligations WHERE id = @refund_id AND purpose = 'CUSTOMER_REFUND');
    IF @request_id IS NULL THROW 52630, 'Refund obligation not found', 1;
    EXEC dbo.usp_DecideRefundRequest
        @request_id = @request_id,
        @actor_id = @actor_id,
        @decision = @decision,
        @rejection_reason = @rejection_reason,
        @retry_failed = @retry_failed;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_ApplyPaymentResult
    @order_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @payment_id uniqueidentifier = NULL,
    @verified_result nvarchar(max), @ticket_codes nvarchar(max) = NULL, @retry_failed_compensation bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp09;
    BEGIN TRY
        IF ISJSON(@verified_result) <> 1 THROW 51390, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @event_id uniqueidentifier, @hold_id uniqueidentifier, @owner_id uniqueidentifier,
                @accepted uniqueidentifier, @sale_start datetime2(3), @sale_end datetime2(3),
                @order_status varchar(30), @hold_status varchar(20), @expires datetime2(3),
                @event_status varchar(30), @total decimal(19,0), @payment_status varchar(20),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result IS NULL OR @result NOT IN ('CAPTURED', 'FAILED', 'UNKNOWN') THROW 51391, 'Unsupported payment result', 1;
        SELECT @event_id = event_id, @hold_id = hold_id, @owner_id = user_id, @order_status = status, @total = total_amount
        FROM dbo.tc_orders WHERE id = @order_id;
        IF @event_id IS NULL THROW 51392, 'Order not found', 1;
        SELECT @event_status = status, @sale_start = sale_start, @sale_end = sale_end FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @hold_status = status, @expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;
        SELECT @order_status=status,@total=total_amount,@accepted=accepted_payment_id
        FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
        SET @now=SYSUTCDATETIME();
        IF @payment_id IS NULL BEGIN
            IF @actor_id IS NULL OR @actor_id <> @owner_id OR @total <> 0 OR @result <> 'CAPTURED'
                THROW 51393, 'Only the owner may complete a zero-value order', 1;
        END ELSE BEGIN
            IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1
                THROW 51389, 'Buyer principal cannot apply a paid provider result', 1;
            IF @result='CAPTURED' AND NULLIF(LTRIM(RTRIM(@provider_reference)),N'') IS NULL THROW 51801,'Captured result requires provider reference',1;
            SELECT @payment_status = status FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK)
            WHERE id = @payment_id AND order_id = @order_id AND amount = @total;
            IF @payment_status IS NULL THROW 51394, 'Payment does not match order amount', 1;
            IF EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND provider_reference IS NOT NULL AND @provider_reference IS NOT NULL AND provider_reference<>@provider_reference) THROW 51802,'Provider reference conflict',1;
            IF JSON_VALUE(@verified_result,'$.providerAmount') IS NOT NULL AND (TRY_CONVERT(decimal(19,0),JSON_VALUE(@verified_result,'$.providerAmount')) IS NULL OR TRY_CONVERT(decimal(19,0),JSON_VALUE(@verified_result,'$.providerAmount'))<>@total) THROW 51803,'Gateway amount mismatch',1;
            IF JSON_VALUE(@verified_result,'$.txnRef') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND txn_ref=JSON_VALUE(@verified_result,'$.txnRef')) THROW 51804,'Gateway transaction mismatch',1;
            IF @payment_status = 'CAPTURED' AND @order_status = 'PAID' AND @accepted=@payment_id BEGIN
                SELECT @order_status order_status, @payment_status payment_status;
                IF @started = 1 COMMIT TRANSACTION; RETURN;
            END;
            IF @payment_status = 'CAPTURED' AND @result <> 'CAPTURED' BEGIN
                SELECT @order_status order_status, @payment_status payment_status;
                IF @started = 1 COMMIT TRANSACTION; RETURN;
            END;
            UPDATE dbo.tc_payments SET status = @result,
                   provider_reference = COALESCE(@provider_reference, provider_reference),
                   captured_at = CASE WHEN @result = 'CAPTURED' THEN COALESCE(captured_at, @now) ELSE captured_at END,
                   version = version + 1 WHERE id = @payment_id;
        END;
        IF @result <> 'CAPTURED' BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_status order_status, @result payment_status; RETURN;
        END;
        IF @order_status = 'PAID' AND (@payment_id IS NULL OR @accepted=@payment_id) BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_status order_status, @result payment_status; RETURN;
        END;
        IF @order_status<>'PENDING_PAYMENT' OR @event_status <> 'PUBLISHED' OR @hold_status <> 'ACTIVE' OR @expires <= @now OR @now<@sale_start OR @now>=@sale_end BEGIN
            IF @payment_id IS NULL THROW 51395, 'Zero-value order expired before completion', 1;
            IF NOT EXISTS (SELECT 1 FROM dbo.vw_RefundAttemptLedger WHERE payment_id = @payment_id AND purpose = 'PAYMENT_COMPENSATION' AND status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED'))
            BEGIN
                IF @retry_failed_compensation = 0 AND EXISTS (SELECT 1 FROM dbo.vw_RefundAttemptLedger WHERE payment_id = @payment_id AND purpose = 'PAYMENT_COMPENSATION' AND status = 'FAILED')
                    THROW 51396, 'Compensation retry requires explicit authorization', 1;
                -- A compensation is one obligation with exactly one attempt that shares its id.
                DECLARE @compensation_id uniqueidentifier = NEWID();
                INSERT dbo.tc_refund_obligations(id, order_id, payment_id, amount, purpose, status, current_attempt_id)
                VALUES (@compensation_id, @order_id, @payment_id, @total, 'PAYMENT_COMPENSATION', 'PROCESSING', @compensation_id);
                INSERT dbo.tc_refund_transfer_logs(id, refund_id, attempt_no, status, result)
                VALUES (@compensation_id, @compensation_id, 1, 'PENDING', 'PENDING');
            END;
            IF @started = 1 COMMIT TRANSACTION;
            SELECT CAST('COMPENSATION_PENDING' AS varchar(30)) order_status, CAST('CAPTURED' AS varchar(20)) payment_status; RETURN;
        END;
        IF ISJSON(@ticket_codes) <> 1 THROW 51397, 'Ticket codes must be a JSON array', 1;
        DECLARE @issued TABLE(ordinal int PRIMARY KEY, ticket_code varchar(100), qr_hash varbinary(64));
        INSERT @issued
        SELECT CONVERT(int, [key]) + 1, JSON_VALUE(value, '$.ticketCode'),
               TRY_CONVERT(varbinary(64), JSON_VALUE(value, '$.qrSecretHashHex'), 2)
        FROM OPENJSON(@ticket_codes);
        DECLARE @expected int = (SELECT SUM(quantity) FROM dbo.tc_order_items WHERE order_id = @order_id);
        IF @expected IS NULL OR @expected <> (SELECT COUNT(*) FROM @issued)
           OR EXISTS (SELECT 1 FROM @issued WHERE ticket_code IS NULL OR qr_hash IS NULL)
           OR EXISTS (SELECT ticket_code FROM @issued GROUP BY ticket_code HAVING COUNT(*) > 1)
            THROW 51398, 'Ticket identities do not match order quantity', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.zone_id = z.id);
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.seat_id = s.id);
        IF EXISTS (SELECT 1 FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
                   WHERE oi.order_id = @order_id AND s.status <> 'HELD')
            THROW 51388, 'Held seat inventory is inconsistent', 1;
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (
                SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_order_items
                WHERE order_id = @order_id AND seat_id IS NULL GROUP BY zone_id
            ) q ON q.zone_id = z.id WHERE z.held_quantity < q.quantity
        ) THROW 51387, 'Held standing inventory is inconsistent', 1;
        UPDATE s SET status = 'SOLD', version = s.version + 1 FROM dbo.tc_seats s
        JOIN dbo.tc_order_items oi ON oi.seat_id = s.id WHERE oi.order_id = @order_id AND s.status = 'HELD';
        UPDATE z SET held_quantity = held_quantity - q.quantity, sold_quantity = sold_quantity + q.quantity, version = z.version + 1
        FROM dbo.tc_zones z JOIN (SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_order_items
                                  WHERE order_id = @order_id AND seat_id IS NULL GROUP BY zone_id) q ON q.zone_id = z.id;
        UPDATE dbo.tc_ticket_holds SET status = 'CONSUMED', version = version + 1 WHERE id = @hold_id AND status = 'ACTIVE';
        UPDATE dbo.tc_coupon_redemptions SET status = 'CONSUMED', consumed_at = @now WHERE order_id = @order_id AND status = 'RESERVED';
        UPDATE dbo.tc_orders SET status = 'PAID', accepted_payment_id=@payment_id, paid_at = @now, version = version + 1 WHERE id = @order_id;
        WITH allocations AS (
            SELECT *, ROW_NUMBER() OVER (ORDER BY order_item_id, ticket_ordinal) ordinal
            FROM dbo.fn_AllocateTicketPaidAmounts(@order_id)
        )
        INSERT dbo.tc_tickets(order_item_id, ticket_code, qr_secret_hash, paid_amount)
        SELECT a.order_item_id, i.ticket_code, i.qr_hash, a.paid_amount FROM allocations a JOIN @issued i ON i.ordinal = a.ordinal;
        IF (SELECT SUM(paid_amount) FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE oi.order_id = @order_id) <> @total
            THROW 51399, 'Ticket paid amount allocation mismatch', 1;
        INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('ORDER_PAID', 'ORDER', @order_id, CONCAT(N'{"orderId":"', @order_id, N'"}'), CONCAT('order-paid:', @order_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT CAST('PAID' AS varchar(30)) order_status, @result payment_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp09;
        THROW;
    END CATCH
END;
GO
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

        UPDATE rr SET reason_type = 'EVENT_CANCELLATION', version = version + 1, updated_at = SYSUTCDATETIME()
        FROM dbo.tc_refund_obligations rr
        WHERE rr.order_id = @order_id AND rr.purpose = 'CUSTOMER_REFUND' AND rr.status = 'REQUESTED'
          AND rr.reason_type = 'CUSTOMER_REQUEST';

        UPDATE rr SET status = 'COMPLETED', decided_at = COALESCE(decided_at, SYSUTCDATETIME()),
                      version = version + 1, updated_at = SYSUTCDATETIME()
        FROM dbo.tc_refund_obligations rr
        WHERE rr.order_id = @order_id AND rr.purpose = 'CUSTOMER_REFUND' AND rr.status = 'REQUESTED'
          AND rr.reason_type = 'EVENT_CANCELLATION'
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_tickets rt WHERE rt.refund_id = rr.id);

        DECLARE @pending TABLE(id uniqueidentifier PRIMARY KEY);
        INSERT @pending
        SELECT id FROM dbo.tc_refund_obligations
        WHERE order_id = @order_id AND purpose = 'CUSTOMER_REFUND' AND status = 'REQUESTED' AND reason_type = 'EVENT_CANCELLATION';
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
            INSERT dbo.tc_refund_obligations(id, order_id, payment_id, amount, purpose, reason_type, reason, status, requester_id)
            SELECT @request_id, o.id, (SELECT p.id FROM dbo.tc_payments p WHERE p.id = o.accepted_payment_id AND p.status = 'CAPTURED'),
                   (SELECT COALESCE(SUM(t.paid_amount), 0) FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                    WHERE oi.order_id = o.id AND t.status = 'ACTIVE'),
                   'CUSTOMER_REFUND', 'EVENT_CANCELLATION', N'Event cancelled', 'REQUESTED', @owner_id
            FROM dbo.tc_orders o WHERE o.id = @order_id;
            INSERT dbo.tc_refund_tickets(refund_id, ticket_id)
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
/* ---- D. legacy objects: nothing may still reference them ---- */
DROP TRIGGER IF EXISTS dbo.trg_RefundRequest_MirrorCanonical;
DROP TRIGGER IF EXISTS dbo.trg_RefundRequestTicket_MirrorCanonical;
DROP TRIGGER IF EXISTS dbo.trg_RefundAttempt_MirrorCanonical;
DROP TRIGGER IF EXISTS dbo.trg_CheckIn_MirrorCanonical;
DROP TRIGGER IF EXISTS dbo.trg_Payout_MirrorCanonical;
DROP TRIGGER IF EXISTS dbo.trg_SettlementItem_MirrorCanonical;
DROP TRIGGER IF EXISTS dbo.trg_SettlementItem_MirrorDelete;
DROP TRIGGER IF EXISTS dbo.trg_SettlementItem_ProtectConfirmedAmounts;
GO
DROP PROCEDURE IF EXISTS dbo.usp_CheckInTicket;
DROP PROCEDURE IF EXISTS dbo.usp_ExecutePayout;
DROP PROCEDURE IF EXISTS dbo.usp_ConfirmEventSettlement;
DROP PROCEDURE IF EXISTS dbo.usp_CalculateEventSettlement;
DROP VIEW IF EXISTS dbo.vw_JpaPayouts;
GO
DROP TABLE dbo.tc_refund_request_tickets;
DROP TABLE dbo.tc_refunds;
DROP TABLE dbo.tc_refund_requests;
DROP TABLE dbo.tc_check_ins;
DROP TABLE dbo.tc_payouts;
DROP TABLE dbo.tc_settlement_items;
GO
