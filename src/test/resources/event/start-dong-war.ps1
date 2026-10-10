param(
 [string]$TomcatDirectory = 'target/dong-runtime/apache-tomcat-11.0.25',
 [string]$JavaDirectory = 'C:\Users\Lenovo\.jdks\openjdk-25.0.2',
 [string]$NativeDirectory = (Join-Path ([IO.Path]::GetTempPath()) 'ticketcenter-dong-native-12-8-1')
)
$ErrorActionPreference='Stop'
$runtime=[IO.Path]::GetFullPath((Join-Path $PWD 'target/dong-runtime'))
$tomcat=[IO.Path]::GetFullPath((Join-Path $PWD $TomcatDirectory))
if(!$tomcat.StartsWith($runtime+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Use a dedicated Tomcat inside target/dong-runtime.'}
foreach($path in @("$tomcat/bin/bootstrap.jar","$tomcat/bin/tomcat-juli.jar", "$JavaDirectory/bin/java.exe", "$NativeDirectory/mssql-jdbc_auth-12.8.1.x64.dll",'target/ticketscenter.war')) {
 if(!(Test-Path -LiteralPath $path)){throw "Missing runtime prerequisite: $path"}
}
if(Test-Path -LiteralPath "$runtime/tomcat.pid") {
 $running=Get-Process -Id ([int][IO.File]::ReadAllText("$runtime/tomcat.pid")) -ErrorAction SilentlyContinue
 if($running){throw 'The dedicated Tomcat is already running. Stop only that verified process before redeploying.'}
}
$server=[xml][IO.File]::ReadAllText("$tomcat/conf/server.xml")
$server.Server.SetAttribute('port','-1')
foreach($connector in @($server.Server.Service.Connector)) {
 if($connector.protocol -like '*HTTP*' -or $connector.protocol -like '*Http*') {
  $connector.SetAttribute('address','127.0.0.1');$connector.SetAttribute('port','18082')
 } else {$connector.ParentNode.RemoveChild($connector) | Out-Null}
}
$server.Save("$tomcat/conf/server.xml")
Copy-Item -LiteralPath 'target/ticketscenter.war' -Destination "$tomcat/webapps/ticketscenter.war"
$env:DB_URL='jdbc:sqlserver://127.0.0.1:11433;databaseName=TicketCenter_Dong_Epic_Test_20261011;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5'
$env:REDIS_HOST='127.0.0.1';$env:REDIS_PORT='16479'
$env:HIKARI_MAXIMUM_POOL_SIZE='4';$env:HIKARI_MINIMUM_IDLE='0';$env:HIKARI_POOL_BUDGET='24'
$env:ADMIN_SEED_ENABLED='false';$env:MAIL_SMTP_HOST='127.0.0.1';$env:MAIL_SMTP_PORT='1'
$env:APP_BASE_URL='http://127.0.0.1:18082/ticketscenter'
$env:REFUND_SIMULATED_STATE_DIRECTORY="$runtime/refund-state"
$arguments=@('--enable-native-access=ALL-UNNAMED', ('"-Djava.library.path='+$NativeDirectory+'"'),
 '-Dcatalina.home=.', '-Dcatalina.base=.',
 '-Djava.util.logging.config.file=conf/logging.properties',
 '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager', '-cp',
 '"bin/bootstrap.jar;bin/tomcat-juli.jar"', 'org.apache.catalina.startup.Bootstrap','start')
# Relative Java paths avoid the Windows launcher losing Unicode workspace characters.
$process=Start-Process -FilePath "$JavaDirectory/bin/java.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/tomcat.log" -RedirectStandardError "$runtime/tomcat-error.log" -WorkingDirectory $tomcat
[IO.File]::WriteAllText("$runtime/tomcat.pid",[string]$process.Id)
Write-Output 'Dedicated WAR starting at http://127.0.0.1:18082/ticketscenter. No shared server configuration changed.'
