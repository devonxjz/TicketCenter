/* Human-readable audit targets. Read-only projection; no business records are modified. */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER VIEW dbo.vw_AuditDisplayNames AS
SELECT a.id,a.actor_id,a.action,a.entity_type,a.entity_id,a.metadata_json,a.created_at,
 COALESCE(NULLIF(u.full_name,N''),NULLIF(u.user_name,N''),N'Hệ thống') actor_name,
 COALESCE(e.title,o.name,
  CASE WHEN se.id IS NOT NULL THEN CONCAT(N'Quyết toán · ',se.title) END,
  CASE WHEN re.id IS NOT NULL THEN CONCAT(N'Hoàn vé · ',re.title) END,
  CASE WHEN co.id IS NOT NULL THEN CONCAT(N'Hoa hồng · ',co.name) END,
  CASE WHEN pe.id IS NOT NULL THEN CONCAT(N'Chi trả · ',pe.title) END,
  CASE WHEN oe.id IS NOT NULL THEN CONCAT(N'Đơn vé · ',oe.title) END) entity_name
FROM dbo.vw_AuditLogs a
LEFT JOIN dbo.tc_users u ON u.id=a.actor_id
LEFT JOIN dbo.tc_events e ON e.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Event','tc_events','dbo.tc_events')
LEFT JOIN dbo.tc_organizations o ON o.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Organization','tc_organizations','dbo.tc_organizations')
LEFT JOIN dbo.tc_settlements s ON s.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Settlement','tc_settlements','dbo.tc_settlements')
LEFT JOIN dbo.tc_events se ON se.id=s.event_id
LEFT JOIN dbo.tc_refund_obligations r ON r.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Refund','RefundRequest','tc_refund_obligations','dbo.tc_refund_obligations','tc_refund_requests','dbo.tc_refund_requests')
LEFT JOIN dbo.tc_orders ro ON ro.id=r.order_id
LEFT JOIN dbo.tc_events re ON re.id=ro.event_id
LEFT JOIN dbo.tc_commission_rules c ON c.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('CommissionRule','tc_commission_rules','dbo.tc_commission_rules')
LEFT JOIN dbo.tc_organizations co ON co.id=c.organization_id
LEFT JOIN dbo.tc_settlement_transfer_logs p ON p.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('SettlementTransferLog','Payout','tc_settlement_transfer_logs','dbo.tc_settlement_transfer_logs')
LEFT JOIN dbo.tc_settlements ps ON ps.id=p.settlement_id
LEFT JOIN dbo.tc_events pe ON pe.id=ps.event_id
LEFT JOIN dbo.tc_orders ord ON ord.id=TRY_CONVERT(uniqueidentifier,a.entity_id)
 AND a.entity_type IN('Order','tc_orders','dbo.tc_orders')
LEFT JOIN dbo.tc_events oe ON oe.id=ord.event_id;
GO
GRANT SELECT ON dbo.vw_AuditDisplayNames TO tc_platform_admin;
GO
