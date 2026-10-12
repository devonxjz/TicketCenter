package vn.ticketscenter.fulfillment.refund.worker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.math.BigDecimal;
import vn.ticketscenter.payment.integration.*;
import static org.junit.jupiter.api.Assertions.*;
class WorkerCrashRecoveryTest {
 @TempDir Path directory;
 @Test void simulatorSavesRealOutcomeBeforeTimeout()throws Exception{
  var id=UUID.randomUUID();var gateway=new SimulatedRefundGateway(directory,RefundGateway.Status.SUCCEEDED,true);
  assertThrows(RefundGateway.Timeout.class,()->gateway.submit(id,new BigDecimal("1000")));
  var restarted=new SimulatedRefundGateway(directory,RefundGateway.Status.FAILED,false);
  assertEquals(RefundGateway.Status.SUCCEEDED,restarted.queryRefundStatus(null,id).status());
  assertEquals(RefundGateway.Status.SUCCEEDED,restarted.submit(id,new BigDecimal("1000.00")).status());
  try(var files=Files.list(directory)){assertEquals(1,files.filter(p->p.toString().endsWith(".receipt")).count());}
 }
 @Test void unresolvedOutcomeCanBeQueriedAndResolved()throws Exception{
  var id=UUID.randomUUID();var gateway=new SimulatedRefundGateway(directory,RefundGateway.Status.UNKNOWN,false);
  assertEquals(RefundGateway.Status.UNKNOWN,gateway.submit(id,new BigDecimal("1")).status());gateway.resolve(id,RefundGateway.Status.SUCCEEDED);
  assertEquals(RefundGateway.Status.SUCCEEDED,new SimulatedRefundGateway(directory,RefundGateway.Status.UNKNOWN,false).queryRefundStatus(null,id).status());
  assertThrows(IllegalArgumentException.class,()->gateway.resolve(id,RefundGateway.Status.FAILED));
 }
 @Test void missingReceiptStaysUnknownWithoutAnotherTransfer()throws Exception{var gateway=new SimulatedRefundGateway(directory,RefundGateway.Status.SUCCEEDED,false);assertEquals(RefundGateway.Status.UNKNOWN,gateway.queryRefundStatus(null,UUID.randomUUID()).status());}
 @Test void sameAttemptWithDifferentAmountIsRejected()throws Exception{var id=UUID.randomUUID();var gateway=new SimulatedRefundGateway(directory,RefundGateway.Status.SUCCEEDED,false);gateway.submit(id,new BigDecimal("1"));assertThrows(IllegalArgumentException.class,()->gateway.submit(id,new BigDecimal("2")));}
 @Test void concurrentSimulatorSubmissionsShareOneReceipt()throws Exception{
  var id=UUID.randomUUID();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
  try{var a=pool.submit(()->{gate.await();return new SimulatedRefundGateway(directory,RefundGateway.Status.SUCCEEDED,false).submit(id,new BigDecimal("1000"));});var b=pool.submit(()->{gate.await();return new SimulatedRefundGateway(directory,RefundGateway.Status.SUCCEEDED,false).submit(id,new BigDecimal("1000"));});gate.countDown();assertEquals(a.get(5,java.util.concurrent.TimeUnit.SECONDS),b.get(5,java.util.concurrent.TimeUnit.SECONDS));}finally{pool.shutdownNow();}
 }
 public static final class ReceiptProbe {
  public static void main(String[] args)throws Exception{
   var gateway=new SimulatedRefundGateway(Path.of(args[0]),RefundGateway.Status.valueOf(args[2]),false);
   UUID id=UUID.fromString(args[1]);
   var result=args[3].equals("submit")?gateway.submit(id,new BigDecimal("1000")):gateway.queryRefundStatus(null,id).gatewayResult();
   System.out.println("RECEIPT:"+result.status().name());
  }
 }
 @Test void separateJvmRestartReadsDurableReceipt()throws Exception{
  UUID id=UUID.randomUUID();
  for(String mode:List.of("submit","query")){
   Path log=directory.resolve(mode+".log");
   var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-cp",System.getProperty("java.class.path"),ReceiptProbe.class.getName(),directory.toString(),id.toString(),mode.equals("submit")?"SUCCEEDED":"FAILED",mode).redirectErrorStream(true).redirectOutput(log.toFile()).start();
   if(!process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("Receipt JVM timeout");}
   assertEquals(0,process.exitValue(),Files.readString(log));assertTrue(Files.readString(log).contains("RECEIPT:SUCCEEDED"));
  }
 }
}
