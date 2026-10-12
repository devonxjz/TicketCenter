package vn.ticketscenter.config.worker;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.repository.RefundRepository;
import vn.ticketscenter.fulfillment.refund.worker.*;
import vn.ticketscenter.payment.integration.RefundGateway;
/** Existing scheduler entry point; new refund logic stays in its owned package. */
public final class RefundJob {
 private final RefundProcessingWorker processing;
 private final RefundReconciliationJob reconciliation;
 public RefundJob(TransactionManager transactions,RefundRepository legacyRepository,RefundGateway gateway,int batchSize){
  this(new RefundProcessingWorker(new RefundWorkerRepository(transactions),gateway,new RedisRefundLease(),batchSize));
 }
 public RefundJob(RefundProcessingWorker processing){this.processing=java.util.Objects.requireNonNull(processing);this.reconciliation=new RefundReconciliationJob(processing);}
 public int runOnce(){return processing.runOnce()+reconciliation.runOnce();}
}
