/*
 * 001_create_tables
 *
 * Creates all base tables with columns, identities, default constraints,
 * and clustered primary key constraints.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF OBJECT_ID(N'dbo.tc_schema_migrations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_schema_migrations (
        [version] varchar(100) NOT NULL,
        [checksum_sha256] char(64) NOT NULL,
        [applied_at] datetime2(3) NOT NULL CONSTRAINT [DF__tc_schema__appli__6FE99F9F] DEFAULT (sysutcdatetime()),
        [applied_by] nvarchar(128) NOT NULL CONSTRAINT [DF__tc_schema__appli__70DDC3D8] DEFAULT (original_login()),
        CONSTRAINT [PK_tc_schema_migrations] PRIMARY KEY CLUSTERED ([version])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_users (
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
        [updated_at] datetime2(6) NULL,
        CONSTRAINT [PK_Users] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_user_platform_roles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_user_platform_roles (
        [user_id] uniqueidentifier NOT NULL,
        [role] varchar(30) NOT NULL,
        [granted_at] datetime2(3) NOT NULL CONSTRAINT [DF_UserPlatformRoles_GrantedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_UserPlatformRoles] PRIMARY KEY CLUSTERED ([user_id], [role])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_otps', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_otps (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Otps_Id] DEFAULT (newsequentialid()),
        [user_id] uniqueidentifier NULL,
        [email_normalized] nvarchar(320) NOT NULL,
        [purpose] varchar(30) NOT NULL,
        [secret_hash] varbinary(64) NOT NULL,
        [expires_at] datetime2(3) NOT NULL,
        [failed_attempts] tinyint NOT NULL CONSTRAINT [DF_Otps_FailedAttempts] DEFAULT ((0)),
        [consumed_at] datetime2(3) NULL,
        [invalidated_at] datetime2(3) NULL,
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Otps_CreatedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_Otps] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_event_categories', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_event_categories (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_EventCategories_Id] DEFAULT (newsequentialid()),
        [name] nvarchar(120) NOT NULL,
        [slug] varchar(120) NOT NULL,
        CONSTRAINT [PK_EventCategories] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_organizations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_organizations (
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
        [decided_at] datetime2(3) NULL,
        CONSTRAINT [PK_Organizations] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_organization_requests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_organization_requests (
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
        [version] bigint NOT NULL CONSTRAINT [DF_OrganizationRequests_Version] DEFAULT ((0)),
        CONSTRAINT [PK_OrganizationRequests] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_organization_memberships', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_organization_memberships (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_OrganizationMemberships_Id] DEFAULT (newsequentialid()),
        [user_id] uniqueidentifier NOT NULL,
        [organization_id] uniqueidentifier NOT NULL,
        [role] varchar(30) NOT NULL,
        [active] bit NOT NULL CONSTRAINT [DF_OrganizationMemberships_Active] DEFAULT ((1)),
        [joined_at] datetime2(3) NOT NULL CONSTRAINT [DF_OrganizationMemberships_JoinedAt] DEFAULT (sysutcdatetime()),
        [version] bigint NOT NULL CONSTRAINT [DF_OrganizationMemberships_Version] DEFAULT ((0)),
        CONSTRAINT [PK_OrganizationMemberships] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_commission_rules', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_commission_rules (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_CommissionRules_Id] DEFAULT (newsequentialid()),
        [organization_id] uniqueidentifier NOT NULL,
        [rate_percent] decimal(7,4) NOT NULL,
        [fixed_fee] decimal(19,0) NOT NULL,
        [effective_from] datetime2(3) NOT NULL,
        [effective_to] datetime2(3) NOT NULL,
        CONSTRAINT [PK_CommissionRules] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_events', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_events (
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
        [event_image_public_id] varchar(255) NULL,
        CONSTRAINT [PK_Events] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_zones', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_zones (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Zones_Id] DEFAULT (newsequentialid()),
        [event_id] uniqueidentifier NOT NULL,
        [name] nvarchar(120) NOT NULL,
        [type] varchar(20) NOT NULL,
        [price] decimal(19,0) NOT NULL,
        [capacity] int NULL,
        [held_quantity] int NOT NULL CONSTRAINT [DF_Zones_HeldQuantity] DEFAULT ((0)),
        [sold_quantity] int NOT NULL CONSTRAINT [DF_Zones_SoldQuantity] DEFAULT ((0)),
        [version] bigint NOT NULL CONSTRAINT [DF_Zones_Version] DEFAULT ((0)),
        CONSTRAINT [PK_Zones] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_seats', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_seats (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Seats_Id] DEFAULT (newsequentialid()),
        [zone_id] uniqueidentifier NOT NULL,
        [row_name] nvarchar(20) NOT NULL,
        [seat_number] int NOT NULL,
        [label] nvarchar(50) NOT NULL,
        [status] varchar(20) NOT NULL CONSTRAINT [DF_Seats_Status] DEFAULT ('AVAILABLE'),
        [version] bigint NOT NULL CONSTRAINT [DF_Seats_Version] DEFAULT ((0)),
        CONSTRAINT [PK_Seats] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_coupons', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_coupons (
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
        CONSTRAINT [PK_Coupons] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_ticket_holds', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_ticket_holds (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_TicketHolds_Id] DEFAULT (newsequentialid()),
        [user_id] uniqueidentifier NOT NULL,
        [event_id] uniqueidentifier NOT NULL,
        [status] varchar(20) NOT NULL CONSTRAINT [DF_TicketHolds_Status] DEFAULT ('ACTIVE'),
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_TicketHolds_CreatedAt] DEFAULT (sysutcdatetime()),
        [expires_at] datetime2(3) NOT NULL,
        [version] bigint NOT NULL CONSTRAINT [DF_TicketHolds_Version] DEFAULT ((0)),
        CONSTRAINT [PK_TicketHolds] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_ticket_hold_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_ticket_hold_items (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_TicketHoldItems_Id] DEFAULT (newsequentialid()),
        [hold_id] uniqueidentifier NOT NULL,
        [zone_id] uniqueidentifier NOT NULL,
        [seat_id] uniqueidentifier NULL,
        [quantity] int NOT NULL,
        [unit_price] decimal(19,0) NOT NULL,
        CONSTRAINT [PK_TicketHoldItems] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_orders', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_orders (
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
        CONSTRAINT [PK_Orders] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_order_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_order_items (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_OrderItems_Id] DEFAULT (newsequentialid()),
        [order_id] uniqueidentifier NOT NULL,
        [zone_id] uniqueidentifier NOT NULL,
        [seat_id] uniqueidentifier NULL,
        [quantity] int NOT NULL,
        [unit_price] decimal(19,0) NOT NULL,
        [zone_name_snapshot] nvarchar(120) NOT NULL,
        [seat_label_snapshot] nvarchar(50) NULL,
        CONSTRAINT [PK_OrderItems] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_payments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_payments (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Payments_Id] DEFAULT (newsequentialid()),
        [order_id] uniqueidentifier NOT NULL,
        [txn_ref] varchar(100) NOT NULL,
        [amount] decimal(19,0) NOT NULL,
        [currency] char(3) NOT NULL CONSTRAINT [DF_Payments_Currency] DEFAULT ('VND'),
        [status] varchar(20) NOT NULL CONSTRAINT [DF_Payments_Status] DEFAULT ('PENDING'),
        [provider_reference] nvarchar(200) NULL,
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Payments_CreatedAt] DEFAULT (sysutcdatetime()),
        [captured_at] datetime2(3) NULL,
        [version] bigint NOT NULL CONSTRAINT [DF_Payments_Version] DEFAULT ((0)),
        CONSTRAINT [PK_Payments] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_tickets', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_tickets (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Tickets_Id] DEFAULT (newsequentialid()),
        [order_item_id] uniqueidentifier NOT NULL,
        [ticket_code] varchar(100) NOT NULL,
        [qr_secret_hash] varbinary(64) NOT NULL,
        [paid_amount] decimal(19,0) NOT NULL,
        [status] varchar(30) NOT NULL CONSTRAINT [DF_Tickets_Status] DEFAULT ('ACTIVE'),
        [issued_at] datetime2(3) NOT NULL CONSTRAINT [DF_Tickets_IssuedAt] DEFAULT (sysutcdatetime()),
        [version] bigint NOT NULL CONSTRAINT [DF_Tickets_Version] DEFAULT ((0)),
        CONSTRAINT [PK_Tickets] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_check_ins', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_check_ins (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_CheckIns_Id] DEFAULT (newsequentialid()),
        [event_id] uniqueidentifier NOT NULL,
        [ticket_id] uniqueidentifier NULL,
        [actor_id] uniqueidentifier NOT NULL,
        [result] varchar(30) NOT NULL,
        [scanned_at] datetime2(3) NOT NULL CONSTRAINT [DF_CheckIns_ScannedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_CheckIns] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_ticket_check_in_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_ticket_check_in_logs (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_TicketCheckInLogs_Id] DEFAULT (newsequentialid()),
        [event_id] uniqueidentifier NOT NULL,
        [ticket_id] uniqueidentifier NULL,
        [actor_id] uniqueidentifier NOT NULL,
        [result] varchar(30) NOT NULL,
        [scanned_at] datetime2(3) NOT NULL CONSTRAINT [DF_TicketCheckInLogs_ScannedAt] DEFAULT (sysutcdatetime()),
        [failure_reason] nvarchar(2000) NULL,
        CONSTRAINT [PK_TicketCheckInLogs] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_coupon_redemptions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_coupon_redemptions (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_CouponRedemptions_Id] DEFAULT (newsequentialid()),
        [coupon_id] uniqueidentifier NOT NULL,
        [order_id] uniqueidentifier NOT NULL,
        [status] varchar(20) NOT NULL CONSTRAINT [DF_CouponRedemptions_Status] DEFAULT ('RESERVED'),
        [reserved_at] datetime2(3) NOT NULL CONSTRAINT [DF_CouponRedemptions_ReservedAt] DEFAULT (sysutcdatetime()),
        [expires_at] datetime2(3) NOT NULL,
        [consumed_at] datetime2(3) NULL,
        [released_at] datetime2(3) NULL,
        CONSTRAINT [PK_CouponRedemptions] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_refund_requests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_refund_requests (
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
        [version] bigint NOT NULL CONSTRAINT [DF_RefundRequests_Version] DEFAULT ((0)),
        CONSTRAINT [PK_RefundRequests] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_refund_request_tickets', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_refund_request_tickets (
        [refund_request_id] uniqueidentifier NOT NULL,
        [ticket_id] uniqueidentifier NOT NULL,
        [is_open] bit NOT NULL CONSTRAINT [DF_RefundRequestTickets_IsOpen] DEFAULT ((1)),
        [added_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundRequestTickets_AddedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_RefundRequestTickets] PRIMARY KEY CLUSTERED ([refund_request_id], [ticket_id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_refund_obligations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_refund_obligations (
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
        [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundObligations_UpdatedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_RefundObligations] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_refund_tickets', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_refund_tickets (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_RefundTickets_Id] DEFAULT (newsequentialid()),
        [refund_id] uniqueidentifier NOT NULL,
        [ticket_id] uniqueidentifier NOT NULL,
        [is_open] bit NOT NULL CONSTRAINT [DF_RefundTickets_IsOpen] DEFAULT ((1)),
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundTickets_CreatedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_RefundTickets] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_refund_transfer_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_refund_transfer_logs (
        [id] uniqueidentifier NOT NULL,
        [refund_id] uniqueidentifier NOT NULL,
        [attempt_no] int NOT NULL,
        [status] varchar(30) NOT NULL,
        [result] varchar(30) NOT NULL,
        [provider_reference] nvarchar(200) NULL,
        [processed_at] datetime2(3) NULL,
        [failure_reason] nvarchar(2000) NULL,
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_RefundTransferLogs_CreatedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_RefundTransferLogs] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_refunds', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_refunds (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Refunds_Id] DEFAULT (newsequentialid()),
        [refund_request_id] uniqueidentifier NULL,
        [payment_id] uniqueidentifier NOT NULL,
        [purpose] varchar(30) NOT NULL,
        [amount] decimal(19,0) NOT NULL,
        [status] varchar(20) NOT NULL CONSTRAINT [DF_Refunds_Status] DEFAULT ('PENDING'),
        [provider_reference] nvarchar(200) NULL,
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Refunds_CreatedAt] DEFAULT (sysutcdatetime()),
        [processed_at] datetime2(3) NULL,
        [version] bigint NOT NULL CONSTRAINT [DF_Refunds_Version] DEFAULT ((0)),
        CONSTRAINT [PK_Refunds] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_settlements', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_settlements (
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
        [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_Settlements_UpdatedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_Settlements] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_settlement_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_settlement_items (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_SettlementItems_Id] DEFAULT (newsequentialid()),
        [settlement_id] uniqueidentifier NOT NULL,
        [order_id] uniqueidentifier NOT NULL,
        [gross_amount] decimal(19,0) NOT NULL,
        [refund_amount] decimal(19,0) NOT NULL,
        [commission_amount] decimal(19,0) NOT NULL,
        [net_amount] decimal(19,0) NOT NULL,
        CONSTRAINT [PK_SettlementItems] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_settlement_order_snapshots', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_settlement_order_snapshots (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_SettlementOrderSnapshots_Id] DEFAULT (newsequentialid()),
        [settlement_id] uniqueidentifier NOT NULL,
        [order_id] uniqueidentifier NOT NULL,
        [gross_amount] decimal(19,0) NOT NULL,
        [refund_amount] decimal(19,0) NOT NULL,
        [commission_amount] decimal(19,0) NOT NULL,
        [net_amount] decimal(19,0) NOT NULL,
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_SettlementOrderSnapshots_CreatedAt] DEFAULT (sysutcdatetime()),
        [updated_at] datetime2(3) NOT NULL CONSTRAINT [DF_SettlementOrderSnapshots_UpdatedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_SettlementOrderSnapshots] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_settlement_transfer_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_settlement_transfer_logs (
        [id] uniqueidentifier NOT NULL,
        [settlement_id] uniqueidentifier NOT NULL,
        [amount] decimal(19,0) NOT NULL,
        [status] varchar(30) NOT NULL,
        [result] varchar(30) NOT NULL,
        [provider_reference] nvarchar(200) NULL,
        [processed_at] datetime2(3) NULL,
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_SettlementTransferLogs_CreatedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_SettlementTransferLogs] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_payouts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_payouts (
        [id] uniqueidentifier NOT NULL CONSTRAINT [DF_Payouts_Id] DEFAULT (newsequentialid()),
        [settlement_id] uniqueidentifier NOT NULL,
        [amount] decimal(19,0) NOT NULL,
        [reference] varchar(100) NOT NULL,
        [status] varchar(20) NOT NULL CONSTRAINT [DF_Payouts_Status] DEFAULT ('PENDING'),
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_Payouts_CreatedAt] DEFAULT (sysutcdatetime()),
        [paid_at] datetime2(3) NULL,
        [version] bigint NOT NULL CONSTRAINT [DF_Payouts_Version] DEFAULT ((0)),
        CONSTRAINT [PK_Payouts] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_audit_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_audit_logs (
        [id] bigint IDENTITY(1,1) NOT NULL,
        [actor_id] uniqueidentifier NULL,
        [action] varchar(100) NOT NULL,
        [aggregate_type] varchar(100) NOT NULL,
        [aggregate_id] uniqueidentifier NOT NULL,
        [detail] nvarchar(max) NULL,
        [created_at] datetime2(3) NOT NULL CONSTRAINT [DF_AuditLogs_CreatedAt] DEFAULT (sysutcdatetime()),
        CONSTRAINT [PK_AuditLogs] PRIMARY KEY CLUSTERED ([id])
    );
END
GO

IF OBJECT_ID(N'dbo.tc_outbox', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_outbox (
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
        [published_at] datetime2(3) NULL,
        CONSTRAINT [PK_Outbox] PRIMARY KEY CLUSTERED ([id])
    );
END
GO
