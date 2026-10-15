package vn.ticketscenter.database;
import java.nio.file.*;
import java.util.*;
import java.sql.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
/** Only the named loopback fixture; exercise the unmodified complete WAR's scheduler. */
public final class RefundWorkerWebFixture {
 public static void main(String[] args)throws Exception{
  Path file=Path.of("target/khanh05-web-fixture.properties");
  if(args[0].equals("prepare")){
   var f=new RefundWorkerSqlServerTest().fixture(false,false);var p=new Properties();
   p.setProperty("refund",f.refund().toString());p.setProperty("attempt",f.attempt().toString());p.setProperty("item",f.base().item().toString());p.setProperty("zone",f.base().zone().toString());
   try(var out=Files.newOutputStream(file)){p.store(out,"Disposable fixture identifiers only");}return;
  }
  var p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}
  UUID attempt=UUID.fromString(p.getProperty("attempt")),refund=UUID.fromString(p.getProperty("refund"));
  if(args[0].equals("due")){
   RefundWorkerSqlServerTest.sql("UPDATE dbo.tc_refund_dispatches SET next_query_at=DATEADD(second,-1,SYSUTCDATETIME()),lease_until=NULL WHERE attempt_id=?",attempt);return;
  }
  String expected=args[0].equals("unknown")?"NEEDS_RECONCILIATION":"COMPLETED";
  String actual=RefundWorkerSqlServerTest.value("SELECT status FROM dbo.tc_refund_obligations WHERE id=?",refund);
  if(!expected.equals(actual))System.exit(3);
  check("1",RefundWorkerSqlServerTest.value("SELECT COUNT(*) FROM dbo.tc_refund_transfer_logs WHERE refund_id=?",refund));
  check("1",RefundWorkerSqlServerTest.value("SELECT COUNT(*) FROM dbo.tc_refund_dispatches WHERE attempt_id=?",attempt));
  check(expected.equals("COMPLETED")?"REFUNDED":"REFUND_PENDING",RefundWorkerSqlServerTest.value("SELECT status FROM dbo.tc_tickets WHERE order_item_id=?",UUID.fromString(p.getProperty("item"))));
  check(expected.equals("COMPLETED")?"0":"1",RefundWorkerSqlServerTest.value("SELECT sold_quantity FROM dbo.tc_zones WHERE id=?",UUID.fromString(p.getProperty("zone"))));
  if(expected.equals("COMPLETED"))check("1",RefundWorkerSqlServerTest.value("SELECT COUNT(*) FROM dbo.tc_outbox WHERE idempotency_key=?","refund-succeeded:"+attempt));
  System.out.println("Full WAR refund scheduler: "+expected+"; one durable attempt and correct inventory.");
 }
 private static void check(String expected,String actual){if(!expected.equals(actual))throw new IllegalStateException("Refund acceptance mismatch");}
}
