$ErrorActionPreference='Stop'
# Fixed NEW local fixture. Never replace a DB or change an existing login/password.
$taskConnection=[System.Data.SqlClient.SqlConnection]::new('Data Source=localhost;Initial Catalog=master;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5')
try {
 $taskConnection.Open();$taskCommand=$taskConnection.CreateCommand();$taskCommand.CommandTimeout=60
 $taskCommand.CommandText="IF DB_ID('TC_Khanh06_Test_Canonical') IS NOT NULL THROW 53990,'Canonical fixture already exists; refusing overwrite',1;CREATE DATABASE TC_Khanh06_Test_Canonical;"
 [void]$taskCommand.ExecuteNonQuery()
 $taskCommand.CommandText='USE TC_Khanh06_Test_Canonical;CREATE USER khanh02_owner FOR LOGIN khanh02_owner;ALTER ROLE db_owner ADD MEMBER khanh02_owner;'
 [void]$taskCommand.ExecuteNonQuery()
 Write-Output 'Created a new isolated canonical acceptance database.'
}finally{$taskConnection.Dispose()}
