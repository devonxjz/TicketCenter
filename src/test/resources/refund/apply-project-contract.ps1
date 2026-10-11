$ErrorActionPreference='Stop'
# Explicitly approved KHANH-03 installation. Adds views/procedure/grants only.
$settings=@{}
Get-Content src/main/resources/application.properties | ForEach-Object {
 if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}
}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected project database target'}
$builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$builder['Data Source']='tcp:'+$settings['db.host']+',1433';$builder['Initial Catalog']=$settings['db.name']
$builder['User ID']=$settings['db.user'];$builder['Password']=$settings['db.password']
$builder['Encrypt']=$true;$builder['TrustServerCertificate']=$false;$builder['Connect Timeout']=30;$builder['Pooling']=$false
$migration='database/migrations/029_khanh03_refund_request.sql'
$version=[IO.Path]::GetFileNameWithoutExtension($migration)
$checksum=(Get-FileHash -LiteralPath $migration -Algorithm SHA256).Hash.ToLowerInvariant()
$connection=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
try {
 $connection.Open();$transaction=$connection.BeginTransaction()
 try {
  $command=$connection.CreateCommand();$command.Transaction=$transaction
  $command.CommandText='SELECT checksum_sha256 FROM dbo.tc_schema_migrations WITH(UPDLOCK,HOLDLOCK) WHERE version=@version'
  [void]$command.Parameters.AddWithValue('@version',$version)
  $old=$command.ExecuteScalar();$command.Dispose()
  if($old -isnot [DBNull] -and $null -ne $old -and $old -ne $checksum){throw 'Recorded migration checksum differs; refusing rewrite'}
  if($old -isnot [DBNull] -and $null -ne $old){$transaction.Commit();Write-Output 'KHANH-03 migration already installed with matching checksum.';return}
  foreach($batch in [regex]::Split((Get-Content -Raw -LiteralPath $migration),'(?im)^\s*GO\s*\r?$')){
   if(!$batch.Trim()){continue}
   $command=$connection.CreateCommand();$command.Transaction=$transaction;$command.CommandText=$batch;$command.CommandTimeout=30
   [void]$command.ExecuteNonQuery();$command.Dispose()
  }
  if($null -eq $old -or $old -is [DBNull]){
   $command=$connection.CreateCommand();$command.Transaction=$transaction
   $command.CommandText='INSERT dbo.tc_schema_migrations(version,checksum_sha256) VALUES(@version,@checksum)'
   [void]$command.Parameters.AddWithValue('@version',$version);[void]$command.Parameters.AddWithValue('@checksum',$checksum)
   [void]$command.ExecuteNonQuery();$command.Dispose()
  }
  $transaction.Commit();Write-Output 'KHANH-03 contract installed atomically; migration checksum recorded. No business data changed.'
 }catch{$transaction.Rollback();throw}
}finally{$connection.Dispose()}
