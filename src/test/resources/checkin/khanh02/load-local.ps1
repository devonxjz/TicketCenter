$ErrorActionPreference='Stop'
$protected=Import-Clixml -LiteralPath docs/handoff/khanh02-local-secrets.xml
function Read-CheckInSecret($key){([System.Net.NetworkCredential]::new('',$protected[$key])).Password}
$env:TC_SCAN_TEST_URL='jdbc:sqlserver://127.0.0.1:1433;databaseName=TicketCenter_Khanh02_Test;encrypt=true;trustServerCertificate=true;loginTimeout=5'
$env:TC_SCAN_OWNER_USER='khanh02_owner';$env:TC_SCAN_OWNER_PASSWORD=Read-CheckInSecret owner
$env:TC_SCAN_CHECKIN_USER='khanh02_checkin';$env:TC_SCAN_CHECKIN_PASSWORD=Read-CheckInSecret checkin
$env:TC_TICKET_SIGNING_SECRET=Read-CheckInSecret signing
$env:TC_SCAN_FIXTURE_PASSWORD=Read-CheckInSecret fixture
$env:TC_SCAN_INTEGRATION='true'
$env:TC_TESTS_DB='true';$env:TC_TESTS_REDIS='true'
$env:REDIS_HOST='127.0.0.1';$env:REDIS_PORT='16379';$env:REDIS_PASSWORD=''
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.4.1';$env:PATH=$env:JAVA_HOME+'\bin;'+$env:PATH
