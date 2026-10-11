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
  $command=$connection.CreateCommand()
  $command.CommandText="SELECT TOP(1) e.id,m.user_id FROM dbo.tc_events e JOIN dbo.tc_organization_memberships m ON m.organization_id=e.organization_id JOIN dbo.tc_users u ON u.id=m.user_id WHERE m.active=1 AND m.role IN('MANAGER','CHECK_IN_STAFF') AND u.status='ACTIVE' ORDER BY e.id;"
  $reader=$command.ExecuteReader();if(!$reader.Read()){throw 'No eligible event staff for read-only contract acceptance'};$event=$reader.GetGuid(0);$actor=$reader.GetGuid(1);$reader.Close();$command.Dispose()
  $command=$connection.CreateCommand()
  $command.CommandText=@'
 EXECUTE AS USER='tc_checkin_user';
 BEGIN TRY
  IF HAS_PERMS_BY_NAME('dbo.sp_ticket_check_in','OBJECT','EXECUTE')<>1 OR HAS_PERMS_BY_NAME('dbo.sp_checkin_recent_logs','OBJECT','EXECUTE')<>1 OR HAS_PERMS_BY_NAME('dbo.tc_tickets','OBJECT','UPDATE')<>0 OR HAS_PERMS_BY_NAME('dbo.tc_orders','OBJECT','SELECT','total_amount','COLUMN')<>0 THROW 53290,'Project check-in role verification failed',1;
  EXEC dbo.sp_checkin_recent_logs @event_id=@event,@staff_user_id=@actor,@limit=20;
  REVERT;
 END TRY
 BEGIN CATCH
  REVERT;THROW;
 END CATCH;
'@
  [void]$command.Parameters.AddWithValue('@event',$event);[void]$command.Parameters.AddWithValue('@actor',$actor)
  $reader=$command.ExecuteReader();$rows=0
  $expectedColumns=@('id','ticket_id','result','scanned_at','staff_name','zone_name','seat_label')
  if($reader.FieldCount -ne $expectedColumns.Count){throw 'Unexpected history projection'}
  for($column=0;$column -lt $reader.FieldCount;$column++){if($reader.GetName($column) -ne $expectedColumns[$column]){throw 'Unexpected history field'}}
  while($reader.Read()){$rows++};$reader.Close();$command.Dispose()
  if($rows -gt 20){throw 'History limit exceeded'}
  Write-Output 'Azure read-only history query passed under existing tc_checkin_user; scan/log EXECUTE allowed, ticket UPDATE and financial-column SELECT denied.'
 }
}finally{$connection.Dispose()}
