package vn.ticketscenter.fulfillment.refund.worker;

import vn.ticketscenter.fulfillment.refund.dao.RefundWorkerStore;
import vn.ticketscenter.fulfillment.refund.dto.RefundTransferResult;
import vn.ticketscenter.fulfillment.refund.dto.RefundTransferRequest;
import java.util.*;
import java.math.BigDecimal;
import vn.ticketscenter.fulfillment.refund.cache.RefundLease;import org.junit.jupiter.api.*;
import vn.ticketscenter.payment.integration.RefundGateway;
import static org.junit.jupiter.api.Assertions.*;

class RefundWorkerTest {
 static class Store implements RefundWorkerStore {
  final Candidate item=new Candidate(UUID.randomUUID(),UUID.randomUUID());
  String mode="EXECUTE";boolean claimed;RefundGateway.Result result;String error;boolean failRecord;
  public List<Candidate> candidates(String kind,int limit){return kind.equals(mode)?List.of(item):List.of();}
  public Optional<Claim> claim(Candidate c,UUID token){if(claimed)return Optional.empty();claimed=true;return Optional.of(new Claim(new RefundTransferRequest(item.refundId(),item.attemptId(),new BigDecimal("1000")),null,mode,token));}
  public void record(Claim claim,RefundGateway.Result r,String e){if(failRecord)throw new IllegalStateException("Commit lost");result=r;error=e;}
 }
 static class Gateway implements RefundGateway {
  int submits,queries;UUID submitted,queried;Status status=Status.SUCCEEDED;boolean timeout;
  public Result submit(UUID id,BigDecimal amount)throws Timeout{submits++;submitted=id;if(timeout)throw new Timeout();return new Result(status,"SIM-"+id);}
  public Result query(String ref){throw new AssertionError("Need attempt ID lookup");}
  public RefundTransferResult queryRefundStatus(String ref,UUID id){queries++;queried=id;return new RefundTransferResult(status,"SIM-"+id);}
 }
 static RefundLease available(){return id->Optional.of(()->{});}
 @Test void submitsAttemptIdAfterClaim(){var s=new Store();var g=new Gateway();assertEquals(1,new RefundProcessingWorker(s,g,available(),5).runOnce());assertEquals(s.item.attemptId(),g.submitted);assertNotEquals(s.item.refundId(),g.submitted);assertTrue(s.claimed);assertEquals(RefundGateway.Status.SUCCEEDED,s.result.status());}
 @Test void confirmedFailureIsStoredForExplicitAdminRetry(){var s=new Store();var g=new Gateway();g.status=RefundGateway.Status.FAILED;new RefundProcessingWorker(s,g,available(),5).runOnce();assertEquals(RefundGateway.Status.FAILED,s.result.status());assertEquals(0,new RefundProcessingWorker(s,g,available(),5).runOnce());assertEquals(1,g.submits);}
 @Test void timeoutRecordsUnknownWithoutInventingProviderReference(){var s=new Store();var g=new Gateway();g.timeout=true;new RefundProcessingWorker(s,g,available(),5).runOnce();assertEquals(RefundGateway.Status.UNKNOWN,s.result.status());assertNull(s.result.providerReference());assertEquals("GATEWAY_OUTCOME_UNKNOWN",s.error);}
 @Test void recoveryQueriesSameAttemptWithoutSubmit(){var s=new Store();s.mode="QUERY";var g=new Gateway();var w=new RefundProcessingWorker(s,g,available(),5);assertEquals(0,w.runOnce());assertEquals(1,new RefundReconciliationJob(w).runOnce());assertEquals(0,g.submits);assertEquals(s.item.attemptId(),g.queried);}
 @Test void failedDatabaseRecordDoesNotAutomaticallySubmitAgain(){var s=new Store();var g=new Gateway();s.failRecord=true;var w=new RefundProcessingWorker(s,g,available(),5);assertEquals(0,w.runOnce());assertEquals(0,w.runOnce());assertEquals(1,g.submits);}
 @Test void leaseContentionSkipsWork(){var s=new Store();var g=new Gateway();assertEquals(0,new RefundProcessingWorker(s,g,id->Optional.empty(),5).runOnce());assertFalse(s.claimed);assertEquals(0,g.submits);}
 @Test void validatesVndAndBatch(){assertThrows(IllegalArgumentException.class,()->new RefundTransferRequest(UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("0")));assertThrows(IllegalArgumentException.class,()->new RefundTransferRequest(UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("1.5")));assertThrows(IllegalArgumentException.class,()->new RefundTransferRequest(UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("10000000000000000000")));assertThrows(IllegalArgumentException.class,()->new RefundProcessingWorker(new Store(),new Gateway(),available(),0));}
}
