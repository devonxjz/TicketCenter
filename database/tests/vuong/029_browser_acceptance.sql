-- Synthetic browser acceptance ONLY. Invoked after the marked local fixture guard.
SET XACT_ABORT ON;
BEGIN TRAN;
IF EXISTS(SELECT 1 FROM dbo.tc_organizations WHERE contact_email='acceptance1011@test.invalid')
 THROW 52901,'Acceptance rows already exist; choose a NEW fixture.',1;
DECLARE @org uniqueidentifier=NEWID(),@category uniqueidentifier=NEWID(),@rule uniqueidentifier=NEWID();
DECLARE @admin uniqueidentifier=(SELECT id FROM dbo.tc_users WHERE email='vuong.acceptance@test.invalid');
DECLARE @buyer uniqueidentifier=(SELECT id FROM dbo.tc_users WHERE email='buyer@ticketscenter.vn');
IF @admin IS NULL OR @buyer IS NULL THROW 52902,'Private full WAR accounts required.',1;
INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,'Acceptance finance 1011','acceptance1011@test.invalid');
INSERT dbo.tc_event_categories(id,name,slug) VALUES(@category,'Acceptance synthetic',CONVERT(varchar(36),@category));
INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to)
 VALUES(@rule,@org,5,2,'2020-01-01','2099-01-01');
INSERT dbo.tc_organization_memberships(user_id,organization_id,role)
 SELECT id,@org,CASE WHEN email='abcc@manager.com' THEN 'MANAGER' ELSE 'CHECK_IN_STAFF' END
 FROM dbo.tc_users WHERE email IN('abcc@manager.com','abcc@staff.com');
DECLARE @n int=1;
WHILE @n<=25 BEGIN
 DECLARE @event uniqueidentifier=NEWID(),@zone uniqueidentifier=NEWID(),@hold uniqueidentifier=NEWID(),@order uniqueidentifier=NEWID(),@payment uniqueidentifier=NEWID();
 DECLARE @amount decimal(19,0)=CASE WHEN @n=1 THEN 9007199254740993 ELSE 100 END;
 INSERT dbo.tc_events(id,organization_id,category_id,commission_rule_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status)
 VALUES(@event,@org,@category,@rule,CASE WHEN @n=1 THEN '=SUM(1,1)' ELSE CONCAT('Acceptance event ',FORMAT(@n,'00')) END,'Synthetic browser fixture','Test venue','Test address','2026-01-01','2026-01-08','2026-01-09','2026-01-10','DRAFT');
 INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES(@zone,@event,'Synthetic zone','STANDING',0,100);
 UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=@event;
 INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,created_at,expires_at) VALUES(@hold,@buyer,@event,'CONSUMED','2026-01-01','2026-01-02');
 INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,status,subtotal_amount,discount_amount,total_amount,paid_at)
 VALUES(@order,@buyer,@event,@hold,CONVERT(varchar(36),@order),'PAID',@amount,0,@amount,'2026-01-02');
 INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,captured_at) VALUES(@payment,@order,@amount,'CAPTURED',CONVERT(varchar(36),@payment),'2026-01-02');
 UPDATE dbo.tc_orders SET accepted_payment_id=@payment WHERE id=@order;
 INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail,created_at)
 VALUES(@admin,'ACCEPTANCE_FINANCE','Event',@event,CONCAT('{"eventId":"',CONVERT(varchar(36),@event),'","passwordHash":"SHOULD_REDACT"}'),DATEADD(second,@n,CONVERT(datetime2,'2026-01-02')));
 SET @n+=1;
END;
COMMIT;
