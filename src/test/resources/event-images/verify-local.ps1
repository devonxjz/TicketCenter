param([int]$TcpPort = 63657, [switch]$Serve)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$taskDatabase = 'TicketCenter_LIEM43_Test'
$taskLogin = 'liem43_verify_' + [Guid]::NewGuid().ToString('N')
$taskPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)) + 'aA!7'
$taskSql = "CREATE LOGIN [$taskLogin] WITH PASSWORD=N'$taskPassword', CHECK_POLICY=ON; USE [$taskDatabase]; CREATE USER [$taskLogin] FOR LOGIN [$taskLogin]; ALTER ROLE db_owner ADD MEMBER [$taskLogin];"
$taskResult = 1
try {
    & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $taskDatabase -i (Join-Path $taskRoot 'database/migrations/028_event_image_workflow.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated migration028 failed' }
    $taskCreate = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -Q $taskSql 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Isolated temporary login creation failed' }
    $env:LIEM43_TEST_JDBC = "jdbc:sqlserver://127.0.0.1:$TcpPort;databaseName=$taskDatabase;encrypt=true;trustServerCertificate=true;loginTimeout=10;"
    $env:LIEM43_TEST_USER = $taskLogin
    $env:LIEM43_TEST_PASSWORD = $taskPassword
    Push-Location $taskRoot
    try {
        if ($Serve) {
            & mvn -B test-compile dependency:build-classpath '-Dmdep.outputFile=target/event-image-classpath.txt'
            if ($LASTEXITCODE -ne 0) { throw 'Web verification build failed' }
            $taskClasspath = 'target/test-classes;target/classes;' + (Get-Content -LiteralPath 'target/event-image-classpath.txt' -Raw).Trim()
            & java -cp $taskClasspath vn.ticketscenter.event.integration.EventImageWebFixture serve-local-test
            $taskResult = $LASTEXITCODE
        } else {
            & mvn -B '-Dtest=EventImageSqlIT,EventImageHttpIT' '-Djunit.jupiter.extensions.autodetection.enabled=false' test
            $taskResult = $LASTEXITCODE
        }
    } finally { Pop-Location }
} finally {
    Remove-Item Env:LIEM43_TEST_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:LIEM43_TEST_USER -ErrorAction SilentlyContinue
    Remove-Item Env:LIEM43_TEST_JDBC -ErrorAction SilentlyContinue
    $taskDrop = "USE [$taskDatabase]; IF USER_ID(N'$taskLogin') IS NOT NULL DROP USER [$taskLogin]; USE master; IF SUSER_ID(N'$taskLogin') IS NOT NULL DROP LOGIN [$taskLogin];"
    $taskCleanup = & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -Q $taskDrop 2>&1
    if ($LASTEXITCODE -ne 0) { Write-Warning 'Temporary verification principal cleanup failed' }
    $taskPassword = $null
    $taskSql = $null
}
exit $taskResult
