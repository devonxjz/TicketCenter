package vn.ticketscenter.database;
import java.sql.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;
/** CI disposable service login; cannot address a remote/project server. */
public final class RefundWorkerWebLogin {
 public static void main(String[] args)throws Exception{
  String user=ScanFixture.required("TC_KHANH05_WORKER_USER"),secret=ScanFixture.required("TC_KHANH05_WORKER_PASSWORD");
  if(!user.matches("khanh05_worker_[a-f0-9]{32}")||!secret.matches("K05![a-f0-9]{48}"))throw new IllegalArgumentException("Disposable login required");
  try(var c=DriverManager.getConnection("jdbc:sqlserver://127.0.0.1:1433;databaseName=TicketCenter_Khanh02_Test;encrypt=true;trustServerCertificate=true;loginTimeout=5","sa",ScanFixture.required("TC_SCAN_SA_PASSWORD"));var q=c.createStatement()){
   q.execute("CREATE LOGIN ["+user+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=ON;CREATE USER ["+user+"] FOR LOGIN ["+user+"];ALTER ROLE tc_worker ADD MEMBER ["+user+"];GRANT VIEW DEFINITION TO ["+user+"]");
  }
 }
}
