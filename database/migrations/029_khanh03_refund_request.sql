-- KHANH-03 additive contract for the canonical 001-008 schema. No business fixtures.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
IF OBJECT_ID('dbo.tc_request_idempotency','U') IS NULL
CREATE TABLE dbo.tc_request_idempotency(
 id uniqueidentifier NOT NULL DEFAULT NEWSEQUENTIALID() PRIMARY KEY,
 actor_id uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),operation varchar(50) NOT NULL,
 idempotency_key varchar(100) NOT NULL,target_id uniqueidentifier NOT NULL,fingerprint varchar(200) NOT NULL,
 response_payload nvarchar(max) NOT NULL,status varchar(20) NOT NULL DEFAULT 'COMPLETED'
 CHECK(status IN('COMPLETED','FAILED')),created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 expires_at datetime2(3) NOT NULL,UNIQUE(actor_id,operation,idempotency_key));
GO
CREATE OR ALTER VIEW dbo.v_refunds AS
SELECT r.id refund_id,r.order_id,o.user_id owner_id,e.title event_name,r.amount,r.status,
       r.reason,r.rejection_reason,r.created_at,r.processed_at,r.current_attempt_id,
       (SELECT COUNT(*) FROM dbo.tc_refund_tickets rt WHERE rt.refund_id=r.id) ticket_count
FROM dbo.tc_refund_obligations r JOIN dbo.tc_orders o ON o.id=r.order_id
JOIN dbo.tc_events e ON e.id=o.event_id WHERE r.purpose='CUSTOMER_REFUND';
GO
CREATE OR ALTER VIEW dbo.v_refund_tickets AS
SELECT rt.refund_id,t.id ticket_id,t.ticket_code,i.zone_name_snapshot,
       i.seat_label_snapshot,t.paid_amount,t.status
FROM dbo.tc_refund_tickets rt JOIN dbo.tc_tickets t ON t.id=rt.ticket_id
JOIN dbo.tc_order_items i ON i.id=t.order_item_id;
GO
CREATE OR ALTER VIEW dbo.v_refundable_orders AS
SELECT o.id order_id,o.order_code,o.user_id owner_id,o.status order_status,e.title event_name,
       e.status event_status,e.start_time
FROM dbo.tc_orders o JOIN dbo.tc_events e ON e.id=o.event_id;
GO
CREATE OR ALTER VIEW dbo.v_refundable_ticket_options AS
SELECT o.id order_id,o.user_id owner_id,t.id ticket_id,t.ticket_code,i.zone_name_snapshot,
       i.seat_label_snapshot,t.paid_amount,t.status,
       CAST(CASE WHEN EXISTS(SELECT 1 FROM dbo.tc_refund_tickets rt WHERE rt.ticket_id=t.id AND rt.is_open=1)
         THEN 1 ELSE 0 END AS bit) has_open_refund
FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id
JOIN dbo.tc_orders o ON o.id=i.order_id;
GO
CREATE OR ALTER VIEW dbo.v_refund_attempts AS
SELECT refund_id,id attempt_id,attempt_no,status,created_at,processed_at
FROM dbo.tc_refund_transfer_logs;
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
  DECLARE @now datetime2(3)=SYSUTCDATETIME(),@fingerprint varchar(200),@old_fingerprint varchar(200),
          @event_id uniqueidentifier,@owner uniqueidentifier,@order_status varchar(30),
          @event_status varchar(30),@start datetime2(3),@payment uniqueidentifier,@amount decimal(19,0),
          @count int,@locked int,@canonical varchar(400);
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@buyer_user_id
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
GRANT SELECT ON dbo.v_refunds TO tc_buyer;
GRANT SELECT ON dbo.v_refund_tickets TO tc_buyer;
GRANT SELECT ON dbo.v_refund_attempts TO tc_buyer;
GRANT SELECT ON dbo.v_refundable_orders TO tc_buyer;
GRANT SELECT ON dbo.v_refundable_ticket_options TO tc_buyer;
GRANT SELECT ON dbo.tc_users (id,status,auth_version) TO tc_buyer;
GRANT EXECUTE ON dbo.sp_refund_request_for_tickets TO tc_buyer;
GO
