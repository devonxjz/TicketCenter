#Requires -Version 7.4
param([Parameter(Mandatory)][string]$ConfigPath,[Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'fixture.ps1')
$fixture=Open-AcceptanceFixture $ConfigPath
try {
 if($fixture.config.database -cnotmatch '^TC_Vuong_Test_Local_Bench_[A-Za-z0-9_]+$'){throw 'Separate NEW Bench fixture required.'}
 $output=New-AcceptanceOutput $OutputDirectory
 & (Join-Path $PSScriptRoot 'benchmark-audit.ps1') -Connection $fixture.connection -OutputDirectory $output
 if(-not(Test-Path -LiteralPath (Join-Path $output 'benchmark.json'))){throw 'Benchmark results missing.'}
 Write-Output ('FINANCE_BENCHMARK_PASS '+$output)
}finally{$fixture.connection.Dispose()}
