# Explicit KHANH-01 database setup. No table/data rewrite, credentials or schema validation changes.
$ErrorActionPreference='Stop'
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object {
 if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
$builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source']='tcp:'+$settings['db.host']+',1433'
$builder['Initial Catalog']=$settings['db.name']
$builder['User ID']=$settings['db.user']; $builder['Password']=$settings['db.password']
$builder['Encrypt']=$true; $builder['TrustServerCertificate']=$false; $builder['Connect Timeout']=5
$connection=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
try {
 $connection.Open(); $transaction=$connection.BeginTransaction()
 try {
  $sql=Get-Content src/test/resources/checkin/azure-contract.sql -Raw
  foreach($batch in [regex]::Split($sql,'(?im)^GO\s*$')) {
   if([string]::IsNullOrWhiteSpace($batch)){continue}
   $command=$connection.CreateCommand(); $command.Transaction=$transaction
   $command.CommandText=$batch; $command.CommandTimeout=10
   [void]$command.ExecuteNonQuery(); $command.Dispose()
  }
  $transaction.Commit()
  Write-Output 'Applied canonical UUID function and minimum CHECK_IN read/execute grants atomically.'
 } catch { $transaction.Rollback(); throw }
} finally { $connection.Dispose() }
