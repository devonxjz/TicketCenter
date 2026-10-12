SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

CREATE OR ALTER PROCEDURE dbo.usp_CouponAdminAuthorize
    @actor_id uniqueidentifier, @auth_version int, @organization_id uniqueidentifier=NULL
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS(SELECT 1 FROM dbo.tc_users
                  WHERE id=@actor_id AND auth_version=@auth_version AND status='ACTIVE')
        THROW 51520,'Current session required',1;
    IF ISNULL(IS_ROLEMEMBER('tc_platform_admin'),0)=1
       AND EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles
                  WHERE user_id=@actor_id AND role='ADMIN') RETURN;
    IF @organization_id IS NOT NULL AND ISNULL(IS_ROLEMEMBER('tc_manager'),0)=1
       AND EXISTS(SELECT 1 FROM dbo.tc_organization_memberships WITH (HOLDLOCK)
                  WHERE user_id=@actor_id AND organization_id=@organization_id
                    AND role='MANAGER' AND active=1) RETURN;
    THROW 51521,'Organization manager or platform admin required',1;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_CanManageCouponOrganization
    @actor_id uniqueidentifier, @auth_version int, @organization_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;
    SELECT CAST(CASE WHEN EXISTS(SELECT 1 FROM dbo.tc_users
                                 WHERE id=@actor_id AND auth_version=@auth_version AND status='ACTIVE')
        AND (ISNULL(IS_ROLEMEMBER('tc_platform_admin'),0)=1
             AND EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles
                        WHERE user_id=@actor_id AND role='ADMIN')
             OR @organization_id IS NOT NULL AND ISNULL(IS_ROLEMEMBER('tc_manager'),0)=1
             AND EXISTS(SELECT 1 FROM dbo.tc_organization_memberships
                        WHERE user_id=@actor_id AND organization_id=@organization_id
                          AND role='MANAGER' AND active=1))
        THEN 1 ELSE 0 END AS bit) AS allowed;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ListManagedCoupons
    @actor_id uniqueidentifier, @auth_version int, @organization_id uniqueidentifier=NULL,
    @page int=1, @size int=20
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    IF @page IS NULL OR @size IS NULL OR @page<1 OR @size<1 OR @size>100
       OR @page>1+2147483647/@size THROW 51522,'Invalid page',1;
    DECLARE @offset int=(@page-1)*@size;
    SELECT c.id,c.organization_id,o.name AS org_name,c.code,c.discount_type,
           c.percentage_value,c.fixed_amount,c.max_discount_amount,c.max_uses,
           u.reserved_uses,u.consumed_uses,u.available_uses,
           c.valid_from,c.valid_to,c.active,c.version
    FROM dbo.tc_coupons c JOIN dbo.vw_CouponUsage u ON u.coupon_id=c.id
    JOIN dbo.tc_organizations o ON o.id=c.organization_id
    WHERE @organization_id IS NULL OR c.organization_id=@organization_id
    ORDER BY c.valid_from DESC,c.id DESC OFFSET @offset ROWS FETCH NEXT @size ROWS ONLY;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_ReadManagedCoupon
    @coupon_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @organization_id uniqueidentifier;
    SELECT @organization_id=organization_id FROM dbo.tc_coupons WHERE id=@coupon_id;
    IF @organization_id IS NULL THROW 51523,'Coupon not found',1;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    SELECT c.id,c.organization_id,o.name AS org_name,c.code,c.discount_type,
           c.percentage_value,c.fixed_amount,c.max_discount_amount,c.max_uses,
           u.reserved_uses,u.consumed_uses,u.available_uses,
           c.valid_from,c.valid_to,c.active,c.version
    FROM dbo.tc_coupons c JOIN dbo.vw_CouponUsage u ON u.coupon_id=c.id
    JOIN dbo.tc_organizations o ON o.id=c.organization_id WHERE c.id=@coupon_id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_CreateManagedCoupon
    @organization_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int,
    @code varchar(80), @discount_type varchar(30), @percentage_value decimal(5,2)=NULL,
    @fixed_amount decimal(19,0)=NULL, @max_discount_amount decimal(19,0)=NULL,
    @max_uses int, @valid_from datetime2(3), @valid_to datetime2(3)
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    DECLARE @id uniqueidentifier=NEWID();
    INSERT dbo.tc_coupons(id,organization_id,code,discount_type,percentage_value,fixed_amount,
                          max_discount_amount,max_uses,valid_from,valid_to,active,version)
    VALUES(@id,@organization_id,@code,@discount_type,@percentage_value,@fixed_amount,
           @max_discount_amount,@max_uses,@valid_from,@valid_to,1,0);
    SELECT @id AS id;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_UpdateManagedCoupon
    @coupon_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int,
    @max_uses int=NULL, @valid_to datetime2(3)=NULL, @max_discount_amount decimal(19,0)=NULL
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @organization_id uniqueidentifier;
    SELECT @organization_id=organization_id FROM dbo.tc_coupons WHERE id=@coupon_id;
    IF @organization_id IS NULL THROW 51523,'Coupon not found',1;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    UPDATE dbo.tc_coupons SET max_uses=COALESCE(@max_uses,max_uses),
           valid_to=COALESCE(@valid_to,valid_to),
           max_discount_amount=COALESCE(@max_discount_amount,max_discount_amount),version=version+1
    WHERE id=@coupon_id AND organization_id=@organization_id;
    IF @@ROWCOUNT<>1 THROW 51523,'Coupon not found',1;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_SetManagedCouponActive
    @coupon_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int, @active bit
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @organization_id uniqueidentifier;
    SELECT @organization_id=organization_id FROM dbo.tc_coupons WHERE id=@coupon_id;
    IF @organization_id IS NULL THROW 51523,'Coupon not found',1;
    EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
    UPDATE dbo.tc_coupons SET active=@active,version=version+1
    WHERE id=@coupon_id AND organization_id=@organization_id;
    IF @@ROWCOUNT<>1 THROW 51523,'Coupon not found',1;
END;
GO

CREATE OR ALTER PROCEDURE dbo.usp_DeleteManagedCoupon
    @coupon_id uniqueidentifier, @actor_id uniqueidentifier, @auth_version int
AS
BEGIN
    SET NOCOUNT ON;
    BEGIN TRANSACTION;
    BEGIN TRY
        DECLARE @organization_id uniqueidentifier;
        SELECT @organization_id=organization_id FROM dbo.tc_coupons WITH (UPDLOCK,HOLDLOCK) WHERE id=@coupon_id;
        IF @organization_id IS NULL THROW 51523,'Coupon not found',1;
        EXEC dbo.usp_CouponAdminAuthorize @actor_id,@auth_version,@organization_id;
        IF EXISTS(SELECT 1 FROM dbo.tc_coupon_redemptions WITH (UPDLOCK,HOLDLOCK) WHERE coupon_id=@coupon_id)
           OR EXISTS(SELECT 1 FROM dbo.tc_orders WITH (UPDLOCK,HOLDLOCK) WHERE coupon_id=@coupon_id)
            THROW 51524,'Coupon is in use',1;
        DELETE dbo.tc_coupons WHERE id=@coupon_id AND organization_id=@organization_id;
        COMMIT TRANSACTION;
    END TRY BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

GRANT EXECUTE ON dbo.usp_ListManagedCoupons TO tc_manager,tc_platform_admin;
GRANT EXECUTE ON dbo.usp_CanManageCouponOrganization TO tc_manager,tc_platform_admin;
GRANT EXECUTE ON dbo.usp_ReadManagedCoupon TO tc_manager,tc_platform_admin;
GRANT EXECUTE ON dbo.usp_CreateManagedCoupon TO tc_manager,tc_platform_admin;
GRANT EXECUTE ON dbo.usp_UpdateManagedCoupon TO tc_manager,tc_platform_admin;
GRANT EXECUTE ON dbo.usp_SetManagedCouponActive TO tc_manager,tc_platform_admin;
GRANT EXECUTE ON dbo.usp_DeleteManagedCoupon TO tc_manager,tc_platform_admin;
GO
