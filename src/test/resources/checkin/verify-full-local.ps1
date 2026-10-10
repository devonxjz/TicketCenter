# All existing Maven tests, real local SQL Server and loopback SMTP; no external mail delivery.
$ErrorActionPreference='Stop'
. ./src/test/resources/checkin/load-local.ps1
. ./src/test/resources/checkin/start-local-smtp.ps1
$env:MAIL_SMTP_HOST='127.0.0.1'; $env:MAIL_SMTP_PORT='18825'
$env:MAIL_SMTP_USER='capture@fixture.invalid'; $env:MAIL_SMTP_PASSWORD='local-fixture-only'
$env:ADMIN_SEED_ENABLED='true'; $env:ADMIN_EMAIL='adminTIcket@gmail.com'
$env:ADMIN_PASSWORD='123123ticketCenter'
foreach($name in @('HIKARI_MAXIMUM-POOL-SIZE','HIKARI_MINIMUM-IDLE','HIKARI_POOL-BUDGET','HIKARI_CONNECTION-TIMEOUT')) {
 [Environment]::SetEnvironmentVariable($name,$null,'Process')
}
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStore=target/khanh01-smtp.p12 -Djavax.net.ssl.trustStorePassword=local-fixture-only'
$before=if(Test-Path target/khanh01-smtp-count.txt){[int](Get-Content target/khanh01-smtp-count.txt)}else{0}
# Surefire forks need these explicitly, independent of Maven's own VM.
& 'C:\Program Files\Apache\maven\bin\mvn.cmd' -B '-DargLine=-Djavax.net.ssl.trustStore=target/khanh01-smtp.p12 -Djavax.net.ssl.trustStorePassword=local-fixture-only' test *> target/khanh01-full-regression.log
$result=$LASTEXITCODE
Get-Content target/khanh01-full-regression.log -Tail 22
if($result -eq 0) {
 if(!(Test-Path target/khanh01-smtp-count.txt) -or ([int](Get-Content target/khanh01-smtp-count.txt)-$before) -lt 2) {
  throw 'Both real SMTP regression messages must be captured locally.'
 }
 Write-Output ('SMTP messages captured in this run: '+([int](Get-Content target/khanh01-smtp-count.txt)-$before))
}
exit $result
