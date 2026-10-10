param(
    [Parameter(Mandatory=$true)][string]$TomcatHome,
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$SqlcmdPath='sqlcmd',
    [string]$SqlServer='localhost',
    [switch]$SqlAuthentication
)
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
if($env:DB_NAME -notmatch '^TC_Vuong_Test_[A-Za-z0-9_]+$' -or $env:DB_URL -notmatch '^jdbc:sqlserver://(127\.0\.0\.1|localhost)(:[0-9]+)?;') { throw 'Explicit loopback dedicated JDBC fixture required.' }
$ids=@{};foreach($name in @('admin','manager','staff','org','otherOrg','event','formulaEvent','pendingEvent','pendingOrder','pendingPayment','pendingPayout','audit','rule','order','payment')){$ids[$name]=[guid]::NewGuid().ToString()}
$sql=@"
SET NOCOUNT ON;SET XACT_ABORT ON;
IF DB_NAME() NOT LIKE N'TC[_]Vuong[_]Test[_]%' OR NOT EXISTS(SELECT 1 FROM dbo.tc_vuong_test_fixture WHERE fixture_key='vuong-finance-v1') THROW 52790,'Dedicated fixture required',1;
BEGIN TRANSACTION;
INSERT dbo.tc_users(id,email,user_name,full_name,platform_role) VALUES('$($ids.admin)','$($ids.admin)@test.invalid','$($ids.admin)','HTTP Fixture Admin','ADMIN'),('$($ids.manager)','$($ids.manager)@test.invalid','$($ids.manager)','HTTP Fixture Manager','CUSTOMER'),('$($ids.staff)','$($ids.staff)@test.invalid','$($ids.staff)','HTTP Fixture Staff','CUSTOMER');
INSERT dbo.tc_organizations(id,name,contact_email) VALUES('$($ids.org)','HTTP Fixture & <script>fixture</script>','org@test.invalid'),('$($ids.otherOrg)','HTTP Fixture Other','other@test.invalid');
INSERT dbo.tc_organization_memberships(user_id,organization_id,role) VALUES('$($ids.manager)','$($ids.org)','MANAGER'),('$($ids.staff)','$($ids.otherOrg)','CHECK_IN_STAFF');
INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES('$($ids.rule)','$($ids.org)',5,2,'2020-01-01','2099-01-01');
INSERT dbo.tc_events(id,organization_id,commission_rule_id,title,end_time) VALUES('$($ids.event)','$($ids.org)','$($ids.rule)','HTTP Fixture & <script>fixture</script>','2020-01-01');
INSERT dbo.tc_events(id,organization_id,commission_rule_id,title,end_time) VALUES('$($ids.formulaEvent)','$($ids.org)','$($ids.rule)','=1+1','2020-01-01');
INSERT dbo.tc_orders(id,buyer_id,event_id,order_code,status,subtotal_amount,total_amount,paid_at) VALUES('$($ids.order)','$($ids.manager)','$($ids.event)','$($ids.order)','PAID',100,100,'2026-01-01');
INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,paid_at) VALUES('$($ids.payment)','$($ids.order)',100,'CAPTURED','$($ids.payment)','2026-01-01');
UPDATE dbo.tc_orders SET accepted_payment_id='$($ids.payment)' WHERE id='$($ids.order)';
INSERT dbo.tc_audit_logs(id,actor_id,action,entity_type,entity_id,metadata_json,created_at) VALUES('$($ids.audit)','$($ids.admin)','TEST_HTTP','Event','$($ids.event)',N'{"eventId":"$($ids.event)","organizationId":"$($ids.org)","amount":"100","passwordHash":"DO_NOT_EXPOSE","otp":"123456","bankAccountNumber":"PRIVATE_TEST_BANK"}','2026-01-01');
COMMIT;
INSERT dbo.tc_events(id,organization_id,commission_rule_id,title,end_time) VALUES('$($ids.pendingEvent)','$($ids.org)','$($ids.rule)','HTTP Fixture Pending Recovery','2020-01-01');
INSERT dbo.tc_orders(id,buyer_id,event_id,order_code,status,subtotal_amount,total_amount,paid_at) VALUES('$($ids.pendingOrder)','$($ids.manager)','$($ids.pendingEvent)','$($ids.pendingOrder)','PAID',100,100,'2026-01-01');
INSERT dbo.tc_payments(id,order_id,amount,status,txn_ref,paid_at) VALUES('$($ids.pendingPayment)','$($ids.pendingOrder)',100,'CAPTURED','$($ids.pendingPayment)','2026-01-01');
UPDATE dbo.tc_orders SET accepted_payment_id='$($ids.pendingPayment)' WHERE id='$($ids.pendingOrder)';
EXEC dbo.usp_RecalculateSettlement '$($ids.pendingEvent)','$($ids.admin)',0;
DECLARE @pendingSettlement uniqueidentifier=(SELECT id FROM dbo.tc_settlements WHERE event_id='$($ids.pendingEvent)'),@fp binary(32)=HASHBYTES('SHA2_256','HTTP seeded durable result');
EXEC dbo.usp_ConfirmSettlement @pendingSettlement,'$($ids.admin)',0;
EXEC dbo.usp_RecordPayout @pendingSettlement,'$($ids.pendingPayout)','$($ids.admin)',40,NULL,NULL,@fp,0;
INSERT dbo.tc_vuong_simulated_transfers(payout_id,fingerprint,result,provider_reference) VALUES('$($ids.pendingPayout)',@fp,'SUCCEEDED','SIM_HTTP_RECOVERY');
"@
$runId=[guid]::NewGuid().ToString('N');$seedFile=Join-Path $repoRoot "target/finance-http-seed-$runId.sql"
[System.IO.File]::WriteAllText($seedFile,$sql)
$sqlArgs=@('-S',$SqlServer,'-d',$env:DB_NAME,'-C','-l','5','-b','-i',$seedFile)
if($SqlAuthentication){if(-not $env:DB_USER -or -not $env:DB_PASSWORD){throw 'Explicit fixture SQL credentials required'};$env:SQLCMDPASSWORD=$env:DB_PASSWORD;$sqlArgs+=@('-U',$env:DB_USER)}else{$sqlArgs+='-E'}
& $SqlcmdPath @sqlArgs
if($LASTEXITCODE -ne 0){throw 'Finance HTTP fixture seed failed'}
$env:TC_FINANCE_HTTP_IDS=$ids|ConvertTo-Json -Compress
$bytes=[byte[]]::new(32);[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes);$env:TC_FINANCE_HTTP_FIXTURE_TOKEN=[Convert]::ToBase64String($bytes)
$env:PAYOUT_ADAPTER='fixture-simulated';$env:PAYOUT_SIMULATED_OUTCOME='SUCCEEDED';$env:PAYOUT_SIMULATED_TIMEOUTAFTERSIDEEFFECT='false';$env:FINANCE_REDIS_ENABLED='false'
try{& (Join-Path $PSScriptRoot 'swagger-smoke.ps1') -TomcatHome $TomcatHome -JavaHome $JavaHome -FinanceFixture}
finally{$env:TC_FINANCE_HTTP_FIXTURE_TOKEN=$null;$env:TC_FINANCE_HTTP_IDS=$null;$env:SQLCMDPASSWORD=$null}
