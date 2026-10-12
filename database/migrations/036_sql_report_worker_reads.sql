SET NOCOUNT ON; SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER VIEW dbo.vw_ExpiredHolds AS
 SELECT id,user_id,event_id,expires_at FROM dbo.tc_ticket_holds WHERE status='ACTIVE' AND expires_at<=SYSUTCDATETIME();
GO
CREATE OR ALTER VIEW dbo.vw_PaymentsToReconcile AS
 SELECT p.id,p.order_id,p.txn_ref,p.amount,p.status,p.provider_reference,p.created_at,p.captured_at,o.event_id,h.expires_at hold_expires_at
 FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id JOIN dbo.tc_ticket_holds h ON h.id=o.hold_id
 WHERE p.status IN('PENDING','UNKNOWN') AND p.created_at<DATEADD(minute,-5,SYSUTCDATETIME());
GO
CREATE OR ALTER VIEW dbo.vw_GatewayRefundReconciliation AS
 SELECT id,refund_id,payment_id,observed_status,provider_reference,reason,received_at
 FROM dbo.tc_gateway_refund_receipts WHERE resolution='NEEDS_RECONCILIATION';
GO
GRANT SELECT ON dbo.vw_ExpiredHolds TO tc_worker;
GRANT SELECT ON dbo.vw_PaymentsToReconcile TO tc_worker;
GRANT SELECT ON dbo.vw_GatewayRefundReconciliation TO tc_worker,tc_platform_admin;
DENY INSERT,UPDATE,DELETE ON dbo.tc_gateway_refund_receipts TO tc_buyer,tc_manager,tc_checkin,tc_auth,tc_platform_admin,tc_worker;
GO
-- Keep the LIEM runtime facade where present; the fallback has the same changes_payload contract.
IF OBJECT_ID('dbo.usp_ExpireHolds','P') IS NULL EXEC(N'
CREATE PROCEDURE dbo.usp_ExpireHolds @batch_size int=50 AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF COALESCE(IS_ROLEMEMBER(''tc_worker''),0)<>1 AND COALESCE(IS_MEMBER(''db_owner''),0)<>1 THROW 51831,''Worker principal required'',1;
 IF @batch_size IS NULL OR @batch_size NOT BETWEEN 1 AND 100 THROW 51832,''Batch size must be 1..100'',1;
 IF @@TRANCOUNT<>0 THROW 51833,''Expiry facade requires autocommit'',1;
 DECLARE @id uniqueidentifier;
 DECLARE @changes TABLE(actorId uniqueidentifier,eventId uniqueidentifier,holdId uniqueidentifier,version bigint);
 DECLARE holds CURSOR LOCAL FAST_FORWARD FOR SELECT TOP(@batch_size) id FROM dbo.tc_ticket_holds WHERE status=''ACTIVE'' AND expires_at<=SYSUTCDATETIME() ORDER BY event_id,id;
 OPEN holds; FETCH NEXT FROM holds INTO @id;
 WHILE @@FETCH_STATUS=0 BEGIN
  BEGIN TRY
   EXEC dbo.usp_ReleaseTicketHold @hold_id=@id,@actor_id=NULL,@reason=''EXPIRED'',@suppress_result=1;
   INSERT @changes SELECT user_id,event_id,id,version FROM dbo.tc_ticket_holds WHERE id=@id AND status=''RELEASED'';
  END TRY BEGIN CATCH IF ERROR_NUMBER() NOT IN(1205,1222,51273) THROW; END CATCH;
  FETCH NEXT FROM holds INTO @id;
 END;
 CLOSE holds; DEALLOCATE holds;
 SELECT COALESCE((SELECT LOWER(CONVERT(char(36),actorId)) actorId,LOWER(CONVERT(char(36),eventId)) eventId,LOWER(CONVERT(char(36),holdId)) holdId,version FROM @changes FOR JSON PATH),N''[]'') changes_payload;
END;');
GO
GRANT EXECUTE ON dbo.usp_ExpireHolds TO tc_worker;
GO
