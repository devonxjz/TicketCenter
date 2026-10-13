/* 054: organization approval/rejection is decided by the database procedures, not by JPA writes.
   - Administrators are recognised from tc_users.platform_role (the column the application maintains), as in
     usp_VuongRequireAdmin/usp_EventAuthorize; tc_user_platform_roles is still honoured but nothing populates it.
   - sp_organization_approve keeps its six leading parameters. The commission policy may be omitted as a whole
     (no rule is created); a partial or invalid policy still raises 52613.
   - New optional @event_* parameters create the organization's initial DRAFT event in the same transaction. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER PROCEDURE dbo.sp_organization_approve
    @organization_id uniqueidentifier,
    @admin_user_id uniqueidentifier,
    @rate_percent decimal(7,4)=NULL,
    @fixed_fee decimal(19,0)=NULL,
    @effective_from datetime2(3)=NULL,
    @effective_to datetime2(3)=NULL,
    @event_title nvarchar(250)=NULL,
    @event_venue nvarchar(250)=NULL,
    @event_start datetime2(3)=NULL,
    @event_end datetime2(3)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @admin_user_id AND platform_role = 'ADMIN')
       AND NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @admin_user_id AND role = 'ADMIN')
        THROW 52610, 'Administrator role required', 1;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION sp_organization_approve_sp;

    BEGIN TRY
        DECLARE @status varchar(30), @requester_id uniqueidentifier, @description nvarchar(2000);
        DECLARE @rule_id uniqueidentifier, @event_id uniqueidentifier, @category_id uniqueidentifier;

        SELECT @status = status, @requester_id = requester_id, @description = description
        FROM dbo.tc_organizations WITH (UPDLOCK, ROWLOCK)
        WHERE id = @organization_id;

        IF @status IS NULL
            THROW 52611, 'Organization not found', 1;
        IF @status <> 'PENDING_APPROVAL'
            THROW 52612, 'ORGANIZATION_ALREADY_PROCESSED', 1;

        IF NOT (@rate_percent IS NULL AND @fixed_fee IS NULL AND @effective_from IS NULL AND @effective_to IS NULL)
        BEGIN
            IF @rate_percent IS NULL OR @rate_percent < 0 OR @rate_percent > 100
               OR @fixed_fee IS NULL OR @fixed_fee < 0
               OR @effective_from IS NULL OR @effective_to IS NULL OR @effective_from >= @effective_to
                THROW 52613, 'Invalid initial commission policy', 1;
            SET @rule_id = NEWID();
            INSERT INTO dbo.tc_commission_rules (id, organization_id, rate_percent, fixed_fee, effective_from, effective_to)
            VALUES (@rule_id, @organization_id, @rate_percent, @fixed_fee, @effective_from, @effective_to);
        END;

        IF @requester_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships
            WHERE user_id = @requester_id AND organization_id = @organization_id)
            INSERT INTO dbo.tc_organization_memberships (user_id, organization_id, role)
            VALUES (@requester_id, @organization_id, 'MANAGER');

        IF NULLIF(LTRIM(RTRIM(@event_title)), N'') IS NOT NULL
        BEGIN
            IF @event_start IS NULL OR @event_end IS NULL OR @event_start >= @event_end
               OR NULLIF(LTRIM(RTRIM(@event_venue)), N'') IS NULL
                THROW 52615, 'Invalid initial event', 1;
            SELECT TOP (1) @category_id = id FROM dbo.tc_event_categories WITH (UPDLOCK, HOLDLOCK)
            ORDER BY CASE WHEN slug = 'concert' THEN 0 ELSE 1 END, name, id;
            IF @category_id IS NULL
            BEGIN
                SET @category_id = NEWID();
                INSERT INTO dbo.tc_event_categories (id, name, slug) VALUES (@category_id, N'Hòa nhạc', 'concert');
            END;
            SET @event_id = NEWID();
            INSERT INTO dbo.tc_events (id, organization_id, category_id, commission_rule_id, title, description,
                                       venue_name, venue_address, sale_start, sale_end, start_time, end_time)
            VALUES (@event_id, @organization_id, @category_id, @rule_id, @event_title,
                    COALESCE(@description, N'Sự kiện được phê duyệt'), @event_venue, @event_venue,
                    DATEADD(day, -7, @event_start), @event_start, @event_start, @event_end);
        END;

        UPDATE dbo.tc_organizations
        SET status = 'APPROVED', decided_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME(), rejection_reason = NULL
        WHERE id = @organization_id;

        INSERT INTO dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id)
        VALUES (@admin_user_id, 'ORGANIZATION_APPROVED', 'ORGANIZATION', @organization_id);

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT @organization_id AS organization_id,
               CAST('APPROVED' AS varchar(30)) AS status,
               @requester_id AS assigned_manager_id,
               @rule_id AS commission_rule_id,
               @event_id AS event_id;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION sp_organization_approve_sp;
        THROW;
    END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.sp_organization_reject
    @organization_id uniqueidentifier,
    @admin_user_id uniqueidentifier,
    @rejected_reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @admin_user_id AND platform_role = 'ADMIN')
       AND NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @admin_user_id AND role = 'ADMIN')
        THROW 52610, 'Administrator role required', 1;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION sp_organization_reject_sp;

    BEGIN TRY
        DECLARE @status varchar(30);

        SELECT @status = status
        FROM dbo.tc_organizations WITH (UPDLOCK, ROWLOCK)
        WHERE id = @organization_id;

        IF @status IS NULL
            THROW 52611, 'Organization not found', 1;
        IF @status <> 'PENDING_APPROVAL'
            THROW 52612, 'ORGANIZATION_ALREADY_PROCESSED', 1;
        IF @rejected_reason IS NULL OR LEN(LTRIM(RTRIM(@rejected_reason))) < 10 OR LEN(LTRIM(RTRIM(@rejected_reason))) > 500
            THROW 52614, 'Rejection reason must be between 10 and 500 characters', 1;

        UPDATE dbo.tc_organizations
        SET status = 'REJECTED', decided_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME(), rejection_reason = @rejected_reason
        WHERE id = @organization_id;

        INSERT INTO dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@admin_user_id, 'ORGANIZATION_REJECTED', 'ORGANIZATION', @organization_id, @rejected_reason);

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT @organization_id AS organization_id, CAST('REJECTED' AS varchar(30)) AS status;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION sp_organization_reject_sp;
        THROW;
    END CATCH;
END;
GO
GRANT EXECUTE ON dbo.sp_organization_approve TO tc_platform_admin;
GRANT EXECUTE ON dbo.sp_organization_reject TO tc_platform_admin;
GO
