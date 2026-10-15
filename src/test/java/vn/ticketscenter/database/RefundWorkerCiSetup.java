package vn.ticketscenter.database;
import java.nio.file.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
/** Fresh CI service or existing named loopback test database, never a project database. */
public final class RefundWorkerCiSetup {
 public static void main(String[] args)throws Exception{
  if(args.length==0)SqlReportCiSetup.main(args);
  else if(args.length!=1||!args[0].equals("--existing"))throw new IllegalArgumentException("Unsupported setup mode");
  try(var c=ScanFixture.owner();var q=c.createStatement()){
   c.setAutoCommit(false);
   try{
    q.execute("SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON");
    if(args.length==1){
     // Local suites may have reinstalled the older Hold runtime's release helper. Restore
     // the EXACT committed helper used by fresh SqlReportCiSetup, fixture only.
     for(String batch:Files.readString(Path.of("database/migrations/035_sql_report_runtime.sql")).split("(?im)^\\s*GO\\s*\\r?$",-1))
      if(batch.contains("CREATE OR ALTER PROCEDURE dbo.usp_ReleaseTicketHold"))q.execute(batch);
     for(String file:new String[]{"040_payment_ticket_runtime_reads.sql","041_hold_refund_runtime_compatibility.sql","042_checkin_event_user_lock_order.sql","043_coupon_admin.sql","044_buyer_order_history_views.sql","045_order_ticket_access_assertions.sql","043_khanh04_admin_refund.sql","045_event_commands_and_public_seats.sql","046_admin_refund_current_authority.sql","047_event_authority_lock_order.sql"})
      for(String batch:Files.readString(Path.of("database/migrations",file)).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())q.execute(batch);
    }
    for(String batch:Files.readString(Path.of("database/migrations/048_khanh05_refund_worker.sql")).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())q.execute(batch);
    c.commit();
   }catch(Exception e){c.rollback();throw e;}
  }
 }
}
