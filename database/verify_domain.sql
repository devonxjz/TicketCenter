-- Run only in a newly installed, empty verification database.
-- This script commits fixtures because routines own their business transactions.
-- Use a database named TicketsCenter_verify_* and remove it after verification.
SET NOCOUNT ON;
IF DB_NAME() NOT LIKE N'TicketsCenter_verify[_]%' THROW 51000,N'Use a dedicated verification database.',1;
IF EXISTS(SELECT 1 FROM dbo.users) THROW 51000,N'Verification database must be empty.',1;
GO
DECLARE @now DATETIME2(6)=SYSUTCDATETIME(),@buyer BIGINT,@org BIGINT,@rule BIGINT,@event BIGINT,@seated BIGINT,@standing BIGINT;
DECLARE @sale_end DATETIME2(6)=DATEADD(DAY,1,@now),@start DATETIME2(6)=DATEADD(DAY,2,@now),@end DATETIME2(6)=DATEADD(DAY,3,@now);
INSERT dbo.users(email,user_name,full_name,phone,status,email_verified,platform_role)
VALUES(N'buyer@example.test',N'buyer',N'Test Buyer',NULL,N'ACTIVE',0,N'USER');
SET @buyer=SCOPE_IDENTITY();
IF dbo.fn_user_is_eligible_buyer(@buyer)<>0 THROW 51000,N'Unverified buyer incorrectly eligible.',1;
EXEC dbo.sp_user_verify_email @user_id=@buyer;
EXEC dbo.sp_user_update_profile @user_id=@buyer,@full_name=N'Updated Buyer',@phone=N'0900000000';
IF dbo.fn_user_is_eligible_buyer(@buyer)<>1 THROW 51000,N'Verified buyer not eligible.',1;
INSERT dbo.organizations(name,contact_email,contact_phone,description,requester_id,status,rejection_reason)
VALUES(N'Test Organizer',N'organizer@example.test',NULL,NULL,@buyer,N'DRAFT',NULL);
SET @org=SCOPE_IDENTITY();
EXEC dbo.sp_organization_submit_for_approval @organization_id=@org;
EXEC dbo.sp_organization_approve @organization_id=@org;
EXEC dbo.sp_user_assign_role @user_id=@buyer,@organization_id=@org,@role=N'OWNER';
EXEC dbo.sp_user_assign_role @user_id=@buyer,@organization_id=@org,@role=N'MANAGER';
IF (SELECT COUNT(*) FROM dbo.user_organization_roles WHERE user_id=@buyer)<>1 THROW 51000,N'Role assignment duplicated membership.',1;
INSERT dbo.commission_rules(organization_id,rate_percent,fixed_fee,effective_from,effective_to)
VALUES(@org,5,2,DATEADD(DAY,-1,@now),DATEADD(DAY,10,@now));
SET @rule=SCOPE_IDENTITY();
IF dbo.fn_commission_rule_calculate_fee(@rule,100)<>7 THROW 51000,N'Commission calculation incorrect.',1;
INSERT dbo.events(organization_id,title,description,category,venue_name,venue_address,cover_image_url,sale_start,sale_end,start_time,end_time,status,rejection_reason,commission_rule_id)
VALUES(@org,N'Test Event',N'Test',N'CONCERT',N'Hall',N'Address',NULL,DATEADD(HOUR,-1,@now),@sale_end,@start,@end,N'DRAFT',NULL,NULL);
SET @event=SCOPE_IDENTITY();
EXEC dbo.sp_event_add_zone @event_id=@event,@name=N'Seated',@type=N'SEATED',@price=100.01,@standing_capacity=NULL,@zone_id=@seated OUTPUT;
EXEC dbo.sp_zone_configure_seating @zone_id=@seated,@rows=1,@seats_per_row=3;
EXEC dbo.sp_event_add_zone @event_id=@event,@name=N'Standing',@type=N'STANDING',@price=50.03,@standing_capacity=3,@zone_id=@standing OUTPUT;
EXEC dbo.sp_event_submit_for_approval @event_id=@event;
EXEC dbo.sp_event_publish @event_id=@event,@rule_id=@rule,@now=@now;
IF dbo.fn_zone_get_capacity(@seated)<>3 OR dbo.fn_zone_get_available(@standing)<>3 THROW 51000,N'Capacity incorrect.',1;
IF dbo.fn_event_is_sale_active(@event,@sale_end)<>0 THROW 51000,N'Sale end must be exclusive.',1;
DECLARE @coupon BIGINT;
INSERT dbo.coupons(organization_id,code,discount_type,fixed_amount,percentage,valid_from,valid_to,is_active,max_uses)
VALUES(@org,N'PERCENT5',N'PERCENTAGE',NULL,5,DATEADD(HOUR,-1,@now),@sale_end,1,1);
SET @coupon=SCOPE_IDENTITY();
DECLARE @hold BIGINT,@item BIGINT,@seat_item BIGINT,@seat BIGINT,@order BIGINT,@payment BIGINT,@expiry DATETIME2(6)=DATEADD(MINUTE,15,@now);
SELECT @seat=MIN(id) FROM dbo.seats WHERE zone_id=@seated;
EXEC dbo.sp_ticket_hold_create @buyer_id=@buyer,@event_id=@event,@expires_at=@expiry,@hold_id=@hold OUTPUT,@now=@now;
EXEC dbo.sp_ticket_hold_add_item @hold_id=@hold,@zone_id=@seated,@seat_id=@seat,@quantity=1,@item_id=@seat_item OUTPUT;
EXEC dbo.sp_zone_hold_standing @zone_id=@standing,@quantity=2,@hold_id=@hold,@item_id=@item OUTPUT;
IF dbo.fn_zone_get_available(@seated)<>2 OR dbo.fn_zone_get_available(@standing)<>1 THROW 51000,N'Holds did not reserve inventory.',1;
EXEC dbo.sp_seat_hold @item_id=@seat_item;
IF dbo.fn_ticket_hold_item_line_total(@item)<>100.06 THROW 51000,N'Hold item total incorrect.',1;
EXEC dbo.sp_order_from_hold @hold_id=@hold,@order_id=@order OUTPUT,@now=@now;
DECLARE @same_order BIGINT=999;
EXEC dbo.sp_order_from_hold @hold_id=@hold,@order_id=@same_order OUTPUT,@now=@now;
IF @same_order<>@order THROW 51000,N'Order creation is not idempotent.',1;
EXEC dbo.sp_order_apply_coupon @order_id=@order,@coupon_id=@coupon,@now=@now;
IF dbo.fn_order_subtotal(@order)<>200.07 OR dbo.fn_order_total(@order)<>190.07 THROW 51000,N'Order totals incorrect.',1;
IF dbo.fn_coupon_is_active_at(@coupon,@now)<>0 THROW 51000,N'Coupon maximum uses not reserved.',1;
EXEC dbo.sp_payment_start @order_id=@order,@txn_ref=N'PAY-1',@payment_id=@payment OUTPUT;
EXEC dbo.sp_payment_mark_captured @payment_id=@payment,@transaction_no=N'CAPTURE-1',@paid_at=@now;
EXEC dbo.sp_payment_mark_captured @payment_id=@payment,@transaction_no=N'CAPTURE-1',@paid_at=@now;
IF (SELECT COUNT(*) FROM dbo.v_tickets WHERE order_id=@order)<>3 THROW 51000,N'Capture issued duplicate/missing tickets.',1;
IF (SELECT SUM(paid_amount) FROM dbo.v_tickets WHERE order_id=@order)<>190.07 THROW 51000,N'Ticket allocation lost cents.',1;
IF (SELECT standing_held FROM dbo.zones WHERE id=@standing)<>0 OR (SELECT standing_sold FROM dbo.zones WHERE id=@standing)<>2 THROW 51000,N'Hold consume did not sell stock.',1;
DECLARE @ticket BIGINT,@refund BIGINT,@ids NVARCHAR(MAX);
SELECT @ticket=id FROM dbo.v_tickets WHERE order_id=@order AND seat_id=@seat;
SET @ids=CONCAT(N'[',@ticket,N']');
EXEC dbo.sp_refund_request_for_tickets @order_id=@order,@ticket_ids=@ids,@reason_type=N'BUYER_REQUEST',@reason=NULL,@refund_id=@refund OUTPUT,@now=@now;
EXEC dbo.sp_refund_reject @refund_id=@refund,@reason=N'First request rejected.';
IF (SELECT status FROM dbo.tickets WHERE id=@ticket)<>N'VALID' THROW 51000,N'Rejection did not restore the ticket.',1;
EXEC dbo.sp_ticket_restore_after_rejection @ticket_id=@ticket;
EXEC dbo.sp_refund_request_for_tickets @order_id=@order,@ticket_ids=@ids,@reason_type=N'BUYER_REQUEST',@reason=NULL,@refund_id=@refund OUTPUT,@now=@now;
EXEC dbo.sp_refund_approve @refund_id=@refund;
EXEC dbo.sp_refund_begin_attempt @refund_id=@refund,@attempt_id=N'REFUND-1';
EXEC dbo.sp_refund_record_attempt_result @refund_id=@refund,@attempt_id=N'REFUND-1',@result=N'UNKNOWN';
IF (SELECT status FROM dbo.seats WHERE id=@seat)<>N'SOLD' THROW 51000,N'Unknown refund returned stock prematurely.',1;
EXEC dbo.sp_refund_record_attempt_result @refund_id=@refund,@attempt_id=N'REFUND-1',@result=N'FAILED',@reference=N'RF-FAILED';
EXEC dbo.sp_refund_begin_attempt @refund_id=@refund,@attempt_id=N'REFUND-2';
EXEC dbo.sp_refund_record_attempt_result @refund_id=@refund,@attempt_id=N'REFUND-2',@result=N'SUCCEEDED',@reference=N'RF-SUCCESS';
EXEC dbo.sp_refund_record_attempt_result @refund_id=@refund,@attempt_id=N'REFUND-2',@result=N'SUCCEEDED',@reference=N'RF-SUCCESS';
IF (SELECT status FROM dbo.seats WHERE id=@seat)<>N'AVAILABLE' OR dbo.fn_zone_get_available(@seated)<>3 THROW 51000,N'Refund did not return exactly one seat.',1;
EXEC dbo.sp_seat_return_to_inventory @ticket_id=@ticket;
-- Release a hold without an order, including duplicate release.
DECLARE @unused_hold BIGINT,@unused_item BIGINT;
EXEC dbo.sp_ticket_hold_create @buyer_id=@buyer,@event_id=@event,@expires_at=@expiry,@hold_id=@unused_hold OUTPUT,@now=@now;
EXEC dbo.sp_ticket_hold_add_item @hold_id=@unused_hold,@zone_id=@standing,@seat_id=NULL,@quantity=1,@item_id=@unused_item OUTPUT;
EXEC dbo.sp_ticket_hold_release @hold_id=@unused_hold;
EXEC dbo.sp_ticket_hold_release @hold_id=@unused_hold;
IF dbo.fn_zone_get_available(@standing)<>1 THROW 51000,N'Hold release duplicated standing inventory.',1;
-- Expire an unpaid order, then compensate a late capture.
DECLARE @late_hold BIGINT,@late_item BIGINT,@late_order BIGINT,@late_payment BIGINT,@after_expiry DATETIME2(6)=DATEADD(SECOND,1,@expiry);
EXEC dbo.sp_ticket_hold_create @buyer_id=@buyer,@event_id=@event,@expires_at=@expiry,@hold_id=@late_hold OUTPUT,@now=@now;
EXEC dbo.sp_ticket_hold_add_item @hold_id=@late_hold,@zone_id=@standing,@seat_id=NULL,@quantity=1,@item_id=@late_item OUTPUT;
EXEC dbo.sp_order_from_hold @hold_id=@late_hold,@order_id=@late_order OUTPUT,@now=@now;
EXEC dbo.sp_payment_start @order_id=@late_order,@txn_ref=N'LATE-1',@payment_id=@late_payment OUTPUT;
EXEC dbo.sp_order_expire @order_id=@late_order,@now=@after_expiry;
EXEC dbo.sp_payment_mark_captured @payment_id=@late_payment,@transaction_no=N'LATE-CAPTURE',@paid_at=@after_expiry;
SELECT @refund=id FROM dbo.refunds WHERE payment_id=@late_payment AND purpose=N'COMPENSATION';
IF @refund IS NULL OR EXISTS(SELECT 1 FROM dbo.v_tickets WHERE order_id=@late_order) THROW 51000,N'Late capture was not compensated.',1;
EXEC dbo.sp_refund_begin_attempt @refund_id=@refund,@attempt_id=N'COMP-1';
EXEC dbo.sp_refund_record_attempt_result @refund_id=@refund,@attempt_id=N'COMP-1',@result=N'SUCCEEDED',@reference=N'COMP-SUCCESS';
DECLARE @settlement BIGINT,@after_event DATETIME2(6)=DATEADD(SECOND,1,@end);
EXEC dbo.sp_settlement_create @event_id=@event,@settlement_id=@settlement OUTPUT;
EXEC dbo.sp_settlement_confirm @settlement_id=@settlement,@now=@after_event;
IF (SELECT gross_revenue FROM dbo.settlements WHERE id=@settlement)<>190.07 THROW 51000,N'Compensation inflated gross revenue.',1;
IF (SELECT total_refund FROM dbo.settlements WHERE id=@settlement)<>95.01 THROW 51000,N'Refund total incorrect.',1;
IF (SELECT net_payable FROM dbo.settlements WHERE id=@settlement)<>88.31 THROW 51000,N'Settlement commission/payable incorrect.',1;
EXEC dbo.sp_settlement_begin_payout @settlement_id=@settlement,@payout_id=N'PAYOUT-1',@amount=40;
EXEC dbo.sp_settlement_begin_payout @settlement_id=@settlement,@payout_id=N'PAYOUT-1',@amount=40;
EXEC dbo.sp_settlement_record_payout_result @settlement_id=@settlement,@payout_id=N'PAYOUT-1',@amount=40,@result=N'UNKNOWN';
IF (SELECT pending_amount FROM dbo.settlements WHERE id=@settlement)<>40 THROW 51000,N'Unknown payout lost its reservation.',1;
EXEC dbo.sp_settlement_record_payout_result @settlement_id=@settlement,@payout_id=N'PAYOUT-1',@amount=40,@result=N'SUCCEEDED',@reference=N'PO-SUCCESS';
EXEC dbo.sp_settlement_record_payout_result @settlement_id=@settlement,@payout_id=N'PAYOUT-1',@amount=40,@result=N'SUCCEEDED',@reference=N'PO-SUCCESS';
EXEC dbo.sp_settlement_begin_payout @settlement_id=@settlement,@payout_id=N'PAYOUT-2',@amount=48.31;
EXEC dbo.sp_settlement_record_payout_result @settlement_id=@settlement,@payout_id=N'PAYOUT-2',@amount=48.31,@result=N'FAILED',@reference=N'PO-FAILED';
IF (SELECT paid_amount FROM dbo.settlements WHERE id=@settlement)<>40 OR (SELECT pending_amount FROM dbo.settlements WHERE id=@settlement)<>0
    OR (SELECT available_to_pay FROM dbo.settlements WHERE id=@settlement)<>48.31 THROW 51000,N'Payout retry or failure corrupted balances.',1;
PRINT N'PASS: seated/standing sale, cents allocation, coupon reservation, refund retry, late capture compensation and payout idempotency.';
GO
DECLARE @now DATETIME2(6)=SYSUTCDATETIME(),@buyer BIGINT,@org BIGINT,@rule BIGINT,@event BIGINT,@seat_zone BIGINT,@stand_zone BIGINT;
SELECT @buyer=id FROM dbo.users WHERE user_name=N'buyer';
SELECT @org=id FROM dbo.organizations WHERE name=N'Test Organizer';
SELECT @rule=MIN(id) FROM dbo.commission_rules WHERE organization_id=@org;
DECLARE @original BIGINT=(SELECT id FROM dbo.events WHERE title=N'Test Event'),@expiry DATETIME2(6)=DATEADD(MINUTE,15,@now);
-- A multi-row failure must roll back both the seat and the standing reservation.
DECLARE @hold BIGINT,@item BIGINT,@seat BIGINT,@original_standing BIGINT,@original_seated BIGINT,@blocked BIT=0;
SELECT @original_standing=id FROM dbo.zones WHERE event_id=@original AND type=N'STANDING';
SELECT @original_seated=id FROM dbo.zones WHERE event_id=@original AND type=N'SEATED';
SELECT @seat=MIN(id) FROM dbo.seats WHERE zone_id=@original_seated AND status=N'AVAILABLE';
EXEC dbo.sp_ticket_hold_create @buyer_id=@buyer,@event_id=@original,@expires_at=@expiry,@hold_id=@hold OUTPUT,@now=@now;
BEGIN TRY
    INSERT dbo.ticket_hold_items(ticket_hold_id,zone_id,seat_id,quantity,unit_price,inventory_state)
    VALUES(@hold,@original_seated,@seat,1,100.01,N'HELD'),(@hold,@original_standing,NULL,2,50.03,N'HELD');
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() NOT IN(51000,547) THROW;
    SET @blocked=1;
END CATCH;
IF @blocked=0 OR EXISTS(SELECT 1 FROM dbo.ticket_hold_items WHERE ticket_hold_id=@hold)
    OR (SELECT status FROM dbo.seats WHERE id=@seat)<>N'AVAILABLE' OR dbo.fn_zone_get_available(@original_standing)<>1
    THROW 51000,N'Multi-row oversell did not roll back atomically.',1;
EXEC dbo.sp_ticket_hold_release @hold_id=@hold;
-- A payout larger than the remaining balance must not create a ledger row.
DECLARE @settlement BIGINT=(SELECT id FROM dbo.settlements WHERE event_id=@original);
SET @blocked=0;
BEGIN TRY
    EXEC dbo.sp_settlement_begin_payout @settlement_id=@settlement,@payout_id=N'OVERPAY',@amount=48.32;
END TRY BEGIN CATCH IF ERROR_NUMBER()<>51000 THROW; SET @blocked=1; END CATCH;
IF @blocked=0 OR EXISTS(SELECT 1 FROM dbo.settlement_payouts WHERE payout_id=N'OVERPAY') THROW 51000,N'Overpayment was not rejected.',1;
-- Invalid direct status changes must be rejected for an entire multi-row UPDATE.
SET @blocked=0;
BEGIN TRY UPDATE dbo.seats SET status=N'SOLD' WHERE zone_id=@original_seated; END TRY
BEGIN CATCH IF ERROR_NUMBER()<>51000 THROW; SET @blocked=1; END CATCH;
IF @blocked=0 OR dbo.fn_zone_get_available(@original_seated)<>3 THROW 51000,N'Invalid multi-row transition was not rejected.',1;
INSERT dbo.events(organization_id,title,description,category,venue_name,venue_address,cover_image_url,sale_start,sale_end,start_time,end_time,status,rejection_reason,commission_rule_id)
SELECT organization_id,N'Cancellation Event',description,category,venue_name,venue_address,NULL,sale_start,sale_end,start_time,end_time,N'DRAFT',NULL,NULL FROM dbo.events WHERE id=@original;
SET @event=SCOPE_IDENTITY();
EXEC dbo.sp_event_add_zone @event_id=@event,@name=N'Free',@type=N'STANDING',@price=0,@standing_capacity=2,@zone_id=@seat_zone OUTPUT;
EXEC dbo.sp_event_add_zone @event_id=@event,@name=N'Paid',@type=N'STANDING',@price=10,@standing_capacity=3,@zone_id=@stand_zone OUTPUT;
EXEC dbo.sp_event_submit_for_approval @event_id=@event;
EXEC dbo.sp_event_publish @event_id=@event,@rule_id=@rule,@now=@now;
DECLARE @free_hold BIGINT,@free_order BIGINT,@free_item BIGINT,@free_ticket BIGINT;
EXEC dbo.sp_ticket_hold_create @buyer_id=@buyer,@event_id=@event,@expires_at=@expiry,@hold_id=@free_hold OUTPUT,@now=@now;
EXEC dbo.sp_ticket_hold_add_item @hold_id=@free_hold,@zone_id=@seat_zone,@seat_id=NULL,@quantity=1,@item_id=@free_item OUTPUT;
EXEC dbo.sp_order_from_hold @hold_id=@free_hold,@order_id=@free_order OUTPUT,@now=@now;
EXEC dbo.sp_order_mark_paid @order_id=@free_order,@payment_id=NULL,@now=@now;
SELECT @free_ticket=id FROM dbo.v_tickets WHERE order_id=@free_order;
IF (SELECT paid_amount FROM dbo.tickets WHERE id=@free_ticket)<>0 THROW 51000,N'Free ticket amount is not zero.',1;
DECLARE @paid_hold BIGINT,@paid_order BIGINT,@paid_item BIGINT,@paid_ticket BIGINT,@payment BIGINT,@refund BIGINT,@ids NVARCHAR(MAX);
EXEC dbo.sp_ticket_hold_create @buyer_id=@buyer,@event_id=@event,@expires_at=@expiry,@hold_id=@paid_hold OUTPUT,@now=@now;
EXEC dbo.sp_ticket_hold_add_item @hold_id=@paid_hold,@zone_id=@stand_zone,@seat_id=NULL,@quantity=1,@item_id=@paid_item OUTPUT;
EXEC dbo.sp_order_from_hold @hold_id=@paid_hold,@order_id=@paid_order OUTPUT,@now=@now;
EXEC dbo.sp_payment_start @order_id=@paid_order,@txn_ref=N'CANCEL-PAY',@payment_id=@payment OUTPUT;
EXEC dbo.sp_payment_mark_captured @payment_id=@payment,@transaction_no=N'CANCEL-CAPTURE',@paid_at=@now;
SELECT @paid_ticket=id FROM dbo.v_tickets WHERE order_id=@paid_order;
SET @ids=CONCAT(N'[',@paid_ticket,N']');
EXEC dbo.sp_refund_request_for_tickets @order_id=@paid_order,@ticket_ids=@ids,@reason_type=N'BUYER_REQUEST',@reason=NULL,@refund_id=@refund OUTPUT,@now=@now;
DECLARE @unpaid_hold BIGINT,@unpaid_order BIGINT,@unpaid_item BIGINT,@loose_hold BIGINT,@loose_item BIGINT;
EXEC dbo.sp_ticket_hold_create @buyer_id=@buyer,@event_id=@event,@expires_at=@expiry,@hold_id=@unpaid_hold OUTPUT,@now=@now;
EXEC dbo.sp_ticket_hold_add_item @hold_id=@unpaid_hold,@zone_id=@stand_zone,@seat_id=NULL,@quantity=1,@item_id=@unpaid_item OUTPUT;
EXEC dbo.sp_order_from_hold @hold_id=@unpaid_hold,@order_id=@unpaid_order OUTPUT,@now=@now;
EXEC dbo.sp_ticket_hold_create @buyer_id=@buyer,@event_id=@event,@expires_at=@expiry,@hold_id=@loose_hold OUTPUT,@now=@now;
EXEC dbo.sp_ticket_hold_add_item @hold_id=@loose_hold,@zone_id=@stand_zone,@seat_id=NULL,@quantity=1,@item_id=@loose_item OUTPUT;
EXEC dbo.sp_event_cancel @event_id=@event,@now=@now;
EXEC dbo.sp_event_cancel @event_id=@event,@now=@now;
IF (SELECT status FROM dbo.orders WHERE id=@unpaid_order)<>N'CANCELLED'
    OR EXISTS(SELECT 1 FROM dbo.ticket_holds WHERE event_id=@event AND status=N'ACTIVE') THROW 51000,N'Cancellation left unpaid inventory reserved.',1;
IF (SELECT status FROM dbo.tickets WHERE id=@free_ticket)<>N'REFUNDED' OR dbo.fn_zone_get_available(@seat_zone)<>2 THROW 51000,N'Cancellation did not finish a zero-value refund.',1;
IF (SELECT status FROM dbo.refunds WHERE id=@refund)<>N'APPROVED' OR (SELECT reason_type FROM dbo.refunds WHERE id=@refund)<>N'EVENT_CANCELLED'
    THROW 51000,N'Cancellation did not adopt the existing refund.',1;
EXEC dbo.sp_refund_begin_attempt @refund_id=@refund,@attempt_id=N'CANCEL-REFUND';
EXEC dbo.sp_refund_record_attempt_result @refund_id=@refund,@attempt_id=N'CANCEL-REFUND',@result=N'SUCCEEDED',@reference=N'CANCEL-SUCCESS';
EXEC dbo.sp_refund_record_attempt_result @refund_id=@refund,@attempt_id=N'CANCEL-REFUND',@result=N'SUCCEEDED',@reference=N'CANCEL-SUCCESS';
IF dbo.fn_zone_get_available(@stand_zone)<>3 THROW 51000,N'Standing refund returned stock more than once.',1;
EXEC dbo.sp_zone_return_sold_standing @zone_id=@stand_zone,@quantity=1,@refund_id=@refund;
DECLARE @cancel_settlement BIGINT;
EXEC dbo.sp_settlement_create @event_id=@event,@settlement_id=@cancel_settlement OUTPUT;
EXEC dbo.sp_settlement_confirm @settlement_id=@cancel_settlement,@now=@now;
IF (SELECT net_payable FROM dbo.settlements WHERE id=@cancel_settlement)<>0 THROW 51000,N'Cancelled event still has revenue payable.',1;
PRINT N'PASS: multi-row rollback, status guards, overpayment rejection, free orders and event cancellation with existing refunds.';
GO
DECLARE @now DATETIME2(6)=SYSUTCDATETIME(),@original BIGINT=(SELECT id FROM dbo.events WHERE title=N'Test Event'),@buyer BIGINT=(SELECT id FROM dbo.users WHERE user_name=N'buyer');
DECLARE @event BIGINT,@zone BIGINT,@hold BIGINT,@item BIGINT,@order BIGINT,@payment BIGINT,@old_payment BIGINT,@ticket BIGINT,@refund BIGINT,@expiry DATETIME2(6)=DATEADD(MINUTE,15,@now);
DECLARE @rule BIGINT=(SELECT MIN(id) FROM dbo.commission_rules),@start DATETIME2(6);
INSERT dbo.events(organization_id,title,description,category,venue_name,venue_address,cover_image_url,sale_start,sale_end,start_time,end_time,status,rejection_reason,commission_rule_id)
SELECT organization_id,N'Check-in Event',description,category,venue_name,venue_address,NULL,sale_start,sale_end,start_time,end_time,N'DRAFT',NULL,NULL FROM dbo.events WHERE id=@original;
SET @event=SCOPE_IDENTITY();
EXEC dbo.sp_event_add_zone @event_id=@event,@name=N'Check-in',@type=N'STANDING',@price=25,@standing_capacity=2,@zone_id=@zone OUTPUT;
EXEC dbo.sp_event_submit_for_approval @event_id=@event;
EXEC dbo.sp_event_publish @event_id=@event,@rule_id=@rule,@now=@now;
EXEC dbo.sp_ticket_hold_create @buyer_id=@buyer,@event_id=@event,@expires_at=@expiry,@hold_id=@hold OUTPUT,@now=@now;
EXEC dbo.sp_ticket_hold_add_item @hold_id=@hold,@zone_id=@zone,@seat_id=NULL,@quantity=1,@item_id=@item OUTPUT;
EXEC dbo.sp_order_from_hold @hold_id=@hold,@order_id=@order OUTPUT,@now=@now;
EXEC dbo.sp_payment_start @order_id=@order,@txn_ref=N'OLD-PAY',@payment_id=@old_payment OUTPUT;
EXEC dbo.sp_payment_mark_failed @payment_id=@old_payment;
EXEC dbo.sp_payment_start @order_id=@order,@txn_ref=N'NEW-PAY',@payment_id=@payment OUTPUT;
EXEC dbo.sp_payment_mark_captured @payment_id=@payment,@transaction_no=N'NEW-CAPTURE',@paid_at=@now;
EXEC dbo.sp_payment_mark_captured @payment_id=@old_payment,@transaction_no=N'OLD-CAPTURE',@paid_at=@now;
EXEC dbo.sp_payment_mark_captured @payment_id=@old_payment,@transaction_no=N'OLD-CAPTURE',@paid_at=@now;
IF (SELECT COUNT(*) FROM dbo.v_tickets WHERE order_id=@order)<>1 OR (SELECT accepted_payment_id FROM dbo.orders WHERE id=@order)<>@payment
    THROW 51000,N'Second capture changed accepted payment or issued tickets.',1;
IF (SELECT COUNT(*) FROM dbo.refunds WHERE payment_id=@old_payment AND purpose=N'COMPENSATION')<>1 THROW 51000,N'Second capture duplicated compensation.',1;
SELECT @refund=id FROM dbo.refunds WHERE payment_id=@old_payment AND purpose=N'COMPENSATION';
EXEC dbo.sp_refund_begin_attempt @refund_id=@refund,@attempt_id=N'OLD-COMP';
EXEC dbo.sp_refund_record_attempt_result @refund_id=@refund,@attempt_id=N'OLD-COMP',@result=N'SUCCEEDED',@reference=N'OLD-COMP-SUCCESS';
SELECT @ticket=id FROM dbo.v_tickets WHERE order_id=@order;
DECLARE @blocked BIT=0;
BEGIN TRY EXEC dbo.sp_ticket_check_in @ticket_id=@ticket,@event_id=@event,@now=@now; END TRY
BEGIN CATCH IF ERROR_NUMBER()<>51000 THROW; SET @blocked=1; END CATCH;
IF @blocked=0 THROW 51000,N'Check-in opened too early.',1;
SELECT @start=start_time FROM dbo.events WHERE id=@event;
EXEC dbo.sp_ticket_check_in @ticket_id=@ticket,@event_id=@event,@now=@start;
SET @blocked=0;
BEGIN TRY EXEC dbo.sp_ticket_check_in @ticket_id=@ticket,@event_id=@event,@now=@start; END TRY
BEGIN CATCH IF ERROR_NUMBER()<>51000 THROW; SET @blocked=1; END CATCH;
IF @blocked=0 OR (SELECT status FROM dbo.tickets WHERE id=@ticket)<>N'CHECKED_IN' THROW 51000,N'Ticket checked in twice.',1;
PRINT N'PASS: duplicate captures are compensated and check-in rejects early/repeated use.';
GO
