-- Optional per-event check-in opening time. NULL keeps the default (start_time - 60 minutes).
-- The closing time stays end_time. Event start/sale times are not touched.
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
IF COL_LENGTH('dbo.tc_events','checkin_open_at') IS NULL
 ALTER TABLE dbo.tc_events ADD checkin_open_at datetime2(3) NULL;
GO
CREATE OR ALTER FUNCTION dbo.fn_event_is_check_in_open(@event_id uniqueidentifier,@now datetime2(7))
RETURNS bit AS
BEGIN
 DECLARE @open bit=0;
 SELECT @open=1 FROM dbo.tc_events
 WHERE id=@event_id AND status='PUBLISHED'
   AND @now>=COALESCE(checkin_open_at,DATEADD(minute,-60,start_time)) AND @now<end_time;
 RETURN @open;
END;
GO
CREATE OR ALTER FUNCTION dbo.fn_GetEventCheckInWindow(@event_id uniqueidentifier,@now_utc datetime2(3))
RETURNS TABLE AS RETURN (
 SELECT id event_id,COALESCE(checkin_open_at,DATEADD(minute,-60,start_time)) opens_at,end_time closes_at,
 CASE WHEN status='CANCELLED' THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_cancelled,
 CASE WHEN status='PUBLISHED' AND @now_utc>=COALESCE(checkin_open_at,DATEADD(minute,-60,start_time)) AND @now_utc<end_time THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_open
 FROM dbo.tc_events WHERE id=@event_id);
GO
CREATE OR ALTER VIEW dbo.vw_CheckInEvents AS
SELECT e.id,e.organization_id,e.title,e.venue_name,e.start_time,e.end_time,e.status,e.checkin_open_at
FROM dbo.tc_events e WHERE e.archived_at IS NULL;
GO
DECLARE @sql nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('dbo.sp_ticket_check_in'));
DECLARE @a nvarchar(max)=N'DECLARE @org uniqueidentifier,@event_status varchar(30),@start datetime2(3),@end datetime2(3);';
DECLARE @b nvarchar(max)=N'@start=start_time,@end=end_time';
DECLARE @c nvarchar(max)=N'@at<DATEADD(minute,-60,@start)';
IF CHARINDEX(@a,@sql)=0 OR CHARINDEX(@b,@sql)=0 OR CHARINDEX(@c,@sql)=0 THROW 51969,'Unexpected sp_ticket_check_in contract',1;
SET @sql=REPLACE(@sql,@a,N'DECLARE @org uniqueidentifier,@event_status varchar(30),@start datetime2(3),@end datetime2(3),@checkin_open datetime2(3);');
SET @sql=REPLACE(@sql,@b,N'@start=start_time,@end=end_time,@checkin_open=checkin_open_at');
SET @sql=REPLACE(@sql,@c,N'@at<COALESCE(@checkin_open,DATEADD(minute,-60,@start))');
SET @sql=STUFF(@sql,CHARINDEX(N'CREATE',@sql),6,N'CREATE OR ALTER');
EXEC(@sql);
GO
IF DATABASE_PRINCIPAL_ID('tc_checkin') IS NOT NULL
BEGIN
 GRANT EXECUTE ON dbo.sp_ticket_check_in TO tc_checkin;
 GRANT EXECUTE ON dbo.fn_event_is_check_in_open TO tc_checkin;
END;
GO
