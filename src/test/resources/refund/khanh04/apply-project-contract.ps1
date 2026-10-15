$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'migration-checksum.ps1')
# Install only committed runtime compatibility dependencies and the KHANH-04 contract.
# This script never seeds users, orders, tickets or refunds, and never changes credentials.
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object {
 if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected project database target'}
$builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source']='tcp:'+$settings['db.host']+',1433';$builder['Initial Catalog']=$settings['db.name']
$builder['User ID']=$settings['db.user'];$builder['Password']=$settings['db.password']
$builder['Encrypt']=$true;$builder['TrustServerCertificate']=$false;$builder['Connect Timeout']=60;$builder['Pooling']=$false
$files=@('040_payment_ticket_runtime_reads.sql','041_hold_refund_runtime_compatibility.sql','042_checkin_event_user_lock_order.sql','043_khanh04_admin_refund.sql','046_admin_refund_current_authority.sql')
$connection=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
try {
 $connection.Open()
 # Refuse to run against an incomplete or differently edited financial contract.
 foreach($file in Get-ChildItem database/migrations -Filter '03*_sql_report_*.sql'){
  $q=$connection.CreateCommand();$q.CommandText='SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version=@version'
  [void]$q.Parameters.AddWithValue('@version',$file.BaseName);$old=$q.ExecuteScalar();$q.Dispose()
  if($null -eq $old -or $old -is [DBNull] -or $old -notin @(Get-Khanh04MigrationHashes $file.FullName)){throw ('Missing or mismatched prerequisite: '+$file.BaseName)}
 }
 # Private recovery snapshot of definitions; no business rows or secrets.
 if(!(git check-ignore docs/handoff/khanh04-schema-before.json)){throw 'Recovery snapshot must be ignored by Git'}
 $names=[Collections.Generic.List[string]]::new()
 foreach($file in $files){
  $sql=Get-Content -Raw -LiteralPath ('database/migrations/'+$file)
  foreach($match in [regex]::Matches($sql,'(?im)CREATE\s+OR\s+ALTER\s+(?:PROCEDURE|VIEW|FUNCTION)\s+dbo\.([A-Za-z0-9_]+)')){$names.Add($match.Groups[1].Value)}
 }
 foreach($expected in @('v_admin_refund_actor','sp_admin_refund_decide','usp_ReviewRefundRequest')){
  if(!$names.Contains($expected)){throw ('Recovery snapshot did not discover '+$expected)}
 }
 $snapshot=[Collections.Generic.List[object]]::new()
 foreach($name in ($names | Sort-Object -Unique)){
  $q=$connection.CreateCommand();$q.CommandText='SELECT OBJECT_DEFINITION(OBJECT_ID(@name))';[void]$q.Parameters.AddWithValue('@name','dbo.'+$name);$definition=$q.ExecuteScalar();$q.Dispose()
  $snapshot.Add([pscustomobject]@{name=$name;definition=if($definition -is [DBNull]){$null}else{$definition}})
 }
 $snapshot | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath docs/handoff/khanh04-schema-before.json -Encoding utf8
 $transaction=$connection.BeginTransaction()
 try {
  foreach($file in $files){
   $path='database/migrations/'+$file;$version=[IO.Path]::GetFileNameWithoutExtension($file)
   $hashes=@(Get-Khanh04MigrationHashes $path);$checksum=$hashes[1]
   $q=$connection.CreateCommand();$q.Transaction=$transaction
   $q.CommandText='SELECT checksum_sha256 FROM dbo.tc_schema_migrations WITH(UPDLOCK,HOLDLOCK) WHERE version=@version'
   [void]$q.Parameters.AddWithValue('@version',$version);$old=$q.ExecuteScalar();$q.Dispose()
   if($null -ne $old -and $old -isnot [DBNull]){if($old -notin $hashes){throw ('Migration checksum mismatch: '+$version)};continue}
   foreach($batch in [regex]::Split((Get-Content -Raw -LiteralPath $path),'(?im)^\s*GO\s*\r?$')){
    if(!$batch.Trim()){continue}
    $q=$connection.CreateCommand();$q.Transaction=$transaction;$q.CommandTimeout=30;$q.CommandText=$batch;[void]$q.ExecuteNonQuery();$q.Dispose()
   }
   $q=$connection.CreateCommand();$q.Transaction=$transaction;$q.CommandText='INSERT dbo.tc_schema_migrations(version,checksum_sha256) VALUES(@version,@checksum)'
   [void]$q.Parameters.AddWithValue('@version',$version);[void]$q.Parameters.AddWithValue('@checksum',$checksum);[void]$q.ExecuteNonQuery();$q.Dispose()
  }
  $transaction.Commit();Write-Output 'Refund runtime migrations040-043 and authority repair046 installed atomically with SHA-256 history. Business data and credentials unchanged.'
 }catch{$transaction.Rollback();throw}
}finally{$connection.Dispose()}
