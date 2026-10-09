$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
# Run from the repository root. Refuse reuse: never drop/overwrite an existing DB.
$database='TicketCenter_Khanh01_Test'
New-Item -ItemType Directory -Path docs/handoff -Force | Out-Null
$credentialFile=Join-Path (Get-Location) 'docs/handoff/khanh01-local-secrets.xml'
if(Test-Path -LiteralPath $credentialFile){throw 'Fixture credentials already exist; use load-local.ps1'}
$connection=New-Object System.Data.SqlClient.SqlConnection 'Server=.;Database=master;Integrated Security=true;TrustServerCertificate=true'
$connection.Open()
try {
 $command=$connection.CreateCommand()
 $command.CommandText="SELECT DB_ID('$database')"
 if($command.ExecuteScalar() -isnot [DBNull]){throw 'Dedicated database already exists; refusing to overwrite'}
 $secrets=@{}
 foreach($key in @('Owner','CheckIn','Fixture')) {
   $bytes=New-Object byte[] 32
   [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
   $secrets[$key]='K01!'+[Convert]::ToHexString($bytes)
 }
 $command.CommandText="CREATE DATABASE [$database]"
 $command.ExecuteNonQuery() | Out-Null
 foreach($pair in @(@('khanh01_owner','Owner'),@('khanh01_checkin','CheckIn'))) {
   $command.CommandText="CREATE LOGIN [$($pair[0])] WITH PASSWORD=N'$($secrets[$pair[1]])', CHECK_POLICY=ON; USE [$database]; CREATE USER [$($pair[0])] FOR LOGIN [$($pair[0])];"
   $command.ExecuteNonQuery() | Out-Null
 }
 $command.CommandText="USE [$database]; ALTER ROLE db_owner ADD MEMBER khanh01_owner;"
 $command.ExecuteNonQuery() | Out-Null
 $protected=@{}
 foreach($key in $secrets.Keys){$protected[$key]=ConvertTo-SecureString $secrets[$key] -AsPlainText -Force}
 $protected | Export-Clixml -LiteralPath $credentialFile
} finally {$connection.Close()}
& sqlcmd -S . -E -d $database -b -f 65001 -i target/khanh01-model-schema.sql
if($LASTEXITCODE -ne 0){throw 'Model schema failed'}
& sqlcmd -S . -E -d $database -b -f 65001 -i src/test/resources/checkin/local-contract.sql
if($LASTEXITCODE -ne 0){throw 'Check-in contract failed'}
Write-Output 'Dedicated local schema and restricted CHECK_IN principal ready.'
