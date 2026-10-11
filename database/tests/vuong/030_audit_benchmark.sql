-- Only a NEW marked Bench_* fixture; runner refuses existing audit rows/index.
SET XACT_ABORT ON;
BEGIN TRAN;
IF EXISTS(SELECT 1 FROM dbo.tc_audit_logs) OR EXISTS(SELECT 1 FROM sys.indexes WHERE name='IX_Acceptance_Audit_Action_Date')
 THROW 52910,'Benchmark requires empty audit history and no candidate index.',1;
INSERT dbo.tc_users(id,email,user_name,full_name,password_hash,platform_role)
 VALUES('00000000-0000-0000-0000-000000000101','bench@test.invalid','acceptance_bench','Synthetic benchmark','TEST_ONLY_NOT_A_PASSWORD','ADMIN');
;WITH numbers AS(SELECT TOP(20000) ROW_NUMBER() OVER(ORDER BY a.object_id,b.object_id) n FROM sys.all_objects a CROSS JOIN sys.all_objects b)
INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail,created_at)
 SELECT CASE WHEN n%2=0 THEN CONVERT(uniqueidentifier,'00000000-0000-0000-0000-000000000101') END,
 CASE WHEN n%10=0 THEN 'SETTLEMENT_CONFIRMED' ELSE 'BENCH_SYNTHETIC' END,'Event','00000000-0000-0000-0000-000000000201',
 N'{"eventId":"00000000-0000-0000-0000-000000000201","grossRevenue":"100"}',DATEADD(second,n,CONVERT(datetime2,'2026-01-01')) FROM numbers;
COMMIT;
