/*
 * 006_create_procedures
 *
 * Creates or alters all 24 stored procedures.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* Stored Procedure: usp_ApplyOrderCoupon */
CREATE OR ALTER PROCEDURE dbo.usp_ApplyOrderCoupon
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @coupon_code varchar(80) = NULL
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp03;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @organization_id uniqueidentifier, @hold_id uniqueidentifier,
                @subtotal decimal(19,0), @old_coupon uniqueidentifier, @new_coupon uniqueidentifier,
                @discount decimal(19,0) = 0, @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id = o.event_id, @hold_id = o.hold_id, @subtotal = o.subtotal_amount, @old_coupon = o.coupon_id
        FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_ticket_holds h ON h.id = o.hold_id
        WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = 'PENDING_PAYMENT' AND h.status = 'ACTIVE' AND h.expires_at > @now;
        IF @event_id IS NULL THROW 51230, 'Order is not coupon-eligible', 1;
        SELECT @organization_id = organization_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id AND status = 'PUBLISHED';
        IF @organization_id IS NULL THROW 51231, 'Event is unavailable', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_payments WHERE order_id = @order_id AND status IN ('PENDING', 'UNKNOWN'))
            THROW 51232, 'Coupon cannot change while payment is unresolved', 1;
        IF @coupon_code IS NOT NULL
            SELECT @new_coupon = id FROM dbo.tc_coupons WITH (UPDLOCK, HOLDLOCK)
            WHERE organization_id = @organization_id AND code = @coupon_code;
        IF @coupon_code IS NOT NULL AND @new_coupon IS NULL THROW 51233, 'Coupon not found', 1;
        IF @old_coupon = @new_coupon OR (@old_coupon IS NULL AND @new_coupon IS NULL) BEGIN
            SELECT subtotal_amount, discount_amount, total_amount, coupon_id FROM dbo.tc_orders WHERE id = @order_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @new_coupon IS NOT NULL BEGIN
            IF NOT EXISTS (SELECT 1 FROM dbo.fn_GetCouponEligibility(@new_coupon, @organization_id, @subtotal, @now) WHERE is_eligible = 1)
                THROW 51234, 'Coupon is not eligible', 1;
            SELECT @discount = dbo.fn_CalculateCouponDiscount(@subtotal, discount_type, percentage_value, fixed_amount)
            FROM dbo.tc_coupons WHERE id = @new_coupon;
            IF @discount IS NULL THROW 51235, 'Coupon discount is invalid', 1;
        END;
        IF EXISTS (SELECT 1 FROM dbo.tc_coupon_redemptions WHERE order_id = @order_id)
            UPDATE dbo.tc_coupon_redemptions
            SET coupon_id = COALESCE(@new_coupon, coupon_id),
                status = CASE WHEN @new_coupon IS NULL THEN 'RELEASED' ELSE 'RESERVED' END,
                reserved_at = CASE WHEN @new_coupon IS NULL THEN reserved_at ELSE @now END,
                expires_at = CASE WHEN @new_coupon IS NULL THEN expires_at ELSE (SELECT expires_at FROM dbo.tc_ticket_holds WHERE id = @hold_id) END,
                consumed_at = NULL, released_at = CASE WHEN @new_coupon IS NULL THEN @now ELSE NULL END
            WHERE order_id = @order_id;
        ELSE IF @new_coupon IS NOT NULL
            INSERT dbo.tc_coupon_redemptions(coupon_id, order_id, expires_at)
            VALUES (@new_coupon, @order_id, (SELECT expires_at FROM dbo.tc_ticket_holds WHERE id = @hold_id));
        UPDATE dbo.tc_orders SET coupon_id = @new_coupon, discount_amount = @discount,
               total_amount = @subtotal - @discount, version = version + 1 WHERE id = @order_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT subtotal_amount, discount_amount, total_amount, coupon_id FROM dbo.tc_orders WHERE id = @order_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp03;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_ApplyPaymentResult */
CREATE OR ALTER PROCEDURE dbo.usp_ApplyPaymentResult
    @order_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @payment_id uniqueidentifier = NULL,
    @verified_result nvarchar(max), @ticket_codes nvarchar(max) = NULL, @retry_failed_compensation bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp09;
    BEGIN TRY
        IF ISJSON(@verified_result) <> 1 THROW 51390, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @event_id uniqueidentifier, @hold_id uniqueidentifier, @owner_id uniqueidentifier,
                @order_status varchar(30), @hold_status varchar(20), @expires datetime2(3),
                @event_status varchar(30), @total decimal(19,0), @payment_status varchar(20),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result NOT IN ('CAPTURED', 'FAILED', 'UNKNOWN') THROW 51391, 'Unsupported payment result', 1;
        SELECT @event_id = event_id, @hold_id = hold_id, @owner_id = user_id, @order_status = status, @total = total_amount
        FROM dbo.tc_orders WHERE id = @order_id;
        IF @event_id IS NULL THROW 51392, 'Order not found', 1;
        SELECT @event_status = status FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @hold_status = status, @expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;
        SELECT 1 FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        IF @payment_id IS NULL BEGIN
            IF @actor_id IS NULL OR @actor_id <> @owner_id OR @total <> 0 OR @result <> 'CAPTURED'
                THROW 51393, 'Only the owner may complete a zero-value order', 1;
        END ELSE BEGIN
            IF IS_ROLEMEMBER('tc_buyer') = 1 AND IS_ROLEMEMBER('tc_worker') <> 1
                THROW 51389, 'Buyer principal cannot apply a paid provider result', 1;
            SELECT @payment_status = status FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK)
            WHERE id = @payment_id AND order_id = @order_id AND amount = @total;
            IF @payment_status IS NULL THROW 51394, 'Payment does not match order amount', 1;
            IF @payment_status = 'CAPTURED' AND @order_status = 'PAID' BEGIN
                SELECT @order_status order_status, @payment_status payment_status;
                IF @started = 1 COMMIT TRANSACTION; RETURN;
            END;
            IF @payment_status = 'CAPTURED' AND @result <> 'CAPTURED' BEGIN
                SELECT @order_status order_status, @payment_status payment_status;
                IF @started = 1 COMMIT TRANSACTION; RETURN;
            END;
            UPDATE dbo.tc_payments SET status = @result,
                   provider_reference = COALESCE(@provider_reference, provider_reference),
                   captured_at = CASE WHEN @result = 'CAPTURED' THEN COALESCE(captured_at, @now) ELSE captured_at END,
                   version = version + 1 WHERE id = @payment_id;
        END;
        IF @result <> 'CAPTURED' BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_status order_status, @result payment_status; RETURN;
        END;
        IF @order_status = 'PAID' BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_status order_status, @result payment_status; RETURN;
        END;
        IF @event_status <> 'PUBLISHED' OR @hold_status <> 'ACTIVE' OR @expires <= @now BEGIN
            IF @payment_id IS NULL THROW 51395, 'Zero-value order expired before completion', 1;
            IF NOT EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE payment_id = @payment_id AND purpose = 'PAYMENT_COMPENSATION' AND status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED'))
            BEGIN
                IF @retry_failed_compensation = 0 AND EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE payment_id = @payment_id AND purpose = 'PAYMENT_COMPENSATION' AND status = 'FAILED')
                    THROW 51396, 'Compensation retry requires explicit authorization', 1;
                INSERT dbo.tc_refunds(payment_id, purpose, amount) VALUES (@payment_id, 'PAYMENT_COMPENSATION', @total);
            END;
            IF @started = 1 COMMIT TRANSACTION;
            SELECT CAST('COMPENSATION_PENDING' AS varchar(30)) order_status, CAST('CAPTURED' AS varchar(20)) payment_status; RETURN;
        END;
        IF ISJSON(@ticket_codes) <> 1 THROW 51397, 'Ticket codes must be a JSON array', 1;
        DECLARE @issued TABLE(ordinal int PRIMARY KEY, ticket_code varchar(100), qr_hash varbinary(64));
        INSERT @issued
        SELECT CONVERT(int, [key]) + 1, JSON_VALUE(value, '$.ticketCode'),
               TRY_CONVERT(varbinary(64), JSON_VALUE(value, '$.qrSecretHashHex'), 2)
        FROM OPENJSON(@ticket_codes);
        DECLARE @expected int = (SELECT SUM(quantity) FROM dbo.tc_order_items WHERE order_id = @order_id);
        IF @expected IS NULL OR @expected <> (SELECT COUNT(*) FROM @issued)
           OR EXISTS (SELECT 1 FROM @issued WHERE ticket_code IS NULL OR qr_hash IS NULL)
           OR EXISTS (SELECT ticket_code FROM @issued GROUP BY ticket_code HAVING COUNT(*) > 1)
            THROW 51398, 'Ticket identities do not match order quantity', 1;
        SELECT z.id FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.zone_id = z.id) ORDER BY z.id;
        SELECT s.id FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.seat_id = s.id) ORDER BY s.id;
        IF EXISTS (SELECT 1 FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
                   WHERE oi.order_id = @order_id AND s.status <> 'HELD')
            THROW 51388, 'Held seat inventory is inconsistent', 1;
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (
                SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_order_items
                WHERE order_id = @order_id AND seat_id IS NULL GROUP BY zone_id
            ) q ON q.zone_id = z.id WHERE z.held_quantity < q.quantity
        ) THROW 51387, 'Held standing inventory is inconsistent', 1;
        UPDATE s SET status = 'SOLD', version = s.version + 1 FROM dbo.tc_seats s
        JOIN dbo.tc_order_items oi ON oi.seat_id = s.id WHERE oi.order_id = @order_id AND s.status = 'HELD';
        UPDATE z SET held_quantity = held_quantity - q.quantity, sold_quantity = sold_quantity + q.quantity, version = z.version + 1
        FROM dbo.tc_zones z JOIN (SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_order_items
                                  WHERE order_id = @order_id AND seat_id IS NULL GROUP BY zone_id) q ON q.zone_id = z.id;
        UPDATE dbo.tc_ticket_holds SET status = 'CONSUMED', version = version + 1 WHERE id = @hold_id AND status = 'ACTIVE';
        UPDATE dbo.tc_coupon_redemptions SET status = 'CONSUMED', consumed_at = @now WHERE order_id = @order_id AND status = 'RESERVED';
        UPDATE dbo.tc_orders SET status = 'PAID', paid_at = @now, version = version + 1 WHERE id = @order_id;
        WITH allocations AS (
            SELECT *, ROW_NUMBER() OVER (ORDER BY order_item_id, ticket_ordinal) ordinal
            FROM dbo.fn_AllocateTicketPaidAmounts(@order_id)
        )
        INSERT dbo.tc_tickets(order_item_id, ticket_code, qr_secret_hash, paid_amount)
        SELECT a.order_item_id, i.ticket_code, i.qr_hash, a.paid_amount FROM allocations a JOIN @issued i ON i.ordinal = a.ordinal;
        IF (SELECT SUM(paid_amount) FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE oi.order_id = @order_id) <> @total
            THROW 51399, 'Ticket paid amount allocation mismatch', 1;
        INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('ORDER_PAID', 'ORDER', @order_id, CONCAT(N'{"orderId":"', @order_id, N'"}'), CONCAT('order-paid:', @order_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT CAST('PAID' AS varchar(30)) order_status, @result payment_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp09;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_ApplyRefundResult */
CREATE OR ALTER PROCEDURE dbo.usp_ApplyRefundResult @refund_id uniqueidentifier, @verified_result nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp11;
    BEGIN TRY
        IF COALESCE(IS_ROLEMEMBER('tc_worker'), 0) <> 1 AND COALESCE(IS_ROLEMEMBER('db_owner'), 0) <> 1
            THROW 51409, 'Worker principal required', 1;
        IF ISJSON(@verified_result) <> 1 THROW 51410, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @request_id uniqueidentifier, @payment_id uniqueidentifier, @purpose varchar(30),
                @amount decimal(19,0), @current varchar(20), @order_id uniqueidentifier,
                @event_id uniqueidentifier, @payment_amount decimal(19,0),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result NOT IN ('SUCCEEDED', 'FAILED', 'UNKNOWN') THROW 51411, 'Unsupported refund result', 1;
        IF @provider_reference IS NULL OR LEN(@provider_reference) = 0 THROW 51415, 'Provider reference is required', 1;

        SELECT @payment_id = payment_id FROM dbo.tc_refunds WHERE id = @refund_id;
        IF @payment_id IS NULL THROW 51412, 'Refund not found', 1;
        SELECT @order_id = order_id FROM dbo.tc_payments WHERE id = @payment_id;
        SELECT @event_id = event_id FROM dbo.tc_orders WHERE id = @order_id;
        SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT 1 FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        SELECT @payment_amount = amount FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK) WHERE id = @payment_id;
        SELECT id FROM dbo.tc_refunds WITH (UPDLOCK, HOLDLOCK) WHERE payment_id = @payment_id ORDER BY id;
        SELECT @request_id = refund_request_id, @purpose = purpose, @amount = amount, @current = status
        FROM dbo.tc_refunds WHERE id = @refund_id AND payment_id = @payment_id;
        IF @current IS NULL THROW 51412, 'Refund not found', 1;
        IF @request_id IS NOT NULL
            SELECT 1 FROM dbo.tc_refund_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;

        IF @current = 'SUCCEEDED' BEGIN
            SELECT @refund_id refund_id, @current refund_status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @current = 'FAILED' AND @result <> 'FAILED'
            THROW 51414, 'Failed refund is terminal; create an explicitly authorized retry', 1;
        IF @result = 'SUCCEEDED' AND
           (SELECT COALESCE(SUM(amount), 0) FROM dbo.tc_refunds WHERE payment_id = @payment_id AND status = 'SUCCEEDED') + @amount > @payment_amount
            THROW 51413, 'Successful refunds exceed captured payment', 1;

        IF @result = 'SUCCEEDED' AND @purpose = 'CUSTOMER_REFUND' BEGIN
            SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id ORDER BY t.id;
            SELECT z.id FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.zone_id = z.id) ORDER BY z.id;
            SELECT s.id FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.seat_id = s.id) ORDER BY s.id;
            IF EXISTS (
                SELECT 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt
                    JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                    WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL AND t.status = 'REFUND_PENDING'
                    GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id WHERE z.sold_quantity < q.quantity)
                THROW 51416, 'Sold standing inventory is inconsistent', 1;
        END;

        UPDATE dbo.tc_refunds SET status = @result, provider_reference = @provider_reference,
               processed_at = CASE WHEN @result IN ('SUCCEEDED', 'FAILED') THEN @now ELSE processed_at END,
               version = version + 1 WHERE id = @refund_id;

        IF @result = 'SUCCEEDED' AND @purpose = 'CUSTOMER_REFUND' BEGIN
            UPDATE s SET status = 'AVAILABLE', version = s.version + 1
            FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
            JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id AND t.status = 'REFUND_PENDING' AND s.status = 'SOLD';
            UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1
            FROM dbo.tc_zones z JOIN (
                SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt
                JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL AND t.status = 'REFUND_PENDING'
                GROUP BY oi.zone_id
            ) q ON q.zone_id = z.id;
            UPDATE t SET status = 'REFUNDED', version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id AND t.status = 'REFUND_PENDING';
            UPDATE dbo.tc_refund_request_tickets SET is_open = 0 WHERE refund_request_id = @request_id;
            UPDATE dbo.tc_refund_requests SET status = 'COMPLETED', version = version + 1 WHERE id = @request_id;
        END;

        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        VALUES (CONCAT('REFUND_', @result), 'REFUND', @refund_id, @provider_reference);
        IF @result = 'SUCCEEDED' INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('REFUND_SUCCEEDED', 'REFUND', @refund_id,
                CONCAT(N'{"refundId":"', @refund_id, N'"}'), CONCAT('refund-succeeded:', @refund_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @refund_id refund_id, @result refund_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp11;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_ApproveOrganization */
/* Canonical names delegate to the proven legacy transaction paths. */
CREATE OR ALTER PROCEDURE dbo.usp_ApproveOrganization
    @organization_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @initial_commission_policy nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM dbo.tc_organization_requests WHERE id = @organization_id)
    BEGIN
        EXEC dbo.usp_ApproveOrganizationRequest
            @request_id = @organization_id,
            @actor_id = @actor_id,
            @initial_commission_policy = @initial_commission_policy;
        RETURN;
    END;

    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 52610, 'Administrator role required', 1;

        DECLARE @status varchar(30), @requester_id uniqueidentifier,
                @rate decimal(7,4), @fixed decimal(19,0),
                @effective_from datetime2(3), @effective_to datetime2(3);
        SELECT @status = status, @requester_id = requester_id
        FROM dbo.tc_organizations WITH (UPDLOCK, HOLDLOCK) WHERE id = @organization_id;
        IF @status IS NULL THROW 52611, 'Organization not found', 1;
        IF @status = 'APPROVED' BEGIN COMMIT; SELECT @organization_id organization_id, @status status; RETURN; END;
        IF @status <> 'PENDING_APPROVAL' THROW 52612, 'Organization is not pending approval', 1;

        SELECT @rate = TRY_CONVERT(decimal(7,4), JSON_VALUE(@initial_commission_policy, '$.ratePercent')),
               @fixed = TRY_CONVERT(decimal(19,0), JSON_VALUE(@initial_commission_policy, '$.fixedFee')),
               @effective_from = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveFrom')),
               @effective_to = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveTo'));
        IF ISJSON(@initial_commission_policy) <> 1 OR @rate IS NULL OR @fixed IS NULL
           OR @effective_from IS NULL OR @effective_to IS NULL
            THROW 52613, 'Invalid initial commission policy', 1;

        INSERT dbo.tc_commission_rules(organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES (@organization_id, @rate, @fixed, @effective_from, @effective_to);
        IF @requester_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships
            WHERE user_id = @requester_id AND organization_id = @organization_id
        )
            INSERT dbo.tc_organization_memberships(user_id, organization_id, role)
            VALUES (@requester_id, @organization_id, 'MANAGER');
        UPDATE dbo.tc_organizations
        SET status = 'APPROVED', decided_at = SYSUTCDATETIME(), updated_at = SYSUTCDATETIME(), rejection_reason = NULL
        WHERE id = @organization_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'ORGANIZATION_APPROVED', 'ORGANIZATION', @organization_id);
        COMMIT;
        SELECT @organization_id organization_id, CAST('APPROVED' AS varchar(30)) status;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK;
        THROW;
    END CATCH;
END;

GO

/* Stored Procedure: usp_ApproveOrganizationRequest */
CREATE OR ALTER PROCEDURE dbo.usp_ApproveOrganizationRequest
    @request_id uniqueidentifier, @actor_id uniqueidentifier, @initial_commission_policy nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp01;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51201, 'Administrator role required', 1;
        DECLARE @status varchar(30), @organization_id uniqueidentifier, @applicant_id uniqueidentifier,
                @name nvarchar(200), @email nvarchar(320), @phone nvarchar(30), @description nvarchar(2000);
        SELECT @status = status, @organization_id = organization_id, @applicant_id = applicant_id,
               @name = name, @email = contact_email, @phone = contact_phone, @description = description
        FROM dbo.tc_organization_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;
        IF @status IS NULL THROW 51202, 'Organization request not found', 1;
        IF @status = 'APPROVED' BEGIN
            SELECT @organization_id organization_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> 'PENDING' THROW 51203, 'Organization request is already rejected', 1;
        DECLARE @rate decimal(7,4) = TRY_CONVERT(decimal(7,4), JSON_VALUE(@initial_commission_policy, '$.ratePercent')),
                @fixed decimal(19,0) = TRY_CONVERT(decimal(19,0), JSON_VALUE(@initial_commission_policy, '$.fixedFee')),
                @from datetime2(3) = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveFrom')),
                @to datetime2(3) = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, '$.effectiveTo'));
        IF ISJSON(@initial_commission_policy) <> 1 OR @rate IS NULL OR @fixed IS NULL OR @from IS NULL OR @to IS NULL
            THROW 51204, 'Invalid initial commission policy', 1;
        SET @organization_id = NEWID();
        INSERT dbo.tc_organizations(id, name, contact_email, contact_phone, description)
        VALUES (@organization_id, @name, @email, @phone, @description);
        INSERT dbo.tc_commission_rules(organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES (@organization_id, @rate, @fixed, @from, @to);
        INSERT dbo.tc_organization_memberships(user_id, organization_id, role) VALUES (@applicant_id, @organization_id, 'MANAGER');
        UPDATE dbo.tc_organization_requests SET reviewer_id = @actor_id, organization_id = @organization_id,
               status = 'APPROVED', decided_at = SYSUTCDATETIME(), rejection_reason = NULL, version = version + 1
        WHERE id = @request_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'ORGANIZATION_REQUEST_APPROVED', 'ORGANIZATION_REQUEST', @request_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @organization_id organization_id, CAST('APPROVED' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp01;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_BeginOrderPayment */
CREATE OR ALTER PROCEDURE dbo.usp_BeginOrderPayment @order_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp08;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @hold_id uniqueidentifier, @amount decimal(19,0), @payment_id uniqueidentifier,
                @txn_ref varchar(100), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id = o.event_id, @hold_id = o.hold_id, @amount = o.total_amount
        FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_ticket_holds h WITH (UPDLOCK, HOLDLOCK) ON h.id = o.hold_id
        WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = 'PENDING_PAYMENT' AND h.status = 'ACTIVE' AND h.expires_at > @now;
        IF @event_id IS NULL THROW 51280, 'Order cannot begin payment', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = 'PUBLISHED' AND @now < sale_end)
            THROW 51281, 'Event is unavailable for payment', 1;
        IF @amount = 0 THROW 51282, 'Zero-value order must use the free completion flow', 1;
        SELECT TOP (1) @payment_id = id, @txn_ref = txn_ref FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK)
        WHERE order_id = @order_id AND status IN ('PENDING', 'UNKNOWN') ORDER BY created_at DESC;
        IF @payment_id IS NULL BEGIN
            SET @payment_id = NEWID(); SET @txn_ref = CONCAT('PAY-', REPLACE(CONVERT(varchar(36), @payment_id), '-', ''));
            INSERT dbo.tc_payments(id, order_id, txn_ref, amount) VALUES (@payment_id, @order_id, @txn_ref, @amount);
        END;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @payment_id payment_id, @txn_ref txn_ref, @amount amount, CAST('VND' AS char(3)) currency;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp08;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_CalculateEventSettlement */
CREATE OR ALTER PROCEDURE dbo.usp_CalculateEventSettlement @event_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp14;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51540, 'Administrator role required', 1;
        DECLARE @rule_id uniqueidentifier, @rate decimal(7,4), @fixed decimal(19,0), @settlement_id uniqueidentifier,
                @settlement_status varchar(20), @gross decimal(19,0), @refund decimal(19,0), @commission decimal(19,0), @net decimal(19,0);
        SELECT @rule_id = commission_rule_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @rule_id IS NULL THROW 51541, 'Event has no applied commission rule', 1;
        SELECT @rate = rate_percent, @fixed = fixed_fee FROM dbo.tc_commission_rules WHERE id = @rule_id;
        SELECT o.id FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK) WHERE o.event_id = @event_id ORDER BY o.id;
        SELECT @settlement_id = id, @settlement_status = status FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id;
        IF @settlement_status IN ('CONFIRMED', 'PAID') THROW 51542, 'Confirmed settlement cannot be recalculated', 1;
        DECLARE @items TABLE(order_id uniqueidentifier PRIMARY KEY, gross decimal(19,0), refund decimal(19,0), commission decimal(19,0), net decimal(19,0));
        INSERT @items
        SELECT order_id, total_amount, ticket_refund_amount,
               dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed),
               remaining_ticket_amount - dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed)
        FROM dbo.vw_OrderFinancialSummary WHERE event_id = @event_id AND status = 'PAID';
        SELECT @gross = COALESCE(SUM(gross), 0), @refund = COALESCE(SUM(refund), 0),
               @commission = COALESCE(SUM(commission), 0), @net = COALESCE(SUM(net), 0) FROM @items;
        IF NOT EXISTS (SELECT 1 FROM @items) AND @settlement_id IS NULL BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT CAST(NULL AS uniqueidentifier) settlement_id, @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable; RETURN;
        END;
        IF @settlement_id IS NULL BEGIN SET @settlement_id = NEWID(); INSERT dbo.tc_settlements(id, event_id) VALUES (@settlement_id, @event_id); END;
        DELETE FROM dbo.tc_settlement_items WHERE settlement_id = @settlement_id;
        INSERT dbo.tc_settlement_items(settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
        SELECT @settlement_id, order_id, gross, refund, commission, net FROM @items;
        UPDATE dbo.tc_settlements SET gross_revenue = @gross, total_refund = @refund,
               total_commission = @commission, net_payable = @net, version = version + 1 WHERE id = @settlement_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @settlement_id settlement_id, @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp14;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_CancelEvent */
CREATE OR ALTER PROCEDURE dbo.usp_CancelEvent @event_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp13;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51530, 'Administrator role required', 1;
        DECLARE @status varchar(30), @start datetime2(3);
        SELECT @status = status, @start = start_time
        FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @status IS NULL THROW 51531, 'Event not found', 1;
        IF @status = 'CANCELLED' BEGIN
            SELECT @event_id event_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> 'PUBLISHED' OR SYSUTCDATETIME() >= @start
            THROW 51532, 'Only a future published event can be cancelled', 1;
        UPDATE dbo.tc_events SET status = 'CANCELLED', version = version + 1 WHERE id = @event_id;
        INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES ('EVENT_CANCELLED', 'EVENT', @event_id,
                CONCAT(N'{"eventId":"', @event_id, N'"}'), CONCAT('event-cancelled:', @event_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @event_id event_id, CAST('CANCELLED' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp13;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_CheckInTicket */
CREATE OR ALTER PROCEDURE dbo.usp_CheckInTicket
    @event_id uniqueidentifier, @actor_id uniqueidentifier, @ticket_code varchar(100)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp04;
    BEGIN TRY
        DECLARE @organization_id uniqueidentifier, @ticket_id uniqueidentifier, @ticket_event uniqueidentifier,
                @ticket_status varchar(30), @result varchar(30), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @organization_id = organization_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @organization_id IS NULL THROW 51240, 'Event not found', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_organization_memberships WHERE user_id = @actor_id
                       AND organization_id = @organization_id AND active = 1 AND role IN ('MANAGER', 'CHECK_IN_STAFF'))
            THROW 51241, 'Active event staff membership required', 1;
        SELECT @ticket_id = t.id, @ticket_status = t.status, @ticket_event = o.event_id
        FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        JOIN dbo.tc_orders o ON o.id = oi.order_id WHERE t.ticket_code = @ticket_code;
        IF @ticket_id IS NULL SET @result = 'NOT_FOUND';
        ELSE IF @ticket_event <> @event_id SET @result = 'WRONG_EVENT';
        ELSE IF EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND status = 'CANCELLED') SET @result = 'EVENT_CANCELLED';
        ELSE IF @now < (SELECT opens_at FROM dbo.fn_GetEventCheckInWindow(@event_id, @now)) SET @result = 'TOO_EARLY';
        ELSE IF @now >= (SELECT closes_at FROM dbo.fn_GetEventCheckInWindow(@event_id, @now)) SET @result = 'TOO_LATE';
        ELSE IF @ticket_status = 'USED' SET @result = 'ALREADY_USED';
        ELSE IF @ticket_status <> 'ACTIVE' SET @result = 'NOT_ACTIVE';
        ELSE BEGIN UPDATE dbo.tc_tickets SET status = 'USED', version = version + 1 WHERE id = @ticket_id; SET @result = 'SUCCESS'; END;
        INSERT dbo.tc_check_ins(event_id, ticket_id, actor_id, result, scanned_at)
        VALUES (@event_id, CASE WHEN @result = 'NOT_FOUND' THEN NULL ELSE @ticket_id END, @actor_id, @result, @now);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @result result, @now scanned_at, @ticket_id ticket_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp04;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_ConfirmEventSettlement */
CREATE OR ALTER PROCEDURE dbo.usp_ConfirmEventSettlement @settlement_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp15;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51550, 'Administrator role required', 1;
        DECLARE @event_id uniqueidentifier, @status varchar(20), @net decimal(19,0);
        SELECT @event_id = event_id, @status = status, @net = net_payable FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK) WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51551, 'Settlement not found', 1;
        IF @status IN ('CONFIRMED', 'PAID') BEGIN
            SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT id FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id ORDER BY id;
        IF EXISTS (SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event_id, SYSUTCDATETIME()))
            THROW 51552, 'Settlement has unresolved blockers', 1;
        EXEC dbo.usp_CalculateEventSettlement @event_id, @actor_id;
        SELECT @net = net_payable FROM dbo.tc_settlements WHERE id = @settlement_id;
        UPDATE dbo.tc_settlements SET status = CASE WHEN @net = 0 THEN 'PAID' ELSE 'CONFIRMED' END,
               confirmed_at = SYSUTCDATETIME(), version = version + 1 WHERE id = @settlement_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'SETTLEMENT_CONFIRMED', 'SETTLEMENT', @settlement_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp15;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_ConfirmSettlement */
CREATE OR ALTER PROCEDURE dbo.usp_ConfirmSettlement
    @settlement_id uniqueidentifier,
    @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp15;
    BEGIN TRY
        IF @settlement_id IS NULL OR @actor_id IS NULL THROW 51710, 'Settlement and actor are required', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51711, 'Administrator role required', 1;

        DECLARE @event_id uniqueidentifier, @status varchar(20), @net decimal(19,0), @lock_count bigint;
        SELECT @event_id = event_id FROM dbo.tc_settlements WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51712, 'Settlement not found', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refund_requests rr WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = rr.order_id WHERE o.event_id = @event_id;
        SELECT @status = status, @net = net_payable
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @settlement_id AND event_id = @event_id;
        IF @status IN ('CONFIRMED', 'PAID') BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
            RETURN;
        END;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_payments p WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refunds r WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        JOIN dbo.tc_orders o ON o.id = oi.order_id WHERE o.event_id = @event_id;

        IF EXISTS (SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event_id, SYSUTCDATETIME()))
            THROW 51713, 'Settlement has unresolved blockers', 1;
        EXEC dbo.usp_RecalculateSettlement @event_id, @actor_id, 0;
        SELECT @net = net_payable FROM dbo.tc_settlements WHERE id = @settlement_id;
        UPDATE dbo.tc_settlements
        SET status = CASE WHEN @net = 0 THEN 'PAID' ELSE 'CONFIRMED' END,
            confirmed_at = SYSUTCDATETIME(), version = version + 1
        WHERE id = @settlement_id AND status = 'DRAFT';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'SETTLEMENT_CONFIRMED', 'SETTLEMENT', @settlement_id);

        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp15;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_CreateOrderFromHold */
CREATE OR ALTER PROCEDURE dbo.usp_CreateOrderFromHold @hold_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp06;
    BEGIN TRY
        DECLARE @order_id uniqueidentifier, @event_id uniqueidentifier, @subtotal decimal(19,0), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @order_id = id FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE hold_id = @hold_id;
        IF @order_id IS NOT NULL BEGIN
            SELECT id order_id, order_code, subtotal_amount, discount_amount, total_amount FROM dbo.tc_orders WHERE id = @order_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT @event_id = event_id FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @hold_id AND user_id = @actor_id AND status = 'ACTIVE' AND expires_at > @now;
        IF @event_id IS NULL THROW 51260, 'Active unexpired hold owned by actor required', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id AND status = 'PUBLISHED')
            THROW 51261, 'Event is unavailable', 1;
        SELECT @subtotal = SUM(unit_price * quantity) FROM dbo.tc_ticket_hold_items WHERE hold_id = @hold_id;
        IF @subtotal IS NULL THROW 51262, 'Hold has no items', 1;
        SET @order_id = NEWID();
        INSERT dbo.tc_orders(id, user_id, event_id, hold_id, order_code, subtotal_amount, discount_amount, total_amount)
        VALUES (@order_id, @actor_id, @event_id, @hold_id, CONCAT('ORD-', REPLACE(CONVERT(varchar(36), @order_id), '-', '')), @subtotal, 0, @subtotal);
        INSERT dbo.tc_order_items(order_id, zone_id, seat_id, quantity, unit_price, zone_name_snapshot, seat_label_snapshot)
        SELECT @order_id, hi.zone_id, hi.seat_id, hi.quantity, hi.unit_price, z.name, s.label
        FROM dbo.tc_ticket_hold_items hi JOIN dbo.tc_zones z ON z.id = hi.zone_id LEFT JOIN dbo.tc_seats s ON s.id = hi.seat_id
        WHERE hi.hold_id = @hold_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT id order_id, order_code, subtotal_amount, discount_amount, total_amount FROM dbo.tc_orders WHERE id = @order_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp06;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_CreateRefundRequest */
CREATE OR ALTER PROCEDURE dbo.usp_CreateRefundRequest
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @ticket_ids nvarchar(max), @reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp05;
    BEGIN TRY
        IF ISJSON(@ticket_ids) <> 1 OR NULLIF(LTRIM(RTRIM(@reason)), N'') IS NULL THROW 51250, 'Ticket list and reason are required', 1;
        DECLARE @tickets TABLE(id uniqueidentifier NOT NULL PRIMARY KEY);
        INSERT @tickets SELECT TRY_CONVERT(uniqueidentifier, value) FROM OPENJSON(@ticket_ids);
        IF NOT EXISTS (SELECT 1 FROM @tickets) OR EXISTS (SELECT 1 FROM @tickets WHERE id IS NULL) THROW 51251, 'Invalid ticket list', 1;
        IF (SELECT COUNT(*) FROM OPENJSON(@ticket_ids)) <> (SELECT COUNT(*) FROM @tickets) THROW 51252, 'Duplicate ticket id', 1;
        DECLARE @event_id uniqueidentifier, @start datetime2(3), @request_id uniqueidentifier = NEWID(), @amount decimal(19,0);
        SELECT @event_id = o.event_id, @start = e.start_time FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_events e WITH (UPDLOCK, HOLDLOCK) ON e.id = o.event_id WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = 'PAID';
        IF @event_id IS NULL THROW 51253, 'Paid order owned by actor required', 1;
        IF SYSUTCDATETIME() >= @start THROW 51254, 'Refund requests close at event start', 1;
        SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN @tickets x ON x.id = t.id ORDER BY t.id;
        IF EXISTS (
            SELECT 1 FROM @tickets x LEFT JOIN dbo.tc_tickets t ON t.id = x.id
            LEFT JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE t.id IS NULL OR oi.order_id <> @order_id OR t.status <> 'ACTIVE'
               OR EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.ticket_id = x.id AND rt.is_open = 1)
        ) THROW 51255, 'One or more tickets are not refundable', 1;
        INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
        VALUES (@request_id, @order_id, @actor_id, @reason, 'CUSTOMER_REQUEST');
        INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id) SELECT @request_id, id FROM @tickets;
        UPDATE t SET status = 'REFUND_PENDING', version = version + 1 FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, 'REFUND_REQUEST_CREATED', 'REFUND_REQUEST', @request_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @request_id request_id, @amount requested_amount;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp05;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_CreateTicketHold */
CREATE OR ALTER PROCEDURE dbo.usp_CreateTicketHold
    @user_id uniqueidentifier, @event_id uniqueidentifier, @selections nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp02;
    BEGIN TRY
        DECLARE @now datetime2(3) = SYSUTCDATETIME(), @hold_id uniqueidentifier = NEWID(), @expires datetime2(3);
        IF ISJSON(@selections) <> 1 THROW 51220, 'Selections must be a JSON array', 1;
        DECLARE @items TABLE(row_no int NOT NULL, zone_id uniqueidentifier NULL, seat_id uniqueidentifier NULL, quantity int NULL);
        INSERT @items SELECT CONVERT(int, [key]), TRY_CONVERT(uniqueidentifier, JSON_VALUE(value, '$.zoneId')),
                             TRY_CONVERT(uniqueidentifier, JSON_VALUE(value, '$.seatId')),
                             TRY_CONVERT(int, JSON_VALUE(value, '$.quantity')) FROM OPENJSON(@selections);
        IF NOT EXISTS (SELECT 1 FROM @items) OR (SELECT SUM(quantity) FROM @items) NOT BETWEEN 1 AND 8
            THROW 51221, 'A hold must contain between 1 and 8 tickets', 1;
        IF EXISTS (SELECT 1 FROM @items WHERE zone_id IS NULL OR quantity IS NULL OR quantity <= 0)
            THROW 51222, 'Invalid hold selection', 1;
        IF EXISTS (SELECT seat_id FROM @items WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING COUNT(*) > 1)
            THROW 51223, 'Duplicate seat selection', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @user_id AND status = 'ACTIVE' AND email_verified_at IS NOT NULL)
            THROW 51224, 'Active verified user required', 1;
        DECLARE @old_hold uniqueidentifier, @old_expires datetime2(3);
        SELECT @old_hold = id, @old_expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK)
        WHERE user_id = @user_id AND status = 'ACTIVE';
        IF @old_hold IS NOT NULL AND @old_expires > @now THROW 51225, 'User already has an active hold', 1;
        IF @old_hold IS NOT NULL EXEC dbo.usp_ReleaseTicketHold @old_hold, NULL, 'EXPIRED';
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = 'PUBLISHED' AND @now >= sale_start AND @now < sale_end)
            THROW 51226, 'Event is not on sale', 1;
        IF EXISTS (
            SELECT 1 FROM @items i LEFT JOIN dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) ON z.id = i.zone_id
            WHERE z.id IS NULL OR z.event_id <> @event_id OR (z.type = 'SEATED' AND (i.seat_id IS NULL OR i.quantity <> 1))
               OR (z.type = 'STANDING' AND i.seat_id IS NOT NULL)
        ) THROW 51227, 'Selection does not match event zone type', 1;
        IF EXISTS (
            SELECT 1 FROM @items i JOIN dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) ON s.id = i.seat_id
            WHERE i.seat_id IS NOT NULL AND (s.zone_id <> i.zone_id OR s.status <> 'AVAILABLE')
        ) OR EXISTS (SELECT 1 FROM @items i WHERE i.seat_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.tc_seats WHERE id = i.seat_id))
            THROW 51228, 'Seat is unavailable', 1;
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (SELECT zone_id, SUM(quantity) q FROM @items WHERE seat_id IS NULL GROUP BY zone_id) i ON i.zone_id = z.id
            WHERE z.capacity - z.held_quantity - z.sold_quantity < i.q
        ) THROW 51229, 'Zone capacity is unavailable', 1;
        SET @expires = DATEADD(minute, 10, @now);
        INSERT dbo.tc_ticket_holds(id, user_id, event_id, created_at, expires_at) VALUES (@hold_id, @user_id, @event_id, @now, @expires);
        INSERT dbo.tc_ticket_hold_items(hold_id, zone_id, seat_id, quantity, unit_price)
        SELECT @hold_id, i.zone_id, i.seat_id, i.quantity, z.price FROM @items i JOIN dbo.tc_zones z ON z.id = i.zone_id;
        UPDATE s SET status = 'HELD', version = version + 1 FROM dbo.tc_seats s JOIN @items i ON i.seat_id = s.id;
        UPDATE z SET held_quantity = held_quantity + i.q, version = version + 1 FROM dbo.tc_zones z
        JOIN (SELECT zone_id, SUM(quantity) q FROM @items WHERE seat_id IS NULL GROUP BY zone_id) i ON i.zone_id = z.id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @hold_id hold_id, @expires expires_at;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp02;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_DecideRefund */
CREATE OR ALTER PROCEDURE dbo.usp_DecideRefund
    @refund_id uniqueidentifier,
    @actor_id uniqueidentifier = NULL,
    @decision varchar(20),
    @rejection_reason nvarchar(2000) = NULL,
    @attempt_id uniqueidentifier = NULL,
    @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @request_id uniqueidentifier = COALESCE(
        (SELECT legacy_request_id FROM dbo.tc_refund_obligations WHERE id = @refund_id),
        CASE WHEN EXISTS (SELECT 1 FROM dbo.tc_refund_requests WHERE id = @refund_id) THEN @refund_id END
    );
    IF @request_id IS NULL THROW 52630, 'Refund obligation not found', 1;
    EXEC dbo.usp_DecideRefundRequest
        @request_id = @request_id,
        @actor_id = @actor_id,
        @decision = @decision,
        @rejection_reason = @rejection_reason,
        @retry_failed = @retry_failed;
END;

GO

/* Stored Procedure: usp_DecideRefundRequest */
CREATE OR ALTER PROCEDURE dbo.usp_DecideRefundRequest
    @request_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @decision varchar(20),
    @rejection_reason nvarchar(1000) = NULL,
    @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_ReviewRefundRequest @request_id, @actor_id, @decision, @rejection_reason, @retry_failed;
END;

GO

/* Stored Procedure: usp_ExecutePayout */
CREATE OR ALTER PROCEDURE dbo.usp_ExecutePayout
    @settlement_id uniqueidentifier, @payout_id uniqueidentifier, @actor_id uniqueidentifier,
    @amount decimal(19,0), @reference varchar(100), @verified_result varchar(20)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp16;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51560, 'Administrator role required', 1;
        IF @amount <= 0 OR @verified_result NOT IN ('PENDING', 'SUCCEEDED', 'FAILED') THROW 51561, 'Invalid payout input', 1;
        DECLARE @event_id uniqueidentifier, @settlement_status varchar(20), @net decimal(19,0), @current varchar(20),
                @paid decimal(19,0), @pending decimal(19,0);
        SELECT @event_id = event_id, @settlement_status = status, @net = net_payable
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK) WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51562, 'Settlement not found', 1;
        SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @current = status FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK) WHERE id = @payout_id;
        IF @current = 'SUCCEEDED' BEGIN
            SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @current IS NULL BEGIN
            IF @settlement_status <> 'CONFIRMED' THROW 51563, 'Only a confirmed settlement accepts a payout', 1;
            SELECT @paid = COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END), 0),
                   @pending = COALESCE(SUM(CASE WHEN status = 'PENDING' THEN amount ELSE 0 END), 0)
            FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK) WHERE settlement_id = @settlement_id;
            IF @paid + @pending + @amount > @net THROW 51564, 'Payout exceeds available settlement balance', 1;
            INSERT dbo.tc_payouts(id, settlement_id, amount, reference, status, paid_at)
            VALUES (@payout_id, @settlement_id, @amount, @reference, @verified_result,
                    CASE WHEN @verified_result = 'SUCCEEDED' THEN SYSUTCDATETIME() END);
        END ELSE BEGIN
            IF @current <> 'PENDING' THROW 51565, 'Failed payout requires a new payout id', 1;
            IF EXISTS (SELECT 1 FROM dbo.tc_payouts WHERE id = @payout_id AND (amount <> @amount OR reference <> @reference))
                THROW 51566, 'Payout replay payload mismatch', 1;
            UPDATE dbo.tc_payouts SET status = @verified_result,
                   paid_at = CASE WHEN @verified_result = 'SUCCEEDED' THEN SYSUTCDATETIME() ELSE NULL END,
                   version = version + 1 WHERE id = @payout_id;
        END;
        IF (SELECT COALESCE(SUM(amount), 0) FROM dbo.tc_payouts WHERE settlement_id = @settlement_id AND status = 'SUCCEEDED') = @net
            UPDATE dbo.tc_settlements SET status = 'PAID', version = version + 1 WHERE id = @settlement_id AND status = 'CONFIRMED';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT('PAYOUT_', @verified_result), 'PAYOUT', @payout_id, @reference);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp16;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_ProcessCancelledOrder */
CREATE OR ALTER PROCEDURE dbo.usp_ProcessCancelledOrder @event_id uniqueidentifier, @order_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp17;
    BEGIN TRY
        IF IS_ROLEMEMBER('tc_worker') <> 1 AND IS_ROLEMEMBER('db_owner') <> 1
            THROW 51569, 'Worker principal required', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = 'CANCELLED')
            THROW 51570, 'Event is not cancelled', 1;
        DECLARE @hold_id uniqueidentifier, @order_status varchar(30), @owner_id uniqueidentifier,
                @request_id uniqueidentifier;
        SELECT @hold_id = hold_id, @order_status = status, @owner_id = user_id
        FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @order_id AND event_id = @event_id;
        IF @hold_id IS NULL THROW 51571, 'Order does not belong to cancelled event', 1;
        IF @order_status IN ('PENDING_PAYMENT', 'EXPIRED', 'CANCELLED') BEGIN
            IF EXISTS (SELECT 1 FROM dbo.tc_ticket_holds WHERE id = @hold_id AND status = 'ACTIVE')
                EXEC dbo.usp_ReleaseTicketHold @hold_id, NULL, 'EVENT_CANCELLED';
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_id order_id, CAST('RELEASED' AS varchar(30)) result; RETURN;
        END;

        SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id ORDER BY t.id;

        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        SELECT 'CANCELLED_EVENT_USED_TICKET', 'TICKET', t.id,
               CONCAT(N'{"orderId":"', @order_id, N'"}')
        FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id AND t.status = 'USED'
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_audit_logs a
                          WHERE a.action = 'CANCELLED_EVENT_USED_TICKET' AND a.aggregate_id = t.id);

        UPDATE rr SET reason_type = 'EVENT_CANCELLATION', version = version + 1
        FROM dbo.tc_refund_requests rr
        WHERE rr.order_id = @order_id AND rr.status = 'PENDING'
          AND rr.reason_type = 'CUSTOMER_REQUEST';

        UPDATE rr SET status = 'COMPLETED', decided_at = COALESCE(decided_at, SYSUTCDATETIME()),
                      version = version + 1
        FROM dbo.tc_refund_requests rr
        WHERE rr.order_id = @order_id AND rr.status = 'PENDING'
          AND rr.reason_type = 'EVENT_CANCELLATION'
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.refund_request_id = rr.id);

        DECLARE @pending TABLE(id uniqueidentifier PRIMARY KEY);
        INSERT @pending
        SELECT id FROM dbo.tc_refund_requests
        WHERE order_id = @order_id AND status = 'PENDING' AND reason_type = 'EVENT_CANCELLATION';
        WHILE EXISTS (SELECT 1 FROM @pending) BEGIN
            SELECT TOP (1) @request_id = id FROM @pending ORDER BY id;
            EXEC dbo.usp_ReviewRefundRequest @request_id, NULL, 'APPROVE', NULL, 0;
            DELETE FROM @pending WHERE id = @request_id;
        END;

        IF EXISTS (
            SELECT 1 FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = 'ACTIVE')
        BEGIN
            SET @request_id = NEWID();
            INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
            VALUES (@request_id, @order_id, @owner_id, N'Event cancelled', 'EVENT_CANCELLATION');
            INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id)
            SELECT @request_id, t.id FROM dbo.tc_tickets t
            JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = 'ACTIVE';
            UPDATE t SET status = 'REFUND_PENDING', version = version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = 'ACTIVE';
            EXEC dbo.usp_ReviewRefundRequest @request_id, NULL, 'APPROVE', NULL, 0;
        END;

        IF @started = 1 COMMIT TRANSACTION;
        SELECT @order_id order_id, @request_id refund_request_id,
               CAST('PROCESSED' AS varchar(30)) result;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp17;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_PublishEvent */
CREATE OR ALTER PROCEDURE dbo.usp_PublishEvent
    @event_id uniqueidentifier, @actor_id uniqueidentifier, @commission_rule_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp12;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51520, 'Administrator role required', 1;
        DECLARE @organization_id uniqueidentifier, @status varchar(30), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @organization_id = organization_id, @status = status FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @status IS NULL THROW 51521, 'Event not found', 1;
        IF @status = 'PUBLISHED' BEGIN
            IF (SELECT commission_rule_id FROM dbo.tc_events WHERE id = @event_id) <> @commission_rule_id
                THROW 51522, 'Published event commission rule is immutable', 1;
            SELECT @event_id event_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> 'PENDING_APPROVAL' THROW 51523, 'Event is not pending approval', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_commission_rules WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @commission_rule_id AND organization_id = @organization_id
                         AND effective_from <= @now AND effective_to > @now)
            THROW 51524, 'Commission rule is not valid for event organization', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND (cover_image_url IS NULL OR sale_start >= sale_end OR sale_end > start_time OR start_time >= end_time))
            THROW 51525, 'Event content or schedule is incomplete', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_zones WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id)
            THROW 51526, 'Event requires at least one zone', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_zones z WHERE z.event_id = @event_id AND z.type = 'SEATED'
                   AND NOT EXISTS (SELECT 1 FROM dbo.tc_seats s WHERE s.zone_id = z.id))
            THROW 51527, 'Each seated zone requires seats', 1;
        UPDATE dbo.tc_events SET commission_rule_id = @commission_rule_id, status = 'PUBLISHED', version = version + 1 WHERE id = @event_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @event_id event_id, CAST('PUBLISHED' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp12;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_RecalculateSettlement */
CREATE OR ALTER PROCEDURE dbo.usp_RecalculateSettlement
    @event_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @return_result bit = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp14;
    BEGIN TRY
        IF @event_id IS NULL OR @actor_id IS NULL THROW 51700, 'Event and actor are required', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51701, 'Administrator role required', 1;

        DECLARE @rule_id uniqueidentifier, @rate decimal(7,4), @fixed decimal(19,0),
                @settlement_id uniqueidentifier, @settlement_status varchar(20), @lock_count bigint,
                @gross decimal(19,0), @refund decimal(19,0), @commission decimal(19,0), @net decimal(19,0);
        SELECT @rule_id = commission_rule_id
        FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @event_id;
        IF @rule_id IS NULL THROW 51702, 'Event not found or has no applied commission rule', 1;
        SELECT @rate = rate_percent, @fixed = fixed_fee
        FROM dbo.tc_commission_rules WITH (HOLDLOCK)
        WHERE id = @rule_id;
        IF dbo.fn_CalculateCommission(0, @rate, @fixed) IS NULL THROW 51703, 'Applied commission rule is invalid', 1;

        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refund_requests rr WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = rr.order_id WHERE o.event_id = @event_id;
        SELECT @settlement_id = id, @settlement_status = status
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK)
        WHERE event_id = @event_id;
        IF @settlement_status IN ('CONFIRMED', 'PAID')
            THROW 51704, 'Confirmed settlement cannot be recalculated', 1;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_payments p WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refunds r WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        JOIN dbo.tc_orders o ON o.id = oi.order_id WHERE o.event_id = @event_id;

        DECLARE @items TABLE(
            order_id uniqueidentifier PRIMARY KEY,
            gross decimal(19,0) NOT NULL,
            refund decimal(19,0) NOT NULL,
            commission decimal(19,0) NOT NULL,
            net decimal(19,0) NOT NULL
        );
        INSERT @items(order_id, gross, refund, commission, net)
        SELECT order_id, ticket_gross_amount, successful_ticket_refund_amount,
               dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed),
               remaining_ticket_amount - dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed)
        FROM dbo.vw_OrderFinancialSummary
        WHERE event_id = @event_id AND status = 'PAID' AND ticket_gross_amount > 0;

        SELECT @gross = COALESCE(SUM(gross), 0), @refund = COALESCE(SUM(refund), 0),
               @commission = COALESCE(SUM(commission), 0), @net = COALESCE(SUM(net), 0)
        FROM @items;

        IF NOT EXISTS (SELECT 1 FROM @items) BEGIN
            IF @settlement_id IS NOT NULL BEGIN
                DELETE FROM dbo.tc_settlement_items WHERE settlement_id = @settlement_id;
                DELETE FROM dbo.tc_settlements WHERE id = @settlement_id AND status = 'DRAFT';
            END;
            IF @started = 1 COMMIT TRANSACTION;
            IF @return_result = 1
                SELECT CAST(NULL AS uniqueidentifier) settlement_id, CAST('EMPTY' AS varchar(20)) status,
                       @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable;
            RETURN;
        END;

        IF @settlement_id IS NULL BEGIN
            SET @settlement_id = NEWID();
            INSERT dbo.tc_settlements(id, event_id) VALUES (@settlement_id, @event_id);
        END;
        DELETE FROM dbo.tc_settlement_items WHERE settlement_id = @settlement_id;
        INSERT dbo.tc_settlement_items(settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
        SELECT @settlement_id, order_id, gross, refund, commission, net FROM @items;
        UPDATE dbo.tc_settlements
        SET gross_revenue = @gross, total_refund = @refund, total_commission = @commission,
            net_payable = @net, version = version + 1
        WHERE id = @settlement_id AND status = 'DRAFT';

        IF @started = 1 COMMIT TRANSACTION;
        IF @return_result = 1
            SELECT @settlement_id settlement_id, CAST('DRAFT' AS varchar(20)) status,
                   @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp14;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_RecordPayout */
CREATE OR ALTER PROCEDURE dbo.usp_RecordPayout
    @settlement_id uniqueidentifier,
    @payout_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @amount decimal(19,0),
    @reference varchar(100),
    @verified_result varchar(20)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp16;
    BEGIN TRY
        IF @settlement_id IS NULL OR @payout_id IS NULL OR @actor_id IS NULL
           OR @amount IS NULL OR @amount <= 0 OR @reference IS NULL OR LEN(TRIM(@reference)) = 0
           OR @verified_result NOT IN ('PENDING', 'SUCCEEDED', 'FAILED')
            THROW 51730, 'Invalid payout input', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51731, 'Administrator role required', 1;

        DECLARE @event_id uniqueidentifier, @settlement_status varchar(20), @net decimal(19,0),
                @current_status varchar(20), @current_settlement uniqueidentifier,
                @current_amount decimal(19,0), @current_reference varchar(100),
                @paid decimal(19,0), @pending decimal(19,0), @lock_count bigint;
        SELECT @event_id = event_id FROM dbo.tc_settlements WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51732, 'Settlement not found', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @settlement_status = status, @net = net_payable
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @settlement_id AND event_id = @event_id;
        SELECT @current_settlement = settlement_id, @current_status = status,
               @current_amount = amount, @current_reference = reference
        FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @payout_id;

        IF @current_status IS NOT NULL BEGIN
            IF @current_settlement <> @settlement_id OR @current_amount <> @amount OR @current_reference <> @reference
                THROW 51733, 'Payout replay payload mismatch', 1;
            IF @current_status = 'SUCCEEDED' BEGIN
                IF @verified_result <> 'SUCCEEDED' THROW 51734, 'Successful payout is immutable', 1;
                IF @started = 1 COMMIT TRANSACTION;
                SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
                RETURN;
            END;
            IF @current_status = 'FAILED' THROW 51735, 'Failed payout requires a new payout id', 1;
            UPDATE dbo.tc_payouts
            SET status = @verified_result,
                paid_at = CASE WHEN @verified_result = 'SUCCEEDED' THEN SYSUTCDATETIME() ELSE NULL END,
                version = version + 1
            WHERE id = @payout_id AND status = 'PENDING';
        END ELSE BEGIN
            IF @settlement_status <> 'CONFIRMED'
                THROW 51736, 'Only a confirmed settlement accepts a new payout', 1;
            SELECT @paid = COALESCE(SUM(CASE WHEN status = 'SUCCEEDED' THEN amount ELSE 0 END), 0),
                   @pending = COALESCE(SUM(CASE WHEN status = 'PENDING' THEN amount ELSE 0 END), 0)
            FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK)
            WHERE settlement_id = @settlement_id;
            IF @paid + @pending + @amount > @net
                THROW 51737, 'Payout exceeds available settlement balance', 1;
            INSERT dbo.tc_payouts(id, settlement_id, amount, reference, status, paid_at)
            VALUES (@payout_id, @settlement_id, @amount, @reference, @verified_result,
                    CASE WHEN @verified_result = 'SUCCEEDED' THEN SYSUTCDATETIME() END);
        END;

        SELECT @paid = COALESCE(SUM(amount), 0)
        FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK)
        WHERE settlement_id = @settlement_id AND status = 'SUCCEEDED';
        IF @paid = @net
            UPDATE dbo.tc_settlements
            SET status = 'PAID', version = version + 1
            WHERE id = @settlement_id AND status = 'CONFIRMED';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT('PAYOUT_', @verified_result), 'PAYOUT', @payout_id, @reference);

        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp16;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_ReleaseTicketHold */
CREATE OR ALTER PROCEDURE dbo.usp_ReleaseTicketHold
    @hold_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @reason varchar(30)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp07;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @user_id uniqueidentifier, @status varchar(20), @expires datetime2(3), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id = h.event_id, @user_id = h.user_id FROM dbo.tc_ticket_holds h WHERE h.id = @hold_id;
        IF @event_id IS NULL THROW 51270, 'Ticket hold not found', 1;
        SELECT 1 lock_row FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @status = status, @expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;
        IF @status <> 'ACTIVE' BEGIN
            SELECT @hold_id hold_id, @status hold_status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @reason NOT IN ('USER_CANCEL', 'EXPIRED', 'EVENT_CANCELLED') THROW 51271, 'Invalid release reason', 1;
        IF @reason = 'USER_CANCEL' AND (@actor_id IS NULL OR @actor_id <> @user_id) THROW 51272, 'Hold owner required', 1;
        IF @reason = 'EXPIRED' AND @now < @expires THROW 51273, 'Hold has not expired', 1;
        IF @reason = 'EVENT_CANCELLED' AND NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND status = 'CANCELLED')
            THROW 51274, 'Event is not cancelled', 1;
        SELECT z.id FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_ticket_hold_items hi WHERE hi.hold_id = @hold_id AND hi.zone_id = z.id) ORDER BY z.id;
        SELECT s.id FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_ticket_hold_items hi WHERE hi.hold_id = @hold_id AND hi.seat_id = s.id) ORDER BY s.id;
        UPDATE s SET status = 'AVAILABLE', version = version + 1 FROM dbo.tc_seats s
        JOIN dbo.tc_ticket_hold_items hi ON hi.seat_id = s.id WHERE hi.hold_id = @hold_id AND s.status = 'HELD';
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (
                SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_ticket_hold_items
                WHERE hold_id = @hold_id AND seat_id IS NULL GROUP BY zone_id
            ) hi ON hi.zone_id = z.id WHERE z.held_quantity < hi.quantity
        ) THROW 51275, 'Held standing inventory is inconsistent', 1;
        UPDATE z SET held_quantity = held_quantity - hi.quantity, version = version + 1
        FROM dbo.tc_zones z JOIN (
            SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_ticket_hold_items WHERE hold_id = @hold_id AND seat_id IS NULL GROUP BY zone_id
        ) hi ON hi.zone_id = z.id WHERE z.held_quantity >= hi.quantity;
        UPDATE dbo.tc_coupon_redemptions SET status = 'RELEASED', released_at = @now
        WHERE order_id IN (SELECT id FROM dbo.tc_orders WHERE hold_id = @hold_id) AND status = 'RESERVED';
        UPDATE dbo.tc_orders SET status = CASE WHEN @reason = 'EXPIRED' THEN 'EXPIRED' ELSE 'CANCELLED' END, version = version + 1
        WHERE hold_id = @hold_id AND status = 'PENDING_PAYMENT';
        UPDATE dbo.tc_ticket_holds SET status = 'RELEASED', version = version + 1 WHERE id = @hold_id AND status = 'ACTIVE';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, 'TICKET_HOLD_RELEASED', 'TICKET_HOLD', @hold_id, @reason);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @hold_id hold_id, CAST('RELEASED' AS varchar(20)) hold_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp07;
        THROW;
    END CATCH
END;

GO

/* Stored Procedure: usp_RequestTicketRefund */
CREATE OR ALTER PROCEDURE dbo.usp_RequestTicketRefund
    @order_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @ticket_ids nvarchar(max),
    @reason_type varchar(30),
    @reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON;
    IF @reason_type <> 'CUSTOMER_REQUEST'
        THROW 52620, 'Only CUSTOMER_REQUEST is accepted from a buyer', 1;
    EXEC dbo.usp_CreateRefundRequest
        @order_id = @order_id, @actor_id = @actor_id, @ticket_ids = @ticket_ids, @reason = @reason;
END;

GO

/* Stored Procedure: usp_ReviewRefundRequest */
CREATE OR ALTER PROCEDURE dbo.usp_ReviewRefundRequest
    @request_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @decision varchar(20),
    @rejection_reason nvarchar(1000) = NULL, @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp10;
    BEGIN TRY
        IF @decision NOT IN ('APPROVE', 'REJECT') THROW 51400, 'Invalid refund decision', 1;
        DECLARE @order_id uniqueidentifier, @event_id uniqueidentifier, @reason_type varchar(30), @status varchar(20),
                @event_status varchar(30), @amount decimal(19,0), @refund_id uniqueidentifier, @payment_id uniqueidentifier,
                @now datetime2(3) = SYSUTCDATETIME();
        SELECT @order_id = order_id, @reason_type = reason_type, @status = status
        FROM dbo.tc_refund_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;
        IF @order_id IS NULL THROW 51401, 'Refund request not found', 1;
        SELECT @event_id = event_id FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        SELECT @event_status = status FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @reason_type = 'CUSTOMER_REQUEST' AND NOT EXISTS (
            SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = 'ADMIN')
            THROW 51402, 'Administrator role required', 1;
        IF @reason_type = 'EVENT_CANCELLATION' AND @event_status <> 'CANCELLED' THROW 51403, 'Cancellation request requires cancelled event', 1;
        IF @status IN ('REJECTED', 'COMPLETED') BEGIN
            IF (@status = 'REJECTED' AND @decision <> 'REJECT') OR (@status = 'COMPLETED' AND @decision <> 'APPROVE')
                THROW 51404, 'Refund request already has the opposite final decision', 1;
            SELECT @request_id request_id, @status request_status, CAST(NULL AS uniqueidentifier) refund_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
        WHERE rt.refund_request_id = @request_id ORDER BY t.id;
        IF @decision = 'REJECT' BEGIN
            IF NULLIF(LTRIM(RTRIM(@rejection_reason)), N'') IS NULL THROW 51405, 'Rejection reason is required', 1;
            UPDATE t SET status = CASE WHEN @event_status = 'CANCELLED' THEN 'INVALIDATED' ELSE 'ACTIVE' END, version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id AND t.status = 'REFUND_PENDING';
            UPDATE dbo.tc_refund_request_tickets SET is_open = 0 WHERE refund_request_id = @request_id;
            UPDATE dbo.tc_refund_requests SET status = 'REJECTED', reviewer_id = @actor_id, decided_at = @now,
                   rejection_reason = @rejection_reason, version = version + 1 WHERE id = @request_id;
        END ELSE BEGIN
            SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id;
            IF @amount = 0 BEGIN
                SELECT z.id FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                    SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_request_id = @request_id AND oi.zone_id = z.id);
                UPDATE s SET status = 'AVAILABLE', version = s.version + 1 FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
                JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
                WHERE rt.refund_request_id = @request_id AND s.status = 'SOLD';
                UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id;
                UPDATE t SET status = 'REFUNDED', version = t.version + 1 FROM dbo.tc_tickets t
                JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id WHERE rt.refund_request_id = @request_id;
                UPDATE dbo.tc_refund_request_tickets SET is_open = 0 WHERE refund_request_id = @request_id;
                UPDATE dbo.tc_refund_requests SET status = 'COMPLETED', reviewer_id = @actor_id, decided_at = @now, version = version + 1 WHERE id = @request_id;
            END ELSE BEGIN
                IF EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE refund_request_id = @request_id AND status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED')) BEGIN
                    SELECT TOP (1) @refund_id = id FROM dbo.tc_refunds WHERE refund_request_id = @request_id ORDER BY created_at DESC;
                END ELSE BEGIN
                    IF @retry_failed = 0 AND EXISTS (SELECT 1 FROM dbo.tc_refunds WHERE refund_request_id = @request_id AND status = 'FAILED')
                        THROW 51406, 'Refund retry requires explicit authorization', 1;
                    SELECT TOP (1) @payment_id = id FROM dbo.tc_payments WHERE order_id = @order_id AND status = 'CAPTURED' ORDER BY captured_at;
                    IF @payment_id IS NULL THROW 51407, 'Captured payment not found', 1;
                    SET @refund_id = NEWID();
                    INSERT dbo.tc_refunds(id, refund_request_id, payment_id, purpose, amount)
                    VALUES (@refund_id, @request_id, @payment_id, 'CUSTOMER_REFUND', @amount);
                END;
                UPDATE dbo.tc_refund_requests SET status = 'APPROVED', reviewer_id = @actor_id,
                       decided_at = COALESCE(decided_at, @now), version = version + 1 WHERE id = @request_id;
            END;
        END;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT('REFUND_REQUEST_', @decision), 'REFUND_REQUEST', @request_id, @rejection_reason);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @request_id request_id, status request_status, @refund_id refund_id FROM dbo.tc_refund_requests WHERE id = @request_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp10;
        THROW;
    END CATCH
END;

GO
