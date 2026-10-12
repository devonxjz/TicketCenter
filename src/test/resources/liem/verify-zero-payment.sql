SET NOCOUNT ON;
SET XACT_ABORT OFF;
IF DB_NAME() <> 'TicketCenter_LIEM02_Bootstrap_Test'
    THROW 51000, 'Isolated LIEM02 bootstrap database required', 1;
BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @buyer uniqueidentifier=NEWID(), @org uniqueidentifier=NEWID(), @category uniqueidentifier=NEWID();
    DECLARE @event uniqueidentifier=NEWID(), @zone uniqueidentifier=NEWID(), @hold uniqueidentifier=NEWID();
    DECLARE @order uniqueidentifier=NEWID(), @item uniqueidentifier=NEWID();
    DECLARE @paid_zone uniqueidentifier=NEWID(), @paid_hold uniqueidentifier=NEWID();
    DECLARE @paid_order uniqueidentifier=NEWID(), @paid_item uniqueidentifier=NEWID(), @payment uniqueidentifier=NEWID();
    DECLARE @code varchar(64)=CONCAT('FREE-',REPLACE(CONVERT(varchar(36),@order),'-',''));
    DECLARE @ticket_code varchar(100)=CONCAT('TC-',REPLACE(CONVERT(varchar(36),NEWID()),'-',''));
    DECLARE @tickets nvarchar(max)=CONCAT('[{"ticketCode":"',@ticket_code,
        '","qrSecretHashHex":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}]');
    DECLARE @paid_tickets nvarchar(max)=CONCAT('[{"ticketCode":"TC-',REPLACE(CONVERT(varchar(36),NEWID()),'-',''),
        '","qrSecretHashHex":"abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"}]');

    INSERT dbo.tc_users(id,email,password_hash,full_name,user_name,email_verified_at)
    VALUES(@buyer,CONCAT('free-',@buyer,'@example.test'),'test','Free order buyer',CONCAT('free_',@buyer),SYSUTCDATETIME());
    INSERT dbo.tc_organizations(id,name,contact_email,status)
    VALUES(@org,'LIEM free order',CONCAT('free-',@org,'@example.test'),'APPROVED');
    INSERT dbo.tc_event_categories(id,name,slug)
    VALUES(@category,'LIEM free order',CONCAT('free-',@category));
    INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,
                          sale_start,sale_end,start_time,end_time,status)
    VALUES(@event,@org,@category,'LIEM free order','Transactional fixture','Test venue','Test address',
           DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()),
           DATEADD(day,2,SYSUTCDATETIME()),DATEADD(day,3,SYSUTCDATETIME()),'DRAFT');
    INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,held_quantity)
    VALUES(@zone,@event,'Free standing','STANDING',0,1,1);
    INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,held_quantity)
    VALUES(@paid_zone,@event,'Paid standing','STANDING',100,1,1);
    UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=@event;
    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at)
    VALUES(@hold,@buyer,@event,'ACTIVE',DATEADD(minute,10,SYSUTCDATETIME()));
    INSERT dbo.tc_ticket_hold_items(hold_id,zone_id,quantity,unit_price)
    VALUES(@hold,@zone,1,0);
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount)
    VALUES(@order,@buyer,@event,@hold,@code,0,0,0);
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot)
    VALUES(@item,@order,@zone,1,0,'Free standing');

    CREATE USER [liem_zero_buyer] WITHOUT LOGIN;
    ALTER ROLE tc_buyer ADD MEMBER [liem_zero_buyer];
    EXECUTE AS USER='liem_zero_buyer';
    EXEC dbo.usp_ApplyPaymentResult @order,@buyer,NULL,
        N'{"status":"CAPTURED","providerReference":"ZERO_PAYMENT"}',@tickets;
    REVERT;

    IF (SELECT status FROM dbo.tc_orders WHERE id=@order)<>'PAID'
        THROW 51000,'Free order was not paid',1;
    IF (SELECT status FROM dbo.tc_ticket_holds WHERE id=@hold)<>'CONSUMED'
        THROW 51000,'Free hold was not consumed',1;
    IF (SELECT sold_quantity FROM dbo.tc_zones WHERE id=@zone)<>1
        THROW 51000,'Free inventory was not sold',1;
    IF (SELECT COUNT(*) FROM dbo.tc_tickets WHERE order_item_id=@item AND paid_amount=0 AND ticket_code=@ticket_code)<>1
        THROW 51000,'Free ticket was not issued',1;
    IF EXISTS(SELECT 1 FROM dbo.tc_payments WHERE order_id=@order)
        THROW 51000,'Free order created a payment attempt',1;

    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at)
    VALUES(@paid_hold,@buyer,@event,'ACTIVE',DATEADD(minute,10,SYSUTCDATETIME()));
    INSERT dbo.tc_ticket_hold_items(hold_id,zone_id,quantity,unit_price)
    VALUES(@paid_hold,@paid_zone,1,100);
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount)
    VALUES(@paid_order,@buyer,@event,@paid_hold,
           CONCAT('PAID-',REPLACE(CONVERT(varchar(36),@paid_order),'-','')),100,0,100);
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot)
    VALUES(@paid_item,@paid_order,@paid_zone,1,100,'Paid standing');
    INSERT dbo.tc_payments(id,order_id,txn_ref,amount)
    VALUES(@payment,@paid_order,CONCAT('PAY-',REPLACE(CONVERT(varchar(36),@payment),'-','')),100);
    CREATE USER [liem_paid_worker] WITHOUT LOGIN;
    ALTER ROLE tc_worker ADD MEMBER [liem_paid_worker];
    EXECUTE AS USER='liem_paid_worker';
    EXEC dbo.usp_ApplyPaymentResult @paid_order,NULL,@payment,
        N'{"status":"CAPTURED","providerReference":"TEST_PROVIDER"}',@paid_tickets;
    REVERT;
    IF (SELECT status FROM dbo.tc_orders WHERE id=@paid_order)<>'PAID'
        THROW 51000,'Paid order was not captured',1;
    IF (SELECT status FROM dbo.tc_payments WHERE id=@payment)<>'CAPTURED'
        THROW 51000,'Paid provider result was not stored',1;
    IF (SELECT COUNT(*) FROM dbo.tc_tickets WHERE order_item_id=@paid_item AND paid_amount=100)<>1
        THROW 51000,'Paid ticket was not issued',1;
    ROLLBACK TRANSACTION;
    PRINT 'PASS free and worker-paid orders issue tickets with correct payment records';
END TRY BEGIN CATCH
    IF ORIGINAL_LOGIN()<>SUSER_SNAME() REVERT;
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
