param([switch]$Apply)
$ErrorActionPreference='Stop'
$files=@('031_sql_report_financial_safety','032_sql_report_permissions','033_sql_report_indexes','034_sql_report_read_scope','035_sql_report_runtime','036_sql_report_worker_reads','037_sql_report_layout_entrypoints','038_sql_report_composite_foreign_keys','039_sql_report_contract_consistency')
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object {
 if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected database; refusing mutation'}
$b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$b['Data Source']='tcp:'+$settings['db.host']+',1433';$b['Initial Catalog']=$settings['db.name']
$b['User ID']=$settings['db.user'];$b['Password']=$settings['db.password'];$b['Encrypt']=$true;$b['TrustServerCertificate']=$false;$b['Connect Timeout']=60;$b['Pooling']=$false
$cn=[System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
$tx=$null
function Command($sql){$q=$cn.CreateCommand();$q.CommandText=$sql;$q.CommandTimeout=120;if($tx){$q.Transaction=$tx};return $q}
function Rows($sql){$q=Command $sql;$r=$q.ExecuteReader();$rows=@();try{while($r.Read()){$item=[ordered]@{};for($i=0;$i -lt $r.FieldCount;$i++){$item[$r.GetName($i)]=if($r.IsDBNull($i)){$null}else{$r.GetValue($i)}};$rows += [pscustomobject]$item}}finally{$r.Dispose();$q.Dispose()};return ,$rows}
function Scalar($sql){$q=Command $sql;try{return $q.ExecuteScalar()}finally{$q.Dispose()}}
function Run($sql){$q=Command $sql;try{[void]$q.ExecuteNonQuery()}finally{$q.Dispose()}}
function Digest($value){[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes([string]$value))).ToLowerInvariant()}
function ScriptHashes($path){
 $raw=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $path))
 $lf=$raw.Replace("`r`n","`n")
 return @((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant(),(Digest $lf),(Digest ($lf.Replace("`n","`r`n"))))
}
try{
 for($attempt=0;;$attempt++){
  try{$cn.Open();break}catch{
   $root=$_.Exception;while($root.InnerException){$root=$root.InnerException}
   if($attempt -ge 2 -or $root -isnot [System.Data.SqlClient.SqlException] -or $root.Number -notin @(-2,40613,40197,40501,49918)){throw}
   Start-Sleep -Seconds 10
  }
 }
 if((Scalar 'SELECT DB_NAME()') -ne 'TicketCenterticke'){throw 'Connection target mismatch'}
 $preflight=@{
  duplicate_provider="SELECT COUNT(*) FROM(SELECT provider_reference FROM dbo.tc_payments WHERE provider_reference IS NOT NULL GROUP BY provider_reference HAVING COUNT(*)>1)x"
  duplicate_request="SELECT COUNT(*) FROM(SELECT refund_request_id FROM dbo.tc_refunds WHERE refund_request_id IS NOT NULL AND status IN('PENDING','UNKNOWN','SUCCEEDED') GROUP BY refund_request_id HAVING COUNT(*)>1)x"
  duplicate_compensation="SELECT COUNT(*) FROM(SELECT payment_id FROM dbo.tc_refunds WHERE purpose='PAYMENT_COMPENSATION' AND status IN('PENDING','UNKNOWN','SUCCEEDED') GROUP BY payment_id HAVING COUNT(*)>1)x"
  duplicate_live_seat="SELECT COUNT(*) FROM(SELECT i.seat_id FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id WHERE i.seat_id IS NOT NULL AND t.status IN('ACTIVE','USED','REFUND_PENDING') GROUP BY i.seat_id HAVING COUNT(*)>1)x"
  cross_event_items="SELECT COUNT(*) FROM dbo.tc_order_items i JOIN dbo.tc_orders o ON o.id=i.order_id JOIN dbo.tc_zones z ON z.id=i.zone_id LEFT JOIN dbo.tc_seats s ON s.id=i.seat_id WHERE o.event_id<>z.event_id OR (i.seat_id IS NOT NULL AND s.zone_id<>i.zone_id)"
  invalid_org_status="SELECT COUNT(*) FROM dbo.tc_organizations WHERE status NOT IN('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED')"
  duplicate_legacy_success="SELECT COUNT(*) FROM(SELECT ticket_id FROM dbo.tc_check_ins WHERE result='SUCCESS' AND ticket_id IS NOT NULL GROUP BY ticket_id HAVING COUNT(*)>1)x"
 }
 $checks=[ordered]@{}
 foreach($key in $preflight.Keys){$checks[$key]=[int](Scalar $preflight[$key]);if($checks[$key] -ne 0){throw ('Data conflict: '+$key+'; refusing to delete or guess business data')}}
 $checks|ConvertTo-Json -Compress
 if(!$Apply){Write-Output 'Read-only preflight passed; no database changes.';return}
 [xml]$proof=Get-Content -Raw target/sql-report-proof.xml
 if([int]$proof.testsuite.tests -lt 16 -or [int]$proof.testsuite.failures -ne 0 -or [int]$proof.testsuite.errors -ne 0 -or [int]$proof.testsuite.skipped -ne 0){throw 'Real SQL regression proof missing or incomplete'}
 $reference=Get-Content -Raw target/sql-report-current.json|ConvertFrom-Json
 $backup=[ordered]@{}
 $backup.modules=Rows 'SELECT o.name,o.type,m.definition FROM sys.objects o JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.is_ms_shipped=0'
 $backup.permissions=Rows 'SELECT p.*,dp.name principal FROM sys.database_permissions p JOIN sys.database_principals dp ON dp.principal_id=p.grantee_principal_id'
 $backup.otp_fields=Rows 'SELECT id,invalidated_at FROM dbo.tc_otps'
 $backup.ticket_fields=Rows 'SELECT id FROM dbo.tc_tickets'
 $backup.migrations=Rows 'SELECT version,checksum_sha256 FROM dbo.tc_schema_migrations'
 $backup.constraints=Rows 'SELECT name,definition,OBJECT_NAME(parent_object_id) table_name FROM sys.check_constraints'
 $backup.count_method='COUNT_BIG'
 $backup.counts=@(foreach($table in (Rows 'SELECT name FROM sys.tables WHERE is_ms_shipped=0 ORDER BY name')){
  $name=$table.name.Replace(']',']]')
  [pscustomobject]@{table_name=$table.name;row_count=(Scalar "SELECT COUNT_BIG(*) FROM dbo.[$name]")}
 })
 $folder='docs/handoff/database-backups/sql-report-'+(Get-Date -Format 'yyyyMMdd-HHmmss')
 [void](New-Item -ItemType Directory -Path $folder)
 $backup|ConvertTo-Json -Depth 12|Set-Content (Join-Path $folder 'before.json') -Encoding UTF8
 Copy-Item -LiteralPath target/sql-report-current.json -Destination (Join-Path $folder 'catalog.json')
 foreach($file in $files){Copy-Item -LiteralPath ('database/migrations/'+$file+'.sql') -Destination $folder}
 $tx=$cn.BeginTransaction()
 Run "DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource='sql-report-20261011',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000; IF @r<0 THROW 51890,'Migration lock unavailable',1;"
 $installed=@();$changedNames=@()
 foreach($file in $files){
  $path='database/migrations/'+$file+'.sql';$hashes=ScriptHashes $path;$hash=$hashes[1]
  $q=Command 'SELECT checksum_sha256 FROM dbo.tc_schema_migrations WITH(UPDLOCK,HOLDLOCK) WHERE version=@v'
  [void]$q.Parameters.AddWithValue('@v',$file);$prior=$q.ExecuteScalar();$q.Dispose()
  if($null -ne $prior -and $prior -isnot [DBNull]){if($hashes -notcontains $prior){throw 'Installed migration checksum changed'};continue}
  $script=Get-Content -Raw -LiteralPath $path
  $names=[regex]::Matches($script,'(?i)CREATE (?:OR ALTER )?(?:PROCEDURE|FUNCTION|TRIGGER) dbo\.(\w+)')|ForEach-Object {$_.Groups[1].Value}|Select-Object -Unique
  foreach($name in $names){
   $q=Command 'SELECT OBJECT_DEFINITION(OBJECT_ID(@name))';[void]$q.Parameters.AddWithValue('@name','dbo.'+$name);$current=$q.ExecuteScalar();$q.Dispose()
   $expected=@($reference.modules|Where-Object name -eq $name)
   # A later migration may intentionally refine an object created earlier in this same atomic run.
   if($changedNames -notcontains $name){
    if($expected.Count -eq 0 -and $current -isnot [DBNull] -and $null -ne $current){throw ('Unrecorded object exists: '+$name)}
    if($expected.Count -eq 1 -and (Digest $current) -ne (Digest $expected[0].definition)){throw ('Object changed since review: '+$name)}
   }
  }
  foreach($batch in [regex]::Split($script,'(?im)^\s*GO\s*\r?$')){if($batch.Trim()){Run $batch}}
  $changedNames += $names
  $q=Command 'INSERT dbo.tc_schema_migrations(version,checksum_sha256) VALUES(@v,@h)'
  [void]$q.Parameters.AddWithValue('@v',$file);[void]$q.Parameters.AddWithValue('@h',$hash);[void]$q.ExecuteNonQuery();$q.Dispose()
  $installed += $file
 }
 $tx.Commit();$tx.Dispose();$tx=$null
 [ordered]@{database='TicketCenterticke';installed=$installed;backup=$folder;installed_at=(Get-Date).ToUniversalTime().ToString('o')}|ConvertTo-Json -Depth 4|Set-Content target/sql-report-installation.json -Encoding UTF8
 Write-Output ('Committed '+$installed.Count+' migrations atomically. Backup: '+$folder)
}catch{if($tx){$tx.Rollback();$tx.Dispose();$tx=$null};throw}finally{$cn.Dispose()}
