param([Alias('RuntimeReadsOnly')][switch]$RuntimeUpdatesOnly)
$ErrorActionPreference='Stop'
# Fixed disposable local target. Never accepts shared database configuration.
if(-not(Test-Path -LiteralPath 'docs/handoff/khanh02-local-secrets.xml')) { throw 'Create the dedicated local check-in fixture first.' }
$connection=[System.Data.SqlClient.SqlConnection]::new('Server=.;Database=TicketCenter_Khanh02_Test;Integrated Security=true;TrustServerCertificate=true')
$transaction=$null
try {
 $connection.Open();$transaction=$connection.BeginTransaction();$q=$connection.CreateCommand();$q.Transaction=$transaction;$q.CommandTimeout=60
 $q.CommandText='SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON;';[void]$q.ExecuteNonQuery()
 $q.CommandText="SELECT COUNT(*) FROM sys.security_policies WHERE name='SqlReportTenantPolicy' AND is_enabled=1"
 $installed=[int]$q.ExecuteScalar()
 if($RuntimeUpdatesOnly -and $installed -ne 1){throw 'Install SQL report migrations first.'}
 if(-not $RuntimeUpdatesOnly -and $installed -ne 0){throw 'SQL report fixture already installed; use -RuntimeUpdatesOnly for additive runtime migrations.'}
 $files=@('029_hold_runtime.sql','029_khanh03_refund_request.sql','030_order_runtime.sql')
 $files+=@(Get-ChildItem database/migrations -Filter '03*_sql_report_*.sql' | Sort-Object Name | Select-Object -ExpandProperty Name)
 $files+=@('040_payment_ticket_runtime_reads.sql','041_hold_refund_runtime_compatibility.sql','042_checkin_event_user_lock_order.sql')
 if($RuntimeUpdatesOnly){$files=@('040_payment_ticket_runtime_reads.sql','041_hold_refund_runtime_compatibility.sql','042_checkin_event_user_lock_order.sql')}
 foreach($file in $files) {
  foreach($batch in [regex]::Split([IO.File]::ReadAllText((Join-Path (Get-Location) "database/migrations/$file")),'(?im)^\s*GO\s*\r?$')) {
   if($batch.Trim()){$q.CommandText=$batch;[void]$q.ExecuteNonQuery()}
  }
 }
 foreach($role in @('manager','auth','worker','buyer','admin')) {
  $dbRole=if($role -eq 'admin'){'tc_platform_admin'}else{'tc_'+$role}
  $q.CommandText="IF USER_ID('sql_report_$role') IS NULL CREATE USER sql_report_$role WITHOUT LOGIN; ALTER ROLE $dbRole ADD MEMBER sql_report_$role;"
  [void]$q.ExecuteNonQuery()
 }
 $transaction.Commit();$transaction.Dispose();$transaction=$null
 Write-Output 'Dedicated Native SQL report fixture ready.'
} catch {if($transaction){$transaction.Rollback();$transaction.Dispose()};throw} finally {$connection.Dispose()}
