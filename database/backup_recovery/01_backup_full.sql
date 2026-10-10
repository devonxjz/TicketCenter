/* ============================================================================
 * 01_backup_full.sql
 *
 * TicketsCenter Database Management - Full Database Backup Script
 * Target: Microsoft SQL Server (Docker / Linux / On-Premise)
 *
 * Description:
 *   Performs a complete baseline backup of the TicketsCenter database.
 *   Uses compression, checksum validation, and outputs execution statistics.
 *   Verifies the backup file integrity upon completion.
 *
 * Note:
 *   For Azure SQL Database (PaaS), full backups are automated by Azure weekly.
 *   This script is used for Local Dev, Staging, On-Premise, or Docker instances.
 * ============================================================================ */

SET NOCOUNT ON;
GO

DECLARE @DatabaseName sysname = DB_NAME();
IF @DatabaseName IN ('master', 'tempdb', 'model', 'msdb')
BEGIN
    SET @DatabaseName = N'TicketsCenter_Dev';
END;

DECLARE @BackupDir nvarchar(260) = N'/var/opt/mssql/backup';
DECLARE @Timestamp nvarchar(20) = REPLACE(CONVERT(nvarchar(20), GETUTCDATE(), 120), ':', '-');
SET @Timestamp = REPLACE(@Timestamp, ' ', '_');

DECLARE @BackupFileName nvarchar(400) = @BackupDir + N'/' + @DatabaseName + N'_Full_' + @Timestamp + N'.bak';
DECLARE @BackupName nvarchar(256) = @DatabaseName + N' - Full Database Backup (' + @Timestamp + N' UTC)';

PRINT '----------------------------------------------------------------------';
PRINT 'Starting Full Backup for database: ' + @DatabaseName;
PRINT 'Target File: ' + @BackupFileName;
PRINT 'Start Time:  ' + CONVERT(nvarchar(30), GETUTCDATE(), 121) + ' UTC';
PRINT '----------------------------------------------------------------------';

-- Execute Full Backup
BACKUP DATABASE @DatabaseName
TO DISK = @BackupFileName
WITH 
    INIT,                           -- Overwrite any existing content in this file
    FORMAT,                         -- Create a new media set
    NAME = @BackupName,
    DESCRIPTION = N'Full database backup with checksum and compression',
    COMPRESSION,                    -- Reduce disk space and I/O overhead
    CHECKSUM,                       -- Verify page checksums during backup
    STATS = 10;                     -- Output progress every 10 percent

-- Verify backup file integrity
PRINT 'Verifying backup media integrity...';
RESTORE VERIFYONLY
FROM DISK = @BackupFileName
WITH CHECKSUM;

IF @@ERROR = 0
BEGIN
    PRINT '✅ FULL BACKUP COMPLETED AND VERIFIED SUCCESSFULLY: ' + @BackupFileName;
END
ELSE
BEGIN
    RAISERROR('❌ Full backup verification failed!', 16, 1);
END
GO
