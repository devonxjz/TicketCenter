#Requires -Version 7.4
param(
 [Parameter(Mandatory=$true)][string]$TomcatHome,
 [string]$JavaHome=$env:JAVA_HOME,
 [string]$ConfigPath,
 [int]$Port=18082,
 [switch]$Smoke
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$java=if($JavaHome){[IO.Path]::Combine($JavaHome,'bin/java.exe')}else{''}
if(-not $java -or -not(Test-Path -LiteralPath $java) -or -not(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) {throw 'JDK 25 is required; supply -JavaHome or JAVA_HOME.'}
$version=(& $java -version 2>&1 | Out-String)
if($version -notmatch 'version "25(?:\.|"|-)') {throw 'JDK 25 is required; the selected Java has a different version.'}
if(-not(Test-Path -LiteralPath (Join-Path $TomcatHome 'bin/bootstrap.jar'))) {throw 'Tomcat 11 home is required.'}
$tomcatVersion=(& $java -cp (Join-Path $TomcatHome 'lib/catalina.jar') org.apache.catalina.util.ServerInfo 2>&1 | Out-String)
if($tomcatVersion -notmatch 'Apache Tomcat/11\.') {throw 'Tomcat 11 home is required.'}
if(-not $ConfigPath) {$ConfigPath=Join-Path $repo '.local/database.json'}
if(-not(Test-Path -LiteralPath $ConfigPath)) {throw 'Run tools/local/setup.ps1 first, or supply a dedicated fixture config.'}
if($Port -lt 1024 -or $Port -gt 65535) {throw 'Unprivileged port required.'}
if(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {throw 'Chosen port is already in use.'}
$oldJava=$env:JAVA_HOME;$oldPath=$env:PATH
$removedEnvironment=@{}
try {
 # Safe-default Maven tests only; opt-in external fixtures must not be inherited.
 foreach($item in Get-ChildItem Env:) {
  if($item.Name -match '^(DB_|TC_|APP_|ADMIN_|REDIS_|MAIL_|PAYOS_|CLOUDINARY_|HIKARI_|REFUND_|PAYOUT_|EVENT_)' -or $item.Name -in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_ARGS','MAVEN_OPTS')) {
   $removedEnvironment[$item.Name]=$item.Value;[Environment]::SetEnvironmentVariable($item.Name,$null,'Process')
  }
 }
 $env:JAVA_HOME=$JavaHome;$env:PATH=(Join-Path $JavaHome 'bin')+';'+$oldPath
 Push-Location $repo
 try {
  & mvn -B clean package '-Dtc.tests.db=false' '-Dtc.tests.smtp=false' '-Dtc.tests.redis=false' '-DskipTests=false' '-Dmaven.test.skip=false' '-DskipITs=false'
  if($LASTEXITCODE -ne 0) {throw 'Build or tests failed; local app was not started.'}
 } finally {Pop-Location}
 $jars=@(& (Join-Path $PSScriptRoot 'jstl.ps1') -LibDirectory (Join-Path $repo '.local/lib'))
 & (Join-Path $repo 'src/test/scripts/full-war-smoke.ps1') -ConfigPath $ConfigPath -TomcatHome $TomcatHome -JavaHome $JavaHome -Port $Port -JstlApiJar $jars[0] -JstlImplementationJar $jars[1] -Serve:(-not $Smoke)
} finally {
 foreach($entry in $removedEnvironment.GetEnumerator()) {[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')}
 $env:JAVA_HOME=$oldJava;$env:PATH=$oldPath
}
