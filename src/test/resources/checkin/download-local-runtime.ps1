$ErrorActionPreference='Stop'
$root='target/khanh01-runtime'
New-Item -ItemType Directory -Path $root -Force | Out-Null
$redisUrl='https://github.com/redis-windows/redis-windows/releases/download/8.10.2/Redis-8.10.2-Windows-x64-cygwin.zip'
# Development-only Cygwin build; no Windows service or global installation.
Invoke-WebRequest $redisUrl -OutFile "$root/redis.zip"
$expected='6de5cc7f5adbf97b5928b13766383d4ad424626ef3d8b313ffff12d820ec6fc1'
if((Get-FileHash "$root/redis.zip" -Algorithm SHA256).Hash -ne $expected){throw 'Redis checksum mismatch'}
Expand-Archive -LiteralPath "$root/redis.zip" -DestinationPath "$root/redis" -Force
$tomcatUrl='https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.25/bin/apache-tomcat-11.0.25.zip'
Invoke-WebRequest $tomcatUrl -OutFile "$root/tomcat.zip"
$expected=(Invoke-WebRequest ($tomcatUrl+'.sha512')).Content.Trim().Split(' ')[0]
if((Get-FileHash "$root/tomcat.zip" -Algorithm SHA512).Hash -ne $expected){throw 'Tomcat checksum mismatch'}
Expand-Archive -LiteralPath "$root/tomcat.zip" -DestinationPath $root -Force
[xml]$config=Get-Content "$root/apache-tomcat-11.0.25/conf/server.xml"
$config.Server.port='-1'
$config.Server.Service.Connector | ForEach-Object {if($_.protocol -eq 'HTTP/1.1'){$_.port='18081';$_.SetAttribute('address','127.0.0.1')}}
$config.Save((Join-Path (Get-Location) "$root/apache-tomcat-11.0.25/conf/server.xml"))
