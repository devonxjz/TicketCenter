# Read-only: uses an existing published Event, never inserts shared test data.
$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4.1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object {
 if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
$builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source']='tcp:'+$settings['db.host']+',1433'; $builder['Initial Catalog']=$settings['db.name']
$builder['User ID']=$settings['db.user']; $builder['Password']=$settings['db.password']
$builder['Encrypt']=$true; $builder['TrustServerCertificate']=$false; $builder['Connect Timeout']=5
$connection=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
try {
 $connection.Open(); $command=$connection.CreateCommand(); $command.CommandTimeout=5
 $command.CommandText="SELECT TOP (1) e.id FROM dbo.tc_events e JOIN dbo.tc_organization_memberships m ON m.organization_id=e.organization_id JOIN dbo.tc_users u ON u.id=m.user_id WHERE e.status='PUBLISHED' AND e.start_time<e.end_time AND m.active=1 AND m.role IN ('MANAGER','CHECK_IN_STAFF') AND u.status='ACTIVE' ORDER BY e.id"
 $event=$command.ExecuteScalar()
 if(!$event -or $event -is [DBNull]){throw 'No existing published Event is available for boundary verification.'}
 $env:DB_HOST=$settings['db.host']; $env:DB_PORT='1433'; $env:DB_NAME=$settings['db.name']
 $env:DB_USER=$settings['db.user']; $env:DB_PASSWORD=$settings['db.password']
 $env:DB_URL='jdbc:sqlserver://'+$env:DB_HOST+':1433;databaseName='+$env:DB_NAME+';encrypt=true;trustServerCertificate=false;loginTimeout=15'
 $env:TC_SQL_TEST_DB=$env:DB_NAME; $env:TC_SQL_TEST_LOGIN=$env:DB_USER; $env:TC_SQL_TEST_PASSWORD=$env:DB_PASSWORD
 $env:TC_CHECKIN_TEST_EVENT_ID=$event.ToString(); $env:DB_TRUSTSERVERCERTIFICATE='false'
 $command.CommandText=@'
EXECUTE AS USER = 'tc_checkin_user';
SELECT HAS_PERMS_BY_NAME('dbo.fn_event_is_check_in_open','OBJECT','EXECUTE') AS udf_execute,
 HAS_PERMS_BY_NAME('dbo.tc_events','OBJECT','SELECT','start_time','COLUMN') AS event_read,
 HAS_PERMS_BY_NAME('dbo.tc_orders','OBJECT','SELECT','total_amount','COLUMN') AS financial_read;
REVERT;
'@
 $reader=$command.ExecuteReader()
 if($reader.Read()){Write-Output ('CHECK_IN grants: UDF='+$reader.GetValue(0)+'; event_time='+$reader.GetValue(1)+'; financial_read='+$reader.GetValue(2))}
 $reader.Dispose(); $command.Dispose()
} finally { $connection.Dispose() }
& 'C:\Program Files\Apache\maven\bin\mvn.cmd' -B -Psqlserver-it '-Dtest=CheckInEventServiceTest,RedisCheckInEventCacheTest,CheckInServletsTest,CheckInEventRepositoryTest' '-Dit.test=CheckInEventSqlServerIT,CheckInEventAzureReadOnlyIT' verify *> target/khanh01-azure-integration.log
$result=$LASTEXITCODE
Get-Content target/khanh01-azure-integration.log -Tail 18
exit $result
