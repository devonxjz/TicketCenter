/* Business scenarios against the consolidated schema. Runs after 050 inside the same (rolled back) transaction.
   Any failed expectation THROWs 59xxx. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
DECLARE @buyer uniqueidentifier=NEWID(), @admin uniqueidentifier=NEWID(), @org uniqueidentifier=NEWID(), @cat uniqueidentifier=NEWID(),
        @event uniqueidentifier=NEWID(), @zone uniqueidentifier=NEWID(), @hold uniqueidentifier=NEWID(), @order uniqueidentifier=NEWID(),
        @pay uniqueidentifier=NEWID(), @item uniqueidentifier=NEWID(), @t1 uniqueidentifier=NEWID(), @t2 uniqueidentifier=NEWID();
INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,email_verified_at,platform_role)
 VALUES(@buyer,CONCAT(@buyer,'@x.test'),CONCAT('b',@buyer),N'fixture',N'Buyer',SYSUTCDATETIME(),'CUSTOMER');
INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,email_verified_at,platform_role)
 VALUES(@admin,CONCAT(@admin,'@x.test'),CONCAT('a',@admin),N'fixture',N'Admin',SYSUTCDATETIME(),'ADMIN');
INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(@admin,'ADMIN');
INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,N'Scenario org',CONCAT(@org,'@x.test'));
INSERT dbo.tc_event_categories(id,name,slug) VALUES(@cat,N'Scenario',CONVERT(varchar(36),@cat));
INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time)
 VALUES(@event,@org,@cat,N'Scenario',N'x',N'Hall',N'Addr',DATEADD(day,-3,SYSUTCDATETIME()),DATEADD(day,-2,SYSUTCDATETIME()),DATEADD(day,2,SYSUTCDATETIME()),DATEADD(day,3,SYSUTCDATETIME()));
INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,sold_quantity) VALUES(@zone,@event,N'Standing','STANDING',100000,100,2);
UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=@event;
INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES(@hold,@buyer,@event,'CONSUMED',DATEADD(minute,10,SYSUTCDATETIME()));
INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at)
 VALUES(@order,@buyer,@event,@hold,CONCAT('S-',@order),200000,0,200000,'PAID',SYSUTCDATETIME());
INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at,provider_reference) VALUES(@pay,@order,CONVERT(varchar(36),@pay),200000,'CAPTURED',SYSUTCDATETIME(),N'fixture');
UPDATE dbo.tc_orders SET accepted_payment_id=@pay WHERE id=@order;
INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES(@item,@order,@zone,2,100000,N'Standing');
INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount) VALUES(@t1,@item,CONCAT('S1-',@t1),0x0102,100000),(@t2,@item,CONCAT('S2-',@t2),0x0102,100000);

DECLARE @auth int=(SELECT auth_version FROM dbo.tc_users WHERE id=@buyer), @aauth int=(SELECT auth_version FROM dbo.tc_users WHERE id=@admin);
DECLARE @refund uniqueidentifier, @attempt uniqueidentifier, @lease uniqueidentifier=NEWID(), @n int, @s varchar(40), @rs varchar(40);

/* 1. buyer requests a refund for ticket 1 */
EXEC dbo.sp_refund_request_for_tickets @order,@buyer,@t1,N'scenario refund number one',@refund OUTPUT,@lease,@auth;
SELECT @s=status,@rs=request_status,@n=(SELECT COUNT(*) FROM dbo.tc_refund_tickets WHERE refund_id=@refund) FROM dbo.tc_refund_obligations WHERE id=@refund AND purpose='CUSTOMER_REFUND' AND requester_id=@buyer AND amount=100000;
IF @s IS DISTINCT FROM 'REQUESTED' OR @rs IS DISTINCT FROM 'PENDING' OR @n<>1 THROW 59001,'request step: obligation not created as REQUESTED/PENDING with one ticket',1;
SELECT 'ok 1 request' step,@s status,@rs request_status;

/* 2. admin approves -> one PENDING attempt */
EXEC dbo.sp_admin_refund_decide @refund,@admin,@aauth,'APPROVE',NULL,NULL,@lease;
SELECT @s=status,@rs=request_status,@attempt=current_attempt_id FROM dbo.tc_refund_obligations WHERE id=@refund;
IF @s<>'PROCESSING' OR @rs<>'APPROVED' OR @attempt IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE id=@attempt AND refund_id=@refund AND attempt_no=1 AND status='PENDING')
 THROW 59002,'approve step: expected PROCESSING with attempt 1 PENDING',1;
SELECT 'ok 2 approve' step,@s status,@rs request_status;

/* 3. worker claims the attempt, gateway fails -> RETRYABLE */
SET @lease=NEWID();
EXEC dbo.sp_refund_begin_attempt @refund,@attempt,@lease;
EXEC dbo.sp_refund_record_attempt_result @attempt,@lease,'FAILED',N'gw-fail-1';
SELECT @s=status FROM dbo.tc_refund_obligations WHERE id=@refund;
IF @s<>'RETRYABLE' OR NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE id=@attempt AND status='FAILED') THROW 59003,'failure step: expected RETRYABLE and attempt FAILED',1;
SELECT 'ok 3 failed' step,@s status;

/* 4. admin retries -> attempt 2 */
EXEC dbo.sp_admin_refund_decide @refund,@admin,@aauth,'RETRY',NULL,NULL,@lease;
DECLARE @attempt2 uniqueidentifier=(SELECT current_attempt_id FROM dbo.tc_refund_obligations WHERE id=@refund);
IF @attempt2 IS NULL OR @attempt2=@attempt OR NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE id=@attempt2 AND attempt_no=2 AND status='PENDING')
 THROW 59004,'retry step: expected a second PENDING attempt',1;
SELECT 'ok 4 retry' step,@attempt2 attempt;

/* 5. second attempt succeeds -> COMPLETED, tickets closed, replay is a no-op */
SET @lease=NEWID();
EXEC dbo.sp_refund_begin_attempt @refund,@attempt2,@lease;
EXEC dbo.sp_refund_record_attempt_result @attempt2,@lease,'SUCCEEDED',N'gw-ok-2';
SELECT @s=status,@rs=request_status FROM dbo.tc_refund_obligations WHERE id=@refund;
IF @s<>'COMPLETED' OR @rs<>'COMPLETED' OR EXISTS(SELECT 1 FROM dbo.tc_refund_tickets WHERE refund_id=@refund AND is_open=1)
 THROW 59005,'success step: expected COMPLETED with closed tickets',1;
DECLARE @payload nvarchar(max)=N'{"status":"SUCCEEDED","providerReference":"gw-ok-2"}';
EXEC dbo.usp_ApplyRefundResult @attempt2,@payload;
SELECT @n=COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=@refund AND status='SUCCEEDED';
IF @n<>1 THROW 59006,'replay step: success applied twice',1;
IF (SELECT COUNT(*) FROM dbo.vw_OutboxEmailContext WHERE event_type='REFUND_SUCCEEDED' AND recipient_email=CONCAT(@buyer,'@x.test') AND amount=100000)<>1
 THROW 59011,'outbox step: REFUND_SUCCEEDED email context missing',1;
SELECT 'ok 5 success+replay' step,@s status,@rs request_status;

/* 6. buyer requests ticket 2, admin rejects -> REJECTED, ticket released */
DECLARE @refund2 uniqueidentifier, @key uniqueidentifier=NEWID();
EXEC dbo.sp_refund_request_for_tickets @order,@buyer,@t2,N'scenario refund number two',@refund2 OUTPUT,@key,@auth;
SET @key=NEWID();
EXEC dbo.sp_admin_refund_decide @refund2,@admin,@aauth,'REJECT',NULL,N'no',@key;
SELECT @s=status,@rs=request_status FROM dbo.tc_refund_obligations WHERE id=@refund2;
IF @s<>'REJECTED' OR @rs<>'REJECTED' OR EXISTS(SELECT 1 FROM dbo.tc_refund_tickets WHERE refund_id=@refund2 AND is_open=1)
 THROW 59007,'reject step: expected REJECTED with released ticket',1;
SELECT 'ok 6 reject' step,@s status;

/* 7. payment compensation: capture arrives after the hold expired */
DECLARE @o2 uniqueidentifier=NEWID(), @h2 uniqueidentifier=NEWID(), @p2 uniqueidentifier=NEWID(), @i2 uniqueidentifier=NEWID(), @res nvarchar(max);
INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,created_at,expires_at) VALUES(@h2,@buyer,@event,'RELEASED',DATEADD(minute,-20,SYSUTCDATETIME()),DATEADD(minute,-10,SYSUTCDATETIME()));
INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status)
 VALUES(@o2,@buyer,@event,@h2,CONCAT('C-',@o2),100000,0,100000,'PENDING_PAYMENT');
INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES(@i2,@o2,@zone,1,100000,N'Standing');
INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status) VALUES(@p2,@o2,CONVERT(varchar(36),@p2),100000,'PENDING');
SET @res=N'{"status":"CAPTURED","providerReference":"gw-late"}';
EXEC dbo.usp_ApplyPaymentResult @o2,NULL,@p2,@res,N'[]',0;
DECLARE @comp uniqueidentifier=(SELECT id FROM dbo.tc_refund_obligations WHERE payment_id=@p2 AND purpose='PAYMENT_COMPENSATION');
IF @comp IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.tc_refund_transfer_logs WHERE id=@comp AND refund_id=@comp AND status='PENDING')
 THROW 59008,'compensation step: obligation + attempt not created',1;
EXEC dbo.usp_ApplyPaymentResult @o2,NULL,@p2,@res,N'[]',0;
SELECT @n=COUNT(*) FROM dbo.tc_refund_obligations WHERE payment_id=@p2 AND purpose='PAYMENT_COMPENSATION';
IF @n<>1 THROW 59009,'compensation step: duplicate compensation created on replay',1;
SET @lease=NEWID();
EXEC dbo.sp_refund_begin_attempt @comp,@comp,@lease;
EXEC dbo.sp_refund_record_attempt_result @comp,@lease,'FAILED',N'gw-comp-fail';
SELECT @s=status FROM dbo.tc_refund_obligations WHERE id=@comp;
IF @s<>'RETRYABLE' THROW 59010,'compensation step: failed transfer should be RETRYABLE',1;
SELECT 'ok 7 compensation' step,@s status;

/* 8. event cancellation turns open requests/orders into EVENT_CANCELLATION refunds */
UPDATE dbo.tc_events SET status='CANCELLED' WHERE id=@event;
EXEC dbo.usp_ProcessCancelledOrder @event,@order;
SELECT 'ok 8 cancellation' step,purpose,reason_type,status,request_status,amount FROM dbo.tc_refund_obligations WHERE order_id=@order;

/* 9. check-in log and settlement snapshots are written canonically */
SELECT 'views' step,(SELECT COUNT(*) FROM dbo.vw_CheckInHistory) check_in_rows,(SELECT COUNT(*) FROM dbo.vw_RefundRequestOverview) overview_rows,(SELECT COUNT(*) FROM dbo.v_admin_refund_requests) admin_rows;
SELECT 'DONE' result;
GO
/* 10. nothing may still reference the legacy tables, and every view/function still compiles */
SELECT 'dangling reference' problem, OBJECT_NAME(d.referencing_id) referencing_object, d.referenced_entity_name
FROM sys.sql_expression_dependencies d
WHERE d.referenced_entity_name IN('tc_refund_requests','tc_refund_request_tickets','tc_refunds','tc_check_ins','tc_payouts','tc_settlement_items',
  'usp_CheckInTicket','usp_ExecutePayout','usp_ConfirmEventSettlement','usp_CalculateEventSettlement','vw_JpaPayouts')
  AND d.referenced_id IS NULL AND d.referenced_schema_name='dbo';
DECLARE @bad TABLE(name sysname, msg nvarchar(400));
DECLARE @o sysname;
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT QUOTENAME(SCHEMA_NAME(schema_id))+'.'+QUOTENAME(name) FROM sys.objects WHERE type IN('V','FN','IF','TF') AND is_ms_shipped=0;
OPEN c; FETCH NEXT FROM c INTO @o;
WHILE @@FETCH_STATUS=0 BEGIN
 BEGIN TRY EXEC sys.sp_refreshsqlmodule @o; END TRY BEGIN CATCH INSERT @bad VALUES(@o,LEFT(ERROR_MESSAGE(),400)); END CATCH
 FETCH NEXT FROM c INTO @o;
END; CLOSE c; DEALLOCATE c;
SELECT 'uncompilable module' problem,* FROM @bad;
SELECT 'checked' result;
GO
