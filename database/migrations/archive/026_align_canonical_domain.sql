/*
 * 026_align_canonical_domain
 *
 * Forward-only bridge from the legacy request/attempt schema to the canonical
 * 15-class domain described by docs/references/SPEC.md (05/10/2026).
 *
 * This migration is intentionally additive:
 * - legacy tables and programmable objects remain available;
 * - canonical persistence tables are added and backfilled;
 * - legacy write paths are mirrored into canonical log/snapshot tables;
 * - no existing table, column, constraint, index or row is removed.
 *
 * The migration runner owns the transaction and tc_schema_migrations record.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'TicketCenterticke'
    THROW 52600, 'Migration 026 can only run on TicketCenterticke', 1;

IF OBJECT_ID(N'dbo.tc_schema_migrations', N'U') IS NULL
    THROW 52601, 'tc_schema_migrations is required', 1;

IF EXISTS (SELECT 1 FROM dbo.tc_schema_migrations WHERE version = '026_align_canonical_domain')
    THROW 52602, 'Migration 026 is already recorded', 1;
GO

/* Organization is the approval aggregate in the canonical model. */
IF COL_LENGTH(N'dbo.tc_organizations', N'requester_id') IS NULL
    ALTER TABLE dbo.tc_organizations ADD requester_id uniqueidentifier NULL;
IF COL_LENGTH(N'dbo.tc_organizations', N'status') IS NULL
    ALTER TABLE dbo.tc_organizations ADD status varchar(30) NOT NULL
        CONSTRAINT DF_Organizations_Status DEFAULT ('APPROVED');
IF COL_LENGTH(N'dbo.tc_organizations', N'rejection_reason') IS NULL
    ALTER TABLE dbo.tc_organizations ADD rejection_reason nvarchar(2000) NULL;
IF COL_LENGTH(N'dbo.tc_organizations', N'updated_at') IS NULL
    ALTER TABLE dbo.tc_organizations ADD updated_at datetime2(3) NOT NULL
        CONSTRAINT DF_Organizations_UpdatedAt DEFAULT (SYSUTCDATETIME());
IF COL_LENGTH(N'dbo.tc_organizations', N'submitted_at') IS NULL
    ALTER TABLE dbo.tc_organizations ADD submitted_at datetime2(3) NULL;
IF COL_LENGTH(N'dbo.tc_organizations', N'decided_at') IS NULL
    ALTER TABLE dbo.tc_organizations ADD decided_at datetime2(3) NULL;
GO

UPDATE o
SET requester_id = manager.user_id,
    updated_at = COALESCE(o.updated_at, o.created_at),
    decided_at = COALESCE(o.decided_at, o.created_at)
FROM dbo.tc_organizations o
OUTER APPLY (
    SELECT TOP (1) m.user_id
    FROM dbo.tc_organization_memberships m
    WHERE m.organization_id = o.id AND m.role = 'MANAGER' AND m.active = 1
    ORDER BY m.joined_at, m.id
) manager
WHERE o.requester_id IS NULL OR o.decided_at IS NULL;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Organizations_Requester')
    ALTER TABLE dbo.tc_organizations WITH CHECK
        ADD CONSTRAINT FK_Organizations_Requester FOREIGN KEY (requester_id) REFERENCES dbo.tc_users(id);

/* Canonical aliases retained next to legacy physical column names. */
IF COL_LENGTH(N'dbo.tc_orders', N'accepted_payment_id') IS NULL
    ALTER TABLE dbo.tc_orders ADD accepted_payment_id uniqueidentifier NULL;
GO

UPDATE o
SET accepted_payment_id = paid.id
FROM dbo.tc_orders o
OUTER APPLY (
    SELECT TOP (1) p.id
    FROM dbo.tc_payments p
    WHERE p.order_id = o.id AND p.status = 'CAPTURED'
    ORDER BY p.captured_at, p.created_at, p.id
) paid
WHERE o.accepted_payment_id IS NULL AND paid.id IS NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Orders_AcceptedPayment')
    ALTER TABLE dbo.tc_orders WITH CHECK
        ADD CONSTRAINT FK_Orders_AcceptedPayment FOREIGN KEY (accepted_payment_id) REFERENCES dbo.tc_payments(id);
GO

/* tc_refunds remains the legacy transfer-attempt table. Canonical Refund
   obligations are stored separately so every legacy constraint is preserved. */

/* Canonical Settlement stores payout balances directly. */
IF COL_LENGTH(N'dbo.tc_settlements', N'paid_amount') IS NULL
    ALTER TABLE dbo.tc_settlements ADD paid_amount decimal(19,0) NOT NULL
        CONSTRAINT DF_Settlements_PaidAmount DEFAULT (0);
IF COL_LENGTH(N'dbo.tc_settlements', N'pending_amount') IS NULL
    ALTER TABLE dbo.tc_settlements ADD pending_amount decimal(19,0) NOT NULL
        CONSTRAINT DF_Settlements_PendingAmount DEFAULT (0);
IF COL_LENGTH(N'dbo.tc_settlements', N'updated_at') IS NULL
    ALTER TABLE dbo.tc_settlements ADD updated_at datetime2(3) NOT NULL
        CONSTRAINT DF_Settlements_UpdatedAt DEFAULT (SYSUTCDATETIME());
GO

UPDATE s
SET paid_amount = amounts.paid_amount,
    pending_amount = amounts.pending_amount,
    updated_at = COALESCE(s.updated_at, s.confirmed_at, s.created_at)
FROM dbo.tc_settlements s
CROSS APPLY (
    SELECT
        COALESCE(SUM(CASE WHEN p.status = 'SUCCEEDED' THEN p.amount ELSE 0 END), 0) paid_amount,
        COALESCE(SUM(CASE WHEN p.status = 'PENDING' THEN p.amount ELSE 0 END), 0) pending_amount
    FROM dbo.tc_payouts p WHERE p.settlement_id = s.id
) amounts;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Settlement_PayoutBalances')
    ALTER TABLE dbo.tc_settlements WITH CHECK ADD CONSTRAINT CK_Settlement_PayoutBalances CHECK (
        paid_amount >= 0 AND pending_amount >= 0
        AND paid_amount + pending_amount <= net_payable
    );
GO

/* Persistence tables required by the canonical model. */
IF OBJECT_ID(N'dbo.tc_refund_obligations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_refund_obligations (
        id uniqueidentifier NOT NULL CONSTRAINT DF_RefundObligations_Id DEFAULT (NEWSEQUENTIALID()),
        legacy_request_id uniqueidentifier NULL,
        order_id uniqueidentifier NOT NULL,
        payment_id uniqueidentifier NULL,
        amount decimal(19,0) NOT NULL,
        purpose varchar(30) NOT NULL,
        reason_type varchar(30) NULL,
        reason nvarchar(2000) NULL,
        rejection_reason nvarchar(2000) NULL,
        status varchar(40) NOT NULL,
        current_attempt_id uniqueidentifier NULL,
        provider_reference nvarchar(200) NULL,
        processed_at datetime2(3) NULL,
        created_at datetime2(3) NOT NULL CONSTRAINT DF_RefundObligations_CreatedAt DEFAULT (SYSUTCDATETIME()),
        updated_at datetime2(3) NOT NULL CONSTRAINT DF_RefundObligations_UpdatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_RefundObligations PRIMARY KEY (id),
        CONSTRAINT FK_RefundObligations_LegacyRequest FOREIGN KEY (legacy_request_id) REFERENCES dbo.tc_refund_requests(id),
        CONSTRAINT FK_RefundObligations_Order FOREIGN KEY (order_id) REFERENCES dbo.tc_orders(id),
        CONSTRAINT FK_RefundObligations_Payment FOREIGN KEY (payment_id) REFERENCES dbo.tc_payments(id),
        CONSTRAINT CK_RefundObligations_Amount CHECK (amount >= 0),
        CONSTRAINT CK_RefundObligations_Purpose CHECK (purpose IN ('CUSTOMER_REFUND', 'PAYMENT_COMPENSATION')),
        CONSTRAINT CK_RefundObligations_ReasonType CHECK (
            reason_type IS NULL OR reason_type IN ('CUSTOMER_REQUEST', 'EVENT_CANCELLATION')
        ),
        CONSTRAINT CK_RefundObligations_Status CHECK (
            status IN ('REQUESTED', 'APPROVED', 'PROCESSING', 'NEEDS_RECONCILIATION',
                       'RETRYABLE', 'REJECTED', 'COMPLETED')
        )
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_refund_obligations') AND name = N'UX_RefundObligations_LegacyRequest')
    CREATE UNIQUE INDEX UX_RefundObligations_LegacyRequest
        ON dbo.tc_refund_obligations(legacy_request_id) WHERE legacy_request_id IS NOT NULL;

IF OBJECT_ID(N'dbo.tc_refund_tickets', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_refund_tickets (
        id uniqueidentifier NOT NULL CONSTRAINT DF_RefundTickets_Id DEFAULT (NEWSEQUENTIALID()),
        refund_id uniqueidentifier NOT NULL,
        ticket_id uniqueidentifier NOT NULL,
        is_open bit NOT NULL CONSTRAINT DF_RefundTickets_IsOpen DEFAULT (1),
        created_at datetime2(3) NOT NULL CONSTRAINT DF_RefundTickets_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_RefundTickets PRIMARY KEY (id),
        CONSTRAINT UQ_RefundTickets_RefundTicket UNIQUE (refund_id, ticket_id),
        CONSTRAINT FK_RefundTickets_Refund FOREIGN KEY (refund_id) REFERENCES dbo.tc_refund_obligations(id),
        CONSTRAINT FK_RefundTickets_Ticket FOREIGN KEY (ticket_id) REFERENCES dbo.tc_tickets(id)
    );
END;

IF OBJECT_ID(N'dbo.tc_refund_transfer_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_refund_transfer_logs (
        id uniqueidentifier NOT NULL,
        refund_id uniqueidentifier NOT NULL,
        attempt_no int NOT NULL,
        status varchar(30) NOT NULL,
        result varchar(30) NOT NULL,
        provider_reference nvarchar(200) NULL,
        processed_at datetime2(3) NULL,
        failure_reason nvarchar(2000) NULL,
        created_at datetime2(3) NOT NULL CONSTRAINT DF_RefundTransferLogs_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_RefundTransferLogs PRIMARY KEY (id),
        CONSTRAINT UQ_RefundTransferLogs_RefundAttempt UNIQUE (refund_id, attempt_no),
        CONSTRAINT FK_RefundTransferLogs_Refund FOREIGN KEY (refund_id) REFERENCES dbo.tc_refund_obligations(id),
        CONSTRAINT CK_RefundTransferLogs_AttemptNo CHECK (attempt_no > 0),
        CONSTRAINT CK_RefundTransferLogs_Status CHECK (status IN ('PENDING', 'UNKNOWN', 'FAILED', 'SUCCEEDED')),
        CONSTRAINT CK_RefundTransferLogs_Result CHECK (result IN ('PENDING', 'UNKNOWN', 'FAILED', 'SUCCEEDED'))
    );
END;

IF OBJECT_ID(N'dbo.tc_settlement_order_snapshots', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_settlement_order_snapshots (
        id uniqueidentifier NOT NULL CONSTRAINT DF_SettlementOrderSnapshots_Id DEFAULT (NEWSEQUENTIALID()),
        settlement_id uniqueidentifier NOT NULL,
        order_id uniqueidentifier NOT NULL,
        gross_amount decimal(19,0) NOT NULL,
        refund_amount decimal(19,0) NOT NULL,
        commission_amount decimal(19,0) NOT NULL,
        net_amount decimal(19,0) NOT NULL,
        created_at datetime2(3) NOT NULL CONSTRAINT DF_SettlementOrderSnapshots_CreatedAt DEFAULT (SYSUTCDATETIME()),
        updated_at datetime2(3) NOT NULL CONSTRAINT DF_SettlementOrderSnapshots_UpdatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_SettlementOrderSnapshots PRIMARY KEY (id),
        CONSTRAINT UQ_SettlementOrderSnapshots_SettlementOrder UNIQUE (settlement_id, order_id),
        CONSTRAINT FK_SettlementOrderSnapshots_Settlement FOREIGN KEY (settlement_id) REFERENCES dbo.tc_settlements(id),
        CONSTRAINT FK_SettlementOrderSnapshots_Order FOREIGN KEY (order_id) REFERENCES dbo.tc_orders(id),
        CONSTRAINT CK_SettlementOrderSnapshots_Amounts CHECK (
            gross_amount >= 0 AND refund_amount >= 0 AND commission_amount >= 0 AND net_amount >= 0
            AND net_amount = gross_amount - refund_amount - commission_amount
        )
    );
END;

IF OBJECT_ID(N'dbo.tc_settlement_transfer_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_settlement_transfer_logs (
        id uniqueidentifier NOT NULL,
        settlement_id uniqueidentifier NOT NULL,
        amount decimal(19,0) NOT NULL,
        status varchar(30) NOT NULL,
        result varchar(30) NOT NULL,
        provider_reference nvarchar(200) NULL,
        processed_at datetime2(3) NULL,
        created_at datetime2(3) NOT NULL CONSTRAINT DF_SettlementTransferLogs_CreatedAt DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_SettlementTransferLogs PRIMARY KEY (id),
        CONSTRAINT FK_SettlementTransferLogs_Settlement FOREIGN KEY (settlement_id) REFERENCES dbo.tc_settlements(id),
        CONSTRAINT CK_SettlementTransferLogs_Amount CHECK (amount > 0),
        CONSTRAINT CK_SettlementTransferLogs_Status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
        CONSTRAINT CK_SettlementTransferLogs_Result CHECK (result IN ('PENDING', 'SUCCEEDED', 'FAILED'))
    );
END;

IF OBJECT_ID(N'dbo.tc_ticket_check_in_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.tc_ticket_check_in_logs (
        id uniqueidentifier NOT NULL CONSTRAINT DF_TicketCheckInLogs_Id DEFAULT (NEWSEQUENTIALID()),
        event_id uniqueidentifier NOT NULL,
        ticket_id uniqueidentifier NULL,
        actor_id uniqueidentifier NOT NULL,
        result varchar(30) NOT NULL,
        scanned_at datetime2(3) NOT NULL CONSTRAINT DF_TicketCheckInLogs_ScannedAt DEFAULT (SYSUTCDATETIME()),
        failure_reason nvarchar(2000) NULL,
        CONSTRAINT PK_TicketCheckInLogs PRIMARY KEY (id),
        CONSTRAINT FK_TicketCheckInLogs_Event FOREIGN KEY (event_id) REFERENCES dbo.tc_events(id),
        CONSTRAINT FK_TicketCheckInLogs_Ticket FOREIGN KEY (ticket_id) REFERENCES dbo.tc_tickets(id),
        CONSTRAINT FK_TicketCheckInLogs_Actor FOREIGN KEY (actor_id) REFERENCES dbo.tc_users(id)
    );
END;
GO

/* Backfill canonical persistence from legacy physical tables. */
INSERT dbo.tc_refund_obligations
    (id, legacy_request_id, order_id, payment_id, amount, purpose, reason_type, reason,
     rejection_reason, status, current_attempt_id, provider_reference, processed_at, created_at, updated_at)
SELECT rr.id, rr.id, rr.order_id, attempt.payment_id,
       COALESCE(ticket_amount.amount, 0), 'CUSTOMER_REFUND', rr.reason_type, rr.reason,
       rr.rejection_reason,
       CASE
           WHEN attempt.status = 'SUCCEEDED' OR rr.status = 'COMPLETED' THEN 'COMPLETED'
           WHEN attempt.status = 'FAILED' THEN 'RETRYABLE'
           WHEN attempt.status = 'UNKNOWN' THEN 'NEEDS_RECONCILIATION'
           WHEN attempt.status = 'PENDING' THEN 'PROCESSING'
           WHEN rr.status = 'APPROVED' THEN 'APPROVED'
           WHEN rr.status = 'REJECTED' THEN 'REJECTED'
           ELSE 'REQUESTED'
       END,
       attempt.id, attempt.provider_reference, attempt.processed_at,
       rr.requested_at, COALESCE(attempt.processed_at, rr.decided_at, rr.requested_at)
FROM dbo.tc_refund_requests rr
OUTER APPLY (
    SELECT SUM(t.paid_amount) amount
    FROM dbo.tc_refund_request_tickets rt
    JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
    WHERE rt.refund_request_id = rr.id
) ticket_amount
OUTER APPLY (
    SELECT TOP (1) r.id, r.payment_id, r.status, r.provider_reference, r.processed_at
    FROM dbo.tc_refunds r
    WHERE r.refund_request_id = rr.id
    ORDER BY r.created_at DESC, r.id
) attempt
WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_refund_obligations o WHERE o.legacy_request_id = rr.id);

INSERT dbo.tc_refund_obligations
    (id, order_id, payment_id, amount, purpose, status, current_attempt_id,
     provider_reference, processed_at, created_at, updated_at)
SELECT r.id, p.order_id, r.payment_id, r.amount, r.purpose,
       CASE r.status
           WHEN 'SUCCEEDED' THEN 'COMPLETED'
           WHEN 'FAILED' THEN 'RETRYABLE'
           WHEN 'UNKNOWN' THEN 'NEEDS_RECONCILIATION'
           ELSE 'PROCESSING'
       END,
       r.id, r.provider_reference, r.processed_at, r.created_at,
       COALESCE(r.processed_at, r.created_at)
FROM dbo.tc_refunds r
JOIN dbo.tc_payments p ON p.id = r.payment_id
WHERE r.refund_request_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_obligations o WHERE o.id = r.id);

INSERT dbo.tc_settlement_order_snapshots
    (id, settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
SELECT i.id, i.settlement_id, i.order_id, i.gross_amount, i.refund_amount, i.commission_amount, i.net_amount
FROM dbo.tc_settlement_items i
WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_settlement_order_snapshots s WHERE s.id = i.id);

INSERT dbo.tc_settlement_transfer_logs
    (id, settlement_id, amount, status, result, provider_reference, processed_at, created_at)
SELECT p.id, p.settlement_id, p.amount, p.status, p.status, p.reference, p.paid_at, p.created_at
FROM dbo.tc_payouts p
WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_settlement_transfer_logs l WHERE l.id = p.id);

INSERT dbo.tc_ticket_check_in_logs (id, event_id, ticket_id, actor_id, result, scanned_at)
SELECT c.id, c.event_id, c.ticket_id, c.actor_id,
       CASE c.result
           WHEN 'NOT_FOUND' THEN 'INVALID_CODE'
           WHEN 'TOO_EARLY' THEN 'OUTSIDE_WINDOW'
           WHEN 'TOO_LATE' THEN 'OUTSIDE_WINDOW'
           WHEN 'NOT_ACTIVE' THEN 'INACTIVE_TICKET'
           WHEN 'EVENT_CANCELLED' THEN 'INACTIVE_TICKET'
           ELSE c.result
       END,
       c.scanned_at
FROM dbo.tc_check_ins c
WHERE NOT EXISTS (SELECT 1 FROM dbo.tc_ticket_check_in_logs l WHERE l.id = c.id);

INSERT dbo.tc_refund_transfer_logs
    (id, refund_id, attempt_no, status, result, provider_reference, processed_at, created_at)
SELECT r.id, COALESCE(o.id, r.id),
       ROW_NUMBER() OVER (PARTITION BY COALESCE(o.id, r.id) ORDER BY r.created_at, r.id),
       r.status, r.status, r.provider_reference, r.processed_at, r.created_at
FROM dbo.tc_refunds r
LEFT JOIN dbo.tc_refund_obligations o ON o.legacy_request_id = r.refund_request_id
WHERE r.status IN ('PENDING', 'UNKNOWN', 'FAILED', 'SUCCEEDED')
  AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_transfer_logs l WHERE l.id = r.id);

INSERT dbo.tc_refund_tickets (refund_id, ticket_id, is_open, created_at)
SELECT o.id, rt.ticket_id, rt.is_open, rt.added_at
FROM dbo.tc_refund_obligations o
JOIN dbo.tc_refund_request_tickets rt ON rt.refund_request_id = o.legacy_request_id
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.tc_refund_tickets x WHERE x.refund_id = o.id AND x.ticket_id = rt.ticket_id
);
GO

/* Canonical performance indexes; unique constraints remain separate. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_ticket_check_in_logs') AND name = N'IX_TicketCheckInLog_Event_ScannedAt')
    CREATE INDEX IX_TicketCheckInLog_Event_ScannedAt
        ON dbo.tc_ticket_check_in_logs(event_id, scanned_at DESC, id) INCLUDE(result, ticket_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_refund_transfer_logs') AND name = N'IX_RefundTransferLog_Status_CreatedAt')
    CREATE INDEX IX_RefundTransferLog_Status_CreatedAt
        ON dbo.tc_refund_transfer_logs(status, created_at, id) INCLUDE(refund_id, provider_reference);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_refund_obligations') AND name = N'IX_Refund_Status_CreatedAt')
    CREATE INDEX IX_Refund_Status_CreatedAt
        ON dbo.tc_refund_obligations(status, created_at, id) INCLUDE(order_id, purpose, reason_type);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_settlement_transfer_logs') AND name = N'IX_Payout_Settlement_Status')
    CREATE INDEX IX_Payout_Settlement_Status
        ON dbo.tc_settlement_transfer_logs(settlement_id, status, id) INCLUDE(amount);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_refund_tickets') AND name = N'UX_RefundTickets_OneOpenPerTicket')
    CREATE UNIQUE INDEX UX_RefundTickets_OneOpenPerTicket
        ON dbo.tc_refund_tickets(ticket_id) WHERE is_open = 1;
GO

/* Canonical view name. Legacy view remains available during transition. */
CREATE OR ALTER VIEW dbo.vw_RefundOverview
AS
WITH ticket_totals AS (
    SELECT refund_id, COUNT_BIG(*) ticket_count,
           SUM(CASE WHEN is_open = 1 THEN 1 ELSE 0 END) open_ticket_count
    FROM dbo.tc_refund_tickets GROUP BY refund_id
), attempt_totals AS (
    SELECT refund_id, COUNT_BIG(*) attempt_count,
           SUM(CASE WHEN status = 'SUCCEEDED' THEN 1 ELSE 0 END) succeeded_attempt_count,
           MAX(created_at) latest_attempt_at
    FROM dbo.tc_refund_transfer_logs GROUP BY refund_id
)
SELECT r.id refund_id, r.order_id, o.event_id, e.organization_id, r.payment_id,
       r.purpose, r.reason_type, r.reason, r.rejection_reason, r.amount, r.status,
       r.current_attempt_id, r.provider_reference, r.processed_at, r.created_at,
       COALESCE(t.ticket_count, 0) ticket_count,
       COALESCE(t.open_ticket_count, 0) open_ticket_count,
       COALESCE(a.attempt_count, 0) attempt_count,
       COALESCE(a.succeeded_attempt_count, 0) succeeded_attempt_count,
       a.latest_attempt_at
FROM dbo.tc_refund_obligations r
LEFT JOIN dbo.tc_orders o ON o.id = r.order_id
LEFT JOIN dbo.tc_events e ON e.id = o.event_id
LEFT JOIN ticket_totals t ON t.refund_id = r.id
LEFT JOIN attempt_totals a ON a.refund_id = r.id;
GO

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
