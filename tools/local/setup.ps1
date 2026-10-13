#Requires -Version 7.4
param(
 [Parameter(Mandatory=$true)][string]$Database,
 [string]$ConfigPath
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if($Database -cnotmatch '^TC_Vuong_Test_Local_[A-Za-z0-9_]{1,40}$') {throw 'Dedicated local database name required: TC_Vuong_Test_Local_<name>.'}
if(-not $ConfigPath) {$ConfigPath=Join-Path $repo '.local/database.json'}
$ConfigPath=[IO.Path]::GetFullPath($ConfigPath)
if(Test-Path -LiteralPath $ConfigPath) {throw 'Configuration already exists; refusing to overwrite.'}
if($ConfigPath.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
 $relative=[IO.Path]::GetRelativePath($repo,$ConfigPath)
 & git -C $repo check-ignore -q -- $relative
 if($LASTEXITCODE -ne 0) {throw 'Credential file inside the checkout must be git-ignored.'}
}
$scripts=@(
 'database/tests/vuong/002_team_schema_snapshot.sql',
 'database/migrations/027_team_schema_adapter.sql',
 'database/migrations/027_vuong_finance.sql',
 'database/migrations/028_vuong_report_checkins.sql',
 'database/migrations/028_event_image_workflow.sql'
)
foreach($file in $scripts) {if(-not(Test-Path -LiteralPath (Join-Path $repo $file))) {throw 'Required schema file is missing.'}}
# Fixed endpoint, Windows authentication, no externally supplied SQL or server.
$master=[System.Data.SqlClient.SqlConnection]::new('Server=tcp:127.0.0.1,1433;Database=master;Integrated Security=True;TrustServerCertificate=True;Connection Timeout=5')
$login='tc_local_'+[guid]::NewGuid().ToString('N')
$password='Tc!9'+[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
try {
 $master.Open();$command=$master.CreateCommand();$command.CommandTimeout=120
 $command.CommandText='SELECT DB_ID(@name)';[void]$command.Parameters.AddWithValue('@name',$Database)
 if($command.ExecuteScalar() -isnot [DBNull]) {throw 'Dedicated database already exists; refusing to overwrite.'}
 $command.Parameters.Clear();$command.CommandText="CREATE DATABASE [$Database]";[void]$command.ExecuteNonQuery()
 # Restricted to a new dedicated fixture DB. This identity is NOT least-privilege acceptance.
 $command.CommandText="CREATE LOGIN [$login] WITH PASSWORD=N'$password',CHECK_POLICY=ON,DEFAULT_DATABASE=[$Database]";[void]$command.ExecuteNonQuery()
} catch {
 if($_.Exception.Message -match 'already exists') {throw}
 throw 'Local SQL provisioning failed. Check Windows SQL permissions, SQL authentication and TCP 127.0.0.1:1433. No existing database was replaced.'
} finally {$master.Dispose()}
$connection=[System.Data.SqlClient.SqlConnection]::new("Server=tcp:127.0.0.1,1433;Database=$Database;Integrated Security=True;TrustServerCertificate=True;Connection Timeout=5")
try {
 $connection.Open();$command=$connection.CreateCommand();$command.CommandTimeout=120
 # The snapshot requires an empty DB; record checksums only AFTER installing it.
 foreach($file in $scripts) {
  $path=Join-Path $repo $file
  foreach($batch in ([regex]::Split([IO.File]::ReadAllText($path),'(?im)^\s*GO\s*(?:--[^\r\n]*)?\r?$'))) {
   if(-not [string]::IsNullOrWhiteSpace($batch)) {$command.CommandText=$batch;[void]$command.ExecuteNonQuery()}
  }
  if($file -eq $scripts[0]) {
   $command.CommandText='CREATE TABLE dbo.tc_local_handoff_migrations(path varchar(200) NOT NULL PRIMARY KEY,sha256 char(64) NOT NULL,installed_at datetime2 NOT NULL DEFAULT SYSUTCDATETIME())';[void]$command.ExecuteNonQuery()
  }
  $command.CommandText='INSERT dbo.tc_local_handoff_migrations(path,sha256) VALUES(@path,@hash)'
  [void]$command.Parameters.AddWithValue('@path',$file);[void]$command.Parameters.AddWithValue('@hash',(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLower())
  [void]$command.ExecuteNonQuery();$command.Parameters.Clear()
  Write-Output ('Installed '+$file)
 }
 $command.CommandText="CREATE USER [$login] FOR LOGIN [$login]; ALTER ROLE db_owner ADD MEMBER [$login]; ALTER ROLE tc_platform_admin ADD MEMBER [$login]; ALTER ROLE tc_manager ADD MEMBER [$login]; ALTER ROLE tc_checkin ADD MEMBER [$login]; ALTER ROLE tc_buyer ADD MEMBER [$login]; ALTER ROLE tc_auth ADD MEMBER [$login]; ALTER ROLE tc_worker ADD MEMBER [$login];";[void]$command.ExecuteNonQuery()
} catch {throw "Local schema installation failed; database $Database was retained for inspection. Choose a NEW name for retry; this tool never drops databases."}
finally {$connection.Dispose()}
$fixture=[ordered]@{database=$Database;url="jdbc:sqlserver://127.0.0.1:1433;databaseName=$Database;encrypt=false;trustServerCertificate=true;loginTimeout=5;";user=$login;password=$password}
$directory=Split-Path -Parent $ConfigPath
New-Item -ItemType Directory -Path $directory -Force | Out-Null
# Credential-bearing files are private to the current Windows identity (and SYSTEM).
# Do not change ACLs on an arbitrary pre-existing parent directory.
$fileStream=[IO.File]::Open($ConfigPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
$fileStream.Dispose()
$fileAcl=[Security.AccessControl.FileSecurity]::new();$fileAcl.SetAccessRuleProtection($true,$false)
foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {$fileAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','Allow'))}
Set-Acl -LiteralPath $ConfigPath -AclObject $fileAcl
[IO.File]::WriteAllText($ConfigPath,($fixture|ConvertTo-Json))
Write-Output ('LOCAL_DATABASE_READY '+$Database)
Write-Output ('PRIVATE_CONFIG '+$ConfigPath)
