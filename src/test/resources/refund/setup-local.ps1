$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$database='TicketCenter_Khanh03_Test'
$file=Join-Path (Get-Location) 'docs/handoff/khanh03-local-secrets.xml'
if(Test-Path -LiteralPath $file){throw 'Fixture already configured; use load-local.ps1'}
New-Item -ItemType Directory -Path docs/handoff -Force | Out-Null
$connection=[System.Data.SqlClient.SqlConnection]::new('Server=.;Database=master;Integrated Security=true;TrustServerCertificate=true')
$connection.Open()
try {
 $command=$connection.CreateCommand();$command.CommandText="SELECT DB_ID('$database')"
 if($command.ExecuteScalar() -isnot [DBNull]){throw 'Database exists; refusing to overwrite'}
 $command.CommandText="CREATE DATABASE [$database]";$command.ExecuteNonQuery() | Out-Null
 $protected=@{}
 foreach($role in @('owner','buyer','admin')) {
  $bytes=[byte[]]::new(32);[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
  $password='K03!'+[Convert]::ToHexString($bytes)
  $login='khanh03_'+$role
  $command.CommandText="CREATE LOGIN [$login] WITH PASSWORD=N'$password',CHECK_POLICY=ON; USE [$database]; CREATE USER [$login] FOR LOGIN [$login];"
  $command.ExecuteNonQuery() | Out-Null
  $protected[$role]=ConvertTo-SecureString $password -AsPlainText -Force
 }
 $command.CommandText="USE [$database]; ALTER ROLE db_owner ADD MEMBER khanh03_owner";$command.ExecuteNonQuery() | Out-Null
 $protected | Export-Clixml -LiteralPath $file
}finally{$connection.Close()}
& ./src/test/resources/refund/apply-base-local.ps1
& sqlcmd -S . -E -I -d $database -b -f 65001 -i database/migrations/029_khanh03_refund_request.sql
if($LASTEXITCODE -ne 0){throw 'Refund contract failed'}
& sqlcmd -S . -E -I -d $database -b -Q 'ALTER ROLE tc_buyer ADD MEMBER khanh03_buyer; ALTER ROLE tc_platform_admin ADD MEMBER khanh03_admin;'
if($LASTEXITCODE -ne 0){throw 'Restricted roles failed'}
Write-Output 'Dedicated local refund database and restricted roles ready.'
