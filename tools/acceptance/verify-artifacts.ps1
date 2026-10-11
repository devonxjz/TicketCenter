#Requires -Version 7.4
param([Parameter(Mandatory)][ValidateSet('inventory','restore','benchmark')][string]$Kind,[Parameter(Mandatory)][string]$OutputDirectory,[string]$ConfigPath)
$ErrorActionPreference='Stop'
if($Kind -eq 'inventory'){
 $data=Get-Content (Join-Path $OutputDirectory 'catalog.json') -Raw|ConvertFrom-Json
 if($data.scope -ne 'Catalog existence, not behavior acceptance' -or $data.objects.Count -lt 1){throw 'Catalog evidence missing.'}
 & node (Join-Path $PSScriptRoot 'summarize-inventory.cjs') (Join-Path $OutputDirectory 'catalog.json') | Out-Null
 if($LASTEXITCODE -ne 0){throw 'SPEC mapping failed.'}
}elseif($Kind -eq 'restore'){
 $data=Get-Content (Join-Path $OutputDirectory 'restore-evidence.json') -Raw|ConvertFrom-Json
 if(-not $data.verified -or $data.checkdb -ne 'PASS' -or $data.source -eq $data.restored -or $data.rows.Count -lt 1 -or $data.columns.Count -lt 1 -or $data.indexes.Count -lt 1){throw 'Restore evidence incomplete.'}
 foreach($row in $data.rows){if($row.dataHash -notmatch '^[A-F0-9]{64}$' -or $row.rowCount -lt 0){throw 'Deterministic table data evidence missing.'}}
 # The SQL service owns its protected backup directory. Verify through SQL,
 # without widening Windows ACLs just to inspect a retained backup.
 if(-not $ConfigPath){throw 'Source fixture config required for server-side backup verification.'}
 . (Join-Path $PSScriptRoot 'fixture.ps1')
 $fixture=Open-AcceptanceFixture $ConfigPath
 try {
  if($data.source -cne $fixture.config.database -or $data.restored -cnotmatch '^TC_Vuong_Test_Local_[A-Za-z0-9_]{1,40}$'){throw 'Restore identity mismatch.'}
  $backupDir=(Invoke-AcceptanceQuery $fixture.connection "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath')) path")[0].path
  $leaf=Split-Path -Leaf $data.backup
  if($leaf -cnotmatch ('^'+[regex]::Escape($data.source)+'_[a-f0-9]{32}\.bak$') -or $data.backup -cne (Join-Path $backupDir $leaf)){throw 'Backup must be the retained fixture backup in SQL default directory.'}
  Invoke-AcceptanceSql $fixture.connection ("RESTORE VERIFYONLY FROM DISK=N'"+$data.backup.Replace("'","''")+"' WITH CHECKSUM;")
  $files=Invoke-AcceptanceQuery $fixture.connection "SELECT physical_name FROM sys.master_files WHERE database_id=DB_ID(N'$($data.restored)') ORDER BY file_id"
  if(($files.physical_name|ConvertTo-Json -Compress) -cne ($data.physicalFiles|ConvertTo-Json -Compress)){throw 'Restored physical file catalog differs.'}
 }finally{$fixture.connection.Dispose()}
}else{
 $data=Get-Content (Join-Path $OutputDirectory 'benchmark.json') -Raw|ConvertFrom-Json
 if($data.syntheticRows -lt 20000 -or $data.runs.Count -ne 18){throw '18 actual benchmark runs required.'}
 $groups=@($data.runs|Group-Object query,phase)
 if($groups.Count -ne 6 -or @($groups|Where-Object Count -ne 3).Count){throw 'Three repeats of three profiles in both phases required.'}
 foreach($group in ($data.runs|Group-Object query)){
  if(@($group.Group.resultHash|Select-Object -Unique).Count -ne 1){throw 'Results differ between phases.'}
 }
 foreach($run in $data.runs){if($run.rows -ne 20 -or $run.logicalReads -lt 0 -or $run.clientElapsedMs -lt 0 -or $run.planFiles -lt 1){throw 'Actual run measurements missing.'}}
 $plans=@(Get-ChildItem -LiteralPath $OutputDirectory -Filter '*.sqlplan')
 if($plans.Count -ne 18){throw '18 retained actual plans required.'}
 foreach($plan in $plans){[xml]$xml=Get-Content -LiteralPath $plan.FullName -Raw;if($xml.DocumentElement.LocalName -ne 'ShowPlanXML'){throw 'Invalid actual plan.'}}
}
Write-Output ($Kind.ToUpperInvariant()+'_ARTIFACTS_PASS')
