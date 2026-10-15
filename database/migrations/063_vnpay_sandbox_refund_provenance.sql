-- No historical/demo backfill: only a payment URL actually generated for sandbox obtains provenance.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
GO
IF OBJECT_ID('dbo.tc_vnpay_payment_provenance','U') IS NULL
 CREATE TABLE dbo.tc_vnpay_payment_provenance(
  payment_id uniqueidentifier NOT NULL PRIMARY KEY REFERENCES dbo.tc_payments(id),
  tmn_code varchar(8) NOT NULL,
  environment varchar(10) NOT NULL CHECK(environment='SANDBOX'),
  transaction_date char(14) NOT NULL,
  created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
GO
CREATE OR ALTER PROCEDURE dbo.usp_RegisterVnpayPaymentProvenance
 @payment_id uniqueidentifier,@actor_id uniqueidentifier,@tmn_code varchar(8),@transaction_date char(14)
WITH EXECUTE AS OWNER
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF LEN(@tmn_code)<>8 OR @tmn_code LIKE '%[^a-zA-Z0-9]%' OR @transaction_date LIKE '%[^0-9]%' OR LEN(@transaction_date)<>14
  THROW 53600,'Invalid sandbox payment provenance',1;
 BEGIN TRAN;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_payments p WITH(UPDLOCK,HOLDLOCK)
  JOIN dbo.tc_orders o ON o.id=p.order_id WHERE p.id=@payment_id AND o.user_id=@actor_id
  AND p.status IN('PENDING','UNKNOWN') AND p.amount>0 AND p.currency='VND')
 BEGIN ROLLBACK; THROW 53601,'Sandbox payment provenance requires an owned pending payment',1; END;
 IF EXISTS(SELECT 1 FROM dbo.tc_vnpay_payment_provenance WHERE payment_id=@payment_id
  AND (tmn_code<>@tmn_code OR transaction_date<>@transaction_date))
 BEGIN ROLLBACK; THROW 53602,'Payment provenance is immutable',1; END;
 IF NOT EXISTS(SELECT 1 FROM dbo.tc_vnpay_payment_provenance WHERE payment_id=@payment_id)
  INSERT dbo.tc_vnpay_payment_provenance(payment_id,tmn_code,environment,transaction_date)
   VALUES(@payment_id,@tmn_code,'SANDBOX',@transaction_date);
 COMMIT;
END;
GO
CREATE OR ALTER VIEW dbo.vw_VnpayRefundContext AS
 SELECT a.refund_id,a.attempt_id,a.amount refund_amount,p.amount payment_amount,p.currency,p.status payment_status,
 p.txn_ref,p.provider_reference payment_reference,v.tmn_code,v.environment,v.transaction_date,a.created_at attempt_created_at
 FROM dbo.vw_RefundAttemptLedger a JOIN dbo.tc_payments p ON p.id=a.payment_id
 JOIN dbo.tc_vnpay_payment_provenance v ON v.payment_id=p.id;
GO
CREATE OR ALTER VIEW dbo.vw_VnpayBuyerPaymentProvenance AS
 SELECT p.id payment_id,o.user_id,v.tmn_code,v.transaction_date
 FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id
 JOIN dbo.tc_vnpay_payment_provenance v ON v.payment_id=p.id
 WHERE p.status IN('PENDING','UNKNOWN') AND v.environment='SANDBOX';
GO
-- Filter BEFORE sp_refund_begin_attempt creates a durable dispatch. Never claim legacy/demo money.
CREATE OR ALTER VIEW dbo.vw_VnpaySandboxRefundCandidates AS
 SELECT q.refund_id,q.attempt_id,q.work_kind,q.created_at,c.tmn_code
 FROM dbo.vw_RefundWorkerCandidates q JOIN dbo.vw_VnpayRefundContext c ON c.attempt_id=q.attempt_id
 WHERE c.environment='SANDBOX' AND c.payment_status='CAPTURED' AND c.currency='VND'
 AND c.refund_amount>0 AND c.refund_amount<=c.payment_amount AND c.refund_amount<=9999999999
 AND LEN(c.tmn_code)=8 AND c.tmn_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Za-z0-9]%'
 AND LEN(c.txn_ref) BETWEEN 1 AND 100 AND c.txn_ref COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Za-z0-9-]%'
 AND LEN(c.payment_reference) BETWEEN 1 AND 15 AND c.payment_reference COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9]%'
 AND LEN(c.transaction_date)=14 AND c.transaction_date COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9]%'
 AND TRY_CONVERT(datetime2(0),CONCAT(SUBSTRING(c.transaction_date,1,4),'-',SUBSTRING(c.transaction_date,5,2),'-',
  SUBSTRING(c.transaction_date,7,2),'T',SUBSTRING(c.transaction_date,9,2),':',SUBSTRING(c.transaction_date,11,2),':',
  SUBSTRING(c.transaction_date,13,2)),126) IS NOT NULL;
GO
GRANT EXECUTE ON dbo.usp_RegisterVnpayPaymentProvenance TO tc_buyer;
GRANT SELECT ON dbo.vw_VnpayRefundContext TO tc_worker;
GRANT SELECT ON dbo.vw_VnpayBuyerPaymentProvenance TO tc_buyer;
GRANT SELECT ON dbo.vw_VnpaySandboxRefundCandidates TO tc_worker;
GO
