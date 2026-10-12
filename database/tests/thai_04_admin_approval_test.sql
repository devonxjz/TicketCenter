/*
 * Test Suite: thai_04_admin_approval_test.sql
 * Issue #4: [THAI-04] Admin duyệt và từ chối tổ chức
 *
 * Covers:
 * 1. Administrator role required (52610)
 * 2. Organization not found (52611)
 * 3. Invalid initial commission policy (52613)
 * 4. sp_organization_approve success state & side-effects
 * 5. sp_organization_approve duplicate execution (52612 - ORGANIZATION_ALREADY_PROCESSED)
 * 6. sp_organization_reject reason validations (52614)
 * 7. sp_organization_reject success state & side-effects
 * 8. sp_organization_reject duplicate execution (52612 - ORGANIZATION_ALREADY_PROCESSED)
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

CREATE OR ALTER PROCEDURE #ExpectError
    @statement nvarchar(max),
    @expected_number int,
    @test_name varchar(100)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @caught int = NULL;
    DECLARE @caught_message nvarchar(2048) = NULL;

    BEGIN TRY
        EXEC sys.sp_executesql @statement;
    END TRY
    BEGIN CATCH
        SET @caught = ERROR_NUMBER();
        SET @caught_message = ERROR_MESSAGE();
        IF XACT_STATE() <> 0 ROLLBACK;
    END CATCH;

    IF @caught IS NULL OR @caught <> @expected_number
    BEGIN
        DECLARE @msg nvarchar(2048) = CONCAT('FAIL: [', @test_name, '] expected error ', @expected_number, ', but got: ', COALESCE(CONVERT(varchar(20), @caught), 'success (no error)'), ' - ', COALESCE(@caught_message, ''));
        THROW 52793, @msg, 1;
    END;

    PRINT CONCAT('PASS: [', @test_name, '] (caught expected error ', @expected_number, ')');
END;
GO

PRINT '=========================================================';
PRINT 'STARTING TEST SUITE: thai_04_admin_approval_test.sql';
PRINT '=========================================================';

DECLARE @admin_id uniqueidentifier = NEWID();
DECLARE @user_id uniqueidentifier = NEWID();
DECLARE @requester_id uniqueidentifier = NEWID();

DECLARE @org_approve_id uniqueidentifier = NEWID();
DECLARE @org_reject_id uniqueidentifier = NEWID();
DECLARE @org_policy_test_id uniqueidentifier = NEWID();

-- 1. Setup test actors
INSERT INTO dbo.tc_users (id, email, user_name, full_name, platform_role)
VALUES
    (@admin_id, CONCAT('admin_', CONVERT(varchar(36), @admin_id), '@test.invalid'), CONVERT(varchar(36), @admin_id), 'Test Admin', 'ADMIN'),
    (@user_id, CONCAT('user_', CONVERT(varchar(36), @user_id), '@test.invalid'), CONVERT(varchar(36), @user_id), 'Test User', 'CUSTOMER'),
    (@requester_id, CONCAT('req_', CONVERT(varchar(36), @requester_id), '@test.invalid'), CONVERT(varchar(36), @requester_id), 'Test Requester', 'CUSTOMER');

INSERT INTO dbo.tc_user_platform_roles (user_id, role)
VALUES (@admin_id, 'ADMIN');

-- 2. Setup test organizations (all initially PENDING_APPROVAL)
INSERT INTO dbo.tc_organizations (id, name, contact_email, requester_id, status)
VALUES
    (@org_approve_id, 'Test Org Approve', 'approve@test.invalid', @requester_id, 'PENDING_APPROVAL'),
    (@org_reject_id, 'Test Org Reject', 'reject@test.invalid', @requester_id, 'PENDING_APPROVAL'),
    (@org_policy_test_id, 'Test Org Policy', 'policy@test.invalid', @requester_id, 'PENDING_APPROVAL');

DECLARE @stmt nvarchar(max);

-- -------------------------------------------------------------
-- TEST CASE 1: Administrator role required (52610)
-- -------------------------------------------------------------
SET @stmt = CONCAT('EXEC dbo.sp_organization_approve ''', @org_approve_id, ''', ''', @user_id, ''', 5.0, 1000, ''2026-01-01'', ''2026-12-31'';');
EXEC #ExpectError @stmt, 52610, 'Approve without ADMIN role';

SET @stmt = CONCAT('EXEC dbo.sp_organization_reject ''', @org_reject_id, ''', ''', @user_id, ''', N''Rejection reason test 12345'';');
EXEC #ExpectError @stmt, 52610, 'Reject without ADMIN role';

-- -------------------------------------------------------------
-- TEST CASE 2: Organization not found (52611)
-- -------------------------------------------------------------
DECLARE @fake_org_id uniqueidentifier = NEWID();
SET @stmt = CONCAT('EXEC dbo.sp_organization_approve ''', @fake_org_id, ''', ''', @admin_id, ''', 5.0, 1000, ''2026-01-01'', ''2026-12-31'';');
EXEC #ExpectError @stmt, 52611, 'Approve non-existent organization';

SET @stmt = CONCAT('EXEC dbo.sp_organization_reject ''', @fake_org_id, ''', ''', @admin_id, ''', N''Rejection reason test 12345'';');
EXEC #ExpectError @stmt, 52611, 'Reject non-existent organization';

-- -------------------------------------------------------------
-- TEST CASE 3: Invalid initial commission policy (52613)
-- -------------------------------------------------------------
-- Rate percent < 0
SET @stmt = CONCAT('EXEC dbo.sp_organization_approve ''', @org_policy_test_id, ''', ''', @admin_id, ''', -1.0, 1000, ''2026-01-01'', ''2026-12-31'';');
EXEC #ExpectError @stmt, 52613, 'Rate percent < 0';

-- Rate percent > 100
SET @stmt = CONCAT('EXEC dbo.sp_organization_approve ''', @org_policy_test_id, ''', ''', @admin_id, ''', 100.5, 1000, ''2026-01-01'', ''2026-12-31'';');
EXEC #ExpectError @stmt, 52613, 'Rate percent > 100';

-- Fixed fee < 0
SET @stmt = CONCAT('EXEC dbo.sp_organization_approve ''', @org_policy_test_id, ''', ''', @admin_id, ''', 5.0, -1, ''2026-01-01'', ''2026-12-31'';');
EXEC #ExpectError @stmt, 52613, 'Fixed fee < 0';

-- Effective from >= Effective to
SET @stmt = CONCAT('EXEC dbo.sp_organization_approve ''', @org_policy_test_id, ''', ''', @admin_id, ''', 5.0, 1000, ''2026-12-31'', ''2026-01-01'';');
EXEC #ExpectError @stmt, 52613, 'Effective from > Effective to';

SET @stmt = CONCAT('EXEC dbo.sp_organization_approve ''', @org_policy_test_id, ''', ''', @admin_id, ''', 5.0, 1000, ''2026-01-01'', ''2026-01-01'';');
EXEC #ExpectError @stmt, 52613, 'Effective from == Effective to';

-- -------------------------------------------------------------
-- TEST CASE 4: sp_organization_approve success
-- -------------------------------------------------------------
DECLARE @approve_result TABLE (
    organization_id uniqueidentifier,
    status varchar(30),
    assigned_manager_id uniqueidentifier
);

INSERT INTO @approve_result
EXEC dbo.sp_organization_approve
    @organization_id = @org_approve_id,
    @admin_user_id = @admin_id,
    @rate_percent = 8.5000,
    @fixed_fee = 5000,
    @effective_from = '2026-01-01',
    @effective_to = '2026-12-31';

-- Assert output result
IF NOT EXISTS (
    SELECT 1 FROM @approve_result
    WHERE organization_id = @org_approve_id
      AND status = 'APPROVED'
      AND assigned_manager_id = @requester_id
)
    THROW 52793, 'Approve output result mismatch', 1;

-- Assert organization status
IF NOT EXISTS (
    SELECT 1 FROM dbo.tc_organizations
    WHERE id = @org_approve_id
      AND status = 'APPROVED'
      AND decided_at IS NOT NULL
      AND rejection_reason IS NULL
)
    THROW 52793, 'tc_organizations status not updated to APPROVED', 1;

-- Assert commission rule
IF NOT EXISTS (
    SELECT 1 FROM dbo.tc_commission_rules
    WHERE organization_id = @org_approve_id
      AND rate_percent = 8.5000
      AND fixed_fee = 5000
)
    THROW 52793, 'tc_commission_rules record missing after approval', 1;

-- Assert organization membership
IF NOT EXISTS (
    SELECT 1 FROM dbo.tc_organization_memberships
    WHERE organization_id = @org_approve_id
      AND user_id = @requester_id
      AND role = 'MANAGER'
      AND active = 1
)
    THROW 52793, 'tc_organization_memberships MANAGER role missing for requester', 1;

-- Assert audit log
IF NOT EXISTS (
    SELECT 1 FROM dbo.tc_audit_logs
    WHERE aggregate_id = @org_approve_id
      AND aggregate_type = 'ORGANIZATION'
      AND action = 'ORGANIZATION_APPROVED'
      AND actor_id = @admin_id
)
    THROW 52793, 'tc_audit_logs entry missing for approval', 1;

PRINT 'PASS: [Approve organization successful execution and state assertions]';

-- -------------------------------------------------------------
-- TEST CASE 5: Duplicate approval (52612 - ORGANIZATION_ALREADY_PROCESSED)
-- -------------------------------------------------------------
SET @stmt = CONCAT('EXEC dbo.sp_organization_approve ''', @org_approve_id, ''', ''', @admin_id, ''', 8.5, 5000, ''2026-01-01'', ''2026-12-31'';');
EXEC #ExpectError @stmt, 52612, 'Re-approving already approved organization';

SET @stmt = CONCAT('EXEC dbo.sp_organization_reject ''', @org_approve_id, ''', ''', @admin_id, ''', N''Valid rejection reason 12345'';');
EXEC #ExpectError @stmt, 52612, 'Rejecting already approved organization';

-- -------------------------------------------------------------
-- TEST CASE 6: Rejection reason validations (52614)
-- -------------------------------------------------------------
-- NULL reason
SET @stmt = CONCAT('EXEC dbo.sp_organization_reject ''', @org_reject_id, ''', ''', @admin_id, ''', NULL;');
EXEC #ExpectError @stmt, 52614, 'NULL rejection reason';

-- Empty reason
SET @stmt = CONCAT('EXEC dbo.sp_organization_reject ''', @org_reject_id, ''', ''', @admin_id, ''', N'''';');
EXEC #ExpectError @stmt, 52614, 'Empty rejection reason';

-- Whitespace only
SET @stmt = CONCAT('EXEC dbo.sp_organization_reject ''', @org_reject_id, ''', ''', @admin_id, ''', N''             '';');
EXEC #ExpectError @stmt, 52614, 'Whitespace-only rejection reason';

-- Less than 10 characters (9 characters)
SET @stmt = CONCAT('EXEC dbo.sp_organization_reject ''', @org_reject_id, ''', ''', @admin_id, ''', N''Short msg'';');
EXEC #ExpectError @stmt, 52614, 'Rejection reason < 10 characters';

-- Greater than 500 characters
DECLARE @oversized_reason nvarchar(2000) = REPLICATE(N'x', 501);
SET @stmt = CONCAT('EXEC dbo.sp_organization_reject ''', @org_reject_id, ''', ''', @admin_id, ''', N''', @oversized_reason, ''';');
EXEC #ExpectError @stmt, 52614, 'Rejection reason > 500 characters';

-- -------------------------------------------------------------
-- TEST CASE 7: sp_organization_reject success
-- -------------------------------------------------------------
DECLARE @reject_result TABLE (
    organization_id uniqueidentifier,
    status varchar(30)
);

DECLARE @rejection_reason_text nvarchar(2000) = N'Giấy phép kinh doanh không hợp lệ hoặc đã hết hiệu lực theo quy định hiện hành.';

INSERT INTO @reject_result
EXEC dbo.sp_organization_reject
    @organization_id = @org_reject_id,
    @admin_user_id = @admin_id,
    @rejected_reason = @rejection_reason_text;

-- Assert output result
IF NOT EXISTS (
    SELECT 1 FROM @reject_result
    WHERE organization_id = @org_reject_id
      AND status = 'REJECTED'
)
    THROW 52793, 'Reject output result mismatch', 1;

-- Assert organization status & rejection reason
IF NOT EXISTS (
    SELECT 1 FROM dbo.tc_organizations
    WHERE id = @org_reject_id
      AND status = 'REJECTED'
      AND decided_at IS NOT NULL
      AND rejection_reason = @rejection_reason_text
)
    THROW 52793, 'tc_organizations status/rejection_reason not updated correctly', 1;

-- Assert NO commission rule was created
IF EXISTS (SELECT 1 FROM dbo.tc_commission_rules WHERE organization_id = @org_reject_id)
    THROW 52793, 'tc_commission_rules should not be created for rejected organization', 1;

-- Assert audit log
IF NOT EXISTS (
    SELECT 1 FROM dbo.tc_audit_logs
    WHERE aggregate_id = @org_reject_id
      AND aggregate_type = 'ORGANIZATION'
      AND action = 'ORGANIZATION_REJECTED'
      AND actor_id = @admin_id
      AND detail = @rejection_reason_text
)
    THROW 52793, 'tc_audit_logs entry missing for rejection', 1;

PRINT 'PASS: [Reject organization successful execution and state assertions]';

-- -------------------------------------------------------------
-- TEST CASE 8: Duplicate rejection (52612 - ORGANIZATION_ALREADY_PROCESSED)
-- -------------------------------------------------------------
SET @stmt = CONCAT('EXEC dbo.sp_organization_reject ''', @org_reject_id, ''', ''', @admin_id, ''', N''Duplicate rejection attempt'';');
EXEC #ExpectError @stmt, 52612, 'Re-rejecting already rejected organization';

SET @stmt = CONCAT('EXEC dbo.sp_organization_approve ''', @org_reject_id, ''', ''', @admin_id, ''', 5.0, 1000, ''2026-01-01'', ''2026-12-31'';');
EXEC #ExpectError @stmt, 52612, 'Approving already rejected organization';

-- -------------------------------------------------------------
-- CLEANUP
-- -------------------------------------------------------------
DELETE FROM dbo.tc_audit_logs WHERE aggregate_id IN (@org_approve_id, @org_reject_id, @org_policy_test_id);
DELETE FROM dbo.tc_organization_memberships WHERE organization_id IN (@org_approve_id, @org_reject_id, @org_policy_test_id);
DELETE FROM dbo.tc_commission_rules WHERE organization_id IN (@org_approve_id, @org_reject_id, @org_policy_test_id);
DELETE FROM dbo.tc_organizations WHERE id IN (@org_approve_id, @org_reject_id, @org_policy_test_id);
DELETE FROM dbo.tc_user_platform_roles WHERE user_id IN (@admin_id, @user_id, @requester_id);
DELETE FROM dbo.tc_users WHERE id IN (@admin_id, @user_id, @requester_id);

DROP PROCEDURE #ExpectError;

PRINT '=========================================================';
PRINT 'ALL TESTS PASSED: thai_04_admin_approval_test.sql';
PRINT '=========================================================';
GO
