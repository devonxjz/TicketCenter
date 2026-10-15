/* 060: admin retry of a failed payment compensation, and neutral names for two shared read models.
   - usp_RetryPaymentCompensation opens a new PAYMENT_COMPENSATION obligation with one PENDING attempt, exactly
     as usp_ApplyPaymentResult does with @retry_failed_compensation=1; the refund worker then executes it.
     THROW numbers: 52810 ADMIN_REQUIRED, 52811 PAYMENT_NOT_FOUND, 52812 COMPENSATION_NOT_RETRYABLE.
   - vw_AuditLogs / vw_EventCheckinCounts carry the definitions of vw_VuongAuditLogs / vw_VuongEventCheckinCounts.
     The old names stay untouched because other branches still read them; drop them once nothing does. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER PROCEDURE dbo.usp_RetryPaymentCompensation
 @payment_id uniqueidentifier,@actor_id uniqueidentifier,@auth_version int=NULL
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 DECLARE @started bit=CASE WHEN @@TRANCOUNT=0 THEN 1 ELSE 0 END;
 IF @started=1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp_comp_retry;
 BEGIN TRY
  IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WITH(HOLDLOCK) WHERE id=@actor_id AND status='ACTIVE' AND platform_role='ADMIN'
                AND (@auth_version IS NULL OR auth_version=@auth_version)) THROW 52810,'ADMIN_REQUIRED',1;
  DECLARE @order_id uniqueidentifier,@amount decimal(19,0),@id uniqueidentifier=NEWID();
  SELECT @order_id=order_id,@amount=amount FROM dbo.tc_payments WITH(UPDLOCK,HOLDLOCK) WHERE id=@payment_id AND status='CAPTURED';
  IF @order_id IS NULL THROW 52811,'PAYMENT_NOT_FOUND',1;
  /* Only a payment whose every compensation attempt failed qualifies; an open or successful one blocks the retry. */
  IF NOT EXISTS(SELECT 1 FROM dbo.vw_FailedCompensationAttempts WHERE payment_id=@payment_id) THROW 52812,'COMPENSATION_NOT_RETRYABLE',1;
  INSERT dbo.tc_refund_obligations(id,order_id,payment_id,amount,purpose,status,current_attempt_id)
  VALUES(@id,@order_id,@payment_id,@amount,'PAYMENT_COMPENSATION','PROCESSING',@id);
  INSERT dbo.tc_refund_transfer_logs(id,refund_id,attempt_no,status,result) VALUES(@id,@id,1,'PENDING','PENDING');
  INSERT dbo.tc_audit_logs(actor_id,action,aggregate_type,aggregate_id,detail)
  VALUES(@actor_id,'PAYMENT_COMPENSATION_RETRY','PAYMENT',@payment_id,CONCAT(N'{"compensationId":"',@id,N'"}'));
  IF @started=1 COMMIT TRANSACTION;
  SELECT @id compensation_id,@payment_id payment_id,CAST('PENDING' AS varchar(20)) status;
 END TRY BEGIN CATCH
  IF @started=1 AND XACT_STATE()<>0 ROLLBACK TRANSACTION;
  ELSE IF @started=0 AND XACT_STATE()=1 ROLLBACK TRANSACTION tc_sp_comp_retry;
  THROW;
 END CATCH
END;
GO
CREATE OR ALTER VIEW dbo.vw_AuditLogs AS
 SELECT CONVERT(uniqueidentifier,CONVERT(binary(16),id)) id,actor_id,action,aggregate_type entity_type,
        CONVERT(varchar(100),aggregate_id) entity_id,detail metadata_json,created_at,created_at updated_at
 FROM dbo.tc_audit_logs;
GO
CREATE OR ALTER VIEW dbo.vw_EventCheckinCounts AS
 SELECT o.event_id,COUNT_BIG(DISTINCT t.id) tickets_checked_in_count
 FROM dbo.tc_ticket_check_in_logs l JOIN dbo.tc_tickets t ON t.id=l.ticket_id
 JOIN dbo.tc_order_items i ON i.id=t.order_item_id JOIN dbo.vw_VuongOrders o ON o.id=i.order_id
 WHERE l.result='SUCCESS' AND l.event_id=o.event_id GROUP BY o.event_id;
GO
GRANT EXECUTE ON dbo.usp_RetryPaymentCompensation TO tc_platform_admin;
GRANT SELECT ON dbo.vw_AuditLogs TO tc_platform_admin;
GRANT SELECT ON dbo.vw_EventCheckinCounts TO tc_manager, tc_platform_admin;
DENY SELECT ON dbo.vw_EventCheckinCounts TO tc_buyer, tc_checkin;
GO
