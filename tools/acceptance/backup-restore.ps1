#Requires -Version 7.4
param([Parameter(Mandatory)][string]$ConfigPath,[Parameter(Mandatory)][string]$OutputDirectory,[Parameter(Mandatory)][string]$RestoredDatabase)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'fixture.ps1')
$fixture=Open-AcceptanceFixture $ConfigPath
try {
 if($RestoredDatabase -cnotmatch '^TC_Vuong_Test_Local_[A-Za-z0-9_]{1,40}$' -or $RestoredDatabase -ceq $fixture.config.database){throw 'NEW dedicated restore database required.'}
 $exists=Invoke-AcceptanceQuery $fixture.connection "SELECT DB_ID(N'$RestoredDatabase') id"
 if($null -ne $exists[0].id){throw 'Restore database already exists; refusing to overwrite.'}
 $output=New-AcceptanceOutput $OutputDirectory
 $dirs=(Invoke-AcceptanceQuery $fixture.connection "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath')) backup_path,CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultDataPath')) data_path,CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultLogPath')) log_path")[0]
 foreach($dir in @($dirs.backup_path,$dirs.data_path,$dirs.log_path)){if(-not $dir -or -not(Test-Path -LiteralPath $dir -PathType Container)){throw 'Existing SQL default backup/data/log directories required.'}}
 $nonce=[guid]::NewGuid().ToString('N');$backup=Join-Path $dirs.backup_path ($fixture.config.database+'_'+$nonce+'.bak')
 if(Test-Path -LiteralPath $backup){throw 'Backup file already exists.'}
 $quoted=$backup.Replace("'","''")
 Invoke-AcceptanceSql $fixture.connection "BACKUP DATABASE [$($fixture.config.database)] TO DISK=N'$quoted' WITH COPY_ONLY,CHECKSUM; RESTORE VERIFYONLY FROM DISK=N'$quoted' WITH CHECKSUM;"
 $files=Invoke-AcceptanceQuery $fixture.connection "RESTORE FILELISTONLY FROM DISK=N'$quoted'"
 $moves=@();$physical=@();$counter=0
 foreach($file in $files){if($file.Type -notin @('D','L')){throw 'Only standard data/log backup files supported.'};$counter++;$dir=if($file.Type -eq 'L'){$dirs.log_path}else{$dirs.data_path};$suffix=if($file.Type -eq 'L'){'.ldf'}else{'.mdf'};$target=Join-Path $dir ($RestoredDatabase+'_'+$nonce+'_'+$counter+$suffix);if(Test-Path -LiteralPath $target){throw 'Restore physical file already exists.'};$physical+=$target;$moves+="MOVE N'$($file.LogicalName.Replace("'","''"))' TO N'$($target.Replace("'","''"))'"}
 Invoke-AcceptanceSql $fixture.connection ("RESTORE DATABASE [$RestoredDatabase] FROM DISK=N'$quoted' WITH CHECKSUM,RECOVERY,"+($moves -join ','))
 $restored=[System.Data.SqlClient.SqlConnection]::new("Server=tcp:127.0.0.1,1433;Database=$RestoredDatabase;Integrated Security=True;TrustServerCertificate=True;Connection Timeout=5")
 try {
  $restored.Open();Invoke-AcceptanceSql $restored "DBCC CHECKDB WITH NO_INFOMSGS;"
  $tables=Invoke-AcceptanceQuery $fixture.connection "SELECT SCHEMA_NAME(t.schema_id) schema_name,t.name table_name,STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(c.name)),',') WITHIN GROUP(ORDER BY ic.key_ordinal) pk_columns FROM sys.tables t JOIN sys.indexes i ON i.object_id=t.object_id AND i.is_primary_key=1 JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE t.is_ms_shipped=0 GROUP BY t.schema_id,t.name ORDER BY schema_name,table_name"
  $before=@();$after=@()
  foreach($table in $tables){
   $name='['+$table.schema_name.Replace(']',']]')+'].['+$table.table_name.Replace(']',']]')+']'
   foreach($entry in @(@{connection=$fixture.connection;phase='source'},@{connection=$restored;phase='restored'})){
    $snapshot=(Invoke-AcceptanceQuery $entry.connection "SELECT (SELECT COUNT_BIG(*) FROM $name) row_count,CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(nvarchar(max),COALESCE((SELECT * FROM $name ORDER BY $($table.pk_columns) FOR JSON PATH,INCLUDE_NULL_VALUES),N'[]'))),2) data_hash")[0]
    $row=[ordered]@{table=$name;rowCount=$snapshot.row_count;dataHash=$snapshot.data_hash}
    if($entry.phase -eq 'source'){$before+=$row}else{$after+=$row}
   }
  }
  $tableCount=(Invoke-AcceptanceQuery $fixture.connection 'SELECT COUNT(*) n FROM sys.tables WHERE is_ms_shipped=0')[0].n
  if($tables.Count -ne $tableCount){throw 'Every source table must have a primary key for deterministic data comparison.'}
  if(($before|ConvertTo-Json -Compress) -cne ($after|ConvertTo-Json -Compress)){throw 'Restored row counts differ; retain both databases for inspection.'}
  $schemaSql="SELECT SCHEMA_NAME(schema_id) schema_name,name,type,CONVERT(varchar(64),HASHBYTES('SHA2_256',COALESCE(OBJECT_DEFINITION(object_id),'')),2) definition_hash FROM sys.objects WHERE is_ms_shipped=0 ORDER BY type,name"
  $sourceSchema=Invoke-AcceptanceQuery $fixture.connection $schemaSql;$restoredSchema=Invoke-AcceptanceQuery $restored $schemaSql
  if(($sourceSchema|ConvertTo-Json -Compress) -cne ($restoredSchema|ConvertTo-Json -Compress)){throw 'Restored schema differs.'}
  $columnsSql="SELECT OBJECT_SCHEMA_NAME(c.object_id) schema_name,OBJECT_NAME(c.object_id) object_name,c.name,TYPE_NAME(c.user_type_id) data_type,c.max_length,c.precision,c.scale,c.is_nullable,c.is_identity,c.is_computed,c.collation_name FROM sys.columns c JOIN sys.objects o ON o.object_id=c.object_id WHERE o.is_ms_shipped=0 ORDER BY schema_name,object_name,c.column_id"
  $sourceColumns=Invoke-AcceptanceQuery $fixture.connection $columnsSql;$restoredColumns=Invoke-AcceptanceQuery $restored $columnsSql
  if(($sourceColumns|ConvertTo-Json -Compress) -cne ($restoredColumns|ConvertTo-Json -Compress)){throw 'Restored column signatures differ.'}
  $indexesSql="SELECT OBJECT_SCHEMA_NAME(i.object_id) schema_name,OBJECT_NAME(i.object_id) table_name,i.name,i.type_desc,i.is_unique,i.is_primary_key,i.is_disabled,i.filter_definition,c.name column_name,ic.key_ordinal,ic.is_descending_key,ic.is_included_column FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id JOIN sys.tables t ON t.object_id=i.object_id WHERE t.is_ms_shipped=0 ORDER BY schema_name,table_name,i.name,ic.index_column_id"
  $sourceIndexes=Invoke-AcceptanceQuery $fixture.connection $indexesSql;$restoredIndexes=Invoke-AcceptanceQuery $restored $indexesSql
  if(($sourceIndexes|ConvertTo-Json -Compress) -cne ($restoredIndexes|ConvertTo-Json -Compress)){throw 'Restored index signatures differ.'}
  if(-not $fixture.config.user -or $fixture.config.user -cnotmatch '^tc_local_[a-f0-9]{32}$'){throw 'Generated local login required for runtime restore config.'}
  Invoke-AcceptanceSql $restored "ALTER USER [$($fixture.config.user)] WITH LOGIN=[$($fixture.config.user)];"
  @{database=$RestoredDatabase;url="jdbc:sqlserver://127.0.0.1:1433;databaseName=$RestoredDatabase;encrypt=false;trustServerCertificate=true;loginTimeout=5;";user=$fixture.config.user;password=$fixture.config.password}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $output 'restored-database.json') -Encoding utf8
  @{source=$fixture.config.database;restored=$RestoredDatabase;backup=$backup;physicalFiles=$physical;verified=$true;checkdb='PASS';rows=$before;schema=$sourceSchema;columns=$sourceColumns;indexes=$sourceIndexes}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $output 'restore-evidence.json') -Encoding utf8
 }finally{$restored.Dispose()}
 Write-Output ('BACKUP_RESTORE_PASS '+$output)
}finally{$fixture.connection.Dispose()}
