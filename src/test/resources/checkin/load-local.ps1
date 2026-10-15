$ErrorActionPreference='Stop'
$protected=Import-Clixml -LiteralPath docs/handoff/khanh01-local-secrets.xml
function Read-LocalSecret($key){([System.Net.NetworkCredential]::new('', $protected[$key])).Password}
$env:DB_HOST='127.0.0.1'
$env:DB_PORT='1433'
$env:DB_NAME='TicketCenter_Khanh01_Test'
$env:DB_URL='jdbc:sqlserver://127.0.0.1:1433;databaseName=TicketCenter_Khanh01_Test;encrypt=true;trustServerCertificate=true;loginTimeout=5'
$env:DB_USER='khanh01_owner'
$env:DB_PASSWORD=Read-LocalSecret 'Owner'
$env:DB_CHECK_IN_USER='khanh01_checkin'
$env:DB_CHECK_IN_PASSWORD=Read-LocalSecret 'CheckIn'
$env:DB_TRUSTSERVERCERTIFICATE='true'
$env:TC_SQL_TEST_DB=$env:DB_NAME
$env:TC_SQL_TEST_LOGIN=$env:DB_CHECK_IN_USER
$env:TC_SQL_TEST_PASSWORD=$env:DB_CHECK_IN_PASSWORD
$env:TC_CHECKIN_FIXTURE_PASSWORD=Read-LocalSecret 'Fixture'
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='16379'
$env:REDIS_PASSWORD=''
$env:HIKARI_MAXIMUM_POOL_SIZE='4'
$env:HIKARI_MINIMUM_IDLE='0'
$env:HIKARI_POOL_BUDGET='24'
$env:HIKARI_CONNECTION_TIMEOUT='5000'
foreach($setting in @(@('HIKARI_MAXIMUM-POOL-SIZE','4'),@('HIKARI_MINIMUM-IDLE','0'),@('HIKARI_POOL-BUDGET','24'),@('HIKARI_CONNECTION-TIMEOUT','5000'))){
 [Environment]::SetEnvironmentVariable($setting[0],$setting[1],'Process')
}
$env:ADMIN_SEED_ENABLED='false'
if(Test-Path -LiteralPath docs/handoff/khanh01-fixture.properties){
 Get-Content docs/handoff/khanh01-fixture.properties | ForEach-Object {
  if($_ -match '^(TC_CHECKIN_[A-Z_]+)=(.+)$'){[Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')}
 }
}
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4.1'
$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
