package vn.ticketscenter.database;
import java.sql.*;
import vn.ticketscenter.fulfillment.checkin.integration.ScanFixture;

/** Dedicated CI SQL container only. */
public final class EventCancellationWebLogin {
    public static void main(String[] args)throws Exception {
        try(var c=DriverManager.getConnection("jdbc:sqlserver://127.0.0.1:1433;databaseName=TicketCenter_Khanh02_Test;encrypt=true;trustServerCertificate=true;loginTimeout=5","sa",ScanFixture.required("TC_SCAN_SA_PASSWORD"));var q=c.createStatement()) {
            for(String role:new String[]{"ADMIN","WORKER"}) {
                String user=ScanFixture.required("TC_KHANH06_"+role+"_USER"),secret=ScanFixture.required("TC_KHANH06_"+role+"_PASSWORD");
                if(!user.matches("khanh06_(admin|worker)_[a-f0-9]{32}")||!secret.matches("K06![a-f0-9]{48}"))throw new IllegalArgumentException("Disposable login required");
                q.execute("CREATE LOGIN ["+user+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=ON;CREATE USER ["+user+"] FOR LOGIN ["+user+"];ALTER ROLE "+(role.equals("ADMIN")?"tc_platform_admin":"tc_worker")+" ADD MEMBER ["+user+"];GRANT VIEW DEFINITION TO ["+user+"]");
            }
        }
    }
}
