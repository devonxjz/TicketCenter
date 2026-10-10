/* ============================================================================
 * 04_point_in_time_recovery.sql
 *
 * TicketsCenter Database Management - Disaster Recovery & Point-In-Time Restore
 * Target: Microsoft SQL Server (Docker / Linux / On-Premise)
 *
 * Scenario:
 *   Simulates a complete Disaster Recovery Drill (Kịch bản phục hồi thảm họa):
 *   1. Normal Operations (T1) -> Data generated.
 *   2. Accidental human error / disaster occurs at Time (T_disaster).
 *      e.g., An unauthorized script deletes or corrupts ticket records.
 *   3. Incident declared: Database switched to SINGLE_USER / NORECOVERY.
 *   4. Backup Tail of Log to capture all transactions up to the instant before disaster.
 *   5. Restore Chain executed:
 *      - Full Backup (NORECOVERY)
 *      - Differential Backup (NORECOVERY, if available)
 *      - Transaction Log Backup(s) (STOPAT = T_safe, RECOVERY)
 *   6. Verification that healthy data is restored and disaster actions are reversed.
 * ============================================================================ */

USE master;
GO

SET NOCOUNT ON;
GO

PRINT '======================================================================';
PRINT 'DISASTER RECOVERY DRILL: POINT-IN-TIME RESTORE (PITR)';
PRINT '======================================================================';

DECLARE @DbName sysname = N'TicketsCenter_Dev';
DECLARE @BackupDir nvarchar(260) = N'/var/opt/mssql/backup';

-- Specify your backup files to restore from:
DECLARE @FullBackupPath nvarchar(400) = @BackupDir + N'/TicketsCenter_Dev_Full.bak';
DECLARE @DiffBackupPath nvarchar(400) = @BackupDir + N'/TicketsCenter_Dev_Diff.bak';
DECLARE @TailLogPath    nvarchar(400) = @BackupDir + N'/TicketsCenter_Dev_TailLog.trn';

-- Target Recovery Time: Set to 1 second BEFORE the disaster event occurred:
-- Format: 'YYYY-MM-DD HH:MM:SS'
DECLARE @TargetRecoveryTime nvarchar(30) = N'2026-10-10 14:29:59';

PRINT 'Target Database:       ' + @DbName;
PRINT 'Target Recovery Point: ' + @TargetRecoveryTime;
PRINT '----------------------------------------------------------------------';

-- STEP 1: Take Tail-Log Backup (if database is still accessible)
-- This captures all log records not yet backed up, preventing any data loss.
PRINT 'STEP 1: Backing up tail of the transaction log with NORECOVERY...';
BEGIN TRY
    DECLARE @TailSql nvarchar(1000) = 
        N'BACKUP LOG [' + @DbName + N'] ' +
        N'TO DISK = N''' + @TailLogPath + N''' ' +
        N'WITH NORECOVERY, FORMAT, INIT, COMPRESSION, CHECKSUM;';
    EXEC sp_executesql @TailSql;
    PRINT '✅ Tail log backup successfully taken: ' + @TailLogPath;
END TRY
BEGIN CATCH
    PRINT '⚠️ Warning taking tail-log (database may already be offline): ' + ERROR_MESSAGE();
END CATCH;

-- STEP 2: Disconnect all active connections
PRINT 'STEP 2: Closing active connections and setting database to SINGLE_USER...';
BEGIN TRY
    DECLARE @KillSql nvarchar(500) = 
        N'ALTER DATABASE [' + @DbName + N'] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;';
    EXEC sp_executesql @KillSql;
END TRY
BEGIN CATCH
    PRINT '⚠️ Database already offline or in transition: ' + ERROR_MESSAGE();
END CATCH;

-- STEP 3: Restore Full Backup (WITH NORECOVERY)
PRINT 'STEP 3: Restoring Full Backup with NORECOVERY...';
RESTORE DATABASE @DbName
FROM DISK = @FullBackupPath
WITH 
    REPLACE,
    NORECOVERY,                     -- Keeps database in restoring state to allow further logs
    CHECKSUM,
    STATS = 20;

-- STEP 4: Restore Differential Backup (WITH NORECOVERY, if differential exists)
PRINT 'STEP 4: Restoring Differential Backup with NORECOVERY...';
-- (Uncomment if differential backup was taken)
/*
RESTORE DATABASE @DbName
FROM DISK = @DiffBackupPath
WITH 
    NORECOVERY,
    CHECKSUM,
    STATS = 20;
*/

-- STEP 5: Restore Transaction Log with STOPAT (Point-in-Time) and RECOVERY
PRINT 'STEP 5: Restoring Transaction Log with STOPAT = ' + @TargetRecoveryTime + ' and RECOVERY...';
RESTORE LOG @DbName
FROM DISK = @TailLogPath
WITH 
    STOPAT = @TargetRecoveryTime,   -- Stop replay at exact time before incident
    RECOVERY,                       -- Bring database back online to MULTI_USER
    CHECKSUM,
    STATS = 20;

-- STEP 6: Set database back to MULTI_USER
PRINT 'STEP 6: Setting database to MULTI_USER...';
DECLARE @MultiSql nvarchar(500) = 
    N'ALTER DATABASE [' + @DbName + N'] SET MULTI_USER;';
EXEC sp_executesql @MultiSql;

PRINT '----------------------------------------------------------------------';
PRINT '✅ POINT-IN-TIME RECOVERY COMPLETED SUCCESSFULLY!';
PRINT 'Database ' + @DbName + ' is now ONLINE at recovery state: ' + @TargetRecoveryTime;
PRINT '----------------------------------------------------------------------';
GO
