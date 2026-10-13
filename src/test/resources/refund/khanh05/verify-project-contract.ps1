$ErrorActionPreference='Stop'
$settings=@{}
$configPath=$env:TC_CONFIG_FILE
if([string]::IsNullOrWhiteSpace($configPath)){$configPath='.local/application.properties'}
Get-Content -LiteralPath $configPath | ForEach-Object {
 if($_ -match '^(db\.[^=]+)=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
if($settings['db.host'] -ne 'ticketscenter.database.windows.net' -or $settings['db.name'] -ne 'TicketsCenter'){throw 'Unexpected project target'}
$b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$b['Data Source']='tcp:'+ $settings['db.host']+',1433';$b['Initial Catalog']=$settings['db.name'];$b['User ID']=$settings['db.user'];$b['Password']=$settings['db.password'];$b['Encrypt']=$true;$b['TrustServerCertificate']=$false;$b['Connect Timeout']=30
$c=[System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
try {
 $c.Open();$q=$c.CreateCommand();$q.CommandTimeout=15
 $q.CommandText="IF (SELECT COUNT_BIG(*) FROM dbo.tc_schema_migrations WHERE version IN('048_khanh05_refund_worker','048_khanh05_refund_worker.sql'))<>1 THROW 53592,'Missing or ambiguous worker migration',1;SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version IN('048_khanh05_refund_worker','048_khanh05_refund_worker.sql')"
 $lf=[IO.File]::ReadAllText((Resolve-Path database/migrations/048_khanh05_refund_worker.sql).Path).Replace("`r`n","`n").Replace("`r","`n")
 $sha=[Security.Cryptography.SHA256]::Create()
 try {$hash=[BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($lf))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
 if($q.ExecuteScalar() -ne $hash){throw 'KHANH-05 migration checksum mismatch'}
 $worker=$settings['db.worker.user']
 if($worker -notmatch '^tc_khanh05_worker_[a-f0-9]{32}$'){throw 'Expected private WORKER configuration'}
 $q.CommandText="EXECUTE AS USER='$worker'";[void]$q.ExecuteNonQuery()
 foreach($sp in @('sp_refund_begin_attempt','sp_refund_record_attempt_result','sp_refund_complete_without_transfer')){
  $q.CommandText="SELECT HAS_PERMS_BY_NAME('dbo.$sp','OBJECT','EXECUTE')";if($q.ExecuteScalar() -ne 1){throw "Missing worker permission: $sp"}
 }
 foreach($table in @('tc_refund_dispatches','tc_refund_obligations','tc_tickets')){
  $q.CommandText="SELECT HAS_PERMS_BY_NAME('dbo.$table','OBJECT','UPDATE')";if($q.ExecuteScalar() -ne 0){throw "Worker has unsafe direct write: $table"}
 }
 foreach($view in @('vw_RefundWorkerCandidates','tc_refund_attempts')){$q.CommandText="SELECT TOP(0) * FROM dbo.$view";[void]$q.ExecuteNonQuery()}
 $q.CommandText='REVERT';[void]$q.ExecuteNonQuery()
 # Check role permission directly; imported Azure schemas may have no legacy buyer impersonation user.
 $q.CommandText="SELECT COUNT_BIG(*) FROM sys.database_permissions p WHERE p.grantee_principal_id=DATABASE_PRINCIPAL_ID('tc_buyer') AND p.major_id=OBJECT_ID('dbo.sp_refund_begin_attempt') AND p.permission_name='EXECUTE' AND p.state IN('G','W')"
 if($q.ExecuteScalar() -ne 0){throw 'Buyer must not dispatch refunds'}
 $b['User ID']=$worker;$b['Password']=$settings['db.worker.password']
 $runtime=[System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
 try{$runtime.Open();$probe=$runtime.CreateCommand();$probe.CommandTimeout=15;$probe.CommandText="SELECT IS_ROLEMEMBER('tc_worker')";if($probe.ExecuteScalar() -ne 1){throw 'Native WORKER role mismatch'};$probe.CommandText='SELECT TOP(0) * FROM dbo.vw_RefundWorkerCandidates';[void]$probe.ExecuteNonQuery()}finally{$runtime.Dispose()}
 Write-Output 'Azure KHANH-05 checksum, native WORKER login/read access, SP grants, direct-write denial and absence of BUYER dispatch grant verified. No business data changed.'
}finally{$c.Dispose()}
