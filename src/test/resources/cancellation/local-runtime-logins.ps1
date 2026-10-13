param([switch]$Cleanup)
$ErrorActionPreference='Stop'
$taskDatabase=if($env:TC_CANCELLATION_CANONICAL_TEST -eq 'true' -and $env:TC_SCAN_TEST_URL -match '^jdbc:sqlserver://127\.0\.0\.1:\d+;databaseName=TC_Khanh06_Test_Canonical;'){'TC_Khanh06_Test_Canonical'}else{'TicketCenter_Khanh02_Test'}
$taskConnection=[System.Data.SqlClient.SqlConnection]::new("Data Source=localhost;Initial Catalog=$taskDatabase;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5")
try {
 $taskConnection.Open()
 foreach($taskRole in @('ADMIN','WORKER')) {
  $taskUser=[Environment]::GetEnvironmentVariable("TC_KHANH06_${taskRole}_USER")
  $taskSecret=[Environment]::GetEnvironmentVariable("TC_KHANH06_${taskRole}_PASSWORD")
  if($taskUser -notmatch '^khanh06_(admin|worker)_[a-f0-9]{32}$' -or $taskSecret -notmatch '^K06![a-f0-9]{48}$'){throw 'Invalid disposable runtime login'}
  $taskCommand=$taskConnection.CreateCommand();$taskCommand.CommandTimeout=15
  $taskDbRole=if($taskRole -eq 'ADMIN'){'tc_platform_admin'}else{'tc_worker'}
  if($Cleanup){$taskCommand.CommandText="DROP USER [$taskUser];DROP LOGIN [$taskUser]"}
  else{$taskCommand.CommandText="CREATE LOGIN [$taskUser] WITH PASSWORD='$taskSecret',CHECK_POLICY=ON;CREATE USER [$taskUser] FOR LOGIN [$taskUser];ALTER ROLE $taskDbRole ADD MEMBER [$taskUser];GRANT VIEW DEFINITION TO [$taskUser]"}
  [void]$taskCommand.ExecuteNonQuery()
 }
}finally{$taskConnection.Dispose()}
