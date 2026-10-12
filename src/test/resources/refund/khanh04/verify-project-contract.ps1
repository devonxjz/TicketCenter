$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'migration-checksum.ps1')
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object{if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected project database target'}
$builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source']='tcp:'+$settings['db.host']+',1433';$builder['Initial Catalog']=$settings['db.name'];$builder['User ID']=$settings['db.user'];$builder['Password']=$settings['db.password'];$builder['Encrypt']=$true;$builder['TrustServerCertificate']=$false;$builder['Connect Timeout']=60;$builder['Pooling']=$false
$c=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
try {
 $c.Open();$q=$c.CreateCommand();$q.CommandTimeout=20
 foreach($file in @('040_payment_ticket_runtime_reads.sql','041_hold_refund_runtime_compatibility.sql','042_checkin_event_user_lock_order.sql','043_khanh04_admin_refund.sql')){
  $q.Parameters.Clear();$q.CommandText='SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version=@version';[void]$q.Parameters.AddWithValue('@version',[IO.Path]::GetFileNameWithoutExtension($file))
  if($q.ExecuteScalar() -notin @(Get-Khanh04MigrationHashes ('database/migrations/'+$file))){throw ('Project checksum mismatch: '+$file)}
 }
 $q.Parameters.Clear();$q.CommandText="SELECT TOP(1) p.name FROM sys.database_principals p JOIN sys.database_role_members m ON m.member_principal_id=p.principal_id JOIN sys.database_principals r ON r.principal_id=m.role_principal_id WHERE r.name='tc_platform_admin' AND p.type='S' ORDER BY p.name"
 $admin=$q.ExecuteScalar();if(!$admin){throw 'No existing restricted ADMIN database user'}
 $q.CommandText="EXECUTE AS USER=N'"+$admin.Replace("'","''")+"'";[void]$q.ExecuteNonQuery()
 foreach($view in @('v_admin_refund_actor','v_admin_refund_requests','v_admin_refund_attempts','v_refund_tickets')){$q.CommandText='SELECT COUNT_BIG(*) FROM dbo.'+$view;[void]$q.ExecuteScalar()}
 foreach($procedure in @('sp_refund_approve','sp_refund_reject','sp_refund_retry')){$q.CommandText="SELECT HAS_PERMS_BY_NAME('dbo."+$procedure+"','OBJECT','EXECUTE')";if($q.ExecuteScalar() -ne 1){throw 'Missing admin procedure grant'}}
 $q.CommandText="SELECT HAS_PERMS_BY_NAME('dbo.tc_tickets','OBJECT','UPDATE'),HAS_PERMS_BY_NAME('dbo.tc_audit_logs','OBJECT','INSERT')";$r=$q.ExecuteReader();[void]$r.Read();if($r.GetInt32(0) -ne 0 -or $r.GetInt32(1) -ne 0){throw 'Unexpected direct runtime write grants'};$r.Dispose()
 $q.CommandText='REVERT';[void]$q.ExecuteNonQuery()
 Write-Output 'Azure KHANH-04 checksums, restricted ADMIN projection/procedure reads and direct-write denials verified. Read-only verification; no business mutations.'
}finally{$c.Dispose()}
