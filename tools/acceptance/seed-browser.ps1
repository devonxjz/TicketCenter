#Requires -Version 7.4
param([Parameter(Mandatory)][string]$ConfigPath)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'fixture.ps1')
$fixture=Open-AcceptanceFixture $ConfigPath
try {
 Invoke-AcceptanceSql $fixture.connection (Get-Content (Join-Path $PSScriptRoot '../../database/tests/vuong/029_browser_acceptance.sql') -Raw)
 Write-Output 'BROWSER_FIXTURE_PASS syntheticEvents=25 auditRows=25'
}finally{$fixture.connection.Dispose()}
