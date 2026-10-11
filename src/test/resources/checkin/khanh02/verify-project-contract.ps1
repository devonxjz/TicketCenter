$ErrorActionPreference='Stop'
# Read-only checks of the configured project contract. Never executes a business scan.
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object {
 if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected project database target'}
$builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source']='tcp:'+$settings['db.host']+',1433';$builder['Initial Catalog']=$settings['db.name']
$builder['User ID']=$settings['db.user'];$builder['Password']=$settings['db.password']
$builder['Encrypt']=$true;$builder['TrustServerCertificate']=$false;$builder['Connect Timeout']=30;$builder['Pooling']=$false
$connection=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
try{
 $connection.Open();$command=$connection.CreateCommand()
 $command.CommandText=@'
 SELECT 'procedure_scan' name,CONVERT(varchar(100),OBJECT_ID('dbo.sp_ticket_check_in','P')) value
 UNION ALL SELECT 'procedure_history',CONVERT(varchar(100),OBJECT_ID('dbo.sp_checkin_recent_logs','P'))
 UNION ALL SELECT 'scan_request_table',CONVERT(varchar(100),OBJECT_ID('dbo.tc_checkin_scan_requests','U'))
 UNION ALL SELECT 'duplicate_success_tickets',CONVERT(varchar(100),COUNT(*)) FROM (SELECT ticket_id FROM dbo.tc_ticket_check_in_logs WHERE result='SUCCESS' AND ticket_id IS NOT NULL GROUP BY ticket_id HAVING COUNT(*)>1) duplicates
 UNION ALL SELECT 'ticket_id_type',TYPE_NAME(system_type_id) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.tc_tickets') AND name='id'
 UNION ALL SELECT 'migration_checksum',checksum_sha256 FROM dbo.tc_schema_migrations WHERE version='030_khanh02_signed_checkin'
 UNION ALL SELECT 'checkin_scan_grant',CONVERT(varchar(100),COUNT(*)) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID('tc_checkin') AND major_id=OBJECT_ID('dbo.sp_ticket_check_in') AND permission_name='EXECUTE' AND state IN('G','W')
 UNION ALL SELECT 'checkin_logs_grant',CONVERT(varchar(100),COUNT(*)) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID('tc_checkin') AND major_id=OBJECT_ID('dbo.sp_checkin_recent_logs') AND permission_name='EXECUTE' AND state IN('G','W');
'@
 $reader=$command.ExecuteReader();$result=@{};while($reader.Read()){$result[$reader.GetString(0)]=if($reader.IsDBNull(1)){$null}else{$reader.GetString(1)}};$reader.Close();$command.Dispose()
 $result | ConvertTo-Json -Compress | Write-Output
 if($result.migration_checksum){
  $expected=(Get-FileHash database/migrations/030_khanh02_signed_checkin.sql -Algorithm SHA256).Hash.ToLowerInvariant()
  if($result.migration_checksum -ne $expected -or !$result.procedure_scan -or !$result.procedure_history -or !$result.scan_request_table -or $result.checkin_scan_grant -ne '1' -or $result.checkin_logs_grant -ne '1' -or $result.duplicate_success_tickets -ne '0' -or $result.ticket_id_type -ne 'uniqueidentifier'){throw 'Project scan contract verification failed'}
 }
}finally{$connection.Dispose()}
