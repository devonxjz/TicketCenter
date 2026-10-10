/*
 * 002_create_relationships_and_constraints
 *
 * Enforces all UNIQUE constraints, CHECK constraints, and FOREIGN KEY constraints.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* -------------------------------------------------------------
   1. UNIQUE CONSTRAINTS
   ------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_CouponRedemptions_Order' AND parent_object_id = OBJECT_ID(N'dbo.tc_coupon_redemptions'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_CouponRedemptions_Order' AND object_id = OBJECT_ID(N'dbo.tc_coupon_redemptions'))
BEGIN
    ALTER TABLE dbo.[tc_coupon_redemptions] ADD CONSTRAINT [UQ_CouponRedemptions_Order] UNIQUE ([order_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Coupons_Organization_Code' AND parent_object_id = OBJECT_ID(N'dbo.tc_coupons'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Coupons_Organization_Code' AND object_id = OBJECT_ID(N'dbo.tc_coupons'))
BEGIN
    ALTER TABLE dbo.[tc_coupons] ADD CONSTRAINT [UQ_Coupons_Organization_Code] UNIQUE ([organization_id], [code]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_EventCategories_Slug' AND parent_object_id = OBJECT_ID(N'dbo.tc_event_categories'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_EventCategories_Slug' AND object_id = OBJECT_ID(N'dbo.tc_event_categories'))
BEGIN
    ALTER TABLE dbo.[tc_event_categories] ADD CONSTRAINT [UQ_EventCategories_Slug] UNIQUE ([slug]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Orders_Hold' AND parent_object_id = OBJECT_ID(N'dbo.tc_orders'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Orders_Hold' AND object_id = OBJECT_ID(N'dbo.tc_orders'))
BEGIN
    ALTER TABLE dbo.[tc_orders] ADD CONSTRAINT [UQ_Orders_Hold] UNIQUE ([hold_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Orders_OrderCode' AND parent_object_id = OBJECT_ID(N'dbo.tc_orders'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Orders_OrderCode' AND object_id = OBJECT_ID(N'dbo.tc_orders'))
BEGIN
    ALTER TABLE dbo.[tc_orders] ADD CONSTRAINT [UQ_Orders_OrderCode] UNIQUE ([order_code]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Membership_User_Organization' AND parent_object_id = OBJECT_ID(N'dbo.tc_organization_memberships'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Membership_User_Organization' AND object_id = OBJECT_ID(N'dbo.tc_organization_memberships'))
BEGIN
    ALTER TABLE dbo.[tc_organization_memberships] ADD CONSTRAINT [UQ_Membership_User_Organization] UNIQUE ([user_id], [organization_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Outbox_IdempotencyKey' AND parent_object_id = OBJECT_ID(N'dbo.tc_outbox'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Outbox_IdempotencyKey' AND object_id = OBJECT_ID(N'dbo.tc_outbox'))
BEGIN
    ALTER TABLE dbo.[tc_outbox] ADD CONSTRAINT [UQ_Outbox_IdempotencyKey] UNIQUE ([idempotency_key]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Payment_TxnRef' AND parent_object_id = OBJECT_ID(N'dbo.tc_payments'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Payment_TxnRef' AND object_id = OBJECT_ID(N'dbo.tc_payments'))
BEGIN
    ALTER TABLE dbo.[tc_payments] ADD CONSTRAINT [UQ_Payment_TxnRef] UNIQUE ([txn_ref]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Payouts_Reference' AND parent_object_id = OBJECT_ID(N'dbo.tc_payouts'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Payouts_Reference' AND object_id = OBJECT_ID(N'dbo.tc_payouts'))
BEGIN
    ALTER TABLE dbo.[tc_payouts] ADD CONSTRAINT [UQ_Payouts_Reference] UNIQUE ([reference]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_RefundTickets_RefundTicket' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_tickets'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_RefundTickets_RefundTicket' AND object_id = OBJECT_ID(N'dbo.tc_refund_tickets'))
BEGIN
    ALTER TABLE dbo.[tc_refund_tickets] ADD CONSTRAINT [UQ_RefundTickets_RefundTicket] UNIQUE ([refund_id], [ticket_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_RefundTransferLogs_RefundAttempt' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_transfer_logs'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_RefundTransferLogs_RefundAttempt' AND object_id = OBJECT_ID(N'dbo.tc_refund_transfer_logs'))
BEGIN
    ALTER TABLE dbo.[tc_refund_transfer_logs] ADD CONSTRAINT [UQ_RefundTransferLogs_RefundAttempt] UNIQUE ([refund_id], [attempt_no]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Seat_Zone_Row_Number' AND parent_object_id = OBJECT_ID(N'dbo.tc_seats'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Seat_Zone_Row_Number' AND object_id = OBJECT_ID(N'dbo.tc_seats'))
BEGIN
    ALTER TABLE dbo.[tc_seats] ADD CONSTRAINT [UQ_Seat_Zone_Row_Number] UNIQUE ([zone_id], [row_name], [seat_number]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Seats_Zone_Label' AND parent_object_id = OBJECT_ID(N'dbo.tc_seats'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Seats_Zone_Label' AND object_id = OBJECT_ID(N'dbo.tc_seats'))
BEGIN
    ALTER TABLE dbo.[tc_seats] ADD CONSTRAINT [UQ_Seats_Zone_Label] UNIQUE ([zone_id], [label]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_SettlementItems_Order' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlement_items'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_SettlementItems_Order' AND object_id = OBJECT_ID(N'dbo.tc_settlement_items'))
BEGIN
    ALTER TABLE dbo.[tc_settlement_items] ADD CONSTRAINT [UQ_SettlementItems_Order] UNIQUE ([order_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_SettlementOrderSnapshots_SettlementOrder' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlement_order_snapshots'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_SettlementOrderSnapshots_SettlementOrder' AND object_id = OBJECT_ID(N'dbo.tc_settlement_order_snapshots'))
BEGIN
    ALTER TABLE dbo.[tc_settlement_order_snapshots] ADD CONSTRAINT [UQ_SettlementOrderSnapshots_SettlementOrder] UNIQUE ([settlement_id], [order_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Settlements_Event' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlements'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Settlements_Event' AND object_id = OBJECT_ID(N'dbo.tc_settlements'))
BEGIN
    ALTER TABLE dbo.[tc_settlements] ADD CONSTRAINT [UQ_Settlements_Event] UNIQUE ([event_id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Tickets_TicketCode' AND parent_object_id = OBJECT_ID(N'dbo.tc_tickets'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Tickets_TicketCode' AND object_id = OBJECT_ID(N'dbo.tc_tickets'))
BEGIN
    ALTER TABLE dbo.[tc_tickets] ADD CONSTRAINT [UQ_Tickets_TicketCode] UNIQUE ([ticket_code]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_User_NormalizedEmail' AND parent_object_id = OBJECT_ID(N'dbo.tc_users'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_User_NormalizedEmail' AND object_id = OBJECT_ID(N'dbo.tc_users'))
BEGIN
    ALTER TABLE dbo.[tc_users] ADD CONSTRAINT [UQ_User_NormalizedEmail] UNIQUE ([normalized_email]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_User_NormalizedUserName' AND parent_object_id = OBJECT_ID(N'dbo.tc_users'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_User_NormalizedUserName' AND object_id = OBJECT_ID(N'dbo.tc_users'))
BEGIN
    ALTER TABLE dbo.[tc_users] ADD CONSTRAINT [UQ_User_NormalizedUserName] UNIQUE ([user_name]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Zones_Event_Name' AND parent_object_id = OBJECT_ID(N'dbo.tc_zones'))
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_Zones_Event_Name' AND object_id = OBJECT_ID(N'dbo.tc_zones'))
BEGIN
    ALTER TABLE dbo.[tc_zones] ADD CONSTRAINT [UQ_Zones_Event_Name] UNIQUE ([event_id], [name]);
END
GO

/* -------------------------------------------------------------
   2. CHECK CONSTRAINTS
   ------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_CheckIns_Result' AND parent_object_id = OBJECT_ID(N'dbo.tc_check_ins'))
BEGIN
    ALTER TABLE dbo.[tc_check_ins] WITH CHECK ADD CONSTRAINT [CK_CheckIns_Result] CHECK ([result]='EVENT_CANCELLED' OR [result]='NOT_ACTIVE' OR [result]='ALREADY_USED' OR [result]='TOO_LATE' OR [result]='TOO_EARLY' OR [result]='WRONG_EVENT' OR [result]='NOT_FOUND' OR [result]='SUCCESS');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_CommissionRule_Terms' AND parent_object_id = OBJECT_ID(N'dbo.tc_commission_rules'))
BEGIN
    ALTER TABLE dbo.[tc_commission_rules] WITH CHECK ADD CONSTRAINT [CK_CommissionRule_Terms] CHECK ([rate_percent]>=(0) AND [fixed_fee]>=(0) AND [effective_from]<[effective_to]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_CouponRedemptions_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_coupon_redemptions'))
BEGIN
    ALTER TABLE dbo.[tc_coupon_redemptions] WITH CHECK ADD CONSTRAINT [CK_CouponRedemptions_Status] CHECK ([status]='RELEASED' OR [status]='CONSUMED' OR [status]='RESERVED');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_CouponRedemptions_Time' AND parent_object_id = OBJECT_ID(N'dbo.tc_coupon_redemptions'))
BEGIN
    ALTER TABLE dbo.[tc_coupon_redemptions] WITH CHECK ADD CONSTRAINT [CK_CouponRedemptions_Time] CHECK ([expires_at]>[reserved_at]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Coupon_Discount_Limit' AND parent_object_id = OBJECT_ID(N'dbo.tc_coupons'))
BEGIN
    ALTER TABLE dbo.[tc_coupons] WITH CHECK ADD CONSTRAINT [CK_Coupon_Discount_Limit] CHECK ([max_uses]>(0) AND [valid_from]<[valid_to] AND ([max_discount_amount] IS NULL OR [max_discount_amount]>=(0)) AND ([discount_type]='PERCENTAGE' AND [percentage_value] IS NOT NULL AND [percentage_value]>(0) AND [percentage_value]<=(30) AND [fixed_amount] IS NULL OR [discount_type]='FIXED_AMOUNT' AND [fixed_amount] IS NOT NULL AND [fixed_amount]>(0) AND [percentage_value] IS NULL));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Coupons_DiscountType' AND parent_object_id = OBJECT_ID(N'dbo.tc_coupons'))
BEGIN
    ALTER TABLE dbo.[tc_coupons] WITH CHECK ADD CONSTRAINT [CK_Coupons_DiscountType] CHECK ([discount_type]='FIXED_AMOUNT' OR [discount_type]='PERCENTAGE');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Event_TimeRange' AND parent_object_id = OBJECT_ID(N'dbo.tc_events'))
BEGIN
    ALTER TABLE dbo.[tc_events] WITH CHECK ADD CONSTRAINT [CK_Event_TimeRange] CHECK ([sale_start]<[sale_end] AND [sale_end]<=[start_time] AND [start_time]<[end_time]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Events_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_events'))
BEGIN
    ALTER TABLE dbo.[tc_events] WITH CHECK ADD CONSTRAINT [CK_Events_Status] CHECK ([status]='CANCELLED' OR [status]='PUBLISHED' OR [status]='REJECTED' OR [status]='PENDING_APPROVAL' OR [status]='DRAFT');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_OrderItem_Seat_Quantity' AND parent_object_id = OBJECT_ID(N'dbo.tc_order_items'))
BEGIN
    ALTER TABLE dbo.[tc_order_items] WITH CHECK ADD CONSTRAINT [CK_OrderItem_Seat_Quantity] CHECK ([seat_id] IS NOT NULL AND [quantity]=(1) OR [seat_id] IS NULL AND [quantity]>(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_OrderItem_UnitPrice' AND parent_object_id = OBJECT_ID(N'dbo.tc_order_items'))
BEGIN
    ALTER TABLE dbo.[tc_order_items] WITH CHECK ADD CONSTRAINT [CK_OrderItem_UnitPrice] CHECK ([unit_price]>=(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Order_Amounts' AND parent_object_id = OBJECT_ID(N'dbo.tc_orders'))
BEGIN
    ALTER TABLE dbo.[tc_orders] WITH CHECK ADD CONSTRAINT [CK_Order_Amounts] CHECK ([subtotal_amount]>=(0) AND [discount_amount]>=(0) AND [total_amount]>=(0) AND [total_amount]=([subtotal_amount]-[discount_amount]) AND [discount_amount]*(10)<=[subtotal_amount]*(3));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Orders_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_orders'))
BEGIN
    ALTER TABLE dbo.[tc_orders] WITH CHECK ADD CONSTRAINT [CK_Orders_Status] CHECK ([status]='EXPIRED' OR [status]='CANCELLED' OR [status]='PAID' OR [status]='PENDING_PAYMENT');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_OrganizationMemberships_Role' AND parent_object_id = OBJECT_ID(N'dbo.tc_organization_memberships'))
BEGIN
    ALTER TABLE dbo.[tc_organization_memberships] WITH CHECK ADD CONSTRAINT [CK_OrganizationMemberships_Role] CHECK ([role]='CHECK_IN_STAFF' OR [role]='MANAGER');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_OrganizationRequest_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_organization_requests'))
BEGIN
    ALTER TABLE dbo.[tc_organization_requests] WITH CHECK ADD CONSTRAINT [CK_OrganizationRequest_Status] CHECK ([status]='REJECTED' OR [status]='APPROVED' OR [status]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Otps_Attempts' AND parent_object_id = OBJECT_ID(N'dbo.tc_otps'))
BEGIN
    ALTER TABLE dbo.[tc_otps] WITH CHECK ADD CONSTRAINT [CK_Otps_Attempts] CHECK ([failed_attempts]>=(0) AND [failed_attempts]<=(5));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Otps_Purpose' AND parent_object_id = OBJECT_ID(N'dbo.tc_otps'))
BEGIN
    ALTER TABLE dbo.[tc_otps] WITH CHECK ADD CONSTRAINT [CK_Otps_Purpose] CHECK ([purpose]='RESET_PASSWORD' OR [purpose]='VERIFY_EMAIL');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Otps_Time' AND parent_object_id = OBJECT_ID(N'dbo.tc_otps'))
BEGIN
    ALTER TABLE dbo.[tc_otps] WITH CHECK ADD CONSTRAINT [CK_Otps_Time] CHECK ([expires_at]>[created_at]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Outbox_Attempts' AND parent_object_id = OBJECT_ID(N'dbo.tc_outbox'))
BEGIN
    ALTER TABLE dbo.[tc_outbox] WITH CHECK ADD CONSTRAINT [CK_Outbox_Attempts] CHECK ([attempts]>=(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Outbox_Lease' AND parent_object_id = OBJECT_ID(N'dbo.tc_outbox'))
BEGIN
    ALTER TABLE dbo.[tc_outbox] WITH CHECK ADD CONSTRAINT [CK_Outbox_Lease] CHECK ([lease_owner] IS NULL AND [lease_until] IS NULL OR [lease_owner] IS NOT NULL AND [lease_until] IS NOT NULL);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Outbox_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_outbox'))
BEGIN
    ALTER TABLE dbo.[tc_outbox] WITH CHECK ADD CONSTRAINT [CK_Outbox_Status] CHECK ([status]='FAILED' OR [status]='PUBLISHED' OR [status]='PROCESSING' OR [status]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Payment_PositiveAmount' AND parent_object_id = OBJECT_ID(N'dbo.tc_payments'))
BEGIN
    ALTER TABLE dbo.[tc_payments] WITH CHECK ADD CONSTRAINT [CK_Payment_PositiveAmount] CHECK ([amount]>(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Payments_Currency' AND parent_object_id = OBJECT_ID(N'dbo.tc_payments'))
BEGIN
    ALTER TABLE dbo.[tc_payments] WITH CHECK ADD CONSTRAINT [CK_Payments_Currency] CHECK ([currency]='VND');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Payments_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_payments'))
BEGIN
    ALTER TABLE dbo.[tc_payments] WITH CHECK ADD CONSTRAINT [CK_Payments_Status] CHECK ([status]='UNKNOWN' OR [status]='FAILED' OR [status]='CAPTURED' OR [status]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Payout_PositiveAmount' AND parent_object_id = OBJECT_ID(N'dbo.tc_payouts'))
BEGIN
    ALTER TABLE dbo.[tc_payouts] WITH CHECK ADD CONSTRAINT [CK_Payout_PositiveAmount] CHECK ([amount]>(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Payouts_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_payouts'))
BEGIN
    ALTER TABLE dbo.[tc_payouts] WITH CHECK ADD CONSTRAINT [CK_Payouts_Status] CHECK ([status]='FAILED' OR [status]='SUCCEEDED' OR [status]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_RefundObligations_Amount' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_obligations'))
BEGIN
    ALTER TABLE dbo.[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [CK_RefundObligations_Amount] CHECK ([amount]>=(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_RefundObligations_Purpose' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_obligations'))
BEGIN
    ALTER TABLE dbo.[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [CK_RefundObligations_Purpose] CHECK ([purpose]='PAYMENT_COMPENSATION' OR [purpose]='CUSTOMER_REFUND');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_RefundObligations_ReasonType' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_obligations'))
BEGIN
    ALTER TABLE dbo.[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [CK_RefundObligations_ReasonType] CHECK ([reason_type] IS NULL OR ([reason_type]='EVENT_CANCELLATION' OR [reason_type]='CUSTOMER_REQUEST'));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_RefundObligations_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_obligations'))
BEGIN
    ALTER TABLE dbo.[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [CK_RefundObligations_Status] CHECK ([status]='COMPLETED' OR [status]='REJECTED' OR [status]='RETRYABLE' OR [status]='NEEDS_RECONCILIATION' OR [status]='PROCESSING' OR [status]='APPROVED' OR [status]='REQUESTED');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_RefundRequests_ReasonType' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_requests'))
BEGIN
    ALTER TABLE dbo.[tc_refund_requests] WITH CHECK ADD CONSTRAINT [CK_RefundRequests_ReasonType] CHECK ([reason_type]='EVENT_CANCELLATION' OR [reason_type]='CUSTOMER_REQUEST');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_RefundRequests_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_requests'))
BEGIN
    ALTER TABLE dbo.[tc_refund_requests] WITH CHECK ADD CONSTRAINT [CK_RefundRequests_Status] CHECK ([status]='COMPLETED' OR [status]='REJECTED' OR [status]='APPROVED' OR [status]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_RefundTransferLogs_AttemptNo' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_transfer_logs'))
BEGIN
    ALTER TABLE dbo.[tc_refund_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_RefundTransferLogs_AttemptNo] CHECK ([attempt_no]>(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_RefundTransferLogs_Result' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_transfer_logs'))
BEGIN
    ALTER TABLE dbo.[tc_refund_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_RefundTransferLogs_Result] CHECK ([result]='SUCCEEDED' OR [result]='FAILED' OR [result]='UNKNOWN' OR [result]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_RefundTransferLogs_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_refund_transfer_logs'))
BEGIN
    ALTER TABLE dbo.[tc_refund_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_RefundTransferLogs_Status] CHECK ([status]='SUCCEEDED' OR [status]='FAILED' OR [status]='UNKNOWN' OR [status]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Refund_PositiveAmount' AND parent_object_id = OBJECT_ID(N'dbo.tc_refunds'))
BEGIN
    ALTER TABLE dbo.[tc_refunds] WITH CHECK ADD CONSTRAINT [CK_Refund_PositiveAmount] CHECK ([amount]>(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Refund_Purpose_Request' AND parent_object_id = OBJECT_ID(N'dbo.tc_refunds'))
BEGIN
    ALTER TABLE dbo.[tc_refunds] WITH CHECK ADD CONSTRAINT [CK_Refund_Purpose_Request] CHECK ([purpose]='CUSTOMER_REFUND' AND [refund_request_id] IS NOT NULL OR [purpose]='PAYMENT_COMPENSATION' AND [refund_request_id] IS NULL);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Refunds_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_refunds'))
BEGIN
    ALTER TABLE dbo.[tc_refunds] WITH CHECK ADD CONSTRAINT [CK_Refunds_Status] CHECK ([status]='UNKNOWN' OR [status]='FAILED' OR [status]='SUCCEEDED' OR [status]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Seats_Number' AND parent_object_id = OBJECT_ID(N'dbo.tc_seats'))
BEGIN
    ALTER TABLE dbo.[tc_seats] WITH CHECK ADD CONSTRAINT [CK_Seats_Number] CHECK ([seat_number]>(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Seats_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_seats'))
BEGIN
    ALTER TABLE dbo.[tc_seats] WITH CHECK ADD CONSTRAINT [CK_Seats_Status] CHECK ([status]='SOLD' OR [status]='HELD' OR [status]='AVAILABLE');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_SettlementItem_Amounts' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlement_items'))
BEGIN
    ALTER TABLE dbo.[tc_settlement_items] WITH CHECK ADD CONSTRAINT [CK_SettlementItem_Amounts] CHECK ([gross_amount]>=(0) AND [refund_amount]>=(0) AND [commission_amount]>=(0) AND [net_amount]>=(0) AND [refund_amount]<=[gross_amount] AND [commission_amount]<=([gross_amount]-[refund_amount]) AND [net_amount]=(([gross_amount]-[refund_amount])-[commission_amount]));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_SettlementOrderSnapshots_Amounts' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlement_order_snapshots'))
BEGIN
    ALTER TABLE dbo.[tc_settlement_order_snapshots] WITH CHECK ADD CONSTRAINT [CK_SettlementOrderSnapshots_Amounts] CHECK ([gross_amount]>=(0) AND [refund_amount]>=(0) AND [commission_amount]>=(0) AND [net_amount]>=(0) AND [net_amount]=(([gross_amount]-[refund_amount])-[commission_amount]));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_SettlementTransferLogs_Amount' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlement_transfer_logs'))
BEGIN
    ALTER TABLE dbo.[tc_settlement_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_SettlementTransferLogs_Amount] CHECK ([amount]>(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_SettlementTransferLogs_Result' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlement_transfer_logs'))
BEGIN
    ALTER TABLE dbo.[tc_settlement_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_SettlementTransferLogs_Result] CHECK ([result]='FAILED' OR [result]='SUCCEEDED' OR [result]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_SettlementTransferLogs_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlement_transfer_logs'))
BEGIN
    ALTER TABLE dbo.[tc_settlement_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_SettlementTransferLogs_Status] CHECK ([status]='FAILED' OR [status]='SUCCEEDED' OR [status]='PENDING');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Settlement_Amounts' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlements'))
BEGIN
    ALTER TABLE dbo.[tc_settlements] WITH CHECK ADD CONSTRAINT [CK_Settlement_Amounts] CHECK ([gross_revenue]>=(0) AND [total_refund]>=(0) AND [total_commission]>=(0) AND [net_payable]>=(0) AND [total_refund]<=[gross_revenue] AND [total_commission]<=([gross_revenue]-[total_refund]) AND [net_payable]=(([gross_revenue]-[total_refund])-[total_commission]));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Settlement_PayoutBalances' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlements'))
BEGIN
    ALTER TABLE dbo.[tc_settlements] WITH CHECK ADD CONSTRAINT [CK_Settlement_PayoutBalances] CHECK ([paid_amount]>=(0) AND [pending_amount]>=(0) AND ([paid_amount]+[pending_amount])<=[net_payable]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Settlements_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_settlements'))
BEGIN
    ALTER TABLE dbo.[tc_settlements] WITH CHECK ADD CONSTRAINT [CK_Settlements_Status] CHECK ([status]='PAID' OR [status]='CONFIRMED' OR [status]='DRAFT');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_HoldItem_Seat_Quantity' AND parent_object_id = OBJECT_ID(N'dbo.tc_ticket_hold_items'))
BEGIN
    ALTER TABLE dbo.[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [CK_HoldItem_Seat_Quantity] CHECK ([seat_id] IS NOT NULL AND [quantity]=(1) OR [seat_id] IS NULL AND [quantity]>(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_HoldItem_UnitPrice' AND parent_object_id = OBJECT_ID(N'dbo.tc_ticket_hold_items'))
BEGIN
    ALTER TABLE dbo.[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [CK_HoldItem_UnitPrice] CHECK ([unit_price]>=(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_TicketHold_TimeRange' AND parent_object_id = OBJECT_ID(N'dbo.tc_ticket_holds'))
BEGIN
    ALTER TABLE dbo.[tc_ticket_holds] WITH CHECK ADD CONSTRAINT [CK_TicketHold_TimeRange] CHECK ([expires_at]>[created_at]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_TicketHolds_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_ticket_holds'))
BEGIN
    ALTER TABLE dbo.[tc_ticket_holds] WITH CHECK ADD CONSTRAINT [CK_TicketHolds_Status] CHECK ([status]='CONSUMED' OR [status]='RELEASED' OR [status]='ACTIVE');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Ticket_PaidAmount' AND parent_object_id = OBJECT_ID(N'dbo.tc_tickets'))
BEGIN
    ALTER TABLE dbo.[tc_tickets] WITH CHECK ADD CONSTRAINT [CK_Ticket_PaidAmount] CHECK ([paid_amount]>=(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Tickets_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_tickets'))
BEGIN
    ALTER TABLE dbo.[tc_tickets] WITH CHECK ADD CONSTRAINT [CK_Tickets_Status] CHECK ([status]='INVALIDATED' OR [status]='REFUNDED' OR [status]='REFUND_PENDING' OR [status]='USED' OR [status]='ACTIVE');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_UserPlatformRoles_Role' AND parent_object_id = OBJECT_ID(N'dbo.tc_user_platform_roles'))
BEGIN
    ALTER TABLE dbo.[tc_user_platform_roles] WITH CHECK ADD CONSTRAINT [CK_UserPlatformRoles_Role] CHECK ([role]='ADMIN');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Users_AuthVersion' AND parent_object_id = OBJECT_ID(N'dbo.tc_users'))
BEGIN
    ALTER TABLE dbo.[tc_users] WITH CHECK ADD CONSTRAINT [CK_Users_AuthVersion] CHECK ([auth_version]>=(0));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Users_Status' AND parent_object_id = OBJECT_ID(N'dbo.tc_users'))
BEGIN
    ALTER TABLE dbo.[tc_users] WITH CHECK ADD CONSTRAINT [CK_Users_Status] CHECK ([status]='DISABLED' OR [status]='ACTIVE');
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Zone_Price_Quota' AND parent_object_id = OBJECT_ID(N'dbo.tc_zones'))
BEGIN
    ALTER TABLE dbo.[tc_zones] WITH CHECK ADD CONSTRAINT [CK_Zone_Price_Quota] CHECK ([price]>=(0) AND [held_quantity]>=(0) AND [sold_quantity]>=(0) AND ([type]='STANDING' AND [capacity] IS NOT NULL AND [capacity]>(0) AND ([held_quantity]+[sold_quantity])<=[capacity] OR [type]='SEATED' AND [capacity] IS NULL AND [held_quantity]=(0) AND [sold_quantity]=(0)));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Zones_Type' AND parent_object_id = OBJECT_ID(N'dbo.tc_zones'))
BEGIN
    ALTER TABLE dbo.[tc_zones] WITH CHECK ADD CONSTRAINT [CK_Zones_Type] CHECK ([type]='STANDING' OR [type]='SEATED');
END
GO

/* -------------------------------------------------------------
   3. FOREIGN KEYS
   ------------------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_AuditLogs_Actor')
BEGIN
    ALTER TABLE dbo.[tc_audit_logs] WITH CHECK ADD CONSTRAINT [FK_AuditLogs_Actor] FOREIGN KEY ([actor_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_CheckIns_Actor')
BEGIN
    ALTER TABLE dbo.[tc_check_ins] WITH CHECK ADD CONSTRAINT [FK_CheckIns_Actor] FOREIGN KEY ([actor_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_CheckIns_Event')
BEGIN
    ALTER TABLE dbo.[tc_check_ins] WITH CHECK ADD CONSTRAINT [FK_CheckIns_Event] FOREIGN KEY ([event_id]) REFERENCES dbo.[tc_events]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_CheckIns_Ticket')
BEGIN
    ALTER TABLE dbo.[tc_check_ins] WITH CHECK ADD CONSTRAINT [FK_CheckIns_Ticket] FOREIGN KEY ([ticket_id]) REFERENCES dbo.[tc_tickets]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_CommissionRules_Organization')
BEGIN
    ALTER TABLE dbo.[tc_commission_rules] WITH CHECK ADD CONSTRAINT [FK_CommissionRules_Organization] FOREIGN KEY ([organization_id]) REFERENCES dbo.[tc_organizations]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_CouponRedemptions_Coupon')
BEGIN
    ALTER TABLE dbo.[tc_coupon_redemptions] WITH CHECK ADD CONSTRAINT [FK_CouponRedemptions_Coupon] FOREIGN KEY ([coupon_id]) REFERENCES dbo.[tc_coupons]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_CouponRedemptions_Order')
BEGIN
    ALTER TABLE dbo.[tc_coupon_redemptions] WITH CHECK ADD CONSTRAINT [FK_CouponRedemptions_Order] FOREIGN KEY ([order_id]) REFERENCES dbo.[tc_orders]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Coupons_Organization')
BEGIN
    ALTER TABLE dbo.[tc_coupons] WITH CHECK ADD CONSTRAINT [FK_Coupons_Organization] FOREIGN KEY ([organization_id]) REFERENCES dbo.[tc_organizations]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Events_Category')
BEGIN
    ALTER TABLE dbo.[tc_events] WITH CHECK ADD CONSTRAINT [FK_Events_Category] FOREIGN KEY ([category_id]) REFERENCES dbo.[tc_event_categories]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Events_CommissionRule')
BEGIN
    ALTER TABLE dbo.[tc_events] WITH CHECK ADD CONSTRAINT [FK_Events_CommissionRule] FOREIGN KEY ([commission_rule_id]) REFERENCES dbo.[tc_commission_rules]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Events_Organization')
BEGIN
    ALTER TABLE dbo.[tc_events] WITH CHECK ADD CONSTRAINT [FK_Events_Organization] FOREIGN KEY ([organization_id]) REFERENCES dbo.[tc_organizations]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_OrderItems_Order')
BEGIN
    ALTER TABLE dbo.[tc_order_items] WITH CHECK ADD CONSTRAINT [FK_OrderItems_Order] FOREIGN KEY ([order_id]) REFERENCES dbo.[tc_orders]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_OrderItems_Seat')
BEGIN
    ALTER TABLE dbo.[tc_order_items] WITH CHECK ADD CONSTRAINT [FK_OrderItems_Seat] FOREIGN KEY ([seat_id]) REFERENCES dbo.[tc_seats]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_OrderItems_Zone')
BEGIN
    ALTER TABLE dbo.[tc_order_items] WITH CHECK ADD CONSTRAINT [FK_OrderItems_Zone] FOREIGN KEY ([zone_id]) REFERENCES dbo.[tc_zones]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Orders_AcceptedPayment')
BEGIN
    ALTER TABLE dbo.[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_AcceptedPayment] FOREIGN KEY ([accepted_payment_id]) REFERENCES dbo.[tc_payments]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Orders_Coupon')
BEGIN
    ALTER TABLE dbo.[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_Coupon] FOREIGN KEY ([coupon_id]) REFERENCES dbo.[tc_coupons]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Orders_Event')
BEGIN
    ALTER TABLE dbo.[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_Event] FOREIGN KEY ([event_id]) REFERENCES dbo.[tc_events]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Orders_Hold')
BEGIN
    ALTER TABLE dbo.[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_Hold] FOREIGN KEY ([hold_id]) REFERENCES dbo.[tc_ticket_holds]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Orders_User')
BEGIN
    ALTER TABLE dbo.[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_User] FOREIGN KEY ([user_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_OrganizationMemberships_Organization')
BEGIN
    ALTER TABLE dbo.[tc_organization_memberships] WITH CHECK ADD CONSTRAINT [FK_OrganizationMemberships_Organization] FOREIGN KEY ([organization_id]) REFERENCES dbo.[tc_organizations]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_OrganizationMemberships_User')
BEGIN
    ALTER TABLE dbo.[tc_organization_memberships] WITH CHECK ADD CONSTRAINT [FK_OrganizationMemberships_User] FOREIGN KEY ([user_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_OrganizationRequests_Applicant')
BEGIN
    ALTER TABLE dbo.[tc_organization_requests] WITH CHECK ADD CONSTRAINT [FK_OrganizationRequests_Applicant] FOREIGN KEY ([applicant_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_OrganizationRequests_Organization')
BEGIN
    ALTER TABLE dbo.[tc_organization_requests] WITH CHECK ADD CONSTRAINT [FK_OrganizationRequests_Organization] FOREIGN KEY ([organization_id]) REFERENCES dbo.[tc_organizations]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_OrganizationRequests_Reviewer')
BEGIN
    ALTER TABLE dbo.[tc_organization_requests] WITH CHECK ADD CONSTRAINT [FK_OrganizationRequests_Reviewer] FOREIGN KEY ([reviewer_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Organizations_Requester')
BEGIN
    ALTER TABLE dbo.[tc_organizations] WITH CHECK ADD CONSTRAINT [FK_Organizations_Requester] FOREIGN KEY ([requester_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Otps_User')
BEGIN
    ALTER TABLE dbo.[tc_otps] WITH CHECK ADD CONSTRAINT [FK_Otps_User] FOREIGN KEY ([user_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Payments_Order')
BEGIN
    ALTER TABLE dbo.[tc_payments] WITH CHECK ADD CONSTRAINT [FK_Payments_Order] FOREIGN KEY ([order_id]) REFERENCES dbo.[tc_orders]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Payouts_Settlement')
BEGIN
    ALTER TABLE dbo.[tc_payouts] WITH CHECK ADD CONSTRAINT [FK_Payouts_Settlement] FOREIGN KEY ([settlement_id]) REFERENCES dbo.[tc_settlements]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundObligations_LegacyRequest')
BEGIN
    ALTER TABLE dbo.[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [FK_RefundObligations_LegacyRequest] FOREIGN KEY ([legacy_request_id]) REFERENCES dbo.[tc_refund_requests]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundObligations_Order')
BEGIN
    ALTER TABLE dbo.[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [FK_RefundObligations_Order] FOREIGN KEY ([order_id]) REFERENCES dbo.[tc_orders]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundObligations_Payment')
BEGIN
    ALTER TABLE dbo.[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [FK_RefundObligations_Payment] FOREIGN KEY ([payment_id]) REFERENCES dbo.[tc_payments]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundRequestTickets_Request')
BEGIN
    ALTER TABLE dbo.[tc_refund_request_tickets] WITH CHECK ADD CONSTRAINT [FK_RefundRequestTickets_Request] FOREIGN KEY ([refund_request_id]) REFERENCES dbo.[tc_refund_requests]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundRequestTickets_Ticket')
BEGIN
    ALTER TABLE dbo.[tc_refund_request_tickets] WITH CHECK ADD CONSTRAINT [FK_RefundRequestTickets_Ticket] FOREIGN KEY ([ticket_id]) REFERENCES dbo.[tc_tickets]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundRequests_Order')
BEGIN
    ALTER TABLE dbo.[tc_refund_requests] WITH CHECK ADD CONSTRAINT [FK_RefundRequests_Order] FOREIGN KEY ([order_id]) REFERENCES dbo.[tc_orders]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundRequests_Requester')
BEGIN
    ALTER TABLE dbo.[tc_refund_requests] WITH CHECK ADD CONSTRAINT [FK_RefundRequests_Requester] FOREIGN KEY ([requester_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundRequests_Reviewer')
BEGIN
    ALTER TABLE dbo.[tc_refund_requests] WITH CHECK ADD CONSTRAINT [FK_RefundRequests_Reviewer] FOREIGN KEY ([reviewer_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundTickets_Refund')
BEGIN
    ALTER TABLE dbo.[tc_refund_tickets] WITH CHECK ADD CONSTRAINT [FK_RefundTickets_Refund] FOREIGN KEY ([refund_id]) REFERENCES dbo.[tc_refund_obligations]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundTickets_Ticket')
BEGIN
    ALTER TABLE dbo.[tc_refund_tickets] WITH CHECK ADD CONSTRAINT [FK_RefundTickets_Ticket] FOREIGN KEY ([ticket_id]) REFERENCES dbo.[tc_tickets]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_RefundTransferLogs_Refund')
BEGIN
    ALTER TABLE dbo.[tc_refund_transfer_logs] WITH CHECK ADD CONSTRAINT [FK_RefundTransferLogs_Refund] FOREIGN KEY ([refund_id]) REFERENCES dbo.[tc_refund_obligations]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Refunds_Payment')
BEGIN
    ALTER TABLE dbo.[tc_refunds] WITH CHECK ADD CONSTRAINT [FK_Refunds_Payment] FOREIGN KEY ([payment_id]) REFERENCES dbo.[tc_payments]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Refunds_Request')
BEGIN
    ALTER TABLE dbo.[tc_refunds] WITH CHECK ADD CONSTRAINT [FK_Refunds_Request] FOREIGN KEY ([refund_request_id]) REFERENCES dbo.[tc_refund_requests]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Seats_Zone')
BEGIN
    ALTER TABLE dbo.[tc_seats] WITH CHECK ADD CONSTRAINT [FK_Seats_Zone] FOREIGN KEY ([zone_id]) REFERENCES dbo.[tc_zones]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_SettlementItems_Order')
BEGIN
    ALTER TABLE dbo.[tc_settlement_items] WITH CHECK ADD CONSTRAINT [FK_SettlementItems_Order] FOREIGN KEY ([order_id]) REFERENCES dbo.[tc_orders]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_SettlementItems_Settlement')
BEGIN
    ALTER TABLE dbo.[tc_settlement_items] WITH CHECK ADD CONSTRAINT [FK_SettlementItems_Settlement] FOREIGN KEY ([settlement_id]) REFERENCES dbo.[tc_settlements]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_SettlementOrderSnapshots_Order')
BEGIN
    ALTER TABLE dbo.[tc_settlement_order_snapshots] WITH CHECK ADD CONSTRAINT [FK_SettlementOrderSnapshots_Order] FOREIGN KEY ([order_id]) REFERENCES dbo.[tc_orders]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_SettlementOrderSnapshots_Settlement')
BEGIN
    ALTER TABLE dbo.[tc_settlement_order_snapshots] WITH CHECK ADD CONSTRAINT [FK_SettlementOrderSnapshots_Settlement] FOREIGN KEY ([settlement_id]) REFERENCES dbo.[tc_settlements]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_SettlementTransferLogs_Settlement')
BEGIN
    ALTER TABLE dbo.[tc_settlement_transfer_logs] WITH CHECK ADD CONSTRAINT [FK_SettlementTransferLogs_Settlement] FOREIGN KEY ([settlement_id]) REFERENCES dbo.[tc_settlements]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Settlements_Event')
BEGIN
    ALTER TABLE dbo.[tc_settlements] WITH CHECK ADD CONSTRAINT [FK_Settlements_Event] FOREIGN KEY ([event_id]) REFERENCES dbo.[tc_events]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_TicketCheckInLogs_Actor')
BEGIN
    ALTER TABLE dbo.[tc_ticket_check_in_logs] WITH CHECK ADD CONSTRAINT [FK_TicketCheckInLogs_Actor] FOREIGN KEY ([actor_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_TicketCheckInLogs_Event')
BEGIN
    ALTER TABLE dbo.[tc_ticket_check_in_logs] WITH CHECK ADD CONSTRAINT [FK_TicketCheckInLogs_Event] FOREIGN KEY ([event_id]) REFERENCES dbo.[tc_events]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_TicketCheckInLogs_Ticket')
BEGIN
    ALTER TABLE dbo.[tc_ticket_check_in_logs] WITH CHECK ADD CONSTRAINT [FK_TicketCheckInLogs_Ticket] FOREIGN KEY ([ticket_id]) REFERENCES dbo.[tc_tickets]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_TicketHoldItems_Hold')
BEGIN
    ALTER TABLE dbo.[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [FK_TicketHoldItems_Hold] FOREIGN KEY ([hold_id]) REFERENCES dbo.[tc_ticket_holds]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_TicketHoldItems_Seat')
BEGIN
    ALTER TABLE dbo.[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [FK_TicketHoldItems_Seat] FOREIGN KEY ([seat_id]) REFERENCES dbo.[tc_seats]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_TicketHoldItems_Zone')
BEGIN
    ALTER TABLE dbo.[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [FK_TicketHoldItems_Zone] FOREIGN KEY ([zone_id]) REFERENCES dbo.[tc_zones]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_TicketHolds_Event')
BEGIN
    ALTER TABLE dbo.[tc_ticket_holds] WITH CHECK ADD CONSTRAINT [FK_TicketHolds_Event] FOREIGN KEY ([event_id]) REFERENCES dbo.[tc_events]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_TicketHolds_User')
BEGIN
    ALTER TABLE dbo.[tc_ticket_holds] WITH CHECK ADD CONSTRAINT [FK_TicketHolds_User] FOREIGN KEY ([user_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Tickets_OrderItem')
BEGIN
    ALTER TABLE dbo.[tc_tickets] WITH CHECK ADD CONSTRAINT [FK_Tickets_OrderItem] FOREIGN KEY ([order_item_id]) REFERENCES dbo.[tc_order_items]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_UserPlatformRoles_User')
BEGIN
    ALTER TABLE dbo.[tc_user_platform_roles] WITH CHECK ADD CONSTRAINT [FK_UserPlatformRoles_User] FOREIGN KEY ([user_id]) REFERENCES dbo.[tc_users]([id]);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Zones_Event')
BEGIN
    ALTER TABLE dbo.[tc_zones] WITH CHECK ADD CONSTRAINT [FK_Zones_Event] FOREIGN KEY ([event_id]) REFERENCES dbo.[tc_events]([id]);
END
GO
