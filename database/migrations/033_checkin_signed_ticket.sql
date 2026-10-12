/* Resolve a signed QR's ticket id inside the check-in role's trusted SQL boundary. */
CREATE OR ALTER PROCEDURE dbo.usp_CheckInSignedTicket
    @event_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @ticket_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;

    IF @event_id IS NULL OR @actor_id IS NULL OR @ticket_id IS NULL
        THROW 51242, 'Event, actor and ticket are required', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM dbo.tc_events e
        JOIN dbo.tc_organization_memberships m ON m.organization_id = e.organization_id
        JOIN dbo.tc_users u ON u.id = m.user_id
        WHERE e.id = @event_id AND m.user_id = @actor_id AND m.active = 1
          AND m.role IN ('MANAGER', 'CHECK_IN_STAFF') AND u.status = 'ACTIVE'
    ) THROW 51241, 'Active event staff membership required', 1;

    DECLARE @ticket_code varchar(100);
    SELECT @ticket_code = ticket_code FROM dbo.tc_tickets WHERE id = @ticket_id;

    -- Unknown signed IDs still go through the canonical procedure and its audit trail.
    IF @ticket_code IS NULL
        SET @ticket_code = CONCAT('UNKNOWN-', CONVERT(varchar(36), @ticket_id));

    EXEC dbo.usp_CheckInTicket @event_id, @actor_id, @ticket_code;
END;
GO

GRANT EXECUTE ON dbo.usp_CheckInSignedTicket TO tc_checkin, tc_manager;
GO
