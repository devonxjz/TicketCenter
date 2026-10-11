#Requires -Version 7.4
# Dot-source only. SQL endpoint and authentication are deliberately not parameters.
function Open-AcceptanceFixture([string]$ConfigPath) {
 $fixture=Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
 $expected="jdbc:sqlserver://127.0.0.1:1433;databaseName=$($fixture.database);encrypt=false;trustServerCertificate=true;loginTimeout=5;"
 if($fixture.database -cnotmatch '^TC_Vuong_Test_Local_[A-Za-z0-9_]{1,40}$' -or $fixture.url -cne $expected) {throw 'Dedicated local fixture configuration required.'}
 $connection=[System.Data.SqlClient.SqlConnection]::new("Server=tcp:127.0.0.1,1433;Database=$($fixture.database);Integrated Security=True;TrustServerCertificate=True;Connection Timeout=5")
 try {
  $connection.Open();$command=$connection.CreateCommand()
  $command.CommandText="SELECT COUNT(*) FROM dbo.tc_vuong_test_fixture WHERE fixture_key IN ('vuong-team-schema-v1','vuong-finance-v1')"
  if([int]$command.ExecuteScalar() -ne 2){throw 'Both dedicated fixture markers required.'}
 } catch {$connection.Dispose();throw 'Marked local SQL fixture unavailable; no acceptance operation performed.'}
 return @{config=$fixture;connection=$connection}
}
function Invoke-AcceptanceQuery($Connection,[string]$Sql) {
 $command=$Connection.CreateCommand();$command.CommandText=$Sql;$command.CommandTimeout=180
 $reader=$command.ExecuteReader();$rows=[Collections.Generic.List[object]]::new()
 try {while($reader.Read()){$row=[ordered]@{};for($i=0;$i -lt $reader.FieldCount;$i++){$row[$reader.GetName($i)]=if($reader.IsDBNull($i)){$null}else{$reader.GetValue($i)}};$rows.Add([pscustomobject]$row)}}finally{$reader.Dispose();$command.Dispose()}
 return ,$rows.ToArray()
}
function Invoke-AcceptanceSql($Connection,[string]$Sql) {
 $command=$Connection.CreateCommand();$command.CommandText=$Sql;$command.CommandTimeout=180
 try {[void]$command.ExecuteNonQuery()}finally{$command.Dispose()}
}
function New-AcceptanceOutput([string]$OutputDirectory) {
 $path=[IO.Path]::GetFullPath($OutputDirectory)
 if(Test-Path -LiteralPath $path){throw 'Acceptance output already exists; choose a NEW directory.'}
 $repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
 if($path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
  & git -C $repo check-ignore -q -- ([IO.Path]::GetRelativePath($repo,$path))
  if($LASTEXITCODE -ne 0){throw 'Acceptance artifacts inside checkout must be git-ignored.'}
 }
 New-Item -ItemType Directory -Path $path | Out-Null
 $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
 foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
 Set-Acl -LiteralPath $path -AclObject $acl
 return $path
}
