$ErrorActionPreference='Stop'
$database='TicketCenter_Khanh02_Test'
$file='docs/handoff/khanh02-local-secrets.xml'
if(Test-Path -LiteralPath $file){throw 'Fixture exists; use load-local.ps1 instead.'}
[void](New-Item -ItemType Directory -Force docs/handoff)
$cn=[System.Data.SqlClient.SqlConnection]::new('Server=.;Database=master;Integrated Security=true;TrustServerCertificate=true')
try {
 $cn.Open();$cmd=$cn.CreateCommand();$cmd.CommandText="SELECT DB_ID('$database')"
 if($cmd.ExecuteScalar() -isnot [DBNull]){throw 'Refusing to overwrite an existing database.'}
 $cmd.CommandText="CREATE DATABASE [$database]";[void]$cmd.ExecuteNonQuery()
 $protected=@{}
 foreach($role in @('owner','checkin')){
  $bytes=[byte[]]::new(32);[Security.Cryptography.RandomNumberGenerator]::Fill($bytes);$password='K02!'+[Convert]::ToHexString($bytes)
  $cmd.CommandText="CREATE LOGIN [khanh02_$role] WITH PASSWORD=N'$password',CHECK_POLICY=ON; USE [$database]; CREATE USER [khanh02_$role] FOR LOGIN [khanh02_$role];"
  [void]$cmd.ExecuteNonQuery();$protected[$role]=ConvertTo-SecureString $password -AsPlainText -Force
 }
 $cmd.CommandText="USE [$database]; ALTER ROLE db_owner ADD MEMBER khanh02_owner;";[void]$cmd.ExecuteNonQuery()
 foreach($key in @('signing','fixture')){$bytes=[byte[]]::new(32);[Security.Cryptography.RandomNumberGenerator]::Fill($bytes);$protected[$key]=ConvertTo-SecureString ('K02!'+[Convert]::ToHexString($bytes)) -AsPlainText -Force}
 $protected | Export-Clixml -LiteralPath $file
}finally{$cn.Dispose()}
& ./src/test/resources/checkin/khanh02/apply-base-local.ps1
& sqlcmd -S . -E -I -d $database -b -f 65001 -i database/migrations/030_khanh02_signed_checkin.sql
if($LASTEXITCODE -ne 0){throw 'KHANH-02 migration failed'}
& sqlcmd -S . -E -I -d $database -b -Q 'ALTER ROLE tc_checkin ADD MEMBER khanh02_checkin; GRANT VIEW DEFINITION TO khanh02_checkin;'
if($LASTEXITCODE -ne 0){throw 'Restricted fixture role failed'}
Write-Output 'Dedicated local KHANH-02 database, SQL checks and restricted staff principal ready.'
