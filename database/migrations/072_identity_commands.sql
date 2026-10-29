/*
  072 · Identity commands: every account and OTP write goes through a procedure

  Until now the AUTH principal wrote dbo.tc_users and dbo.tc_otps itself (JPA persist/merge and JPQL
  UPDATE). These procedures take over those writes one for one, and tc_auth loses its direct
  INSERT/UPDATE grants on both tables; it keeps SELECT and the existing DENY on status/platform_role.

    usp_RegisterUser          new CUSTOMER account; email and user name are unique ignoring case
    usp_RehashPassword        stronger hash for the same password (login upgrade); sessions stay valid
    usp_ResetPassword         new password and auth_version + 1, which ends every session and reset token
    usp_RevokeUserSessions    auth_version + 1
    usp_MarkEmailVerified     email_verified_at
    usp_InvalidateOtps        closes the open OTPs of an email and purpose
    usp_IssueOtp              closes the open OTPs of an email and purpose, then stores a new one
    usp_ConsumeOtp            uses an OTP once; returns consumed = 0 when it was used, closed, expired or locked
    usp_RecordOtpFailure      counts a wrong code; the fifth one closes the OTP; returns failed_attempts

  Each procedure joins a caller's transaction through a savepoint, like 070.
  New THROW numbers: 52900 email already used, 52901 user name already used, 52902 user not found.
  The OTP lock-out stays at 5 failed attempts, as the JPQL it replaces.
*/
CREATE OR ALTER PROCEDURE dbo.usp_RegisterUser
    @email nvarchar(320),
    @user_name nvarchar(100),
    @full_name nvarchar(200),
    @password_hash nvarchar(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION usp_RegisterUser_sp;

    BEGIN TRY
        DECLARE @created TABLE (id uniqueidentifier);

        IF EXISTS (SELECT 1 FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK) WHERE LOWER(email) = LOWER(@email))
            THROW 52900, 'Email already used', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK) WHERE LOWER(user_name) = LOWER(@user_name))
            THROW 52901, 'User name already used', 1;

        INSERT dbo.tc_users (email, user_name, full_name, password_hash, status, auth_version, platform_role,
                             created_at, updated_at)
        OUTPUT inserted.id INTO @created
        VALUES (@email, @user_name, @full_name, @password_hash, 'ACTIVE', 0, 'CUSTOMER',
                SYSUTCDATETIME(), SYSUTCDATETIME());

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT id AS user_id FROM @created;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION usp_RegisterUser_sp;
        THROW;
    END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_RehashPassword
    @user_id uniqueidentifier,
    @password_hash nvarchar(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    UPDATE dbo.tc_users
       SET password_hash = @password_hash, version = version + 1, updated_at = SYSUTCDATETIME()
     WHERE id = @user_id;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_ResetPassword
    @user_id uniqueidentifier,
    @password_hash nvarchar(255)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    UPDATE dbo.tc_users
       SET password_hash = @password_hash, auth_version = auth_version + 1, version = version + 1,
           updated_at = SYSUTCDATETIME()
     WHERE id = @user_id;
    IF @@ROWCOUNT = 0
        THROW 52902, 'User not found', 1;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_RevokeUserSessions
    @user_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    UPDATE dbo.tc_users
       SET auth_version = auth_version + 1, version = version + 1, updated_at = SYSUTCDATETIME()
     WHERE id = @user_id;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_MarkEmailVerified
    @user_id uniqueidentifier,
    @verified_at datetime2
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    UPDATE dbo.tc_users
       SET email_verified_at = @verified_at, version = version + 1, updated_at = SYSUTCDATETIME()
     WHERE id = @user_id;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_InvalidateOtps
    @email_normalized nvarchar(320),
    @purpose varchar(30),
    @now datetime2
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    UPDATE dbo.tc_otps
       SET invalidated_at = @now
     WHERE LOWER(email_normalized) = LOWER(LTRIM(RTRIM(@email_normalized)))
       AND purpose = @purpose
       AND consumed_at IS NULL
       AND invalidated_at IS NULL;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_IssueOtp
    @user_id uniqueidentifier,
    @email_normalized nvarchar(320),
    @purpose varchar(30),
    @secret_hash varbinary(64),
    @expires_at datetime2,
    @now datetime2
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @tran_started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @tran_started = 1
        BEGIN TRANSACTION;
    ELSE
        SAVE TRANSACTION usp_IssueOtp_sp;

    BEGIN TRY
        DECLARE @created TABLE (id uniqueidentifier);

        EXEC dbo.usp_InvalidateOtps @email_normalized, @purpose, @now;

        INSERT dbo.tc_otps (user_id, email_normalized, purpose, secret_hash, expires_at, failed_attempts, created_at)
        OUTPUT inserted.id INTO @created
        VALUES (@user_id, @email_normalized, @purpose, @secret_hash, @expires_at, 0, @now);

        IF @tran_started = 1
            COMMIT TRANSACTION;

        SELECT id AS otp_id FROM @created;
    END TRY
    BEGIN CATCH
        IF @tran_started = 1 AND XACT_STATE() <> 0
            ROLLBACK TRANSACTION;
        ELSE IF @tran_started = 0 AND XACT_STATE() = 1
            ROLLBACK TRANSACTION usp_IssueOtp_sp;
        THROW;
    END CATCH;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_ConsumeOtp
    @otp_id uniqueidentifier,
    @now datetime2
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    UPDATE dbo.tc_otps
       SET consumed_at = @now
     WHERE id = @otp_id
       AND consumed_at IS NULL
       AND invalidated_at IS NULL
       AND expires_at > @now
       AND failed_attempts < 5;

    SELECT CAST(@@ROWCOUNT AS bit) AS consumed;
END;
GO
CREATE OR ALTER PROCEDURE dbo.usp_RecordOtpFailure
    @otp_id uniqueidentifier,
    @now datetime2
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    UPDATE dbo.tc_otps
       SET failed_attempts = failed_attempts + 1,
           invalidated_at = CASE WHEN failed_attempts + 1 >= 5 THEN @now ELSE invalidated_at END
     WHERE id = @otp_id
       AND consumed_at IS NULL
       AND invalidated_at IS NULL;

    SELECT failed_attempts FROM dbo.tc_otps WHERE id = @otp_id;
END;
GO
GRANT EXECUTE ON dbo.usp_RegisterUser TO tc_auth;
GRANT EXECUTE ON dbo.usp_RehashPassword TO tc_auth;
GRANT EXECUTE ON dbo.usp_ResetPassword TO tc_auth;
GRANT EXECUTE ON dbo.usp_RevokeUserSessions TO tc_auth;
GRANT EXECUTE ON dbo.usp_MarkEmailVerified TO tc_auth;
GRANT EXECUTE ON dbo.usp_InvalidateOtps TO tc_auth;
GRANT EXECUTE ON dbo.usp_IssueOtp TO tc_auth;
GRANT EXECUTE ON dbo.usp_ConsumeOtp TO tc_auth;
GRANT EXECUTE ON dbo.usp_RecordOtpFailure TO tc_auth;
GO
REVOKE INSERT ON dbo.tc_users FROM tc_auth;
REVOKE UPDATE ON dbo.tc_users (password_hash, email_verified_at, auth_version, version, updated_at) FROM tc_auth;
REVOKE INSERT, UPDATE ON dbo.tc_otps FROM tc_auth;
GO
