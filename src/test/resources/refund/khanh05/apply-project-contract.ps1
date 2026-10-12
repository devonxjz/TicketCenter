$ErrorActionPreference='Stop'
# KHANH-05 schema-only installation. Fixed project target; no business data or passwords changed.
function Get-Hashes([string]$path) {
 $raw=[IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $path).Path)
 $lf=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $path).Path).Replace("`r`n","`n").Replace("`r","`n")
 $sha=[Security.Cryptography.SHA256]::Create()
 try {
  foreach($bytes in @($raw,[Text.Encoding]::UTF8.GetBytes($lf),[Text.Encoding]::UTF8.GetBytes($lf.Replace("`n","`r`n")))) {
   ([BitConverter]::ToString($sha.ComputeHash([byte[]]$bytes))).Replace('-','').ToLowerInvariant()
  }
 } finally {$sha.Dispose()}
}
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object {
 if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected project target'}
$builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source']='tcp:'+ $settings['db.host']+',1433';$builder['Initial Catalog']=$settings['db.name']
$builder['User ID']=$settings['db.user'];$builder['Password']=$settings['db.password'];$builder['Encrypt']=$true;$builder['TrustServerCertificate']=$false;$builder['Connect Timeout']=30;$builder['Pooling']=$false
$connection=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
function Query([string]$sql) {
 $command=$connection.CreateCommand();$command.Transaction=$transaction;$command.CommandText=$sql;$command.CommandTimeout=30
 return $command
}
try {
 $connection.Open();$transaction=$connection.BeginTransaction()
 try {
  $q=Query "DECLARE @r int;EXEC @r=sys.sp_getapplock @Resource=N'tc:migrations',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=15000;IF @r<0 THROW 53590,'Migration lock unavailable',1;"
  [void]$q.ExecuteNonQuery();$q.Dispose()
  foreach($name in @('031_sql_report_financial_safety','032_sql_report_permissions','033_sql_report_indexes','039_sql_report_contract_consistency')) {
   $q=Query 'SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version=@v';[void]$q.Parameters.AddWithValue('@v',$name);$old=$q.ExecuteScalar();$q.Dispose()
   if($null -eq $old -or $old -is [DBNull] -or $old -notin @(Get-Hashes "database/migrations/$name.sql")){throw "Missing or mismatched required migration: $name"}
  }
  $migration='database/migrations/045_khanh05_refund_worker.sql';$version=[IO.Path]::GetFileNameWithoutExtension($migration)
  $hashes=@(Get-Hashes $migration)
  $q=Query 'SELECT checksum_sha256 FROM dbo.tc_schema_migrations WITH(UPDLOCK,HOLDLOCK) WHERE version=@v';[void]$q.Parameters.AddWithValue('@v',$version);$old=$q.ExecuteScalar();$q.Dispose()
  if($null -ne $old -and $old -isnot [DBNull]) {
   if($old -notin $hashes){throw 'KHANH-05 checksum conflict; refusing migration rewrite'}
   $transaction.Commit();Write-Output 'KHANH-05 already installed with matching checksum.';return
  }
  $q=Query "IF OBJECT_ID('dbo.tc_refund_dispatches','U') IS NOT NULL OR OBJECT_ID('dbo.tc_refund_attempts') IS NOT NULL OR OBJECT_ID('dbo.vw_RefundWorkerCandidates') IS NOT NULL OR OBJECT_ID('dbo.sp_refund_begin_attempt') IS NOT NULL OR OBJECT_ID('dbo.sp_refund_record_attempt_result') IS NOT NULL OR OBJECT_ID('dbo.sp_refund_complete_without_transfer') IS NOT NULL THROW 53591,'Untracked worker contract; reconcile before installing',1;"
  [void]$q.ExecuteNonQuery();$q.Dispose()
  foreach($batch in [regex]::Split([IO.File]::ReadAllText((Resolve-Path $migration).Path),'(?im)^\s*GO\s*\r?$')) {
   if(!$batch.Trim()){continue};$q=Query $batch;[void]$q.ExecuteNonQuery();$q.Dispose()
  }
  $q=Query 'INSERT dbo.tc_schema_migrations(version,checksum_sha256) VALUES(@v,@h)';[void]$q.Parameters.AddWithValue('@v',$version);[void]$q.Parameters.AddWithValue('@h',$hashes[1]);[void]$q.ExecuteNonQuery();$q.Dispose()
  $transaction.Commit();Write-Output 'KHANH-05 contract installed atomically; checksum recorded. No business data changed.'
 }catch{if($transaction.Connection){$transaction.Rollback()};throw}
}finally{$connection.Dispose()}
