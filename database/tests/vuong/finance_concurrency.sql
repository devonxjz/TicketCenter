-- Invoke through run-finance-concurrency.ps1. RunId is a generated UUID, Mode is an allowlisted literal.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
IF DB_NAME() NOT LIKE N'TC[_]Vuong[_]Test[_]%' OR OBJECT_ID('dbo.tc_vuong_test_fixture') IS NULL THROW 52790,'Isolated fixture required',1;
IF OBJECT_ID('dbo.tc_vuong_concurrency_runs') IS NULL CREATE TABLE dbo.tc_vuong_concurrency_runs(
 run_id uniqueidentifier NOT NULL,case_name varchar(30) NOT NULL,actor_id uniqueidentifier NOT NULL,event_id uniqueidentifier NOT NULL,rule_id uniqueidentifier NOT NULL,
 order_id uniqueidentifier NULL,settlement_id uniqueidentifier NULL,payout_id uniqueidentifier NOT NULL,PRIMARY KEY(run_id,case_name));
DECLARE @run uniqueidentifier='$(RunId)',@mode varchar(30)='$(Mode)',@actor uniqueidentifier,@org uniqueidentifier,@event uniqueidentifier,@rule uniqueidentifier,@order uniqueidentifier,@settlement uniqueidentifier,@payout uniqueidentifier,@fp binary(32)=HASHBYTES('SHA2_256','concurrency');
IF @mode='SETUP' BEGIN
 SET @actor=NEWID(); SET @org=NEWID();
 INSERT dbo.tc_users(id,email,user_name,full_name,platform_role) VALUES(@actor,CONCAT(@actor,'@test.invalid'),CONVERT(varchar(36),@actor),'Race Admin','ADMIN');
 INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,'Concurrency','c@test.invalid');
 DECLARE @case varchar(30);
 DECLARE cases CURSOR LOCAL FAST_FORWARD FOR SELECT c FROM(VALUES('CAPACITY'),('REPLAY'),('REFUND_CONFIRM'),('PUBLISH_EDIT'),('EVIDENCE_VERIFY'),('EVIDENCE_FREEZE')) v(c);
 OPEN cases; FETCH NEXT FROM cases INTO @case;
 WHILE @@FETCH_STATUS=0 BEGIN
 SET @rule=NEWID(); SET @event=NEWID(); SET @order=NEWID(); SET @payout=NEWID();
 INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES(@rule,@org,0,0,'2020-01-01','2099-01-01');
 INSERT dbo.tc_events(id,organization_id,commission_rule_id,title,end_time) VALUES(@event,@org,CASE WHEN @case='PUBLISH_EDIT' THEN NULL ELSE @rule END,@case,'2020-01-01');
 IF @case<>'PUBLISH_EDIT' BEGIN
 INSERT dbo.tc_orders(id,buyer_id,event_id,order_code,status,subtotal_amount,total_amount,paid_at) VALUES(@order,@actor,@event,CONVERT(varchar(36),@order),'PAID',100,100,'2026-01-01');
 DECLARE @payment uniqueidentifier=NEWID();
 INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,paid_at) VALUES(@payment,@order,100,'CAPTURED',CONVERT(varchar(36),@payment),'2026-01-01');
 UPDATE dbo.tc_orders SET accepted_payment_id=@payment WHERE id=@order;
 IF @case IN('EVIDENCE_VERIFY','EVIDENCE_FREEZE') BEGIN
 DECLARE @evidence_refund uniqueidentifier=NEWID(),@evidence_attempt uniqueidentifier=NEWID();
 INSERT dbo.tc_refunds(id,order_id,payment_id,amount,purpose,status,current_attempt_id,processed_at) VALUES(@evidence_refund,@order,@payment,10,'CUSTOMER_REFUND','COMPLETED',@evidence_attempt,'2026-01-02');
 INSERT dbo.tc_refund_transfer_logs(id,refund_id,attempt_no,result,processed_at) VALUES(@evidence_attempt,@evidence_refund,1,'SUCCEEDED','2026-01-02');
 END;
 EXEC dbo.usp_RecalculateSettlement @event,@actor,0;
 SELECT @settlement=id FROM dbo.tc_settlements WHERE event_id=@event;
 IF @case NOT IN('REFUND_CONFIRM','EVIDENCE_VERIFY','EVIDENCE_FREEZE') EXEC dbo.usp_ConfirmSettlement @settlement,@actor,0;
 END ELSE BEGIN SET @order=NULL; SET @settlement=NULL; END;
 INSERT dbo.tc_vuong_concurrency_runs VALUES(@run,@case,@actor,@event,@rule,@order,@settlement,@payout);
 FETCH NEXT FROM cases INTO @case;
 END;
 CLOSE cases; DEALLOCATE cases;
 PRINT 'READY: concurrency fixtures';
END;
IF @mode IN('CAPACITY_A','CAPACITY_B','REPLAY_A','REPLAY_B','REFUND_A','CONFIRM_B','PUBLISH_A','EDIT_B','EVIDENCE_A','EVIDENCE_CONFIRM_B','EVIDENCE_CONFIRM_A','EVIDENCE_WRITE_B') BEGIN
 SELECT @actor=actor_id,@event=event_id,@rule=rule_id,@order=order_id,@settlement=settlement_id,@payout=payout_id FROM dbo.tc_vuong_concurrency_runs WHERE run_id=@run AND case_name=CASE WHEN @mode LIKE 'CAPACITY%' THEN 'CAPACITY' WHEN @mode LIKE 'REPLAY%' THEN 'REPLAY' WHEN @mode IN('REFUND_A','CONFIRM_B') THEN 'REFUND_CONFIRM' WHEN @mode IN('EVIDENCE_A','EVIDENCE_CONFIRM_B') THEN 'EVIDENCE_VERIFY' WHEN @mode IN('EVIDENCE_CONFIRM_A','EVIDENCE_WRITE_B') THEN 'EVIDENCE_FREEZE' ELSE 'PUBLISH_EDIT' END;
 IF @actor IS NULL THROW 52793,'Run fixture missing',1;
 IF @mode IN('CAPACITY_A','REPLAY_A','REFUND_A','PUBLISH_A','EVIDENCE_A','EVIDENCE_CONFIRM_A') BEGIN
 BEGIN TRANSACTION;
 IF @mode='CAPACITY_A' EXEC dbo.usp_RecordPayout @settlement,@payout,@actor,80,'RACE',NULL,@fp,0;
 IF @mode='REPLAY_A' EXEC dbo.usp_RecordPayout @settlement,@payout,@actor,40,'RACE',NULL,@fp,0;
 IF @mode='REFUND_A' BEGIN
 SELECT id FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event;
 INSERT dbo.tc_refunds(id,order_id,amount,purpose,status) VALUES(NEWID(),@order,10,'CUSTOMER_REFUND','REQUESTED');
 END;
 IF @mode='PUBLISH_A' UPDATE dbo.tc_events SET commission_rule_id=@rule WHERE id=@event;
 IF @mode='EVIDENCE_A' UPDATE l SET result='UNKNOWN',processed_at=NULL FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refunds r ON r.id=l.refund_id WHERE r.order_id=@order;
 IF @mode='EVIDENCE_CONFIRM_A' EXEC dbo.usp_ConfirmSettlement @settlement,@actor,0;
 RAISERROR('LOCKED: contender A holds transaction',0,1) WITH NOWAIT;
 WAITFOR DELAY '00:00:03';
 COMMIT;
 PRINT 'PASS: contender A committed';
 END ELSE BEGIN
 DECLARE @blocked bit=0;
 BEGIN TRY
 IF @mode='CAPACITY_B' BEGIN DECLARE @other uniqueidentifier=NEWID(); EXEC dbo.usp_RecordPayout @settlement,@other,@actor,80,'RACE',NULL,@fp,0; END;
 IF @mode='REPLAY_B' EXEC dbo.usp_RecordPayout @settlement,@payout,@actor,40,'RACE',NULL,@fp,0;
 IF @mode='CONFIRM_B' EXEC dbo.usp_ConfirmSettlement @settlement,@actor,0;
 IF @mode='EVIDENCE_CONFIRM_B' EXEC dbo.usp_ConfirmSettlement @settlement,@actor,0;
 IF @mode='EVIDENCE_WRITE_B' UPDATE l SET result='UNKNOWN',processed_at=NULL FROM dbo.tc_refund_transfer_logs l JOIN dbo.tc_refunds r ON r.id=l.refund_id WHERE r.order_id=@order;
 IF @mode='EDIT_B' BEGIN DECLARE @editkey uniqueidentifier=NEWID(); SELECT @org=organization_id FROM dbo.tc_events WHERE id=@event; EXEC dbo.usp_UpdateCommissionRule @rule,@org,@actor,@editkey,5,0,'2020-01-01','2099-01-01',0; END;
 END TRY BEGIN CATCH IF ERROR_NUMBER()=52709 SET @blocked=1; ELSE THROW; END CATCH;
 IF @mode='REPLAY_B' AND @blocked=1 THROW 52793,'Same-ID contender should replay',1;
 IF @mode<>'REPLAY_B' AND @blocked=0 THROW 52793,'Conflicting contender was not blocked',1;
 PRINT 'PASS: contender B matched expected outcome';
 END;
END;
IF @mode='ASSERT' BEGIN
 IF EXISTS(SELECT 1 FROM dbo.tc_vuong_concurrency_runs c JOIN dbo.tc_settlements s ON s.id=c.settlement_id WHERE c.run_id=@run AND ((c.case_name='CAPACITY' AND (s.pending_amount<>80 OR (SELECT COUNT(*) FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=s.id)<>1)) OR (c.case_name='REPLAY' AND (s.pending_amount<>40 OR (SELECT COUNT(*) FROM dbo.tc_settlement_transfer_logs WHERE settlement_id=s.id)<>1)) OR (c.case_name='REFUND_CONFIRM' AND s.status<>'DRAFT'))) THROW 52793,'Concurrent financial invariants failed',1;
 IF EXISTS(SELECT 1 FROM dbo.tc_vuong_concurrency_runs c JOIN dbo.tc_commission_rules r ON r.id=c.rule_id JOIN dbo.tc_events e ON e.id=c.event_id WHERE c.run_id=@run AND c.case_name='PUBLISH_EDIT' AND (r.rate_percent<>0 OR e.commission_rule_id<>r.id)) THROW 52793,'Concurrent publish/edit invariant failed',1;
 IF EXISTS(SELECT 1 FROM dbo.tc_vuong_concurrency_runs c JOIN dbo.tc_settlements s ON s.id=c.settlement_id JOIN dbo.tc_refunds r ON r.order_id=c.order_id JOIN dbo.tc_refund_transfer_logs l ON l.id=r.current_attempt_id WHERE c.run_id=@run AND
 ((c.case_name='EVIDENCE_VERIFY' AND (s.status<>'DRAFT' OR l.result<>'UNKNOWN')) OR (c.case_name='EVIDENCE_FREEZE' AND (s.status<>'CONFIRMED' OR l.result<>'SUCCEEDED' OR l.processed_at IS NULL OR s.total_refund<>10)))) THROW 52793,'Concurrent refund evidence/confirmation invariant failed',1;
 PRINT 'PASS: all six two-session races retained invariants';
END;
GO
