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
 'database/migrations/028_event_image_workflow.sql',
 'database/migrations/028_vuong_report_checkins.sql',
 'database/migrations/029_admin_organization_approval.sql',
 'database/migrations/029_hold_runtime.sql',
 'database/migrations/029_khanh03_refund_request.sql',
 'database/migrations/030_khanh02_signed_checkin.sql',
 'database/migrations/030_order_runtime.sql'
)
$scripts+=@(Get-ChildItem (Join-Path $repo 'database/migrations') -File | Where-Object Name -match '^(03[1-9]|04[0-2])_' | Sort-Object Name | ForEach-Object {'database/migrations/'+$_.Name})
$scripts+=@('database/migrations/043_coupon_admin.sql','database/migrations/045_order_ticket_access_assertions.sql',
 'database/migrations/044_buyer_order_history_views.sql','database/migrations/045_event_commands_and_public_seats.sql',
 'database/migrations/043_khanh04_admin_refund.sql','database/migrations/046_admin_refund_current_authority.sql',
 'database/migrations/047_event_authority_lock_order.sql')
$scripts+=@(Get-ChildItem (Join-Path $repo 'database/migrations') -File | Where-Object Name -match '^(04[89]|0[5-9][0-9])_' | Sort-Object Name | ForEach-Object {'database/migrations/'+$_.Name})
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
   if(-not [string]::IsNullOrWhiteSpace($batch)) {
    # Historical052 references an optional SSMS table in a compound IF. Defer its
    # resolution until existence is confirmed; preserve migration bytes and all module definitions.
    if($file -eq 'database/migrations/052_drop_unused_objects.sql' -and $batch.Trim().StartsWith("IF OBJECT_ID('dbo.sysdiagrams','U') IS NOT NULL AND EXISTS")) {
     $batch="IF OBJECT_ID('dbo.sysdiagrams','U') IS NOT NULL EXEC sys.sp_executesql N'IF EXISTS(SELECT 1 FROM dbo.sysdiagrams) THROW 52070,''052 refuses to drop sysdiagrams while it holds diagrams'',1;';"
    }
    $command.CommandText=$batch;[void]$command.ExecuteNonQuery()
   }
  }
  if($file -eq $scripts[0]) {
   foreach($role in @('tc_platform_admin','tc_manager','tc_checkin','tc_buyer','tc_auth','tc_worker')) {
    $command.CommandText="IF DATABASE_PRINCIPAL_ID('$role') IS NULL CREATE ROLE [$role]";[void]$command.ExecuteNonQuery()
   }
   $command.CommandText='CREATE TABLE dbo.tc_local_handoff_migrations(path varchar(200) NOT NULL PRIMARY KEY,sha256 char(64) NOT NULL,installed_at datetime2 NOT NULL DEFAULT SYSUTCDATETIME())';[void]$command.ExecuteNonQuery()
  }
  $command.CommandText='INSERT dbo.tc_local_handoff_migrations(path,sha256) VALUES(@path,@hash)'
  [void]$command.Parameters.AddWithValue('@path',$file);[void]$command.Parameters.AddWithValue('@hash',(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLower())
  [void]$command.ExecuteNonQuery();$command.Parameters.Clear()
  Write-Output ('Installed '+$file)
 }
 # This fixture intentionally shares an owner connection across runtime principals.
 # Combining all production roles instead causes DENY from one role to override another.
 # Ownership applies only to the newly created isolated DB; it is not least-privilege evidence.
 $command.CommandText="ALTER AUTHORIZATION ON DATABASE::[$Database] TO [$login]";[void]$command.ExecuteNonQuery()
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
