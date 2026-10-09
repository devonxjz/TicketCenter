$ErrorActionPreference='Stop'
$redis=Get-ChildItem -LiteralPath target/khanh01-runtime/redis -Recurse -Filter redis-server.exe | Select-Object -First 1
if(!$redis){throw 'Download the verified portable Redis runtime first'}
$process=Start-Process -FilePath $redis.FullName -WorkingDirectory $redis.DirectoryName -ArgumentList @('--bind','127.0.0.1','--port','16379','--save','""','--appendonly','no') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path (Get-Location) 'target/khanh01-redis.log') -RedirectStandardError (Join-Path (Get-Location) 'target/khanh01-redis-error.log')
$process.Id | Set-Content target/khanh01-redis.pid
