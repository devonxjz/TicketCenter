#Requires -Version 7.0
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
  if($item.Name -match '^(DB_|TC_|APP_|ADMIN_|REDIS_|MAIL_|VNPAY_|CLOUDINARY_|HIKARI_|REFUND_|PAYOUT_|EVENT_)' -or $item.Name -in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')) {
   $removedEnvironment[$item.Name]=$item.Value;[Environment]::SetEnvironmentVariable($item.Name,$null,'Process')
  }
 }
 $env:JAVA_HOME=$JavaHome;$env:PATH=(Join-Path $JavaHome 'bin')+';'+$oldPath
 Push-Location $repo
 try {
  & mvn -B clean package
  if($LASTEXITCODE -ne 0) {throw 'Build or tests failed; local app was not started.'}
 } finally {Pop-Location}
 $lib=Join-Path $repo '.local/lib';New-Item -ItemType Directory -Path $lib -Force | Out-Null
 $artifacts=@(
  @{path='jakarta/servlet/jsp/jstl/jakarta.servlet.jsp.jstl-api/3.0.1';name='jakarta.servlet.jsp.jstl-api-3.0.1.jar'},
  @{path='org/glassfish/web/jakarta.servlet.jsp.jstl/3.0.1';name='jakarta.servlet.jsp.jstl-3.0.1.jar'}
 )
 foreach($artifact in $artifacts) {
  $destination=Join-Path $lib $artifact.name
  $url='https://repo.maven.apache.org/maven2/'+$artifact.path+'/'+$artifact.name
  # Validate cached copies too. Maven Central provides the checksum over HTTPS.
  $expected=(Invoke-WebRequest ($url+'.sha512')).Content.Trim().Split(' ')[0]
  if($expected -notmatch '^[a-fA-F0-9]{128}$') {throw 'Invalid JSTL artifact checksum.'}
  if(-not(Test-Path -LiteralPath $destination)) {
   $temporary=Join-Path $lib ([guid]::NewGuid().ToString('N')+'.part')
   try {
    Invoke-WebRequest $url -OutFile $temporary
    if((Get-FileHash -LiteralPath $temporary -Algorithm SHA512).Hash -ine $expected) {throw 'JSTL checksum mismatch.'}
    Move-Item -LiteralPath $temporary -Destination $destination
   } finally {if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
  }
  if((Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash -ine $expected) {throw 'JSTL checksum mismatch.'}
 }
 & (Join-Path $repo 'src/test/scripts/full-war-smoke.ps1') -ConfigPath $ConfigPath -TomcatHome $TomcatHome -JavaHome $JavaHome -Port $Port -JstlApiJar (Join-Path $lib $artifacts[0].name) -JstlImplementationJar (Join-Path $lib $artifacts[1].name) -Serve:(-not $Smoke)
} finally {
 foreach($entry in $removedEnvironment.GetEnumerator()) {[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')}
 $env:JAVA_HOME=$oldJava;$env:PATH=$oldPath
}
