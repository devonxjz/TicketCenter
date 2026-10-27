/*
  071 · Event submission requires a cover image

  The application no longer generates a placeholder cover when an event is created without one, so the
  cover becomes a submission prerequisite (SPEC UI-11: "gửi duyệt → kiểm tra ảnh bìa"). Until now only the
  editor JavaScript checked it; usp_PublishEvent (51525) was the first database check, which let an event
  without a cover reach the approval queue through a direct API call.

  Patches the SUBMIT branch of dbo.usp_EventCommand in place, as 061 does, so the rest of the 051
  definition (and 061's local image URL rule) stays byte-for-byte unchanged. Re-running is a no-op.
  CREATE OR ALTER keeps the existing EXECUTE grants.
  THROW 52000 is the existing "event content incomplete" code that Java maps to 400 VALIDATION_FAILED.
*/
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @definition nvarchar(max),@before nvarchar(500),@after nvarchar(1000);
 SET @definition=OBJECT_DEFINITION(OBJECT_ID('dbo.usp_EventCommand'));
 SET @before=N'IF NOT EXISTS(SELECT 1 FROM dbo.vw_ZoneInventory WHERE event_id=@event_id AND capacity>0) THROW 52000,''Event requires inventory'',1;';
 SET @after=N'IF EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND NULLIF(LTRIM(RTRIM(event_image)),'''') IS NULL) THROW 52000,''Event requires a cover image'',1;
    '+@before;
 IF @definition IS NULL THROW 52716,'Event command procedure is missing',1;
 IF CHARINDEX(N'Event requires a cover image',@definition)=0 BEGIN
  IF CHARINDEX(@before,@definition)=0 THROW 52716,'Unexpected event submit validation; review migration',1;
  SET @definition=REPLACE(@definition,@before,@after);
  SET @definition=N'CREATE OR ALTER '+SUBSTRING(@definition,CHARINDEX(N'PROCEDURE',UPPER(@definition)),LEN(@definition));
  EXEC sys.sp_executesql @definition;
 END;
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
GO
