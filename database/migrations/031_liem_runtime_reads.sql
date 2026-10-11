SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ReadPaymentOrder
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT o.order_code, o.total_amount,
           DATEDIFF_BIG(millisecond,SYSUTCDATETIME(),h.expires_at) AS hold_remaining_ms, o.status,
           h.status AS hold_status, e.status AS event_status
    FROM dbo.tc_orders o
    JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id
    JOIN dbo.tc_events e ON e.id=o.event_id
    WHERE o.id=@order_id AND o.user_id=@actor_id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ReadExistingPayment
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT TOP (1) p.id, p.txn_ref, p.amount, o.order_code,
           DATEDIFF_BIG(millisecond,SYSUTCDATETIME(),h.expires_at) AS hold_remaining_ms
    FROM dbo.tc_payments p
    JOIN dbo.tc_orders o ON o.id=p.order_id
    JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id
    WHERE p.order_id=@order_id AND o.user_id=@actor_id AND p.status IN ('PENDING','UNKNOWN')
    ORDER BY p.created_at DESC, p.id DESC;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ReadPaymentStatus
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT o.id AS order_id, o.status AS order_status, o.total_amount,
           p.id AS payment_id, p.status AS payment_status, p.amount AS payment_amount,
           p.currency, p.captured_at AS paid_at
    FROM dbo.tc_orders o
    OUTER APPLY (SELECT TOP (1) id,status,amount,currency,captured_at
                 FROM dbo.tc_payments WHERE order_id=o.id
                 ORDER BY created_at DESC,id DESC) p
    WHERE o.id=@order_id AND o.user_id=@actor_id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ReadProviderPayment @txn_ref varchar(100)
AS
BEGIN
    SET NOCOUNT ON;
    IF ISNULL(IS_ROLEMEMBER('tc_worker'),0)<>1 THROW 51501,'Worker principal required',1;
    SELECT p.id AS payment_id,p.order_id,o.user_id,p.txn_ref,p.amount,p.currency,
           p.status AS payment_status,o.status AS order_status,o.total_amount,
           p.captured_at AS paid_at
    FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id
    WHERE p.txn_ref=@txn_ref;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ReadOrderTicketQuantity
    @order_id uniqueidentifier, @actor_id uniqueidentifier=NULL, @auth_version int=NULL
AS
BEGIN
    SET NOCOUNT ON;
    IF @actor_id IS NULL
    BEGIN
        IF ISNULL(IS_ROLEMEMBER('tc_worker'),0)<>1 THROW 51501,'Worker principal required',1;
    END
    ELSE
    BEGIN
        EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_orders WHERE id=@order_id AND user_id=@actor_id)
            THROW 51502,'Order owner required',1;
    END;
    SELECT COALESCE(SUM(oi.quantity),0) AS ticket_count
    FROM dbo.tc_order_items oi WHERE oi.order_id=@order_id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_CountBuyerOrders
    @actor_id uniqueidentifier, @auth_version int, @status varchar(30)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT COUNT_BIG(*) AS total FROM dbo.tc_orders
    WHERE user_id=@actor_id AND (@status IS NULL OR status=@status);
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ListBuyerOrders
    @actor_id uniqueidentifier, @auth_version int, @page int, @size int, @status varchar(30)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    IF @page IS NULL OR @size IS NULL OR @page<1 OR @size<1 OR @size>50
       OR @page>1+2147483647/@size THROW 51503,'Invalid page',1;
    DECLARE @offset int=(@page-1)*@size;
    SELECT o.id,o.order_code,o.event_id,e.title AS event_title,e.venue_name,e.start_time,
           o.subtotal_amount,o.discount_amount,o.total_amount,o.status,o.created_at,o.paid_at,
           c.code AS coupon_code,
           (SELECT COUNT_BIG(*) FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id=t.order_item_id
            WHERE oi.order_id=o.id) AS ticket_count
    FROM dbo.tc_orders o JOIN dbo.tc_events e ON e.id=o.event_id
    LEFT JOIN dbo.tc_coupons c ON c.id=o.coupon_id
    WHERE o.user_id=@actor_id AND (@status IS NULL OR o.status=@status)
    ORDER BY o.created_at DESC,o.id DESC OFFSET @offset ROWS FETCH NEXT @size ROWS ONLY;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ReadBuyerOrderDetail
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT o.id,o.user_id AS buyer_id,o.order_code,o.event_id,e.title AS event_title,
           e.venue_name,e.venue_address,e.start_time,e.end_time,
           o.subtotal_amount,o.discount_amount,o.total_amount,o.status,o.created_at,o.paid_at,
           c.code AS coupon_code,p.status AS payment_status,p.txn_ref,p.provider_reference,p.captured_at
    FROM dbo.tc_orders o JOIN dbo.tc_events e ON e.id=o.event_id
    LEFT JOIN dbo.tc_coupons c ON c.id=o.coupon_id
    LEFT JOIN dbo.tc_payments p ON p.id=o.accepted_payment_id
    WHERE o.id=@order_id AND o.user_id=@actor_id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ListBuyerOrderItems
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT oi.id,oi.zone_name_snapshot,oi.seat_label_snapshot,oi.quantity,oi.unit_price,
           oi.quantity*oi.unit_price AS line_total
    FROM dbo.tc_order_items oi JOIN dbo.tc_orders o ON o.id=oi.order_id
    WHERE o.id=@order_id AND o.user_id=@actor_id ORDER BY oi.id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ListBuyerOrderTickets
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT t.id,t.ticket_code,t.status,t.paid_amount
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id=t.order_item_id
    JOIN dbo.tc_orders o ON o.id=oi.order_id
    WHERE o.id=@order_id AND o.user_id=@actor_id ORDER BY t.issued_at,t.id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ListBuyerTickets
    @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT t.id,t.ticket_code,t.paid_amount,t.status,t.issued_at,t.version,
           oi.order_id,oi.zone_name_snapshot,oi.seat_label_snapshot,
           e.title AS event_title,e.venue_name,e.start_time
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id=t.order_item_id
    JOIN dbo.tc_orders o ON o.id=oi.order_id JOIN dbo.tc_events e ON e.id=o.event_id
    WHERE o.user_id=@actor_id ORDER BY t.issued_at DESC,t.id DESC;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ReadBuyerTicket
    @ticket_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT t.id,t.ticket_code,t.paid_amount,t.status,t.issued_at,t.version,
           oi.order_id,oi.zone_name_snapshot,oi.seat_label_snapshot,o.order_code,o.user_id AS buyer_id,
           e.id AS event_id,e.title AS event_title,e.venue_name,e.venue_address,e.start_time,e.end_time
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id=t.order_item_id
    JOIN dbo.tc_orders o ON o.id=oi.order_id JOIN dbo.tc_events e ON e.id=o.event_id
    WHERE t.id=@ticket_id AND o.user_id=@actor_id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ListBuyerOrderTicketsDetailed
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_HoldAuthorize @actor_id, @auth_version;
    SELECT t.id,t.ticket_code,t.paid_amount,t.status,t.issued_at,t.version,
           oi.order_id,oi.zone_name_snapshot,oi.seat_label_snapshot,o.order_code,o.user_id AS buyer_id,
           e.id AS event_id,e.title AS event_title,e.venue_name,e.venue_address,e.start_time,e.end_time
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id=t.order_item_id
    JOIN dbo.tc_orders o ON o.id=oi.order_id JOIN dbo.tc_events e ON e.id=o.event_id
    WHERE o.id=@order_id AND o.user_id=@actor_id ORDER BY t.issued_at,t.id;
END;
GO

GRANT EXECUTE ON dbo.usp_ReadPaymentOrder TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReadExistingPayment TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReadPaymentStatus TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReadProviderPayment TO tc_worker;
GRANT EXECUTE ON dbo.usp_ReadOrderTicketQuantity TO tc_buyer,tc_worker;
GRANT EXECUTE ON dbo.usp_CountBuyerOrders TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ListBuyerOrders TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReadBuyerOrderDetail TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ListBuyerOrderItems TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ListBuyerOrderTickets TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ListBuyerTickets TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ReadBuyerTicket TO tc_buyer;
GRANT EXECUTE ON dbo.usp_ListBuyerOrderTicketsDetailed TO tc_buyer;
GO
