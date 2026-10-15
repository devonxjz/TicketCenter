package vn.ticketscenter.fulfillment.refund.worker;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.hibernate.Session;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.payment.integration.RefundGateway;
/** Each method commits and closes before gateway I/O. SPs own writes. */
public final class RefundWorkerRepository implements RefundWorkerStore {
 @FunctionalInterface private interface Sql<T>{T run(Connection c)throws SQLException;}
 @FunctionalInterface private interface Transactions{Object run(Sql<?> work);}
 private final Transactions transactions;
 public RefundWorkerRepository(TransactionManager manager){transactions=work->manager.execute(DatabasePrincipal.WORKER,em->em.unwrap(Session.class).doReturningWork(work::run));}
 public RefundWorkerRepository(DataSource worker){transactions=work->{
  try(var c=worker.getConnection()){c.setAutoCommit(false);try{var result=work.run(c);c.commit();return result;}catch(SQLException|RuntimeException e){c.rollback();throw e;}}
  catch(SQLException e){throw new IllegalStateException("Refund worker database operation failed",e);}
 };}
 @SuppressWarnings("unchecked") private <T>T sql(Sql<T> work){return (T)transactions.run(work);}
 @Override public List<Candidate> candidates(String kind,int limit){
  if(!Set.of("EXECUTE","QUERY").contains(kind)||limit<1||limit>1000)throw new IllegalArgumentException("Invalid batch");
  String query="SELECT TOP(?) refund_id,attempt_id FROM dbo.vw_RefundWorkerCandidates WHERE work_kind=? ORDER BY created_at,attempt_id";
  return sql(c->{try(var q=c.prepareStatement(query)){
   q.setQueryTimeout(15);q.setInt(1,limit);q.setString(2,kind);
   try(var r=q.executeQuery()){var rows=new ArrayList<Candidate>();while(r.next())rows.add(new Candidate(UUID.fromString(r.getString(1)),UUID.fromString(r.getString(2))));return List.copyOf(rows);}
  }});
 }
 @Override public Optional<Claim> claim(Candidate item,UUID token){return sql(c->{
  try(var q=c.prepareStatement("EXEC dbo.sp_refund_begin_attempt ?,?,?")){
  q.setQueryTimeout(15);q.setString(1,item.refundId().toString());q.setString(2,item.attemptId().toString());q.setString(3,token.toString());
  boolean rows=q.execute();while(!rows&&q.getUpdateCount()!=-1)rows=q.getMoreResults();if(!rows)return Optional.empty();
  try(var r=q.getResultSet()){if(!r.next())return Optional.empty();return Optional.of(new Claim(new RefundTransferRequest(UUID.fromString(r.getString("refund_id")),UUID.fromString(r.getString("attempt_id")),r.getBigDecimal("amount")),r.getString("provider_reference"),r.getString("work_kind"),UUID.fromString(r.getString("lease_token"))));}
 }});}
 @Override public void record(Claim claim,RefundGateway.Result result,String error){
  Objects.requireNonNull(result);Objects.requireNonNull(result.status());
  sql(c->{try(var q=c.prepareStatement("EXEC dbo.sp_refund_record_attempt_result ?,?,?,?,?")){
   q.setQueryTimeout(20);q.setString(1,claim.request().attemptId().toString());q.setString(2,claim.leaseToken().toString());
   q.setString(3,result.status()==RefundGateway.Status.PENDING?"UNKNOWN":result.status().name());q.setString(4,result.providerReference());q.setString(5,error);
   boolean rows=q.execute();
   while(true){
    if(rows){try(var r=q.getResultSet()){while(r.next()){ /* Consume nested SP11 result before commit. */ }}}
    else if(q.getUpdateCount()==-1)break;
    rows=q.getMoreResults();
   }
   return null;
  }});
 }
}
