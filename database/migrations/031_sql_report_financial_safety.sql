-- SQL report 2026-10-11: additive corrections; preserve canonical tables and migration history.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;
GO
IF OBJECT_ID('dbo.tc_gateway_refund_receipts','U') IS NULL
CREATE TABLE dbo.tc_gateway_refund_receipts(
 id uniqueidentifier NOT NULL CONSTRAINT PK_GatewayRefundReceipts PRIMARY KEY,
 refund_id uniqueidentifier NOT NULL REFERENCES dbo.tc_refunds(id),payment_id uniqueidentifier NOT NULL REFERENCES dbo.tc_payments(id),
 fingerprint binary(32) NOT NULL CONSTRAINT UQ_GatewayRefundReceipts_Fingerprint UNIQUE,
 observed_status varchar(20) NOT NULL CHECK(observed_status IN('SUCCEEDED','FAILED','UNKNOWN')),
 provider_reference nvarchar(200) NOT NULL,verified_payload nvarchar(max) NOT NULL CHECK(ISJSON(verified_payload)=1),
 resolution varchar(30) NOT NULL DEFAULT 'RECEIVED' CHECK(resolution IN('RECEIVED','APPLIED','IGNORED','NEEDS_RECONCILIATION')),
 reason varchar(60) NULL,received_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
GO
/* Stored Procedure: usp_ApplyPaymentResult */
CREATE OR ALTER PROCEDURE dbo.usp_ApplyPaymentResult
    @order_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @payment_id uniqueidentifier = NULL,
    @verified_result nvarchar(max), @ticket_codes nvarchar(max) = NULL, @retry_failed_compensation bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp09;
    BEGIN TRY
        IF ISJSON(@verified_result) <> 1 THROW 51390, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @event_id uniqueidentifier, @hold_id uniqueidentifier, @owner_id uniqueidentifier,
                @accepted uniqueidentifier, @sale_start datetime2(3), @sale_end datetime2(3),
                @order_status varchar(30), @hold_status varchar(20), @expires datetime2(3),
                @event_status varchar(30), @total decimal(19,0), @payment_status varchar(20),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result IS NULL OR @result NOT IN ('CAPTURED', 'FAILED', 'UNKNOWN') THROW 51391, 'Unsupported payment result', 1;
        SELECT @event_id = event_id, @hold_id = hold_id, @owner_id = user_id, @order_status = status, @total = total_amount
        FROM dbo.tc_orders WHERE id = @order_id;
        IF @event_id IS NULL THROW 51392, 'Order not found', 1;
        SELECT @event_status = status, @sale_start = sale_start, @sale_end = sale_end FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @hold_status = status, @expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;
        SELECT @order_status=status,@total=total_amount,@accepted=accepted_payment_id
        FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
        SET @now=SYSUTCDATETIME();
        IF @payment_id IS NULL BEGIN
            IF @actor_id IS NULL OR @actor_id <> @owner_id OR @total <> 0 OR @result <> 'CAPTURED'
                THROW 51393, 'Only the owner may complete a zero-value order', 1;
        END ELSE BEGIN
            IF COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1
                THROW 51389, 'Buyer principal cannot apply a paid provider result', 1;
            IF @result='CAPTURED' AND NULLIF(LTRIM(RTRIM(@provider_reference)),N'') IS NULL THROW 51801,'Captured result requires provider reference',1;
            SELECT @payment_status = status FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK)
            WHERE id = @payment_id AND order_id = @order_id AND amount = @total;
            IF @payment_status IS NULL THROW 51394, 'Payment does not match order amount', 1;
            IF EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND provider_reference IS NOT NULL AND @provider_reference IS NOT NULL AND provider_reference<>@provider_reference) THROW 51802,'Provider reference conflict',1;
            IF JSON_VALUE(@verified_result,'$.providerAmount') IS NOT NULL AND (TRY_CONVERT(decimal(19,0),JSON_VALUE(@verified_result,'$.providerAmount')) IS NULL OR TRY_CONVERT(decimal(19,0),JSON_VALUE(@verified_result,'$.providerAmount'))<>@total) THROW 51803,'Gateway amount mismatch',1;
            IF JSON_VALUE(@verified_result,'$.txnRef') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND txn_ref=JSON_VALUE(@verified_result,'$.txnRef')) THROW 51804,'Gateway transaction mismatch',1;
            IF @payment_status = 'CAPTURED' AND @order_status = 'PAID' AND @accepted=@payment_id BEGIN
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
        IF @order_status = 'PAID' AND (@payment_id IS NULL OR @accepted=@payment_id) BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_status order_status, @result payment_status; RETURN;
        END;
        IF @order_status<>'PENDING_PAYMENT' OR @event_status <> 'PUBLISHED' OR @hold_status <> 'ACTIVE' OR @expires <= @now OR @now<@sale_start OR @now>=@sale_end BEGIN
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
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.zone_id = z.id);
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_order_items oi WHERE oi.order_id = @order_id AND oi.seat_id = s.id);
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
        UPDATE dbo.tc_orders SET status = 'PAID', accepted_payment_id=@payment_id, paid_at = @now, version = version + 1 WHERE id = @order_id;
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
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp11;
    BEGIN TRY
        IF COALESCE(IS_ROLEMEMBER('tc_worker'), 0) <> 1 AND COALESCE(IS_ROLEMEMBER('db_owner'), 0) <> 1
            THROW 51409, 'Worker principal required', 1;
        IF ISJSON(@verified_result) <> 1 THROW 51410, 'Verified result must be JSON', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, '$.status'),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, '$.providerReference'),
                @request_id uniqueidentifier, @payment_id uniqueidentifier, @purpose varchar(30),
                @receipt uniqueidentifier, @fingerprint binary(32), @old_reference nvarchar(200), @problem varchar(60),
                @amount decimal(19,0), @current varchar(20), @order_id uniqueidentifier,
                @event_id uniqueidentifier, @payment_amount decimal(19,0),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result IS NULL OR @result NOT IN ('SUCCEEDED', 'FAILED', 'UNKNOWN') THROW 51411, 'Unsupported refund result', 1;
        IF @provider_reference IS NULL OR LEN(@provider_reference) = 0 THROW 51415, 'Provider reference is required', 1;

        SELECT @payment_id = payment_id FROM dbo.tc_refunds WHERE id = @refund_id;
        IF @payment_id IS NULL THROW 51412, 'Refund not found', 1;
        SELECT @order_id = order_id FROM dbo.tc_payments WHERE id = @payment_id;
        SELECT @event_id = event_id FROM dbo.tc_orders WHERE id = @order_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        SELECT @payment_amount = amount FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK) WHERE id = @payment_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_refunds WITH (UPDLOCK, HOLDLOCK) WHERE payment_id = @payment_id;
        SELECT @request_id = refund_request_id, @purpose = purpose, @amount = amount, @current = status, @old_reference=provider_reference
        FROM dbo.tc_refunds WHERE id = @refund_id AND payment_id = @payment_id;
        IF @current IS NULL THROW 51412, 'Refund not found', 1;
        IF @request_id IS NOT NULL
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_refund_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;

        SET @now=SYSUTCDATETIME();
        SET @fingerprint=HASHBYTES('SHA2_256',CONCAT(CONVERT(varchar(36),@refund_id),'|',@result,'|',@provider_reference));
        SELECT @receipt=id FROM dbo.tc_gateway_refund_receipts WITH(UPDLOCK,HOLDLOCK) WHERE fingerprint=@fingerprint;
        IF @receipt IS NULL BEGIN
            SET @receipt=NEWID();
            INSERT dbo.tc_gateway_refund_receipts(id,refund_id,payment_id,fingerprint,observed_status,provider_reference,verified_payload)
            VALUES(@receipt,@refund_id,@payment_id,@fingerprint,@result,@provider_reference,@verified_result);
        END;
        IF @old_reference IS NOT NULL AND @old_reference<>@provider_reference SET @problem='PROVIDER_REFERENCE_CONFLICT';
        IF @current = 'SUCCEEDED' AND @problem IS NULL BEGIN
            UPDATE dbo.tc_gateway_refund_receipts SET resolution='APPLIED' WHERE id=@receipt;
            SELECT @refund_id refund_id, @current refund_status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @current='FAILED' AND @result='UNKNOWN' AND @problem IS NULL BEGIN
            UPDATE dbo.tc_gateway_refund_receipts SET resolution='IGNORED' WHERE id=@receipt;
            IF @started=1 COMMIT;
            SELECT @refund_id refund_id,@current refund_status; RETURN;
        END;
        IF @current='FAILED' AND @result='SUCCEEDED' AND (@old_reference IS NULL OR @old_reference<>@provider_reference) SET @problem='LATE_SUCCESS_UNMATCHED_REFERENCE';
        IF @current='FAILED' AND @result='SUCCEEDED' AND EXISTS(SELECT 1 FROM dbo.tc_refunds WHERE id<>@refund_id AND status IN('PENDING','UNKNOWN','SUCCEEDED') AND ((refund_request_id=@request_id AND @request_id IS NOT NULL) OR (@purpose='PAYMENT_COMPENSATION' AND payment_id=@payment_id AND purpose=@purpose))) SET @problem='LATE_SUCCESS_WITH_ANOTHER_ATTEMPT';
        IF @result = 'SUCCEEDED' AND
           (SELECT COALESCE(SUM(amount), 0) FROM dbo.tc_refunds WHERE payment_id = @payment_id AND status = 'SUCCEEDED') + @amount > @payment_amount
            SET @problem='REFUND_EXCEEDS_CAPTURE';

        IF @result = 'SUCCEEDED' AND @purpose = 'CUSTOMER_REFUND' BEGIN
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id;
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.zone_id = z.id);
            SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.seat_id = s.id);
            IF EXISTS (
                SELECT 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt
                    JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                    WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL AND t.status = 'REFUND_PENDING'
                    GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id WHERE z.sold_quantity < q.quantity)
                SET @problem='INVENTORY_INCONSISTENT';
        END;

        IF @problem IS NOT NULL BEGIN
            UPDATE dbo.tc_gateway_refund_receipts SET resolution='NEEDS_RECONCILIATION',reason=@problem WHERE id=@receipt;
            UPDATE dbo.tc_refund_obligations SET status='NEEDS_RECONCILIATION',updated_at=@now WHERE id=@refund_id OR legacy_request_id=@request_id;
            INSERT dbo.tc_audit_logs(action,aggregate_type,aggregate_id,detail) VALUES('REFUND_RECONCILIATION_REQUIRED','REFUND',@refund_id,@problem);
            IF @started=1 COMMIT;
            SELECT @refund_id refund_id,CAST('NEEDS_RECONCILIATION' AS varchar(30)) refund_status; RETURN;
        END;
        UPDATE dbo.tc_gateway_refund_receipts SET resolution='APPLIED' WHERE id=@receipt;
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
/* Stored Procedure: usp_ReviewRefundRequest */
CREATE OR ALTER PROCEDURE dbo.usp_ReviewRefundRequest
    @request_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @decision varchar(20),
    @rejection_reason nvarchar(1000) = NULL, @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp10;
    BEGIN TRY
        IF @decision NOT IN ('APPROVE', 'REJECT') THROW 51400, 'Invalid refund decision', 1;
        DECLARE @order_id uniqueidentifier, @event_id uniqueidentifier, @reason_type varchar(30), @status varchar(20),
                @event_status varchar(30), @amount decimal(19,0), @refund_id uniqueidentifier, @payment_id uniqueidentifier,
                @now datetime2(3) = SYSUTCDATETIME();
        SELECT @order_id=order_id FROM dbo.tc_refund_requests WHERE id=@request_id;
        SELECT @event_id=event_id FROM dbo.tc_orders WHERE id=@order_id;
        IF @event_id IS NULL THROW 51401,'Refund request not found',1;
        SELECT @event_status=status FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
        SELECT @payment_id=accepted_payment_id FROM dbo.tc_orders WITH(UPDLOCK,HOLDLOCK) WHERE id=@order_id;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment_id;
        SELECT @reason_type=reason_type,@status=status FROM dbo.tc_refund_requests WITH(UPDLOCK,HOLDLOCK) WHERE id=@request_id;
        SET @now=SYSUTCDATETIME();
        IF @actor_id IS NULL BEGIN
            IF @decision<>'APPROVE' OR @reason_type<>'EVENT_CANCELLATION' OR @event_status<>'CANCELLED'
               OR (COALESCE(IS_ROLEMEMBER('tc_worker'),0)<>1 AND COALESCE(IS_MEMBER('db_owner'),0)<>1) THROW 51402,'System approval only for cancelled event',1;
        END ELSE IF NOT EXISTS(SELECT 1 FROM dbo.tc_user_platform_roles pr JOIN dbo.tc_users u ON u.id=pr.user_id WHERE pr.user_id=@actor_id AND pr.role='ADMIN' AND u.status='ACTIVE') THROW 51402,'Administrator role required',1;
        IF @reason_type = 'EVENT_CANCELLATION' AND @event_status <> 'CANCELLED' THROW 51403, 'Cancellation request requires cancelled event', 1;
        IF @status IN ('REJECTED', 'COMPLETED') BEGIN
            IF (@status = 'REJECTED' AND @decision <> 'REJECT') OR (@status = 'COMPLETED' AND @decision <> 'APPROVE')
                THROW 51404, 'Refund request already has the opposite final decision', 1;
            SELECT @request_id request_id, @status request_status, CAST(NULL AS uniqueidentifier) refund_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
        WHERE rt.refund_request_id = @request_id;
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
                SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
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
                    SELECT @payment_id=accepted_payment_id FROM dbo.tc_orders WHERE id=@order_id;
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_payments WHERE id=@payment_id AND order_id=@order_id AND status='CAPTURED') SET @payment_id=NULL;
                    IF @payment_id IS NULL THROW 51407, 'Captured payment not found', 1;
                    IF (SELECT COALESCE(SUM(amount),0) FROM dbo.tc_refunds WHERE payment_id=@payment_id AND status IN('PENDING','UNKNOWN','SUCCEEDED'))+@amount>(SELECT amount FROM dbo.tc_payments WHERE id=@payment_id) THROW 51805,'Refund reservation exceeds captured amount',1;
                    IF EXISTS(SELECT 1 FROM dbo.tc_gateway_refund_receipts WHERE payment_id=@payment_id AND resolution='NEEDS_RECONCILIATION') THROW 51806,'Gateway reconciliation is required before retry',1;
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
/* Stored Procedure: usp_BeginOrderPayment */
CREATE OR ALTER PROCEDURE dbo.usp_BeginOrderPayment @order_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @lock_count bigint;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp08;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @hold_id uniqueidentifier, @amount decimal(19,0), @payment_id uniqueidentifier,
                @txn_ref varchar(100), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id=event_id,@hold_id=hold_id FROM dbo.tc_orders WHERE id=@order_id AND user_id=@actor_id;
        IF @event_id IS NULL THROW 51280,'Order cannot begin payment',1;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_events WITH(UPDLOCK,HOLDLOCK) WHERE id=@event_id;
        SELECT @lock_count=COUNT_BIG(*) FROM dbo.tc_ticket_holds WITH(UPDLOCK,HOLDLOCK) WHERE id=@hold_id;
        SET @now=SYSUTCDATETIME();
        SELECT @amount=o.total_amount FROM dbo.tc_orders o WITH(UPDLOCK,HOLDLOCK) JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id
        WHERE o.id=@order_id AND o.user_id=@actor_id AND o.status='PENDING_PAYMENT' AND h.status='ACTIVE' AND h.expires_at>@now;
        IF @amount IS NULL THROW 51280,'Order cannot begin payment',1;
        IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WHERE id=@event_id AND status='PUBLISHED' AND @now>=sale_start AND @now<sale_end) THROW 51281,'Event is unavailable for payment',1;
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
CREATE OR ALTER PROCEDURE dbo.usp_CompleteFreeOrder @order_id uniqueidentifier,@actor_id uniqueidentifier,@ticket_codes nvarchar(max)
AS BEGIN SET NOCOUNT ON;
 EXEC dbo.usp_ApplyPaymentResult @order_id=@order_id,@actor_id=@actor_id,@payment_id=NULL,@verified_result=N'{"status":"CAPTURED"}',@ticket_codes=@ticket_codes;
END;
GO
GRANT EXECUTE ON dbo.usp_CompleteFreeOrder TO tc_buyer;
REVOKE EXECUTE ON dbo.usp_ApplyPaymentResult FROM tc_buyer;

GO
CREATE OR ALTER TRIGGER dbo.trg_SettlementItem_MirrorDelete ON dbo.tc_settlement_items AFTER DELETE AS
BEGIN SET NOCOUNT ON;
 DELETE s FROM dbo.tc_settlement_order_snapshots s JOIN deleted d ON d.id=s.id;
END;
GO
