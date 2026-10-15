#Requires -Version 7.4
param([string]$TomcatHome,[string]$JavaHome,[string]$ConfigPath,[int]$Port=0,[switch]$CheckConfig)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if(-not $ConfigPath){$ConfigPath=Join-Path $repo 'src/main/resources/application.properties'}
if(-not(Test-Path -LiteralPath $ConfigPath)){throw 'Copy src/main/resources/application.properties.example to src/main/resources/application.properties and fill the credentials.'}
$settings=@{}
foreach($line in [IO.File]::ReadAllLines($ConfigPath)){
 if($line -match '^([^#!\s][^=]*)=(.*)$'){$settings[$matches[1].Trim()]=$matches[2].Trim()}
}
if($settings['db.host'] -ne 'ticketscenter.database.windows.net' -or $settings['db.name'] -ne 'TicketsCenter'){throw 'Unexpected Azure target'}
foreach($key in @('db.user','db.password')){if([string]::IsNullOrWhiteSpace($settings[$key])){throw 'Azure credentials required'}}
if($CheckConfig){Write-Output 'AZURE_CONFIG_VALID ticketscenter.database.windows.net / TicketsCenter';return}
if(-not $TomcatHome){$TomcatHome=$settings['runtime.tomcat.home']}
if(-not $JavaHome){$JavaHome=$settings['runtime.java.home']}
if(-not $JavaHome){$JavaHome=$env:JAVA_HOME}
if($Port -eq 0){$Port=if($settings['runtime.port']){[int]$settings['runtime.port']}else{18082}}
if($Port -lt 1024 -or $Port -gt 65535){throw 'Unprivileged port required'}
if(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue){throw 'Chosen port is in use'}
if(-not $JavaHome -or -not(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))){throw 'JDK 25 required; supply -JavaHome or JAVA_HOME'}
$java=Join-Path $JavaHome 'bin/java.exe'
if((& $java -version 2>&1|Out-String) -notmatch 'version "25(?:\.|"|-)'){throw 'JDK 25 required'}
if(-not $TomcatHome -or -not(Test-Path -LiteralPath (Join-Path $TomcatHome 'bin/bootstrap.jar'))){throw 'Tomcat 11 required; supply -TomcatHome'}
$tomcat=(Resolve-Path -LiteralPath $TomcatHome).Path
if((& $java -cp (Join-Path $tomcat 'lib/catalina.jar') org.apache.catalina.util.ServerInfo 2>&1|Out-String) -notmatch 'Apache Tomcat/11\.'){throw 'Tomcat 11 required'}
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().User
$identityHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($identity.Value))).Substring(0,8)
$networkTemp=Join-Path ([Environment]::GetFolderPath('CommonApplicationData')) ('TicketsCenter-java-'+$identityHash)
if(-not(Test-Path -LiteralPath $networkTemp)){
 New-Item -ItemType Directory -Path $networkTemp|Out-Null
 $networkAcl=[Security.AccessControl.DirectorySecurity]::new();$networkAcl.SetAccessRuleProtection($true,$false)
 foreach($sid in @($identity,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){$networkAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
 try{Set-Acl -LiteralPath $networkTemp -AclObject $networkAcl -ErrorAction SilentlyContinue}catch{}
}
$networkArg='-DargLine=-Djdk.net.unixdomain.tmpdir='+$networkTemp.Replace('\','/')
$previousJava=$env:JAVA_HOME;$previousPath=$env:PATH;$removed=@{}
try{
 foreach($item in Get-ChildItem Env:){if($item.Name -match '^(DB_|TC_|APP_|ADMIN_|REDIS_|MAIL_|PAYOS_|HIKARI_|REFUND_|PAYOUT_|EVENT_)' -or $item.Name -in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_ARGS','MAVEN_OPTS')){$removed[$item.Name]=$item.Value;[Environment]::SetEnvironmentVariable($item.Name,$null,'Process')}}
 $env:JAVA_HOME=$JavaHome;$env:PATH=(Join-Path $JavaHome 'bin')+';'+$previousPath
 Push-Location $repo
 try{& mvn -B clean package '-Dtc.tests.db=false' '-Dtc.tests.smtp=false' '-Dtc.tests.redis=false' '-DskipTests=false' '-Dmaven.test.skip=false' $networkArg;if($LASTEXITCODE -ne 0){throw 'Build or tests failed'}}finally{Pop-Location}
}finally{foreach($item in $removed.GetEnumerator()){[Environment]::SetEnvironmentVariable($item.Key,$item.Value,'Process')};$env:JAVA_HOME=$previousJava;$env:PATH=$previousPath}
$jars=@(& (Join-Path $repo 'tools/local/jstl.ps1') -LibDirectory (Join-Path $repo '.local/lib'))

$run=Join-Path $repo ('.local/azure-runtime/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run -Force|Out-Null
$acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
try{Set-Acl -LiteralPath $run -AclObject $acl -ErrorAction SilentlyContinue}catch{}
foreach($dir in @('conf','logs','temp','webapps','work','data','lib')){New-Item -ItemType Directory -Path (Join-Path $run $dir)|Out-Null}
foreach($file in @('web.xml','catalina.properties')){Copy-Item -LiteralPath (Join-Path $tomcat ('conf/'+$file)) -Destination (Join-Path $run ('conf/'+$file))}
Copy-Item -LiteralPath $jars[1] -Destination (Join-Path $run 'lib')
Copy-Item -LiteralPath $jars[0] -Destination (Join-Path $run 'lib')
[IO.File]::WriteAllText((Join-Path $run 'conf/server.xml'),('<Server port="-1"><Service name="Catalina"><Connector address="127.0.0.1" port="'+$Port+'" protocol="org.apache.coyote.http11.Http11Nio2Protocol" connectionTimeout="5000"/><Engine name="Catalina" defaultHost="localhost"><Host name="localhost" appBase="webapps" autoDeploy="false" deployOnStartup="true"/></Engine></Service></Server>'))
[IO.File]::WriteAllText((Join-Path $run 'conf/context.xml'),'<Context/>')
[IO.File]::WriteAllText((Join-Path $run 'conf/logging.properties'),"handlers=java.util.logging.ConsoleHandler`n.level=INFO`njava.util.logging.ConsoleHandler.level=INFO`n")
$app=Join-Path $run 'webapps/ticketscenter'
[IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $repo 'target/ticketscenter.war'),$app)
$settings['db.url']='jdbc:sqlserver://ticketscenter.database.windows.net:1433;databaseName=TicketsCenter;encrypt=true;trustServerCertificate=false;loginTimeout=15;'
$settings['db.port']='1433';$settings['db.encrypt']='true';$settings['db.trustServerCertificate']='false';$settings['db.loginTimeout']='15'
foreach($role in @('auth','buyer','manager','check_in','admin','worker')){
 foreach($field in @('user','password')){if([string]::IsNullOrWhiteSpace($settings['db.'+$role+'.'+$field])){$settings['db.'+$role+'.'+$field]=$settings['db.'+$field]}}
}
$settings['app.env']='development';$settings['app.data.mode']='real';$settings['admin.seed.enabled']='false';$settings['demo.seed.enabled']='false';$settings['mail.outbox.enabled']='false'
$settings['app.base.url']="http://127.0.0.1:$Port/ticketscenter"
$settings['payos.return.url']=$settings['app.base.url']+'/api/payments/payos/return'
$settings['payos.cancel.url']=$settings['app.base.url']+'/api/payments/payos/cancel'
if([string]::IsNullOrWhiteSpace($settings['app.secret.key'])){
 $settings['app.secret.key']=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
 if(Test-Path -LiteralPath $ConfigPath){
  [IO.File]::AppendAllText([IO.Path]::GetFullPath($ConfigPath),"`n# Generated once for this machine; use the existing shared key for cross-machine QR verification.`napp.secret.key="+$settings['app.secret.key']+"`n")
 }
}
$settings['hikari.maximum-pool-size']='2';$settings['hikari.minimum-idle']='0';$settings['hikari.pool-budget']='24';$settings['hikari.connection-timeout']='15000'
$settings['finance.redis.enabled']='false';$settings['redis.host']='127.0.0.1';$settings['redis.port']='16379';$settings['redis.timeout']='300'
$settings['payout.adapter']='unavailable'
if([string]::IsNullOrWhiteSpace($settings['event.image.directory'])){$settings['event.image.directory']='.local/event-images'}
if(-not [IO.Path]::IsPathRooted($settings['event.image.directory'])){$settings['event.image.directory']=[IO.Path]::GetFullPath((Join-Path $repo $settings['event.image.directory']))}
$settings['event.image.directory']=$settings['event.image.directory'].Replace('\','/')
# Leave outbound providers unconfigured in this display session.
$lines=foreach($entry in $settings.GetEnumerator()){$entry.Key+'='+([string]$entry.Value).Replace('\','\\').Replace("`r",'\r').Replace("`n",'\n')}
$runtimeConfig=Join-Path $run 'conf/application.properties'
[IO.File]::WriteAllLines($runtimeConfig,$lines,[Text.UTF8Encoding]::new($false))
$info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$java;$info.WorkingDirectory=$run;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
foreach($key in @($info.Environment.Keys)){if($key -match '^(DB_|TC_|APP_|ADMIN_|REDIS_|MAIL_|PAYOS_|HIKARI_|REFUND_|PAYOUT_|EVENT_)' -or $key -in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){$info.Environment.Remove($key)|Out-Null}}
$info.Environment['TC_CONFIG_FILE']=$runtimeConfig
foreach($arg in @("-Dcatalina.home=$tomcat","-Dcatalina.base=$run","-Djava.io.tmpdir=$run/temp","-Djdk.net.unixdomain.tmpdir=$networkTemp",'-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',"-Djava.util.logging.config.file=$run/conf/logging.properties",'-classpath',"$tomcat/bin/bootstrap.jar;$tomcat/bin/tomcat-juli.jar",'org.apache.catalina.startup.Bootstrap','start')){$info.ArgumentList.Add($arg)}
$process=[Diagnostics.Process]::new();$process.StartInfo=$info
try{
 if(-not $process.Start()){throw 'Tomcat launch failed'}
 $outTask=$process.StandardOutput.ReadToEndAsync();$errTask=$process.StandardError.ReadToEndAsync()
 [IO.File]::WriteAllText((Join-Path $repo '.local/azure-runtime.json'),(@{run=$run;pid=$process.Id;origin=$settings['app.base.url']}|ConvertTo-Json))
 $deadline=[DateTime]::UtcNow.AddSeconds(60);$ready=$false
 while([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited){try{$health=Invoke-WebRequest ($settings['app.base.url']+'/health/ready') -TimeoutSec 2 -SkipHttpErrorCheck;$catalog=Invoke-WebRequest ($settings['app.base.url']+'/api/events') -TimeoutSec 2 -SkipHttpErrorCheck;if($health.StatusCode -eq 200 -and $catalog.StatusCode -eq 200){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 300}
 if(-not $ready){throw 'Azure application did not become ready; inspect the private runtime logs'}
 Write-Output ('AZURE_APP_READY '+$settings['app.base.url'])
 Write-Output 'Connected to shared Azure data. No schema install, import or demo seed was run. Keep this terminal open; Ctrl+C stops this instance.'
 while(-not $process.WaitForExit(1000)){}
}finally{
 if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()}
 if($outTask){[IO.File]::WriteAllText((Join-Path $run 'logs/stdout.log'),$outTask.GetAwaiter().GetResult());[IO.File]::WriteAllText((Join-Path $run 'logs/stderr.log'),$errTask.GetAwaiter().GetResult())}
 if(Test-Path -LiteralPath $runtimeConfig){Remove-Item -LiteralPath $runtimeConfig}
 $process.Dispose()
}
