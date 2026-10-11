SET NOCOUNT ON;
SET XACT_ABORT OFF;
IF DB_NAME() <> 'TicketCenter_LIEM_SQL_Runtime_Test'
    THROW 51000,'Isolated LIEM SQL test database required',1;
BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @buyer uniqueidentifier='68c35ad8-5657-4234-9638-f3b0ad353391';
    DECLARE @manager uniqueidentifier='5491b47e-c54e-4980-a410-a3036b7fa0b2';
    DECLARE @admin uniqueidentifier='42b0bd08-881c-4638-9f87-c3741e37c0c3';
    DECLARE @org uniqueidentifier='a57f3a25-4a35-4a82-96bb-9b94ec518c49';
    INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,user_name,email_verified_at)
    VALUES(@buyer,'liem-sql-buyer@example.test','liem-sql-buyer@example.test','test','Buyer','liem_sql_buyer',SYSUTCDATETIME()),
          (@manager,'liem-sql-manager@example.test','liem-sql-manager@example.test','test','Manager','liem_sql_manager',SYSUTCDATETIME()),
          (@admin,'liem-sql-admin@example.test','liem-sql-admin@example.test','test','Admin','liem_sql_admin',SYSUTCDATETIME());
    INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,'LIEM SQL Test','liem-sql-org@example.test');
    INSERT dbo.tc_organization_memberships(user_id,organization_id,role)
    VALUES(@manager,@org,'MANAGER');
    INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(@admin,'ADMIN');
    CREATE USER [liem_sql_buyer] WITHOUT LOGIN;
    CREATE USER [liem_sql_manager] WITHOUT LOGIN;
    CREATE USER [liem_sql_admin] WITHOUT LOGIN;
    CREATE USER [liem_sql_worker] WITHOUT LOGIN;
    ALTER ROLE tc_buyer ADD MEMBER [liem_sql_buyer];
    ALTER ROLE tc_manager ADD MEMBER [liem_sql_manager];
    ALTER ROLE tc_platform_admin ADD MEMBER [liem_sql_admin];
    ALTER ROLE tc_worker ADD MEMBER [liem_sql_worker];

    EXECUTE AS USER='liem_sql_buyer';
    IF HAS_PERMS_BY_NAME('dbo.tc_orders','OBJECT','SELECT')<>0 THROW 51000,'Buyer received raw order SELECT',1;
    IF HAS_PERMS_BY_NAME('dbo.usp_ReadPaymentOrder','OBJECT','EXECUTE')<>1 THROW 51000,'Buyer payment facade unavailable',1;
    EXEC dbo.usp_ReadPaymentOrder '11111111-1111-1111-1111-111111111111',@buyer,0;
    EXEC dbo.usp_ReadPaymentStatus '11111111-1111-1111-1111-111111111111',@buyer,0;
    EXEC dbo.usp_ListBuyerOrders @buyer,0,1,20,NULL;
    EXEC dbo.usp_ListBuyerTickets @buyer,0;
    BEGIN TRY
        EXEC dbo.usp_ReadOrderTicketQuantity '11111111-1111-1111-1111-111111111111',@buyer,0;
        THROW 51000,'Buyer read missing order ticket quantity',1;
    END TRY BEGIN CATCH
        IF ERROR_NUMBER()<>51502 THROW;
    END CATCH;
    REVERT;

    EXECUTE AS USER='liem_sql_manager';
    DECLARE @created TABLE(id uniqueidentifier);
    INSERT @created EXEC dbo.usp_CreateManagedCoupon @org,@manager,0,'LIEMSQLTEST','PERCENTAGE',10.00,NULL,NULL,5,
        '2026-01-01T00:00:00','2027-01-01T00:00:00';
    IF (SELECT COUNT(*) FROM @created)<>1 THROW 51000,'Manager create failed',1;
    DECLARE @coupon uniqueidentifier=(SELECT id FROM @created);
    DECLARE @can TABLE(allowed bit);
    INSERT @can EXEC dbo.usp_CanManageCouponOrganization @manager,0,@org;
    IF (SELECT allowed FROM @can)<>1 THROW 51000,'Manager permission lookup failed',1;
    EXEC dbo.usp_ReadManagedCoupon @coupon,@manager,0;
    EXEC dbo.usp_UpdateManagedCoupon @coupon,@manager,0,6,NULL,NULL;
    EXEC dbo.usp_SetManagedCouponActive @coupon,@manager,0,0;
    BEGIN TRY
        EXEC dbo.usp_ListManagedCoupons @manager,0,NULL,1,20;
        THROW 51000,'Manager accessed global coupon list',1;
    END TRY BEGIN CATCH
        IF ERROR_NUMBER()<>51521 THROW;
    END CATCH;
    EXEC dbo.usp_DeleteManagedCoupon @coupon,@manager,0;
    REVERT;

    EXECUTE AS USER='liem_sql_admin';
    EXEC dbo.usp_ListManagedCoupons @admin,0,NULL,1,20;
    REVERT;

    EXECUTE AS USER='liem_sql_worker';
    EXEC dbo.usp_ReadOrderTicketQuantity '11111111-1111-1111-1111-111111111111',NULL,NULL;
    EXEC dbo.usp_ReadProviderPayment 'no-such-payment';
    REVERT;

    ROLLBACK TRANSACTION;
    PRINT 'PASS LIEM SQL principal and coupon facade checks';
END TRY BEGIN CATCH
    IF ORIGINAL_LOGIN()<>SUSER_SNAME() REVERT;
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
