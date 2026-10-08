-- SQL Server 2017+. Install after schema.sql, functions.sql and views.sql.
-- Public procedures are transactional and support nested calls. An error rolls
-- back the complete active transaction, including a caller-owned transaction.
-- Mutations use row/range locks. Retry deadlock victims as whole business calls.
-- Application authorization is required before calling administrative methods.
-- This SQL API records transfer jobs/results; it does not contact a payment API.
-- Create base users/organizations/coupons/rules/draft events with ordinary INSERT.
-- SQL object parameters flatten the UML value objects; ticket lists are JSON arrays.
-- Inventory belongs to hold items. Standing primitives additionally take a hold
-- or item identifier; consume sells the complete aggregate, never a partial order.
-- Insert allocates inventory automatically. Return-to-inventory methods verify
-- refund completion; triggers already returned it and must not return it twice.
-- New transfer retries use new identifiers. UNKNOWN results keep funds reserved.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER PROCEDURE dbo.sp_user_update_profile
@user_id BIGINT, @full_name NVARCHAR(255), @phone NVARCHAR(32) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NULLIF(LTRIM(RTRIM(@full_name)),N'') IS NULL THROW 51000,N'Full name is required.',1;
UPDATE dbo.users SET full_name=@full_name,phone=@phone WHERE id=@user_id;
IF @@ROWCOUNT=0 THROW 51000,N'User not found.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_user_verify_email
@user_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
UPDATE dbo.users SET email_verified=1 WHERE id=@user_id;
IF @@ROWCOUNT=0 THROW 51000,N'User not found.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_user_assign_role
@user_id BIGINT, @organization_id BIGINT, @role NVARCHAR(64)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NULLIF(LTRIM(RTRIM(@role)),N'') IS NULL THROW 51000,N'Role is required.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.users WITH(UPDLOCK,HOLDLOCK) WHERE id=@user_id) THROW 51000,N'User not found.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.organizations WHERE id=@organization_id) THROW 51000,N'Organization not found.',1;
UPDATE dbo.user_organization_roles SET role=@role WHERE user_id=@user_id AND organization_id=@organization_id;
IF @@ROWCOUNT=0 INSERT dbo.user_organization_roles(user_id,organization_id,role) VALUES(@user_id,@organization_id,@role);
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_user_revoke_role
@user_id BIGINT, @organization_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.users WITH(UPDLOCK,HOLDLOCK) WHERE id=@user_id) THROW 51000,N'User not found.',1;
DELETE dbo.user_organization_roles WHERE user_id=@user_id AND organization_id=@organization_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_organization_submit_for_approval
@organization_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
UPDATE dbo.organizations SET status=N'PENDING_APPROVAL', rejection_reason=NULL
WHERE id=@organization_id AND status IN (N'DRAFT',N'REJECTED');
IF @@ROWCOUNT=0 THROW 51000,N'Organization not found or transition is not allowed.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_organization_approve
@organization_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
UPDATE dbo.organizations SET status=N'APPROVED', rejection_reason=NULL
WHERE id=@organization_id AND status IN (N'PENDING_APPROVAL',N'APPROVED');
IF @@ROWCOUNT=0 THROW 51000,N'Organization not found or transition is not allowed.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_organization_reject
@organization_id BIGINT, @reason NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NULLIF(LTRIM(RTRIM(@reason)),N'') IS NULL THROW 51000,N'Rejection reason is required.',1;
UPDATE dbo.organizations SET status=N'REJECTED', rejection_reason=@reason
WHERE id=@organization_id AND status IN (N'PENDING_APPROVAL');
IF @@ROWCOUNT=0 THROW 51000,N'Organization not found or transition is not allowed.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_coupon_activate
@coupon_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
UPDATE dbo.coupons SET is_active=1 WHERE id=@coupon_id;
IF @@ROWCOUNT=0 THROW 51000,N'Coupon not found.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_coupon_deactivate
@coupon_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
UPDATE dbo.coupons SET is_active=0 WHERE id=@coupon_id;
IF @@ROWCOUNT=0 THROW 51000,N'Coupon not found.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_coupon_revise_terms
@coupon_id BIGINT, @discount_type NVARCHAR(64), @fixed_amount DECIMAL(19,2),
@percentage DECIMAL(7,4), @valid_from DATETIME2(6), @valid_to DATETIME2(6), @max_uses INT, @allocated_uses INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.coupons WITH(UPDLOCK,HOLDLOCK) WHERE id=@coupon_id) THROW 51000,N'Coupon not found.',1;
DECLARE @actual BIGINT;
SELECT @actual=allocated_uses FROM dbo.v_coupons WHERE id=@coupon_id;
IF @allocated_uses IS NULL OR @actual<>@allocated_uses OR @max_uses<@actual THROW 51000,N'Allocated uses must match database reservations.',1;
IF @actual>0 AND EXISTS(SELECT discount_type,fixed_amount,percentage,valid_from,valid_to FROM dbo.coupons WHERE id=@coupon_id
    EXCEPT SELECT @discount_type,@fixed_amount,@percentage,@valid_from,@valid_to)
    THROW 51000,N'Reserved coupon terms cannot be changed.',1;
UPDATE dbo.coupons SET discount_type=@discount_type,fixed_amount=@fixed_amount,percentage=@percentage,
valid_from=@valid_from,valid_to=@valid_to,max_uses=@max_uses WHERE id=@coupon_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_event_update_details
@event_id BIGINT, @title NVARCHAR(255), @description NVARCHAR(MAX), @category NVARCHAR(64),
@venue_name NVARCHAR(255), @venue_address NVARCHAR(500), @cover_image_url NVARCHAR(MAX) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
UPDATE dbo.events SET title=@title,description=@description,category=@category,venue_name=@venue_name,
venue_address=@venue_address,cover_image_url=@cover_image_url WHERE id=@event_id AND status IN(N'DRAFT',N'REJECTED');
IF @@ROWCOUNT=0 THROW 51000,N'Only draft or rejected events can be edited.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_event_configure_schedule
@event_id BIGINT, @sale_start DATETIME2(6), @sale_end DATETIME2(6),
@start_time DATETIME2(6), @end_time DATETIME2(6)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT(@sale_start<@sale_end AND @sale_end<=@start_time AND @start_time<@end_time)
    THROW 51000,N'Invalid event and sale schedule.',1;
UPDATE dbo.events SET sale_start=@sale_start,sale_end=@sale_end,start_time=@start_time,end_time=@end_time
WHERE id=@event_id AND status IN(N'DRAFT',N'REJECTED');
IF @@ROWCOUNT=0 THROW 51000,N'Only draft or rejected event schedules can be edited.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_event_add_zone
@event_id BIGINT, @name NVARCHAR(255), @type NVARCHAR(64), @price DECIMAL(19,2),
@standing_capacity INT, @zone_id BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id AND status IN(N'DRAFT',N'REJECTED'))
    THROW 51000,N'Only draft or rejected events can add zones.',1;
INSERT dbo.zones(event_id,name,type,price,standing_capacity,standing_held,standing_sold)
VALUES(@event_id,@name,@type,@price,CASE WHEN @type=N'STANDING' THEN @standing_capacity END,
CASE WHEN @type=N'STANDING' THEN 0 END,CASE WHEN @type=N'STANDING' THEN 0 END);
SET @zone_id=SCOPE_IDENTITY();
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_event_remove_zone
@event_id BIGINT, @zone_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id AND status IN(N'DRAFT',N'REJECTED'))
    THROW 51000,N'Only draft or rejected events can remove zones.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.zones WITH(UPDLOCK,HOLDLOCK) WHERE id=@zone_id AND event_id=@event_id) THROW 51000,N'Zone not found in this event.',1;
IF EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE zone_id=@zone_id) OR EXISTS(SELECT 1 FROM dbo.order_items WHERE zone_id=@zone_id)
    THROW 51000,N'Zone has reservation or order history.',1;
DELETE dbo.seats WHERE zone_id=@zone_id;
DELETE dbo.zones WHERE id=@zone_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_event_submit_for_approval
@event_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.events e WITH(UPDLOCK,HOLDLOCK) JOIN dbo.organizations o ON o.id=e.organization_id
    WHERE e.id=@event_id AND e.status IN(N'DRAFT',N'REJECTED') AND o.status=N'APPROVED' AND e.start_time>SYSUTCDATETIME())
    THROW 51000,N'Event cannot be submitted.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.zones WHERE event_id=@event_id) OR EXISTS(SELECT 1 FROM dbo.zones WHERE event_id=@event_id AND dbo.fn_zone_get_capacity(id)<=0)
    THROW 51000,N'Every event zone must have capacity.',1;
UPDATE dbo.events SET status=N'PENDING_APPROVAL',rejection_reason=NULL WHERE id=@event_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_event_publish
@event_id BIGINT, @rule_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
IF NOT EXISTS(SELECT 1 FROM dbo.events e WITH(UPDLOCK,HOLDLOCK) JOIN dbo.organizations o ON o.id=e.organization_id
    JOIN dbo.commission_rules r ON r.organization_id=e.organization_id AND r.id=@rule_id
    WHERE e.id=@event_id AND e.status=N'PENDING_APPROVAL' AND o.status=N'APPROVED' AND e.start_time>@now
    AND dbo.fn_commission_rule_is_effective_at(@rule_id,@now)=1)
    THROW 51000,N'Event, organization or commission rule is not publishable.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.zones WHERE event_id=@event_id) OR EXISTS(SELECT 1 FROM dbo.zones WHERE event_id=@event_id AND dbo.fn_zone_get_capacity(id)<=0)
    THROW 51000,N'Every event zone must have capacity.',1;
UPDATE dbo.events SET status=N'PUBLISHED',commission_rule_id=@rule_id,rejection_reason=NULL WHERE id=@event_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_event_reject
@event_id BIGINT, @reason NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NULLIF(LTRIM(RTRIM(@reason)),N'') IS NULL THROW 51000,N'Rejection reason is required.',1;
UPDATE dbo.events SET status=N'REJECTED',rejection_reason=@reason WHERE id=@event_id AND status=N'PENDING_APPROVAL';
IF @@ROWCOUNT=0 THROW 51000,N'Event is not awaiting approval.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_zone_change_price
@zone_id BIGINT, @price DECIMAL(19,2)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
UPDATE z SET price=@price FROM dbo.zones z JOIN dbo.events e ON e.id=z.event_id
WHERE z.id=@zone_id AND e.status<>N'CANCELLED' AND e.start_time>SYSUTCDATETIME();
IF @@ROWCOUNT=0 THROW 51000,N'Zone not found or event has started/cancelled.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_zone_configure_seating
@zone_id BIGINT, @rows INT, @seats_per_row INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF @rows IS NULL OR @seats_per_row IS NULL OR @rows<=0 OR @seats_per_row<=0 OR CAST(@rows AS BIGINT)*@seats_per_row>10000
    THROW 51000,N'Seating must contain 1 to 10000 seats.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.zones z WITH(UPDLOCK,HOLDLOCK) JOIN dbo.events e ON e.id=z.event_id
    WHERE z.id=@zone_id AND z.type=N'SEATED' AND e.status IN(N'DRAFT',N'REJECTED')) THROW 51000,N'Seated zone is not editable.',1;
IF EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE zone_id=@zone_id) OR EXISTS(SELECT 1 FROM dbo.order_items WHERE zone_id=@zone_id)
    THROW 51000,N'Cannot rebuild seating with reservation history.',1;
DELETE dbo.seats WHERE zone_id=@zone_id;
INSERT dbo.seats(zone_id,row_name,seat_number,status)
SELECT @zone_id,CONVERT(NVARCHAR(100),r.n),s.n,N'AVAILABLE' FROM dbo.v_numbers r CROSS JOIN dbo.v_numbers s
WHERE r.n<=@rows AND s.n<=@seats_per_row;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_zone_set_standing_capacity
@zone_id BIGINT, @capacity INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
UPDATE z SET standing_capacity=@capacity FROM dbo.zones z JOIN dbo.events e ON e.id=z.event_id
WHERE z.id=@zone_id AND z.type=N'STANDING' AND e.status<>N'CANCELLED' AND e.start_time>SYSUTCDATETIME()
AND @capacity>=z.standing_held+z.standing_sold;
IF @@ROWCOUNT=0 THROW 51000,N'Capacity cannot be below held and sold inventory.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_ticket_hold_create
@buyer_id BIGINT, @event_id BIGINT, @expires_at DATETIME2(6), @hold_id BIGINT OUTPUT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
IF dbo.fn_user_is_eligible_buyer(@buyer_id)=0 OR dbo.fn_event_is_sale_active(@event_id,@now)=0 THROW 51000,N'Buyer or sale is not eligible.',1;
IF @expires_at IS NULL OR @expires_at<=@now OR NOT EXISTS(SELECT 1 FROM dbo.events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id AND @expires_at<=sale_end)
    THROW 51000,N'Hold expiry must be within the current sale window.',1;
INSERT dbo.ticket_holds(buyer_id,event_id,expires_at,status) VALUES(@buyer_id,@event_id,@expires_at,N'ACTIVE');
SET @hold_id=SCOPE_IDENTITY();
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_ticket_hold_add_item
@hold_id BIGINT, @zone_id BIGINT, @seat_id BIGINT, @quantity INT, @item_id BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF @quantity IS NULL OR @quantity<=0 OR @quantity>10000 THROW 51000,N'Quantity must be 1 to 10000.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ticket_holds WITH(UPDLOCK,HOLDLOCK) WHERE id=@hold_id AND status=N'ACTIVE' AND expires_at>SYSUTCDATETIME())
    THROW 51000,N'Hold is not active.',1;
IF EXISTS(SELECT 1 FROM dbo.orders WHERE hold_id=@hold_id) THROW 51000,N'An order already freezes this hold.',1;
DECLARE @price DECIMAL(19,2);
SELECT @price=z.price FROM dbo.zones z WITH(UPDLOCK,HOLDLOCK) JOIN dbo.ticket_holds h ON h.event_id=z.event_id
WHERE h.id=@hold_id AND z.id=@zone_id AND dbo.fn_event_is_sale_active(h.event_id,SYSUTCDATETIME())=1;
IF @price IS NULL THROW 51000,N'Zone does not belong to an active sale for this hold.',1;
INSERT dbo.ticket_hold_items(ticket_hold_id,zone_id,seat_id,quantity,unit_price,inventory_state)
VALUES(@hold_id,@zone_id,@seat_id,@quantity,@price,N'HELD');
SET @item_id=SCOPE_IDENTITY();
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_ticket_hold_release
@hold_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @status NVARCHAR(64);
SELECT @status=status FROM dbo.ticket_holds WITH(UPDLOCK,HOLDLOCK) WHERE id=@hold_id;
IF @status=N'RELEASED' GOTO ProcDone;
IF @status IS NULL OR @status<>N'ACTIVE' THROW 51000,N'Only active holds can be released.',1;
UPDATE dbo.orders SET status=N'CANCELLED' WHERE hold_id=@hold_id AND status=N'AWAITING_PAYMENT';
UPDATE dbo.ticket_holds SET status=N'RELEASED' WHERE id=@hold_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_ticket_hold_consume
@hold_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
DECLARE @status NVARCHAR(64), @event BIGINT;
SELECT @status=status,@event=event_id FROM dbo.ticket_holds WITH(UPDLOCK,HOLDLOCK) WHERE id=@hold_id;
IF @status=N'CONSUMED' GOTO ProcDone;
IF dbo.fn_ticket_hold_is_active(@hold_id,@now)=0 OR dbo.fn_event_is_sale_active(@event,@now)=0 THROW 51000,N'Hold cannot be consumed.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE ticket_hold_id=@hold_id) OR EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE ticket_hold_id=@hold_id AND inventory_state<>N'HELD')
    THROW 51000,N'Hold must contain only reserved items.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.orders o WHERE o.hold_id=@hold_id AND o.status=N'AWAITING_PAYMENT'
    AND (dbo.fn_order_total(o.id)=0 OR EXISTS(SELECT 1 FROM dbo.payments p WHERE p.order_id=o.id AND p.status=N'CAPTURED' AND p.amount=dbo.fn_order_total(o.id))))
    THROW 51000,N'A captured payment or a zero-value order is required.',1;
UPDATE dbo.ticket_holds SET status=N'CONSUMED' WHERE id=@hold_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_order_from_hold
@hold_id BIGINT, @order_id BIGINT OUTPUT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
DECLARE @buyer BIGINT,@event BIGINT;
SELECT @buyer=buyer_id,@event=event_id FROM dbo.ticket_holds WITH(UPDLOCK,HOLDLOCK) WHERE id=@hold_id;
SET @order_id=NULL;
SELECT @order_id=id FROM dbo.orders WHERE hold_id=@hold_id;
IF @order_id IS NOT NULL BEGIN
    IF EXISTS(SELECT 1 FROM dbo.orders WHERE id=@order_id AND status IN(N'AWAITING_PAYMENT',N'PAID')) GOTO ProcDone;
    THROW 51000,N'This hold already has a terminal order.',1;
END;
IF dbo.fn_ticket_hold_is_active(@hold_id,@now)=0 OR dbo.fn_user_is_eligible_buyer(@buyer)=0 OR dbo.fn_event_is_sale_active(@event,@now)=0
    THROW 51000,N'Hold cannot create an order.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE ticket_hold_id=@hold_id) OR EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE ticket_hold_id=@hold_id AND inventory_state<>N'HELD')
    THROW 51000,N'Hold must contain reserved items.',1;
INSERT dbo.orders(buyer_id,event_id,hold_id,coupon_id,accepted_payment_id,order_code,status,discount_amount)
VALUES(@buyer,@event,@hold_id,NULL,NULL,CONCAT(N'O-',CONVERT(NVARCHAR(36),NEWID())),N'AWAITING_PAYMENT',0);
SET @order_id=SCOPE_IDENTITY();
INSERT dbo.order_items(hold_item_id,order_id,zone_id,seat_id,zone_name_snapshot,seat_label_snapshot,quantity,unit_price)
SELECT i.id,@order_id,i.zone_id,i.seat_id,z.name,CASE WHEN s.id IS NOT NULL THEN CONCAT(s.row_name,N'-',s.seat_number) END,i.quantity,i.unit_price
FROM dbo.ticket_hold_items i JOIN dbo.zones z ON z.id=i.zone_id LEFT JOIN dbo.seats s ON s.id=i.seat_id WHERE i.ticket_hold_id=@hold_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_order_apply_coupon
@order_id BIGINT, @coupon_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.orders o WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id AND status=N'AWAITING_PAYMENT' AND dbo.fn_ticket_hold_is_active(hold_id,SYSUTCDATETIME())=1)
    THROW 51000,N'Order is not editable.',1;
IF EXISTS(SELECT 1 FROM dbo.payments WHERE order_id=@order_id AND status IN(N'PENDING',N'UNKNOWN',N'CAPTURED'))
    THROW 51000,N'Order terms are frozen by a payment attempt.',1;

SET @now=COALESCE(@now,SYSUTCDATETIME());
IF EXISTS(SELECT 1 FROM dbo.orders WHERE id=@order_id AND coupon_id=@coupon_id) GOTO ProcDone;
IF NOT EXISTS(SELECT 1 FROM dbo.coupons c WITH(UPDLOCK,HOLDLOCK) JOIN dbo.events e ON e.organization_id=c.organization_id
    JOIN dbo.orders o ON o.event_id=e.id WHERE o.id=@order_id AND c.id=@coupon_id)
    THROW 51000,N'Coupon belongs to a different organization.',1;
IF dbo.fn_coupon_is_active_at(@coupon_id,@now)=0 THROW 51000,N'Coupon is inactive, expired or exhausted.',1;
DECLARE @discount DECIMAL(19,2)=dbo.fn_coupon_calculate_discount(@coupon_id,dbo.fn_order_subtotal(@order_id));
IF @discount IS NULL THROW 51000,N'Coupon terms are invalid.',1;
UPDATE dbo.orders SET coupon_id=@coupon_id,discount_amount=@discount WHERE id=@order_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_order_remove_coupon
@order_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.orders o WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id AND status=N'AWAITING_PAYMENT' AND dbo.fn_ticket_hold_is_active(hold_id,SYSUTCDATETIME())=1)
    THROW 51000,N'Order is not editable.',1;
IF EXISTS(SELECT 1 FROM dbo.payments WHERE order_id=@order_id AND status IN(N'PENDING',N'UNKNOWN',N'CAPTURED'))
    THROW 51000,N'Order terms are frozen by a payment attempt.',1;

UPDATE dbo.orders SET coupon_id=NULL,discount_amount=0 WHERE id=@order_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_order_mark_paid
@order_id BIGINT, @payment_id BIGINT = NULL, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
DECLARE @status NVARCHAR(64),@accepted BIGINT,@hold BIGINT,@buyer BIGINT,@event BIGINT,@total DECIMAL(19,2),@subtotal DECIMAL(19,2);
SELECT @status=status,@accepted=accepted_payment_id,@hold=hold_id,@buyer=buyer_id,@event=event_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
IF @status=N'PAID' BEGIN
    IF (@accepted=@payment_id OR (@accepted IS NULL AND @payment_id IS NULL)) GOTO ProcDone;
    THROW 51000,N'Order already accepted a different payment.',1;
END;
SET @subtotal=dbo.fn_order_subtotal(@order_id); SET @total=dbo.fn_order_total(@order_id);
IF @status IS NULL OR @status<>N'AWAITING_PAYMENT' OR @total<0 OR dbo.fn_user_is_eligible_buyer(@buyer)=0 OR dbo.fn_event_is_sale_active(@event,@now)=0
    THROW 51000,N'Order cannot be paid.',1;
IF @payment_id IS NULL BEGIN
    IF @total<>0 THROW 51000,N'Nonzero orders require a captured payment.',1;
END ELSE IF NOT EXISTS(SELECT 1 FROM dbo.payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment_id AND order_id=@order_id AND status=N'CAPTURED' AND amount=@total)
    THROW 51000,N'Payment is not captured for the exact order total.',1;
EXEC dbo.sp_ticket_hold_consume @hold_id=@hold,@now=@now;
UPDATE dbo.orders SET status=N'PAID',accepted_payment_id=@payment_id WHERE id=@order_id;
;WITH units AS (
    SELECT i.id AS item_id,i.unit_price,n.n FROM dbo.order_items i JOIN dbo.v_numbers n ON n.n<=i.quantity WHERE i.order_id=@order_id
), allocation AS (
    SELECT *,ROW_NUMBER() OVER(ORDER BY item_id,n) AS rn,COUNT_BIG(*) OVER() AS total_units,
        CAST(ROUND(CASE WHEN @subtotal=0 THEN 0 ELSE unit_price*@total/@subtotal END,2,1) AS DECIMAL(19,2)) AS base_amount FROM units
)
INSERT dbo.tickets(order_item_id,ticket_code,status,paid_amount)
SELECT item_id,CONCAT(N'T-',CONVERT(NVARCHAR(36),NEWID())),N'VALID',
    base_amount+CASE WHEN rn=total_units THEN @total-SUM(base_amount) OVER() ELSE 0 END FROM allocation;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_order_cancel
@order_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @status NVARCHAR(64),@hold BIGINT;
SELECT @status=status,@hold=hold_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
IF @status=N'CANCELLED' GOTO ProcDone;
IF @status IS NULL OR @status<>N'AWAITING_PAYMENT' THROW 51000,N'Only unpaid orders can close.',1;

UPDATE dbo.orders SET status=N'CANCELLED' WHERE id=@order_id;
EXEC dbo.sp_ticket_hold_release @hold_id=@hold;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_order_expire
@order_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @status NVARCHAR(64),@hold BIGINT;
SELECT @status=status,@hold=hold_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
IF @status=N'EXPIRED' GOTO ProcDone;
IF @status IS NULL OR @status<>N'AWAITING_PAYMENT' THROW 51000,N'Only unpaid orders can close.',1;
SET @now=COALESCE(@now,SYSUTCDATETIME());
IF dbo.fn_ticket_hold_is_expired(@hold,@now)=0 THROW 51000,N'Order hold has not expired.',1;
UPDATE dbo.orders SET status=N'EXPIRED' WHERE id=@order_id;
EXEC dbo.sp_ticket_hold_release @hold_id=@hold;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_payment_start
@order_id BIGINT, @txn_ref NVARCHAR(255), @payment_id BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NULLIF(LTRIM(RTRIM(@txn_ref)),N'') IS NULL THROW 51000,N'Transaction reference is required.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id) THROW 51000,N'Order not found.',1;
SET @payment_id=NULL;
SELECT @payment_id=id FROM dbo.payments WITH(UPDLOCK,HOLDLOCK) WHERE txn_ref=@txn_ref AND order_id=@order_id;
IF @payment_id IS NOT NULL GOTO ProcDone;
IF EXISTS(SELECT 1 FROM dbo.payments WHERE txn_ref=@txn_ref) THROW 51000,N'Transaction reference belongs to another order.',1;
DECLARE @amount DECIMAL(19,2)=dbo.fn_order_total(@order_id);
IF @amount IS NULL OR @amount<=0 OR NOT EXISTS(SELECT 1 FROM dbo.orders WHERE id=@order_id AND status=N'AWAITING_PAYMENT' AND dbo.fn_ticket_hold_is_active(hold_id,SYSUTCDATETIME())=1)
    THROW 51000,N'Order cannot start a payment.',1;
IF EXISTS(SELECT 1 FROM dbo.payments WHERE order_id=@order_id AND status IN(N'PENDING',N'UNKNOWN')) THROW 51000,N'An unresolved payment already exists.',1;
INSERT dbo.payments(order_id,amount,status,txn_ref,transaction_no) VALUES(@order_id,@amount,N'PENDING',@txn_ref,NULL);
SET @payment_id=SCOPE_IDENTITY();
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_payment_mark_failed
@payment_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @order BIGINT,@lock BIGINT;
SELECT @order=order_id FROM dbo.payments WHERE id=@payment_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
UPDATE dbo.payments SET status=N'FAILED' WHERE id=@payment_id AND status IN(N'PENDING',N'UNKNOWN',N'FAILED');
IF @@ROWCOUNT=0 THROW 51000,N'Payment state cannot accept this result.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_payment_mark_unknown
@payment_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @order BIGINT,@lock BIGINT;
SELECT @order=order_id FROM dbo.payments WHERE id=@payment_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
UPDATE dbo.payments SET status=N'UNKNOWN' WHERE id=@payment_id AND status IN(N'PENDING',N'UNKNOWN');
IF @@ROWCOUNT=0 THROW 51000,N'Payment state cannot accept this result.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_refund_create_compensation
@payment_id BIGINT, @reason NVARCHAR(MAX), @refund_id BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NULLIF(LTRIM(RTRIM(@reason)),N'') IS NULL THROW 51000,N'Compensation reason is required.',1;
DECLARE @order BIGINT,@lock BIGINT,@amount DECIMAL(19,2);
SELECT @order=order_id FROM dbo.payments WHERE id=@payment_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
SET @refund_id=NULL;
SELECT @refund_id=id FROM dbo.refunds WHERE payment_id=@payment_id AND purpose=N'COMPENSATION';
IF @refund_id IS NOT NULL GOTO ProcDone;
SELECT @amount=amount FROM dbo.payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment_id AND status=N'CAPTURED';
IF @amount IS NULL OR EXISTS(SELECT 1 FROM dbo.orders WHERE accepted_payment_id=@payment_id) THROW 51000,N'Only unaccepted captures can be compensated.',1;
INSERT dbo.refunds(order_id,payment_id,amount,purpose,reason_type,reason,rejection_reason,status,current_attempt_id,provider_reference,processed_at)
VALUES(@order,@payment_id,@amount,N'COMPENSATION',NULL,@reason,NULL,N'APPROVED',NULL,NULL,NULL);
SET @refund_id=SCOPE_IDENTITY();
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_payment_mark_captured
@payment_id BIGINT, @transaction_no NVARCHAR(255), @paid_at DATETIME2(6)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NULLIF(LTRIM(RTRIM(@transaction_no)),N'') IS NULL OR @paid_at IS NULL THROW 51000,N'Capture reference and timestamp are required.',1;
DECLARE @order BIGINT,@lock BIGINT,@old NVARCHAR(64),@ref NVARCHAR(255);
SELECT @order=order_id FROM dbo.payments WHERE id=@payment_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
SELECT @old=status,@ref=transaction_no FROM dbo.payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment_id;
IF @old IS NULL THROW 51000,N'Payment not found.',1;
IF @old=N'CAPTURED' BEGIN
    IF @ref=@transaction_no GOTO ProcDone;
    THROW 51000,N'Conflicting capture callback.',1;
END;
UPDATE dbo.payments SET status=N'CAPTURED',transaction_no=@transaction_no WHERE id=@payment_id;
IF EXISTS(SELECT 1 FROM dbo.orders o JOIN dbo.payments p ON p.order_id=o.id AND p.id=@payment_id
    WHERE o.id=@order AND o.status=N'AWAITING_PAYMENT' AND p.amount=dbo.fn_order_total(o.id)
    AND dbo.fn_user_is_eligible_buyer(o.buyer_id)=1 AND dbo.fn_ticket_hold_is_active(o.hold_id,@paid_at)=1 AND dbo.fn_event_is_sale_active(o.event_id,@paid_at)=1)
    EXEC dbo.sp_order_mark_paid @order_id=@order,@payment_id=@payment_id,@now=@paid_at;
ELSE BEGIN
    DECLARE @refund BIGINT;
    EXEC dbo.sp_refund_create_compensation @payment_id=@payment_id,@reason=N'Captured payment cannot fulfill its order.',@refund_id=@refund OUTPUT;
END;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_refund_request_for_tickets
@order_id BIGINT, @ticket_ids NVARCHAR(MAX), @reason_type NVARCHAR(64),
@reason NVARCHAR(MAX), @refund_id BIGINT OUTPUT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
IF ISJSON(@ticket_ids)<>1 OR LEFT(LTRIM(@ticket_ids),1)<>N'[' THROW 51000,N'Ticket identifiers must be a JSON array.',1;
IF NULLIF(LTRIM(RTRIM(@reason_type)),N'') IS NULL THROW 51000,N'Refund reason type is required.',1;
DECLARE @ids TABLE(id BIGINT PRIMARY KEY);
IF EXISTS(SELECT 1 FROM OPENJSON(@ticket_ids) WHERE type<>2 OR TRY_CONVERT(BIGINT,value) IS NULL)
    THROW 51000,N'Ticket identifiers must be integers.',1;
IF EXISTS(SELECT value FROM OPENJSON(@ticket_ids) GROUP BY value HAVING COUNT(*)>1) THROW 51000,N'Duplicate ticket identifiers.',1;
INSERT @ids SELECT CONVERT(BIGINT,value) FROM OPENJSON(@ticket_ids);
IF NOT EXISTS(SELECT 1 FROM @ids) THROW 51000,N'Refund requires at least one ticket.',1;
DECLARE @payment BIGINT,@event BIGINT,@amount DECIMAL(19,2),@count INT;
SELECT @payment=accepted_payment_id,@event=event_id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id AND status=N'PAID';
IF @event IS NULL THROW 51000,N'Order is not paid.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.events WHERE id=@event AND (status=N'CANCELLED' OR @now<start_time)) THROW 51000,N'Refund requests close when the event starts.',1;
SELECT @count=COUNT(*),@amount=SUM(t.paid_amount) FROM dbo.tickets t WITH(UPDLOCK,HOLDLOCK)
JOIN dbo.order_items i ON i.id=t.order_item_id JOIN @ids x ON x.id=t.id WHERE i.order_id=@order_id AND t.status=N'VALID';
IF @count<>(SELECT COUNT(*) FROM @ids) THROW 51000,N'Every requested ticket must be valid and belong to this order.',1;
INSERT dbo.refunds(order_id,payment_id,amount,purpose,reason_type,reason,rejection_reason,status,current_attempt_id,provider_reference,processed_at)
VALUES(@order_id,@payment,@amount,N'TICKET_REFUND',@reason_type,@reason,NULL,N'REQUESTED',NULL,NULL,NULL);
SET @refund_id=SCOPE_IDENTITY();
INSERT dbo.refund_tickets(refund_id,ticket_id) SELECT @refund_id,id FROM @ids;
UPDATE t SET status=N'REFUND_PENDING' FROM dbo.tickets t JOIN @ids x ON x.id=t.id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_refund_adopt_event_cancellation
@refund_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @order BIGINT,@lock BIGINT;
SELECT @order=order_id FROM dbo.refunds WHERE id=@refund_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
IF NOT EXISTS(SELECT 1 FROM dbo.refunds WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id) THROW 51000,N'Refund not found.',1;

IF NOT EXISTS(SELECT 1 FROM dbo.refunds r JOIN dbo.orders o ON o.id=r.order_id JOIN dbo.events e ON e.id=o.event_id
    WHERE r.id=@refund_id AND r.purpose=N'TICKET_REFUND' AND e.status=N'CANCELLED' AND r.status NOT IN(N'REJECTED',N'COMPLETED'))
    THROW 51000,N'Refund cannot adopt this event cancellation.',1;
UPDATE dbo.refunds SET reason_type=N'EVENT_CANCELLED',reason=N'Event cancelled.',
status=CASE WHEN status=N'REQUESTED' THEN N'APPROVED' ELSE status END WHERE id=@refund_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_refund_approve
@refund_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @order BIGINT,@lock BIGINT;
SELECT @order=order_id FROM dbo.refunds WHERE id=@refund_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
IF NOT EXISTS(SELECT 1 FROM dbo.refunds WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id) THROW 51000,N'Refund not found.',1;

UPDATE dbo.refunds SET status=N'APPROVED' WHERE id=@refund_id AND status IN(N'REQUESTED',N'APPROVED');
IF @@ROWCOUNT=0 THROW 51000,N'Refund is not awaiting approval.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_refund_reject
@refund_id BIGINT, @reason NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @order BIGINT,@lock BIGINT;
SELECT @order=order_id FROM dbo.refunds WHERE id=@refund_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
IF NOT EXISTS(SELECT 1 FROM dbo.refunds WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id) THROW 51000,N'Refund not found.',1;

IF NULLIF(LTRIM(RTRIM(@reason)),N'') IS NULL THROW 51000,N'Rejection reason is required.',1;
UPDATE dbo.refunds SET status=N'REJECTED',rejection_reason=@reason WHERE id=@refund_id AND status IN(N'REQUESTED',N'APPROVED') AND purpose=N'TICKET_REFUND'
AND NOT EXISTS(SELECT 1 FROM dbo.orders o JOIN dbo.events e ON e.id=o.event_id WHERE o.id=dbo.refunds.order_id AND e.status=N'CANCELLED');
IF @@ROWCOUNT=0 THROW 51000,N'Refund cannot be rejected after a transfer starts.',1;
UPDATE t SET status=N'VALID' FROM dbo.tickets t JOIN dbo.refund_tickets rt ON rt.ticket_id=t.id WHERE rt.refund_id=@refund_id AND t.status=N'REFUND_PENDING';
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_refund_begin_attempt
@refund_id BIGINT, @attempt_id NVARCHAR(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @order BIGINT,@lock BIGINT;
SELECT @order=order_id FROM dbo.refunds WHERE id=@refund_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
IF NOT EXISTS(SELECT 1 FROM dbo.refunds WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id) THROW 51000,N'Refund not found.',1;

IF NULLIF(LTRIM(RTRIM(@attempt_id)),N'') IS NULL THROW 51000,N'Attempt identifier is required.',1;
IF EXISTS(SELECT 1 FROM dbo.refund_attempts WHERE attempt_id=@attempt_id) BEGIN
    IF EXISTS(SELECT 1 FROM dbo.refund_attempts WHERE attempt_id=@attempt_id AND refund_id=@refund_id) GOTO ProcDone;
    THROW 51000,N'Attempt identifier belongs to another refund.',1;
END;
DECLARE @amount DECIMAL(19,2);
SELECT @amount=r.amount FROM dbo.refunds r JOIN dbo.payments p ON p.id=r.payment_id
WHERE r.id=@refund_id AND r.status IN(N'APPROVED',N'FAILED') AND r.amount>0 AND p.status=N'CAPTURED';
IF @amount IS NULL THROW 51000,N'Refund cannot start a transfer.',1;
INSERT dbo.refund_attempts(attempt_id,refund_id,amount,status) VALUES(@attempt_id,@refund_id,@amount,N'PROCESSING');
UPDATE dbo.refunds SET current_attempt_id=@attempt_id,status=N'PROCESSING',provider_reference=NULL,processed_at=NULL WHERE id=@refund_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_refund_record_attempt_result
@refund_id BIGINT, @attempt_id NVARCHAR(255), @result NVARCHAR(16),
@reference NVARCHAR(255) = NULL, @processed_at DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @order BIGINT,@lock BIGINT;
SELECT @order=order_id FROM dbo.refunds WHERE id=@refund_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
IF NOT EXISTS(SELECT 1 FROM dbo.refunds WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id) THROW 51000,N'Refund not found.',1;

IF @result IS NULL OR @result NOT IN(N'SUCCEEDED',N'FAILED',N'UNKNOWN') THROW 51000,N'Invalid transfer result.',1;
DECLARE @old NVARCHAR(16),@old_ref NVARCHAR(255),@current NVARCHAR(255);
SELECT @current=current_attempt_id FROM dbo.refunds WHERE id=@refund_id;
SELECT @old=status,@old_ref=provider_reference FROM dbo.refund_attempts WITH(UPDLOCK,HOLDLOCK) WHERE attempt_id=@attempt_id AND refund_id=@refund_id;
IF @old IS NULL THROW 51000,N'Refund attempt not found.',1;
IF @old IN(N'SUCCEEDED',N'FAILED') BEGIN
    IF @old=@result AND (@old_ref=@reference OR (@old_ref IS NULL AND @reference IS NULL)) GOTO ProcDone;
    THROW 51000,N'Conflicting terminal refund callback.',1;
END;
IF @current<>@attempt_id OR @current IS NULL THROW 51000,N'Callback does not match the current refund attempt.',1;
SET @processed_at=CASE WHEN @result=N'UNKNOWN' THEN NULL ELSE COALESCE(@processed_at,SYSUTCDATETIME()) END;
UPDATE dbo.refund_attempts SET status=@result,provider_reference=@reference,processed_at=@processed_at WHERE attempt_id=@attempt_id;
UPDATE dbo.refunds SET status=CASE @result WHEN N'SUCCEEDED' THEN N'COMPLETED' ELSE @result END,
provider_reference=@reference,processed_at=@processed_at WHERE id=@refund_id;
IF @result=N'SUCCEEDED' UPDATE t SET status=N'REFUNDED' FROM dbo.tickets t JOIN dbo.refund_tickets rt ON rt.ticket_id=t.id WHERE rt.refund_id=@refund_id AND t.status=N'REFUND_PENDING';
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_refund_complete_without_transfer
@refund_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @order BIGINT,@lock BIGINT;
SELECT @order=order_id FROM dbo.refunds WHERE id=@refund_id;
SELECT @lock=id FROM dbo.orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order;
IF NOT EXISTS(SELECT 1 FROM dbo.refunds WITH(UPDLOCK,HOLDLOCK) WHERE id=@refund_id) THROW 51000,N'Refund not found.',1;

IF EXISTS(SELECT 1 FROM dbo.refunds WHERE id=@refund_id AND status=N'COMPLETED' AND amount=0) GOTO ProcDone;
UPDATE dbo.refunds SET status=N'COMPLETED',processed_at=SYSUTCDATETIME() WHERE id=@refund_id AND status=N'APPROVED' AND amount=0;
IF @@ROWCOUNT=0 THROW 51000,N'Only approved zero-value refunds can skip a transfer.',1;
UPDATE t SET status=N'REFUNDED' FROM dbo.tickets t JOIN dbo.refund_tickets rt ON rt.ticket_id=t.id WHERE rt.refund_id=@refund_id AND t.status=N'REFUND_PENDING';
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_ticket_check_in
@ticket_id BIGINT, @event_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
IF dbo.fn_event_is_check_in_open(@event_id,@now)=0 THROW 51000,N'Check-in is closed.',1;
UPDATE t SET status=N'CHECKED_IN' FROM dbo.tickets t JOIN dbo.order_items i ON i.id=t.order_item_id JOIN dbo.orders o ON o.id=i.order_id
WHERE t.id=@ticket_id AND o.event_id=@event_id AND t.status=N'VALID';
IF @@ROWCOUNT=0 THROW 51000,N'Ticket cannot check in for this event.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_ticket_mark_refund_pending
@ticket_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
IF NOT EXISTS(SELECT 1 FROM dbo.refund_tickets rt JOIN dbo.refunds r ON r.id=rt.refund_id JOIN dbo.orders o ON o.id=r.order_id JOIN dbo.events e ON e.id=o.event_id
    WHERE rt.ticket_id=@ticket_id AND r.status NOT IN(N'REJECTED',N'COMPLETED') AND (e.status=N'CANCELLED' OR @now<e.start_time))
    THROW 51000,N'Ticket has no eligible refund request.',1;
UPDATE dbo.tickets SET status=N'REFUND_PENDING' WHERE id=@ticket_id AND status IN(N'VALID',N'REFUND_PENDING');
IF @@ROWCOUNT=0 THROW 51000,N'Ticket cannot become refund pending.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_ticket_restore_after_rejection
@ticket_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.refund_tickets rt JOIN dbo.refunds r ON r.id=rt.refund_id WHERE rt.ticket_id=@ticket_id AND r.status=N'REJECTED')
    OR EXISTS(SELECT 1 FROM dbo.refund_tickets rt JOIN dbo.refunds r ON r.id=rt.refund_id WHERE rt.ticket_id=@ticket_id AND r.status<>N'REJECTED')
    THROW 51000,N'Ticket has no rejected refund to restore.',1;
UPDATE dbo.tickets SET status=N'VALID' WHERE id=@ticket_id AND status IN(N'VALID',N'REFUND_PENDING');
IF @@ROWCOUNT=0 THROW 51000,N'Ticket cannot be restored.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_ticket_mark_refunded
@ticket_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.refund_tickets rt JOIN dbo.refunds r ON r.id=rt.refund_id WHERE rt.ticket_id=@ticket_id AND r.status=N'COMPLETED')
    THROW 51000,N'Ticket has no completed refund.',1;
UPDATE dbo.tickets SET status=N'REFUNDED' WHERE id=@ticket_id AND status IN(N'REFUND_PENDING',N'REFUNDED');
IF @@ROWCOUNT=0 THROW 51000,N'Ticket cannot be refunded.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_seat_hold
@item_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.ticket_hold_items i JOIN dbo.seats s ON s.id=i.seat_id WHERE i.id=@item_id AND i.inventory_state=N'HELD' AND s.status=N'HELD')
    THROW 51000,N'Item does not hold a seat. Reserve it with sp_ticket_hold_add_item.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_seat_release
@item_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.ticket_hold_items WITH(UPDLOCK,HOLDLOCK) WHERE id=@item_id AND seat_id IS NOT NULL AND inventory_state IN(N'HELD',N'RELEASED'))
    THROW 51000,N'Seat hold item cannot be released.',1;
UPDATE dbo.ticket_hold_items SET inventory_state=N'RELEASED' WHERE id=@item_id AND inventory_state=N'HELD';
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_seat_mark_sold
@item_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @hold BIGINT;
SELECT @hold=ticket_hold_id FROM dbo.ticket_hold_items WHERE id=@item_id AND seat_id IS NOT NULL;
IF @hold IS NULL THROW 51000,N'Seated hold item not found.',1;
EXEC dbo.sp_ticket_hold_consume @hold_id=@hold,@now=@now;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_seat_return_to_inventory
@ticket_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.tickets t JOIN dbo.order_items i ON i.id=t.order_item_id WHERE t.id=@ticket_id AND t.status=N'REFUNDED' AND i.seat_id IS NOT NULL)
    THROW 51000,N'Only refunded seated tickets return inventory.',1;
-- The ticket transition trigger already returned inventory. A retry must not
-- release a seat that has subsequently been reserved by another buyer.
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_zone_hold_standing
@zone_id BIGINT, @quantity INT, @hold_id BIGINT, @item_id BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.zones WHERE id=@zone_id AND type=N'STANDING') THROW 51000,N'Standing zone not found.',1;
EXEC dbo.sp_ticket_hold_add_item @hold_id=@hold_id,@zone_id=@zone_id,@seat_id=NULL,@quantity=@quantity,@item_id=@item_id OUTPUT;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_zone_release_held_standing
@zone_id BIGINT, @quantity INT, @item_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.ticket_hold_items WITH(UPDLOCK,HOLDLOCK) WHERE id=@item_id AND zone_id=@zone_id AND seat_id IS NULL AND quantity=@quantity AND inventory_state IN(N'HELD',N'RELEASED'))
    THROW 51000,N'Quantity must match one standing reservation.',1;
UPDATE dbo.ticket_hold_items SET inventory_state=N'RELEASED' WHERE id=@item_id AND inventory_state=N'HELD';
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_zone_sell_held_standing
@zone_id BIGINT, @quantity INT, @item_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @hold BIGINT;
SELECT @hold=ticket_hold_id FROM dbo.ticket_hold_items WHERE id=@item_id AND zone_id=@zone_id AND quantity=@quantity AND seat_id IS NULL;
IF @hold IS NULL THROW 51000,N'Standing reservation not found.',1;
EXEC dbo.sp_ticket_hold_consume @hold_id=@hold,@now=@now;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_zone_return_sold_standing
@zone_id BIGINT, @quantity INT, @refund_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF @quantity IS NULL OR @quantity<=0 OR @quantity<>(SELECT COUNT(*) FROM dbo.refund_tickets rt JOIN dbo.refunds r ON r.id=rt.refund_id
    JOIN dbo.tickets t ON t.id=rt.ticket_id JOIN dbo.order_items i ON i.id=t.order_item_id WHERE rt.refund_id=@refund_id AND r.status=N'COMPLETED' AND t.status=N'REFUNDED' AND i.zone_id=@zone_id AND i.seat_id IS NULL)
    THROW 51000,N'Quantity must match completed standing-ticket refunds.',1;
-- Inventory is returned once by tr_tickets_inventory, including callback retries.
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_settlement_create
@event_id BIGINT, @settlement_id BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dbo.events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id) THROW 51000,N'Event not found.',1;
SET @settlement_id=NULL;
SELECT @settlement_id=id FROM dbo.settlements WHERE event_id=@event_id;
IF @settlement_id IS NOT NULL GOTO ProcDone;
INSERT dbo.settlements(event_id,status,gross_revenue,total_refund,total_commission,paid_amount,pending_amount)
VALUES(@event_id,N'DRAFT',0,0,0,0,0);
SET @settlement_id=SCOPE_IDENTITY();
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_settlement_recalculate
@settlement_id BIGINT, @gross_revenue DECIMAL(19,2), @total_refund DECIMAL(19,2), @total_commission DECIMAL(19,2)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
UPDATE dbo.settlements SET gross_revenue=@gross_revenue,total_refund=@total_refund,total_commission=@total_commission WHERE id=@settlement_id AND status=N'DRAFT';
IF @@ROWCOUNT=0 THROW 51000,N'Only draft settlements can be recalculated.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_settlement_recalculate_from_event
@settlement_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
DECLARE @gross DECIMAL(19,2),@refund DECIMAL(19,2),@commission DECIMAL(19,2);
IF NOT EXISTS(SELECT 1 FROM dbo.settlements WITH(UPDLOCK,HOLDLOCK) WHERE id=@settlement_id AND status=N'DRAFT') THROW 51000,N'Settlement is not a draft.',1;
SELECT @gross=f.gross_revenue,@refund=f.total_refund,@commission=f.total_commission FROM dbo.v_event_financials f JOIN dbo.settlements s ON s.event_id=f.event_id WHERE s.id=@settlement_id;
EXEC dbo.sp_settlement_recalculate @settlement_id=@settlement_id,@gross_revenue=@gross,@total_refund=@refund,@total_commission=@commission;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_settlement_confirm
@settlement_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
DECLARE @event BIGINT;
SELECT @event=event_id FROM dbo.settlements WITH(UPDLOCK,HOLDLOCK) WHERE id=@settlement_id;
IF @event IS NULL THROW 51000,N'Settlement not found.',1;
IF EXISTS(SELECT 1 FROM dbo.settlements WHERE id=@settlement_id AND status=N'CONFIRMED') GOTO ProcDone;
IF NOT EXISTS(SELECT 1 FROM dbo.events WHERE id=@event AND (status=N'CANCELLED' OR end_time<=@now)) THROW 51000,N'Event has not finished.',1;
IF EXISTS(SELECT 1 FROM dbo.refunds r JOIN dbo.orders o ON o.id=r.order_id WHERE o.event_id=@event AND r.status NOT IN(N'COMPLETED',N'REJECTED'))
    OR EXISTS(SELECT 1 FROM dbo.payments p JOIN dbo.orders o ON o.id=p.order_id WHERE o.event_id=@event AND p.status IN(N'PENDING',N'UNKNOWN'))
    OR EXISTS(SELECT 1 FROM dbo.orders WHERE event_id=@event AND status=N'AWAITING_PAYMENT')
    THROW 51000,N'Payments, refunds or orders are unresolved.',1;
EXEC dbo.sp_settlement_recalculate_from_event @settlement_id=@settlement_id;
UPDATE dbo.settlements SET status=N'CONFIRMED' WHERE id=@settlement_id AND net_payable>=0;
IF @@ROWCOUNT=0 THROW 51000,N'Settlement has a negative payable balance.',1;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_settlement_begin_payout
@settlement_id BIGINT, @payout_id NVARCHAR(255), @amount DECIMAL(19,2)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF NULLIF(LTRIM(RTRIM(@payout_id)),N'') IS NULL OR @amount IS NULL OR @amount<=0 THROW 51000,N'Payout identifier and positive amount are required.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.settlements WITH(UPDLOCK,HOLDLOCK) WHERE id=@settlement_id AND status=N'CONFIRMED') THROW 51000,N'Settlement is not confirmed.',1;
IF EXISTS(SELECT 1 FROM dbo.settlement_payouts WITH(UPDLOCK,HOLDLOCK) WHERE payout_id=@payout_id) BEGIN
    IF EXISTS(SELECT 1 FROM dbo.settlement_payouts WHERE payout_id=@payout_id AND settlement_id=@settlement_id AND amount=@amount) GOTO ProcDone;
    THROW 51000,N'Payout identifier has conflicting terms.',1;
END;
IF NOT EXISTS(SELECT 1 FROM dbo.settlements WHERE id=@settlement_id AND available_to_pay>=@amount) THROW 51000,N'Payout exceeds available balance.',1;
INSERT dbo.settlement_payouts(payout_id,settlement_id,amount,status) VALUES(@payout_id,@settlement_id,@amount,N'PROCESSING');
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_settlement_record_payout_result
@settlement_id BIGINT, @payout_id NVARCHAR(255), @amount DECIMAL(19,2),
@result NVARCHAR(16), @reference NVARCHAR(255) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
IF @result IS NULL OR @result NOT IN(N'SUCCEEDED',N'FAILED',N'UNKNOWN') THROW 51000,N'Invalid payout result.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.settlements WITH(UPDLOCK,HOLDLOCK) WHERE id=@settlement_id) THROW 51000,N'Settlement not found.',1;
DECLARE @old NVARCHAR(16),@old_ref NVARCHAR(255);
SELECT @old=status,@old_ref=provider_reference FROM dbo.settlement_payouts WITH(UPDLOCK,HOLDLOCK) WHERE payout_id=@payout_id AND settlement_id=@settlement_id AND amount=@amount;
IF @old IS NULL THROW 51000,N'Payout identifier or amount does not match.',1;
IF @old IN(N'SUCCEEDED',N'FAILED') BEGIN
    IF @old=@result AND (@old_ref=@reference OR (@old_ref IS NULL AND @reference IS NULL)) GOTO ProcDone;
    THROW 51000,N'Conflicting terminal payout callback.',1;
END;
UPDATE dbo.settlement_payouts SET status=@result,provider_reference=@reference,
processed_at=CASE WHEN @result=N'UNKNOWN' THEN NULL ELSE SYSUTCDATETIME() END WHERE payout_id=@payout_id;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_event_cancel
@event_id BIGINT, @now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
DECLARE @status NVARCHAR(64);
SELECT @status=status FROM dbo.events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
IF @status=N'CANCELLED' GOTO ProcDone;
IF @status IS NULL OR NOT EXISTS(SELECT 1 FROM dbo.events WHERE id=@event_id AND start_time>@now) THROW 51000,N'Event cannot be cancelled after it starts.',1;
UPDATE dbo.events SET status=N'CANCELLED' WHERE id=@event_id;
DECLARE @order BIGINT,@hold BIGINT,@refund BIGINT,@ids NVARCHAR(MAX);
DECLARE order_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM dbo.orders WHERE event_id=@event_id AND status=N'AWAITING_PAYMENT' ORDER BY id;
OPEN order_cursor; FETCH NEXT FROM order_cursor INTO @order;
WHILE @@FETCH_STATUS=0 BEGIN
    EXEC dbo.sp_order_cancel @order_id=@order;
    FETCH NEXT FROM order_cursor INTO @order;
END;
CLOSE order_cursor; DEALLOCATE order_cursor;
DECLARE hold_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM dbo.ticket_holds WHERE event_id=@event_id AND status=N'ACTIVE' ORDER BY id;
OPEN hold_cursor; FETCH NEXT FROM hold_cursor INTO @hold;
WHILE @@FETCH_STATUS=0 BEGIN
    EXEC dbo.sp_ticket_hold_release @hold_id=@hold;
    FETCH NEXT FROM hold_cursor INTO @hold;
END;
CLOSE hold_cursor; DEALLOCATE hold_cursor;
DECLARE refund_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT r.id FROM dbo.refunds r JOIN dbo.orders o ON o.id=r.order_id
WHERE o.event_id=@event_id AND r.purpose=N'TICKET_REFUND' AND r.status NOT IN(N'REJECTED',N'COMPLETED') ORDER BY r.id;
OPEN refund_cursor; FETCH NEXT FROM refund_cursor INTO @refund;
WHILE @@FETCH_STATUS=0 BEGIN
    EXEC dbo.sp_refund_adopt_event_cancellation @refund_id=@refund;
    IF EXISTS(SELECT 1 FROM dbo.refunds WHERE id=@refund AND status=N'APPROVED' AND amount=0) EXEC dbo.sp_refund_complete_without_transfer @refund_id=@refund;
    FETCH NEXT FROM refund_cursor INTO @refund;
END;
CLOSE refund_cursor; DEALLOCATE refund_cursor;
DECLARE paid_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM dbo.orders WHERE event_id=@event_id AND status=N'PAID' ORDER BY id;
OPEN paid_cursor; FETCH NEXT FROM paid_cursor INTO @order;
WHILE @@FETCH_STATUS=0 BEGIN
    SELECT @ids=N'['+STRING_AGG(CONVERT(NVARCHAR(MAX),t.id),N',')+N']' FROM dbo.tickets t JOIN dbo.order_items i ON i.id=t.order_item_id
    WHERE i.order_id=@order AND t.status=N'VALID';
    IF @ids IS NOT NULL BEGIN
        EXEC dbo.sp_refund_request_for_tickets @order_id=@order,@ticket_ids=@ids,@reason_type=N'EVENT_CANCELLED',@reason=N'Event cancelled.',@refund_id=@refund OUTPUT,@now=@now;
        EXEC dbo.sp_refund_approve @refund_id=@refund;
        IF EXISTS(SELECT 1 FROM dbo.refunds WHERE id=@refund AND amount=0) EXEC dbo.sp_refund_complete_without_transfer @refund_id=@refund;
    END;
    FETCH NEXT FROM paid_cursor INTO @order;
END;
CLOSE paid_cursor; DEALLOCATE paid_cursor;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_release_expired_holds
@now DATETIME2(6) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
SET @now=COALESCE(@now,SYSUTCDATETIME());
DECLARE @hold BIGINT,@order BIGINT;
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT id FROM dbo.ticket_holds WHERE status=N'ACTIVE' AND expires_at<=@now ORDER BY id;
OPEN c; FETCH NEXT FROM c INTO @hold;
WHILE @@FETCH_STATUS=0 BEGIN
    SET @order=NULL; SELECT @order=id FROM dbo.orders WHERE hold_id=@hold AND status=N'AWAITING_PAYMENT';
    IF @order IS NOT NULL EXEC dbo.sp_order_expire @order_id=@order,@now=@now;
    ELSE EXEC dbo.sp_ticket_hold_release @hold_id=@hold;
    FETCH NEXT FROM c INTO @hold;
END;
CLOSE c; DEALLOCATE c;
ProcDone:
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO
