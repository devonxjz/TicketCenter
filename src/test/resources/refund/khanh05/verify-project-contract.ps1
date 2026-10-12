$ErrorActionPreference='Stop'
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object {
 if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected project target'}
$b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$b['Data Source']='tcp:'+ $settings['db.host']+',1433';$b['Initial Catalog']=$settings['db.name'];$b['User ID']=$settings['db.user'];$b['Password']=$settings['db.password'];$b['Encrypt']=$true;$b['TrustServerCertificate']=$false;$b['Connect Timeout']=30
$c=[System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
try {
 $c.Open();$q=$c.CreateCommand();$q.CommandTimeout=15
 $q.CommandText="SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version='045_khanh05_refund_worker'"
 $lf=[IO.File]::ReadAllText((Resolve-Path database/migrations/045_khanh05_refund_worker.sql).Path).Replace("`r`n","`n").Replace("`r","`n")
 $sha=[Security.Cryptography.SHA256]::Create()
 try {$hash=[BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($lf))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
 if($q.ExecuteScalar() -ne $hash){throw 'KHANH-05 migration checksum mismatch'}
 $q.CommandText="EXECUTE AS USER='tc_worker_user'";[void]$q.ExecuteNonQuery()
 foreach($sp in @('sp_refund_begin_attempt','sp_refund_record_attempt_result','sp_refund_complete_without_transfer')){
  $q.CommandText="SELECT HAS_PERMS_BY_NAME('dbo.$sp','OBJECT','EXECUTE')";if($q.ExecuteScalar() -ne 1){throw "Missing worker permission: $sp"}
 }
 foreach($table in @('tc_refund_dispatches','tc_refunds','tc_tickets')){
  $q.CommandText="SELECT HAS_PERMS_BY_NAME('dbo.$table','OBJECT','UPDATE')";if($q.ExecuteScalar() -ne 0){throw "Worker has unsafe direct write: $table"}
 }
 foreach($view in @('vw_RefundWorkerCandidates','tc_refund_attempts')){$q.CommandText="SELECT TOP(0) * FROM dbo.$view";[void]$q.ExecuteNonQuery()}
 $q.CommandText='REVERT';[void]$q.ExecuteNonQuery()
 $q.CommandText="EXECUTE AS USER='tc_buyer_user';SELECT HAS_PERMS_BY_NAME('dbo.sp_refund_begin_attempt','OBJECT','EXECUTE')"
 if($q.ExecuteScalar() -ne 0){throw 'Buyer must not dispatch refunds'}
 $q.CommandText='REVERT';[void]$q.ExecuteNonQuery()
 Write-Output 'Azure KHANH-05 checksum, WORKER reads/SP grants, direct-write denial and BUYER dispatch denial verified read-only through impersonation.'
}finally{$c.Dispose()}
