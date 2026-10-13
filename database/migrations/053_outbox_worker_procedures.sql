/* 053: the outbox lease/complete state machine lives in the database.
   tc_worker executes usp_OutboxLease / usp_OutboxComplete and no longer updates tc_outbox directly. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER PROCEDURE dbo.usp_OutboxLease
 @owner varchar(120),@max_attempts int,@batch int=50
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF NULLIF(LTRIM(RTRIM(@owner)),'') IS NULL OR @max_attempts<1 OR @batch NOT BETWEEN 1 AND 500 THROW 52080,'Invalid outbox lease request',1;
 DECLARE @leased TABLE(id uniqueidentifier PRIMARY KEY,event_type varchar(120),payload nvarchar(max),created_at datetime2(7));
 WITH due AS (
  SELECT TOP(@batch) id,event_type,payload,created_at,status,lease_owner,lease_until,attempts
  FROM dbo.tc_outbox WITH(UPDLOCK,READPAST,ROWLOCK)
  WHERE (status IN('PENDING','FAILED') OR (status='PROCESSING' AND lease_until<=SYSUTCDATETIME()))
    AND attempts<@max_attempts AND event_type<>'EVENT_CANCELLED' AND available_at<=SYSUTCDATETIME()
  ORDER BY created_at)
 UPDATE due SET status='PROCESSING',lease_owner=@owner,lease_until=DATEADD(minute,2,SYSUTCDATETIME()),attempts=attempts+1
 OUTPUT inserted.id,inserted.event_type,inserted.payload,inserted.created_at INTO @leased;
 SELECT id,event_type,payload FROM @leased ORDER BY created_at,id;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_OutboxComplete
 @id uniqueidentifier,@owner varchar(120),@sent bit
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 /* Only the current lease holder may finish a row; a lost lease is a silent no-op, as before. */
 UPDATE dbo.tc_outbox SET
  status=CASE WHEN @sent=1 THEN 'PUBLISHED' ELSE 'FAILED' END,
  published_at=CASE WHEN @sent=1 THEN SYSUTCDATETIME() ELSE published_at END,
  available_at=CASE WHEN @sent=1 THEN available_at ELSE DATEADD(minute,5,SYSUTCDATETIME()) END,
  lease_owner=NULL,lease_until=NULL
 WHERE id=@id AND lease_owner=@owner AND status='PROCESSING';
 SELECT @@ROWCOUNT completed;
END;
GO
GRANT EXECUTE ON dbo.usp_OutboxLease TO tc_worker;
GRANT EXECUTE ON dbo.usp_OutboxComplete TO tc_worker;
REVOKE UPDATE ON dbo.tc_outbox FROM tc_worker;
GO
