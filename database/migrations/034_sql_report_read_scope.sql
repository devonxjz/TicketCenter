-- Explicit technical principals bypass only where their contracts require it.
-- Buyer/manager data is scoped by the server-authenticated actor; unset actor fails closed.
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER FUNCTION dbo.fn_ReportOrderVisibility(@owner uniqueidentifier,@event uniqueidentifier)
RETURNS TABLE WITH SCHEMABINDING AS RETURN
 SELECT 1 permitted WHERE
 COALESCE(IS_MEMBER('db_owner'),0)=1 OR USER_NAME()=N'dbo'
 OR COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_worker'),0)=1
 OR COALESCE(IS_ROLEMEMBER('tc_checkin'),0)=1
 OR @owner=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id'))
 OR (COALESCE(IS_ROLEMEMBER('tc_manager'),0)=1 AND EXISTS(
 SELECT 1 FROM dbo.tc_events e JOIN dbo.tc_organization_memberships m ON m.organization_id=e.organization_id
 JOIN dbo.tc_users u ON u.id=m.user_id
 WHERE e.id=@event AND m.user_id=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id')) AND m.role='MANAGER' AND m.active=1 AND u.status='ACTIVE'));
GO
CREATE OR ALTER FUNCTION dbo.fn_ReportUserVisibility(@user uniqueidentifier)
RETURNS TABLE WITH SCHEMABINDING AS RETURN
 SELECT 1 permitted WHERE
 COALESCE(IS_MEMBER('db_owner'),0)=1 OR USER_NAME()=N'dbo'
 OR COALESCE(IS_ROLEMEMBER('tc_auth'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)=1
 OR COALESCE(IS_ROLEMEMBER('tc_worker'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_checkin'),0)=1
 OR @user=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id'))
 OR (COALESCE(IS_ROLEMEMBER('tc_manager'),0)=1 AND EXISTS(
 SELECT 1 FROM dbo.tc_organization_memberships member JOIN dbo.tc_organization_memberships manager ON manager.organization_id=member.organization_id
 WHERE member.user_id=@user AND member.active=1 AND manager.user_id=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id')) AND manager.role='MANAGER' AND manager.active=1));
GO
CREATE OR ALTER FUNCTION dbo.fn_ReportOrganizationRequestVisibility(@applicant uniqueidentifier)
RETURNS TABLE WITH SCHEMABINDING AS RETURN
 SELECT 1 permitted WHERE USER_NAME()=N'dbo' OR COALESCE(IS_MEMBER('db_owner'),0)=1
 OR COALESCE(IS_ROLEMEMBER('tc_auth'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)=1
 OR @applicant=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id'));
GO
CREATE OR ALTER FUNCTION dbo.fn_ReportOrganizationVisibility(@organization uniqueidentifier,@public bit)
RETURNS TABLE WITH SCHEMABINDING AS RETURN
 SELECT 1 permitted WHERE @public=1 OR USER_NAME()=N'dbo' OR COALESCE(IS_MEMBER('db_owner'),0)=1
 OR COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_worker'),0)=1
 OR EXISTS(SELECT 1 FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id=m.user_id
 WHERE m.organization_id=@organization AND m.user_id=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id')) AND m.active=1 AND u.status='ACTIVE');
GO
IF NOT EXISTS(SELECT 1 FROM sys.security_policies WHERE name='SqlReportTenantPolicy')
 EXEC(N'CREATE SECURITY POLICY dbo.SqlReportTenantPolicy
 ADD FILTER PREDICATE dbo.fn_ReportOrderVisibility(user_id,event_id) ON dbo.tc_orders,
 ADD FILTER PREDICATE dbo.fn_ReportUserVisibility(id) ON dbo.tc_users,
 ADD FILTER PREDICATE dbo.fn_ReportOrganizationRequestVisibility(applicant_id) ON dbo.tc_organization_requests
 WITH(STATE=ON,SCHEMABINDING=ON)');
GO
-- Coupon aggregate codes and organization-wide personal details are not buyer projections.
DENY SELECT ON dbo.vw_CouponUsage TO tc_buyer;
DENY SELECT ON dbo.vw_OrganizationMembers TO tc_buyer;
DENY SELECT ON dbo.tc_user_platform_roles TO tc_buyer;
DENY SELECT ON dbo.tc_organization_memberships TO tc_buyer;
GO
CREATE OR ALTER FUNCTION dbo.fn_ReadOwnMemberships()
RETURNS TABLE AS RETURN
 SELECT user_id,organization_id,role,active FROM dbo.tc_organization_memberships
 WHERE user_id=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id'));
GO
GRANT SELECT ON dbo.fn_ReadOwnMemberships TO tc_buyer;
GO
-- The payment/order policy scopes financial projections. Coupons have an independent organization root.
CREATE OR ALTER FUNCTION dbo.fn_ReportCouponVisibility(@organization uniqueidentifier)
RETURNS TABLE WITH SCHEMABINDING AS RETURN
 SELECT 1 permitted WHERE USER_NAME()=N'dbo' OR COALESCE(IS_MEMBER('db_owner'),0)=1
 OR COALESCE(IS_ROLEMEMBER('tc_platform_admin'),0)=1 OR COALESCE(IS_ROLEMEMBER('tc_worker'),0)=1
 -- Buyer has only eligibility/procedure access; code-containing aggregates are denied above.
 OR COALESCE(IS_ROLEMEMBER('tc_buyer'),0)=1
 OR EXISTS(SELECT 1 FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id=m.user_id
 WHERE m.organization_id=@organization AND m.user_id=TRY_CONVERT(uniqueidentifier,SESSION_CONTEXT(N'actor_id')) AND m.role='MANAGER' AND m.active=1 AND u.status='ACTIVE');
GO
IF NOT EXISTS(SELECT 1 FROM sys.security_policies WHERE name='SqlReportCouponPolicy')
 EXEC(N'CREATE SECURITY POLICY dbo.SqlReportCouponPolicy ADD FILTER PREDICATE dbo.fn_ReportCouponVisibility(organization_id) ON dbo.tc_coupons WITH(STATE=ON,SCHEMABINDING=ON)');
GO
