param([string]$Baseline='docs/handoff/database-backups/sql-report-20261011-215314/before.json')
$ErrorActionPreference='Stop'
$settings=@{}
Get-Content src/main/resources/application.properties|ForEach-Object{if($_ -match '^(db\.(?:host|name|user|password))=(.*)$'){$settings[$Matches[1]]=$Matches[2].Trim()}}
if($settings['db.host'] -ne 'devonxjz.database.windows.net' -or $settings['db.name'] -ne 'TicketCenterticke'){throw 'Unexpected database'}
$b=[System.Data.SqlClient.SqlConnectionStringBuilder]::new();$b['Data Source']='tcp:'+$settings['db.host']+',1433';$b['Initial Catalog']=$settings['db.name'];$b['User ID']=$settings['db.user'];$b['Password']=$settings['db.password'];$b['Encrypt']=$true;$b['TrustServerCertificate']=$false;$b['Connect Timeout']=60;$b['Pooling']=$false
$cn=[System.Data.SqlClient.SqlConnection]::new($b.ConnectionString)
function Scalar($sql){$q=$cn.CreateCommand();$q.CommandText=$sql;$q.CommandTimeout=30;try{return $q.ExecuteScalar()}finally{$q.Dispose()}}
function Run($sql){$q=$cn.CreateCommand();$q.CommandText=$sql;try{[void]$q.ExecuteNonQuery()}finally{$q.Dispose()}}
function Require($value,$expected,$name){if($value -ne $expected){throw ('Verification failed: '+$name+'; actual='+$value+'; expected='+$expected)};return [pscustomobject]@{check=$name;result='PASS'}}
try{
 $cn.Open();$results=@()
 foreach($file in Get-ChildItem database/migrations -Filter '03*_sql_report_*.sql'){
  $v=$file.BaseName;$actual=Scalar "SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version='$v'"
  $raw=[IO.File]::ReadAllText($file.FullName);$lf=$raw.Replace("`r`n","`n")
  $hashes=@((Get-FileHash $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant())
  foreach($variant in @($lf,$lf.Replace("`n","`r`n"))){$sha=[Security.Cryptography.SHA256]::Create();try{$hashes+=[BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($variant))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}}
  $results+=Require ($hashes -contains $actual) $true ('ledger:'+ $v)
 }
 $results+=Require (Scalar "SELECT COUNT(*) FROM sys.security_policies WHERE name IN('SqlReportTenantPolicy','SqlReportCouponPolicy') AND is_enabled=1") 2 'RLS enabled'
 $results+=Require (Scalar "SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN('FK_OrderItem_OrderEvent_Report','FK_OrderItem_ZoneEvent_Report','FK_OrderItem_SeatZone_Report','FK_Ticket_Seat_Report') AND is_disabled=0 AND is_not_trusted=0") 4 'Composite/seat FKs trusted'
 $results+=Require (Scalar "SELECT COUNT(*) FROM dbo.tc_order_items i JOIN dbo.tc_orders o ON o.id=i.order_id WHERE i.event_id IS NULL OR i.event_id<>o.event_id") 0 'Order-item event backfill valid'
 $results+=Require (Scalar "SELECT COUNT(*) FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id WHERE (t.seat_id IS NULL AND i.seat_id IS NOT NULL) OR t.seat_id<>i.seat_id") 0 'Ticket seat backfill valid'
 $results+=Require (Scalar "SELECT COUNT(*) FROM sys.triggers WHERE name='trg_SettlementItem_MirrorDelete' AND is_disabled=0") 1 'Settlement mirror delete active'
 $roles=@{
  tc_manager_user=@(@('dbo.tc_events','UPDATE',0),@('dbo.tc_zones','UPDATE',0),@('dbo.tc_seats','UPDATE',0),@('dbo.tc_organization_memberships','INSERT',0),@('dbo.tc_audit_logs','INSERT',0),@('dbo.usp_SaveEventDraft','EXECUTE',1),@('dbo.usp_AddMember','EXECUTE',1))
  tc_auth_user=,@('dbo.tc_user_platform_roles','INSERT',0)
  tc_buyer_user=@(@('dbo.vw_CouponUsage','SELECT',0),@('dbo.usp_ApplyPaymentResult','EXECUTE',0),@('dbo.usp_CompleteFreeOrder','EXECUTE',1))
  tc_admin_user=@(@('dbo.tc_audit_logs','INSERT',0),@('dbo.usp_CalculateEventSettlement','EXECUTE',0),@('dbo.usp_ConfirmEventSettlement','EXECUTE',0),@('dbo.usp_ExecutePayout','EXECUTE',0),@('dbo.usp_RecalculateSettlement','EXECUTE',1))
  tc_worker_user=@(@('dbo.usp_ReviewRefundRequest','EXECUTE',0),@('dbo.usp_DecideRefundRequest','EXECUTE',0),@('dbo.usp_ApplyPaymentResult','EXECUTE',1),@('dbo.usp_ApplyRefundResult','EXECUTE',1),@('dbo.usp_ExpireHolds','EXECUTE',1),@('dbo.vw_PaymentsToReconcile','SELECT',1))
  tc_checkin_user=@(@('dbo.sp_ticket_check_in','EXECUTE',1),@('dbo.sp_checkin_recent_logs','EXECUTE',1),@('dbo.tc_orders','UPDATE',0))
 }
 foreach($user in $roles.Keys){
  Run "EXECUTE AS USER = '$user'"
  try{
   foreach($check in $roles[$user]){$results+=Require (Scalar "SELECT HAS_PERMS_BY_NAME('$($check[0])','OBJECT','$($check[1])')") $check[2] ($user+':'+$check[1]+':'+$check[0])}
   if($user -eq 'tc_auth_user'){
    foreach($column in @('platform_role','status')){$results+=Require (Scalar "SELECT HAS_PERMS_BY_NAME('dbo.tc_users','OBJECT','UPDATE','$column','COLUMN')") 0 ('auth protected:'+ $column)}
    $results+=Require (Scalar "SELECT HAS_PERMS_BY_NAME('dbo.tc_users','OBJECT','UPDATE','password_hash','COLUMN')") 1 'auth password update preserved'
   }
   if($user -in @('tc_buyer_user','tc_manager_user')){
    Run "EXEC sys.sp_set_session_context @key=N'actor_id',@value=NULL"
    $results+=Require (Scalar 'SELECT COUNT(*) FROM dbo.vw_TicketDetails') 0 ($user+':unset actor fails closed')
   }
  }finally{Run 'REVERT'}
 }
 $first=Get-Content -Raw -LiteralPath $Baseline|ConvertFrom-Json
 foreach($entry in @(@('tc_tickets','ticket_fields'),@('tc_otps','otp_fields'))){
  foreach($row in $first.($entry[1])){
   $id=[guid]$row.id
   $results+=Require (Scalar "SELECT COUNT(*) FROM dbo.[$($entry[0])] WHERE id='$id'") 1 ('Original ID retained:'+ $entry[0]+':'+$id)
  }
 }
 $counts=@()
 foreach($table in $first.counts){
  if($table.table_name -eq 'tc_schema_migrations'){continue}
  $name=$table.table_name.Replace(']',']]')
  $actual=Scalar "SELECT COUNT_BIG(*) FROM dbo.[$name]"
  $counts += [pscustomobject]@{table=$table.table_name;current_exact_count=$actual;baseline_count=$table.row_count;baseline_exact=($first.count_method -eq 'COUNT_BIG')}
  # The original backup used sys.partitions.rows (an estimate). Do not label it an exact row-preservation assertion.
  if($first.count_method -eq 'COUNT_BIG'){$results+=Require $actual $table.row_count ('Rows preserved:'+ $table.table_name)}
 }
 $counts|ConvertTo-Json -Depth 4|Set-Content target/sql-report-row-counts.json -Encoding UTF8
 $results|ConvertTo-Json -Depth 4|Set-Content target/sql-report-azure-verification.json -Encoding UTF8
 Write-Output ($results.Count.ToString()+' read-only Azure checks passed: hashes, RLS, effective roles, FKs and original ticket/OTP IDs. Exact current counts saved separately.')
}finally{$cn.Dispose()}
