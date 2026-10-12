/*
 * Migration: 029_admin_organization_approval.sql
 * Issue #4: [THAI-04] Admin duyệt và từ chối tổ chức
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* Stored Procedure: dbo.sp_organization_approve */
CREATE OR ALTER PROCEDURE dbo.sp_organization_approve
    @organization_id uniqueidentifier,
    @admin_user_id uniqueidentifier,
    @rate_percent decimal(7,4),
    @fixed_fee decimal(19,0),
    @effective_from datetime2(3),
    @effective_to datetime2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    -- Kiểm tra quyền ADMIN trong dbo.tc_user_platform_roles
    IF NOT EXISTS (
        SELECT 1
        FROM dbo.tc_user_platform_roles
        WHERE user_id = @admin_user_id AND role = 'ADMIN'
    )
    BEGIN
        THROW 52610, 'Administrator role required', 1;
    END;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION sp_organization_approve_sp;

    BEGIN TRY
        -- Khóa tổ chức bằng WITH (UPDLOCK, ROWLOCK) WHERE id = @organization_id
        DECLARE @status varchar(30);
        DECLARE @requester_id uniqueidentifier;

        SELECT @status = status,
               @requester_id = requester_id
        FROM dbo.tc_organizations WITH (UPDLOCK, ROWLOCK)
        WHERE id = @organization_id;

        -- Nếu không tìm thấy THROW 52611, 'Organization not found', 1;
        IF @status IS NULL
            THROW 52611, 'Organization not found', 1;

        -- Nếu status <> 'PENDING_APPROVAL' THROW 52612, 'ORGANIZATION_ALREADY_PROCESSED', 1;
        IF @status <> 'PENDING_APPROVAL'
            THROW 52612, 'ORGANIZATION_ALREADY_PROCESSED', 1;

        -- Validate: @rate_percent BETWEEN 0 AND 100, @fixed_fee >= 0, @effective_from < @effective_to
        IF @rate_percent IS NULL OR @rate_percent < 0 OR @rate_percent > 100
           OR @fixed_fee IS NULL OR @fixed_fee < 0
           OR @effective_from IS NULL OR @effective_to IS NULL OR @effective_from >= @effective_to
            THROW 52613, 'Invalid initial commission policy', 1;

        -- Chèn dbo.tc_commission_rules
        INSERT INTO dbo.tc_commission_rules (organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES (@organization_id, @rate_percent, @fixed_fee, @effective_from, @effective_to);

        -- Nếu requester chưa có membership trong tổ chức, chèn dbo.tc_organization_memberships
        IF @requester_id IS NOT NULL AND NOT EXISTS (
            SELECT 1
            FROM dbo.tc_organization_memberships
            WHERE user_id = @requester_id AND organization_id = @organization_id
        )
        BEGIN
            INSERT INTO dbo.tc_organization_memberships (user_id, organization_id, role)
            VALUES (@requester_id, @organization_id, 'MANAGER');
        END;

        -- Cập nhật dbo.tc_organizations: status = 'APPROVED', decided_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME(), rejection_reason = NULL
        UPDATE dbo.tc_organizations
        SET status = 'APPROVED',
            decided_at = SYSUTCDATETIME(),
            updated_at = SYSUTCDATETIME(),
            rejection_reason = NULL
        WHERE id = @organization_id;

        -- Ghi dbo.tc_audit_logs
        INSERT INTO dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id)
        VALUES (@admin_user_id, 'ORGANIZATION_APPROVED', 'ORGANIZATION', @organization_id);

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT @organization_id AS organization_id,
               CAST('APPROVED' AS varchar(30)) AS status,
               @requester_id AS assigned_manager_id;
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

/* Stored Procedure: dbo.sp_organization_reject */
CREATE OR ALTER PROCEDURE dbo.sp_organization_reject
    @organization_id uniqueidentifier,
    @admin_user_id uniqueidentifier,
    @rejected_reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    -- Kiểm tra quyền ADMIN trong dbo.tc_user_platform_roles
    IF NOT EXISTS (
        SELECT 1
        FROM dbo.tc_user_platform_roles
        WHERE user_id = @admin_user_id AND role = 'ADMIN'
    )
    BEGIN
        THROW 52610, 'Administrator role required', 1;
    END;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION sp_organization_reject_sp;

    BEGIN TRY
        -- Khóa tổ chức WITH (UPDLOCK, ROWLOCK)
        DECLARE @status varchar(30);

        SELECT @status = status
        FROM dbo.tc_organizations WITH (UPDLOCK, ROWLOCK)
        WHERE id = @organization_id;

        -- Nếu không tìm thấy THROW 52611
        IF @status IS NULL
            THROW 52611, 'Organization not found', 1;

        -- Nếu status <> 'PENDING_APPROVAL' THROW 52612, 'ORGANIZATION_ALREADY_PROCESSED', 1;
        IF @status <> 'PENDING_APPROVAL'
            THROW 52612, 'ORGANIZATION_ALREADY_PROCESSED', 1;

        -- Validate @rejected_reason: không rỗng, độ dài từ 10 đến 500 ký tự. Nếu sai THROW 52614
        IF @rejected_reason IS NULL OR LEN(LTRIM(RTRIM(@rejected_reason))) < 10 OR LEN(LTRIM(RTRIM(@rejected_reason))) > 500
            THROW 52614, 'Rejection reason must be between 10 and 500 characters', 1;

        -- Cập nhật dbo.tc_organizations: status = 'REJECTED', decided_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME(), rejection_reason = @rejected_reason
        UPDATE dbo.tc_organizations
        SET status = 'REJECTED',
            decided_at = SYSUTCDATETIME(),
            updated_at = SYSUTCDATETIME(),
            rejection_reason = @rejected_reason
        WHERE id = @organization_id;

        -- Ghi dbo.tc_audit_logs
        INSERT INTO dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@admin_user_id, 'ORGANIZATION_REJECTED', 'ORGANIZATION', @organization_id, @rejected_reason);

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT @organization_id AS organization_id,
               CAST('REJECTED' AS varchar(30)) AS status;
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

/* Cấp quyền thực thi cho tc_platform_admin */
IF DATABASE_PRINCIPAL_ID(N'tc_platform_admin') IS NOT NULL
BEGIN
    GRANT EXECUTE ON dbo.sp_organization_approve TO tc_platform_admin;
    GRANT EXECUTE ON dbo.sp_organization_reject TO tc_platform_admin;
END;
GO
