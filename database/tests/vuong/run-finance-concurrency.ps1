param([string]$DatabaseName='TC_Vuong_Test_20261010_Finance',[string]$Server='localhost',[string]$SqlCmd='C:/Program Files/Microsoft SQL Server/Client SDK/ODBC/180/Tools/Binn/sqlcmd.exe')
$ErrorActionPreference='Stop'
if ($DatabaseName -notmatch '^TC_Vuong_Test_[A-Za-z0-9_]+$' -or $Server -ne 'localhost') { throw 'Only an isolated local TC_Vuong_Test_ target is permitted' }
$runId=[guid]::NewGuid().ToString()
$source=Join-Path $PSScriptRoot 'finance_concurrency.sql'
$logRoot=Join-Path $env:TEMP ('vuong-finance-races-'+$runId)
New-Item -ItemType Directory -Path $logRoot | Out-Null
function Invoke-Mode([string]$Mode) {
 & $SqlCmd -S $Server -E -C -b -I -d $DatabaseName -i $source -v "RunId=$runId" "Mode=$Mode" -o (Join-Path $logRoot ($Mode+'.txt'))
 if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $logRoot ($Mode+'.txt')) -Tail 15; throw "SQL mode $Mode failed: $LASTEXITCODE" }
}
Invoke-Mode 'SETUP'
foreach ($pair in @(@('CAPACITY_A','CAPACITY_B'),@('REPLAY_A','REPLAY_B'),@('REFUND_A','CONFIRM_B'),@('PUBLISH_A','EDIT_B'),@('EVIDENCE_A','EVIDENCE_CONFIRM_B'),@('EVIDENCE_CONFIRM_A','EVIDENCE_WRITE_B'))) {
 $aLog=Join-Path $logRoot ($pair[0]+'.txt')
 $arguments=@('-S',$Server,'-E','-C','-b','-I','-d',$DatabaseName,'-i',('"'+$source+'"'),'-v',"RunId=$runId",('Mode='+$pair[0]),'-o',('"'+$aLog+'"'))
 $process=Start-Process -FilePath $SqlCmd -ArgumentList $arguments -WindowStyle Hidden -PassThru
 $deadline=(Get-Date).AddSeconds(15)
 while ((Get-Date) -lt $deadline -and -not $process.HasExited -and (-not (Test-Path -LiteralPath $aLog) -or -not ((Get-Content -LiteralPath $aLog -Raw) -match 'LOCKED:'))) { Start-Sleep -Milliseconds 50; $process.Refresh() }
 if (-not (Test-Path -LiteralPath $aLog) -or -not ((Get-Content -LiteralPath $aLog -Raw) -match 'LOCKED:')) { throw ('Contender A did not establish lock: '+$pair[0]) }
 Invoke-Mode $pair[1]
 $process.WaitForExit()
 if ($process.ExitCode -ne 0) { Get-Content -LiteralPath $aLog -Tail 15; throw ('Contender A failed: '+$pair[0]) }
 Write-Output ('PASS: '+$pair[0]+' / '+$pair[1])
}
Invoke-Mode 'ASSERT'
Get-Content -LiteralPath (Join-Path $logRoot 'ASSERT.txt')
Write-Output ('Concurrency logs: '+$logRoot)
