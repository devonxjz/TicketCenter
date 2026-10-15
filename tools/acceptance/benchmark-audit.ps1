#Requires -Version 7.4
param([Parameter(Mandatory)]$Connection,[Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'fixture.ps1')
if($Connection.Database -cnotmatch '^TC_Vuong_Test_Local_Bench_[A-Za-z0-9_]+$'){throw 'Separate Bench_* fixture required.'}
Invoke-AcceptanceSql $Connection (Get-Content (Join-Path $PSScriptRoot '../../database/tests/vuong/030_audit_benchmark.sql') -Raw)
$base="SELECT id,actor_id,action,entity_type,entity_id,metadata_json,created_at FROM dbo.vw_VuongAuditLogs WHERE created_at>='2026-01-01' AND created_at<'2026-01-02'"
$queries=[ordered]@{
 date_page=$base+" ORDER BY created_at DESC,id DESC OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY;"
 action_page=$base+" AND action='SETTLEMENT_CONFIRMED' ORDER BY created_at DESC,id DESC OFFSET 0 ROWS FETCH NEXT 20 ROWS ONLY;"
 actor_action_page=$base+" AND action='SETTLEMENT_CONFIRMED' AND actor_id='00000000-0000-0000-0000-000000000101' ORDER BY created_at DESC,id DESC OFFSET 20 ROWS FETCH NEXT 20 ROWS ONLY;"
}
$runs=[Collections.Generic.List[object]]::new();$hashes=@{}
foreach($phase in @('before','after')){
 if($phase -eq 'after'){
  Invoke-AcceptanceSql $Connection 'CREATE INDEX IX_Acceptance_Audit_Action_Date ON dbo.tc_audit_logs(action,created_at DESC,id DESC) INCLUDE(actor_id,aggregate_type,aggregate_id,detail);'
 }
 foreach($entry in $queries.GetEnumerator()){
  for($run=1;$run -le 3;$run++){
   $messages=[Collections.Generic.List[string]]::new()
   $handler=[System.Data.SqlClient.SqlInfoMessageEventHandler]{param($sender,$eventArgs) $messages.Add($eventArgs.Message)}
   $Connection.add_InfoMessage($handler)
   $command=$Connection.CreateCommand();$command.CommandTimeout=180
   $command.CommandText='SET STATISTICS IO ON; SET STATISTICS TIME ON; SET STATISTICS XML ON; '+$entry.Value
   $rows=[Collections.Generic.List[object]]::new();$plans=[Collections.Generic.List[string]]::new()
   $watch=[Diagnostics.Stopwatch]::StartNew()
   try {
    $reader=$command.ExecuteReader()
    try {
     do {
      if($reader.FieldCount -eq 0){continue}
      $isPlan=$reader.FieldCount -eq 1 -and $reader.GetName(0) -eq 'Microsoft SQL Server 2005 XML Showplan'
      while($reader.Read()){
       if($isPlan){$plans.Add($reader.GetString(0))}else{
        $row=[ordered]@{};for($i=0;$i -lt $reader.FieldCount;$i++){$row[$reader.GetName($i)]=if($reader.IsDBNull($i)){$null}else{$reader.GetValue($i)}};$rows.Add([pscustomobject]$row)
       }
      }
     }while($reader.NextResult())
    }finally{$reader.Dispose()}
   }finally{$watch.Stop();$command.Dispose();$Connection.remove_InfoMessage($handler);Invoke-AcceptanceSql $Connection 'SET STATISTICS XML OFF; SET STATISTICS IO OFF; SET STATISTICS TIME OFF;'}
   if($plans.Count -lt 1 -or $rows.Count -ne 20){throw 'Actual plan and twenty result rows required.'}
   $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($rows.ToArray()|ConvertTo-Json -Depth 5 -Compress))))
   if($hashes.ContainsKey($entry.Key) -and $hashes[$entry.Key] -cne $hash){throw 'Benchmark result rows changed.'};$hashes[$entry.Key]=$hash
   $stem=$phase+'-'+$entry.Key+'-'+$run
   for($p=0;$p -lt $plans.Count;$p++){$plans[$p]|Set-Content -LiteralPath (Join-Path $OutputDirectory ($stem+'-'+$p+'.sqlplan')) -Encoding utf8}
   $statistics=$messages -join "`n";$statistics|Set-Content -LiteralPath (Join-Path $OutputDirectory ($stem+'.stats.txt')) -Encoding utf8
   $reads=0;foreach($match in [regex]::Matches($statistics,'(?<!lob )logical reads (\d+)')){$reads+=[int]$match.Groups[1].Value}
   if(-not $statistics.Contains('logical reads')){throw 'STATISTICS IO output required (English SQL Server messages).'}
   $runs.Add(@{phase=$phase;query=$entry.Key;run=$run;rows=$rows.Count;resultHash=$hash;logicalReads=$reads;clientElapsedMs=$watch.Elapsed.TotalMilliseconds;planFiles=$plans.Count})
  }
 }
}
$sizes=Invoke-AcceptanceQuery $Connection "SELECT i.name,SUM(p.row_count) rows,SUM(p.used_page_count)*8 used_kb FROM sys.indexes i JOIN sys.dm_db_partition_stats p ON p.object_id=i.object_id AND p.index_id=i.index_id WHERE i.object_id=OBJECT_ID('dbo.tc_audit_logs') GROUP BY i.name ORDER BY i.name"
@{database=$Connection.Database;syntheticRows=20000;queries=$queries;runs=$runs.ToArray();indexes=$sizes;scope='AuditRepository date/action/actor list profiles through actual adapter view; no org/event EXISTS profiles; warm cache, XML capture overhead included; candidate only in private fixture.'}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $OutputDirectory 'benchmark.json') -Encoding utf8
