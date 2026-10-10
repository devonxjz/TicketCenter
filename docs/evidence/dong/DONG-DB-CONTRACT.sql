/* Coordination proposal for #45. Apply as a NEW migration after owner review.
   Does not modify migrations 001-008 or locked JPA models. Tests apply it only
   to a dedicated Dong database. SQL owns all Event/Zone writes and receipts. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO
IF OBJECT_ID('dbo.tc_event_command_receipts','U') IS NULL
 CREATE TABLE dbo.tc_event_command_receipts(
 actor_id uniqueidentifier NOT NULL, scope varchar(30) NOT NULL,
 idempotency_key varchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL, request_hash char(64) NOT NULL,
 organization_id uniqueidentifier NOT NULL, event_id uniqueidentifier NULL,
 status_code int NOT NULL, response_json nvarchar(max) NOT NULL,
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT PK_EventCommandReceipts PRIMARY KEY(actor_id,scope,idempotency_key));
GO
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_event_command_receipts') AND name='idempotency_key' AND collation_name<>'Latin1_General_100_BIN2')
BEGIN
 ALTER TABLE dbo.tc_event_command_receipts DROP CONSTRAINT PK_EventCommandReceipts;
 ALTER TABLE dbo.tc_event_command_receipts ALTER COLUMN idempotency_key varchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL;
 ALTER TABLE dbo.tc_event_command_receipts ADD CONSTRAINT PK_EventCommandReceipts PRIMARY KEY(actor_id,scope,idempotency_key);
END;
GO
CREATE OR ALTER VIEW dbo.vw_PublicEventSeats AS
 SELECT s.id,s.zone_id,s.row_name,s.seat_number,s.label,s.status
 FROM dbo.tc_seats s JOIN dbo.tc_zones z ON z.id=s.zone_id
 JOIN dbo.tc_events e ON e.id=z.event_id WHERE e.status='PUBLISHED';
GO
CREATE OR ALTER PROCEDURE dbo.usp_EventAuthorize
 @actor_id uniqueidentifier,@actor_version int,@admin bit,
 @organization_id uniqueidentifier OUTPUT,@event_id uniqueidentifier OUTPUT,@zone_id uniqueidentifier=NULL
AS
BEGIN
 SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND auth_version=@actor_version)
  THROW 52001,'Current session is not valid',1;
 IF @zone_id IS NOT NULL BEGIN
  DECLARE @zone_event uniqueidentifier;
  SELECT @zone_event=event_id FROM dbo.tc_zones WHERE id=@zone_id;
  IF @zone_event IS NULL THROW 52004,'Zone not found',1;
  IF @event_id IS NOT NULL AND @event_id<>@zone_event THROW 52003,'Wrong event scope',1;
  SET @event_id=@zone_event;
 END;
 IF @event_id IS NOT NULL BEGIN
  DECLARE @owner uniqueidentifier;
  SELECT @owner=organization_id FROM dbo.tc_events WHERE id=@event_id;
  IF @owner IS NULL THROW 52004,'Event not found',1;
  IF @organization_id IS NOT NULL AND @organization_id<>@owner THROW 52003,'Wrong organization scope',1;
  SET @organization_id=@owner;
 END;
 IF @admin=1 BEGIN
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@actor_id AND platform_role='ADMIN')
   THROW 52003,'Administrator role required',1;
 END ELSE IF @organization_id IS NOT NULL BEGIN
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_organization_memberships m WITH(HOLDLOCK)
   JOIN dbo.tc_organizations o ON o.id=m.organization_id
   WHERE m.user_id=@actor_id AND m.organization_id=@organization_id AND m.role='MANAGER' AND m.active=1 AND o.status='APPROVED')
   THROW 52003,'Current manager membership required',1;
 END;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_EventRead
 @actor_id uniqueidentifier,@actor_version int,@admin bit=0,@kind varchar(30),
 @organization_id uniqueidentifier=NULL,@event_id uniqueidentifier=NULL,@zone_id uniqueidentifier=NULL,
 @status varchar(30)=NULL,@page int=1,@page_size int=20
AS
BEGIN
 SET NOCOUNT ON;
 IF @page<1 OR @page_size<1 OR @page_size>100 OR CONVERT(bigint,@page-1)*@page_size>1000000 THROW 52000,'Invalid page',1;
 IF @status IS NOT NULL AND @status NOT IN('DRAFT','PENDING_APPROVAL','REJECTED','PUBLISHED','CANCELLED') THROW 52000,'Invalid status',1;
 EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@organization_id OUTPUT,@event_id OUTPUT,@zone_id;
 IF @kind='AUTHORIZE' BEGIN
  SELECT @organization_id organization_id,@event_id event_id; RETURN;
 END;
 IF @kind='COVER_CONTEXT' BEGIN
  SELECT organization_id,status,version,event_image_public_id,COALESCE(event_image,cover_image_url) FROM dbo.tc_events WHERE id=@event_id; RETURN;
 END;
 IF @kind='ORGANIZATIONS' BEGIN
  SELECT CONCAT('{"data":',COALESCE((SELECT LOWER(CONVERT(varchar(36),o.id)) organizationId,o.name FROM dbo.tc_organizations o
   JOIN dbo.tc_organization_memberships m ON m.organization_id=o.id
   WHERE m.user_id=@actor_id AND m.active=1 AND m.role='MANAGER' AND o.status='APPROVED'
   ORDER BY o.name,o.id FOR JSON PATH),'[]'),'}') json; RETURN;
 END;
 IF @kind='RULES' BEGIN
  IF @admin<>1 OR @event_id IS NULL THROW 52003,'Admin event scope required',1;
  SELECT CONCAT('{"data":',COALESCE((SELECT LOWER(CONVERT(varchar(36),r.id)) commissionRuleId,CONVERT(varchar(30),r.rate_percent) ratePercent,
   CONVERT(varchar(40),r.fixed_fee) fixedFee,CONVERT(varchar(30),r.effective_from,126)+'Z' effectiveFrom,
   CONVERT(varchar(30),r.effective_to,126)+'Z' effectiveTo FROM dbo.tc_commission_rules r
   JOIN dbo.tc_events e ON e.organization_id=r.organization_id
   WHERE e.id=@event_id AND r.effective_from<=SYSUTCDATETIME() AND r.effective_to>SYSUTCDATETIME()
   AND r.effective_from<=e.start_time AND r.effective_to>e.start_time ORDER BY r.effective_from,r.id FOR JSON PATH),'[]'),'}') json; RETURN;
 END;
 IF @kind='LAYOUT' BEGIN
  IF @event_id IS NULL THROW 52000,'Event required',1;
  SELECT CONCAT('{"data":',COALESCE((SELECT LOWER(CONVERT(varchar(36),z.zone_id)) zoneId,LOWER(CONVERT(varchar(36),z.event_id)) eventId,z.name,z.type zoneType,
   CONVERT(varchar(40),z.price) basePrice,z.capacity totalCapacity,z.held_quantity heldQuantity,z.sold_quantity soldQuantity,
   z.available_quantity availableSeats,JSON_QUERY((SELECT LOWER(CONVERT(varchar(36),s.id)) seatId,s.row_name rowName,s.seat_number seatNumber,
    s.label seatLabel,s.status FROM dbo.tc_seats s WHERE s.zone_id=z.zone_id ORDER BY LEN(s.row_name),s.row_name,s.seat_number,s.id FOR JSON PATH)) seats
   FROM dbo.vw_ZoneInventory z WHERE z.event_id=@event_id ORDER BY z.name,z.zone_id FOR JSON PATH),'[]'),'}') json; RETURN;
 END;
 IF @kind NOT IN('LIST','DETAIL') THROW 52000,'Invalid read operation',1;
 IF @admin=0 AND @organization_id IS NULL THROW 52003,'Organization scope required',1;
 DECLARE @total bigint=(SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE (@admin=1 OR organization_id=@organization_id)
   AND (@status IS NULL OR status=@status) AND (@event_id IS NULL OR id=@event_id));
 IF @kind='DETAIL' AND @event_id IS NULL THROW 52000,'Event required',1;
 DECLARE @data nvarchar(max)=(SELECT LOWER(CONVERT(varchar(36),e.id)) eventId,LOWER(CONVERT(varchar(36),e.organization_id)) organizationId,o.name organizationName,
  LOWER(CONVERT(varchar(36),e.category_id)) categoryId,c.name category,e.title,e.description,e.venue_name venueName,e.venue_address venueAddress,
  COALESCE(e.event_image,e.cover_image_url) coverImageUrl,e.status,e.rejection_reason rejectionReason,e.version,
  CONVERT(varchar(30),e.sale_start,126)+'Z' saleStartTime,CONVERT(varchar(30),e.sale_end,126)+'Z' saleEndTime,
  CONVERT(varchar(30),e.start_time,126)+'Z' startTime,CONVERT(varchar(30),e.end_time,126)+'Z' endTime,
  CONVERT(varchar(30),e.created_at,126)+'Z' createdAt,
  (SELECT COUNT_BIG(*) FROM dbo.tc_zones z WHERE z.event_id=e.id) totalZones,
  COALESCE((SELECT SUM(CONVERT(bigint,capacity)) FROM dbo.vw_ZoneInventory z WHERE z.event_id=e.id),0) totalCapacity
  FROM dbo.tc_events e JOIN dbo.tc_organizations o ON o.id=e.organization_id JOIN dbo.tc_event_categories c ON c.id=e.category_id
  WHERE (@admin=1 OR e.organization_id=@organization_id) AND (@status IS NULL OR e.status=@status)
  AND (@event_id IS NULL OR e.id=@event_id) ORDER BY e.created_at DESC,e.id
  OFFSET (@page-1)*@page_size ROWS FETCH NEXT @page_size ROWS ONLY FOR JSON PATH,INCLUDE_NULL_VALUES);
 IF @kind='DETAIL' SELECT CONCAT('{"data":',JSON_QUERY(@data,'$[0]'),'}') json;
 ELSE SELECT CONCAT('{"data":',COALESCE(@data,'[]'),',"page":',@page,',"pageSize":',@page_size,',"total":',@total,
  ',"totalPages":',@total/@page_size+CASE WHEN @total%@page_size=0 THEN 0 ELSE 1 END,'}') json;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_EventReceipt
 @actor_id uniqueidentifier,@actor_version int,@scope varchar(30),@idempotency_key varchar(128),@request_hash char(64)
AS
BEGIN
 SET NOCOUNT ON;
 DECLARE @organization_id uniqueidentifier,@event_id uniqueidentifier=NULL,@admin bit=CASE WHEN @scope='EVENT_REVIEW' THEN 1 ELSE 0 END;
 SELECT @organization_id=organization_id FROM dbo.tc_event_command_receipts
  WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key;
 EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@organization_id OUTPUT,@event_id OUTPUT;
 IF EXISTS(SELECT 1 FROM dbo.tc_event_command_receipts WHERE actor_id=@actor_id AND scope=@scope
  AND idempotency_key=@idempotency_key AND request_hash<>@request_hash) THROW 52009,'Idempotency payload conflict',1;
 SELECT status_code,response_json,organization_id,event_id FROM dbo.tc_event_command_receipts
  WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_EventCommand
 @actor_id uniqueidentifier,@actor_version int,@scope varchar(30),@idempotency_key varchar(128),@request_hash char(64),
 @operation varchar(30),@organization_id uniqueidentifier=NULL,@event_id uniqueidentifier=NULL,@zone_id uniqueidentifier=NULL,
 @payload nvarchar(max)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @started=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_event_command;
 BEGIN TRY
  IF @payload IS NULL OR ISJSON(@payload)<>1 OR @idempotency_key IS NULL OR LEN(@idempotency_key) NOT BETWEEN 1 AND 128 OR @scope IS NULL OR @scope NOT IN('EVENT','EVENT_REVIEW') OR @request_hash IS NULL THROW 52000,'Invalid command',1;
  DECLARE @admin bit=CASE WHEN @scope='EVENT_REVIEW' THEN 1 ELSE 0 END,@stored_org uniqueidentifier,@stored_event uniqueidentifier;
  EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@stored_org OUTPUT,@stored_event OUTPUT;
  DECLARE @lock_result int,@resource nvarchar(255)=CONCAT('tc:event:command:',@actor_id,':',@scope,':',CONVERT(varchar(64),HASHBYTES('SHA2_256',@idempotency_key),2));
  EXEC @lock_result=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
  IF @lock_result<0 THROW 52009,'Command already processing',1;
  SELECT @stored_org=organization_id FROM dbo.tc_event_command_receipts WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key;
  IF @stored_org IS NOT NULL BEGIN
   SET @stored_event=NULL;
   EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@stored_org OUTPUT,@stored_event OUTPUT;
   IF EXISTS(SELECT 1 FROM dbo.tc_event_command_receipts WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key AND request_hash<>@request_hash)
    THROW 52009,'Idempotency payload conflict',1;
   IF @started=1 COMMIT;
   SELECT status_code,response_json,organization_id,event_id FROM dbo.tc_event_command_receipts WHERE actor_id=@actor_id AND scope=@scope AND idempotency_key=@idempotency_key; RETURN;
  END;
  EXEC dbo.usp_EventAuthorize @actor_id,@actor_version,@admin,@organization_id OUTPUT,@event_id OUTPUT,@zone_id;
  IF @organization_id IS NULL THROW 52003,'Organization scope required',1;
  IF (@admin=1 AND @operation NOT IN('PUBLISH','REJECT')) OR (@admin=0 AND @operation IN('PUBLISH','REJECT')) THROW 52003,'Wrong command scope',1;
  DECLARE @status varchar(30),@version bigint,@now datetime2(3)=SYSUTCDATETIME();
  IF @event_id IS NOT NULL SELECT @status=status,@version=version FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
  IF @event_id IS NOT NULL AND @status IS NULL THROW 52004,'Event was deleted',1;
  DECLARE @expected bigint=TRY_CONVERT(bigint,JSON_VALUE(@payload,'$.expectedVersion'));
  IF @expected IS NOT NULL AND @expected<>@version THROW 52009,'Event changed since it was read',1;
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=@actor_id;
  DECLARE @title nvarchar(250)=JSON_VALUE(@payload,'$.title'),@description nvarchar(max)=(SELECT [value] FROM OPENJSON(@payload) WHERE [key]='description'),
   @venue nvarchar(250)=JSON_VALUE(@payload,'$.venueName'),@address nvarchar(500)=JSON_VALUE(@payload,'$.venueAddress'),
   @category uniqueidentifier=TRY_CONVERT(uniqueidentifier,JSON_VALUE(@payload,'$.categoryId')),
   @sale_start datetime2(3)=TRY_CONVERT(datetime2(3),JSON_VALUE(@payload,'$.saleStartTime'),127),
   @sale_end datetime2(3)=TRY_CONVERT(datetime2(3),JSON_VALUE(@payload,'$.saleEndTime'),127),
   @start datetime2(3)=TRY_CONVERT(datetime2(3),JSON_VALUE(@payload,'$.startTime'),127),
   @end datetime2(3)=TRY_CONVERT(datetime2(3),JSON_VALUE(@payload,'$.endTime'),127),
   @response nvarchar(max),@code int=200,@cover nvarchar(2048)=JSON_VALUE(@payload,'$.coverImageUrl');
  IF @operation='CREATE' BEGIN
   IF @admin=1 OR @event_id IS NOT NULL OR @sale_start IS NULL OR @sale_end IS NULL OR @start IS NULL OR @end IS NULL OR @sale_start<=@now OR @title IS NULL OR LEN(@title) NOT BETWEEN 5 AND 200 THROW 52000,'Invalid draft',1;
   IF NOT(@sale_start<@sale_end AND @sale_end<=@start AND @start<@end) OR @category IS NULL OR @description IS NULL OR LEN(@description) NOT BETWEEN 1 AND 5000 OR @venue IS NULL OR LEN(@venue)=0 OR @address IS NULL OR LEN(@address)=0 THROW 52000,'Invalid event fields or schedule',1;
   SET @event_id=NEWID(); SET @status='DRAFT'; SET @code=201;
   INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status)
    VALUES(@event_id,@organization_id,@category,@title,@description,@venue,@address,@sale_start,@sale_end,@start,@end,@status);
  END ELSE IF @operation='EDIT' BEGIN
   IF @status NOT IN('DRAFT','REJECTED','PUBLISHED') THROW 52009,'Event is locked',1;
   IF @status='PUBLISHED' AND EXISTS(SELECT 1 FROM OPENJSON(@payload) WHERE [key] NOT IN('description','expectedVersion')) THROW 52009,'Only description may change after publication',1;
   IF (@description IS NOT NULL AND LEN(@description) NOT BETWEEN 1 AND 5000) OR (@title IS NOT NULL AND LEN(@title) NOT BETWEEN 5 AND 200) THROW 52000,'Invalid event text',1;
   UPDATE dbo.tc_events SET title=COALESCE(@title,title),description=COALESCE(@description,description),category_id=COALESCE(@category,category_id),
    venue_name=COALESCE(@venue,venue_name),venue_address=COALESCE(@address,venue_address),sale_start=COALESCE(@sale_start,sale_start),
    sale_end=COALESCE(@sale_end,sale_end),start_time=COALESCE(@start,start_time),end_time=COALESCE(@end,end_time),version=version+1 WHERE id=@event_id;
  END ELSE IF @operation='DELETE' BEGIN
   IF @status<>'DRAFT' OR EXISTS(SELECT 1 FROM dbo.tc_orders WHERE event_id=@event_id) OR EXISTS(SELECT 1 FROM dbo.tc_ticket_holds WHERE event_id=@event_id) THROW 52009,'Draft cannot be deleted',1;
   DELETE s FROM dbo.tc_seats s JOIN dbo.tc_zones z ON z.id=s.zone_id WHERE z.event_id=@event_id;
   DELETE dbo.tc_zones WHERE event_id=@event_id; DELETE dbo.tc_events WHERE id=@event_id; SET @status='DELETED';
  END ELSE IF @operation='SUBMIT' BEGIN
   IF @status='PENDING_APPROVAL' BEGIN SET @status='PENDING_APPROVAL'; END
   ELSE BEGIN
    IF @status NOT IN('DRAFT','REJECTED') THROW 52009,'Event cannot be submitted',1;
    IF NOT EXISTS(SELECT 1 FROM dbo.vw_ZoneInventory WHERE event_id=@event_id AND capacity>0) THROW 52000,'Event requires inventory',1;
    IF EXISTS(SELECT 1 FROM dbo.vw_ZoneInventory WHERE event_id=@event_id AND capacity<=0) THROW 52000,'All zones require inventory',1;
    IF EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND start_time<=@now) THROW 52000,'Event schedule has expired',1;
    UPDATE dbo.tc_events SET status='PENDING_APPROVAL',rejection_reason=NULL,version=version+1 WHERE id=@event_id; SET @status='PENDING_APPROVAL';
   END;
  END ELSE IF @operation IN('ZONE_CREATE','ZONE_EDIT','ZONE_DELETE') BEGIN
   IF @status NOT IN('DRAFT','REJECTED','PUBLISHED') THROW 52009,'Event inventory is locked',1;
   IF @status='PUBLISHED' AND (@operation<>'ZONE_EDIT' OR EXISTS(SELECT 1 FROM OPENJSON(@payload) WHERE [key] NOT IN('basePrice','expectedVersion'))) THROW 52009,'Published layout is immutable',1;
   DECLARE @type varchar(20)=JSON_VALUE(@payload,'$.zoneType'),@name nvarchar(120)=JSON_VALUE(@payload,'$.name'),
    @price decimal(19,0)=TRY_CONVERT(decimal(19,0),JSON_VALUE(@payload,'$.basePrice')),
    @capacity int=TRY_CONVERT(int,JSON_VALUE(@payload,'$.capacity')),@rows int=TRY_CONVERT(int,JSON_VALUE(@payload,'$.rows')),
    @per int=TRY_CONVERT(int,JSON_VALUE(@payload,'$.seatsPerRow')),@old_type varchar(20),@occupied bigint;
   IF @zone_id IS NOT NULL SELECT @old_type=type,@occupied=held_quantity+sold_quantity FROM dbo.tc_zones WITH(UPDLOCK,HOLDLOCK) WHERE id=@zone_id AND event_id=@event_id;
   IF @operation<>'ZONE_CREATE' AND @old_type IS NULL THROW 52004,'Zone not found',1;
   IF @operation='ZONE_DELETE' BEGIN
    IF @occupied>0 OR EXISTS(SELECT 1 FROM dbo.tc_seats WHERE zone_id=@zone_id AND status<>'AVAILABLE') THROW 52009,'Zone has allocated inventory',1;
    DELETE dbo.tc_seats WHERE zone_id=@zone_id; DELETE dbo.tc_zones WHERE id=@zone_id;
   END ELSE BEGIN
    IF @price IS NULL OR @price<0 THROW 52000,'Invalid price',1;
    IF @operation='ZONE_CREATE' BEGIN
     IF @name IS NULL OR LEN(@name) NOT BETWEEN 1 AND 120 OR @type IS NULL OR @type NOT IN('SEATED','STANDING') THROW 52000,'Invalid Zone',1;
     IF (@type='SEATED' AND (@rows IS NULL OR @per IS NULL OR @capacity IS NOT NULL))
       OR (@type='STANDING' AND (@capacity IS NULL OR @capacity NOT BETWEEN 1 AND 10000000 OR @rows IS NOT NULL OR @per IS NOT NULL)) THROW 52000,'Zone fields do not match type',1;
     SET @zone_id=NEWID(); SET @code=201;
     INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES(@zone_id,@event_id,@name,@type,@price,CASE WHEN @type='STANDING' THEN @capacity END);
    END ELSE BEGIN
     IF @type IS NOT NULL AND @type<>@old_type THROW 52009,'Zone type cannot change',1;
     SET @type=@old_type;
     IF @capacity IS NOT NULL AND (@type<>'STANDING' OR @capacity NOT BETWEEN 1 AND 10000000 OR @capacity<@occupied) THROW 52009,'Capacity below allocated inventory',1;
     UPDATE dbo.tc_zones SET name=COALESCE(@name,name),price=@price,capacity=COALESCE(@capacity,capacity),version=version+1 WHERE id=@zone_id;
    END;
    IF @rows IS NOT NULL OR @per IS NOT NULL BEGIN
     IF @type<>'SEATED' OR @rows IS NULL OR @per IS NULL OR @rows NOT BETWEEN 1 AND 1000 OR @per NOT BETWEEN 1 AND 1000 OR CONVERT(bigint,@rows)*@per>10000 THROW 52000,'Invalid seat matrix',1;
     IF EXISTS(SELECT 1 FROM dbo.tc_seats WHERE zone_id=@zone_id AND status<>'AVAILABLE') THROW 52009,'Allocated seat layout is immutable',1;
     DELETE dbo.tc_seats WHERE zone_id=@zone_id;
     DECLARE @row int=1,@seat int,@label nvarchar(20),@n int;
     WHILE @row<=@rows BEGIN
      SET @n=@row; SET @label=''; WHILE @n>0 BEGIN SET @label=CHAR(65+(@n-1)%26)+@label; SET @n=(@n-1)/26; END;
      SET @seat=1; WHILE @seat<=@per BEGIN
       INSERT dbo.tc_seats(zone_id,row_name,seat_number,label) VALUES(@zone_id,@label,@seat,CONCAT(@label,@seat)); SET @seat+=1;
      END; SET @row+=1;
     END;
    END;
   END;
   UPDATE dbo.tc_events SET version=version+1 WHERE id=@event_id;
  END ELSE IF @operation='COVER' BEGIN
   IF @status NOT IN('DRAFT','REJECTED') THROW 52009,'Event media is locked',1;
   IF @cover IS NULL OR @cover NOT LIKE 'https://%' OR JSON_VALUE(@payload,'$.publicId') IS NULL THROW 52000,'Invalid storage metadata',1;
   UPDATE dbo.tc_events SET cover_image_url=@cover,event_image=@cover,event_image_public_id=JSON_VALUE(@payload,'$.publicId'),version=version+1 WHERE id=@event_id;
  END ELSE IF @operation='PUBLISH' BEGIN
   DECLARE @rule uniqueidentifier=TRY_CONVERT(uniqueidentifier,JSON_VALUE(@payload,'$.commissionRuleId'));
   IF @rule IS NULL THROW 52000,'Commission rule required',1;
   IF @status<>'PUBLISHED' AND NOT EXISTS(SELECT 1 FROM dbo.tc_commission_rules r JOIN dbo.tc_events e ON e.organization_id=r.organization_id
    WHERE e.id=@event_id AND r.id=@rule AND r.effective_from<=e.start_time AND r.effective_to>e.start_time) THROW 52000,'Rule does not cover event date',1;
   EXEC dbo.usp_PublishEvent @event_id,@actor_id,@rule; SET @status='PUBLISHED';
   IF NULLIF(JSON_VALUE(@payload,'$.adminNote'),'') IS NOT NULL
    INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail) VALUES(@actor_id,'EVENT_REVIEW_NOTE','EVENT',@event_id,JSON_VALUE(@payload,'$.adminNote'));
  END ELSE IF @operation='REJECT' BEGIN
   DECLARE @reason nvarchar(500)=JSON_VALUE(@payload,'$.reason');
   IF LEN(LTRIM(RTRIM(@reason))) NOT BETWEEN 10 AND 500 OR @reason IS NULL THROW 52000,'Rejection reason required',1;
   IF @status='REJECTED' AND EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND rejection_reason=@reason) SET @status='REJECTED';
   ELSE BEGIN
    IF @status<>'PENDING_APPROVAL' THROW 52009,'Event is not pending approval',1;
    UPDATE dbo.tc_events SET status='REJECTED',rejection_reason=@reason,version=version+1 WHERE id=@event_id; SET @status='REJECTED';
   END;
  END ELSE THROW 52000,'Unsupported operation',1;
  IF @operation NOT IN('SUBMIT','PUBLISH','REJECT')
   INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail) VALUES(@actor_id,CONCAT('EVENT_',@operation),'EVENT',@event_id,NULL);
  SET @response=CONCAT('{"data":',(SELECT LOWER(CONVERT(varchar(36),@event_id)) eventId,LOWER(CONVERT(varchar(36),@organization_id)) organizationId,LOWER(CONVERT(varchar(36),@zone_id)) zoneId,@status status,@cover coverImageUrl FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES),'}');
  INSERT dbo.tc_event_command_receipts(actor_id,scope,idempotency_key,request_hash,organization_id,event_id,status_code,response_json)
   VALUES(@actor_id,@scope,@idempotency_key,@request_hash,@organization_id,@event_id,@code,@response);
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=NULL;
  IF @started=1 COMMIT;
  SELECT @code status_code,@response response_json,@organization_id organization_id,@event_id event_id;
 END TRY BEGIN CATCH
  EXEC sys.sp_set_session_context @key=N'actor_id',@value=NULL;
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK;
  ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_event_command;
  THROW;
 END CATCH;
END;
GO
GRANT SELECT ON dbo.vw_PublicEventSeats TO tc_buyer;
GRANT EXECUTE ON dbo.usp_EventRead TO tc_manager;
GRANT EXECUTE ON dbo.usp_EventRead TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_EventReceipt TO tc_manager;
GRANT EXECUTE ON dbo.usp_EventReceipt TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_EventCommand TO tc_manager;
GRANT EXECUTE ON dbo.usp_EventCommand TO tc_platform_admin;
GO
