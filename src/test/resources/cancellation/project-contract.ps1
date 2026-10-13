param([ValidateSet('Preflight','Install','Verify')][string]$Mode='Verify')
$ErrorActionPreference='Stop'
# Schema only. No cancellation, refund, seeding, password reset or data test runs here.
function Get-CancellationHashes([string]$Path) {
 $taskPath=(Resolve-Path -LiteralPath $Path).Path
 $taskText=[IO.File]::ReadAllText($taskPath).Replace("`r`n","`n").Replace("`r","`n")
 $taskHash=[Security.Cryptography.SHA256]::Create()
 try {foreach($taskBytes in @([IO.File]::ReadAllBytes($taskPath),[Text.Encoding]::UTF8.GetBytes($taskText),[Text.Encoding]::UTF8.GetBytes($taskText.Replace("`n","`r`n")))) {
  ([BitConverter]::ToString($taskHash.ComputeHash([byte[]]$taskBytes))).Replace('-','').ToLowerInvariant()
 }}finally{$taskHash.Dispose()}
}
$taskSettings=@{};$taskConfig=$env:TC_CONFIG_FILE
if([string]::IsNullOrWhiteSpace($taskConfig)){$taskConfig='.local/application.properties'}
Get-Content -LiteralPath $taskConfig | ForEach-Object {if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$taskSettings[$Matches[1]]=$Matches[2].Trim()}}
if($taskSettings['db.host'] -ne 'ticketscenter.database.windows.net' -or $taskSettings['db.name'] -ne 'TicketsCenter'){throw 'Unexpected project target'}
$taskBuilder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$taskBuilder['Data Source']='tcp:'+$taskSettings['db.host']+',1433';$taskBuilder['Initial Catalog']=$taskSettings['db.name'];$taskBuilder['User ID']=$taskSettings['db.user'];$taskBuilder['Password']=$taskSettings['db.password'];$taskBuilder['Encrypt']=$true;$taskBuilder['TrustServerCertificate']=$false;$taskBuilder['Connect Timeout']=30;$taskBuilder['Pooling']=$false
$taskConnection=[System.Data.SqlClient.SqlConnection]::new($taskBuilder.ConnectionString)
$taskTransaction=$null
function New-CancellationCommand([string]$Sql) {
 $taskCommand=$taskConnection.CreateCommand();$taskCommand.CommandTimeout=45;$taskCommand.Transaction=$taskTransaction;$taskCommand.CommandText=$Sql;return $taskCommand
}
try {
 $taskConnection.Open()
 foreach($taskPrerequisite in @('046_admin_refund_current_authority','048_khanh05_refund_worker','050_consolidate_canonical_tables')) {
  $taskCommand=New-CancellationCommand 'IF (SELECT COUNT(*) FROM dbo.tc_schema_migrations WHERE version=@v OR version=@v+''.sql'')<>1 THROW 53990,''Missing or ambiguous prerequisite'',1;SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version=@v OR version=@v+''.sql'''
  [void]$taskCommand.Parameters.AddWithValue('@v',$taskPrerequisite);$taskOld=$taskCommand.ExecuteScalar();$taskCommand.Dispose()
  if($taskOld -notin @(Get-CancellationHashes "database/migrations/$taskPrerequisite.sql")){throw ('Prerequisite checksum mismatch: '+$taskPrerequisite)}
 }
 $taskMigration='database/migrations/058_khanh06_event_cancellation.sql';$taskVersion=[IO.Path]::GetFileName($taskMigration);$taskHashes=@(Get-CancellationHashes $taskMigration)
 $taskCommand=New-CancellationCommand 'IF (SELECT COUNT(*) FROM dbo.tc_schema_migrations WHERE version=@v OR version=REPLACE(@v,''.sql'',''''))>1 THROW 53990,''Ambiguous cancellation migration'',1;SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version=@v OR version=REPLACE(@v,''.sql'','''')'
 [void]$taskCommand.Parameters.AddWithValue('@v',$taskVersion);$taskOld=$taskCommand.ExecuteScalar();$taskCommand.Dispose()
 if($null -ne $taskOld -and $taskOld -isnot [DBNull] -and $taskOld -notin $taskHashes){throw 'Cancellation migration checksum conflict; refusing rewrite'}
 if($Mode -eq 'Preflight'){Write-Output 'KHANH-06 project prerequisites/checksums verified read-only.';return}
 if($Mode -eq 'Install' -and ($null -eq $taskOld -or $taskOld -is [DBNull])) {
  $taskCommand=New-CancellationCommand "IF OBJECT_ID('dbo.tc_cancellation_jobs') IS NOT NULL OR OBJECT_ID('dbo.tc_cancellation_orders') IS NOT NULL OR OBJECT_ID('dbo.tc_cancellation_receipts') IS NOT NULL OR OBJECT_ID('dbo.sp_event_cancel') IS NOT NULL THROW 53990,'Untracked cancellation contract; refusing overwrite',1;"
  [void]$taskCommand.ExecuteNonQuery();$taskCommand.Dispose()
  $taskCommand=New-CancellationCommand "SELECT OBJECT_DEFINITION(OBJECT_ID('dbo.usp_CancelEvent'))"
  $taskBefore=$taskCommand.ExecuteScalar();$taskCommand.Dispose()
  if($null -eq $taskBefore -or $taskBefore -is [DBNull]){throw 'Missing canonical SP13 prerequisite'}
  # This definition snapshot contains no rows or secrets, and lives under ignored target/.
  [IO.File]::WriteAllText((Join-Path (Get-Location) 'target/khanh06-project-sp13-before.sql'),[string]$taskBefore,[Text.Encoding]::UTF8)
  $taskTransaction=$taskConnection.BeginTransaction()
  try {
   $taskCommand=New-CancellationCommand "DECLARE @r int;EXEC @r=sys.sp_getapplock @Resource=N'tc:migrations',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=15000;IF @r<0 THROW 53990,'Migration lock unavailable',1;"
   [void]$taskCommand.ExecuteNonQuery();$taskCommand.Dispose()
   foreach($taskBatch in [regex]::Split([IO.File]::ReadAllText((Resolve-Path -LiteralPath $taskMigration).Path),'(?im)^\s*GO\s*\r?$')) {
    if(!$taskBatch.Trim()){continue};$taskCommand=New-CancellationCommand $taskBatch;[void]$taskCommand.ExecuteNonQuery();$taskCommand.Dispose()
   }
   $taskCommand=New-CancellationCommand 'INSERT dbo.tc_schema_migrations(version,checksum_sha256) VALUES(@v,@h)'
   [void]$taskCommand.Parameters.AddWithValue('@v',$taskVersion);[void]$taskCommand.Parameters.AddWithValue('@h',$taskHashes[1]);[void]$taskCommand.ExecuteNonQuery();$taskCommand.Dispose()
   $taskTransaction.Commit()
  }catch{if($taskTransaction.Connection){$taskTransaction.Rollback()};throw}finally{$taskTransaction.Dispose();$taskTransaction=$null}
  Write-Output 'KHANH-06 schema installed atomically; existing business rows were not updated.'
 }
 $taskCommand=New-CancellationCommand "IF OBJECT_ID('dbo.tc_cancellation_jobs','U') IS NULL OR OBJECT_ID('dbo.sp_event_cancel','P') IS NULL OR OBJECT_ID('dbo.sp_refund_adopt_event_cancellation','P') IS NULL OR OBJECT_ID('dbo.sp_event_cancellation_finish','P') IS NULL THROW 53990,'Missing cancellation runtime',1;SELECT COUNT(*) FROM sys.database_permissions WHERE major_id=OBJECT_ID('dbo.tc_cancellation_jobs') AND state='D' AND permission_name IN('SELECT','INSERT','UPDATE','DELETE')"
 $taskDenials=[int]$taskCommand.ExecuteScalar();$taskCommand.Dispose();if($taskDenials -lt 24){throw 'Missing queue direct-access denials'}
 foreach($taskRole in @('tc_platform_admin','tc_worker')) {
  $taskCommand=New-CancellationCommand 'SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID(@role) AND state IN(''G'',''W'') AND permission_name=''EXECUTE'' AND major_id IN(OBJECT_ID(''dbo.sp_event_cancel''),OBJECT_ID(''dbo.sp_refund_adopt_event_cancellation''),OBJECT_ID(''dbo.sp_event_cancellation_finish''))'
  [void]$taskCommand.Parameters.AddWithValue('@role',$taskRole);$taskCount=[int]$taskCommand.ExecuteScalar();$taskCommand.Dispose()
  if($taskCount -ne $(if($taskRole -eq 'tc_worker'){2}else{1})){throw 'Missing or excessive procedure grant'}
 }
 Write-Output 'KHANH-06 project schema, checksum prerequisites, ADMIN/WORKER grants and queue denials verified read-only.'
}catch {
 # Never echo SqlConnectionString, settings or provider error text that could contain credentials.
 throw ('KHANH-06 project contract failed: '+$_.Exception.GetType().Name+'. No business-data test was executed.')
}finally{$taskConnection.Dispose()}
