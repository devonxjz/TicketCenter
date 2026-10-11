-- Align check-in with buyer hold/refund Event -> User locking.
-- Preserve current membership validation before durable replay and ticket mutation.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
GO
CREATE OR ALTER PROCEDURE dbo.sp_ticket_check_in
 @event_id uniqueidentifier,@staff_user_id uniqueidentifier,@ticket_uuid uniqueidentifier=NULL,
 @ticket_code varchar(120)=NULL,@customer_name nvarchar(200)=NULL,
 @request_key uniqueidentifier=NULL,@request_fingerprint char(64)=NULL,@invalid_signature bit=0
AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT ON;
 DECLARE @owns bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @owns=1 BEGIN TRANSACTION;ELSE SAVE TRANSACTION tc_k02_scan;
 BEGIN TRY
  IF @event_id IS NULL OR @staff_user_id IS NULL OR @request_key IS NULL
   OR @request_fingerprint IS NULL OR @request_fingerprint LIKE '%[^0-9a-f]%'
   OR LEN(@request_fingerprint)<>64
   OR (@invalid_signature=0 AND (@ticket_uuid IS NULL AND @ticket_code IS NULL OR @ticket_uuid IS NOT NULL AND @ticket_code IS NOT NULL))
   THROW 53204,'Invalid check-in arguments',1;
  DECLARE @org uniqueidentifier,@event_status varchar(30),@start datetime2(3),@end datetime2(3);
  SELECT @org=organization_id,@event_status=status,@start=start_time,@end=end_time
   FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
  IF @org IS NULL THROW 53202,'Event not found',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@staff_user_id AND status='ACTIVE')
   THROW 53201,'Active event staff membership required',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_organization_memberships WITH(UPDLOCK,HOLDLOCK)
   WHERE user_id=@staff_user_id AND organization_id=@org AND active=1 AND role IN('MANAGER','CHECK_IN_STAFF'))
   THROW 53201,'Active event staff membership required',1;
  DECLARE @fingerprint char(64),@result varchar(30),@ticket uniqueidentifier,@at datetime2(3),@checked datetime2(3);
  SELECT @fingerprint=fingerprint,@result=result,@ticket=ticket_id,@at=scanned_at,@checked=checked_in_at,@customer_name=customer_name
   FROM dbo.tc_checkin_scan_requests WITH(UPDLOCK,HOLDLOCK) WHERE actor_id=@staff_user_id AND request_key=@request_key;
  IF @fingerprint IS NOT NULL
  BEGIN
   IF @fingerprint<>@request_fingerprint OR NOT EXISTS(SELECT 1 FROM dbo.tc_checkin_scan_requests
     WHERE actor_id=@staff_user_id AND request_key=@request_key AND event_id=@event_id)
    THROW 53203,'Idempotency key belongs to a different scan',1;
  END
  ELSE
  BEGIN
   DECLARE @ticket_status varchar(30),@ticket_event uniqueidentifier,@display nvarchar(200);
   IF @invalid_signature=0
    SELECT @ticket=t.id,@ticket_status=t.status,@ticket_event=o.event_id,@display=u.full_name
    FROM dbo.tc_tickets t WITH(UPDLOCK,HOLDLOCK) JOIN dbo.tc_order_items i ON i.id=t.order_item_id
    JOIN dbo.tc_orders o ON o.id=i.order_id JOIN dbo.tc_users u ON u.id=o.user_id
    WHERE (@ticket_uuid IS NOT NULL AND t.id=@ticket_uuid) OR (@ticket_code IS NOT NULL AND t.ticket_code=@ticket_code);
   SET @at=SYSUTCDATETIME();
   SET @customer_name=CASE WHEN @ticket_event=@event_id THEN COALESCE(@customer_name,@display) ELSE NULL END;
   SET @result=CASE
    WHEN @invalid_signature=1 THEN 'INVALID_SIGNATURE'
    WHEN @ticket IS NULL THEN 'TICKET_NOT_FOUND'
    WHEN @ticket_event<>@event_id THEN 'WRONG_EVENT'
    WHEN @event_status='CANCELLED' THEN 'EVENT_CANCELLED'
    WHEN @event_status<>'PUBLISHED' OR @at<DATEADD(minute,-60,@start) OR @at>=@end THEN 'OUTSIDE_WINDOW'
    WHEN @ticket_status='USED' THEN 'ALREADY_CHECKED_IN'
    WHEN @ticket_status='REFUND_PENDING' THEN 'TICKET_REFUND_PENDING'
    WHEN @ticket_status='REFUNDED' THEN 'TICKET_REFUNDED'
    WHEN @ticket_status<>'ACTIVE' THEN 'TICKET_INVALID_STATUS'
    ELSE 'CHECKED_IN_SUCCESS' END;
   IF @result='CHECKED_IN_SUCCESS'
   BEGIN
    UPDATE dbo.tc_tickets SET status='USED',used_at=@at,version=version+1 WHERE id=@ticket AND status='ACTIVE';
    IF @@ROWCOUNT<>1 THROW 53205,'Ticket changed during scan',1;
    SET @checked=@at;
    INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail)
     VALUES(@staff_user_id,'TICKET_CHECKED_IN','TICKET',@ticket,N'{"source":"KHANH-02"}');
   END
   ELSE IF @result='ALREADY_CHECKED_IN'
    SELECT @checked=MAX(scanned_at) FROM dbo.tc_ticket_check_in_logs WHERE ticket_id=@ticket AND result='SUCCESS';
   -- Foreign/unknown identifiers cannot expose another organization's customer or ticket data.
   IF @ticket_event<>@event_id SET @ticket=NULL;
   INSERT dbo.tc_ticket_check_in_logs(event_id,ticket_id,actor_id,result,scanned_at)
    VALUES(@event_id,@ticket,@staff_user_id,
     CASE @result WHEN 'CHECKED_IN_SUCCESS' THEN 'SUCCESS' WHEN 'ALREADY_CHECKED_IN' THEN 'ALREADY_USED'
      WHEN 'WRONG_EVENT' THEN 'WRONG_EVENT' WHEN 'OUTSIDE_WINDOW' THEN 'OUTSIDE_WINDOW'
      WHEN 'INVALID_SIGNATURE' THEN 'INVALID_CODE' WHEN 'TICKET_NOT_FOUND' THEN 'INVALID_CODE' ELSE 'INACTIVE_TICKET' END,@at);
   INSERT dbo.tc_checkin_scan_requests(actor_id,request_key,fingerprint,event_id,ticket_id,result,customer_name,checked_in_at,scanned_at,expires_at)
    VALUES(@staff_user_id,@request_key,@request_fingerprint,@event_id,@ticket,@result,@customer_name,@checked,@at,DATEADD(hour,1,@at));
  END;
  -- Keep a durable tombstone beyond the Redis TTL: an old key cannot cause a second mutation.
  SELECT @result result,@ticket ticket_id,
   CASE WHEN @result IN('CHECKED_IN_SUCCESS','ALREADY_CHECKED_IN') THEN t.ticket_code ELSE NULL END ticket_code,
   @customer_name customer_name,i.zone_name_snapshot zone_name,i.seat_label_snapshot seat_label,
   @checked checked_in_at,@at scanned_at,@org organization_id
  FROM (SELECT 1 n) d LEFT JOIN dbo.tc_tickets t ON t.id=@ticket LEFT JOIN dbo.tc_order_items i ON i.id=t.order_item_id;
  IF @owns=1 COMMIT TRANSACTION;
 END TRY
 BEGIN CATCH
  IF @owns=1 AND XACT_STATE()<>0 ROLLBACK TRANSACTION;
  ELSE IF @owns=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_k02_scan;
  THROW;
 END CATCH;
END;
GO
