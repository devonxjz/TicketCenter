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
    }
    for(String batch:Files.readString(Path.of("database/migrations/045_khanh05_refund_worker.sql")).split("(?im)^\\s*GO\\s*\\r?$",-1))if(!batch.isBlank())q.execute(batch);
    c.commit();
   }catch(Exception e){c.rollback();throw e;}
  }
 }
}
