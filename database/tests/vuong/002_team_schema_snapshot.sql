-- Schema-only snapshot from SQL Azure TicketCenterticke, 2026-10-10.
-- No accounts, application rows or credentials. Dedicated empty local test DB only.
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME() NOT LIKE 'TC[_]Vuong[_]Test[_]%' THROW 52790,'Dedicated test database required',1;
IF EXISTS(SELECT 1 FROM sys.tables WHERE is_ms_shipped=0) THROW 52791,'Empty database required',1;
GO
CREATE TABLE [dbo].[tc_audit_logs] (
 [id] bigint IDENTITY(1,1) NOT NULL,
 [actor_id] uniqueidentifier NULL,
 [action] varchar(100) NOT NULL,
 [aggregate_type] varchar(100) NOT NULL,
 [aggregate_id] uniqueidentifier NOT NULL,
 [detail] nvarchar(max) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_AuditLogs_CreatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_check_ins] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_CheckIns_Id] DEFAULT (newsequentialid()),
 [event_id] uniqueidentifier NOT NULL,
 [ticket_id] uniqueidentifier NULL,
 [actor_id] uniqueidentifier NOT NULL,
 [result] varchar(30) NOT NULL,
 [scanned_at] datetime2(3) NOT NULL CONSTRAINT [DF_CheckIns_ScannedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_commission_rules] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_CommissionRules_Id] DEFAULT (newsequentialid()),
 [organization_id] uniqueidentifier NOT NULL,
 [rate_percent] decimal(7,4) NOT NULL,
 [fixed_fee] decimal(19,0) NOT NULL,
 [effective_from] datetime2(3) NOT NULL,
 [effective_to] datetime2(3) NOT NULL
);
GO
CREATE TABLE [dbo].[tc_coupon_redemptions] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_CouponRedemptions_Id] DEFAULT (newsequentialid()),
 [coupon_id] uniqueidentifier NOT NULL,
 [order_id] uniqueidentifier NOT NULL,
 [status] varchar(20) NOT NULL CONSTRAINT [DF_CouponRedemptions_Status] DEFAULT ('RESERVED'),
 [reserved_at] datetime2(3) NOT NULL CONSTRAINT [DF_CouponRedemptions_ReservedAt] DEFAULT (sysutcdatetime()),
 [expires_at] datetime2(3) NOT NULL,
 [consumed_at] datetime2(3) NULL,
 [released_at] datetime2(3) NULL,
 [user_id] uniqueidentifier NULL
);
GO
CREATE TABLE [dbo].[tc_coupons] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Coupons_Id] DEFAULT (newsequentialid()),
 [organization_id] uniqueidentifier NOT NULL,
 [code] varchar(80) NOT NULL,
 [discount_type] varchar(30) NOT NULL,
 [percentage_value] decimal(5,2) NULL,
 [fixed_amount] decimal(19,0) NULL,
 [max_discount_amount] decimal(19,0) NULL,
 [max_uses] int NOT NULL,
 [valid_from] datetime2(3) NOT NULL,
 [valid_to] datetime2(3) NOT NULL,
 [active] bit NOT NULL CONSTRAINT [DF_Coupons_Active] DEFAULT ((1)),
 [version] bigint NOT NULL CONSTRAINT [DF_Coupons_Version] DEFAULT ((0)),
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Coupons_CreatedAt] DEFAULT (sysutcdatetime()),
 [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_Coupons_UpdatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_event_categories] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_EventCategories_Id] DEFAULT (newsequentialid()),
 [name] nvarchar(120) NOT NULL,
 [slug] varchar(120) NOT NULL
);
GO
CREATE TABLE [dbo].[tc_events] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Events_Id] DEFAULT (newsequentialid()),
 [organization_id] uniqueidentifier NOT NULL,
 [category_id] uniqueidentifier NOT NULL,
 [commission_rule_id] uniqueidentifier NULL,
 [title] nvarchar(250) NOT NULL,
 [description] nvarchar(max) NOT NULL,
 [cover_image_url] nvarchar(2048) NULL,
 [venue_name] nvarchar(250) NOT NULL,
 [venue_address] nvarchar(500) NOT NULL,
 [sale_start] datetime2(3) NOT NULL,
 [sale_end] datetime2(3) NOT NULL,
 [start_time] datetime2(3) NOT NULL,
 [end_time] datetime2(3) NOT NULL,
 [status] varchar(30) NOT NULL CONSTRAINT [DF_Events_Status] DEFAULT ('DRAFT'),
 [version] bigint NOT NULL CONSTRAINT [DF_Events_Version] DEFAULT ((0)),
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Events_CreatedAt] DEFAULT (sysutcdatetime()),
 [rejection_reason] nvarchar(1000) NULL,
 [event_image] varchar(2048) NULL,
 [event_image_public_id] varchar(255) NULL
);
GO
CREATE TABLE [dbo].[tc_order_items] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_OrderItems_Id] DEFAULT (newsequentialid()),
 [order_id] uniqueidentifier NOT NULL,
 [zone_id] uniqueidentifier NOT NULL,
 [seat_id] uniqueidentifier NULL,
 [quantity] int NOT NULL,
 [unit_price] decimal(19,0) NOT NULL,
 [zone_name_snapshot] nvarchar(200) NOT NULL,
 [seat_label_snapshot] nvarchar(100) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_OrderItems_CreatedAt] DEFAULT (sysutcdatetime()),
 [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_OrderItems_UpdatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_orders] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Orders_Id] DEFAULT (newsequentialid()),
 [user_id] uniqueidentifier NOT NULL,
 [event_id] uniqueidentifier NOT NULL,
 [hold_id] uniqueidentifier NOT NULL,
 [coupon_id] uniqueidentifier NULL,
 [order_code] varchar(64) NOT NULL,
 [subtotal_amount] decimal(19,0) NOT NULL,
 [discount_amount] decimal(19,0) NOT NULL,
 [total_amount] decimal(19,0) NOT NULL,
 [status] varchar(30) NOT NULL CONSTRAINT [DF_Orders_Status] DEFAULT ('PENDING_PAYMENT'),
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Orders_CreatedAt] DEFAULT (sysutcdatetime()),
 [paid_at] datetime2(3) NULL,
 [version] bigint NOT NULL CONSTRAINT [DF_Orders_Version] DEFAULT ((0)),
 [accepted_payment_id] uniqueidentifier NULL,
 [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_Orders_UpdatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_organization_memberships] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_OrganizationMemberships_Id] DEFAULT (newsequentialid()),
 [user_id] uniqueidentifier NOT NULL,
 [organization_id] uniqueidentifier NOT NULL,
 [role] varchar(30) NOT NULL,
 [active] bit NOT NULL CONSTRAINT [DF_OrganizationMemberships_Active] DEFAULT ((1)),
 [joined_at] datetime2(3) NOT NULL CONSTRAINT [DF_OrganizationMemberships_JoinedAt] DEFAULT (sysutcdatetime()),
 [version] bigint NOT NULL CONSTRAINT [DF_OrganizationMemberships_Version] DEFAULT ((0))
);
GO
CREATE TABLE [dbo].[tc_organization_requests] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_OrganizationRequests_Id] DEFAULT (newsequentialid()),
 [applicant_id] uniqueidentifier NOT NULL,
 [reviewer_id] uniqueidentifier NULL,
 [organization_id] uniqueidentifier NULL,
 [name] nvarchar(200) NOT NULL,
 [contact_email] nvarchar(320) NOT NULL,
 [contact_phone] nvarchar(30) NULL,
 [description] nvarchar(2000) NULL,
 [status] varchar(30) NOT NULL CONSTRAINT [DF_OrganizationRequest_Status] DEFAULT ('PENDING'),
 [requested_at] datetime2(3) NOT NULL CONSTRAINT [DF_OrganizationRequests_RequestedAt] DEFAULT (sysutcdatetime()),
 [decided_at] datetime2(3) NULL,
 [rejection_reason] nvarchar(1000) NULL,
 [version] bigint NOT NULL CONSTRAINT [DF_OrganizationRequests_Version] DEFAULT ((0))
);
GO
CREATE TABLE [dbo].[tc_organizations] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Organizations_Id] DEFAULT (newsequentialid()),
 [name] nvarchar(200) NOT NULL,
 [contact_email] nvarchar(320) NOT NULL,
 [contact_phone] nvarchar(30) NULL,
 [description] nvarchar(2000) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Organizations_CreatedAt] DEFAULT (sysutcdatetime()),
 [requester_id] uniqueidentifier NULL,
 [status] varchar(30) NOT NULL CONSTRAINT [DF_Organizations_Status] DEFAULT ('APPROVED'),
 [rejection_reason] nvarchar(2000) NULL,
 [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_Organizations_UpdatedAt] DEFAULT (sysutcdatetime()),
 [submitted_at] datetime2(3) NULL,
 [decided_at] datetime2(3) NULL
);
GO
CREATE TABLE [dbo].[tc_otps] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Otps_Id] DEFAULT (newsequentialid()),
 [user_id] uniqueidentifier NULL,
 [email_normalized] nvarchar(320) NOT NULL,
 [purpose] varchar(30) NOT NULL,
 [secret_hash] varbinary(64) NOT NULL,
 [expires_at] datetime2(3) NOT NULL,
 [failed_attempts] tinyint NOT NULL CONSTRAINT [DF_Otps_FailedAttempts] DEFAULT ((0)),
 [consumed_at] datetime2(3) NULL,
 [invalidated_at] datetime2(3) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Otps_CreatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_outbox] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Outbox_Id] DEFAULT (newsequentialid()),
 [event_type] varchar(120) NOT NULL,
 [aggregate_type] varchar(100) NOT NULL,
 [aggregate_id] uniqueidentifier NOT NULL,
 [payload] nvarchar(max) NOT NULL,
 [idempotency_key] varchar(200) NOT NULL,
 [status] varchar(20) NOT NULL CONSTRAINT [DF_Outbox_Status] DEFAULT ('PENDING'),
 [available_at] datetime2(3) NOT NULL CONSTRAINT [DF_Outbox_AvailableAt] DEFAULT (sysutcdatetime()),
 [lease_owner] varchar(120) NULL,
 [lease_until] datetime2(3) NULL,
 [attempts] int NOT NULL CONSTRAINT [DF_Outbox_Attempts] DEFAULT ((0)),
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Outbox_CreatedAt] DEFAULT (sysutcdatetime()),
 [published_at] datetime2(3) NULL
);
GO
CREATE TABLE [dbo].[tc_payments] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Payments_Id] DEFAULT (newsequentialid()),
 [order_id] uniqueidentifier NOT NULL,
 [txn_ref] varchar(100) NOT NULL,
 [amount] decimal(19,0) NOT NULL,
 [currency] char(3) NOT NULL CONSTRAINT [DF_Payments_Currency] DEFAULT ('VND'),
 [status] varchar(20) NOT NULL CONSTRAINT [DF_Payments_Status] DEFAULT ('PENDING'),
 [provider_reference] nvarchar(200) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Payments_CreatedAt] DEFAULT (sysutcdatetime()),
 [captured_at] datetime2(3) NULL,
 [version] bigint NOT NULL CONSTRAINT [DF_Payments_Version] DEFAULT ((0))
);
GO
CREATE TABLE [dbo].[tc_payouts] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Payouts_Id] DEFAULT (newsequentialid()),
 [settlement_id] uniqueidentifier NOT NULL,
 [amount] decimal(19,0) NOT NULL,
 [reference] varchar(100) NOT NULL,
 [status] varchar(20) NOT NULL CONSTRAINT [DF_Payouts_Status] DEFAULT ('PENDING'),
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Payouts_CreatedAt] DEFAULT (sysutcdatetime()),
 [paid_at] datetime2(3) NULL,
 [version] bigint NOT NULL CONSTRAINT [DF_Payouts_Version] DEFAULT ((0))
);
GO
CREATE TABLE [dbo].[tc_refund_obligations] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_RefundObligations_Id] DEFAULT (newsequentialid()),
 [legacy_request_id] uniqueidentifier NULL,
 [order_id] uniqueidentifier NOT NULL,
 [payment_id] uniqueidentifier NULL,
 [amount] decimal(19,0) NOT NULL,
 [purpose] varchar(30) NOT NULL,
 [reason_type] varchar(30) NULL,
 [reason] nvarchar(2000) NULL,
 [rejection_reason] nvarchar(2000) NULL,
 [status] varchar(40) NOT NULL,
 [current_attempt_id] uniqueidentifier NULL,
 [provider_reference] nvarchar(200) NULL,
 [processed_at] datetime2(3) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundObligations_CreatedAt] DEFAULT (sysutcdatetime()),
 [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundObligations_UpdatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_refund_request_tickets] (
 [refund_request_id] uniqueidentifier NOT NULL,
 [ticket_id] uniqueidentifier NOT NULL,
 [is_open] bit NOT NULL CONSTRAINT [DF_RefundRequestTickets_IsOpen] DEFAULT ((1)),
 [added_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundRequestTickets_AddedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_refund_requests] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_RefundRequests_Id] DEFAULT (newsequentialid()),
 [order_id] uniqueidentifier NOT NULL,
 [requester_id] uniqueidentifier NOT NULL,
 [reviewer_id] uniqueidentifier NULL,
 [reason] nvarchar(2000) NOT NULL,
 [reason_type] varchar(30) NOT NULL,
 [status] varchar(20) NOT NULL CONSTRAINT [DF_RefundRequests_Status] DEFAULT ('PENDING'),
 [requested_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundRequests_RequestedAt] DEFAULT (sysutcdatetime()),
 [decided_at] datetime2(3) NULL,
 [rejection_reason] nvarchar(1000) NULL,
 [version] bigint NOT NULL CONSTRAINT [DF_RefundRequests_Version] DEFAULT ((0))
);
GO
CREATE TABLE [dbo].[tc_refund_tickets] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_RefundTickets_Id] DEFAULT (newsequentialid()),
 [refund_id] uniqueidentifier NOT NULL,
 [ticket_id] uniqueidentifier NOT NULL,
 [is_open] bit NOT NULL CONSTRAINT [DF_RefundTickets_IsOpen] DEFAULT ((1)),
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundTickets_CreatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_refund_transfer_logs] (
 [id] uniqueidentifier NOT NULL,
 [refund_id] uniqueidentifier NOT NULL,
 [attempt_no] int NOT NULL,
 [status] varchar(30) NOT NULL,
 [result] varchar(30) NOT NULL,
 [provider_reference] nvarchar(200) NULL,
 [processed_at] datetime2(3) NULL,
 [failure_reason] nvarchar(2000) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundTransferLogs_CreatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_refunds] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Refunds_Id] DEFAULT (newsequentialid()),
 [refund_request_id] uniqueidentifier NULL,
 [payment_id] uniqueidentifier NOT NULL,
 [purpose] varchar(30) NOT NULL,
 [amount] decimal(19,0) NOT NULL,
 [status] varchar(20) NOT NULL CONSTRAINT [DF_Refunds_Status] DEFAULT ('PENDING'),
 [provider_reference] nvarchar(200) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Refunds_CreatedAt] DEFAULT (sysutcdatetime()),
 [processed_at] datetime2(3) NULL,
 [version] bigint NOT NULL CONSTRAINT [DF_Refunds_Version] DEFAULT ((0))
);
GO
CREATE TABLE [dbo].[tc_request_idempotency] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_RequestIdempotency_Id] DEFAULT (newsequentialid()),
 [actor_id] uniqueidentifier NOT NULL,
 [operation] varchar(50) NOT NULL,
 [idempotency_key] varchar(100) NOT NULL,
 [target_id] uniqueidentifier NOT NULL,
 [fingerprint] varchar(200) NOT NULL,
 [response_payload] nvarchar(max) NOT NULL,
 [status] varchar(20) NOT NULL CONSTRAINT [DF_RequestIdempotency_Status] DEFAULT ('COMPLETED'),
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_RequestIdempotency_CreatedAt] DEFAULT (sysutcdatetime()),
 [expires_at] datetime2(3) NOT NULL
);
GO
CREATE TABLE [dbo].[tc_schema_migrations] (
 [version] varchar(100) NOT NULL,
 [checksum_sha256] char(64) NOT NULL,
 [applied_at] datetime2(3) NOT NULL CONSTRAINT [DF__tc_schema__appli__6FE99F9F] DEFAULT (sysutcdatetime()),
 [applied_by] sysname NOT NULL CONSTRAINT [DF__tc_schema__appli__70DDC3D8] DEFAULT (original_login())
);
GO
CREATE TABLE [dbo].[tc_seats] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Seats_Id] DEFAULT (newsequentialid()),
 [zone_id] uniqueidentifier NOT NULL,
 [row_name] nvarchar(20) NOT NULL,
 [seat_number] int NOT NULL,
 [label] nvarchar(50) NOT NULL,
 [status] varchar(20) NOT NULL CONSTRAINT [DF_Seats_Status] DEFAULT ('AVAILABLE'),
 [version] bigint NOT NULL CONSTRAINT [DF_Seats_Version] DEFAULT ((0))
);
GO
CREATE TABLE [dbo].[tc_settlement_items] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_SettlementItems_Id] DEFAULT (newsequentialid()),
 [settlement_id] uniqueidentifier NOT NULL,
 [order_id] uniqueidentifier NOT NULL,
 [gross_amount] decimal(19,0) NOT NULL,
 [refund_amount] decimal(19,0) NOT NULL,
 [commission_amount] decimal(19,0) NOT NULL,
 [net_amount] decimal(19,0) NOT NULL
);
GO
CREATE TABLE [dbo].[tc_settlement_order_snapshots] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_SettlementOrderSnapshots_Id] DEFAULT (newsequentialid()),
 [settlement_id] uniqueidentifier NOT NULL,
 [order_id] uniqueidentifier NOT NULL,
 [gross_amount] decimal(19,0) NOT NULL,
 [refund_amount] decimal(19,0) NOT NULL,
 [commission_amount] decimal(19,0) NOT NULL,
 [net_amount] decimal(19,0) NOT NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_SettlementOrderSnapshots_CreatedAt] DEFAULT (sysutcdatetime()),
 [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_SettlementOrderSnapshots_UpdatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_settlement_transfer_logs] (
 [id] uniqueidentifier NOT NULL,
 [settlement_id] uniqueidentifier NOT NULL,
 [amount] decimal(19,0) NOT NULL,
 [status] varchar(30) NOT NULL,
 [result] varchar(30) NOT NULL,
 [provider_reference] nvarchar(200) NULL,
 [processed_at] datetime2(3) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_SettlementTransferLogs_CreatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_settlements] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Settlements_Id] DEFAULT (newsequentialid()),
 [event_id] uniqueidentifier NOT NULL,
 [gross_revenue] decimal(19,0) NOT NULL CONSTRAINT [DF_Settlements_GrossRevenue] DEFAULT ((0)),
 [total_refund] decimal(19,0) NOT NULL CONSTRAINT [DF_Settlements_TotalRefund] DEFAULT ((0)),
 [total_commission] decimal(19,0) NOT NULL CONSTRAINT [DF_Settlements_TotalCommission] DEFAULT ((0)),
 [net_payable] decimal(19,0) NOT NULL CONSTRAINT [DF_Settlements_NetPayable] DEFAULT ((0)),
 [status] varchar(20) NOT NULL CONSTRAINT [DF_Settlements_Status] DEFAULT ('DRAFT'),
 [confirmed_at] datetime2(3) NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Settlements_CreatedAt] DEFAULT (sysutcdatetime()),
 [version] bigint NOT NULL CONSTRAINT [DF_Settlements_Version] DEFAULT ((0)),
 [paid_amount] decimal(19,0) NOT NULL CONSTRAINT [DF_Settlements_PaidAmount] DEFAULT ((0)),
 [pending_amount] decimal(19,0) NOT NULL CONSTRAINT [DF_Settlements_PendingAmount] DEFAULT ((0)),
 [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_Settlements_UpdatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_ticket_check_in_logs] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_TicketCheckInLogs_Id] DEFAULT (newsequentialid()),
 [event_id] uniqueidentifier NOT NULL,
 [ticket_id] uniqueidentifier NULL,
 [actor_id] uniqueidentifier NOT NULL,
 [result] varchar(30) NOT NULL,
 [scanned_at] datetime2(3) NOT NULL CONSTRAINT [DF_TicketCheckInLogs_ScannedAt] DEFAULT (sysutcdatetime()),
 [failure_reason] nvarchar(2000) NULL
);
GO
CREATE TABLE [dbo].[tc_ticket_hold_items] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_TicketHoldItems_Id] DEFAULT (newsequentialid()),
 [hold_id] uniqueidentifier NOT NULL,
 [zone_id] uniqueidentifier NOT NULL,
 [seat_id] uniqueidentifier NULL,
 [quantity] int NOT NULL,
 [unit_price] decimal(19,0) NOT NULL,
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_TicketHoldItems_CreatedAt] DEFAULT (sysutcdatetime()),
 [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_TicketHoldItems_UpdatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_ticket_holds] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_TicketHolds_Id] DEFAULT (newsequentialid()),
 [user_id] uniqueidentifier NOT NULL,
 [event_id] uniqueidentifier NOT NULL,
 [status] varchar(20) NOT NULL CONSTRAINT [DF_TicketHolds_Status] DEFAULT ('ACTIVE'),
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_TicketHolds_CreatedAt] DEFAULT (sysutcdatetime()),
 [expires_at] datetime2(3) NOT NULL,
 [version] bigint NOT NULL CONSTRAINT [DF_TicketHolds_Version] DEFAULT ((0)),
 [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_TicketHolds_UpdatedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_tickets] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Tickets_Id] DEFAULT (newsequentialid()),
 [order_item_id] uniqueidentifier NOT NULL,
 [ticket_code] varchar(100) NOT NULL,
 [qr_secret_hash] varbinary(64) NOT NULL,
 [paid_amount] decimal(19,0) NOT NULL,
 [status] varchar(30) NOT NULL CONSTRAINT [DF_Tickets_Status] DEFAULT ('ACTIVE'),
 [issued_at] datetime2(3) NOT NULL CONSTRAINT [DF_Tickets_IssuedAt] DEFAULT (sysutcdatetime()),
 [version] bigint NOT NULL CONSTRAINT [DF_Tickets_Version] DEFAULT ((0))
);
GO
CREATE TABLE [dbo].[tc_user_platform_roles] (
 [user_id] uniqueidentifier NOT NULL,
 [role] varchar(30) NOT NULL,
 [granted_at] datetime2(3) NOT NULL CONSTRAINT [DF_UserPlatformRoles_GrantedAt] DEFAULT (sysutcdatetime())
);
GO
CREATE TABLE [dbo].[tc_users] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Users_Id] DEFAULT (newsequentialid()),
 [email] nvarchar(320) NOT NULL,
 [normalized_email] nvarchar(320) NULL,
 [password_hash] nvarchar(255) NOT NULL,
 [full_name] nvarchar(200) NOT NULL,
 [phone] nvarchar(30) NULL,
 [status] varchar(20) NOT NULL CONSTRAINT [DF_Users_Status] DEFAULT ('ACTIVE'),
 [email_verified_at] datetime2(3) NULL,
 [auth_version] int NOT NULL CONSTRAINT [DF_Users_AuthVersion] DEFAULT ((0)),
 [version] bigint NOT NULL CONSTRAINT [DF_Users_Version] DEFAULT ((0)),
 [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Users_CreatedAt] DEFAULT (sysutcdatetime()),
 [avatar] varchar(2048) NULL,
 [avatar_public_id] varchar(255) NULL,
 [user_name] nvarchar(100) NOT NULL,
 [platform_role] nvarchar(20) NOT NULL CONSTRAINT [DF_tc_users_platform_role] DEFAULT ('CUSTOMER'),
 [updated_at] datetime2(6) NULL
);
GO
CREATE TABLE [dbo].[tc_zones] (
 [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Zones_Id] DEFAULT (newsequentialid()),
 [event_id] uniqueidentifier NOT NULL,
 [name] nvarchar(120) NOT NULL,
 [type] varchar(20) NOT NULL,
 [price] decimal(19,0) NOT NULL,
 [capacity] int NULL,
 [held_quantity] int NOT NULL CONSTRAINT [DF_Zones_HeldQuantity] DEFAULT ((0)),
 [sold_quantity] int NOT NULL CONSTRAINT [DF_Zones_SoldQuantity] DEFAULT ((0)),
 [version] bigint NOT NULL CONSTRAINT [DF_Zones_Version] DEFAULT ((0))
);
GO
CREATE NONCLUSTERED INDEX [IX_AuditLog_Aggregate_CreatedAt] ON [dbo].[tc_audit_logs] ([aggregate_type] ASC,[aggregate_id] ASC,[created_at] DESC,[id] ASC) INCLUDE ([action]);
ALTER TABLE [dbo].[tc_audit_logs] ADD CONSTRAINT [PK_AuditLogs] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE NONCLUSTERED INDEX [IX_CheckIn_Event_ScannedAt] ON [dbo].[tc_check_ins] ([event_id] ASC,[scanned_at] DESC,[id] ASC) INCLUDE ([result],[ticket_id]);
ALTER TABLE [dbo].[tc_check_ins] ADD CONSTRAINT [PK_CheckIns] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE NONCLUSTERED INDEX [IX_CommissionRule_Organization_EffectiveFrom] ON [dbo].[tc_commission_rules] ([organization_id] ASC,[effective_from] ASC,[id] ASC) INCLUDE ([effective_to],[rate_percent],[fixed_fee]);
ALTER TABLE [dbo].[tc_commission_rules] ADD CONSTRAINT [PK_CommissionRules] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE NONCLUSTERED INDEX [IX_CouponRedemption_Coupon_Status] ON [dbo].[tc_coupon_redemptions] ([coupon_id] ASC,[status] ASC,[order_id] ASC);
CREATE NONCLUSTERED INDEX [IX_CouponRedemptions_CouponStatus] ON [dbo].[tc_coupon_redemptions] ([coupon_id] ASC,[status] ASC);
CREATE NONCLUSTERED INDEX [IX_CouponRedemptions_User] ON [dbo].[tc_coupon_redemptions] ([user_id] ASC);
ALTER TABLE [dbo].[tc_coupon_redemptions] ADD CONSTRAINT [PK_CouponRedemptions] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_coupon_redemptions] ADD CONSTRAINT [UQ_CouponRedemptions_Order] UNIQUE NONCLUSTERED ([order_id] ASC);
ALTER TABLE [dbo].[tc_coupons] ADD CONSTRAINT [PK_Coupons] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_coupons] ADD CONSTRAINT [UQ_Coupons_Organization_Code] UNIQUE NONCLUSTERED ([organization_id] ASC,[code] ASC);
ALTER TABLE [dbo].[tc_event_categories] ADD CONSTRAINT [PK_EventCategories] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_event_categories] ADD CONSTRAINT [UQ_EventCategories_Slug] UNIQUE NONCLUSTERED ([slug] ASC);
CREATE NONCLUSTERED INDEX [IX_Event_Organization_Status_StartTime] ON [dbo].[tc_events] ([organization_id] ASC,[status] ASC,[start_time] ASC,[id] ASC) INCLUDE ([title],[category_id]);
CREATE NONCLUSTERED INDEX [IX_Event_Status_StartTime] ON [dbo].[tc_events] ([status] ASC,[start_time] ASC,[id] ASC) INCLUDE ([category_id],[organization_id],[title],[cover_image_url]);
ALTER TABLE [dbo].[tc_events] ADD CONSTRAINT [PK_Events] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_order_items] ADD CONSTRAINT [PK_OrderItems] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE UNIQUE NONCLUSTERED INDEX [UX_OrderItems_Seat] ON [dbo].[tc_order_items] ([order_id] ASC,[seat_id] ASC) WHERE ([seat_id] IS NOT NULL);
CREATE NONCLUSTERED INDEX [IX_Order_Event_Status_PaidAt] ON [dbo].[tc_orders] ([event_id] ASC,[status] ASC,[paid_at] ASC,[id] ASC) INCLUDE ([user_id],[total_amount]);
CREATE NONCLUSTERED INDEX [IX_Order_User_CreatedAt] ON [dbo].[tc_orders] ([user_id] ASC,[created_at] DESC,[id] ASC) INCLUDE ([event_id],[status],[total_amount]);
ALTER TABLE [dbo].[tc_orders] ADD CONSTRAINT [PK_Orders] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_orders] ADD CONSTRAINT [UQ_Orders_Hold] UNIQUE NONCLUSTERED ([hold_id] ASC);
ALTER TABLE [dbo].[tc_orders] ADD CONSTRAINT [UQ_Orders_OrderCode] UNIQUE NONCLUSTERED ([order_code] ASC);
ALTER TABLE [dbo].[tc_organization_memberships] ADD CONSTRAINT [PK_OrganizationMemberships] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_organization_memberships] ADD CONSTRAINT [UQ_Membership_User_Organization] UNIQUE NONCLUSTERED ([user_id] ASC,[organization_id] ASC);
ALTER TABLE [dbo].[tc_organization_requests] ADD CONSTRAINT [PK_OrganizationRequests] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_organizations] ADD CONSTRAINT [PK_Organizations] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE NONCLUSTERED INDEX [IX_Otps_UserPurposeCreated] ON [dbo].[tc_otps] ([user_id] ASC,[purpose] ASC,[created_at] DESC);
ALTER TABLE [dbo].[tc_otps] ADD CONSTRAINT [PK_Otps] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE NONCLUSTERED INDEX [IX_Outbox_Claim] ON [dbo].[tc_outbox] ([status] ASC,[available_at] ASC,[lease_until] ASC);
ALTER TABLE [dbo].[tc_outbox] ADD CONSTRAINT [PK_Outbox] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_outbox] ADD CONSTRAINT [UQ_Outbox_IdempotencyKey] UNIQUE NONCLUSTERED ([idempotency_key] ASC);
CREATE NONCLUSTERED INDEX [IX_Payment_Status_CreatedAt] ON [dbo].[tc_payments] ([status] ASC,[created_at] ASC,[id] ASC) INCLUDE ([order_id],[txn_ref]);
ALTER TABLE [dbo].[tc_payments] ADD CONSTRAINT [PK_Payments] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_payments] ADD CONSTRAINT [UQ_Payment_TxnRef] UNIQUE NONCLUSTERED ([txn_ref] ASC);
CREATE NONCLUSTERED INDEX [IX_Payout_Settlement_Status] ON [dbo].[tc_payouts] ([settlement_id] ASC,[status] ASC,[id] ASC) INCLUDE ([amount]);
ALTER TABLE [dbo].[tc_payouts] ADD CONSTRAINT [PK_Payouts] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_payouts] ADD CONSTRAINT [UQ_Payouts_Reference] UNIQUE NONCLUSTERED ([reference] ASC);
CREATE NONCLUSTERED INDEX [IX_Refund_Status_CreatedAt] ON [dbo].[tc_refund_obligations] ([status] ASC,[created_at] ASC,[id] ASC) INCLUDE ([order_id],[purpose],[reason_type]);
ALTER TABLE [dbo].[tc_refund_obligations] ADD CONSTRAINT [PK_RefundObligations] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE UNIQUE NONCLUSTERED INDEX [UX_RefundObligations_LegacyRequest] ON [dbo].[tc_refund_obligations] ([legacy_request_id] ASC) WHERE ([legacy_request_id] IS NOT NULL);
ALTER TABLE [dbo].[tc_refund_request_tickets] ADD CONSTRAINT [PK_RefundRequestTickets] PRIMARY KEY CLUSTERED ([refund_request_id] ASC,[ticket_id] ASC);
CREATE UNIQUE NONCLUSTERED INDEX [UX_RefundRequestTickets_OneOpenPerTicket] ON [dbo].[tc_refund_request_tickets] ([ticket_id] ASC) WHERE ([is_open]=(1));
CREATE NONCLUSTERED INDEX [IX_RefundRequest_Status_RequestedAt] ON [dbo].[tc_refund_requests] ([status] ASC,[requested_at] ASC,[id] ASC) INCLUDE ([order_id],[reason_type]);
ALTER TABLE [dbo].[tc_refund_requests] ADD CONSTRAINT [PK_RefundRequests] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_refund_tickets] ADD CONSTRAINT [PK_RefundTickets] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_refund_tickets] ADD CONSTRAINT [UQ_RefundTickets_RefundTicket] UNIQUE NONCLUSTERED ([refund_id] ASC,[ticket_id] ASC);
CREATE UNIQUE NONCLUSTERED INDEX [UX_RefundTickets_OneOpenPerTicket] ON [dbo].[tc_refund_tickets] ([ticket_id] ASC) WHERE ([is_open]=(1));
CREATE NONCLUSTERED INDEX [IX_RefundTransferLog_Status_CreatedAt] ON [dbo].[tc_refund_transfer_logs] ([status] ASC,[created_at] ASC,[id] ASC) INCLUDE ([refund_id],[provider_reference]);
ALTER TABLE [dbo].[tc_refund_transfer_logs] ADD CONSTRAINT [PK_RefundTransferLogs] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_refund_transfer_logs] ADD CONSTRAINT [UQ_RefundTransferLogs_RefundAttempt] UNIQUE NONCLUSTERED ([refund_id] ASC,[attempt_no] ASC);
CREATE NONCLUSTERED INDEX [IX_Refund_Status_CreatedAt] ON [dbo].[tc_refunds] ([status] ASC,[created_at] ASC,[id] ASC) INCLUDE ([amount],[provider_reference],[payment_id],[purpose],[refund_request_id]);
ALTER TABLE [dbo].[tc_refunds] ADD CONSTRAINT [PK_Refunds] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE NONCLUSTERED INDEX [IX_RequestIdempotency_Expires] ON [dbo].[tc_request_idempotency] ([expires_at] ASC);
ALTER TABLE [dbo].[tc_request_idempotency] ADD CONSTRAINT [PK_RequestIdempotency] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_request_idempotency] ADD CONSTRAINT [UQ_RequestIdempotency_Key] UNIQUE NONCLUSTERED ([actor_id] ASC,[operation] ASC,[idempotency_key] ASC);
ALTER TABLE [dbo].[tc_schema_migrations] ADD CONSTRAINT [PK__tc_schem__79B5C94C19A639FF] PRIMARY KEY CLUSTERED ([version] ASC);
CREATE NONCLUSTERED INDEX [IX_Seat_Zone_Status] ON [dbo].[tc_seats] ([zone_id] ASC,[status] ASC,[id] ASC) INCLUDE ([row_name],[seat_number]);
ALTER TABLE [dbo].[tc_seats] ADD CONSTRAINT [PK_Seats] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_seats] ADD CONSTRAINT [UQ_Seat_Zone_Row_Number] UNIQUE NONCLUSTERED ([zone_id] ASC,[row_name] ASC,[seat_number] ASC);
ALTER TABLE [dbo].[tc_seats] ADD CONSTRAINT [UQ_Seats_Zone_Label] UNIQUE NONCLUSTERED ([zone_id] ASC,[label] ASC);
ALTER TABLE [dbo].[tc_settlement_items] ADD CONSTRAINT [PK_SettlementItems] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_settlement_items] ADD CONSTRAINT [UQ_SettlementItems_Order] UNIQUE NONCLUSTERED ([order_id] ASC);
ALTER TABLE [dbo].[tc_settlement_order_snapshots] ADD CONSTRAINT [PK_SettlementOrderSnapshots] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_settlement_order_snapshots] ADD CONSTRAINT [UQ_SettlementOrderSnapshots_SettlementOrder] UNIQUE NONCLUSTERED ([settlement_id] ASC,[order_id] ASC);
CREATE NONCLUSTERED INDEX [IX_Payout_Settlement_Status] ON [dbo].[tc_settlement_transfer_logs] ([settlement_id] ASC,[status] ASC,[id] ASC) INCLUDE ([amount]);
ALTER TABLE [dbo].[tc_settlement_transfer_logs] ADD CONSTRAINT [PK_SettlementTransferLogs] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_settlements] ADD CONSTRAINT [PK_Settlements] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_settlements] ADD CONSTRAINT [UQ_Settlements_Event] UNIQUE NONCLUSTERED ([event_id] ASC);
CREATE NONCLUSTERED INDEX [IX_TicketCheckInLog_Event_ScannedAt] ON [dbo].[tc_ticket_check_in_logs] ([event_id] ASC,[scanned_at] DESC,[id] ASC) INCLUDE ([result],[ticket_id]);
ALTER TABLE [dbo].[tc_ticket_check_in_logs] ADD CONSTRAINT [PK_TicketCheckInLogs] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_ticket_hold_items] ADD CONSTRAINT [PK_TicketHoldItems] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE UNIQUE NONCLUSTERED INDEX [UX_TicketHoldItems_Seat] ON [dbo].[tc_ticket_hold_items] ([hold_id] ASC,[seat_id] ASC) WHERE ([seat_id] IS NOT NULL);
CREATE NONCLUSTERED INDEX [IX_TicketHold_Status_ExpiresAt] ON [dbo].[tc_ticket_holds] ([status] ASC,[expires_at] ASC,[id] ASC) INCLUDE ([user_id],[event_id]);
ALTER TABLE [dbo].[tc_ticket_holds] ADD CONSTRAINT [PK_TicketHolds] PRIMARY KEY CLUSTERED ([id] ASC);
CREATE UNIQUE NONCLUSTERED INDEX [UX_TicketHolds_OneActivePerUser] ON [dbo].[tc_ticket_holds] ([user_id] ASC) WHERE ([status]='ACTIVE');
CREATE NONCLUSTERED INDEX [IX_Ticket_OrderItem_Status] ON [dbo].[tc_tickets] ([order_item_id] ASC,[status] ASC,[id] ASC) INCLUDE ([paid_amount]);
ALTER TABLE [dbo].[tc_tickets] ADD CONSTRAINT [PK_Tickets] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_tickets] ADD CONSTRAINT [UQ_Tickets_TicketCode] UNIQUE NONCLUSTERED ([ticket_code] ASC);
ALTER TABLE [dbo].[tc_user_platform_roles] ADD CONSTRAINT [PK_UserPlatformRoles] PRIMARY KEY CLUSTERED ([user_id] ASC,[role] ASC);
ALTER TABLE [dbo].[tc_users] ADD CONSTRAINT [PK_Users] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_users] ADD CONSTRAINT [UQ_User_NormalizedEmail] UNIQUE NONCLUSTERED ([normalized_email] ASC);
ALTER TABLE [dbo].[tc_users] ADD CONSTRAINT [UQ_User_NormalizedUserName] UNIQUE NONCLUSTERED ([user_name] ASC);
ALTER TABLE [dbo].[tc_zones] ADD CONSTRAINT [PK_Zones] PRIMARY KEY CLUSTERED ([id] ASC);
ALTER TABLE [dbo].[tc_zones] ADD CONSTRAINT [UQ_Zones_Event_Name] UNIQUE NONCLUSTERED ([event_id] ASC,[name] ASC);
ALTER TABLE [dbo].[tc_check_ins] WITH CHECK ADD CONSTRAINT [CK_CheckIns_Result] CHECK ([result]='EVENT_CANCELLED' OR [result]='NOT_ACTIVE' OR [result]='ALREADY_USED' OR [result]='TOO_LATE' OR [result]='TOO_EARLY' OR [result]='WRONG_EVENT' OR [result]='NOT_FOUND' OR [result]='SUCCESS');
ALTER TABLE [dbo].[tc_commission_rules] WITH CHECK ADD CONSTRAINT [CK_CommissionRule_Terms] CHECK ([rate_percent]>=(0) AND [fixed_fee]>=(0) AND [effective_from]<[effective_to]);
ALTER TABLE [dbo].[tc_coupons] WITH CHECK ADD CONSTRAINT [CK_Coupon_Discount_Limit] CHECK ([max_uses]>(0) AND [valid_from]<[valid_to] AND ([max_discount_amount] IS NULL OR [max_discount_amount]>=(0)) AND ([discount_type]='PERCENTAGE' AND [percentage_value] IS NOT NULL AND [percentage_value]>(0) AND [percentage_value]<=(30) AND [fixed_amount] IS NULL OR [discount_type]='FIXED_AMOUNT' AND [fixed_amount] IS NOT NULL AND [fixed_amount]>(0) AND [percentage_value] IS NULL));
ALTER TABLE [dbo].[tc_coupon_redemptions] WITH CHECK ADD CONSTRAINT [CK_CouponRedemptions_Status] CHECK ([status]='RELEASED' OR [status]='CONSUMED' OR [status]='RESERVED');
ALTER TABLE [dbo].[tc_coupon_redemptions] WITH CHECK ADD CONSTRAINT [CK_CouponRedemptions_Time] CHECK ([expires_at]>[reserved_at]);
ALTER TABLE [dbo].[tc_coupons] WITH CHECK ADD CONSTRAINT [CK_Coupons_DiscountType] CHECK ([discount_type]='FIXED_AMOUNT' OR [discount_type]='PERCENTAGE');
ALTER TABLE [dbo].[tc_events] WITH CHECK ADD CONSTRAINT [CK_Event_TimeRange] CHECK ([sale_start]<[sale_end] AND [sale_end]<=[start_time] AND [start_time]<[end_time]);
ALTER TABLE [dbo].[tc_events] WITH CHECK ADD CONSTRAINT [CK_Events_Status] CHECK ([status]='CANCELLED' OR [status]='PUBLISHED' OR [status]='REJECTED' OR [status]='PENDING_APPROVAL' OR [status]='DRAFT');
ALTER TABLE [dbo].[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [CK_HoldItem_Seat_Quantity] CHECK ([seat_id] IS NOT NULL AND [quantity]=(1) OR [seat_id] IS NULL AND [quantity]>(0));
ALTER TABLE [dbo].[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [CK_HoldItem_UnitPrice] CHECK ([unit_price]>=(0));
ALTER TABLE [dbo].[tc_orders] WITH CHECK ADD CONSTRAINT [CK_Order_Amounts] CHECK ([subtotal_amount]>=(0) AND [discount_amount]>=(0) AND [total_amount]>=(0) AND [total_amount]=([subtotal_amount]-[discount_amount]) AND [discount_amount]*(10)<=[subtotal_amount]*(3));
ALTER TABLE [dbo].[tc_order_items] WITH CHECK ADD CONSTRAINT [CK_OrderItem_Seat_Quantity] CHECK ([seat_id] IS NOT NULL AND [quantity]=(1) OR [seat_id] IS NULL AND [quantity]>(0));
ALTER TABLE [dbo].[tc_order_items] WITH CHECK ADD CONSTRAINT [CK_OrderItem_UnitPrice] CHECK ([unit_price]>=(0));
ALTER TABLE [dbo].[tc_orders] WITH CHECK ADD CONSTRAINT [CK_Orders_Status] CHECK ([status]='EXPIRED' OR [status]='CANCELLED' OR [status]='PAID' OR [status]='PENDING_PAYMENT');
ALTER TABLE [dbo].[tc_organization_memberships] WITH CHECK ADD CONSTRAINT [CK_OrganizationMemberships_Role] CHECK ([role]='CHECK_IN_STAFF' OR [role]='MANAGER');
ALTER TABLE [dbo].[tc_organization_requests] WITH CHECK ADD CONSTRAINT [CK_OrganizationRequest_Status] CHECK ([status]='REJECTED' OR [status]='APPROVED' OR [status]='PENDING');
ALTER TABLE [dbo].[tc_otps] WITH CHECK ADD CONSTRAINT [CK_Otps_Attempts] CHECK ([failed_attempts]>=(0) AND [failed_attempts]<=(5));
ALTER TABLE [dbo].[tc_otps] WITH CHECK ADD CONSTRAINT [CK_Otps_Purpose] CHECK ([purpose]='RESET_PASSWORD' OR [purpose]='VERIFY_EMAIL');
ALTER TABLE [dbo].[tc_otps] WITH CHECK ADD CONSTRAINT [CK_Otps_Time] CHECK ([expires_at]>[created_at]);
ALTER TABLE [dbo].[tc_outbox] WITH CHECK ADD CONSTRAINT [CK_Outbox_Attempts] CHECK ([attempts]>=(0));
ALTER TABLE [dbo].[tc_outbox] WITH CHECK ADD CONSTRAINT [CK_Outbox_Lease] CHECK ([lease_owner] IS NULL AND [lease_until] IS NULL OR [lease_owner] IS NOT NULL AND [lease_until] IS NOT NULL);
ALTER TABLE [dbo].[tc_outbox] WITH CHECK ADD CONSTRAINT [CK_Outbox_Status] CHECK ([status]='FAILED' OR [status]='PUBLISHED' OR [status]='PROCESSING' OR [status]='PENDING');
ALTER TABLE [dbo].[tc_payments] WITH CHECK ADD CONSTRAINT [CK_Payment_PositiveAmount] CHECK ([amount]>(0));
ALTER TABLE [dbo].[tc_payments] WITH CHECK ADD CONSTRAINT [CK_Payments_Currency] CHECK ([currency]='VND');
ALTER TABLE [dbo].[tc_payments] WITH CHECK ADD CONSTRAINT [CK_Payments_Status] CHECK ([status]='UNKNOWN' OR [status]='FAILED' OR [status]='CAPTURED' OR [status]='PENDING');
ALTER TABLE [dbo].[tc_payouts] WITH CHECK ADD CONSTRAINT [CK_Payout_PositiveAmount] CHECK ([amount]>(0));
ALTER TABLE [dbo].[tc_payouts] WITH CHECK ADD CONSTRAINT [CK_Payouts_Status] CHECK ([status]='FAILED' OR [status]='SUCCEEDED' OR [status]='PENDING');
ALTER TABLE [dbo].[tc_refunds] WITH CHECK ADD CONSTRAINT [CK_Refund_PositiveAmount] CHECK ([amount]>(0));
ALTER TABLE [dbo].[tc_refunds] WITH CHECK ADD CONSTRAINT [CK_Refund_Purpose_Request] CHECK ([purpose]='CUSTOMER_REFUND' AND [refund_request_id] IS NOT NULL OR [purpose]='PAYMENT_COMPENSATION' AND [refund_request_id] IS NULL);
ALTER TABLE [dbo].[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [CK_RefundObligations_Amount] CHECK ([amount]>=(0));
ALTER TABLE [dbo].[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [CK_RefundObligations_Purpose] CHECK ([purpose]='PAYMENT_COMPENSATION' OR [purpose]='CUSTOMER_REFUND');
ALTER TABLE [dbo].[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [CK_RefundObligations_ReasonType] CHECK ([reason_type] IS NULL OR ([reason_type]='EVENT_CANCELLATION' OR [reason_type]='CUSTOMER_REQUEST'));
ALTER TABLE [dbo].[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [CK_RefundObligations_Status] CHECK ([status]='COMPLETED' OR [status]='REJECTED' OR [status]='RETRYABLE' OR [status]='NEEDS_RECONCILIATION' OR [status]='PROCESSING' OR [status]='APPROVED' OR [status]='REQUESTED');
ALTER TABLE [dbo].[tc_refund_requests] WITH CHECK ADD CONSTRAINT [CK_RefundRequests_ReasonType] CHECK ([reason_type]='EVENT_CANCELLATION' OR [reason_type]='CUSTOMER_REQUEST');
ALTER TABLE [dbo].[tc_refund_requests] WITH CHECK ADD CONSTRAINT [CK_RefundRequests_Status] CHECK ([status]='COMPLETED' OR [status]='REJECTED' OR [status]='APPROVED' OR [status]='PENDING');
ALTER TABLE [dbo].[tc_refunds] WITH CHECK ADD CONSTRAINT [CK_Refunds_Status] CHECK ([status]='UNKNOWN' OR [status]='FAILED' OR [status]='SUCCEEDED' OR [status]='PENDING');
ALTER TABLE [dbo].[tc_refund_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_RefundTransferLogs_AttemptNo] CHECK ([attempt_no]>(0));
ALTER TABLE [dbo].[tc_refund_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_RefundTransferLogs_Result] CHECK ([result]='SUCCEEDED' OR [result]='FAILED' OR [result]='UNKNOWN' OR [result]='PENDING');
ALTER TABLE [dbo].[tc_refund_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_RefundTransferLogs_Status] CHECK ([status]='SUCCEEDED' OR [status]='FAILED' OR [status]='UNKNOWN' OR [status]='PENDING');
ALTER TABLE [dbo].[tc_request_idempotency] WITH CHECK ADD CONSTRAINT [CK_RequestIdempotency_Status] CHECK ([status]='COMPLETED' OR [status]='FAILED');
ALTER TABLE [dbo].[tc_seats] WITH CHECK ADD CONSTRAINT [CK_Seats_Number] CHECK ([seat_number]>(0));
ALTER TABLE [dbo].[tc_seats] WITH CHECK ADD CONSTRAINT [CK_Seats_Status] CHECK ([status]='SOLD' OR [status]='HELD' OR [status]='AVAILABLE');
ALTER TABLE [dbo].[tc_settlements] WITH CHECK ADD CONSTRAINT [CK_Settlement_Amounts] CHECK ([gross_revenue]>=(0) AND [total_refund]>=(0) AND [total_commission]>=(0) AND [net_payable]>=(0) AND [total_refund]<=[gross_revenue] AND [total_commission]<=([gross_revenue]-[total_refund]) AND [net_payable]=(([gross_revenue]-[total_refund])-[total_commission]));
ALTER TABLE [dbo].[tc_settlements] WITH CHECK ADD CONSTRAINT [CK_Settlement_PayoutBalances] CHECK ([paid_amount]>=(0) AND [pending_amount]>=(0) AND ([paid_amount]+[pending_amount])<=[net_payable]);
ALTER TABLE [dbo].[tc_settlement_items] WITH CHECK ADD CONSTRAINT [CK_SettlementItem_Amounts] CHECK ([gross_amount]>=(0) AND [refund_amount]>=(0) AND [commission_amount]>=(0) AND [net_amount]>=(0) AND [refund_amount]<=[gross_amount] AND [commission_amount]<=([gross_amount]-[refund_amount]) AND [net_amount]=(([gross_amount]-[refund_amount])-[commission_amount]));
ALTER TABLE [dbo].[tc_settlement_order_snapshots] WITH CHECK ADD CONSTRAINT [CK_SettlementOrderSnapshots_Amounts] CHECK ([gross_amount]>=(0) AND [refund_amount]>=(0) AND [commission_amount]>=(0) AND [net_amount]>=(0) AND [net_amount]=(([gross_amount]-[refund_amount])-[commission_amount]));
ALTER TABLE [dbo].[tc_settlements] WITH CHECK ADD CONSTRAINT [CK_Settlements_Status] CHECK ([status]='PAID' OR [status]='CONFIRMED' OR [status]='DRAFT');
ALTER TABLE [dbo].[tc_settlement_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_SettlementTransferLogs_Amount] CHECK ([amount]>(0));
ALTER TABLE [dbo].[tc_settlement_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_SettlementTransferLogs_Result] CHECK ([result]='FAILED' OR [result]='SUCCEEDED' OR [result]='PENDING');
ALTER TABLE [dbo].[tc_settlement_transfer_logs] WITH CHECK ADD CONSTRAINT [CK_SettlementTransferLogs_Status] CHECK ([status]='FAILED' OR [status]='SUCCEEDED' OR [status]='PENDING');
ALTER TABLE [dbo].[tc_tickets] WITH CHECK ADD CONSTRAINT [CK_Ticket_PaidAmount] CHECK ([paid_amount]>=(0));
ALTER TABLE [dbo].[tc_ticket_holds] WITH CHECK ADD CONSTRAINT [CK_TicketHold_TimeRange] CHECK ([expires_at]>[created_at]);
ALTER TABLE [dbo].[tc_ticket_holds] WITH CHECK ADD CONSTRAINT [CK_TicketHolds_Status] CHECK ([status]='CONSUMED' OR [status]='RELEASED' OR [status]='ACTIVE');
ALTER TABLE [dbo].[tc_tickets] WITH CHECK ADD CONSTRAINT [CK_Tickets_Status] CHECK ([status]='INVALIDATED' OR [status]='REFUNDED' OR [status]='REFUND_PENDING' OR [status]='USED' OR [status]='ACTIVE');
ALTER TABLE [dbo].[tc_user_platform_roles] WITH CHECK ADD CONSTRAINT [CK_UserPlatformRoles_Role] CHECK ([role]='ADMIN');
ALTER TABLE [dbo].[tc_users] WITH CHECK ADD CONSTRAINT [CK_Users_AuthVersion] CHECK ([auth_version]>=(0));
ALTER TABLE [dbo].[tc_users] WITH CHECK ADD CONSTRAINT [CK_Users_Status] CHECK ([status]='DISABLED' OR [status]='ACTIVE');
ALTER TABLE [dbo].[tc_zones] WITH CHECK ADD CONSTRAINT [CK_Zone_Price_Quota] CHECK ([price]>=(0) AND [held_quantity]>=(0) AND [sold_quantity]>=(0) AND ([type]='STANDING' AND [capacity] IS NOT NULL AND [capacity]>(0) AND ([held_quantity]+[sold_quantity])<=[capacity] OR [type]='SEATED' AND [capacity] IS NULL AND [held_quantity]=(0) AND [sold_quantity]=(0)));
ALTER TABLE [dbo].[tc_zones] WITH CHECK ADD CONSTRAINT [CK_Zones_Type] CHECK ([type]='STANDING' OR [type]='SEATED');
ALTER TABLE [dbo].[tc_audit_logs] WITH CHECK ADD CONSTRAINT [FK_AuditLogs_Actor] FOREIGN KEY ([actor_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_check_ins] WITH CHECK ADD CONSTRAINT [FK_CheckIns_Actor] FOREIGN KEY ([actor_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_check_ins] WITH CHECK ADD CONSTRAINT [FK_CheckIns_Event] FOREIGN KEY ([event_id]) REFERENCES [dbo].[tc_events] ([id]);
ALTER TABLE [dbo].[tc_check_ins] WITH CHECK ADD CONSTRAINT [FK_CheckIns_Ticket] FOREIGN KEY ([ticket_id]) REFERENCES [dbo].[tc_tickets] ([id]);
ALTER TABLE [dbo].[tc_commission_rules] WITH CHECK ADD CONSTRAINT [FK_CommissionRules_Organization] FOREIGN KEY ([organization_id]) REFERENCES [dbo].[tc_organizations] ([id]);
ALTER TABLE [dbo].[tc_coupon_redemptions] WITH CHECK ADD CONSTRAINT [FK_CouponRedemptions_Coupon] FOREIGN KEY ([coupon_id]) REFERENCES [dbo].[tc_coupons] ([id]);
ALTER TABLE [dbo].[tc_coupon_redemptions] WITH CHECK ADD CONSTRAINT [FK_CouponRedemptions_Order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[tc_orders] ([id]);
ALTER TABLE [dbo].[tc_coupon_redemptions] WITH CHECK ADD CONSTRAINT [FK_CouponRedemptions_User] FOREIGN KEY ([user_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_coupons] WITH CHECK ADD CONSTRAINT [FK_Coupons_Organization] FOREIGN KEY ([organization_id]) REFERENCES [dbo].[tc_organizations] ([id]);
ALTER TABLE [dbo].[tc_events] WITH CHECK ADD CONSTRAINT [FK_Events_Category] FOREIGN KEY ([category_id]) REFERENCES [dbo].[tc_event_categories] ([id]);
ALTER TABLE [dbo].[tc_events] WITH CHECK ADD CONSTRAINT [FK_Events_CommissionRule] FOREIGN KEY ([commission_rule_id]) REFERENCES [dbo].[tc_commission_rules] ([id]);
ALTER TABLE [dbo].[tc_events] WITH CHECK ADD CONSTRAINT [FK_Events_Organization] FOREIGN KEY ([organization_id]) REFERENCES [dbo].[tc_organizations] ([id]);
ALTER TABLE [dbo].[tc_order_items] WITH CHECK ADD CONSTRAINT [FK_OrderItems_Order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[tc_orders] ([id]);
ALTER TABLE [dbo].[tc_order_items] WITH CHECK ADD CONSTRAINT [FK_OrderItems_Seat] FOREIGN KEY ([seat_id]) REFERENCES [dbo].[tc_seats] ([id]);
ALTER TABLE [dbo].[tc_order_items] WITH CHECK ADD CONSTRAINT [FK_OrderItems_Zone] FOREIGN KEY ([zone_id]) REFERENCES [dbo].[tc_zones] ([id]);
ALTER TABLE [dbo].[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_AcceptedPayment] FOREIGN KEY ([accepted_payment_id]) REFERENCES [dbo].[tc_payments] ([id]);
ALTER TABLE [dbo].[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_Coupon] FOREIGN KEY ([coupon_id]) REFERENCES [dbo].[tc_coupons] ([id]);
ALTER TABLE [dbo].[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_Event] FOREIGN KEY ([event_id]) REFERENCES [dbo].[tc_events] ([id]);
ALTER TABLE [dbo].[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_Hold] FOREIGN KEY ([hold_id]) REFERENCES [dbo].[tc_ticket_holds] ([id]);
ALTER TABLE [dbo].[tc_orders] WITH CHECK ADD CONSTRAINT [FK_Orders_User] FOREIGN KEY ([user_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_organization_memberships] WITH CHECK ADD CONSTRAINT [FK_OrganizationMemberships_Organization] FOREIGN KEY ([organization_id]) REFERENCES [dbo].[tc_organizations] ([id]);
ALTER TABLE [dbo].[tc_organization_memberships] WITH CHECK ADD CONSTRAINT [FK_OrganizationMemberships_User] FOREIGN KEY ([user_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_organization_requests] WITH CHECK ADD CONSTRAINT [FK_OrganizationRequests_Applicant] FOREIGN KEY ([applicant_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_organization_requests] WITH CHECK ADD CONSTRAINT [FK_OrganizationRequests_Organization] FOREIGN KEY ([organization_id]) REFERENCES [dbo].[tc_organizations] ([id]);
ALTER TABLE [dbo].[tc_organization_requests] WITH CHECK ADD CONSTRAINT [FK_OrganizationRequests_Reviewer] FOREIGN KEY ([reviewer_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_organizations] WITH CHECK ADD CONSTRAINT [FK_Organizations_Requester] FOREIGN KEY ([requester_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_otps] WITH CHECK ADD CONSTRAINT [FK_Otps_User] FOREIGN KEY ([user_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_payments] WITH CHECK ADD CONSTRAINT [FK_Payments_Order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[tc_orders] ([id]);
ALTER TABLE [dbo].[tc_payouts] WITH CHECK ADD CONSTRAINT [FK_Payouts_Settlement] FOREIGN KEY ([settlement_id]) REFERENCES [dbo].[tc_settlements] ([id]);
ALTER TABLE [dbo].[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [FK_RefundObligations_LegacyRequest] FOREIGN KEY ([legacy_request_id]) REFERENCES [dbo].[tc_refund_requests] ([id]);
ALTER TABLE [dbo].[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [FK_RefundObligations_Order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[tc_orders] ([id]);
ALTER TABLE [dbo].[tc_refund_obligations] WITH CHECK ADD CONSTRAINT [FK_RefundObligations_Payment] FOREIGN KEY ([payment_id]) REFERENCES [dbo].[tc_payments] ([id]);
ALTER TABLE [dbo].[tc_refund_requests] WITH CHECK ADD CONSTRAINT [FK_RefundRequests_Order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[tc_orders] ([id]);
ALTER TABLE [dbo].[tc_refund_requests] WITH CHECK ADD CONSTRAINT [FK_RefundRequests_Requester] FOREIGN KEY ([requester_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_refund_requests] WITH CHECK ADD CONSTRAINT [FK_RefundRequests_Reviewer] FOREIGN KEY ([reviewer_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_refund_request_tickets] WITH CHECK ADD CONSTRAINT [FK_RefundRequestTickets_Request] FOREIGN KEY ([refund_request_id]) REFERENCES [dbo].[tc_refund_requests] ([id]);
ALTER TABLE [dbo].[tc_refund_request_tickets] WITH CHECK ADD CONSTRAINT [FK_RefundRequestTickets_Ticket] FOREIGN KEY ([ticket_id]) REFERENCES [dbo].[tc_tickets] ([id]);
ALTER TABLE [dbo].[tc_refunds] WITH CHECK ADD CONSTRAINT [FK_Refunds_Payment] FOREIGN KEY ([payment_id]) REFERENCES [dbo].[tc_payments] ([id]);
ALTER TABLE [dbo].[tc_refunds] WITH CHECK ADD CONSTRAINT [FK_Refunds_Request] FOREIGN KEY ([refund_request_id]) REFERENCES [dbo].[tc_refund_requests] ([id]);
ALTER TABLE [dbo].[tc_refund_tickets] WITH CHECK ADD CONSTRAINT [FK_RefundTickets_Refund] FOREIGN KEY ([refund_id]) REFERENCES [dbo].[tc_refund_obligations] ([id]);
ALTER TABLE [dbo].[tc_refund_tickets] WITH CHECK ADD CONSTRAINT [FK_RefundTickets_Ticket] FOREIGN KEY ([ticket_id]) REFERENCES [dbo].[tc_tickets] ([id]);
ALTER TABLE [dbo].[tc_refund_transfer_logs] WITH CHECK ADD CONSTRAINT [FK_RefundTransferLogs_Refund] FOREIGN KEY ([refund_id]) REFERENCES [dbo].[tc_refund_obligations] ([id]);
ALTER TABLE [dbo].[tc_request_idempotency] WITH CHECK ADD CONSTRAINT [FK_RequestIdempotency_Actor] FOREIGN KEY ([actor_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_seats] WITH CHECK ADD CONSTRAINT [FK_Seats_Zone] FOREIGN KEY ([zone_id]) REFERENCES [dbo].[tc_zones] ([id]);
ALTER TABLE [dbo].[tc_settlement_items] WITH CHECK ADD CONSTRAINT [FK_SettlementItems_Order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[tc_orders] ([id]);
ALTER TABLE [dbo].[tc_settlement_items] WITH CHECK ADD CONSTRAINT [FK_SettlementItems_Settlement] FOREIGN KEY ([settlement_id]) REFERENCES [dbo].[tc_settlements] ([id]);
ALTER TABLE [dbo].[tc_settlement_order_snapshots] WITH CHECK ADD CONSTRAINT [FK_SettlementOrderSnapshots_Order] FOREIGN KEY ([order_id]) REFERENCES [dbo].[tc_orders] ([id]);
ALTER TABLE [dbo].[tc_settlement_order_snapshots] WITH CHECK ADD CONSTRAINT [FK_SettlementOrderSnapshots_Settlement] FOREIGN KEY ([settlement_id]) REFERENCES [dbo].[tc_settlements] ([id]);
ALTER TABLE [dbo].[tc_settlements] WITH CHECK ADD CONSTRAINT [FK_Settlements_Event] FOREIGN KEY ([event_id]) REFERENCES [dbo].[tc_events] ([id]);
ALTER TABLE [dbo].[tc_settlement_transfer_logs] WITH CHECK ADD CONSTRAINT [FK_SettlementTransferLogs_Settlement] FOREIGN KEY ([settlement_id]) REFERENCES [dbo].[tc_settlements] ([id]);
ALTER TABLE [dbo].[tc_ticket_check_in_logs] WITH CHECK ADD CONSTRAINT [FK_TicketCheckInLogs_Actor] FOREIGN KEY ([actor_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_ticket_check_in_logs] WITH CHECK ADD CONSTRAINT [FK_TicketCheckInLogs_Event] FOREIGN KEY ([event_id]) REFERENCES [dbo].[tc_events] ([id]);
ALTER TABLE [dbo].[tc_ticket_check_in_logs] WITH CHECK ADD CONSTRAINT [FK_TicketCheckInLogs_Ticket] FOREIGN KEY ([ticket_id]) REFERENCES [dbo].[tc_tickets] ([id]);
ALTER TABLE [dbo].[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [FK_TicketHoldItems_Hold] FOREIGN KEY ([hold_id]) REFERENCES [dbo].[tc_ticket_holds] ([id]);
ALTER TABLE [dbo].[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [FK_TicketHoldItems_Seat] FOREIGN KEY ([seat_id]) REFERENCES [dbo].[tc_seats] ([id]);
ALTER TABLE [dbo].[tc_ticket_hold_items] WITH CHECK ADD CONSTRAINT [FK_TicketHoldItems_Zone] FOREIGN KEY ([zone_id]) REFERENCES [dbo].[tc_zones] ([id]);
ALTER TABLE [dbo].[tc_ticket_holds] WITH CHECK ADD CONSTRAINT [FK_TicketHolds_Event] FOREIGN KEY ([event_id]) REFERENCES [dbo].[tc_events] ([id]);
ALTER TABLE [dbo].[tc_ticket_holds] WITH CHECK ADD CONSTRAINT [FK_TicketHolds_User] FOREIGN KEY ([user_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_tickets] WITH CHECK ADD CONSTRAINT [FK_Tickets_OrderItem] FOREIGN KEY ([order_item_id]) REFERENCES [dbo].[tc_order_items] ([id]);
ALTER TABLE [dbo].[tc_user_platform_roles] WITH CHECK ADD CONSTRAINT [FK_UserPlatformRoles_User] FOREIGN KEY ([user_id]) REFERENCES [dbo].[tc_users] ([id]);
ALTER TABLE [dbo].[tc_zones] WITH CHECK ADD CONSTRAINT [FK_Zones_Event] FOREIGN KEY ([event_id]) REFERENCES [dbo].[tc_events] ([id]);
GO
SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO
/* Function: fn_CalculateCommission */
CREATE   FUNCTION dbo.fn_CalculateCommission(
    @remaining_amount decimal(19,0), @rate_percent decimal(7,4), @fixed_fee decimal(19,0)
)
RETURNS decimal(19,0)
AS
BEGIN
    IF @remaining_amount IS NULL OR @rate_percent IS NULL OR @fixed_fee IS NULL
       OR @remaining_amount < 0 OR @rate_percent < 0 OR @fixed_fee < 0 RETURN NULL;
    IF @remaining_amount = 0 RETURN 0;
    DECLARE @fee decimal(19,0) = ROUND(@remaining_amount * @rate_percent / 100.0 + @fixed_fee, 0);
    RETURN CASE WHEN @fee < @remaining_amount THEN @fee ELSE @remaining_amount END;
END;
GO
/* Function: fn_CalculateCouponDiscount */
CREATE   FUNCTION dbo.fn_CalculateCouponDiscount(
    @subtotal decimal(19,0), @discount_type varchar(30),
    @percentage decimal(5,2), @fixed_amount decimal(19,0)
)
RETURNS decimal(19,0)
AS
BEGIN
    IF @subtotal IS NULL OR @subtotal < 0 RETURN NULL;
    DECLARE @cap decimal(19,0) = FLOOR(@subtotal * 0.30);
    IF @discount_type = 'PERCENTAGE' AND @percentage > 0 AND @percentage <= 30 AND @fixed_amount IS NULL
        RETURN FLOOR(@subtotal * @percentage / 100.0);
    IF @discount_type = 'FIXED_AMOUNT' AND @fixed_amount > 0 AND @percentage IS NULL
        RETURN CASE WHEN @fixed_amount < @cap THEN @fixed_amount ELSE @cap END;
    RETURN NULL;
END;
GO
/* Function: fn_event_is_check_in_open */
CREATE   FUNCTION dbo.fn_event_is_check_in_open
(@event_id uniqueidentifier,@now datetime2(7))
RETURNS bit
AS
BEGIN
    DECLARE @open bit=0;
    SELECT @open=1 FROM dbo.tc_events
    WHERE id=@event_id AND status='PUBLISHED'
      AND @now>=DATEADD(minute,-60,start_time) AND @now<end_time;
    RETURN @open;
END;
GO
/* Function: fn_AllocateTicketPaidAmounts */
CREATE   FUNCTION dbo.fn_AllocateTicketPaidAmounts(@order_id uniqueidentifier)
RETURNS TABLE
AS RETURN (
    WITH n AS (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
               UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8),
    units AS (
        SELECT oi.id order_item_id, n.n ticket_ordinal, oi.unit_price,
               o.subtotal_amount, o.discount_amount, o.total_amount,
               CAST(CASE WHEN o.subtotal_amount = 0 THEN 0
                    ELSE o.discount_amount * oi.unit_price / o.subtotal_amount END AS decimal(38,12)) raw_discount
        FROM dbo.tc_order_items oi JOIN dbo.tc_orders o ON o.id = oi.order_id
        JOIN n ON n.n <= oi.quantity WHERE o.id = @order_id
    ), base AS (
        SELECT *, FLOOR(raw_discount) base_discount, raw_discount - FLOOR(raw_discount) remainder,
               SUM(FLOOR(raw_discount)) OVER () allocated_base
        FROM units
    ), ranked AS (
        SELECT *, ROW_NUMBER() OVER (ORDER BY remainder DESC, order_item_id, ticket_ordinal) remainder_rank
        FROM base
    )
    SELECT order_item_id, ticket_ordinal, unit_price original_amount,
           CAST(base_discount + CASE WHEN remainder_rank <= discount_amount - allocated_base THEN 1 ELSE 0 END AS decimal(19,0)) discount_amount,
           CAST(unit_price - base_discount - CASE WHEN remainder_rank <= discount_amount - allocated_base THEN 1 ELSE 0 END AS decimal(19,0)) paid_amount
    FROM ranked
);
GO
/* View: vw_CouponUsage */
CREATE   VIEW dbo.vw_CouponUsage
AS
WITH u AS (
    SELECT coupon_id,
           SUM(CASE WHEN status = 'RESERVED' THEN 1 ELSE 0 END) reserved_uses,
           SUM(CASE WHEN status = 'CONSUMED' THEN 1 ELSE 0 END) consumed_uses,
           SUM(CASE WHEN status = 'RELEASED' THEN 1 ELSE 0 END) released_uses
    FROM dbo.tc_coupon_redemptions GROUP BY coupon_id
)
SELECT c.id coupon_id, c.organization_id, c.code, c.active, c.valid_from, c.valid_to, c.max_uses,
       COALESCE(u.reserved_uses, 0) reserved_uses, COALESCE(u.consumed_uses, 0) consumed_uses,
       COALESCE(u.released_uses, 0) released_uses,
       CASE WHEN c.max_uses > COALESCE(u.reserved_uses, 0) + COALESCE(u.consumed_uses, 0)
            THEN c.max_uses - COALESCE(u.reserved_uses, 0) - COALESCE(u.consumed_uses, 0) ELSE 0 END available_uses
FROM dbo.tc_coupons c LEFT JOIN u ON u.coupon_id = c.id;
GO
/* Function: fn_GetCouponEligibility */
CREATE   FUNCTION dbo.fn_GetCouponEligibility(
    @coupon_id uniqueidentifier, @organization_id uniqueidentifier,
    @subtotal decimal(19,0), @now_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    SELECT c.id coupon_id,
       CASE WHEN c.organization_id <> @organization_id THEN CAST(0 AS bit)
            WHEN c.active = 0 THEN CAST(0 AS bit)
            WHEN @now_utc < c.valid_from OR @now_utc >= c.valid_to THEN CAST(0 AS bit)
            WHEN u.available_uses <= 0 THEN CAST(0 AS bit) ELSE CAST(1 AS bit) END is_eligible,
       CASE WHEN c.organization_id <> @organization_id THEN 'WRONG_ORGANIZATION'
            WHEN c.active = 0 THEN 'INACTIVE'
            WHEN @now_utc < c.valid_from THEN 'NOT_STARTED' WHEN @now_utc >= c.valid_to THEN 'EXPIRED'
            WHEN u.available_uses <= 0 THEN 'QUOTA_EXHAUSTED' ELSE 'ELIGIBLE' END reason,
       u.available_uses,
       dbo.fn_CalculateCouponDiscount(@subtotal, c.discount_type, c.percentage_value, c.fixed_amount) preview_discount
    FROM dbo.tc_coupons c JOIN dbo.vw_CouponUsage u ON u.coupon_id = c.id WHERE c.id = @coupon_id
);
GO
/* Function: fn_GetEventCheckInWindow */
CREATE   FUNCTION dbo.fn_GetEventCheckInWindow(@event_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT id event_id, DATEADD(minute, -60, start_time) opens_at, end_time closes_at,
           CASE WHEN status = 'CANCELLED' THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_cancelled,
           CASE WHEN status <> 'CANCELLED' AND @now_utc >= DATEADD(minute, -60, start_time)
                     AND @now_utc < end_time THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_open
    FROM dbo.tc_events WHERE id = @event_id
);
GO
/* Function: fn_GetOrganizationCashFlow */
CREATE   FUNCTION dbo.fn_GetOrganizationCashFlow(
    @organization_id uniqueidentifier, @from_utc datetime2(3), @to_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    WITH flows AS (
        SELECT CASE WHEN EXISTS (
                        SELECT 1 FROM dbo.tc_refunds r
                        WHERE r.payment_id = p.id AND r.purpose = 'PAYMENT_COMPENSATION'
                    ) THEN 'COMPENSATION_CAPTURE' ELSE 'TICKET_CAPTURE' END flow_type,
               p.amount inflow, CAST(0 AS decimal(19,0)) outflow
        FROM dbo.tc_payments p
        JOIN dbo.tc_orders o ON o.id = p.order_id
        JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND p.status = 'CAPTURED'
          AND o.paid_at >= @from_utc AND o.paid_at < @to_utc
        UNION ALL
        SELECT CASE WHEN r.purpose = 'PAYMENT_COMPENSATION' THEN 'COMPENSATION_REFUND' ELSE 'TICKET_REFUND' END,
               CAST(0 AS decimal(19,0)), r.amount
        FROM dbo.tc_refunds r
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id
        JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND r.status = 'SUCCEEDED'
          AND r.processed_at >= @from_utc AND r.processed_at < @to_utc
    )
    SELECT flow_type, SUM(inflow) inflow_amount, SUM(outflow) outflow_amount,
           SUM(inflow - outflow) net_amount
    FROM flows GROUP BY flow_type
);
GO
/* View: vw_OrderFinancialSummary */
CREATE   VIEW dbo.vw_OrderFinancialSummary
AS
WITH captured AS (
    SELECT order_id, SUM(amount) captured_amount
    FROM dbo.tc_payments
    WHERE status = 'CAPTURED'
    GROUP BY order_id
), refunds AS (
    SELECT p.order_id,
           SUM(CASE WHEN r.purpose = 'CUSTOMER_REFUND' THEN r.amount ELSE 0 END) ticket_refund_amount,
           SUM(CASE WHEN r.purpose = 'PAYMENT_COMPENSATION' THEN r.amount ELSE 0 END) compensation_refund_amount
    FROM dbo.tc_refunds r
    JOIN dbo.tc_payments p ON p.id = r.payment_id
    WHERE r.status = 'SUCCEEDED'
    GROUP BY p.order_id
), tickets AS (
    SELECT oi.order_id, SUM(t.paid_amount) ticket_gross_amount
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    GROUP BY oi.order_id
)
SELECT o.id order_id, o.user_id owner_id, o.event_id, e.organization_id, o.status,
       o.subtotal_amount, o.discount_amount, o.total_amount, o.paid_at,
       COALESCE(c.captured_amount, 0) captured_amount,
       COALESCE(t.ticket_gross_amount, 0) issued_ticket_amount,
       COALESCE(t.ticket_gross_amount, 0) ticket_gross_amount,
       COALESCE(r.ticket_refund_amount, 0) ticket_refund_amount,
       COALESCE(r.ticket_refund_amount, 0) successful_ticket_refund_amount,
       COALESCE(r.compensation_refund_amount, 0) compensation_refund_amount,
       CASE WHEN COALESCE(t.ticket_gross_amount, 0) > COALESCE(r.ticket_refund_amount, 0)
            THEN COALESCE(t.ticket_gross_amount, 0) - COALESCE(r.ticket_refund_amount, 0)
            ELSE 0 END remaining_ticket_amount
FROM dbo.tc_orders o
JOIN dbo.tc_events e ON e.id = o.event_id
LEFT JOIN captured c ON c.order_id = o.id
LEFT JOIN refunds r ON r.order_id = o.id
LEFT JOIN tickets t ON t.order_id = o.id;
GO
/* Function: fn_GetOrganizationRevenue */
CREATE   FUNCTION dbo.fn_GetOrganizationRevenue(
    @organization_id uniqueidentifier, @from_utc datetime2(3), @to_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    SELECT COUNT_BIG(*) paid_order_count,
           COALESCE(SUM(s.ticket_gross_amount), 0) gross_revenue,
           COALESCE(SUM(COALESCE(r.refunded_amount, 0)), 0) refunded_amount,
           COALESCE(SUM(s.ticket_gross_amount - COALESCE(r.refunded_amount, 0)), 0) net_revenue
    FROM dbo.vw_OrderFinancialSummary s
    OUTER APPLY (
        SELECT SUM(x.amount) refunded_amount
        FROM dbo.tc_refunds x
        JOIN dbo.tc_payments p ON p.id = x.payment_id
        WHERE p.order_id = s.order_id AND x.purpose = 'CUSTOMER_REFUND'
          AND x.status = 'SUCCEEDED' AND x.processed_at < @to_utc
    ) r
    WHERE s.organization_id = @organization_id AND s.status = 'PAID'
      AND s.paid_at >= @from_utc AND s.paid_at < @to_utc
);
GO
/* Function: fn_GetRefundableTickets */
CREATE   FUNCTION dbo.fn_GetRefundableTickets(@order_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT t.id ticket_id, t.paid_amount, oi.zone_name_snapshot, oi.seat_label_snapshot
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE o.id = @order_id AND @now_utc < e.start_time AND t.status = 'ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.ticket_id = t.id AND rt.is_open = 1)
);
GO
/* Function: fn_GetSettlementBlockers */
CREATE   FUNCTION dbo.fn_GetSettlementBlockers(@event_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT CAST('EVENT_NOT_ENDED' AS varchar(40)) blocker_type, e.id blocker_id,
           CAST(NULL AS uniqueidentifier) related_id
    FROM dbo.tc_events e
    WHERE e.id = @event_id AND @now_utc < e.end_time

    UNION ALL
    SELECT 'PAYMENT_UNRESOLVED', p.id, p.order_id
    FROM dbo.tc_payments p
    JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND p.status IN ('PENDING', 'UNKNOWN')

    UNION ALL
    SELECT 'REFUND_REQUEST_OPEN', rr.id, rr.order_id
    FROM dbo.tc_refund_requests rr
    JOIN dbo.tc_orders o ON o.id = rr.order_id
    WHERE o.event_id = @event_id AND rr.status IN ('PENDING', 'APPROVED')

    UNION ALL
    SELECT 'REFUND_UNRESOLVED', r.id, p.order_id
    FROM dbo.tc_refunds r
    JOIN dbo.tc_payments p ON p.id = r.payment_id
    JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND r.status IN ('PENDING', 'UNKNOWN')

    UNION ALL
    SELECT 'COMPENSATION_UNRESOLVED', p.id, p.order_id
    FROM dbo.tc_payments p
    JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND p.status = 'CAPTURED'
      AND EXISTS (SELECT 1 FROM dbo.tc_refunds r WHERE r.payment_id = p.id AND r.purpose = 'PAYMENT_COMPENSATION')
      AND COALESCE((SELECT SUM(r.amount) FROM dbo.tc_refunds r
                    WHERE r.payment_id = p.id AND r.purpose = 'PAYMENT_COMPENSATION'
                      AND r.status = 'SUCCEEDED'), 0) < p.amount

    UNION ALL
    SELECT 'CANCELLATION_PENDING', x.id, x.aggregate_id
    FROM dbo.tc_outbox x
    WHERE x.aggregate_type = 'EVENT' AND x.aggregate_id = @event_id
      AND x.event_type = 'EVENT_CANCELLED' AND x.status <> 'PUBLISHED'
);
GO
/* View: vw_ZoneInventory */
CREATE   VIEW dbo.vw_ZoneInventory
AS
WITH seated AS (
    SELECT zone_id, COUNT_BIG(*) capacity,
           SUM(CASE WHEN status = 'HELD' THEN 1 ELSE 0 END) held,
           SUM(CASE WHEN status = 'SOLD' THEN 1 ELSE 0 END) sold,
           SUM(CASE WHEN status = 'AVAILABLE' THEN 1 ELSE 0 END) available
    FROM dbo.tc_seats GROUP BY zone_id
)
SELECT z.id zone_id, z.event_id, e.organization_id, z.name, z.type, z.price,
       CASE WHEN z.type = 'SEATED' THEN COALESCE(s.capacity, 0) ELSE z.capacity END capacity,
       CASE WHEN z.type = 'SEATED' THEN COALESCE(s.held, 0) ELSE z.held_quantity END held_quantity,
       CASE WHEN z.type = 'SEATED' THEN COALESCE(s.sold, 0) ELSE z.sold_quantity END sold_quantity,
       CASE WHEN z.type = 'SEATED' THEN COALESCE(s.available, 0)
            ELSE z.capacity - z.held_quantity - z.sold_quantity END available_quantity
FROM dbo.tc_zones z
JOIN dbo.tc_events e ON e.id = z.event_id
LEFT JOIN seated s ON s.zone_id = z.id;
GO
/* Function: fn_GetZoneAvailability */
CREATE   FUNCTION dbo.fn_GetZoneAvailability(@zone_id uniqueidentifier)
RETURNS TABLE
AS RETURN (SELECT * FROM dbo.vw_ZoneInventory WHERE zone_id = @zone_id);
GO
/* Stored Procedure: usp_ApplyOrderCoupon */
CREATE   PROCEDURE dbo.usp_ApplyOrderCoupon
    @order_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @coupon_code varchar(80) = NULL,
    @idempotency_key varchar(100) = NULL,
    @request_fingerprint varchar(200) = NULL
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp03;
    BEGIN TRY
        IF @order_id IS NULL OR @actor_id IS NULL THROW 51230, 'Order is not coupon-eligible', 1;

        DECLARE @effective_fingerprint varchar(200) = COALESCE(@request_fingerprint, COALESCE(@coupon_code, '__REMOVE__'));

        -- DB04: Durable request idempotency recovery
        IF @idempotency_key IS NOT NULL BEGIN
            DECLARE @existing_target uniqueidentifier, @existing_fingerprint varchar(200), @existing_payload nvarchar(max);
            SELECT @existing_target = target_id, @existing_fingerprint = fingerprint, @existing_payload = response_payload
            FROM dbo.tc_request_idempotency WITH (UPDLOCK, HOLDLOCK)
            WHERE actor_id = @actor_id AND operation = 'APPLY_ORDER_COUPON' AND idempotency_key = @idempotency_key;

            IF @existing_target IS NOT NULL BEGIN
                -- Conflict detection: key reused with different target or fingerprint
                IF @existing_target <> @order_id OR @existing_fingerprint <> @effective_fingerprint
                    THROW 51239, 'Idempotency conflict: key already used with different payload or target', 1;

                -- Replay stored immutable response
                SELECT
                    CAST(JSON_VALUE(@existing_payload, '$.subtotalAmount') AS decimal(19,0)) subtotal_amount,
                    CAST(JSON_VALUE(@existing_payload, '$.discountAmount') AS decimal(19,0)) discount_amount,
                    CAST(JSON_VALUE(@existing_payload, '$.totalAmount') AS decimal(19,0)) total_amount,
                    TRY_CONVERT(uniqueidentifier, JSON_VALUE(@existing_payload, '$.couponId')) coupon_id,
                    CAST(JSON_VALUE(@existing_payload, '$.version') AS bigint) version;
                IF @started = 1 COMMIT TRANSACTION;
                RETURN;
            END;
        END;

        DECLARE @event_id uniqueidentifier, @organization_id uniqueidentifier, @hold_id uniqueidentifier,
                @subtotal decimal(19,0), @old_coupon uniqueidentifier, @new_coupon uniqueidentifier,
                @discount decimal(19,0) = 0, @now datetime2(3),
                @order_user uniqueidentifier, @order_status varchar(30), @order_version bigint,
                @hold_status varchar(20), @hold_expires datetime2(3);

        -- Standard lock hierarchy: 1. Event -> 2. Hold -> 3. Order -> 4. Coupon (DB05)
        SELECT @event_id = o.event_id, @hold_id = o.hold_id FROM dbo.tc_orders o WHERE o.id = @order_id;
        IF @event_id IS NULL THROW 51230, 'Order is not coupon-eligible', 1;

        SELECT @organization_id = organization_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id AND status = 'PUBLISHED';
        IF @organization_id IS NULL THROW 51231, 'Event is unavailable', 1;

        SELECT @hold_status = status, @hold_expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;

        SELECT @order_user = user_id, @subtotal = subtotal_amount, @old_coupon = coupon_id, @order_status = status, @order_version = version
        FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;

        SET @now = SYSUTCDATETIME();

        -- Recheck expiry & eligibility after acquiring locks (DB05)
        IF @order_user <> @actor_id OR @order_status <> 'PENDING_PAYMENT' OR @hold_status <> 'ACTIVE' OR @hold_expires <= @now
            THROW 51230, 'Order is not coupon-eligible', 1;

        IF EXISTS (SELECT 1 FROM dbo.tc_payments WHERE order_id = @order_id AND status IN ('PENDING', 'UNKNOWN'))
            THROW 51232, 'Coupon cannot change while payment is unresolved', 1;

        IF @coupon_code IS NOT NULL
            SELECT @new_coupon = id FROM dbo.tc_coupons WITH (UPDLOCK, HOLDLOCK)
            WHERE organization_id = @organization_id AND code = @coupon_code;

        IF @coupon_code IS NOT NULL AND @new_coupon IS NULL THROW 51233, 'Coupon not found', 1;

        DECLARE @snap_subtotal decimal(19,0), @snap_discount decimal(19,0), @snap_total decimal(19,0),
                @snap_coupon uniqueidentifier, @snap_version bigint;

        -- No-op / Replay with same coupon: retain snapshot discount without re-running eligibility (DB03)
        IF @old_coupon = @new_coupon OR (@old_coupon IS NULL AND @new_coupon IS NULL) BEGIN
            SELECT @snap_subtotal = subtotal_amount, @snap_discount = discount_amount,
                   @snap_total = total_amount, @snap_coupon = coupon_id, @snap_version = version
            FROM dbo.tc_orders WHERE id = @order_id;

            IF @idempotency_key IS NOT NULL BEGIN
                DECLARE @res_json_noop nvarchar(max) = (
                    SELECT @snap_subtotal subtotalAmount, @snap_discount discountAmount, @snap_total totalAmount,
                           @snap_coupon couponId, @snap_version version
                    FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
                );
                INSERT dbo.tc_request_idempotency(actor_id, operation, idempotency_key, target_id, fingerprint, response_payload, expires_at)
                VALUES (@actor_id, 'APPLY_ORDER_COUPON', @idempotency_key, @order_id, @effective_fingerprint, @res_json_noop, DATEADD(day, 1, @now));
            END;

            IF @started = 1 COMMIT TRANSACTION;
            SELECT @snap_subtotal subtotal_amount, @snap_discount discount_amount, @snap_total total_amount, @snap_coupon coupon_id, @snap_version version;
            RETURN;
        END;

        IF @new_coupon IS NOT NULL BEGIN
            IF NOT EXISTS (SELECT 1 FROM dbo.fn_GetCouponEligibility(@new_coupon, @organization_id, @subtotal, @now) WHERE is_eligible = 1)
                THROW 51234, 'Coupon is not eligible', 1;
            SELECT @discount = dbo.fn_CalculateCouponDiscount(@subtotal, discount_type, percentage_value, fixed_amount)
            FROM dbo.tc_coupons WHERE id = @new_coupon;
            IF @discount IS NULL THROW 51235, 'Coupon discount is invalid', 1;
        END;

        IF EXISTS (SELECT 1 FROM dbo.tc_coupon_redemptions WHERE order_id = @order_id)
            UPDATE dbo.tc_coupon_redemptions
            SET coupon_id = COALESCE(@new_coupon, coupon_id),
                user_id = COALESCE(@actor_id, user_id),
                status = CASE WHEN @new_coupon IS NULL THEN 'RELEASED' ELSE 'RESERVED' END,
                reserved_at = CASE WHEN @new_coupon IS NULL THEN reserved_at ELSE @now END,
                expires_at = CASE WHEN @new_coupon IS NULL THEN expires_at ELSE (SELECT expires_at FROM dbo.tc_ticket_holds WHERE id = @hold_id) END,
                consumed_at = NULL, released_at = CASE WHEN @new_coupon IS NULL THEN @now ELSE NULL END
            WHERE order_id = @order_id;
        ELSE IF @new_coupon IS NOT NULL
            INSERT dbo.tc_coupon_redemptions(coupon_id, order_id, user_id, expires_at)
            VALUES (@new_coupon, @order_id, @actor_id, (SELECT expires_at FROM dbo.tc_ticket_holds WHERE id = @hold_id));

        -- Update order and capture snapshot INSIDE mutation transaction (DB03)
        UPDATE dbo.tc_orders
        SET coupon_id = @new_coupon,
            discount_amount = @discount,
            total_amount = @subtotal - @discount,
            version = version + 1,
            updated_at = @now
        WHERE id = @order_id;

        SELECT @snap_subtotal = subtotal_amount, @snap_discount = discount_amount,
               @snap_total = total_amount, @snap_coupon = coupon_id, @snap_version = version
        FROM dbo.tc_orders WHERE id = @order_id;

        -- Record durable idempotency if key supplied (DB04)
        IF @idempotency_key IS NOT NULL BEGIN
            DECLARE @res_json_mut nvarchar(max) = (
                SELECT @snap_subtotal subtotalAmount, @snap_discount discountAmount, @snap_total totalAmount,
                       @snap_coupon couponId, @snap_version version
                FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
            );
            INSERT dbo.tc_request_idempotency(actor_id, operation, idempotency_key, target_id, fingerprint, response_payload, expires_at)
            VALUES (@actor_id, 'APPLY_ORDER_COUPON', @idempotency_key, @order_id, @effective_fingerprint, @res_json_mut, DATEADD(day, 1, @now));
        END;

        IF @started = 1 COMMIT TRANSACTION;
        SELECT @snap_subtotal subtotal_amount, @snap_discount discount_amount, @snap_total total_amount, @snap_coupon coupon_id, @snap_version version;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp03;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_ApplyPaymentResult */
CREATE   PROCEDURE dbo.usp_ApplyPaymentResult
    @order_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @payment_id uniqueidentifier = NULL,
    @verified_result nvarchar(max), @ticket_codes nvarchar(max) = NULL, @retry_failed_compensation bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp09;
    BEGIN TRY
        IF ISJSON(@verified_result) <> 1 THROW 51390, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @event_id uniqueidentifier, @hold_id uniqueidentifier, @owner_id uniqueidentifier,
                @order_status varchar(30), @hold_status varchar(20), @expires datetime2(3),
                @event_status varchar(30), @total decimal(19,0), @payment_status varchar(20),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result NOT IN ('CAPTURED', 'FAILED', 'UNKNOWN') THROW 51391, 'Unsupported payment result', 1;
        SELECT @event_id = event_id, @hold_id = hold_id, @owner_id = user_id, @order_status = status, @total = total_amount
        FROM dbo.tc_orders WHERE id = @order_id;
        IF @event_id IS NULL THROW 51392, 'Order not found', 1;
        SELECT @event_status = status FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @hold_status = status, @expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;
        SELECT 1 FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        IF @payment_id IS NULL BEGIN
            IF @actor_id IS NULL OR @actor_id <> @owner_id OR @total <> 0 OR @result <> 'CAPTURED'
                THROW 51393, 'Only the owner may complete a zero-value order', 1;
        END ELSE BEGIN
            IF IS_ROLEMEMBER('tc_buyer') = 1 AND IS_ROLEMEMBER('tc_worker') <> 1
                THROW 51389, 'Buyer principal cannot apply a paid provider result', 1;
            SELECT @payment_status = status FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK)
            WHERE id = @payment_id AND order_id = @order_id AND amount = @total;
            IF @payment_status IS NULL THROW 51394, 'Payment does not match order amount', 1;
            IF @payment_status = 'CAPTURED' AND @order_status = 'PAID' BEGIN
                SELECT @order_status order_status, @payment_status payment_status;
                IF @started = 1 COMMIT TRANSACTION; RETURN;
            END;
            IF @payment_status = 'CAPTURED' AND @result <> 'CAPTURED' BEGIN
                SELECT @order_status order_status, @payment_status payment_status;
                IF @started = 1 COMMIT TRANSACTION; RETURN;
            END;
            UPDATE dbo.tc_payments SET status = @result,
                   provider_reference = COALESCE(@provider_reference, provider_reference),
                   captured_at = CASE WHEN @result = 'CAPTURED' THEN COALESCE(captured_at, @now) ELSE captured_at END,
                   version = version + 1 WHERE id = @payment_id;
        END;
        IF @result <> 'CAPTURED' BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_status order_status, @result payment_status; RETURN;
        END;
        IF @order_status = 'PAID' BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_status order_status, @result payment_status; RETURN;
        END;
        IF @event_status <> 'PUBLISHED' OR @hold_status <> 'ACTIVE' OR @expires <= @now BEGIN
            IF @payment_id IS NULL THROW 51395, 'Zero-value order expired before completion', 1;
            IF NOT EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE payment_id = @payment_id AND purpose = 'PAYMENT_COMPENSATION' AND status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED'))
            BEGIN
                IF @retry_failed_compensation = 0 AND EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE payment_id = @payment_id AND purpose = 'PAYMENT_COMPENSATION' AND status = 'FAILED')
                    THROW 51396, 'Compensation retry requires explicit authorization', 1;
                INSERT dbo.tc_refunds(payment_id, purpose, amount) VALUES (@payment_id, 'PAYMENT_COMPENSATION', @total);
            END;
            IF @started = 1 COMMIT TRANSACTION;
            SELECT CAST('COMPENSATION_PENDING' AS varchar(30)) order_status, CAST('CAPTURED' AS varchar(20)) payment_status; RETURN;
        END;
        IF ISJSON(@ticket_codes) <> 1 THROW 51397, 'Ticket codes must be a JSON array', 1;
        DECLARE @issued TABLE(ordinal int PRIMARY KEY, ticket_code varchar(100), qr_hash varbinary(64));
        INSERT @issued
        SELECT CONVERT(int, [key]) + 1, JSON_VALUE(value, '$.ticketCode'),
               TRY_CONVERT(varbinary(64), JSON_VALUE(value, '$.qrSecretHashHex'), 2)
        FROM OPENJSON(@ticket_codes);
        DECLARE @expected int = (SELECT SUM(quantity) FROM dbo.tc_order_items WHERE order_id = @order_id);
        IF @expected IS NULL OR @expected <> (SELECT COUNT(*) FROM @issued)
           OR EXISTS (SELECT 1 FROM @issued WHERE ticket_code IS NULL OR qr_hash IS NULL)
           OR EXISTS (SELECT ticket_code FROM @issued GROUP BY ticket_code HAVING COUNT(*) > 1)
            THROW 51398, 'Ticket identities do not match order quantity', 1;
        SELECT z.id FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.zone_id = z.id) ORDER BY z.id;
        SELECT s.id FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.seat_id = s.id) ORDER BY s.id;
        IF EXISTS (SELECT 1 FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
                   WHERE oi.order_id = @order_id AND s.status <> 'HELD')
            THROW 51388, 'Held seat inventory is inconsistent', 1;
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (
                SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_order_items
                WHERE order_id = @order_id AND seat_id IS NULL GROUP BY zone_id
            ) q ON q.zone_id = z.id WHERE z.held_quantity < q.quantity
        ) THROW 51387, 'Held standing inventory is inconsistent', 1;
        UPDATE s SET status = 'SOLD', version = s.version + 1 FROM dbo.tc_seats s
        JOIN dbo.tc_order_items oi ON oi.seat_id = s.id WHERE oi.order_id = @order_id AND s.status = 'HELD';
        UPDATE z SET held_quantity = held_quantity - q.quantity, sold_quantity = sold_quantity + q.quantity, version = z.version + 1
        FROM dbo.tc_zones z JOIN (SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_order_items
                                  WHERE order_id = @order_id AND seat_id IS NULL GROUP BY zone_id) q ON q.zone_id = z.id;
        UPDATE dbo.tc_ticket_holds SET status = 'CONSUMED', version = version + 1 WHERE id = @hold_id AND status = 'ACTIVE';
        UPDATE dbo.tc_coupon_redemptions SET status = 'CONSUMED', consumed_at = @now WHERE order_id = @order_id AND status = 'RESERVED';
        UPDATE dbo.tc_orders SET status = 'PAID', paid_at = @now, version = version + 1 WHERE id = @order_id;
        WITH allocations AS (
            SELECT *, ROW_NUMBER() OVER (ORDER BY order_item_id, ticket_ordinal) ordinal
            FROM dbo.fn_AllocateTicketPaidAmounts(@order_id)
        )
        INSERT dbo.tc_tickets(order_item_id, ticket_code, qr_secret_hash, paid_amount)
        SELECT a.order_item_id, i.ticket_code, i.qr_hash, a.paid_amount FROM allocations a JOIN @issued i ON i.ordinal = a.ordinal;
        IF (SELECT SUM(paid_amount) FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE oi.order_id = @order_id) <> @total
            THROW 51399, 'Ticket paid amount allocation mismatch', 1;
        INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('ORDER_PAID', 'ORDER', @order_id, CONCAT(N'{"orderId":"', @order_id, N'"}'), CONCAT('order-paid:', @order_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT CAST('PAID' AS varchar(30)) order_status, @result payment_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp09;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_ApplyRefundResult */
CREATE   PROCEDURE dbo.usp_ApplyRefundResult @refund_id uniqueidentifier, @verified_result nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp11;
    BEGIN TRY
        IF COALESCE(IS_ROLEMEMBER('tc_worker'), 0) <> 1 AND COALESCE(IS_ROLEMEMBER('db_owner'), 0) <> 1
            THROW 51409, 'Worker principal required', 1;
        IF ISJSON(@verified_result) <> 1 THROW 51410, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @request_id uniqueidentifier, @payment_id uniqueidentifier, @purpose varchar(30),
                @amount decimal(19,0), @current varchar(20), @order_id uniqueidentifier,
                @event_id uniqueidentifier, @payment_amount decimal(19,0),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result NOT IN ('SUCCEEDED', 'FAILED', 'UNKNOWN') THROW 51411, 'Unsupported refund result', 1;
        IF @provider_reference IS NULL OR LEN(@provider_reference) = 0 THROW 51415, 'Provider reference is required', 1;

        SELECT @payment_id = payment_id FROM dbo.tc_refunds WHERE id = @refund_id;
        IF @payment_id IS NULL THROW 51412, 'Refund not found', 1;
        SELECT @order_id = order_id FROM dbo.tc_payments WHERE id = @payment_id;
        SELECT @event_id = event_id FROM dbo.tc_orders WHERE id = @order_id;
        SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT 1 FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        SELECT @payment_amount = amount FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK) WHERE id = @payment_id;
        SELECT id FROM dbo.tc_refunds WITH (UPDLOCK, HOLDLOCK) WHERE payment_id = @payment_id ORDER BY id;
        SELECT @request_id = refund_request_id, @purpose = purpose, @amount = amount, @current = status
        FROM dbo.tc_refunds WHERE id = @refund_id AND payment_id = @payment_id;
        IF @current IS NULL THROW 51412, 'Refund not found', 1;
        IF @request_id IS NOT NULL
            SELECT 1 FROM dbo.tc_refund_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;

        IF @current = 'SUCCEEDED' BEGIN
            SELECT @refund_id refund_id, @current refund_status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @current = 'FAILED' AND @result <> 'FAILED'
            THROW 51414, 'Failed refund is terminal; create an explicitly authorized retry', 1;
        IF @result = 'SUCCEEDED' AND
           (SELECT COALESCE(SUM(amount), 0) FROM dbo.tc_refunds WHERE payment_id = @payment_id AND status = 'SUCCEEDED') + @amount > @payment_amount
            THROW 51413, 'Successful refunds exceed captured payment', 1;

        IF @result = 'SUCCEEDED' AND @purpose = 'CUSTOMER_REFUND' BEGIN
            SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id ORDER BY t.id;
            SELECT z.id FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.zone_id = z.id) ORDER BY z.id;
            SELECT s.id FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.seat_id = s.id) ORDER BY s.id;
            IF EXISTS (
                SELECT 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt
                    JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                    WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL AND t.status = 'REFUND_PENDING'
                    GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id WHERE z.sold_quantity < q.quantity)
                THROW 51416, 'Sold standing inventory is inconsistent', 1;
        END;

        UPDATE dbo.tc_refunds SET status = @result, provider_reference = @provider_reference,
               processed_at = CASE WHEN @result IN ('SUCCEEDED', 'FAILED') THEN @now ELSE processed_at END,
               version = version + 1 WHERE id = @refund_id;

        IF @result = 'SUCCEEDED' AND @purpose = 'CUSTOMER_REFUND' BEGIN
            UPDATE s SET status = 'AVAILABLE', version = s.version + 1
            FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
            JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id AND t.status = 'REFUND_PENDING' AND s.status = 'SOLD';
            UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1
            FROM dbo.tc_zones z JOIN (
                SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt
                JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL AND t.status = 'REFUND_PENDING'
                GROUP BY oi.zone_id
            ) q ON q.zone_id = z.id;
            UPDATE t SET status = 'REFUNDED', version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id AND t.status = 'REFUND_PENDING';
            UPDATE dbo.tc_refund_request_tickets SET is_open = 0 WHERE refund_request_id = @request_id;
            UPDATE dbo.tc_refund_requests SET status = 'COMPLETED', version = version + 1 WHERE id = @request_id;
        END;

        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        VALUES (CONCAT('REFUND_', @result), 'REFUND', @refund_id, @provider_reference);
        IF @result = 'SUCCEEDED' INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('REFUND_SUCCEEDED', 'REFUND', @refund_id,
                CONCAT(N'{"refundId":"', @refund_id, N'"}'), CONCAT('refund-succeeded:', @refund_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @refund_id refund_id, @result refund_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp11;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_ApproveOrganization */
/* Canonical names delegate to the proven legacy transaction paths. */
CREATE   PROCEDURE dbo.usp_ApproveOrganization
    @organization_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @initial_commission_policy nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM dbo.tc_organization_requests WHERE id = @organization_id)
    BEGIN
        EXEC dbo.usp_ApproveOrganizationRequest
            @request_id = @organization_id,
            @actor_id = @actor_id,
            @initial_commission_policy = @initial_commission_policy;
        RETURN;
    END;

    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 52610, 'Administrator role required', 1;

        DECLARE @status varchar(30), @requester_id uniqueidentifier,
                @rate decimal(7,4), @fixed decimal(19,0),
                @effective_from datetime2(3), @effective_to datetime2(3);
        SELECT @status = status, @requester_id = requester_id
        FROM dbo.tc_organizations WITH (UPDLOCK, HOLDLOCK) WHERE id = @organization_id;
        IF @status IS NULL THROW 52611, 'Organization not found', 1;
        IF @status = 'APPROVED' BEGIN COMMIT; SELECT @organization_id organization_id, @status status; RETURN; END;
        IF @status <> 'PENDING_APPROVAL' THROW 52612, 'Organization is not pending approval', 1;

        SELECT @rate = TRY_CONVERT(decimal(7,4), JSON_VALUE(@initial_commission_policy, '$.ratePercent')),
               @fixed = TRY_CONVERT(decimal(19,0), JSON_VALUE(@initial_commission_policy, '$.fixedFee')),
               @effective_from = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveFrom')),
               @effective_to = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveTo'));
        IF ISJSON(@initial_commission_policy) <> 1 OR @rate IS NULL OR @fixed IS NULL
           OR @effective_from IS NULL OR @effective_to IS NULL
            THROW 52613, 'Invalid initial commission policy', 1;

        INSERT dbo.tc_commission_rules(organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES (@organization_id, @rate, @fixed, @effective_from, @effective_to);
        IF @requester_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships
            WHERE user_id = @requester_id AND organization_id = @organization_id
        )
            INSERT dbo.tc_organization_memberships(user_id, organization_id, role)
            VALUES (@requester_id, @organization_id, 'MANAGER');
        UPDATE dbo.tc_organizations
        SET status = 'APPROVED', decided_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME(), rejection_reason = NULL
        WHERE id = @organization_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'ORGANIZATION_APPROVED', 'ORGANIZATION', @organization_id);
        COMMIT;
        SELECT @organization_id organization_id, CAST('APPROVED' AS varchar(30)) status;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
/* Stored Procedure: usp_ApproveOrganizationRequest */
CREATE   PROCEDURE dbo.usp_ApproveOrganizationRequest
    @request_id uniqueidentifier, @actor_id uniqueidentifier, @initial_commission_policy nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp01;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51201, 'Administrator role required', 1;
        DECLARE @status varchar(30), @organization_id uniqueidentifier, @applicant_id uniqueidentifier,
                @name nvarchar(200), @email nvarchar(320), @phone nvarchar(30), @description nvarchar(2000);
        SELECT @status = status, @organization_id = organization_id, @applicant_id = applicant_id,
               @name = name, @email = contact_email, @phone = contact_phone, @description = description
        FROM dbo.tc_organization_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;
        IF @status IS NULL THROW 51202, 'Organization request not found', 1;
        IF @status = 'APPROVED' BEGIN
            SELECT @organization_id organization_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> 'PENDING' THROW 51203, 'Organization request is already rejected', 1;
        DECLARE @rate decimal(7,4) = TRY_CONVERT(decimal(7,4), JSON_VALUE(@initial_commission_policy, '$.ratePercent')),
                @fixed decimal(19,0) = TRY_CONVERT(decimal(19,0), JSON_VALUE(@initial_commission_policy, '$.fixedFee')),
                @from datetime2(3) = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveFrom')),
                @to datetime2(3) = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveTo'));
        IF ISJSON(@initial_commission_policy) <> 1 OR @rate IS NULL OR @fixed IS NULL OR @from IS NULL OR @to IS NULL
            THROW 51204, 'Invalid initial commission policy', 1;
        SET @organization_id = NEWID();
        INSERT dbo.tc_organizations(id, name, contact_email, contact_phone, description)
        VALUES (@organization_id, @name, @email, @phone, @description);
        INSERT dbo.tc_commission_rules(organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES (@organization_id, @rate, @fixed, @from, @to);
        INSERT dbo.tc_organization_memberships(user_id, organization_id, role) VALUES (@applicant_id, @organization_id, 'MANAGER');
        UPDATE dbo.tc_organization_requests SET reviewer_id = @actor_id, organization_id = @organization_id,
               status = 'APPROVED', decided_at = SYSUTCDATETIME(), rejection_reason = NULL, version = version + 1
        WHERE id = @request_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'ORGANIZATION_REQUEST_APPROVED', 'ORGANIZATION_REQUEST', @request_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @organization_id organization_id, CAST('APPROVED' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp01;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_BeginOrderPayment */
CREATE   PROCEDURE dbo.usp_BeginOrderPayment @order_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp08;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @hold_id uniqueidentifier, @amount decimal(19,0), @payment_id uniqueidentifier,
                @txn_ref varchar(100), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id = o.event_id, @hold_id = o.hold_id, @amount = o.total_amount
        FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_ticket_holds h WITH (UPDLOCK, HOLDLOCK) ON h.id = o.hold_id
        WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = 'PENDING_PAYMENT' AND h.status = 'ACTIVE' AND h.expires_at > @now;
        IF @event_id IS NULL THROW 51280, 'Order cannot begin payment', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = 'PUBLISHED' AND @now < sale_end)
            THROW 51281, 'Event is unavailable for payment', 1;
        IF @amount = 0 THROW 51282, 'Zero-value order must use the free completion flow', 1;
        SELECT TOP (1) @payment_id = id, @txn_ref = txn_ref FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK)
        WHERE order_id = @order_id AND status IN ('PENDING', 'UNKNOWN') ORDER BY created_at DESC;
        IF @payment_id IS NULL BEGIN
            SET @payment_id = NEWID(); SET @txn_ref = CONCAT('PAY-', REPLACE(CONVERT(varchar(36), @payment_id), '-', ''));
            INSERT dbo.tc_payments(id, order_id, txn_ref, amount) VALUES (@payment_id, @order_id, @txn_ref, @amount);
        END;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @payment_id payment_id, @txn_ref txn_ref, @amount amount, CAST('VND' AS char(3)) currency;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp08;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_CalculateEventSettlement */
CREATE   PROCEDURE dbo.usp_CalculateEventSettlement @event_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp14;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51540, 'Administrator role required', 1;
        DECLARE @rule_id uniqueidentifier, @rate decimal(7,4), @fixed decimal(19,0), @settlement_id uniqueidentifier,
                @settlement_status varchar(20), @gross decimal(19,0), @refund decimal(19,0), @commission decimal(19,0), @net decimal(19,0);
        SELECT @rule_id = commission_rule_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @rule_id IS NULL THROW 51541, 'Event has no applied commission rule', 1;
        SELECT @rate = rate_percent, @fixed = fixed_fee FROM dbo.tc_commission_rules WHERE id = @rule_id;
        SELECT o.id FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK) WHERE o.event_id = @event_id ORDER BY o.id;
        SELECT @settlement_id = id, @settlement_status = status FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id;
        IF @settlement_status IN ('CONFIRMED', 'PAID') THROW 51542, 'Confirmed settlement cannot be recalculated', 1;
        DECLARE @items TABLE(order_id uniqueidentifier PRIMARY KEY, gross decimal(19,0), refund decimal(19,0), commission decimal(19,0), net decimal(19,0));
        INSERT @items
        SELECT order_id, total_amount, ticket_refund_amount,
               dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed),
               remaining_ticket_amount - dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed)
        FROM dbo.vw_OrderFinancialSummary WHERE event_id = @event_id AND status = 'PAID';
        SELECT @gross = COALESCE(SUM(gross), 0), @refund = COALESCE(SUM(refund), 0),
               @commission = COALESCE(SUM(commission), 0), @net = COALESCE(SUM(net), 0) FROM @items;
        IF NOT EXISTS (SELECT 1 FROM @items) AND @settlement_id IS NULL BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT CAST(NULL AS uniqueidentifier) settlement_id, @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable; RETURN;
        END;
        IF @settlement_id IS NULL BEGIN SET @settlement_id = NEWID(); INSERT dbo.tc_settlements(id, event_id) VALUES (@settlement_id, @event_id); END;
        DELETE FROM dbo.tc_settlement_items WHERE settlement_id = @settlement_id;
        INSERT dbo.tc_settlement_items(settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
        SELECT @settlement_id, order_id, gross, refund, commission, net FROM @items;
        UPDATE dbo.tc_settlements SET gross_revenue = @gross, total_refund = @refund,
               total_commission = @commission, net_payable = @net, version = version + 1 WHERE id = @settlement_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @settlement_id settlement_id, @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp14;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_CancelEvent */
CREATE   PROCEDURE dbo.usp_CancelEvent @event_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp13;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51530, 'Administrator role required', 1;
        DECLARE @status varchar(30), @start datetime2(3);
        SELECT @status = status, @start = start_time
        FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @status IS NULL THROW 51531, 'Event not found', 1;
        IF @status = 'CANCELLED' BEGIN
            SELECT @event_id event_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> 'PUBLISHED' OR SYSUTCDATETIME() >= @start
            THROW 51532, 'Only a future published event can be cancelled', 1;
        UPDATE dbo.tc_events SET status = 'CANCELLED', version = version + 1 WHERE id = @event_id;
        INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('EVENT_CANCELLED', 'EVENT', @event_id,
                CONCAT(N'{"eventId":"', @event_id, N'"}'), CONCAT('event-cancelled:', @event_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @event_id event_id, CAST('CANCELLED' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp13;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_CheckInTicket */
CREATE   PROCEDURE dbo.usp_CheckInTicket
    @event_id uniqueidentifier, @actor_id uniqueidentifier, @ticket_code varchar(100)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp04;
    BEGIN TRY
        DECLARE @organization_id uniqueidentifier, @ticket_id uniqueidentifier, @ticket_event uniqueidentifier,
                @ticket_status varchar(30), @result varchar(30), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @organization_id = organization_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @organization_id IS NULL THROW 51240, 'Event not found', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_organization_memberships WHERE user_id = @actor_id
                       AND organization_id = @organization_id AND active = 1 AND role IN ('MANAGER', 'CHECK_IN_STAFF'))
            THROW 51241, 'Active event staff membership required', 1;
        SELECT @ticket_id = t.id, @ticket_status = t.status, @ticket_event = o.event_id
        FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        JOIN dbo.tc_orders o ON o.id = oi.order_id WHERE t.ticket_code = @ticket_code;
        IF @ticket_id IS NULL SET @result = 'NOT_FOUND';
        ELSE IF @ticket_event <> @event_id SET @result = 'WRONG_EVENT';
        ELSE IF EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND status = 'CANCELLED') SET @result = 'EVENT_CANCELLED';
        ELSE IF @now < (SELECT opens_at FROM dbo.fn_GetEventCheckInWindow(@event_id, @now)) SET @result = 'TOO_EARLY';
        ELSE IF @now >= (SELECT closes_at FROM dbo.fn_GetEventCheckInWindow(@event_id, @now)) SET @result = 'TOO_LATE';
        ELSE IF @ticket_status = 'USED' SET @result = 'ALREADY_USED';
        ELSE IF @ticket_status <> 'ACTIVE' SET @result = 'NOT_ACTIVE';
        ELSE BEGIN UPDATE dbo.tc_tickets SET status = 'USED', version = version + 1 WHERE id = @ticket_id; SET @result = 'SUCCESS'; END;
        INSERT dbo.tc_check_ins(event_id, ticket_id, actor_id, result, scanned_at)
        VALUES (@event_id, CASE WHEN @result = 'NOT_FOUND' THEN NULL ELSE @ticket_id END, @actor_id, @result, @now);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @result result, @now scanned_at, @ticket_id ticket_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp04;
        THROW;
    END CATCH
END;
GO
/* View: vw_SettlementPayoutBalance */
CREATE   VIEW dbo.vw_SettlementPayoutBalance
AS
WITH payouts AS (
    SELECT settlement_id,
           SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END) paid_amount,
           SUM(CASE WHEN status = 'PENDING' THEN amount ELSE 0 END) pending_amount
    FROM dbo.tc_payouts
    GROUP BY settlement_id
)
SELECT s.id settlement_id, s.event_id, s.gross_revenue, s.total_refund, s.total_commission,
       s.net_payable, s.status, s.confirmed_at,
       COALESCE(p.paid_amount, 0) paid_amount,
       COALESCE(p.pending_amount, 0) pending_amount,
       CASE WHEN s.net_payable > COALESCE(p.paid_amount, 0)
            THEN s.net_payable - COALESCE(p.paid_amount, 0) ELSE 0 END remaining_amount,
       CASE WHEN s.status = 'CONFIRMED'
                 AND s.net_payable > COALESCE(p.paid_amount, 0) + COALESCE(p.pending_amount, 0)
            THEN s.net_payable - COALESCE(p.paid_amount, 0) - COALESCE(p.pending_amount, 0)
            ELSE 0 END available_amount
FROM dbo.tc_settlements s
LEFT JOIN payouts p ON p.settlement_id = s.id;
GO
/* Stored Procedure: usp_ConfirmEventSettlement */
CREATE   PROCEDURE dbo.usp_ConfirmEventSettlement @settlement_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp15;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51550, 'Administrator role required', 1;
        DECLARE @event_id uniqueidentifier, @status varchar(20), @net decimal(19,0);
        SELECT @event_id = event_id, @status = status, @net = net_payable FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK) WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51551, 'Settlement not found', 1;
        IF @status IN ('CONFIRMED', 'PAID') BEGIN
            SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT id FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id ORDER BY id;
        IF EXISTS (SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event_id, SYSUTCDATETIME()))
            THROW 51552, 'Settlement has unresolved blockers', 1;
        EXEC dbo.usp_CalculateEventSettlement @event_id, @actor_id;
        SELECT @net = net_payable FROM dbo.tc_settlements WHERE id = @settlement_id;
        UPDATE dbo.tc_settlements SET status = CASE WHEN @net = 0 THEN 'PAID' ELSE 'CONFIRMED' END,
               confirmed_at = SYSUTCDATETIME(), version = version + 1 WHERE id = @settlement_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'SETTLEMENT_CONFIRMED', 'SETTLEMENT', @settlement_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp15;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_ConfirmSettlement */
CREATE   PROCEDURE dbo.usp_ConfirmSettlement
    @settlement_id uniqueidentifier,
    @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp15;
    BEGIN TRY
        IF @settlement_id IS NULL OR @actor_id IS NULL THROW 51710, 'Settlement and actor are required', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51711, 'Administrator role required', 1;

        DECLARE @event_id uniqueidentifier, @status varchar(20), @net decimal(19,0), @lock_count bigint;
        SELECT @event_id = event_id FROM dbo.tc_settlements WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51712, 'Settlement not found', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refund_requests rr WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = rr.order_id WHERE o.event_id = @event_id;
        SELECT @status = status, @net = net_payable
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @settlement_id AND event_id = @event_id;
        IF @status IN ('CONFIRMED', 'PAID') BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
            RETURN;
        END;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_payments p WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refunds r WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        JOIN dbo.tc_orders o ON o.id = oi.order_id WHERE o.event_id = @event_id;

        IF EXISTS (SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event_id, SYSUTCDATETIME()))
            THROW 51713, 'Settlement has unresolved blockers', 1;
        EXEC dbo.usp_RecalculateSettlement @event_id, @actor_id, 0;
        SELECT @net = net_payable FROM dbo.tc_settlements WHERE id = @settlement_id;
        UPDATE dbo.tc_settlements
        SET status = CASE WHEN @net = 0 THEN 'PAID' ELSE 'CONFIRMED' END,
            confirmed_at = SYSUTCDATETIME(), version = version + 1
        WHERE id = @settlement_id AND status = 'DRAFT';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'SETTLEMENT_CONFIRMED', 'SETTLEMENT', @settlement_id);

        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp15;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_CreateOrderFromHold */
CREATE   PROCEDURE dbo.usp_CreateOrderFromHold
    @hold_id uniqueidentifier,
    @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp06;
    BEGIN TRY
        IF @actor_id IS NULL THROW 51263, 'Hold or order owner required', 1;
        IF @hold_id IS NULL THROW 51260, 'Active unexpired hold owned by actor required', 1;

        DECLARE @order_id uniqueidentifier, @order_user_id uniqueidentifier, @order_status varchar(30),
                @event_id uniqueidentifier, @hold_user_id uniqueidentifier, @hold_status varchar(20),
                @hold_expires datetime2(3), @subtotal decimal(19,0), @now datetime2(3);

        -- Read event_id without lock to determine lock hierarchy (DB05)
        SELECT @event_id = event_id, @hold_user_id = user_id FROM dbo.tc_ticket_holds WHERE id = @hold_id;
        IF @event_id IS NULL THROW 51260, 'Active unexpired hold owned by actor required', 1;

        -- Lock hierarchy: 1. Event -> 2. Hold -> 3. Order (DB05)
        DECLARE @lock_dummy int;
        SELECT @lock_dummy = 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;

        SELECT @hold_user_id = user_id, @hold_status = status, @hold_expires = expires_at
        FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;

        SELECT @order_id = id, @order_user_id = user_id, @order_status = status
        FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE hold_id = @hold_id;

        -- Authoritative time recheck after lock wait (DB05)
        SET @now = SYSUTCDATETIME();

        -- Existing Order branch (DB01): check owner and valid replay status
        IF @order_id IS NOT NULL BEGIN
            IF @order_user_id <> @actor_id THROW 51263, 'Hold or order owner required', 1;
            IF @order_status NOT IN ('PENDING_PAYMENT', 'PAID')
                THROW 51264, 'Order is not in replayable status', 1;

            SELECT id order_id, order_code, subtotal_amount, discount_amount, total_amount
            FROM dbo.tc_orders WHERE id = @order_id;
            IF @started = 1 COMMIT TRANSACTION;
            RETURN;
        END;

        -- New Order branch: verify hold is active, owned by actor, and unexpired
        IF @hold_user_id <> @actor_id OR @hold_status <> 'ACTIVE' OR @hold_expires <= @now
            THROW 51260, 'Active unexpired hold owned by actor required', 1;

        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND status = 'PUBLISHED')
            THROW 51261, 'Event is unavailable', 1;

        SELECT @subtotal = SUM(unit_price * quantity) FROM dbo.tc_ticket_hold_items WHERE hold_id = @hold_id;
        IF @subtotal IS NULL THROW 51262, 'Hold has no items', 1;

        SET @order_id = NEWID();
        INSERT dbo.tc_orders(id, user_id, event_id, hold_id, order_code, subtotal_amount, discount_amount, total_amount, updated_at)
        VALUES (@order_id, @actor_id, @event_id, @hold_id, CONCAT('ORD-', REPLACE(CONVERT(varchar(36), @order_id), '-', '')), @subtotal, 0, @subtotal, @now);

        INSERT dbo.tc_order_items(order_id, zone_id, seat_id, quantity, unit_price, zone_name_snapshot, seat_label_snapshot, updated_at)
        SELECT @order_id, hi.zone_id, hi.seat_id, hi.quantity, hi.unit_price, z.name, s.label, @now
        FROM dbo.tc_ticket_hold_items hi
        JOIN dbo.tc_zones z ON z.id = hi.zone_id
        LEFT JOIN dbo.tc_seats s ON s.id = hi.seat_id
        WHERE hi.hold_id = @hold_id;

        DECLARE @snap_id uniqueidentifier, @snap_code varchar(64), @snap_sub decimal(19,0), @snap_disc decimal(19,0), @snap_tot decimal(19,0);
        SELECT @snap_id = id, @snap_code = order_code, @snap_sub = subtotal_amount, @snap_disc = discount_amount, @snap_tot = total_amount
        FROM dbo.tc_orders WHERE id = @order_id;

        IF @started = 1 COMMIT TRANSACTION;
        SELECT @snap_id order_id, @snap_code order_code, @snap_sub subtotal_amount, @snap_disc discount_amount, @snap_tot total_amount;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp06;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_CreateRefundRequest */
CREATE   PROCEDURE dbo.usp_CreateRefundRequest
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @ticket_ids nvarchar(max), @reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp05;
    BEGIN TRY
        IF ISJSON(@ticket_ids) <> 1 OR NULLIF(LTRIM(RTRIM(@reason)), N'') IS NULL THROW 51250, 'Ticket list and reason are required', 1;
        DECLARE @tickets TABLE(id uniqueidentifier NOT NULL PRIMARY KEY);
        INSERT @tickets SELECT TRY_CONVERT(uniqueidentifier, value) FROM OPENJSON(@ticket_ids);
        IF NOT EXISTS (SELECT 1 FROM @tickets) OR EXISTS (SELECT 1 FROM @tickets WHERE id IS NULL) THROW 51251, 'Invalid ticket list', 1;
        IF (SELECT COUNT(*) FROM OPENJSON(@ticket_ids)) <> (SELECT COUNT(*) FROM @tickets) THROW 51252, 'Duplicate ticket id', 1;
        DECLARE @event_id uniqueidentifier, @start datetime2(3), @request_id uniqueidentifier = NEWID(), @amount decimal(19,0);
        SELECT @event_id = o.event_id, @start = e.start_time FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_events e WITH (UPDLOCK, HOLDLOCK) ON e.id = o.event_id WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = 'PAID';
        IF @event_id IS NULL THROW 51253, 'Paid order owned by actor required', 1;
        IF SYSUTCDATETIME() >= @start THROW 51254, 'Refund requests close at event start', 1;
        SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN @tickets x ON x.id = t.id ORDER BY t.id;
        IF EXISTS (
            SELECT 1 FROM @tickets x LEFT JOIN dbo.tc_tickets t ON t.id = x.id
            LEFT JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE t.id IS NULL OR oi.order_id <> @order_id OR t.status <> 'ACTIVE'
               OR EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.ticket_id = x.id AND rt.is_open = 1)
        ) THROW 51255, 'One or more tickets are not refundable', 1;
        INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
        VALUES (@request_id, @order_id, @actor_id, @reason, 'CUSTOMER_REQUEST');
        INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id) SELECT @request_id, id FROM @tickets;
        UPDATE t SET status = 'REFUND_PENDING', version = version + 1 FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'REFUND_REQUEST_CREATED', 'REFUND_REQUEST', @request_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @request_id request_id, @amount requested_amount;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp05;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_CreateTicketHold */
CREATE   PROCEDURE dbo.usp_CreateTicketHold
    @user_id uniqueidentifier, @event_id uniqueidentifier, @selections nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp02;
    BEGIN TRY
        DECLARE @now datetime2(3) = SYSUTCDATETIME(), @hold_id uniqueidentifier = NEWID(), @expires datetime2(3);
        IF ISJSON(@selections) <> 1 THROW 51220, 'Selections must be a JSON array', 1;
        DECLARE @items TABLE(row_no int NOT NULL, zone_id uniqueidentifier NULL, seat_id uniqueidentifier NULL, quantity int NULL);
        INSERT @items SELECT CONVERT(int, [key]), TRY_CONVERT(uniqueidentifier, JSON_VALUE(value, '$.zoneId')),
                             TRY_CONVERT(uniqueidentifier, JSON_VALUE(value, '$.seatId')),
                             TRY_CONVERT(int, JSON_VALUE(value, '$.quantity')) FROM OPENJSON(@selections);
        IF NOT EXISTS (SELECT 1 FROM @items) OR (SELECT SUM(quantity) FROM @items) NOT BETWEEN 1 AND 8
            THROW 51221, 'A hold must contain between 1 and 8 tickets', 1;
        IF EXISTS (SELECT 1 FROM @items WHERE zone_id IS NULL OR quantity IS NULL OR quantity <= 0)
            THROW 51222, 'Invalid hold selection', 1;
        IF EXISTS (SELECT seat_id FROM @items WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING COUNT(*) > 1)
            THROW 51223, 'Duplicate seat selection', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @user_id AND status = 'ACTIVE' AND email_verified_at IS NOT NULL)
            THROW 51224, 'Active verified user required', 1;
        DECLARE @old_hold uniqueidentifier, @old_expires datetime2(3);
        SELECT @old_hold = id, @old_expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK)
        WHERE user_id = @user_id AND status = 'ACTIVE';
        IF @old_hold IS NOT NULL AND @old_expires > @now THROW 51225, 'User already has an active hold', 1;
        IF @old_hold IS NOT NULL EXEC dbo.usp_ReleaseTicketHold @old_hold, @user_id, 'EXPIRED', @suppress_result = 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = 'PUBLISHED' AND @now >= sale_start AND @now < sale_end)
            THROW 51226, 'Event is not on sale', 1;
        IF EXISTS (
            SELECT 1 FROM @items i LEFT JOIN dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) ON z.id = i.zone_id
            WHERE z.id IS NULL OR z.event_id <> @event_id OR (z.type = 'SEATED' AND (i.seat_id IS NULL OR i.quantity <> 1))
               OR (z.type = 'STANDING' AND i.seat_id IS NOT NULL)
        ) THROW 51227, 'Selection does not match event zone type', 1;
        IF EXISTS (
            SELECT 1 FROM @items i JOIN dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) ON s.id = i.seat_id
            WHERE i.seat_id IS NOT NULL AND (s.zone_id <> i.zone_id OR s.status <> 'AVAILABLE')
        ) OR EXISTS (SELECT 1 FROM @items i WHERE i.seat_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.tc_seats WHERE id = i.seat_id))
            THROW 51228, 'Seat is unavailable', 1;
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (SELECT zone_id, SUM(quantity) q FROM @items WHERE seat_id IS NULL GROUP BY zone_id) i ON i.zone_id = z.id
            WHERE z.capacity - z.held_quantity - z.sold_quantity < i.q
        ) THROW 51229, 'Zone capacity is unavailable', 1;
        SET @expires = DATEADD(minute, 10, @now);
        INSERT dbo.tc_ticket_holds(id, user_id, event_id, created_at, expires_at, updated_at) VALUES (@hold_id, @user_id, @event_id, @now, @expires, @now);
        INSERT dbo.tc_ticket_hold_items(hold_id, zone_id, seat_id, quantity, unit_price, created_at, updated_at)
        SELECT @hold_id, i.zone_id, i.seat_id, i.quantity, z.price, @now, @now FROM @items i JOIN dbo.tc_zones z ON z.id = i.zone_id;
        UPDATE s SET status = 'HELD', version = version + 1 FROM dbo.tc_seats s JOIN @items i ON i.seat_id = s.id;
        UPDATE z SET held_quantity = held_quantity + i.q, version = version + 1 FROM dbo.tc_zones z
        JOIN (SELECT zone_id, SUM(quantity) q FROM @items WHERE seat_id IS NULL GROUP BY zone_id) i ON i.zone_id = z.id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @hold_id hold_id, @expires expires_at;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp02;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_DecideRefund */
CREATE   PROCEDURE dbo.usp_DecideRefund
    @refund_id uniqueidentifier,
    @actor_id uniqueidentifier = NULL,
    @decision varchar(20),
    @rejection_reason nvarchar(2000) = NULL,
    @attempt_id uniqueidentifier = NULL,
    @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @request_id uniqueidentifier = COALESCE(
        (SELECT legacy_request_id FROM dbo.tc_refund_obligations WHERE id = @refund_id),
        CASE WHEN EXISTS (SELECT 1 FROM dbo.tc_refund_requests WHERE id = @refund_id) THEN @refund_id END
    );
    IF @request_id IS NULL THROW 52630, 'Refund obligation not found', 1;
    EXEC dbo.usp_DecideRefundRequest
        @request_id = @request_id,
        @actor_id = @actor_id,
        @decision = @decision,
        @rejection_reason = @rejection_reason,
        @retry_failed = @retry_failed;
END;
GO
/* Stored Procedure: usp_DecideRefundRequest */
CREATE   PROCEDURE dbo.usp_DecideRefundRequest
    @request_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @decision varchar(20),
    @rejection_reason nvarchar(1000) = NULL,
    @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_ReviewRefundRequest @request_id, @actor_id, @decision, @rejection_reason, @retry_failed;
END;
GO
/* Stored Procedure: usp_ExecutePayout */
CREATE   PROCEDURE dbo.usp_ExecutePayout
    @settlement_id uniqueidentifier, @payout_id uniqueidentifier, @actor_id uniqueidentifier,
    @amount decimal(19,0), @reference varchar(100), @verified_result varchar(20)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp16;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51560, 'Administrator role required', 1;
        IF @amount <= 0 OR @verified_result NOT IN ('PENDING', 'SUCCEEDED', 'FAILED') THROW 51561, 'Invalid payout input', 1;
        DECLARE @event_id uniqueidentifier, @settlement_status varchar(20), @net decimal(19,0), @current varchar(20),
                @paid decimal(19,0), @pending decimal(19,0);
        SELECT @event_id = event_id, @settlement_status = status, @net = net_payable
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK) WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51562, 'Settlement not found', 1;
        SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @current = status FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK) WHERE id = @payout_id;
        IF @current = 'SUCCEEDED' BEGIN
            SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @current IS NULL BEGIN
            IF @settlement_status <> 'CONFIRMED' THROW 51563, 'Only a confirmed settlement accepts a payout', 1;
            SELECT @paid = COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END), 0),
                   @pending = COALESCE(SUM(CASE WHEN status = 'PENDING' THEN amount ELSE 0 END), 0)
            FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK) WHERE settlement_id = @settlement_id;
            IF @paid + @pending + @amount > @net THROW 51564, 'Payout exceeds available settlement balance', 1;
            INSERT dbo.tc_payouts(id, settlement_id, amount, reference, status, paid_at)
            VALUES (@payout_id, @settlement_id, @amount, @reference, @verified_result,
                    CASE WHEN @verified_result = 'SUCCEEDED' THEN SYSUTCDATETIME() END);
        END ELSE BEGIN
            IF @current <> 'PENDING' THROW 51565, 'Failed payout requires a new payout id', 1;
            IF EXISTS (SELECT 1 FROM dbo.tc_payouts WHERE id = @payout_id AND (amount <> @amount OR reference <> @reference))
                THROW 51566, 'Payout replay payload mismatch', 1;
            UPDATE dbo.tc_payouts SET status = @verified_result,
                   paid_at = CASE WHEN @verified_result = 'SUCCEEDED' THEN SYSUTCDATETIME() ELSE NULL END,
                   version = version + 1 WHERE id = @payout_id;
        END;
        IF (SELECT COALESCE(SUM(amount), 0) FROM dbo.tc_payouts WHERE settlement_id = @settlement_id AND status = 'SUCCEEDED') = @net
            UPDATE dbo.tc_settlements SET status = 'PAID', version = version + 1 WHERE id = @settlement_id AND status = 'CONFIRMED';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT('PAYOUT_', @verified_result), 'PAYOUT', @payout_id, @reference);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp16;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_ProcessCancelledOrder */
CREATE   PROCEDURE dbo.usp_ProcessCancelledOrder @event_id uniqueidentifier, @order_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp17;
    BEGIN TRY
        IF IS_ROLEMEMBER('tc_worker') <> 1 AND IS_ROLEMEMBER('db_owner') <> 1
            THROW 51569, 'Worker principal required', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = 'CANCELLED')
            THROW 51570, 'Event is not cancelled', 1;
        DECLARE @hold_id uniqueidentifier, @order_status varchar(30), @owner_id uniqueidentifier,
                @request_id uniqueidentifier;
        SELECT @hold_id = hold_id, @order_status = status, @owner_id = user_id
        FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @order_id AND event_id = @event_id;
        IF @hold_id IS NULL THROW 51571, 'Order does not belong to cancelled event', 1;
        IF @order_status IN ('PENDING_PAYMENT', 'EXPIRED', 'CANCELLED') BEGIN
            IF EXISTS (SELECT 1 FROM dbo.tc_ticket_holds WHERE id = @hold_id AND status = 'ACTIVE')
                EXEC dbo.usp_ReleaseTicketHold @hold_id, NULL, 'EVENT_CANCELLED', @suppress_result = 1;
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_id order_id, CAST('RELEASED' AS varchar(30)) result; RETURN;
        END;

        SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id ORDER BY t.id;

        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        SELECT 'CANCELLED_EVENT_USED_TICKET', 'TICKET', t.id,
               CONCAT(N'{"orderId":"', @order_id, N'"}')
        FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id AND t.status = 'USED'
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_audit_logs a
                          WHERE a.action = 'CANCELLED_EVENT_USED_TICKET' AND a.aggregate_id = t.id);

        UPDATE rr SET reason_type = 'EVENT_CANCELLATION', version = version + 1
        FROM dbo.tc_refund_requests rr
        WHERE rr.order_id = @order_id AND rr.status = 'PENDING'
          AND rr.reason_type = 'CUSTOMER_REQUEST';

        UPDATE rr SET status = 'COMPLETED', decided_at = COALESCE(decided_at, SYSUTCDATETIME()),
                      version = version + 1
        FROM dbo.tc_refund_requests rr
        WHERE rr.order_id = @order_id AND rr.status = 'PENDING'
          AND rr.reason_type = 'EVENT_CANCELLATION'
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.refund_request_id = rr.id);

        DECLARE @pending TABLE(id uniqueidentifier PRIMARY KEY);
        INSERT @pending
        SELECT id FROM dbo.tc_refund_requests
        WHERE order_id = @order_id AND status = 'PENDING' AND reason_type = 'EVENT_CANCELLATION';
        WHILE EXISTS (SELECT 1 FROM @pending) BEGIN
            SELECT TOP (1) @request_id = id FROM @pending ORDER BY id;
            EXEC dbo.usp_ReviewRefundRequest @request_id, NULL, 'APPROVE', NULL, 0;
            DELETE FROM @pending WHERE id = @request_id;
        END;

        IF EXISTS (
            SELECT 1 FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = 'ACTIVE')
        BEGIN
            SET @request_id = NEWID();
            INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
            VALUES (@request_id, @order_id, @owner_id, N'Event cancelled', 'EVENT_CANCELLATION');
            INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id)
            SELECT @request_id, t.id FROM dbo.tc_tickets t
            JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = 'ACTIVE';
            UPDATE t SET status = 'REFUND_PENDING', version = version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = 'ACTIVE';
            EXEC dbo.usp_ReviewRefundRequest @request_id, NULL, 'APPROVE', NULL, 0;
        END;

        IF @started = 1 COMMIT TRANSACTION;
        SELECT @order_id order_id, @request_id refund_request_id,
               CAST('PROCESSED' AS varchar(30)) result;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp17;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_PublishEvent */
CREATE   PROCEDURE dbo.usp_PublishEvent
    @event_id uniqueidentifier, @actor_id uniqueidentifier, @commission_rule_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp12;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51520, 'Administrator role required', 1;
        DECLARE @organization_id uniqueidentifier, @status varchar(30), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @organization_id = organization_id, @status = status FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @status IS NULL THROW 51521, 'Event not found', 1;
        IF @status = 'PUBLISHED' BEGIN
            IF (SELECT commission_rule_id FROM dbo.tc_events WHERE id = @event_id) <> @commission_rule_id
                THROW 51522, 'Published event commission rule is immutable', 1;
            SELECT @event_id event_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> 'PENDING_APPROVAL' THROW 51523, 'Event is not pending approval', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_commission_rules WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @commission_rule_id AND organization_id = @organization_id
                         AND effective_from <= @now AND effective_to > @now)
            THROW 51524, 'Commission rule is not valid for event organization', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND (cover_image_url IS NULL OR sale_start >= sale_end OR sale_end > start_time OR start_time >= end_time))
            THROW 51525, 'Event content or schedule is incomplete', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_zones WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id)
            THROW 51526, 'Event requires at least one zone', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_zones z WHERE z.event_id = @event_id AND z.type = 'SEATED'
                   AND NOT EXISTS (SELECT 1 FROM dbo.tc_seats s WHERE s.zone_id = z.id))
            THROW 51527, 'Each seated zone requires seats', 1;
        UPDATE dbo.tc_events SET commission_rule_id = @commission_rule_id, status = 'PUBLISHED', version = version + 1 WHERE id = @event_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @event_id event_id, CAST('PUBLISHED' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp12;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_RecalculateSettlement */
CREATE   PROCEDURE dbo.usp_RecalculateSettlement
    @event_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @return_result bit = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp14;
    BEGIN TRY
        IF @event_id IS NULL OR @actor_id IS NULL THROW 51700, 'Event and actor are required', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51701, 'Administrator role required', 1;

        DECLARE @rule_id uniqueidentifier, @rate decimal(7,4), @fixed decimal(19,0),
                @settlement_id uniqueidentifier, @settlement_status varchar(20), @lock_count bigint,
                @gross decimal(19,0), @refund decimal(19,0), @commission decimal(19,0), @net decimal(19,0);
        SELECT @rule_id = commission_rule_id
        FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @event_id;
        IF @rule_id IS NULL THROW 51702, 'Event not found or has no applied commission rule', 1;
        SELECT @rate = rate_percent, @fixed = fixed_fee
        FROM dbo.tc_commission_rules WITH (HOLDLOCK)
        WHERE id = @rule_id;
        IF dbo.fn_CalculateCommission(0, @rate, @fixed) IS NULL THROW 51703, 'Applied commission rule is invalid', 1;

        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refund_requests rr WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = rr.order_id WHERE o.event_id = @event_id;
        SELECT @settlement_id = id, @settlement_status = status
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK)
        WHERE event_id = @event_id;
        IF @settlement_status IN ('CONFIRMED', 'PAID')
            THROW 51704, 'Confirmed settlement cannot be recalculated', 1;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_payments p WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refunds r WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        JOIN dbo.tc_orders o ON o.id = oi.order_id WHERE o.event_id = @event_id;

        DECLARE @items TABLE(
            order_id uniqueidentifier PRIMARY KEY,
            gross decimal(19,0) NOT NULL,
            refund decimal(19,0) NOT NULL,
            commission decimal(19,0) NOT NULL,
            net decimal(19,0) NOT NULL
        );
        INSERT @items(order_id, gross, refund, commission, net)
        SELECT order_id, ticket_gross_amount, successful_ticket_refund_amount,
               dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed),
               remaining_ticket_amount - dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed)
        FROM dbo.vw_OrderFinancialSummary
        WHERE event_id = @event_id AND status = 'PAID' AND ticket_gross_amount > 0;

        SELECT @gross = COALESCE(SUM(gross), 0), @refund = COALESCE(SUM(refund), 0),
               @commission = COALESCE(SUM(commission), 0), @net = COALESCE(SUM(net), 0)
        FROM @items;

        IF NOT EXISTS (SELECT 1 FROM @items) BEGIN
            IF @settlement_id IS NOT NULL BEGIN
                DELETE FROM dbo.tc_settlement_items WHERE settlement_id = @settlement_id;
                DELETE FROM dbo.tc_settlements WHERE id = @settlement_id AND status = 'DRAFT';
            END;
            IF @started = 1 COMMIT TRANSACTION;
            IF @return_result = 1
                SELECT CAST(NULL AS uniqueidentifier) settlement_id, CAST('EMPTY' AS varchar(20)) status,
                       @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable;
            RETURN;
        END;

        IF @settlement_id IS NULL BEGIN
            SET @settlement_id = NEWID();
            INSERT dbo.tc_settlements(id, event_id) VALUES (@settlement_id, @event_id);
        END;
        DELETE FROM dbo.tc_settlement_items WHERE settlement_id = @settlement_id;
        INSERT dbo.tc_settlement_items(settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
        SELECT @settlement_id, order_id, gross, refund, commission, net FROM @items;
        UPDATE dbo.tc_settlements
        SET gross_revenue = @gross, total_refund = @refund, total_commission = @commission,
            net_payable = @net, version = version + 1
        WHERE id = @settlement_id AND status = 'DRAFT';

        IF @started = 1 COMMIT TRANSACTION;
        IF @return_result = 1
            SELECT @settlement_id settlement_id, CAST('DRAFT' AS varchar(20)) status,
                   @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp14;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_RecordPayout */
CREATE   PROCEDURE dbo.usp_RecordPayout
    @settlement_id uniqueidentifier,
    @payout_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @amount decimal(19,0),
    @reference varchar(100),
    @verified_result varchar(20)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp16;
    BEGIN TRY
        IF @settlement_id IS NULL OR @payout_id IS NULL OR @actor_id IS NULL
           OR @amount IS NULL OR @amount <= 0 OR @reference IS NULL OR LEN(TRIM(@reference)) = 0
           OR @verified_result NOT IN ('PENDING', 'SUCCEEDED', 'FAILED')
            THROW 51730, 'Invalid payout input', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51731, 'Administrator role required', 1;

        DECLARE @event_id uniqueidentifier, @settlement_status varchar(20), @net decimal(19,0),
                @current_status varchar(20), @current_settlement uniqueidentifier,
                @current_amount decimal(19,0), @current_reference varchar(100),
                @paid decimal(19,0), @pending decimal(19,0), @lock_count bigint;
        SELECT @event_id = event_id FROM dbo.tc_settlements WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51732, 'Settlement not found', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @settlement_status = status, @net = net_payable
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @settlement_id AND event_id = @event_id;
        SELECT @current_settlement = settlement_id, @current_status = status,
               @current_amount = amount, @current_reference = reference
        FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @payout_id;

        IF @current_status IS NOT NULL BEGIN
            IF @current_settlement <> @settlement_id OR @current_amount <> @amount OR @current_reference <> @reference
                THROW 51733, 'Payout replay payload mismatch', 1;
            IF @current_status = 'SUCCEEDED' BEGIN
                IF @verified_result <> 'SUCCEEDED' THROW 51734, 'Successful payout is immutable', 1;
                IF @started = 1 COMMIT TRANSACTION;
                SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
                RETURN;
            END;
            IF @current_status = 'FAILED' THROW 51735, 'Failed payout requires a new payout id', 1;
            UPDATE dbo.tc_payouts
            SET status = @verified_result,
                paid_at = CASE WHEN @verified_result = 'SUCCEEDED' THEN SYSUTCDATETIME() ELSE NULL END,
                version = version + 1
            WHERE id = @payout_id AND status = 'PENDING';
        END ELSE BEGIN
            IF @settlement_status <> 'CONFIRMED'
                THROW 51736, 'Only a confirmed settlement accepts a new payout', 1;
            SELECT @paid = COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END), 0),
                   @pending = COALESCE(SUM(CASE WHEN status = 'PENDING' THEN amount ELSE 0 END), 0)
            FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK)
            WHERE settlement_id = @settlement_id;
            IF @paid + @pending + @amount > @net
                THROW 51737, 'Payout exceeds available settlement balance', 1;
            INSERT dbo.tc_payouts(id, settlement_id, amount, reference, status, paid_at)
            VALUES (@payout_id, @settlement_id, @amount, @reference, @verified_result,
                    CASE WHEN @verified_result = 'SUCCEEDED' THEN SYSUTCDATETIME() END);
        END;

        SELECT @paid = COALESCE(SUM(amount), 0)
        FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK)
        WHERE settlement_id = @settlement_id AND status = 'SUCCEEDED';
        IF @paid = @net
            UPDATE dbo.tc_settlements
            SET status = 'PAID', version = version + 1
            WHERE id = @settlement_id AND status = 'CONFIRMED';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT('PAYOUT_', @verified_result), 'PAYOUT', @payout_id, @reference);

        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp16;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_ReleaseTicketHold */
CREATE   PROCEDURE dbo.usp_ReleaseTicketHold
    @hold_id uniqueidentifier,
    @actor_id uniqueidentifier = NULL,
    @reason varchar(30),
    @suppress_result bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp07;
    BEGIN TRY
        IF @hold_id IS NULL THROW 51270, 'Ticket hold not found', 1;

        -- DB02: Validate reason strictly (reject NULL and out of domain)
        IF @reason IS NULL OR @reason NOT IN ('USER_CANCEL', 'EXPIRED', 'EVENT_CANCELLED')
            THROW 51271, 'Invalid release reason', 1;

        DECLARE @event_id uniqueidentifier, @user_id uniqueidentifier, @status varchar(20), @expires datetime2(3), @now datetime2(3);
        SELECT @event_id = h.event_id, @user_id = h.user_id FROM dbo.tc_ticket_holds h WHERE h.id = @hold_id;
        IF @event_id IS NULL THROW 51270, 'Ticket hold not found', 1;

        -- DB02: Check permissions BEFORE checking hold status
        IF @reason = 'USER_CANCEL' BEGIN
            IF @actor_id IS NULL OR @actor_id <> @user_id
                THROW 51272, 'Hold owner required', 1;
        END ELSE IF @reason IN ('EXPIRED', 'EVENT_CANCELLED') BEGIN
            IF IS_ROLEMEMBER('tc_worker') <> 1 AND IS_ROLEMEMBER('tc_platform_admin') <> 1 AND IS_ROLEMEMBER('db_owner') <> 1
               AND NOT (@reason = 'EXPIRED' AND @actor_id IS NOT NULL AND @actor_id = @user_id)
                THROW 51276, 'System role required for expired or event cancelled release', 1;
        END;

        -- Lock hierarchy: 1. Event -> 2. Hold -> 3. Order -> 4. Zones/Seats (DB05)
        -- Suppress client resultsets using variable assignment (DB06)
        DECLARE @lock_dummy int;
        SELECT @lock_dummy = 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;

        SELECT @status = status, @expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;

        SELECT @lock_dummy = 1 FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE hold_id = @hold_id;

        SET @now = SYSUTCDATETIME();

        -- DB02: Replay of inactive hold returns previous status without double releasing
        IF @status <> 'ACTIVE' BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            IF @suppress_result = 0 SELECT @hold_id hold_id, @status hold_status;
            RETURN;
        END;

        -- Active release preconditions
        IF @reason = 'EXPIRED' AND @now < @expires THROW 51273, 'Hold has not expired', 1;
        IF @reason = 'EVENT_CANCELLED' AND NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND status = 'CANCELLED')
            THROW 51274, 'Event is not cancelled', 1;

        DECLARE @lock_count int;
        SELECT @lock_count = COUNT(*) FROM dbo.tc_zones WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_ticket_hold_items hi WHERE hi.hold_id = @hold_id AND hi.zone_id = tc_zones.id);

        SELECT @lock_count = COUNT(*) FROM dbo.tc_seats WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_ticket_hold_items hi WHERE hi.hold_id = @hold_id AND hi.seat_id = tc_seats.id);

        UPDATE s SET status = 'AVAILABLE', version = version + 1 FROM dbo.tc_seats s
        JOIN dbo.tc_ticket_hold_items hi ON hi.seat_id = s.id WHERE hi.hold_id = @hold_id AND s.status = 'HELD';

        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (
                SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_ticket_hold_items
                WHERE hold_id = @hold_id AND seat_id IS NULL GROUP BY zone_id
            ) hi ON hi.zone_id = z.id WHERE z.held_quantity < hi.quantity
        ) THROW 51275, 'Held standing inventory is inconsistent', 1;

        UPDATE z SET held_quantity = held_quantity - hi.quantity, version = version + 1
        FROM dbo.tc_zones z JOIN (
            SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_ticket_hold_items WHERE hold_id = @hold_id AND seat_id IS NULL GROUP BY zone_id
        ) hi ON hi.zone_id = z.id WHERE z.held_quantity >= hi.quantity;

        UPDATE dbo.tc_coupon_redemptions SET status = 'RELEASED', released_at = @now
        WHERE order_id IN (SELECT id FROM dbo.tc_orders WHERE hold_id = @hold_id) AND status = 'RESERVED';

        UPDATE dbo.tc_orders SET status = CASE WHEN @reason = 'EXPIRED' THEN 'EXPIRED' ELSE 'CANCELLED' END, version = version + 1, updated_at = @now
        WHERE hold_id = @hold_id AND status = 'PENDING_PAYMENT';

        UPDATE dbo.tc_ticket_holds SET status = 'RELEASED', version = version + 1, updated_at = @now WHERE id = @hold_id AND status = 'ACTIVE';

        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, 'TICKET_HOLD_RELEASED', 'TICKET_HOLD', @hold_id, @reason);

        IF @started = 1 COMMIT TRANSACTION;
        IF @suppress_result = 0 SELECT @hold_id hold_id, CAST('RELEASED' AS varchar(20)) hold_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp07;
        THROW;
    END CATCH
END;
GO
/* Stored Procedure: usp_RequestTicketRefund */
CREATE   PROCEDURE dbo.usp_RequestTicketRefund
    @order_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @ticket_ids nvarchar(max),
    @reason_type varchar(30),
    @reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON;
    IF @reason_type <> 'CUSTOMER_REQUEST'
        THROW 52620, 'Only CUSTOMER_REQUEST is accepted from a buyer', 1;
    EXEC dbo.usp_CreateRefundRequest
        @order_id = @order_id, @actor_id = @actor_id, @ticket_ids = @ticket_ids, @reason = @reason;
END;
GO
/* Stored Procedure: usp_ReviewRefundRequest */
CREATE   PROCEDURE dbo.usp_ReviewRefundRequest
    @request_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @decision varchar(20),
    @rejection_reason nvarchar(1000) = NULL, @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp10;
    BEGIN TRY
        IF @decision NOT IN ('APPROVE', 'REJECT') THROW 51400, 'Invalid refund decision', 1;
        DECLARE @order_id uniqueidentifier, @event_id uniqueidentifier, @reason_type varchar(30), @status varchar(20),
                @event_status varchar(30), @amount decimal(19,0), @refund_id uniqueidentifier, @payment_id uniqueidentifier,
                @now datetime2(3) = SYSUTCDATETIME();
        SELECT @order_id = order_id, @reason_type = reason_type, @status = status
        FROM dbo.tc_refund_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;
        IF @order_id IS NULL THROW 51401, 'Refund request not found', 1;
        SELECT @event_id = event_id FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        SELECT @event_status = status FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @reason_type = 'CUSTOMER_REQUEST' AND NOT EXISTS (
            SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51402, 'Administrator role required', 1;
        IF @reason_type = 'EVENT_CANCELLATION' AND @event_status <> 'CANCELLED' THROW 51403, 'Cancellation request requires cancelled event', 1;
        IF @status IN ('REJECTED', 'COMPLETED') BEGIN
            IF (@status = 'REJECTED' AND @decision <> 'REJECT') OR (@status = 'COMPLETED' AND @decision <> 'APPROVE')
                THROW 51404, 'Refund request already has the opposite final decision', 1;
            SELECT @request_id request_id, @status request_status, CAST(NULL AS uniqueidentifier) refund_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
        WHERE rt.refund_request_id = @request_id ORDER BY t.id;
        IF @decision = 'REJECT' BEGIN
            IF NULLIF(LTRIM(RTRIM(@rejection_reason)), N'') IS NULL THROW 51405, 'Rejection reason is required', 1;
            UPDATE t SET status = CASE WHEN @event_status = 'CANCELLED' THEN 'INVALIDATED' ELSE 'ACTIVE' END, version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id AND t.status = 'REFUND_PENDING';
            UPDATE dbo.tc_refund_request_tickets SET is_open = 0 WHERE refund_request_id = @request_id;
            UPDATE dbo.tc_refund_requests SET status = 'REJECTED', reviewer_id = @actor_id, decided_at = @now,
                   rejection_reason = @rejection_reason, version = version + 1 WHERE id = @request_id;
        END ELSE BEGIN
            SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id;
            IF @amount = 0 BEGIN
                SELECT z.id FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                    SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_request_id = @request_id AND oi.zone_id = z.id);
                UPDATE s SET status = 'AVAILABLE', version = s.version + 1 FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
                JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
                WHERE rt.refund_request_id = @request_id AND s.status = 'SOLD';
                UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id;
                UPDATE t SET status = 'REFUNDED', version = t.version + 1 FROM dbo.tc_tickets t
                JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id WHERE rt.refund_request_id = @request_id;
                UPDATE dbo.tc_refund_request_tickets SET is_open = 0 WHERE refund_request_id = @request_id;
                UPDATE dbo.tc_refund_requests SET status = 'COMPLETED', reviewer_id = @actor_id, decided_at = @now, version = version + 1 WHERE id = @request_id;
            END ELSE BEGIN
                IF EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE refund_request_id = @request_id AND status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED')) BEGIN
                    SELECT TOP (1) @refund_id = id FROM dbo.tc_refunds WHERE refund_request_id = @request_id ORDER BY created_at DESC;
                END ELSE BEGIN
                    IF @retry_failed = 0 AND EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE refund_request_id = @request_id AND status = 'FAILED')
                        THROW 51406, 'Refund retry requires explicit authorization', 1;
                    SELECT TOP (1) @payment_id = id FROM dbo.tc_payments WHERE order_id = @order_id AND status = 'CAPTURED' ORDER BY captured_at;
                    IF @payment_id IS NULL THROW 51407, 'Captured payment not found', 1;
                    SET @refund_id = NEWID();
                    INSERT dbo.tc_refunds(id, refund_request_id, payment_id, purpose, amount)
                    VALUES (@refund_id, @request_id, @payment_id, 'CUSTOMER_REFUND', @amount);
                END;
                UPDATE dbo.tc_refund_requests SET status = 'APPROVED', reviewer_id = @actor_id,
                       decided_at = COALESCE(decided_at, @now), version = version + 1 WHERE id = @request_id;
            END;
        END;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT('REFUND_REQUEST_', @decision), 'REFUND_REQUEST', @request_id, @rejection_reason);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @request_id request_id, status request_status, @refund_id refund_id FROM dbo.tc_refund_requests WHERE id = @request_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp10;
        THROW;
    END CATCH
END;
GO
/* Trigger: trg_AuditLog_AppendOnly */
CREATE   TRIGGER dbo.trg_AuditLog_AppendOnly ON dbo.tc_audit_logs
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted) THROW 51110, 'Audit log is append-only', 1;
END;
GO
/* Trigger: trg_CheckIn_MirrorCanonical */
CREATE   TRIGGER dbo.trg_CheckIn_MirrorCanonical
ON dbo.tc_check_ins AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.tc_ticket_check_in_logs(id, event_id, ticket_id, actor_id, result, scanned_at)
    SELECT i.id, i.event_id, i.ticket_id, i.actor_id,
           CASE i.result
               WHEN 'NOT_FOUND' THEN 'INVALID_CODE'
               WHEN 'TOO_EARLY' THEN 'OUTSIDE_WINDOW'
               WHEN 'TOO_LATE' THEN 'OUTSIDE_WINDOW'
               WHEN 'NOT_ACTIVE' THEN 'INACTIVE_TICKET'
               WHEN 'EVENT_CANCELLED' THEN 'INACTIVE_TICKET'
               ELSE i.result
           END,
           i.scanned_at
    FROM inserted i
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_ticket_check_in_logs l WHERE l.id = i.id);
END;
GO
/* Trigger: trg_CommissionRule_ProtectAppliedTerms */
CREATE   TRIGGER dbo.trg_CommissionRule_ProtectAppliedTerms ON dbo.tc_commission_rules
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        WHERE EXISTS (SELECT 1 FROM dbo.tc_events e WHERE e.commission_rule_id = d.id)
          AND (i.id IS NULL OR i.organization_id <> d.organization_id
               OR i.rate_percent <> d.rate_percent OR i.fixed_fee <> d.fixed_fee)
    ) THROW 51103, 'Applied commission terms are immutable', 1;
END;
GO
/* Trigger: trg_Coupon_ProtectUsageLimit */
CREATE   TRIGGER dbo.trg_Coupon_ProtectUsageLimit ON dbo.tc_coupons
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        WHERE i.max_uses < (SELECT COUNT_BIG(*) FROM dbo.tc_coupon_redemptions r WITH (UPDLOCK, HOLDLOCK)
                            WHERE r.coupon_id = i.id AND r.status IN ('RESERVED', 'CONSUMED'))
    ) THROW 51107, 'Coupon max uses is below current usage', 1;
END;
GO
/* Trigger: trg_Event_AuditStatusChange */
CREATE   TRIGGER dbo.trg_Event_AuditStatusChange ON dbo.tc_events
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
    SELECT TRY_CONVERT(uniqueidentifier, SESSION_CONTEXT(N'actor_id')), 'EVENT_STATUS_CHANGED', 'EVENT',
           i.id, CONCAT(N'{"from":"', d.status, N'","to":"', i.status, N'"}')
    FROM inserted i JOIN deleted d ON d.id = i.id WHERE i.status <> d.status;
END;
GO
/* Trigger: trg_Membership_ProtectLastManager */
CREATE   TRIGGER dbo.trg_Membership_ProtectLastManager ON dbo.tc_organization_memberships
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM (SELECT organization_id FROM deleted UNION SELECT organization_id FROM inserted) x
        WHERE NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships m WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_users u ON u.id = m.user_id
            WHERE m.organization_id = x.organization_id AND m.role = 'MANAGER'
              AND m.active = 1 AND u.status = 'ACTIVE'
        )
    ) THROW 51106, 'Organization must retain an active manager', 1;
END;
GO
/* Trigger: trg_Payout_MirrorCanonical */
CREATE   TRIGGER dbo.trg_Payout_MirrorCanonical
ON dbo.tc_payouts AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE l
    SET settlement_id = i.settlement_id, amount = i.amount, status = i.status, result = i.status,
        provider_reference = i.reference, processed_at = i.paid_at
    FROM dbo.tc_settlement_transfer_logs l JOIN inserted i ON i.id = l.id;

    INSERT dbo.tc_settlement_transfer_logs
        (id, settlement_id, amount, status, result, provider_reference, processed_at, created_at)
    SELECT i.id, i.settlement_id, i.amount, i.status, i.status, i.reference, i.paid_at, i.created_at
    FROM inserted i
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_settlement_transfer_logs l WHERE l.id = i.id);

    UPDATE s
    SET paid_amount = balances.paid_amount,
        pending_amount = balances.pending_amount,
        updated_at = SYSUTCDATETIME()
    FROM dbo.tc_settlements s
    JOIN (SELECT DISTINCT settlement_id FROM inserted) affected ON affected.settlement_id = s.id
    CROSS APPLY (
        SELECT COALESCE(SUM(CASE WHEN p.status = 'SUCCEEDED' THEN p.amount ELSE 0 END), 0) paid_amount,
               COALESCE(SUM(CASE WHEN p.status = 'PENDING' THEN p.amount ELSE 0 END), 0) pending_amount
        FROM dbo.tc_payouts p WHERE p.settlement_id = s.id
    ) balances;
END;
GO
/* Trigger: trg_RefundAttempt_MirrorCanonical */
CREATE   TRIGGER dbo.trg_RefundAttempt_MirrorCanonical
ON dbo.tc_refunds AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    INSERT dbo.tc_refund_obligations
        (id, order_id, payment_id, amount, purpose, status, current_attempt_id,
         provider_reference, processed_at, created_at, updated_at)
    SELECT i.id, p.order_id, i.payment_id, i.amount, i.purpose,
           CASE i.status WHEN 'SUCCEEDED' THEN 'COMPLETED' WHEN 'FAILED' THEN 'RETRYABLE'
                         WHEN 'UNKNOWN' THEN 'NEEDS_RECONCILIATION' ELSE 'PROCESSING' END,
           i.id, i.provider_reference, i.processed_at, i.created_at, SYSUTCDATETIME()
    FROM inserted i
    JOIN dbo.tc_payments p ON p.id = i.payment_id
    WHERE i.refund_request_id IS NULL
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_obligations o WHERE o.id = i.id);

    UPDATE l
    SET status = i.status, result = i.status,
        provider_reference = i.provider_reference, processed_at = i.processed_at
    FROM dbo.tc_refund_transfer_logs l JOIN inserted i ON i.id = l.id;

    ;WITH attempts AS (
        SELECT i.*, COALESCE(o.id, i.id) obligation_id,
               ROW_NUMBER() OVER (PARTITION BY COALESCE(o.id, i.id) ORDER BY i.created_at, i.id)
               + COALESCE(existing.max_attempt_no, 0) attempt_no
        FROM inserted i
        LEFT JOIN dbo.tc_refund_obligations o ON o.legacy_request_id = i.refund_request_id
        OUTER APPLY (
            SELECT MAX(l.attempt_no) max_attempt_no
            FROM dbo.tc_refund_transfer_logs l
            WHERE l.refund_id = COALESCE(o.id, i.id)
        ) existing
    )
    INSERT dbo.tc_refund_transfer_logs
        (id, refund_id, attempt_no, status, result, provider_reference, processed_at, created_at)
    SELECT a.id, a.obligation_id, a.attempt_no, a.status, a.status,
           a.provider_reference, a.processed_at, a.created_at
    FROM attempts a
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.id = a.id);

    UPDATE o
    SET payment_id = i.payment_id,
        amount = i.amount,
        status = CASE i.status WHEN 'SUCCEEDED' THEN 'COMPLETED' WHEN 'FAILED' THEN 'RETRYABLE'
                               WHEN 'UNKNOWN' THEN 'NEEDS_RECONCILIATION' ELSE 'PROCESSING' END,
        current_attempt_id = i.id,
        provider_reference = i.provider_reference,
        processed_at = i.processed_at,
        updated_at = SYSUTCDATETIME()
    FROM dbo.tc_refund_obligations o
    JOIN inserted i ON o.id = i.id OR o.legacy_request_id = i.refund_request_id;
END;
GO
/* Trigger: trg_RefundRequest_MirrorCanonical */
/* Mirror proven legacy write paths into canonical persistence tables. */
CREATE   TRIGGER dbo.trg_RefundRequest_MirrorCanonical
ON dbo.tc_refund_requests AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE o
    SET order_id = i.order_id,
        reason_type = i.reason_type,
        reason = i.reason,
        rejection_reason = i.rejection_reason,
        status = CASE
            WHEN i.status = 'COMPLETED' THEN 'COMPLETED'
            WHEN i.status = 'REJECTED' THEN 'REJECTED'
            WHEN i.status = 'APPROVED' AND o.current_attempt_id IS NULL THEN 'APPROVED'
            ELSE o.status
        END,
        updated_at = SYSUTCDATETIME()
    FROM dbo.tc_refund_obligations o
    JOIN inserted i ON i.id = o.legacy_request_id;

    INSERT dbo.tc_refund_obligations
        (id, legacy_request_id, order_id, payment_id, amount, purpose, reason_type,
         reason, rejection_reason, status, created_at, updated_at)
    SELECT i.id, i.id, i.order_id, captured.id, 0, 'CUSTOMER_REFUND', i.reason_type,
           i.reason, i.rejection_reason,
           CASE i.status WHEN 'APPROVED' THEN 'APPROVED' WHEN 'REJECTED' THEN 'REJECTED'
                         WHEN 'COMPLETED' THEN 'COMPLETED' ELSE 'REQUESTED' END,
           i.requested_at, SYSUTCDATETIME()
    FROM inserted i
    OUTER APPLY (
        SELECT TOP (1) p.id FROM dbo.tc_payments p
        WHERE p.order_id = i.order_id AND p.status = 'CAPTURED'
        ORDER BY p.captured_at, p.created_at, p.id
    ) captured
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_refund_obligations o WHERE o.legacy_request_id = i.id);
END;
GO
/* Trigger: trg_RefundRequestTicket_MirrorCanonical */
CREATE   TRIGGER dbo.trg_RefundRequestTicket_MirrorCanonical
ON dbo.tc_refund_request_tickets AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE t
    SET is_open = i.is_open
    FROM dbo.tc_refund_tickets t
    JOIN dbo.tc_refund_obligations o ON o.id = t.refund_id
    JOIN inserted i ON i.refund_request_id = o.legacy_request_id AND i.ticket_id = t.ticket_id;

    INSERT dbo.tc_refund_tickets(refund_id, ticket_id, is_open, created_at)
    SELECT o.id, i.ticket_id, i.is_open, i.added_at
    FROM inserted i
    JOIN dbo.tc_refund_obligations o ON o.legacy_request_id = i.refund_request_id
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.tc_refund_tickets t WHERE t.refund_id = o.id AND t.ticket_id = i.ticket_id
    );

    UPDATE o
    SET amount = totals.amount, updated_at = SYSUTCDATETIME()
    FROM dbo.tc_refund_obligations o
    JOIN (SELECT DISTINCT refund_request_id FROM inserted) affected ON affected.refund_request_id = o.legacy_request_id
    CROSS APPLY (
        SELECT COALESCE(SUM(t.paid_amount), 0) amount
        FROM dbo.tc_refund_request_tickets rt
        JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
        WHERE rt.refund_request_id = o.legacy_request_id
    ) totals;
END;
GO
/* Trigger: trg_Seat_ProtectPublishedLayout */
CREATE   TRIGGER dbo.trg_Seat_ProtectPublishedLayout ON dbo.tc_seats
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.tc_zones z ON z.id = i.zone_id JOIN dbo.tc_events e ON e.id = z.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED')
          AND (d.id IS NULL OR d.zone_id <> i.zone_id OR d.row_name <> i.row_name
               OR d.seat_number <> i.seat_number OR d.label <> i.label)
    ) OR EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        JOIN dbo.tc_zones z ON z.id = d.zone_id JOIN dbo.tc_events e ON e.id = z.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED') AND (i.id IS NULL OR i.zone_id <> d.zone_id)
    ) THROW 51102, 'Published event seat layout is immutable', 1;
END;
GO
/* Trigger: trg_Settlement_ProtectConfirmedSnapshot */
CREATE   TRIGGER dbo.trg_Settlement_ProtectConfirmedSnapshot ON dbo.tc_settlements
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        WHERE d.status IN ('CONFIRMED', 'PAID')
          AND (i.id IS NULL OR i.event_id <> d.event_id OR i.created_at <> d.created_at
               OR ISNULL(i.confirmed_at, '19000101') <> ISNULL(d.confirmed_at, '19000101')
               OR i.gross_revenue <> d.gross_revenue OR i.total_refund <> d.total_refund
               OR i.total_commission <> d.total_commission OR i.net_payable <> d.net_payable
               OR NOT (d.status = 'CONFIRMED' AND i.status IN ('CONFIRMED', 'PAID') OR d.status = 'PAID' AND i.status = 'PAID'))
    ) THROW 51109, 'Confirmed settlement snapshot is immutable', 1;
END;
GO
/* Trigger: trg_SettlementItem_MirrorCanonical */
CREATE   TRIGGER dbo.trg_SettlementItem_MirrorCanonical
ON dbo.tc_settlement_items AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE s
    SET settlement_id = i.settlement_id, order_id = i.order_id,
        gross_amount = i.gross_amount, refund_amount = i.refund_amount,
        commission_amount = i.commission_amount, net_amount = i.net_amount,
        updated_at = SYSUTCDATETIME()
    FROM dbo.tc_settlement_order_snapshots s JOIN inserted i ON i.id = s.id;

    INSERT dbo.tc_settlement_order_snapshots
        (id, settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
    SELECT i.id, i.settlement_id, i.order_id, i.gross_amount, i.refund_amount, i.commission_amount, i.net_amount
    FROM inserted i
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_settlement_order_snapshots s WHERE s.id = i.id);
END;
GO
/* Trigger: trg_SettlementItem_ProtectConfirmedAmounts */
CREATE   TRIGGER dbo.trg_SettlementItem_ProtectConfirmedAmounts ON dbo.tc_settlement_items
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN dbo.tc_settlements s ON s.id = i.settlement_id
        WHERE s.status IN ('CONFIRMED', 'PAID')
    ) OR EXISTS (
        SELECT 1 FROM deleted d JOIN dbo.tc_settlements s ON s.id = d.settlement_id
        WHERE s.status IN ('CONFIRMED', 'PAID')
    ) THROW 51104, 'Confirmed settlement items are immutable', 1;
END;
GO
/* Trigger: trg_SettlementOrderSnapshot_ProtectConfirmedAmounts */
CREATE   TRIGGER dbo.trg_SettlementOrderSnapshot_ProtectConfirmedAmounts
ON dbo.tc_settlement_order_snapshots AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT settlement_id FROM inserted
            UNION
            SELECT settlement_id FROM deleted
        ) affected
        JOIN dbo.tc_settlements s WITH (UPDLOCK, HOLDLOCK) ON s.id = affected.settlement_id
        WHERE s.status IN ('CONFIRMED', 'PAID')
    ) THROW 52640, 'Confirmed settlement order snapshots are immutable', 1;
END;
GO
/* Trigger: trg_tc_users_normalize */
CREATE   TRIGGER dbo.trg_tc_users_normalize
    ON dbo.tc_users AFTER INSERT, UPDATE
    AS
    BEGIN
        SET NOCOUNT ON;
        UPDATE u
        SET normalized_email = LOWER(u.email)
        FROM dbo.tc_users u
        JOIN inserted i ON u.id = i.id
        WHERE u.normalized_email IS NULL OR u.normalized_email <> LOWER(u.email);
    END
GO
/* Trigger: trg_Ticket_ProtectIssuedSnapshot */
CREATE   TRIGGER dbo.trg_Ticket_ProtectIssuedSnapshot ON dbo.tc_tickets
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.id = i.id
        WHERE i.order_item_id <> d.order_item_id OR i.ticket_code <> d.ticket_code
           OR i.qr_secret_hash <> d.qr_secret_hash OR i.paid_amount <> d.paid_amount
           OR i.issued_at <> d.issued_at
    ) THROW 51108, 'Issued ticket snapshot is immutable', 1;
END;
GO
/* Trigger: trg_UserOrganizationRole_ProtectLastManager */
CREATE   TRIGGER dbo.trg_UserOrganizationRole_ProtectLastManager
ON dbo.tc_organization_memberships AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM (SELECT organization_id FROM deleted UNION SELECT organization_id FROM inserted) x
        WHERE NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships m WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_users u ON u.id = m.user_id
            WHERE m.organization_id = x.organization_id AND m.role = 'MANAGER'
              AND m.active = 1 AND u.status = 'ACTIVE'
        )
    ) THROW 52641, 'Organization must retain an active manager', 1;
END;
GO
/* Trigger: trg_Zone_ProtectPublishedLayout */
CREATE   TRIGGER dbo.trg_Zone_ProtectPublishedLayout ON dbo.tc_zones
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.tc_events e ON e.id = i.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED')
          AND (d.id IS NULL OR d.event_id <> i.event_id OR d.name <> i.name OR d.type <> i.type
               OR ISNULL(d.capacity, -1) <> ISNULL(i.capacity, -1))
    ) OR EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        JOIN dbo.tc_events e ON e.id = d.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED')
          AND (i.id IS NULL OR i.event_id <> d.event_id)
    ) THROW 51101, 'Published event zone layout is immutable', 1;
END;
GO
/* View: vw_CheckInHistory */
CREATE   VIEW dbo.vw_CheckInHistory
AS
SELECT ci.id check_in_id, ci.event_id, e.organization_id, ci.actor_id, ci.scanned_at,
       ci.result, ci.ticket_id, oi.zone_name_snapshot, oi.seat_label_snapshot
FROM dbo.tc_check_ins ci JOIN dbo.tc_events e ON e.id = ci.event_id
LEFT JOIN dbo.tc_tickets t ON t.id = ci.ticket_id
LEFT JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id;
GO
/* View: vw_EventCancellationExceptions */
CREATE   VIEW dbo.vw_EventCancellationExceptions
AS
    SELECT o.event_id, CAST('USED_TICKET' AS varchar(40)) exception_type,
           t.id exception_id, o.id order_id, t.status
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED' AND t.status = 'USED'
    UNION ALL
    SELECT o.event_id, CAST('REFUND_RESULT' AS varchar(40)),
           r.id, o.id, r.status
    FROM dbo.tc_refunds r
    JOIN dbo.tc_refund_requests rr ON rr.id = r.refund_request_id
    JOIN dbo.tc_orders o ON o.id = rr.order_id
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED' AND rr.reason_type = 'EVENT_CANCELLATION'
      AND r.status IN ('FAILED', 'UNKNOWN');
GO
/* View: vw_EventCancellationProgress */
CREATE   VIEW dbo.vw_EventCancellationProgress
AS
    SELECT e.id event_id, e.status event_status,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o WHERE o.event_id = e.id) total_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id AND (
                EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
                OR EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                           WHERE oi.order_id = o.id AND t.status = 'ACTIVE')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_requests rr WHERE rr.order_id = o.id AND rr.status = 'PENDING')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_requests rr
                           LEFT JOIN dbo.tc_refunds r ON r.refund_request_id = rr.id
                           WHERE rr.order_id = o.id AND rr.reason_type = 'EVENT_CANCELLATION'
                             AND rr.status = 'APPROVED' AND (r.id IS NULL OR r.status = 'PENDING')))) pending_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                              WHERE oi.order_id = o.id AND t.status NOT IN ('REFUNDED', 'INVALIDATED'))
              AND NOT EXISTS (SELECT 1 FROM dbo.vw_EventCancellationExceptions x WHERE x.order_id = o.id)) completed_orders,
           (SELECT COUNT_BIG(*) FROM dbo.vw_EventCancellationExceptions x WHERE x.event_id = e.id) exception_count,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refund_requests rr JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION') requests_created,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refunds r JOIN dbo.tc_refund_requests rr ON rr.id = r.refund_request_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION' AND r.status = 'SUCCEEDED') refunds_succeeded,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refunds r JOIN dbo.tc_refund_requests rr ON rr.id = r.refund_request_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = 'EVENT_CANCELLATION' AND r.status = 'PENDING') refunds_pending
    FROM dbo.tc_events e;
GO
/* View: vw_EventCancellationWork */
CREATE   VIEW dbo.vw_EventCancellationWork
AS
    SELECT h.event_id, CAST('HOLD' AS varchar(10)) work_type, h.id work_id
    FROM dbo.tc_ticket_holds h
    JOIN dbo.tc_events e ON e.id = h.event_id
    WHERE e.status = 'CANCELLED' AND h.status = 'ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_orders o WHERE o.hold_id = h.id)
      AND EXISTS (
          SELECT 1 FROM dbo.tc_outbox x
          WHERE x.event_type = 'EVENT_CANCELLED' AND x.aggregate_id = h.event_id
            AND x.status <> 'PUBLISHED')
    UNION ALL
    SELECT o.event_id, CAST('ORDER' AS varchar(10)), o.id
    FROM dbo.tc_orders o
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = 'CANCELLED'
      AND EXISTS (
          SELECT 1 FROM dbo.tc_outbox x
          WHERE x.event_type = 'EVENT_CANCELLED' AND x.aggregate_id = o.event_id
            AND x.status <> 'PUBLISHED')
      AND (
          EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = 'ACTIVE')
          OR EXISTS (
              SELECT 1 FROM dbo.tc_tickets t
              JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
              WHERE oi.order_id = o.id AND t.status = 'ACTIVE')
          OR EXISTS (
              SELECT 1 FROM dbo.tc_refund_requests rr
              WHERE rr.order_id = o.id AND rr.status = 'PENDING'));
GO
/* View: vw_EventSalesReport */
CREATE   VIEW dbo.vw_EventSalesReport
AS
WITH orders AS (
    SELECT s.event_id, COUNT_BIG(*) paid_order_count,
           SUM(s.ticket_gross_amount) gross_revenue,
           SUM(s.successful_ticket_refund_amount) total_refund,
           SUM(dbo.fn_CalculateCommission(s.remaining_ticket_amount, r.rate_percent, r.fixed_fee)) projected_commission,
           SUM(s.remaining_ticket_amount - dbo.fn_CalculateCommission(s.remaining_ticket_amount, r.rate_percent, r.fixed_fee)) projected_net
    FROM dbo.vw_OrderFinancialSummary s
    JOIN dbo.tc_events e ON e.id = s.event_id
    JOIN dbo.tc_commission_rules r ON r.id = e.commission_rule_id
    WHERE s.status = 'PAID'
    GROUP BY s.event_id
), tickets AS (
    SELECT o.event_id,
           SUM(CASE WHEN t.status = 'ACTIVE' THEN 1 ELSE 0 END) active_tickets,
           SUM(CASE WHEN t.status = 'USED' THEN 1 ELSE 0 END) used_tickets,
           SUM(CASE WHEN t.status IN ('REFUNDED', 'INVALIDATED') THEN 1 ELSE 0 END) inactive_tickets
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id
    GROUP BY o.event_id
)
SELECT e.id event_id, e.organization_id, e.title, e.status,
       COALESCE(o.paid_order_count, 0) paid_order_count,
       COALESCE(t.active_tickets, 0) active_tickets,
       COALESCE(t.used_tickets, 0) used_tickets,
       COALESCE(t.inactive_tickets, 0) inactive_tickets,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN s.gross_revenue ELSE COALESCE(o.gross_revenue, 0) END gross_revenue,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN s.total_refund ELSE COALESCE(o.total_refund, 0) END total_refund,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN s.total_commission ELSE COALESCE(o.projected_commission, 0) END total_commission,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN s.net_payable ELSE COALESCE(o.projected_net, 0) END net_payable,
       CASE WHEN s.status IN ('CONFIRMED', 'PAID') THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_settlement_snapshot
FROM dbo.tc_events e
LEFT JOIN orders o ON o.event_id = e.id
LEFT JOIN tickets t ON t.event_id = e.id
LEFT JOIN dbo.tc_settlements s ON s.event_id = e.id;
GO
/* View: vw_FailedCompensationAttempts */
CREATE   VIEW dbo.vw_FailedCompensationAttempts
AS
    SELECT DISTINCT r.payment_id, p.order_id
    FROM dbo.tc_refunds r
    JOIN dbo.tc_payments p ON p.id = r.payment_id
    WHERE r.purpose = 'PAYMENT_COMPENSATION' AND r.status = 'FAILED'
      AND NOT EXISTS (
          SELECT 1 FROM dbo.tc_refunds active
          WHERE active.payment_id = r.payment_id AND active.purpose = 'PAYMENT_COMPENSATION'
            AND active.status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED'));
GO
/* View: vw_OrganizationMembers */
CREATE   VIEW dbo.vw_OrganizationMembers
AS
SELECT m.id membership_id, m.organization_id, m.user_id, u.email, u.full_name,
       m.role, m.active, u.status user_status, m.joined_at
FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id = m.user_id;
GO
/* View: vw_PublicEvents */
CREATE   VIEW dbo.vw_PublicEvents
AS
SELECT e.id event_id, e.organization_id, e.category_id, e.title, e.description,
       COALESCE(e.event_image, CONVERT(varchar(2048), e.cover_image_url)) cover_image_url,
       e.venue_name, e.venue_address, e.sale_start, e.sale_end,
       e.start_time, e.end_time, MIN(z.price) minimum_price
FROM dbo.tc_events e
LEFT JOIN dbo.tc_zones z ON z.event_id = e.id
WHERE e.status = 'PUBLISHED'
GROUP BY e.id, e.organization_id, e.category_id, e.title, e.description,
         e.event_image, e.cover_image_url, e.venue_name, e.venue_address,
         e.sale_start, e.sale_end, e.start_time, e.end_time;
GO
/* View: vw_RefundOverview */
/* Canonical view name. Legacy view remains available during transition. */
CREATE   VIEW dbo.vw_RefundOverview
AS
WITH ticket_totals AS (
    SELECT refund_id, COUNT_BIG(*) ticket_count,
           SUM(CASE WHEN is_open = 1 THEN 1 ELSE 0 END) open_ticket_count
    FROM dbo.tc_refund_tickets GROUP BY refund_id
), attempt_totals AS (
    SELECT refund_id, COUNT_BIG(*) attempt_count,
           SUM(CASE WHEN status = 'SUCCEEDED' THEN 1 ELSE 0 END) succeeded_attempt_count,
           MAX(created_at) latest_attempt_at
    FROM dbo.tc_refund_transfer_logs GROUP BY refund_id
)
SELECT r.id refund_id, r.order_id, o.event_id, e.organization_id, r.payment_id,
       r.purpose, r.reason_type, r.reason, r.rejection_reason, r.amount, r.status,
       r.current_attempt_id, r.provider_reference, r.processed_at, r.created_at,
       COALESCE(t.ticket_count, 0) ticket_count,
       COALESCE(t.open_ticket_count, 0) open_ticket_count,
       COALESCE(a.attempt_count, 0) attempt_count,
       COALESCE(a.succeeded_attempt_count, 0) succeeded_attempt_count,
       a.latest_attempt_at
FROM dbo.tc_refund_obligations r
LEFT JOIN dbo.tc_orders o ON o.id = r.order_id
LEFT JOIN dbo.tc_events e ON e.id = o.event_id
LEFT JOIN ticket_totals t ON t.refund_id = r.id
LEFT JOIN attempt_totals a ON a.refund_id = r.id;
GO
/* View: vw_RefundRequestOverview */
CREATE   VIEW dbo.vw_RefundRequestOverview
AS
WITH requested AS (
    SELECT rt.refund_request_id, COUNT_BIG(*) ticket_count, SUM(t.paid_amount) requested_amount
    FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
    GROUP BY rt.refund_request_id
), attempts AS (
    SELECT refund_request_id, SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END) refunded_amount,
           SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) pending_attempts,
           SUM(CASE WHEN status = 'UNKNOWN' THEN 1 ELSE 0 END) unknown_attempts,
           SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) failed_attempts
    FROM dbo.tc_refunds WHERE refund_request_id IS NOT NULL GROUP BY refund_request_id
)
SELECT rr.id refund_request_id, o.user_id owner_id, o.event_id, e.organization_id,
       rr.reason_type, rr.reason, rr.status, rr.requested_at, rr.decided_at,
       COALESCE(q.ticket_count, 0) ticket_count, COALESCE(q.requested_amount, 0) requested_amount,
       COALESCE(a.refunded_amount, 0) refunded_amount, COALESCE(a.pending_attempts, 0) pending_attempts,
       COALESCE(a.unknown_attempts, 0) unknown_attempts, COALESCE(a.failed_attempts, 0) failed_attempts
FROM dbo.tc_refund_requests rr JOIN dbo.tc_orders o ON o.id = rr.order_id
JOIN dbo.tc_events e ON e.id = o.event_id LEFT JOIN requested q ON q.refund_request_id = rr.id
LEFT JOIN attempts a ON a.refund_request_id = rr.id;
GO
/* View: vw_RefundWork */
CREATE   VIEW dbo.vw_RefundWork
AS
    SELECT id, amount, status, provider_reference, created_at
    FROM dbo.tc_refunds
    WHERE status IN ('PENDING', 'UNKNOWN');
GO
/* View: vw_TicketDetails */
CREATE   VIEW dbo.vw_TicketDetails
AS
SELECT t.id ticket_id, o.user_id owner_id, o.id order_id, o.event_id, e.organization_id,
       e.title event_title, e.venue_name, e.venue_address, e.start_time, e.end_time,
       oi.zone_name_snapshot, oi.seat_label_snapshot, t.paid_amount, t.status, t.issued_at
FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
JOIN dbo.tc_orders o ON o.id = oi.order_id JOIN dbo.tc_events e ON e.id = o.event_id;
GO

CREATE TABLE dbo.tc_vuong_test_fixture(fixture_key varchar(40) NOT NULL PRIMARY KEY);
INSERT dbo.tc_vuong_test_fixture VALUES ('vuong-finance-v1'),('vuong-team-schema-v1');
GO
