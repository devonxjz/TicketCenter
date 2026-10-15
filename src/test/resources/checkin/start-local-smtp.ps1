$ErrorActionPreference='Stop'
. ./src/test/resources/checkin/load-local.ps1
if(Test-Path target/khanh01-smtp.pid) {
 $captureId=[int](Get-Content target/khanh01-smtp.pid)
 $existing=Get-CimInstance Win32_Process -Filter "ProcessId=$captureId"
 if($existing -and $existing.CommandLine.Contains('LocalAcceptanceSmtpServer')) {
  Write-Output 'Local STARTTLS capture is already running.'; return
 }
}
if(Get-NetTCPConnection -LocalPort 18825 -State Listen -ErrorAction SilentlyContinue){throw 'SMTP fixture port is in use by another process'}
& 'C:\Program Files\Apache\maven\bin\mvn.cmd' -B test-compile *> target/khanh01-smtp-compile.log
if($LASTEXITCODE -ne 0){throw 'Compile failed; see target/khanh01-smtp-compile.log'}
$temporaryStore='target/khanh01-smtp-'+[guid]::NewGuid().ToString()+'.p12'
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -alias local-capture -keyalg RSA -keysize 2048 -validity 2 -dname CN=localhost -ext SAN=ip:127.0.0.1,dns:localhost -storetype PKCS12 -keystore $temporaryStore -storepass local-fixture-only -keypass local-fixture-only -noprompt 2>&1 | Out-Null
if($LASTEXITCODE -ne 0){throw 'Local test certificate generation failed'}
Move-Item -LiteralPath $temporaryStore -Destination target/khanh01-smtp.p12 -Force
'0' | Set-Content target/khanh01-smtp-count.txt
$capture=Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @('-cp','target/test-classes','vn.ticketscenter.fulfillment.checkin.integration.LocalAcceptanceSmtpServer','target/khanh01-smtp.p12','target/khanh01-smtp-count.txt') -WindowStyle Hidden -RedirectStandardOutput target/khanh01-smtp.log -RedirectStandardError target/khanh01-smtp-error.log -PassThru
$capture.Id | Set-Content target/khanh01-smtp.pid
for($attempt=0;$attempt -lt 20;$attempt++) {
 if((Get-Content target/khanh01-smtp.log -Raw -ErrorAction SilentlyContinue) -match 'READY'){Write-Output 'Local STARTTLS capture ready';return}
 Start-Sleep -Milliseconds 250
}
throw 'SMTP fixture did not start; see target/khanh01-smtp-error.log'
