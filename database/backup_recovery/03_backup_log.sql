/* ============================================================================
 * 03_backup_log.sql
 *
 * TicketsCenter Database Management - Transaction Log Backup Script
 * Target: Microsoft SQL Server (Docker / Linux / On-Premise)
 *
 * Description:
 *   Captures all transaction log records since the previous log backup.
 *   Truncates the inactive portion of the transaction log.
 *   Enables Point-In-Time Recovery (PITR) to minimize RPO (< 15 minutes).
 *
 * Prerequisite:
 *   Database must be in FULL recovery model.
 * ============================================================================ */

SET NOCOUNT ON;
GO

DECLARE @DatabaseName sysname = DB_NAME();
IF @DatabaseName IN ('master', 'tempdb', 'model', 'msdb')
BEGIN
    SET @DatabaseName = N'TicketsCenter_Dev';
END;

-- Ensure FULL recovery model
DECLARE @RecoveryModelDesc nvarchar(60);
SELECT @RecoveryModelDesc = recovery_model_desc 
FROM sys.databases 
WHERE name = @DatabaseName;

IF @RecoveryModelDesc <> 'FULL'
BEGIN
    PRINT 'Switching database ' + @DatabaseName + ' to FULL recovery model...';
    DECLARE @AlterSql nvarchar(500) = N'ALTER DATABASE [' + @DatabaseName + N'] SET RECOVERY FULL WITH NO_WAIT;';
    EXEC sp_executesql @AlterSql;
END;

DECLARE @BackupDir nvarchar(260) = N'/var/opt/mssql/backup';
DECLARE @Timestamp nvarchar(20) = REPLACE(CONVERT(nvarchar(20), GETUTCDATE(), 120), ':', '-');
SET @Timestamp = REPLACE(@Timestamp, ' ', '_');

DECLARE @BackupFileName nvarchar(400) = @BackupDir + N'/' + @DatabaseName + N'_Log_' + @Timestamp + N'.trn';
DECLARE @BackupName nvarchar(256) = @DatabaseName + N' - Transaction Log Backup (' + @Timestamp + N' UTC)';

PRINT '----------------------------------------------------------------------';
PRINT 'Starting Transaction Log Backup for database: ' + @DatabaseName;
PRINT 'Target File: ' + @BackupFileName;
PRINT 'Start Time:  ' + CONVERT(nvarchar(30), GETUTCDATE(), 121) + ' UTC';
PRINT '----------------------------------------------------------------------';

-- Execute Transaction Log Backup
BACKUP LOG @DatabaseName
TO DISK = @BackupFileName
WITH 
    INIT,
    NAME = @BackupName,
    DESCRIPTION = N'Transaction log backup for Point-In-Time Recovery',
    COMPRESSION,
    CHECKSUM,
    STATS = 10;

-- Verify integrity
PRINT 'Verifying log backup integrity...';
RESTORE VERIFYONLY
FROM DISK = @BackupFileName
WITH CHECKSUM;

IF @@ERROR = 0
BEGIN
    PRINT '✅ TRANSACTION LOG BACKUP COMPLETED AND VERIFIED SUCCESSFULLY: ' + @BackupFileName;
END
ELSE
BEGIN
    RAISERROR('❌ Transaction log backup verification failed!', 16, 1);
END
GO
