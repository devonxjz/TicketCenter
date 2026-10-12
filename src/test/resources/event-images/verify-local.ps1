param([int]$TcpPort = 63657, [switch]$Serve)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$taskDatabase = 'TicketCenter_LIEM43_Test'
$taskLogin = 'liem43_verify_' + [Guid]::NewGuid().ToString('N')
$taskBytes = New-Object byte[] 24
(New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($taskBytes)
$taskPassword = [System.BitConverter]::ToString($taskBytes).Replace('-', '') + 'aA!7'
$taskSql = "CREATE LOGIN [$taskLogin] WITH PASSWORD=N'$taskPassword', CHECK_POLICY=ON; USE [$taskDatabase]; CREATE USER [$taskLogin] FOR LOGIN [$taskLogin]; ALTER ROLE db_owner ADD MEMBER [$taskLogin];"
$taskResult = 1
try {
    & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $taskDatabase -i (Join-Path $taskRoot 'database/migrations/028_event_image_workflow.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated migration028 failed' }
    & sqlcmd -S '.\SQLEXPRESS' -E -N -C -I -b -d $taskDatabase -i (Join-Path $taskRoot 'database/migrations/032_sql_report_permissions.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Isolated migration032 failed' }
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
            $m2 = Join-Path $HOME '.m2/repository'
            $embeddedJars = @(
                (Join-Path $m2 'org/apache/tomcat/embed/tomcat-embed-core/11.0.25/tomcat-embed-core-11.0.25.jar'),
                (Join-Path $m2 'org/apache/tomcat/embed/tomcat-embed-jasper/11.0.25/tomcat-embed-jasper-11.0.25.jar'),
                (Join-Path $m2 'org/apache/tomcat/embed/tomcat-embed-el/11.0.25/tomcat-embed-el-11.0.25.jar'),
                (Join-Path $m2 'org/apache/tomcat/tomcat-annotations-api/11.0.25/tomcat-annotations-api-11.0.25.jar'),
                (Join-Path $m2 'org/eclipse/jdt/ecj/3.46.0/ecj-3.46.0.jar'),
                (Join-Path $m2 'jakarta/servlet/jsp/jakarta.servlet.jsp-api/4.0.0/jakarta.servlet.jsp-api-4.0.0.jar'),
                (Join-Path $m2 'jakarta/servlet/jsp/jstl/jakarta.servlet.jsp.jstl-api/3.0.1/jakarta.servlet.jsp.jstl-api-3.0.1.jar'),
                (Join-Path $m2 'org/glassfish/web/jakarta.servlet.jsp.jstl/3.0.1/jakarta.servlet.jsp.jstl-3.0.1.jar')
            ) -join ';'
            $taskClasspath = "target/test-classes;target/classes;$embeddedJars;" + (Get-Content -LiteralPath 'target/event-image-classpath.txt' -Raw).Trim()
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
