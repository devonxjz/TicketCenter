/*
 * 003_create_indexes
 *
 * Creates all non-PK, non-UK performance indexes, filtered indexes, and INCLUDE columns.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_AuditLog_Aggregate_CreatedAt' AND object_id = OBJECT_ID(N'dbo.tc_audit_logs'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_AuditLog_Aggregate_CreatedAt] ON dbo.[tc_audit_logs] ([aggregate_type] ASC, [aggregate_id] ASC, [created_at] DESC, [id] ASC) INCLUDE ([action]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_CheckIn_Event_ScannedAt' AND object_id = OBJECT_ID(N'dbo.tc_check_ins'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_CheckIn_Event_ScannedAt] ON dbo.[tc_check_ins] ([event_id] ASC, [scanned_at] DESC, [id] ASC) INCLUDE ([result], [ticket_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_CommissionRule_Organization_EffectiveFrom' AND object_id = OBJECT_ID(N'dbo.tc_commission_rules'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_CommissionRule_Organization_EffectiveFrom] ON dbo.[tc_commission_rules] ([organization_id] ASC, [effective_from] ASC, [id] ASC) INCLUDE ([effective_to], [rate_percent], [fixed_fee]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_CouponRedemption_Coupon_Status' AND object_id = OBJECT_ID(N'dbo.tc_coupon_redemptions'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_CouponRedemption_Coupon_Status] ON dbo.[tc_coupon_redemptions] ([coupon_id] ASC, [status] ASC, [order_id] ASC);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_CouponRedemptions_CouponStatus' AND object_id = OBJECT_ID(N'dbo.tc_coupon_redemptions'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_CouponRedemptions_CouponStatus] ON dbo.[tc_coupon_redemptions] ([coupon_id] ASC, [status] ASC);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Event_Organization_Status_StartTime' AND object_id = OBJECT_ID(N'dbo.tc_events'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Event_Organization_Status_StartTime] ON dbo.[tc_events] ([organization_id] ASC, [status] ASC, [start_time] ASC, [id] ASC) INCLUDE ([title], [category_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Event_Status_StartTime' AND object_id = OBJECT_ID(N'dbo.tc_events'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Event_Status_StartTime] ON dbo.[tc_events] ([status] ASC, [start_time] ASC, [id] ASC) INCLUDE ([category_id], [organization_id], [title], [cover_image_url]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_OrderItems_Seat' AND object_id = OBJECT_ID(N'dbo.tc_order_items'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [UX_OrderItems_Seat] ON dbo.[tc_order_items] ([order_id] ASC, [seat_id] ASC) WHERE ([seat_id] IS NOT NULL);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Order_Event_Status_PaidAt' AND object_id = OBJECT_ID(N'dbo.tc_orders'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Order_Event_Status_PaidAt] ON dbo.[tc_orders] ([event_id] ASC, [status] ASC, [paid_at] ASC, [id] ASC) INCLUDE ([user_id], [total_amount]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Order_User_CreatedAt' AND object_id = OBJECT_ID(N'dbo.tc_orders'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Order_User_CreatedAt] ON dbo.[tc_orders] ([user_id] ASC, [created_at] DESC, [id] ASC) INCLUDE ([event_id], [status], [total_amount]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Otps_UserPurposeCreated' AND object_id = OBJECT_ID(N'dbo.tc_otps'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Otps_UserPurposeCreated] ON dbo.[tc_otps] ([user_id] ASC, [purpose] ASC, [created_at] DESC);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Outbox_Claim' AND object_id = OBJECT_ID(N'dbo.tc_outbox'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Outbox_Claim] ON dbo.[tc_outbox] ([status] ASC, [available_at] ASC, [lease_until] ASC);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Payment_Status_CreatedAt' AND object_id = OBJECT_ID(N'dbo.tc_payments'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Payment_Status_CreatedAt] ON dbo.[tc_payments] ([status] ASC, [created_at] ASC, [id] ASC) INCLUDE ([order_id], [txn_ref]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Payout_Settlement_Status' AND object_id = OBJECT_ID(N'dbo.tc_payouts'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Payout_Settlement_Status] ON dbo.[tc_payouts] ([settlement_id] ASC, [status] ASC, [id] ASC) INCLUDE ([amount]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Refund_Status_CreatedAt' AND object_id = OBJECT_ID(N'dbo.tc_refund_obligations'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Refund_Status_CreatedAt] ON dbo.[tc_refund_obligations] ([status] ASC, [created_at] ASC, [id] ASC) INCLUDE ([order_id], [purpose], [reason_type]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_RefundObligations_LegacyRequest' AND object_id = OBJECT_ID(N'dbo.tc_refund_obligations'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [UX_RefundObligations_LegacyRequest] ON dbo.[tc_refund_obligations] ([legacy_request_id] ASC) WHERE ([legacy_request_id] IS NOT NULL);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_RefundRequestTickets_OneOpenPerTicket' AND object_id = OBJECT_ID(N'dbo.tc_refund_request_tickets'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [UX_RefundRequestTickets_OneOpenPerTicket] ON dbo.[tc_refund_request_tickets] ([ticket_id] ASC) WHERE ([is_open]=(1));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_RefundRequest_Status_RequestedAt' AND object_id = OBJECT_ID(N'dbo.tc_refund_requests'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_RefundRequest_Status_RequestedAt] ON dbo.[tc_refund_requests] ([status] ASC, [requested_at] ASC, [id] ASC) INCLUDE ([order_id], [reason_type]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_RefundTickets_OneOpenPerTicket' AND object_id = OBJECT_ID(N'dbo.tc_refund_tickets'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [UX_RefundTickets_OneOpenPerTicket] ON dbo.[tc_refund_tickets] ([ticket_id] ASC) WHERE ([is_open]=(1));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_RefundTransferLog_Status_CreatedAt' AND object_id = OBJECT_ID(N'dbo.tc_refund_transfer_logs'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_RefundTransferLog_Status_CreatedAt] ON dbo.[tc_refund_transfer_logs] ([status] ASC, [created_at] ASC, [id] ASC) INCLUDE ([refund_id], [provider_reference]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Refund_Status_CreatedAt' AND object_id = OBJECT_ID(N'dbo.tc_refunds'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Refund_Status_CreatedAt] ON dbo.[tc_refunds] ([status] ASC, [created_at] ASC, [id] ASC) INCLUDE ([amount], [provider_reference], [payment_id], [purpose], [refund_request_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Seat_Zone_Status' AND object_id = OBJECT_ID(N'dbo.tc_seats'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Seat_Zone_Status] ON dbo.[tc_seats] ([zone_id] ASC, [status] ASC, [id] ASC) INCLUDE ([row_name], [seat_number]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Payout_Settlement_Status' AND object_id = OBJECT_ID(N'dbo.tc_settlement_transfer_logs'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Payout_Settlement_Status] ON dbo.[tc_settlement_transfer_logs] ([settlement_id] ASC, [status] ASC, [id] ASC) INCLUDE ([amount]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_TicketCheckInLog_Event_ScannedAt' AND object_id = OBJECT_ID(N'dbo.tc_ticket_check_in_logs'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_TicketCheckInLog_Event_ScannedAt] ON dbo.[tc_ticket_check_in_logs] ([event_id] ASC, [scanned_at] DESC, [id] ASC) INCLUDE ([result], [ticket_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_TicketHoldItems_Seat' AND object_id = OBJECT_ID(N'dbo.tc_ticket_hold_items'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [UX_TicketHoldItems_Seat] ON dbo.[tc_ticket_hold_items] ([hold_id] ASC, [seat_id] ASC) WHERE ([seat_id] IS NOT NULL);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_TicketHold_Status_ExpiresAt' AND object_id = OBJECT_ID(N'dbo.tc_ticket_holds'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_TicketHold_Status_ExpiresAt] ON dbo.[tc_ticket_holds] ([status] ASC, [expires_at] ASC, [id] ASC) INCLUDE ([user_id], [event_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_TicketHolds_OneActivePerUser' AND object_id = OBJECT_ID(N'dbo.tc_ticket_holds'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [UX_TicketHolds_OneActivePerUser] ON dbo.[tc_ticket_holds] ([user_id] ASC) WHERE ([status]='ACTIVE');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_Ticket_OrderItem_Status' AND object_id = OBJECT_ID(N'dbo.tc_tickets'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_Ticket_OrderItem_Status] ON dbo.[tc_tickets] ([order_item_id] ASC, [status] ASC, [id] ASC) INCLUDE ([paid_amount]);
END
GO
