$ErrorActionPreference='Stop'
$protected=Import-Clixml -LiteralPath docs/handoff/khanh03-local-secrets.xml
$env:TC_REFUND_TEST_URL='jdbc:sqlserver://127.0.0.1:1433;databaseName=TicketCenter_Khanh03_Test;encrypt=true;trustServerCertificate=true;loginTimeout=5'
foreach($role in @('owner','buyer','admin')){
 [Environment]::SetEnvironmentVariable('TC_REFUND_'+$role.ToUpper()+'_USER','khanh03_'+$role,'Process')
 [Environment]::SetEnvironmentVariable('TC_REFUND_'+$role.ToUpper()+'_PASSWORD',([System.Net.NetworkCredential]::new('',$protected[$role])).Password,'Process')
}
$env:TC_REFUND_TESTS='true'
$env:TC_REFUND_REDIS_TESTS='true'
$env:REDIS_HOST='127.0.0.1';$env:REDIS_PORT='16379';$env:REDIS_PASSWORD=''
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4.1';$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
