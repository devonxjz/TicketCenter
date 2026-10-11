-- Seekable serializable ranges and database backstops. No unique CAPTURED/order:
-- SPEC 6.6 requires persisting every real capture and compensating the extra one.
SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
GO
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_payments') AND name='IX_Payment_Order_Status')
 CREATE INDEX IX_Payment_Order_Status ON dbo.tc_payments(order_id,status) INCLUDE(amount,captured_at);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_refunds') AND name='IX_Refund_Payment')
 CREATE INDEX IX_Refund_Payment ON dbo.tc_refunds(payment_id,status) INCLUDE(amount,purpose,refund_request_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_order_items') AND name='IX_OrderItem_Order')
 CREATE INDEX IX_OrderItem_Order ON dbo.tc_order_items(order_id) INCLUDE(zone_id,seat_id,quantity,unit_price);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_refund_requests') AND name='IX_RefundRequest_Order')
 CREATE INDEX IX_RefundRequest_Order ON dbo.tc_refund_requests(order_id,status);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_ticket_hold_items') AND name='IX_HoldItem_Hold')
 CREATE INDEX IX_HoldItem_Hold ON dbo.tc_ticket_hold_items(hold_id) INCLUDE(zone_id,seat_id,quantity);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_settlement_items') AND name='IX_SettlementItem_Settlement')
 CREATE INDEX IX_SettlementItem_Settlement ON dbo.tc_settlement_items(settlement_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_organization_memberships') AND name='IX_Membership_Org')
 CREATE INDEX IX_Membership_Org ON dbo.tc_organization_memberships(organization_id,role,active) INCLUDE(user_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_outbox') AND name='IX_Outbox_Aggregate')
 CREATE INDEX IX_Outbox_Aggregate ON dbo.tc_outbox(aggregate_id,event_type) INCLUDE(status);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_ticket_holds') AND name='IX_TicketHold_Event_Status')
 CREATE INDEX IX_TicketHold_Event_Status ON dbo.tc_ticket_holds(event_id,status);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_payments') AND name='UX_Payments_OneProviderRef')
 CREATE UNIQUE INDEX UX_Payments_OneProviderRef ON dbo.tc_payments(provider_reference) WHERE provider_reference IS NOT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_refunds') AND name='UX_Refunds_OneActivePerRequest')
 CREATE UNIQUE INDEX UX_Refunds_OneActivePerRequest ON dbo.tc_refunds(refund_request_id) WHERE refund_request_id IS NOT NULL AND status IN('PENDING','UNKNOWN','SUCCEEDED');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_refunds') AND name='UX_Refunds_OneCompensationPerPayment')
 CREATE UNIQUE INDEX UX_Refunds_OneCompensationPerPayment ON dbo.tc_refunds(payment_id) WHERE purpose='PAYMENT_COMPENSATION' AND status IN('PENDING','UNKNOWN','SUCCEEDED');
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_check_ins') AND name='UX_CheckIns_OneSuccessPerTicket')
 CREATE UNIQUE INDEX UX_CheckIns_OneSuccessPerTicket ON dbo.tc_check_ins(ticket_id) WHERE ticket_id IS NOT NULL AND result='SUCCESS';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_gateway_refund_receipts') AND name='IX_GatewayRefundReceipts_Reconcile')
 CREATE INDEX IX_GatewayRefundReceipts_Reconcile ON dbo.tc_gateway_refund_receipts(payment_id,resolution) INCLUDE(refund_id,reason);
GO
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_Organization_Status_Report')
 ALTER TABLE dbo.tc_organizations WITH CHECK ADD CONSTRAINT CK_Organization_Status_Report CHECK(status IN('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED'));
GO
-- Keep IDs, attempts and audit history. Invalidate expired/superseded OTPs before adding the concurrency backstop.
UPDATE dbo.tc_otps SET invalidated_at=SYSUTCDATETIME() WHERE consumed_at IS NULL AND invalidated_at IS NULL AND expires_at<=SYSUTCDATETIME();
;WITH ranked AS(SELECT id,ROW_NUMBER() OVER(PARTITION BY email_normalized,purpose ORDER BY created_at DESC,id DESC) rn FROM dbo.tc_otps WHERE consumed_at IS NULL AND invalidated_at IS NULL)
UPDATE o SET invalidated_at=SYSUTCDATETIME() FROM dbo.tc_otps o JOIN ranked r ON r.id=o.id WHERE r.rn>1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_otps') AND name='UX_Otp_OneCurrentPerPurpose')
 CREATE UNIQUE INDEX UX_Otp_OneCurrentPerPurpose ON dbo.tc_otps(email_normalized,purpose) WHERE consumed_at IS NULL AND invalidated_at IS NULL;
GO
-- Owner procedures and ORM insert paths both retain their column contracts.
-- This trigger rejects cross-event zones and cross-zone seats rather than silently repairing bad data.
CREATE OR ALTER TRIGGER dbo.trg_OrderItem_ValidateEventSeat ON dbo.tc_order_items AFTER INSERT,UPDATE AS
BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.tc_orders o ON o.id=i.order_id JOIN dbo.tc_zones z ON z.id=i.zone_id LEFT JOIN dbo.tc_seats s ON s.id=i.seat_id
 WHERE z.event_id<>o.event_id OR (i.seat_id IS NOT NULL AND s.zone_id<>i.zone_id)) THROW 51820,'Order item zone or seat belongs to another event',1;
END;
GO
IF COL_LENGTH('dbo.tc_tickets','seat_id') IS NULL ALTER TABLE dbo.tc_tickets ADD seat_id uniqueidentifier NULL;
GO
UPDATE t SET seat_id=i.seat_id FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id WHERE t.seat_id IS NULL AND i.seat_id IS NOT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_Ticket_Seat_Report') ALTER TABLE dbo.tc_tickets WITH CHECK ADD CONSTRAINT FK_Ticket_Seat_Report FOREIGN KEY(seat_id) REFERENCES dbo.tc_seats(id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.tc_tickets') AND name='UX_Tickets_OneLiveSeat')
 CREATE UNIQUE INDEX UX_Tickets_OneLiveSeat ON dbo.tc_tickets(seat_id) WHERE seat_id IS NOT NULL AND status IN('ACTIVE','USED','REFUND_PENDING');
GO
CREATE OR ALTER TRIGGER dbo.trg_Ticket_AssignSeat ON dbo.tc_tickets AFTER INSERT,UPDATE AS
BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted t JOIN dbo.tc_order_items i ON i.id=t.order_item_id WHERE t.seat_id IS NOT NULL AND (i.seat_id IS NULL OR t.seat_id<>i.seat_id)) THROW 51821,'Ticket seat must match issued order item',1;
 UPDATE t SET seat_id=i.seat_id FROM dbo.tc_tickets t JOIN inserted n ON n.id=t.id JOIN dbo.tc_order_items i ON i.id=t.order_item_id WHERE t.seat_id IS NULL AND i.seat_id IS NOT NULL;
END;
GO
