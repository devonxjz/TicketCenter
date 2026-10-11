param(
 [Parameter(Mandatory=$true)][string]$ConfigPath,
 [Parameter(Mandatory=$true)][string]$TomcatHome,
 [Parameter(Mandatory=$true)][string]$JstlApiJar,
 [Parameter(Mandatory=$true)][string]$JstlImplementationJar,
 [string]$JavaHome=$env:JAVA_HOME,
 [string]$NodePath='node',
 [int]$Port=0,
 [switch]$Serve
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$privateRoot=Join-Path $repo 'target/full-war-smoke'
$tomcat=(Resolve-Path -LiteralPath $TomcatHome).Path
$java=Join-Path $JavaHome 'bin/java.exe'
$fixture=Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
if ($fixture.database -notmatch '^TC_Vuong_Test_[A-Za-z0-9_]+$' -or $fixture.url -notmatch '^jdbc:sqlserver://127\.0\.0\.1:1433;') { throw 'Unsafe fixture JDBC configuration.' }
$properties=@{}
foreach($part in ($fixture.url -split ';' | Select-Object -Skip 1)) {
 if($part -eq '') {continue}
 $pair=$part -split '=',2
 if($pair.Length -ne 2 -or $pair[0] -notin @('databaseName','database','encrypt','trustServerCertificate','loginTimeout')) {throw 'Unsafe fixture JDBC configuration.'}
 $key=if($pair[0] -eq 'database'){'databaseName'}else{$pair[0]}
 if($properties.ContainsKey($key)) {throw 'Unsafe fixture JDBC configuration.'}
 $properties[$key]=$pair[1]
}
if($properties['databaseName'] -cne $fixture.database -or ($properties.ContainsKey('encrypt') -and $properties['encrypt'] -ne 'false') -or ($properties.ContainsKey('trustServerCertificate') -and $properties['trustServerCertificate'] -ne 'true') -or ($properties.ContainsKey('loginTimeout') -and $properties['loginTimeout'] -notmatch '^(?:[1-9]|10)$')) {throw 'Unsafe fixture JDBC configuration.'}
# Never forward an externally supplied JDBC URL to listeners/seeders/workers.
$jdbcUrl="jdbc:sqlserver://127.0.0.1:1433;databaseName=$($fixture.database);encrypt=false;trustServerCertificate=true;loginTimeout=5;"
# Verify a database marker before full listeners, seeders or workers may write.
$connection=[System.Data.SqlClient.SqlConnection]::new("Server=tcp:127.0.0.1,1433;Database=$($fixture.database);Integrated Security=True;TrustServerCertificate=True;Connection Timeout=5")
try {
 $connection.Open();$command=$connection.CreateCommand()
 $command.CommandText="SELECT COUNT(*) FROM dbo.tc_vuong_test_fixture WHERE fixture_key IN ('vuong-team-schema-v1','vuong-finance-v1')"
 if([int]$command.ExecuteScalar() -lt 1) { throw 'Dedicated fixture marker required.' }
} finally { $connection.Dispose() }
foreach($required in @($java,(Join-Path $repo 'target/ticketscenter.war'),$JstlApiJar,$JstlImplementationJar)) { if(-not(Test-Path -LiteralPath $required -PathType Leaf)) {throw 'Required runtime file is missing.'} }
if($Port -eq 0) { $reservation=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$reservation.Start();$Port=$reservation.LocalEndpoint.Port;$reservation.Stop() }
if($Port -lt 1024 -or $Port -gt 65535) {throw 'Unprivileged port required.'}
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw 'Chosen port is already in use.' }
$run=Join-Path $privateRoot ([guid]::NewGuid().ToString('N'))
foreach($dir in @('conf','logs','temp','webapps','work','data','lib')) { New-Item -ItemType Directory -Path (Join-Path $run $dir) -Force | Out-Null }
$runAcl=[Security.AccessControl.DirectorySecurity]::new();$runAcl.SetAccessRuleProtection($true,$false)
foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
 $runAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
}
Set-Acl -LiteralPath $run -AclObject $runAcl
[IO.File]::WriteAllText((Join-Path $run 'conf/server.xml'), "<Server port=`"-1`"><Service name=`"Catalina`"><Connector address=`"127.0.0.1`" port=`"$Port`" protocol=`"org.apache.coyote.http11.Http11Nio2Protocol`" connectionTimeout=`"5000`"/><Engine name=`"Catalina`" defaultHost=`"localhost`"><Host name=`"localhost`" appBase=`"webapps`" autoDeploy=`"false`" deployOnStartup=`"true`"/></Engine></Service></Server>")
[IO.File]::WriteAllText((Join-Path $run 'conf/context.xml'), '<Context/>')
[IO.File]::WriteAllText((Join-Path $run 'conf/logging.properties'), "handlers=java.util.logging.ConsoleHandler`n.level=INFO`njava.util.logging.ConsoleHandler.level=INFO`n")
Copy-Item -LiteralPath (Join-Path $tomcat 'conf/web.xml') -Destination (Join-Path $run 'conf/web.xml')
Copy-Item -LiteralPath (Join-Path $tomcat 'conf/catalina.properties') -Destination (Join-Path $run 'conf/catalina.properties')
# Explicit local runtime prerequisites; the locked application POM is unchanged.
Copy-Item -LiteralPath $JstlApiJar -Destination (Join-Path $run 'lib')
Copy-Item -LiteralPath $JstlImplementationJar -Destination (Join-Path $run 'lib')
$app=Join-Path $run 'webapps/ticketscenter'
[IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $repo 'target/ticketscenter.war'),$app)
if(Test-Path -LiteralPath (Join-Path $app 'WEB-INF/web.xml')) { throw 'Unexpected deployment descriptor: inspect annotation scanning before proceeding.' }
$adminPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$config=[ordered]@{
 'db.host'='127.0.0.1';'db.port'='1433';'db.name'=$fixture.database;'db.url'=$jdbcUrl;'db.user'=$fixture.user;'db.password'=$fixture.password;
 'db.encrypt'='false';'db.trustServerCertificate'='true';'db.loginTimeout'='5';'app.env'='test';'app.base.url'="http://127.0.0.1:$Port/ticketscenter";
 'app.secret.key'=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32));'admin.seed.enabled'='true';'admin.email'='vuong.acceptance@test.invalid';'admin.password'=$adminPassword;
 'hikari.maximum-pool-size'='2';'hikari.minimum-idle'='0';'hikari.pool-budget'='24';'hikari.connection-timeout'='5000';
 'redis.host'='127.0.0.1';'redis.port'='16379';'redis.timeout'='300';'redis.password'='';'finance.redis.enabled'='false';'payout.adapter'='unavailable';
 'mail.smtp.host'='127.0.0.1';'mail.smtp.port'='1025';'mail.smtp.user'='local-test';'mail.smtp.password'='local-test';
 'vnpay.pay.url'='http://127.0.0.1:19090/disabled';'vnpay.api.url'='http://127.0.0.1:19090/disabled';'vnpay.return.url'="http://127.0.0.1:$Port/ticketscenter/api/payments/vnpay/return";'vnpay.tmn.code'='LOCAL_TEST';'vnpay.hash.secret'='local-test-only';
 'CLOUDINARY_CLOUD_NAME'='';'CLOUDINARY_API_KEY'='';'CLOUDINARY_API_SECRET'='';
 'event.image.directory'=($run.Replace('\','/')+'/data/images');'refund.simulated.state.directory'=($run.Replace('\','/')+'/data/refunds');'refund.simulated.outcome'='SUCCEEDED';'refund.simulated.timeoutAfterSideEffect'='false';
 'payout.simulated.state.directory'=($run.Replace('\','/')+'/data/payouts');'payout.simulated.outcome'='SUCCEEDED';'payout.simulated.timeoutAfterSideEffect'='false'
}
foreach($principal in @('buyer','manager','check_in','admin','auth','worker')) { $config['db.'+$principal+'.user']=$fixture.user; $config['db.'+$principal+'.password']=$fixture.password }
# Java Properties escaping preserves private credentials without putting them in process arguments.
$lines=foreach($entry in $config.GetEnumerator()) { $entry.Key+'='+([string]$entry.Value).Replace('\','\\').Replace("`r",'\r').Replace("`n",'\n') }
foreach($name in @('application.properties','application.properties.example')) { [IO.File]::WriteAllLines((Join-Path $app ('WEB-INF/classes/'+$name)), $lines, [Text.UTF8Encoding]::new($false)) }
$psi=[Diagnostics.ProcessStartInfo]::new()
$psi.FileName=$java;$psi.WorkingDirectory=$run;$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
# Remove inherited app overrides, including Java launcher injection, before loading private configuration.
foreach($key in @($psi.Environment.Keys)) { if($key -match '^(DB_|TC_|APP_|ADMIN_|REDIS_|MAIL_|VNPAY_|CLOUDINARY_|HIKARI_|REFUND_|PAYOUT_|EVENT_|db\.|app\.|admin\.|redis\.|mail\.|vnpay\.|hikari\.|refund\.|payout\.|event\.)' -or $key -in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')) { $psi.Environment.Remove($key) | Out-Null } }
foreach($arg in @("-Dcatalina.home=$tomcat","-Dcatalina.base=$run","-Djava.io.tmpdir=$run/temp",'-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',"-Djava.util.logging.config.file=$run/conf/logging.properties",'-classpath',"$tomcat/bin/bootstrap.jar;$tomcat/bin/tomcat-juli.jar",'org.apache.catalina.startup.Bootstrap','start')) { $psi.ArgumentList.Add($arg) }
$proc=[Diagnostics.Process]::new();$proc.StartInfo=$psi
$started=$false
try {
 $started=$proc.Start();if(-not $started) { throw 'Tomcat failed to launch.' }
 $outTask=$proc.StandardOutput.ReadToEndAsync();$errTask=$proc.StandardError.ReadToEndAsync()
 $origin="http://127.0.0.1:$Port/ticketscenter";$deadline=[DateTime]::UtcNow.AddSeconds(50);$live=$false
 while([DateTime]::UtcNow -lt $deadline -and -not $proc.HasExited) { try { $r=Invoke-WebRequest "$origin/health/live" -TimeoutSec 2 -SkipHttpErrorCheck; if($r.StatusCode -eq 200) { $live=$true;break } } catch{}; Start-Sleep -Milliseconds 300 }
 if(-not $live) { throw 'Full WAR did not start: inspect this run logs.' }
 $authFile=Join-Path $run 'auth.json'
 [IO.File]::WriteAllText($authFile,(@{origin=$origin;adminEmail='vuong.acceptance@test.invalid';adminPassword=$adminPassword}|ConvertTo-Json))
 $previous=$env:TC_FULL_WAR_AUTH_FILE
 try { $env:TC_FULL_WAR_AUTH_FILE=$authFile; & $NodePath --test (Join-Path $repo 'src/test/js/full-war-auth-smoke.test.cjs'); if($LASTEXITCODE -ne 0){throw 'Full WAR authentication/finance acceptance failed.'} }
 finally { $env:TC_FULL_WAR_AUTH_FILE=$previous; Remove-Item -LiteralPath $authFile }
 Write-Output ('FULL_WAR_ACCEPTANCE_PASS '+$origin)
 if($Serve) {
  $accounts=Join-Path $run 'accounts.json'
  [IO.File]::WriteAllText($accounts,(@{origin=$origin;adminEmail='vuong.acceptance@test.invalid';adminPassword=$adminPassword}|ConvertTo-Json))
  Write-Output ('LOCAL_APP_READY '+$origin)
  Write-Output ('LOCAL_ACCOUNTS '+$accounts)
  Write-Output 'Keep this terminal open. Press Ctrl+C to stop this dedicated JVM. Business E2E and external providers are not enabled by this local fixture.'
  while(-not $proc.WaitForExit(1000)) {}
  throw 'Local Tomcat exited; inspect the run logs.'
 }
} finally {
 if($started -and -not $proc.HasExited) { $proc.Kill($true);$proc.WaitForExit(15000)|Out-Null }
 if($started) { [IO.File]::WriteAllText((Join-Path $run 'logs/stdout.log'),$outTask.GetAwaiter().GetResult());[IO.File]::WriteAllText((Join-Path $run 'logs/stderr.log'),$errTask.GetAwaiter().GetResult()) }
 # Remove the credential-bearing copies after stopping the dedicated JVM. Keep logs and generated JSPs.
 foreach($name in @('application.properties','application.properties.example')) { $path=Join-Path $app ('WEB-INF/classes/'+$name); if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path} }
 if($Serve -and (Test-Path -LiteralPath (Join-Path $run 'accounts.json'))) {Remove-Item -LiteralPath (Join-Path $run 'accounts.json')}
 Write-Output ('FULL_WAR_LOGS '+(Join-Path $run 'logs'))
 $proc.Dispose()
}
