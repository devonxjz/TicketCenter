/* 057: read models. The application no longer reads tc_* tables with inline SQL; every projection, join,
   filter rule and aggregate it needs is a view or inline function here, and Java only selects from these.
   Each object reproduces the query it replaces (same columns, same predicates). */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
/* ---- authority ---- */
CREATE OR ALTER VIEW dbo.vw_ActorAuthority AS
 SELECT id,status,auth_version,platform_role FROM dbo.tc_users;
GO
CREATE OR ALTER VIEW dbo.vw_ActiveManagerMemberships AS
 SELECT user_id,organization_id FROM dbo.tc_organization_memberships WHERE active=1 AND role='MANAGER';
GO
/* Organizations a manager may create events for: approved, active MANAGER membership. */
CREATE OR ALTER VIEW dbo.vw_ManagerOrganizations AS
 SELECT m.user_id,o.id,o.name
 FROM dbo.tc_organizations o JOIN dbo.tc_organization_memberships m ON m.organization_id=o.id
 WHERE m.active=1 AND m.role='MANAGER' AND o.status='APPROVED';
GO
CREATE OR ALTER PROCEDURE dbo.usp_EventRequireSession
 @actor_id uniqueidentifier,@auth_version int
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND auth_version=@auth_version)
  THROW 52703,'Current session is no longer valid',1;
END;
GO
/* ---- finance ---- */
CREATE OR ALTER VIEW dbo.vw_FinanceOrganizations AS
 SELECT id,name FROM dbo.tc_organizations;
GO
CREATE OR ALTER VIEW dbo.vw_FinanceEvents AS
 SELECT e.id,e.title,e.organization_id,e.created_at,e.end_time,s.id settlement_id,s.status settlement_status
 FROM dbo.tc_events e LEFT JOIN dbo.tc_settlements s ON s.event_id=e.id;
GO
CREATE OR ALTER VIEW dbo.vw_FinanceSettlements AS
 SELECT s.id,s.event_id,e.organization_id
 FROM dbo.tc_settlements s JOIN dbo.tc_events e ON e.id=s.event_id;
GO
CREATE OR ALTER VIEW dbo.vw_CommissionRuleUsage AS
 SELECT c.id rule_id,c.organization_id,c.rate_percent,c.fixed_fee,c.effective_from,c.effective_to,
        (SELECT COUNT_BIG(*) FROM dbo.tc_events e WHERE e.commission_rule_id=c.id) associated_events_count
 FROM dbo.tc_commission_rules c;
GO
CREATE OR ALTER VIEW dbo.vw_SettlementSummary AS
 SELECT b.settlement_id,b.event_id,b.organization_id,b.status,b.gross_revenue,b.total_refund,b.total_commission,
        b.net_payable,b.paid_amount,b.pending_amount,b.available_to_pay,b.confirmed_at,e.end_time
 FROM dbo.vw_SettlementPayoutBalance b JOIN dbo.tc_events e ON e.id=b.event_id;
GO
CREATE OR ALTER VIEW dbo.vw_SettlementPayouts AS
 SELECT l.id payout_id,l.settlement_id,l.amount,l.status,l.verification_state,l.provider_reference,l.processed_at,
        l.created_at,l.request_fingerprint,s.status settlement_status,s.net_payable,s.paid_amount,s.pending_amount,
        CAST(s.net_payable-s.paid_amount-s.pending_amount AS decimal(19,0)) available_to_pay
 FROM dbo.tc_settlement_transfer_logs l JOIN dbo.tc_settlements s ON s.id=l.settlement_id;
GO
/* ---- dashboard ---- */
CREATE OR ALTER VIEW dbo.vw_DashboardPlatformCounts AS
 SELECT (SELECT COUNT_BIG(*) FROM dbo.tc_users) total_users,
        (SELECT COUNT_BIG(*) FROM dbo.tc_organizations WHERE status='APPROVED') active_organizations,
        (SELECT COUNT_BIG(*) FROM dbo.tc_organizations WHERE status='PENDING_APPROVAL') pending_organizations,
        (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE status='PENDING_APPROVAL') pending_events,
        (SELECT COUNT_BIG(*) FROM dbo.vw_VuongRefunds WHERE status='REQUESTED') pending_refunds,
        (SELECT COUNT_BIG(*) FROM dbo.tc_settlements WHERE status='DRAFT') draft_settlements;
GO
CREATE OR ALTER FUNCTION dbo.fn_DashboardOrganizationCounts(@organization_id uniqueidentifier)
RETURNS TABLE AS RETURN
 SELECT (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE organization_id=@organization_id) event_count,
        (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE organization_id=@organization_id AND status='PENDING_APPROVAL') pending_events,
        (SELECT COUNT_BIG(*) FROM dbo.vw_VuongOrders o JOIN dbo.tc_events e ON e.id=o.event_id
         WHERE e.organization_id=@organization_id AND o.status='PAID') paid_orders;
GO
/* ---- check-in (no financial columns) ---- */
CREATE OR ALTER VIEW dbo.vw_CheckInStaffOrganizations AS
 SELECT m.user_id,m.organization_id
 FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id=m.user_id
 WHERE m.active=1 AND u.status='ACTIVE' AND m.role IN('MANAGER','CHECK_IN_STAFF');
GO
CREATE OR ALTER VIEW dbo.vw_CheckInEvents AS
 SELECT e.id,e.organization_id,e.title,e.venue_name,e.start_time,e.end_time,e.status FROM dbo.tc_events e;
GO
/* Issued tickets of an event (including later used/refunded ones) and how many were checked in; never prices. */
CREATE OR ALTER FUNCTION dbo.fn_CheckInEventTicketCounts(@event_id uniqueidentifier)
RETURNS TABLE AS RETURN
 SELECT COUNT_BIG(*) issued_count,
        COALESCE(SUM(CASE WHEN t.status IN('CHECKED_IN','USED') THEN CAST(1 AS bigint) ELSE CAST(0 AS bigint) END),0) checked_in_count
 FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id
 JOIN dbo.tc_orders o ON o.id=i.order_id WHERE o.event_id=@event_id;
GO
/* ---- audit scope: does an audit row concern this event / this organization? One row when it does. ---- */
CREATE OR ALTER FUNCTION dbo.fn_AuditEventMatch(@event_id uniqueidentifier,@entity_type varchar(100),@entity_id varchar(100),@metadata_json nvarchar(max))
RETURNS TABLE AS RETURN
 SELECT 1 matched FROM dbo.tc_events e
 WHERE e.id=@event_id AND (
   (@entity_type='Event' AND TRY_CONVERT(uniqueidentifier,@entity_id)=e.id)
   OR TRY_CONVERT(uniqueidentifier,JSON_VALUE(CASE WHEN ISJSON(@metadata_json)=1 THEN @metadata_json ELSE N'{}' END,'$.eventId'))=e.id
   OR EXISTS(SELECT 1 FROM dbo.tc_settlements s WHERE s.event_id=e.id AND (
        (@entity_type='Settlement' AND TRY_CONVERT(uniqueidentifier,@entity_id)=s.id)
        OR TRY_CONVERT(uniqueidentifier,JSON_VALUE(CASE WHEN ISJSON(@metadata_json)=1 THEN @metadata_json ELSE N'{}' END,'$.settlementId'))=s.id
        OR EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs l WHERE l.settlement_id=s.id
                  AND @entity_type='SettlementTransferLog' AND TRY_CONVERT(uniqueidentifier,@entity_id)=l.id))));
GO
CREATE OR ALTER FUNCTION dbo.fn_AuditOrganizationMatch(@organization_id uniqueidentifier,@entity_type varchar(100),@entity_id varchar(100),@metadata_json nvarchar(max))
RETURNS TABLE AS RETURN
 SELECT 1 matched
 WHERE (@entity_type='Organization' AND TRY_CONVERT(uniqueidentifier,@entity_id)=@organization_id)
    OR TRY_CONVERT(uniqueidentifier,JSON_VALUE(CASE WHEN ISJSON(@metadata_json)=1 THEN @metadata_json ELSE N'{}' END,'$.organizationId'))=@organization_id
    OR EXISTS(SELECT 1 FROM dbo.tc_events e WHERE e.organization_id=@organization_id
              AND EXISTS(SELECT 1 FROM dbo.fn_AuditEventMatch(e.id,@entity_type,@entity_id,@metadata_json)))
    OR EXISTS(SELECT 1 FROM dbo.tc_commission_rules c WHERE c.organization_id=@organization_id
              AND @entity_type='CommissionRule' AND TRY_CONVERT(uniqueidentifier,@entity_id)=c.id);
GO
/* ---- public catalog ---- */
CREATE OR ALTER VIEW dbo.vw_EventCategories AS
 SELECT id,name,slug FROM dbo.tc_event_categories;
GO
CREATE OR ALTER VIEW dbo.vw_PublicEventCatalog AS
 SELECT e.event_id,e.organization_id,e.category_id,e.title,e.description,e.cover_image_url,e.venue_name,e.venue_address,
        e.sale_start,e.sale_end,e.start_time,e.end_time,e.minimum_price,
        c.name category_name,o.name organizer_name,prices.maximum_price,
        CASE WHEN SYSUTCDATETIME()>=e.sale_start AND SYSUTCDATETIME()<e.sale_end THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_on_sale
 FROM dbo.vw_PublicEvents e
 JOIN dbo.tc_event_categories c ON c.id=e.category_id
 JOIN dbo.tc_organizations o ON o.id=e.organization_id
 OUTER APPLY (SELECT MAX(zi.price) maximum_price FROM dbo.vw_ZoneInventory zi WHERE zi.event_id=e.event_id) prices;
GO
/* ---- event management ---- */
CREATE OR ALTER VIEW dbo.vw_ManagedEvents AS
 SELECT e.id,e.organization_id,e.category_id,e.title,e.description,e.venue_name,e.venue_address,e.sale_start,e.sale_end,
        e.start_time,e.end_time,e.status,e.version,NULLIF(e.event_image,'') image_url,e.event_image_public_id,
        cat.slug category_slug,e.event_image
 FROM dbo.tc_events e JOIN dbo.tc_event_categories cat ON cat.id=e.category_id;
GO
/* ---- refunds ---- */
CREATE OR ALTER VIEW dbo.vw_AdminRefundApprovalNotes AS
 SELECT aggregate_id refund_id,JSON_VALUE(detail,'$.note') note,created_at,id
 FROM dbo.tc_audit_logs WHERE action='ADMIN_REFUND_APPROVE' AND ISJSON(detail)=1;
GO
GRANT SELECT ON dbo.vw_ActorAuthority TO tc_platform_admin, tc_buyer;
GRANT SELECT ON dbo.vw_ActiveManagerMemberships TO tc_platform_admin;
GRANT SELECT ON dbo.vw_ManagerOrganizations TO tc_manager;
GRANT EXECUTE ON dbo.usp_EventRequireSession TO tc_manager;
GRANT EXECUTE ON dbo.usp_EventImageAuthorize TO tc_manager;
GRANT SELECT ON dbo.vw_FinanceOrganizations TO tc_platform_admin;
GRANT SELECT ON dbo.vw_FinanceEvents TO tc_platform_admin;
GRANT SELECT ON dbo.vw_FinanceSettlements TO tc_platform_admin;
GRANT SELECT ON dbo.vw_CommissionRuleUsage TO tc_platform_admin;
GRANT SELECT ON dbo.vw_SettlementSummary TO tc_platform_admin;
GRANT SELECT ON dbo.vw_SettlementPayouts TO tc_platform_admin;
GRANT SELECT ON dbo.vw_DashboardPlatformCounts TO tc_platform_admin;
GRANT SELECT ON dbo.fn_DashboardOrganizationCounts TO tc_platform_admin;
GRANT SELECT ON dbo.vw_CheckInStaffOrganizations TO tc_checkin;
GRANT SELECT ON dbo.vw_CheckInEvents TO tc_checkin;
GRANT SELECT ON dbo.fn_CheckInEventTicketCounts TO tc_checkin;
GRANT SELECT ON dbo.fn_AuditEventMatch TO tc_platform_admin;
GRANT SELECT ON dbo.fn_AuditOrganizationMatch TO tc_platform_admin;
GRANT SELECT ON dbo.vw_EventCategories TO tc_buyer, tc_manager;
GRANT SELECT ON dbo.vw_PublicEventCatalog TO tc_buyer;
GRANT SELECT ON dbo.vw_ManagedEvents TO tc_manager;
GRANT SELECT ON dbo.vw_AdminRefundApprovalNotes TO tc_platform_admin;
GO
