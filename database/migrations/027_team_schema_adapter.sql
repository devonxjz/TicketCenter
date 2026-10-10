/* Install before 027_vuong_finance.sql. Finance-only compatibility for the two
   observed UUID layouts. Keeps legacy columns, attempt tables and audit IDs.
   Caller owns transaction/migration record; never creates a base schema. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
IF OBJECT_ID('dbo.tc_orders','U') IS NULL OR OBJECT_ID('dbo.tc_payments','U') IS NULL
 OR OBJECT_ID('dbo.tc_audit_logs','U') IS NULL OR OBJECT_ID('dbo.tc_commission_rules','U') IS NULL
 THROW 52700,'Finance adapter requires the installed owner schema',1;
IF COL_LENGTH('dbo.tc_orders','buyer_id') IS NULL AND COL_LENGTH('dbo.tc_orders','user_id') IS NULL
 THROW 52700,'Unknown order buyer mapping',1;
IF COL_LENGTH('dbo.tc_payments','paid_at') IS NULL AND COL_LENGTH('dbo.tc_payments','captured_at') IS NULL
 THROW 52700,'Unknown payment capture mapping',1;
IF COL_LENGTH('dbo.tc_refunds','order_id') IS NULL AND COL_LENGTH('dbo.tc_refund_obligations','current_attempt_id') IS NULL
 THROW 52700,'Canonical refund obligations are required',1;
IF COL_LENGTH('dbo.tc_audit_logs','entity_type') IS NULL AND COL_LENGTH('dbo.tc_audit_logs','aggregate_type') IS NULL
 THROW 52700,'Unknown audit mapping',1;
IF COL_LENGTH('dbo.tc_audit_logs','entity_type') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_audit_logs') AND name='id' AND TYPE_NAME(user_type_id)='uniqueidentifier')
 THROW 52700,'Canonical audit requires UUID identifiers',1;
IF COL_LENGTH('dbo.tc_commission_rules','created_at') IS NULL
 ALTER TABLE dbo.tc_commission_rules ADD created_at datetime2(3) NOT NULL CONSTRAINT DF_Vuong_RuleCreated DEFAULT SYSUTCDATETIME();
IF COL_LENGTH('dbo.tc_commission_rules','updated_at') IS NULL
 ALTER TABLE dbo.tc_commission_rules ADD updated_at datetime2(3) NOT NULL CONSTRAINT DF_Vuong_RuleUpdated DEFAULT SYSUTCDATETIME();
GO
DECLARE @buyer sysname=CASE WHEN COL_LENGTH('dbo.tc_orders','buyer_id') IS NOT NULL THEN 'buyer_id' ELSE 'user_id' END;
DECLARE @projection nvarchar(max)=N'CREATE OR ALTER VIEW dbo.vw_VuongOrders AS SELECT id,'+QUOTENAME(@buyer)+N' buyer_id,event_id,status,accepted_payment_id,paid_at,subtotal_amount,discount_amount,total_amount FROM dbo.tc_orders;';
EXEC(@projection);
DECLARE @captured sysname=CASE WHEN COL_LENGTH('dbo.tc_payments','paid_at') IS NOT NULL THEN 'paid_at' ELSE 'captured_at' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongPayments AS SELECT id,order_id,status,amount,'+QUOTENAME(@captured)+N' paid_at FROM dbo.tc_payments;';
EXEC(@projection);
DECLARE @refunds sysname=CASE WHEN COL_LENGTH('dbo.tc_refunds','order_id') IS NOT NULL THEN 'tc_refunds' ELSE 'tc_refund_obligations' END;
SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS SELECT id,order_id,amount,purpose,status,current_attempt_id,processed_at FROM dbo.'+QUOTENAME(@refunds)+N';';
IF @refunds='tc_refund_obligations'
 /* The owner's compensation mirror creates one obligation per legacy attempt.
    Hide only a failed mirror after the SAME capture is fully compensated by
    timestamped, verified successes. Partial/unknown/manual debts stay visible;
    no history, status or transfer evidence is rewritten. */
 SET @projection=N'CREATE OR ALTER VIEW dbo.vw_VuongRefunds AS
 SELECT r.id,r.order_id,r.amount,r.purpose,r.status,r.current_attempt_id,r.processed_at
 FROM dbo.tc_refund_obligations r WHERE NOT (
 r.purpose=''PAYMENT_COMPENSATION'' AND r.status=''RETRYABLE'' AND EXISTS (
 SELECT 1 FROM dbo.tc_refunds failed JOIN dbo.tc_payments p ON p.id=failed.payment_id
 WHERE failed.id=r.id AND failed.refund_request_id IS NULL AND failed.status=''FAILED''
 AND failed.purpose=''PAYMENT_COMPENSATION'' AND failed.payment_id=r.payment_id
 AND p.order_id=r.order_id AND p.status=''CAPTURED'' AND r.amount<=p.amount
 AND p.amount>0 AND p.amount<=(SELECT COALESCE(SUM(done.amount),0)
 FROM dbo.tc_refund_obligations done WHERE done.payment_id=p.id AND done.order_id=r.order_id
 AND done.purpose=''PAYMENT_COMPENSATION'' AND done.status=''COMPLETED'' AND done.processed_at IS NOT NULL
 AND EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.refund_id=done.id
 AND l.id=done.current_attempt_id AND l.result=''SUCCEEDED'' AND l.processed_at IS NOT NULL))));';
EXEC(@projection);
IF COL_LENGTH('dbo.tc_audit_logs','entity_type') IS NOT NULL
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT id,actor_id,action,entity_type,entity_id,metadata_json,created_at,updated_at FROM dbo.tc_audit_logs;');
ELSE
BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_audit_logs') AND name='id' AND TYPE_NAME(user_type_id)='bigint' AND is_identity=1)
  THROW 52700,'Legacy audit requires an identity BIGINT key',1;
 /* The zero-padded 128-bit legacy key is an injective UUID projection. It is
    stable across reads/restarts without rekeying or updating historical rows. */
 EXEC(N'CREATE OR ALTER VIEW dbo.vw_VuongAuditLogs AS SELECT CONVERT(uniqueidentifier,CONVERT(binary(16),id)) id,actor_id,action,aggregate_type entity_type,CONVERT(varchar(100),aggregate_id) entity_id,detail metadata_json,created_at,created_at updated_at FROM dbo.tc_audit_logs;');
 EXEC(N'CREATE OR ALTER TRIGGER dbo.trg_VuongLegacyAuditInsert ON dbo.vw_VuongAuditLogs INSTEAD OF INSERT AS
 BEGIN
  SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted WHERE TRY_CONVERT(uniqueidentifier,entity_id) IS NULL) THROW 52702,''Invalid finance audit entity identifier'',1;
  /* Internal NEWID supplied by finance procedures is not a public audit ID.
     The legacy store allocates its own identity; reads return its stable UUID. */
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail,created_at)
  SELECT actor_id,action,entity_type,CONVERT(uniqueidentifier,entity_id),metadata_json,created_at FROM inserted;
 END;');
END;
GO
