SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

CREATE OR ALTER PROCEDURE dbo.usp_AssertOrderAccess
    @order_id uniqueidentifier
WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @session_actor uniqueidentifier = TRY_CONVERT(uniqueidentifier, SESSION_CONTEXT(N'actor_id'));
    IF @session_actor IS NULL
        THROW 51403, 'A current verified owner is required.', 1;

    DECLARE @owner_id uniqueidentifier;
    SELECT @owner_id = user_id FROM dbo.tc_orders WHERE id = @order_id;
    IF @owner_id IS NULL
        THROW 51404, 'Order was not found.', 1;
    IF @owner_id <> @session_actor
        THROW 51403, 'A current verified owner is required.', 1;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_AssertTicketAccess
    @ticket_id uniqueidentifier
WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @session_actor uniqueidentifier = TRY_CONVERT(uniqueidentifier, SESSION_CONTEXT(N'actor_id'));
    IF @session_actor IS NULL
        THROW 51403, 'Ticket belongs to another user.', 1;

    DECLARE @owner_id uniqueidentifier;
    SELECT @owner_id = o.user_id
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id
    WHERE t.id = @ticket_id;

    IF @owner_id IS NULL
        THROW 51404, 'Ticket was not found.', 1;
    IF @owner_id <> @session_actor
        THROW 51403, 'Ticket belongs to another user.', 1;
END;
GO

GRANT EXECUTE ON dbo.usp_AssertOrderAccess TO tc_buyer;
GRANT EXECUTE ON dbo.usp_AssertTicketAccess TO tc_buyer;
GO
