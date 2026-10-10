/*
 * 027_enforce_user_username_and_event_description
 *
 * Forward-only migration enforcing canonical schema constraints:
 * 1. User.userName: NOT NULL and UNIQUE constraint UQ_User_NormalizedUserName on dbo.tc_users(user_name).
 * 2. Event.description: NOT NULL on dbo.tc_events(description) after backfilling existing NULLs.
 *
 * The migration runner owns the transaction and tc_schema_migrations record.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'TicketCenterticke'
    THROW 52700, 'Migration 027 can only run on TicketCenterticke', 1;

IF OBJECT_ID(N'dbo.tc_schema_migrations', N'U') IS NULL
    THROW 52701, 'tc_schema_migrations is required', 1;

IF EXISTS (SELECT 1 FROM dbo.tc_schema_migrations WHERE version = '027_enforce_user_username_and_event_description')
    THROW 52702, 'Migration 027 is already recorded', 1;
GO

/* 1. Backfill any NULL user_name in tc_users (if any exist) */
UPDATE dbo.tc_users
SET user_name = LOWER(SUBSTRING(email, 1, CHARINDEX('@', email + '@') - 1))
WHERE user_name IS NULL;
GO

/* 2. Alter dbo.tc_users.user_name to NOT NULL */
ALTER TABLE dbo.tc_users ALTER COLUMN user_name nvarchar(100) NOT NULL;
GO

/* 3. Add UNIQUE constraint UQ_User_NormalizedUserName on dbo.tc_users(user_name) */
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_User_NormalizedUserName' AND parent_object_id = OBJECT_ID(N'dbo.tc_users'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_User_NormalizedUserName' AND object_id = OBJECT_ID(N'dbo.tc_users'))
BEGIN
    ALTER TABLE dbo.tc_users ADD CONSTRAINT UQ_User_NormalizedUserName UNIQUE (user_name);
END
GO

/* 4. Backfill existing NULL descriptions in dbo.tc_events */
UPDATE dbo.tc_events
SET description = COALESCE(NULLIF(LTRIM(RTRIM(description)), N''), title, N'Sự kiện')
WHERE description IS NULL OR LTRIM(RTRIM(description)) = N'';
GO

/* 5. Alter dbo.tc_events.description to NOT NULL */
ALTER TABLE dbo.tc_events ALTER COLUMN description nvarchar(max) NOT NULL;
GO
