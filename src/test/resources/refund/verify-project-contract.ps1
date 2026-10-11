$ErrorActionPreference='Stop'
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object{if($_ -match '^(db\.[^=]+)=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected project target'}
$b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$b['Data Source']='tcp:'+$settings['db.host']+',1433';$b['Initial Catalog']=$settings['db.name'];$b['User ID']=$settings['db.user'];$b['Password']=$settings['db.password'];$b['Encrypt']=$true;$b['TrustServerCertificate']=$false;$b['Connect Timeout']=30
$c=[System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
try {
 $c.Open();$q=$c.CreateCommand();$q.CommandTimeout=15
 $q.CommandText="SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version='029_khanh03_refund_request'"
 if($q.ExecuteScalar() -ne (Get-FileHash database/migrations/029_khanh03_refund_request.sql -Algorithm SHA256).Hash.ToLowerInvariant()){throw 'Project migration checksum mismatch'}
 $q.CommandText="EXECUTE AS USER='tc_buyer_user'";[void]$q.ExecuteNonQuery()
 $q.CommandText="SELECT HAS_PERMS_BY_NAME('dbo.sp_refund_request_for_tickets','OBJECT','EXECUTE'),HAS_PERMS_BY_NAME('dbo.tc_tickets','OBJECT','UPDATE')"
 $r=$q.ExecuteReader();[void]$r.Read();if($r.GetInt32(0) -ne 1 -or $r.GetInt32(1) -ne 0){throw 'Unexpected buyer permissions'};$r.Dispose()
 foreach($view in @('v_refunds','v_refund_tickets','v_refundable_orders','v_refundable_ticket_options','v_refund_attempts')){$q.CommandText='SELECT COUNT_BIG(*) FROM dbo.'+$view;[void]$q.ExecuteScalar()}
 $q.CommandText="SELECT TOP(1) id,status,auth_version FROM dbo.tc_users WHERE status='ACTIVE'";$r=$q.ExecuteReader();if(!$r.Read()){throw 'No active user projection'};$r.Dispose()
 $q.CommandText='REVERT';[void]$q.ExecuteNonQuery()
 Write-Output 'Azure BUYER user verified through database impersonation: projections readable, request executable, direct ticket updates denied. Read-only verification; no runtime password changes.'
}finally{$c.Dispose()}
