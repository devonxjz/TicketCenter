/* Apply after 027_team_schema_adapter.sql and 027_vuong_finance.sql.
   Read-only compatibility projection; no owner table, history or status changes. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
IF OBJECT_ID('dbo.vw_VuongOrders','V') IS NULL OR OBJECT_ID('dbo.vw_EventSalesReport','V') IS NULL
 THROW 52800,'Finance report prerequisites must be installed first',1;
IF OBJECT_ID('dbo.tc_ticket_check_in_logs','U') IS NOT NULL
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongEventCheckinCounts AS
 SELECT o.event_id,COUNT_BIG(DISTINCT t.id) tickets_checked_in_count
 FROM dbo.tc_ticket_check_in_logs l JOIN dbo.tc_tickets t ON t.id=l.ticket_id
 JOIN dbo.tc_order_items i ON i.id=t.order_item_id JOIN dbo.vw_VuongOrders o ON o.id=i.order_id
 WHERE l.result=''SUCCESS'' AND l.event_id=o.event_id GROUP BY o.event_id;');
ELSE IF COL_LENGTH('dbo.tc_tickets','used_at') IS NOT NULL
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongEventCheckinCounts AS
 SELECT o.event_id,COUNT_BIG(*) tickets_checked_in_count
 FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id
 JOIN dbo.vw_VuongOrders o ON o.id=i.order_id
 WHERE t.used_at IS NOT NULL GROUP BY o.event_id;');
ELSE THROW 52800,'Supported successful check-in evidence is required',1;
GO
GRANT SELECT ON dbo.vw_VuongEventCheckinCounts TO tc_platform_admin,tc_manager;
DENY SELECT ON dbo.vw_VuongEventCheckinCounts TO tc_checkin,tc_buyer;
GO
