/*
 * 072_identity_commands_test.sql
 * Scenarios for migration 072. Run each scenario in its own transaction and roll it back
 * (procedures use XACT_ABORT, so an expected error ends its scenario). A failure THROWs 52999.
 * The tc_auth checks impersonate a member of tc_auth (railway_auth on the shared DB).
 */

-- SCENARIO: tc_auth registers, re-hashes, resets, revokes and verifies through the procedures
EXECUTE AS USER = 'railway_auth';
DECLARE @created TABLE (user_id uniqueidentifier);
DECLARE @email nvarchar(320) = CONCAT(N'tc072.', LEFT(CONVERT(varchar(36), NEWID()), 8), N'@example.invalid');
DECLARE @name nvarchar(100) = CONCAT(N'tc072_', LEFT(CONVERT(varchar(36), NEWID()), 8));
INSERT @created EXEC dbo.usp_RegisterUser @email, @name, N'Test 072', N'hash-1';
DECLARE @user uniqueidentifier = (SELECT user_id FROM @created);
IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @user AND status = 'ACTIVE' AND platform_role = 'CUSTOMER'
               AND auth_version = 0 AND password_hash = N'hash-1' AND normalized_email = LOWER(@email))
    THROW 52999, 'registered user row is wrong', 1;
EXEC dbo.usp_RehashPassword @user, N'hash-2';
IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @user AND password_hash = N'hash-2' AND auth_version = 0)
    THROW 52999, 'rehash must keep auth_version', 1;
EXEC dbo.usp_ResetPassword @user, N'hash-3';
IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @user AND password_hash = N'hash-3' AND auth_version = 1)
    THROW 52999, 'reset must raise auth_version', 1;
EXEC dbo.usp_RevokeUserSessions @user;
IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @user AND auth_version = 2)
    THROW 52999, 'revoke must raise auth_version', 1;
DECLARE @verified datetime2 = '2026-10-29T08:00:00';
EXEC dbo.usp_MarkEmailVerified @user, @verified;
IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = @user AND email_verified_at = @verified)
    THROW 52999, 'email_verified_at not set', 1;
REVERT;
GO

-- SCENARIO: OTP issue, failures, consume and lock-out
EXECUTE AS USER = 'railway_auth';
DECLARE @created TABLE (user_id uniqueidentifier);
DECLARE @email nvarchar(320) = CONCAT(N'tc072.', LEFT(CONVERT(varchar(36), NEWID()), 8), N'@example.invalid');
DECLARE @name nvarchar(100) = CONCAT(N'tc072_', LEFT(CONVERT(varchar(36), NEWID()), 8));
INSERT @created EXEC dbo.usp_RegisterUser @email, @name, N'Test 072', N'hash-1';
DECLARE @user uniqueidentifier = (SELECT user_id FROM @created);
DECLARE @now datetime2(3) = SYSUTCDATETIME();   -- tc_otps stores datetime2(3)
DECLARE @later datetime2(3) = DATEADD(MINUTE, 5, @now);
DECLARE @otps TABLE (n int IDENTITY, otp_id uniqueidentifier);
DECLARE @result TABLE (n int IDENTITY, value int);

INSERT @otps EXEC dbo.usp_IssueOtp @user, @email, 'VERIFY_EMAIL', 0x01, @later, @now;
INSERT @otps EXEC dbo.usp_IssueOtp @user, @email, 'VERIFY_EMAIL', 0x02, @later, @now;
DECLARE @first uniqueidentifier = (SELECT otp_id FROM @otps WHERE n = 1);
DECLARE @second uniqueidentifier = (SELECT otp_id FROM @otps WHERE n = 2);
IF NOT EXISTS (SELECT 1 FROM dbo.tc_otps WHERE id = @first AND invalidated_at = @now)
    THROW 52999, 'a new OTP must close the previous one', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.tc_otps WHERE id = @second AND invalidated_at IS NULL AND created_at = @now AND failed_attempts = 0)
    THROW 52999, 'new OTP row is wrong', 1;

INSERT @result EXEC dbo.usp_RecordOtpFailure @second, @now;
INSERT @result EXEC dbo.usp_RecordOtpFailure @second, @now;
IF (SELECT value FROM @result WHERE n = 2) <> 2
    THROW 52999, 'failure count must be returned', 1;
INSERT @result EXEC dbo.usp_ConsumeOtp @second, @now;
IF (SELECT value FROM @result WHERE n = 3) <> 1
    THROW 52999, 'an open OTP must be consumable', 1;
INSERT @result EXEC dbo.usp_ConsumeOtp @second, @now;
IF (SELECT value FROM @result WHERE n = 4) <> 0
    THROW 52999, 'an OTP is consumed only once', 1;
INSERT @result EXEC dbo.usp_RecordOtpFailure @second, @now;
IF (SELECT value FROM @result WHERE n = 5) <> 2
    THROW 52999, 'a consumed OTP must not count failures', 1;

INSERT @otps EXEC dbo.usp_IssueOtp @user, @email, 'RESET_PASSWORD', 0x03, @later, @now;
DECLARE @third uniqueidentifier = (SELECT otp_id FROM @otps WHERE n = 3);
DECLARE @i int = 0;
WHILE @i < 5
BEGIN
    INSERT @result EXEC dbo.usp_RecordOtpFailure @third, @now;
    SET @i += 1;
END;
IF NOT EXISTS (SELECT 1 FROM dbo.tc_otps WHERE id = @third AND failed_attempts = 5 AND invalidated_at = @now)
    THROW 52999, 'the fifth failure must close the OTP', 1;
INSERT @result EXEC dbo.usp_ConsumeOtp @third, @now;
IF (SELECT TOP 1 value FROM @result ORDER BY n DESC) <> 0
    THROW 52999, 'a locked OTP must not be consumable', 1;

INSERT @otps EXEC dbo.usp_IssueOtp @user, @email, 'VERIFY_EMAIL', 0x04, @later, @now;
DECLARE @expired uniqueidentifier = (SELECT otp_id FROM @otps WHERE n = 4);
DECLARE @after_expiry datetime2(3) = DATEADD(MINUTE, 10, @now);
INSERT @result EXEC dbo.usp_ConsumeOtp @expired, @after_expiry;
IF (SELECT TOP 1 value FROM @result ORDER BY n DESC) <> 0
    THROW 52999, 'an expired OTP must not be consumable', 1;

EXEC dbo.usp_InvalidateOtps @email, 'RESET_PASSWORD', @now;
IF NOT EXISTS (SELECT 1 FROM dbo.tc_otps WHERE id = @expired AND invalidated_at IS NULL)
    THROW 52999, 'invalidation must only touch the given purpose', 1;
EXEC dbo.usp_InvalidateOtps @email, 'VERIFY_EMAIL', @now;
IF NOT EXISTS (SELECT 1 FROM dbo.tc_otps WHERE id = @expired AND invalidated_at = @now)
    THROW 52999, 'invalidation must close open OTPs', 1;
REVERT;
GO

-- SCENARIO: duplicate email, any case, is refused expect=52900
DECLARE @created TABLE (user_id uniqueidentifier);
DECLARE @email nvarchar(320) = CONCAT(N'tc072.', LEFT(CONVERT(varchar(36), NEWID()), 8), N'@example.invalid');
INSERT @created EXEC dbo.usp_RegisterUser @email, N'tc072_first_owner', N'Test 072', N'hash';
DECLARE @upper nvarchar(320) = UPPER(@email);
EXEC dbo.usp_RegisterUser @upper, N'tc072_second_owner', N'Test 072', N'hash';
GO

-- SCENARIO: duplicate user name, any case, is refused expect=52901
DECLARE @created TABLE (user_id uniqueidentifier);
INSERT @created EXEC dbo.usp_RegisterUser N'tc072.a@example.invalid', N'tc072_SameName', N'Test 072', N'hash';
EXEC dbo.usp_RegisterUser N'tc072.b@example.invalid', N'TC072_samename', N'Test 072', N'hash';
GO

-- SCENARIO: reset of an unknown user expect=52902
DECLARE @nobody uniqueidentifier = NEWID();
EXEC dbo.usp_ResetPassword @nobody, N'hash';
GO

-- SCENARIO: tc_auth can no longer insert users directly expect=229
EXECUTE AS USER = 'railway_auth';
INSERT dbo.tc_users (email, user_name, full_name, password_hash) VALUES (N'tc072.c@example.invalid', N'tc072_c', N'x', N'x');
GO

-- SCENARIO: tc_auth can no longer change a password hash directly expect=229
EXECUTE AS USER = 'railway_auth';
UPDATE dbo.tc_users SET password_hash = N'x' WHERE id = NEWID();
GO

-- SCENARIO: tc_auth can no longer write OTPs directly expect=229
EXECUTE AS USER = 'railway_auth';
UPDATE dbo.tc_otps SET consumed_at = SYSUTCDATETIME() WHERE id = NEWID();
GO

-- SCENARIO: tc_auth still reads users
EXECUTE AS USER = 'railway_auth';
DECLARE @count int = (SELECT COUNT(*) FROM dbo.tc_users);
IF @count < 1 THROW 52999, 'tc_auth must still read tc_users', 1;
REVERT;
GO
