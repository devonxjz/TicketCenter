/* 059: the profile write lives in the database. usp_UpdateMyProfile is the only way the application
   changes full_name / phone. THROW numbers: 52800 PROFILE_INVALID, 52801 PROFILE_USER_NOT_FOUND. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER PROCEDURE dbo.usp_UpdateMyProfile
 @actor_id uniqueidentifier,@full_name nvarchar(200),@phone nvarchar(30)=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @name nvarchar(200)=NULLIF(LTRIM(RTRIM(@full_name)),N''),@number nvarchar(30)=NULLIF(LTRIM(RTRIM(@phone)),N'');
 IF @name IS NULL OR LEN(@name)<2 THROW 52800,'Full name is required',1;
 IF @number IS NOT NULL AND (LEN(@number)<8 OR @number LIKE N'%[^0-9+ ]%') THROW 52800,'Phone number is invalid',1;
 UPDATE dbo.tc_users SET full_name=@name,phone=@number,version=version+1,updated_at=SYSUTCDATETIME()
 WHERE id=@actor_id AND status='ACTIVE';
 IF @@ROWCOUNT=0 THROW 52801,'Active user not found',1;
 SELECT id,email,user_name,full_name,phone FROM dbo.tc_users WHERE id=@actor_id;
END;
GO
GRANT EXECUTE ON dbo.usp_UpdateMyProfile TO tc_auth;
GO
