-- Guard mutation entry points before removing direct runtime writes.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
GO
CREATE OR ALTER PROCEDURE dbo.usp_RequireOrganizationManager @actor_id uniqueidentifier,@organization_id uniqueidentifier
AS BEGIN SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id=m.user_id
 WHERE m.user_id=@actor_id AND m.organization_id=@organization_id AND m.role='MANAGER' AND m.active=1 AND u.status='ACTIVE')
 THROW 51810,'Active manager of this organization required',1;
END;
GO
-- Minimal internal lookup permits inviting a user who is not a member yet;
-- it returns no personal data and has no direct runtime EXECUTE grant.
CREATE OR ALTER PROCEDURE dbo.usp_RequireActiveMember @user_id uniqueidentifier WITH EXECUTE AS OWNER
AS BEGIN SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id=@user_id AND status='ACTIVE') THROW 51817,'Active member required',1;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_SaveEventDraft
 @event_id uniqueidentifier,@organization_id uniqueidentifier,@actor_id uniqueidentifier,@create bit,@expected_version bigint,
 @category_id uniqueidentifier=NULL,@title nvarchar(200)=NULL,@description nvarchar(max)=NULL,
 @venue_name nvarchar(200)=NULL,@venue_address nvarchar(500)=NULL,@sale_start datetime2(3)=NULL,@sale_end datetime2(3)=NULL,
 @start_time datetime2(3)=NULL,@end_time datetime2(3)=NULL,@image_url varchar(2048)=NULL,@image_public_id varchar(255)=NULL,@cover_only bit=0
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END,@status varchar(30),@version bigint;
 IF @started=1 BEGIN TRANSACTION;
 BEGIN TRY
  EXEC dbo.usp_RequireOrganizationManager @actor_id,@organization_id;
  IF @create=1 BEGIN
   IF @cover_only=1 THROW 51811,'Draft fields required',1;
   INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status,version,event_image,event_image_public_id,cover_image_url)
   VALUES(@event_id,@organization_id,@category_id,@title,@description,@venue_name,@venue_address,@sale_start,@sale_end,@start_time,@end_time,'DRAFT',0,@image_url,@image_public_id,@image_url);
  END ELSE BEGIN
   SELECT @status=status,@version=version FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id AND organization_id=@organization_id;
   IF @status IS NULL OR @status NOT IN('DRAFT','REJECTED','PUBLISHED') OR (@status='PUBLISHED' AND @cover_only<>1) THROW 51812,'Only editable drafts or the cover of a published event may be changed',1;
   IF @version<>@expected_version THROW 51813,'Event version conflict',1;
   UPDATE dbo.tc_events SET
    category_id=CASE WHEN @cover_only=1 THEN category_id ELSE @category_id END,
    title=CASE WHEN @cover_only=1 THEN title ELSE @title END,description=CASE WHEN @cover_only=1 THEN description ELSE @description END,
    venue_name=CASE WHEN @cover_only=1 THEN venue_name ELSE @venue_name END,venue_address=CASE WHEN @cover_only=1 THEN venue_address ELSE @venue_address END,
    sale_start=CASE WHEN @cover_only=1 THEN sale_start ELSE @sale_start END,sale_end=CASE WHEN @cover_only=1 THEN sale_end ELSE @sale_end END,
    start_time=CASE WHEN @cover_only=1 THEN start_time ELSE @start_time END,end_time=CASE WHEN @cover_only=1 THEN end_time ELSE @end_time END,
    event_image=COALESCE(@image_url,event_image),cover_image_url=COALESCE(@image_url,cover_image_url),event_image_public_id=COALESCE(@image_public_id,event_image_public_id),version=version+1
   WHERE id=@event_id;
  END;
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id) VALUES(@actor_id,CASE WHEN @create=1 THEN 'EVENT_DRAFT_CREATED' ELSE 'EVENT_DRAFT_UPDATED' END,'EVENT',@event_id);
  IF @started=1 COMMIT;
 END TRY BEGIN CATCH IF @started=1 AND XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_SubmitEvent @event_id uniqueidentifier,@actor_id uniqueidentifier,@expected_version bigint
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @org uniqueidentifier;
 SELECT @org=organization_id FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
 EXEC dbo.usp_RequireOrganizationManager @actor_id,@org;
 UPDATE dbo.tc_events SET status='PENDING_APPROVAL',version=version+1 WHERE id=@event_id AND status IN('DRAFT','REJECTED') AND version=@expected_version;
 IF @@ROWCOUNT<>1 THROW 51813,'Event version or state conflict',1;
 INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id) VALUES(@actor_id,'EVENT_SUBMITTED','EVENT',@event_id);
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_RejectEvent @event_id uniqueidentifier,@actor_id uniqueidentifier,@reason nvarchar(1000)
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 IF NULLIF(LTRIM(RTRIM(@reason)),N'') IS NULL THROW 51814,'Rejection reason required',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles pr JOIN dbo.tc_users u ON u.id=pr.user_id WHERE pr.user_id=@actor_id AND pr.role='ADMIN' AND u.status='ACTIVE') THROW 51815,'Active administrator required',1;
 UPDATE dbo.tc_events SET status='REJECTED',version=version+1 WHERE id=@event_id AND status='PENDING_APPROVAL';
 IF @@ROWCOUNT<>1 THROW 51812,'Event is not awaiting approval',1;
 INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail) VALUES(@actor_id,'EVENT_REJECTED','EVENT',@event_id,@reason);
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_AddMember @organization_id uniqueidentifier,@actor_id uniqueidentifier,@user_id uniqueidentifier,@role varchar(30)
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @lock_count bigint;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_organizations WITH(UPDLOCK,HOLDLOCK) WHERE id=@organization_id;
 EXEC dbo.usp_RequireOrganizationManager @actor_id,@organization_id;
 IF @role IS NULL OR @role NOT IN('MANAGER','CHECK_IN_STAFF') THROW 51816,'Invalid organization role',1;
 EXEC dbo.usp_RequireActiveMember @user_id;
 INSERT dbo.tc_organization_memberships(user_id,organization_id,role,active) VALUES(@user_id,@organization_id,@role,1);
 INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail) VALUES(@actor_id,'MEMBER_ADDED','ORGANIZATION',@organization_id,CONVERT(varchar(36),@user_id));
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_ChangeMemberRole @organization_id uniqueidentifier,@actor_id uniqueidentifier,@user_id uniqueidentifier,@role varchar(30),@active bit=1
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 DECLARE @lock_count bigint;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_organizations WITH(UPDLOCK,HOLDLOCK) WHERE id=@organization_id;
 EXEC dbo.usp_RequireOrganizationManager @actor_id,@organization_id;
 IF @role IS NULL OR @role NOT IN('MANAGER','CHECK_IN_STAFF') OR @active IS NULL THROW 51816,'Invalid organization role',1;
 UPDATE dbo.tc_organization_memberships SET role=@role,active=@active WHERE user_id=@user_id AND organization_id=@organization_id;
 IF @@ROWCOUNT<>1 THROW 51817,'Member not found',1;
 INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail) VALUES(@actor_id,'MEMBER_ROLE_CHANGED','ORGANIZATION',@organization_id,CONVERT(varchar(36),@user_id));
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
-- AUTH may register customers, never manufacture an administrator through the denormalized role column.
CREATE OR ALTER TRIGGER dbo.trg_User_AuthRoleGuard ON dbo.tc_users AFTER INSERT,UPDATE AS
BEGIN SET NOCOUNT ON;
 IF COALESCE(IS_ROLEMEMBER('tc_auth'),0)=1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1
 AND EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE (d.id IS NULL AND i.platform_role<>'CUSTOMER') OR (d.id IS NOT NULL AND i.platform_role<>d.platform_role))
 THROW 51818,'Authentication principal cannot assign platform roles',1;
END;
GO
DENY INSERT,UPDATE,DELETE ON dbo.tc_events TO tc_manager;
DENY INSERT,UPDATE,DELETE ON dbo.tc_zones TO tc_manager;
DENY INSERT,UPDATE,DELETE ON dbo.tc_seats TO tc_manager;
DENY INSERT,UPDATE,DELETE ON dbo.tc_organization_memberships TO tc_manager;
DENY INSERT,UPDATE,DELETE ON dbo.tc_audit_logs TO tc_manager,tc_platform_admin,tc_buyer,tc_checkin;
DENY INSERT,UPDATE,DELETE ON dbo.tc_user_platform_roles TO tc_auth;
REVOKE UPDATE ON dbo.tc_users FROM tc_auth;
GRANT UPDATE(password_hash,email_verified_at,auth_version,version,updated_at) ON dbo.tc_users TO tc_auth;
DENY UPDATE(platform_role,status) ON dbo.tc_users TO tc_auth;
GRANT EXECUTE ON dbo.usp_SaveEventDraft TO tc_manager;
GRANT EXECUTE ON dbo.usp_SubmitEvent TO tc_manager;
GRANT EXECUTE ON dbo.usp_RejectEvent TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_AddMember TO tc_manager;
GRANT EXECUTE ON dbo.usp_ChangeMemberRole TO tc_manager;
REVOKE EXECUTE ON dbo.usp_ReviewRefundRequest FROM tc_worker;
REVOKE EXECUTE ON dbo.usp_DecideRefundRequest FROM tc_worker;
REVOKE EXECUTE ON dbo.usp_CalculateEventSettlement FROM tc_platform_admin;
REVOKE EXECUTE ON dbo.usp_ConfirmEventSettlement FROM tc_platform_admin;
REVOKE EXECUTE ON dbo.usp_ExecutePayout FROM tc_platform_admin;
GO
