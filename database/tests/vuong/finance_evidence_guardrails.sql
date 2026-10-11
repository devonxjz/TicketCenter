-- Run with sqlcmd -v Case=ALL (or one allowlisted regression case for RED evidence).
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
IF DB_NAME() NOT LIKE N'TC[_]Vuong[_]Test[_]%' OR OBJECT_ID('dbo.tc_vuong_test_fixture') IS NULL
 THROW 52790,'Dedicated isolated fixture required',1;
IF NOT EXISTS(SELECT 1 FROM dbo.tc_vuong_test_fixture WHERE fixture_key='vuong-finance-v1')
 THROW 52790,'Dedicated isolated fixture marker required',1;
GO
CREATE PROCEDURE #ExpectEvidenceError @statement nvarchar(max),@test varchar(100) AS
BEGIN
 SET NOCOUNT ON;
 DECLARE @caught int=NULL;
 BEGIN TRY EXEC sys.sp_executesql @statement; END TRY
 BEGIN CATCH SET @caught=ERROR_NUMBER(); IF XACT_STATE()<>0 ROLLBACK; END CATCH;
 IF @caught IS NULL OR @caught<>52709 BEGIN
 DECLARE @message nvarchar(2048)=CONCAT('FAIL: ',@test,' expected 52709, got ',COALESCE(CONVERT(varchar(20),@caught),'success'));
 THROW 52793,@message,1;
 END;
 PRINT CONCAT('PASS: ',@test);
END;
GO
DECLARE @case varchar(30)='$(Case)';
IF @case NOT IN('ALL','PENDING','CURRENT_ATTEMPT','LOG_UPDATE','LOG_DELETE','LOG_INSERT')
 THROW 52793,'Allowlisted Case required',1;
DECLARE @actor uniqueidentifier=NEWID(),@org uniqueidentifier=NEWID(),@rule uniqueidentifier=NEWID(),@event uniqueidentifier=NEWID(),
 @order uniqueidentifier=NEWID(),@payment uniqueidentifier=NEWID(),@refund uniqueidentifier=NEWID(),@attempt uniqueidentifier=NEWID(),@old_attempt uniqueidentifier=NEWID();
INSERT dbo.tc_users(id,email,user_name,full_name,platform_role) VALUES(@actor,CONCAT(@actor,'@test.invalid'),CONVERT(varchar(36),@actor),'Evidence Admin','ADMIN');
INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,'Evidence','evidence@test.invalid');
INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES(@rule,@org,0,0,'2020-01-01','2099-01-01');
INSERT dbo.tc_events(id,organization_id,commission_rule_id,title,end_time) VALUES(@event,@org,@rule,'Evidence Event','2020-01-01');
INSERT dbo.tc_orders(id,buyer_id,event_id,order_code,status,subtotal_amount,total_amount,paid_at) VALUES(@order,@actor,@event,CONVERT(varchar(36),@order),'PAID',100,100,'2026-01-01');
INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,paid_at) VALUES(@payment,@order,100,'CAPTURED',CONVERT(varchar(36),@payment),'2026-01-01');
UPDATE dbo.tc_orders SET accepted_payment_id=@payment WHERE id=@order;
INSERT dbo.tc_refunds(id,order_id,payment_id,amount,purpose,status,current_attempt_id,processed_at) VALUES(@refund,@order,@payment,10,'CUSTOMER_REFUND','COMPLETED',@attempt,'2026-01-02');
INSERT dbo.tc_refund_transfer_logs(id,refund_id,attempt_no,result,provider_reference,processed_at)
 VALUES(@old_attempt,@refund,1,'FAILED','OLD','2026-01-01'),(@attempt,@refund,2,'SUCCEEDED','SUCCESS','2026-01-02');
EXEC dbo.usp_RecalculateSettlement @event,@actor,0;
DECLARE @settlement uniqueidentifier=(SELECT id FROM dbo.tc_settlements WHERE event_id=@event);
EXEC dbo.usp_ConfirmSettlement @settlement,@actor,0;
DECLARE @statement nvarchar(max);
IF @case IN('ALL','CURRENT_ATTEMPT') BEGIN
 SET @statement=CONCAT('UPDATE dbo.tc_refunds SET current_attempt_id=''',@old_attempt,''' WHERE id=''',@refund,''';');
 EXEC #ExpectEvidenceError @statement,'confirmed current refund attempt immutable';
END;
IF @case IN('ALL','LOG_UPDATE') BEGIN
 SET @statement=CONCAT('UPDATE dbo.tc_refund_transfer_logs SET result=''UNKNOWN'',processed_at=NULL WHERE id=''',@attempt,''';');
 EXEC #ExpectEvidenceError @statement,'confirmed successful refund evidence immutable';
END;
IF @case IN('ALL','LOG_DELETE') BEGIN
 SET @statement=CONCAT('DELETE dbo.tc_refund_transfer_logs WHERE id=''',@attempt,''';');
 EXEC #ExpectEvidenceError @statement,'confirmed refund evidence cannot be deleted';
END;
IF @case IN('ALL','LOG_INSERT') BEGIN
 SET @statement=CONCAT('INSERT dbo.tc_refund_transfer_logs(id,refund_id,attempt_no,result) VALUES(NEWID(),''',@refund,''',3,''PENDING'');');
 EXEC #ExpectEvidenceError @statement,'confirmed customer refund cannot acquire new attempt';
END;
IF @case IN('ALL','PENDING') BEGIN
 DECLARE @payout uniqueidentifier=NEWID(),@fp binary(32)=HASHBYTES('SHA2_256','evidence-reservation');
 SET @statement=CONCAT('EXEC dbo.usp_RecordPayout ''',@settlement,''',''',NEWID(),''',''',@actor,''',91,''TOO_LARGE'';');
 EXEC #ExpectEvidenceError @statement,'first reservation cannot exceed confirmed net';
 EXEC dbo.usp_RecordPayout @settlement,@payout,@actor,40,'REQUEST',NULL,@fp,0;
 SET @statement=CONCAT('EXEC dbo.usp_RecordPayout ''',@settlement,''',''',NEWID(),''',''',@actor,''',10,''NEW'';');
 EXEC #ExpectEvidenceError @statement,'reservation-only PENDING blocks a new payout ID after crash';
 EXEC dbo.usp_RecordPayout @settlement,@payout,@actor,40,'REQUEST',NULL,@fp,0;
 IF (SELECT pending_amount FROM dbo.tc_settlements WHERE id=@settlement)<>40 OR
  (SELECT COUNT_BIG(*) FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=@settlement)<>1
  THROW 52793,'PENDING same-ID replay changed reservation',1;
 EXEC dbo.usp_RecordPayout @settlement,@payout,@actor,40,'PROVIDER','UNKNOWN',@fp,0;
 EXEC #ExpectEvidenceError @statement,'UNKNOWN also blocks a new payout ID';
 EXEC dbo.usp_RecordPayout @settlement,@payout,@actor,40,'PROVIDER','FAILED',@fp,0;
 DECLARE @next uniqueidentifier=NEWID(),@last uniqueidentifier=NEWID();
 EXEC dbo.usp_RecordPayout @settlement,@next,@actor,40,'NEXT',NULL,@fp,0;
 EXEC dbo.usp_RecordPayout @settlement,@next,@actor,40,'NEXT','SUCCEEDED',@fp,0;
 EXEC dbo.usp_RecordPayout @settlement,@last,@actor,50,'LAST',NULL,@fp,0;
 EXEC dbo.usp_RecordPayout @settlement,@last,@actor,50,'LAST','SUCCEEDED',@fp,0;
 IF (SELECT status FROM dbo.tc_settlements WHERE id=@settlement)<>'PAID' OR
  (SELECT paid_amount FROM dbo.tc_settlements WHERE id=@settlement)<>90 OR
  (SELECT pending_amount FROM dbo.tc_settlements WHERE id=@settlement)<>0
  THROW 52793,'Reconciliation/verified failure release/full payment invariant failed',1;
 PRINT 'PASS: same-ID reconciliation and verified failure release allow subsequent payouts';
END;
IF @case='ALL' BEGIN
 IF EXISTS(SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event,SYSUTCDATETIME())) OR
  (SELECT total_refund FROM dbo.fn_GetOrganizationRevenue(@org,'2026-01-01','2026-01-03') WHERE event_id=@event)<>10
  THROW 52793,'Frozen refund verification/report history changed',1;
 -- Late nonaccepted captures and their compensation remain the other owner's valid write path.
 DECLARE @extra uniqueidentifier=NEWID(),@comp uniqueidentifier=NEWID(),@comp_attempt uniqueidentifier=NEWID();
 INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,paid_at) VALUES(@extra,@order,20,'CAPTURED',CONVERT(varchar(36),@extra),'2026-01-03');
 INSERT dbo.tc_refunds(id,order_id,payment_id,amount,purpose,status,current_attempt_id) VALUES(@comp,@order,@extra,20,'PAYMENT_COMPENSATION','PROCESSING',@comp_attempt);
 INSERT dbo.tc_refund_transfer_logs(id,refund_id,attempt_no,result) VALUES(@comp_attempt,@comp,1,'PENDING');
 UPDATE dbo.tc_refund_transfer_logs SET result='SUCCEEDED',provider_reference='COMP',processed_at='2026-01-04' WHERE id=@comp_attempt;
 UPDATE dbo.tc_refunds SET status='COMPLETED',processed_at='2026-01-04' WHERE id=@comp;
 IF (SELECT total_refund FROM dbo.tc_settlements WHERE id=@settlement)<>10 OR
  (SELECT compensation_refund FROM dbo.fn_GetOrganizationCashFlow(@org,'2026-01-03','2026-01-05') WHERE event_id=@event)<>20
  THROW 52793,'Late compensation changed frozen ticket source or failed to reconcile',1;
 PRINT 'PASS: later nonaccepted capture/compensation evidence remains writable';
END;
PRINT CONCAT('PASS: financial evidence regression case ',@case);
GO
