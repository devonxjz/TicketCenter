$ErrorActionPreference='Stop'
. ./src/test/resources/checkin/khanh02/load-local.ps1
$env:DB_HOST='127.0.0.1';$env:DB_PORT='1433';$env:DB_NAME='TicketCenter_Khanh02_Test';$env:DB_URL=$env:TC_SCAN_TEST_URL
$env:DB_USER=$env:TC_SCAN_OWNER_USER;$env:DB_PASSWORD=$env:TC_SCAN_OWNER_PASSWORD
$env:DB_TRUSTSERVERCERTIFICATE='true';$env:ADMIN_SEED_ENABLED='false'
foreach($role in @('BUYER','MANAGER','CHECK_IN','ADMIN','AUTH','WORKER')){
 $user=$env:TC_SCAN_OWNER_USER;$password=$env:TC_SCAN_OWNER_PASSWORD
 if($role -eq 'CHECK_IN'){$user=$env:TC_SCAN_CHECKIN_USER;$password=$env:TC_SCAN_CHECKIN_PASSWORD}
 if($role -eq 'BUYER'){$user=$env:TC_SCAN_OWNER_USER;$password=$env:TC_SCAN_OWNER_PASSWORD}
 if($role -eq 'ADMIN'){$user=$env:TC_SCAN_OWNER_USER;$password=$env:TC_SCAN_OWNER_PASSWORD}
 [Environment]::SetEnvironmentVariable('DB_'+$role+'_USER',$user,'Process');[Environment]::SetEnvironmentVariable('DB_'+$role+'_PASSWORD',$password,'Process')
}
foreach($setting in @(@('HIKARI_MAXIMUM-POOL-SIZE','3'),@('HIKARI_MINIMUM-IDLE','0'),@('HIKARI_POOL-BUDGET','24'),@('HIKARI_CONNECTION-TIMEOUT','5000'))){[Environment]::SetEnvironmentVariable($setting[0],$setting[1],'Process')}
$env:MAIL_SMTP_HOST='127.0.0.1';$env:MAIL_SMTP_PORT='18825'
$tomcat=(Resolve-Path -LiteralPath target/khanh01-runtime/apache-tomcat-11.0.25).Path
if(Get-NetTCPConnection -LocalPort 18081 -State Listen -ErrorAction SilentlyContinue){throw 'Isolated web port is already in use'}
$runtimeRoot=[IO.Path]::GetFullPath($tomcat)+[IO.Path]::DirectorySeparatorChar
# Reproducible WAR entries have fixed timestamps; archive this app's generated JSP classes too.
foreach($old in @('webapps/ticketscenter.war','webapps/ticketscenter','work/Catalina/localhost/ticketscenter')){
 $source=[IO.Path]::GetFullPath((Join-Path $tomcat $old));$backup=[IO.Path]::GetFullPath((Join-Path $tomcat ('temp/previous-'+[guid]::NewGuid().ToString())))
 if(!$source.StartsWith($runtimeRoot,[StringComparison]::OrdinalIgnoreCase) -or !$backup.StartsWith($runtimeRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Deployment path escaped isolated runtime'}
 if(Test-Path -LiteralPath $source){Move-Item -LiteralPath $source -Destination $backup}
}
[IO.Compression.ZipFile]::ExtractToDirectory((Resolve-Path target/ticketscenter.war).Path,(Join-Path $tomcat 'webapps/ticketscenter'))
$javaArguments=@(('-Dcatalina.home='+$tomcat),('-Dcatalina.base='+$tomcat),('-Djava.io.tmpdir='+(Join-Path $tomcat 'temp')),'-cp',((Join-Path $tomcat 'bin/bootstrap.jar')+';'+(Join-Path $tomcat 'bin/tomcat-juli.jar')),'org.apache.catalina.startup.Bootstrap','start')
$quoted=$javaArguments | ForEach-Object {'"'+$_+'"'}
$process=Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -WorkingDirectory $tomcat -ArgumentList $quoted -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path (Get-Location) 'target/khanh02-tomcat.log') -RedirectStandardError (Join-Path (Get-Location) 'target/khanh02-tomcat-error.log')
$process.Id | Set-Content target/khanh02-tomcat.pid
Write-Output 'Isolated QR check-in app started on http://127.0.0.1:18081/ticketscenter/check-ins'
