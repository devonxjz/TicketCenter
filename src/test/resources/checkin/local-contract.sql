-- Acceptance fixture only; never apply to the shared database.
IF DB_NAME() <> 'TicketCenter_Khanh01_Test'
    THROW 51000, 'This script is restricted to TicketCenter_Khanh01_Test', 1;
GO
-- Existing identity regression expects this optional legacy compatibility column.
IF COL_LENGTH('dbo.tc_users','normalized_email') IS NULL
    ALTER TABLE dbo.tc_users ADD normalized_email nvarchar(320) NULL;
-- Membership mapping inherited from dev now owns joined_at instead of BaseEntity dates.
IF COL_LENGTH('dbo.tc_organization_memberships','joined_at') IS NULL
    ALTER TABLE dbo.tc_organization_memberships ADD joined_at datetimeoffset(7) NOT NULL
        CONSTRAINT df_khanh01_membership_joined DEFAULT SYSUTCDATETIME();
IF COL_LENGTH('dbo.tc_organization_memberships','version') IS NULL
    ALTER TABLE dbo.tc_organization_memberships ADD version bigint NULL;
-- Compatibility when rerunning an older, already-created local fixture after updating dev.
IF COL_LENGTH('dbo.tc_organization_memberships','created_at') IS NOT NULL
 AND NOT EXISTS (SELECT 1 FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID('dbo.tc_organization_memberships')
                 AND parent_column_id=COLUMNPROPERTY(OBJECT_ID('dbo.tc_organization_memberships'),'created_at','ColumnId'))
    ALTER TABLE dbo.tc_organization_memberships ADD CONSTRAINT df_khanh01_membership_created DEFAULT SYSUTCDATETIME() FOR created_at;
IF COL_LENGTH('dbo.tc_organization_memberships','updated_at') IS NOT NULL
 AND NOT EXISTS (SELECT 1 FROM sys.default_constraints WHERE parent_object_id=OBJECT_ID('dbo.tc_organization_memberships')
                 AND parent_column_id=COLUMNPROPERTY(OBJECT_ID('dbo.tc_organization_memberships'),'updated_at','ColumnId'))
    ALTER TABLE dbo.tc_organization_memberships ADD CONSTRAINT df_khanh01_membership_updated DEFAULT SYSUTCDATETIME() FOR updated_at;
-- Preserve Vietnamese in the locked varchar Event mappings on SQL Server 2022.
ALTER TABLE dbo.tc_events ALTER COLUMN title varchar(300) COLLATE Latin1_General_100_CI_AS_SC_UTF8 NOT NULL;
ALTER TABLE dbo.tc_events ALTER COLUMN venue_name varchar(300) COLLATE Latin1_General_100_CI_AS_SC_UTF8 NOT NULL;
GO
CREATE OR ALTER FUNCTION dbo.fn_event_is_check_in_open
(@event_id uniqueidentifier, @now datetime2(7))
RETURNS bit
AS
BEGIN
    DECLARE @open bit = 0;
    SELECT @open = 1 FROM dbo.tc_events
    WHERE id = @event_id AND status = 'PUBLISHED'
      AND @now >= DATEADD(minute, -60, start_time) AND @now < end_time;
    RETURN @open;
END;
GO
GRANT VIEW DEFINITION TO khanh01_checkin;
GRANT SELECT ON dbo.tc_users (id,status) TO khanh01_checkin;
GRANT SELECT ON dbo.tc_organization_memberships (user_id,organization_id,active,role) TO khanh01_checkin;
GRANT SELECT ON dbo.tc_events (id,organization_id,title,venue_name,start_time,end_time,status) TO khanh01_checkin;
GRANT SELECT ON dbo.tc_tickets (order_item_id,status) TO khanh01_checkin;
GRANT SELECT ON dbo.tc_order_items (id,order_id) TO khanh01_checkin;
GRANT SELECT ON dbo.tc_orders (id,event_id) TO khanh01_checkin;
GRANT EXECUTE ON dbo.fn_event_is_check_in_open TO khanh01_checkin;
