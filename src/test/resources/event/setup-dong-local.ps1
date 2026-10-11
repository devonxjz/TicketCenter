param([switch]$ResumeCreatedFixture,
 [ValidatePattern('^TicketCenter_Dong_[A-Za-z0-9_]+$')][string]$DatabaseName='TicketCenter_Dong_Epic_Test_20261011')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$database=$DatabaseName
$connection=[System.Data.SqlClient.SqlConnection]::new('Server=.\SQLEXPRESS;Database=master;Integrated Security=true;TrustServerCertificate=true')
$connection.Open()
try {
 $command=$connection.CreateCommand();$command.CommandText="SELECT DB_ID(N'$database')"
 if($command.ExecuteScalar() -isnot [DBNull]) {
  if(!$ResumeCreatedFixture){throw 'Dedicated Dong database already exists; refusing to overwrite it.'}
 } else {$command.CommandText="CREATE DATABASE [$database]";$command.ExecuteNonQuery() | Out-Null}
 $connection.ChangeDatabase($database)
 $files=@(Get-ChildItem -LiteralPath database/migrations -Filter '*.sql' | Sort-Object Name)
 # Resolve the mutually dependent baseline functions/views before adapters.
 # A deferred004 batch must never overwrite a later finance projection.
 $phases=@([pscustomobject]@{files=@($files | Where-Object {$_.Name -match '^00[1-8]_'})})
 $phases+=@($files | Where-Object {$_.Name -notmatch '^00[1-8]_'} | ForEach-Object {[pscustomobject]@{files=@($_)}})
 foreach($phase in $phases) {
 $pending=[Collections.Generic.List[object]]::new()
 foreach($file in $phase.files) {
  $source=[IO.File]::ReadAllText($file.FullName)
  foreach($batch in [regex]::Split($source,'(?im)^\s*GO\s*\r?$')) {
   if(!$batch.Trim()){continue};$pending.Add(@{name=$file.Name;text=$batch})
  }
 }
 # Inline functions and Views have mutual dependencies in the existing suite.
 # Retry only unresolved-object errors; any actual SQL error fails this fixture.
 while($pending.Count -gt 0) {
  $next=[Collections.Generic.List[object]]::new();$progress=0
  foreach($batch in $pending) {
   try {$command.CommandText=$batch.text;$command.CommandTimeout=30;$command.ExecuteNonQuery() | Out-Null;$progress++}
   catch [System.Data.SqlClient.SqlException] {
    if($_.Exception.Number -in @(208,4121,195,15151)){$batch.lastError=$_.Exception.Message;$next.Add($batch)}
    else {throw ('Migration '+$batch.name+' failed with SQL error '+$_.Exception.Number+': '+$_.Exception.Message)}
   }
  }
  if($progress -eq 0){throw ('Unresolved migration dependencies: '+(($next | ForEach-Object {$_.name+': '+$_.lastError} | Select-Object -Unique)-join ', '))}
  $pending=$next
 }
 }
 foreach($batch in [regex]::Split([IO.File]::ReadAllText((Join-Path $PWD 'docs/evidence/dong/DONG-DB-CONTRACT.sql')),'(?im)^\s*GO\s*\r?$')) {
  if($batch.Trim()){$command.CommandText=$batch;$command.ExecuteNonQuery() | Out-Null}
 }
 $command.CommandText=@'
IF DATABASE_PRINCIPAL_ID('dong_buyer') IS NULL CREATE USER dong_buyer WITHOUT LOGIN;
IF DATABASE_PRINCIPAL_ID('dong_manager') IS NULL CREATE USER dong_manager WITHOUT LOGIN;
IF DATABASE_PRINCIPAL_ID('dong_admin') IS NULL CREATE USER dong_admin WITHOUT LOGIN;
ALTER ROLE tc_buyer ADD MEMBER dong_buyer;
ALTER ROLE tc_manager ADD MEMBER dong_manager;
ALTER ROLE tc_platform_admin ADD MEMBER dong_admin;
'@
 $command.ExecuteNonQuery() | Out-Null
 Write-Output 'Dedicated Dong database, baseline suite and restricted database users created. Existing databases untouched.'
} finally {$connection.Dispose()}
