$ErrorActionPreference='Stop'
. ./src/test/resources/checkin/load-local.ps1
$tomcat=(Resolve-Path -LiteralPath target/khanh01-runtime/apache-tomcat-11.0.25).Path
$env:CATALINA_HOME=$tomcat
$env:CATALINA_BASE=$tomcat
if(Get-NetTCPConnection -LocalPort 18081 -State Listen -ErrorAction SilentlyContinue) {
 throw 'Stop the isolated Tomcat before updating its web application.'
}
# Deploy an exploded WAR: Tomcat packed-WAR URLs can mis-handle this Unicode/OneDrive path.
# Preserve the previous deployment outside webapps, with all moves confined to this test runtime.
$runtimeRoot=[IO.Path]::GetFullPath($tomcat)+[IO.Path]::DirectorySeparatorChar
foreach($old in @('webapps/ticketscenter.war','webapps/ticketscenter')) {
 $source=[IO.Path]::GetFullPath((Join-Path $tomcat $old))
 $backup=[IO.Path]::GetFullPath((Join-Path $tomcat ('temp/previous-'+[guid]::NewGuid().ToString())))
 if(!$source.StartsWith($runtimeRoot,[StringComparison]::OrdinalIgnoreCase) -or !$backup.StartsWith($runtimeRoot,[StringComparison]::OrdinalIgnoreCase)) {
  throw 'Deployment path escaped the isolated runtime'
 }
 if(Test-Path -LiteralPath $source){Move-Item -LiteralPath $source -Destination $backup}
}
$war=(Resolve-Path -LiteralPath target/ticketscenter.war).Path
[IO.Compression.ZipFile]::ExtractToDirectory($war,(Join-Path $tomcat 'webapps/ticketscenter'))
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
