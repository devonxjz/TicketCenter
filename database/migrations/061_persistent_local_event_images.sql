/* Only immutable, same-origin image paths are accepted in addition to existing provider URLs. */
CREATE OR ALTER FUNCTION dbo.fn_IsLocalEventImageUrl(@url varchar(4000))
RETURNS bit
AS
BEGIN
 DECLARE @route varchar(30)='/assets/event-images/',@position int=CHARINDEX('/assets/event-images/',@url COLLATE Latin1_General_BIN2);
 IF @url IS NULL OR @position=0 OR DATALENGTH(@url)<>LEN(@url) RETURN 0;
 DECLARE @context varchar(4000)=LEFT(@url,@position-1),@filename varchar(4000)=SUBSTRING(@url,@position+LEN(@route),4000);
 IF @context<>'' AND (LEFT(@context,1)<>'/' OR LEN(@context)<2 OR SUBSTRING(@context,2,4000) COLLATE Latin1_General_BIN2 LIKE '%[^A-Za-z0-9_-]%') RETURN 0;
 IF LEN(@filename)<>68 OR RIGHT(@filename,4) COLLATE Latin1_General_BIN2<>'.img'
    OR LEFT(@filename,64) COLLATE Latin1_General_BIN2 LIKE '%[^0-9a-f]%' RETURN 0;
 RETURN 1;
END;
GO
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @definition nvarchar(max),@before nvarchar(500),@after nvarchar(500);
 SET @definition=OBJECT_DEFINITION(OBJECT_ID('dbo.usp_EventImageSave'));
 SET @before=N'OR LEFT(@asset_secure_url,27)<>''https://res.cloudinary.com/'' COLLATE Latin1_General_BIN2';
 SET @after=N'OR (LEFT(@asset_secure_url,27)<>''https://res.cloudinary.com/'' COLLATE Latin1_General_BIN2 AND dbo.fn_IsLocalEventImageUrl(@asset_secure_url)=0)';
 IF @definition IS NULL THROW 52716,'Event image procedure is missing',1;
 IF CHARINDEX(@after,@definition)=0 BEGIN
  IF CHARINDEX(@before,@definition)=0 THROW 52716,'Unexpected event image validation; review migration',1;
  SET @definition=REPLACE(@definition,@before,@after);
  SET @definition=N'CREATE OR ALTER '+SUBSTRING(@definition,CHARINDEX(N'PROCEDURE',UPPER(@definition)),LEN(@definition));
  EXEC sys.sp_executesql @definition;
 END;
 SET @definition=OBJECT_DEFINITION(OBJECT_ID('dbo.usp_EventCommand'));
 SET @before=N'@cover IS NULL OR @cover NOT LIKE ''https://%'' OR JSON_VALUE';
 SET @after=N'@cover IS NULL OR (@cover NOT LIKE ''https://%'' AND dbo.fn_IsLocalEventImageUrl(@cover)=0) OR JSON_VALUE';
 IF @definition IS NULL THROW 52716,'Event command procedure is missing',1;
 IF CHARINDEX(@after,@definition)=0 BEGIN
  IF CHARINDEX(@before,@definition)=0 THROW 52716,'Unexpected event cover validation; review migration',1;
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
