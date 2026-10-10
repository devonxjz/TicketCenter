/*
 * 008_create_runtime_roles_and_permissions
 *
 * Creates the 6 runtime database roles and grants least-privilege permissions.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* 1. Runtime Roles */
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'tc_auth' AND type = 'R')
BEGIN
    CREATE ROLE [tc_auth];
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'tc_buyer' AND type = 'R')
BEGIN
    CREATE ROLE [tc_buyer];
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'tc_checkin' AND type = 'R')
BEGIN
    CREATE ROLE [tc_checkin];
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'tc_manager' AND type = 'R')
BEGIN
    CREATE ROLE [tc_manager];
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'tc_platform_admin' AND type = 'R')
BEGIN
    CREATE ROLE [tc_platform_admin];
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'tc_worker' AND type = 'R')
BEGIN
    CREATE ROLE [tc_worker];
END
GO

/* 2. Runtime Permissions */
GRANT INSERT ON [dbo].[tc_otps] TO [tc_auth];
GRANT INSERT ON [dbo].[tc_user_platform_roles] TO [tc_auth];
GRANT INSERT ON [dbo].[tc_users] TO [tc_auth];
GRANT SELECT ON [dbo].[tc_otps] TO [tc_auth];
GRANT SELECT ON [dbo].[tc_user_platform_roles] TO [tc_auth];
GRANT SELECT ON [dbo].[tc_users] TO [tc_auth];
GRANT UPDATE ON [dbo].[tc_otps] TO [tc_auth];
GRANT UPDATE ON [dbo].[tc_users] TO [tc_auth];
GRANT EXECUTE ON [dbo].[usp_ApplyOrderCoupon] TO [tc_buyer];
GRANT EXECUTE ON [dbo].[usp_ApplyPaymentResult] TO [tc_buyer];
GRANT EXECUTE ON [dbo].[usp_BeginOrderPayment] TO [tc_buyer];
GRANT EXECUTE ON [dbo].[usp_CreateOrderFromHold] TO [tc_buyer];
GRANT EXECUTE ON [dbo].[usp_CreateRefundRequest] TO [tc_buyer];
GRANT EXECUTE ON [dbo].[usp_CreateTicketHold] TO [tc_buyer];
GRANT EXECUTE ON [dbo].[usp_ReleaseTicketHold] TO [tc_buyer];
GRANT INSERT ON [dbo].[tc_organization_requests] TO [tc_buyer];
GRANT SELECT ON [dbo].[fn_GetCouponEligibility] TO [tc_buyer];
GRANT SELECT ON [dbo].[fn_GetRefundableTickets] TO [tc_buyer];
GRANT SELECT ON [dbo].[fn_GetZoneAvailability] TO [tc_buyer];
GRANT SELECT ON [dbo].[tc_event_categories] TO [tc_buyer];
GRANT SELECT ON [dbo].[tc_organization_memberships] TO [tc_buyer];
GRANT SELECT ON [dbo].[tc_organization_requests] TO [tc_buyer];
GRANT SELECT ON [dbo].[tc_organizations] TO [tc_buyer];
GRANT SELECT ON [dbo].[tc_user_platform_roles] TO [tc_buyer];
GRANT SELECT ON [dbo].[tc_users] ([id], [email], [full_name], [phone], [status], [email_verified_at], [avatar]) TO [tc_buyer];
GRANT SELECT ON [dbo].[vw_CouponUsage] TO [tc_buyer];
GRANT SELECT ON [dbo].[vw_OrderFinancialSummary] TO [tc_buyer];
GRANT SELECT ON [dbo].[vw_OrganizationMembers] TO [tc_buyer];
GRANT SELECT ON [dbo].[vw_PublicEvents] TO [tc_buyer];
GRANT SELECT ON [dbo].[vw_RefundRequestOverview] TO [tc_buyer];
GRANT SELECT ON [dbo].[vw_TicketDetails] TO [tc_buyer];
GRANT SELECT ON [dbo].[vw_ZoneInventory] TO [tc_buyer];
DENY EXECUTE ON [dbo].[fn_CalculateCommission] TO [tc_checkin];
DENY SELECT ON [dbo].[fn_GetOrganizationCashFlow] TO [tc_checkin];
DENY SELECT ON [dbo].[fn_GetOrganizationRevenue] TO [tc_checkin];
DENY SELECT ON [dbo].[fn_GetSettlementBlockers] TO [tc_checkin];
DENY SELECT ON [dbo].[tc_order_items] TO [tc_checkin];
DENY SELECT ON [dbo].[tc_orders] TO [tc_checkin];
DENY SELECT ON [dbo].[tc_payments] TO [tc_checkin];
DENY SELECT ON [dbo].[tc_payouts] TO [tc_checkin];
DENY SELECT ON [dbo].[tc_refund_requests] TO [tc_checkin];
DENY SELECT ON [dbo].[tc_refunds] TO [tc_checkin];
DENY SELECT ON [dbo].[tc_settlement_items] TO [tc_checkin];
DENY SELECT ON [dbo].[tc_settlements] TO [tc_checkin];
DENY SELECT ON [dbo].[vw_EventSalesReport] TO [tc_checkin];
DENY SELECT ON [dbo].[vw_OrderFinancialSummary] TO [tc_checkin];
DENY SELECT ON [dbo].[vw_RefundRequestOverview] TO [tc_checkin];
DENY SELECT ON [dbo].[vw_SettlementPayoutBalance] TO [tc_checkin];
DENY SELECT ON [dbo].[vw_TicketDetails] TO [tc_checkin];
GRANT EXECUTE ON [dbo].[fn_event_is_check_in_open] TO [tc_checkin];
GRANT EXECUTE ON [dbo].[usp_CheckInTicket] TO [tc_checkin];
GRANT SELECT ON [dbo].[fn_GetEventCheckInWindow] TO [tc_checkin];
GRANT SELECT ON [dbo].[tc_events] ([id], [organization_id], [title], [venue_name], [start_time], [end_time], [status]) TO [tc_checkin];
GRANT SELECT ON [dbo].[tc_order_items] ([id], [order_id]) TO [tc_checkin];
GRANT SELECT ON [dbo].[tc_orders] ([id], [event_id]) TO [tc_checkin];
GRANT SELECT ON [dbo].[tc_organization_memberships] ([user_id], [organization_id], [role], [active]) TO [tc_checkin];
GRANT SELECT ON [dbo].[tc_tickets] ([order_item_id], [status]) TO [tc_checkin];
GRANT SELECT ON [dbo].[tc_users] ([id], [status]) TO [tc_checkin];
GRANT SELECT ON [dbo].[vw_CheckInHistory] TO [tc_checkin];
GRANT DELETE ON [dbo].[tc_events] TO [tc_manager];
GRANT DELETE ON [dbo].[tc_seats] TO [tc_manager];
GRANT DELETE ON [dbo].[tc_zones] TO [tc_manager];
GRANT EXECUTE ON [dbo].[usp_CheckInTicket] TO [tc_manager];
GRANT INSERT ON [dbo].[tc_audit_logs] TO [tc_manager];
GRANT INSERT ON [dbo].[tc_events] TO [tc_manager];
GRANT INSERT ON [dbo].[tc_organization_memberships] TO [tc_manager];
GRANT INSERT ON [dbo].[tc_seats] TO [tc_manager];
GRANT INSERT ON [dbo].[tc_zones] TO [tc_manager];
GRANT SELECT ON [dbo].[fn_GetEventCheckInWindow] TO [tc_manager];
GRANT SELECT ON [dbo].[fn_GetOrganizationCashFlow] TO [tc_manager];
GRANT SELECT ON [dbo].[fn_GetOrganizationRevenue] TO [tc_manager];
GRANT SELECT ON [dbo].[tc_event_categories] TO [tc_manager];
GRANT SELECT ON [dbo].[tc_events] TO [tc_manager];
GRANT SELECT ON [dbo].[tc_organization_memberships] TO [tc_manager];
GRANT SELECT ON [dbo].[tc_organizations] TO [tc_manager];
GRANT SELECT ON [dbo].[tc_seats] TO [tc_manager];
GRANT SELECT ON [dbo].[tc_users] ([id], [normalized_email], [status]) TO [tc_manager];
GRANT SELECT ON [dbo].[tc_zones] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_CheckInHistory] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_CouponUsage] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_EventSalesReport] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_OrderFinancialSummary] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_OrganizationMembers] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_PublicEvents] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_RefundRequestOverview] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_SettlementPayoutBalance] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_TicketDetails] TO [tc_manager];
GRANT SELECT ON [dbo].[vw_ZoneInventory] TO [tc_manager];
GRANT UPDATE ON [dbo].[tc_events] TO [tc_manager];
GRANT UPDATE ON [dbo].[tc_organization_memberships] TO [tc_manager];
GRANT UPDATE ON [dbo].[tc_seats] TO [tc_manager];
GRANT UPDATE ON [dbo].[tc_zones] TO [tc_manager];
GRANT EXECUTE ON [dbo].[usp_ApproveOrganizationRequest] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_CalculateEventSettlement] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_CancelEvent] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_ConfirmEventSettlement] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_ConfirmSettlement] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_DecideRefundRequest] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_ExecutePayout] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_PublishEvent] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_RecalculateSettlement] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_RecordPayout] TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_ReviewRefundRequest] TO [tc_platform_admin];
GRANT INSERT ON [dbo].[tc_audit_logs] TO [tc_platform_admin];
GRANT INSERT ON [dbo].[tc_commission_rules] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[fn_GetOrganizationCashFlow] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[fn_GetOrganizationRevenue] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[fn_GetSettlementBlockers] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_audit_logs] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_commission_rules] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_event_categories] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_events] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_organization_memberships] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_organization_requests] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_organizations] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_refund_requests] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_settlements] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[tc_users] ([id], [email], [full_name], [status]) TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_CheckInHistory] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_CouponUsage] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_EventCancellationExceptions] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_EventCancellationProgress] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_EventSalesReport] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_OrderFinancialSummary] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_OrganizationMembers] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_PublicEvents] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_RefundRequestOverview] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_SettlementPayoutBalance] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_TicketDetails] TO [tc_platform_admin];
GRANT SELECT ON [dbo].[vw_ZoneInventory] TO [tc_platform_admin];
GRANT UPDATE ON [dbo].[tc_events] TO [tc_platform_admin];
GRANT UPDATE ON [dbo].[tc_organization_requests] TO [tc_platform_admin];
GRANT UPDATE ON [dbo].[tc_users] ([status], [version]) TO [tc_platform_admin];
GRANT EXECUTE ON [dbo].[usp_ApplyPaymentResult] TO [tc_worker];
GRANT EXECUTE ON [dbo].[usp_ApplyRefundResult] TO [tc_worker];
GRANT EXECUTE ON [dbo].[usp_DecideRefundRequest] TO [tc_worker];
GRANT EXECUTE ON [dbo].[usp_ProcessCancelledOrder] TO [tc_worker];
GRANT EXECUTE ON [dbo].[usp_ReleaseTicketHold] TO [tc_worker];
GRANT EXECUTE ON [dbo].[usp_ReviewRefundRequest] TO [tc_worker];
GRANT SELECT ON [dbo].[tc_outbox] TO [tc_worker];
GRANT SELECT ON [dbo].[vw_EventCancellationWork] TO [tc_worker];
GRANT SELECT ON [dbo].[vw_FailedCompensationAttempts] TO [tc_worker];
GRANT SELECT ON [dbo].[vw_RefundWork] TO [tc_worker];
GRANT UPDATE ON [dbo].[tc_outbox] ([status], [available_at], [lease_owner], [lease_until], [attempts], [published_at]) TO [tc_worker];
GO
