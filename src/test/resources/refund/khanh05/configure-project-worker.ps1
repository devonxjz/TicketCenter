$ErrorActionPreference='Stop'
$configPath=$env:TC_CONFIG_FILE
if([string]::IsNullOrWhiteSpace($configPath)){$configPath='.local/application.properties'}
$resolved=(Resolve-Path -LiteralPath $configPath).Path
$original=[IO.File]::ReadAllText($resolved)
$settings=@{}
foreach($line in $original -split "`n"){if($line -match '^(db\.[^=]+)=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}}
if($settings['db.host'] -ne 'ticketscenter.database.windows.net' -or $settings['db.name'] -ne 'TicketsCenter'){throw 'Unexpected project target'}
if($settings['db.worker.user'] -and $settings['db.worker.password']){Write-Output 'Private WORKER configuration already present; no credentials changed.';return}
$name='tc_khanh05_worker_'+[guid]::NewGuid().ToString('N')
$bytes=[byte[]]::new(24);$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
$password='K05!'+([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
$b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
$b['Data Source']='tcp:'+ $settings['db.host']+',1433';$b['Initial Catalog']=$settings['db.name'];$b['User ID']=$settings['db.user'];$b['Password']=$settings['db.password'];$b['Encrypt']=$true;$b['TrustServerCertificate']=$false;$b['Connect Timeout']=30
$c=[System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
try {
 $c.Open();$transaction=$c.BeginTransaction()
 try {
  $q=$c.CreateCommand();$q.Transaction=$transaction;$q.CommandTimeout=30
  $q.CommandText="CREATE USER [$name] WITH PASSWORD='$password';ALTER ROLE tc_worker ADD MEMBER [$name];GRANT VIEW DEFINITION TO [$name]"
  [void]$q.ExecuteNonQuery();$q.Dispose()
  $updated=[regex]::Replace($original,'(?m)^db\.worker\.(?:user|password)=.*\r?\n?','')
  $updated=$updated.TrimEnd()+"`n"+'db.worker.user='+$name+"`n"+'db.worker.password='+$password+"`n"
  $temp=$resolved+'.worker-pending'
  [IO.File]::WriteAllText($temp,$updated,[Text.UTF8Encoding]::new($false))
  $transaction.Commit()
  Move-Item -LiteralPath $temp -Destination $resolved -Force
  Write-Output 'Dedicated Azure WORKER user created with generated private credentials, tc_worker role and metadata permission. No existing login/password or business data changed.'
 }catch{if($transaction.Connection){$transaction.Rollback()};throw}
}finally{$c.Dispose()}
