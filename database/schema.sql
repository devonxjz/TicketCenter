-- SQL Server 2017+; run in an existing, empty database using SSMS or sqlcmd.
-- Source: docs/classdiagram/diagram.md. This replaces the previous MySQL DDL.
-- Money uses DECIMAL(19,2), Instant uses DATETIME2(6) in UTC, strings use Unicode.
-- Enum members are implementation choices documented in functions.sql.
-- BIGINT IDENTITY keys and business-key uniqueness are mapping choices.
-- Install schema.sql, functions.sql, views.sql, procedures.sql, triggers.sql.
-- Derived attributes use persisted computed columns or views.
-- Extra persistence fields connect an order snapshot to its reserved hold item
-- and record inventory state; transfer ledgers support retry-safe callbacks.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
IF CONVERT(INT,SERVERPROPERTY('ProductMajorVersion')) < 14
    THROW 51000,N'SQL Server 2017 or newer is required.',1;
IF (SELECT compatibility_level FROM sys.databases WHERE name=DB_NAME()) < 130
    THROW 51000,N'Database compatibility level 130 or newer is required.',1;
GO
CREATE TABLE dbo.users (
    id BIGINT IDENTITY(1,1) NOT NULL,
    email NVARCHAR(254) NOT NULL,
    user_name NVARCHAR(100) NOT NULL,
    full_name NVARCHAR(255) NOT NULL,
    phone NVARCHAR(32) NULL,
    status NVARCHAR(64) NOT NULL,
    email_verified BIT NOT NULL,
    platform_role NVARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_user_name UNIQUE (user_name)
);
GO

CREATE TABLE dbo.organizations (
    id BIGINT IDENTITY(1,1) NOT NULL,
    name NVARCHAR(255) NOT NULL,
    contact_email NVARCHAR(254) NOT NULL,
    contact_phone NVARCHAR(32) NULL,
    description NVARCHAR(MAX) NULL,
    requester_id BIGINT NULL,
    status NVARCHAR(64) NOT NULL,
    rejection_reason NVARCHAR(MAX) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_organizations_requester FOREIGN KEY (requester_id) REFERENCES dbo.users (id)
);
GO

CREATE TABLE dbo.user_organization_roles (
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    role NVARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, organization_id),
    CONSTRAINT fk_user_organization_roles_user FOREIGN KEY (user_id) REFERENCES dbo.users (id),
    CONSTRAINT fk_user_organization_roles_organization FOREIGN KEY (organization_id) REFERENCES dbo.organizations (id)
);
GO

CREATE TABLE dbo.coupons (
    id BIGINT IDENTITY(1,1) NOT NULL,
    organization_id BIGINT NOT NULL,
    code NVARCHAR(100) NOT NULL,
    discount_type NVARCHAR(64) NOT NULL,
    fixed_amount DECIMAL(19,2) NULL,
    percentage DECIMAL(7,4) NULL,
    valid_from DATETIME2(6) NOT NULL,
    valid_to DATETIME2(6) NOT NULL,
    is_active BIT NOT NULL,
    max_uses INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_coupons_organization_code UNIQUE (organization_id, code),
    CONSTRAINT fk_coupons_organization FOREIGN KEY (organization_id) REFERENCES dbo.organizations (id),
    CONSTRAINT chk_coupons_fixed_amount CHECK (fixed_amount IS NULL OR fixed_amount >= 0),
    CONSTRAINT chk_coupons_percentage CHECK (percentage IS NULL OR percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_coupons_validity CHECK (valid_to >= valid_from),
    CONSTRAINT chk_coupons_max_uses CHECK (max_uses >= 0)
);
GO

CREATE TABLE dbo.commission_rules (
    id BIGINT IDENTITY(1,1) NOT NULL,
    organization_id BIGINT NOT NULL,
    rate_percent DECIMAL(7,4) NOT NULL,
    fixed_fee DECIMAL(19,2) NOT NULL,
    effective_from DATETIME2(6) NOT NULL,
    effective_to DATETIME2(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_commission_rules_organization FOREIGN KEY (organization_id) REFERENCES dbo.organizations (id),
    CONSTRAINT chk_commission_rules_rate CHECK (rate_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_commission_rules_fixed_fee CHECK (fixed_fee >= 0),
    CONSTRAINT chk_commission_rules_validity CHECK (effective_to >= effective_from)
);
GO

CREATE TABLE dbo.events (
    id BIGINT IDENTITY(1,1) NOT NULL,
    organization_id BIGINT NOT NULL,
    title NVARCHAR(255) NOT NULL,
    description NVARCHAR(MAX) NOT NULL,
    category NVARCHAR(64) NOT NULL,
    venue_name NVARCHAR(255) NOT NULL,
    venue_address NVARCHAR(500) NOT NULL,
    cover_image_url NVARCHAR(MAX) NULL,
    sale_start DATETIME2(6) NOT NULL,
    sale_end DATETIME2(6) NOT NULL,
    start_time DATETIME2(6) NOT NULL,
    end_time DATETIME2(6) NOT NULL,
    status NVARCHAR(64) NOT NULL,
    rejection_reason NVARCHAR(MAX) NULL,
    commission_rule_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_events_organization FOREIGN KEY (organization_id) REFERENCES dbo.organizations (id),
    CONSTRAINT fk_events_commission_rule FOREIGN KEY (commission_rule_id) REFERENCES dbo.commission_rules (id),
    CONSTRAINT chk_events_sale_period CHECK (sale_end >= sale_start),
    CONSTRAINT chk_events_event_period CHECK (end_time >= start_time)
);
GO

CREATE TABLE dbo.zones (
    id BIGINT IDENTITY(1,1) NOT NULL,
    event_id BIGINT NOT NULL,
    name NVARCHAR(255) NOT NULL,
    type NVARCHAR(64) NOT NULL,
    price DECIMAL(19,2) NOT NULL,
    standing_capacity INT NULL,
    standing_held INT NULL,
    standing_sold INT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_zones_event FOREIGN KEY (event_id) REFERENCES dbo.events (id),
    CONSTRAINT chk_zones_price CHECK (price >= 0),
    CONSTRAINT chk_zones_standing_capacity CHECK (standing_capacity IS NULL OR standing_capacity >= 0),
    CONSTRAINT chk_zones_standing_held CHECK (standing_held IS NULL OR standing_held >= 0),
    CONSTRAINT chk_zones_standing_sold CHECK (standing_sold IS NULL OR standing_sold >= 0)
);
GO

CREATE TABLE dbo.seats (
    id BIGINT IDENTITY(1,1) NOT NULL,
    zone_id BIGINT NOT NULL,
    row_name NVARCHAR(100) NOT NULL,
    seat_number INT NOT NULL,
    status NVARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_seats_position UNIQUE (zone_id, row_name, seat_number),
    CONSTRAINT fk_seats_zone FOREIGN KEY (zone_id) REFERENCES dbo.zones (id),
    CONSTRAINT chk_seats_number CHECK (seat_number > 0)
);
GO

-- Derived money formulas are mapping assumptions, since the diagram only
-- marks the attributes as derived and does not specify their expressions.
CREATE TABLE dbo.settlements (
    id BIGINT IDENTITY(1,1) NOT NULL,
    event_id BIGINT NOT NULL,
    status NVARCHAR(64) NOT NULL,
    gross_revenue DECIMAL(19,2) NOT NULL,
    total_refund DECIMAL(19,2) NOT NULL,
    total_commission DECIMAL(19,2) NOT NULL,
    net_payable AS CAST((gross_revenue - total_refund - total_commission) AS DECIMAL(19,2)) PERSISTED,
    paid_amount DECIMAL(19,2) NOT NULL,
    pending_amount DECIMAL(19,2) NOT NULL,
    available_to_pay AS CAST((gross_revenue - total_refund - total_commission - paid_amount - pending_amount) AS DECIMAL(19,2)) PERSISTED,
    PRIMARY KEY (id),
    CONSTRAINT uq_settlements_event UNIQUE (event_id),
    CONSTRAINT fk_settlements_event FOREIGN KEY (event_id) REFERENCES dbo.events (id),
    CONSTRAINT chk_settlements_amounts CHECK (
        gross_revenue >= 0 AND total_refund >= 0 AND total_commission >= 0
        AND paid_amount >= 0 AND pending_amount >= 0
    )
);
GO

CREATE TABLE dbo.ticket_holds (
    id BIGINT IDENTITY(1,1) NOT NULL,
    buyer_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    expires_at DATETIME2(6) NOT NULL,
    status NVARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ticket_holds_buyer FOREIGN KEY (buyer_id) REFERENCES dbo.users (id),
    CONSTRAINT fk_ticket_holds_event FOREIGN KEY (event_id) REFERENCES dbo.events (id)
);
GO

CREATE TABLE dbo.ticket_hold_items (
    id BIGINT IDENTITY(1,1) NOT NULL,
    ticket_hold_id BIGINT NOT NULL,
    inventory_state NVARCHAR(16) NOT NULL CONSTRAINT df_hold_item_inventory DEFAULT N'HELD',
    zone_id BIGINT NOT NULL,
    seat_id BIGINT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ticket_hold_items_hold FOREIGN KEY (ticket_hold_id) REFERENCES dbo.ticket_holds (id),
    CONSTRAINT fk_ticket_hold_items_zone FOREIGN KEY (zone_id) REFERENCES dbo.zones (id),
    CONSTRAINT fk_ticket_hold_items_seat FOREIGN KEY (seat_id) REFERENCES dbo.seats (id),
    CONSTRAINT chk_hold_item_inventory CHECK (inventory_state IN (N'HELD', N'SOLD', N'RELEASED')),
    CONSTRAINT chk_ticket_hold_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_ticket_hold_items_seated_quantity CHECK (seat_id IS NULL OR quantity = 1),
    CONSTRAINT chk_ticket_hold_items_price CHECK (unit_price >= 0)
);
GO

CREATE TABLE dbo.orders (
    id BIGINT IDENTITY(1,1) NOT NULL,
    buyer_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    hold_id BIGINT NOT NULL,
    coupon_id BIGINT NULL,
    accepted_payment_id BIGINT NULL,
    order_code NVARCHAR(100) NOT NULL,
    status NVARCHAR(64) NOT NULL,
    discount_amount DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_orders_code UNIQUE (order_code),
    CONSTRAINT uq_orders_hold UNIQUE (hold_id),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES dbo.users (id),
    CONSTRAINT fk_orders_event FOREIGN KEY (event_id) REFERENCES dbo.events (id),
    CONSTRAINT fk_orders_hold FOREIGN KEY (hold_id) REFERENCES dbo.ticket_holds (id),
    CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES dbo.coupons (id),
    CONSTRAINT chk_orders_discount CHECK (discount_amount >= 0)
);
GO

CREATE TABLE dbo.order_items (
    id BIGINT IDENTITY(1,1) NOT NULL,
    hold_item_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    zone_id BIGINT NOT NULL,
    seat_id BIGINT NULL,
    zone_name_snapshot NVARCHAR(255) NOT NULL,
    seat_label_snapshot NVARCHAR(100) NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_order_items_hold_item UNIQUE (hold_item_id),
    CONSTRAINT fk_order_items_hold_item FOREIGN KEY (hold_item_id) REFERENCES dbo.ticket_hold_items (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES dbo.orders (id),
    CONSTRAINT fk_order_items_zone FOREIGN KEY (zone_id) REFERENCES dbo.zones (id),
    CONSTRAINT fk_order_items_seat FOREIGN KEY (seat_id) REFERENCES dbo.seats (id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_seated_quantity CHECK (seat_id IS NULL OR quantity = 1),
    CONSTRAINT chk_order_items_price CHECK (unit_price >= 0)
);
GO

CREATE TABLE dbo.tickets (
    id BIGINT IDENTITY(1,1) NOT NULL,
    order_item_id BIGINT NOT NULL,
    ticket_code NVARCHAR(100) NOT NULL,
    status NVARCHAR(64) NOT NULL,
    paid_amount DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_tickets_code UNIQUE (ticket_code),
    CONSTRAINT fk_tickets_order_item FOREIGN KEY (order_item_id) REFERENCES dbo.order_items (id),
    CONSTRAINT chk_tickets_paid_amount CHECK (paid_amount >= 0)
);
GO

CREATE TABLE dbo.payments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status NVARCHAR(64) NOT NULL,
    txn_ref NVARCHAR(255) NOT NULL,
    transaction_no NVARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_payments_txn_ref UNIQUE (txn_ref),
    CONSTRAINT uq_payments_id_order UNIQUE (id, order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES dbo.orders (id),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
);
GO

-- The accepted payment must belong to this order. Add the constraint after
-- both tables exist because Order and Payment reference each other.
ALTER TABLE dbo.orders
    ADD CONSTRAINT fk_orders_accepted_payment
    FOREIGN KEY (accepted_payment_id, id) REFERENCES dbo.payments (id, order_id);

CREATE TABLE dbo.refunds (
    id BIGINT IDENTITY(1,1) NOT NULL,
    order_id BIGINT NOT NULL,
    payment_id BIGINT NULL,
    amount DECIMAL(19,2) NOT NULL,
    purpose NVARCHAR(64) NOT NULL,
    reason_type NVARCHAR(64) NULL,
    reason NVARCHAR(MAX) NULL,
    rejection_reason NVARCHAR(MAX) NULL,
    status NVARCHAR(64) NOT NULL,
    current_attempt_id NVARCHAR(255) NULL,
    provider_reference NVARCHAR(255) NULL,
    processed_at DATETIME2(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES dbo.orders (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id, order_id) REFERENCES dbo.payments (id, order_id),
    CONSTRAINT chk_refunds_amount CHECK (amount >= 0)
);
GO

CREATE TABLE dbo.refund_tickets (
    refund_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    PRIMARY KEY (refund_id, ticket_id),
    CONSTRAINT fk_refund_tickets_refund FOREIGN KEY (refund_id) REFERENCES dbo.refunds (id),
    CONSTRAINT fk_refund_tickets_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.tickets (id)
);
GO

GO
CREATE OR ALTER VIEW dbo.order_totals AS
SELECT
    o.*,
    COALESCE(item_totals.subtotal_amount, 0) AS subtotal_amount,
    COALESCE(item_totals.subtotal_amount, 0) - o.discount_amount AS total_amount
FROM dbo.orders AS o
LEFT JOIN (
    SELECT order_id, SUM(quantity * unit_price) AS subtotal_amount
    FROM dbo.order_items
    GROUP BY order_id
) AS item_totals ON item_totals.order_id = o.id;

GO
CREATE OR ALTER VIEW dbo.ticket_locations AS
SELECT t.*, oi.zone_id, oi.seat_id
FROM dbo.tickets AS t
JOIN dbo.order_items AS oi ON oi.id = t.order_item_id;

GO
CREATE INDEX idx_ticket_holds_expiry ON dbo.ticket_holds (status, expires_at);
GO
-- Each transfer identifier is globally unique. The ledgers retain terminal
-- results so duplicate callbacks cannot pay or refund the same amount twice.
CREATE TABLE dbo.settlement_payouts (
    payout_id NVARCHAR(255) NOT NULL PRIMARY KEY,
    settlement_id BIGINT NOT NULL REFERENCES dbo.settlements(id),
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    status NVARCHAR(16) NOT NULL CHECK (status IN (N'PROCESSING', N'UNKNOWN', N'SUCCEEDED', N'FAILED')),
    provider_reference NVARCHAR(255) NULL,
    processed_at DATETIME2(6) NULL
);
GO
CREATE TABLE dbo.refund_attempts (
    attempt_id NVARCHAR(255) NOT NULL PRIMARY KEY,
    refund_id BIGINT NOT NULL REFERENCES dbo.refunds(id),
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    status NVARCHAR(16) NOT NULL CHECK (status IN (N'PROCESSING', N'UNKNOWN', N'SUCCEEDED', N'FAILED')),
    provider_reference NVARCHAR(255) NULL,
    processed_at DATETIME2(6) NULL
);
GO
CREATE UNIQUE INDEX uq_refund_compensation ON dbo.refunds(payment_id)
WHERE purpose = N'COMPENSATION' AND payment_id IS NOT NULL;
GO
