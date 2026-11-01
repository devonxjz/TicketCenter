/*
  070 · Organization membership commands (usp_AddMember, usp_ChangeMemberRole)

  Replaces the 032 definitions so Java can hand every membership write to the database:
    - join a caller's transaction through a savepoint (was: always BEGIN TRANSACTION / ROLLBACK);
    - a platform ADMIN may manage any organization, as the Java service always allowed;
    - usp_AddMember reactivates a deactivated member instead of failing on the unique key;
    - usp_ChangeMemberRole keeps the current role / active flag when the argument is NULL, so
      deactivate and activate no longer need the caller to read the row first;
    - activation changes are audited (MEMBER_ACTIVATED / MEMBER_DEACTIVATED) next to MEMBER_ROLE_CHANGED;
    - both return the resulting membership row, so the caller does not read it back.

  The last-manager rule stays in trg_Membership_ProtectLastManager (THROW 51106).
  usp_ChangeMemberRole used to default @active to 1; NULL now means "keep". No Java or SQL caller relied on it.
  New THROW numbers: 52650 member already active, 52651 member not found, 52652 nothing to change.
  (51817 stays "active user required" from usp_RequireActiveMember; 032 also used it for "member not found".)
*/
CREATE OR ALTER PROCEDURE dbo.usp_RequireOrganizationManagerOrAdmin
    @actor_id uniqueidentifier,
    @organization_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @actor_id AND status = 'ACTIVE' AND platform_role = 'ADMIN')
        RETURN;
    EXEC dbo.usp_RequireOrganizationManager @actor_id, @organization_id;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_AddMember
    @organization_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @user_id uniqueidentifier,
    @role varchar(30)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION usp_AddMember_sp;

    BEGIN TRY
        DECLARE @lock_count bigint, @membership_id uniqueidentifier, @was_active bit;

        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_organizations WITH (UPDLOCK, HOLDLOCK) WHERE id = @organization_id;
        EXEC dbo.usp_RequireOrganizationManagerOrAdmin @actor_id, @organization_id;
        IF @role IS NULL OR @role NOT IN ('MANAGER', 'CHECK_IN_STAFF')
            THROW 51816, 'Invalid organization role', 1;
        EXEC dbo.usp_RequireActiveMember @user_id;

        SELECT @membership_id = id, @was_active = active
          FROM dbo.tc_organization_memberships WITH (UPDLOCK, HOLDLOCK)
         WHERE user_id = @user_id AND organization_id = @organization_id;

        IF @membership_id IS NOT NULL AND @was_active = 1
            THROW 52650, 'Member is already active in this organization', 1;

        IF @membership_id IS NULL
        BEGIN
            INSERT dbo.tc_organization_memberships (user_id, organization_id, role, active)
            VALUES (@user_id, @organization_id, @role, 1);
            SELECT @membership_id = id FROM dbo.tc_organization_memberships
             WHERE user_id = @user_id AND organization_id = @organization_id;
            INSERT dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id, detail)
            VALUES (@actor_id, 'MEMBER_ADDED', 'ORGANIZATION', @organization_id, CONVERT(varchar(36), @user_id));
        END
        ELSE
        BEGIN
            UPDATE dbo.tc_organization_memberships
               SET role = @role, active = 1, version = version + 1
             WHERE id = @membership_id;
            INSERT dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id, detail)
            VALUES (@actor_id, 'MEMBER_ACTIVATED', 'ORGANIZATION', @organization_id, CONVERT(varchar(36), @user_id));
        END;

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT id AS membership_id, user_id, role, active, joined_at
          FROM dbo.tc_organization_memberships WHERE id = @membership_id;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION usp_AddMember_sp;
        THROW;
    END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_ChangeMemberRole
    @organization_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @user_id uniqueidentifier,
    @role varchar(30) = NULL,
    @active bit = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION usp_ChangeMemberRole_sp;

    BEGIN TRY
        DECLARE @lock_count bigint, @membership_id uniqueidentifier, @old_role varchar(30), @old_active bit,
                @new_role varchar(30), @new_active bit;

        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_organizations WITH (UPDLOCK, HOLDLOCK) WHERE id = @organization_id;
        EXEC dbo.usp_RequireOrganizationManagerOrAdmin @actor_id, @organization_id;
        IF @role IS NULL AND @active IS NULL
            THROW 52652, 'Nothing to change', 1;
        IF @role IS NOT NULL AND @role NOT IN ('MANAGER', 'CHECK_IN_STAFF')
            THROW 51816, 'Invalid organization role', 1;

        SELECT @membership_id = id, @old_role = role, @old_active = active
          FROM dbo.tc_organization_memberships WITH (UPDLOCK, HOLDLOCK)
         WHERE user_id = @user_id AND organization_id = @organization_id;
        IF @membership_id IS NULL
            THROW 52651, 'Member not found', 1;

        SET @new_role = COALESCE(@role, @old_role);
        SET @new_active = COALESCE(@active, @old_active);

        IF @new_role <> @old_role OR @new_active <> @old_active
        BEGIN
            UPDATE dbo.tc_organization_memberships
               SET role = @new_role, active = @new_active, version = version + 1
             WHERE id = @membership_id;

            IF @new_role <> @old_role
                INSERT dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id, detail)
                VALUES (@actor_id, 'MEMBER_ROLE_CHANGED', 'ORGANIZATION', @organization_id, CONVERT(varchar(36), @user_id));
            IF @new_active <> @old_active
                INSERT dbo.tc_audit_logs (actor_id, action, aggregate_type, aggregate_id, detail)
                VALUES (@actor_id,
                        CASE WHEN @new_active = 1 THEN 'MEMBER_ACTIVATED' ELSE 'MEMBER_DEACTIVATED' END,
                        'ORGANIZATION', @organization_id, CONVERT(varchar(36), @user_id));
        END;

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT id AS membership_id, user_id, role, active, joined_at
          FROM dbo.tc_organization_memberships WHERE id = @membership_id;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION usp_ChangeMemberRole_sp;
        THROW;
    END CATCH;
END;
GO
GRANT EXECUTE ON dbo.usp_AddMember TO tc_manager;
GRANT EXECUTE ON dbo.usp_ChangeMemberRole TO tc_manager;
GO
