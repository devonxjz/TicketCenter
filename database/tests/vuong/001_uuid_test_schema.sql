-- Test fixture only. Caller creates a fresh isolated database; never installs in a shared DB.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
BEGIN TRANSACTION;
IF DB_NAME() NOT LIKE N'TC[_]Vuong[_]Test[_]%'
    THROW 52790, 'A TC_Vuong_Test_ database is required', 1;
IF EXISTS (SELECT 1 FROM sys.tables WHERE is_ms_shipped=0)
    THROW 52791, 'Fixture requires an empty database', 1;
CREATE TABLE dbo.tc_vuong_test_fixture(fixture_key varchar(40) NOT NULL PRIMARY KEY);
INSERT dbo.tc_vuong_test_fixture VALUES ('vuong-finance-v1');
CREATE TABLE dbo.tc_users (
 id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(), email nvarchar(320) NOT NULL UNIQUE,
 user_name nvarchar(100) NOT NULL UNIQUE, full_name nvarchar(200) NOT NULL, phone nvarchar(30),
 status varchar(20) NOT NULL DEFAULT 'ACTIVE', password_hash nvarchar(255) NOT NULL DEFAULT 'TEST_ONLY_NOT_A_PASSWORD',
 auth_version int NOT NULL DEFAULT 0, email_verified_at datetime2(3), platform_role varchar(20) NOT NULL DEFAULT 'CUSTOMER',
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(), updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_organizations (
 id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),name nvarchar(200) NOT NULL,
 contact_email nvarchar(320) NOT NULL,contact_phone nvarchar(30),description nvarchar(2000),requester_id uniqueidentifier REFERENCES dbo.tc_users(id),
 status varchar(30) NOT NULL DEFAULT 'APPROVED',rejection_reason nvarchar(2000),
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(), updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_organization_memberships (
 id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),user_id uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),
 organization_id uniqueidentifier NOT NULL REFERENCES dbo.tc_organizations(id),role varchar(30) NOT NULL,active bit NOT NULL DEFAULT 1,
 joined_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),version bigint DEFAULT 0,UNIQUE(user_id,organization_id,role));
CREATE TABLE dbo.tc_commission_rules (
 id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),organization_id uniqueidentifier NOT NULL REFERENCES dbo.tc_organizations(id),
 rate_percent decimal(12,4) NOT NULL,fixed_fee decimal(19,0) NOT NULL,effective_from datetime2(3) NOT NULL,effective_to datetime2(3) NOT NULL,
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_events (
 id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),organization_id uniqueidentifier NOT NULL REFERENCES dbo.tc_organizations(id),
 commission_rule_id uniqueidentifier REFERENCES dbo.tc_commission_rules(id),title nvarchar(300) NOT NULL,description nvarchar(max) NOT NULL DEFAULT '',
 category varchar(30) NOT NULL DEFAULT 'OTHER',venue_name nvarchar(300) NOT NULL DEFAULT '',venue_address nvarchar(1000) NOT NULL DEFAULT '',cover_image_url nvarchar(1000),
 sale_start datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),sale_end datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 start_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),end_time datetime2(3) NOT NULL,status varchar(30) NOT NULL DEFAULT 'PUBLISHED',rejection_reason nvarchar(2000),
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_ticket_holds(id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),buyer_id uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),
 event_id uniqueidentifier NOT NULL REFERENCES dbo.tc_events(id),status varchar(30) NOT NULL DEFAULT 'CONVERTED',expires_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_orders (
 id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),buyer_id uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),event_id uniqueidentifier NOT NULL REFERENCES dbo.tc_events(id),
 ticket_hold_id uniqueidentifier REFERENCES dbo.tc_ticket_holds(id),coupon_id uniqueidentifier,accepted_payment_id uniqueidentifier,
 order_code nvarchar(50) NOT NULL UNIQUE,status varchar(30) NOT NULL,discount_amount decimal(19,0) NOT NULL DEFAULT 0,
 subtotal_amount decimal(19,0) NOT NULL,total_amount decimal(19,0) NOT NULL,paid_at datetime2(3),
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_payments (
 id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),order_id uniqueidentifier NOT NULL REFERENCES dbo.tc_orders(id),
 amount decimal(19,0) NOT NULL,status varchar(20) NOT NULL,txn_ref nvarchar(200) NOT NULL UNIQUE,transaction_no nvarchar(200),paid_at datetime2(3),
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
ALTER TABLE dbo.tc_orders ADD CONSTRAINT FK_Fixture_AcceptedPayment FOREIGN KEY(accepted_payment_id) REFERENCES dbo.tc_payments(id);
CREATE TABLE dbo.tc_order_items(id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),order_id uniqueidentifier NOT NULL REFERENCES dbo.tc_orders(id),
 zone_id uniqueidentifier,seat_id uniqueidentifier,zone_name_snapshot nvarchar(200) NOT NULL DEFAULT '',seat_label_snapshot nvarchar(100),quantity int NOT NULL DEFAULT 1,unit_price decimal(19,0) NOT NULL DEFAULT 0,
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_tickets(id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),order_item_id uniqueidentifier NOT NULL REFERENCES dbo.tc_order_items(id),
 ticket_code nvarchar(120) NOT NULL UNIQUE,status varchar(30) NOT NULL DEFAULT 'ACTIVE',paid_amount decimal(19,0) NOT NULL,issued_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),used_at datetime2(3),
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_refunds (
 id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),order_id uniqueidentifier NOT NULL REFERENCES dbo.tc_orders(id),payment_id uniqueidentifier REFERENCES dbo.tc_payments(id),
 amount decimal(19,0) NOT NULL,purpose varchar(30) NOT NULL,reason_type varchar(30),reason nvarchar(2000),rejection_reason nvarchar(2000),
 status varchar(40) NOT NULL,current_attempt_id uniqueidentifier,provider_reference nvarchar(200),processed_at datetime2(3),
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_refund_transfer_logs(id uniqueidentifier NOT NULL PRIMARY KEY,refund_id uniqueidentifier NOT NULL REFERENCES dbo.tc_refunds(id),
 attempt_no int NOT NULL,result varchar(30) NOT NULL,provider_reference nvarchar(200),processed_at datetime2(3),failure_reason nvarchar(2000));
CREATE TABLE dbo.tc_settlements (
 id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),event_id uniqueidentifier NOT NULL UNIQUE REFERENCES dbo.tc_events(id),status varchar(20) NOT NULL DEFAULT 'DRAFT',
 gross_revenue decimal(19,0) NOT NULL DEFAULT 0,total_refund decimal(19,0) NOT NULL DEFAULT 0,total_commission decimal(19,0) NOT NULL DEFAULT 0,
 net_payable AS (gross_revenue-total_refund-total_commission) PERSISTED,paid_amount decimal(19,0) NOT NULL DEFAULT 0,pending_amount decimal(19,0) NOT NULL DEFAULT 0,
 confirmed_at datetime2(3),created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_settlement_order_snapshots(id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),
 settlement_id uniqueidentifier NOT NULL REFERENCES dbo.tc_settlements(id),order_id uniqueidentifier NOT NULL REFERENCES dbo.tc_orders(id),
 gross_amount decimal(19,0) NOT NULL,refund_amount decimal(19,0) NOT NULL,commission_amount decimal(19,0) NOT NULL,net_amount decimal(19,0) NOT NULL,
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),UNIQUE(settlement_id,order_id));
CREATE TABLE dbo.tc_settlement_transfer_logs(id uniqueidentifier NOT NULL PRIMARY KEY,settlement_id uniqueidentifier NOT NULL REFERENCES dbo.tc_settlements(id),
 amount decimal(19,0) NOT NULL,status varchar(30) NOT NULL,result varchar(30) NOT NULL,provider_reference nvarchar(200),processed_at datetime2(3),created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_audit_logs(id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),actor_id uniqueidentifier REFERENCES dbo.tc_users(id),
 action nvarchar(100) NOT NULL,entity_type nvarchar(100) NOT NULL,entity_id nvarchar(100),metadata_json nvarchar(max),
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
CREATE TABLE dbo.tc_outbox(id uniqueidentifier NOT NULL PRIMARY KEY DEFAULT NEWSEQUENTIALID(),event_type varchar(100) NOT NULL,payload nvarchar(max) NOT NULL,
 status varchar(30) NOT NULL DEFAULT 'PENDING',available_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),attempts int NOT NULL DEFAULT 0,
 lease_owner varchar(200),lease_until datetime2(3),published_at datetime2(3),created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
COMMIT;
GO
