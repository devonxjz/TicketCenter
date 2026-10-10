/* UUID/tc_* finance only. No base-schema creation, legacy writes, credentials or database selection.
   Run with a migration principal after prerequisite catalog verification. GO-aware runner required. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
DECLARE @required TABLE(t sysname,c sysname);
INSERT @required VALUES
('tc_users','id'),('tc_users','status'),('tc_users','platform_role'),('tc_users','auth_version'),
('tc_organizations','id'),('tc_organization_memberships','user_id'),('tc_organization_memberships','organization_id'),('tc_organization_memberships','role'),('tc_organization_memberships','active'),
('tc_commission_rules','id'),('tc_commission_rules','organization_id'),('tc_commission_rules','rate_percent'),('tc_commission_rules','fixed_fee'),('tc_commission_rules','effective_from'),('tc_commission_rules','effective_to'),('tc_commission_rules','created_at'),('tc_commission_rules','updated_at'),
('tc_events','id'),('tc_events','organization_id'),('tc_events','commission_rule_id'),('tc_events','title'),('tc_events','status'),('tc_events','end_time'),
('tc_orders','id'),('tc_orders','buyer_id'),('tc_orders','event_id'),('tc_orders','status'),('tc_orders','accepted_payment_id'),('tc_orders','paid_at'),('tc_orders','subtotal_amount'),('tc_orders','discount_amount'),('tc_orders','total_amount'),
('tc_payments','id'),('tc_payments','order_id'),('tc_payments','status'),('tc_payments','amount'),('tc_payments','paid_at'),
('tc_refunds','id'),('tc_refunds','order_id'),('tc_refunds','amount'),('tc_refunds','purpose'),('tc_refunds','status'),('tc_refunds','processed_at'),
('tc_refund_transfer_logs','id'),('tc_refund_transfer_logs','refund_id'),('tc_refund_transfer_logs','result'),('tc_refund_transfer_logs','processed_at'),
('tc_settlements','id'),('tc_settlements','event_id'),('tc_settlements','status'),('tc_settlements','gross_revenue'),('tc_settlements','total_refund'),('tc_settlements','total_commission'),('tc_settlements','net_payable'),('tc_settlements','paid_amount'),('tc_settlements','pending_amount'),('tc_settlements','confirmed_at'),('tc_settlements','created_at'),('tc_settlements','updated_at'),
('tc_settlement_order_snapshots','id'),('tc_settlement_order_snapshots','settlement_id'),('tc_settlement_order_snapshots','order_id'),('tc_settlement_order_snapshots','gross_amount'),('tc_settlement_order_snapshots','refund_amount'),('tc_settlement_order_snapshots','commission_amount'),('tc_settlement_order_snapshots','net_amount'),
('tc_settlement_transfer_logs','id'),('tc_settlement_transfer_logs','settlement_id'),('tc_settlement_transfer_logs','amount'),('tc_settlement_transfer_logs','status'),('tc_settlement_transfer_logs','result'),('tc_settlement_transfer_logs','provider_reference'),('tc_settlement_transfer_logs','processed_at'),
('tc_order_items','id'),('tc_order_items','order_id'),('tc_tickets','order_item_id'),('tc_tickets','status'),
('tc_audit_logs','id'),('tc_audit_logs','actor_id'),('tc_audit_logs','action'),('tc_audit_logs','entity_type'),('tc_audit_logs','entity_id'),('tc_audit_logs','metadata_json'),('tc_audit_logs','created_at'),
('tc_outbox','id'),('tc_outbox','event_type'),('tc_outbox','payload'),('tc_outbox','status');
INSERT @required VALUES ('tc_audit_logs','updated_at'),('tc_settlement_order_snapshots','created_at'),('tc_settlement_order_snapshots','updated_at'),('tc_settlement_transfer_logs','created_at');
INSERT @required VALUES ('tc_refunds','current_attempt_id');
IF EXISTS(SELECT 1 FROM @required WHERE COL_LENGTH('dbo.'+t,c) IS NULL)
 THROW 52700,'Canonical prerequisite column missing; owner schema must be installed first',1;
IF EXISTS(SELECT 1 FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id WHERE t.name IN
 ('tc_users','tc_organizations','tc_events','tc_orders','tc_payments','tc_refunds','tc_commission_rules','tc_settlements','tc_settlement_order_snapshots','tc_settlement_transfer_logs') AND c.name='id' AND TYPE_NAME(c.system_type_id)<>'uniqueidentifier')
 THROW 52700,'Canonical identifiers must be UUID',1;
IF EXISTS(SELECT 1 FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id WHERE t.name IN
 ('tc_commission_rules','tc_orders','tc_payments','tc_refunds','tc_settlements','tc_settlement_order_snapshots','tc_settlement_transfer_logs')
 AND c.name IN ('fixed_fee','total_amount','subtotal_amount','discount_amount','amount','gross_revenue','total_refund','total_commission','paid_amount','pending_amount','gross_amount','refund_amount','commission_amount','net_amount')
 AND NOT (TYPE_NAME(c.system_type_id)='bigint' OR (TYPE_NAME(c.system_type_id) IN ('decimal','numeric') AND c.scale=0)))
 THROW 52700,'Canonical money must be integer VND',1;
IF OBJECT_ID('dbo.tc_finance_mutations') IS NULL
CREATE TABLE dbo.tc_finance_mutations(
 idempotency_key uniqueidentifier NOT NULL PRIMARY KEY, actor_id uniqueidentifier NOT NULL REFERENCES dbo.tc_users(id),
 operation varchar(30) NOT NULL,fingerprint binary(32) NOT NULL,resource_id uniqueidentifier NOT NULL,response_json nvarchar(2000) NULL,
 created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
IF COL_LENGTH('dbo.tc_finance_mutations','response_json') IS NULL ALTER TABLE dbo.tc_finance_mutations ADD response_json nvarchar(2000) NULL;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.tc_commission_rules') AND name='CK_Vuong_CommissionTerms')
 ALTER TABLE dbo.tc_commission_rules WITH CHECK ADD CONSTRAINT CK_Vuong_CommissionTerms CHECK(rate_percent>=0 AND rate_percent<=100 AND fixed_fee>=0 AND effective_from<effective_to);
IF COL_LENGTH('dbo.tc_settlement_transfer_logs','request_fingerprint') IS NULL
 ALTER TABLE dbo.tc_settlement_transfer_logs ADD request_fingerprint binary(32) NULL;
IF COL_LENGTH('dbo.tc_settlement_transfer_logs','requested_reference') IS NULL
 ALTER TABLE dbo.tc_settlement_transfer_logs ADD requested_reference nvarchar(200) NULL;
IF COL_LENGTH('dbo.tc_settlement_transfer_logs','verification_state') IS NULL
 ALTER TABLE dbo.tc_settlement_transfer_logs ADD verification_state varchar(20) NOT NULL CONSTRAINT DF_Vuong_VerificationState DEFAULT 'PENDING';
IF OBJECT_ID('dbo.tc_vuong_simulated_transfers') IS NULL
 CREATE TABLE dbo.tc_vuong_simulated_transfers(payout_id uniqueidentifier NOT NULL PRIMARY KEY,
 fingerprint binary(32) NOT NULL,result varchar(20) NOT NULL CHECK(result IN('SUCCEEDED','FAILED','UNKNOWN')),
 provider_reference varchar(100) NULL,created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),call_count int NOT NULL DEFAULT 1 CHECK(call_count=1));
GO
UPDATE dbo.tc_settlement_transfer_logs SET request_fingerprint=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),settlement_id),'|',amount,'|',COALESCE(provider_reference,''))),
 requested_reference=provider_reference,verification_state=CASE WHEN status IN('SUCCEEDED','FAILED') THEN status ELSE 'PENDING' END WHERE request_fingerprint IS NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_Vuong_Order_Event' AND object_id=OBJECT_ID('dbo.tc_orders'))
 CREATE INDEX IX_Vuong_Order_Event ON dbo.tc_orders(event_id,status,paid_at,id) INCLUDE(total_amount,accepted_payment_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_Vuong_Refund_Order' AND object_id=OBJECT_ID('dbo.tc_refunds'))
 CREATE INDEX IX_Vuong_Refund_Order ON dbo.tc_refunds(order_id,status,purpose,id) INCLUDE(amount,processed_at);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_Vuong_Payment_Order' AND object_id=OBJECT_ID('dbo.tc_payments'))
 CREATE INDEX IX_Vuong_Payment_Order ON dbo.tc_payments(order_id,status,id) INCLUDE(amount,paid_at);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_Vuong_Settlement_Event' AND object_id=OBJECT_ID('dbo.tc_settlements'))
 CREATE UNIQUE INDEX UX_Vuong_Settlement_Event ON dbo.tc_settlements(event_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UX_Vuong_Snapshot_Order' AND object_id=OBJECT_ID('dbo.tc_settlement_order_snapshots'))
 CREATE UNIQUE INDEX UX_Vuong_Snapshot_Order ON dbo.tc_settlement_order_snapshots(settlement_id,order_id);
GO
CREATE OR ALTER FUNCTION dbo.fn_CalculateCommission(@remaining_amount decimal(19,0),@rate_percent decimal(12,4),@fixed_fee decimal(19,0))
RETURNS decimal(19,0) AS
BEGIN
 IF @remaining_amount IS NULL OR @rate_percent IS NULL OR @fixed_fee IS NULL OR @remaining_amount<0 OR @rate_percent<0 OR @rate_percent>100 OR @fixed_fee<0 RETURN NULL;
 IF @remaining_amount=0 RETURN 0;
 DECLARE @fee decimal(38,4)=ROUND(CAST(@remaining_amount AS decimal(23,0))*@rate_percent/100,0)+@fixed_fee;
 RETURN CONVERT(decimal(19,0),CASE WHEN @fee>@remaining_amount THEN @remaining_amount ELSE @fee END);
END;
GO
CREATE OR ALTER VIEW dbo.vw_OrderFinancialSummary AS
SELECT o.id order_id,o.buyer_id owner_id,o.event_id,e.organization_id,o.status,o.paid_at,o.subtotal_amount,o.discount_amount,o.total_amount,
 CAST(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.id IS NOT NULL AND p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount ELSE 0 END AS decimal(19,0)) ticket_revenue,
 CAST(CASE WHEN o.status='PAID' THEN COALESCE(r.ticket_refund,0) ELSE 0 END AS decimal(19,0)) ticket_refund,
 CAST(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.id IS NOT NULL AND p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount-COALESCE(r.ticket_refund,0) ELSE 0 END AS decimal(19,0)) remaining_amount,
 dbo.fn_CalculateCommission(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount-COALESCE(r.ticket_refund,0) ELSE 0 END,c.rate_percent,c.fixed_fee) commission_amount,
 CAST(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount-COALESCE(r.ticket_refund,0) ELSE 0 END-
 dbo.fn_CalculateCommission(CASE WHEN o.status='PAID' AND (o.total_amount=0 OR (p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount)) THEN o.total_amount-COALESCE(r.ticket_refund,0) ELSE 0 END,c.rate_percent,c.fixed_fee) AS decimal(19,0)) net_amount,
 CAST(COALESCE(pc.amount,0) AS decimal(19,0)) compensation_captured,CAST(COALESCE(r.compensation_refund,0) AS decimal(19,0)) compensation_refund
FROM dbo.tc_orders o JOIN dbo.tc_events e ON e.id=o.event_id LEFT JOIN dbo.tc_payments p ON p.id=o.accepted_payment_id
LEFT JOIN dbo.tc_commission_rules c ON c.id=e.commission_rule_id
OUTER APPLY(SELECT SUM(CASE WHEN purpose='CUSTOMER_REFUND' THEN amount ELSE 0 END) ticket_refund,SUM(CASE WHEN purpose='PAYMENT_COMPENSATION' THEN amount ELSE 0 END) compensation_refund FROM dbo.tc_refunds WHERE order_id=o.id AND status='COMPLETED') r
OUTER APPLY(SELECT SUM(amount) amount FROM dbo.tc_payments WHERE order_id=o.id AND status='CAPTURED' AND (o.accepted_payment_id IS NULL OR id<>o.accepted_payment_id)) pc;
GO
CREATE OR ALTER VIEW dbo.vw_SettlementPayoutBalance AS
SELECT s.id settlement_id,s.event_id,e.organization_id,s.status,s.gross_revenue,s.total_refund,s.total_commission,
 s.net_payable,s.paid_amount,s.pending_amount,CAST(s.net_payable-s.paid_amount-s.pending_amount AS decimal(19,0)) available_to_pay,s.confirmed_at
FROM dbo.tc_settlements s JOIN dbo.tc_events e ON e.id=s.event_id;
GO
CREATE OR ALTER VIEW dbo.vw_EventSalesReport AS
SELECT e.id event_id,e.organization_id,e.title event_title,e.status event_status,e.end_time,
 COALESCE(f.paid_order_count,0) paid_order_count,COALESCE(t.ticket_count,0) ticket_count,COALESCE(t.valid_ticket_count,0) valid_ticket_count,COALESCE(t.refunded_ticket_count,0) refunded_ticket_count,
 CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN s.gross_revenue ELSE COALESCE(f.gross_revenue,0) END AS decimal(19,0)) gross_revenue,
 CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN s.total_refund ELSE COALESCE(f.total_refund,0) END AS decimal(19,0)) total_refund,
 CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN s.total_commission ELSE COALESCE(f.total_commission,0) END AS decimal(19,0)) total_commission,
 CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN s.net_payable ELSE COALESCE(f.net_payable,0) END AS decimal(19,0)) net_payable,
 s.id settlement_id,s.status settlement_status,CAST(CASE WHEN s.status IN('CONFIRMED','PAID') THEN 1 ELSE 0 END AS bit) is_confirmed
FROM dbo.tc_events e LEFT JOIN dbo.tc_settlements s ON s.event_id=e.id
OUTER APPLY(SELECT COUNT_BIG(*) paid_order_count,SUM(ticket_revenue) gross_revenue,SUM(ticket_refund) total_refund,SUM(commission_amount) total_commission,SUM(net_amount) net_payable FROM dbo.vw_OrderFinancialSummary WHERE event_id=e.id AND status='PAID') f
OUTER APPLY(SELECT COUNT_BIG(*) ticket_count,SUM(CASE WHEN t.status IN('ACTIVE','USED') THEN CONVERT(bigint,1) ELSE 0 END) valid_ticket_count,SUM(CASE WHEN t.status='REFUNDED' THEN CONVERT(bigint,1) ELSE 0 END) refunded_ticket_count FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id JOIN dbo.tc_orders o ON o.id=i.order_id WHERE o.event_id=e.id) t;
GO
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationRevenue(@organization_id uniqueidentifier,@from_utc datetime2(3),@to_utc datetime2(3))
RETURNS TABLE AS RETURN
SELECT e.id event_id,e.organization_id,COALESCE(f.paid_order_count,0) paid_order_count,
 CAST(COALESCE(f.gross_revenue,0) AS decimal(19,0)) gross_revenue,CAST(COALESCE(f.total_refund,0) AS decimal(19,0)) total_refund,
 CAST(COALESCE(f.gross_revenue-f.total_refund,0) AS decimal(19,0)) remaining_amount,CAST(COALESCE(f.total_commission,0) AS decimal(19,0)) total_commission,
 CAST(COALESCE(f.gross_revenue-f.total_refund-f.total_commission,0) AS decimal(19,0)) net_payable
FROM dbo.tc_events e OUTER APPLY (
 SELECT COUNT_BIG(*) paid_order_count,SUM(o.total_amount) gross_revenue,SUM(COALESCE(r.amount,0)) total_refund,
 SUM(dbo.fn_CalculateCommission(o.total_amount-COALESCE(r.amount,0),c.rate_percent,c.fixed_fee)) total_commission
 FROM dbo.tc_orders o LEFT JOIN dbo.tc_payments p ON p.id=o.accepted_payment_id LEFT JOIN dbo.tc_commission_rules c ON c.id=e.commission_rule_id
 OUTER APPLY(SELECT SUM(r.amount) amount FROM dbo.tc_refunds r WHERE r.order_id=o.id AND r.purpose='CUSTOMER_REFUND' AND EXISTS
 (SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=r.id AND l.result='SUCCEEDED' AND l.processed_at<@to_utc)) r
 WHERE o.event_id=e.id AND o.status='PAID' AND o.paid_at>=@from_utc AND o.paid_at<@to_utc AND
 (o.total_amount=0 OR (p.status='CAPTURED' AND p.order_id=o.id AND p.amount=o.total_amount))) f
WHERE (@organization_id IS NULL OR e.organization_id=@organization_id) AND @from_utc<@to_utc;
GO
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationCashFlow(@organization_id uniqueidentifier,@from_utc datetime2(3),@to_utc datetime2(3))
RETURNS TABLE AS RETURN
SELECT e.id event_id,e.organization_id,CAST(COALESCE(p.ticket_captured,0) AS decimal(19,0)) ticket_captured,
 CAST(COALESCE(p.compensation_captured,0) AS decimal(19,0)) compensation_captured,CAST(COALESCE(r.ticket_refund,0) AS decimal(19,0)) ticket_refund,
 CAST(COALESCE(r.compensation_refund,0) AS decimal(19,0)) compensation_refund,
 CAST(COALESCE(p.ticket_captured,0)+COALESCE(p.compensation_captured,0)-COALESCE(r.ticket_refund,0)-COALESCE(r.compensation_refund,0) AS decimal(19,0)) net_cash_flow
FROM dbo.tc_events e OUTER APPLY(SELECT SUM(CASE WHEN p.id=o.accepted_payment_id THEN p.amount ELSE 0 END) ticket_captured,
 SUM(CASE WHEN o.accepted_payment_id IS NULL OR p.id<>o.accepted_payment_id THEN p.amount ELSE 0 END) compensation_captured
 FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id WHERE o.event_id=e.id AND p.status='CAPTURED' AND p.paid_at>=@from_utc AND p.paid_at<@to_utc) p
OUTER APPLY(SELECT SUM(CASE WHEN r.purpose='CUSTOMER_REFUND' THEN r.amount ELSE 0 END) ticket_refund,SUM(CASE WHEN r.purpose='PAYMENT_COMPENSATION' THEN r.amount ELSE 0 END) compensation_refund
 FROM dbo.tc_refunds r JOIN dbo.tc_orders o ON o.id=r.order_id WHERE o.event_id=e.id AND EXISTS
 (SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=r.id AND l.result='SUCCEEDED' AND l.processed_at>=@from_utc AND l.processed_at<@to_utc)) r
WHERE (@organization_id IS NULL OR e.organization_id=@organization_id) AND @from_utc<@to_utc;
GO
CREATE OR ALTER FUNCTION dbo.fn_GetSettlementBlockers(@event_id uniqueidentifier,@now_utc datetime2(3)) RETURNS TABLE AS RETURN
SELECT CAST('EVENT_NOT_ENDED' AS varchar(40)) blocker_type,e.id object_id,CAST('Scheduled event end has not passed' AS nvarchar(200)) detail FROM dbo.tc_events e WHERE e.id=@event_id AND e.end_time>@now_utc
UNION ALL SELECT 'ORDER_PAYMENT',id,'Order payment is unresolved' FROM dbo.tc_orders WHERE event_id=@event_id AND status='PENDING_PAYMENT'
UNION ALL SELECT 'PAYMENT',p.id,'Capture is pending or unknown' FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id WHERE o.event_id=@event_id AND p.status IN('PENDING','UNKNOWN')
UNION ALL SELECT 'REFUND',r.id,'Refund obligation is unresolved' FROM dbo.tc_refunds r JOIN dbo.tc_orders o ON o.id=r.order_id WHERE o.event_id=@event_id AND r.status NOT IN('COMPLETED','REJECTED')
UNION ALL SELECT 'REFUND_VERIFICATION',r.id,'Completed positive refund lacks a verified successful transfer' FROM dbo.tc_refunds r JOIN dbo.tc_orders o ON o.id=r.order_id WHERE o.event_id=@event_id AND r.status='COMPLETED' AND r.amount>0 AND
 (NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=r.id AND l.result='SUCCEEDED' AND l.processed_at IS NOT NULL) OR
 (r.current_attempt_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.id=r.current_attempt_id AND l.refund_id=r.id AND l.result='SUCCEEDED' AND l.processed_at IS NOT NULL)))
UNION ALL SELECT 'CANCELLATION_WORK',x.id,'Cancellation outbox work is unfinished' FROM dbo.tc_outbox x WHERE x.event_type='EVENT_CANCELLED' AND x.status<>'PUBLISHED' AND TRY_CONVERT(uniqueidentifier,JSON_VALUE(CASE WHEN ISJSON(x.payload)=1 THEN x.payload ELSE '{}' END,'$.eventId'))=@event_id
UNION ALL SELECT 'CANCELLED_REVENUE',f.order_id,'Cancelled order still has unrefunded ticket revenue' FROM dbo.vw_OrderFinancialSummary f JOIN dbo.tc_events e ON e.id=f.event_id WHERE e.id=@event_id AND e.status='CANCELLED' AND f.remaining_amount>0
UNION ALL SELECT 'INVALID_FINANCIALS',f.order_id,'Accepted order or commission financial data is invalid' FROM dbo.vw_OrderFinancialSummary f WHERE f.event_id=@event_id AND f.status='PAID' AND (f.commission_amount IS NULL OR f.remaining_amount<0 OR (f.total_amount>0 AND f.ticket_revenue=0));
GO
CREATE OR ALTER PROCEDURE dbo.usp_VuongRequireAdmin @actor_id uniqueidentifier,@auth_version int=NULL AS
BEGIN
 SET NOCOUNT ON;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND platform_role='ADMIN' AND (@auth_version IS NULL OR auth_version=@auth_version))
 THROW 52701,'ADMIN_REQUIRED',1;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_CreateCommissionRule
 @organization_id uniqueidentifier,@actor_id uniqueidentifier,@idempotency_key uniqueidentifier,@rate_percent decimal(12,4),@fixed_fee decimal(19,0),@effective_from datetime2(3),@effective_to datetime2(3),@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY
 BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 IF @idempotency_key IS NULL OR @rate_percent IS NULL OR @rate_percent<0 OR @rate_percent>100 OR @fixed_fee IS NULL OR @fixed_fee<0 OR @effective_from IS NULL OR @effective_to IS NULL OR @effective_from>=@effective_to THROW 52702,'COMMISSION_RULE_INVALID',1;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_organizations WITH(UPDLOCK,HOLDLOCK) WHERE id=@organization_id) THROW 52704,'ORGANIZATION_NOT_FOUND',1;
 DECLARE @fingerprint binary(32)=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@organization_id),'|',@rate_percent,'|',@fixed_fee,'|',CONVERT(varchar(30),@effective_from,126),'|',CONVERT(varchar(30),@effective_to,126))),@rule_id uniqueidentifier,@replay bit=0;
 SELECT @rule_id=resource_id FROM dbo.tc_finance_mutations WITH(UPDLOCK,HOLDLOCK) WHERE idempotency_key=@idempotency_key;
 IF @rule_id IS NOT NULL BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_finance_mutations WHERE idempotency_key=@idempotency_key AND actor_id=@actor_id AND operation='COMMISSION_CREATE' AND fingerprint=@fingerprint) THROW 52709,'IDEMPOTENCY_CONFLICT',1;
 SET @replay=1;
 END ELSE BEGIN
 SET @rule_id=NEWID();
 INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to,created_at,updated_at) VALUES(@rule_id,@organization_id,@rate_percent,@fixed_fee,@effective_from,@effective_to,SYSUTCDATETIME(),SYSUTCDATETIME());
 INSERT dbo.tc_finance_mutations(idempotency_key,actor_id,operation,fingerprint,resource_id,response_json) VALUES(@idempotency_key,@actor_id,'COMMISSION_CREATE',@fingerprint,@rule_id,
 (SELECT @organization_id organizationId,@rate_percent ratePercent,@fixed_fee fixedFee,@effective_from effectiveFrom,@effective_to effectiveTo FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 INSERT dbo.tc_audit_logs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'COMMISSION_CREATED','CommissionRule',CONVERT(varchar(36),@rule_id),(SELECT @organization_id organizationId,@rate_percent ratePercent,@fixed_fee fixedFee FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 SELECT c.id rule_id,COALESCE(TRY_CONVERT(uniqueidentifier,JSON_VALUE(m.response_json,'$.organizationId')),c.organization_id) organization_id,
 COALESCE(TRY_CONVERT(decimal(12,4),JSON_VALUE(m.response_json,'$.ratePercent')),c.rate_percent) rate_percent,
 COALESCE(TRY_CONVERT(decimal(19,0),JSON_VALUE(m.response_json,'$.fixedFee')),c.fixed_fee) fixed_fee,
 COALESCE(TRY_CONVERT(datetime2(3),JSON_VALUE(m.response_json,'$.effectiveFrom')),c.effective_from) effective_from,
 COALESCE(TRY_CONVERT(datetime2(3),JSON_VALUE(m.response_json,'$.effectiveTo')),c.effective_to) effective_to,
 (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE commission_rule_id=c.id) associated_events_count,@replay is_replay FROM dbo.tc_commission_rules c JOIN dbo.tc_finance_mutations m ON m.resource_id=c.id AND m.idempotency_key=@idempotency_key WHERE c.id=@rule_id;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_UpdateCommissionRule
 @rule_id uniqueidentifier,@organization_id uniqueidentifier,@actor_id uniqueidentifier,@idempotency_key uniqueidentifier,@rate_percent decimal(12,4),@fixed_fee decimal(19,0),@effective_from datetime2(3),@effective_to datetime2(3),@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 IF @idempotency_key IS NULL OR @rate_percent IS NULL OR @rate_percent<0 OR @rate_percent>100 OR @fixed_fee IS NULL OR @fixed_fee<0 OR @effective_from IS NULL OR @effective_to IS NULL OR @effective_from>=@effective_to THROW 52702,'COMMISSION_RULE_INVALID',1;
 DECLARE @fingerprint binary(32)=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@rule_id),'|',CONVERT(varchar(36),@organization_id),'|',@rate_percent,'|',@fixed_fee,'|',CONVERT(varchar(30),@effective_from,126),'|',CONVERT(varchar(30),@effective_to,126))),@replay bit=0;
 IF EXISTS(SELECT 1 FROM dbo.tc_finance_mutations WITH(UPDLOCK,HOLDLOCK) WHERE idempotency_key=@idempotency_key) BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_finance_mutations WHERE idempotency_key=@idempotency_key AND actor_id=@actor_id AND operation='COMMISSION_UPDATE' AND fingerprint=@fingerprint AND resource_id=@rule_id) THROW 52709,'IDEMPOTENCY_CONFLICT',1;
 SET @replay=1;
 END ELSE BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_commission_rules WITH(UPDLOCK,HOLDLOCK) WHERE id=@rule_id AND organization_id=@organization_id) THROW 52704,'COMMISSION_RULE_NOT_FOUND',1;
 IF EXISTS(SELECT 1 FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE commission_rule_id=@rule_id) THROW 52709,'RULE_ALREADY_ATTACHED_TO_EVENTS',1;
 UPDATE dbo.tc_commission_rules SET rate_percent=@rate_percent,fixed_fee=@fixed_fee,effective_from=@effective_from,effective_to=@effective_to,updated_at=SYSUTCDATETIME() WHERE id=@rule_id;
 INSERT dbo.tc_finance_mutations(idempotency_key,actor_id,operation,fingerprint,resource_id,response_json) VALUES(@idempotency_key,@actor_id,'COMMISSION_UPDATE',@fingerprint,@rule_id,
 (SELECT @organization_id organizationId,@rate_percent ratePercent,@fixed_fee fixedFee,@effective_from effectiveFrom,@effective_to effectiveTo FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 INSERT dbo.tc_audit_logs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'COMMISSION_UPDATED','CommissionRule',CONVERT(varchar(36),@rule_id),(SELECT @organization_id organizationId,@rate_percent ratePercent,@fixed_fee fixedFee FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 SELECT c.id rule_id,COALESCE(TRY_CONVERT(uniqueidentifier,JSON_VALUE(m.response_json,'$.organizationId')),c.organization_id) organization_id,
 COALESCE(TRY_CONVERT(decimal(12,4),JSON_VALUE(m.response_json,'$.ratePercent')),c.rate_percent) rate_percent,
 COALESCE(TRY_CONVERT(decimal(19,0),JSON_VALUE(m.response_json,'$.fixedFee')),c.fixed_fee) fixed_fee,
 COALESCE(TRY_CONVERT(datetime2(3),JSON_VALUE(m.response_json,'$.effectiveFrom')),c.effective_from) effective_from,
 COALESCE(TRY_CONVERT(datetime2(3),JSON_VALUE(m.response_json,'$.effectiveTo')),c.effective_to) effective_to,
 (SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE commission_rule_id=c.id) associated_events_count,@replay is_replay FROM dbo.tc_commission_rules c JOIN dbo.tc_finance_mutations m ON m.resource_id=c.id AND m.idempotency_key=@idempotency_key WHERE c.id=@rule_id;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_RecalculateSettlement @event_id uniqueidentifier,@actor_id uniqueidentifier,@auth_version int=NULL,@emit_result bit=1
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id) THROW 52704,'EVENT_NOT_FOUND',1;
 DECLARE @settlement_id uniqueidentifier,@status varchar(20),@lock_count bigint;
 SELECT @settlement_id=id,@status=status FROM dbo.tc_settlements WITH(UPDLOCK,HOLDLOCK) WHERE event_id=@event_id;
 IF @status IS NOT NULL AND @status<>'DRAFT' THROW 52709,'SETTLEMENT_NOT_DRAFT',1;
 -- Lock relevant ranges so ordinary owner writes cannot race confirmation, even if their SP omits the event lock.
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE event_id=@event_id;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_payments p WITH(UPDLOCK,HOLDLOCK) JOIN dbo.tc_orders o ON o.id=p.order_id WHERE o.event_id=@event_id;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_refunds r WITH(UPDLOCK,HOLDLOCK) JOIN dbo.tc_orders o ON o.id=r.order_id WHERE o.event_id=@event_id;
 IF EXISTS(SELECT 1 FROM dbo.vw_OrderFinancialSummary WHERE event_id=@event_id AND status='PAID' AND (commission_amount IS NULL OR remaining_amount<0 OR (total_amount>0 AND ticket_revenue=0))) THROW 52702,'INVALID_FINANCIALS',1;
 IF EXISTS(SELECT 1 FROM dbo.vw_OrderFinancialSummary WHERE event_id=@event_id AND status='PAID') BEGIN
 IF @settlement_id IS NULL BEGIN SET @settlement_id=NEWID(); INSERT dbo.tc_settlements(id,event_id,status,gross_revenue,total_refund,total_commission,paid_amount,pending_amount,created_at,updated_at) VALUES(@settlement_id,@event_id,'DRAFT',0,0,0,0,0,SYSUTCDATETIME(),SYSUTCDATETIME()); END;
 DELETE dbo.tc_settlement_order_snapshots WHERE settlement_id=@settlement_id;
 INSERT dbo.tc_settlement_order_snapshots(id,settlement_id,order_id,gross_amount,refund_amount,commission_amount,net_amount,created_at,updated_at)
 SELECT NEWID(),@settlement_id,order_id,ticket_revenue,ticket_refund,commission_amount,net_amount,SYSUTCDATETIME(),SYSUTCDATETIME() FROM dbo.vw_OrderFinancialSummary WHERE event_id=@event_id AND status='PAID';
 UPDATE s SET gross_revenue=t.gross,total_refund=t.refund,total_commission=t.commission,updated_at=SYSUTCDATETIME()
 FROM dbo.tc_settlements s CROSS APPLY(SELECT SUM(gross_amount) gross,SUM(refund_amount) refund,SUM(commission_amount) commission FROM dbo.tc_settlement_order_snapshots WHERE settlement_id=s.id) t WHERE s.id=@settlement_id;
 INSERT dbo.tc_audit_logs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'SETTLEMENT_RECALCULATED','Settlement',CONVERT(varchar(36),@settlement_id),(SELECT @event_id eventId FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END ELSE IF @settlement_id IS NOT NULL THROW 52709,'SETTLEMENT_SOURCE_MISSING',1;
 IF @emit_result=1 BEGIN
 IF @settlement_id IS NULL SELECT CAST(NULL AS uniqueidentifier) settlement_id,@event_id event_id,CAST('DRAFT' AS varchar(20)) status,CAST(0 AS decimal(19,0)) gross_revenue,CAST(0 AS decimal(19,0)) total_refund,CAST(0 AS decimal(19,0)) total_commission,CAST(0 AS decimal(19,0)) net_payable,CAST(0 AS decimal(19,0)) paid_amount,CAST(0 AS decimal(19,0)) pending_amount,CAST(0 AS decimal(19,0)) available_to_pay,CAST(NULL AS datetime2(3)) confirmed_at,CAST(0 AS bit) is_replay;
 ELSE SELECT settlement_id,event_id,status,gross_revenue,total_refund,total_commission,net_payable,paid_amount,pending_amount,available_to_pay,confirmed_at,CAST(0 AS bit) is_replay FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id=@settlement_id;
 END;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_ConfirmSettlement @settlement_id uniqueidentifier,@actor_id uniqueidentifier,@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 DECLARE @event_id uniqueidentifier,@status varchar(20),@replay bit=0,@lock_count bigint;
 SELECT @event_id=event_id FROM dbo.tc_settlements WHERE id=@settlement_id;
 IF @event_id IS NULL THROW 52704,'SETTLEMENT_NOT_FOUND',1;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
 SELECT @status=status FROM dbo.tc_settlements WITH(UPDLOCK,HOLDLOCK) WHERE id=@settlement_id;
 IF @status IN('CONFIRMED','PAID') SET @replay=1;
 ELSE BEGIN
 EXEC dbo.usp_RecalculateSettlement @event_id,@actor_id,@auth_version,0;
 SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_outbox WITH(UPDLOCK,HOLDLOCK) WHERE event_type='EVENT_CANCELLED';
 IF EXISTS(SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event_id,SYSUTCDATETIME())) THROW 52709,'SETTLEMENT_BLOCKED',1;
 UPDATE dbo.tc_settlements SET status=CASE WHEN net_payable=0 THEN 'PAID' ELSE 'CONFIRMED' END,confirmed_at=SYSUTCDATETIME(),updated_at=SYSUTCDATETIME() WHERE id=@settlement_id;
 INSERT dbo.tc_audit_logs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'SETTLEMENT_CONFIRMED','Settlement',CONVERT(varchar(36),@settlement_id),(SELECT @event_id eventId FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 SELECT settlement_id,event_id,status,gross_revenue,total_refund,total_commission,net_payable,paid_amount,pending_amount,available_to_pay,confirmed_at,@replay is_replay FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id=@settlement_id;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_RecordPayout
 @settlement_id uniqueidentifier,@payout_id uniqueidentifier,@actor_id uniqueidentifier,@amount decimal(19,0),@reference nvarchar(200)=NULL,@verified_result varchar(20)=NULL,@fingerprint varbinary(32)=NULL,@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 BEGIN TRY BEGIN TRANSACTION;
 EXEC dbo.usp_VuongRequireAdmin @actor_id,@auth_version;
 IF @payout_id IS NULL OR @amount IS NULL OR @amount<=0 OR (@verified_result IS NOT NULL AND @verified_result NOT IN('SUCCEEDED','FAILED','UNKNOWN')) THROW 52702,'PAYOUT_INVALID',1;
 IF @fingerprint IS NULL SET @fingerprint=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@settlement_id),'|',@amount,'|',COALESCE(@reference,'')));
 IF DATALENGTH(@fingerprint)<>32 THROW 52702,'PAYOUT_INVALID',1;
 DECLARE @old varchar(30),@replay bit=0,@state varchar(20),@net decimal(19,0),@paid decimal(19,0),@pending decimal(19,0);
 SELECT @state=status,@net=net_payable,@paid=paid_amount,@pending=pending_amount FROM dbo.tc_settlements WITH(UPDLOCK,HOLDLOCK) WHERE id=@settlement_id;
 IF @state IS NULL THROW 52704,'SETTLEMENT_NOT_FOUND',1;
 SELECT @old=status FROM dbo.tc_settlement_transfer_logs WITH(UPDLOCK,HOLDLOCK) WHERE id=@payout_id;
 IF @old IS NOT NULL BEGIN
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs WHERE id=@payout_id AND settlement_id=@settlement_id AND amount=@amount AND request_fingerprint=@fingerprint AND (@verified_result IS NOT NULL OR requested_reference=@reference OR (requested_reference IS NULL AND @reference IS NULL))) THROW 52709,'PAYOUT_TERMS_CONFLICT',1;
 IF @old=@verified_result AND NOT EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs WHERE id=@payout_id AND (provider_reference=@reference OR (provider_reference IS NULL AND @reference IS NULL))) THROW 52709,'PAYOUT_RESULT_CONFLICT',1;
 IF @verified_result IS NULL OR (@old=@verified_result) OR (@verified_result='UNKNOWN' AND @old='PENDING' AND EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs WHERE id=@payout_id AND verification_state='UNKNOWN')) SET @replay=1;
 ELSE IF @old IN('SUCCEEDED','FAILED') THROW 52709,'PAYOUT_RESULT_CONFLICT',1;
 ELSE BEGIN
 UPDATE dbo.tc_settlement_transfer_logs SET status=CASE WHEN @verified_result='UNKNOWN' THEN 'PENDING' ELSE @verified_result END,result=CASE WHEN @verified_result='UNKNOWN' THEN 'PENDING' ELSE @verified_result END,
 verification_state=@verified_result,provider_reference=CASE WHEN @verified_result='UNKNOWN' THEN provider_reference ELSE @reference END,processed_at=CASE WHEN @verified_result='UNKNOWN' THEN NULL ELSE SYSUTCDATETIME() END WHERE id=@payout_id;
 INSERT dbo.tc_audit_logs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'PAYOUT_RESULT','SettlementTransferLog',CONVERT(varchar(36),@payout_id),(SELECT @settlement_id settlementId,@amount amount,@verified_result result FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 END ELSE BEGIN
 IF @verified_result IS NOT NULL THROW 52709,'PAYOUT_RESERVATION_REQUIRED',1;
 IF @state<>'CONFIRMED' THROW 52709,'SETTLEMENT_NOT_CONFIRMED',1;
 IF EXISTS(SELECT 1 FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=@settlement_id AND status='PENDING' AND verification_state='UNKNOWN') THROW 52709,'PAYOUT_RECONCILIATION_REQUIRED',1;
 IF @net-@paid-@pending<@amount THROW 52709,'PAYOUT_EXCEEDS_BALANCE',1;
 INSERT dbo.tc_settlement_transfer_logs(id,settlement_id,amount,status,result,request_fingerprint,requested_reference,verification_state,created_at)
 VALUES(@payout_id,@settlement_id,@amount,'PENDING','PENDING',@fingerprint,@reference,'PENDING',SYSUTCDATETIME());
 INSERT dbo.tc_audit_logs(id,created_at,updated_at,actor_id,action,entity_type,entity_id,metadata_json) VALUES(NEWID(),SYSUTCDATETIME(),SYSUTCDATETIME(),@actor_id,'PAYOUT_RESERVED','SettlementTransferLog',CONVERT(varchar(36),@payout_id),(SELECT @settlement_id settlementId,@amount amount FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 END;
 SELECT l.id payout_id,l.settlement_id,l.amount,l.status,l.verification_state,l.provider_reference,l.processed_at,@replay is_replay,
 s.status settlement_status,s.net_payable,s.paid_amount,s.pending_amount,CAST(s.net_payable-s.paid_amount-s.pending_amount AS decimal(19,0)) available_to_pay
 FROM dbo.tc_settlement_transfer_logs l JOIN dbo.tc_settlements s ON s.id=l.settlement_id WHERE l.id=@payout_id;
 COMMIT;
 END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
END;
GO
CREATE OR ALTER TRIGGER dbo.trg_VuongCommissionFreeze ON dbo.tc_commission_rules AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id JOIN dbo.tc_events e WITH(UPDLOCK,HOLDLOCK) ON e.commission_rule_id=d.id WHERE i.id IS NULL OR EXISTS(SELECT i.organization_id,i.rate_percent,i.fixed_fee,i.effective_from,i.effective_to EXCEPT SELECT d.organization_id,d.rate_percent,d.fixed_fee,d.effective_from,d.effective_to)) THROW 52709,'RULE_ALREADY_ATTACHED_TO_EVENTS',1;
END;
GO
CREATE OR ALTER TRIGGER dbo.trg_VuongSnapshotFreeze ON dbo.tc_settlement_order_snapshots AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM (SELECT settlement_id FROM inserted UNION SELECT settlement_id FROM deleted) a JOIN dbo.tc_settlements s WITH(UPDLOCK,HOLDLOCK) ON s.id=a.settlement_id WHERE s.status IN('CONFIRMED','PAID')) THROW 52709,'SETTLEMENT_SNAPSHOT_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted WHERE gross_amount<0 OR refund_amount<0 OR commission_amount<0 OR net_amount<0 OR refund_amount+commission_amount>gross_amount OR net_amount<>gross_amount-refund_amount-commission_amount) THROW 52702,'INVALID_FINANCIALS',1;
END;
GO
CREATE OR ALTER TRIGGER dbo.trg_VuongSettlementValidate ON dbo.tc_settlements AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id WHERE i.id IS NULL AND d.status<>'DRAFT') THROW 52709,'SETTLEMENT_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted WHERE status NOT IN('DRAFT','CONFIRMED','PAID') OR gross_revenue<0 OR total_refund<0 OR total_commission<0 OR total_refund+total_commission>gross_revenue OR paid_amount<0 OR pending_amount<0 OR paid_amount+pending_amount>net_payable OR (status='PAID' AND (paid_amount<>net_payable OR pending_amount<>0))) THROW 52702,'SETTLEMENT_BALANCE_INVALID',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE i.event_id<>d.event_id OR (d.status IN('CONFIRMED','PAID') AND EXISTS(SELECT i.gross_revenue,i.total_refund,i.total_commission,i.confirmed_at EXCEPT SELECT d.gross_revenue,d.total_refund,d.total_commission,d.confirmed_at)) OR (d.status='PAID' AND i.status<>'PAID') OR (d.status='CONFIRMED' AND i.status='DRAFT')) THROW 52709,'SETTLEMENT_SNAPSHOT_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted s WHERE s.paid_amount<>COALESCE((SELECT SUM(amount) FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=s.id AND status='SUCCEEDED'),0) OR s.pending_amount<>COALESCE((SELECT SUM(amount) FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=s.id AND status='PENDING'),0)) THROW 52702,'PAYOUT_LEDGER_MISMATCH',1;
 IF EXISTS(SELECT 1 FROM inserted s WHERE s.gross_revenue<>COALESCE((SELECT SUM(gross_amount) FROM dbo.tc_settlement_order_snapshots WHERE settlement_id=s.id),0) OR s.total_refund<>COALESCE((SELECT SUM(refund_amount) FROM dbo.tc_settlement_order_snapshots WHERE settlement_id=s.id),0) OR s.total_commission<>COALESCE((SELECT SUM(commission_amount) FROM dbo.tc_settlement_order_snapshots WHERE settlement_id=s.id),0)) THROW 52702,'SETTLEMENT_SNAPSHOT_MISMATCH',1;
END;
GO
CREATE OR ALTER TRIGGER dbo.trg_VuongTransferLedger ON dbo.tc_settlement_transfer_logs AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id WHERE i.id IS NULL) THROW 52709,'PAYOUT_HISTORY_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id=i.id WHERE d.id IS NULL AND i.status<>'PENDING') THROW 52709,'PAYOUT_RESERVATION_REQUIRED',1;
 IF EXISTS(SELECT 1 FROM inserted WHERE amount<=0 OR status NOT IN('PENDING','SUCCEEDED','FAILED') OR result<>status OR request_fingerprint IS NULL OR verification_state NOT IN('PENDING','UNKNOWN','SUCCEEDED','FAILED') OR (status='PENDING' AND (processed_at IS NOT NULL OR verification_state NOT IN('PENDING','UNKNOWN'))) OR (status IN('SUCCEEDED','FAILED') AND (processed_at IS NULL OR verification_state<>status))) THROW 52702,'PAYOUT_INVALID',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id WHERE EXISTS(SELECT i.settlement_id,i.amount,i.request_fingerprint,i.requested_reference EXCEPT SELECT d.settlement_id,d.amount,d.request_fingerprint,d.requested_reference) OR (d.status IN('SUCCEEDED','FAILED') AND EXISTS(SELECT i.status,i.result,i.provider_reference,i.processed_at,i.verification_state EXCEPT SELECT d.status,d.result,d.provider_reference,d.processed_at,d.verification_state)) OR (d.verification_state='UNKNOWN' AND i.verification_state='PENDING')) THROW 52709,'PAYOUT_HISTORY_IMMUTABLE',1;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.tc_settlements s WITH(UPDLOCK,HOLDLOCK) ON s.id=i.settlement_id WHERE s.status<>'CONFIRMED') THROW 52709,'SETTLEMENT_NOT_CONFIRMED',1;
 UPDATE s SET paid_amount=t.paid,pending_amount=t.pending,status=CASE WHEN t.paid=s.net_payable AND t.pending=0 THEN 'PAID' ELSE 'CONFIRMED' END,updated_at=SYSUTCDATETIME()
 FROM dbo.tc_settlements s JOIN(SELECT DISTINCT settlement_id FROM inserted) a ON a.settlement_id=s.id CROSS APPLY(SELECT COALESCE(SUM(CASE WHEN status='SUCCEEDED' THEN amount ELSE 0 END),0) paid,COALESCE(SUM(CASE WHEN status='PENDING' THEN amount ELSE 0 END),0) pending FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=s.id) t;
END;
GO
CREATE OR ALTER TRIGGER dbo.trg_VuongAuditAppendOnly ON dbo.tc_audit_logs AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 52709,'AUDIT_APPEND_ONLY',1; END;
GO
CREATE OR ALTER TRIGGER dbo.trg_VuongMutationAppendOnly ON dbo.tc_finance_mutations AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 52709,'MUTATION_HISTORY_IMMUTABLE',1; END;
GO
CREATE OR ALTER TRIGGER dbo.trg_VuongSimulatedTransferAppendOnly ON dbo.tc_vuong_simulated_transfers AFTER UPDATE,DELETE AS
BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 52709,'SIMULATED_TRANSFER_HISTORY_IMMUTABLE',1; END;
GO
-- Reject later owner writes that would invalidate a confirmed financial source. These triggers
-- also acquire the Event lock, so conflicts with confirmation abort instead of accepting a stale snapshot.
CREATE OR ALTER TRIGGER dbo.trg_VuongOrderFinancialFreeze ON dbo.tc_orders AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i FULL JOIN deleted d ON d.id=i.id
 CROSS APPLY(SELECT i.event_id event_id UNION SELECT d.event_id) a JOIN dbo.tc_events e WITH(UPDLOCK,HOLDLOCK) ON e.id=a.event_id JOIN dbo.tc_settlements s ON s.event_id=e.id
 WHERE s.status IN('CONFIRMED','PAID') AND (i.id IS NULL OR d.id IS NULL OR EXISTS(SELECT i.buyer_id,i.event_id,i.status,i.accepted_payment_id,i.paid_at,i.total_amount,i.subtotal_amount,i.discount_amount EXCEPT SELECT d.buyer_id,d.event_id,d.status,d.accepted_payment_id,d.paid_at,d.total_amount,d.subtotal_amount,d.discount_amount)))
 THROW 52709,'CONFIRMED_FINANCIAL_SOURCE_IMMUTABLE',1;
END;
GO
CREATE OR ALTER TRIGGER dbo.trg_VuongRefundFinancialFreeze ON dbo.tc_refunds AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i FULL JOIN deleted d ON d.id=i.id
 CROSS APPLY(SELECT i.order_id order_id UNION SELECT d.order_id) a JOIN dbo.tc_orders o ON o.id=a.order_id JOIN dbo.tc_events e WITH(UPDLOCK,HOLDLOCK) ON e.id=o.event_id JOIN dbo.tc_settlements s ON s.event_id=e.id
 WHERE s.status IN('CONFIRMED','PAID') AND (i.purpose='CUSTOMER_REFUND' OR d.purpose='CUSTOMER_REFUND')
 AND (i.id IS NULL OR d.id IS NULL OR EXISTS(SELECT i.order_id,i.amount,i.purpose,i.status,i.processed_at EXCEPT SELECT d.order_id,d.amount,d.purpose,d.status,d.processed_at)))
 THROW 52709,'CONFIRMED_FINANCIAL_SOURCE_IMMUTABLE',1;
END;
GO
CREATE OR ALTER TRIGGER dbo.trg_VuongPaymentFinancialFreeze ON dbo.tc_payments AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i FULL JOIN deleted d ON d.id=i.id
 CROSS APPLY(SELECT i.order_id order_id UNION SELECT d.order_id) a JOIN dbo.tc_orders o ON o.id=a.order_id JOIN dbo.tc_events e WITH(UPDLOCK,HOLDLOCK) ON e.id=o.event_id JOIN dbo.tc_settlements s ON s.event_id=e.id
 WHERE s.status IN('CONFIRMED','PAID') AND (o.accepted_payment_id=COALESCE(i.id,d.id) OR i.status IN('PENDING','UNKNOWN'))
 AND (i.id IS NULL OR d.id IS NULL OR EXISTS(SELECT i.order_id,i.amount,i.status,i.paid_at EXCEPT SELECT d.order_id,d.amount,d.status,d.paid_at)))
 THROW 52709,'CONFIRMED_FINANCIAL_SOURCE_IMMUTABLE',1;
END;
GO
-- Database roles only: no passwords/logins and no db_owner runtime grants. Caller enforces row scope.
IF DATABASE_PRINCIPAL_ID('tc_platform_admin') IS NULL CREATE ROLE tc_platform_admin;
IF DATABASE_PRINCIPAL_ID('tc_manager') IS NULL CREATE ROLE tc_manager;
IF DATABASE_PRINCIPAL_ID('tc_checkin') IS NULL CREATE ROLE tc_checkin;
IF DATABASE_PRINCIPAL_ID('tc_buyer') IS NULL CREATE ROLE tc_buyer;
GRANT EXECUTE ON dbo.usp_CreateCommissionRule TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_UpdateCommissionRule TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_RecalculateSettlement TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_ConfirmSettlement TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_RecordPayout TO tc_platform_admin;
GRANT SELECT ON dbo.vw_OrderFinancialSummary TO tc_platform_admin,tc_manager;
GRANT SELECT ON dbo.vw_EventSalesReport TO tc_platform_admin,tc_manager;
GRANT SELECT ON dbo.vw_SettlementPayoutBalance TO tc_platform_admin,tc_manager;
GRANT SELECT ON dbo.fn_GetOrganizationRevenue TO tc_platform_admin,tc_manager;
GRANT SELECT ON dbo.fn_GetOrganizationCashFlow TO tc_platform_admin,tc_manager;
GRANT SELECT ON dbo.fn_GetSettlementBlockers TO tc_platform_admin;
GRANT SELECT ON dbo.tc_commission_rules TO tc_platform_admin;
GRANT SELECT ON dbo.tc_organizations TO tc_platform_admin;
GRANT SELECT ON dbo.tc_refunds TO tc_platform_admin;
GRANT SELECT ON dbo.tc_settlements TO tc_platform_admin;
GRANT SELECT ON dbo.tc_settlement_order_snapshots TO tc_platform_admin;
GRANT SELECT ON dbo.tc_settlement_transfer_logs TO tc_platform_admin;
GRANT SELECT ON dbo.tc_audit_logs TO tc_platform_admin;
GRANT SELECT(id,status,platform_role,auth_version,full_name) ON dbo.tc_users TO tc_platform_admin,tc_manager;
GRANT SELECT ON dbo.tc_organization_memberships TO tc_platform_admin,tc_manager;
GRANT SELECT ON dbo.tc_events TO tc_platform_admin,tc_manager;
DENY INSERT,UPDATE,DELETE ON dbo.tc_commission_rules TO tc_platform_admin,tc_manager,tc_checkin,tc_buyer;
DENY INSERT,UPDATE,DELETE ON dbo.tc_settlements TO tc_platform_admin,tc_manager,tc_checkin,tc_buyer;
DENY INSERT,UPDATE,DELETE ON dbo.tc_settlement_order_snapshots TO tc_platform_admin,tc_manager,tc_checkin,tc_buyer;
DENY INSERT,UPDATE,DELETE ON dbo.tc_settlement_transfer_logs TO tc_platform_admin,tc_manager,tc_checkin,tc_buyer;
DENY INSERT,UPDATE,DELETE ON dbo.tc_audit_logs TO tc_platform_admin,tc_manager,tc_checkin,tc_buyer;
DENY INSERT,UPDATE,DELETE ON dbo.tc_finance_mutations TO tc_platform_admin,tc_manager,tc_checkin,tc_buyer;
DENY SELECT ON dbo.vw_OrderFinancialSummary TO tc_checkin,tc_buyer;
DENY SELECT ON dbo.vw_EventSalesReport TO tc_checkin,tc_buyer;
DENY SELECT ON dbo.vw_SettlementPayoutBalance TO tc_checkin,tc_buyer;
DENY SELECT ON dbo.fn_GetOrganizationRevenue TO tc_checkin,tc_buyer;
DENY SELECT ON dbo.fn_GetOrganizationCashFlow TO tc_checkin,tc_buyer;
DENY SELECT ON dbo.tc_settlements TO tc_checkin,tc_buyer;
DENY SELECT ON dbo.tc_settlement_order_snapshots TO tc_checkin,tc_buyer;
DENY SELECT ON dbo.tc_settlement_transfer_logs TO tc_checkin,tc_buyer;
-- Simulated provider is an explicit isolated-test capability, never a production runtime grant.
IF DB_NAME() LIKE N'TC[_]Vuong[_]Test[_]%' GRANT SELECT,INSERT ON dbo.tc_vuong_simulated_transfers TO tc_platform_admin;
GO
