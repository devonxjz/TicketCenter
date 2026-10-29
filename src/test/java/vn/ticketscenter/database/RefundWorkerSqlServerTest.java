package vn.ticketscenter.database;

import vn.ticketscenter.fulfillment.refund.dao.RefundWorkerStore;
import vn.ticketscenter.fulfillment.refund.dao.RefundWorkerRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigDecimal;
import javax.sql.DataSource;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
import vn.ticketscenter.fulfillment.refund.cache.RefundLeaseImpl;
import vn.ticketscenter.fulfillment.refund.worker.*;
import vn.ticketscenter.payment.integration.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine SQL locks, restricted worker principal and committed SPs in named loopback fixture. */
@EnabledIfEnvironmentVariable(named="TC_REFUND_WORKER_SQL_TEST",matches="true")
public class RefundWorkerSqlServerTest {
 @TempDir Path directory;
 record Fixture(SqlReportSafetyIT.Fixture base,UUID refund,UUID attempt,UUID reviewer,UUID seat) {}
 static void sql(String command,Object...args)throws Exception{try(var c=ScanFixture.owner()){ScanFixture.execute(c,command,args);}}
 static String value(String command,Object...args)throws Exception{return ScanFixture.scalar(command,args);}
 Fixture fixture(boolean seated,boolean zero)throws Exception{
  var f=SqlReportSafetyIT.fixture();UUID reviewer=UUID.randomUUID(),seat=seated?UUID.randomUUID():null;
  sql("UPDATE dbo.tc_users SET platform_role='CUSTOMER' WHERE id=?;DELETE dbo.tc_user_platform_roles WHERE user_id=?;INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?,'CUSTOMER')",f.user(),f.user(),f.user());
  sql("INSERT dbo.tc_users(id,email,user_name,password_hash,full_name,email_verified_at,platform_role) VALUES(?,?,?,N'test-only',N'KHANH05 Reviewer',SYSUTCDATETIME(),'ADMIN');INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?,'ADMIN')",reviewer,reviewer+"@fixture.invalid",reviewer.toString(),reviewer);
  if(seated){
   sql("UPDATE dbo.tc_events SET status='DRAFT' WHERE id=?",f.event());
   sql("UPDATE dbo.tc_zones SET type='SEATED',capacity=NULL,held_quantity=0 WHERE id=?",f.zone());
   sql("INSERT dbo.tc_seats(id,zone_id,row_name,seat_number,label,status) VALUES(?,?,'A',1,'A1','HELD')",seat,f.zone());
   sql("UPDATE dbo.tc_ticket_hold_items SET seat_id=? WHERE hold_id=?;UPDATE dbo.tc_order_items SET seat_id=? WHERE id=?",seat,f.hold(),seat,f.item());
   sql("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?",f.event());
  }
  if(zero){
   sql("UPDATE dbo.tc_order_items SET unit_price=0 WHERE id=?;UPDATE dbo.tc_orders SET subtotal_amount=0,total_amount=0 WHERE id=?;UPDATE dbo.tc_payments SET status='FAILED' WHERE id=?",f.item(),f.order(),f.payment());
   new vn.ticketscenter.payment.dao.PaymentRepository(PaymentCrossDomainSqlTest.source("buyer",f.user()),PaymentCrossDomainSqlTest.source("worker",null)).completeZeroOrder(f.order(),f.user());
  }else SqlReportSafetyIT.capture(f,f.payment(),f.payment().toString());
  String ticket=value("SELECT CONVERT(varchar(36),id) FROM dbo.tc_tickets WHERE order_item_id=?",f.item());
  int version=Integer.parseInt(value("SELECT auth_version FROM dbo.tc_users WHERE id=?",f.user()));
  try(var c=PaymentCrossDomainSqlTest.source("buyer",f.user()).getConnection()){
   ScanFixture.execute(c,"DECLARE @id uniqueidentifier;EXEC dbo.sp_refund_request_for_tickets ?,?,?,?,@id OUTPUT,?,?",f.order(),f.user(),ticket,"KHANH05 worker acceptance refund",UUID.randomUUID(),version);
  }
  UUID refund=UUID.fromString(value("SELECT CONVERT(varchar(36),id) FROM dbo.tc_refund_obligations WHERE order_id=?",f.order()));
  UUID request=UUID.fromString(value("SELECT CONVERT(varchar(36),legacy_request_id) FROM dbo.tc_refund_obligations WHERE id=?",refund));
  try(var c=PaymentCrossDomainSqlTest.source("admin",reviewer).getConnection()){ScanFixture.execute(c,"EXEC dbo.usp_ReviewRefundRequest ?,?,'APPROVE'",request,reviewer);}
  String attempt=value("SELECT CONVERT(varchar(36),current_attempt_id) FROM dbo.tc_refund_obligations WHERE id=?",refund);
  return new Fixture(f,refund,attempt==null?null:UUID.fromString(attempt),reviewer,seat);
 }
 RefundWorkerRepository repository(){return new RefundWorkerRepository(PaymentCrossDomainSqlTest.source("worker",null));}
 RefundWorkerStore scoped(Fixture f,RefundWorkerRepository repo){return new RefundWorkerStore(){
  public List<Candidate> candidates(String kind,int limit){return repo.candidates(kind,1000).stream().filter(c->c.refundId().equals(f.refund())).toList();}
  public Optional<Claim> claim(Candidate c,UUID token){return repo.claim(c,token);}
  public void record(Claim c,RefundGateway.Result r,String e){repo.record(c,r,e);}
 };}
 RefundProcessingWorker worker(Fixture f,RefundGateway gateway){return new RefundProcessingWorker(scoped(f,repository()),gateway,id->Optional.of(()->{}),50);}
 RefundGatewayImpl gateway(RefundGateway.Status status,boolean timeout){return new RefundGatewayImpl(directory,status,timeout);}
 void due(Fixture f)throws Exception{sql("UPDATE dbo.tc_refund_dispatches SET lease_until=DATEADD(second,-1,SYSUTCDATETIME()),next_query_at=DATEADD(second,-1,SYSUTCDATETIME()) WHERE attempt_id=?",f.attempt());}
 String status(Fixture f)throws Exception{return value("SELECT status FROM dbo.tc_refund_obligations WHERE id=?",f.refund());}
 @Test void WRK01StandingSuccessReturnsInventoryExactlyOnce()throws Exception{
  var f=fixture(false,false);var w=worker(f,gateway(RefundGateway.Status.SUCCEEDED,false));assertEquals(1,w.runOnce());assertEquals("COMPLETED",status(f));
  assertEquals("REFUNDED",value("SELECT status FROM dbo.tc_tickets WHERE order_item_id=?",f.base().item()));assertEquals("0",value("SELECT sold_quantity FROM dbo.tc_zones WHERE id=?",f.base().zone()));
  assertEquals(0,w.runOnce());assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=?",f.refund()));
  assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_outbox WHERE idempotency_key=?","refund-succeeded:"+f.attempt()));
 }
 @Test void WRK01SeatedSuccessReturnsOnlyAllocatedSeat()throws Exception{
  var f=fixture(true,false);worker(f,gateway(RefundGateway.Status.SUCCEEDED,false)).runOnce();assertEquals("COMPLETED",status(f));assertEquals("AVAILABLE",value("SELECT status FROM dbo.tc_seats WHERE id=?",f.seat()));
 }
 @Test void WRK02FailedRequiresExplicitAdminRetryWithNewAttempt()throws Exception{
  var f=fixture(false,false);var w=worker(f,gateway(RefundGateway.Status.FAILED,false));assertEquals(1,w.runOnce());assertEquals("RETRYABLE",status(f));assertEquals("REFUND_PENDING",value("SELECT status FROM dbo.tc_tickets WHERE order_item_id=?",f.base().item()));assertEquals(0,w.runOnce());
  UUID request=UUID.fromString(value("SELECT CONVERT(varchar(36),legacy_request_id) FROM dbo.tc_refund_obligations WHERE id=?",f.refund()));
  try(var c=PaymentCrossDomainSqlTest.source("admin",f.reviewer()).getConnection()){
   assertThrows(SQLException.class,()->ScanFixture.execute(c,"EXEC dbo.usp_ReviewRefundRequest ?,?,'APPROVE'",request,f.reviewer()));
   ScanFixture.execute(c,"EXEC dbo.usp_ReviewRefundRequest ?,?,'APPROVE',NULL,1",request,f.reviewer());
  }
  UUID retry=UUID.fromString(value("SELECT CONVERT(varchar(36),current_attempt_id) FROM dbo.tc_refund_obligations WHERE id=?",f.refund()));assertNotEquals(f.attempt(),retry);
  worker(f,gateway(RefundGateway.Status.SUCCEEDED,false)).runOnce();assertEquals("COMPLETED",status(f));assertEquals("2",value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=?",f.refund()));
 }
 @Test void WRK03TimeoutStaysUnknownUntilSameAttemptIsQueried()throws Exception{
  var f=fixture(false,false);var g=gateway(RefundGateway.Status.SUCCEEDED,true);var w=worker(f,g);assertEquals(1,w.runOnce());assertEquals("NEEDS_RECONCILIATION",status(f));
  assertNull(value("SELECT provider_reference FROM dbo.tc_refund_transfer_logs WHERE id=?",f.attempt()));assertEquals("1",value("SELECT sold_quantity FROM dbo.tc_zones WHERE id=?",f.base().zone()));
  assertEquals(0,w.runOnce());assertEquals(0,new RefundReconciliationJob(w).runOnce());due(f);
  var restarted=worker(f,gateway(RefundGateway.Status.FAILED,false));assertEquals(1,new RefundReconciliationJob(restarted).runOnce());assertEquals("COMPLETED",status(f));assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=?",f.refund()));
 }
 @Test void WRK04ZeroHasNoAttemptPaymentOrGatewayDispatch()throws Exception{
  var f=fixture(false,true);assertEquals("COMPLETED",status(f));assertNull(f.attempt());
  assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=?",f.refund()));assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_payments WHERE order_id=? AND status='CAPTURED'",f.base().order()));
  RefundGateway fail=new RefundGateway(){public Result submit(UUID id,BigDecimal amount){throw new AssertionError("Zero transfer");}public Result query(String r){throw new AssertionError("Zero query");}};
  assertEquals(0,worker(f,fail).runOnce());try(var c=PaymentCrossDomainSqlTest.source("worker",null).getConnection()){ScanFixture.execute(c,"EXEC dbo.sp_refund_complete_without_transfer ?",f.refund());}
 }
 @Test void WRK05CrashAfterGatewayBeforeDatabaseResultRecoversWithoutResubmit()throws Exception{
  var f=fixture(false,false);var repo=repository();var claim=repo.claim(new RefundWorkerStore.Candidate(f.refund(),f.attempt()),UUID.randomUUID()).orElseThrow();assertEquals("EXECUTE",claim.kind());
  gateway(RefundGateway.Status.SUCCEEDED,false).executeRefund(claim.request()); // Crash: no record(), new worker instance.
  assertTrue(repo.claim(new RefundWorkerStore.Candidate(f.refund(),f.attempt()),UUID.randomUUID()).isEmpty());due(f);
  assertEquals(1,new RefundReconciliationJob(worker(f,gateway(RefundGateway.Status.FAILED,false))).runOnce());assertEquals("COMPLETED",status(f));
 }
 @Test void crashBeforeGatewayNeverInventsFailureOrResubmits()throws Exception{
  var f=fixture(false,false);repository().claim(new RefundWorkerStore.Candidate(f.refund(),f.attempt()),UUID.randomUUID()).orElseThrow();due(f);
  var w=worker(f,gateway(RefundGateway.Status.SUCCEEDED,false));assertEquals(0,w.runOnce());assertEquals(1,new RefundReconciliationJob(w).runOnce());assertEquals("NEEDS_RECONCILIATION",status(f));
  try(var files=Files.list(directory)){assertEquals(0,files.filter(p->p.toString().endsWith(".receipt")).count());}
 }
 @Test void twoSqlConnectionsCompeteForOneDurableDispatchWithoutRedis()throws Exception{
  var f=fixture(false,false);var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
  try{Callable<Optional<RefundWorkerStore.Claim>> task=()->{ready.countDown();go.await();return repository().claim(new RefundWorkerStore.Candidate(f.refund(),f.attempt()),UUID.randomUUID());};var a=pool.submit(task);var b=pool.submit(task);assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();assertEquals(1,(a.get(20,TimeUnit.SECONDS).isPresent()?1:0)+(b.get(20,TimeUnit.SECONDS).isPresent()?1:0));}finally{pool.shutdownNow();}
  assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_dispatches WHERE attempt_id=?",f.attempt()));
 }
 @Test void unknownReceiptResolutionNeverAllocatesAnotherAttempt()throws Exception{
  var f=fixture(false,false);var g=gateway(RefundGateway.Status.UNKNOWN,false);var w=worker(f,g);w.runOnce();assertEquals("NEEDS_RECONCILIATION",status(f));
  g.resolve(f.attempt(),RefundGateway.Status.SUCCEEDED);due(f);new RefundReconciliationJob(w).runOnce();assertEquals("COMPLETED",status(f));assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=?",f.refund()));
 }
 @Test void reconciliationConfirmedFailureEnablesExplicitRetryOnly()throws Exception{
  var f=fixture(false,false);var g=gateway(RefundGateway.Status.UNKNOWN,false);var w=worker(f,g);w.runOnce();
  UUID request=UUID.fromString(value("SELECT CONVERT(varchar(36),legacy_request_id) FROM dbo.tc_refund_obligations WHERE id=?",f.refund()));
  try(var c=PaymentCrossDomainSqlTest.source("admin",f.reviewer()).getConnection()){ScanFixture.execute(c,"EXEC dbo.usp_ReviewRefundRequest ?,?,'APPROVE',NULL,1",request,f.reviewer());}
  assertEquals(f.attempt().toString(),value("SELECT CONVERT(varchar(36),current_attempt_id) FROM dbo.tc_refund_obligations WHERE id=?",f.refund()).toLowerCase());
  g.resolve(f.attempt(),RefundGateway.Status.FAILED);due(f);new RefundReconciliationJob(w).runOnce();assertEquals("RETRYABLE",status(f));assertEquals("GATEWAY_DECLINED",value("SELECT error_message FROM dbo.tc_refund_attempts WHERE attempt_id=?",f.attempt()));assertEquals(0,w.runOnce());
 }
 @Test void workerRoleCannotApproveCustomerRefundOrWriteTablesDirectly()throws Exception{
  var f=fixture(false,false);try(var c=PaymentCrossDomainSqlTest.source("worker",null).getConnection()){
   assertThrows(SQLException.class,()->ScanFixture.execute(c,"UPDATE dbo.tc_refund_dispatches SET lease_token=NULL WHERE attempt_id=?",f.attempt()));
   assertThrows(SQLException.class,()->ScanFixture.execute(c,"UPDATE dbo.tc_tickets SET status='REFUNDED' WHERE order_item_id=?",f.base().item()));
   UUID request=UUID.fromString(value("SELECT CONVERT(varchar(36),legacy_request_id) FROM dbo.tc_refund_obligations WHERE id=?",f.refund()));
   assertThrows(SQLException.class,()->ScanFixture.execute(c,"EXEC dbo.usp_ReviewRefundRequest ?,?,'APPROVE'",request,f.reviewer()));
  }
  try(var c=PaymentCrossDomainSqlTest.source("buyer",f.base().user()).getConnection()){assertThrows(SQLException.class,()->ScanFixture.execute(c,"EXEC dbo.sp_refund_begin_attempt ?,?,?",f.refund(),f.attempt(),UUID.randomUUID()));}
 }
 @Test void gatewayIoHoldsNoWorkerConnectionAndSeesCommittedClaim()throws Exception{
  var f=fixture(false,false);var open=new AtomicInteger();var raw=PaymentCrossDomainSqlTest.source("worker",null);
  DataSource metered=(DataSource)java.lang.reflect.Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class<?>[]{DataSource.class},(p,m,a)->{
   if(!m.getName().equals("getConnection"))throw new UnsupportedOperationException();Connection c=raw.getConnection();open.incrementAndGet();
   return java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p2,m2,a2)->{if(m2.getName().equals("close")){try{c.close();}finally{open.decrementAndGet();}return null;}try{return m2.invoke(c,a2);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});
  });
  var g=new RefundGateway(){public Result submit(UUID id,BigDecimal amount){assertEquals(0,open.get());try{assertEquals("1",value("SELECT COUNT(*) FROM dbo.tc_refund_dispatches WHERE attempt_id=?",id));}catch(Exception e){throw new AssertionError(e);}return new Result(Status.SUCCEEDED,"SIM-"+id);}public Result query(String ref){throw new AssertionError();}};
  assertEquals(1,new RefundProcessingWorker(scoped(f,new RefundWorkerRepository(metered)),g,id->Optional.of(()->{}),50).runOnce());assertEquals(0,open.get());assertEquals("COMPLETED",status(f));
 }
 @Test void failedResultTransactionRollsBackFinancialWritesAndCanReconcile()throws Exception{
  var f=fixture(false,false);var repo=repository();var claim=repo.claim(new RefundWorkerStore.Candidate(f.refund(),f.attempt()),UUID.randomUUID()).orElseThrow();
  try(var c=ScanFixture.owner()){c.setAutoCommit(false);ScanFixture.execute(c,"EXEC dbo.sp_refund_record_attempt_result ?,?,'SUCCEEDED',?",f.attempt(),claim.leaseToken(),"SIM-"+f.attempt());assertEquals("COMPLETED",scalar(c,"SELECT status FROM dbo.tc_refund_obligations WHERE id=?",f.refund()));c.rollback();}
  assertEquals("PROCESSING",status(f));assertEquals("REFUND_PENDING",value("SELECT status FROM dbo.tc_tickets WHERE order_item_id=?",f.base().item()));assertEquals("1",value("SELECT sold_quantity FROM dbo.tc_zones WHERE id=?",f.base().zone()));
  repo.record(claim,new RefundGateway.Result(RefundGateway.Status.SUCCEEDED,"SIM-"+f.attempt()),null);assertEquals("COMPLETED",status(f));
 }
 static String scalar(Connection c,String s,Object id)throws Exception{try(var q=c.prepareStatement(s)){q.setString(1,id.toString());try(var r=q.executeQuery()){r.next();return r.getString(1);}}}
 @Test void staleLeaseCannotFinalizeAfterAnotherWorkerClaimsQuery()throws Exception{
  var f=fixture(false,false);var repo=repository();var old=repo.claim(new RefundWorkerStore.Candidate(f.refund(),f.attempt()),UUID.randomUUID()).orElseThrow();due(f);var current=repo.claim(new RefundWorkerStore.Candidate(f.refund(),f.attempt()),UUID.randomUUID()).orElseThrow();
  assertEquals("QUERY",current.kind());assertThrows(IllegalStateException.class,()->repo.record(old,new RefundGateway.Result(RefundGateway.Status.SUCCEEDED,"SIM-"+f.attempt()),null));assertEquals("PROCESSING",status(f));repo.record(current,new RefundGateway.Result(RefundGateway.Status.SUCCEEDED,"SIM-"+f.attempt()),null);assertEquals("COMPLETED",status(f));
 }
 @Test void compensationProcessesAcceptedExtraCaptureWithoutTouchingTickets()throws Exception{
  var base=SqlReportSafetyIT.fixture();SqlReportSafetyIT.capture(base,base.payment(),base.payment().toString());UUID extra=UUID.randomUUID();sql("INSERT dbo.tc_payments(id,order_id,txn_ref,amount) VALUES(?,?,?,1000)",extra,base.order(),extra.toString());SqlReportSafetyIT.capture(base,extra,extra.toString());
  UUID attempt=UUID.fromString(value("SELECT CONVERT(varchar(36),attempt_id) FROM dbo.vw_RefundAttemptLedger WHERE payment_id=?",extra));var f=new Fixture(base,attempt,attempt,base.user(),null);
  assertEquals(1,worker(f,gateway(RefundGateway.Status.SUCCEEDED,false)).runOnce());assertEquals("COMPLETED",status(f));assertEquals("ACTIVE",value("SELECT status FROM dbo.tc_tickets WHERE order_item_id=?",base.item()));assertEquals("1",value("SELECT sold_quantity FROM dbo.tc_zones WHERE id=?",base.zone()));
 }
 @Test void twoWorkersWithRedisDownExecuteGatewayOnce()throws Exception{
  var f=fixture(false,false);var barrier=new CyclicBarrier(2);var calls=new AtomicInteger();var repo=repository();var filtered=scoped(f,repo);
  var racing=new RefundWorkerStore(){
   public List<Candidate> candidates(String kind,int limit){var list=filtered.candidates(kind,limit);try{barrier.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}return list;}
   public Optional<Claim> claim(Candidate c,UUID t){return filtered.claim(c,t);}
   public void record(Claim c,RefundGateway.Result r,String e){filtered.record(c,r,e);}
  };
  var g=new RefundGateway(){public Result submit(UUID id,BigDecimal amount){calls.incrementAndGet();return new Result(Status.SUCCEEDED,"SIM-"+id);}public Result query(String ref){throw new AssertionError();}};
  var lease=new RefundLeaseImpl(()->{throw new IllegalStateException("Fixture Redis outage");});var pool=Executors.newFixedThreadPool(2);
  try{var a=pool.submit(()->new RefundProcessingWorker(racing,g,lease,50).runOnce());var b=pool.submit(()->new RefundProcessingWorker(racing,g,lease,50).runOnce());assertEquals(1,a.get(25,TimeUnit.SECONDS)+b.get(25,TimeUnit.SECONDS));assertEquals(1,calls.get());assertEquals("COMPLETED",status(f));}finally{pool.shutdownNow();}
 }
 @Test void injectedFailureAfterInventoryMutationRollsBackAndRestartQueriesReceipt()throws Exception{
  var f=fixture(false,false);String name="khanh05_failure_"+f.attempt().toString().replace("-","");
  sql("CREATE TRIGGER dbo."+name+" ON dbo.tc_outbox AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE aggregate_id='"+f.attempt()+"') THROW 53999,'Fixture failure after inventory return',1; END");
  try{assertEquals(0,worker(f,gateway(RefundGateway.Status.SUCCEEDED,false)).runOnce());assertEquals("PROCESSING",status(f));assertEquals("REFUND_PENDING",value("SELECT status FROM dbo.tc_tickets WHERE order_item_id=?",f.base().item()));assertEquals("1",value("SELECT sold_quantity FROM dbo.tc_zones WHERE id=?",f.base().zone()));assertEquals("0",value("SELECT COUNT(*) FROM dbo.tc_gateway_refund_receipts WHERE refund_id=?",f.attempt()));}
  finally{sql("DROP TRIGGER dbo."+name);}
  due(f);assertEquals(1,new RefundReconciliationJob(worker(f,gateway(RefundGateway.Status.FAILED,false))).runOnce());assertEquals("COMPLETED",status(f));
 }
 @Test void jpaWorkerAndExistingSchedulerEntryPointUseActualStoredProcedures()throws Exception{
  var f=fixture(false,false);
  var delegate=PaymentCrossDomainSqlTest.source("worker",null);
  DataSource source=(DataSource)java.lang.reflect.Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class<?>[]{DataSource.class},(p,m,a)->{
   if(m.getName().equals("toString"))return "KHANH05 restricted worker fixture";
   if(m.getName().equals("getConnection"))return delegate.getConnection();
   if(m.getName().equals("isWrapperFor"))return false;
   throw new UnsupportedOperationException(m.getName());
  });
  try(var factory=jakarta.persistence.Persistence.createEntityManagerFactory("ticketscenter",Map.of("jakarta.persistence.nonJtaDataSource",source,"hibernate.hbm2ddl.auto","none"))){
   var tx=new vn.ticketscenter.config.persistence.TransactionManager(Map.of(vn.ticketscenter.config.persistence.DatabasePrincipal.WORKER,factory));
   var worker=new RefundProcessingWorker(scoped(f,new RefundWorkerRepository(tx)),gateway(RefundGateway.Status.SUCCEEDED,false),id->Optional.of(()->{}),50);
   assertEquals(1,new vn.ticketscenter.config.worker.RefundJob(worker).runOnce());assertEquals("COMPLETED",status(f));
  }
 }
}
