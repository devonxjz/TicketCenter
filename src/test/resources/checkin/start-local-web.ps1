$ErrorActionPreference='Stop'
. ./src/test/resources/checkin/load-local.ps1
$tomcat=(Resolve-Path -LiteralPath target/khanh01-runtime/apache-tomcat-11.0.25).Path
$env:CATALINA_HOME=$tomcat
$env:CATALINA_BASE=$tomcat
Copy-Item -LiteralPath target/ticketscenter.war -Destination (Join-Path $tomcat 'webapps/ticketscenter.war') -Force
$javaArguments=@(
 ('-Dcatalina.home='+$tomcat),
 ('-Dcatalina.base='+$tomcat),
 ('-Djava.io.tmpdir='+(Join-Path $tomcat 'temp')),
 '-cp',
 ((Join-Path $tomcat 'bin/bootstrap.jar')+';'+(Join-Path $tomcat 'bin/tomcat-juli.jar')),
 'org.apache.catalina.startup.Bootstrap','start'
)
$quoted=$javaArguments | ForEach-Object {'"'+$_+'"'}
$process=Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -WorkingDirectory $tomcat -ArgumentList $quoted -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path (Get-Location) 'target/khanh01-tomcat.log') -RedirectStandardError (Join-Path (Get-Location) 'target/khanh01-tomcat-error.log')
$process.Id | Set-Content target/khanh01-tomcat.pid
Write-Output 'Local app: http://127.0.0.1:18081/ticketscenter/check-ins'
