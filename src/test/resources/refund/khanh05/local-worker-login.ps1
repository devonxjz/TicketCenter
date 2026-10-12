param([switch]$Cleanup)
$ErrorActionPreference='Stop'
# Windows integrated access is used only for this disposable LOCAL test login, never Azure.
$user=$env:TC_KHANH05_WORKER_USER;$secret=$env:TC_KHANH05_WORKER_PASSWORD
if($user -notmatch '^khanh05_worker_[a-f0-9]{32}$' -or $secret -notmatch '^K05![a-f0-9]{48}$'){throw 'Invalid disposable login settings'}
$c=[System.Data.SqlClient.SqlConnection]::new('Data Source=localhost;Initial Catalog=TicketCenter_Khanh02_Test;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5')
try {
 $c.Open();$q=$c.CreateCommand();$q.CommandTimeout=15
 if($Cleanup){$q.CommandText="DROP USER [$user];DROP LOGIN [$user]"}
 else {$q.CommandText="CREATE LOGIN [$user] WITH PASSWORD='$secret',CHECK_POLICY=ON;CREATE USER [$user] FOR LOGIN [$user];ALTER ROLE tc_worker ADD MEMBER [$user];GRANT VIEW DEFINITION TO [$user]"}
 [void]$q.ExecuteNonQuery()
}finally{$c.Dispose()}
