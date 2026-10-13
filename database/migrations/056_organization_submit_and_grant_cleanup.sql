/* 056: organization requests are submitted through a procedure, and write grants nothing uses are removed.
   - usp_OrganizationSubmit locks the requester row, so two concurrent submissions cannot both pass the
     "one pending request per user" check (the JPA version checked and inserted without a lock).
   - tc_buyer INSERT and tc_platform_admin UPDATE on tc_organization_requests, and tc_platform_admin UPDATE on
     tc_events, had no caller: those tables are written only by procedures (usp_ApproveOrganizationRequest,
     usp_EventCommand, usp_PublishEvent, ...), which do not need the caller to hold table permissions.
   THROW numbers: 52620 USER_NOT_FOUND, 52621 USER_SUSPENDED, 52622 ORGANIZATION_REQUEST_EXISTS, 52623 invalid input. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER PROCEDURE dbo.usp_OrganizationSubmit
    @requester_id uniqueidentifier,
    @name nvarchar(200),
    @contact_email nvarchar(320),
    @contact_phone nvarchar(30)=NULL,
    @description nvarchar(2000)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION usp_OrganizationSubmit_sp;

    BEGIN TRY
        DECLARE @user_status varchar(20), @organization_id uniqueidentifier = NEWID(), @now datetime2(7) = SYSUTCDATETIME();

        SELECT @user_status = status FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK) WHERE id = @requester_id;
        IF @user_status IS NULL
            THROW 52620, 'User not found', 1;
        IF @user_status <> 'ACTIVE'
            THROW 52621, 'User is not active', 1;
        IF NULLIF(LTRIM(RTRIM(@name)), N'') IS NULL OR NULLIF(LTRIM(RTRIM(@contact_email)), N'') IS NULL
            THROW 52623, 'Organization name and contact email are required', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_organizations WHERE requester_id = @requester_id AND status = 'PENDING_APPROVAL')
            THROW 52622, 'A pending organization request already exists', 1;

        INSERT INTO dbo.tc_organizations (id, name, contact_email, contact_phone, description, requester_id, status, submitted_at, created_at, updated_at)
        VALUES (@organization_id, @name, @contact_email, @contact_phone, @description, @requester_id, 'PENDING_APPROVAL', @now, @now, @now);

        INSERT INTO dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id)
        VALUES (@requester_id, 'ORGANIZATION_SUBMITTED', 'ORGANIZATION', @organization_id);

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT @organization_id AS organization_id, CAST('PENDING_APPROVAL' AS varchar(30)) AS status, @now AS submitted_at;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION usp_OrganizationSubmit_sp;
        THROW;
    END CATCH;
END;
GO
GRANT EXECUTE ON dbo.usp_OrganizationSubmit TO tc_buyer;
REVOKE INSERT ON dbo.tc_organization_requests FROM tc_buyer;
REVOKE UPDATE ON dbo.tc_organization_requests FROM tc_platform_admin;
REVOKE UPDATE ON dbo.tc_events FROM tc_platform_admin;
GO
