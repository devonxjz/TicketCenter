/* ============================================================================
 * 02_backup_diff.sql
 *
 * TicketsCenter Database Management - Differential Database Backup Script
 * Target: Microsoft SQL Server (Docker / Linux / On-Premise)
 *
 * Description:
 *   Captures only the extents (data pages) that have changed since the last
 *   successful Full Database Backup.
 *   Typically scheduled daily or every 12 hours between weekly full backups.
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

DECLARE @BackupFileName nvarchar(400) = @BackupDir + N'/' + @DatabaseName + N'_Diff_' + @Timestamp + N'.bak';
DECLARE @BackupName nvarchar(256) = @DatabaseName + N' - Differential Database Backup (' + @Timestamp + N' UTC)';

PRINT '----------------------------------------------------------------------';
PRINT 'Starting Differential Backup for database: ' + @DatabaseName;
PRINT 'Target File: ' + @BackupFileName;
PRINT 'Start Time:  ' + CONVERT(nvarchar(30), GETUTCDATE(), 121) + ' UTC';
PRINT '----------------------------------------------------------------------';

-- Execute Differential Backup
BACKUP DATABASE @DatabaseName
TO DISK = @BackupFileName
WITH 
    DIFFERENTIAL,                   -- Capture changes since base Full Backup
    INIT,                           -- Create new file container
    NAME = @BackupName,
    DESCRIPTION = N'Differential database backup since last full backup',
    COMPRESSION,
    CHECKSUM,
    STATS = 10;

-- Verify integrity
PRINT 'Verifying differential backup integrity...';
RESTORE VERIFYONLY
FROM DISK = @BackupFileName
WITH CHECKSUM;

IF @@ERROR = 0
BEGIN
    PRINT '✅ DIFFERENTIAL BACKUP COMPLETED AND VERIFIED SUCCESSFULLY: ' + @BackupFileName;
END
ELSE
BEGIN
    RAISERROR('❌ Differential backup verification failed!', 16, 1);
END
GO
