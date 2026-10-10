param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Select JDK 25 with JAVA_HOME or -JavaHome.'
}
$previousJava = $env:JAVA_HOME
$previousPath = $env:PATH
Push-Location -LiteralPath $repoRoot
try {
    $env:JAVA_HOME = $JavaHome
    $env:PATH = (Join-Path $JavaHome 'bin') + ';' + $previousPath
    # Explicit false flags override any opt-in inherited by a local Maven environment.
    & mvn -B clean test '-Dtc.tests.db=false' '-Dtc.tests.smtp=false' '-Dtc.tests.redis=false'
    $javaExit = $LASTEXITCODE
    & node src/test/scripts/test-report-summary.cjs target/surefire-reports
    $reportExit = $LASTEXITCODE
    if ($javaExit -ne 0 -or $reportExit -ne 0) { throw 'Safe Java test run failed; inspect local reports.' }
    & node --test src/test/js/swagger-initializer.test.cjs src/test/scripts/test-report-summary.test.cjs src/test/js/finance-readside.test.cjs src/test/js/finance-workbench.test.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Node boundary/report checks failed.' }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJava
    $env:PATH = $previousPath
}
