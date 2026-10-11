param(
    [int]$TcpPort = 63657,
    [Parameter(Mandatory = $true)][string]$RedisDirectory,
    [Parameter(Mandatory = $true)][string]$MigrationPath,
    [string]$TestSelector = ''
)
$ErrorActionPreference = 'Stop'
$holdRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$holdDatabase = 'TicketCenter_LIEM01_Test'
$holdMigration = (Resolve-Path -LiteralPath $MigrationPath).Path
$holdMigrationText = (Get-Content -LiteralPath $holdMigration -Raw).Replace("`r`n", "`n").Replace("`r", "`n")
if ($holdMigrationText -match '(?im)^\s*(USE\s|:CONNECT\s|:r\s|!!)') { throw 'Hold dependency must stay in the guarded database without script includes or shell commands' }
$holdChecksum = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($holdMigrationText))).ToLowerInvariant()
$holdReceiptVersion = 'LIEM01_LOCAL_PROPOSAL_' + $holdChecksum
$holdServer = Join-Path $RedisDirectory 'redis-server.exe'
$holdCli = Join-Path $RedisDirectory 'redis-cli.exe'
if (!(Test-Path -LiteralPath $holdServer) -or !(Test-Path -LiteralPath $holdCli)) { throw 'Explicit Redis runtime directory required' }
if ($TcpPort -lt 1 -or $TcpPort -gt 65535) { throw 'Invalid SQL loopback TCP port' }
$holdGuard = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $holdDatabase -h -1 -W -Q "SET NOCOUNT ON; IF DB_NAME()<>'$holdDatabase' THROW 51000,'Isolated target required',1; SELECT DB_NAME();" 2>&1
if ($LASTEXITCODE -ne 0 -or ($holdGuard -join '').Trim() -ne $holdDatabase) { throw 'Guarded isolated SQL database required' }
$holdListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$holdListener.Start()
$holdRedisPort = $holdListener.LocalEndpoint.Port
$holdListener.Stop()
$holdRun = Join-Path $holdRoot ('.codex/liem-01/completion/verification-' + [DateTimeOffset]::Now.ToString('yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $holdRun -Force | Out-Null
$holdChecksum | Set-Content -LiteralPath (Join-Path $holdRun 'dependency-checksum.sha256')
@"
bind 127.0.0.1
port $holdRedisPort
protected-mode yes
save ""
appendonly no
maxmemory 32mb
maxmemory-policy noeviction
loglevel warning
"@ | Set-Content -LiteralPath (Join-Path $holdRun 'redis.conf') -Encoding ascii
$holdPrincipals = @()
$holdRedis = $null
$holdResult = 1
$holdAllowedTests = @('ActiveHoldProjectionTest','CreateHoldRequestTest','HoldMutationFingerprintTest',
    'HoldRequestParserTest','HoldExpiryPolicyTest','RedisHoldIdempotencyTest','RedisHoldIdempotencyIT',
    'RedisHoldReconciliationIT','RedisActiveHoldCacheIT','HoldSqlIT','HoldHttpIT','HoldRuntimeIT')
if ($TestSelector -eq '') { $TestSelector = $holdAllowedTests -join ',' }
$holdSelections = $TestSelector -split ','
foreach ($holdSelection in $holdSelections) {
    $holdClass = ($holdSelection -split '#')[0]
    if ($holdClass -notin $holdAllowedTests -or $holdSelection -notmatch '^[A-Za-z][A-Za-z0-9]*(#[A-Za-z][A-Za-z0-9]*(\+[A-Za-z][A-Za-z0-9]*)*)?$') {
        throw 'Only guarded owned Hold test classes and named methods may run with extension autodetection disabled'
    }
}
$holdTests = @($holdSelections | ForEach-Object { ($_ -split '#')[0] } | Sort-Object -Unique)
try {
    & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $holdDatabase -i $holdMigration 2>&1 | Set-Content (Join-Path $holdRun 'migration.log')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Hold dependency installation failed' }
    $holdReceipt = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $holdDatabase -Q "SET NOCOUNT ON; IF DB_NAME()<>'$holdDatabase' THROW 51000,'Isolated target required',1; IF NOT EXISTS(SELECT 1 FROM dbo.tc_schema_migrations WHERE version='$holdReceiptVersion') INSERT dbo.tc_schema_migrations(version,checksum_sha256,applied_at,applied_by) VALUES('$holdReceiptVersion','$holdChecksum',SYSUTCDATETIME(),ORIGINAL_LOGIN()); IF NOT EXISTS(SELECT 1 FROM dbo.tc_schema_migrations WHERE version='$holdReceiptVersion' AND checksum_sha256='$holdChecksum') THROW 51000,'Dependency checksum receipt mismatch',1;" 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Hold dependency checksum receipt failed' }
    foreach ($holdRole in @('OWNER','BUYER','WORKER')) {
        $holdLogin = 'liem01_' + $holdRole.ToLowerInvariant() + '_' + [Guid]::NewGuid().ToString('N')
        $holdPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)) + 'aA!7'
        $holdPrincipals += $holdLogin
        $holdDatabaseRole = switch ($holdRole) { 'OWNER' { 'db_owner' } 'BUYER' { 'tc_buyer' } 'WORKER' { 'tc_worker' } }
        $holdCreate = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -Q "CREATE LOGIN [$holdLogin] WITH PASSWORD=N'$holdPassword',CHECK_POLICY=ON; USE [$holdDatabase]; CREATE USER [$holdLogin] FOR LOGIN [$holdLogin]; ALTER ROLE [$holdDatabaseRole] ADD MEMBER [$holdLogin];" 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'Temporary verification principal setup failed' }
        if ($holdRole -eq 'OWNER') {
            $holdObserve = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -Q "GRANT VIEW SERVER STATE TO [$holdLogin]; IF CONVERT(int,SERVERPROPERTY('ProductMajorVersion'))>=16 EXEC(N'GRANT VIEW SERVER PERFORMANCE STATE TO [$holdLogin]');" 2>&1
            if ($LASTEXITCODE -ne 0) { throw 'Temporary fixture observer permission setup failed' }
        }
        [Environment]::SetEnvironmentVariable("LIEM01_TEST_${holdRole}_USER", $holdLogin, 'Process')
        [Environment]::SetEnvironmentVariable("LIEM01_TEST_${holdRole}_PASSWORD", $holdPassword, 'Process')
    }
    $env:LIEM01_TEST_JDBC = "jdbc:sqlserver://127.0.0.1:$TcpPort;databaseName=$holdDatabase;encrypt=true;trustServerCertificate=true;loginTimeout=10;"
    $holdRedis = Start-Process -FilePath $holdServer -ArgumentList 'redis.conf' -WorkingDirectory $holdRun -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $holdRun 'redis.stdout.log') -RedirectStandardError (Join-Path $holdRun 'redis.stderr.log')
    $holdReady = $false
    for ($holdAttempt = 0; $holdAttempt -lt 30; $holdAttempt++) {
        if ($holdRedis.HasExited) { throw 'Dedicated Redis process exited during startup' }
        $holdPong = & $holdCli -h 127.0.0.1 -p $holdRedisPort PING 2>$null
        if ($LASTEXITCODE -eq 0 -and $holdPong -eq 'PONG') { $holdReady = $true; break }
        Start-Sleep -Milliseconds 100
    }
    if (!$holdReady) { throw 'Dedicated Redis fixture failed to start' }
    Push-Location -LiteralPath $holdRoot
    try {
        git rev-parse HEAD | Set-Content (Join-Path $holdRun 'revision.txt')
        foreach ($holdTest in $holdTests) {
            if (Test-Path -LiteralPath 'target/surefire-reports') {
                Get-ChildItem -LiteralPath 'target/surefire-reports' -Filter "TEST-*.$holdTest.xml" | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
            }
        }
        & mvn -B "-Dtest=$TestSelector" '-Djunit.jupiter.extensions.autodetection.enabled=false' '-Dliem.hold.redisTestHost=127.0.0.1' "-Dliem.hold.redisTestPort=$holdRedisPort" test 2>&1 | Tee-Object -FilePath (Join-Path $holdRun 'tests.log')
        if ($LASTEXITCODE -ne 0) { throw 'Hold verification tests failed' }
        foreach ($holdTest in $holdTests) {
            $holdReport = @(Get-ChildItem -LiteralPath 'target/surefire-reports' -Filter "TEST-*.$holdTest.xml")
            if ($holdReport.Count -ne 1) { throw "Expected one report for $holdTest" }
            [xml]$holdXml = Get-Content -LiteralPath $holdReport[0].FullName
            if ([int]$holdXml.testsuite.tests -le 0 -or [int]$holdXml.testsuite.failures -ne 0 -or [int]$holdXml.testsuite.errors -ne 0 -or [int]$holdXml.testsuite.skipped -ne 0) { throw "Missing passing zero-skip evidence for $holdTest" }
        }
        & mvn -B '-DskipTests' package 2>&1 | Tee-Object -FilePath (Join-Path $holdRun 'package.log')
        if ($LASTEXITCODE -ne 0) { throw 'Hold WAR packaging failed' }
        git diff --check
        if ($LASTEXITCODE -ne 0) { throw 'Hold whitespace check failed' }
        $holdResult = 0
    } finally { Pop-Location }
} finally {
    if ($null -ne $holdRedis -and !$holdRedis.HasExited) {
        & $holdCli -h 127.0.0.1 -p $holdRedisPort SHUTDOWN NOSAVE 2>$null | Out-Null
        if (!$holdRedis.WaitForExit(5000)) { Stop-Process -Id $holdRedis.Id }
    }
    foreach ($holdRole in @('OWNER','BUYER','WORKER')) {
        [Environment]::SetEnvironmentVariable("LIEM01_TEST_${holdRole}_USER", $null, 'Process')
        [Environment]::SetEnvironmentVariable("LIEM01_TEST_${holdRole}_PASSWORD", $null, 'Process')
    }
    Remove-Item Env:LIEM01_TEST_JDBC -ErrorAction SilentlyContinue
    foreach ($holdLogin in $holdPrincipals) {
        $holdDrop = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -Q "USE [$holdDatabase]; IF USER_ID(N'$holdLogin') IS NOT NULL DROP USER [$holdLogin]; USE master; IF SUSER_ID(N'$holdLogin') IS NOT NULL DROP LOGIN [$holdLogin];" 2>&1
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Temporary Hold test principal cleanup failed' }
    }
    $holdPassword = $null
    Write-Output $holdRun
}
exit $holdResult
