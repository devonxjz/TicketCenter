/* 055: the event image save/cleanup state machine lives in the database.
   EventRepository used to INSERT/UPDATE tc_event_image_requests, tc_event_image_assets and (in maintenance) tc_events
   directly. Every write now goes through a procedure; tc_manager and tc_worker lose their direct write grants.
   Procedures run inside the caller's transaction (the repository opens one per call) and report failures by THROW:
     52701 EVENT_NOT_FOUND            52710 CATEGORY_NOT_FOUND
     52703 SESSION_EXPIRED            52711 EVENT_VERSION_CONFLICT
     52704 EVENT_FORBIDDEN            52712 EVENT_NOT_EDITABLE (edit)
     52705 ORGANIZATION_NOT_APPROVED  52713 EVENT_NOT_EDITABLE (cover)
     52706 MAINTENANCE_FORBIDDEN      52714 REQUEST_LOST
     52707 IDEMPOTENCY_CONFLICT       52715 STALE_REQUEST
     52708 REQUEST_ABORTED            52716 IMAGE_METADATA_INVALID
     52709 REQUEST_IN_PROGRESS        52717 ASSET_UNAVAILABLE
                                      52718 invalid cleanup limit */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER PROCEDURE dbo.usp_EventImageAuthorize
 @actor_id uniqueidentifier,@auth_version int,@organization_id uniqueidentifier,@maintenance bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @maintenance=1 BEGIN
  IF NOT(USER_NAME()='dbo' OR IS_ROLEMEMBER('db_owner')=1 OR IS_SRVROLEMEMBER('sysadmin')=1)
   THROW 52706,'Database owner connection is required for maintenance',1;
  RETURN;
 END;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND auth_version=@auth_version)
  THROW 52703,'Current session is no longer valid',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_organization_memberships WITH(UPDLOCK,HOLDLOCK)
               WHERE user_id=@actor_id AND organization_id=@organization_id AND active=1 AND role='MANAGER')
  THROW 52704,'Current Manager membership is required',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_organizations WITH(HOLDLOCK) WHERE id=@organization_id AND status='APPROVED')
  THROW 52705,'Organization is not approved',1;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_EventImageCheckTarget
 @operation varchar(10),@event_id uniqueidentifier,@expected_version bigint,@has_fields bit,@category_id uniqueidentifier
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @has_fields=1 AND NOT EXISTS(SELECT 1 FROM dbo.tc_event_categories WHERE id=@category_id)
  THROW 52710,'Unknown category',1;
 IF @operation='CREATE' RETURN;
 DECLARE @status varchar(30),@version bigint;
 SELECT @status=status,@version=version FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
 IF @status IS NULL THROW 52701,'Event was not found',1;
 IF @version<>@expected_version THROW 52711,'Event has changed; reload before saving',1;
 IF @operation='EDIT' AND @status NOT IN('DRAFT','REJECTED') THROW 52712,'Only draft or rejected Events can be edited',1;
 IF @operation='COVER' AND @status NOT IN('DRAFT','REJECTED','PUBLISHED') THROW 52713,'Event cover cannot be changed in this state',1;
END;
GO
/* Claims (or re-claims, or replays) one idempotent save request. Returns one row; result_json is set only for a replay. */
CREATE OR ALTER PROCEDURE dbo.usp_EventImageClaim
 @actor_id uniqueidentifier,@auth_version int,@maintenance bit,@request_id uniqueidentifier,@fingerprint char(64),
 @operation varchar(10),@target_id uniqueidentifier,@expected_version bigint,@has_fields bit,@category_id uniqueidentifier,@has_image bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @row_actor uniqueidentifier=CASE WHEN @maintenance=1 THEN CAST('00000000-0000-0000-0000-000000000000' AS uniqueidentifier) ELSE @actor_id END;
 DECLARE @event_id uniqueidentifier,@organization_id uniqueidentifier,@token uniqueidentifier,@public_id varchar(255);
 IF @operation='CREATE' SELECT @event_id=NEWID(),@organization_id=@target_id;
 ELSE BEGIN
  SET @event_id=@target_id;
  SELECT @organization_id=organization_id FROM dbo.tc_events WHERE id=@target_id;
  IF @organization_id IS NULL THROW 52701,'Event was not found',1;
 END;
 EXEC dbo.usp_EventImageAuthorize @actor_id,@auth_version,@organization_id,@maintenance;

 DECLARE @r_fingerprint char(64),@r_event uniqueidentifier,@r_org uniqueidentifier,@r_public varchar(255),
         @r_state varchar(12),@r_until datetime2(7),@r_json nvarchar(max);
 SELECT @r_fingerprint=fingerprint,@r_event=event_id,@r_org=organization_id,@r_public=public_id,
        @r_state=state,@r_until=lease_until,@r_json=result_json
 FROM dbo.tc_event_image_requests WITH(UPDLOCK,HOLDLOCK) WHERE actor_id=@row_actor AND request_id=@request_id;

 IF @r_state IS NOT NULL BEGIN
  IF @r_fingerprint<>@fingerprint COLLATE Latin1_General_BIN2 THROW 52707,'Request key was already used for different input',1;
  SELECT @event_id=@r_event,@organization_id=@r_org;
  EXEC dbo.usp_EventImageAuthorize @actor_id,@auth_version,@organization_id,@maintenance;
  IF @r_state='COMMITTED' BEGIN
   SELECT @row_actor actor_id,@event_id event_id,@organization_id organization_id,CAST(NULL AS uniqueidentifier) lease_token,@r_public public_id,@r_json result_json;
   RETURN;
  END;
  IF @r_state='ABORTED' THROW 52708,'Save has been cancelled; use a new request key',1;
  IF @r_until>SYSUTCDATETIME() THROW 52709,'Save is still running; retry shortly',1;
  EXEC dbo.usp_EventImageCheckTarget @operation,@event_id,@expected_version,@has_fields,@category_id;
  SET @token=NEWID();
  UPDATE dbo.tc_event_image_requests SET lease_token=@token,lease_until=DATEADD(minute,10,SYSUTCDATETIME()),updated_at=SYSUTCDATETIME()
  WHERE actor_id=@row_actor AND request_id=@request_id;
  SELECT @row_actor actor_id,@event_id event_id,@organization_id organization_id,@token lease_token,@r_public public_id,CAST(NULL AS nvarchar(max)) result_json;
  RETURN;
 END;

 EXEC dbo.usp_EventImageCheckTarget @operation,@event_id,@expected_version,@has_fields,@category_id;
 SET @token=NEWID();
 IF @has_image=1
  SET @public_id='ticketcenter/events/'+LOWER(CONVERT(varchar(36),@event_id))+'/'+LOWER(CONVERT(varchar(36),@request_id));
 INSERT dbo.tc_event_image_requests(actor_id,request_id,fingerprint,operation,event_id,organization_id,expected_version,public_id,state,lease_token,lease_until)
 VALUES(@row_actor,@request_id,@fingerprint,@operation,@event_id,@organization_id,@expected_version,@public_id,'PENDING',@token,DATEADD(minute,10,SYSUTCDATETIME()));
 IF @public_id IS NOT NULL
  INSERT dbo.tc_event_image_assets(public_id,event_id,state) VALUES(@public_id,@event_id,'AVAILABLE');
 SELECT @row_actor actor_id,@event_id event_id,@organization_id organization_id,@token lease_token,@public_id public_id,CAST(NULL AS nvarchar(max)) result_json;
END;
GO
/* Validates the lease, attaches the uploaded image and writes the event. Returns one row:
   committed=1 with result_json when the request was already committed (replay), otherwise committed=0 and the
   previous image key; the caller then snapshots the event and calls usp_EventImageCommit in the same transaction. */
CREATE OR ALTER PROCEDURE dbo.usp_EventImageSave
 @actor_id uniqueidentifier,@auth_version int,@maintenance bit,@request_id uniqueidentifier,@lease_token uniqueidentifier,
 @event_id uniqueidentifier,@organization_id uniqueidentifier,@operation varchar(10),@expected_version bigint,
 @has_fields bit,@category_id uniqueidentifier=NULL,@title nvarchar(250)=NULL,@description nvarchar(max)=NULL,
 @venue_name nvarchar(250)=NULL,@venue_address nvarchar(500)=NULL,@sale_start datetime2(3)=NULL,@sale_end datetime2(3)=NULL,
 @start_time datetime2(3)=NULL,@end_time datetime2(3)=NULL,@asset_public_id varchar(255)=NULL,@asset_secure_url varchar(4000)=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @row_actor uniqueidentifier=CASE WHEN @maintenance=1 THEN CAST('00000000-0000-0000-0000-000000000000' AS uniqueidentifier) ELSE @actor_id END;
 EXEC dbo.usp_EventImageAuthorize @actor_id,@auth_version,@organization_id,@maintenance;

 DECLARE @r_state varchar(12),@r_token uniqueidentifier,@r_json nvarchar(max),@r_public varchar(255);
 SELECT @r_state=state,@r_token=lease_token,@r_json=result_json,@r_public=public_id
 FROM dbo.tc_event_image_requests WITH(UPDLOCK,HOLDLOCK) WHERE actor_id=@row_actor AND request_id=@request_id;
 IF @r_state IS NULL THROW 52714,'Save intent is missing',1;
 IF @r_state='COMMITTED' BEGIN
  SELECT CAST(1 AS bit) committed,@r_json result_json,CAST(NULL AS varchar(255)) old_public_id;
  RETURN;
 END;
 IF @r_state<>'PENDING' OR @lease_token IS NULL OR @r_token<>@lease_token THROW 52715,'Save lease has changed; retry',1;

 EXEC dbo.usp_EventImageCheckTarget @operation,@event_id,@expected_version,@has_fields,@category_id;

 IF @asset_public_id IS NOT NULL BEGIN
  IF @r_public IS NULL OR @r_public<>@asset_public_id COLLATE Latin1_General_BIN2
     OR @asset_secure_url IS NULL OR LEN(@asset_secure_url)>2048
     OR LEFT(@asset_secure_url,27)<>'https://res.cloudinary.com/' COLLATE Latin1_General_BIN2
   THROW 52716,'Image provider returned invalid metadata',1;
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_event_image_assets WITH(UPDLOCK,HOLDLOCK) WHERE public_id=@asset_public_id AND state='AVAILABLE')
   THROW 52717,'Image is scheduled for cleanup',1;
  UPDATE dbo.tc_event_image_assets SET secure_url=@asset_secure_url WHERE public_id=@asset_public_id;
 END;

 DECLARE @old_public_id varchar(255),@found bit=0;
 IF @operation<>'CREATE' BEGIN
  SELECT @old_public_id=event_image_public_id,@found=1 FROM dbo.tc_events WHERE id=@event_id;
  IF @found=0 THROW 52701,'Event was not found',1;
 END;

 IF @maintenance=0 BEGIN
  DECLARE @create bit=CASE WHEN @operation='CREATE' THEN 1 ELSE 0 END,@cover_only bit=CASE WHEN @operation='COVER' THEN 1 ELSE 0 END;
  EXEC dbo.usp_SaveEventDraft @event_id,@organization_id,@actor_id,@create,@expected_version,@category_id,@title,@description,
       @venue_name,@venue_address,@sale_start,@sale_end,@start_time,@end_time,@asset_secure_url,@asset_public_id,@cover_only;
 END
 ELSE IF @operation='CREATE'
  INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status,version,event_image,event_image_public_id)
  VALUES(@event_id,@organization_id,@category_id,@title,@description,@venue_name,@venue_address,@sale_start,@sale_end,@start_time,@end_time,'DRAFT',0,@asset_secure_url,@asset_public_id);
 ELSE BEGIN
  UPDATE dbo.tc_events SET version=version+1,
   category_id=CASE WHEN @operation='EDIT' THEN @category_id ELSE category_id END,
   title=CASE WHEN @operation='EDIT' THEN @title ELSE title END,
   description=CASE WHEN @operation='EDIT' THEN @description ELSE description END,
   venue_name=CASE WHEN @operation='EDIT' THEN @venue_name ELSE venue_name END,
   venue_address=CASE WHEN @operation='EDIT' THEN @venue_address ELSE venue_address END,
   sale_start=CASE WHEN @operation='EDIT' THEN @sale_start ELSE sale_start END,
   sale_end=CASE WHEN @operation='EDIT' THEN @sale_end ELSE sale_end END,
   start_time=CASE WHEN @operation='EDIT' THEN @start_time ELSE start_time END,
   end_time=CASE WHEN @operation='EDIT' THEN @end_time ELSE end_time END,
   event_image=CASE WHEN @asset_public_id IS NOT NULL THEN @asset_secure_url ELSE event_image END,
   event_image_public_id=CASE WHEN @asset_public_id IS NOT NULL THEN @asset_public_id ELSE event_image_public_id END
  WHERE id=@event_id AND version=@expected_version;
  IF @@ROWCOUNT<>1 THROW 52711,'Event has changed; reload before saving',1;
 END;
 SELECT CAST(0 AS bit) committed,CAST(NULL AS nvarchar(max)) result_json,@old_public_id old_public_id;
END;
GO
/* Records the saved event snapshot on the request and schedules the replaced image for cleanup. */
CREATE OR ALTER PROCEDURE dbo.usp_EventImageCommit
 @actor_id uniqueidentifier,@request_id uniqueidentifier,@lease_token uniqueidentifier,@result_json nvarchar(max),
 @old_public_id varchar(255)=NULL,@new_public_id varchar(255)=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 UPDATE dbo.tc_event_image_requests SET state='COMMITTED',result_json=@result_json,updated_at=SYSUTCDATETIME()
 WHERE actor_id=@actor_id AND request_id=@request_id AND state='PENDING' AND lease_token=@lease_token;
 IF @@ROWCOUNT<>1 THROW 52715,'Save lease has changed; retry',1;
 IF @new_public_id IS NOT NULL AND @old_public_id IS NOT NULL AND @old_public_id<>@new_public_id COLLATE Latin1_General_BIN2
  UPDATE dbo.tc_event_image_assets SET available_at=SYSUTCDATETIME() WHERE public_id=@old_public_id AND state='AVAILABLE';
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_EventImageRelease
 @actor_id uniqueidentifier,@request_id uniqueidentifier,@lease_token uniqueidentifier,@abort bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 UPDATE dbo.tc_event_image_requests
 SET state=CASE WHEN @abort=1 THEN 'ABORTED' ELSE 'PENDING' END,lease_until=SYSUTCDATETIME(),updated_at=SYSUTCDATETIME()
 WHERE actor_id=@actor_id AND request_id=@request_id AND lease_token=@lease_token AND state='PENDING';
END;
GO
/* Expires abandoned requests, then leases unreferenced images for deletion at the storage provider. */
CREATE OR ALTER PROCEDURE dbo.usp_EventImageClaimCleanup
 @limit int
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @limit IS NULL OR @limit NOT BETWEEN 1 AND 100 THROW 52718,'Invalid cleanup limit',1;
 UPDATE dbo.tc_event_image_requests SET state='ABORTED',updated_at=SYSUTCDATETIME()
 WHERE state='PENDING' AND lease_until<SYSUTCDATETIME() AND updated_at<DATEADD(day,-1,SYSUTCDATETIME());

 DECLARE @uuid varchar(500)=REPLICATE('[0-9a-fA-F-]',36);
 DECLARE @leased TABLE(public_id varchar(255) PRIMARY KEY,lease_token uniqueidentifier,available_at datetime2(7));
 WITH due AS (
  SELECT TOP(@limit) a.public_id,a.state,a.lease_token,a.lease_until,a.attempts,a.available_at
  FROM dbo.tc_event_image_assets a WITH(UPDLOCK,HOLDLOCK)
  WHERE a.state IN('AVAILABLE','DELETE_PENDING') AND a.available_at<=SYSUTCDATETIME()
    AND (a.lease_until IS NULL OR a.lease_until<SYSUTCDATETIME())
    AND a.public_id LIKE 'ticketcenter/events/'+@uuid+'/'+@uuid
    AND NOT EXISTS(SELECT 1 FROM dbo.tc_events e WHERE e.event_image_public_id=a.public_id
                   OR (a.secure_url IS NOT NULL AND e.event_image=a.secure_url))
    AND NOT EXISTS(SELECT 1 FROM dbo.tc_users u WHERE u.avatar_public_id=a.public_id
                   OR (a.secure_url IS NOT NULL AND u.avatar=a.secure_url))
    AND NOT EXISTS(SELECT 1 FROM dbo.tc_event_image_requests r WITH(UPDLOCK,HOLDLOCK)
                   WHERE r.public_id=a.public_id AND r.state='PENDING')
  ORDER BY a.available_at,a.public_id)
 UPDATE due SET state='DELETE_PENDING',lease_token=NEWID(),lease_until=DATEADD(minute,10,SYSUTCDATETIME()),attempts=attempts+1
 OUTPUT inserted.public_id,inserted.lease_token,deleted.available_at INTO @leased;
 SELECT public_id,lease_token FROM @leased ORDER BY available_at,public_id;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_EventImageCleanupFinished
 @public_id varchar(255),@lease_token uniqueidentifier,@success bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 UPDATE dbo.tc_event_image_assets
 SET state=CASE WHEN @success=1 THEN 'DELETED' ELSE 'DELETE_PENDING' END,lease_until=NULL,lease_token=NULL,
     available_at=DATEADD(minute,CASE WHEN @success=1 THEN 1440 ELSE 5 END,SYSUTCDATETIME())
 WHERE public_id=@public_id AND lease_token=@lease_token AND state='DELETE_PENDING';
END;
GO
GRANT EXECUTE ON dbo.usp_EventImageClaim TO tc_manager;
GRANT EXECUTE ON dbo.usp_EventImageSave TO tc_manager;
GRANT EXECUTE ON dbo.usp_EventImageCommit TO tc_manager;
GRANT EXECUTE ON dbo.usp_EventImageRelease TO tc_manager;
GRANT EXECUTE ON dbo.usp_EventImageClaimCleanup TO tc_worker;
GRANT EXECUTE ON dbo.usp_EventImageCleanupFinished TO tc_worker;
REVOKE INSERT,UPDATE ON dbo.tc_event_image_requests FROM tc_manager;
REVOKE INSERT,UPDATE ON dbo.tc_event_image_assets FROM tc_manager;
REVOKE UPDATE ON dbo.tc_event_image_requests FROM tc_worker;
REVOKE UPDATE ON dbo.tc_event_image_assets FROM tc_worker;
GO
