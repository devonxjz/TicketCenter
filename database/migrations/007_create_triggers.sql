/*
 * 007_create_triggers
 *
 * Creates or alters all 19 domain and consistency triggers.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* Trigger: trg_AuditLog_AppendOnly */
CREATE OR ALTER TRIGGER dbo.trg_AuditLog_AppendOnly ON dbo.tc_audit_logs
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted) THROW 51110, 'Audit log is append-only', 1;
END;

GO

/* Trigger: trg_CheckIn_MirrorCanonical */
CREATE OR ALTER TRIGGER dbo.trg_CheckIn_MirrorCanonical
ON dbo.tc_check_ins AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.tc_ticket_check_in_logs(id, event_id, ticket_id, actor_id, result, scanned_at)
    SELECT i.id, i.event_id, i.ticket_id, i.actor_id,
           CASE i.result
               WHEN 'NOT_FOUND' THEN 'INVALID_CODE'
               WHEN 'TOO_EARLY' THEN 'OUTSIDE_WINDOW'
               WHEN 'TOO_LATE' THEN 'OUTSIDE_WINDOW'
               WHEN 'NOT_ACTIVE' THEN 'INACTIVE_TICKET'
               WHEN 'EVENT_CANCELLED' THEN 'INACTIVE_TICKET'
               ELSE i.result
           END,
           i.scanned_at
    FROM inserted i
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_ticket_check_in_logs l WHERE l.id = i.id);
END;

GO

/* Trigger: trg_CommissionRule_ProtectAppliedTerms */
CREATE OR ALTER TRIGGER dbo.trg_CommissionRule_ProtectAppliedTerms ON dbo.tc_commission_rules
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        WHERE EXISTS (SELECT 1 FROM dbo.tc_events e WHERE e.commission_rule_id = d.id)
          AND (i.id IS NULL OR i.organization_id <> d.organization_id
               OR i.rate_percent <> d.rate_percent OR i.fixed_fee <> d.fixed_fee)
    ) THROW 51103, 'Applied commission terms are immutable', 1;
END;

GO

/* Trigger: trg_Coupon_ProtectUsageLimit */
CREATE OR ALTER TRIGGER dbo.trg_Coupon_ProtectUsageLimit ON dbo.tc_coupons
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        WHERE i.max_uses < (SELECT COUNT_BIG(*) FROM dbo.tc_coupon_redemptions r WITH (UPDLOCK, HOLDLOCK)
                            WHERE r.coupon_id = i.id AND r.status IN ('RESERVED', 'CONSUMED'))
    ) THROW 51107, 'Coupon max uses is below current usage', 1;
END;

GO

/* Trigger: trg_Event_AuditStatusChange */
CREATE OR ALTER TRIGGER dbo.trg_Event_AuditStatusChange ON dbo.tc_events
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
    SELECT TRY_CONVERT(uniqueidentifier, SESSION_CONTEXT(N'actor_id')), 'EVENT_STATUS_CHANGED', 'EVENT',
           i.id, CONCAT(N'{"from":"', d.status, N'","to":"', i.status, N'"}')
    FROM inserted i JOIN deleted d ON d.id = i.id WHERE i.status <> d.status;
END;

GO

/* Trigger: trg_Membership_ProtectLastManager */
CREATE OR ALTER TRIGGER dbo.trg_Membership_ProtectLastManager ON dbo.tc_organization_memberships
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM (SELECT organization_id FROM deleted UNION SELECT organization_id FROM inserted) x
        WHERE NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships m WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_users u ON u.id = m.user_id
            WHERE m.organization_id = x.organization_id AND m.role = 'MANAGER'
              AND m.active = 1 AND u.status = 'ACTIVE'
        )
    ) THROW 51106, 'Organization must retain an active manager', 1;
END;

GO

/* Trigger: trg_Payout_MirrorCanonical */
CREATE OR ALTER TRIGGER dbo.trg_Payout_MirrorCanonical
ON dbo.tc_payouts AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE l
    SET settlement_id = i.settlement_id, amount = i.amount, status = i.status, result = i.status,
        provider_reference = i.reference, processed_at = i.paid_at
    FROM dbo.tc_settlement_transfer_logs l JOIN inserted i ON i.id = l.id;

    INSERT dbo.tc_settlement_transfer_logs
        (id, settlement_id, amount, status, result, provider_reference, processed_at, created_at)
    SELECT i.id, i.settlement_id, i.amount, i.status, i.status, i.reference, i.paid_at, i.created_at
    FROM inserted i
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_settlement_transfer_logs l WHERE l.id = i.id);

    UPDATE s
    SET paid_amount = balances.paid_amount,
        pending_amount = balances.pending_amount,
        updated_at = SYSUTCDATETIME()
    FROM dbo.tc_settlements s
    JOIN (SELECT DISTINCT settlement_id FROM inserted) affected ON affected.settlement_id = s.id
    CROSS APPLY (
        SELECT COALESCE(SUM(CASE WHEN p.status = 'SUCCEEDED' THEN p.amount ELSE 0 END), 0) paid_amount,
               COALESCE(SUM(CASE WHEN p.status = 'PENDING' THEN p.amount ELSE 0 END), 0) pending_amount
        FROM dbo.tc_payouts p WHERE p.settlement_id = s.id
    ) balances;
END;

GO

/* Trigger: trg_RefundAttempt_MirrorCanonical */
CREATE OR ALTER TRIGGER dbo.trg_RefundAttempt_MirrorCanonical
ON dbo.tc_refunds AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    INSERT dbo.tc_refund_obligations
        (id, order_id, payment_id, amount, purpose, status, current_attempt_id,
         provider_reference, processed_at, created_at, updated_at)
    SELECT i.id, p.order_id, i.payment_id, i.amount, i.purpose,
           CASE i.status WHEN 'SUCCEEDED' THEN 'COMPLETED' WHEN 'FAILED' THEN 'RETRYABLE'
                         WHEN 'UNKNOWN' THEN 'NEEDS_RECONCILIATION' ELSE 'PROCESSING' END,
           i.id, i.provider_reference, i.processed_at, i.created_at, SYSUTCDATETIME()
    FROM inserted i
    JOIN dbo.tc_payments p ON p.id = i.payment_id
    WHERE i.refund_request_id IS NULL
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_obligations o WHERE o.id = i.id);

    UPDATE l
    SET status = i.status, result = i.status,
        provider_reference = i.provider_reference, processed_at = i.processed_at
    FROM dbo.tc_refund_transfer_logs l JOIN inserted i ON i.id = l.id;

    ;WITH attempts AS (
        SELECT i.*, COALESCE(o.id, i.id) obligation_id,
               ROW_NUMBER() OVER (PARTITION BY COALESCE(o.id, i.id) ORDER BY i.created_at, i.id)
               + COALESCE(existing.max_attempt_no, 0) attempt_no
        FROM inserted i
        LEFT JOIN dbo.tc_refund_obligations o ON o.legacy_request_id = i.refund_request_id
        OUTER APPLY (
            SELECT MAX(l.attempt_no) max_attempt_no
            FROM dbo.tc_refund_transfer_logs l
            WHERE l.refund_id = COALESCE(o.id, i.id)
        ) existing
    )
    INSERT dbo.tc_refund_transfer_logs
        (id, refund_id, attempt_no, status, result, provider_reference, processed_at, created_at)
    SELECT a.id, a.obligation_id, a.attempt_no, a.status, a.status,
           a.provider_reference, a.processed_at, a.created_at
    FROM attempts a
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.id = a.id);

    UPDATE o
    SET payment_id = i.payment_id,
        amount = i.amount,
        status = CASE i.status WHEN 'SUCCEEDED' THEN 'COMPLETED' WHEN 'FAILED' THEN 'RETRYABLE'
                               WHEN 'UNKNOWN' THEN 'NEEDS_RECONCILIATION' ELSE 'PROCESSING' END,
        current_attempt_id = i.id,
        provider_reference = i.provider_reference,
        processed_at = i.processed_at,
        updated_at = SYSUTCDATETIME()
    FROM dbo.tc_refund_obligations o
    JOIN inserted i ON o.id = i.id OR o.legacy_request_id = i.refund_request_id;
END;

GO

/* Trigger: trg_RefundRequestTicket_MirrorCanonical */
CREATE OR ALTER TRIGGER dbo.trg_RefundRequestTicket_MirrorCanonical
ON dbo.tc_refund_request_tickets AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE t
    SET is_open = i.is_open
    FROM dbo.tc_refund_tickets t
    JOIN dbo.tc_refund_obligations o ON o.id = t.refund_id
    JOIN inserted i ON i.refund_request_id = o.legacy_request_id AND i.ticket_id = t.ticket_id;

    INSERT dbo.tc_refund_tickets(refund_id, ticket_id, is_open, created_at)
    SELECT o.id, i.ticket_id, i.is_open, i.added_at
    FROM inserted i
    JOIN dbo.tc_refund_obligations o ON o.legacy_request_id = i.refund_request_id
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.tc_refund_tickets t WHERE t.refund_id = o.id AND t.ticket_id = i.ticket_id
    );

    UPDATE o
    SET amount = totals.amount, updated_at = SYSUTCDATETIME()
    FROM dbo.tc_refund_obligations o
    JOIN (SELECT DISTINCT refund_request_id FROM inserted) affected ON affected.refund_request_id = o.legacy_request_id
    CROSS APPLY (
        SELECT COALESCE(SUM(t.paid_amount), 0) amount
        FROM dbo.tc_refund_request_tickets rt
        JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
        WHERE rt.refund_request_id = o.legacy_request_id
    ) totals;
END;

GO

/* Trigger: trg_RefundRequest_MirrorCanonical */
/* Mirror proven legacy write paths into canonical persistence tables. */
CREATE OR ALTER TRIGGER dbo.trg_RefundRequest_MirrorCanonical
ON dbo.tc_refund_requests AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE o
    SET order_id = i.order_id,
        reason_type = i.reason_type,
        reason = i.reason,
        rejection_reason = i.rejection_reason,
        status = CASE
            WHEN i.status = 'COMPLETED' THEN 'COMPLETED'
            WHEN i.status = 'REJECTED' THEN 'REJECTED'
            WHEN i.status = 'APPROVED' AND o.current_attempt_id IS NULL THEN 'APPROVED'
            ELSE o.status
        END,
        updated_at = SYSUTCDATETIME()
    FROM dbo.tc_refund_obligations o
    JOIN inserted i ON i.id = o.legacy_request_id;

    INSERT dbo.tc_refund_obligations
        (id, legacy_request_id, order_id, payment_id, amount, purpose, reason_type,
         reason, rejection_reason, status, created_at, updated_at)
    SELECT i.id, i.id, i.order_id, captured.id, 0, 'CUSTOMER_REFUND', i.reason_type,
           i.reason, i.rejection_reason,
           CASE i.status WHEN 'APPROVED' THEN 'APPROVED' WHEN 'REJECTED' THEN 'REJECTED'
                         WHEN 'COMPLETED' THEN 'COMPLETED' ELSE 'REQUESTED' END,
           i.requested_at, SYSUTCDATETIME()
    FROM inserted i
    OUTER APPLY (
        SELECT TOP (1) p.id FROM dbo.tc_payments p
        WHERE p.order_id = i.order_id AND p.status = 'CAPTURED'
        ORDER BY p.captured_at, p.created_at, p.id
    ) captured
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_refund_obligations o WHERE o.legacy_request_id = i.id);
END;

GO

/* Trigger: trg_Seat_ProtectPublishedLayout */
CREATE OR ALTER TRIGGER dbo.trg_Seat_ProtectPublishedLayout ON dbo.tc_seats
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.tc_zones z ON z.id = i.zone_id JOIN dbo.tc_events e ON e.id = z.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED')
          AND (d.id IS NULL OR d.zone_id <> i.zone_id OR d.row_name <> i.row_name
               OR d.seat_number <> i.seat_number OR d.label <> i.label)
    ) OR EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        JOIN dbo.tc_zones z ON z.id = d.zone_id JOIN dbo.tc_events e ON e.id = z.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED') AND (i.id IS NULL OR i.zone_id <> d.zone_id)
    ) THROW 51102, 'Published event seat layout is immutable', 1;
END;

GO

/* Trigger: trg_SettlementItem_MirrorCanonical */
CREATE OR ALTER TRIGGER dbo.trg_SettlementItem_MirrorCanonical
ON dbo.tc_settlement_items AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE s
    SET settlement_id = i.settlement_id, order_id = i.order_id,
        gross_amount = i.gross_amount, refund_amount = i.refund_amount,
        commission_amount = i.commission_amount, net_amount = i.net_amount,
        updated_at = SYSUTCDATETIME()
    FROM dbo.tc_settlement_order_snapshots s JOIN inserted i ON i.id = s.id;

    INSERT dbo.tc_settlement_order_snapshots
        (id, settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
    SELECT i.id, i.settlement_id, i.order_id, i.gross_amount, i.refund_amount, i.commission_amount, i.net_amount
    FROM inserted i
    WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_settlement_order_snapshots s WHERE s.id = i.id);
END;

GO

/* Trigger: trg_SettlementItem_ProtectConfirmedAmounts */
CREATE OR ALTER TRIGGER dbo.trg_SettlementItem_ProtectConfirmedAmounts ON dbo.tc_settlement_items
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN dbo.tc_settlements s ON s.id = i.settlement_id
        WHERE s.status IN ('CONFIRMED', 'PAID')
    ) OR EXISTS (
        SELECT 1 FROM deleted d JOIN dbo.tc_settlements s ON s.id = d.settlement_id
        WHERE s.status IN ('CONFIRMED', 'PAID')
    ) THROW 51104, 'Confirmed settlement items are immutable', 1;
END;

GO

/* Trigger: trg_SettlementOrderSnapshot_ProtectConfirmedAmounts */
CREATE OR ALTER TRIGGER dbo.trg_SettlementOrderSnapshot_ProtectConfirmedAmounts
ON dbo.tc_settlement_order_snapshots AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT settlement_id FROM inserted
            UNION
            SELECT settlement_id FROM deleted
        ) affected
        JOIN dbo.tc_settlements s WITH (UPDLOCK, HOLDLOCK) ON s.id = affected.settlement_id
        WHERE s.status IN ('CONFIRMED', 'PAID')
    ) THROW 52640, 'Confirmed settlement order snapshots are immutable', 1;
END;

GO

/* Trigger: trg_Settlement_ProtectConfirmedSnapshot */
CREATE OR ALTER TRIGGER dbo.trg_Settlement_ProtectConfirmedSnapshot ON dbo.tc_settlements
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        WHERE d.status IN ('CONFIRMED', 'PAID')
          AND (i.id IS NULL OR i.event_id <> d.event_id OR i.created_at <> d.created_at
               OR ISNULL(i.confirmed_at, '19000101') <> ISNULL(d.confirmed_at, '19000101')
               OR i.gross_revenue <> d.gross_revenue OR i.total_refund <> d.total_refund
               OR i.total_commission <> d.total_commission OR i.net_payable <> d.net_payable
               OR NOT (d.status = 'CONFIRMED' AND i.status IN ('CONFIRMED', 'PAID') OR d.status = 'PAID' AND i.status = 'PAID'))
    ) THROW 51109, 'Confirmed settlement snapshot is immutable', 1;
END;

GO

/* Trigger: trg_Ticket_ProtectIssuedSnapshot */
CREATE OR ALTER TRIGGER dbo.trg_Ticket_ProtectIssuedSnapshot ON dbo.tc_tickets
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.id = i.id
        WHERE i.order_item_id <> d.order_item_id OR i.ticket_code <> d.ticket_code
           OR i.qr_secret_hash <> d.qr_secret_hash OR i.paid_amount <> d.paid_amount
           OR i.issued_at <> d.issued_at
    ) THROW 51108, 'Issued ticket snapshot is immutable', 1;
END;

GO

/* Trigger: trg_UserOrganizationRole_ProtectLastManager */
CREATE OR ALTER TRIGGER dbo.trg_UserOrganizationRole_ProtectLastManager
ON dbo.tc_organization_memberships AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM (SELECT organization_id FROM deleted UNION SELECT organization_id FROM inserted) x
        WHERE NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships m WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_users u ON u.id = m.user_id
            WHERE m.organization_id = x.organization_id AND m.role = 'MANAGER'
              AND m.active = 1 AND u.status = 'ACTIVE'
        )
    ) THROW 52641, 'Organization must retain an active manager', 1;
END;

GO

/* Trigger: trg_Zone_ProtectPublishedLayout */
CREATE OR ALTER TRIGGER dbo.trg_Zone_ProtectPublishedLayout ON dbo.tc_zones
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.tc_events e ON e.id = i.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED')
          AND (d.id IS NULL OR d.event_id <> i.event_id OR d.name <> i.name OR d.type <> i.type
               OR ISNULL(d.capacity, -1) <> ISNULL(i.capacity, -1))
    ) OR EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        JOIN dbo.tc_events e ON e.id = d.event_id
        WHERE e.status IN ('PUBLISHED', 'CANCELLED')
          AND (i.id IS NULL OR i.event_id <> d.event_id)
    ) THROW 51101, 'Published event zone layout is immutable', 1;
END;

GO

/* Trigger: trg_tc_users_normalize */
CREATE OR ALTER TRIGGER dbo.trg_tc_users_normalize
    ON dbo.tc_users AFTER INSERT, UPDATE
    AS
    BEGIN
        SET NOCOUNT ON;
        UPDATE u
        SET normalized_email = LOWER(u.email)
        FROM dbo.tc_users u
        JOIN inserted i ON u.id = i.id
        WHERE u.normalized_email IS NULL OR u.normalized_email <> LOWER(u.email);
    END

GO
