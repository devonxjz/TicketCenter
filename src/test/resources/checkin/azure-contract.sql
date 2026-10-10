-- KHANH-01 provisioning, executed with the user's explicit database authorization.
-- Refuses an existing canonical function rather than overwriting another owner's work.
-- KHANH-01 only: no table/data rewrite, no password/login creation.
SET XACT_ABORT ON;
IF DB_NAME() <> 'TicketCenterticke'
    THROW 51000, 'Unexpected database; stop', 1;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_events')
               AND name='id' AND TYPE_NAME(user_type_id)='uniqueidentifier')
    THROW 51000, 'tc_events UUID contract missing; stop', 1;
IF OBJECT_ID('dbo.fn_event_is_check_in_open') IS NOT NULL
    THROW 51000, 'Existing function must be reviewed, not overwritten', 1;
IF DATABASE_PRINCIPAL_ID('tc_checkin') IS NULL
    THROW 51000, 'Existing CHECK_IN role missing; stop', 1;
GO
CREATE FUNCTION dbo.fn_event_is_check_in_open
(@event_id uniqueidentifier,@now datetime2(7))
RETURNS bit
AS
BEGIN
    DECLARE @open bit=0;
    SELECT @open=1 FROM dbo.tc_events
    WHERE id=@event_id AND status='PUBLISHED'
      AND @now>=DATEADD(minute,-60,start_time) AND @now<end_time;
    RETURN @open;
END;
GO
GRANT SELECT ON dbo.tc_users (id,status) TO tc_checkin;
GRANT SELECT ON dbo.tc_organization_memberships (user_id,organization_id,active,role) TO tc_checkin;
GRANT SELECT ON dbo.tc_events (id,organization_id,title,venue_name,start_time,end_time,status) TO tc_checkin;
GRANT SELECT ON dbo.tc_tickets (order_item_id,status) TO tc_checkin;
GRANT SELECT ON dbo.tc_order_items (id,order_id) TO tc_checkin;
GRANT SELECT ON dbo.tc_orders (id,event_id) TO tc_checkin;
GRANT EXECUTE ON dbo.fn_event_is_check_in_open TO tc_checkin;
