$ErrorActionPreference='Stop'
# Resolve the existing view/function dependencies without modifying historical migrations.
# Restricted to our disposable local database; never accepts a user-provided target.
$connection=[System.Data.SqlClient.SqlConnection]::new('Server=.;Database=TicketCenter_Khanh02_Test;Integrated Security=true;TrustServerCertificate=true')
$connection.Open()
try {
 $q=$connection.CreateCommand();$q.CommandText='SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;';$q.ExecuteNonQuery() | Out-Null
 $pending=[Collections.Generic.List[string]]::new()
 foreach($file in Get-ChildItem database/migrations -Filter '00*.sql' | Sort-Object Name){
  foreach($batch in [regex]::Split((Get-Content -Raw -LiteralPath $file.FullName),'(?im)^\s*GO\s*\r?$')){if($batch.Trim()){$pending.Add($batch)}}
 }
 for($pass=0;$pending.Count -gt 0 -and $pass -lt 8;$pass++){
  $next=[Collections.Generic.List[string]]::new();$lastFailure=$null
  foreach($batch in $pending){try{$q.CommandText=$batch;$q.ExecuteNonQuery() | Out-Null}catch{
   $exception=$_.Exception;while($exception.InnerException){$exception=$exception.InnerException}
   if($exception -isnot [System.Data.SqlClient.SqlException] -or $exception.Number -notin @(208,4121,15151)) {throw}
   $next.Add($batch);$lastFailure=$exception.Message
  }}
  if($next.Count -eq $pending.Count){throw ('Unresolved schema dependency: '+$lastFailure)}
  $pending=$next
 }
 if($pending.Count){throw 'Unresolved schema dependencies after 8 passes'}
 Write-Output 'Historical base schema applied with all dependencies resolved.'
}finally{$connection.Close()}