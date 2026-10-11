param(
    [int]$TcpPort = 63657,
    [Parameter(Mandatory = $true)][string]$RedisDirectory,
    [Parameter(Mandatory = $true)][string]$MigrationPath,
    [Parameter(Mandatory = $true)][string]$HoldMigrationPath,
    [string]$TestSelector = ''
)
$ErrorActionPreference = 'Stop'
$orderRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$orderDatabase = 'TicketCenter_LIEM02_Test'
$orderMigration = (Resolve-Path -LiteralPath $MigrationPath).Path
$orderMigrationText = (Get-Content -LiteralPath $orderMigration -Raw).Replace("`r`n", "`n").Replace("`r", "`n")
if ($orderMigrationText -match '(?im)^\s*(USE\s|:CONNECT\s|:r\s|!!)') { throw 'Order dependency must stay in the guarded database without script includes or shell commands' }
$orderChecksum = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($orderMigrationText))).ToLowerInvariant()
$orderReceiptVersion = 'LIEM02_LOCAL_PROPOSAL_' + $orderChecksum
$orderServer = Join-Path $RedisDirectory 'redis-server.exe'
$orderCli = Join-Path $RedisDirectory 'redis-cli.exe'
if (!(Test-Path -LiteralPath $orderServer) -or !(Test-Path -LiteralPath $orderCli)) { throw 'Explicit Redis runtime directory required' }
if ($TcpPort -lt 1 -or $TcpPort -gt 65535) { throw 'Invalid SQL loopback TCP port' }
$orderGuard = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $orderDatabase -h -1 -W -Q "SET NOCOUNT ON; IF DB_NAME()<>'$orderDatabase' THROW 51000,'Isolated target required',1; SELECT DB_NAME();" 2>&1
if ($LASTEXITCODE -ne 0 -or ($orderGuard -join '').Trim() -ne $orderDatabase) { throw 'Guarded isolated SQL database required' }
$orderListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$orderListener.Start()
$orderRedisPort = $orderListener.LocalEndpoint.Port
$orderListener.Stop()
$orderRun = Join-Path $orderRoot ('.codex/liem-02/completion/verification-' + [DateTimeOffset]::Now.ToString('yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $orderRun -Force | Out-Null
$orderChecksum | Set-Content -LiteralPath (Join-Path $orderRun 'dependency-checksum.sha256')
@"
bind 127.0.0.1
port $orderRedisPort
protected-mode yes
save ""
appendonly no
maxmemory 32mb
maxmemory-policy noeviction
loglevel warning
"@ | Set-Content -LiteralPath (Join-Path $orderRun 'redis.conf') -Encoding ascii
$orderPrincipals = @()
$orderRedis = $null
$orderResult = 1
$orderAllowedTests = @('OrderRequestsTest','OrderSnapshotTest','CouponPolicyTest','OrderSqlIT','OrderHttpIT','OrderRuntimeIT')
if ($TestSelector -eq '') { $TestSelector = $orderAllowedTests -join ',' }
$orderSelections = $TestSelector -split ','
foreach ($orderSelection in $orderSelections) {
    $orderClass = ($orderSelection -split '#')[0]
    if ($orderClass -notin $orderAllowedTests -or $orderSelection -notmatch '^[A-Za-z][A-Za-z0-9]*(#[A-Za-z][A-Za-z0-9]*(\+[A-Za-z][A-Za-z0-9]*)*)?$') {
        throw 'Only guarded owned Order test classes and named methods may run with extension autodetection disabled'
    }
}
$orderTests = @($orderSelections | ForEach-Object { ($_ -split '#')[0] } | Sort-Object -Unique)
try {
    $orderHoldMigration = (Resolve-Path -LiteralPath $HoldMigrationPath).Path
    $orderHoldText = (Get-Content -LiteralPath $orderHoldMigration -Raw).Replace("`r`n", "`n").Replace("`r", "`n")
    $orderHoldChecksum = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($orderHoldText))).ToLowerInvariant()
    $orderHoldChecksum | Set-Content -LiteralPath (Join-Path $orderRun 'hold-dependency-checksum.sha256')
    if ($orderHoldText -match '(?im)^\s*(USE\s|:CONNECT\s|:r\s|!!)') { throw 'Hold dependency must stay in the guarded database' }
    & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $orderDatabase -i $orderHoldMigration 2>&1 | Set-Content (Join-Path $orderRun 'hold-migration.log')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Hold dependency installation failed' }
    & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $orderDatabase -i $orderMigration 2>&1 | Set-Content (Join-Path $orderRun 'migration.log')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Order dependency installation failed' }
    $orderReceipt = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $orderDatabase -Q "SET NOCOUNT ON; IF DB_NAME()<>'$orderDatabase' THROW 51000,'Isolated target required',1; IF NOT EXISTS(SELECT 1 FROM dbo.tc_schema_migrations WHERE version='$orderReceiptVersion') INSERT dbo.tc_schema_migrations(version,checksum_sha256,applied_at,applied_by) VALUES('$orderReceiptVersion','$orderChecksum',SYSUTCDATETIME(),ORIGINAL_LOGIN()); IF NOT EXISTS(SELECT 1 FROM dbo.tc_schema_migrations WHERE version='$orderReceiptVersion' AND checksum_sha256='$orderChecksum') THROW 51000,'Dependency checksum receipt mismatch',1;" 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Order dependency checksum receipt failed' }
    foreach ($orderRole in @('OWNER','BUYER','WORKER')) {
        $orderLogin = 'liem02_' + $orderRole.ToLowerInvariant() + '_' + [Guid]::NewGuid().ToString('N')
        $orderPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)) + 'aA!7'
        $orderPrincipals += $orderLogin
        $orderDatabaseRole = switch ($orderRole) { 'OWNER' { 'db_owner' } 'BUYER' { 'tc_buyer' } 'WORKER' { 'tc_worker' } }
        $orderCreate = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -Q "CREATE LOGIN [$orderLogin] WITH PASSWORD=N'$orderPassword',CHECK_POLICY=ON; USE [$orderDatabase]; CREATE USER [$orderLogin] FOR LOGIN [$orderLogin]; ALTER ROLE [$orderDatabaseRole] ADD MEMBER [$orderLogin];" 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'Temporary verification principal setup failed' }
        if ($orderRole -eq 'OWNER') {
            $orderObserve = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -Q "GRANT VIEW SERVER STATE TO [$orderLogin]; IF CONVERT(int,SERVERPROPERTY('ProductMajorVersion'))>=16 EXEC(N'GRANT VIEW SERVER PERFORMANCE STATE TO [$orderLogin]');" 2>&1
            if ($LASTEXITCODE -ne 0) { throw 'Temporary fixture observer permission setup failed' }
        }
        [Environment]::SetEnvironmentVariable("LIEM02_TEST_${orderRole}_USER", $orderLogin, 'Process')
        [Environment]::SetEnvironmentVariable("LIEM02_TEST_${orderRole}_PASSWORD", $orderPassword, 'Process')
    }
    $env:LIEM02_TEST_JDBC = "jdbc:sqlserver://127.0.0.1:$TcpPort;databaseName=$orderDatabase;encrypt=true;trustServerCertificate=true;loginTimeout=10;"
    $orderRedis = Start-Process -FilePath $orderServer -ArgumentList 'redis.conf' -WorkingDirectory $orderRun -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $orderRun 'redis.stdout.log') -RedirectStandardError (Join-Path $orderRun 'redis.stderr.log')
    $orderReady = $false
    for ($orderAttempt = 0; $orderAttempt -lt 30; $orderAttempt++) {
        if ($orderRedis.HasExited) { throw 'Dedicated Redis process exited during startup' }
        $orderPong = & $orderCli -h 127.0.0.1 -p $orderRedisPort PING 2>$null
        if ($LASTEXITCODE -eq 0 -and $orderPong -eq 'PONG') { $orderReady = $true; break }
        Start-Sleep -Milliseconds 100
    }
    if (!$orderReady) { throw 'Dedicated Redis fixture failed to start' }
    Push-Location -LiteralPath $orderRoot
    try {
        git rev-parse HEAD | Set-Content (Join-Path $orderRun 'revision.txt')
        foreach ($orderTest in $orderTests) {
            if (Test-Path -LiteralPath 'target/surefire-reports') {
                Get-ChildItem -LiteralPath 'target/surefire-reports' -Filter "TEST-*.$orderTest.xml" | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
            }
        }
        & mvn -B "-Dtest=$TestSelector" '-Djunit.jupiter.extensions.autodetection.enabled=false' '-Dliem.order.redisTestHost=127.0.0.1' "-Dliem.order.redisTestPort=$orderRedisPort" test 2>&1 | Tee-Object -FilePath (Join-Path $orderRun 'tests.log')
        if ($LASTEXITCODE -ne 0) { throw 'Order verification tests failed' }
        foreach ($orderTest in $orderTests) {
            $orderReport = @(Get-ChildItem -LiteralPath 'target/surefire-reports' -Filter "TEST-*.$orderTest.xml")
            if ($orderReport.Count -ne 1) { throw "Expected one report for $orderTest" }
            [xml]$orderXml = Get-Content -LiteralPath $orderReport[0].FullName
            if ([int]$orderXml.testsuite.tests -le 0 -or [int]$orderXml.testsuite.failures -ne 0 -or [int]$orderXml.testsuite.errors -ne 0 -or [int]$orderXml.testsuite.skipped -ne 0) { throw "Missing passing zero-skip evidence for $orderTest" }
        }
        & mvn -B '-DskipTests' package 2>&1 | Tee-Object -FilePath (Join-Path $orderRun 'package.log')
        if ($LASTEXITCODE -ne 0) { throw 'Order WAR packaging failed' }
        git diff --check
        if ($LASTEXITCODE -ne 0) { throw 'Order whitespace check failed' }
        $orderResult = 0
    } finally { Pop-Location }
} finally {
    if ($null -ne $orderRedis -and !$orderRedis.HasExited) {
        & $orderCli -h 127.0.0.1 -p $orderRedisPort SHUTDOWN NOSAVE 2>$null | Out-Null
        if (!$orderRedis.WaitForExit(5000)) { Stop-Process -Id $orderRedis.Id }
    }
    foreach ($orderRole in @('OWNER','BUYER','WORKER')) {
        [Environment]::SetEnvironmentVariable("LIEM02_TEST_${orderRole}_USER", $null, 'Process')
        [Environment]::SetEnvironmentVariable("LIEM02_TEST_${orderRole}_PASSWORD", $null, 'Process')
    }
    Remove-Item Env:LIEM02_TEST_JDBC -ErrorAction SilentlyContinue
    foreach ($orderLogin in $orderPrincipals) {
        $orderDrop = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -Q "USE [$orderDatabase]; IF USER_ID(N'$orderLogin') IS NOT NULL DROP USER [$orderLogin]; USE master; IF SUSER_ID(N'$orderLogin') IS NOT NULL DROP LOGIN [$orderLogin];" 2>&1
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Temporary Order test principal cleanup failed' }
    }
    $orderPassword = $null
    Write-Output $orderRun
}
exit $orderResult
