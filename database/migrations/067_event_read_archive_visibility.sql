-- Apply publication archive visibility consistently to the canonical list/count procedure.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO
DECLARE @definition nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('dbo.usp_EventRead'));
IF @definition IS NULL THROW 54410,'Event read procedure required',1;
IF CHARINDEX('WHERE archived_at IS NULL AND (@admin=1 OR organization_id=@organization_id)',@definition)=0
BEGIN
 IF CHARINDEX('WHERE (@admin=1 OR organization_id=@organization_id)',@definition)=0
  OR CHARINDEX('WHERE (@admin=1 OR e.organization_id=@organization_id)',@definition)=0
  THROW 54411,'Event read definition changed; review required',1;
 SET @definition=REPLACE(@definition,'WHERE (@admin=1 OR organization_id=@organization_id)',
  'WHERE archived_at IS NULL AND (@admin=1 OR organization_id=@organization_id)');
 SET @definition=REPLACE(@definition,'WHERE (@admin=1 OR e.organization_id=@organization_id)',
  'WHERE e.archived_at IS NULL AND (@admin=1 OR e.organization_id=@organization_id)');
 SET @definition=N'CREATE OR ALTER '+SUBSTRING(@definition,CHARINDEX(N'PROCEDURE',UPPER(@definition)),LEN(@definition));
 EXEC sys.sp_executesql @definition;
END;
GO
